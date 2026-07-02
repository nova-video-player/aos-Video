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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
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

import java.lang.ref.WeakReference;

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
    private Subtitle currentSubtitle = null;

    private boolean mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing;
    private int mGestureAreaHeight, mControlBarHeight;

    Surface                     mUiSurface;
    private boolean mForbidWindow ;
    DispSubtitleThread mDispSubtitleThread = null;
    private static int mRoundCornerRadius = 0;
    private static boolean mFullScreenWithCutout = true;

    public static final int SUBTITLE_TYPE_NONE = 0;
    public static final int SUBTITLE_TYPE_TEXT = 1;
    public static final int SUBTITLE_TYPE_GFX = 2;

    private static final int MSG_STOP_SUBTITLE = 0;
    private static final int MSG_DISPLAY_SUBTITLE = 1;
    private static final int MSG_REMOVE_SUBTITLE = 2;
    private static final int MSG_SET_STATUSBAR_EVADE = 3;

    // Range for TextView.setTextSize() (txt Subtitle)
    private static final int TXT_SIZE_MIN = 16;
    private static final int TXT_SIZE_MAX = 64;
    private static final float TXT_SIZE_RANGE = TXT_SIZE_MAX - TXT_SIZE_MIN;

    private static class SubtitleHandler extends Handler {
        private final WeakReference<SubtitleManager> mSubtitleManager;

        SubtitleHandler(SubtitleManager subtitleManager) {
            super(Looper.getMainLooper());
            mSubtitleManager = new WeakReference<>(subtitleManager);
        }

        @Override
        public void handleMessage(Message msg) {
            SubtitleManager subtitleManager = mSubtitleManager.get();
            if (subtitleManager != null) {
                subtitleManager.handleMessage(msg);
            }
        }
    }

    private final Handler mHandler = new SubtitleHandler(this);

    // NOTE: the native subtitle pipeline (avos_mp_video.c::send_subtitle) now returns
    // early for every subtitle format (SSA/TEXT/DVD_GFX/PGS) and never reaches the old
    // JNI addSubtitle() callback — SubtitleEngine's native GL/libass renderer draws
    // everything now. displayView()/removeView() are therefore unreachable in practice
    // and have been removed along with the SubtitleGfxView/Subtitle3DTextView rendering
    // calls; addSubtitle()/DispSubtitleThread are kept intact (now harmless no-ops)
    // rather than ripped out, since PlayerActivity/FloatingPlayerService still call
    // addSubtitle() and removing that call site is a separate, larger change.
    private void handleMessage(Message msg) {
        if (log.isDebugEnabled()) log.debug("handleMessage: {}", msg.what);
        switch (msg.what) {
            case MSG_STOP_SUBTITLE:
            case MSG_DISPLAY_SUBTITLE:
            case MSG_REMOVE_SUBTITLE:
                if (log.isDebugEnabled()) log.debug("handleMessage: {} (no-op, native GL engine owns rendering)", msg.what);
                break;
            case MSG_SET_STATUSBAR_EVADE: {
                // Handle status bar evade
                if (log.isDebugEnabled()) log.debug("handleMessage: MSG_SET_STATUSBAR_EVADE");
            }
        }
    }

    private int mColor;
    private boolean mOutline;
    private boolean mBackground;
    private int mBgOpacity;
    private int mUiMode;

    // --- NEW: libass styling system (bg mode / override mode / absolute values) ---
    // mBgMode / mOutline / mBackground are kept in sync with each other so that legacy
    // callers (TV picker, old prefs) and the new dialog never disagree about state:
    //   mBgMode 0 (Floating)    <=> mOutline may be true/false, mBackground=false
    //   mBgMode 1 (Boxed Line)  <=> mBackground=true, mOutline forced false
    //   mBgMode 2 (Boxed Block) <=> mBackground=true, mOutline may be true/false
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

    private void removeSubtitle(Subtitle subtitle) {
        if (log.isDebugEnabled()) log.debug("removeSubtitle");
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REMOVE_SUBTITLE, subtitle));
    }

    private void displaySubtitle(Subtitle subtitle) {
        if (log.isDebugEnabled()) log.debug("displaySubtitle");
        mHandler.removeMessages(MSG_REMOVE_SUBTITLE);
        mHandler.removeMessages(MSG_DISPLAY_SUBTITLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_DISPLAY_SUBTITLE, subtitle));
    }

    public int getColor() {
        return mColor;
    }

    // --- LEGACY (TV picker + old prefs): boolean outline/background toggles ---
    // Kept working by mapping onto the new bg_mode enum under the hood.

    public boolean getOutlineState() { return mOutline; }

    /**
     * Legacy on/off outline toggle. Only meaningful in Floating mode (bg_mode 0);
     * if a boxed mode is active, forces Floating so the outline is actually visible,
     * matching what a user flipping this switch would expect to see.
     */
    public void setOutlineState(boolean outline) {
        mOutline = outline;
        if (mBgMode != BG_MODE_FLOATING) {
            setBgMode(BG_MODE_FLOATING);
        }
        setOutlineWidth(outline ? 4.0f : 0.0f); // matches legacy SubtitleTextView stroke width
    }

    public boolean getBackgroundState() {
        return mBackground;
    }

    /**
     * Legacy on/off background toggle. Maps to Boxed Line (bg_mode 1), the closest
     * equivalent to the old per-line CC-style box. Turning it off returns to Floating.
     */
    public void setBackgroundState(boolean background) {
        mBackground = background;
        setBgMode(background ? BG_MODE_BOXED_LINE : BG_MODE_FLOATING);
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
     * Also updates the legacy mOutline/mBackground booleans so getters used by the
     * TV picker and preference-save code stay consistent with whichever surface
     * last changed the mode.
     */
    public void setBgMode(int mode) {
        mBgMode = mode;
        mBackground = (mode != BG_MODE_FLOATING);
        if (mode != BG_MODE_FLOATING) mOutline = false;

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

    public void setBold(boolean bold) {
        mBold = bold;
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setBold(bold);
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

    final class DispSubtitleThread extends Thread {
        private boolean mSuspended = true;
        private boolean mRunning = true;
        private Subtitle mCurrentSubtitle = null;
        private Subtitle mNextSubtitle = null;
        private boolean interrupted = false;

        void quit() {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread quit");
            mRunning = false;
            mDispSubtitleThread = null;
            interrupt();
            try {
                join();
            } catch (InterruptedException e) {
                log.error("DispSubtitleThread quit - interrupted", e);
            }
        }

        @Override
        public void run() {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread started: set mSubtitleDisplayLeft=0");
            int mSubtitleDisplayLeft = 0;
            while (mRunning) {
                interrupted = false;
                synchronized (this) {
                    // wait() until we get a new Subtitle via addSubtitle() / player continues
                    while (mSuspended) {
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread wait()");
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            if (!mRunning) {
                                if (log.isDebugEnabled()) log.debug("DispSubtitleThread wait() - interrupted and not running, clear subtitle at {}!", System.currentTimeMillis());
                                clear();
                                return;
                            }
                            if (log.isDebugEnabled()) log.debug("DispSubtitleThread wait() - interrupted");
                        }
                    }
                }
                synchronized (this) {
                    // we don't have a subtitle, go back to wait()
                    if ((mCurrentSubtitle == null && mNextSubtitle == null) || (mCurrentSubtitle == null && mNextSubtitle != null && mNextSubtitle.getDuration() == 0)) {
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread no valid Subtitle, mNextSubtitle={}+{}ms",
                                mNextSubtitle != null ? mNextSubtitle.getPosition() : "null",
                                mNextSubtitle != null ? mNextSubtitle.getDuration() : "null");
                        if (mNextSubtitle != null) mNextSubtitle = null; // if mCurrentSubtitle is null, receiving zero subtitle has no effect
                        mSuspended = true;
                        continue;
                    }

                    // we have a subtitle that is not displayed yet
                    if (mCurrentSubtitle == null) { // new subtitle only considered if current one is not null
                        mCurrentSubtitle = mNextSubtitle; // the next subtitle has a duration > 0 other wise it would have been filtered out before
                        currentSubtitle = mCurrentSubtitle;
                        mNextSubtitle = null;
                        displaySubtitle(mCurrentSubtitle);
                        mSubtitleDisplayLeft = mCurrentSubtitle.getDuration();
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread displaying new (current=new) subtitle={}+{}ms, bounds={}, mSubtitleDisplayLeft={}", mCurrentSubtitle.getPosition(), mCurrentSubtitle.getDuration(), mCurrentSubtitle.getBounds(), mSubtitleDisplayLeft);
                    }
                }

                // outside of synchronized since sleep does NOT release the lock
                // go to sleep if we have still have mSubtitleDisplayLeft
                Subtitle currentSub = mCurrentSubtitle;
                if (mSubtitleDisplayLeft > 0 && currentSub != null) { // we have a subtitle to display
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread after displaying mCurrentSubtitle={}+{}ms, sleep for {}", currentSub.getPosition(), currentSub.getDuration(), mSubtitleDisplayLeft);
                    long sleepStart = System.currentTimeMillis();
                    try {
                        sleep(mSubtitleDisplayLeft);
                    } catch (InterruptedException e) { // wake up from sleep
                        interrupted = true;
                        long elapsedTime = System.currentTimeMillis() - sleepStart;
                        Subtitle curSub = mCurrentSubtitle;
                        Subtitle nxtSub = mNextSubtitle;
                        if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt, waking up after {}ms, mCurrentSubtitle={}+{}ms, mNextSubtitle={}+{}ms, old mSubtitleDisplayLeft={}",
                                elapsedTime,
                                curSub != null ? curSub.getPosition() : "null",
                                curSub != null ? curSub.getDuration() : "null",
                                nxtSub != null ? nxtSub.getPosition() : "null",
                                nxtSub != null ? nxtSub.getDuration() : "null",
                                mSubtitleDisplayLeft);
                        if (curSub != null && nxtSub != null) {
                            // woke up from sleep by interrupt because getting new subtitle
                            int currentPosition = curSub.getPosition() + (int) elapsedTime;
                            int realCurrentSubtitleDuration;
                            // need to correct time left only if the next subtitle starts before the current one ends
                            if (curSub.getPosition() + curSub.getDuration() > nxtSub.getPosition()) {
                                if (log.isDebugEnabled()) log.debug("DispSubtitleThread: cannot sleep after mNextSubtitle, adjust");
                                realCurrentSubtitleDuration = nxtSub.getPosition() - curSub.getPosition();
                                curSub.setDuration(realCurrentSubtitleDuration);
                                mSubtitleDisplayLeft = nxtSub.getPosition() - currentPosition;
                            } else {
                                realCurrentSubtitleDuration = curSub.getDuration();
                                mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                            }
                            if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt bcoz received new subtitle, recompute duration currentPosition={}, realCurrentSubtitleDuration={}, updated mSubtitleDisplayLeft={}", currentPosition, realCurrentSubtitleDuration, mSubtitleDisplayLeft);
                            if (nxtSub.getDuration() == 0) { // this is an empty subtitle that is used to provide the correct duration
                                if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt bcoz received empty Subtitle, dismiss mNextSubtitle");
                                mNextSubtitle = null; // remove the empty subtitle
                            }
                        } else {
                            mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                            if (log.isDebugEnabled()) log.debug("DispSubtitleThread sleep interrupt by seek/exit condition, updated mSubtitleDisplayLeft={}", mSubtitleDisplayLeft);
                        }
                    }
                    // if not interrupted update mSubtitleDisplayLeft (otherwise it is already done)
                    if (! interrupted) mSubtitleDisplayLeft -= (int) (System.currentTimeMillis() - sleepStart);
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread now mSubtitleDisplayLeft={}", mSubtitleDisplayLeft);
                }
                // if we slept without interrupt or no display time is left remove the subtitle
                if (mSubtitleDisplayLeft <= 0) {
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread removing subtitle because mSubtitleDisplayLeft={}<0", mSubtitleDisplayLeft);
                    synchronized (this) {
                        if (mCurrentSubtitle != null) {
                            removeSubtitle(mCurrentSubtitle);
                            mCurrentSubtitle = null;
                            currentSubtitle = null;
                            mSubtitleDisplayLeft = 0;
                        }
                    }
                }
            }
            clear();
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread exited");
        }

        synchronized void addSubtitle(Subtitle subtitle) {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle isBitmap={} isText={} isTimed={} position={} duration={}", subtitle.isBitmap(), subtitle.isText(), subtitle.isTimed(), subtitle.getPosition(), subtitle.getDuration());
            mSuspended = false;

            if (subtitle.isTimed()) {
                mNextSubtitle = subtitle;
                if (!isAlive()) {
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle thread is not alive -> start");
                    super.start();
                } else {
                    if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle thread is alive -> interrupt");
                    interrupt();
                }
            } else {
                if (log.isDebugEnabled()) log.debug("DispSubtitleThread addSubtitle not timed!");
                if (mCurrentSubtitle != null) {
                    removeSubtitle(mCurrentSubtitle);
                    mCurrentSubtitle = null;
                }

                if (subtitle.getText() != null) {
                    mCurrentSubtitle = subtitle;
                    displaySubtitle(mCurrentSubtitle);
                }
            }
        }

        synchronized void show() {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread show");
            // could setVisibility here
        }

        synchronized void clear() {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread clear");
            mSuspended = true;
            if (mCurrentSubtitle != null) {
                removeSubtitle(mCurrentSubtitle);
                mCurrentSubtitle = null;
                mNextSubtitle = null;
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_SUBTITLE));
        }

        synchronized void setSuspended(boolean suspended) {
            if (log.isDebugEnabled()) log.debug("DispSubtitleThread setSuspended");
            if (mSuspended == suspended)
                return;
            mSuspended = suspended;
            interrupt();
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
        if (currentSubtitle != null) displaySubtitle(currentSubtitle); // redisplay when changing screen size or video surface format
        setSize(mSubtitleSize);
        updateSubtitleLayout();
    }

    public void updateSubtitleLayout() {
        if (log.isDebugEnabled()) log.debug("updateSubtitleLayout");
        // surface change redisplay sub to adjust surface size
        if (! isFirstTime) adjustView();
        if (currentSubtitle != null) {
            displaySubtitle(currentSubtitle);
        }
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
            // Disconnect the Java canvas path — SubtitleTextView stops calling lockCanvas().
            disconnectJavaSubtitleSurface();

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

    // Silently disconnects all Java-canvas subtitle drawing without clearing mUiSurface,
    // so we can reconnect later if we switch back to 2D mode.
    private void disconnectJavaSubtitleSurface() {
        if (mSubtitleSpacer != null) mSubtitleSpacer.setRenderingSurface(null);
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
        // NOTE: subtitle_gfx_view / subtitle_txt_view no longer exist — the native
        // SubtitleEngine renders everything (2D and 3D) now. Only the position-hint
        // spacer still needs the surface reference.
        if (mSubtitleSpacer != null)
            mSubtitleSpacer.setRenderingSurface(uiSurface);
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

        if (mDispSubtitleThread == null) {
            mDispSubtitleThread = new DispSubtitleThread();
            try {
                mDispSubtitleThread.start();
            } catch (IllegalThreadStateException e) {
                // thread has been started before
            }
        }

        show();
    }

    public void stop() {
        if (log.isDebugEnabled()) log.debug("stop");

        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.quit();
        }
        detachWindow();
    }

    public void show() {
        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.show();
        }
    }

    public void clear() {
        if (mDispSubtitleThread != null) {
            mDispSubtitleThread.clear();
        }
    }

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
        // note: Increased the Range from 0.100 to 0.255 to make it smoother
        // translate VPos 0..255 to 0..(1/3)DisplayHeight
        // mScreenHeight / 3 * pos / 255
        mSubtitleVPosPixel = (mScreenHeight * pos / 765) + 1;
        setVerticalPositionInternal(mSubtitleVPosPixel);
    }

    private void setVerticalPositionInternal (int pos) {
        if (mIsSubtitleGfx) mSubtitleEvadedVPos = 0;
        else mSubtitleEvadedVPos = pos;

        // --- Route margin to Native Engine ---
        // libass applies MarginV as a bottom-only offset for our bottom-center alignment
        // (see generate_dynamic_ass_header()/sub_style_set_margin_bottom -> ASS MarginV).
        // The legacy mSubtitleSpacer view below is NOT resized here anymore: it was a
        // Java-canvas era mechanism for reserving screen space above SubtitleTextView, and
        // dynamically growing/shrinking its height in the layout visually shifts the video
        // surface's top boundary too, which looked like "the top margin is moving" even
        // though MarginV itself is correctly bottom-only. The native offset alone now owns
        // vertical position; the spacer stays at whatever static size the layout gives it.
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setVerticalOffset(mSubtitleEvadedVPos);
        }
    }

    public void addSubtitle(Subtitle subtitle) {
        if (mDispSubtitleThread != null)
            mDispSubtitleThread.addSubtitle(subtitle);
    }

    public void onPlay() {
        if (mDispSubtitleThread != null)
            mDispSubtitleThread.setSuspended(false);
    }

    public void onPause() {
        if (mDispSubtitleThread != null)
            mDispSubtitleThread.setSuspended(true);
    }

    public void onSeekStart(int pos) {
        if (mDispSubtitleThread != null) {
            if (log.isDebugEnabled()) log.debug("onSeekStart: clear");
            mDispSubtitleThread.clear();
            mDispSubtitleThread.interrupt();
        }
    }
}
