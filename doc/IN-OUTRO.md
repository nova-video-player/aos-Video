# Intro/Outro Auto-Skip — Player Skipping Strategy (Video)

## Overview

The Video module consumes the fused, provider-agnostic `IntroSegments` model
produced by MediaLib and, when the user opts in, **auto-skips** intro / credits
/ outro / preview segments during playback by seeking past them. Recap is
handled specially: it is only skipped while genuinely **binge-watching**.

This document covers the **playback / skipping** half. The **provider query and
fusion** half is documented in `MediaLib/doc/IN-OUTRO.md`.

## User-facing controls

A **single** toggle, **off by default**, exposed in the in-player **Play mode**
UI (TV tile and phone/tablet dialog), persisted in shared prefs:

| Preference key | Default | Label | Effect |
|----------------|---------|-------|--------|
| `introdb_enabled` (`KEY_INTRODB_ENABLED`) | false | "Skip intro/outro" | enables auto-skip of intro/credits/outro/preview, and of recap while binge-watching |

There is **no separate recap toggle**: recap skipping is derived from this one
toggle plus the binge conditions below, so the user has a single control to
understand. The Play mode UI also renders the fetched segment timings (via
`IntroSegments.toSummaryString`) so the user can see what was found.

## Specification

### What gets skipped, and when

- **Standard skip**: when `introdb_enabled` is on, applies to `INTRO`,
  `CREDITS`, `OUTRO`, `PREVIEW`, in that priority order.
- **Recap skip**: applies to `RECAP` **only** and only when **all** of the
  following hold:
  1. `introdb_enabled` is on (the same single toggle), **and**
  2. play mode is `PLAYMODE_BINGE`, **and**
  3. the current episode was reached by **auto-advancing** from the previous
     one (`mArrivedViaBingeTransition`), not by the user manually opening it.

  Rationale: the first episode you deliberately start keeps its recap (you may
  want it); only subsequent episodes auto-played in a binge skip the recap, i.e.
  when you are actually watching several episodes back-to-back.

`RECAP` is deliberately excluded from the standard skip set — recaps are often
wanted, so they are never skipped outside the binge condition above.

### Eligibility for a single segment

A segment is a skip candidate only if:

- its type is in the currently-eligible set (per the two flags above), **and**
- it has a **concrete end** (`endMs != null`); a null end means "end of media"
  and is never used as a seek target (we must never jump to EOF), **and**
- `endMs > currentPosition` (the segment is still ahead), **and**
- it **contains** the current position.

### Overlap merge

Skippable segments can overlap (e.g. an `OUTRO` 42'32''→47'32'' and a `CREDITS`
42'31''→44'45''). To avoid multiple small jumps, once a containing segment is
found its end is **extended** across any other eligible segment that starts at
or before the running end and ends later, chaining until no further extension is
possible. The result is a **single seek to the furthest merged end**.

### End-of-media guard

If the (merged) skip target lands within `AUTO_SKIP_END_MARGIN_MS` (5000 ms) of
the media duration, seeking right to EOF fails ("stream_seek time err") and
breaks playback. In that case the player **completes the video naturally**
(`onCompletion()`) instead of seeking, which cleanly advances to the next
episode (binge/folder) or stops.

### Anti-fight guard

