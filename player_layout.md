# Player layout and window insets specification

Status: design source of truth for the Video player OSD. Scope: phone, tablet,
and TV controller; landscape and portrait; immersive and non-immersive playback.

## 1. Principle

The player is made of separate layers:

1. **Video layer** - may be full-bleed, under system insets and rounded corners.
2. **Scrim layer** - may be full-bleed; it prevents visible black bands. It is
   not the control bar background.
3. **Interactive OSD layer** - must stay inside the safe rectangle: no display
   cutout, status bar, navigation bar, or mandatory system gesture area. For
   now, rounded corners may overlap this layer; see R6.
4. **System UI layer** - status bar, navigation bar, gesture handle. Always
   above the app; controls must not be underneath it.
5. **App action bar layer** - app chrome, not a system inset. It may overlay
   video, but must not cover interactive OSD.

A visible gap is valid only when reserved by a display cutout, system bar,
system gesture area, rounded corner, or the app action bar. Any other gap is a
layout bug.

The design rule is therefore: **the video/scrim may bleed; touchable OSD content
may not**. The control bar background also stops at the safe rectangle. The
volume bar's decorative background may extend through the top system strip, but
its buttons, slider, and touch targets stay inside the safe rectangle. The
control bar sits directly above the bottom system area with no gap and no
underlap.

## 2. Official names

| Informal name | Official Android name | API / source |
|---|---|---|
| Cutout display / notch | **display cutout** | `DisplayCutout`, `WindowInsets.Type.displayCutout()` |
| Round edges | **rounded corners** | `RoundedCorner`, `WindowInsets.getRoundedCorner(...)` |
| Notification area | **status bar** | `WindowInsets.Type.statusBars()` |
| Button bar | **navigation bar** (3-button navigation); **taskbar** on large screens | `WindowInsets.Type.navigationBars()`, `WindowInsets.Type.taskbar()` |
| Gesture area | **system gesture insets** / **mandatory system gestures** | `WindowInsets.Type.systemGestures()`, `WindowInsets.Type.mandatorySystemGestures()` |
| Action bar | **app action bar / app bar** | `?attr/actionBarSize`, `R.id.action_bar_container` |
| Window title bar | **caption bar** | `WindowInsets.Type.captionBar()` |
| Insets | **window insets** | `WindowInsets` |

The expanded notification shade is a system window, not an inset. The status bar
is the notification-related inset.

## 3. Coordinate model

All values are screen pixels. `x` grows right, `y` grows down. Bounds are
half-open: `[left, top] .. [right, bottom)`.

```text
W, H        = screen width and height
cL,cT,cR,cB = display cutout safe insets
sL,sT,sR,sB = system bar insets, excluding the caption bar
gL,gR,gB    = mandatory system gesture insets
nB          = navigation bar height when 3-button navigation is on the bottom
aT,aB       = app action bar top and bottom
r           = rounded corner radius (per corner when precision is needed)
Vw          = player2014_bar_width (volume/control bar thickness)
```

`systemBars()` includes the caption bar in freeform/desktop windows. The app
layout already excludes the caption bar, so use `systemBars() & ~captionBar()`.
Do not add the caption bar twice.

## 4. Inset composition

The OSD content rectangle is:

```text
left   = max(cL, sL)
top    = max(cT, sT)
right  = max(cR, sR)
bottom = max(cB, sB)
```

`bottom` is then extended for the bottom system UI:

```text
bottom = max(cB, sB)
if (controlBarShowing && gestureNavigation && sB == 0):
    bottom = max(bottom, gestureAreaHeight)
else if (navBarOnBottom && navigationBarShowing):
    bottom = max(bottom, navigationBarHeight)
```

The control bar therefore follows the **visible** navigation bar inset rather
than the mandatory gesture inset. The gesture area is only a fallback when no
visible bottom system bar is reported. This avoids reserving the gesture area
twice and keeps the control bar aligned with a visible 3-button bar or gesture
handle.

When the control bar is hidden, the gesture fallback is not added. The volume
bar background uses the same `H - bottom`, so it may pass through the faded
gesture area until the controls are shown again. See open decision 4.

The bars may then use tighter, bar-specific horizontal edges:

