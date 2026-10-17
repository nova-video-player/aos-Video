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
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import androidx.preference.PreferenceManager;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.WindowInsetsCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubtitleManager {

    private static final Logger log = LoggerFactory.getLogger(SubtitleManager.class);

    private Context             mContext;
    private final Defaults      mDefaults;
    private ViewGroup           mPlayerView;
    private View                mRootView;
    private View                mGlSubtitleView;      // native subtitle canvas, resolved lazily
    private final View.OnLayoutChangeListener mCanvasLayoutListener =
            (v, l, t, r, b, ol, ot, or_, ob) -> { if (t != ot || b != ob) applyNativeVerticalOffset(); };
    private View                mSubtitleLayout = null;
    private SubtitleSpacerView  mSubtitleSpacer = null;
    private LayoutParams        mSubtitleSpacerParams = null;
    private Drawable            mSubtitlePosHintDrawable;
    private int                 mScreenWidth;
    private int                 mScreenHeight;
    private int                 mSubtitleVPos = 10;
    private int                 mSubtitleEvadedVPos;
    private boolean mGLEngineActive = false;
    // ------------------------------------------------------------------------------------
    // Subtitle kind: the ONE place Java gives meaning to a track's kind.
    //
    // Native decides (sub_kind_from_format() in sub_engine.c, derived from the same table that
    // picks the renderer) and sends the value in the SUBTITLE_TRACK_KIND metadata field.
    // Everything else -- layout category, vertical-position handling, control-bar avoidance,
    // whether the settings menu applies -- is answered from here. Nothing outside this class
    // may infer a kind from a format label, a file extension or a gfx flag.
    // The KIND_* values must match SUB_KIND in sub_kind.h.
    // ------------------------------------------------------------------------------------
    public static final int KIND_NONE        = 0; // no track selected
    public static final int KIND_SSA         = 1; // ASS/SSA: authored styles, PlayRes tied to the video frame
    public static final int KIND_PLAIN_TEXT  = 2; // SRT/VTT/...: free-form text
    public static final int KIND_GRAPHIC     = 3; // PGS / DVD / VobSub: baked-in position and size
    public static final int KIND_UNSUPPORTED = 4; // recognised, but nothing renders it

    private int mKind = KIND_NONE;

    /** Fail safe: a value native did not send, or one from a newer native, is UNSUPPORTED. */
    public static int kindFromNative(int nativeKind) {
        return (nativeKind >= KIND_SSA && nativeKind <= KIND_UNSUPPORTED) ? nativeKind : KIND_UNSUPPORTED;
    }

    /** Tells the manager which track is active; null means no track (the "None" entry). */
    public void setActiveTrack(@Nullable VideoMetadata.SubtitleTrack track) {
        setSubtitleKind(kindOf(track));
    }

    public void setSubtitleKind(int kind) {
        if (mKind == kind) return;
        boolean wasGraphic = isGraphic();
        mKind = kind;
        if (log.isDebugEnabled()) log.debug("setSubtitleKind: {}", kind);
        if (wasGraphic != isGraphic()) {
            // Margins, insets and control-bar avoidance differ between bitmap and text tracks.
            adjustView();
        }
    }

    public int getSubtitleKind() { return mKind; }

    // ---- Kind rules -------------------------------------------------------------------
    // Static and pure: a function of the kind value only. Any caller can answer them straight
    // from a track, kindOf(track), without touching manager state or causing a layout pass.
    // The instance methods further down answer the same questions for the ACTIVE track.

    /** Kind of a track as native reported it; NONE for null (the "None" entry). */
    public static int kindOf(@Nullable VideoMetadata.SubtitleTrack track) {
        return track == null ? KIND_NONE : kindFromNative(track.kind);
    }

    /** Bitmap track (PGS, VobSub, DVD): own position/size, never margin-shifted or restyled. */
    public static boolean isGraphic(int kind) { return kind == KIND_GRAPHIC; }

    public static boolean isPlainText(int kind) { return kind == KIND_PLAIN_TEXT; }

    public static boolean isStyled(int kind) { return kind == KIND_SSA; }

    /** The user's text-style settings (size, colour, position, ...) can affect this kind. */
    public static boolean supportsUserStyle(int kind) { return kind == KIND_SSA || kind == KIND_PLAIN_TEXT; }

    /** Only styled subtitles have a style-mode choice (file's style / custom / scale only). */
    public static boolean canChooseOverrideMode(int kind) { return kind == KIND_SSA; }

    /**
     * The override mode actually in force. Native forces Custom for plain text whatever is
     * stored (sync_styles(): force_all = is_plain_text || mode == CUSTOM), so the stored mode
     * (the user's choice for styled files) must be left alone and only filtered here.
     */
    public static int effectiveOverrideMode(int kind, int storedMode) {
        return kind == KIND_PLAIN_TEXT ? OVERRIDE_CUSTOM : storedMode;
    }

    // Same answers for the active track.
    public boolean isGraphic() { return isGraphic(mKind); }
    public boolean isPlainText() { return isPlainText(mKind); }
    public boolean isStyled() { return isStyled(mKind); }
    public boolean supportsUserStyle() { return supportsUserStyle(mKind); }
    public boolean canChooseOverrideMode() { return canChooseOverrideMode(mKind); }

    /** getOverrideMode() filtered through the active kind: what the engine really applies. */
    public int getEffectiveOverrideMode() { return effectiveOverrideMode(mKind, mOverrideMode); }

    /** SurfaceController.SUBTITLE_CATEGORY_* for the active track. */
    public int getLayoutCategory() {
        switch (mKind) {
            case KIND_SSA:  return SurfaceController.SUBTITLE_CATEGORY_ASS;
            case KIND_GRAPHIC: return SurfaceController.SUBTITLE_CATEGORY_GFX;
            default:           return SurfaceController.SUBTITLE_CATEGORY_PLAIN_TEXT;
        }
    }

    // ---- Persisted style preferences ---------------------------------------------------
    // The one list of preference keys for the user's subtitle style. The strings are what is
    // stored on users' devices: never change them. PlayerActivity.KEY_SUBTITLE_* alias these.
    public static final String KEY_VPOS             = "pref_play_subtitle_vpos_key";
    public static final String KEY_COLOR            = "pref_play_subtitle_color_key";
    public static final String KEY_BG_OPACITY       = "subtitle_bg_opacity";
    public static final String KEY_BG_MODE          = "pref_play_subtitle_bg_mode_key";
    public static final String KEY_OVERRIDE_MODE    = "pref_play_subtitle_override_mode_key";
    public static final String KEY_BOLD             = "pref_play_subtitle_bold_key";
    public static final String KEY_OUTLINE_COLOR    = "pref_play_subtitle_outline_color_key";
    public static final String KEY_SHADOW_COLOR     = "pref_play_subtitle_shadow_color_key";
    public static final String KEY_BACKGROUND_COLOR = "pref_play_subtitle_background_color_key";
    public static final String KEY_OUTLINE_WIDTH    = "pref_play_subtitle_outline_width_key";
    public static final String KEY_SHADOW_WIDTH     = "pref_play_subtitle_shadow_width_key";
    public static final String KEY_FONT_SIZE_PT     = "pref_play_subtitle_font_size_pt_key";
    public static final String KEY_FONT_SCALE       = "pref_play_subtitle_font_scale_key";

    /**
     * Every preference that makes up the user's style. To reset to the defaults, remove these
     * from the preferences and call restoreStyle() (then apply the vertical position the same
     * way startup does): every key is gone, so everything falls back to its default.
     */
    public static final java.util.List<String> STYLE_KEYS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(KEY_VPOS, KEY_COLOR, KEY_BG_OPACITY, KEY_BG_MODE, KEY_OVERRIDE_MODE,
                    KEY_BOLD, KEY_OUTLINE_COLOR, KEY_SHADOW_COLOR, KEY_BACKGROUND_COLOR,
                    KEY_OUTLINE_WIDTH, KEY_SHADOW_WIDTH, KEY_FONT_SIZE_PT, KEY_FONT_SCALE));

    /**
     * The style a user gets until they change something. Values come from res/values/config.xml
     * (libass sizes text relative to the screen, so one value serves every device). This is the
     * only place they are read; the two modes are code constants so they cannot drift from the
     * OVERRIDE_* / BG_MODE_* values the native engine understands.
     */
    public static final class Defaults {
        public final int vpos, color, bgOpacity, fontSizePt, overrideMode, bgMode;
        public final int outlineColor, shadowColor, backgroundColor;
        public final float fontScale, outlineWidth, shadowWidth;
        public final boolean bold;

        public Defaults(Context context) {
            final android.content.res.Resources res = context.getResources();
            vpos = res.getInteger(R.integer.player_pref_subtitle_vpos_default);
            color = ContextCompat.getColor(context, R.color.subtitle_default_text_color);
            bgOpacity = res.getInteger(R.integer.subtitle_default_bg_opacity);
            fontSizePt = res.getInteger(R.integer.player_pref_subtitle_size_default);
            fontScale = res.getInteger(R.integer.subtitle_default_font_scale_percent) / 100f;
            bold = res.getBoolean(R.bool.subtitle_default_bold);
            outlineColor = ContextCompat.getColor(context, R.color.subtitle_default_outline_color);
            shadowColor = ContextCompat.getColor(context, R.color.subtitle_default_shadow_color);
            backgroundColor = ContextCompat.getColor(context, R.color.subtitle_default_background_color);
            outlineWidth = res.getInteger(R.integer.subtitle_default_outline_width);
            shadowWidth = res.getInteger(R.integer.subtitle_default_shadow_width);
            overrideMode = OVERRIDE_CUSTOM;
            bgMode = BG_MODE_FLOATING;
        }
    }

    public Defaults getDefaults() { return mDefaults; }

    /**
     * Short side, in pixels, of the surface the subtitles are laid out on. The settings preview
     * scales its text from it the way the renderer does (pt x short side / 720).
     */
    public int getScreenShortSide() {
        return Math.min(mScreenWidth, mScreenHeight);
    }

    /**
     * Applies the saved style, falling back to the default for anything never saved. This is
     * also how a reset works: remove STYLE_KEYS from the preferences first.
     * Does not touch the vertical position: callers apply it themselves because it is track-kind
     * aware (and scaled in multi-window); read it with KEY_VPOS and getDefaults().vpos.
     * The order is the one the engine has always been fed.
     */
    public void restoreStyle(SharedPreferences prefs) {
        final Defaults d = mDefaults;
        setColor(prefs.getInt(KEY_COLOR, d.color));
        setOverrideMode(prefs.getInt(KEY_OVERRIDE_MODE, d.overrideMode));
        setBgMode(prefs.getInt(KEY_BG_MODE, d.bgMode));
        setFontSizePt(prefs.getInt(KEY_FONT_SIZE_PT, d.fontSizePt));
        setFontScale(prefs.getFloat(KEY_FONT_SCALE, d.fontScale));
        setBold(prefs.getBoolean(KEY_BOLD, d.bold));
        setOutlineColor(prefs.getInt(KEY_OUTLINE_COLOR, d.outlineColor));
        setShadowColor(prefs.getInt(KEY_SHADOW_COLOR, d.shadowColor));
        setBackgroundColor(prefs.getInt(KEY_BACKGROUND_COLOR, d.backgroundColor));
        // after the background colour: setBackgroundColor() re-sends the opacity
        setBackgroundOpacity(prefs.getInt(KEY_BG_OPACITY, d.bgOpacity));
        setOutlineWidth(prefs.getFloat(KEY_OUTLINE_WIDTH, d.outlineWidth));
        setShadowWidth(prefs.getFloat(KEY_SHADOW_WIDTH, d.shadowWidth));
    }

    private boolean mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing;

    Surface                     mUiSurface;
    private static boolean mFullScreenWithCutout = true;

    private int mColor;
    private int mBgOpacity;

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

    /**
     * Sets the active font family. In Custom/Force override mode (and always for plain-text
     * SRT/VTT), this name is force-applied to every subtitle style -- see sync_styles() in
     * sub_format_ssa.c. To use a font from a custom fonts folder, pass its family name here
     * AFTER calling {@link #setFontsFolder(String)} with the folder containing it, so libass
     * has already registered the file and can resolve the name.
     */
    public void setFontFamily(String familyName) {
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setFontFamily(familyName);
        }
    }

    /**
     * Sets a custom fonts folder (MX Player / mpv-android style "third fonts folder"):
     * every .ttf/.otf/.ttc file found in {@code dirPath} is registered with libass and takes
     * priority over the system fontconfig database when a style names a matching font family.
     * Persisted to SharedPreferences so it survives across playback sessions. Takes effect
     * starting with the next track opened -- if subtitles are already playing, closing and
     * reopening the track (e.g. toggling the track off/on) makes it take effect immediately.
     * Pass null to disable and fall back to fontconfig-only resolution.
     */
    public void setFontsFolder(String dirPath) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(VideoPreferencesCommon.KEY_SUBTITLE_FONTS_FOLDER, dirPath)
                .apply();
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setFontsFolder(dirPath);
        }
    }

    /**
     * Sets the fallback family name libass uses when nothing else names a font -- this is
     * what plain SRT/VTT subtitles render with, since they carry no font info of their own.
     * Should name a file that's resolvable given the folder last passed to
     * {@link #setFontsFolder(String)}. Persisted across sessions; pass null to fall back to
     * the generic "sans-serif" fontconfig alias.
     */
    public void setDefaultFontName(String familyName) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(VideoPreferencesCommon.KEY_SUBTITLE_DEFAULT_FONT, familyName)
                .apply();
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().setDefaultFontName(familyName);
        }
    }

    public void setUIMode(int uiMode) {
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
        mDefaults = new Defaults(context);
        mPlayerView = playerView;
        mSubtitlePosHintDrawable = ContextCompat.getDrawable(context, com.archos.mediacenter.video.R.drawable.subtitle_baseline);
    }

    public void setScreenSize(int displayWidth, int displayHeight) {
        if (log.isDebugEnabled()) log.debug("setScreenSize: {}x{} isGraphic()={}, mSubtitleLayout={}", displayWidth, displayHeight, isGraphic(), (mSubtitleLayout == null ? "null" : "not null"));
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
        adjustView();
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

        mRootView = mSubtitleLayout.getRootView();
        // note OnApplyWindowInsetsListener does not update when navigation bar fades away, OnGlobalLayoutListener or addOnPreDrawListener are constantly triggering -> only setOnSystemUiVisibilityChangeListener works
        // however setOnSystemUiVisibilityChangeListener is unreliable on Android 6.0 thus use addOnLayoutChangeListener
        // in reality we need to do combination of setOnApplyWindowInsetsListener to get insets but not updated when UI mode changes and thus combine with setOnSystemUiVisibilityChangeListener

        // insets observer is needed for rotation
        mSubtitleLayout.setOnApplyWindowInsetsListener((v, insets) -> {
            if (log.isDebugEnabled()) log.debug("attachWindow, onApplyWindowInsetsListener, isGraphic()={}", isGraphic());
            adjustView();
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
            if (log.isDebugEnabled()) log.debug("attachWindow, setOnSystemUiVisibilityChangeListener: mNavigationBarShowing={}, mSystemBarShowing={}, mActionBarShowing={}, mControlBarShowing={}, mIsNavBarOnBottom={}, mIsGestureAreaShowing={}",
                    mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, PlayerController.isControlBarShowing(), mIsNavBarOnBottom, mIsGestureAreaShowing);
            adjustView();
        });


        mPlayerView.addView(mSubtitleLayout, mScreenWidth, mScreenHeight);
    }

    private void adjustView() {
        // Callers (setScreenSize(), the control-bar callback, ...) can fire before start()
        // has attached the layout, or after stop() detached it. There is nothing to pad then,
        // but the native renderer still needs the offset, so keep that part unconditional.
        if (mSubtitleLayout == null || mRootView == null) {
            applyNativeVerticalOffset();
            return;
        }
        // strategy is videoView avoids cutout if not in fullscreen
        // adjust subtitle text height (bottom/top) to avoid system bars and playerController bar only if text subtitle but not left/right
        boolean avoidCutout = ! mFullScreenWithCutout;
        boolean isFloatingPlayer = Player.sPlayer != null && Player.sPlayer.isFloatingPlayer();
        // Player.sPlayer.getSurfaceControllerWidth(), Player.sPlayer.getSurfaceControllerHeight() is for the videoView but virtualScreen is larger
        // do not apply globalShift if in floating player mode
        if (log.isDebugEnabled()) log.debug("adjustView: isGraphic()={}", isGraphic());
        mActionBarShowing = PlayerController.isActionBarShowing();
        MiscUtils.adjustViewLayoutForInsets(mContext, mRootView, mSubtitleLayout, "mSubtitleLayout",
                mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, PlayerController.isControlBarShowing(), mIsNavBarOnBottom, mIsGestureAreaShowing,
                (! isGraphic() && PlayerController.isControlBarShowing() ? PlayerController.getControlBarCurrentHeight() : 0), (isGraphic() ? 0 :mSubtitleEvadedVPos),
                false, ! isGraphic(), false, ! isGraphic(),
                avoidCutout, avoidCutout, avoidCutout, avoidCutout, ! isGraphic(), isGraphic() && ! isFloatingPlayer);
        // The inset pass above still positions mSubtitleLayout (and with it the position-hint
        // spacer), but text subtitles are drawn natively (libass on gl_subtitle_view), so it no
        // longer moves them. The obstruction is computed separately and fed to the renderer.
        applyNativeVerticalOffset();
    }

    public void onControlBarVisibilityChanged() {
        adjustView();
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
        mSubtitleVPos = pos; // the user's value; this is what gets persisted
        mSubtitleEvadedVPos = (mScreenHeight * pos / 765) + 1;
        applyNativeVerticalOffset();

        if (mSubtitleSpacer != null && mSubtitleSpacerParams != null) {
            mSubtitleSpacerParams.height = mSubtitleEvadedVPos;
            mSubtitleSpacer.setLayoutParams(mSubtitleSpacerParams);
        }
    }

    /**
     * The native subtitle canvas (gl_subtitle_view). This, not the activity root, is the
     * surface libass measures MarginV from, and SurfaceController sizes it differently per
     * mode: tethered to the video box (its bottom sits above the letterbox bar) or extended
     * over the whole parent when subtitles may use the bars. Measuring against the view itself
     * is therefore right in every mode and follows any future change to that sizing.
     */
    private View getSubtitleCanvas() {
        if (mGlSubtitleView == null && mPlayerView != null) {
            mGlSubtitleView = mPlayerView.findViewById(R.id.gl_subtitle_view);
            // The canvas moves/resizes without SubtitleManager being told (aspect-ratio or
            // use-margins change in SurfaceController.updateSurface()), and that changes how
            // much of the controls overlap it, so re-evaluate whenever it is laid out.
            if (mGlSubtitleView != null) mGlSubtitleView.addOnLayoutChangeListener(mCanvasLayoutListener);
        }
        return mGlSubtitleView;
    }

    /**
     * Pixels the system bottom area (navigation bar / gesture area / bottom cutout) overlaps the
     * bottom of the given canvas. Mirrors the inputs MiscUtils.adjustViewLayoutForInsets() used
     * for text subtitles before rendering moved native, so users get the familiar behaviour.
     */
    private int computeSystemBottomOverlap(View canvas) {
        int inset = 0;
        WindowInsets insets = mPlayerView.getRootWindowInsets();
        if (insets == null) return 0;
        WindowInsetsCompat compat = WindowInsetsCompat.toWindowInsetsCompat(insets, mPlayerView);
        if (! mFullScreenWithCutout) {
            DisplayCutoutCompat cutout = compat.getDisplayCutout();
            if (cutout != null) inset = cutout.getSafeInsetBottom();
        }
        boolean gestureArea = mIsGestureAreaShowing && PlayerController.isControlBarShowing();
        boolean navBar = mIsNavBarOnBottom && mNavigationBarShowing;
        if (gestureArea || navBar) {
            int systemBarBottom = compat.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int effective = gestureArea
                    ? Math.max(systemBarBottom, MiscUtils.getGestureAreaHeight(mContext))
                    : Math.max(systemBarBottom, MiscUtils.getNavigationBarHeight(mContext));
            inset = Math.max(inset, effective);
        }
        if (inset <= 0) return 0;
        int[] rootLoc = new int[2];
        int[] canvasLoc = new int[2];
        mPlayerView.getLocationOnScreen(rootLoc);
        canvas.getLocationOnScreen(canvasLoc);
        int rootBottom = rootLoc[1] + mPlayerView.getHeight();
        int canvasBottom = canvasLoc[1] + canvas.getHeight();
        return Math.max(canvasBottom - (rootBottom - inset), 0);
    }

    /**
     * Pixels above the canvas bottom that ordinary text subtitles must stay clear of: the
     * playback controls (seek bar included) or the system bottom area, whichever reaches
     * higher. 0 for bitmap subtitles (PGS/VobSub), which keep their own positioning.
     *
     * The control bar is measured from its real on-screen position rather than from
     * PlayerController.getControlBarCurrentHeight(), which reports 0 unless the navigation bar
     * or gesture area is showing (so it is 0 on TV) and is read before the bar is laid out on
     * the first show.
     */
    private int computeBottomObstruction() {
        if (isGraphic() || mPlayerView == null) return 0;
        View canvas = getSubtitleCanvas();
        if (canvas == null || ! canvas.isLaidOut() || canvas.getHeight() <= 0) return 0;
        return Math.max(PlayerController.getControlBarClearanceAbove(canvas),
                        computeSystemBottomOverlap(canvas));
    }

    /**
     * Pushes the effective bottom offset to the native renderer: the user's saved position, or
     * the obstruction if that reaches higher (the same max() the legacy layout applied, so text
     * that already sits above the controls does not move). Font size is untouched.
     *
     * The obstruction is transient. It is never stored in mSubtitleEvadedVPos / mSubtitleVPos
     * and never written to SharedPreferences, so the saved position is unchanged and text
     * returns to it when the controls hide.
     *
     * Deliberately unconditional: an unchanged value is a no-op natively (the style serial only
     * bumps when the margin actually changes, see sub_style_set_margin_bottom()), so this can
     * be called from every layout/visibility/inset path for the cost of one JNI call. The
     * setter also wakes the render thread, so it takes effect while playback is paused.
     */
    private void applyNativeVerticalOffset() {
        if (Player.sPlayer == null || Player.sPlayer.getSubtitleEngine() == null) return;
        int offset = Math.max(mSubtitleEvadedVPos, computeBottomObstruction());
        Player.sPlayer.getSubtitleEngine().setVerticalOffset(offset);
    }

}
