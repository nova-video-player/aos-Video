// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.mediacenter.video;
import android.app.Application;
import android.content.Context;
import androidx.preference.PreferenceManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 23)
public class PassthroughPreferencesProbeTest {
    @Test
    public void settingsEnableMode2AfterProbeWithoutLosingSavedChoiceDuringRouteChanges() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        androidx.preference.PreferenceFragmentCompat fragment = mock(androidx.preference.PreferenceFragmentCompat.class);
        androidx.preference.ListPreference mode = new androidx.preference.ListPreference(context);
        mode.setValue("2");
        when(fragment.findPreference("force_audio_passthrough_multiple")).thenReturn(mode);
        com.archos.mediacenter.video.utils.VideoPreferencesCommon preferences =
                new com.archos.mediacenter.video.utils.VideoPreferencesCommon(fragment);
        android.content.SharedPreferences saved = PreferenceManager.getDefaultSharedPreferences(context);
        saved.edit().putString("force_audio_passthrough_multiple", "2").commit();
        setPreferenceField(preferences, "mSharedPreferences", saved);
        androidx.preference.CheckBoxPreference force = new androidx.preference.CheckBoxPreference(context);
        setPreferenceField(preferences, "mForceAudioPassthrough", force);
        setPreferenceField(preferences, "mEnableSpatialization", new androidx.preference.CheckBoxPreference(context));
        setPreferenceField(preferences, "mEnableDynamicAudioDelay", new androidx.preference.CheckBoxPreference(context));
        Method update = preferences.getClass().getDeclaredMethod("updateAudioOutputPreferences", boolean.class);
        update.setAccessible(true);
        Field listener = preferences.getClass().getDeclaredField("mAudioOutputListener");
        listener.setAccessible(true);
        Runnable refresh = (Runnable) listener.get(preferences);
        try (MockedStatic<CustomApplication> app = mockStatic(CustomApplication.class)) {
            app.when(CustomApplication::isPassthroughProbePending).thenReturn(true);
            update.invoke(preferences, true);
            assertFalse(mode.isEnabled());
            assertEquals("2", saved.getString("force_audio_passthrough_multiple", "0"));
            app.when(CustomApplication::isPassthroughProbePending).thenReturn(false);
            app.when(CustomApplication::isPassthroughSupported).thenReturn(true);
            app.when(CustomApplication::isUsingProbedPassthrough).thenReturn(true);
            app.when(() -> CustomApplication.isPassthroughModeSupported(2)).thenReturn(true);
            refresh.run();
            assertTrue(mode.isEnabled());
            assertTrue(force.isEnabled());
            assertTrue(java.util.Arrays.asList(mode.getEntryValues()).contains("2"));
            assertFalse(java.util.Arrays.asList(mode.getEntryValues()).contains("1"));
            assertFalse(java.util.Arrays.asList(mode.getEntryValues()).contains("3"));
            assertFalse(mode.callChangeListener("1"));
            assertFalse(mode.callChangeListener("3"));
            app.when(() -> CustomApplication.isPassthroughModeSupported(3)).thenReturn(true);
            refresh.run();
            assertTrue(java.util.Arrays.asList(mode.getEntryValues()).contains("3"));
            app.when(CustomApplication::isPassthroughSupported).thenReturn(false);
            refresh.run();
            assertFalse(mode.isEnabled());
            assertEquals("2", saved.getString("force_audio_passthrough_multiple", "0"));
        } finally {
            saved.edit().clear().commit();
        }
    }

    private static void setPreferenceField(Object preferences, String name, Object value) throws Exception {
        Field field = preferences.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(preferences, value);
    }

}