```text
volumeBarLeft = left
if (API 31+ && cutoutPathLeft >= 0 && cutoutPathLeft < left):
    volumeBarLeft = max(cutoutPathLeft, sL)

controlBarRight = right
if (API 31+ && cutoutPathRight >= 0 && cutoutPathRight < right):
    controlBarRight = max(cutoutPathRight, sR)
if (API 29+ && rightCutoutBottom above the control-bar top):
    controlBarRight = sR
```

`cutoutPathLeft` follows the actual left cutout path; `cutoutPathRight` follows
the right cutout path. A right-side cutout that ends above the control bar must
not force the control bar to stop short, so the control bar uses the full right
edge in that case.

The values returned to touch handling are the content rectangle
`(left, top, right, bottom)`, not the bar-specific edges. If 3-button navigation
is on the left or right, `sL`/`sR` already carry that space. Do not assume the
navigation bar is always on the bottom.

## 5. Design rules

### R1 - Bar backgrounds and content follow their safe bands

The control bar background and all touchable OSD content (control buttons,
seekbar, volume buttons, slider, thumb) stop at the safe rectangle: no display
cutout, no bottom system area. A right-side cutout that ends above the control
bar does not constrain that bar, so the bar may use the full right edge there.

The volume bar is the one exception at the top: its decorative background may
start at the physical top (`y = 0`) while its buttons and slider are inset by the
top system bar. Its background still stops at the bottom safe edge. The
background must not enter the left/right cutout area; only video/scrim may be
full-bleed around the cutout.

For now, rounded corners are allowed to overlap and clip OSD controls. Do not
add radius-based margins, padding, height changes, or a global shift for them.

### R2 - Volume bar must not overlap the display cutout

```text
volume.left  = volumeBarLeft
volume.right = volume.left + Vw

volumeBarLeft = max(cL, sL)
if (API 31+ && cutoutPathLeft >= 0 && cutoutPathLeft < max(cL, sL)):
    volumeBarLeft = max(cutoutPathLeft, sL)
```

On a landscape phone with a left display cutout, the volume bar cannot be flush
with `x = 0`. The strip between the physical edge and the volume bar is the
cutout safe area; fill it with video/scrim, not with a control background.

The cutout bounding rect and, on API 31+, the cutout path must be respected. The
path may let the volume bar sit closer to the camera cutout than the rectangular
safe inset, but no part of the volume bar or its touch target may intersect the
cutout.

### R3 - No artificial gap between volume bar and control bar

When the volume bar is visible:

```text
control.left = volume.right
```

The landscape layout achieves this with `layout_toEndOf="@id/volume_bar"`; the
control bar's left margin stays `0`.

When it is not visible:

```text
control.left = left = max(cL, sL)
```

There must be no arbitrary margin between them. The only allowed separation is
a real inset. Rounded corners may clip, but do not create a layout gap under the
current policy.

### R4 - Seekbar and control bar sit directly above the bottom system area

```text
control.backgroundBottom = H - bottom
control.contentBottom    = control.backgroundBottom
seek_progress.bottom    <= control.contentBottom
```

The control bar background must not continue below that line, and there must be
no gap between it and the gesture/navigation area. The track, thumb, and touch
target must not enter the gesture area or the navigation bar.

On the reference device this means `control_bar.bottom == 1002` and
`seek_progress.bottom <= 1002`, not `1080` or `1068`.

### R5 - Control bar content geometry

```text
control.backgroundBottom = H - bottom
control.contentBottom    = control.backgroundBottom
control.contentTop       = control.contentBottom - Vw   // landscape
control.left             = volume.right                 // volume visible
control.left             = left                         // volume gone
control.right            = W - controlBarRight
```

`bottom` is the value from section 4. The bar-specific right edge is
`controlBarRight`: normally `max(cR, sR)`, but it follows the right cutout path
when that path is tighter, and it becomes `sR` when a right-side cutout ends
above the control bar.

In landscape the control bar stays `Vw` thick. Do not make it taller to absorb
an inset. Absorb the inset with padding/positioning, not by growing the bar.
Under the current temporary policy, no end padding is added for the
bottom-right rounded corner.

### R6 - Rounded corners may fully clip controls for now

Temporary policy: allow full clipping by rounded corners. Do not compensate at
all. Keep the safe rectangle from the display cutout and system areas, and let
the display shape clip any OSD background or control that enters a corner.

Do not use the corner radius for margins, padding, height, per-corner safe
edges, or a global shift. Do not shorten the volume bar by `2r`. Do not add end
padding for the control bar. Any control may be clipped, not just the bottom-right
corner.

