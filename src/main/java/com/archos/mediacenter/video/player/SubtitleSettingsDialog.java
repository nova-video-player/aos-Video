/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediacenter.video.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;

/**
 * Subtitle style settings dialog for the libass-backed SubtitleEngine.
 *
 * All controls write straight into SubtitleManager, which forwards to the native
 * SubtitleEngine and remains the single source of truth shared with the TV remote
 * picker (PlayerActivity#createTVSubtitleSettingsDialog) and preference restore.
 *
 * UI Mode (2D/3D) and Font Family are intentionally NOT exposed here — both are
 * locked internal decisions per the libass integration design.
 */
public class SubtitleSettingsDialog extends AlertDialog implements
        SeekBar.OnSeekBarChangeListener, SubtitleColorPicker.ColorPickListener {

    private static final int FONT_SIZE_MIN = 1;
    private static final int FONT_SIZE_MAX = 250;
    private static final int FONT_SIZE_STEP = 2;

    private static final float FONT_SCALE_MIN = 0.10f;
    private static final float FONT_SCALE_MAX = 3.0f;
    private static final float FONT_SCALE_STEP = 0.1f;

    private static final float WIDTH_MIN = 0f;
    private static final float WIDTH_MAX = 50f;
    private static final float WIDTH_STEP = 1f;

    private SubtitleManager mSubtitleManager;
    private SharedPreferences mSharedPreferences;

    private TextView mSampleText;

    // Override mode
    private RadioGroup mOverrideModeGroup;
    private TextView mOverrideHint;
    private LinearLayout mStyleControls;

    // Font size stepper
    private TextView mFontSizeValue;
    private int mFontSizePt;
    private LinearLayout mFontSizeStepperGroup;

    // Font scale stepper (Scale Only mode)
    private LinearLayout mFontScaleRow;
    private TextView mFontScaleValue;
    private float mFontScale;

    // Bold + text color
    private LinearLayout mBoldColorRow;
    private CheckBox mBoldCheckBox;
    private View mSwatchTextColor;

    // Background mode
    private RadioGroup mBgModeGroup;
    private LinearLayout mFloatingControls;
    private LinearLayout mBackgroundControls;
    private LinearLayout mBoxedBlockExtraControls;
    private LinearLayout mBoxedLinePaddingRow;

    // Floating mode controls (outline + shadow)
    private TextView mOutlineWidthValue;
    private View mSwatchOutlineColor;
    private TextView mShadowWidthValue;
    private View mSwatchShadowColor;
    private float mOutlineWidth;
    private float mShadowWidth;

    // Boxed line/block controls (background)
    private View mSwatchBackgroundColor;
    private SeekBar mBgOpacitySeekBar;
    private int mBgOpacity;
    private TextView mLinePaddingValue;

    // Boxed block extra controls (outline stays active, shadow becomes padding)
    private TextView mBlockOutlineWidthValue;
    private View mSwatchBlockOutlineColor;
    private TextView mBlockPaddingValue;
    private float mBlockPadding;

    // Vertical position (kept as the existing spacer-based +/- stepper)
    private SeekBar mVertSeekBar;
    private View mLeftVerticalButton;
    private View mRightVerticalButton;
    private int mVPos;
    private boolean touching = false;
    // "Vertical Position:" row label — dimmed alongside the seekbar/buttons outside Custom mode.
    private TextView mVertLabel;

    // Shared color picker, retargeted per swatch tap
    private SubtitleColorPicker mColorPicker;

    public SubtitleSettingsDialog(Context context, SubtitleManager subtitleManager) {
        super(context);
        init(context, subtitleManager);
    }

    private void init(Context context, final SubtitleManager stm) {
        mSubtitleManager = stm;
        mFontSizePt = stm.getFontSizePt();
        mOutlineWidth = stm.getOutlineWidth();
        mShadowWidth = stm.getShadowWidth();
        mBlockPadding = stm.getShadowWidth(); // padding reuses shadow_width in Boxed Block mode
        mBgOpacity = stm.getBackgroundOpacity();
        mVPos = stm.getVerticalPosition();

        setIcon(R.drawable.ic_menu_settings);

        getWindow().setGravity(Gravity.TOP);
        getWindow().setBackgroundDrawable(new ColorDrawable(VideoInfoCommonClass.getAlphaColor(ContextCompat.getColor(context, R.color.background_material_dark), 128)));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        final LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final LinearLayout view = (LinearLayout) inflater.inflate(R.layout.subtitle_settings_dialog, null);
        setView(view);

        mSampleText = view.findViewById(R.id.subtitle_sample_text);

        // --- Override mode ---
        // NOTE: setOverrideModeChecked (called at the end of init(), after every view it
        // touches via updateOverrideModeUI is wired) sets the initial radio state and
        // dims/shows the right controls. Only the checked-change listener is attached here.
        mOverrideModeGroup = view.findViewById(R.id.subtitle_override_mode_group);
        mOverrideHint = view.findViewById(R.id.subtitle_override_hint);
        mStyleControls = view.findViewById(R.id.subtitle_style_controls);
        mOverrideModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.override_embedded) mode = SubtitleManager.OVERRIDE_EMBEDDED;
            else if (checkedId == R.id.override_scale_only) mode = SubtitleManager.OVERRIDE_SCALE_ONLY;
            else mode = SubtitleManager.OVERRIDE_CUSTOM;
            mSubtitleManager.setOverrideMode(mode);
            updateOverrideModeUI(mode);
        });

        // --- Font size stepper (Embedded/Custom modes) ---
        mFontSizeStepperGroup = view.findViewById(R.id.font_size_stepper);
        mFontSizeValue = view.findViewById(R.id.font_size_value);
        updateFontSizeLabel();
        view.findViewById(R.id.font_size_minus).setOnClickListener(v -> {
            if (mFontSizePt - FONT_SIZE_STEP >= FONT_SIZE_MIN) {
                mFontSizePt -= FONT_SIZE_STEP;
                applyFontSize();
            }
        });
        view.findViewById(R.id.font_size_plus).setOnClickListener(v -> {
            if (mFontSizePt + FONT_SIZE_STEP <= FONT_SIZE_MAX) {
                mFontSizePt += FONT_SIZE_STEP;
                applyFontSize();
            }
        });

        // --- Font scale stepper (Scale Only mode) ---
        mFontScaleRow = view.findViewById(R.id.font_scale_row);
        mFontScaleValue = view.findViewById(R.id.font_scale_value);
        mFontScale = stm.getFontScale();
        updateFontScaleLabel();
        view.findViewById(R.id.font_scale_minus).setOnClickListener(v -> {
            if (mFontScale - FONT_SCALE_STEP >= FONT_SCALE_MIN) {
                mFontScale -= FONT_SCALE_STEP;
                applyFontScale();
            }
        });
        view.findViewById(R.id.font_scale_plus).setOnClickListener(v -> {
            if (mFontScale + FONT_SCALE_STEP <= FONT_SCALE_MAX) {
                mFontScale += FONT_SCALE_STEP;
                applyFontScale();
            }
        });

        // --- Bold + text color ---
        mBoldColorRow = view.findViewById(R.id.bold_color_row);
        mBoldCheckBox = view.findViewById(R.id.subBold);
        mBoldCheckBox.setChecked(stm.getBold());
        mBoldCheckBox.setOnClickListener(v -> mSubtitleManager.setBold(mBoldCheckBox.isChecked()));

        mSwatchTextColor = view.findViewById(R.id.swatch_text_color);
        mSwatchTextColor.setBackgroundColor(stm.getColor());
        mSwatchTextColor.setOnClickListener(v -> openColorPicker(SubtitleColorPicker.Target.TEXT, stm.getColor(), (View) mSwatchTextColor.getParent()));

        // --- Background mode ---
        mBgModeGroup = view.findViewById(R.id.subtitle_bg_mode_group);
        mFloatingControls = view.findViewById(R.id.floating_controls);
        mBackgroundControls = view.findViewById(R.id.background_controls);
        mBoxedLinePaddingRow = view.findViewById(R.id.boxed_line_padding_row);
        mBoxedBlockExtraControls = view.findViewById(R.id.boxed_block_extra_controls);
        setBgModeChecked(stm.getBgMode());
        updateBgModeUI(stm.getBgMode());
        mBgModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.bg_mode_boxed_line) mode = SubtitleManager.BG_MODE_BOXED_LINE;
            else if (checkedId == R.id.bg_mode_boxed_block) mode = SubtitleManager.BG_MODE_BOXED_BLOCK;
            else mode = SubtitleManager.BG_MODE_FLOATING;
            mSubtitleManager.setBgMode(mode);
            updateBgModeUI(mode);
        });

        // --- Floating mode: outline + shadow ---
        mOutlineWidthValue = view.findViewById(R.id.outline_width_value);
        mSwatchOutlineColor = view.findViewById(R.id.swatch_outline_color);
        updateOutlineWidthLabel();
        mSwatchOutlineColor.setBackgroundColor(stm.getOutlineColor());
        view.findViewById(R.id.outline_width_minus).setOnClickListener(v -> {
            if (mOutlineWidth - WIDTH_STEP >= WIDTH_MIN) {
                mOutlineWidth -= WIDTH_STEP;
                applyOutlineWidth();
            }
        });
        view.findViewById(R.id.outline_width_plus).setOnClickListener(v -> {
            if (mOutlineWidth + WIDTH_STEP <= WIDTH_MAX) {
                mOutlineWidth += WIDTH_STEP;
                applyOutlineWidth();
            }
        });
        mSwatchOutlineColor.setOnClickListener(v -> openColorPicker(SubtitleColorPicker.Target.OUTLINE, stm.getOutlineColor(), (View) mSwatchOutlineColor.getParent()));

        mShadowWidthValue = view.findViewById(R.id.shadow_width_value);
        mSwatchShadowColor = view.findViewById(R.id.swatch_shadow_color);
        updateShadowWidthLabel();
        mSwatchShadowColor.setBackgroundColor(stm.getShadowColor());
        view.findViewById(R.id.shadow_width_minus).setOnClickListener(v -> {
            if (mShadowWidth - WIDTH_STEP >= WIDTH_MIN) {
                mShadowWidth -= WIDTH_STEP;
                applyShadowWidth();
            }
        });
        view.findViewById(R.id.shadow_width_plus).setOnClickListener(v -> {
            if (mShadowWidth + WIDTH_STEP <= WIDTH_MAX) {
                mShadowWidth += WIDTH_STEP;
                applyShadowWidth();
            }
        });
        mSwatchShadowColor.setOnClickListener(v -> openColorPicker(SubtitleColorPicker.Target.SHADOW, stm.getShadowColor(), (View) mSwatchShadowColor.getParent()));

        // --- Boxed line/block: background color + opacity ---
        mSwatchBackgroundColor = view.findViewById(R.id.swatch_background_color);
        mSwatchBackgroundColor.setBackgroundColor(stm.getBackgroundColor());
        mSwatchBackgroundColor.setOnClickListener(v -> openColorPicker(SubtitleColorPicker.Target.BACKGROUND, stm.getBackgroundColor(), (View) mSwatchBackgroundColor.getParent()));

        mBgOpacitySeekBar = view.findViewById(R.id.subtitle_bg_opacity_seekbar);
        mBgOpacitySeekBar.setMax(255);
        mBgOpacitySeekBar.setProgress(mBgOpacity);
        mBgOpacitySeekBar.setOnSeekBarChangeListener(this);

        // --- Boxed line: padding ---
        // BorderStyle=3's Outline field is genuinely uniform box padding (see the XML
        // comment on boxed_line_padding_row), so this reuses the same mOutlineWidth field
        // and applyOutlineWidth() as Floating mode's outline stepper and Boxed Block's
        // outline stepper — all three are the same underlying value, just labeled per
        // what it actually means in each mode's rendering.
        mLinePaddingValue = view.findViewById(R.id.line_padding_value);
        updateLinePaddingLabel();
        view.findViewById(R.id.line_padding_minus).setOnClickListener(v -> {
            if (mOutlineWidth - WIDTH_STEP >= WIDTH_MIN) {
                mOutlineWidth -= WIDTH_STEP;
                applyOutlineWidth();
            }
        });
        view.findViewById(R.id.line_padding_plus).setOnClickListener(v -> {
            if (mOutlineWidth + WIDTH_STEP <= WIDTH_MAX) {
                mOutlineWidth += WIDTH_STEP;
                applyOutlineWidth();
            }
        });

        // --- Boxed block extra: outline (again, inside the box) + padding ---
        mBlockOutlineWidthValue = view.findViewById(R.id.block_outline_width_value);
        mSwatchBlockOutlineColor = view.findViewById(R.id.swatch_block_outline_color);
        updateBlockOutlineWidthLabel();
        mSwatchBlockOutlineColor.setBackgroundColor(stm.getOutlineColor());
        view.findViewById(R.id.block_outline_width_minus).setOnClickListener(v -> {
            if (mOutlineWidth - WIDTH_STEP >= WIDTH_MIN) {
                mOutlineWidth -= WIDTH_STEP;
                applyOutlineWidth();
            }
        });
        view.findViewById(R.id.block_outline_width_plus).setOnClickListener(v -> {
            if (mOutlineWidth + WIDTH_STEP <= WIDTH_MAX) {
                mOutlineWidth += WIDTH_STEP;
                applyOutlineWidth();
            }
        });
        mSwatchBlockOutlineColor.setOnClickListener(v -> openColorPicker(SubtitleColorPicker.Target.OUTLINE, stm.getOutlineColor(), (View) mSwatchBlockOutlineColor.getParent()));

        mBlockPaddingValue = view.findViewById(R.id.block_padding_value);
        updateBlockPaddingLabel();
        view.findViewById(R.id.block_padding_minus).setOnClickListener(v -> {
            if (mBlockPadding - WIDTH_STEP >= WIDTH_MIN) {
                mBlockPadding -= WIDTH_STEP;
                applyBlockPadding();
            }
        });
        view.findViewById(R.id.block_padding_plus).setOnClickListener(v -> {
            if (mBlockPadding + WIDTH_STEP <= WIDTH_MAX) {
                mBlockPadding += WIDTH_STEP;
                applyBlockPadding();
            }
        });

        // --- Shared color picker ---
        mColorPicker = view.findViewById(R.id.color_layout);
        mColorPicker.setColorPickListener(this);

        // --- Vertical position (existing spacer-based stepper, kept as-is) ---
        mVertSeekBar = view.findViewById(R.id.subtitle_vert_seekbar);
        mVertSeekBar.setMax(255);
        mVertSeekBar.setOnSeekBarChangeListener(this);

        mLeftVerticalButton = view.findViewById(R.id.left_icon);
        mRightVerticalButton = view.findViewById(R.id.right_icon);
        mLeftVerticalButton.setOnClickListener(v -> stepVerticalPosition(-3));
        mRightVerticalButton.setOnClickListener(v -> stepVerticalPosition(3));
        mVertLabel = view.findViewById(R.id.subtitle_vert_text);

        // Apply initial override-mode UI state now that every view it touches is wired.
        setOverrideModeChecked(stm.getOverrideMode());

        setCancelable(true);
        setCanceledOnTouchOutside(true);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    // ------------------------------------------------------------------
    // Override mode
    // ------------------------------------------------------------------

    private void setOverrideModeChecked(int mode) {
        if (mode == SubtitleManager.OVERRIDE_EMBEDDED) mOverrideModeGroup.check(R.id.override_embedded);
        else if (mode == SubtitleManager.OVERRIDE_SCALE_ONLY) mOverrideModeGroup.check(R.id.override_scale_only);
        else mOverrideModeGroup.check(R.id.override_custom);
        updateOverrideModeUI(mode);
    }

    /**
     * Custom style controls only take visual effect when override mode isn't Embedded.
     * Rather than hide them outright (the user may still want to stage values for later),
     * dim them and show an explanatory hint.
     *
     * Scale Only mode is a third case: sync_styles() only reads font_scale there — bold,
     * colors, outline/shadow/background are all ignored (see sub_format_ssa.c's
     * `force_all` gate, which is false for override_mode==2). So in Scale Only we show
     * the font-scale stepper instead of the absolute font-size stepper, and dim every
     * other control group since none of them take effect in this mode.
     */
    private void updateOverrideModeUI(int mode) {
        boolean embedded = (mode == SubtitleManager.OVERRIDE_EMBEDDED);
        boolean scaleOnly = (mode == SubtitleManager.OVERRIDE_SCALE_ONLY);
        boolean custom = (mode == SubtitleManager.OVERRIDE_CUSTOM);

        mOverrideHint.setVisibility(embedded ? View.VISIBLE : View.GONE);
        mStyleControls.setAlpha(embedded ? 0.4f : 1.0f);
        setViewGroupEnabled(mStyleControls, !embedded);

        // Swap font-size stepper <-> font-scale stepper
        mFontSizeStepperGroup.setVisibility(scaleOnly ? View.GONE : View.VISIBLE);
        mFontScaleRow.setVisibility(scaleOnly ? View.VISIBLE : View.GONE);

        // In Scale Only mode, only the scale row actually does anything — dim the rest
        // (bold/colors/bg-mode/outline/shadow/vertical-position-adjacent controls) to
        // signal they're inert, without the heavier "disabled" look Embedded gets.
        float otherAlpha = (!embedded && scaleOnly) ? 0.4f : 1.0f;
        mBoldColorRow.setAlpha(otherAlpha);
        mBgModeGroup.setAlpha(otherAlpha);
        mFloatingControls.setAlpha(otherAlpha);
        mBackgroundControls.setAlpha(otherAlpha);

        // Vertical position only ever takes effect in Custom mode (Embedded keeps the
        // source subtitle's own position, and Scale Only's force_all gate ignores it
        // too — see sub_format_ssa.c). Grey it out (dim + disable) rather than hide it,
        // matching how every other inert control group in this dialog is treated.
        float vertAlpha = custom ? 1.0f : 0.4f;
        mVertSeekBar.setEnabled(custom);
        mVertSeekBar.setAlpha(vertAlpha);
        mLeftVerticalButton.setEnabled(custom);
        mLeftVerticalButton.setAlpha(vertAlpha);
        mRightVerticalButton.setEnabled(custom);
        mRightVerticalButton.setAlpha(vertAlpha);
        mVertLabel.setAlpha(vertAlpha);
    }

    private void setViewGroupEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setViewGroupEnabled(vg.getChildAt(i), enabled);
            }
        }
    }

    // ------------------------------------------------------------------
    // Font size
    // ------------------------------------------------------------------

    private void updateFontSizeLabel() {
        mFontSizeValue.setText(mFontSizePt + "pt");
        if (mSampleText != null) mSampleText.setTextSize(mFontSizePt);
    }

    private void applyFontSize() {
        updateFontSizeLabel();
        mSubtitleManager.setFontSizePt(mFontSizePt);
    }

    private void updateFontScaleLabel() {
        mFontScaleValue.setText(Math.round(mFontScale * 100) + "%");
    }

    private void applyFontScale() {
        updateFontScaleLabel();
        mSubtitleManager.setFontScale(mFontScale);
    }

    // ------------------------------------------------------------------
    // Background mode
    // ------------------------------------------------------------------

    private void setBgModeChecked(int mode) {
        if (mode == SubtitleManager.BG_MODE_BOXED_LINE) mBgModeGroup.check(R.id.bg_mode_boxed_line);
        else if (mode == SubtitleManager.BG_MODE_BOXED_BLOCK) mBgModeGroup.check(R.id.bg_mode_boxed_block);
        else mBgModeGroup.check(R.id.bg_mode_floating);
    }

    private void updateBgModeUI(int mode) {
        switch (mode) {
            case SubtitleManager.BG_MODE_BOXED_LINE:
                mFloatingControls.setVisibility(View.GONE);
                mBackgroundControls.setVisibility(View.VISIBLE);
                mBoxedLinePaddingRow.setVisibility(View.VISIBLE);
                mBoxedBlockExtraControls.setVisibility(View.GONE);
                break;
            case SubtitleManager.BG_MODE_BOXED_BLOCK:
                mFloatingControls.setVisibility(View.GONE);
                mBackgroundControls.setVisibility(View.VISIBLE);
                mBoxedLinePaddingRow.setVisibility(View.GONE);
                mBoxedBlockExtraControls.setVisibility(View.VISIBLE);
                break;
            case SubtitleManager.BG_MODE_FLOATING:
            default:
                mFloatingControls.setVisibility(View.VISIBLE);
                mBackgroundControls.setVisibility(View.GONE);
                mBoxedLinePaddingRow.setVisibility(View.GONE);
                mBoxedBlockExtraControls.setVisibility(View.GONE);
                break;
        }
    }

    // ------------------------------------------------------------------
    // Outline / shadow / padding steppers
    // ------------------------------------------------------------------

    private void updateOutlineWidthLabel() {
        mOutlineWidthValue.setText(String.valueOf((int) mOutlineWidth));
    }

    private void updateBlockOutlineWidthLabel() {
        mBlockOutlineWidthValue.setText(String.valueOf((int) mOutlineWidth));
    }

    private void updateLinePaddingLabel() {
        mLinePaddingValue.setText(String.valueOf((int) mOutlineWidth));
    }

    private void applyOutlineWidth() {
        updateOutlineWidthLabel();
        updateBlockOutlineWidthLabel();
        updateLinePaddingLabel();
        mSubtitleManager.setOutlineWidth(mOutlineWidth);
    }

    private void updateShadowWidthLabel() {
        mShadowWidthValue.setText(String.valueOf((int) mShadowWidth));
    }

    private void applyShadowWidth() {
        updateShadowWidthLabel();
        mSubtitleManager.setShadowWidth(mShadowWidth);
    }

    private void updateBlockPaddingLabel() {
        mBlockPaddingValue.setText(String.valueOf((int) mBlockPadding));
    }

    private void applyBlockPadding() {
        updateBlockPaddingLabel();
        // Boxed Block mode hijacks shadow_width as padding — see sub_format_ssa.c.
        mSubtitleManager.setShadowWidth(mBlockPadding);
    }

    // ------------------------------------------------------------------
    // Shared color picker
    // ------------------------------------------------------------------

    /**
     * Detaches the shared picker from wherever it currently sits and re-inserts it
     * immediately after the row containing the tapped swatch, so it always appears
     * right below the control the user is editing instead of fixed at the bottom.
     */
    private void openColorPicker(SubtitleColorPicker.Target target, int currentColor, View anchorRow) {
        mColorPicker.setTarget(target);
        mColorPicker.setCurrentColor(currentColor);

        ViewGroup currentParent = (ViewGroup) mColorPicker.getParent();
        if (currentParent != null) currentParent.removeView(mColorPicker);

        ViewGroup newParent = (ViewGroup) anchorRow.getParent();
        int anchorIndex = newParent.indexOfChild(anchorRow);
        newParent.addView(mColorPicker, anchorIndex + 1);

        mColorPicker.setVisibility(View.VISIBLE);
    }

    @Override
    public void onColorPicked(int color) {
        SubtitleColorPicker.Target target = mColorPicker.getTarget();
        switch (target) {
            case TEXT:
                mSubtitleManager.setColor(color);
                mSwatchTextColor.setBackgroundColor(color);
                if (mSampleText != null) mSampleText.setTextColor(color);
                break;
            case OUTLINE:
                mSubtitleManager.setOutlineColor(color);
                mSwatchOutlineColor.setBackgroundColor(color);
                mSwatchBlockOutlineColor.setBackgroundColor(color);
                break;
            case SHADOW:
                mSubtitleManager.setShadowColor(color);
                mSwatchShadowColor.setBackgroundColor(color);
                break;
            case BACKGROUND:
                mSubtitleManager.setBackgroundColor(color);
                mSwatchBackgroundColor.setBackgroundColor(color);
                break;
        }
        mColorPicker.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------
    // Vertical position — kept as the existing spacer-based +/- stepper
    // ------------------------------------------------------------------

    private void stepVerticalPosition(int delta) {
        int newPos = mVPos + delta;
        if (newPos < 0 || newPos > mVertSeekBar.getMax()) return;
        mVPos = newPos;
        mVertSeekBar.setProgress(mVPos);
        applyVerticalPosition();
    }

    private void applyVerticalPosition() {
        if (!touching) {
            mSubtitleManager.fadeSubtitlePositionHint(true);
            mVertSeekBar.postDelayed(() -> mSubtitleManager.fadeSubtitlePositionHint(false), 200);
        }
        mSubtitleManager.setVerticalPosition(mVPos);
    }

    // ------------------------------------------------------------------
    // SeekBar.OnSeekBarChangeListener (background opacity + vertical position)
    // ------------------------------------------------------------------

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (seekBar == mBgOpacitySeekBar) {
            mBgOpacity = progress;
            mSubtitleManager.setBackgroundOpacity(progress);
        } else if (seekBar == mVertSeekBar) {
            mVPos = progress;
            if (!touching) {
                mSubtitleManager.fadeSubtitlePositionHint(true);
                seekBar.postDelayed(() -> mSubtitleManager.fadeSubtitlePositionHint(false), 200);
            }
            mSubtitleManager.setVerticalPosition(mVPos);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        touching = true;
        if (seekBar == mVertSeekBar) {
            mSubtitleManager.fadeSubtitlePositionHint(true);
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        touching = false;
        if (seekBar == mVertSeekBar) {
            mSubtitleManager.fadeSubtitlePositionHint(false);
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle: restore on attach, persist on detach
    // ------------------------------------------------------------------

    @Override
    public void onDetachedFromWindow() {
        Log.d("Player", "onDetachedFromWindow");
        if (getWindow() != null) {
            WindowCompat.getInsetsController(getWindow(), mSampleText)
                    .show(WindowInsetsCompat.Type.statusBars());
        }
        mSharedPreferences.edit()
                .putInt(PlayerActivity.KEY_SUBTITLE_FONT_SIZE_PT, mFontSizePt)
                .putInt(PlayerActivity.KEY_SUBTITLE_VPOS, mVPos)
                .putInt(PlayerActivity.KEY_SUBTITLE_COLOR, mSubtitleManager.getColor())
                .putInt(PlayerActivity.KEY_SUBTITLE_BG_OPACITY, mBgOpacity)
                .putInt(PlayerActivity.KEY_SUBTITLE_BG_MODE, mSubtitleManager.getBgMode())
                .putInt(PlayerActivity.KEY_SUBTITLE_OVERRIDE_MODE, mSubtitleManager.getOverrideMode())
                .putBoolean(PlayerActivity.KEY_SUBTITLE_BOLD, mSubtitleManager.getBold())
                .putInt(PlayerActivity.KEY_SUBTITLE_OUTLINE_COLOR, mSubtitleManager.getOutlineColor())
                .putInt(PlayerActivity.KEY_SUBTITLE_SHADOW_COLOR, mSubtitleManager.getShadowColor())
                .putInt(PlayerActivity.KEY_SUBTITLE_BACKGROUND_COLOR, mSubtitleManager.getBackgroundColor())
                .putFloat(PlayerActivity.KEY_SUBTITLE_OUTLINE_WIDTH, mOutlineWidth)
                .putFloat(PlayerActivity.KEY_SUBTITLE_SHADOW_WIDTH, mSubtitleManager.getShadowWidth())
                .apply();
        mSubtitleManager.fadeSubtitlePositionHint(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindow() != null) {
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), mSampleText);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.statusBars());
        }

        mVertSeekBar.setProgress(mSubtitleManager.getVerticalPosition());
        mSubtitleManager.setShowSubtitlePositionHint(true);

        mSampleText.setTextSize(mFontSizePt);
        mSampleText.setTextColor(mSubtitleManager.getColor());
    }
}
