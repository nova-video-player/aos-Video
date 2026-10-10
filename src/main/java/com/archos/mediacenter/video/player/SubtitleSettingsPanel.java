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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.SwitchCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.tvmenu.TVScrollView;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The subtitle settings UI for phone and TV: one view, three levels (Basic, Advanced appearance,
 * Color), built from the row layouts in res/layout/subtitle_row_*.xml. The phone dialog and the TV
 * overlay only host it and pick the theme overlay (ThemeOverlay.SubtitlePanel.Phone / .Tv).
 *
 * Nothing is mirrored here: every value is read from, and written to, SubtitleManager, and each
 * change is saved to the preferences straight away (the keys are SubtitleManager.KEY_*).
 * Which rows exist is decided in rowsFor(): the kind and mode rules come from SubtitleManager
 * (getSubtitleKind(), canChooseOverrideMode(), getEffectiveOverrideMode()), the layout of them is
 * the only thing decided here.
 *
 * Stored values double as pill positions: OVERRIDE_EMBEDDED/CUSTOM/SCALE_ONLY and
 * BG_MODE_FLOATING/BOXED_LINE/BOXED_BLOCK are 0, 1, 2 (SubtitleSettingsPanelTest guards that).
 */
public class SubtitleSettingsPanel extends LinearLayout {

    public interface Host {
        /** The panel was closed from its own header (Basic level). */
        void onCloseRequested();

        /** Short side, in pixels, of the surface the subtitles render on (for the preview). */
        int getVideoShortSide();

        boolean isTv();
    }

    // ---- model --------------------------------------------------------------------------------

    private enum Type { PILLS, STEPPER, TOGGLE, SLIDER, COLOR, NAV, ACTION, NOTE, SECTION, GRID, HEX }

    private enum Level { BASIC, ADVANCED, COLOR }

    private interface IntGet { int get(); }

    private interface IntSet { void set(int v); }

    private interface Fmt { String format(int v); }

    private static final int P_NONE = 0, P_INT = 1, P_FLOAT = 2, P_BOOL = 3, P_FLOAT_PERCENT = 4;

    /** One adjustable value: the manager is the truth, the preference is written on every change. */
    private final class Setting {
        final String key;
        final int pref, min, max, step;
        final IntGet get;
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

