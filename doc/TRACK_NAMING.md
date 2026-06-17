# Track Naming Specification

This documents the current audio and subtitle track-name generation used by
Nova Video Player.

## Scope

The canonical implementation is:

* `MediaLib/src/com/archos/mediacenter/utils/ISO639codes.java`
* `Video/src/main/java/com/archos/mediacenter/video/browser/subtitlesmanager/ISO639codes.java`
* `Video/src/main/java/com/archos/mediacenter/video/utils/VideoUtils.java`

`MediaLib` contains the pure formatting logic. `Video` supplies Android
resources for localized language exceptions, disposition labels, and the
localized unknown-track fallback.

## Inputs

* `string`: stream title/name metadata, typically the Matroska `title` tag.
* `lang`: raw language code, usually ISO 639-1, ISO 639-2b, or ISO 639-3.
* `langName`: localized language name resolved by the caller. In `Video`, this
  also resolves Nova special strings such as `s_brazilian`.
* `format`: codec or subtitle format label, such as `AC3`, `EAC3`, `SSA`,
  `PGS`, or `TEXT`.
* `disposition`: FFmpeg disposition bitmask.
* `dispLabel`: localized label derived from `disposition`.
* `titleFirst`: `true` for audio-style output, `false` for subtitle-style
  output.
* `unknownTrackName`: localized fallback used when no usable primary label is
  available.

## Language Handling

`und` and `unknown` mean undefined language and are ignored for primary and
secondary language labels.

ISO 639-2b codes are normalized before ISO 639-1 lookup, for example:

* `fre` -> `fra` -> `fr`
* `ger` -> `deu` -> `de`

ISO 639-3 to ISO 639-1 conversion uses the JVM locale language table instead of
a large hardcoded map. Nova-specific and OpenSubtitles-specific exceptions
remain in the existing small exception maps, for example `pb`, `zt`, `pt-br`,
`zh-cn`, and `zh-tw`.

## Disposition Labels

Only one disposition label is selected. The first matching bit in this order is
used:

1. Hearing impaired
2. Visual impaired / audio description
3. Captions
4. Descriptions
5. Forced
6. Original
7. Dubbed / translated
8. Commentary
9. Lyrics
10. Karaoke

`default`, `none`, and unknown disposition bits do not add a label.

## Primary Label

The first non-empty value in this order becomes the primary label:

1. Title
2. Language
3. Disposition
4. Format

If all are empty, the localized unknown-track name is returned.

Before primary selection, a title is suppressed when it is only redundant
metadata:

* a two- or three-letter language code resolving to the same language
* the English language name for `lang`, such as `English`
* the raw `lang` code
* a language plus disposition title, such as `English (SDH)` or
  `French (Forced)`
* `SDH`

Suppressed titles allow the cleaner language or disposition label to become the
primary label. Normal descriptive titles such as `Commentary` and
`Audio Description` are kept as titles.

## Secondary Labels

Secondary labels are appended in this order:

1. Language
2. Disposition
3. Format

A secondary label is skipped if it is already represented by the primary label:

* Language is skipped when the primary contains the localized language name, the
  English language name, or equals the ISO 639-1/639-3 code.
* Disposition is skipped when the primary contains the disposition label.
* Dubbed / translated is skipped when a language label is available, because
  the language already carries the useful information and `Language
  (Translated)` is redundant.
* French hearing-impaired labels are also considered redundant when the primary
  contains `(SDH)`.
* Format is skipped when the primary already contains the format.

## Output Formatting

Secondary labels are rendered so that the technical format is always
parenthesized when it is additional information. The only exception is when the
format is the primary label because it is the only available information.

When the only secondary label is the format, no dash is used:

```text
Primary (Format)
```

Subtitle-style output (`titleFirst = false`) wraps that parenthesized format in
`<small>`:

```html
Primary <small>(Format)</small>
```

When there is secondary metadata other than the format, a dash separates the
primary label from the secondary block. Audio-style output is plain text:

```text
Primary - FirstSecondary (NextSecondary)
```

Subtitle-style output wraps the full secondary block in a single `<small>`:

```html
Primary - <small>FirstSecondary (NextSecondary)</small>
```

The first secondary label is not wrapped in its own parentheses. Additional
secondary labels are parenthesized. This avoids stacked parenthetical output
such as `Primary (Secondary) (Format)`.

## Examples

| Title | Lang | Disposition | Format | titleFirst | Output |
| --- | --- | --- | --- | --- | --- |
| `English (SDH)` | `eng` | Hearing impaired | `SSA` | `false` | `Anglais - <small>Malentendants (SSA)</small>` |
| `En` | `eng` | none | `SSA` | `false` | `Anglais <small>(SSA)</small>` |
| empty | `und` | Original | `TEXT` | `false` | `Original <small>(TEXT)</small>` |
| empty | `und` | Hearing impaired + Original | `TEXT` | `false` | `Malentendants <small>(TEXT)</small>` |
| empty | `und` | Translated | `TEXT` | `false` | `Traduit <small>(TEXT)</small>` |
| `SDH` | `fre` | Hearing impaired | `TEXT` | `false` | `Français - <small>Malentendants (TEXT)</small>` |
| `French (Forced)` | `fre` | Default + Forced | `TEXT` | `false` | `Français - <small>Forced (TEXT)</small>` |
| `Commentary` | `eng` | Commentary | `AC3` | `true` | `Commentary - Anglais (AC3)` |
| `Audio Description` | `fre` | Visual impaired | `EAC3` | `true` | `Audio Description - Français (EAC3)` |
| `Main` | `fre` | Translated | `TEXT` | `false` | `Main - <small>Français (TEXT)</small>` |
| `Main` | `eng` | Dubbed | `AC3` | `true` | `Main - Anglais (AC3)` |

These examples are covered by
`MediaLib/test/resources/track_naming_tests.csv`.

## Test Data Tool

Real media samples can be converted into append-ready CSV rows with:

```bash
MediaLib/test/tools/ffprobe_track_naming_to_csv.sh VIDEO
```

The script runs `ffprobe`, parses audio and subtitle stream metadata, computes
the FFmpeg disposition bitmask, maps common codecs to the format labels used by
the tests, and prints rows matching `track_naming_tests.csv`:

```csv
title,lang,format,disposition,titleFirst,expectedResult
```

Preview rows before modifying the CSV:

```bash
MediaLib/test/tools/ffprobe_track_naming_to_csv.sh --json-out /tmp/video.ffprobe.json /path/to/video.mkv
```

Append generated rows to the default test CSV:

```bash
MediaLib/test/tools/ffprobe_track_naming_to_csv.sh --append /path/to/video.mkv
```

Use preview mode for files with many internal subtitle streams. The script emits
one row per audio/subtitle stream and intentionally does not deduplicate, so
files with repeated subtitle dispositions can generate many identical rows.

## Legacy Overload

The older `generateTrackName(string, lang, format, titleFirst)` overload is
still used in paths that do not have disposition metadata. It keeps the previous
behavior:

* audio-style output: `Title (Language)` or `Language`
* subtitle-style output: `Language<small> (Format)</small>` and optional title
  suffix
* empty output is interpreted by `Video` as the localized unknown-track name

New playback and metadata display paths that have disposition metadata should
use the disposition-aware overload.
