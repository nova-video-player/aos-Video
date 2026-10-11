# Subtitle Display Pipeline

This document describes how Nova displays subtitles today. Subtitles are
decoded, styled, and rendered entirely in native code (`libass` + OpenGL). No
subtitle text or bitmap crosses the JNI boundary into Java for display. Java's
role is limited to telling the native engine *how* to render — style, geometry
(canvas size and the video's on-screen box), surface lifecycle — never *what*
to render.

The only content-shaped data Java ever pulls from native is a pre-blended
bitmap for the stereoscopic 3D path (see "Stereoscopic 3D subtitles"), and the
only native-to-Java call is a content-changed "poke" for that same path.

## High-level flow

1. Native AVOS parses subtitle tracks together with the media stream, and
   separately discovers external subtitle files alongside the video.
2. For the selected track, `Source/stream_subtitle.c` (internal/embedded
   tracks) or `Source/stream_sub_ext.c` (external files) feeds the cue data
   directly into the native `SUB_ENGINE` via `sub_engine_feed*()` — a plain C
   function call, not a JNI hop. Times handed to the engine are converted from
   the stream's time-scaled domain to real stream time at the call site (see
   "Timing contract").
3. `Source/sub_engine.c` dispatches to a format backend: `sub_format_srt.c`
   and `sub_format_ssa.c` both wrap `libass`, while `sub_format_gfx.c`
   handles PGS/VobSub bitmap tracks without `libass`. Which backend a track
   gets is decided in exactly one place, `sub_fmt_from_format()`.
4. A native render thread in `Source/sub_render_gl.c` polls the engine against
   the engine clock, rasterizes whatever the active backend returns, and draws
   it onto the subtitle `TextureView`'s OpenGL surface. It sleeps until the
   backend says something can change (see "Timing contract").
5. `SubtitleEngine.java` (backed by `jni/libavosjni/jni_sub_engine.c`) is the
   Java control surface: it drives the native engine's lifecycle, reports the
   canvas size and the video box, and pushes user style preferences down
   through JNI setters.
6. `SubtitleManager.java` persists the user's style preferences, forwards
   them to `SubtitleEngine`, owns the "subtitle kind" rules, and computes the
   bottom obstruction (control bar / system bars) that lifts text subtitles.
   `SurfaceController.java` sizes the subtitle surface and reports the video
   box.

Native owns subtitle *content, timing, and drawing*; Java owns only *style
preferences, geometry, and surface plumbing*.

## Timing contract

The whole pipeline below the stream layer works in one clock domain. The
authoritative statement lives in `Source/sub_format.h`; this is the summary.

- **RST** — real stream time: media position in ms, the domain of the UI seek
  bar and of every external `.srt`/`.ass`/`.idx` file. It does not move when
  playback speed changes.
- **TS** — the player's internal time-scaled domain (`ts = rst / speed`). Its
  anchor is rewritten on every speed change, so a cue converted to TS once and
  held (external files are fed in full at open; `libass` keeps its events)
  would silently go stale. TS never crosses the engine boundary.

### Engine clock

`engine_clock_cb()` in `Source/avos_mp_video.c` is registered once in
`avos_mp_video_open()` (`sub_engine_start()`) and lives for the stream's whole
lifetime, including across track re-opens. It returns:

- the video clock (or the audio clock when there is no video) mapped back to
  RST via `TS_TO_RST_TIME`, minus `subtitle_offset` (the user's delay, in RST
  ms, subtracted here and nowhere else), clamped at 0;
- for **external text** tracks only (`ext && !gfx`), additionally scaled by the
  user's subtitle ratio: `t * d / n`;
- `-1` when there is no valid clock yet (seek/init).

External bitmap tracks (VobSub/PGS) are scaled at lookup time in
`stream_sub_ext_get_gfx_data()` (`scale_time()`); internal tracks carry no
ratio.

### Feeding

`pts_ms`/`duration_ms` passed to `sub_engine_feed*()` are RST ms and
**undelayed**. `stream_subtitle.c` converts at feed time with
`_sub_time_to_engine()` / `_sub_dur_to_engine()`:

- `duration > 0` finite; `0` = clear (bitmap tracks only); `< 0` = unknown /
  until the next cue.
- A cue end pinned at `INT_MAX` (PGS/DVD cues with no end time) is detected
  before scaling and passed as `-1`, since scaling it would overflow at
  speeds other than 1.0.
- `_feed_bitmap_to_engine()` holds a decoded bitmap back (`subtitle_pending`)
  until its start time is due, so the engine never receives a future bitmap
  early.
- External text cues are fed at their raw file times; the ratio is applied by
  the clock, so a ratio change is live and needs no re-feed.

### Rendering and scheduling

- `render_at(pts)`: a cue is visible iff `start <= pts < start + duration`.
- `get_schedule()` reports when output can next change by itself, as an
  absolute RST deadline strictly greater than `pts`, plus an `animating` flag
  (libass fade/karaoke/scroll).
- `sub_engine_wait_event()` converts that to a wall-clock wait
  (`schedule_to_wall_ms()`): `rst_to_ts_delta()` at the speed in force when the
  wait starts, rounded up, never less than 1 ms, capped at 250 ms (bounding
  staleness after a speed change), and ticking at 16 ms while a backend is
  animating. With no valid clock it polls every 50 ms. While paused it sleeps
  until an explicit broadcast.
- A delay, ratio or speed change therefore needs no re-feed: the clock moves,
  `stream_set_subtitle_offset()` / `stream_set_subtitle_ratio()` call
  `sub_engine_force_wake()`, and every cue is re-evaluated declaratively.
  The wake also works while paused.
- Known, accepted inexactness: for an external text track with a ratio the
  clock runs at `d/n` (0.959x / 1.043x) of RST, which the schedule conversion
  does not model. Waking up to ~4% early just re-polls; late wake-ups are
  bounded by the 250 ms cap.

### Engine lifetime vs. the stream

`avos_mp_video_open()` acquires a reference-counted handle to the published
engine (`sub_engine_registry_acquire()`) and `avos_mp_video_close()` releases
it last. Teardown order matters: `sub_engine_stop()` first (the clock context
is the raw `STREAM*`), then `stream_delete()` (which stops the sub-decode
thread), then `sub_engine_close_track()`, then the registry release. Closing
the track earlier would let a still-running decode thread re-open the track
with the old video's data.

## Native subtitle decoding

`Source/stream_subtitle.c` drives subtitle decoding from
`stream_sub_dec_thread()`:

- `_sub_decode()` runs while the stream has a valid subtitle track and is not
  paused (or a replay is pending), using `s->video_time` as the reference clock
  and subtracting `s->subtitle_offset` before deciding which subtitle is due.
- Internal subtitles use `_get_next_int_sub()`, which classifies the track
  format into one of three cases and feeds the native `SUB_ENGINE` directly
  (`sub_engine_feed*()`, a plain C call — no JNI, no `VIDEO_FRAME` handed to
  Java):
  - **Case 1 — raw passthrough** (`SUB_FORMAT_SSA`, `SUB_FORMAT_TEXT`): the
    demuxed packet is already clean text; `sub_engine_open_track()` opens the
    track with no `sub_dec`, and packets are fed straight to
    `sub_engine_feed()`. A leading 4-byte duration prefix is stripped from the
    payload first.
  - **Case 2 — ffdec-then-engine** (`SUB_FORMAT_WEBVTT`, `SUB_FORMAT_MOV_TEXT`):
    `codec_ffsub.c` decodes the binary-wrapped packet to plain text first,
    then that text is fed to the engine as `SUB_FMT_SRT`.
  - **Case 3 — ffdec-then-engine-bitmap** (`SUB_FORMAT_PGS`, `SUB_FORMAT_DVD_GFX`):
    `codec_ffsub.c` decodes to BGRA pixels (and opens the `SUB_FMT_GFX` engine
    track itself); `_feed_bitmap_to_engine()` hands them to
    `sub_engine_feed_bitmap()`.
- External subtitles use `_get_next_ext_sub()` (see `Source/stream_sub_ext.c`
  below).
- `open_track()` receives the decoded video size (for GFX, `codec_ffsub.c`
  passes the subtitle stream's own coordinate frame in that slot — see
  "Graphic subtitle rendering") plus the codec-private header and any fonts
  embedded in the container (`_bridge_embedded_fonts()` copies the demuxer's
  `ATTACHED_FONT` records into `SUB_EMBEDDED_FONT`).
