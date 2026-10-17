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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/** Which rows exist for which kind and mode: the table the settings design is built on. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SubtitleSettingsPanelTest {

    private SubtitleManager mManager;
    private SharedPreferences mPrefs;
    private SubtitleSettingsPanel mPanel;
    private boolean mClosed;

    private SubtitleSettingsPanel inflatePanel(boolean tv) {
        final Context app = RuntimeEnvironment.getApplication();
        final Context themed = new ContextThemeWrapper(
                new ContextThemeWrapper(app, androidx.appcompat.R.style.Theme_AppCompat),
                tv ? R.style.ThemeOverlay_SubtitlePanel_Tv : R.style.ThemeOverlay_SubtitlePanel_Phone);
        return (SubtitleSettingsPanel) LayoutInflater.from(themed).inflate(R.layout.subtitle_settings_panel, null);
    }

    private SubtitleSettingsPanel.Host host(final boolean tv) {
        return new SubtitleSettingsPanel.Host() {
            @Override
            public void onCloseRequested() {
                mClosed = true;
            }

            @Override
            public int getVideoShortSide() {
                return 1080;
            }

            @Override
            public boolean isTv() {
                return tv;
            }
        };
    }

    private SubtitleSettingsPanel newPanel(boolean tv) {
        final SubtitleSettingsPanel panel = inflatePanel(tv);
        panel.attach(mManager, mPrefs, host(tv));
        return panel;
    }

    @Before
    public void setUp() {
        final Context app = RuntimeEnvironment.getApplication();
        mManager = new SubtitleManager(app, new FrameLayout(app), null, false);
        mPrefs = app.getSharedPreferences("subtitle_panel_test", Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
        mManager.restoreStyle(mPrefs); // start from the defaults, like the player does
        mPanel = newPanel(false);
    }

    private List<String> rows(int kind, int mode, int bg) {
        mManager.setSubtitleKind(kind);
        mManager.setOverrideMode(mode);
        mManager.setBgMode(bg);
        mPanel.refresh();
        return mPanel.getRowIds();
    }

    private static List<String> ids(String... ids) {
        return Arrays.asList(ids);
    }

    /** The row view whose label is the given string resource. */
    private View rowLabelled(int labelRes) {
        final String text = RuntimeEnvironment.getApplication().getString(labelRes);
        final ViewGroup rows = mPanel.findViewById(R.id.sp_rows);
        for (int i = 0; i < rows.getChildCount(); i++) {
            final TextView label = rows.getChildAt(i).findViewById(R.id.sp_label);
            if (label != null && text.contentEquals(label.getText())) return rows.getChildAt(i);
        }
        return null;
    }

    @Test
    public void pillsUseTheStoredValueAsPosition() {
        // The pills show OVERRIDE_* / BG_MODE_* by index: these must stay 0, 1, 2 in this order.
        assertEquals(0, SubtitleManager.OVERRIDE_EMBEDDED);
        assertEquals(1, SubtitleManager.OVERRIDE_CUSTOM);
        assertEquals(2, SubtitleManager.OVERRIDE_SCALE_ONLY);
        assertEquals(0, SubtitleManager.BG_MODE_FLOATING);
        assertEquals(1, SubtitleManager.BG_MODE_BOXED_LINE);
        assertEquals(2, SubtitleManager.BG_MODE_BOXED_BLOCK);
    }

    @Test
    public void noSettingsForNoneGraphicOrUnsupported() {
        for (int kind : new int[] {SubtitleManager.KIND_NONE, SubtitleManager.KIND_GRAPHIC,
                SubtitleManager.KIND_UNSUPPORTED}) {
            assertEquals(ids("note_unavailable"),
                    rows(kind, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING));
        }
    }

    @Test
    public void plainTextIsLockedToCustomWhateverIsStored() {
        for (int stored : new int[] {SubtitleManager.OVERRIDE_EMBEDDED, SubtitleManager.OVERRIDE_CUSTOM,
                SubtitleManager.OVERRIDE_SCALE_ONLY}) {
            assertEquals(ids("note_plain_text", "size", "bold", "bg", "vpos", "advanced", "reset"),
                    rows(SubtitleManager.KIND_PLAIN_TEXT, stored, SubtitleManager.BG_MODE_FLOATING));
            assertEquals(stored, mManager.getOverrideMode()); // the user's choice for styled files is untouched
        }
    }

    @Test
    public void styledFileStyleShowsNothingToAdjust() {
        assertEquals(ids("mode", "note_mode", "reset"),
                rows(SubtitleManager.KIND_SSA, SubtitleManager.OVERRIDE_EMBEDDED, SubtitleManager.BG_MODE_FLOATING));
    }

    @Test
    public void styledScaleOnlyShowsOnlyScale() {
        assertEquals(ids("mode", "note_mode", "scale", "reset"),
                rows(SubtitleManager.KIND_SSA, SubtitleManager.OVERRIDE_SCALE_ONLY, SubtitleManager.BG_MODE_FLOATING));
    }

    @Test
    public void styledCustomShowsTheBasicSetAndAdvanced() {
        assertEquals(ids("mode", "note_mode", "size", "bold", "bg", "vpos", "advanced", "reset"),
                rows(SubtitleManager.KIND_SSA, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING));
    }

    @Test
    public void advancedFollowsTheBackgroundMode() {
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        rowLabelled(R.string.subtitle_panel_advanced).performClick();
        assertEquals(ids("sec_text", "size", "bold", "text_c", "sec_background", "bg",
                "outline_w", "outline_c", "shadow_w", "shadow_c", "sec_position", "vpos", "reset"),
                mPanel.getRowIds());

        mManager.setBgMode(SubtitleManager.BG_MODE_BOXED_LINE);
        mPanel.refresh();
        assertEquals(ids("sec_text", "size", "bold", "text_c", "sec_background", "bg",
                "bg_c", "opacity", "outline_w", "sec_position", "vpos", "reset"), mPanel.getRowIds());

        mManager.setBgMode(SubtitleManager.BG_MODE_BOXED_BLOCK);
        mPanel.refresh();
        assertEquals(ids("sec_text", "size", "bold", "text_c", "sec_background", "bg",
                "bg_c", "opacity", "outline_w", "outline_c", "shadow_w", "sec_position", "vpos", "reset"),
                mPanel.getRowIds());
    }

    @Test
    public void backGoesUpOneLevelAndThenAsksTheHostToClose() {
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        rowLabelled(R.string.subtitle_panel_advanced).performClick();
        rowLabelled(R.string.subtitle_panel_text_color).performClick();
        assertEquals(ids("grid", "rgb_r", "rgb_g", "rgb_b", "hex"), mPanel.getRowIds());

        assertTrue(mPanel.handleBack());   // color -> advanced
        assertTrue(mPanel.getRowIds().contains("sec_text"));
        assertTrue(mPanel.handleBack());   // advanced -> basic
        assertTrue(mPanel.getRowIds().contains("advanced"));
        assertFalse(mPanel.handleBack());  // basic: the host closes
    }

    @Test
    public void tvLeavesOutTheHexField() {
        mPanel = newPanel(true);
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        rowLabelled(R.string.subtitle_panel_advanced).performClick();
        rowLabelled(R.string.subtitle_panel_text_color).performClick();
        assertEquals(ids("grid", "rgb_r", "rgb_g", "rgb_b"), mPanel.getRowIds());
    }

    @Test
    public void theThreeDCopyFollowsTheMasterAndTakesNoInput() {
        mPanel = newPanel(true);
        final SubtitleSettingsPanel mirror = inflatePanel(true);
        mirror.attachAsMirror(mPanel, mManager, mPrefs, host(true));

        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        assertEquals(mPanel.getRowIds(), mirror.getRowIds());

        rowLabelled(R.string.subtitle_panel_advanced).performClick(); // master goes to Advanced
        assertTrue(mirror.getRowIds().contains("sec_text"));
        assertEquals(mPanel.getRowIds(), mirror.getRowIds());

        mManager.setBgMode(SubtitleManager.BG_MODE_BOXED_LINE);       // rows change with the mode
        mPanel.refresh();
        assertEquals(mPanel.getRowIds(), mirror.getRowIds());

        assertTrue(mPanel.handleBack());                              // and back up again
        assertEquals(mPanel.getRowIds(), mirror.getRowIds());

        final MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        assertTrue(mirror.onInterceptTouchEvent(down));               // the copy only shows
        down.recycle();
    }

    @Test
    public void leftAndRightMoveFocusBetweenColorTilesAndStayInThePanel() {
        mPanel = newPanel(true);
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        rowLabelled(R.string.subtitle_panel_advanced).performClick();
        rowLabelled(R.string.subtitle_panel_text_color).performClick();
        mPanel.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY));
        mPanel.layout(0, 0, 800, 1200);

        final ViewGroup grid = (ViewGroup) ((ViewGroup) mPanel.findViewById(R.id.sp_rows)).getChildAt(0);
        final ViewGroup line = (ViewGroup) grid.getChildAt(0);
        for (int i = 0; i < line.getChildCount(); i++) line.getChildAt(i).setFocusableInTouchMode(true);
        final View first = line.getChildAt(0);
        final View second = line.getChildAt(1);

        assertTrue(first.requestFocus());
        // The player's own onKeyDown() takes Left/Right for itself, so the panel has to move focus
        // and consume the key: it must never be left to the default navigation.
        assertTrue(mPanel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertTrue(second.isFocused());
        assertTrue(mPanel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        assertTrue(first.isFocused());
        // at the left edge: nothing to move to, focus stays in the panel, the key is still consumed
        assertTrue(mPanel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)));
        assertTrue(first.isFocused());
    }

    @Test
    public void aChangeIsAppliedAndSavedAtOnce() {
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        final int before = mManager.getFontSizePt();
        rowLabelled(R.string.subtitle_panel_font_size).findViewById(R.id.sp_plus).performClick();
        assertEquals(before + 1, mManager.getFontSizePt());
        assertEquals(before + 1, mPrefs.getInt(SubtitleManager.KEY_FONT_SIZE_PT, -1));
    }

    @Test
    public void pickingAColorKeepsItsAlpha() {
        mManager.setShadowColor(0x80123456);
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        rowLabelled(R.string.subtitle_panel_advanced).performClick();
        rowLabelled(R.string.subtitle_panel_shadow_color).performClick();
        final ViewGroup grid = (ViewGroup) ((ViewGroup) mPanel.findViewById(R.id.sp_rows)).getChildAt(0);
        assertNotNull(grid);
        final View firstTile = ((ViewGroup) grid.getChildAt(0)).getChildAt(0);
        final int rgb = (Integer) firstTile.getTag(R.id.sp_tile_rgb);
        firstTile.performClick();
        assertEquals(0x80000000 | rgb, mManager.getShadowColor());
        assertEquals(mManager.getShadowColor(), mPrefs.getInt(SubtitleManager.KEY_SHADOW_COLOR, 0));
    }

    @Test
    public void resetNeedsASecondPressAndThenForgetsTheSavedStyle() {
        rows(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.OVERRIDE_CUSTOM, SubtitleManager.BG_MODE_FLOATING);
        mManager.setFontSizePt(99);
        mPrefs.edit().putInt(SubtitleManager.KEY_FONT_SIZE_PT, 99).commit();

        mPanel.onResetPressed();
        assertEquals(99, mManager.getFontSizePt()); // first press only asks

        mPanel.onResetPressed();
        assertEquals(mManager.getDefaults().fontSizePt, mManager.getFontSizePt());
        assertFalse(mPrefs.contains(SubtitleManager.KEY_FONT_SIZE_PT));
        assertEquals(0, mPrefs.getAll().size());
    }
}
