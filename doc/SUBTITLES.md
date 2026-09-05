# Subtitle Display Pipeline

This document describes how Nova displays subtitles today. Subtitles are
decoded, styled, and rendered entirely in native code (`libass` + OpenGL). No
subtitle text or bitmap crosses the JNI boundary into Java. Java's role is
limited to telling the native engine *how* to render — style, position,
surface lifecycle — never *what* to render.

## High-level flow

1. Native AVOS parses subtitle tracks together with the media stream, and
   separately discovers external subtitle files alongside the video.
2. For the selected track, `Source/stream_subtitle.c` (internal/embedded
   tracks) or `Source/stream_sub_ext.c` (external files) feeds the cue data
   directly into the native `SUB_ENGINE` via `sub_engine_feed*()` — a plain C
   function call on the decode thread, not a JNI hop.
3. `Source/sub_engine.c` dispatches to a format backend: `sub_format_srt.c`
   and `sub_format_ssa.c` both wrap `libass`, while `sub_format_gfx.c`
   handles PGS/VobSub bitmap tracks without `libass`.
4. `Source/sub_render_gl.c` rasterizes whatever the active format backend
   returns and draws it onto the video's OpenGL surface.
5. `SubtitleEngine.java` (backed by `jni/libavosjni/jni_sub_engine.c`) is the
   Java control surface: it drives the native engine's lifecycle and pushes
   user style preferences down through JNI setters. It never receives
   subtitle content.
6. `SubtitleManager.java` persists the user's style preferences, forwards
   them to `SubtitleEngine`, and manages the subtitle surface's
   position/insets within the player view hierarchy.

Native owns subtitle *content, timing, and drawing*; Java owns only *style
preferences and surface plumbing*.

## Native subtitle decoding

`Source/stream_subtitle.c` drives subtitle decoding from
`stream_sub_dec_thread()`:

- `_sub_decode()` runs while the stream has a valid subtitle track and is not
  paused, using `s->video_time` as the reference clock and subtracting
  `s->subtitle_offset` before deciding which subtitle is due.
- Internal subtitles use `_get_next_int_sub()`, which classifies the track
  format into one of three cases and feeds the native `SUB_ENGINE` directly
  (`sub_engine_feed*()`, a plain C call — no JNI, no `VIDEO_FRAME` handed to
  Java):
  - **Case 1 — raw passthrough** (`SUB_FORMAT_SSA`, `SUB_FORMAT_TEXT`): the
    demuxed packet is already clean text; `sub_engine_open_track()` opens the
    track with no `sub_dec`, and packets are fed straight to
    `sub_engine_feed()`.
  - **Case 2 — ffdec-then-engine** (`SUB_FORMAT_WEBVTT`, `SUB_FORMAT_MOV_TEXT`):
    `codec_ffsub.c` decodes the binary-wrapped packet to plain text first,
    then that text is fed to the engine as `SUB_FMT_SRT`.
  - **Case 3 — ffdec-then-engine-bitmap** (`SUB_FORMAT_PGS`, `SUB_FORMAT_DVD_GFX`):
    `codec_ffsub.c` decodes to BGRA pixels; `_feed_bitmap_to_engine()`
    uploads them via `sub_engine_feed_bitmap()` for `Source/sub_render_gl.c`
    to upload as a texture.
- External subtitles use `_get_next_ext_sub()` (see `Source/stream_sub_ext.c`
  below).

Subtitle delay and timing ratio are applied in native:

- `avos_mp_video_setsubtitledelay()` calls
  `stream_set_subtitle_offset(delay)` directly.
- `avos_mp_video_setsubtitleratio()` stores a numerator/denominator pair used
  by external subtitle timing conversion.

Track selection is also native (`Source/avos_mp_video.c`):

- `avos_mp_video_setsubtitletrack(track < 0)` calls `sub_engine_close_track()`
  to disable subtitles.
- Valid tracks call `stream_set_subtitle_stream()`, which pauses the stream,
  closes the old decoder, frees the subtitle frame, selects the new track, and
  reseeks near the current time when possible so internal subtitle decoders are
  reinitialized. The engine track itself is opened lazily by
  `stream_subtitle.c` when the first packet on the new track arrives.

External subtitle discovery is refreshed through `stream_check_subtitles()`,
which compares external subtitle files, rebuilds external subtitle state when it
changes, and emits subtitle metadata changes.

## Automatic subtitle-track selection

