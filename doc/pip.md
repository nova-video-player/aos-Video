# Picture-in-Picture (PiP) & Floating Player in Nova Video Player

This document details the architecture, compliance requirements, lifecycle management, and debugging procedures for Nova's two distinct compact playback modes: **Native Android Picture-in-Picture (PiP)** and the **Floating Window Player**.

---

## 1. Overview & Dual-Mode Architecture

Nova provides two different mechanisms for continuing video playback while navigating outside the main player activity:

| Feature | **Native Android PiP** | **Floating Window Player** |
| :--- | :--- | :--- |
| **Primary Platform** | Android TV (Leanback) | Phones and Tablets |
| **Implementation** | `PlayerActivity` (`enterPictureInPictureMode`) | `FloatingPlayerService` (`WindowManager` overlay) |
| **Window Type** | System-managed PiP task window | System overlay (`TYPE_APPLICATION_OVERLAY`) |
| **Permissions** | None (platform feature) | `SYSTEM_ALERT_WINDOW` (`Settings.canDrawOverlays()`) |
| **Trigger Point** | TV format menu item (`vPicInPic`) | Overflow / window mode menu (`MENU_WINDOW_MODE`) |
| **Resizing** | OS-controlled (system gestures/D-pad) | Custom pinch-to-zoom gesture in service |
| **Draggability** | OS-controlled (system PiP dock) | Custom single-pointer drag gesture |

```
                       [Playback Initiated]
                                │
                                ▼
                       ┌─────────────────┐
                       │ PlayerActivity  │
                       └────────┬────────┘
                                │
         ┌──────────────────────┴──────────────────────┐
         ▼ (TV Mode: Format Menu)                      ▼ (Phone/Tablet: Window Mode)
┌─────────────────────────────────┐           ┌─────────────────────────────────┐
│     Native Android PiP Mode     │           │     Floating Window Player      │
│  enterPictureInPictureMode()    │           │      FloatingPlayerService      │
├─────────────────────────────────┤           ├─────────────────────────────────┤
│ • System-managed window frame   │           │ • WindowManager overlay         │
│ • SurfaceController.getView()   │           │ • TYPE_APPLICATION_OVERLAY     │
│   bounds for sourceRectHint     │           │ • Absolute TOP | LEFT gravity   │
│ • Clamped aspect ratio (CTS)    │           │ • Custom drag & pinch gestures  │
│ • Sync menu dismissal           │           │ • LTR layout direction locked   │
└─────────────────────────────────┘           └─────────────────────────────────┘
```

---

## 2. Native Android Picture-in-Picture (PiP)

Native PiP is available on Android 7.0 (API 24) and above, and is enhanced with `PictureInPictureParams` in Android 8.0 (API 26+).

### 2.1 Manifest Configuration
`PlayerActivity` is declared in [`AndroidManifest.xml`](file:///Users/marc/Documents/git/nova-publish/Video/AndroidManifest.xml) with:
```xml
<activity
    android:name=".player.PlayerActivity"
    android:supportsPictureInPicture="true"
    android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
    ... />
```
- `android:supportsPictureInPicture="true"`: Grants the activity permission to enter PiP.
- `android:configChanges`: Prevents the activity from being destroyed and recreated when entering or exiting PiP mode.

### 2.2 Entry Parameters (`PictureInPictureParams.Builder`)
When entering PiP in [`PlayerActivity.java`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java), three critical requirements are handled:

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();

    // 1. Aspect Ratio (Clamped to Android CTS bounds)
    if (mPlayer != null && mPlayer.getVideoWidth() > 0 && mPlayer.getVideoHeight() > 0) {
        int vw = mPlayer.getVideoWidth();
        int vh = mPlayer.getVideoHeight();
        double ratio = (double) vw / vh;
        if (ratio >= 1.0 / 2.39 && ratio <= 2.39) {
            builder.setAspectRatio(new Rational(vw, vh));
        }
    }

    // 2. Source Rect Hint (Actual video surface view, not root activity)
    View targetView = (mSurfaceController != null && mSurfaceController.getView() != null)
            ? mSurfaceController.getView() : mRootView;
    if (targetView != null) {
        Rect sourceRectHint = new Rect();
        targetView.getGlobalVisibleRect(sourceRectHint);
        builder.setSourceRectHint(sourceRectHint);
    }

    enterPictureInPictureMode(builder.build());
}
```

1. **Aspect Ratio Clamping**:
   - Android Compatibility Test Suite (CTS) mandates that PiP aspect ratio must fall between `1:2.39` (vertical video) and `2.39:1` (widescreen cinema).
   - Supplying a ratio outside this range throws an `IllegalArgumentException`. Clamping protects against crashes on extreme video dimensions.
2. **Source Rect Hint**:
   - `sourceRectHint` informs the OS of the exact screen rectangle to animate into the PiP window.
   - Targeting the actual video surface ([`mSurfaceController.getView()`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/SurfaceController.java)) avoids letterboxing / black bar zoom glitches that occur when targeting `mRootView`.
3. **Synchronous Overlay Dismissal**:
   - The TV menu is dismissed (`mPlayerController.showTVMenu(false)`) **before** invoking `enterPictureInPictureMode()`, preventing the menu overlay from being frozen into the transition animation snapshot.

### 2.3 Lifecycle in PiP Mode
- **`onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig)`**:
  - Sets `mWasInPictureInPicture = true`.
  - Triggers `updateSizes()` to adjust subtitle scaling (`mSubtitleManager.setSize(...)`) proportionally to the compact window size.
  - Hides popup controllers and menus during PiP.
- **`onPause()` handling**:
  - On Android 7.0–11, activities in PiP mode are in the `PAUSED` state while remaining visible. Playback must **not** pause on `onPause()` when in PiP mode.

---

## 3. Floating Window Player (`FloatingPlayerService`)

The Floating Player provides a movable, resizable video overlay window that floats over any application on phones and tablets.

### 3.1 Overlay Window Setup
In [`FloatingPlayerService.java`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/FloatingPlayerService.java), the overlay window is attached to the system `WindowManager`:
```java
WindowManager.LayoutParams mParamsF = new WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_PHONE
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSPARENT);

