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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 33)
public class TraktSettingsTest {
    @Test
    public void rotatingTokensWithSettingsOpenPreservesSyncState() {
        Application app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        Trakt.initApiKeys(app);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(app);
        String timestamp = TraktService.PREFERENCE_TRAKT_LAST_TIME_SYNC_TO_DB_PROGRESS;
        pref.edit().putString(Trakt.KEY_TRAKT_ACCESS_TOKEN, "first")
                .putString("trakt_refresh_token", "refresh").putLong(timestamp, 123L).apply();

        try (MockedStatic<CustomApplication> application = mockStatic(CustomApplication.class);
             ActivityController<VideoPreferencesActivity> activity = Robolectric.buildActivity(VideoPreferencesActivity.class)) {
            application.when(CustomApplication::getAppContext).thenReturn(app);
            activity.setup();
            // Opening Settings requests an initial sync. Observe only work added by rotation.
            assertTrue(shadowOf(app).getNextStartedService().getAction().endsWith(".SYNC"));
            assertNull(shadowOf(app).getNextStartedService());
            pref.edit().putString(Trakt.KEY_TRAKT_ACCESS_TOKEN, "rotated")
                    .putString("trakt_refresh_token", "rotated-refresh").apply();
            assertEquals(123L, pref.getLong(timestamp, 0));
            assertNull(shadowOf(app).getNextStartedService());
        }
    }
}
