/* SPDX-License-Identifier: Apache-2.0 */
package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.archos.environment.ArchosFeatures;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.medialib.LibAvos;
import com.archos.medialib.SofaProfileManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One policy shared by settings, player menus and background playback. */
public final class SpatializationSettings {
    public static final String KEY_MODE = "player_spatialization_mode";
    public static final int OFF = 0, SYSTEM = 1, TV = 2, HEADPHONES = 3;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static int generation;
    private static int appliedMode = -1;

    private SpatializationSettings() {}

    public static int getMode(SharedPreferences prefs) {
        if (!prefs.contains(KEY_MODE)) {
            int sofa = prefs.getInt("player_sofa_mode", 0);
            int mode = sofa == 1 ? TV : sofa == 2 ? HEADPHONES :
                    prefs.getBoolean("player_spatialization_enabled", false) ? SYSTEM : OFF;
            prefs.edit().putString(KEY_MODE, Integer.toString(mode)).apply();
            return mode;
        }
        try {
            int mode = Integer.parseInt(prefs.getString(KEY_MODE, "0"));
            return mode >= OFF && mode <= HEADPHONES ? mode : OFF;
        } catch (NumberFormatException e) { return OFF; }
    }
    public static boolean isPassthrough(SharedPreferences prefs) {
        return !"0".equals(prefs.getString("force_audio_passthrough_multiple", "0"));
    }
    public static boolean isSystemAvailable() {
        int caps = CustomApplication.getSpatializerCapabilities();
        int required = CodecDiscovery.SPATIALIZER_CAP_SUPPORTED | CodecDiscovery.SPATIALIZER_CAP_AVAILABLE
                | CodecDiscovery.SPATIALIZER_CAP_ENABLED;
        return Build.VERSION.SDK_INT >= 32 && (caps & required) == required &&
                (caps & (CodecDiscovery.SPATIALIZER_CAP_CAN_SPATIALIZE_5_1 |
                        CodecDiscovery.SPATIALIZER_CAP_CAN_SPATIALIZE_7_1)) != 0;
    }
    public static int effectiveMode(SharedPreferences prefs) {
        if (isPassthrough(prefs)) return OFF;
        int mode = getMode(prefs);
        return mode == SYSTEM && !isSystemAvailable() ? OFF : mode;
    }
    public static String label(Context context, SharedPreferences prefs) {
        int mode = getMode(prefs);
        String label = context.getResources().getStringArray(R.array.spatialization_entries)[mode];
        if (mode != OFF && effectiveMode(prefs) == OFF)
            label += " (" + context.getString(R.string.spatialization_unavailable) + ")";
        return context.getString(R.string.spatialization_title) + ": " + label;
    }
    public static void applyDownmix(Context context, SharedPreferences prefs) {
        applyDownmix(context, prefs, effectiveMode(prefs));
    }
    private static void applyDownmix(Context context, SharedPreferences prefs, int mode) {
        if (!LibAvos.isAvailable()) return;
        if (isPassthrough(prefs) || mode != OFF) LibAvos.setDownmix(0);
        else if (ArchosFeatures.isAndroidTV(context))
            LibAvos.setDownmix(prefs.getBoolean("enable_downmix_androidtv", false) ? 1 : 0);
        else LibAvos.setDownmix(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                prefs.getBoolean("disable_downmix", false) ? 0 : 1);
    }
    /** Call on the main thread; extraction/checksums never run on that thread. */
    public static void apply(Context context, SharedPreferences prefs, Runnable refresh) {
        apply(context, prefs, refresh, false);
    }
    /** Complete profile preparation before native prepare opens the decoder/sink. */
    public static void prepareForPlayback(Context context, SharedPreferences prefs, Runnable ready) {
        apply(context, prefs, ready, true);
    }
    private static void apply(Context context, SharedPreferences prefs, Runnable refresh, boolean preparing) {
        if (!LibAvos.isAvailable()) { if (preparing) refresh.run(); return; }
        Context app = context.getApplicationContext();
        int request = ++generation;
        int mode = effectiveMode(prefs);
        if (mode == TV || mode == HEADPHONES) {
            WORKER.execute(() -> {
                String path = SofaProfileManager.getInstance(app).getProfilePathByMode(mode - 1);
                MAIN.post(() -> {
                    if (request != generation) {
                        if (preparing) apply(app, prefs, refresh, true);
                        return;
                    }
                    // Passthrough or another preference may have changed while
                    // the profile was being extracted. Never publish stale DSP policy.
                    if (mode != effectiveMode(prefs)) {
                        apply(app, prefs, refresh, preparing);
                        return;
                    }
                    if (path == null) {
                        applyNative(app, prefs, OFF, null, refresh, preparing);
                        Toast.makeText(app, R.string.spatialization_profile_error, Toast.LENGTH_LONG).show();
                    } else applyNative(app, prefs, mode, path, refresh, preparing);
                });
            });
        } else applyNative(app, prefs, mode, null, refresh, preparing);
    }
    private static void applyNative(Context context, SharedPreferences prefs, int mode, String path, Runnable refresh, boolean preparing) {
        int sofaMode = mode >= TV ? mode - 1 : 0;
        if (!LibAvos.setSofaMode(sofaMode, path)) {
            LibAvos.setSofaMode(0, null);
            LibAvos.setSpatializerEnabled(false);
            applyDownmix(context, prefs, OFF);
            appliedMode = OFF;
            Toast.makeText(context, R.string.spatialization_profile_error, Toast.LENGTH_LONG).show();
            if (refresh != null) refresh.run();
            return;
        }
        LibAvos.setSpatializerEnabled(mode == SYSTEM);
        applyDownmix(context, prefs, mode);
        boolean changed = appliedMode != mode;
        appliedMode = mode;
        if ((changed || preparing) && refresh != null) refresh.run();
    }
}
