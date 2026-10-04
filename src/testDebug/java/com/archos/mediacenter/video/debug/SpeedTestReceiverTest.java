package com.archos.mediacenter.video.debug;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Looper;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.medialib.LibAvos;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 30)
public class SpeedTestReceiverTest {
    private String send(Context context, String operation, String session, String backend) {
        SpeedTestReceiver receiver = new SpeedTestReceiver();
        String action = "test.speed.CONFIGURE";
        // This manifest-free test exercises ordered results. Production access
        // is restricted separately by the debug manifest's DUMP permission.
        androidx.core.content.ContextCompat.registerReceiver(context, receiver,
                new IntentFilter(action), androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
        String[] reply = new String[1];
        Intent intent = new Intent(action).putExtra("operation", operation)
                .putExtra("session", session).putExtra("backend", backend);
        context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent result) {
                reply[0] = getResultData();
            }
        }, null, 0, null, null);
        shadowOf(Looper.getMainLooper()).idle();
        context.unregisterReceiver(receiver);
        return reply[0];
    }

    @Test
    public void savesAndRestoresOnlyTestPreferencesAcrossReceiverInstances() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("audio_speed_mode", "1")
                .putString("force_audio_passthrough_multiple", "2")
                .putFloat("save_audio_speed_setting_pref_key", 1.25f)
                .putString("unrelated", "keep").commit();
        try (MockedStatic<LibAvos> nativeCalls = mockStatic(LibAvos.class)) {
            assertEquals("speed-test:configured:session1:sonic", send(context, "configure", "session1", "sonic"));
            assertEquals("3", prefs.getString("audio_speed_mode", null));
            assertEquals("1", prefs.getString("audio_decoder_choice", null));
            assertEquals("0", prefs.getString("force_audio_passthrough_multiple", null));
            assertEquals(1f, prefs.getFloat("save_audio_speed_setting_pref_key", 0), 0);
            nativeCalls.verify(() -> LibAvos.avsh("dbgs 2"));
            nativeCalls.verify(() -> LibAvos.avsh("dbgsink 2"));
            nativeCalls.verify(() -> LibAvos.avsh("dbga 2"));
            assertEquals("speed-test:error:invalid-operation-or-pending-restore",
                    send(context, "configure", "session2", "atempo"));
            assertEquals("speed-test:error:session-mismatch", send(context, "restore", "session2", null));
            assertEquals("speed-test:restored:session1", send(context, "restore", "session1", null));
            assertEquals("1", prefs.getString("audio_speed_mode", null));
            assertFalse(prefs.contains("audio_decoder_choice"));
            assertEquals("2", prefs.getString("force_audio_passthrough_multiple", null));
            assertEquals(1.25f, prefs.getFloat("save_audio_speed_setting_pref_key", 0), 0);
            assertEquals("keep", prefs.getString("unrelated", null));
            assertFalse(context.getSharedPreferences("speed_test_preferences", Context.MODE_PRIVATE).contains("session"));
        }
    }

    @Test
    public void rejectsInvalidBackendBeforeSavingOrChangingPreferences() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("audio_speed_mode", "1").commit();
        assertEquals("speed-test:error:invalid-backend", send(context, "configure", "session1", "unknown"));
        assertEquals("1", prefs.getString("audio_speed_mode", null));
        assertFalse(context.getSharedPreferences("speed_test_preferences", Context.MODE_PRIVATE).contains("session"));
    }
}
