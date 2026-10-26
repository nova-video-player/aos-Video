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
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.SubtitleSettingsModel.ColorTarget;
import com.archos.mediacenter.video.player.SubtitleSettingsModel.Row;
import com.archos.mediacenter.video.player.SubtitleSettingsModel.Setting;
import com.archos.mediacenter.video.player.tvmenu.SubtitleDelayTVPicker;
import com.archos.mediacenter.video.player.tvmenu.TVCardDialog;
import com.archos.mediacenter.video.player.tvmenu.TVMenu;
import com.archos.mediacenter.video.player.tvmenu.TVMenuItem;
import com.archos.mediacenter.video.player.tvmenu.TVPillItem;
import com.archos.mediacenter.video.player.tvmenu.TVSlaveView;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The TV's subtitle settings, built the way every other player card is: one TVCardDialog in the TV
 * menu container (centered, the card row slides away while it is open) holding a TVMenu of
 * TVMenuItem rows and SubtitleDelayTVPicker steppers. What the rows are, and how a change is
 * applied and saved, comes from SubtitleSettingsModel (shared with the phone).
 *
 * Two things are new: the pill (TVPillItem) for the choices, and an "Advanced" switch that adds the
 * extra rows below it in the same menu (turning it off only hides them). The color pages replace the
 * menu's content inside the same card; Back (and B / Escape) goes back to the settings with focus on
 * the row that opened the page, and only from the settings does it close the card, which then hands
 * focus back to the card row that opened it (setDiscrete(false)).
 */
public class TvSubtitleSettings implements SubtitleSettingsModel.Listener {

    /** Rows whose change adds or removes other rows: the menu is rebuilt. */
    private static final Set<String> STRUCTURAL =
            new HashSet<>(Arrays.asList("mode", "bg", "advanced", "reset"));

    /** The vertical position runs 0..255: the remote moves it by 10, as it always did. */
    private static final int SLIDER_TV_STEP = 1;

    /** The card is a bit wider than the stock one, so the pills ("File's style", "Scale only") and the values fit. */
    private static final float CARD_WIDTH_FACTOR = 1.3f;

    /** ...but never more than this share of the screen width. */
    private static final float CARD_MAX_SCREEN_SHARE = 0.9f;

    private final Context mContext;
    private final PlayerController mController;
    private final SubtitleManager mManager;
    private final SubtitleSettingsModel mModel;
    private final int mPickerWidth;

    private TVCardDialog mCard;
    private TVMenu mMenu;
    private PreviewItem mPreview;
    private TVMenuItem mLabelLook;
    private ColorTarget mColorPage;          // null: the settings themselves
    private String mReturnFocusId;           // the row that opened the color page
    private final Map<String, View> mRowViews = new LinkedHashMap<>(); // focusable rows only, in order
    private final ArrayList<Runnable> mSyncs = new ArrayList<>();

    public static void show(Context context, PlayerController controller, SubtitleManager manager,
                            SharedPreferences prefs) {
        new TvSubtitleSettings(context, controller, manager, prefs).open();
    }

    private TvSubtitleSettings(Context context, PlayerController controller, SubtitleManager manager,
                               SharedPreferences prefs) {
        mContext = context;
        mController = controller;
        mManager = manager;
        mModel = new SubtitleSettingsModel(context, manager, prefs, true);
        mModel.setListener(this);
        mPickerWidth = (int) (100 * context.getResources().getDisplayMetrics().density);
    }

    @SuppressLint("InflateParams")
    private void open() {
        final View main = LayoutInflater.from(mContext).inflate(R.layout.card_dialog_layout, null);
        mCard = (TVCardDialog) main.findViewById(R.id.card_view);
        final FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mCard.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.width = wideCardWidth(params.width);
        mCard.setLayoutParams(params); // set before the card is added: the 3D copy takes this width too

        // The sample is pinned under the title, outside the scrolling rows: it stays put while they scroll
        mPreview = new PreviewItem(mContext, mManager);
        mCard.setHeaderView(mPreview);

        mMenu = mController.getTVMenuAdapter().createTVMenu();
        mCard.addOtherView(mMenu);
        populate(null, null);

        mCard.setOnBackListener(new TVCardDialog.OnBackListener() {
            @Override
            public boolean onBack() {
                if (mColorPage == null) return false; // on the settings: close the card
                closeColorPage();
                return true;
            }
        });
        mCard.setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {
            @Override
            public void onResult(int code) {
                // every change was saved as it was made; this only hands the screen back
                mModel.release();
                mController.getTVMenuAdapter().setDiscrete(false);
                mManager.fadeSubtitlePositionHint(false);
                mCard = null;
            }
        });