This section describes Nova's current automatic-selection policy and the
remaining implementation work needed to make forced-track detection reliable.

### Current implementation

`PlayerService` reads the audio-selection preferences, one subtitle preference
(`favSubLang`), and the *Hide subtitles by default* preference. The detailed
audio policy is specified in [AUDIO_TRACK_SELECTION.md](AUDIO_TRACK_SELECTION.md).
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
   track, or none if it is absent.
5. In every other case, Nova scans for a non-forced full subtitle matching
   `favSubLang`, using Chinese title variants and the `default` disposition as
   tie breakers. Forced tracks are not candidates for this full-subtitle scan.
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

For embedded tracks, the FFmpeg `forced` disposition is authoritative. For
external tracks and containers that omit the disposition, a `forced` token in a
normalized title or filename is a fallback heuristic only. Classification must
be retained separately from the localized display name. The display name and
disposition-label rules are specified in [Track Naming Specification](TRACK_NAMING.md);
in particular, `Forced` may be a secondary UI label and is not a language.

### Policy using the existing preferences

No new preference is required. `favSubLang` remains the user's single preferred
full-subtitle language. When it has the same normalized base language as the
current device locale, Nova assumes it is the user's native language: full
subtitles in that language are useful for foreign audio, but not for local audio.

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
   subtitles, not full French subtitles.
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
`subtitle_smi.c`) are bulk-fed into the native engine **once**, at track open,
via `stream_sub_ext_feed_engine()` — SRT/VTT stream cue-by-cue as they're
parsed (`sub_engine_feed_gen()`, gated by a per-track generation token so a
track switch mid-feed can be safely discarded), while SMI/MicroDVD/MPL2 parse
into a list first and are then walked synchronously
(`sub_engine_feed()`). After that initial feed, the engine (`libass`) owns
the entire timeline itself; nothing is polled per video-time from Java or
from this file.

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
- `frame->width`/`frame->height` are set to an original subtitle frame size:
  PGS uses at least `1920x1080`; DVD/VobSub uses at least `720x576`.

PGS has a special end-marker behavior. When ffmpeg returns a bitmap subtitle
with `sub.format == 0` and `sub.num_rects == 0`, native creates a `1x1` empty
bitmap with `duration = 0`. `Source/sub_format_gfx.c`'s `gfx_feed_bitmap()`
treats that zero duration as an end signal — "hide the current subtitle" —
rather than a real frame.

When PGS timing has no explicit duration, native temporarily assigns
`duration = 100000`. The real end is inferred natively from the next
zero-duration packet described above.

## Native subtitle engine

`Source/sub_engine.c` dispatches to one of three `SUB_FORMAT_BACKEND`
implementations per open track:

- **`sub_format_ssa.c`** (`SUB_FMT_SSA`): owns a real `libass` renderer.
  `ssa_open()` calls `ass_set_fonts()`; `ssa_feed()` calls `ass_process_data()`
  once for the full script header/styles and `ass_process_chunk()` per
  streamed dialogue line. `sync_styles()` re-applies the current
  `SUB_USER_STYLE` to the `libass` track on every feed, so a style change
  takes effect without reopening the track.
- **`sub_format_srt.c`** (`SUB_FMT_SRT`): a thin wrapper around the SSA
  backend for every plain-text format. `srt_open()` synthesizes a minimal ASS
  header — aspect-correct `PlayResX`/`PlayResY`, the user's resolved default
  font, bottom-center alignment — and `srt_feed()` formats each cue as an ASS
  `Dialogue:`-shaped line before handing it to the same SSA backend. Every
  text format ends up as `libass` dialogue lines; only real `.ass`/`.ssa`
  files keep their authentic script.
- **`sub_format_gfx.c`** (`SUB_FMT_GFX`): no `libass` involved. Stores
  whatever BGRA-swizzled frame `codec_ffsub.c` last decoded and hands back a
  clone on each `render_at()` poll until the PGS clear signal described above
  arrives.

`Source/sub_render_gl.c` rasterizes whatever the active backend returns and
draws it onto the video's OpenGL surface — normally on its own native render
thread with no Java involvement per frame. A monotonically increasing frame
generation counter (`sub_engine_get_frame_generation()`) lets callers cheaply
detect "nothing changed since last time."