After a skip, the merged end is remembered (`mLastAutoSkippedEndMs`). If the
user deliberately seeks **back** into the same segment, it is not re-skipped
(the same end won't trigger twice in a row).

### User feedback

On each skip a short toast is shown: `Auto-skip: <type>` (type label
translatable). A separate **TRACE-only** debug toast can surface the full fetched
segment summary when tracing is enabled.

## Implementation

### Lifecycle / wiring (`PlayerService`)

| Stage | What happens |
|-------|--------------|
| `onCreate` | `IntroDbManager.init(appContext)`; build the self-rescheduling `mAutoSkipTask` runnable. |
| `onStart` | Reset `mArrivedViaBingeTransition = false`; load `mPlayMode`. |
| prepared / video-db ready | `fetchIntroDbIfNeeded()`; (re)start the periodic tick. |
| tick (every `AUTO_SKIP_INTERVAL` = 1000 ms) | `autoSkipIfNeeded()`. |
| `onCompletion` (auto-advance to next episode) | after `onStart(mIntent)`, set `mArrivedViaBingeTransition = true`. |
| stop / destroy | `removeCallbacks(mAutoSkipTask)`. |

### Fetch (`fetchIntroDbIfNeeded`)

- Skips entirely if the toggle is off (no network unless the feature is enabled).
- Requires scraped metadata; builds an `IntroDbQueryParams` from the scraper
  ids (`buildIntroDbQuery`); fetches once per video URI on a background
  `IntroDbFetch` thread; caches the fused `IntroSegments` in `mIntroSegments`.
- Guards against duplicate/stale fetches via `mIntroDbFetchedUri`; a result is
  dropped if the video changed while the request was in flight.
- On success, posts `onIntroDbReady()` to the frontend so the Play mode UI can
  refresh its displayed timings.

### Tick decision (`autoSkipIfNeeded`)

```
if (!playing) return;
introEnabled = pref(KEY_INTRODB_ENABLED);
if (!introEnabled) return;
// recap rides on the same toggle, gated by the binge conditions
recapEnabled = mPlayMode == PLAYMODE_BINGE && mArrivedViaBingeTransition;

skip = segments.findSkip(position, introEnabled, recapEnabled);   // MediaLib
if (skip == null) return;
if (skip.endMs == mLastAutoSkippedEndMs) return;   // anti-fight
mLastAutoSkippedEndMs = skip.endMs;

if (duration > 0 && skip.endMs >= duration - AUTO_SKIP_END_MARGIN_MS) {
    showAutoSkipToast(skip.type);
    onCompletion();        // end-of-media guard: complete instead of seek
    return;
}
seekTo(skip.endMs);
showAutoSkipToast(skip.type);
```

### Skip lookup (`IntroSegments.findSkip` — MediaLib)

The eligible-type selection and overlap merge live in the model:

```
findSkip(positionMs, includeStandard, includeRecap)
  types = eligibleTypes(includeStandard, includeRecap)
          // standard:        INTRO, CREDITS, OUTRO, PREVIEW
          // recap:           RECAP
          // both:            INTRO, RECAP, CREDITS, OUTRO, PREVIEW
  for type in priority order:
     for segment of that type with concrete end ahead of position, containing position:
        return Skip(type, mergedEnd(segment.endMs, types))   // overlap merge
  return null
```

`mergedEnd` grows the end across overlapping eligible segments (any source/type
in the eligible set) until stable, yielding one jump target.

### Binge-transition flag (`mArrivedViaBingeTransition`)

- Set to **false** on every `onStart` (a manual start of any video).
- Set to **true** in `onCompletion` immediately after auto-advancing into the
  next episode (`onStart(mIntent)` with the next URI).
- Read in `autoSkipIfNeeded` as one of the recap-skip conditions (alongside the
  enabled toggle and `PLAYMODE_BINGE`).

This is what distinguishes "I deliberately opened episode 3" (keep recap) from
"episode 3 started because episode 2 finished while I was binging" (skip recap).

### Play mode UI (`PlayerActivity`)

- **TV**: the Play mode tile (`createPlayerTVMenu`) lists the play modes, then a
  separator, then the single "Skip intro/outro" switch; the fetched segment
  summary is appended as a non-focusable item at the bottom
  (`refreshPlayModeIntroSummary`, refreshed on menu open and on `onIntroDbReady`).
- **Phone/tablet**: the Play mode dialog (`MENU_PLAYMODE_ID`) is a custom
  `LinearLayout` inside a `ScrollView` (`adb.setView`): a `RadioButton` per play
  mode, the single "Skip intro/outro" `Switch`, then the segment-timings summary
  at the bottom.

The toggle only writes its preference; the actual skipping (including the
binge-gated recap) is entirely driven by `autoSkipIfNeeded` reading that pref
and the play-mode/transition state on the next tick.
