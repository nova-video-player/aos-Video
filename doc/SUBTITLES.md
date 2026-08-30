# Subtitle Display Pipeline

This document describes how Nova displays subtitles today. The pipeline is split
between native AVOS subtitle decoding, the `MediaLib` JNI bridge, and the `Video`
player UI.

## High-level flow

1. Native AVOS parses subtitle tracks together with the media stream.
2. The native subtitle thread decodes the selected subtitle track into a
   `VIDEO_FRAME`.
3. `Source/avos_mp_video.c` receives `STREAM_SUBTITLE_CHANGED`, converts the
   current subtitle frame to an AVOS message, and sends `MEDIA_SUBTITLE`.
4. `jni/libavosjni/avos_media_player.c` converts the native AVOS message to a
   Java `com.archos.medialib.Subtitle`.
5. `MediaLib` dispatches the event through `IMediaPlayer.OnSubtitleListener`.
6. `Video` forwards the subtitle event to `SubtitleManager`.
7. `SubtitleManager` schedules display/removal, then renders either text through
   `Subtitle3DTextView`/`SubtitleTextView` or graphics through `SubtitleGfxView`.

The important consequence is that AVOS decodes and timestamps subtitles, but the
Android UI owns final display, positioning, color, font size, background, and
visibility.

## Native subtitle decoding

The active subtitle is stored in `STREAM.subtitle`, with decoded output stored in
`STREAM.subtitle_frame`.

`Source/stream_subtitle.c` drives subtitle decoding from
`stream_sub_dec_thread()`:

- `_sub_decode()` runs while the stream has a valid subtitle track and is not
  paused.
- It uses `s->video_time` as the reference clock.
- It subtracts `s->subtitle_offset` before deciding which subtitle is due.
- Internal subtitles use `_get_next_int_sub()`.
- External subtitles use `_get_next_ext_sub()`.
- When a subtitle frame is produced, `_output_sub()` adds
  `s->subtitle_offset` back to `frame->time` and emits
  `STREAM_SUBTITLE_CHANGED`.

Subtitle delay and timing ratio are applied in native:

- `avos_mp_video_setsubtitledelay()` calls
  `stream_set_subtitle_offset(delay + SUBTITLE_SEND_OFFSET)`.
- `SUBTITLE_SEND_OFFSET` is `-100`, so subtitles are sent about 100 ms early to
  compensate for app/UI delivery latency.
- `avos_mp_video_setsubtitleratio()` stores a numerator/denominator pair used by
  external subtitle timing conversion.

Track selection is also native:

- `avos_mp_video_setsubtitletrack(track < 0)` disables subtitle event sending by
  setting `video->send_sub = 0`.
- Valid tracks call `stream_set_subtitle_stream()`, which pauses the stream,
  closes the old decoder, frees the subtitle frame, selects the new track, and
  reseeks near the current time when possible so internal subtitle decoders are
  reinitialized.

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

External text subtitles are handled by `Source/stream_sub_ext.c`:

- The converted subtitle list is searched for the first cue active at the
  current video time.
- `top` and `bottom` text lines are joined into `frame->data[0]`; if both exist,
  they are separated with the literal characters `\n`.
- `frame->time` is set to the subtitle start time and `frame->duration` to
  `end - start`.

`Source/codec_textsub.c` decodes simple text payloads in the format
`start:end,text`, then fills `frame->data[0]`, `frame->time`, and
`frame->duration`.

`Source/codec_ffsub.c` is used for embedded ffmpeg-supported text formats such
as `TEXT`, `MOV_TEXT`, `SSA`, and `ASS`.

- Plain `rect->text` is copied into the frame.
- `rect->ass` is parsed to recover timing and text. The code handles both older
  ffmpeg `Dialogue:` output and newer comma-separated ffmpeg output.
- `\N` in ASS text is converted to a newline before Java receives it.
- If ffmpeg does not provide display timing, the decoder falls back to parsing
  timing out of the raw subtitle data.

External SSA parsing also exists in `Source/subtitle_ssa.c`. It reads the
`[Events]` format, extracts `Start`, `End`, and `Text`, splits text on `\N`, and
preserves style tags in the text. Java performs the final lightweight style
cleanup/conversion.

### Bitmap

Bitmap subtitles are decoded through `Source/codec_ffsub.c`, mainly for
VobSub/DVD and PGS:

- ffmpeg subtitle rectangles are merged into one bounding box.
- The bitmap data is converted from PAL8 to BGRA.
- For VobSub with a four-color palette, the code swaps the semi-transparent
  black and white palette entries before conversion.