        mModel.open();
        mController.getTVMenuAdapter().setDiscrete(true);
        mController.addToMenuContainer(main);
        final View first = firstFocusable();
        if (first != null) first.requestFocus();
    }

    /** The stock width of the card, a bit wider (an explicit width only: match / wrap are left alone). */
    private int wideCardWidth(int stock) {
        if (stock <= 0) return stock;
        final int screen = mContext.getResources().getDisplayMetrics().widthPixels;
        final int wide = Math.min(Math.round(stock * CARD_WIDTH_FACTOR), Math.round(screen * CARD_MAX_SCREEN_SHARE));
        return Math.max(stock, wide);
    }

    // ---- pages --------------------------------------------------------------------------------

    private void openColorPage(Row row) {
        mReturnFocusId = row.id;
        mColorPage = row.color;
        populate(mColorPage, null);
    }

    private void closeColorPage() {
        final String focusId = mReturnFocusId;
        mColorPage = null;
        mReturnFocusId = null;
        populate(null, focusId);
    }

    /** (Re)builds the menu for a page. TVMenu.clean() also cleans the 3D copy. */
    private void populate(ColorTarget page, String focusId) {
        mRowViews.clear();
        mSyncs.clear();
        mMenu.clean();
        mCard.setText(mContext.getString(mModel.getTitleRes(page)));

        for (Row row : mModel.rows(page)) addRow(row);

        // a rebuild (style mode, background, Advanced, Reset) can change what the sample shows, e.g. the
        // "file's own style" caption: redraw it here, onSettingChanged() returns early for these rows
        mPreview.refresh();

        if (focusId == null) {
            final View scroll = mCard.findViewById(R.id.contentScrollView);
            if (scroll != null) scroll.scrollTo(0, 0);
        }
        if (mCard.isAttachedToWindow()) {
            View target = focusId != null ? mRowViews.get(focusId) : null;
            if (target == null || !target.isFocusable()) target = firstFocusable();
            if (target != null) target.requestFocus();
        }
    }

    private View firstFocusable() {
        for (View v : mRowViews.values()) {
            if (v.isFocusable()) return v;
        }
        return null;
    }

    private String currentFocusId() {
        for (Map.Entry<String, View> e : mRowViews.entrySet()) {
            if (e.getValue().hasFocus()) return e.getKey();
        }
        return null;
    }

    /** A value changed: rebuild when the rows depend on it, else just refresh what is on screen. */
    private void onSettingChanged(String rowId) {
        if (STRUCTURAL.contains(rowId)) {
            populate(mColorPage, rowId);
            return;
        }
        for (Runnable sync : mSyncs) sync.run();
        mPreview.refresh();
    }

    @Override
    public void onModelChanged() { // the Reset confirmation timed out
        if (mCard != null && mCard.getVisibility() == View.VISIBLE) populate(mColorPage, currentFocusId());
    }

    @SuppressLint("InflateParams")
    private TVMenuItem labelLook() { // a throwaway label, only to read its text size, color and padding
        if (mLabelLook == null) {
            mLabelLook = (TVMenuItem) LayoutInflater.from(mContext).inflate(R.layout.menu_item_layout, null);
        }
        return mLabelLook;
    }

    // ---- rows ---------------------------------------------------------------------------------

    private void addRow(Row row) {
        switch (row.type) {
            case NOTE:
                mMenu.addTVMenuItem(new NoteItem(mContext, mContext.getString(row.labelRes), labelLook()));
                break;
            case SECTION:
                mMenu.createAndAddSeparator();
                mMenu.addTVMenuItem(new SectionItem(mContext, mContext.getString(row.labelRes), labelLook()));
                break;
            case PILLS:
                addPills(row);
                break;
            case STEPPER:
                addPicker(row, false);
                break;
            case SLIDER:
                addPicker(row, true);
                break;
            case TOGGLE:
                addToggle(row);
                break;
            case COLOR:
                addColor(row);
                break;
            case ACTION:
                addAction();
                break;
            case GRID:
                addGrid(row);
                break;
            default:
                break;
        }
    }

    private void addPills(final Row row) {
        final Setting s = row.setting;
        final TVMenuItem label = mMenu.createAndAddTVMenuItem(mContext.getString(row.labelRes), false);
        final float textSize = ((TextView) label.findViewById(R.id.info_text)).getTextSize();
        final CharSequence[] options = new CharSequence[row.options.length];
        for (int i = 0; i < options.length; i++) options[i] = mContext.getString(row.options[i]);

        final TVPillItem pill = new TVPillItem(mContext);
        pill.setOptions(options, textSize);
        pill.setSelectedIndex(s.get());
        pill.setOnSelectedListener(new TVPillItem.OnSelectedListener() {
            @Override
            public void onSelected(int index) {
                s.apply(index);
                onSettingChanged(row.id);
            }
        });
        mMenu.addTVMenuItem(pill); // after the options: the 3D copy is made from what it holds
        mRowViews.put(row.id, pill);
        mSyncs.add(new Runnable() {
            @Override
            public void run() {
                pill.setSelectedIndex(s.get());
            }
        });
    }

    /** A value stepped with Left / Right: the picker the size, position and opacity always used. */
    @SuppressLint("InflateParams")
    private void addPicker(final Row row, boolean slider) {
        final Setting s = row.setting;
        mMenu.createAndAddTVMenuItem(mContext.getString(row.labelRes), false);

        final SubtitleDelayTVPicker picker = (SubtitleDelayTVPicker) LayoutInflater.from(mContext)
                .inflate(R.layout.subtitle_delay_tv_picker, null);
        picker.setStep(slider ? SLIDER_TV_STEP : s.step); // the picker works in hundredths
        picker.setMin(s.min * 100);
        picker.setMax(s.max * 100);
        mMenu.addTVMenuItem(picker);
        picker.setTextViewWidth(mPickerWidth);
        picker.setUpdateText(false);
        picker.setText(valueText(row));
        picker.init(s.get() * 100, new SubtitleDelayPickerAbstract.OnDelayChangedListener() {
            @Override
            public void onDelayChanged(SubtitleDelayPickerAbstract view, int delay) {
                s.apply(delay / 100); // the model clamps, saves and flashes the position hint
                onSettingChanged(row.id);
            }
        });
        mRowViews.put(row.id, picker);
        mSyncs.add(new Runnable() {
            @Override
            public void run() {
                picker.updateDelay(s.get() * 100); // the model may have clamped what the picker stepped to
                picker.setText(valueText(row));
            }
        });
    }

    private String valueText(Row row) {
        final int value = row.setting.get();
        return row.fmt != null ? row.fmt.format(value) : String.valueOf(value);
    }

    private void addToggle(final Row row) {
        final Setting s = row.setting;
        final TVMenuItem item = mMenu.createAndAddTVSwitchableMenuItem(mContext.getString(row.labelRes), s.get() != 0);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                s.apply(s.get() != 0 ? 0 : 1);
                item.setChecked(s.get() != 0);
                onSettingChanged(row.id);
            }
        });
        mRowViews.put(row.id, item);
        mSyncs.add(new Runnable() {
            @Override
            public void run() {
                item.setChecked(s.get() != 0);
            }
        });
    }

    /** A row that opens the color page: the name on the left, the color's chip and hex value on the right. */
    private void addColor(final Row row) {
        final ColorItem item = new ColorItem(mContext, mContext.getString(row.labelRes), row.color.get(), labelLook());
        // add first: TVMenu.addTVMenuItem() sets the menu's own click listener (null here) on every item
        mMenu.addTVMenuItem(item);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openColorPage(row);
            }
        });
        mRowViews.put(row.id, item);
        mSyncs.add(new Runnable() {
            @Override
            public void run() {
                item.setColor(row.color.get());
            }
        });
    }

    private void addAction() {
        final TVMenuItem item = mMenu.createAndAddTVMenuItem(mContext.getString(mModel.getResetLabelRes()), false, false);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mModel.onResetPressed();
                populate(mColorPage, "reset"); // armed: the label asks again; done: every row is re-read
            }
        });
        mRowViews.put("reset", item);
    }

    /** The preset colors, as they always were; the current color is marked. */
    private void addGrid(final Row row) {
        final SubtitleColorPicker grid = new SubtitleColorPicker(mContext);
        grid.setCurrentColor(row.color.get());
        grid.setColorPickListener(new SubtitleColorPicker.ColorPickListener() {
            @Override
            public void onColorPicked(int color) {
                row.color.applyRgb(color); // keeps the alpha the color already has
                onSettingChanged(row.id);
            }
        });
        mMenu.addTVMenuItem(grid);
        mRowViews.put(row.id, grid);
        mSyncs.add(new Runnable() {
            @Override
            public void run() {
                grid.setCurrentColor(row.color.get());
            }
        });
    }

    // ---- the sample strip -----------------------------------------------------------------------

    /** SubtitlePreviewView as a (non focusable) menu row that the 3D copy mirrors. */
    private static final class PreviewItem extends FrameLayout implements TVSlaveView {
        private final SubtitleManager mManager;
        private final SubtitlePreviewView mView;
        private PreviewItem mSlave;

        PreviewItem(Context context, SubtitleManager manager) {
            super(context);
            mManager = manager;
            setFocusable(false);
            setBackgroundResource(R.drawable.subtitle_preview_bg);
            mView = new SubtitlePreviewView(context);
            mView.bind(manager, manager::getScreenShortSide);
            final int minHeight = context.getResources()
                    .getDimensionPixelSize(R.dimen.subtitles_settings_dialog_sample_min_height);
            addView(mView, new LayoutParams(LayoutParams.MATCH_PARENT, minHeight));
        }

        void refresh() {
            mView.invalidate();
            if (mSlave != null) mSlave.refresh();
        }

        @Override
        public void updateSlaveView() {
        }

        @Override
        public View getSlaveView() {
            return mSlave;
        }

        @Override
        public View createSlaveView() {
            setSlaveView(new PreviewItem(getContext(), mManager));
            return mSlave;
        }

        @Override
        public void setSlaveView(View v) {
            if (v instanceof PreviewItem) mSlave = (PreviewItem) v;
        }

        @Override
        public void removeSlaveView() {
            mSlave = null;
        }
    }

    /** A wrapped, non-focusable text row (TVMenuItem labels are one line); the 3D copy mirrors it. */
    private static final class NoteItem extends AppCompatTextView implements TVSlaveView {
        private NoteItem mSlave;

        /** Looks like the menu's labels, a little smaller and dimmer. */
        NoteItem(Context context, CharSequence text, TVMenuItem like) {
            super(context);
            final TextView label = like.findViewById(R.id.info_text);
            setText(text);
            setFocusable(false);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, label.getTextSize() * 0.85f);
            setTextColor(label.getTextColors());
            setAlpha(0.7f);
            setPadding(like.getPaddingLeft() + label.getPaddingLeft(), label.getPaddingTop(),
                    like.getPaddingRight() + label.getPaddingRight(), label.getPaddingBottom());
        }

        private NoteItem(Context context, NoteItem from) {
            super(context);
            setText(from.getText());
            setFocusable(false);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, from.getTextSize());
            setTextColor(from.getTextColors());
            setAlpha(from.getAlpha());
            setPadding(from.getPaddingLeft(), from.getPaddingTop(), from.getPaddingRight(), from.getPaddingBottom());
        }

        @Override public void updateSlaveView() { }
        @Override public View getSlaveView() { return mSlave; }
        @Override public View createSlaveView() { setSlaveView(new NoteItem(getContext(), this)); return mSlave; }
        @Override public void setSlaveView(View v) { if (v instanceof NoteItem) mSlave = (NoteItem) v; }
        @Override public void removeSlaveView() { mSlave = null; }
    }

    /**
     * A section header (Text, Background...) built from the TV menu's own values: 3/4 of a menu row's
     * text size (tv_menu_font_size), and the padding of a menu row (menu_item_layout), so it lines up
     * with their labels. What sets it apart is the theme's accent color, upper case and the smaller size.
     * Not focusable; the 3D copy mirrors it.
     */
    private static final class SectionItem extends AppCompatTextView implements TVSlaveView {
        private SectionItem mSlave;

        SectionItem(Context context, CharSequence text, TVMenuItem like) {
            super(context);
            final TextView label = like.findViewById(R.id.info_text);
            setText(text.toString().toUpperCase(Locale.getDefault()));
            setFocusable(false);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, label.getTextSize() * 0.75f);
            setTextColor(accentColor(context, label.getCurrentTextColor()));
            setPadding(like.getPaddingLeft() + label.getPaddingLeft(), like.getPaddingTop() + label.getPaddingTop(),
                    like.getPaddingRight() + label.getPaddingRight(), like.getPaddingBottom() + label.getPaddingBottom());
        }

        private SectionItem(Context context, SectionItem from) {
            super(context);
            setText(from.getText());
            setFocusable(false);
            setTextSize(TypedValue.COMPLEX_UNIT_PX, from.getTextSize());
            setTypeface(from.getTypeface());
            setTextColor(from.getCurrentTextColor());
            setPadding(from.getPaddingLeft(), from.getPaddingTop(), from.getPaddingRight(), from.getPaddingBottom());
        }

        /** The theme's accent color; the label's own color, dimmed, if the theme has none. */
        private static int accentColor(Context context, int fallback) {
            final TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)
                    || context.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
            }
            return (fallback & 0x00FFFFFF) | 0xB3000000;
        }

        @Override public void updateSlaveView() { }
        @Override public View getSlaveView() { return mSlave; }
        @Override public View createSlaveView() { setSlaveView(new SectionItem(getContext(), this)); return mSlave; }
        @Override public void setSlaveView(View v) { if (v instanceof SectionItem) mSlave = (SectionItem) v; }
        @Override public void removeSlaveView() { mSlave = null; }
    }

    /**
     * A focusable menu row for a color: the name left-aligned, the color's chip and hex value
     * right-aligned. Looks like the menu's labels (size, color, padding); the focused row is
     * highlighted, and the 3D copy mirrors the row and its highlight.
     */
    private static final class ColorItem extends LinearLayout implements TVSlaveView {
        private final TextView mLabel;
        private final TextView mValue;
        private int mArgb;
        private ColorItem mSlave;

        ColorItem(Context context, CharSequence label, int argb, TVMenuItem like) {
            super(context);
            final TextView ref = like.findViewById(R.id.info_text);
            mLabel = new TextView(context);
            mValue = new TextView(context);
            build(label, ref.getTextSize(), ref.getTextColors(),
                    like.getPaddingLeft() + ref.getPaddingLeft(), like.getPaddingTop() + ref.getPaddingTop(),
                    like.getPaddingRight() + ref.getPaddingRight(), like.getPaddingBottom() + ref.getPaddingBottom());
            setMinimumHeight(like.getMinimumHeight()); // the menu rows' 32dp
            setColor(argb);
        }

        private ColorItem(Context context, ColorItem from) {
            super(context);
            mLabel = new TextView(context);
            mValue = new TextView(context);
            build(from.mLabel.getText(), from.mLabel.getTextSize(), from.mLabel.getTextColors(),
                    from.getPaddingLeft(), from.getPaddingTop(), from.getPaddingRight(), from.getPaddingBottom());
            setMinimumHeight(from.getMinimumHeight());
            setColor(from.mArgb);
        }

        private void build(CharSequence label, float textSizePx, android.content.res.ColorStateList colors,
                           int left, int top, int right, int bottom) {
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setClickable(true);
            setPadding(left, top, right, bottom);

            final int highlight = ContextCompat.getColor(getContext(), R.color.video_info_next_prev_button_focused);
            final StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(highlight));
            bg.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(highlight)); // the 3D copy
            bg.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            setBackground(bg);

            mLabel.setText(label);
            mLabel.setSingleLine(true);
            mLabel.setEllipsize(TextUtils.TruncateAt.END);
            mLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            mLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
            mLabel.setTextColor(colors);
            mLabel.setFocusable(false);
            mLabel.setDuplicateParentStateEnabled(true); // follows the row's focus state
            addView(mLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            mValue.setSingleLine(true);
            mValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            mValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
            mValue.setTextColor(colors);
            mValue.setFocusable(false);
            mValue.setDuplicateParentStateEnabled(true);
            final LayoutParams valueParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            valueParams.setMarginStart(Math.round(16 * getResources().getDisplayMetrics().density));
            addView(mValue, valueParams);
        }

        /** The chip (a colored block) and the hex value of the color in force (alpha is not shown). */
        void setColor(int argb) {
            mArgb = argb;
            final int rgb = argb & 0xFFFFFF;
            final SpannableString value = new SpannableString("\u25A0 " + String.format(Locale.US, "#%06X", rgb));
            value.setSpan(new ForegroundColorSpan(0xFF000000 | rgb), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new RelativeSizeSpan(1.25f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mValue.setText(value);
            if (mSlave != null) mSlave.setColor(argb);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (TVUtils.isOKKey(keyCode)) {
                if (event.getRepeatCount() == 0) performClick();
                return true;
            }
            // else, we send it to the enclosing card (focus moves to the next row, Back, ...)
            return TVUtils.dispatchToCardParent(this, keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            return TVUtils.isOKKey(keyCode);
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
            if (mSlave != null) mSlave.setSelected(gainFocus);
        }

        @Override public void updateSlaveView() { }
        @Override public View getSlaveView() { return mSlave; }
        @Override public View createSlaveView() { setSlaveView(new ColorItem(getContext(), this)); return mSlave; }

        @Override
        public void setSlaveView(View v) {
            if (v instanceof ColorItem) {
                mSlave = (ColorItem) v;
                mSlave.setFocusable(false);
                mSlave.setClickable(false);
                mSlave.setSelected(isFocused());
            }
        }

        @Override public void removeSlaveView() { mSlave = null; }
    }
}
