# Right-to-Left (RTL) vs Left-to-Right (LTR) Policy in Nova Video Player

This document records the architectural decisions, UX rationale, industry standards, and technical safeguards governing RTL and LTR support in Nova Video Player.

---

## 1. The Dual-Domain Architecture

Nova strictly separates its user interface into two distinct domains:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Nova Video Player UI                             │
├────────────────────────────────────┬────────────────────────────────────┤
│     1. Browse & Metadata Domain    │      2. Playback & Media Domain    │
│       (Mirrored: RTL in RTL)       │        (Locked: Strictly LTR)      │
├────────────────────────────────────┼────────────────────────────────────┤
│ • Leanback TV launcher & categories│ • Fullscreen player (PlayerActivity)│
│ • Movie & TV show details screens  │ • Transport scrubber & timeline    │
│ • File browsers (SMB, UPnP, local) │ • In-player OSD TV menu drawer     │
│ • Search, preferences & dialogs    │ • Floating player window (service) │
│ • Reading flow: Right-to-Left      │ • Time progression: Left-to-Right  │
└────────────────────────────────────┴────────────────────────────────────┘
```

---

## 2. UX Rationale & User Perception

### 2.1 Why Browse & Metadata Must Be RTL
In RTL languages (Arabic, Hebrew, Persian, Urdu), reading direction, visual hierarchy, and mental models proceed from **right to left**:
- **System Parity**: The Android TV / Google TV home screen aligns categories and drawers on the **right** in RTL locales.
- **Natural Eye Flow**: Placing categories on the right and flowing poster cards right-to-left allows native speakers to scan content naturally.
- **Avoiding "Broken Localization"**: Displaying translated Arabic or Hebrew text in an unmirrored LTR layout causes text truncation, misaligned icons, and is perceived as an unfinished or broken localization.

### 2.2 Why Playback & Controls Must Remain LTR
Media playback represents **time**, not written language. Worldwide standards (IEC, ISO) and platform design guidelines universally treat media playback as moving forward from left to right:

> **Google Material Design Guidelines on Bidirectionality:**
> *"Media player controls (play, pause, fast forward, rewind) and timeline scrubbers should not be mirrored in RTL because they represent the direction of time progression, which is universally standardized."*

- **Timeline Progression**: The progress bar always starts at `0:00` on the left and advances rightward toward the total duration. Flipping this would cause progress bars to fill backwards (right-to-left), creating severe cognitive friction.
- **Directional Transport Symbols**: Standard playback symbols (`▶` play, `⏩` fast-forward, `⏪` rewind) point in consistent directions worldwide.
- **Industry Precedents**: All major streaming applications on Android TV and mobile (**YouTube, Netflix, Disney+, Prime Video, and VLC**) keep their video playback timelines and transport controls LTR in RTL locales while presenting their browse catalog in RTL.

### 2.3 The In-Player TV Menu Drawer
While the main Leanback browse menu appears on the right in RTL, the in-player TV menu drawer (`tv_menu_layout.xml`) opens on the **left**:
- The player canvas is a unified LTR environment.
- Placing the menu on the left matches the left-hand position of the volume bar and prevents off-screen rendering bugs caused by contradictory coordinate systems.
- RTL users naturally interpret the player as an LTR media tool, distinct from the content catalog.

---

## 3. Technical Implementation & Safeguards

### 3.1 Playback Root Layouts (`android:layoutDirection="ltr"`)
Every playback-related layout declares `android:layoutDirection="ltr"` at its root tag to block framework-level mirroring:
- [`res/layout/player.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/player.xml)
- [`res/layout/player_controller.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/player_controller.xml) (and its `land`, `sw500dp`, `sw600dp` variants)
- [`res/layout/player_controller_inside.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/player_controller_inside.xml)
- [`res/layout/tv_menu_layout.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/tv_menu_layout.xml)
- [`res/layout/floating_player.xml`](file:///Users/marc/Documents/git/nova-publish/Video/res/layout/floating_player.xml)

### 3.2 Programmatic Enforcement in Code
Inflated player views explicitly set LTR layout direction to prevent inheritance from RTL parent contexts:
- [`PlayerActivity.java`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerActivity.java): `mRootView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);`
- [`PlayerController.java`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/PlayerController.java): `mControllerView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);`
- [`FloatingPlayerService.java`](file:///Users/marc/Documents/git/nova-publish/Video/src/main/java/com/archos/mediacenter/video/player/FloatingPlayerService.java): `mFloatingPlayerRootView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);`
- `TVCardView.java`: Programmatic LTR enforcement on the TV menu container.

### 3.3 WindowManager Coordinate Space (`FloatingPlayerService`)
Overlay windows created via `WindowManager` use display-absolute coordinates:
```java
mParamsF.gravity = Gravity.TOP | Gravity.LEFT;
```
> **Warning**: Never use `Gravity.START` for `WindowManager.LayoutParams` in floating windows. In RTL locales, `START` resolves to `RIGHT`, causing `mParamsF.x` to measure from the right screen edge. This inverts touch drag physics (dragging right moves the window left) and breaks edge boundary clamping.

---

## 4. Key Commits & Evolution

| Commit | Summary |
| :--- | :--- |
| [`d7b87bd2`](file:///Users/marc/Documents/git/nova-publish/Video) | Batch 8: Enabled `android:supportsRtl="true"` in manifest and migrated browse layouts to `Start`/`End`. |
| [`daa25061`](file:///Users/marc/Documents/git/nova-publish/Video) | Locked TV menu container to LTR layout direction to fix off-screen positioning in RTL locales. |
| [`57e9f6e7`](file:///Users/marc/Documents/git/nova-publish/Video) | Locked `PlayerController` UI and inside layouts to LTR layout direction. |
| [`5f9113a8`](file:///Users/marc/Documents/git/nova-publish/Video) | Locked `player.xml` and `floating_player.xml` to LTR; reverted `FloatingPlayerService` gravity to `Gravity.TOP \| Gravity.LEFT`. |
