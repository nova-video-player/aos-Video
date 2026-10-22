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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatTextView;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        mCard.setLayoutParams(params);

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

        mController.getTVMenuAdapter().setDiscrete(true);
        mController.addToMenuContainer(main);
        final View first = firstFocusable();
        if (first != null) first.requestFocus();
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
                mMenu.createAndAddTVMenuItem(mContext.getString(row.labelRes), false);
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

    /** A row that opens the color page; the color in force is shown as a chip and its hex value. */
    private void addColor(final Row row) {
        final TVMenuItem item = mMenu.createAndAddTVMenuItem(colorLabel(row), false, false);
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
                item.setText(colorLabel(row));
            }
        });
    }

    private String colorLabel(Row row) { // TVMenuItem text is HTML: the chip is a colored block
        final int rgb = row.color.get() & 0xFFFFFF;
        final String hex = String.format("#%06X", rgb);
        return TextUtils.htmlEncode(mContext.getString(row.labelRes))
                + "&nbsp;&nbsp;<big><font color=\"" + hex + "\">\u25A0</font></big>&nbsp;" + hex;
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
}
