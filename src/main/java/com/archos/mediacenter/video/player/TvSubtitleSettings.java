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
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.archos.mediacenter.video.R;

/**
 * The TV's subtitle settings: SubtitleSettingsPanel docked to the right edge of the TV menu
 * container, replacing the old card. All the rows, rules and saving live in the panel and in
 * SubtitleManager; this only places it, and ties it to PlayerController for Back and for 3D.
 *
 * Back: PlayerController asks the overlay first (handleBack()): the panel goes up one level
 * (Color -> Advanced -> Basic) and only on Basic does the controller remove it. Hiding the TV menu
 * removes it too. The card row slides away while it is open (setDiscrete), exactly as it did for
 * the old card.
 */
public class TvSubtitleSettings implements PlayerController.MenuOverlay, SubtitleSettingsPanel.Host {

    private final Context mContext;
    private final PlayerController mController;
    private final SubtitleManager mManager;
    private final SharedPreferences mPrefs;
    private final Context mThemed;
    private SubtitleSettingsPanel mPanel;

    public static void show(Context context, PlayerController controller, SubtitleManager manager,
                            SharedPreferences prefs) {
        new TvSubtitleSettings(context, controller, manager, prefs).open();
    }

    private TvSubtitleSettings(Context context, PlayerController controller, SubtitleManager manager,
                               SharedPreferences prefs) {
        mContext = context;
        mController = controller;
        mManager = manager;
        mPrefs = prefs;
        // Sizes come from an overlay on the player's own theme, so accent and fonts stay the player's.
        mThemed = new ContextThemeWrapper(context, R.style.ThemeOverlay_SubtitlePanel_Tv);
    }

    @SuppressLint("InflateParams")
    private SubtitleSettingsPanel inflatePanel() {
        return (SubtitleSettingsPanel) LayoutInflater.from(mThemed).inflate(R.layout.subtitle_settings_panel, null);
    }

    @SuppressLint("RtlHardcoded")
    private void open() {
        mPanel = inflatePanel();
        mPanel.attach(mManager, mPrefs, this);

        final DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        final float fraction = SubtitleSettingsPanel.fractionRes(mContext.getResources(),
                R.dimen.subtitle_panel_width_fraction_tv);
        // The TV menu container is laid out left-to-right: RIGHT is the right edge on every locale.
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (dm.widthPixels * fraction), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT);

        mController.getTVMenuAdapter().setDiscrete(true);
        mController.addMenuOverlay(mPanel, lp, this);
        mPanel.requestInitialFocus();
    }

    // ---- PlayerController.MenuOverlay ---------------------------------------------------------

    @Override
    public boolean handleBack() {
        return mPanel != null && mPanel.handleBack();
    }

    /** Side-by-side 3D: the same panel again for the right-hand view, following this one. */
    @Override
    public View createMirror() {
        final SubtitleSettingsPanel mirror = inflatePanel();
        mirror.attachAsMirror(mPanel, mManager, mPrefs, this);
        return mirror;
    }

    @Override
    public void onRemoved() {
        mController.getTVMenuAdapter().setDiscrete(false);
        mManager.fadeSubtitlePositionHint(false);
        mPanel = null;
    }

    // ---- SubtitleSettingsPanel.Host -----------------------------------------------------------

    @Override
    public void onCloseRequested() {
        mController.removeMenuOverlay();
    }

    @Override
    public int getVideoShortSide() {
        return mManager.getScreenShortSide();
    }

    @Override
    public boolean isTv() {
        return true;
    }
}
