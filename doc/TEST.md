# Video Module Test Guide

This document describes how to run and create unit/functional tests for the `Video` module, as well as how to use the CLI extraction tool to capture real-world video track selection test cases.

---

## Running the Unit Test Suite

Run tests from the `Video` directory:

```bash
cd Video
./gradlew testNoamazonDebugUnitTest
```

Or run offline when dependencies are already cached:

```bash
./gradlew --offline testNoamazonDebugUnitTest
```

### Test Reports
- **HTML Report**: `Video/build/reports/tests/testNoamazonDebugUnitTest/index.html`
- **JUnit XML Results**: `Video/build/test-results/testNoamazonDebugUnitTest/`

---

## Running Specific Tests

Run a single test class:

```bash
./gradlew testNoamazonDebugUnitTest \
  --tests 'com.archos.mediacenter.video.player.TrackSelectionTest'
```

Run a specific test method:

```bash
./gradlew testNoamazonDebugUnitTest \
  --tests 'com.archos.mediacenter.video.player.TrackSelectionTest.testCsvTrackSelectionSuite'
```

---

## Existing Tests

### 1. Audio Track Selection Policy (`AudioTrackSelectionPolicyTest`)

**Class**: `com.archos.mediacenter.video.player.AudioTrackSelectionPolicyTest`

Dedicated specification unit tests verifying all rules and tie-breakers in [`AUDIO_TRACK_SELECTION.md`](AUDIO_TRACK_SELECTION.md):
- **Preservation of User Choices**: Valid saved audio track index is retained and not overwritten.
- **Selection Triggers**: Unset (-1), out-of-bounds, or unsupported audio tracks trigger auto-selection.
- **Selection Precedence**: Prefer VO/Original Audio (`KEY_PREFER_ORIGINAL_AUDIO_TRACK`) -> Favorite Audio Language (`favAudioLang`) -> First Supported Track fallback.
- **VO Scraper Integration**: Matches scraped original language; skips undetermined (`und`) original language.
- **Tie-Breakers**: Respects container `default` disposition flags for both VO and favorite language; disambiguates Chinese audio title variants (Mandarin, Cantonese, Taiwan).
- **Unsupported Tracks**: Skips unsupported audio codecs even if the language matches.

### 2. Subtitle Track Selection Policy (`SubtitleTrackSelectionPolicyTest`)

**Class**: `com.archos.mediacenter.video.player.SubtitleTrackSelectionPolicyTest`

Dedicated specification unit tests verifying all rules and the complete decision table in [`SUBTITLES.md`](SUBTITLES.md):
- **Full Examples Table (Rows 1-10)**: Covers English native (full vs forced vs none), French native, foreign audio with full native subtitles, exact sidecar fallback (`dummy.srt` selection for native English audio over competing tagged sidecars), hide subtitles with forced tracks, and single-audio untagged forced tracks.
- **Audio Track Switching**: Changing audio track dynamically switches matching forced subtitles or disables them if none exists, without overriding manual or full subtitle selections.
- **Multi-Audio Untagged Forced Rejection**: Rejects untagged forced subtitles when multiple audio streams are present to prevent language mismatch.
- **Saved Subtitle Preservation**: Retains valid saved subtitle selection when the language at that index matches.
- **Full Subtitle Fallback**: Never falls back to a forced track when full subtitles are requested; falls back to English full subtitle when foreign audio has no native subtitle match.
- **Tie-Breakers**: Respects container `default` disposition and Chinese subtitle title variants (Simplified vs Traditional).

### 3. Data-Driven Track Selection Regression Suite (`TrackSelectionTest`)

**Class**: `com.archos.mediacenter.video.player.TrackSelectionTest`

Data-driven regression runner evaluating curated representative fixtures in `track_selection_tests.csv`. Additional fixtures from real-world media files can be generated and appended using `ffprobe_track_selection_to_csv.sh`. Failures are explicitly reported with row numbers if any malformed rows are encountered.

### 4. Playback Resume Policy (`PlaybackResumePolicyTest`)

**Class**: `com.archos.mediacenter.video.player.PlaybackResumePolicyTest`

Verifies resume bookmarking logic, lifecycle checkpoints vs database state, external resume intents, and "Start from beginning" overrides.

### 5. External Resume Intent Parsing (`ExternalResumeIntentTest`)

**Class**: `com.archos.mediacenter.video.player.ExternalResumeIntentTest`

Verifies intent extra extraction for external video playback launches (position priority, timestamp bounds, redaction in logs).

### 6. Image Transformations (`FidelityTransformationTest`)