The former `PLAYER_BAR_ROUND_CORNER_SHIFT_FACTOR` experiment has been removed.
Player-bar layout no longer has a configurable corner shift or radius trim; only
the subtitle solver still uses the rounded-corner radius.

Example only; other corners may clip too:

```text
        x=2281      2395 2404
y=957   +-------------+----+
        |             |   /
        |             |  /
y=1002  |-------------+ /      <- control bar bottom
        |              /
y=1080  +-------------+
```

At `y=1002`, the safe right edge would be about `2395`; the control bar reaches
`2404`. Roughly `9px` is clipped here, and that is accepted for now.

Later, if we choose to avoid the corners, compute per-corner safe edges or add
end padding to content. Do not apply one global radius inset to every side.

### R7 - Do not globally shift the controller to dodge insets

Global shift is reserved for subtitle/graphics alignment when the video surface
is larger than the screen. Position OSD per bar and per content rectangle.

The implementation zeroes the controller container's margins, keeps the
controller full-screen, and applies margins/height to the individual bars. If a
future controller is inset, keep OSD chrome at the controller's safe bounds;
only the video/scrim and the volume bar's top background extension may fill
outside them. Do not move the whole OSD into the safe rectangle and leave a dark
band around it.

### R8 - Action bar is app chrome, not a system inset

The app action bar is not `statusBars()`. Do not add its height to system bar
insets. Use its real bottom when it is visible:

```text
osdTop = max(actionBarBottom, cT, sT)   // visible and opaque
osdTop = max(cT, sT)                   // hidden or non-blocking
```

Current code offsets only `play_touch_zone` with `?attr/actionBarSize`; it does
not offset the top of the volume bar. If the action bar is visible above the
volume bar, the top volume button must not sit underneath it.

### R9 - Status bar is the notification area

Reserve `sT` for the status bar. Do not place interactive OSD under it. The
volume bar's decorative background may extend under the status bar, but its
buttons, slider, and touch targets may not. The expanded notification shade is
not part of layout; no app action is required.

### R10 - Navigation bar and gesture navigation are alternatives

Use the actual `navigationBars()` side and height for 3-button navigation. Use
`mandatorySystemGestures()` for the gesture area. In gesture mode the handle is
inside the navigation bar area; in 3-button mode the buttons occupy it.

For the bottom control bar the visible system bar inset is the primary
boundary; the gesture area is the fallback when the system reports no visible
bottom system bar. Do not add the full gesture area on top of a visible
navigation bar.

Left/right back-gesture insets are soft system gestures. Do not create a visible
left/right gap for them. If a control conflicts with a back gesture, use a
system gesture exclusion rect or move only that control inward.

### R11 - Volume bar vertical extent

```text
volume.background.top    = 0
volume.background.bottom = H - bottom
volume.background.height = volume.background.bottom

volume.content.top       = top
volume.content.bottom    = H - bottom
```

The decorative background fills the top system strip so a left-side status bar
does not leave a separate strip above the volume bar. The content is inset by
the top system area.

In landscape the implementation sets the volume bar's `topMargin` to `0`,
`bottomMargin` to `0`, and its height to `controller.height - bottom`. It applies
`paddingTop = top` and `paddingBottom = 0`, so the buttons, slider, and thumb
stay below the status bar. The background stops at the bottom safe edge; in
gesture mode, when the control bar is hidden, the computed `bottom` falls back to
a smaller value and lets the background pass through the faded gesture area.

For portrait it restores `MATCH_PARENT` height, clears the vertical margins, and
clears the vertical padding.

Do not trim the volume bar by the rounded-corner radius. Its horizontal position
already avoids the left rounded corners.

### R12 - Variants

- Phone portrait: no volume bar; `control_bar` is a vertical `wrap_content` bar.
  Place it above the bottom system area; do not assume `Vw` is its height. The
  implementation applies only the bottom margin and clears left/right margins.
- Phone landscape: volume bar on the left, background from the physical top to
  the bottom safe edge; control bar `Vw` thick on the bottom. The control bar
  gets the right margin
  `controlBarRight`; its left edge comes from `layout_toEndOf` when the volume
  bar is visible.
- `sw500dp-notouch` / `sw600dp` / TV: bar thickness and volume-bar placement
  differ by resource variant. Use the variant's `player2014_bar_width`; never
  hard-code `45dp` in code.
