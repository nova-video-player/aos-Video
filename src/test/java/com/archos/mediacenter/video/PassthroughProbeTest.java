// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.mediacenter.video;

import android.app.Application;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosFeatures;
import com.archos.medialib.LibAvos;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 23)
@LooperMode(LooperMode.Mode.PAUSED)
public class PassthroughProbeTest {
    private static AudioDeviceInfo device(int id, int type, int... encodings) {
        AudioDeviceInfo device = mock(AudioDeviceInfo.class);
        when(device.getId()).thenReturn(id);
        when(device.getType()).thenReturn(type);
        when(device.getEncodings()).thenReturn(encodings);
        when(device.getChannelCounts()).thenReturn(new int[0]);
        return device;
    }

    @Test
    @Config(sdk = 23)
    public void zappitiInventoryRemainsEligibleWhenTrackReportsSpeaker() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        AudioDeviceInfo earpiece = device(1, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        String key = PassthroughProbe.routeKey(new AudioDeviceInfo[] {speaker, earpiece}, null, false);
        assertNotNull(key);
        assertEquals(key, PassthroughProbe.routeKey(new AudioDeviceInfo[] {earpiece, speaker}, speaker, false));
        assertNull(PassthroughProbe.routeKey(new AudioDeviceInfo[] {earpiece, speaker}, earpiece, false));
    }

