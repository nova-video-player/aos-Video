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
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;

/**
 * The phone's subtitle settings: a window around SubtitleSettingsPanel, nothing more. All the
 * rows, rules and saving live in the panel and in SubtitleManager.
 *
 * Docking: portrait, the panel hangs from the top of the screen (as the old dialog did) and is
 * only as tall as its rows need, up to a share of the screen; landscape, it is docked to the
 * right edge, full height. The window has no dim and a transparent background: the panel draws
 * its own translucent surface, so the video and the live subtitle stay visible around it.
 */
public class SubtitleSettingsDialog extends AppCompatDialog implements SubtitleSettingsPanel.Host {

    private final SubtitleManager mSubtitleManager;
    private SubtitleSettingsPanel mPanel;
    private DockLayout mDock;

    public SubtitleSettingsDialog(Context context, SubtitleManager subtitleManager) {
        super(context);
        mSubtitleManager = subtitleManager;
    }

    @Override
    @SuppressLint("InflateParams")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        // Sizes come from an overlay on the player's own theme, so accent and fonts stay the player's.
        final Context themed = new ContextThemeWrapper(getContext(), R.style.ThemeOverlay_SubtitlePanel_Phone);
        mPanel = (SubtitleSettingsPanel) LayoutInflater.from(themed).inflate(R.layout.subtitle_settings_panel, null);
        mPanel.attach(mSubtitleManager, PreferenceManager.getDefaultSharedPreferences(getContext()), this);

        mDock = new DockLayout(themed);
        mDock.addView(mPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(mDock);

        final Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        applyDock();
        // Register the new back press dispatcher callback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mPanel == null || !mPanel.handleBack()) {
                    // Disable this callback to allow the default dialog back/dismiss behavior
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    // Re-enable in case the dialog remains alive
                    setEnabled(true);
                }
            }
        });
    }

    /** Portrait: hung from the top. Landscape: docked right, full height. */
    @SuppressLint("RtlHardcoded")
    private void applyDock() {
        final Window window = getWindow();
        if (window == null || mPanel == null) return;
        final DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
        final boolean landscape = getContext().getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mPanel.getLayoutParams();
        if (landscape) {
            final float width = SubtitleSettingsPanel.fractionRes(getContext().getResources(), R.dimen.subtitle_panel_width_fraction);
            window.setLayout((int) (dm.widthPixels * width), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.RIGHT | Gravity.TOP); // RIGHT, not END: the panel's rounded corners are on its left
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mPanel.setMaxHeight(0);
        } else {
            final float height = SubtitleSettingsPanel.fractionRes(getContext().getResources(), R.dimen.subtitle_panel_max_height_fraction);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.TOP);
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mPanel.setMaxHeight((int) (dm.heightPixels * height));
        }
        mPanel.setLayoutParams(lp);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindow() != null && mPanel != null) { // as before: no status bar over the video while adjusting
            final WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), mPanel);
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.statusBars());
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (getWindow() != null && mPanel != null) {
            WindowCompat.getInsetsController(getWindow(), mPanel).show(WindowInsetsCompat.Type.statusBars());
        }
        super.onDetachedFromWindow();
    }

    // ---- SubtitleSettingsPanel.Host -----------------------------------------------------------

    @Override
    public void onCloseRequested() {
        dismiss();
    }

    @Override
    public int getVideoShortSide() {
        return mSubtitleManager.getScreenShortSide();
    }

    @Override
    public boolean isTv() {
        return false;
    }

    /** Rotation does not recreate the player (it handles the change itself), so the dock is re-applied here. */
    private final class DockLayout extends FrameLayout {
        DockLayout(Context context) {
            super(context);
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            applyDock();
        }
    }
}