- `frame->window` stores the subtitle bitmap bounding box.
- `frame->width`/`frame->height` are set to an original subtitle frame size:
  PGS uses at least `1920x1080`; DVD/VobSub uses at least `720x576`.

PGS has a special end-marker behavior. When ffmpeg returns a bitmap subtitle
with `sub.format == 0` and `sub.num_rects == 0`, native creates a `1x1` empty
bitmap with `duration = 0`. This cannot use `-1`, because Java would treat that
as an untimed subtitle. `SubtitleManager` interprets a zero-duration next
subtitle as an end signal for the previous bitmap subtitle.

When PGS timing has no explicit duration, native temporarily assigns
`duration = 100000`. The real end is then inferred on the Java side when the
next zero-duration subtitle arrives.

## Native-to-Java bridge

`Source/avos_mp_video.c::send_subtitle()` converts `STREAM.subtitle_frame` into
an AVOS message:

- Text tracks call `avos_msg_new_text_subtitle(0, sub_time, duration, text)`.
- Graphic tracks call
  `avos_msg_new_bitmap_subtitle(0, sub_time, duration, (IMAGE *)sub_frame)`.
- `sub_time` is computed as `sub_frame->time - SUBTITLE_SEND_OFFSET`, undoing
  the `-100 ms` send offset before the Java subtitle object is created.

`Source/avos_common.c` stores the transport payload:

- Text messages contain `position`, `duration`, and UTF-8 text.
- Bitmap messages contain `position`, `duration`, bitmap left/top coordinates,
  original frame dimensions, and packed bitmap pixels.

`jni/libavosjni/avos_media_player.c` runs an event thread for AVOS events. For
subtitle messages it creates Java objects through static factory methods on
`com.archos.medialib.Subtitle`:

- `createTimedTextSubtitle(position, duration, text)` returns
  `Subtitle.TimedTextSubtitle`.
- `createTimedBitmapSubtitle(position, duration, left, top, originalWidth,
  originalHeight, bitmap)` returns `Subtitle.TimedBitmapSubtitle`.

The bitmap bridge uses `create_bitmap()` to create an Android `Bitmap` from the
native pixel buffer before constructing the Java subtitle object.

## MediaLib event dispatch

`AvosMediaPlayer` receives native events through
`postEventFromNative(Object mediaplayer_ref, int what, int arg1, int arg2,
Object obj)`. It posts them to its `EventHandler`.

`MEDIA_SUBTITLE` is `1000`. The event handler verifies that `msg.obj` is a
`Subtitle` and calls `mOnSubtitleListener.onSubtitle(mMediaPlayer, subtitle)`.

The app registers listeners in this chain:

- `Player.openVideo()` calls `mMediaPlayer.setOnSubtitleListener(this)`.
- `Player.onSubtitle()` forwards to `Player.Listener`.
- `PlayerService.onSubtitle()` forwards to the current frontend.
- `PlayerActivity` and `FloatingPlayerService` call
  `SubtitleManager.addSubtitle(subtitle)`.

## Java subtitle model

`MediaLib/src/com/archos/medialib/Subtitle.java` defines three subtitle shapes:

- `TextSubtitle`: untimed text, with `duration == -1`.
- `TimedTextSubtitle`: timed text, with position, duration, and text.
- `TimedBitmapSubtitle`: timed bitmap, with position, duration, bitmap,
  original frame size, and bounds.

`Subtitle.isTimed()` returns true for timed text and timed bitmap subtitles only
when `duration != -1`. `duration == 0` is still timed and is used by bitmap
end markers.

`TimedBitmapSubtitle` builds `bounds` from the native left/top corner plus the
actual Android bitmap width and height. The original native subtitle frame size
is available separately through `getFrameWidth()` and `getFrameHeight()`.

## SubtitleManager scheduling

`SubtitleManager` owns the Java display lifecycle. It attaches
`R.layout.subtitle_layout`, finds:

- `SubtitleSpacerView`
- `SubtitleGfxView`
- `Subtitle3DTextView`

Then it starts one `DispSubtitleThread`.

The display thread is deliberately separate from the main looper:

- `addSubtitle()` stores the new subtitle as `mNextSubtitle`.
- Timed subtitles start or interrupt the thread.
- Untimed text subtitles replace the currently displayed subtitle immediately.
- UI work is posted back to the main looper through `SubtitleHandler`.

For timed subtitles:

- A subtitle with `duration > 0` becomes `mCurrentSubtitle` and is displayed.
- The thread sleeps for the remaining subtitle display duration.
- If another subtitle arrives before the current one expires, the thread wakes
  up and recomputes the remaining time.
- If the next subtitle starts before the current one ends, the current subtitle
  duration is shortened so the next cue can take over cleanly.
- If the next subtitle has `duration == 0`, it is treated as an empty/end cue
  and is discarded after shortening/removing the current subtitle.
- When no display time remains, `removeSubtitle()` clears the current UI view.

`DispSubtitleThread` is a single-active-cue scheduler. It keeps only
`mCurrentSubtitle` and `mNextSubtitle`; when `mNextSubtitle` starts before the
current subtitle would naturally end, the current subtitle is shortened rather
than composited with the new one. Overlapping SRT/ASS cues are therefore not
displayed simultaneously in the current design.

Pause, play, and seek are handled by controlling this thread:

- `onPause()` suspends it.
- `onPlay()` resumes it.
- `onSeekStart()` clears any displayed subtitle and interrupts the thread.

## Text subtitle rendering

Text subtitles are displayed through `Subtitle3DTextView`, which wraps one or
two `SubtitleTextView` instances:

- Normal UI mode uses only the primary text view.
- Side-by-side and top-bottom 3D modes enable the secondary text view and split
  layout horizontally or vertically.

Before drawing, `SubtitleManager.displayView()`:

1. Switches from graphic layout to text layout when needed.
2. Extracts alignment from SubRip/SSA-style `{\an1}` through `{\an9}` tags.
3. Converts the alignment to both TextView position gravity and multiline text
   justification.
4. Cleans and lightly converts subtitle text.
5. Runs the result through `HtmlCompat.fromHtml()`.
6. Applies a `TextShadowSpan` to the entire resulting spannable.
7. Sets the text on `Subtitle3DTextView`.

Text cleanup currently includes:

- Trimming leading/trailing whitespace.
- Converting real newlines and literal `\n` to `<br />`.
- Recovering some concatenated SRT lines by inserting a break between sentence
  punctuation and an immediately following capital letter.
- Converting WebVTT voice tags such as `<v Bob>` into bold speaker prefixes.
- Converting a limited subset of SSA/ASS tags to HTML:
  color, bold, italic, underline, and strikethrough.
- Removing remaining SSA/ASS tags.

`SubtitleTextView` handles the actual drawing:

- Text line spacing is set to `1.15`.
- Optional per-line rounded black background rectangles are drawn behind text.
- Optional outline drawing exists but is disabled by default because it is too
  slow on low-end devices.
- Text can be drawn either into the normal view canvas or into an external UI
  `Surface`.

Text size is controlled by `SubtitleManager.setSize(size)`:

- The persisted range is `0..100`.
- It maps to `TextView.setTextSize()` from `16sp` to `64sp`.

Text color, outline, background visibility, and background opacity are set by
`SubtitleManager` and persisted from the player subtitle settings UI.

## Graphic subtitle rendering

Bitmap subtitles are displayed through `SubtitleGfxView`.

`SubtitleManager.displayView()` switches to graphic mode and calls:

```java
mSubtitleGfxView.setSubtitle(
    subtitle.getBitmap(),
    subtitle.getBounds(),
    subtitle.getFrameWidth(),
    subtitle.getFrameHeight()
);
```

`SubtitleGfxView.RECT_COORDINATES` is currently `true`. That means bitmap
subtitle file coordinates are preserved as much as possible:

- The native subtitle frame coordinates are scaled to the current video surface.
- The bitmap is drawn at the scaled original left/top bounds.
- The user's vertical subtitle position setting is ignored for graphic subtitles
  in this mode.

The target dimensions are normally the current `Player` surface controller
width and height. Floating player mode uses the display dimensions known to the
subtitle view.

Scaling uses the original subtitle frame aspect ratio and the video surface
aspect ratio:

- If the subtitle frame is wider than the surface, scaling fills the target
  width and centers vertically.
- Otherwise scaling fills the target height and centers horizontally.
- Stretch/aspect-ratio modes are accounted for by comparing the surface size to
  the decoded video size.

`SubtitleGfxView.onDraw()` draws the Android bitmap from its full source rect to
the scaled subtitle bounds. Like text rendering, it can draw into either the
normal view canvas or an external UI `Surface`.

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

### Text duplication

`Subtitle3DTextView` wraps a primary and a secondary `SubtitleTextView`.
`setUIMode()` reads the active mode:

- `SBS_MODE` splits the layout horizontally so each text view gets half the
  width.
- `TB_MODE` splits the layout vertically so each text view gets half the height.

The same cue is drawn into both views, positioned through `setGravity3D()`.
Normal 2D playback uses only the primary view.

### External GL surface

When an OpenGL video effect is active (3D stereo merge), the video runs through
`VideoEffectRenderer` with `StereoMergeEffect`/`StereoMergeArchosEffect`, which
merges the two half-frames into the stereo output. In this mode `Player` calls
`setUIExternalSurface(mUISurface)`, handing the subtitle views a `Surface` owned
by the GL renderer instead of the normal view hierarchy.

`SubtitleTextView.setRenderingSurface()` then draws via `Surface.lockCanvas()` /
`unlockCanvasAndPost()` directly onto that GL surface, so the subtitle pixels are
composited and warped by the same stereo shader as the video. This external
surface path is the part most coupled to the custom subtitle views: it draws into
an arbitrary `Surface` rather than the view's own canvas.

## Layout, insets, and bars

`SubtitleManager` adds `subtitle_layout` over the player root view and updates
its dimensions when the screen or video surface changes.

`adjustView()` delegates inset handling to
`MiscUtils.adjustViewLayoutForInsets()`:

- Text subtitles avoid the player control bar, status/navigation bars, gesture
  area, and optionally the display cutout.
- Graphic subtitles keep their native rectangle layout and avoid fewer UI
  offsets; they can also use a surface-controller-sized layout.
- Floating player mode has separate size handling and does not enable the
  external UI surface path.

The user's vertical text subtitle position is implemented with
`SubtitleSpacerView`:

- The setting range is `0..255`.
- It maps to roughly `0..screenHeight/3`.
- `SubtitleSpacerView` also displays the temporary position hint baseline while
  the user adjusts vertical position.
- In graphic mode with `RECT_COORDINATES == true`, this spacer height is forced
  to zero.

## Current quirks and debugging notes

- `SUBTITLE_SEND_OFFSET` is applied in native to send subtitle events early, then
  undone before building the Java subtitle object. Delay changes add the user
  delay to this offset.
- Java still owns final display duration. This is important for PGS, where
  native may send a long placeholder duration and later a zero-duration empty
  bitmap to end the cue.
- Overlapping text cues are not composited. Java truncates the current timed cue
  when a next cue arrives before it ends, and the native external-subtitle path
  also tracks a single `p->out` cue before advancing.
- Text and bitmap subtitles use different layout rules. Text respects user
  vertical position and UI bars; graphics preserve original subtitle rectangle
  coordinates when `RECT_COORDINATES` is true.
- Switching between text and bitmap subtitles forces a layout adjustment because
  the two modes use different sizing/inset behavior.
- The visible text may differ from native text because Java performs final
  cleanup, HTML conversion, SSA tag removal, WebVTT voice formatting, and shadow
  span application.
- `SubtitleTextView.setVisibility()` and `SubtitleGfxView.setVisibility()` clear
  the external UI surface when one is attached, preventing stale subtitle pixels.

Useful entry points:

- Native stream timing: `native/avos-*/Source/stream_subtitle.c`
- Native ffmpeg subtitle decoding: `native/avos-*/Source/codec_ffsub.c`
- Native external subtitle lookup: `native/avos-*/Source/stream_sub_ext.c`
- Native SSA parser: `native/avos-*/Source/subtitle_ssa.c`
- Native event creation: `native/avos-*/Source/avos_mp_video.c`
- JNI event bridge: `native/avos-*/jni/libavosjni/avos_media_player.c`
- Java subtitle object: `MediaLib/src/com/archos/medialib/Subtitle.java`
- Java event dispatch: `MediaLib/src/com/archos/medialib/AvosMediaPlayer.java`
- App scheduling/rendering: `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleManager.java`
- Text drawing: `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleTextView.java`
- 3D text duplication: `Video/src/main/java/com/archos/mediacenter/video/player/Subtitle3DTextView.java`
- 3D filename detection: `MediaLib/src/com/archos/mediaprovider/video/VideoNameProcessor.java`
- 3D effect/mode selection: `Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java`
- 3D GL stereo merge: `Video/src/main/java/com/archos/mediacenter/video/player/StereoMergeArchosEffect.java`
- Graphic drawing: `Video/src/main/java/com/archos/mediacenter/video/player/SubtitleGfxView.java`
