package com.archos.mediacenter.video.debug;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.archos.medialib.LibAvos;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

/** ADB-only setup for a speed campaign; this class exists only in debug builds. */
public final class SpeedTestReceiver extends BroadcastReceiver {
    private static final String BACKUP = "speed_test_preferences";
    private static final String SESSION = "session";
    private static final String SPEED = "save_audio_speed_setting_pref_key";
    private static final String PASSTHROUGH = "force_audio_passthrough_multiple";
    private static final String[] STRINGS = {
            VideoPreferencesCommon.KEY_AUDIO_SPEED_MODE,
            VideoPreferencesCommon.KEY_AUDIO_DECODER_CHOICE, PASSTHROUGH
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        String session = intent.getStringExtra(SESSION);
        String operation = intent.getStringExtra("operation");
        if (session == null || !session.matches("[a-zA-Z0-9_-]{1,80}")) {
            reject("invalid-session");
            return;
        }
        // The launcher stops playback before changing or restoring preferences.
        if (PlayerService.sPlayerService != null) {
            reject("stop-playback-first");
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences backup = context.getSharedPreferences(BACKUP, Context.MODE_PRIVATE);
        if ("restore".equals(operation)) {
            if (!session.equals(backup.getString(SESSION, null))) {
                reject("session-mismatch");
                return;
            }
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : STRINGS) {
                if (backup.contains(key)) restore.putString(key, backup.getString(key, null));
                else restore.remove(key);
            }
            if (backup.contains(SPEED)) restore.putFloat(SPEED, backup.getFloat(SPEED, 1.0f));
            else restore.remove(SPEED);
            if (!restore.commit() || !backup.edit().clear().commit()) {
                reject("restore-failed");
                return;
            }
            acknowledge("restored", session, "");
            return;
        }
        String backend = intent.getStringExtra("backend");
        String mode;
        if ("atempo".equals(backend)) mode = VideoPreferencesCommon.AUDIO_SPEED_MODE_ATEMPO;
        else if ("audiotrack".equals(backend)) mode = VideoPreferencesCommon.AUDIO_SPEED_MODE_AUDIOTRACK;
        else if ("sonic".equals(backend)) mode = VideoPreferencesCommon.AUDIO_SPEED_MODE_SONIC;
        else {
            reject("invalid-backend");
            return;
        }
        if (!"configure".equals(operation) || backup.contains(SESSION)) {
            reject("invalid-operation-or-pending-restore");
            return;
        }
        SharedPreferences.Editor save = backup.edit().clear().putString(SESSION, session);
        for (String key : STRINGS) {
            if (prefs.contains(key)) save.putString(key, prefs.getString(key, null));
        }
        if (prefs.contains(SPEED)) save.putFloat(SPEED, prefs.getFloat(SPEED, 1.0f));
        // Persist the backup first so interrupted setup remains recoverable.
        if (!save.commit() || !prefs.edit()
                .putString(VideoPreferencesCommon.KEY_AUDIO_SPEED_MODE, mode)
                .putString(VideoPreferencesCommon.KEY_AUDIO_DECODER_CHOICE, "1")
                .putString(PASSTHROUGH, "0").putFloat(SPEED, 1.0f).commit()) {
            reject("configure-failed");
            return;
        }
        LibAvos.init(context);
        LibAvos.debugInit();
        LibAvos.avsh("dbgs 2");
        LibAvos.avsh("dbgsink 2");
        LibAvos.avsh("dbga 2");
        acknowledge("configured", session, ":" + backend);
    }

    private void acknowledge(String operation, String session, String suffix) {
        setResultCode(Activity.RESULT_OK);
        setResultData("speed-test:" + operation + ":" + session + suffix);
    }

    private void reject(String reason) {
        setResultCode(Activity.RESULT_CANCELED);
        setResultData("speed-test:error:" + reason);
    }
}
