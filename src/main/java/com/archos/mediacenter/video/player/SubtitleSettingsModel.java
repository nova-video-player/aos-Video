// Copyright 2026 Courville Software
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.archos.mediacenter.video.R;

import java.util.ArrayList;
import java.util.List;

/**
 * What the subtitle settings contain, for which kind of subtitle and which style mode, and how a
 * change is applied and saved. No views in here: the phone (SubtitleSettingsDialog) and the TV
 * (TvSubtitleSettings) each draw the rows this model returns, with their own widgets and navigation.
 *
 * The manager is the truth, a preference is written on every change. The row rules follow
 * sync_styles() in sub_format_ssa.c: the user style is only in force when the effective override
 * mode is Custom, and the same stored widths mean different things per background mode.
 */
public class SubtitleSettingsModel {

    public interface IntGet { int get(); }

    public interface IntSet { void set(int v); }

    public interface Fmt { String format(int v); }

    /** Something changed on its own (the Reset confirmation timed out): redraw the rows. */
    public interface Listener { void onModelChanged(); }

    public enum Type { PILLS, STEPPER, TOGGLE, SLIDER, COLOR, ACTION, NOTE, SECTION, GRID }

    private static final int P_NONE = 0, P_INT = 1, P_FLOAT = 2, P_BOOL = 3, P_FLOAT_PERCENT = 4;

    /** One adjustable value: the manager is the truth, the preference is written on every change. */
    public final class Setting {
        final String key;
        final int pref;
        public final int min, max, step;
        public final IntGet get;
        final IntSet set;
        Runnable onChanged;

        Setting(String key, int pref, int min, int max, int step, IntGet get, IntSet set) {
            this.key = key;
            this.pref = pref;
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
        }

        public int get() {
            return get.get();
        }

        /** Clamps, applies to the manager, saves. */
        public void apply(int value) {
            final int v = Math.max(min, Math.min(max, value));
            if (v == get.get()) return;
            set.set(v);
            if (key != null) {
                final SharedPreferences.Editor e = mPrefs.edit();
                switch (pref) {
                    case P_INT: e.putInt(key, v); break;
                    case P_FLOAT: e.putFloat(key, v); break;
                    case P_BOOL: e.putBoolean(key, v != 0); break;
                    case P_FLOAT_PERCENT: e.putFloat(key, v / 100f); break;
                    default: break;
                }
                e.apply();
            }
            if (onChanged != null) onChanged.run();
        }
    }

    /** A color the user can edit. Picking replaces the RGB only: the alpha it already has is kept. */
    public final class ColorTarget {
        final String key;
        public final int labelRes;
        final IntGet get;
        final IntSet set;

        ColorTarget(String key, int labelRes, IntGet get, IntSet set) {
            this.key = key;
            this.labelRes = labelRes;
            this.get = get;
            this.set = set;
        }

        public int get() {
            return get.get();
        }

        public void apply(int argb) {
            if (argb == get.get()) return;
            set.set(argb);
            mPrefs.edit().putInt(key, argb).apply();
        }

        public void applyRgb(int rgb) {
            apply((get.get() & 0xFF000000) | (rgb & 0x00FFFFFF));
        }
    }

    public static final class Row {
        public final String id;
        public final Type type;
        public int labelRes;
        public Setting setting;
        public ColorTarget color;
        public int[] options;
        public Fmt fmt;

        Row(String id, Type type) {
            this.id = id;
            this.type = type;
        }
    }

    private final Context mContext;
    private final SubtitleManager mManager;
    private final SharedPreferences mPrefs;
    private final boolean mTv;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Listener mListener;

    private final Setting mSize, mScale, mBold, mBg, mMode, mVpos, mOutlineW, mShadowW, mPadding, mOpacity;
    private final Setting mAdvancedSetting;
    private final ColorTarget mTextColor, mOutlineColor, mShadowColor, mBackgroundColor;

    private boolean mAdvanced;      // only which rows are shown: turning it off never resets a value
    private boolean mResetArmed;
    private boolean mDraggingVpos;

    private final Runnable mDisarmReset = new Runnable() {
        @Override
        public void run() {
            mResetArmed = false;
            if (mListener != null) mListener.onModelChanged();
        }
    };
    private final Runnable mHideHint = new Runnable() {
        @Override
        public void run() {
            mManager.fadeSubtitlePositionHint(false);
        }
    };