- Split view: each controller pane gets its own safe rectangle; do not reuse the
  main pane's geometry.

### R13 - Recompute when visibility or size changes

Re-run OSD geometry when system bar visibility, navigation mode, action bar
visibility, control bar visibility, volume bar visibility, rotation/window size,
or split-view layout changes. The controller's layout-change listener posts
`adjustView()` when its width or height changes. `showControlBar()` and
`showVolumeBar()` also call `adjustView()` when visibility changes. The volume
bar becoming visible changes `control.left`; do not leave a stale margin from
when it was gone.

After a rotation or controller relayout, `attachWindow()` restores the volume
bar with `showVolumeBar(mVolumeBarShowing)` so the volume bar does not disappear
across rotation.

### R14 - Touch zones use content insets

Touch handling uses the content rectangle from section 4, not the raw controller
size.

- **Central play zone** is a circle, not a central rectangle. Its diameter is
  `R.dimen.player_central_tap_zone` (`160dp` on the base phone layout, `200dp`
  for `sw500dp` / `sw600dp`), clamped to half the smaller controller dimension.
  The center is the central play icon's parent when laid out, falling back to
  `mControllerViewLeft`, then the controller. Hit-testing uses screen
  coordinates (`getRawX()` / `getRawY()`) so it matches the visible icon.
- **Double-tap zones** map touch coordinates into the content rectangle by
  subtracting `mContentInsetLeft` / `mContentInsetTop` and using the inset width
  and height. The top two thirds is seek: left third rewinds, right third fast
  forwards. The bottom third is audio speed: left third decreases, right third
  increases. The central horizontal third has no double-tap action.
- Bars and insets are excluded from both zones because the content rectangle
  already removes them.

## 6. Current layout structure

```text
playerControllersContainer (FrameLayout, match_parent)
└── playerControllers (PlayerControlsRelativeLayout, match_parent)
    ├── tv_menu_container / my_recycler_view
    ├── volume_bar (land/tablet only; Vw wide; left/top aligned)
    │   ├── volume_up
    │   ├── volume_level
    │   └── volume_down
    ├── play_touch_zone (toEndOf volume_bar, above control_bar)
    ├── control_bar (bottom aligned; Vw thick)
    │   ├── pause
    │   ├── backward
    │   ├── seek_progress
    │   ├── current time / duration
    │   ├── forward
    │   └── fullscreen / format buttons
    ├── help_overlay
    ├── unlock_instructions
    └── central_play_icon
```

Measured reference device (Pixel 11 Pro XL, landscape, 390 dpi):

| Item | Value |
|---|---:|
| Screen | `2404 x 1080` |
| Display cutout safe left `cL` | `161` |
| Display cutout rect | `[0,496] .. [161,583]` |
| Status bar top `sT` | `59` |
| Bottom gesture area `gB` | `78` |
| Rounded corner radius `r` | `123` |
| `player2014_bar_width` | `45dp`, about `110px` |
| Action bar container | `[0,0] .. [2404,176]` |

The volume bar must not intersect the cutout rect. The seekbar must end at or
above `y = 1002`.

## 7. Previous design rules

The pre-existing controller path called:

```text
adjustLeft = adjustTop = adjustRight = adjustBottom = true
avoidCutoutLeft = avoidCutoutTop = avoidCutoutRight = avoidCutoutBottom = true
avoidRoundEdges = false
applyGlobalShift = false
```

It produced this controller rectangle:

```text
[left, top] .. [right, bottom]
= [max(cL, sL), max(cT, sT)] .. [W - max(cR, sR), H - max(cB, sB, gB)]
= [161, 59] .. [2404, 1002]        // reference device
```

Inside that rectangle:

```text
volume_bar  = [161, 59] .. [271, 1002]
control_bar = [271, 892] .. [2404, 1002]
```

This geometry is safe:

- the volume bar starts at the cutout safe edge, so it does not overlap the
  cutout;
- the control bar starts at the volume bar's right edge, so there is no gap;
- control bar content ends at the gesture-area boundary, so the seekbar is above
  it;
- the volume bar's left edge is beyond the rounded-corner radius, so no radius
  trim is needed.

The visible dark bands in that design came from the video/scrim not filling the
safe strips, not from the safe rectangle being wrong. Extend video/scrim to the
physical edges, but keep the control bar background and all interactive content
at the safe rectangle. The volume bar background may also fill the top strip.