    @Test
    public void phonesAndAdvertisedSpeakerCapabilitiesDoNotTriggerProbe() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        AudioDeviceInfo[] devices = {speaker};
        assertNull(PassthroughProbe.routeKey(devices, null, true));
        when(speaker.getEncodings()).thenReturn(new int[] {AudioFormat.ENCODING_PCM_16BIT});
        assertNull(PassthroughProbe.routeKey(devices, null, false));
        when(speaker.getEncodings()).thenReturn(new int[0]);
        when(speaker.getChannelCounts()).thenReturn(new int[] {0});
        assertNotNull(PassthroughProbe.routeKey(devices, null, false));
        when(speaker.getChannelCounts()).thenReturn(new int[] {2});
        assertNull(PassthroughProbe.routeKey(devices, null, false));
        assertNull(PassthroughProbe.routeKey(new AudioDeviceInfo[0], null, false));
    }

    @Test
    public void everyOtherConnectedOutputVetoesFallbackEvenWithSpeakerSelected() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        for (int type : new int[] {AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE,
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_UNKNOWN, 26 /* BLE_HEADSET */, 27 /* BLE_SPEAKER */}) {
            AudioDeviceInfo other = device(3, type);
            assertNull("type=" + type, PassthroughProbe.routeKey(
                    new AudioDeviceInfo[] {speaker, other}, speaker, false));
        }
    }

    @Test
    public void codecProbeIsAsynchronousPerCodecAndCachedForUnchangedRoute() {
        Queue<Runnable> work = new ArrayDeque<>();
        Queue<Runnable> main = new ArrayDeque<>();
        java.util.List<Integer> attempted = new java.util.ArrayList<>();
        PassthroughProbe probe = new PassthroughProbe(encoding -> {
            attempted.add(encoding);
            return encoding == AudioFormat.ENCODING_AC3;
        });
        Runnable completed = mock(Runnable.class);
        assertEquals(0, probe.update("speaker", work::add, main::add, completed));
        assertTrue(probe.isPending());
        assertTrue(attempted.isEmpty());
        probe.update("speaker", work::add, main::add, completed);
        assertEquals(1, work.size());
        work.remove().run();
        assertTrue(probe.isPending());
        main.remove().run();
        assertFalse(probe.isPending());
        assertEquals(1L << AudioFormat.ENCODING_AC3,
                probe.update("speaker", work::add, main::add, completed));
        assertEquals(java.util.Arrays.asList(AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_DTS), attempted);
        assertTrue(work.isEmpty());
        verify(completed).run();
    }

    @Test
    public void disconnectDiscardsOutstandingResultAndReconnectionProbesAgain() {
        Queue<Runnable> work = new ArrayDeque<>();
        Queue<Runnable> main = new ArrayDeque<>();
        PassthroughProbe probe = new PassthroughProbe(encoding -> true);
        Runnable completed = mock(Runnable.class);
        probe.update("speaker", work::add, main::add, completed);
        assertEquals(0, probe.update(null, work::add, main::add, completed));
        work.remove().run();
        assertTrue(main.isEmpty());
        verifyNoInteractions(completed);
        assertFalse(probe.isPending());
        probe.update("speaker", work::add, main::add, completed);
        work.remove().run();
        main.remove().run();
        long optical = (1L << AudioFormat.ENCODING_AC3) | (1L << AudioFormat.ENCODING_DTS);
        assertEquals(optical, probe.update("speaker", work::add, main::add, completed));
    }

    @Test
    public void completedResultIsDiscardedIfRouteChangedBeforeMainThreadDelivery() {
        Queue<Runnable> work = new ArrayDeque<>();
        Queue<Runnable> main = new ArrayDeque<>();
        PassthroughProbe probe = new PassthroughProbe(encoding -> true);
        Runnable completed = mock(Runnable.class);
        probe.update("speaker", work::add, main::add, completed);
        work.remove().run();
        probe.update(null, work::add, main::add, completed);
        main.remove().run();
        verifyNoInteractions(completed);
        assertEquals(0, probe.update(null, work::add, main::add, completed));
    }

    @Test
    public void failuresAreCachedWithoutRepeatedTrackConstruction() {
        Queue<Runnable> work = new ArrayDeque<>();
        PassthroughProbe probe = new PassthroughProbe(encoding -> false);
        probe.update("speaker", work::add, Runnable::run, () -> {});
        work.remove().run();
        assertEquals(0, probe.update("speaker", work::add, Runnable::run, () -> {}));
        assertFalse(probe.isPending());
        assertTrue(work.isEmpty());
    }

    @Test
    @Config(sdk = 23)
    public void successfulTrackUsesCodecEncodingAndIsReleasedWithoutPlayback() {
        try (MockedStatic<AudioTrack> api = mockStatic(AudioTrack.class);
             MockedConstruction<AudioTrack> tracks = mockConstruction(AudioTrack.class, (track, context) -> {
                 when(track.getState()).thenReturn(AudioTrack.STATE_INITIALIZED);
                 AudioAttributes attributes = (AudioAttributes) context.arguments().get(0);
                 AudioFormat format = (AudioFormat) context.arguments().get(1);
                 assertEquals(AudioAttributes.USAGE_MEDIA, attributes.getUsage());
                 assertEquals(AudioFormat.ENCODING_AC3, format.getEncoding());
                 assertEquals(48000, format.getSampleRate());
                 assertEquals(AudioFormat.CHANNEL_OUT_STEREO, format.getChannelMask());
             })) {
            api.when(() -> AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_AC3)).thenReturn(4096);
            assertTrue(PassthroughProbe.canOpenEncoding(AudioFormat.ENCODING_AC3));
            AudioTrack track = tracks.constructed().get(0);
            verify(track).getState();
            verify(track).release();
            verifyNoMoreInteractions(track);
        }
    }

    @Test
    public void uninitializedTrackIsRejectedAndReleased() {
        try (MockedStatic<AudioTrack> api = mockStatic(AudioTrack.class);
             MockedConstruction<AudioTrack> tracks = mockConstruction(AudioTrack.class)) {
            api.when(() -> AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_DTS)).thenReturn(4096);
            assertFalse(PassthroughProbe.canOpenEncoding(AudioFormat.ENCODING_DTS));
            verify(tracks.constructed().get(0)).release();
        }
    }

    @Test
    public void invalidBufferSizesAndPlatformExceptionsFailClosed() {
        try (MockedStatic<AudioTrack> api = mockStatic(AudioTrack.class);
             MockedConstruction<AudioTrack> tracks = mockConstruction(AudioTrack.class)) {
            api.when(() -> AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_AC3)).thenReturn(-2, 0, 2 * 1024 * 1024)
                    .thenThrow(new IllegalArgumentException("unsupported"));
            for (int i = 0; i < 4; i++) assertFalse(PassthroughProbe.canOpenEncoding(AudioFormat.ENCODING_AC3));
            assertTrue(tracks.constructed().isEmpty());
        }
    }

    @Test
    public void probeEnablesMode2SurvivesSpeakerCallbackAndClearsOnHeadphones() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        CustomApplication app = spy(new CustomApplication());
        doReturn(context.getPackageManager()).when(app).getPackageManager();
        AudioManager manager = mock(AudioManager.class);
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        AudioDeviceInfo earpiece = device(1, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
        AudioDeviceInfo[] devices = {speaker, earpiece};
        when(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(devices);
        PassthroughProbe probe = new PassthroughProbe(encoding -> encoding == AudioFormat.ENCODING_AC3);
        probe.update(PassthroughProbe.routeKey(devices, null, false), Runnable::run, Runnable::run, () -> {});
        Field probeField = CustomApplication.class.getDeclaredField("passthroughProbe");
        probeField.setAccessible(true);
        probeField.set(app, probe);
        Map<Field, Object> previous = new HashMap<>();
        // Restore every static field touched by refresh so existing route tests stay isolated.
        for (Field field : CustomApplication.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && !java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                previous.put(field, field.get(null));
            }
        }
        Method refresh = CustomApplication.class.getDeclaredMethod("refreshAudioOutputCapabilities", String.class);
        refresh.setAccessible(true);
        try (MockedStatic<LibAvos> nativeApi = mockStatic(LibAvos.class);
             MockedStatic<ArchosFeatures> features = mockStatic(ArchosFeatures.class)) {
            setStatic("mAudioManager", manager);
            setStatic("mContext", context);
            setStatic("log", mock(Logger.class));
            refresh.invoke(app, "onCreate");
            assertTrue(CustomApplication.isPassthroughSupported());
            assertFalse(CustomApplication.isSpdifConnected());
            assertFalse(CustomApplication.isIecEncapsulationCapable());
            assertEquals(1L << AudioFormat.ENCODING_AC3, CustomApplication.getNativeAudioCodecsFlag());
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
            refresh.invoke(app, "trackRoute");
            assertTrue(CustomApplication.isPassthroughSupported());
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString("force_audio_passthrough_multiple", "2").putBoolean("force_passthrough", true).commit();
            nativeApi.when(LibAvos::isAvailable).thenReturn(true);
            CustomApplication.applyAudioOutputToNative(context);
            nativeApi.verify(() -> LibAvos.setPassthrough(2));
            // Even a saved force preference must not turn a successful AC3 probe into all codecs.
            nativeApi.verify(() -> LibAvos.setHdmiSupportedAudioCodecs(1L << AudioFormat.ENCODING_AC3));
            AudioDeviceInfo headphones = device(3, AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
            when(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                    .thenReturn(new AudioDeviceInfo[] {speaker, earpiece, headphones});
            refresh.invoke(app, "devicesAdded");
            assertFalse(CustomApplication.isPassthroughSupported());
            assertEquals(0, CustomApplication.getNativeAudioCodecsFlag());
        } finally {
            for (Map.Entry<Field, Object> entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        }
    }

    @Test
    @Config(sdk = {24, 30, 33})
    public void newerApisNeverInspectSpeakerForLegacyProbe() {
        AudioDeviceInfo speaker = mock(AudioDeviceInfo.class);
        assertNull(PassthroughProbe.routeKey(new AudioDeviceInfo[] {speaker}, null, false));
        verifyNoInteractions(speaker);
        assertFalse(CustomApplication.isPassthroughProbePending());
    }

    @Test
    public void nativeHandoffRejectsMode1AndRequiresAc3ForMode3() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Map<Field, Object> previous = snapshotStatics();
        try (MockedStatic<LibAvos> nativeApi = mockStatic(LibAvos.class)) {
            setStatic("log", mock(Logger.class));
            setStatic("hasHdmi", false);
            setStatic("hasSpdif", false);
            setStatic("isIecEncapsulationCapable", false);
            setStatic("mediaCodecAudioCapabilityFlagInitialized", true);
            setStatic("spatializerCapabilitiesInitialized", true);
            nativeApi.when(LibAvos::isAvailable).thenReturn(true);
            for (int encoding : new int[] {AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_DTS}) {
                long codecs = 1L << encoding;
                setStatic("probedAudioEncodingFlag", codecs);
                for (int mode = 0; mode <= 3; mode++) {
                    nativeApi.clearInvocations();
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                            .putString("force_audio_passthrough_multiple", Integer.toString(mode))
                            .putBoolean("force_passthrough", true).commit();
                    int expected = mode == 2 || (mode == 3 && encoding == AudioFormat.ENCODING_AC3) ? mode : 0;
                    CustomApplication.applyAudioOutputToNative(context);
                    nativeApi.verify(() -> LibAvos.setPassthrough(expected));
                    nativeApi.verify(() -> LibAvos.setHdmiSupportedAudioCodecs(codecs));
                    assertEquals(mode == 0 || expected != 0, CustomApplication.isPassthroughModeSupported(mode));
                }
            }
            // Probing does not alter the behavior of a normally detected HDMI route.
            setStatic("hasHdmi", true);
            setStatic("hdmiAudioEncodingFlag", 1L << AudioFormat.ENCODING_E_AC3);
            assertTrue(CustomApplication.isPassthroughModeSupported(1));
            assertTrue(CustomApplication.isPassthroughModeSupported(3));
            assertEquals(1L << AudioFormat.ENCODING_E_AC3, CustomApplication.getNativeAudioCodecsFlag());
        } finally {
            restoreStatics(previous);
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        }
    }

    @Test
    public void probeCompletionDoesNotChangePcmOrUnsupportedModePlaybackSignature() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        Map<Field, Object> previous = snapshotStatics();
        try (MockedStatic<ArchosFeatures> features = mockStatic(ArchosFeatures.class)) {
            setStatic("mContext", context);
            setStatic("log", mock(Logger.class));
            setStatic("hasHdmi", false);
            setStatic("hasSpdif", false);
            setStatic("audioRouteResolved", false);
            setStatic("spatializerCapabilitiesInitialized", true);
            // Mode 0 stays PCM; saved Mode 1 was never probed; DTS cannot enable Mode 3.
            for (int mode : new int[] {0, 1, 3}) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString("force_audio_passthrough_multiple", Integer.toString(mode)).commit();
                setStatic("probedAudioEncodingFlag", 0L);
                String before = CustomApplication.getAudioOutputSignature();
                setStatic("probedAudioEncodingFlag", 1L << AudioFormat.ENCODING_DTS);
                assertEquals(before, CustomApplication.getAudioOutputSignature());
            }
            for (int mode : new int[] {2, 3}) {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString("force_audio_passthrough_multiple", Integer.toString(mode)).commit();
                setStatic("probedAudioEncodingFlag", 0L);
                String before = CustomApplication.getAudioOutputSignature();
                setStatic("probedAudioEncodingFlag", 1L << AudioFormat.ENCODING_AC3);
                assertNotEquals(before, CustomApplication.getAudioOutputSignature());
            }
        } finally {
            restoreStatics(previous);
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
        }
    }

    @Test
    public void activeMusicDefersNewProbeAndCancelsQueuedProbe() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        CustomApplication app = spy(new CustomApplication());
        doReturn(context.getPackageManager()).when(app).getPackageManager();
        AudioManager manager = mock(AudioManager.class);
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        when(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(new AudioDeviceInfo[] {speaker});
        java.util.concurrent.ExecutorService worker = mock(java.util.concurrent.ExecutorService.class);
        Field executor = CustomApplication.class.getDeclaredField("passthroughProbeExecutor");
        executor.setAccessible(true);
        executor.set(app, worker);
        Map<Field, Object> previous = snapshotStatics();
        Method refresh = CustomApplication.class.getDeclaredMethod("refreshAudioOutputCapabilities", String.class);
        refresh.setAccessible(true);
        try (MockedStatic<LibAvos> nativeApi = mockStatic(LibAvos.class);
             MockedStatic<ArchosFeatures> features = mockStatic(ArchosFeatures.class)) {
            setStatic("mAudioManager", manager);
            setStatic("mContext", context);
            setStatic("sInstance", app);
            setStatic("log", mock(Logger.class));
            when(manager.isMusicActive()).thenReturn(true);
            refresh.invoke(app, "onCreate");
            verifyNoInteractions(worker);
            assertTrue(CustomApplication.isPassthroughProbePending());
            when(manager.isMusicActive()).thenReturn(false);
            refresh.invoke(app, "settings");
            org.mockito.ArgumentCaptor<Runnable> request = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(worker).execute(request.capture());
            when(manager.isMusicActive()).thenReturn(true);
            refresh.invoke(app, "trackRoute");
            try (MockedStatic<AudioTrack> tracks = mockStatic(AudioTrack.class)) {
                request.getValue().run();
                tracks.verifyNoInteractions();
            }
        } finally {
            restoreStatics(previous);
        }
    }

    private static Map<Field, Object> snapshotStatics() throws Exception {
        Map<Field, Object> result = new HashMap<>();
        for (Field field : CustomApplication.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && !java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                result.put(field, field.get(null));
            }
        }
        return result;
    }

    private static void restoreStatics(Map<Field, Object> previous) throws Exception {
        for (Map.Entry<Field, Object> entry : previous.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = CustomApplication.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