    public SubtitleSettingsModel(Context context, SubtitleManager manager, SharedPreferences prefs, boolean tv) {
        mContext = context;
        mManager = manager;
        mPrefs = prefs;
        mTv = tv;

        final SubtitleManager m = manager;
        mSize = new Setting(SubtitleManager.KEY_FONT_SIZE_PT, P_INT, 1, 250, 1, m::getFontSizePt, m::setFontSizePt);
        mScale = new Setting(SubtitleManager.KEY_FONT_SCALE, P_FLOAT_PERCENT, 10, 300, 10,
                () -> Math.round(m.getFontScale() * 100f), v -> m.setFontScale(v / 100f));
        mBold = new Setting(SubtitleManager.KEY_BOLD, P_BOOL, 0, 1, 1,
                () -> m.getBold() ? 1 : 0, v -> m.setBold(v != 0));
        mBg = new Setting(SubtitleManager.KEY_BG_MODE, P_INT, 0, 2, 1, m::getBgMode, m::setBgMode);
        mMode = new Setting(SubtitleManager.KEY_OVERRIDE_MODE, P_INT, 0, 2, 1, m::getOverrideMode, m::setOverrideMode);
        mVpos = new Setting(SubtitleManager.KEY_VPOS, P_INT, 0, 255, 1, m::getVerticalPosition, m::setVerticalPosition);
        mVpos.onChanged = new Runnable() {
            @Override
            public void run() {
                if (!mDraggingVpos) { // keys / buttons: flash the position hint, as the old dialogs did
                    mManager.fadeSubtitlePositionHint(true);
                    mHandler.removeCallbacks(mHideHint);
                    mHandler.postDelayed(mHideHint, 200);
                }
            }
        };
        mOutlineW = new Setting(SubtitleManager.KEY_OUTLINE_WIDTH, P_FLOAT, 0, 50, 1,
                () -> Math.round(m.getOutlineWidth()), m::setOutlineWidth);
        mShadowW = new Setting(SubtitleManager.KEY_SHADOW_WIDTH, P_FLOAT, 0, 50, 1,
                () -> Math.round(m.getShadowWidth()), m::setShadowWidth);
        mPadding = new Setting(SubtitleManager.KEY_PADDING, P_FLOAT, 1, 50, 1,
                () -> Math.round(m.getPadding()), m::setPadding);
        mOpacity = new Setting(SubtitleManager.KEY_BG_OPACITY, P_INT, 0, 255, 13,
                m::getBackgroundOpacity, m::setBackgroundOpacity);
        mAdvancedSetting = new Setting(null, P_NONE, 0, 1, 1,
                () -> mAdvanced ? 1 : 0, v -> mAdvanced = v != 0);

        mTextColor = new ColorTarget(SubtitleManager.KEY_COLOR, R.string.subtitle_panel_text_color, m::getColor, m::setColor);
        mOutlineColor = new ColorTarget(SubtitleManager.KEY_OUTLINE_COLOR, R.string.subtitle_panel_outline_color,
                m::getOutlineColor, m::setOutlineColor);
        mShadowColor = new ColorTarget(SubtitleManager.KEY_SHADOW_COLOR, R.string.subtitle_panel_shadow_color,
                m::getShadowColor, m::setShadowColor);
        mBackgroundColor = new ColorTarget(SubtitleManager.KEY_BACKGROUND_COLOR, R.string.subtitle_panel_background_color,
                m::getBackgroundColor, m::setBackgroundColor);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public SubtitleManager getManager() {
        return mManager;
    }

    public boolean isTv() {
        return mTv;
    }

    /** The vertical position slider is being dragged: the hint stays up instead of flashing. */
    public void setDraggingVpos(boolean dragging) {
        mDraggingVpos = dragging;
        mHandler.removeCallbacks(mHideHint);
        mManager.fadeSubtitlePositionHint(dragging);
    }

    /**
     * Call when the settings are shown. The blue position line is the background of the manager's
     * spacer view, and it only has one once this is on; fading it in and out (the vertical position
     * rows do it) shows it. The old dialogs switched it on when they opened.
     */
    public void open() {
        mManager.setShowSubtitlePositionHint(true);
    }

    /** Call when the settings are closed. */
    public void release() {
        mHandler.removeCallbacks(mDisarmReset);
        mHandler.removeCallbacks(mHideHint);
        mManager.fadeSubtitlePositionHint(false);
    }

    // ---- reset --------------------------------------------------------------------------------

    public boolean isResetArmed() {
        return mResetArmed;
    }

    /** First press arms (a label asks for confirmation), second press within 3 s resets the whole style. */
    public void onResetPressed() {
        if (!mResetArmed) {
            mResetArmed = true;
            mHandler.removeCallbacks(mDisarmReset);
            mHandler.postDelayed(mDisarmReset, 3000);
            return;
        }
        mHandler.removeCallbacks(mDisarmReset);
        mResetArmed = false;
        // A reset is "forget the saved style, then restore": every key is gone, so the defaults apply.
        final SharedPreferences.Editor e = mPrefs.edit();
        for (String key : SubtitleManager.STYLE_KEYS) e.remove(key);
        e.apply();
        mManager.restoreStyle(mPrefs);
        mManager.setVerticalPosition(mPrefs.getInt(SubtitleManager.KEY_VPOS, mManager.getDefaults().vpos));
    }

    /** The label of the Reset row: it asks for a second press while armed. */
    public int getResetLabelRes() {
        if (!mResetArmed) return R.string.subtitle_panel_reset;
        return mTv ? R.string.subtitle_panel_reset_confirm_tv : R.string.subtitle_panel_reset_confirm_touch;
    }

    // ---- rows ---------------------------------------------------------------------------------

    /** The title of a page: the color's name on a color page, the panel's title on the main one. */
    public int getTitleRes(ColorTarget colorPage) {
        return colorPage != null ? colorPage.labelRes : R.string.subtitle_panel_title;
    }

    /** The rows of the main page when {@code colorPage} is null, else the color page of that target. */
    public List<Row> rows(ColorTarget colorPage) {
        final ArrayList<Row> rows = new ArrayList<>();
        if (colorPage != null) {
            colorRows(rows, colorPage);
        } else {
            mainRows(rows);
        }
        return rows;
    }

    private void mainRows(List<Row> rows) {
        final int kind = mManager.getSubtitleKind();
        if (!mManager.supportsUserStyle()) {
            final int note;
            switch (kind) {
                case SubtitleManager.KIND_GRAPHIC: note = R.string.subtitle_panel_note_graphic; break;
                case SubtitleManager.KIND_UNSUPPORTED: note = R.string.subtitle_panel_note_unsupported; break;
                default: note = R.string.subtitle_panel_note_none; break;
            }
            rows.add(note("note_unavailable", note));
            return;
        }
        final int mode = mManager.getEffectiveOverrideMode();
        if (mManager.canChooseOverrideMode()) {
            rows.add(pills("mode", R.string.subtitle_panel_style, mMode,
                    R.string.subtitle_panel_mode_file, R.string.subtitle_panel_mode_custom, R.string.subtitle_panel_mode_scale));
            rows.add(note("note_mode", mode == SubtitleManager.OVERRIDE_EMBEDDED ? R.string.subtitle_panel_caption_file
                    : mode == SubtitleManager.OVERRIDE_SCALE_ONLY ? R.string.subtitle_panel_caption_scale
                    : R.string.subtitle_panel_caption_custom));
        } else {
            rows.add(note("note_plain_text", R.string.subtitle_panel_note_plain_text));
        }
        if (mode == SubtitleManager.OVERRIDE_SCALE_ONLY) {
            rows.add(stepper("scale", R.string.subtitle_panel_scale, mScale,
                    v -> mContext.getString(R.string.subtitle_panel_value_percent, v)));
        } else if (mode == SubtitleManager.OVERRIDE_CUSTOM) {
            rows.add(stepper("size", R.string.subtitle_panel_font_size, mSize,
                    v -> mContext.getString(R.string.subtitle_panel_value_pt, v)));
            rows.add(toggle("bold", R.string.subtitle_panel_bold, mBold));
            rows.add(pills("bg", R.string.subtitle_panel_background, mBg,
                    R.string.subtitle_panel_bg_off, R.string.subtitle_panel_bg_line, R.string.subtitle_panel_bg_box));
            rows.add(slider("vpos", R.string.subtitle_panel_vertical_position, mVpos));
            rows.add(toggle("advanced", R.string.subtitle_panel_advanced, mAdvancedSetting));
            if (mAdvanced) advancedRows(rows);
        }
        rows.add(reset());
    }

    /** Appended below the Advanced switch; size, bold, background and position stay above it. */
    private void advancedRows(List<Row> rows) {
        final Fmt plain = v -> Integer.toString(v);
        final Fmt percent = v -> mContext.getString(R.string.subtitle_panel_value_percent, Math.round(v * 100f / 255f));
        final int bg = mManager.getBgMode();
        // The same stored widths mean different things per mode (see sync_styles() in sub_format_ssa.c):
        //   Off : outline_width = outline, shadow_width = shadow
        //   Line: padding = box padding (outline / shadow unused)
        //   Box : outline_width = outline around the glyphs, padding = box padding
        rows.add(section("sec_text", R.string.subtitle_panel_section_text));
        rows.add(color("text_c", mTextColor));
        if (bg == SubtitleManager.BG_MODE_BOXED_LINE) {
            rows.add(section("sec_background", R.string.subtitle_panel_background));
            rows.add(color("bg_c", mBackgroundColor));
            rows.add(stepper("opacity", R.string.subtitle_panel_background_opacity, mOpacity, percent));
            rows.add(stepper("padding", R.string.subtitle_panel_padding, mPadding, plain));
        } else if (bg == SubtitleManager.BG_MODE_BOXED_BLOCK) {
            rows.add(stepper("outline_w", R.string.subtitle_panel_outline_width, mOutlineW, plain));
            rows.add(color("outline_c", mOutlineColor));
            rows.add(section("sec_background", R.string.subtitle_panel_background));
            rows.add(color("bg_c", mBackgroundColor));
            rows.add(stepper("opacity", R.string.subtitle_panel_background_opacity, mOpacity, percent));
            rows.add(stepper("padding", R.string.subtitle_panel_padding, mPadding, plain));
        } else {
            rows.add(stepper("outline_w", R.string.subtitle_panel_outline_width, mOutlineW, plain));
            rows.add(color("outline_c", mOutlineColor));
            rows.add(stepper("shadow_w", R.string.subtitle_panel_shadow_width, mShadowW, plain));
            rows.add(color("shadow_c", mShadowColor));
        }
    }

    /** Preset grid first (the quick path), the RGB steppers below it (secondary). */
    private void colorRows(List<Row> rows, ColorTarget target) {
        final Row grid = new Row("grid", Type.GRID);
        grid.color = target;
        rows.add(grid);
        rows.add(channel("rgb_r", R.string.subtitle_panel_red, target, 16));
        rows.add(channel("rgb_g", R.string.subtitle_panel_green, target, 8));
        rows.add(channel("rgb_b", R.string.subtitle_panel_blue, target, 0));
    }

    private Row note(String id, int res) {
        final Row r = new Row(id, Type.NOTE);
        r.labelRes = res;
        return r;
    }

    private Row section(String id, int res) {
        final Row r = new Row(id, Type.SECTION);
        r.labelRes = res;
        return r;
    }

    private Row pills(String id, int label, Setting s, int... options) {
        final Row r = new Row(id, Type.PILLS);
        r.labelRes = label;
        r.setting = s;
        r.options = options;
        return r;
    }

    private Row stepper(String id, int label, Setting s, Fmt fmt) {
        final Row r = new Row(id, Type.STEPPER);
        r.labelRes = label;
        r.setting = s;
        r.fmt = fmt;
        return r;
    }

    private Row toggle(String id, int label, Setting s) {
        final Row r = new Row(id, Type.TOGGLE);
        r.labelRes = label;
        r.setting = s;
        return r;
    }

    private Row slider(String id, int label, Setting s) {
        final Row r = new Row(id, Type.SLIDER);
        r.labelRes = label;
        r.setting = s;
        return r;
    }

    private Row color(String id, ColorTarget target) {
        final Row r = new Row(id, Type.COLOR);
        r.labelRes = target.labelRes;
        r.color = target;
        return r;
    }

    private Row reset() {
        final Row r = new Row("reset", Type.ACTION);
        r.labelRes = R.string.subtitle_panel_reset;
        return r;
    }

    /** One of the R / G / B components of a color, as a stepper (alpha untouched). */
    private Row channel(String id, int label, final ColorTarget target, final int shift) {
        final Row r = new Row(id, Type.STEPPER);
        r.labelRes = label;
        r.setting = new Setting(null, P_NONE, 0, 255, 1,
                () -> (target.get.get() >> shift) & 0xFF,
                v -> target.apply((target.get.get() & ~(0xFF << shift)) | (v << shift)));
        r.fmt = v -> Integer.toString(v);
        return r;
    }
}
