# Player layout and window insets specification

Status: design source of truth for the Video player OSD. Scope: phone, tablet,
and TV controller; landscape and portrait; immersive and non-immersive playback.

## 1. Principle

The player is made of separate layers:

1. **Video layer** - may be full-bleed, under system insets and rounded corners.
2. **Scrim layer** - may be full-bleed; it prevents visible black bands. It is
   not the control bar background.
3. **Interactive OSD layer** - must stay inside the safe rectangle: no display
   cutout, status bar, navigation bar, or mandatory system gesture area. The
   control bar also reserves an end inset for the bottom-right rounded corner;
   see R6. Other rounded-corner overlaps may still clip OSD content.
4. **System UI layer** - status bar, navigation bar, gesture handle. Always
   above the app; controls must not be underneath it.
5. **App action bar layer** - app chrome, not a system inset. It may overlay
   video, but must not cover interactive OSD.

A visible gap is valid only when reserved by a display cutout, system bar,
system gesture area, rounded corner, or the app action bar. Any other gap is a
layout bug.

The design rule is therefore: **the video/scrim may bleed; touchable OSD content
may not**. The control bar background also stops at the safe rectangle. The
volume bar's decorative background may extend through the top system strip and
leftward under a left-side display cutout, but its buttons, slider, and touch
targets stay inside the safe rectangle. The control bar sits directly above the
bottom system area with no gap and no underlap.

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

All touchable OSD content (control buttons, seekbar, volume buttons, slider,
thumb) stops at the safe rectangle: no display cutout, no bottom system area.
The control bar background also stops at the safe rectangle. A right-side cutout
that ends above the control bar does not constrain that bar, so the bar may use
the full right edge there.

The volume bar is the exception: its decorative background may start at the
physical top (`y = 0`) and extend to the physical left edge (`x = 0`) while its
buttons and slider are inset by the top system bar and the left cutout. Its
background still stops at the bottom safe edge. The background may enter the
left display-cutout area; touchable content may not. Only video/scrim and the
volume bar background may be full-bleed around the cutout.

The control bar reserves end padding for the bottom-right rounded corner as
described in R6. Other rounded-corner overlaps may still clip OSD content; do
not add global radius-based margins, height changes, or a controller shift.

### R2 - Volume bar content must not overlap the display cutout

```text
volume.backgroundLeft = 0
volume.contentLeft    = volumeBarLeft
volume.contentRight   = volumeBarLeft + Vw
volume.backgroundRight = volume.contentRight

volumeBarLeft = max(cL, sL)
if (API 31+ && cutoutPathLeft >= 0 && cutoutPathLeft < max(cL, sL)):
    volumeBarLeft = max(cutoutPathLeft, sL)
```

On a landscape phone with a left display cutout, the volume bar content cannot
be flush with `x = 0`. Its decorative background may be: the background extends
left under the cutout while a left padding of `volumeBarLeft` keeps the controls
at the safe edge. The strip between the physical edge and the controls is filled
by the volume bar background, video, or scrim as appropriate.

The cutout bounding rect and, on API 31+, the cutout path must be respected. The
path may let the volume bar content sit closer to the camera cutout than the
rectangular safe inset, but no button, slider, thumb, or touch target may
intersect the cutout.

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
a real inset. The control bar's rounded-corner end padding is inside its content
area and does not create a gap between the volume and control bars.

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
control.contentRight     = control.right - controlEndPadding
```

`bottom` is the value from section 4. The bar-specific right edge is
`controlBarRight`: normally `max(cR, sR)`, but it follows the right cutout path
when that path is tighter, and it becomes `sR` when a right-side cutout ends
above the control bar.

In landscape the control bar stays `Vw` thick. Do not make it taller to absorb
an inset. Absorb the inset with padding/positioning, not by growing the bar. The
bar background remains at `control.right`; only its content is inset by
`controlEndPadding`.

### R6 - Control bar end content clears the bottom-right rounded corner

Keep the control bar background at the bar's right edge and move only its
content. Use the bottom-right `RoundedCorner` only; do not use the maximum
radius of all corners and do not shorten the volume bar.

```text
c        = insets.getRoundedCorner(POSITION_BOTTOM_RIGHT)
r        = c.radius
cx, cy   = c.center relative to the root view
barBottom = H - bottom
barRight  = W - controlBarRight
dy        = barBottom - cy

if (0 < dy <= r):
    arcInset = max(0, barRight - (cx + sqrt(r² - dy²)))
    d45      = round(r * (1 - 1/√2))
    controlEndPadding = min(arcInset, d45)
else:
    controlEndPadding = 0
