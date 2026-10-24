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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.player.SubtitleSettingsModel.ColorTarget;

/**
 * The phone's subtitle settings, in the player's usual dialog: an AlertDialog hung from the top, a
 * translucent surface and no dim, so the video and the live subtitle stay visible while adjusting.
 * What the rows are comes from SubtitleSettingsModel (shared with the TV), drawn by
 * SubtitleSettingsPhoneRows; every change is applied and saved as it is made.
 *
 * Colors have a page of their own (presets, then R / G / B steppers). Back goes from there to the
 * settings, at the scroll position it was left at, and only from the settings does it dismiss.
 */
public class SubtitleSettingsDialog extends AlertDialog
        implements SubtitleSettingsPhoneRows.Callbacks, SubtitleSettingsModel.Listener {

    private final SubtitleManager mSubtitleManager;
    private final SubtitleSettingsModel mModel;
    private SubtitleSettingsPhoneRows mRows;
    private SubtitlePreviewView mPreview;
    private ScrollView mScroll;
    private TextView mBack;             // visible on the color page: up to the settings
    private ColorTarget mColorPage;     // null: the settings themselves
    private int mSettingsScrollY;

    public SubtitleSettingsDialog(Context context, SubtitleManager subtitleManager) {
        super(context);
        mSubtitleManager = subtitleManager;
        mModel = new SubtitleSettingsModel(context, subtitleManager,
                PreferenceManager.getDefaultSharedPreferences(context), false);
        mModel.setListener(this);
        init(context);
    }

    @SuppressLint("InflateParams") // Custom content view for AlertDialog.setView() has no parent container at inflation
    private void init(Context context) {
        final Context themed = new androidx.appcompat.view.ContextThemeWrapper(
            context, R.style.ThemeOverlay_SubtitlePanel_Phone);
        setIcon(R.drawable.ic_menu_settings);
        setTitle(mModel.getTitleRes(null));

        getWindow().setGravity(Gravity.TOP);
        getWindow().setBackgroundDrawable(new ColorDrawable(VideoInfoCommonClass.getAlphaColor(
                ContextCompat.getColor(context, R.color.background_material_dark), 128)));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        final View view = LayoutInflater.from(themed).inflate(R.layout.subtitle_settings_dialog, null);
        mPreview = view.findViewById(R.id.sp_preview);
        mPreview.bind(mSubtitleManager, mSubtitleManager::getScreenShortSide);
        mScroll = view.findViewById(R.id.sp_scroll);
        mBack = view.findViewById(R.id.sp_back);
        mBack.setText("\u2039  " + context.getString(mModel.getTitleRes(null)));
        mBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPage(null); // same as Back: up to the settings
            }
        });
        mRows = new SubtitleSettingsPhoneRows((ViewGroup) view.findViewById(R.id.sp_rows), mModel, this);
        setView(view);

        setCancelable(true);
        setCanceledOnTouchOutside(true);
        mRows.show(null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mColorPage != null) {
                    showPage(null);   // one level up: the settings
                } else {
                    cancel();         // the usual dismissal
                }
            }
        });
    }

    private void showPage(final ColorTarget target) {
        if (target != null && mColorPage == null) mSettingsScrollY = mScroll.getScrollY();
        mColorPage = target;
        mBack.setVisibility(target != null ? View.VISIBLE : View.GONE);
        setTitle(mModel.getTitleRes(target));
        mRows.show(target);
        mScroll.post(new Runnable() {
            @Override
            public void run() {
                mScroll.scrollTo(0, target == null ? mSettingsScrollY : 0);
            }
        });
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mModel.open();
        if (getWindow() != null) { // no status bar over the video while adjusting
            final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), mPreview);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.statusBars());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (getWindow() != null) {
            WindowCompat.getInsetsController(getWindow(), mPreview).show(WindowInsetsCompat.Type.statusBars());
        }
        mRows.release();
        mModel.release();
        super.onDetachedFromWindow();
    }

    // ---- SubtitleSettingsPhoneRows.Callbacks / SubtitleSettingsModel.Listener -------------------

    @Override
    public void onOpenColor(ColorTarget target) {
        showPage(target);
    }

    @Override
    public void onRefreshed() {
        mPreview.invalidate();
    }

    @Override
    public void onModelChanged() { // the Reset confirmation timed out
        mRows.refresh();
    }
}
