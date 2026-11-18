// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.mediacenter.video;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.IntPredicate;

/** Limited fallback for firmware that hides digital output behind an undocumented speaker. */
final class PassthroughProbe {
    private static final Logger log = LoggerFactory.getLogger(PassthroughProbe.class);
    private final IntPredicate supportsEncoding;
    // State belongs to the main thread; only AudioTrack construction runs on the worker.
    private String route;
    private volatile int generation;
    private boolean pending;
    private long codecs;

    PassthroughProbe() {
        this(PassthroughProbe::canOpenEncoding);
    }

    PassthroughProbe(IntPredicate supportsEncoding) {
        this.supportsEncoding = supportsEncoding;
    }

    static String routeKey(AudioDeviceInfo[] devices, AudioDeviceInfo selected, boolean telephony) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.M) return null;
        if (telephony || devices == null || devices.length == 0) return null;
        if (selected != null && selected.getType() != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return null;
        boolean speaker = false;
        boolean selectedPresent = selected == null;
        int[] ids = new int[devices.length];
        for (int i = 0; i < devices.length; i++) {
            AudioDeviceInfo device = devices[i];
            int type = device.getType();
            // A synthetic earpiece is present on Zappiti. All other outputs,
            // including USB, Bluetooth, headphones and known digital routes, veto probing.
            if (type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    && type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) return null;
            if (type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                if (!empty(device.getEncodings()) || hasChannels(device.getChannelCounts())) return null;
                speaker = true;
            }
            ids[i] = device.getId();
            if (selected != null && selected.getId() == device.getId()) selectedPresent = true;
        }
        if (!speaker || !selectedPresent) return null;
        Arrays.sort(ids);
        return Arrays.toString(ids);
    }

    private static boolean hasChannels(int[] counts) {
        if (counts != null) {
            for (int count : counts) if (count > 0) return true;
        }
        return false;
    }

    private static boolean empty(int[] values) {
        return values == null || values.length == 0;
    }

    long update(String newRoute, Executor worker, Executor main, Runnable completed) {
        if (Objects.equals(route, newRoute)) return codecs;
        route = newRoute;
        codecs = 0;
        pending = newRoute != null;
        int request = ++generation;
        if (pending) {
            worker.execute(() -> {
                long result = 0;
                // Optical-compatible formats only. Do not infer EAC3/HD codecs or IEC61937.
                for (int encoding : new int[] {AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_DTS}) {
                    if (generation != request) return;
                    if (supportsEncoding.test(encoding)) result |= 1L << encoding;
                }
                long supported = result;
                main.execute(() -> {
                    if (generation != request) return;
                    codecs = supported;
                    pending = false;
                    log.info("Passthrough probe: route={} codecs=0x{}", route, Long.toHexString(codecs));
                    completed.run();
                });
            });
        }
        return codecs;
    }

    boolean hasResult(String key) {
        return key != null && key.equals(route) && !pending;
    }

    static boolean supportsMode(long codecs, int mode) {
        return mode == 0 || (mode == 2 && codecs != 0)
                || (mode == 3 && (codecs & (1L << AudioFormat.ENCODING_AC3)) != 0);
    }

    boolean isPending() {
        return pending;
    }

    static boolean canOpenEncoding(int encoding) {
        AudioTrack track = null;
        try {
            int size = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, encoding);
            // Reject errors and implausible vendor sizes instead of allocating without a bound.
            if (size <= 0 || size > 1024 * 1024) return false;
            track = new AudioTrack(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                    new AudioFormat.Builder().setSampleRate(48000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(encoding).build(),
                    size, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            boolean supported = track.getState() == AudioTrack.STATE_INITIALIZED;
            log.info("Passthrough probe: encoding={} initialized={}", encoding, supported);
            return supported;
        } catch (RuntimeException e) {
            log.info("Passthrough probe: encoding={} rejected: {}", encoding, e.toString());
            return false;
        } finally {
            // Never play or write: this checks firmware acceptance, not receiver support.
            if (track != null) {
                try {
                    track.release();
                } catch (RuntimeException e) {
                    log.warn("Passthrough probe: release failed: {}", e.toString());
                }
            }
        }
    }
}
