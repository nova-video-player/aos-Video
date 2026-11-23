// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.mediacenter.video;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Looper;

import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosFeatures;
import com.archos.mediacenter.video.utils.CodecDiscovery;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.medialib.LibAvos;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises discovery through native configuration for the speaker-only TV in #1981. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 31)
@LooperMode(LooperMode.Mode.PAUSED)
public class TvSpeakerPassthroughTest {
    private CustomApplication app;
    private Context context;
    private SharedPreferences preferences;
    private AudioManager audioManager;
    private ExecutorService probeWorker;
    private MockedStatic<LibAvos> nativeApi;
    private MockedStatic<ArchosFeatures> features;
    private MockedStatic<CodecDiscovery> codecs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        app = new CustomApplication();
        ReflectionHelpers.callInstanceMethod(app, "attach",
                ReflectionHelpers.ClassParameter.from(Context.class, context));
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().clear().putString("force_audio_passthrough_multiple", "2").commit();
        audioManager = mock(AudioManager.class);
        probeWorker = mock(ExecutorService.class);
        ReflectionHelpers.setField(app, "passthroughProbeExecutor", probeWorker);
        ReflectionHelpers.setStaticField(CustomApplication.class, "sInstance", app);
        ReflectionHelpers.setStaticField(CustomApplication.class, "mContext", context);
        ReflectionHelpers.setStaticField(CustomApplication.class, "mAudioManager", audioManager);
        ReflectionHelpers.setStaticField(CustomApplication.class, "log", mock(Logger.class));
        ReflectionHelpers.setStaticField(CustomApplication.class, "mediaCodecAudioCapabilityFlagInitialized", true);
        ReflectionHelpers.setStaticField(CustomApplication.class, "spatializerCapabilitiesInitialized", true);
        nativeApi = mockStatic(LibAvos.class);
        nativeApi.when(LibAvos::isAvailable).thenReturn(true);
        features = mockStatic(ArchosFeatures.class);
        features.when(() -> ArchosFeatures.isAndroidTV(app)).thenReturn(true);
        features.when(() -> ArchosFeatures.isAndroidTV(context)).thenReturn(true);
        codecs = mockStatic(CodecDiscovery.class);
    }

    @After
    public void tearDown() {
        codecs.close();
        nativeApi.close();
        features.close();
    }

    private static AudioDeviceInfo device(int id, int type, int... encodings) {
        AudioDeviceInfo device = mock(AudioDeviceInfo.class);
        when(device.getId()).thenReturn(id);
        when(device.getType()).thenReturn(type);
        when(device.getEncodings()).thenReturn(encodings);
        // Compressed profile channel counts must not become PCM/HDMI capabilities.
        when(device.getChannelCounts()).thenReturn(new int[] {2, 6, 8});
        return device;
    }

    private void refresh(String reason, AudioDeviceInfo... devices) {
        when(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(devices);
        ReflectionHelpers.callInstanceMethod(app, "refreshAudioOutputCapabilities",
                ReflectionHelpers.ClassParameter.from(String.class, reason));
    }

    private static long flags(int... encodings) {
        long flags = 0;
        for (int encoding : encodings) flags |= 1L << encoding;
        return flags;
    }

    @Test
    public void atmosSpeakerRouteKeepsCapabilitiesAndModeAcrossPlaybackStart() {
        long advertised = flags(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AC3,
                AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_E_AC3_JOC);
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AC3,
                AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_E_AC3_JOC);
        refresh("startup", speaker);
        assertTrue(CustomApplication.isPassthroughSupported());
        assertEquals(advertised, CustomApplication.getNativeAudioCodecsFlag());
        String beforePlayback = CustomApplication.getAudioOutputSignature();
        nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
        refresh("trackRoute", speaker);
        assertTrue(CustomApplication.isPassthroughSupported());
        assertEquals(beforePlayback, CustomApplication.getAudioOutputSignature());
        assertEquals(advertised, CustomApplication.getHdmiAudioCodecsFlag());
        assertFalse(CustomApplication.isHdmiConnected());
        assertFalse(CustomApplication.isSpdifConnected());
        assertFalse(CustomApplication.isIecEncapsulationCapable());
        assertFalse(CustomApplication.isDirectPcmMultichannelCapable());
        assertEquals(0, CustomApplication.getEffectiveMaxPcmChannels());
        assertNull(CustomApplication.getHdmiChannelMasks());
        for (boolean force : new boolean[] {false, true}) {
            preferences.edit().putBoolean(VideoPreferencesCommon.KEY_FORCE_AUDIO_PASSTHROUGH, force).commit();
            nativeApi.clearInvocations();
            CustomApplication.applyAudioOutputToNative(context);
            nativeApi.verify(() -> LibAvos.setPassthrough(2));
            nativeApi.verify(() -> LibAvos.setHdmiSupportedAudioCodecs(advertised));
        }
    }

    @Test
    @Config(sdk = {23, 28, 31, 33})
    public void advertisedAc3SpeakerWorksAcrossApisAndDoesNotEnablePassthroughWhenOff() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioFormat.ENCODING_AC3);
        refresh("startup", speaker);
        String signature = CustomApplication.getAudioOutputSignature();
        nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
        refresh("trackRoute", speaker);
        assertEquals(signature, CustomApplication.getAudioOutputSignature());
        assertTrue(CustomApplication.isPassthroughSupported());
        assertEquals(flags(AudioFormat.ENCODING_AC3), CustomApplication.getNativeAudioCodecsFlag());
        assertFalse(CustomApplication.isUsingProbedPassthrough());
        assertFalse(CustomApplication.isPassthroughProbePending());
        verifyNoInteractions(probeWorker);
        preferences.edit().putString("force_audio_passthrough_multiple", "0").commit();
        CustomApplication.applyAudioOutputToNative(context);
        nativeApi.verify(() -> LibAvos.setPassthrough(0));
    }

    @Test
    @Config(sdk = 23)
    public void advertisedSpeakerReplacesCachedLegacyProbeWithoutKeepingItsCodecsOrModeLimits() {
        ReflectionHelpers.setField(app, "passthroughProbe",
                new PassthroughProbe(encoding -> encoding == AudioFormat.ENCODING_AC3));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(probeWorker).execute(any(Runnable.class));
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        when(speaker.getChannelCounts()).thenReturn(new int[0]);
        nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
        refresh("startup", speaker);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(CustomApplication.isUsingProbedPassthrough());
        assertEquals(flags(AudioFormat.ENCODING_AC3), CustomApplication.getNativeAudioCodecsFlag());
        assertFalse(CustomApplication.isPassthroughModeSupported(1));
        assertTrue(CustomApplication.isPassthroughModeSupported(3));

        when(speaker.getEncodings()).thenReturn(new int[] {AudioFormat.ENCODING_DTS});
        refresh("devicesChanged", speaker);
        assertFalse(CustomApplication.isUsingProbedPassthrough());
        assertFalse(CustomApplication.isPassthroughProbePending());
        assertEquals(flags(AudioFormat.ENCODING_DTS), CustomApplication.getNativeAudioCodecsFlag());
        assertEquals(0L, (long) ReflectionHelpers.getStaticField(CustomApplication.class, "probedAudioEncodingFlag"));
        verify(probeWorker, times(1)).execute(any(Runnable.class));
        preferences.edit().putBoolean(VideoPreferencesCommon.KEY_FORCE_AUDIO_PASSTHROUGH, true).commit();
        CustomApplication.applyAudioOutputToNative(context);
        nativeApi.verify(() -> LibAvos.setPassthrough(2));
        nativeApi.verify(() -> LibAvos.setHdmiSupportedAudioCodecs(flags(AudioFormat.ENCODING_DTS)));
    }

    @Test
    public void jocOnlySpeakerQualifiesWithoutAssumingOtherDolbyFormats() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioFormat.ENCODING_E_AC3_JOC);
        nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
        refresh("trackRoute", speaker);
        assertTrue(CustomApplication.isPassthroughSupported());
        assertEquals(flags(AudioFormat.ENCODING_E_AC3_JOC), CustomApplication.getNativeAudioCodecsFlag());
        assertFalse(CustomApplication.isIecEncapsulationCapable());
    }

    @Test
    public void iecRequiresExplicitAdvertisementAndIsClearedWhenCapabilitiesDisappear() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_IEC61937);
        nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
        refresh("trackRoute", speaker);
        assertTrue(CustomApplication.isIecEncapsulationCapable());
        when(speaker.getEncodings()).thenReturn(new int[] {AudioFormat.ENCODING_PCM_16BIT});
        refresh("devicesChanged", speaker);
        assertFalse(CustomApplication.isPassthroughSupported());
        assertFalse(CustomApplication.isIecEncapsulationCapable());
        assertEquals(0, CustomApplication.getNativeAudioCodecsFlag());
        CustomApplication.applyAudioOutputToNative(context);
        nativeApi.verify(() -> LibAvos.setPassthrough(0));
    }

    @Test
    public void phonesAndPcmOrAacOnlyTvSpeakersDoNotGainPassthrough() {
        for (int[] encodings : new int[][] {{}, {AudioFormat.ENCODING_PCM_16BIT},
                {AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AAC_LC, AudioFormat.ENCODING_MP3}}) {
            AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, encodings);
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
            refresh("trackRoute", speaker);
            assertFalse(CustomApplication.isPassthroughSupported());
            assertEquals(0, CustomApplication.getNativeAudioCodecsFlag());
        }
        features.when(() -> ArchosFeatures.isAndroidTV(app)).thenReturn(false);
        features.when(() -> ArchosFeatures.isAndroidTV(context)).thenReturn(false);
        AudioDeviceInfo phone = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioFormat.ENCODING_AC3);
        nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(phone);
        refresh("trackRoute", phone);
        assertFalse(CustomApplication.isPassthroughSupported());
        assertEquals(0, CustomApplication.getNativeAudioCodecsFlag());
    }

    @Test
    public void privateRoutesClearSpeakerCapabilitiesAndDisconnectRestoresThem() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioFormat.ENCODING_AC3);
        for (int type : new int[] {AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER}) {
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
            refresh("trackRoute", speaker);
            assertTrue(CustomApplication.isPassthroughSupported());
            AudioDeviceInfo headphones = device(3, type, AudioFormat.ENCODING_AC3);
            for (AudioDeviceInfo routed : new AudioDeviceInfo[] {null, headphones}) {
                nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(routed);
                refresh("devicesAdded", speaker, headphones);
                assertFalse(CustomApplication.isPassthroughSupported());
                assertEquals(0, CustomApplication.getNativeAudioCodecsFlag());
                nativeApi.clearInvocations();
                CustomApplication.applyAudioOutputToNative(context);
                nativeApi.verify(() -> LibAvos.setPassthrough(0));
            }
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(null);
            refresh("devicesRemoved", speaker);
            assertTrue(CustomApplication.isPassthroughSupported());
            assertEquals(flags(AudioFormat.ENCODING_AC3), CustomApplication.getNativeAudioCodecsFlag());
        }
    }

    @Test
    public void digitalEndpointsKeepTheirOwnCodecLimits() {
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DTS_HD);
        for (int type : new int[] {AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_HDMI_EARC, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE}) {
            AudioDeviceInfo digital = device(3, type, AudioFormat.ENCODING_AC3);
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(null);
            refresh("startup", speaker, digital);
            assertEquals(flags(AudioFormat.ENCODING_AC3), CustomApplication.getNativeAudioCodecsFlag());
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(
                    type == AudioDeviceInfo.TYPE_HDMI ? digital : speaker);
            refresh("trackRoute", speaker, digital);
            assertTrue(CustomApplication.isPassthroughSupported());
            assertEquals(flags(AudioFormat.ENCODING_AC3), CustomApplication.getNativeAudioCodecsFlag());
        }
    }
}
