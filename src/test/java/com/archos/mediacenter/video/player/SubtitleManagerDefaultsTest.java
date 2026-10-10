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
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.FrameLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SubtitleManagerDefaultsTest {
    private SubtitleManager mManager;
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        mManager = new SubtitleManager(ctx, new FrameLayout(ctx), null, false);
        mPrefs = ctx.getSharedPreferences("subtitle_defaults_test", Context.MODE_PRIVATE);
        mPrefs.edit().clear().commit();
    }

    private void assertStyleIsDefaults() {
        SubtitleManager.Defaults d = mManager.getDefaults();
        assertEquals(d.color, mManager.getColor());
        assertEquals(d.overrideMode, mManager.getOverrideMode());
        assertEquals(d.bgMode, mManager.getBgMode());
        assertEquals(d.fontSizePt, mManager.getFontSizePt());
        assertEquals(d.fontScale, mManager.getFontScale(), 0.001f);
        assertEquals(d.bold, mManager.getBold());
        assertEquals(d.outlineColor, mManager.getOutlineColor());
        assertEquals(d.shadowColor, mManager.getShadowColor());
        assertEquals(d.backgroundColor, mManager.getBackgroundColor());
        assertEquals(d.bgOpacity, mManager.getBackgroundOpacity());
        assertEquals(d.outlineWidth, mManager.getOutlineWidth(), 0.001f);
        assertEquals(d.shadowWidth, mManager.getShadowWidth(), 0.001f);
    }

    @Test
    public void restoreWithNothingSavedAppliesTheDefaults() {
        mManager.setFontSizePt(99); // something else first, so the restore really has to write
        mManager.setBold(true);
        mManager.restoreStyle(mPrefs);
        assertStyleIsDefaults();
    }

    @Test
    public void savedValuesWinOverDefaults() {
        mPrefs.edit()
                .putInt(SubtitleManager.KEY_FONT_SIZE_PT, 80)
                .putFloat(SubtitleManager.KEY_FONT_SCALE, 1.5f)
                .putBoolean(SubtitleManager.KEY_BOLD, true)
                .putInt(SubtitleManager.KEY_OVERRIDE_MODE, SubtitleManager.OVERRIDE_SCALE_ONLY)
                .putInt(SubtitleManager.KEY_BG_MODE, SubtitleManager.BG_MODE_BOXED_BLOCK)
                .putInt(SubtitleManager.KEY_SHADOW_COLOR, 0x80112233)
                .putFloat(SubtitleManager.KEY_OUTLINE_WIDTH, 4f)
                .commit();
        mManager.restoreStyle(mPrefs);
        assertEquals(80, mManager.getFontSizePt());
        assertEquals(1.5f, mManager.getFontScale(), 0.001f);
        assertEquals(true, mManager.getBold());
        assertEquals(SubtitleManager.OVERRIDE_SCALE_ONLY, mManager.getOverrideMode());
        assertEquals(SubtitleManager.BG_MODE_BOXED_BLOCK, mManager.getBgMode());
        assertEquals(0x80112233, mManager.getShadowColor());
        assertEquals(4f, mManager.getOutlineWidth(), 0.001f);
        // anything not saved still falls back to its default
        assertEquals(mManager.getDefaults().outlineColor, mManager.getOutlineColor());
    }

    @Test
    public void forgettingTheSavedStyleThenRestoringIsAReset() {
        mPrefs.edit()
                .putInt(SubtitleManager.KEY_FONT_SIZE_PT, 99)
                .putFloat(SubtitleManager.KEY_FONT_SCALE, 2f)
                .putBoolean(SubtitleManager.KEY_BOLD, true)
                .putInt(SubtitleManager.KEY_OVERRIDE_MODE, SubtitleManager.OVERRIDE_EMBEDDED)
                .putInt(SubtitleManager.KEY_BG_MODE, SubtitleManager.BG_MODE_BOXED_LINE)
                .putInt(SubtitleManager.KEY_SHADOW_COLOR, 0xFF00FF00)
                .putInt(SubtitleManager.KEY_VPOS, 200)
                .commit();
        mManager.restoreStyle(mPrefs);
        assertEquals(99, mManager.getFontSizePt()); // really customised first

        SharedPreferences.Editor e = mPrefs.edit();
        for (String key : SubtitleManager.STYLE_KEYS) e.remove(key);
        e.commit();
        mManager.restoreStyle(mPrefs);

        assertStyleIsDefaults();
        assertEquals(mManager.getDefaults().vpos,
                mPrefs.getInt(SubtitleManager.KEY_VPOS, mManager.getDefaults().vpos));
        assertEquals(0, mPrefs.getAll().size()); // and it is durable: nothing saved is left
    }

    @Test
    public void styleKeysCoverEveryStylePreference() {
        assertEquals(13, SubtitleManager.STYLE_KEYS.size());
        for (String key : new String[] {SubtitleManager.KEY_VPOS, SubtitleManager.KEY_COLOR,
                SubtitleManager.KEY_BG_OPACITY, SubtitleManager.KEY_BG_MODE,
                SubtitleManager.KEY_OVERRIDE_MODE, SubtitleManager.KEY_BOLD,
                SubtitleManager.KEY_OUTLINE_COLOR, SubtitleManager.KEY_SHADOW_COLOR,
                SubtitleManager.KEY_BACKGROUND_COLOR, SubtitleManager.KEY_OUTLINE_WIDTH,
                SubtitleManager.KEY_SHADOW_WIDTH, SubtitleManager.KEY_FONT_SIZE_PT,
                SubtitleManager.KEY_FONT_SCALE}) {
            assertTrue(key, SubtitleManager.STYLE_KEYS.contains(key));
        }
    }

    @Test
    public void preferenceKeysNeverChangeAndPlayerActivityAliasesThem() {
        // These strings are what is stored on users' devices.
        assertEquals("pref_play_subtitle_vpos_key", SubtitleManager.KEY_VPOS);
        assertEquals("pref_play_subtitle_color_key", SubtitleManager.KEY_COLOR);
        assertEquals("subtitle_bg_opacity", SubtitleManager.KEY_BG_OPACITY);
        assertEquals("pref_play_subtitle_bg_mode_key", SubtitleManager.KEY_BG_MODE);
        assertEquals("pref_play_subtitle_override_mode_key", SubtitleManager.KEY_OVERRIDE_MODE);
        assertEquals("pref_play_subtitle_bold_key", SubtitleManager.KEY_BOLD);
        assertEquals("pref_play_subtitle_outline_color_key", SubtitleManager.KEY_OUTLINE_COLOR);
        assertEquals("pref_play_subtitle_shadow_color_key", SubtitleManager.KEY_SHADOW_COLOR);
        assertEquals("pref_play_subtitle_background_color_key", SubtitleManager.KEY_BACKGROUND_COLOR);
        assertEquals("pref_play_subtitle_outline_width_key", SubtitleManager.KEY_OUTLINE_WIDTH);
        assertEquals("pref_play_subtitle_shadow_width_key", SubtitleManager.KEY_SHADOW_WIDTH);
        assertEquals("pref_play_subtitle_font_size_pt_key", SubtitleManager.KEY_FONT_SIZE_PT);
        assertEquals("pref_play_subtitle_font_scale_key", SubtitleManager.KEY_FONT_SCALE);

        assertEquals(SubtitleManager.KEY_VPOS, PlayerActivity.KEY_SUBTITLE_VPOS);
        assertEquals(SubtitleManager.KEY_COLOR, PlayerActivity.KEY_SUBTITLE_COLOR);
        assertEquals(SubtitleManager.KEY_BG_OPACITY, PlayerActivity.KEY_SUBTITLE_BG_OPACITY);
        assertEquals(SubtitleManager.KEY_BG_MODE, PlayerActivity.KEY_SUBTITLE_BG_MODE);
        assertEquals(SubtitleManager.KEY_OVERRIDE_MODE, PlayerActivity.KEY_SUBTITLE_OVERRIDE_MODE);
        assertEquals(SubtitleManager.KEY_BOLD, PlayerActivity.KEY_SUBTITLE_BOLD);
        assertEquals(SubtitleManager.KEY_OUTLINE_COLOR, PlayerActivity.KEY_SUBTITLE_OUTLINE_COLOR);
        assertEquals(SubtitleManager.KEY_SHADOW_COLOR, PlayerActivity.KEY_SUBTITLE_SHADOW_COLOR);
        assertEquals(SubtitleManager.KEY_BACKGROUND_COLOR, PlayerActivity.KEY_SUBTITLE_BACKGROUND_COLOR);
        assertEquals(SubtitleManager.KEY_OUTLINE_WIDTH, PlayerActivity.KEY_SUBTITLE_OUTLINE_WIDTH);
        assertEquals(SubtitleManager.KEY_SHADOW_WIDTH, PlayerActivity.KEY_SUBTITLE_SHADOW_WIDTH);
        assertEquals(SubtitleManager.KEY_FONT_SIZE_PT, PlayerActivity.KEY_SUBTITLE_FONT_SIZE_PT);
        assertEquals(SubtitleManager.KEY_FONT_SCALE, PlayerActivity.KEY_SUBTITLE_FONT_SCALE);
    }
}