        void apply(int value) {
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
    private final class ColorTarget {
        final String key;
        final int labelRes;
        final IntGet get;
        final IntSet set;

        ColorTarget(String key, int labelRes, IntGet get, IntSet set) {
            this.key = key;
            this.labelRes = labelRes;
            this.get = get;
            this.set = set;
        }

        void apply(int argb) {
            if (argb == get.get()) return;
            set.set(argb);
            mPrefs.edit().putInt(key, argb).apply();
        }

        void applyRgb(int rgb) {
            apply((get.get() & 0xFF000000) | (rgb & 0x00FFFFFF));
        }
    }

    private static final class Row {
        final String id;
        final Type type;
        int labelRes;
        Setting setting;
        ColorTarget color;
        int[] options;
        Fmt fmt;
        Level target;

        Row(String id, Type type) {
            this.id = id;
            this.type = type;
        }
    }

    private static final class Frame {
        final Level level;
        final ColorTarget color;
        String focusId;

        Frame(Level level, ColorTarget color) {
            this.level = level;
            this.color = color;
        }
    }

    // ---- state --------------------------------------------------------------------------------

    private SubtitleManager mManager;
    private SharedPreferences mPrefs;
    private Host mHost;

    private SubtitlePreviewView mPreview;
    private ViewGroup mRows;
    private TextView mTitle;
    private ImageView mBack;

    private Setting mSize, mScale, mBold, mBg, mMode, mVpos, mOutlineW, mShadowW, mOpacity;
    private ColorTarget mTextColor, mOutlineColor, mShadowColor, mBackgroundColor;

    private final ArrayList<Frame> mStack = new ArrayList<>();
    private final java.util.HashMap<String, View> mViews = new java.util.HashMap<>();
    private final ArrayList<String> mRowIds = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mBinding;
    private boolean mUsingKeys;
    private boolean mResetArmed;
    private boolean mDraggingVpos;
    private int mMaxHeight;
    private ScrollView mScroll;
    private SubtitleSettingsPanel mMirror;   // the 3D right-hand copy of this panel, if any
    private boolean mIsMirror;

    private final Runnable mDisarmReset = new Runnable() {
        @Override
        public void run() {
            mResetArmed = false;
            refresh();
        }
    };
    private final Runnable mHideHint = new Runnable() {
        @Override
        public void run() {
            if (mManager != null) mManager.fadeSubtitlePositionHint(false);
        }
    };

    public SubtitleSettingsPanel(Context context) {
        super(context);
    }

    public SubtitleSettingsPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SubtitleSettingsPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPreview = findViewById(R.id.sp_preview);
        mRows = findViewById(R.id.sp_rows);
        mTitle = findViewById(R.id.sp_title);
        mBack = findViewById(R.id.sp_back);
        mScroll = findViewById(R.id.sp_scroll);
        mBack.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!handleBack() && mHost != null) mHost.onCloseRequested();
            }
        });
    }

    /** A float resource declared as {@code <item type="dimen" format="float">} (the docking fractions). */
    public static float fractionRes(Resources res, int id) {
        final TypedValue tv = new TypedValue();
        res.getValue(id, tv, true);
        return tv.getFloat();
    }

    // ---- public API ---------------------------------------------------------------------------

    /** Connects the panel to the manager and the preferences, and shows the Basic level. */
    public void attach(final SubtitleManager manager, SharedPreferences prefs, Host host) {
        mManager = manager;
        mPrefs = prefs;
        mHost = host;
        mUsingKeys = host.isTv();

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
                if (!mDraggingVpos) { // keyboard / buttons: flash the position hint, as the old dialogs did
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
        mOpacity = new Setting(SubtitleManager.KEY_BG_OPACITY, P_INT, 0, 255, 13,
                m::getBackgroundOpacity, m::setBackgroundOpacity);

        mTextColor = new ColorTarget(SubtitleManager.KEY_COLOR, R.string.subtitle_panel_text_color, m::getColor, m::setColor);
        mOutlineColor = new ColorTarget(SubtitleManager.KEY_OUTLINE_COLOR, R.string.subtitle_panel_outline_color,
                m::getOutlineColor, m::setOutlineColor);
        mShadowColor = new ColorTarget(SubtitleManager.KEY_SHADOW_COLOR, R.string.subtitle_panel_shadow_color,
                m::getShadowColor, m::setShadowColor);
        mBackgroundColor = new ColorTarget(SubtitleManager.KEY_BACKGROUND_COLOR, R.string.subtitle_panel_background_color,
                m::getBackgroundColor, m::setBackgroundColor);

        mPreview.bind(manager, new SubtitlePreviewView.ShortSideProvider() {
            @Override
            public int getShortSide() {
                return mHost != null ? mHost.getVideoShortSide() : 0;
            }
        });

        mStack.clear();
        mStack.add(new Frame(Level.BASIC, null));
        showLevel(null);
    }

    /** First focus when the panel is shown with a remote (the host calls it once the panel is in the window). */
    public void requestInitialFocus() {
        focusRow(null);
    }

    // ---- 3D: the right-hand copy ----------------------------------------------------------------
    // PlayerController mirrors its TV overlays into a second controller view for side-by-side 3D.
    // The mirror is another panel on the same manager: it follows this one's level and rows
    // (refresh() syncs it), shows which row is focused (activated state) and scrolls with it.
    // It takes no input.

    /**
     * Attaches this panel as the 3D copy of {@code master}: same manager and preferences, but it
     * only follows the master (rows, level, focused row, scroll) and takes no focus or touch.
     */
    public void attachAsMirror(SubtitleSettingsPanel master, SubtitleManager manager,
                               SharedPreferences prefs, Host host) {
        mIsMirror = true; // before attach(), so it never asks for focus
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        attach(manager, prefs, host);
        master.linkMirror(this);
    }

    private void linkMirror(SubtitleSettingsPanel mirror) {
        mMirror = mirror;
        if (mScroll instanceof TVScrollView) {
            ((TVScrollView) mScroll).setOnScrollListener(new com.archos.mediacenter.video.player.tvmenu.TVOnScrollListener() {
                @Override
                public void onScrollChanged() {
                    if (mMirror != null) mMirror.mScroll.scrollTo(0, mScroll.getScrollY());
                }
            });
        }
        syncMirror();
    }

    private void syncMirror() {
        if (mMirror == null) return;
        mMirror.followFrames(mStack);
        final Row focused = rowOf(findFocus());
        mMirror.showMirrorFocus(focused != null ? focused.id : null);
    }

    private void followFrames(List<Frame> frames) {
        final Frame before = top();
        mStack.clear();
        mStack.addAll(frames);
        final Frame after = top();
        if (before.level != after.level || before.color != after.color) {
            mViews.clear();
            mRows.removeAllViews();
        }
        refresh();
    }

    private void showMirrorFocus(String rowId) {
        for (View v : mViews.values()) v.setActivated(false);
        final View v = rowId != null ? mViews.get(rowId) : null;
        if (v != null) v.setActivated(true);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        if (mMirror != null) {
            final Row r = rowOf(focused);
            mMirror.showMirrorFocus(r != null ? r.id : null);
        }
    }

    /**
     * Caps the panel's height (px). The panel is only as tall as its rows need, up to this; the
     * rows scroll beyond it. Ignored when the host gives an exact height (the docked TV panel).
     */
    public void setMaxHeight(int px) {
        mMaxHeight = px;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mMaxHeight > 0 && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            final int cap = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? mMaxHeight : Math.min(MeasureSpec.getSize(heightMeasureSpec), mMaxHeight);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Goes up one level. Returns false when already on Basic: the host then closes itself. */
    public boolean handleBack() {
        if (mStack.size() <= 1) return false;
        mStack.remove(mStack.size() - 1);
        showLevel(top().focusId);
        return true;
    }

    /** Re-reads everything from the manager (call it when the active track, and so the kind, changes). */
    public void refresh() {
        if (mManager == null) return;
        final Frame frame = top();
        // Advanced only exists while the effective mode is Custom
        if (frame.level == Level.ADVANCED && mManager.getEffectiveOverrideMode() != SubtitleManager.OVERRIDE_CUSTOM) {
            while (mStack.size() > 1) mStack.remove(mStack.size() - 1);
            showLevel(null);
            return;
        }
        final List<Row> rows = rowsFor(frame);
        updateHeader(frame);

        mBinding = true;
        try {
            final HashSet<String> ids = new HashSet<>();
            mRowIds.clear();
            for (int i = 0; i < rows.size(); i++) {
                final Row r = rows.get(i);
                ids.add(r.id);
                mRowIds.add(r.id);
                View v = mViews.get(r.id);
                if (v == null) {
                    v = createView(r);
                    mViews.put(r.id, v);
                }
                v.setTag(r);
                bind(r, v);
                if (mRows.getChildAt(i) != v) {
                    final ViewGroup parent = (ViewGroup) v.getParent();
                    if (parent != null) parent.removeView(v);
                    mRows.addView(v, i);
                }
            }
            while (mRows.getChildCount() > rows.size()) mRows.removeViewAt(rows.size());
            mViews.keySet().retainAll(ids);
        } finally {
            mBinding = false;
        }
        mPreview.invalidate();
        syncMirror();
    }

    @VisibleForTesting
    List<String> getRowIds() {
        return new ArrayList<>(mRowIds);
    }

    @VisibleForTesting
    void onResetPressed() {
        if (!mResetArmed) {
            mResetArmed = true;
            mHandler.removeCallbacks(mDisarmReset);
            mHandler.postDelayed(mDisarmReset, 3000);
            refresh();
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
        refresh();
    }

    // ---- rows: what exists, for which kind and mode --------------------------------------------

    private Frame top() {
        return mStack.get(mStack.size() - 1);
    }

    private List<Row> rowsFor(Frame frame) {
        final ArrayList<Row> rows = new ArrayList<>();
        switch (frame.level) {
            case ADVANCED:
                advancedRows(rows);
                break;
            case COLOR:
                colorRows(rows, frame.color);
                break;
            default:
                basicRows(rows);
                break;
        }
        return rows;
    }

    private void basicRows(List<Row> rows) {
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
            rows.add(stepper("scale", R.string.subtitle_panel_scale, mScale, v -> getContext().getString(R.string.subtitle_panel_value_percent, v)));
        } else if (mode == SubtitleManager.OVERRIDE_CUSTOM) {
            rows.add(stepper("size", R.string.subtitle_panel_font_size, mSize, v -> getContext().getString(R.string.subtitle_panel_value_pt, v)));
            rows.add(toggle("bold", R.string.subtitle_panel_bold, mBold));
            rows.add(pills("bg", R.string.subtitle_panel_background, mBg,
                    R.string.subtitle_panel_bg_off, R.string.subtitle_panel_bg_line, R.string.subtitle_panel_bg_box));
            rows.add(slider("vpos", R.string.subtitle_panel_vertical_position, mVpos));
            final Row adv = new Row("advanced", Type.NAV);
            adv.labelRes = R.string.subtitle_panel_advanced;
            adv.target = Level.ADVANCED;
            rows.add(adv);
        }
        rows.add(reset());
    }

    private void advancedRows(List<Row> rows) {
        final Fmt plain = v -> Integer.toString(v);
        final int bg = mManager.getBgMode();
        rows.add(section("sec_text", R.string.subtitle_panel_section_text));
        rows.add(stepper("size", R.string.subtitle_panel_font_size, mSize, v -> getContext().getString(R.string.subtitle_panel_value_pt, v)));
        rows.add(toggle("bold", R.string.subtitle_panel_bold, mBold));
        rows.add(color("text_c", mTextColor));
        rows.add(section("sec_background", R.string.subtitle_panel_section_background));
        rows.add(pills("bg", R.string.subtitle_panel_background, mBg,
                R.string.subtitle_panel_bg_off, R.string.subtitle_panel_bg_line, R.string.subtitle_panel_bg_box));
        // The same stored widths mean different things per mode (see sync_styles() in sub_format_ssa.c):
        //   Off : outline_width = outline, shadow_width = shadow
        //   Line: outline_width = box padding
        //   Box : outline_width = outline around the glyphs, shadow_width = box padding
        if (bg == SubtitleManager.BG_MODE_BOXED_LINE) {
            rows.add(color("bg_c", mBackgroundColor));
            rows.add(stepper("opacity", R.string.subtitle_panel_background_opacity, mOpacity, v -> getContext().getString(R.string.subtitle_panel_value_percent, Math.round(v * 100f / 255f))));
            rows.add(stepper("outline_w", R.string.subtitle_panel_padding, mOutlineW, plain));
        } else if (bg == SubtitleManager.BG_MODE_BOXED_BLOCK) {
            rows.add(color("bg_c", mBackgroundColor));
            rows.add(stepper("opacity", R.string.subtitle_panel_background_opacity, mOpacity, v -> getContext().getString(R.string.subtitle_panel_value_percent, Math.round(v * 100f / 255f))));
            rows.add(stepper("outline_w", R.string.subtitle_panel_outline_width, mOutlineW, plain));
            rows.add(color("outline_c", mOutlineColor));
            rows.add(stepper("shadow_w", R.string.subtitle_panel_padding, mShadowW, plain));
        } else {
            rows.add(stepper("outline_w", R.string.subtitle_panel_outline_width, mOutlineW, plain));
            rows.add(color("outline_c", mOutlineColor));
            rows.add(stepper("shadow_w", R.string.subtitle_panel_shadow_width, mShadowW, plain));
            rows.add(color("shadow_c", mShadowColor));
        }
        rows.add(section("sec_position", R.string.subtitle_panel_section_position));
        rows.add(slider("vpos", R.string.subtitle_panel_vertical_position, mVpos));
        rows.add(reset());
    }

    private void colorRows(List<Row> rows, final ColorTarget target) {
        final Row grid = new Row("grid", Type.GRID);
        grid.color = target;
        rows.add(grid);
        rows.add(channel("rgb_r", R.string.subtitle_panel_red, target, 16));
        rows.add(channel("rgb_g", R.string.subtitle_panel_green, target, 8));
        rows.add(channel("rgb_b", R.string.subtitle_panel_blue, target, 0));
        if (!mHost.isTv()) { // typing hex with a remote is painful and pops the keyboard up while navigating
            final Row hex = new Row("hex", Type.HEX);
            hex.color = target;
            rows.add(hex);
        }
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

    // ---- views --------------------------------------------------------------------------------

    private View inflate(int layout, ViewGroup parent) {
        return LayoutInflater.from(getContext()).inflate(layout, parent, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private View createView(Row row) {
        final View v;
        switch (row.type) {
            case PILLS: {
                v = inflate(R.layout.subtitle_row_pills, mRows);
                final ViewGroup pills = v.findViewById(R.id.sp_pills);
                for (int i = 0; i < row.options.length; i++) {
                    final int index = i;
                    final View seg = inflate(R.layout.subtitle_row_pill_segment, pills);
                    seg.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View s) {
                            onUserSet(((Row) v.getTag()).setting, index);
                        }
                    });
                    pills.addView(seg);
                }
                break;
            }
            case STEPPER: {
                v = inflate(R.layout.subtitle_row_stepper, mRows);
                setupRepeat(v.findViewById(R.id.sp_minus), -1);
                setupRepeat(v.findViewById(R.id.sp_plus), 1);
                break;
            }
            case TOGGLE: {
                v = inflate(R.layout.subtitle_row_toggle, mRows);
                ((SwitchCompat) v.findViewById(R.id.sp_toggle)).setOnCheckedChangeListener(
                        new android.widget.CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                                if (!mBinding) onUserSet(((Row) v.getTag()).setting, checked ? 1 : 0);
                            }
                        });
                break;
            }
            case SLIDER: {
                v = inflate(R.layout.subtitle_row_slider, mRows);
                ((SeekBar) v.findViewById(R.id.sp_slider)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                        if (fromUser && !mBinding) onUserSet(((Row) v.getTag()).setting, progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar bar) {
                        mDraggingVpos = true;
                        mHandler.removeCallbacks(mHideHint);
                        mManager.fadeSubtitlePositionHint(true);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar bar) {
                        mDraggingVpos = false;
                        mManager.fadeSubtitlePositionHint(false);
                    }
                });
                break;
            }
            case COLOR: {
                v = inflate(R.layout.subtitle_row_color, mRows);
                v.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View c) {
                        push(Level.COLOR, ((Row) v.getTag()).color);
                    }
                });
                break;
            }
            case NAV: {
                v = inflate(R.layout.subtitle_row_nav, mRows);
                v.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View c) {
                        push(((Row) v.getTag()).target, null);
                    }
                });
                break;
            }
            case ACTION: {
                v = inflate(R.layout.subtitle_row_action, mRows);
                v.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View c) {
                        onResetPressed();
                    }
                });
                break;
            }
            case NOTE:
                v = inflate(R.layout.subtitle_row_note, mRows);
                break;
            case SECTION:
                v = inflate(R.layout.subtitle_row_section, mRows);
                break;
            case GRID:
                v = createGrid(row);
                break;
            default: { // HEX
                v = inflate(R.layout.subtitle_row_hex, mRows);
                final EditText edit = v.findViewById(R.id.sp_edit);
                edit.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

                    @Override
                    public void onTextChanged(CharSequence s, int a, int b, int c) { }

                    @Override
                    public void afterTextChanged(Editable e) {
                        if (mBinding) return;
                        final String hex = e.toString().replace("#", "");
                        if (hex.length() != 6) return;
                        try {
                            final int rgb = Integer.parseInt(hex, 16);
                            final Row r = (Row) v.getTag();
                            r.color.applyRgb(rgb);
                            refresh();
                        } catch (NumberFormatException ignored) {
                            // not a full color yet
                        }
                    }
                });
                edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView t, int action, KeyEvent ev) {
                        if (action == EditorInfo.IME_ACTION_DONE) {
                            t.clearFocus();
                            final InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) imm.hideSoftInputFromWindow(t.getWindowToken(), 0);
                            return true;
                        }
                        return false;
                    }
                });
                break;
            }
        }
        return v;
    }

    /** 8 tiles fit the TV panel (40dp tiles); on a phone, 4 keeps them at a comfortable touch size in both orientations. */
    private int gridColumns() {
        return mHost.isTv() ? 8 : 4;
    }

    /** The preset tiles. Plain focusable views: the D-pad uses Android's own focus search between them. */
    private View createGrid(Row row) {
        final String[] presets = getResources().getStringArray(R.array.color_picker_subtitle);
        final String[] names = getResources().getStringArray(R.array.subtitle_panel_color_names);
        final int columns = gridColumns();
        final LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(VERTICAL);
        grid.setPadding(dp(6), dp(4), dp(6), dp(4));
        LinearLayout line = null;
        for (int i = 0; i < presets.length; i++) {
            if (i % columns == 0) {
                line = new LinearLayout(getContext());
                line.setOrientation(HORIZONTAL);
                grid.addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            final int rgb = Color.parseColor(presets[i]) & 0x00FFFFFF;
            final View tile = inflate(R.layout.subtitle_swatch_tile, line);
            ((GradientDrawable) ((LayerDrawable) tile.getBackground().mutate())
                    .findDrawableByLayerId(R.id.swatch_fill)).setColor(0xFF000000 | rgb);
            tile.setTag(R.id.sp_tile_rgb, rgb); // a keyed tag, not a Row: the key handling climbs past it
            tile.setContentDescription(i < names.length ? names[i] : presets[i]);
            tile.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View t) {
                    ((Row) grid.getTag()).color.applyRgb(rgb);
                    refresh();
                }
            });
            line.addView(tile);
        }
        return grid;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupRepeat(final View button, final int direction) {
        final RepeatTouch repeat = new RepeatTouch(button, direction);
        button.setOnTouchListener(repeat);
        // touch is consumed above; this is what an accessibility service's "click" runs
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Row r = rowOf(v);
                if (r != null) adjust(r, direction, 0);
            }
        });
    }

    /** Hold a - or + button: one step at once, then repeating, faster the longer it is held. */
    @SuppressLint("ClickableViewAccessibility")
    private final class RepeatTouch implements OnTouchListener, Runnable {
        private final View mButton;
        private final int mDirection;
        private int mCount;

        RepeatTouch(View button, int direction) {
            mButton = button;
            mDirection = direction;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    mCount = 0;
                    step();
                    mHandler.postDelayed(this, 400);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    mHandler.removeCallbacks(this);
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public void run() {
            step();
            mHandler.postDelayed(this, 120);
        }

        private void step() {
            final Row r = rowOf(mButton);
            if (r != null) adjust(r, mDirection, mCount++);
        }
    }

    // ---- binding ------------------------------------------------------------------------------

    private void bind(Row r, View v) {
        final TextView label = v.findViewById(R.id.sp_label);
        if (label != null && r.labelRes != 0) label.setText(r.labelRes);
        switch (r.type) {
            case PILLS: {
                final ViewGroup pills = v.findViewById(R.id.sp_pills);
                final int current = r.setting.get.get();
                for (int i = 0; i < pills.getChildCount(); i++) {
                    final TextView seg = (TextView) pills.getChildAt(i);
                    seg.setText(r.options[i]);
                    seg.setSelected(i == current);
                }
                break;
            }
            case STEPPER: {
                final int value = r.setting.get.get();
                ((TextView) v.findViewById(R.id.sp_value)).setText(r.fmt.format(value));
                v.findViewById(R.id.sp_minus).setAlpha(value > r.setting.min ? 1f : 0.35f);
                v.findViewById(R.id.sp_plus).setAlpha(value < r.setting.max ? 1f : 0.35f);
                break;
            }
            case TOGGLE: {
                final SwitchCompat sw = v.findViewById(R.id.sp_toggle);
                sw.setText(r.labelRes);
                sw.setChecked(r.setting.get.get() != 0);
                break;
            }
            case SLIDER: {
                final SeekBar bar = v.findViewById(R.id.sp_slider);
                bar.setMax(r.setting.max - r.setting.min);
                bar.setProgress(r.setting.get.get() - r.setting.min);
                ((TextView) v.findViewById(R.id.sp_value)).setText(String.valueOf(r.setting.get.get()));
                break;
            }
            case COLOR: {
                final int argb = r.color.get.get();
                setChip(v.findViewById(R.id.sp_chip), argb);
                ((TextView) v.findViewById(R.id.sp_value)).setText(String.format("#%06X", argb & 0xFFFFFF));
                break;
            }
            case ACTION: {
                if (label != null) {
                    label.setText(mResetArmed
                            ? (mHost.isTv() ? R.string.subtitle_panel_reset_confirm_tv : R.string.subtitle_panel_reset_confirm_touch)
                            : R.string.subtitle_panel_reset);
                }
                break;
            }
            case NOTE:
                ((TextView) v).setText(r.labelRes);
                break;
            case SECTION:
                ((TextView) v).setText(r.labelRes);
                break;
            case GRID: {
                final int current = r.color.get.get() & 0xFFFFFF;
                final ViewGroup grid = (ViewGroup) v;
                for (int l = 0; l < grid.getChildCount(); l++) {
                    final ViewGroup line = (ViewGroup) grid.getChildAt(l);
                    for (int t = 0; t < line.getChildCount(); t++) {
                        final View tile = line.getChildAt(t);
                        final boolean selected = ((Integer) tile.getTag(R.id.sp_tile_rgb)) == current;
                        tile.setSelected(selected);
                        tile.findViewById(R.id.sp_check).setVisibility(selected ? VISIBLE : GONE);
                    }
                }
                break;
            }
            case HEX: {
                final EditText edit = v.findViewById(R.id.sp_edit);
                if (!edit.hasFocus()) edit.setText(String.format("#%06X", r.color.get.get() & 0xFFFFFF));
                break;
            }
            default:
                break;
        }
    }

    private void setChip(View chip, int argb) {
        final LayerDrawable d = (LayerDrawable) chip.getBackground().mutate();
        ((GradientDrawable) d.findDrawableByLayerId(R.id.swatch_fill)).setColor(0xFF000000 | (argb & 0xFFFFFF));
    }

    private void updateHeader(Frame frame) {
        final boolean basic = frame.level == Level.BASIC;
        mTitle.setText(frame.level == Level.COLOR ? frame.color.labelRes
                : basic ? R.string.subtitle_panel_title : R.string.subtitle_panel_advanced);
        mBack.setImageResource(basic ? R.drawable.ic_subtitle_close : R.drawable.ic_subtitle_chevron);
        mBack.setRotation(basic ? 0f : 180f);
        mBack.setContentDescription(getContext().getString(basic ? R.string.subtitle_panel_close : R.string.subtitle_panel_back));
    }

    // ---- navigation ---------------------------------------------------------------------------

    private void push(Level level, ColorTarget color) {
        final Row focused = rowOf(findFocus());
        top().focusId = focused != null ? focused.id : null;
        mStack.add(new Frame(level, color));
        showLevel(null);
    }

    private void showLevel(String focusId) {
        mViews.clear();
        mRows.removeAllViews();
        refresh();
        if (mUsingKeys && !mIsMirror) {
            focusRow(focusId);
        }
        post(new Runnable() { // new level starts at the top
            @Override
            public void run() {
                final View scroll = findViewById(R.id.sp_scroll);
                if (scroll != null && focusId == null) scroll.scrollTo(0, 0);
            }
        });
    }

    /** Focus on a row by id (the first focusable one if null / gone): the one we came from after Back. */
    private void focusRow(String id) {
        View target = id != null ? mViews.get(id) : null;
        if (target != null && top().level == Level.COLOR && "grid".equals(id)) target = firstTile(target);
        if (target == null || !target.isFocusable()) {
            for (String rowId : mRowIds) {
                View v = mViews.get(rowId);
                if (v == null) continue;
                if (v instanceof ViewGroup && "grid".equals(rowId)) v = selectedOrFirstTile(v);
                if (v != null && v.isFocusable()) {
                    target = v;
                    break;
                }
            }
        }
        if (target != null) target.requestFocus();
    }

    private View firstTile(View grid) {
        final ViewGroup g = (ViewGroup) grid;
        return g.getChildCount() > 0 && ((ViewGroup) g.getChildAt(0)).getChildCount() > 0
                ? ((ViewGroup) g.getChildAt(0)).getChildAt(0) : null;
    }

    private View selectedOrFirstTile(View grid) {
        final ViewGroup g = (ViewGroup) grid;
        for (int l = 0; l < g.getChildCount(); l++) {
            final ViewGroup line = (ViewGroup) g.getChildAt(l);
            for (int t = 0; t < line.getChildCount(); t++) {
                if (line.getChildAt(t).isSelected()) return line.getChildAt(t);
            }
        }
        return firstTile(grid);
    }

    // ---- input --------------------------------------------------------------------------------

    private void onUserSet(Setting s, int value) {
        s.apply(value);
        refresh();
    }

    private static int accel(int repeat) {
        return repeat < 5 ? 1 : repeat < 15 ? 2 : 5;
    }

    /** Left/Right (or - / +) on a row. Returns true if the row type takes it. */
    private boolean adjust(Row r, int direction, int repeat) {
        if (r == null || r.setting == null) return false;
        switch (r.type) {
            case PILLS:
                onUserSet(r.setting, r.setting.get.get() + direction);
                return true;
            case TOGGLE:
                onUserSet(r.setting, direction > 0 ? 1 : 0);
                return true;
            case STEPPER:
            case SLIDER:
                onUserSet(r.setting, r.setting.get.get() + direction * r.setting.step * accel(repeat));
                return true;
            default:
                return false;
        }
    }

    private static int focusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    private Row rowOf(View v) {
        while (v != null && v != this) {
            final Object tag = v.getTag();
            if (tag instanceof Row) return (Row) tag;
            final Object parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B) {
                if (!handleBack() && mHost != null) mHost.onCloseRequested();
                return true;
            }
            final boolean ok = TVUtils.isOKKey(code);
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                    || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN || ok) {
                mUsingKeys = true;
                final View focus = findFocus();
                if (focus instanceof EditText) return super.dispatchKeyEvent(event); // typing, not navigation
                final Row r = rowOf(focus);
                if (r != null) {
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT && adjust(r, -1, event.getRepeatCount())) return true;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT && adjust(r, 1, event.getRepeatCount())) return true;
                }
                final int direction = focusDirection(code);
                if (direction != 0 && focus != null) {
                    // Move focus here, and always consume the key. If this were left to the default navigation,
                    // the activity would see Left/Right first: PlayerController.onKeyDown() turns them into
                    // "previous / next TV menu card" and returns true, so focus would never move.
                    final View next = focusSearch(focus, direction); // confined to this panel
                    if (next != null && next != focus) next.requestFocus(direction);
                    return true;
                }
                if (ok && focus != null) {
                    if (r != null && r.type == Type.PILLS) {
                        onUserSet(r.setting, (r.setting.get.get() + 1) % r.options.length);
                    } else if (r != null && r.type == Type.TOGGLE) {
                        onUserSet(r.setting, r.setting.get.get() == 0 ? 1 : 0);
                    } else if (focus.isClickable()) {
                        focus.performClick(); // nav, color, action rows and the tiles; also for the A button
                    }
                    return true; // consumed here, so the key-up cannot click a second time
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /** Focus never leaves the panel: behind it are the cards and the player controls. */
    @Override
    public View focusSearch(View focused, int direction) {
        final View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
        return next != null ? next : focused;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mIsMirror) return true; // the 3D copy only shows
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && mHost != null) mUsingKeys = mHost.isTv();
        return super.onInterceptTouchEvent(ev);
    }

    // ---- lifecycle ----------------------------------------------------------------------------

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mManager != null) mManager.setShowSubtitlePositionHint(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeCallbacksAndMessages(null);
        if (mManager != null) mManager.fadeSubtitlePositionHint(false);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mManager == null) return;
        // Rotation: header and preview heights come from the theme and differ per orientation.
        final TypedValue tv = new TypedValue();
        if (getContext().getTheme().resolveAttribute(R.attr.subtitlePreviewHeight, tv, true)) {
            final ViewGroup.LayoutParams lp = mPreview.getLayoutParams();
            lp.height = (int) tv.getDimension(getResources().getDisplayMetrics());
            mPreview.setLayoutParams(lp);
        }
        if (getContext().getTheme().resolveAttribute(R.attr.subtitleHeaderHeight, tv, true)) {
            ((View) mBack.getParent()).setMinimumHeight((int) tv.getDimension(getResources().getDisplayMetrics()));
        }
        requestLayout();
    }
}