The old generic solver also defined the subtitle behavior:

- `avoidRoundEdges` applies a rounded-corner margin only to subtitles, mainly on
  left/right, to prevent text clipping.
- `applyGlobalShift` aligns the graphics subtitle view when the video surface is
  larger than the screen. It is not an OSD rule.
- `additionalBottomMargin` reserves the control bar height for subtitles.
- `alreadyAppliedBottomMargin` avoids counting a subtitle position margin twice.

## 8. Current implementation and verification

The controller remains edge-to-edge and its geometry is applied per bar:

- controller margins are zero, so the controller is `[0,0] .. [2404,1080]`;
- `volume_bar` uses `volumeBarLeft` for its left edge and extends from `y = 0`
  to `H - bottom`. Its controls are padded down by `top`, so the content begins
  at `y = top`. On the reference geometry that is background
  `[161,0] .. [271,1002]` and content `[161,59] .. [271,1002]`; on API 31+ a
  left cutout path may place the left edge closer to the camera cutout while
  keeping the same width;
- `control_bar` is `[271,892] .. [2404,1002]` on the reference device, flush
  against the volume bar and the bottom system area. Its right margin is
  `controlBarRight`, so a right-side cutout that clears the bottom band leaves
  the bar at the full window edge;
- portrait clears the control bar's left/right margins and applies only its
  bottom margin; the control bar keeps its `wrap_content` height;
- `seek_progress` stays inside the control bar at `[521,904] .. [2029,990]`;
- no rounded-corner shift is applied, so the display shape may clip the bars;
- `showVolumeBar(mVolumeBarShowing)` restores the volume bar after rotation and
  relayout.

Earlier `uiautomator` verification on the reference device confirmed no
volume/cutout overlap, no control/gesture overlap, and no gap between the volume
bar, control bar, and bottom system area.

## 9. Implementation mapping

- `MiscUtils.adjustViewLayoutForInsets(...)`: generic margin solver. Use
  `adjust*` for the edges to move, `avoidCutout*` for cutout-safe insets,
  `avoidRoundEdges` only for subtitles, and `applyGlobalShift` only for graphics
  subtitles.
- `MiscUtils.adjustPlayerBarsForInsets(...)`: OSD-specific solver. It computes
  the content edges from cutout + visible system bars + the bottom fallback,
  derives `volumeBarLeft` / `controlBarRight` from the cutout path and
  right-cutout bottom edge, zeroes the controller margins, applies the per-bar
  insets, and returns the content rectangle.
- `MiscUtils.applyPlayerBarInsets(...)`: applies the computed edges. In
  landscape it sets the control bar's right/bottom margins and, when the volume
  bar is gone, its left margin; it sets the volume bar's left margin and height
  to `controller.height - bottom`, with `topMargin = 0` and `paddingTop = top`,
  so the background reaches the physical top while the controls stay below the
  status bar. In portrait it clears horizontal and volume-bar margins and
  padding and applies only the control bar's bottom margin.
- `MiscUtils.getPlayerBarBottomInset(...)`: uses the visible bottom system bar
  inset, falling back to the gesture area when the control bar is visible and the
  system reports no visible bottom bar; falls back to the navigation bar height
  for bottom 3-button navigation.
- `MiscUtils.setCutoutPathInsets(...)`, `computeCutoutPathLeft(...)`,
  `computeCutoutPathRight(...)`: API 31+ cutout-path edges used to place the
  volume and control bars closer to the actual cutout.
- `MiscUtils.setCutoutRightBottomEdge(...)` and
  `cutoutOverlapsBottomBand(...)`: detect a right-side cutout that ends above the
  control bar so the bar can use the full right edge.
- `MiscUtils.getRoundCornersRadius(...)`: subtitle-only rounded-corner handling.
  Player bars do not apply radius trim.
- `MiscUtils.setCutoutMetrics(...)`: provides `mCutoutLeft/Top/Right/Bottom`
  from `DisplayCutout`.
- `MiscUtils.getGestureAreaHeight(...)`, `getNavigationBarHeight(...)`,
  `getStatusBarHeight(...)`, `getActionBarHeight(...)`: measured system/app
  heights used by the solver.
- `PlayerActivity`: requests short-edge display-cutout layout; the root view
  covers the full screen including the cutout, and cutout metrics are forwarded
  to the surface controller.