mParamsF.gravity = Gravity.TOP | Gravity.LEFT;
mParamsF.x = 0;
mParamsF.y = 100;
```

### 3.2 LTR Layout & Coordinate Space Safety
1. **Absolute `Gravity.TOP | Gravity.LEFT`**:
   - In `WindowManager.LayoutParams`, `gravity` defines the reference origin for `(mParamsF.x, mParamsF.y)`.
   - `Gravity.START` resolves to `Gravity.RIGHT` in RTL locales (Arabic, Hebrew, etc.). With `Gravity.RIGHT`, `mParamsF.x` measures distance from the *right* edge of the screen, inverting touch drag physics (dragging finger right moves window left) and breaking boundary clamping.
   - Therefore, `mParamsF.gravity` is locked to `Gravity.TOP | Gravity.LEFT` across all locales.
2. **Layout Direction Lock**:
   - Media controls (progress scrubbers, volume sliders, button ordering) are universally LTR.
   - Both [`res/layout/floating_player.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/floating_player.xml) (`android:layoutDirection="ltr"`) and `mFloatingPlayerRootView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR)` enforce LTR layout direction regardless of the system locale.

### 3.3 Gestures & Resizing
- **Drag (1 Finger)**:
  - Movement exceeding `MOVE_THRESHOLD` sets `mode = DRAG`.
  - Calculates delta: `x = initialX + (int)(event.getRawX() - initialTouchX)`.
  - Clamps to display bounds: `0 <= x <= size.x - mParamsF.width`.
- **Pinch-to-Zoom (2 Fingers)**:
  - Spacing between pointers adjusts window width: `width = initialWidth + (newDist - oldDist)`.
  - Proportional height calculated using the video's pixel aspect ratio.
  - Enforces bounds: `mFloatingPlayerSize <= width <= displayWidth`.
- **Single Tap**:
  - Toggles the floating player controller bar (play/pause, fullscreen restore, exit, seekbar, volume).
  - Automatically hides after 3 seconds of inactivity.

### 3.4 Fullscreen Handover
Clicking the fullscreen button in the floating window calls `startPlayerActivity()`:
1. Passes current playback position via `ExternalResumeIntent.FLOATING_POSITION`.
2. Starts [`PlayerActivity`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java) with `FLAG_ACTIVITY_NEW_TASK`.
3. `FloatingPlayerService.stopSelf()` stops the overlay service and unbinds from `PlayerService`.

---

## 4. Key Classes and Modules

### `Video` Module
- **[`PlayerActivity`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java)**:
  Main video activity. Manages native Android PiP entry, transitions, TV format menu item, and configuration updates.
- **[`SurfaceController`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/SurfaceController.java)**:
  Encapsulates video rendering surface (`SurfaceView` or OpenGL `TextureView`). Exposes `getView()` to supply precise video render bounds to `sourceRectHint`.
- **[`FloatingPlayerService`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/FloatingPlayerService.java)**:
  Foreground overlay service managing floating window lifecycle, touch gestures, volume controls, and playback synchronization.
- **[`FloatingPlayerActivity`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/FloatingPlayerActivity.java)**:
  Legacy helper activity (retained for backward compatibility; not declared in current manifests).
- **[`res/layout/floating_player.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/floating_player.xml)**:
  Layout hierarchy for the floating window overlay, locked to LTR.
- **[`res/layout/player.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/player.xml)**:
  Main player layout hierarchy, locked to LTR.

---

## 5. Debugging with ADB Shell

All commands assume a device connected via `adb` (`adb -s <DEVICE_IP>:5555 shell ...`).

### 5.1 Check Overlay Permission (Floating Player)
Verify whether Nova has permission to draw overlays on phones/tablets:
```bash
adb shell appops get org.courville.nova SYSTEM_ALERT_WINDOW
```
*Expected on granted:* `SYSTEM_ALERT_WINDOW: allow`

Grant overlay permission via ADB if needed:
```bash
adb shell appops set org.courville.nova SYSTEM_ALERT_WINDOW allow
```

### 5.2 Check PiP Feature Support
Verify if the target device supports native Android PiP:
```bash
adb shell pm has-system-feature android.software.picture_in_picture
```
*Expected on supported devices:* `true`

### 5.3 Inspect Active PiP Window State
Query WindowManager for active PiP window bounds and task state:
```bash
adb shell dumpsys activity top | grep -iE "picture-in-picture|pip"
```
Or check WindowManager window containers:
```bash
adb shell dumpsys window windows | grep -iE "pip|pinned"
```

### 5.4 Inspect Floating Window View
Check if `FloatingPlayerService` overlay view is currently added to WindowManager:
```bash
adb shell dumpsys window windows | grep -i "org.courville.nova/com.archos.mediacenter.video.player.FloatingPlayerService"
```

### 5.5 Inspect PiP and Floating Player Logs
Stream logcat filtered for PiP and floating player events:
```bash
adb logcat -d | grep -iE "PlayerActivity|FloatingPlayer|PictureInPicture|CONFIG updateSizes"
```