The engine is a single published instance guarded by an acquire/release/
publish/retract registry (`Source/sub_engine_registry.c`), so a `STREAM`
thread holding a `s->sub_engine` snapshot can never be left with a freed
pointer if Java tears the engine down (e.g. on surface destroy) mid-playback.
`nativeDestroy()` blocks — up to a bounded 2-second timeout — until every
outstanding reference has been released before the engine is actually freed.

## JNI bridge for the subtitle engine

`jni/libavosjni/jni_sub_engine.c` is a separate JNI translation unit from
`libavosjni`'s `avos_media_player.c`/`libavos.c`, dedicated entirely to
`SubtitleEngine.java`. Unlike the old event-dispatch model, calls only flow
Java-to-native (control), never native-to-Java (content):

- **Lifecycle & surface**: `nativeCreate`/`nativeDestroy` (wrapping the
  registry publish/retract above `sub_engine_create`/`destroy`), and
  `nativeSurfaceCreated`/`Changed`/`Destroyed`, which attach/resize/detach the
  `ANativeWindow` behind the subtitle `TextureView`.
- **3D hybrid render bridge**: `nativeSetUIMode` (2D vs SBS/TB), and two
  bitmap-pull entry points that both call `sub_engine_fill_bitmap()` into a
  Java `Bitmap` — `nativeFillBitmap` is the cheap version used on every video
  frame, while `nativeSyncFillBitmap` forces a fresh render and blocks
  (bounded) until it's applied, used only for the infrequent "redraw right
  now" path after a style change. `nativeGetSubtitleGeneration` lets Java skip
  redundant redraws when nothing changed since the last pull.
- **Style setters**: one native setter per style property (font size/scale/
  family/bold/color, outline color/width, shadow color/width, background
  mode/color/opacity, vertical offset, override mode, fonts folder, default
  font name), each writing into the shared `SUB_STYLE` and calling
  `sub_engine_force_wake()` so the render thread picks up the change on its
  next frame.

## SubtitleManager role

`SubtitleManager` no longer schedules or renders anything — `libass` owns
cue timing and compositing entirely inside the native engine. Its role is:

- **Style persistence/forwarding**: get/set pairs for color, background
  opacity/mode, override mode, font size (pt)/scale/family/bold, outline
  color/width, shadow color/width, background color, custom fonts
  folder/default font name — each setter both persists the preference and
  calls the matching `SubtitleEngine` setter.
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
- **Layout/window management**: sizes and positions the subtitle surface for
  insets, rotation, and system-UI-visibility changes (see "Layout, insets,
  and bars" below).

Pause, play, and seek no longer need explicit subtitle-scheduler handling —
there is no display thread to suspend/resume/interrupt; the native engine
renders whatever `libass`'s internal clock says is current for the position
`Player` is driving.

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
  including the PGS zero-rect clear frame described above. It swizzles
  BGRA→RGBA (`codec_ffsub.c` always produces BGRA) and stores a single
  current frame, replacing whatever was stored before.
- `gfx_render_at()` is polled by `Source/sub_render_gl.c` every frame. There
  is no per-frame re-render for bitmap subtitles — it hands back a clone of
  whatever `codec_ffsub.c` last decoded (or an empty frame on the clear
  signal) until the next feed. PGS/VobSub durations aren't reliable, so this
  deliberately shows until cleared rather than doing a time-window check.
- On seek, `gfx_flush()` clears the stored frame so a stale bitmap can't
  reappear.

Scaling and positioning are handled by `sub_render_gl.c` against the native
subtitle frame's original bounding box and dimensions
(`frame->window`/`frame->width`/`frame->height`, set in `codec_ffsub.c` as
described above) — the same original-coordinates-preserved concern the old
Java `SubtitleGfxView` handled, now done natively as part of the GL draw.

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
  OpenGL thread owns the render loop in 2D mode").
- Switching *into* 3D, `setUIMode()` proactively calls
  `nativeSurfaceDestroyed()` to shut down that EGL attachment; switching back
  to 2D, it calls `nativeSurfaceCreated()` again against the retained
  surface. In 3D, the subtitle `TextureView` is therefore untouched by
  native — `VideoEffectRenderer`'s separate UI overlay surface (`mUiSurface`)
  is the target instead.

### External GL surface (3D bitmap pull)

When an OpenGL video effect is active (3D stereo merge), the video runs
through `VideoEffectRenderer` with `StereoMergeEffect`/`StereoMergeArchosEffect`,
which merges the two half-frames into the stereo output.
`VideoEffectRenderer.primeThreeDSurface()` registers its UI overlay surface
(`mUiSurface`) with the native engine at init — before any real video frame
necessarily arrives, so a style change made while playback is paused still
has a redraw target. On every `onFrameAvailable()` — if
`SubtitleEngine.is3DMode()` — it calls `draw3DSubtitles()`, which pulls a
freshly rendered subtitle bitmap from native (`nativeFillBitmap`/
`nativeSyncFillBitmap`) and blits it into `mUiSurface` for the stereo shader
to composite alongside the video, duplicating it into both eye-halves as
part of that same composite. A separate `wakeDrawLoop()` path handles the
case where a style change needs to be shown while video is paused and
`onFrameAvailable()` isn't firing.

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

`SubtitleManager` still adds `subtitle_layout` over the player root view and
updates its dimensions when the screen or video surface changes, delegating
inset handling to `MiscUtils.adjustViewLayoutForInsets()`. Sizing no longer
branches on subtitle category (`SUBTITLE_CATEGORY_PLAIN_TEXT`/`_ASS`/`_GFX`,
tracked via `SubtitleManager.setSubtitleIsGfx()`): a single `mUseSubMargins`
user preference controls whether the subtitle layer is allowed to extend into
top/bottom letterbox bars, uniformly across all three categories. Left/right
bars are never used regardless of this preference. When enabled for an
ASS/SSA track, the matching native-side change is required too —
`sub_engine_open_track()`'s frame size must widen to the same canvas, or
`libass`'s `PlayResX`/`PlayResY` scale is computed against the smaller
video-only box while actually drawing into the larger one. Floating player
mode has separate size handling and does not enable the external UI surface
path.

The user's vertical text subtitle position is still implemented with
`SubtitleSpacerView`:

- The setting range is `0..255`.
- It maps to roughly `0..screenHeight/3`.
- `SubtitleSpacerView` also displays the temporary position hint baseline while
  the user adjusts vertical position.
- Actual subtitle vertical position is `libass`'s `MarginV`, applied natively
  via `nativeSetVerticalOffset()` — `SubtitleSpacerView` is a UI hint only,
  not the positioning mechanism itself.

## Current quirks and debugging notes

- Overlapping text cues are now composited natively by `libass`, unlike the
  old single-active-cue Java scheduler that truncated the current cue when a
  new one arrived early.
- PGS's real end time is still signalled by a zero-rect/zero-duration "clear"
  packet rather than a reliable duration — only the consumer moved, from
  Java's `SubtitleManager` to native's `sub_format_gfx.c`.
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

Useful entry points:

- Native internal-track classification and engine feed:
  `native/avos/Source/stream_subtitle.c`
- Native ffmpeg subtitle decoding: `native/avos/Source/codec_ffsub.c`
- Native external subtitle discovery and engine feed:
  `native/avos/Source/stream_sub_ext.c`
- External format parsers: `native/avos/Source/subtitle_srt.c`,
  `subtitle_vtt.c`, `subtitle_ssa.c`, `subtitle_sub.c`, `subtitle_mpl2.c`,
  `subtitle_smi.c`, `subtitle_idx.c`, `subtitle_pgs.c`, `subtitle_formats.c`
- Native subtitle engine and format backends: `native/avos/Source/sub_engine.c`,
  `sub_format_srt.c`, `sub_format_ssa.c`, `sub_format_gfx.c`
- GPU rendering: `native/avos/Source/sub_render_gl.c`
- Engine lifecycle/thread-safety: `native/avos/Source/sub_engine_registry.c`
- Track selection, delay: `native/avos/Source/avos_mp_video.c`
- JNI bridge for the subtitle engine:
  `native/avos/jni/libavosjni/jni_sub_engine.c`
- Java engine control surface:
  `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleEngine.java`
- App style/layout forwarding:
  `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleManager.java`
- Surface ownership and sizing:
  `Video/src/main/java/com/archos/mediacenter/video/player/SurfaceController.java`
- 3D compositing bridge:
  `Video/src/main/java/com/archos/mediacenter/video/player/VideoEffectRenderer.java`
- 3D filename detection:
  `MediaLib/src/com/archos/mediaprovider/video/VideoNameProcessor.java`
- 3D effect/mode selection:
  `Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java`
- 3D GL stereo merge:
  `Video/src/main/java/com/archos/mediacenter/video/player/StereoMergeArchosEffect.java`