**Class**: `com.archos.mediacenter.video.picasso.FidelityTransformationTest`

Verifies Picasso image cropping, aspect ratio preservation, and dimension clamping.

---

## CLI Track Selection Extraction Tool

A standalone CLI script is provided to inspect video files using `ffprobe` and `jq`, evaluate Nova's selection rules, and optionally append the generated test case to `track_selection_tests.csv`.

**Location**: `Video/src/test/tools/ffprobe_track_selection_to_csv.sh`

### Usage

```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh [OPTIONS] VIDEO_FILE [CSV_FILE]
```

### Available Options

| Option | Description | Default |
|---|---|---|
| `--fav-sub-lang <LANG>` | Preferred subtitle language code (e.g. `en`, `fr`, `ja`, `zh`) | `en` |
| `--fav-audio-lang <LANG>` | Preferred audio language code (e.g. `en`, `fr`, `es`) | `en` |
| `--ui-lang <LOCALE>` | System / UI locale code (e.g. `en`, `fr`, `es`) | `en` |
| `--prefer-vo`, `--prefer-original-audio` | Enable prefer original audio (VO) track setting | `false` |
| `--vo-lang <LANG>`, `--original-audio-lang <LANG>` | Scraper original/VO audio language (e.g. `de`, `is`, `ja`, `fr`) | Auto from container or empty |
| `--sub <FILE>` | Add external sidecar subtitle file (can be repeated) | Auto-discovered from directory |
| `--hide-subtitles`, `--no-subtitles-apart-forced` | Enable "Hide subtitles by default / Forced only" | `false` |
| `--name <NAME>` | Name / description for the test case | Video basename |
| `--append` | Append generated test case directly to CSV file | `false` |
| `--json-out <FILE>` | Save raw ffprobe stream JSON to file | Temporary file |

### Examples

#### 1. German Series with External English and German Subtitles (`Deutschland 83`):
Evaluates original German audio (`de`) with French UI locale and subtitle preference (`fr`). Since no French subtitle exists, it falls back to the external English full subtitle:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --append \
  --prefer-vo \
  --vo-lang de \
  --fav-sub-lang fr \
  --ui-lang fr \
  --sub Deutschland-S01E01-Quantum_Jump-1080p-AVC-AC3-5.1.eng.srt \
  --sub Deutschland-S01E01-Quantum_Jump-1080p-AVC-AC3-5.1.ger.srt \
  Deutschland-S01E01-Quantum_Jump-1080p-AVC-AC3-5.1-short.mkv
```

#### 2. Icelandic Drama with Embedded French & Icelandic Audio and French Subtitles (`Trapped`):
Prefers the original Icelandic audio (`is`) over the default French dub, triggering automatic selection of French full subtitles for the French UI user:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --append \
  --prefer-vo \
  --vo-lang is \
  --fav-sub-lang fr \
  --ui-lang fr \
  Trapped_\(2015\)-S01E01-Episode_1-1080p-AVC-EAC3-5.1-short.mkv
```

#### 3. Anime with Japanese VO Preference and English Subtitles:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --append \
  --name "Anime Japanese VO with English Subtitles" \
  --prefer-vo \
  --vo-lang ja \
  --fav-audio-lang en \
  --fav-sub-lang en \
  --ui-lang en \
  anime_episode.mkv
```

#### 4. Forced-Only Subtitle Selection:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --no-subtitles-apart-forced \
  --fav-audio-lang fr \
  --fav-sub-lang fr \
  --ui-lang fr \
  movie.mkv
```

---

## CSV Fixture Format (`track_selection_tests.csv`)

Rows in `Video/src/test/resources/track_selection_tests.csv` follow the format:

```text
name,audioTracks,subtitleTracks,uiLocale,favAudioLang,favSubLang,hideSubtitles,preferOriginalAudio,originalAudioLang,expectedAudioTrack,expectedSubtitleTrack
```

- **Audio Track Syntax**: `name;lang;format;disposition;supported|...`
  - `disposition`: Bitmask (`1` = Default, `4` = Original/VO, `64` = Forced, `128` = Hearing Impaired)
- **Subtitle Track Syntax**: `name;lang;path;disposition;isGfx;format|...`
  - `disposition`: Bitmask (`1` = Default, `64` = Forced, `128` = Hearing Impaired)
  - `path`: Non-empty if external subtitle file
- **`expectedAudioTrack`**: 0-based index of selected audio track.
- **`expectedSubtitleTrack`**: 0-based index of selected subtitle track, or `-1` for no subtitles.
