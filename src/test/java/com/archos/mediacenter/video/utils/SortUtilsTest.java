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

package com.archos.mediacenter.video.utils;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.mediaprovider.video.VideoStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SortUtilsTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void testResolveSortOrderMovie() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, true)
                .commit();

        String raw = "name COLLATE LOCALIZED ASC";
        String resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.MOVIE, raw);
        assertEquals(VideoStore.Video.VideoColumns.SCRAPER_M_SORT_NAME + " COLLATE LOCALIZED ASC", resolved);

        // When ignore articles is disabled
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, false)
                .commit();

        resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.MOVIE, raw);
        assertEquals("name COLLATE LOCALIZED ASC", resolved);
    }

    @Test
    public void testResolveSortOrderShow() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, true)
                .commit();

        String raw = VideoStore.Video.VideoColumns.SCRAPER_TITLE + " COLLATE LOCALIZED ASC";
        String resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.SHOW, raw);
        assertEquals(VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME + " COLLATE LOCALIZED ASC", resolved);

        // When ignore articles is disabled
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, false)
                .commit();

        resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.SHOW, VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME + " COLLATE LOCALIZED ASC");
        assertEquals(VideoStore.Video.VideoColumns.SCRAPER_TITLE + " COLLATE LOCALIZED ASC", resolved);
    }

    @Test
    public void testResolveSortOrderCollection() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, true)
                .commit();

        String raw = VideoStore.Video.VideoColumns.SCRAPER_C_NAME + " COLLATE LOCALIZED ASC";
        String resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.COLLECTION, raw);
        assertEquals(VideoStore.Video.VideoColumns.SCRAPER_C_SORT_NAME + " COLLATE LOCALIZED ASC", resolved);

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, false)
                .commit();

        resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.COLLECTION, raw);
        assertEquals(VideoStore.Video.VideoColumns.SCRAPER_C_NAME + " COLLATE LOCALIZED ASC", resolved);
    }

    @Test
    public void testResolveSortOrderVideoViewNullSafe() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, true)
                .commit();

        String raw = "name COLLATE LOCALIZED ASC";
        String resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.VIDEO_VIEW, raw);
        String expected = "COALESCE(NULLIF(" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + ", ''), name) COLLATE LOCALIZED ASC";
        assertEquals(expected, resolved);

        // When disabled: remains raw name
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, false)
                .commit();

        resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.VIDEO_VIEW, raw);
        assertEquals("name COLLATE LOCALIZED ASC", resolved);
    }

    @Test
    public void testResolveSortOrderVideoViewCompound() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, true)
                .commit();

        String compoundSearchSort = "CASE WHEN scraper_title LIKE ? THEN 0 ELSE 1 END, name COLLATE LOCALIZED ASC, scraper_e_season ASC, scraper_e_episode ASC";
        String resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.VIDEO_VIEW, compoundSearchSort);
        String expected = "CASE WHEN scraper_title LIKE ? THEN 0 ELSE 1 END, COALESCE(NULLIF(" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + ", ''), name) COLLATE LOCALIZED ASC, scraper_e_season ASC, scraper_e_episode ASC";
        assertEquals(expected, resolved);

        // When disabled
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, false)
                .commit();

        resolved = SortUtils.resolveSortOrder(context, SortUtils.SortScope.VIDEO_VIEW, compoundSearchSort);
        assertEquals(compoundSearchSort, resolved);
    }
}
