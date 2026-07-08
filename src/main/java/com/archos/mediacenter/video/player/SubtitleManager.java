// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.player;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.medialib.Subtitle;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubtitleManager {

    private static final Logger log = LoggerFactory.getLogger(SubtitleManager.class);

    private Context             mContext;
    private ViewGroup           mPlayerView;
    private View                mRootView;
    private WindowManager       mWindow;
    private Resources           mRes;
    private View                mSubtitleLayout = null;
    private SubtitleSpacerView  mSubtitleSpacer = null;
    private LayoutParams        mSubtitleSpacerParams = null;
    private Drawable            mSubtitlePosHintDrawable;
    private int                 mScreenWidth;
    private int                 mScreenHeight;
    private int                 mSubtitleSize = 50;
    private int                 mSubtitleVPos = 10;
    private int                 mSubtitleVPosPixel;
    private int                 mSubtitleEvadedVPos;
    private boolean mIsSubtitleGfx = false;
    private boolean isFirstTime = true;

    private boolean mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing;
    private int mGestureAreaHeight, mControlBarHeight;

    Surface                     mUiSurface;
    private boolean mForbidWindow ;
    private static int mRoundCornerRadius = 0;
    private static boolean mFullScreenWithCutout = true;

    public static final int SUBTITLE_TYPE_NONE = 0;
    public static final int SUBTITLE_TYPE_TEXT = 1;
    public static final int SUBTITLE_TYPE_GFX = 2;

    // Range for TextView.setTextSize() (txt Subtitle)
    private static final int TXT_SIZE_MIN = 16;
    private static final int TXT_SIZE_MAX = 64;
    private static final float TXT_SIZE_RANGE = TXT_SIZE_MAX - TXT_SIZE_MIN;

    // NOTE: this class used to hold a Handler (SubtitleHandler) posting MSG_STOP_SUBTITLE/
    // MSG_DISPLAY_SUBTITLE/MSG_REMOVE_SUBTITLE/MSG_SET_STATUSBAR_EVADE messages, driven by
    // displaySubtitle()/removeSubtitle(), which were in turn only ever called by
    // DispSubtitleThread. All of that has been removed: the native subtitle pipeline
    // (avos_mp_video.c::send_subtitle) returns early for every subtitle format before the
    // JNI callback that would feed this chain ever fires, so the whole thing only ever
    // processed zero real messages. The native SubtitleEngine (libass + GL) renders
    // everything now.

    private int mColor;
    private int mBgOpacity;
    private int mUiMode;

    // --- libass styling system (bg mode / override mode / absolute values) ---
    public static final int BG_MODE_FLOATING    = 0;
    public static final int BG_MODE_BOXED_LINE  = 1;
    public static final int BG_MODE_BOXED_BLOCK = 2;

    public static final int OVERRIDE_EMBEDDED   = 0;
    public static final int OVERRIDE_CUSTOM     = 1;
    public static final int OVERRIDE_SCALE_ONLY = 2;

    private int mBgMode = BG_MODE_FLOATING;
    private int mOverrideMode = OVERRIDE_CUSTOM;
    private int mFontSizePt = 42;
    private float mFontScale = 1.0f;
    private boolean mBold = false;
    private int mOutlineColor = 0xFF000000;
    private int mShadowColor  = 0xAA000000;
    private int mBackgroundColor = 0x88000000; // matches sub_style.c's native default (50% transparent black)
    private float mOutlineWidth = 2.0f; // px, also used as "outline" in Boxed Block mode
    private float mShadowWidth  = 2.0f; // px in Floating mode, padding in Boxed Block mode

    public int getColor() {
        return mColor;
    }

    public int getBackgroundOpacity() {
        return mBgOpacity;
    }

    public void setBackgroundOpacity(int opacity) {
        mBgOpacity = opacity;

        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            // Java passes 0-255, our C-engine expects a 0.0 - 1.0 float!
            Player.sPlayer.getSubtitleEngine().setBackgroundOpacity(opacity / 255.0f);
        }
    }

    // --- NEW: libass styling system ---

    public int getBgMode() { return mBgMode; }

    /**
     * Switches between Floating (0) / Boxed Line (1) / Boxed Block (2).
     */
    public void setBgMode(int mode) {
        mBgMode = mode;

        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setBackgroundMode(mode);
        }
    }

    public int getOverrideMode() { return mOverrideMode; }

    /** 0 = Embedded track styles, 1 = Force custom user styles, 2 = Scale only. */
    public void setOverrideMode(int mode) {
        mOverrideMode = mode;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setOverrideMode(mode);
        }
    }

    public int getFontSizePt() { return mFontSizePt; }

    /** Absolute point size, replaces the old 0..100 abstract scale for the new dialog. */
    public void setFontSizePt(int pt) {
        mFontSizePt = pt;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setFontSize((float) pt);
        }
        // Keep the legacy 0..100 slider field roughly in sync in case old code paths
        // (TV picker) read mSubtitleSize before this dialog has run.
        mSubtitleSize = pt;
    }

    public boolean getBold() { return mBold; }

    public void setBold(boolean bold) {
        mBold = bold;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setBold(bold);
        }
    }

    /**
     * Multiplier applied on top of the embedded track's own font size — only meaningful
     * in OVERRIDE_SCALE_ONLY mode. In OVERRIDE_CUSTOM/OVERRIDE_EMBEDDED this value is
     * tracked but never read by sync_styles() (see sub_format_ssa.c's override_mode==2
     * branch, which multiplies the embedded ASS_Style.FontSize by font_scale — it never
     * touches the absolute font_size field that setFontSizePt() drives).
     */
    public float getFontScale() { return mFontScale; }

    public void setFontScale(float scale) {
        mFontScale = scale;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setFontScale(scale);
        }
    }

    public int getOutlineColor() { return mOutlineColor; }

    public void setOutlineColor(int color) {
        mOutlineColor = color;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setOutlineColor(color);
        }
    }

    public int getShadowColor() { return mShadowColor; }

    public void setShadowColor(int color) {
        mShadowColor = color;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setShadowColor(color);
        }
    }

    public int getBackgroundColor() { return mBackgroundColor; }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setBackgroundColor(color);
            // nativeSetBackgroundColor overwrites the ENTIRE bg_color word (RGB + the
            // transparency byte nativeSetBackgroundOpacity separately patches). Re-apply
            // the currently tracked opacity right after, or picking a background color
            // silently resets opacity to whatever alpha channel the picked color's swatch
            // default happened to carry — which is how "Boxed Line looks broken" manifests:
            // the box was drawn, just at the wrong (often fully opaque) transparency.
            Player.sPlayer.getSubtitleEngine().setBackgroundOpacity(mBgOpacity / 255.0f);
        }
    }

    public float getOutlineWidth() { return mOutlineWidth; }

    /** In Boxed Block mode (bg_mode 2) this is the outline drawn inside the box. */
    public void setOutlineWidth(float px) {
        mOutlineWidth = px;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setOutlineWidth(px);
        }
    }

    public float getShadowWidth() { return mShadowWidth; }

    /** In Boxed Block mode (bg_mode 2) this value is hijacked by libass as box padding. */
    public void setShadowWidth(float px) {
        mShadowWidth = px;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setShadowWidth(px);
        }
    }

    public void setUIMode(int uiMode) {
        mUiMode = uiMode;

        // Determine whether the native GL engine is now the active subtitle renderer.
        // In SBS or TB mode, SubtitleEngine's EGL thread owns gl_subtitle_view exclusively.
        // In 2D mode, the Java canvas path (SubtitleTextView.lockCanvas) is active instead.
        // These two paths must never run simultaneously — doing so causes lockCanvas() to
        // overwrite GL frames with a black clear, producing the black screen bug.
        boolean glEngineIsActive = ((uiMode & VideoEffect.SBS_MODE) != 0)
                                || ((uiMode & VideoEffect.TB_MODE) != 0);
        setGLEngineActive(glEngineIsActive);

        // Pass the 3D mode to the GPU compositor
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setUIMode(uiMode);
        }
    }

    // DispSubtitleThread removed: it was a scheduling engine for displaying/timing
    // subtitles fed via addSubtitle(), but addSubtitle() is never called in practice —
    // avos_mp_video.c's send_subtitle() returns early for every subtitle format
    // (SSA/TEXT/DVD_GFX/PGS) before the native code ever reaches the JNI callback that
    // would invoke it. The native SubtitleEngine (libass + GL, see sub_engine.c /
    // sub_format_ssa.c / sub_render_gl.c) is the only thing that renders subtitles now.
    // See start()/stop()/show()/clear()/addSubtitle()/onPlay()/onPause()/onSeekStart()
    // below for the no-op stubs kept for API compatibility with PlayerActivity and
    // FloatingPlayerService, which still call these methods from live code paths.

    public SubtitleManager(Context context, ViewGroup playerView, WindowManager window, boolean forbidWindow) {
        mContext = context;
        mPlayerView = playerView;
        mWindow = window;
        mRes = context.getResources();
        mForbidWindow = forbidWindow;
        mSubtitlePosHintDrawable = ContextCompat.getDrawable(context, com.archos.mediacenter.video.R.drawable.subtitle_baseline);
    }

    public void setScreenSize(int displayWidth, int displayHeight) {
        if (log.isDebugEnabled()) log.debug("setScreenSize: {}x{} mIsSubtitleGfx={}, mSubtitleLayout={}", displayWidth, displayHeight, mIsSubtitleGfx, (mSubtitleLayout == null ? "null" : "not null"));
        mScreenWidth = displayWidth;
        mScreenHeight = displayHeight;
        if (mSubtitleLayout != null) {
            // reset layout params to get full screen text subs since before it could have been gfx subs with different layout
            ViewGroup.LayoutParams lp = mSubtitleLayout.getLayoutParams();
            lp.width = mScreenWidth;
            lp.height = mScreenHeight;
            mPlayerView.updateViewLayout(mSubtitleLayout, lp);
        }
        // NOTE: this used to redisplay a currentSubtitle field here, tracked by
        // DispSubtitleThread, which has been removed (see the note above start()).
        setSize(mSubtitleSize);
        updateSubtitleLayout();
    }

    public void updateSubtitleLayout() {
        if (log.isDebugEnabled()) log.debug("updateSubtitleLayout");
        // surface change redisplay sub to adjust surface size
        if (! isFirstTime) adjustView();
    }

    // When true, the native SubtitleEngine GL thread owns gl_subtitle_view.
    // The Java canvas subtitle path (SubtitleTextView.setRenderingSurface / lockCanvas)
    // must be completely disconnected — two producers cannot share one SurfaceTexture,
    // and calling lockCanvas() on a TextureView-backed Surface that has an active EGL
    // context wipes the GL frame with a black canvas clear every time it fires.
    private boolean mGLEngineActive = false;

    public void setGLEngineActive(boolean active) {
        if (log.isDebugEnabled()) log.debug("setGLEngineActive: {}", active);
        mGLEngineActive = active;
        if (active) {
            // CRITICAL: VideoEffectRenderer.draw() calls mUISurfaceTexture.updateTexImage()
            // unconditionally every frame. mUISurface is the Surface backed by that
            // SurfaceTexture. Now that we've disconnected all Java subtitle drawing,
            // nothing will ever produce a buffer into mUISurfaceTexture's queue again.
            // Calling updateTexImage() on a SurfaceTexture with an empty queue corrupts
            // GL texture unit state on most Android drivers, turning the video black.
            //
            // Fix: post one transparent clear frame to mUiSurface right now, so
            // mUISurfaceTexture has a valid initial buffer. VideoEffectRenderer will
            // consume it on the first draw() call and then keep re-using that last
            // frame (updateTexImage with no new buffer is safe once a valid frame exists).
            postClearFrameToUISurface();
        } else {
            // Re-connect the Java path with whatever surface was last set.
            setUIExternalSurface(mUiSurface);
        }
    }

    // Posts a single fully-transparent frame to mUiSurface so that
    // VideoEffectRenderer's mUISurfaceTexture always has a valid buffer.
    private void postClearFrameToUISurface() {
        if (mUiSurface == null) {
            if (log.isDebugEnabled()) log.debug("postClearFrameToUISurface: mUiSurface is null, skipping");
            return;
        }
        try {
            android.graphics.Canvas c = mUiSurface.lockCanvas(null);
            if (c != null) {
                c.drawColor(0x00000000); // fully transparent clear
                mUiSurface.unlockCanvasAndPost(c);
                if (log.isDebugEnabled()) log.debug("postClearFrameToUISurface: posted transparent frame to keep mUISurfaceTexture queue valid");
            }
        } catch (Exception e) {
            // Surface may be in an invalid state during init; log and continue.
            log.warn("postClearFrameToUISurface: failed to post clear frame", e);
        }
    }

    public void setUIExternalSurface(Surface uiSurface) {
        if (log.isDebugEnabled()) log.debug("setUIExternalSurface {}", uiSurface);
        mUiSurface = uiSurface;
        if (mGLEngineActive) {
            // GL engine owns gl_subtitle_view. Do NOT forward this surface to the Java
            // canvas path — it would be pointing at gl_surface_view (the VIDEO surface)
            // and lockCanvas() calls would black out video frames on every subtitle update.
            if (log.isDebugEnabled()) log.debug("setUIExternalSurface: GL engine active, skipping Java canvas path");
            // But we DO need a single transparent frame in mUISurfaceTexture's queue so
            // VideoEffectRenderer.draw()'s unconditional updateTexImage() doesn't corrupt
            // GL state. Post it now that we have the real surface reference.
            postClearFrameToUISurface();
            return;
        }
        // NOTE: subtitle_gfx_view / subtitle_txt_view no longer exist, and the position-hint
        // spacer (SubtitleSpacerView) no longer has a rendering-surface concept either — it's
        // a plain View shown via alpha/background now, not a second Java-canvas draw target.
        // This surface is simply tracked in mUiSurface above for whichever GL-active branch
        // needs it next time this is called.
    }

    // setOnSystemUiVisibilityChangeListener is the only reliable way to track transient bar visibility;
    // no WindowInsetsControllerCompat equivalent exists for this use case.
    @SuppressWarnings("deprecation")
    private void attachWindow() {
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (mPreferences != null) mFullScreenWithCutout = mPreferences.getBoolean("enable_cutout_mode_short_edges", true);
        if (mSubtitleLayout != null) return;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSubtitleLayout = inflater.inflate(R.layout.subtitle_layout, mPlayerView, false);
        if (mSubtitleLayout == null) return;
        // NOTE: subtitle_gfx_view / subtitle_txt_view are no longer inflated or referenced here.
        // The native SubtitleEngine (libass + GL) owns all subtitle rendering now; this class
        // only retains the spacer (position-hint view) and the UI surface plumbing below.
        mSubtitleSpacer = (SubtitleSpacerView) mSubtitleLayout.findViewById(R.id.subtitle_spacer);
        if (mSubtitleSpacer == null) return;
        mSubtitleSpacerParams = mSubtitleSpacer.getLayoutParams();
        if (log.isDebugEnabled()) log.debug("attachWindow: mSubtitleSpacerParams.height={}", mSubtitleSpacerParams.height);
        mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
        setUIExternalSurface(mUiSurface);

        if (mSubtitleLayout != null) {
            mRootView = mSubtitleLayout.getRootView();
            // note OnApplyWindowInsetsListener does not update when navigation bar fades away, OnGlobalLayoutListener or addOnPreDrawListener are constantly triggering -> only setOnSystemUiVisibilityChangeListener works
            // however setOnSystemUiVisibilityChangeListener is unreliable on Android 6.0 thus use addOnLayoutChangeListener
            // in reality we need to do combination of setOnApplyWindowInsetsListener to get insets but not updated when UI mode changes and thus combine with setOnSystemUiVisibilityChangeListener

            // insets observer is needed for rotation
            mSubtitleLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                if (log.isDebugEnabled()) log.debug("attachWindow, onApplyWindowInsetsListener, mIsSubtitleGfx={}", mIsSubtitleGfx);
                if (! isFirstTime) adjustView();
                return insets;
            });

            // ui visibility listener is needed for UI mode changes
            // No WindowInsetsControllerCompat equivalent for transient bar visibility tracking;
            // setOnSystemUiVisibilityChangeListener remains the only reliable option here.
            //noinspection deprecation
            mRootView.setOnSystemUiVisibilityChangeListener(visibility -> {
                //noinspection deprecation
                mNavigationBarShowing = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                //noinspection deprecation
                mSystemBarShowing = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
                mActionBarShowing = PlayerController.isActionBarShowing();
                mIsNavBarOnBottom = MiscUtils.isNavigationBarOnBottom(mRootView, mContext);
                mIsGestureAreaShowing = MiscUtils.isGestureAreaDisplayed(mContext);
                mGestureAreaHeight = MiscUtils.getGestureAreaHeight(mContext);
                if (log.isDebugEnabled()) log.debug("attachWindow, setOnSystemUiVisibilityChangeListener: mNavigationBarShowing={}, mSystemBarShowing={}, mActionBarShowing={}, mControlBarShowing={}, mIsNavBarOnBottom={}, mIsGestureAreaShowing={}",
                        mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, PlayerController.isControlBarShowing(), mIsNavBarOnBottom, mIsGestureAreaShowing);
                // extra parameters injected for subtitles handling that need to be shifted up above controlBar of playerController if the mSubtitleEvadedVPos is not shifting them already above
                if (! isFirstTime) adjustView();
            });

        }

        mPlayerView.addView(mSubtitleLayout, mScreenWidth, mScreenHeight);
    }

    private void adjustView() {
        // strategy is videoView avoids cutout if not in fullscreen
        // adjust subtitle text height (bottom/top) to avoid system bars and playerController bar only if text subtitle but not left/right
        boolean avoidCutout = ! mFullScreenWithCutout;
        boolean isFloatingPlayer = Player.sPlayer != null && Player.sPlayer.isFloatingPlayer();
        // Player.sPlayer.getSurfaceControllerWidth(), Player.sPlayer.getSurfaceControllerHeight() is for the videoView but virtualScreen is larger
        // do not apply globalShift if in floating player mode
        if (log.isDebugEnabled()) log.debug("adjustView: mIsSubtitleGfx={}", mIsSubtitleGfx);
        mActionBarShowing = PlayerController.isActionBarShowing();
        MiscUtils.adjustViewLayoutForInsets(mContext, mRootView, mSubtitleLayout, "mSubtitleLayout",
                mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, PlayerController.isControlBarShowing(), mIsNavBarOnBottom, mIsGestureAreaShowing,
                (! mIsSubtitleGfx && PlayerController.isControlBarShowing() ? PlayerController.getControlBarCurrentHeight() : 0), (mIsSubtitleGfx ? 0 :mSubtitleEvadedVPos),
                false, ! mIsSubtitleGfx, false, ! mIsSubtitleGfx,
                avoidCutout, avoidCutout, avoidCutout, avoidCutout, ! mIsSubtitleGfx, mIsSubtitleGfx && ! isFloatingPlayer);
    }

    public void onControlBarVisibilityChanged() {
        if (! isFirstTime) adjustView();
    }

    private void detachWindow() {
        if (mSubtitleLayout == null)
            return;
        if (log.isDebugEnabled()) log.debug("detachWindow");
        mPlayerView.removeView(mSubtitleLayout);
        mSubtitleLayout = null;
    }

    public void start() {
        if (log.isDebugEnabled()) log.debug("start");
        attachWindow();
    }

    public void stop() {
        if (log.isDebugEnabled()) log.debug("stop");
        detachWindow();
    }

    // NOTE: show()/clear()/addSubtitle()/onPlay()/onPause()/onSeekStart() below are kept
    // as no-op stubs rather than removed outright, since PlayerActivity and
    // FloatingPlayerService still call them from live code paths (track open/close,
    // seek, play/pause, and the onSubtitle() JNI callback). Their bodies are empty
    // because DispSubtitleThread — the class that used to give them meaning — has been
    // removed: avos_mp_video.c's send_subtitle() returns early for every subtitle format
    // (SSA/TEXT/DVD_GFX/PGS) before it ever reaches the JNI callback that would call
    // addSubtitle(), so the thread only ever sat blocked on wait() for its entire life.
    // The native SubtitleEngine (libass + GL) is the only thing that actually renders
    // subtitles now.

    public int getSize() {
        return mSubtitleSize;
    }

    public int getVerticalPosition() {
        return mSubtitleVPos;
    }

    /**
     * Translates size to a usable size for TextView.SetTextSize()
     *
     * @param size 0..100 so we can use default slidebar values
     * @return float between TXT_SIZE_MIN and TXT_SIZE_MAX
     */
    public static float calcTextSize(int size) {
        int tmp = size;
        if (tmp > 100)
            tmp = 100;
        if (tmp < 0)
            tmp = 0;
        return (tmp / 100f) * TXT_SIZE_RANGE + TXT_SIZE_MIN;
    }

    /**
     * @param size expects Number 0..100
     */
    public void setSize(int size) {
        if (log.isDebugEnabled()) log.debug("setSize: {}", size);
        mSubtitleSize = size;

        // --- NEW: Route to Native Engine ---
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setFontSize(calcTextSize(size));
        }
    }

    public void setColor(int color){
        if (log.isDebugEnabled()) log.debug("setColor: {}", color);
        mColor = color;

        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setTextColor(color);
        }
    }

    /**
     * Animates the Alpha
     * @param fadeIn true to fade in, false to fade out
     */
    public void fadeSubtitlePositionHint (boolean fadeIn) {
        if (log.isDebugEnabled()) log.debug("fadeSubtitlePositionHint: {}", fadeIn);
        if (mSubtitleSpacer == null)
            return;
        if (fadeIn) {
            mSubtitleSpacer.animate().alpha(1).setDuration(100);
        } else {
            mSubtitleSpacer.animate().alpha(0).setDuration(500);
        }
    }

    /**
     * after you enable this you need to call fadeSubtitlePositionHint(true)
     * otherwise the Alpha of the Drawable stays at 0
     * @param show
     */
    public void setShowSubtitlePositionHint (boolean show) {
        if (log.isDebugEnabled()) log.debug("setShowSubtitlePositionHint: {}", show);
        if (mSubtitleSpacer == null)
            return;
        mSubtitleSpacer.setAlpha(0);
        if (show) {
            mSubtitleSpacer.setBackground(mSubtitlePosHintDrawable);
        } else {
            mSubtitleSpacer.setBackground(null);
        }
    }

    /**
     * Sets Subtitle Vertical Position by sizing an invisible view<br>
     * Space below Subtitle is max 1/3 of mScreenHeight.
     * @param pos 0..255.
     */
    public void setVerticalPosition(int pos) {
        if (mIsSubtitleGfx)
            mSubtitleVPos = 0;
        else
            mSubtitleVPos = pos;

        mSubtitleVPosPixel = (mScreenHeight * pos / 765) + 1;
        setVerticalPositionInternal(mSubtitleVPosPixel);
    }

    private void setVerticalPositionInternal (int pos) {
        if (mIsSubtitleGfx) mSubtitleEvadedVPos = 0;
        else mSubtitleEvadedVPos = pos;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setVerticalOffset(mSubtitleEvadedVPos);
        }

        if (mSubtitleSpacer != null && mSubtitleSpacerParams != null) {
            mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
            mSubtitleSpacer.setLayoutParams(mSubtitleSpacerParams);
        }
    }
}