- On a track switch the stream sets `subtitle_replay`; the next decode pass
  re-feeds from the container but skips cues already expired at the current
  time (`_cue_expired()`), and for bitmap tracks feeds only the last bitmap if
  it is still active.

Subtitle delay and timing ratio are applied in native (see "Timing contract"):

- `avos_mp_video_setsubtitledelay()` calls `stream_set_subtitle_offset(delay)`,
  which stores the offset and force-wakes the engine.
- `avos_mp_video_setsubtitleratio()` stores a numerator/denominator pair and
  force-wakes the engine; the engine clock applies it to external text tracks.

Track selection is also native (`Source/avos_mp_video.c`):

- `avos_mp_video_setsubtitletrack(track < 0)` calls `sub_engine_close_track()`
  to disable subtitles.
- Valid tracks call `stream_set_subtitle_stream()`. Tracks whose parser offers
  a subtitle-only reset (and all external tracks) go through
  `_switch_subtitle()`: it idles only the parser and subtitle threads, closes
  the old decoder, frees the subtitle frame, **closes the previous engine
  track immediately** (so the old track's text or bitmap does not linger until
  the new track's first packet), commits the new selection, resets the
  parser's subtitle queue, and sets `subtitle_replay`. No video/audio seek or
  restart is needed. Legacy parsers without a subtitle reset still pause,
  select, and reseek near the current time so the internal subtitle decoder
  reinitializes. If the parser cannot be idled within 500 ms the selection is
  left unchanged. The engine track itself is opened lazily by
  `stream_subtitle.c` when the first packet on the new track arrives.
- Selection is committed under `subtitle_table_lock`, re-resolving an external
  track by path in case a concurrent rescan moved or removed it
  (`_commit_subtitle_selection()`); a `subtitle_select_generation` counter
  lets a rescan avoid reverting a newer manual selection.

External subtitle discovery is refreshed through `stream_check_subtitles()`.
It is non-blocking: it queues a scan on a process-wide discovery worker
(single-slot mailbox) so the filesystem scan and per-candidate `detect()` do
not run on the JNI caller's thread. The scan itself is incremental
(`stream_sub_ext_update()`): unchanged file sets are a no-op, pure additions
are appended live without pausing, and anything removed or renamed triggers a
full rebuild that reselects the previous external track by path. Closing a
stream waits for the discovery worker (`stream_sub_ext_wait_for_discovery()`).

## Subtitle kind

Native decides what *kind* of subtitle a track is and tells Java; Java never
infers a kind from a format label, file extension, or gfx flag.

- `sub_kind_from_format()` (`Source/sub_engine.c`, `sub_kind.h`) is a pure
  function of `sub_fmt_from_format()`: SSA → `SUB_KIND_SSA`, SRT-routed text →
  `SUB_KIND_PLAIN_TEXT`, GFX → `SUB_KIND_GRAPHIC`, anything else →
  `SUB_KIND_UNSUPPORTED`. The values are wire values and are never renumbered.
- The kind travels in the per-track `AVOS_MP_METADATA_SUBTITLE_TRACK_KIND`
  field (alongside name, path, format, language, disposition).
- `SubtitleManager.kindFromNative()` maps unknown/missing values to
  `KIND_UNSUPPORTED` (fail safe). `SubtitleManager.setActiveTrack(track)` sets
  the active kind; the static rules (`isGraphic`, `isPlainText`, `isStyled`,
  `supportsUserStyle`, `canChooseOverrideMode`, `effectiveOverrideMode`) answer
  every downstream question: layout category, vertical-position handling,
  control-bar avoidance, whether the style settings apply.
- `effectiveOverrideMode()` returns Custom for plain text regardless of the
  stored mode, mirroring native (`force_all = is_plain_text || mode == CUSTOM`);
  the stored mode is left alone for styled files.

## Automatic subtitle-track selection

This section describes Nova's current automatic-selection policy and the
remaining implementation work needed to make forced-track detection reliable.

### Current implementation

`PlayerService` reads the audio-selection preferences, one subtitle preference
(`favSubLang`), the *Hide subtitles by default* preference, and the *Show
subtitles for local-language audio* preference. The detailed audio policy is
specified in [AUDIO_TRACK_SELECTION.md](AUDIO_TRACK_SELECTION.md).
The language preferences fall back to the device locale's ISO-639-3 code when
no value is stored; neither has a distinct **System language** value.

Current automatic selection works as follows:

1. On first enumeration, audio selection retains a valid saved audio track. If
   none is valid, it follows the audio-selection policy before subtitle rules
   evaluate the resulting active audio language.
2. Subtitle selection retains a saved subtitle track when the language at its
   saved index still matches. A manual or saved selection is never replaced by
   automatic forced-track selection.
3. If *Hide subtitles by default* is enabled and there is no saved selection,
   Nova suppresses full subtitles but selects a forced track matching the active
   audio language. A forced track with no determined language is eligible only
   when there is exactly one audio track.
4. Otherwise, if `favSubLang` matches the current locale and the active audio
   also matches that locale, Nova treats the user as a native speaker: it
   suppresses full local-language subtitles and selects the matching forced
   track, or none if it is absent. This step is skipped, falling through to
   step 5, when *Show subtitles for local-language audio* is enabled.
5. In every other case, Nova scans for a non-forced full subtitle matching
   `favSubLang` (using normalized language codes via `isFavoriteLanguageMatch`),
   using Chinese title variants and the `default` disposition as tie breakers.
   Forced tracks are not candidates for this full-subtitle scan, unless all
   subtitle tracks in the container are marked with the forced disposition bit
   (indicating a mislabeled/bogus container disposition), in which case the
   disposition-based forced flag is ignored during full-subtitle selection unless
   the track's title explicitly indicates "forced".
6. If no preferred-language full subtitle exists, *Hide subtitles by default*
   is off, and the valid active audio language differs from the device locale,
   Nova falls back to a non-forced English full subtitle. A matching `default`
   track wins over the first English match.
7. An external text subtitle named exactly like the video (for example
   `movie.srt`) is an untagged default full subtitle. When hiding is off, Nova
   selects it if no matching forced subtitle is available for local audio, or
   as the final normal fallback. This is intentional: the exact sidecar name
   convention indicates that it was supplied as the video's default subtitle.
8. If no eligible full or forced track exists, Nova selects none.

Audio processing is performed before deferred subtitle processing, so this scan
uses the resolved audio selection. There is no separate forced-subtitle
preference. The remaining work is to make the FFmpeg forced disposition and
external filename heuristic consistently available to this classification.

### Forced subtitle semantics

A *forced* subtitle track contains only dialogue or on-screen text that needs
translation for an audience listening to the track's language. It is not a
complete transcription of the film. For example, an English-forced track
alongside English audio commonly translates a short conversation in another
language; it is useful to an English-audio listener even if they normally keep
subtitles off.

A forced track must not be treated as a better version of a full subtitle track:

- If the user wants full French subtitles, Nova must select the full French
  track rather than an English-forced track. Selecting the latter would show
  only a few English lines and fail to provide the requested French subtitles.
- An English-forced track must never be enabled automatically for French audio
  merely because it is marked forced or default.
- A French-forced track may be useful with French audio, including for a
  French-locale user. The local-audio rule that suppresses automatic *full*
  French subtitles must not suppress this forced-only track.
- Nova renders one subtitle track at a time. A forced track therefore cannot be
  overlaid on a selected full subtitle track; the full track wins.

For embedded tracks, the FFmpeg `forced` disposition is normally authoritative.
However, if every subtitle track in a multi-track container has the `forced`
disposition bit set, the container is deemed mislabeled (a known quirk of certain
WEB-DL/streaming rips where the muxer sets `disposition: default, forced` on every
stream). In that case, Nova ignores the container's `forced` disposition bit during
full-subtitle candidate selection so that dialogue tracks are not starved out,
relying instead on track titles or filenames for explicit "forced" indicators. For
external tracks and containers that omit the disposition, a `forced` token in a
normalized title or filename is a fallback heuristic only. Classification must
be retained separately from the localized display name. The display name and
disposition-label rules are specified in [Track Naming Specification](TRACK_NAMING.md);
in particular, `Forced` may be a secondary UI label and is not a language.

### Policy using the existing preferences

`favSubLang` remains the user's single preferred full-subtitle language. When
it has the same normalized base language as the current device locale, Nova
assumes it is the user's native language: full subtitles in that language are
useful for foreign audio, but not for local audio.

This assumption does not hold for every user: device/system locale is a weak
signal of native language, especially for English, which is commonly left as
the out-of-box default regardless of the owner's actual language. The *Show
subtitles for local-language audio* preference (`subtitles_show_full_for_local_audio`,
off by default) lets a user opt out of the native-speaker suppression and fall
back to the normal `favSubLang` full-subtitle scan (step 5) even when audio,
locale, and `favSubLang` all match.

`subtitles_hide_default` means **hide full subtitles by default; still show
matching forced subtitles**. Its summary must state this behaviour. It does not
disable a manual or valid saved subtitle selection.

Language comparison uses normalized ISO 639 base codes. Thus English variants
such as `en`, `en-GB`, and `en-US` match each other for forced-track selection;
existing Chinese title-variant tie breakers remain in effect.

### Selection algorithm

The policy follows Kodi's useful forced-track adaptation, but uses Nova's
existing preferences and strict language matching:

1. Resolve the active audio track, including the user's preferred-audio
   selection and supported-track fallback.
2. Keep a valid saved or manually selected subtitle track unchanged.
3. If `subtitles_hide_default` is enabled, select only a forced track matching
   the active audio language. With exactly one audio track, a forced track with
   no usable language metadata is also eligible. Otherwise select none.
4. If `favSubLang` and the current locale match the active audio language,
   select only a forced track under the same rule as step 3. This is the native
   speaker case: French audio plus French `favSubLang` selects French forced
   subtitles, not full French subtitles. Skip this step, falling through to
   step 5, when *Show subtitles for local-language audio* is enabled.
5. Otherwise select a non-forced full subtitle matching `favSubLang`. A French
   user watching English audio therefore gets full French subtitles, not an
   English forced track. Apply language-variant and `default` tie breakers only
   among eligible tracks.
6. If no preferred full subtitle exists, hiding is off, and the active audio is
   not in the device locale, select a non-forced English full subtitle (default
   disposition first, then first match).
7. If hiding is off and the active audio is in the device locale, select an
   untagged default external text subtitle only after a matching forced track.
   A sidecar named exactly like the video, such as `movie.srt`, is that default.
8. A forced track is never a fallback for a requested full subtitle. If no
   eligible track exists, select none.

The `default` disposition is only a tie breaker among already eligible tracks;
it must never override a language mismatch. Audio and subtitle languages are
compared as normalized ISO 639-1 base codes, so equivalent two-letter,
three-letter, and locale-tag forms compare equally.

External subtitle suffixes such as `.eng.srt` and `.ger.srt` are also matched
by their ISO language identity, even when the file browser renders their names
in the device language (for example, `Anglais` on a French device).

When the user changes audio, repeat the forced-track steps only if the currently selected
subtitle was automatically selected as forced. Switch to a forced track matching
the new audio language, or disable subtitles if none exists. Never replace a
manual or full-subtitle selection during an audio change.

### Examples

| Active audio | System locale | `favSubLang` / hide setting | Available subtitle tracks | Automatic result |
| --- | --- | --- | --- | --- |
| English | English (any variant) | English / hide off | English full; English forced | English forced only |
| English | English (any variant) | English / hide off | English full only | None |
| French | French | French / hide off | French full; French forced | French forced only |
| French | French | French / hide off | French full only | None |
| English | French | French / hide off | French full; English forced | French full |
| French | French | English / hide off | English full; French forced | English full |
| Japanese | French | French / hide off | English full only | English full |
| English | English | English / hide off | `movie.srt` only | `movie.srt` |
| English | French | French / hide on | English forced only | English forced only |
| French | French | French / hide on | Forced with no language; one French audio track | Forced track |
| English | English (any variant) | English / hide off / show-local-audio-subs on | English full; English forced | English full |

All rows assume *Show subtitles for local-language audio* is off (the
default), except the last row, which shows the native-speaker rule (rows 1-4)
skipped when that preference is enabled: the full-subtitle scan (step 5)
runs instead and picks the full track.

The first two rows are the English-native-speaker case. The French rows apply
the same rule, while a French full-subtitle preference is still respected for
foreign audio. The final row permits an untagged forced track only when there is
no ambiguity about the audio language.

## Native subtitle formats

### Text

Text subtitles can come from external subtitle parsers or ffmpeg subtitle
decoders.

External text subtitles are handled by `Source/stream_sub_ext.c`. Format
parsers (`subtitle_srt.c`, `subtitle_vtt.c`, `subtitle_sub.c`, `subtitle_mpl2.c`,
`subtitle_smi.c`) feed the native engine **once**, at track open, via
`stream_sub_ext_feed_engine()`, with raw file times (the ratio is applied by
the engine clock):

- **SRT/VTT (streaming)** have no `parse()`; their `feed()` streams cues into
  the engine (`sub_engine_feed_gen()`). The feed runs on a **parse-worker
  pool** (up to 3 threads per stream, never `stream_sub_dec_thread`, so a slow
  file read cannot stall the decode thread's rendezvous), checkpointing every
  ~200 cues to yield and to bail out on pause/seek/close/track switch.
  Every cue and flush is gated by a **track-generation token**:
  `sub_engine_open_track()` returns it, `stream_sub_ext_set_track_generation()`
  pins it to the track's `uni_sub` synchronously on the selecting thread
  *before* the job can be enqueued, and the engine silently drops any
  `*_gen()` call whose token no longer matches the open track. A stale worker
  can therefore never leak cues into a different track.
- A feed pass that runs to true EOF uninterrupted (and without allocation
  failures) is also captured into a cue list. Later refeeds after a pause or
  seek walk that cache (`_feed_from_cache()`) instead of re-reading and
  re-parsing the file. An interrupted or incomplete pass discards its partial
  list.
- **SMI/MicroDVD/MPL2** parse into a list on the pool first (MicroDVD
  frame→ms conversion runs once, on the worker, right after its parse), then
  `stream_sub_ext_feed_engine()` walks the list synchronously
  (`sub_engine_feed()`), merging top/bottom lines with `\N`.
- A track that is not yet `SUBT_PARSE_DONE` leaves the engine track open but
  empty; the worker sets `subtitle_ext_needs_refeed` on completion, and
  `_get_next_ext_sub()`'s per-frame check re-invokes the feed. After a seek's
  flush, streaming tracks reset `DONE` → `NOT_QUEUED` first
  (`stream_sub_ext_force_streaming_refeed()`).

After the initial feed, the engine (`libass`) owns the entire timeline itself;
nothing is polled per video-time from Java or from this file.

`Source/codec_ffsub.c` is used for embedded ffmpeg-supported text formats such
as `TEXT`, `MOV_TEXT`, `SSA`, and `ASS`.

- Plain `rect->text` is copied into the frame.
- `rect->ass` is parsed to recover timing and text when ffmpeg doesn't supply
  `avpkt->duration` directly. The code handles both older ffmpeg `Dialogue:`
  output and newer comma-separated ffmpeg output.
- `AVSubtitle.start_display_time`/`end_display_time` always come back `0`
  from ffmpeg's `mov_text`/`webvtt` decoders, so `stream_parser_ffmpeg.c`
  prepends a 4-byte little-endian duration to the packet before it reaches
  `codec_ffsub.c`; the decoder strips that prefix, forwards it to ffmpeg via
  `avpkt->duration`, and uses it directly as `frame->duration`.

External ASS/SSA files are handled differently from every other external
format: `Source/subtitle_ssa.c`'s `parse_SSA()` does not parse cues into a
list at all — it reads the entire file into a heap buffer
(`uni_sub->raw_data`/`raw_size`) and sets `is_ssa = 1`. `stream_sub_ext_feed_engine()`
detects that flag and calls `sub_engine_feed_raw()` once, handing `libass`
the complete script — `[Script Info]`, `[V4+ Styles]`, `[Events]`, embedded
fonts — untouched, rather than extracting `Start`/`End`/`Text` per cue.
`ssa_feed()` strips a leading UTF-8 BOM (common in Aegisub files) before
recognizing the `[Script Info]` header.

### Bitmap

Bitmap subtitles are decoded through `Source/codec_ffsub.c`, mainly for
VobSub/DVD and PGS:

- ffmpeg subtitle rectangles are merged into one bounding box.
- The bitmap data is converted from PAL8 to BGRA.
- VobSub's 4-color palette can order fill/outline into any of the 4 slots
  depending on the source rip, so the decoder counts index usage per rect and
  normalizes it — the most-used visible index becomes solid black fill, the
  next becomes white outline — before conversion.
- `frame->window` stores the subtitle bitmap bounding box.
- The subtitle stream's own coordinate frame comes from one source for PGS and
  VobSub alike: a `size: WxH` line in the track's init data
  (`_gfx_frame_size()` in `codec_ffsub.c`; accepted range 16–8192 per side).
  Whoever first learns the size writes that line: for external tracks
  `stream_sub_ext.c`, from the size read at detect time (the idx `size:` line,
  or the `.sup`'s first PCS segment); for internal tracks
  `stream_parser_ffmpeg.c`, from FFmpeg's probed `codecpar` width/height. With
  no line, the format's standard plane is used: **1920x1080 for PGS, 720x576
  for VobSub**. The decoded *video's* size is deliberately never used (a
  cropped 1920x800 video still carries 1920x1080 PGS / 720x480 VobSub).
  `open()` opens the engine track with this frame, after `avcodec_open2()`
  succeeds so a decoder that fails to open never leaves an engine track
  behind. See "Graphic subtitle rendering".
- Decoded frames report `frame->width/height` as the larger of that frame and
  the bitmap's right/bottom edge (not a fixed minimum). The decoder's PGS
  context is reopened on flush (the FFmpeg PGS decoder has no flush callback)
  so palettes and objects from the old timeline are discarded.
- For external VobSub/PGS, `_add_menu_entries()` builds the same init data
  (`palette:` for VobSub, `size:` for both, from the file's parsed header) so
  external and embedded bitmap tracks share one code path in `open()`.

PGS has a special end-marker behavior. When ffmpeg returns a bitmap subtitle
with `sub.format == 0` and `sub.num_rects == 0`, native creates a `1x1` empty
bitmap with `duration = 0`. `Source/sub_format_gfx.c`'s `gfx_feed_bitmap()`
treats that zero duration as an end signal — "hide the current subtitle" —
rather than a real frame.

When PGS/DVD timing has no explicit duration, native temporarily pins the end
at `INT_MAX`; `_sub_dur_to_engine()` turns that into the "unbounded" sentinel,
so the cue shows until the next cue or clear packet. Finite-duration VobSub
cues expire on their own (see "Graphic subtitle rendering").

## Native subtitle engine

`Source/sub_engine.c` dispatches to one of three `SUB_FORMAT_BACKEND`
implementations per open track. The vtable (`sub_format.h`) is `open`, `feed`,
`feed_bitmap`, `render_at`, `free_frame`, `resize`, `set_video_box` (optional),
`flush`, `close`, and `get_schedule`:

- **`sub_format_ssa.c`** (`SUB_FMT_SSA`): owns a real `libass` renderer.
  `ssa_open()` registers fonts (custom fonts folder, then container-embedded
  fonts, both before `ass_set_fonts()` so they outrank fontconfig), then calls
  `ass_set_fonts()`; `ssa_feed()` calls `ass_process_data()` once for the full
  script header/styles (or a single `Dialogue:` line) and `ass_process_chunk()`
  per streamed plain-text cue. `sync_styles()` re-applies the current
  `SUB_USER_STYLE` to the `libass` track whenever the style serial changes, so
  a style change takes effect without reopening the track. Event pruning is
  deliberately disabled so backward seeks in fully pre-fed external tracks
  still find their events.
- **`sub_format_srt.c`** (`SUB_FMT_SRT`): a thin wrapper around the SSA
  backend for every plain-text format. `srt_open()` synthesizes a minimal ASS
  header — `PlayResY` 720 with `PlayResX` from the canvas aspect, the user's
  resolved default font, bottom-center alignment — and `srt_feed()` formats each
  cue as an ASS `Dialogue:`-shaped line before handing it to the same SSA
  backend. Every text format ends up as `libass` dialogue lines; only real
  `.ass`/`.ssa` files keep their authentic script. `set_video_box` and
  `get_schedule` are forwarded to the inner SSA backend.
- **`sub_format_gfx.c`** (`SUB_FMT_GFX`): no `libass` involved. Stores the
  last decoded BGRA frame (swizzled to RGBA) with its cue window and hands it
  back on state transitions only. Details under "Graphic subtitle rendering".

### Geometry: canvas and video box

The engine tracks two independent pieces of geometry, both reported by Java:

- **Canvas** (`canvas_w/h`): the pixel size of the subtitle surface, which
  with *use margins* may extend into the top/bottom letterbox bars.
  `sub_engine_surface_resized()`.
- **Video box** (`video_box_x/y/w/h`): where the video itself sits inside that
  canvas. `sub_engine_set_video_box()`. All-zero means "video fills the
  canvas 1:1".

These arrive from Java through two different timing domains (the box
synchronously from layout math, the canvas asynchronously from the
`TextureView` size callback, possibly frames later). So the setters only
**cache and mark `geometry_dirty`** (`SUB_GEOM_CANVAS` / `SUB_GEOM_BOX`);
`sub_engine_poll_and_publish()` is the single place that ever calls
`backend->resize()` / `backend->set_video_box()`, applying whatever is
currently cached as one pair under the same lock hold that renders. The backend
therefore never observes a canvas change paired with a box computed for the old
canvas, and rapid calls coalesce into one apply. `resize()` (the expensive
direction — libass re-wraps every line) runs only when the canvas bit is set;
`set_video_box()` is cheap and runs on any dirty bit. A zero box is resolved to
"fills canvas" at apply time, so the backend never receives a raw zero box
(which would collapse every PGS bitmap to zero size).

Surface attach and detach **invalidate** both the cached canvas and box (a
box measured against a vanished surface can't be trusted) and mark the box
dirty. Java replays the real box right after (`resendVideoBox()`), which
coalesces into the same apply. `open_track()` snapshots the cached canvas/box
for the new backend (falling back to the raw video size when no canvas is known
yet) and marks both bits dirty so a resize/box update that landed during the
slow `backend->open()` still reaches the fresh backend before its first render.

For libass, `Source/ssa_geometry.h` turns canvas + box into libass calls
(`ssa_geom_compute()` / `ssa_geom_apply()`):

- `ass_set_frame_size()` = the whole canvas; `ass_set_margins()` = the bars
  (everything that is not video); `ass_set_use_margins(1)`.
- libass therefore derives PlayRes→pixel scaling from the **video box**, places
  `\pos`/`\move` relative to it, and still lets ordinary bottom/top-aligned
  dialogue sit in the bars.
- Margins are never negative; a canvas/box pair that doesn't fit falls back to
  "video fills canvas".
- Pixel aspect is pinned to 1.0, and storage size (coded video size) is set
  only when its orientation agrees with the box, so border/shadow scaling is
  right without squeezing glyph width in non-native aspect modes.
- `sync_styles()` bakes `content_h` (video box height, for `MarginV`) and the
  canvas short side (for forced font size/outline/shadow) into forced style
  state, so `ssa_apply_geometry()` invalidates the style cache whenever either
  changes, independently.

Forced font size is expressed as "PlayRes-720-normalized": converted to the
script's own PlayRes, then rescaled so the same setting renders at the same
physical size relative to the **canvas short side** (stable across rotation)
rather than the video box height. Position (`MarginV`, `\pos`) stays
video-box-relative. Forced styles also set `ScaledBorderAndShadow = yes` so
outline/shadow scale with the font. In *scale only* mode the engine uses
`ass_set_font_scale()` rather than rescaling PlayRes, so authored `\pos`
coordinates keep their meaning.

### Render thread, publication, and locking

`sub_render_gl.c`'s render thread loops: sample the engine's wake generation,
`sub_engine_poll_and_publish()`, draw if something changed, then
`sub_engine_wait_event()`. It polls even when no EGL surface is attached (3D
mode), so the in-memory frame the CPU blender reads stays current.

- **One lock hold from render to publish.** `poll_and_publish` renders from
  the backend and installs the frame into the renderer in a single `eng->lock`
  hold. `open_track()` swaps the backend, bumps `track_generation`, and clears
  the renderer in one hold too. A frame rendered from track T can therefore
  only be installed while T is still the open track; no stale cue can
  reappear after a track switch.
- **Lock order:** `eng->lock` is always taken before the renderer's `r->lock`.
  Code holding `r->lock` must never call anything that takes `eng->lock`.
- Publication classifies the backend's answer: `NULL`/same pointer → unchanged;
  an empty frame over an already-empty screen → unchanged; empty frame over
  visible content → clear; anything else → new frame. Only real changes bump
  the renderer's `frame_generation`.
- `track_generation` is also the token for the `*_gen()` feed calls.
- `close_track()`/`open_track()` close and free the old backend while holding
  `eng->lock`, so the render thread can never be mid-call into a freed backend.
- `wakeup_generation` is a predicate counter: a wake that lands between the
  render thread sampling it and starting to wait is not lost.
- The wait uses `CLOCK_MONOTONIC`, so system time changes can't stretch or
  collapse a timeout.
- Frames use atomic refcounts (`sub_frame_ref/unref`); the GL thread pins the
  frame it is about to upload so a concurrent clear can't free its pixels.
- The renderer adopts a newly attached window's real EGL size immediately
  instead of waiting for a separate resize callback.

The engine is a single published instance guarded by an acquire/release/
publish/retract registry (`Source/sub_engine_registry.c`), so a `STREAM`
thread holding a `s->sub_engine` snapshot can never be left with a freed
pointer if Java tears the engine down (e.g. on surface destroy) mid-playback.
`nativeDestroy()` blocks — up to a bounded 2-second timeout — until every
outstanding reference has been released before the engine is actually freed.

### Fonts

`sub_engine_set_fonts_dir()` / `sub_engine_set_default_font_name()` take effect
on the **next** `open_track()` (values are snapshotted as owned copies under
the lock). The SSA backend registers every `.ttf/.otf/.ttc` in the folder with
`ass_add_font()` before fontconfig, then container-embedded fonts. Folder
contents are cached in-process (keyed by a directory signature of entry count
plus summed mtime and size) so repeated track opens skip the disk reads. The
default font's real family name is resolved with FreeType
(`font_name_resolve_family()`), not guessed from the filename; plain SRT uses
it in its synthetic header, and `sync_styles()` prefers it over the factory
default family when the user never touched the general font picker.

### Style value conversion (Android → libass)

User style lives in one `SUB_USER_STYLE` (`sub_style.c`), guarded by a mutex.
Every setter bumps a `serial` only when the value actually changes, and
`sync_styles()` re-applies the style to the libass track only when the serial
differs from the last one it applied. The render thread reads it through
`sub_style_snapshot()`, which deep-copies `font_family` (the caller frees it).

Conversions at the boundary:

- **Colors:** Android `0xAARRGGBB` → libass `0xRRGGBBAA`, where the low byte
  is *transparency* (`0x00` = solid, `0xFF` = invisible), i.e.
  `argb_to_libass()` stores `255 - alpha`.
- **Background opacity:** Java passes 0–255 as a 0.0–1.0 float; JNI turns it
  back into an Android alpha byte, and `sub_style_set_bg_opacity()` rewrites
  only the transparency byte of `bg_color` (so `setBackgroundColor()` in Java
  re-sends the opacity afterwards).
- **Outline / shadow width:** floats from Java are truncated to int.
- **Font size:** stored in "PlayRes-720-normalized" points and converted per
  script (see "Geometry: canvas and video box").
- **Vertical margin (physical pixels → libass PlayRes units):** Java computes
  `max(userOffset, bottomObstruction)` in physical screen pixels and sends it
  through `nativeSetVerticalOffset()` as `margin_bottom`. In `sync_styles()` it
  becomes the style's `MarginV` as
  `margin_bottom * PlayResY / content_h`, where `content_h` is the **video
  box height** (libass maps PlayResY onto the box, not the canvas). If
  `content_h` or `PlayResY` is not known yet, the raw pixel value is used as a
  fallback. Only bottom-aligned styles (numpad alignments 1–3) are changed;
  `MarginV` for top-aligned styles is measured from the top and would be
  pushed the wrong way. Because `content_h` can change without the style
  serial changing (rotation, box change), `ssa_apply_geometry()` resets the
  cached serial whenever `content_h` or the canvas short side changes.

### libass API usage

All libass calls are confined to `sub_format_ssa.c` and `ssa_geometry.h`
(SRT wraps the SSA backend, so plain text goes through the same calls):

| Purpose | Call |
| --- | --- |
| Library/renderer/track lifecycle | `ass_library_init`, `ass_renderer_init`, `ass_new_track`, `ass_free_track`, `ass_renderer_done`, `ass_library_done` |
| Diagnostics | `ass_set_message_cb` (logs level < 4, and font-related messages up to level 6) |
| Embedded font extraction | `ass_set_extract_fonts(lib, 1)` |
| Fonts | `ass_add_font` (custom folder, then container fonts), then `ass_set_fonts(…, default_font, ASS_FONTPROVIDER_FONTCONFIG, …)` |
| Script/cue input | `ass_process_codec_private` (header), `ass_process_data` (full script or one `Dialogue:` line), `ass_process_chunk` (timed plain-text cue) |
| Frame geometry | `ass_set_frame_size` (canvas), `ass_set_margins` (bars), `ass_set_use_margins(1)`, `ass_set_pixel_aspect(1.0)`, `ass_set_storage_size` |
| **Font scale** | **`ass_set_font_scale`** |
| Render | `ass_render_frame(renderer, track, pts, &change)` |
| Seek | `ass_flush_events` |

`ass_set_font_scale()` implements **Scale only** override mode. It is called
from `sync_styles()` as
`ass_set_font_scale(r, (!force_all && override_mode == 2 && font_scale > 0) ? font_scale : 1.0)`,
so it resets to 1.0 in every other mode. It replaced an earlier approach that
divided `PlayResX/Y` by the scale: that enlarged text but also rewrote the
coordinate space every `\pos`/`\move`/`\clip` was authored in, so typeset signs
landed in the wrong place. `ass_set_font_scale()` scales fonts only and leaves
PlayRes untouched; by default libass applies it to dialogue-style events and
leaves positioned typesetting at its authored size. Plain-text formats always
run with `force_all`, so scale-only never applies to them.

After a style change that mutates the track's styles, `ssa_render_at()` also
"wiggles" the frame height by one pixel (`ass_set_frame_size(h + 1)` then back)
to flush libass's layout cache; without it the new `MarginV` is not
recalculated.

## JNI bridge for the subtitle engine

`jni/libavosjni/jni_sub_engine.c` is a separate JNI translation unit from
`libavosjni`'s `avos_media_player.c`/`libavos.c`, dedicated entirely to
`SubtitleEngine.java`. Calls flow Java-to-native (control); the single
native-to-Java call is the content-changed push described below.

- **Lifecycle & surface**: `nativeCreate` creates the engine, installs the
  content-changed listener, and publishes it to the registry; `nativeDestroy`
  retracts from the registry first (blocking until every `STREAM` reference is
  released), then destroys the engine (which joins the render thread), and
  only then frees the listener's global ref.
  `nativeSurfaceCreated`/`Changed`/`Destroyed` attach/resize/detach the
  `ANativeWindow` behind the subtitle `TextureView`.
- **Video box**: `nativeSetVideoBox(handle, x, y, w, h)` reports the video's
  on-screen box in canvas pixels (see "Geometry").
- **3D hybrid render bridge**: `nativeSetUIMode` (2D vs SBS/TB) and one pull,
  `nativeFillBitmap(handle, bitmap, lastGeneration, sync, outGeneration[])`.
  It replaces the old `nativeFillBitmap` / `nativeSyncFillBitmap` /
  `nativeGetSubtitleGeneration` trio, whose separate calls let the generation
  Java recorded describe a different frame than the pixels it had just copied.
  It returns one of:
  - `FILL_UNCHANGED` (0): the frame identified by `lastGeneration` is still
    current; bitmap untouched.
  - `FILL_CLEAR` (1): nothing should be showing; bitmap untouched, Java clears
    its own surface.
  - `FILL_FRAME` (2): bitmap was cleared and the current frame blended in.
  - `FILL_ERROR` (-1): bad arguments; nothing decided, Java must record nothing.

  `outGeneration[0]` is set for every non-error result to the generation of
  exactly what that result describes, decided in the same native critical
  section as the pixel copy. `lastGeneration = -1` means "nothing valid posted".
  `sync = true` is the style-change path: it force-wakes the render thread,
  waits (bounded to 50 ms) until it has applied the change, and never answers
  `UNCHANGED`. `sync = false` is the cheap once-per-video-frame pull.
  The `FILL_*` values must match `SUB_FILL_RESULT` in `sub_engine.h`.
- **Content-changed push (native → Java)**: when the player is paused in 3D
  mode, a native-originated change (track switch/close, a new track's first
  cue) would never be pulled. The render thread announces it through
  `on_content_changed()`, which calls `SubtitleEngine.onNativeSubtitleContentChanged()`
  via a global ref and a `jmethodID` resolved in `nativeCreate()` on a Java
  thread (`FindClass` from a native thread only sees the system class loader).
  The render thread attaches to the JVM once and detaches at thread exit
  through a pthread key. The method is `@Keep`; if it is ever stripped, native
  logs a warning and the push is simply disabled (the 3D path falls back to
  pulling on video frames and style changes). Never announced while playing.
- **Style setters**: one native setter per style property (font size/scale/
  family/bold/color, outline color/width, shadow color/width, background
  mode/color/opacity, vertical offset, override mode, fonts folder, default
  font name), each writing into the shared `SUB_STYLE` and calling
  `sub_engine_force_wake()` so the render thread picks up the change on its
  next frame. `nativeSetVerticalOffset` takes **physical screen pixels** and
  casts to int (`sub_style_set_margin_bottom()`); it is not a fraction. The
  conversion to libass units happens later, in `sync_styles()` (see "Style
  value conversion"). Outline and shadow widths are likewise cast to int, and
  colors/opacity are converted from Android conventions on the way in.

## SubtitleManager role

`SubtitleManager` no longer schedules or renders anything — `libass` owns
cue timing and compositing entirely inside the native engine. Its role is:

- **Style persistence/forwarding**: get/set pairs for color, background
  opacity/mode, override mode, font size (pt)/scale/family/bold, outline
  color/width, shadow color/width, background color, custom fonts
  folder/default font name — each setter both stores the value and calls the
  matching `SubtitleEngine` setter. The preference keys are centralized as
  `KEY_*` constants (the strings are on users' devices and must never
  change); `STYLE_KEYS` lists every one, so a reset is "remove `STYLE_KEYS`
  from the preferences and call `restoreStyle()`". `Defaults` reads the
  factory style from `res/values/config.xml` (libass sizes text relative to
  the screen, so one value serves every device); override mode and background
  mode defaults are code constants so they can't drift from the native values.
  `restoreStyle()` applies the saved-or-default style in a fixed order
  (background opacity after background color, since setting the color re-sends
  the opacity); it deliberately does not touch vertical position.
- **Subtitle kind**: see "Subtitle kind" above.
- **Vertical position and obstruction**: `setVerticalPosition(0..255)` maps to
  `screenHeight * pos / 765 + 1` (up to about a third of the screen). The
  effective native offset is `max(userOffset, bottomObstruction)`
  (`applyNativeVerticalOffset()`): the obstruction is the playback controls
  (measured from their real on-screen position via
  `PlayerController.getControlBarClearanceAbove()`, because
  `getControlBarCurrentHeight()` reports 0 on TV and before first layout) or
  the system bottom area (navigation bar / gesture area / bottom cutout),
  whichever reaches higher, measured against the subtitle canvas view itself
  (right in every sizing mode). It is 0 for graphic subtitles. It is transient:
  never stored in the saved position or SharedPreferences, so text returns to
  the user's position when the controls hide. The call is deliberately
  unconditional — an unchanged value is a native no-op (the style serial only
  bumps when the margin changes) and the setter's wake works while paused. It
  fires from every layout/visibility/inset path, including a layout listener on
  the canvas view.
- **3D mode wiring**: `setUIMode()` forwards the mode to
  `SubtitleEngine.setUIMode()`, which is what actually detaches native from
  the subtitle `TextureView` and hands `VideoEffectRenderer`'s UI overlay
  surface the rendering role instead — see "Stereoscopic 3D subtitles" below.
  `SubtitleManager`'s own `setUIExternalSurface()`/`mUiSurface` handling is
  narrower than it looks: in SBS/TB mode it posts a single transparent clear
  frame so `VideoEffectRenderer`'s `SurfaceTexture` queue has a valid buffer
  before the first real subtitle bitmap arrives; outside SBS/TB it correctly
  does nothing further, since `mUiSurface` isn't the subtitle rendering
  target in 2D mode at all.
- **Layout/window management**: positions the `subtitle_layout` (and the
  position-hint spacer) for insets, rotation, and system-UI-visibility changes
  (see "Layout, insets, and bars" below).

Pause, play, and seek no longer need explicit subtitle-scheduler handling —
there is no display thread to suspend/resume/interrupt. `avos_mp_video_pause()`
/ `start()` call `sub_engine_set_paused()`, which wakes the render thread; while
paused it sleeps until an explicit wake, and every wake (a style or geometry
change, a feed, a delay change) still produces one fresh frame at the frozen
clock, so edits are visible immediately while paused. A seek flushes the
engine (`gfx_flush()`, `ass_flush_events()`) and the clock reports "no valid
clock" until playback resumes.

## Text tag translation

Each external text-format parser translates its own dialect into
ASS-compatible plain text (or real `{\...}` override blocks) *before* the cue
reaches `Source/sub_format_srt.c`'s shared tag layer, `srt_text_to_ass()`:

- `subtitle_vtt.c`: `<v Speaker>` → `"Speaker: "` prefix, `<c.class>`/`<lang>`
  unwrapped to plain text; shared `<b>`/`<i>`/`<u>`/`<font>` tags are left
  alone for the shared layer to handle.
- `subtitle_sub.c` (MicroDVD): `{y:i/b/u/s}` → `{\i1}`/`{\b1}`/`{\u1}`/`{\s1}`,
  `{c:$BBGGRR}` → `{\c&HBBGGRR&}`; `{f:...}`/`{s:...}`/`{H:...}` are dropped
  (no reliable ASS equivalent).
- `subtitle_mpl2.c`: a leading `/` (per-line italics marker) →
  `{\i1}...{\i0}`.
- `subtitle_srt.c`/`sub_format_srt.c`: no format-specific markup of its own;
  `srt_text_to_ass()` is the shared layer every other parser's output also
  passes through — it maps `<b>`/`<i>`/`<u>`/`<font color=#RRGGBB>` to ASS
  override tags, passes any already-present `{\...}` block through
  byte-for-byte, and silently drops anything else so no stray `<...>` leaks
  onto the screen as literal text.

All of these parsers force `clean_tags = 0` so styling tags survive to reach
`libass` instead of being stripped, and two-line cues are concatenated with
the ASS `\N` line break rather than being split into separate display lines.
`{\an1}`–`{\an9}` alignment tags and per-cue color/bold/italic/underline are
therefore handled by `libass` itself, not by Java.

Text size, color, outline, shadow, and background are no longer per-cue Java
`TextView` properties — they're global style preferences forwarded through
`SubtitleManager`'s setters to `SubtitleEngine`'s native `SUB_STYLE` (see
"JNI bridge for the subtitle engine" above), applied by `libass` uniformly.

## Graphic subtitle rendering

Bitmap subtitles (PGS/VobSub) go through `Source/sub_format_gfx.c`, the
`SUB_FMT_GFX` backend, with no `libass` involved:

- `gfx_feed_bitmap()` is called from `_feed_bitmap_to_engine()` in
  `stream_subtitle.c` every time `codec_ffsub.c` produces a decoded frame,
  including the PGS zero-rect clear frame. It swizzles BGRA→RGBA into one
  shared, immutable pixel block and stores a single current frame together
  with its **cue window** (`cue_start`, `cue_end`; `cue_end < 0` = unbounded).
  Pixels live in one `malloc` block laid out as `[atomic refcount | pad | pixels]`
  (`SUB_EVENT::pixel_refs`), so frames can share pixels without copying.
- `gfx_render_at()` is polled by the engine. The cue is visible iff
  `cue_start <= pts < cue_end`. A frame is handed out only on a **transition**
  (cue starts, cue expires, geometry changes, new feed, flush), never per poll,
  so an idle poll costs two compares; otherwise it returns `NULL` and the GL
  bypass skips the GPU. Finite-duration VobSub cues therefore expire on their
  own, while PGS (unbounded) shows until the next clear/cue. `gfx_get_schedule()`
  wakes the engine at the cue's start and finite end.
- **Geometry changes** (canvas resize, new video box — rotation, floating
  window, margins toggle) are handled copy-on-write: if the cached frame's
  baked-in canvas/box no longer match the backend's, `gfx_clone_with_geometry()`
  builds a new frame stamped with the current geometry, sharing the same pixel
  block (two small allocations and an atomic increment, even for a full-screen
  bitmap, because it runs under `eng->lock`). The old frame is never mutated;
  the pointer change is what makes the renderer redraw. A `delivered` flag
  avoids handing the renderer a reference it would never release.
- On seek, `gfx_flush()` clears the stored frame so a stale bitmap can't
  reappear.

### Subtitle coordinate frame and scaling

Bitmap x/y/width/height are expressed in the **subtitle stream's own
coordinate frame** (`gfx_stream_w/h`), not the video's and not the canvas's:
PGS uses its PCS plane (usually 1920x1080 even when the video was cropped to
1920x800; 1280x720 and 3840x2160 also occur), VobSub uses the DVD canvas from
the idx `size:` line (e.g. 720x480). `codec_ffsub.c` passes that frame to
`sub_engine_open_track()` in the `video_w/video_h` argument slot for GFX
tracks, which `gfx_open()` stores as `gfx_stream_w/h` (a subtitle size despite
the parameter name — see the caveat in `sub_format.h`).

`gfx_map_to_canvas()` in `sub_render_gl.c` is the single placement rule, shared
by the GL path and the CPU blender used for 3D so they cannot drift apart:

- The subtitle frame is scaled **uniformly** (aspect ratio preserved, never
  stretched) to fit inside a target area, then centered on the video box.
  Whatever it doesn't fill is margin: left/right for a frame narrower than the
  area (a 3:2 DVD frame on a 16:9 picture), top/bottom for a wider one.
- The target area depends on whether the canvas is using the letterbox bars:
  - **Bars in use** (canvas taller than the video box — *use margins* on and
    bars exist): the full canvas. A 1920x1080 PGS frame on a 1920x1080 screen
    with a 1920x800 picture scales at 1.0 and the bottom band lands in the
    black bar. In portrait the scale is set by the screen width and the frame
    is centered on the picture, so the part extending past the picture renders
    just beyond it. The frame is clamped to stay on the canvas.
  - **Otherwise** (option off, or no bars): just the video box. A 1920x1080
    frame in a 1920x800 box scales to about 0.74 — smaller, but not stretched.
- Left/right bars are never used; the canvas is always exactly as wide as the
  box.
- Consequence for anamorphic DVD VobSub: it is not stretched with the
  picture's pixel aspect, so on a 16:9 picture it is about 16% narrower than
  picture-relative size, centered with side margins.
- The frame is assumed centered on the picture (a symmetric crop). Neither the
  container nor the decoder gives a crop offset, so an asymmetric crop would
  shift subtitles by half the difference.

The CPU blender (`blend_frame()`) uses the same mapping with nearest-neighbor
sampling and alpha blending, for the 3D path.

## Stereoscopic 3D subtitles

When a stereoscopic 3D video plays, subtitles must be duplicated into both eye
halves so the left and right eye see matching text. Otherwise the text doubles
or floats at the wrong depth and breaks the 3D effect.

### Mode detection

The 3D mode is auto-detected from the **filename** during library scanning by
`MediaLib/src/com/archos/mediaprovider/video/VideoNameProcessor.java`. It
keyword-matches the name and stores a stereo type in the DB column
`Archos_videoStereo`:

- Top-bottom (`ARCHOS_STEREO_3D_TB`): `tb`, `htb`, `top bot`, `topbot`, `tab`,
  `htab`.
- Side-by-side (`ARCHOS_STEREO_3D_SBS`): `sbs`, `hsbs`, `side by side`,
  `sidebyside`.
- Anaglyph (`ARCHOS_STEREO_3D_ANAGLYPH`): `anaglyph`.
- Generic 3D (`ARCHOS_STEREO_3D_UNKNOWN`): `3d`.

`PlayerActivity` reads the stored value through `mVideoInfo.videoStereo` and maps
it to a `VideoEffect` mode (`SBS_MODE`, `TB_MODE`, `ANAGLYPH_MODE`, or
`NORMAL_2D_MODE`). The user can also override the mode manually from the player's
3D menu.

To test the 3D subtitle path, name a clip something like `movie.sbs.mkv` or
`movie.htb.mkv` with an SRT alongside; it comes up in 3D mode and the subtitle is
split/duplicated per eye-half.

### 2D/3D surface handoff

The switch is driven by `SubtitleEngine.is3DMode()` and its
`TextureView.SurfaceTextureListener` callbacks for the subtitle
`TextureView`, not by `SubtitleManager`:

- In 2D (`!is3DMode()`), `onSurfaceTextureAvailable`/`SizeChanged`/`Destroyed`
  call `nativeSurfaceCreated`/`Changed`/`Destroyed` — native's EGL thread owns
  the subtitle surface directly and renders every frame with no Java
  involvement (`onSurfaceTextureUpdated` is a deliberate no-op: "the native
  OpenGL thread owns the render loop in 2D mode"). Because attach zeroes the
  native video box, `onSurfaceTextureAvailable` and `resyncSurfaceSize()`
  replay it via `resendVideoBox()`.
- Switching *into* 3D, `setUIMode()` proactively calls
  `nativeSurfaceDestroyed()` to shut down that EGL attachment and replays the
  box; switching back to 2D, it calls `nativeSurfaceCreated()` again against
  the retained surface, replays the box unconditionally (its coordinate space
  just changed), and restores the 2D size. In 3D, the subtitle `TextureView`
  is therefore untouched by native — `VideoEffectRenderer`'s separate UI
  overlay surface (`mUiSurface`) is the target instead.

### External GL surface (3D bitmap pull)

When an OpenGL video effect is active (3D stereo merge), the video runs
through `VideoEffectRenderer` with `StereoMergeEffect`/`StereoMergeArchosEffect`,
which merges the two half-frames into the stereo output.
`VideoEffectRenderer.primeThreeDSurface()` registers its UI overlay surface
(`mUiSurface`) with the native engine at init — before any real video frame
necessarily arrives, so a style change made while playback is paused still
has a redraw target. On every `onFrameAvailable()` — if
`SubtitleEngine.is3DMode()` — it calls `draw3DSubtitles()`, which asks native
what is on screen with the single `nativeFillBitmap` pull and acts on the
answer: `UNCHANGED` → do nothing; `CLEAR` → clear the overlay surface;
`FRAME` → draw the pre-blended bitmap into `mUiSurface` for the stereo shader
to composite, duplicating it into both eye-halves as part of that same
composite. The bitmap is the full physical size — no 3D halving.

Correctness details:

- Java records `(Surface, generation)` as a pair, only after a successful
  `unlockCanvasAndPost()`, using the generation native reported for the exact
  pixels it copied. A different Surface, a resized bitmap, or an error
  invalidates it (`-1`), forcing the next pull to produce a real answer. An
  `ERROR` result records nothing and never paints an empty overlay.
- If the overlay buffer hasn't caught up with the view size yet, the bitmap is
  scaled to the buffer so proportions stay right.
- The video box in 3D: the native canvas is the overlay bitmap, which is the
  video's own view (never bar-extended), so `sendVideoBoxLocked()` sends an
  **all-zero box** in 3D ("video fills canvas"), which native resolves against
  whatever canvas is current at apply time. A numeric box would disagree with
  the canvas for the whole gap before the bitmap resizes on the next video
  frame (never, while paused).
- **While paused**, style setters call `redraw3DIfNeeded()` (a `sync = true`
  pull, then `VideoEffectRenderer.wakeDrawLoop()` so the consumer composites
  it), and native-originated changes arrive by the content-changed push, which
  coalesces into one pending main-thread pull (`mPullQueued`).
- `release()` takes `m3DDrawLock`, sets `mReleased`, and only then destroys the
  native engine and recycles the bitmap; every draw path re-checks `mReleased`
  under that lock, so a draw already blocked on it can't touch freed memory.
  The push entry point deliberately does not take that lock (`nativeDestroy`
  joins the very thread calling it). `release()` must be called only after
  `VideoEffectRenderer` has stopped delivering frames (`Player.releasePlayer()`
  calls `stop()`, not just `pause()`).

## Layout, insets, and bars

`SurfaceController` owns the subtitle `TextureView` alongside the two video
surfaces (GL-effect and plain), stacking it above both. `TextureView`s are
opaque by default; `SurfaceController` explicitly calls `setOpaque(false)` on
it in its constructor — skipping this makes Android treat the layer as a
solid black box, which optimizes away the video underneath and stalls/crashes
the hardware decoder. `SurfaceController.setSubtitleTextureCallback()`
registers `SubtitleEngine` (implementing `TextureView.SurfaceTextureListener`)
against it, and — since the surface can already exist by the time the
listener is attached — immediately replays `onSurfaceTextureAvailable()` in
that case rather than waiting for a callback that already fired.

### Subtitle surface sizing

Sizing no longer branches on subtitle category
(`SUBTITLE_CATEGORY_PLAIN_TEXT`/`_ASS`/`_GFX`, from
`SubtitleManager.getLayoutCategory()`, which derives it from the native kind);
the category is still recorded but doesn't affect sizing. A single
`mUseSubMargins` preference (`setSubtitleLayoutMode(category, useMargins)`)
applies uniformly:

- `mUseSubMargins = false`: the subtitle view is tethered exactly like the
  video view (`dcw x dch`, same margins) — clipped to the video's own box.
- `mUseSubMargins = true`: width stays exactly `dcw` (left/right bars are
  never used); height extends to `MATCH_PARENT` (letterbox bars included) **only
  when top/bottom bars actually exist** (`extendVertically`). Otherwise it
  falls back to tethered sizing.
- `willStretchY` is first computed as a naive aspect-vs-screen prediction
  (the STRETCH_XY case needs that), then **recomputed once `dch` has reached
  its final value** (after the format switch, 3D doubling, crop rounding, and
  HDMI remap): `willStretchY = dch < dchMax`. Formats that never leave bars
  (full-screen, stretch, auto crop) therefore don't needlessly extend the
  canvas, and cutout margin isn't double-counted as bar height.
- For ASS/SSA the native side needs the matching canvas: `open_track()` uses
  the reported canvas size (not the raw video size) for every format, so
  `PlayResX`/`PlayResY` are mapped onto the same canvas Java sized. This
  doesn't distort authored layout: PlayRes is a property of the track, and
  libass always scales it to whatever frame it's given.
- HDMI mirroring: the external-display rectangle is mapped back into the
  local mirrored viewport, and `dh`/`dchMax` get the same remap so
  `(dh - dch) / 2` doesn't mix coordinate spaces.
- Floating player mode has separate size handling and does not enable the
  external UI surface path.

### Video box reporting

`SurfaceController` reports where the video sits inside the subtitle canvas
whenever `updateSurface()` recomputes it (rotation, margins toggle, a new
video's aspect ratio) through `VideoBoxListener.onVideoBoxChanged(x, y, w, h)`,
which `SubtitleEngine` implements:

- `x` is always 0 and `w`/`h` are `dcw`/`dch` — the video and subtitle views
  share the same left margin and width in every branch.
- `y` is `getVideoBoxTop()`: **0** when the canvas is tethered (both views get
  the same centering offset and margin, which cancel), or
  `(dh - dch) / 2 + mMarginTop` when extended. The `(dh - dch) / 2` term is the
  FrameLayout's own centering of the video within the taller parent, applied
  before `mMarginTop`; dropping it (an earlier bug) pushed PGS/VobSub bitmaps
  up by half the bar height whenever margins were in use.
- `setVideoBoxListener()` **replays** the last computed box immediately if
  `updateSurface()` has already run, so a late-registered listener doesn't leave
  the native box at its invalidated fallback until some unrelated layout
  change.
- `SubtitleEngine` caches the last box (`mBoxLock` also serializes the native
  call so a replay can't deliver an older box last) and replays it after every
  surface attach, since native invalidates its copy on attach/detach.

### Insets and the position hint

`SubtitleManager` still adds `subtitle_layout` over the player root view and
updates its dimensions when the screen or video surface changes, delegating
inset handling to `MiscUtils.adjustViewLayoutForInsets()`. That pass now only
positions the layout and the position-hint spacer; text subtitles are drawn
natively on the subtitle canvas, so the obstruction that lifts them is computed
separately and fed to the renderer (see "Vertical position and obstruction"
above). Graphic subtitles are never margin-shifted or lifted.

The user's vertical text subtitle position is implemented with
`SubtitleSpacerView` as a UI hint only:

- The setting range is `0..255`.
- It maps to roughly `0..screenHeight/3`.
- `SubtitleSpacerView` displays the temporary position hint baseline while
  the user adjusts vertical position.
- Actual subtitle vertical position is `libass`'s `MarginV`, applied natively
  via `nativeSetVerticalOffset()`, converted from physical pixels using the
  **video box height** (libass maps PlayResY onto the box, not the canvas) and
  only for bottom-aligned styles (alignments 1-3).

## Current quirks and debugging notes

- Overlapping text cues are now composited natively by `libass`, unlike the
  old single-active-cue Java scheduler that truncated the current cue when a
  new one arrived early.
- PGS's real end time is still signalled by a zero-rect/zero-duration "clear"
  packet (or the next cue) rather than a reliable duration — only the consumer
  moved, from Java's `SubtitleManager` to native's `sub_format_gfx.c`.
- VobSub's palette normalization in `codec_ffsub.c` (dominant index → black
  fill, next-most-used → white outline) is a heuristic based on pixel counts
  per decoded rect, not a fixed slot convention — a rip with unusual palette
  usage could normalize incorrectly.
- `gl_subtitle_view` (2D, native-direct) and `mUiSurface` (3D, bitmap-pull)
  are mutually exclusive rendering targets gated by
  `SubtitleEngine.is3DMode()`, not by anything in `SubtitleManager` —
  reasoning about "why isn't native drawing to X" for either surface starts
  there.
- The subtitle `TextureView` must have `setOpaque(false)` called on it —
  `TextureView`s default to opaque, and Android will otherwise treat the
  layer as a solid black box, optimize away the video underneath, and stall
  or crash the hardware decoder.
- The visible text may still differ slightly from the raw source: every
  parser forces `clean_tags = 0` so styling tags survive, but each format's
  own dialect (VTT voice tags, MicroDVD control codes, MPL2's italics marker)
  is translated to ASS before `libass` ever sees it.
- Geometry glitches (text sized or placed for the wrong canvas) usually mean
  the canvas and box were applied out of step or a surface attach just
  invalidated the box. Check `SUB_SURFACE` debug lines (`DBG` with the sub
  debug flag), and `SUB_GFX` lines for bitmap placement.
- Timing bugs: confirm which domain a number is in. Anything below the stream
  layer is RST, undelayed; the delay and (for external text) the ratio are in
  the clock, never in the cue. A cue that "shows twice delayed" usually means
  a caller pre-applied the delay.
- Font changes (folder or default font) apply only on the next track open;
  toggle the track off and on to see them immediately.
- A subtitle that stays on screen after a track switch is a lock-order or
  publication bug until proven otherwise: the old track must be closed and the
  renderer cleared in the same `eng->lock` hold.

Useful entry points:

- Native internal-track classification and engine feed:
  `native/avos/Source/stream_subtitle.c`
- Native ffmpeg subtitle decoding: `native/avos/Source/codec_ffsub.c`
- Native external subtitle discovery, parse workers, and engine feed:
  `native/avos/Source/stream_sub_ext.c`
- External format parsers: `native/avos/Source/subtitle_srt.c`,
  `subtitle_vtt.c`, `subtitle_ssa.c`, `subtitle_sub.c`, `subtitle_mpl2.c`,
  `subtitle_smi.c`, `subtitle_idx.c`, `subtitle_pgs.c`, `subtitle_formats.c`
- Native subtitle engine and format backends: `native/avos/Source/sub_engine.c`,
  `sub_format_srt.c`, `sub_format_ssa.c`, `sub_format_gfx.c`; interface and the
  timing contract: `sub_format.h`; kind mapping: `sub_kind.h`
- libass canvas/video-box geometry: `native/avos/Source/ssa_geometry.h`
- GPU rendering, GFX placement, 3D CPU blender: `native/avos/Source/sub_render_gl.c`
- Engine lifecycle/thread-safety: `native/avos/Source/sub_engine_registry.c`
- Engine clock, track selection, delay/ratio, pause:
  `native/avos/Source/avos_mp_video.c`
- Track metadata fields (including subtitle kind):
  `native/avos/Source/avos_mp_metadata.h`
- JNI bridge for the subtitle engine:
  `native/avos/jni/libavosjni/jni_sub_engine.c` / `jni_sub_engine.h`
- Java engine control surface:
  `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleEngine.java`
- App style/layout forwarding, kind rules, obstruction:
  `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleManager.java`
- Surface ownership, sizing, and video box:
  `Video/src/main/java/com/archos/mediacenter/video/player/SurfaceController.java`
- 3D compositing bridge:
  `Video/src/main/java/com/archos/mediacenter/video/player/VideoEffectRenderer.java`
- 3D filename detection:
  `MediaLib/src/com/archos/mediaprovider/video/VideoNameProcessor.java`
- 3D effect/mode selection:
  `Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java`
- 3D GL stereo merge:
  `Video/src/main/java/com/archos/mediacenter/video/player/StereoMergeArchosEffect.java`
