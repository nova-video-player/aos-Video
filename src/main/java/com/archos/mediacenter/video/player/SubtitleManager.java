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
    private int                 mSubtitleVPos = 10;
    private int                 mSubtitleVPosPixel;
    private int                 mSubtitleEvadedVPos;
    private boolean mGLEngineActive = false;
    private boolean mIsSubtitleGfx = false;
    private boolean isFirstTime = true;

    private boolean mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing;
    private int mGestureAreaHeight;

    Surface                     mUiSurface;
    private boolean mForbidWindow ;
    private static boolean mFullScreenWithCutout = true;

    private int mColor;
    private int mBgOpacity;
    private int mUiMode;

    private int mBgMode = BG_MODE_FLOATING;
    private int mOverrideMode = OVERRIDE_CUSTOM;
    private int mFontSizePt;
    private float mFontScale;
    private boolean mBold;
    private int mOutlineColor;
    private int mShadowColor;
    private int mBackgroundColor;
    private float mOutlineWidth;
    private float mShadowWidth;

    public static final int BG_MODE_FLOATING    = 0;
    public static final int BG_MODE_BOXED_LINE  = 1;
    public static final int BG_MODE_BOXED_BLOCK = 2;

    public static final int OVERRIDE_EMBEDDED   = 0;
    public static final int OVERRIDE_CUSTOM     = 1;
    public static final int OVERRIDE_SCALE_ONLY = 2;

    public int getColor() {
        return mColor;
    }

    public void setColor(int color){
        if (log.isDebugEnabled()) log.debug("setColor: {}", color);
        mColor = color;

        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setTextColor(color);
        }
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
     * in OVERRIDE_SCALE_ONLY mode.
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
        // These two paths must never run simultaneously
        boolean glEngineIsActive = ((uiMode & VideoEffect.SBS_MODE) != 0)
                                || ((uiMode & VideoEffect.TB_MODE) != 0);
        setGLEngineActive(glEngineIsActive);

        // Pass the 3D mode to the GPU compositor
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setUIMode(uiMode);
        }
    }

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
        setFontSizePt(mFontSizePt);
        updateSubtitleLayout();
    }


    public void updateSubtitleLayout() {
        if (log.isDebugEnabled()) log.debug("updateSubtitleLayout");
        // surface change redisplay sub to adjust surface size
        if (! isFirstTime) adjustView();
    }

    public void setGLEngineActive(boolean active) {
        if (log.isDebugEnabled()) log.debug("setGLEngineActive: {}", active);
        mGLEngineActive = active;
        if (active) {
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

    public int getVerticalPosition() {
        return mSubtitleVPos;
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