- `PlayerController.adjustView()`: calls the OSD solver, stores
  `mContentInsetLeft/Top/Right/Bottom` for touch handling, and recomputes the
  control-bar content height. It must recompute when bar visibility changes.
- `PlayerController.addOnLayoutChangeListener(...)`: posts `adjustView()` when
  the controller's size changes.
- `PlayerController.getControlBarContentHeight()`: preserves the control bar's
  base layout height instead of its inset-adjusted height for subtitle
  clearance.
- `PlayerController.getControlBarClearanceAbove(...)`: subtitle clearance; it
  must continue to see the real control bar top after OSD positioning.
- `PlayerController.showControlBar()` / `showVolumeBar()`: visibility changes
  call `adjustView()`; `showVolumeBar()` also repairs a visibility mismatch after
  rotation.
- `PlayerController.onDoubleTapEvent()`: maps touch coordinates through the
  content insets and applies the top-two-thirds / bottom-third zones.
- `PlayerController.isTouchInCentralZone()`: circular central-play hit region
  anchored to the visible icon.
- `mControlBar2` / `mVolumeBar2`: split-view panes need their own safe
  rectangles.

## 10. Acceptance criteria

For landscape on the reference device:

- `volume_bar.left == volumeBarLeft`; on the reference device that is `161`.
  No part of the volume bar intersects `[0,496]..[161,583]`. On API 31+ the
  cutout path may place the left edge closer to the cutout without intersecting
  it.
- `volume_bar.top == 0`, while `volume_up.top == top`; the slider and
  volume-down button stay above the bottom safe edge.
- `control_bar.bottom == 1002`; `seek_progress.bottom <= 1002`.
- `control_bar.left == volume_bar.right` when the volume bar is visible.
- The control bar's right edge follows `controlBarRight`: the right cutout path
  when it is tighter, or the full right edge when the right cutout ends above the
  control bar.
- While the control bar is visible, no clickable OSD region and no OSD bar
  background enters the bottom system area. No clickable OSD region intersects
  the display cutout.
- Only video/scrim and the volume bar's decorative top background may reach the
  physical screen edges; interactive OSD content stays inside the safe
  rectangle.
- No global controller shift; no gap between the control bar and the bottom
  system area.
- The control bar uses the visible navigation bar inset, with the gesture area as
  a fallback only when no visible bottom system bar is reported.
- Rounded corners may fully clip any OSD control for now; no radius-based trim,
  padding, safe-edge calculation, or shift is applied.
- Portrait places the wrap-content control bar above the bottom system area and
  clears its left/right margins.
- 3-button navigation places the seekbar above the navigation bar.
- Action bar visibility does not cover the top volume button.
- Volume bar visibility survives rotation and controller relayout.
- Central-play taps hit a circular region centered on the visible play icon.
  Double-taps use the inset content rectangle: top two thirds seek, bottom third
  changes audio speed, and the central horizontal third has no double-tap action.
- Split-view panes obey the same rules independently.

## 11. Decided control bar edge

The control bar background and content stop at `H - bottom`. The bar sits
directly above the gesture/navigation area with no gap and no underlap.

```text
DESIRED - CONTROL BAR FLUSH ABOVE BOTTOM SYSTEM AREA

x=0   161  271                                     2404
y=892  +----+------------------------------------------+
       |    |  pause   <<< seekbar >>>   time  icons   |
       |    |  control content (Vw tall)               |
       |    |  control background                      |
y=1002 +----+------------------------------------------+  <- control bar bottom
       |    |  video/scrim                             |
       |    |  gesture area / navigation bar            |
y=1080 +----+------------------------------------------+
            ^ no gap; no control background below 1002
```

The strip below `y=1002` is the gesture/navigation area. It is not a layout gap
and must not be filled by the control bar background.

## 12. Open decisions

1. Should the left cutout strip be filled with video, a scrim, or remain black?
   It must not be filled by a control background that looks like a thicker
   volume bar.
2. Left/right back gestures: avoid them by insetting controls, or keep controls
   at the edge with system gesture exclusion rects?
3. Action bar: should the volume bar start below the action bar, or should the
   action bar be hidden/transparent during OSD? The current `play_touch_zone`
   offset alone does not protect the volume button.
4. When the control bar is hidden, should the volume bar background also stop
   above the gesture area? `getPlayerBarBottomInset()` currently adds the gesture
   fallback only while the control bar is visible.