```

`d45` is the horizontal distance from the physical right edge to the 45° point
of the quarter circle. It is the maximum end padding: the trailing icon does not
fill the button's transparent lower-right corner, so aligning the content end
with the 45° point avoids the full-radius gap while keeping the visible icon
clear. The bar background may still be clipped by the display shape.

On the reference device, `r = 123` and `d45 = 36`. With 3-button navigation at
the bottom, the arc would require `123px` to clear the button bounds, but the
policy caps the padding at `36px`. In the tested gesture-navigation layout,
`barBottom = 1021` and the uncapped `arcInset` is `18px`, so the padding is
`18px`.

Apply the padding to the control bar's end only, using
`setPaddingRelative(start, top, controlEndPadding, bottom)` and preserving its
other padding. Reset it to zero in portrait, when `controlBarRight` already
clears the corner, and when the control bar is in a scaled split-view pane that
does not reach the window's right edge.

### R7 - Do not globally shift the controller to dodge insets

Global shift is reserved for subtitle/graphics alignment when the video surface
is larger than the screen. Position OSD per bar and per content rectangle.

The implementation zeroes the controller container's margins, keeps the
controller full-screen, and applies margins/height to the individual bars. If a
future controller is inset, keep OSD chrome at the controller's safe bounds;
only the video/scrim and the volume bar's top/left background extension may fill
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

Do not trim the volume bar by the rounded-corner radius. Its background reaches
the physical left edge; only its content stays at the cutout-safe left edge.

### R12 - Variants

- Phone portrait: no volume bar; `control_bar` is a vertical `wrap_content` bar.
  Place it above the bottom system area; do not assume `Vw` is its height. The
  implementation applies only the bottom margin and clears left/right margins.
- Phone landscape: volume bar on the left, background from the physical top and
  left edge to the bottom safe edge; control bar `Vw` thick on the bottom. The
  control bar gets the right margin
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
    ├── volume_bar (land/tablet only; background from x=0; content Vw wide
    │                starting at volumeBarLeft; left/top aligned)
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
| Volume content left `volumeBarLeft` | `119` |
| Status bar top `sT` | `59` |
| Bottom gesture area `gB` | `78` |
| Rounded corner radius `r` | `123` |
| Control-bar end-padding cap `r(1 - 1/√2)` | `36` |
| `player2014_bar_width` | `45dp`, about `110px` |
| Action bar container | `[0,0] .. [2404,176]` |

The volume bar content must not intersect the cutout rect; its background may
extend under it to the physical left edge. The seekbar must end at or above
`y = 1002`.

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
at the safe rectangle. The volume bar background may also fill the top strip and
extend left under the cutout.

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
- `volume_bar` spans from `x = 0` to `volumeBarLeft + Vw` and from `y = 0` to
  `H - bottom`. Its width is `volumeBarLeft + Vw`, with left padding
  `volumeBarLeft`; its controls are padded down by `top`, so the content begins
  at `(volumeBarLeft, top)`. On the 3-button reference geometry that is
  background `[0,0] .. [229,1080]` and content `[119,59] .. [229,970]`; with
  gesture navigation it is background `[0,0] .. [229,1021]` and content
  `[119,59] .. [229,911]`;
- `control_bar` is `[227,970] .. [2404,1080]` on the reference device, flush
  against the volume bar and the bottom system area. Its right margin is
  `controlBarRight`, so a right-side cutout that clears the bottom band leaves
  the bar at the full window edge;
- the control bar's content end is inset by `controlEndPadding` (`36px` on the
  reference device). The trailing `format` button becomes
  `[2248,970] .. [2368,1080]` and `fullscreen_cutouts` becomes
  `[2123,970] .. [2243,1080]`; the bar background remains
  `[227,970] .. [2404,1080]`;
- portrait clears the control bar's left/right margins and end padding and
  applies only its bottom margin; the control bar keeps its `wrap_content`
  height;
- `seek_progress` stays inside the control bar at `[477,982] .. [1993,1068]`;
- `showVolumeBar(mVolumeBarShowing)` restores the volume bar after rotation and
  relayout.

Earlier `uiautomator` verification on the reference device confirmed no volume
content/cutout overlap, no control/gesture overlap, and no gap between the volume
bar, control bar, and bottom system area. The volume bar background intentionally
extends left under the cutout to `x = 0`; the controls remain at
`volumeBarLeft`.

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
- `MiscUtils.getControlBarEndPadding(...)`: computes the capped bottom-right
  rounded-corner end padding from `RoundedCorner`, `controlBarRight`, and
  `bottom`; returns zero below API 31, when the corner is absent, or when the
  control bar's parent is scaled for split view.
- `MiscUtils.applyPlayerBarInsets(...)`: applies the computed edges. In
  landscape it sets the control bar's right/bottom margins and, when the volume
  bar is gone, its left margin; it sets the control bar's end padding for the
  rounded corner while preserving its start/top/bottom padding; it sets the
  volume bar's left margin to `0`, expands its width by `volumeBarLeft`, adds
  `paddingLeft = volumeBarLeft`, and sets its height to
  `controller.height - bottom`, with `topMargin = 0` and `paddingTop = top`, so
  the background reaches the physical left and top edges while the controls stay
  below the status bar and right of the cutout. In portrait it restores the
  original width, clears horizontal and volume-bar margins/padding, and clears
  the control bar's end padding; it applies only the control bar's bottom margin.
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

- `volume_bar.left == 0`, while its content starts at `volumeBarLeft`; on the
  reference device that is `119`. No button, slider, thumb, or touch target
  intersects `[0,496]..[161,583]`. On API 31+ the cutout path may place the
  content edge closer to the cutout without intersecting it; the background
  still reaches `x = 0`.
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
- Only video/scrim and the volume bar's decorative top/left background may
  reach the physical screen edges; interactive OSD content stays inside the safe
  rectangle.
- No global controller shift; no gap between the control bar and the bottom
  system area.
- The control bar uses the visible navigation bar inset, with the gesture area as
  a fallback only when no visible bottom system bar is reported.
- The control bar background may be clipped by the bottom-right rounded corner,
  but its trailing visible control is clear of the corner. End padding is capped
  at `round(r * (1 - 1/√2))`; no global radius trim or controller shift is
  applied.
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

1. Left/right back gestures: avoid them by insetting controls, or keep controls
   at the edge with system gesture exclusion rects?
2. Action bar: should the volume bar start below the action bar, or should the
   action bar be hidden/transparent during OSD? The current `play_touch_zone`
   offset alone does not protect the volume button.
3. When the control bar is hidden, should the volume bar background also stop
   above the gesture area? `getPlayerBarBottomInset()` currently adds the gesture
   fallback only while the control bar is visible.
