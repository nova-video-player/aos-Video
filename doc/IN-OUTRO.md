# Intro/Outro Skip Prompt — Player Strategy (Video)

## Overview

The Video module consumes the fused, provider-agnostic `IntroSegments` model produced by MediaLib. When enabled, Nova displays an interactive, **Netflix-style "Skip <Type>" button** during intro, outro, credits, preview, or binge-watched recap segments. 

Pressing the button (or pressing OK/Center on Android TV D-pad) seeks directly to the end of the segment. The feature is supported in **Fullscreen**, **Floating Window Player**, and **Picture-in-Picture (PiP)** mode.

This document describes the player skipping architecture in the `Video` module. Querying and metadata fusion from providers is documented in `MediaLib/doc/IN-OUTRO.md`.

---

## User-Facing Controls

A single toggle, **off by default**, exposed in the in-player **Play mode** UI (TV tile and phone/tablet dialog), persisted in shared preferences:

| Preference Key | Default | Label | Effect |
|---|---|---|---|
| `introdb_enabled` (`KEY_INTRODB_ENABLED`) | `false` (OFF) | "Skip intro/outro" | Enables the interactive Skip button for detected Intro, Outro, Credits, and Preview segments, plus Recap while binge-watching. |

There is no separate recap toggle; recap skipping is derived from this toggle combined with the binge-watching condition. The Play mode UI also displays fetched segment timestamps (via `IntroSegments.toSummaryString`) so users can inspect detected chapter marks.

---

## Specification & Behavior

### 1. Eligible Segments
When `introdb_enabled` is active, skip prompts are supported for:
- **Standard Segments**: `INTRO`, `OUTRO`, `CREDITS`, `PREVIEW`.
- **Recap (`RECAP`)**: Prompted **only** during binge-watching, i.e.:
  1. Play mode is set to **Binge watching** (`PLAYMODE_BINGE`), and
  2. The current episode was reached by **auto-advancing** from the previous episode (`mArrivedViaBingeTransition`).
  
  *Rationale (same as main repo):* When manually starting an episode, viewers often want the recap. During binge-watching of back-to-back episodes, the recap prompt appears so viewers can skip it.

### 2. Segment Detection & Overlap Merging
- Every second (`AUTO_SKIP_INTERVAL` = 1000 ms), the player checks if the current playback position falls within an eligible segment.
- If overlapping segments exist (e.g. `OUTRO` overlapping with `CREDITS`), they are automatically merged into a single contiguous segment ending at the latest timestamp.

### 3. Seek Target (`AUTO_SKIP_BUFFER_MS = 0`)
- Pressing the skip button seeks directly to the segment end (`targetMs = skip.endMs`).
- The buffer is set to `0 ms` to jump cleanly to the exact start of the post-segment scene without cutting off dialogue or scene transitions.

### 4. End-of-Media Guard (`AUTO_SKIP_END_MARGIN_MS = 5000`)
- If the skip target lands within 5 seconds of the total video duration, seeking to the very end can trigger player errors (`stream_seek time err`).
- The player intercepts this and triggers clean video completion (`onCompletion()`), naturally advancing to the next episode in binge mode or finishing cleanly.

### 5. Prompt Lifecycle & Seek Guards
- **Prompt Appearance**: When playback enters an eligible segment, the button fades in with the appropriate label (`"Skip Intro"`, `"Skip Recap"`, `"Skip Preview"`, or `"Skip Credits"`).
- **Auto-Dismissal**: When playback exits the segment naturally, the button fades out and is hidden.
- **Seeking**:
  - Seeking out of a segment immediately hides the prompt.
  - Seeking back into an unskipped segment displays the prompt again.
  - After a skip action, `mLastAutoSkippedEndMs` prevents duplicate actions on the same boundary.

### 6. User Feedback
- When a skip is executed, a brief toast message appears: `"Skipped: <Type>"` (e.g., `"Skipped: Intro"`).

---

## Multi-Platform & UI Support

### Fullscreen Player (`PlayerActivity`)
- Clean semi-transparent pill button in the lower-right corner.
- Smooth alpha fade-in (250 ms) and fade-out (250 ms) transitions.
- Guarded state flag (`mIsSkipPromptShowing`) to prevent race conditions during rapid segment transitions.

### Android TV & Leanback D-Pad Focus Lifecycle
- **Automatic Focus**: The button requests focus when shown, provided the window has focus and player controls (control bar, TV menu, or dialogs) are not currently displayed. If the control bar is visible, focus remains with the user and transitions to the button when the bar hides.
- **D-Pad Directional Navigation**:
  - `DPAD_LEFT` / `DPAD_RIGHT`: Dedicated to backward/forward seeking (scrubbing); never trapped or stolen for focus navigation.
  - `DPAD_UP`: Passes navigation to open the TV player controls menu.
  - `DPAD_DOWN`: Moves focus to player controls and displays the control bar.
  - `DPAD_CENTER` / `OK`: Activates the button to skip the segment.
  - `BACK`: Dismisses and hides the skip prompt without skipping.
- **Focus Restoration**: When the skip button disappears, focus is cleanly restored to `mPlayerControllerPlaceholder` so TV navigation is never lost.

### Floating Window Player (`FloatingPlayerService`)
- Interactive skip button overlay inside the floating player window (`floating_skip_button_layout.xml`).
- Direct single-tap skip execution while watching in desktop / overlay mode.

### Native Picture-in-Picture (PiP)
- Automatically adapts the on-screen skip button overlay when entering Picture-in-Picture mode (`adjustSkipButtonForPiP`).
- Scales the button into a compact layout with reduced padding, margins, and typography so it remains accessible and unobtrusive directly inside the PiP window.

---

## Implementation Architecture

### `PlayerService`
- Fetches metadata via `IntroDbManager` on playback start if `introdb_enabled` is true.
- Tracks binge-transition lifecycle (`mArrivedViaBingeTransition = true` upon auto-advancing in `onCompletion`, `false` on manual start in `onStart`).
- Runs periodic tick task (`mAutoSkipTask`, 1000 ms) calling `autoSkipIfNeeded()`.
- Emits events via `PlayerFrontend`:
  - `onShowSkipPrompt(IntroSegments.Type type, long targetMs)`
  - `onHideSkipPrompt()`
- Executes seek and end-of-media handling in `executeSkip(type, targetMs)`.

### `PlayerActivity` & `FloatingPlayerService`
- Implement `onShowSkipPrompt` and `onHideSkipPrompt`.
- Bind UI buttons, set string resource `skip_intro_button_format_notimer` (`"Skip %1$s"`), and trigger `PlayerService.executeSkip()`.
