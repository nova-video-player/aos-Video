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

import android.app.Application;
import android.net.Uri;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import org.robolectric.RuntimeEnvironment;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.medialib.IMediaPlayer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 30)
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackRouteRecoveryTest {
    private Player player;
    private IMediaPlayer media;
    private Handler handler;
    private Runnable prepared;
    private Runnable refresh;
    private MockedStatic<CustomApplication> application;

    @Before
    public void setUp() throws Exception {
        // Run the real recovery methods without constructing the native renderer.
        player = mock(Player.class, CALLS_REAL_METHODS);
        media = mock(IMediaPlayer.class);
        handler = new Handler(Looper.getMainLooper());
        prepared = mock(Runnable.class);
        refresh = mock(Runnable.class);
        set("mHandler", handler);
        set("mPreparedAsync", prepared);
        set("mRefreshRateCheckerAsync", refresh);
        set("mMediaPlayer", media);
        set("mUri", Uri.parse("file:///video.mkv"));
        set("mSurfaceHolder", mock(SurfaceHolder.class));
        set("mCurrentState", 5); // PLAYING
        set("mTargetState", 5);
        set("mMetadataReady", true);
        set("mHasAudio", true);
        set("mSessionPrepared", true);
        set("mAudioOutputSignature", "HDMI-A");
        application = mockStatic(CustomApplication.class);
        application.when(CustomApplication::getAudioOutputSignature).thenReturn("HDMI-A");
    }

    @After
    public void tearDown() {
        application.close();
    }

    @Test
    public void exitCancelsPendingWorkAndRejectsRecoveryWithoutReleasingSurface() throws Exception {
        set("mOpenGeneration", 7);
        handler.postDelayed(prepared, 100);
        handler.postDelayed(refresh, 100);

        player.beginPlaybackExit();
        player.beginPlaybackExit();
        player.onAudioOutputChanged();
        player.onAudioBecomingNoisy();
        player.onPrepared(media);
        player.start(PlayerController.STATE_NORMAL);
        player.openVideo();
        player.setVideoURI(Uri.parse("file:///late-source.mkv"), null);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));

        assertEquals(8, get("mOpenGeneration"));
        assertEquals(5, get("mTargetState"));
        assertSame(media, get("mMediaPlayer"));
        verifyNoInteractions(media, prepared, refresh);
        application.verifyNoInteractions();
    }

    @Test
    public void unchangedCapabilitiesDoNotReopenActivePlayer() {
        player.onAudioOutputChanged();
        verifyNoInteractions(media);
        verify(player, never()).openVideo();
    }

    @Test
    public void noisyEventStillPausesAnActiveSession() throws Exception {
        player.onAudioBecomingNoisy();
        verify(media).pause();
        assertEquals(6, get("mTargetState")); // PAUSED
    }

    @Test
    public void changedCapabilitiesPreservePlayingIntentAndPosition() throws Exception {
        assertRouteRecoveryPreservesTransport(5);
    }

    @Test
    public void changedCapabilitiesPreserveUserPauseAndPosition() throws Exception {
        assertRouteRecoveryPreservesTransport(6); // PAUSED
    }

    @Test
    public void lateHdmiDisconnectAfterRefreshTimeoutPreservesPlayback() throws Exception {
        AudioManager audio = hdmiOutputs();
        player.beginHdmiSwitch();
        configureRefreshCheck(24, 0);
        // Reproduce #2035: startup wait expires, then HDMI switches after start().
        player.checkRefreshRate();
        set("mFocusGranted", true);
        player.start(PlayerController.STATE_NORMAL);
        when(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(new AudioDeviceInfo[0]);
        player.onAudioBecomingNoisy();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300));
        verify(media, never()).pause();
        doReturn(outputs(AudioDeviceInfo.TYPE_HDMI)).when(audio).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        verify(media, never()).pause();
        assertEquals(5, get("mTargetState"));
    }

    @Test
    public void actualHdmiUnplugDuringSwitchPausesAfterBoundedWait() throws Exception {
        AudioManager audio = hdmiOutputs();
        player.beginHdmiSwitch();
        when(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(new AudioDeviceInfo[0]);
        player.onAudioBecomingNoisy();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        verify(media).pause();
        assertEquals(6, get("mTargetState"));
    }

    @Test
    public void explicitPauseCancelsDeferredNoisyCheck() throws Exception {
        hdmiOutputs();
        player.beginHdmiSwitch();
        player.onAudioBecomingNoisy();
        player.pause(PlayerController.STATE_NORMAL);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        verify(media).pause();
        verify(media, never()).start();
        assertEquals(6, get("mTargetState"));
    }

    @Test
    public void exitingCancelsDeferredNoisyCheck() throws Exception {
        AudioManager audio = hdmiOutputs();
        player.beginHdmiSwitch();
        player.onAudioBecomingNoisy();
        player.beginPlaybackExit();
        when(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(new AudioDeviceInfo[0]);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        verifyNoInteractions(media);
    }

    @Test
    public void repeatedModeRequestsAndNoisyEventsCannotExtendWindow() throws Exception {
        AudioManager audio = hdmiOutputs();
        player.beginHdmiSwitch();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        player.beginHdmiSwitch();
        when(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(new AudioDeviceInfo[0]);
        player.onAudioBecomingNoisy();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        player.onAudioBecomingNoisy();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        verify(media).pause();
    }

    @Test
    public void noisyEventAfterSwitchWindowPausesImmediately() throws Exception {
        hdmiOutputs();
        player.beginHdmiSwitch();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        player.onAudioBecomingNoisy();
        verify(media).pause();
    }

    @Test
    public void headphonesKeepNormalUnplugHandlingEvenWithHdmiConnected() throws Exception {
        AudioManager audio = hdmiOutputs();
        doReturn(outputs(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
                .when(audio).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        player.beginHdmiSwitch();
        doReturn(outputs(AudioDeviceInfo.TYPE_HDMI)).when(audio).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        player.onAudioBecomingNoisy();
        verify(media).pause();
    }

    @Test
    public void finalRefreshCheckAcceptsCompletedModeSwitch() throws Exception {
        configureRefreshCheck(19, 0);
        try (MockedStatic<Toast> toasts = mockStatic(Toast.class)) {
            player.checkRefreshRate();
            assertEquals(3, get("mCurrentState")); // REFRESH_PREPARED
            toasts.verifyNoInteractions();
        }
    }

    private void configureRefreshCheck(int modeId, int retries) throws Exception {
        Window window = mock(Window.class);
        View decor = mock(View.class);
        Display display = mock(Display.class);
        Display.Mode mode = Display.Mode.class.getDeclaredConstructor(int.class, int.class, int.class, float.class)
                .newInstance(modeId, 1920, 1080, 24f);
        when(window.getDecorView()).thenReturn(decor);
        when(decor.getDisplay()).thenReturn(display);
        when(display.getMode()).thenReturn(mode);
        when(display.getRefreshRate()).thenReturn(24f);
        set("mContext", RuntimeEnvironment.getApplication());
        set("mWindow", window);
        set("mWaitForNewRate", true);
        set("wantedModeId", 19);
        set("numberRetries", retries);
        set("mCurrentState", 2); // PREPARED
        set("mVideoWidth", 1280);
        set("mVideoHeight", 544);
        set("mSurfaceController", mock(SurfaceController.class));
    }

    private AudioManager hdmiOutputs() throws Exception {
        AudioManager audio = mock(AudioManager.class);
        doReturn(outputs(AudioDeviceInfo.TYPE_HDMI)).when(audio).getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        set("mAudioManager", audio);
        return audio;
    }

    private AudioDeviceInfo[] outputs(int... types) {
        AudioDeviceInfo[] outputs = new AudioDeviceInfo[types.length];
        for (int i = 0; i < types.length; i++) {
            outputs[i] = mock(AudioDeviceInfo.class);
            when(outputs[i].getType()).thenReturn(types[i]);
        }
        return outputs;
    }

    private void assertRouteRecoveryPreservesTransport(int target) throws Exception {
        set("mTargetState", target);
        set("mCurrentState", target);
        when(media.getCurrentPosition()).thenReturn(12_345);
        doNothing().when(player).openVideo();
        application.when(CustomApplication::getAudioOutputSignature).thenReturn("HDMI-B");

        player.onAudioOutputChanged();

        verify(media).release();
        verify(player).openVideo();
        assertEquals(target, get("mTargetState"));
        assertEquals(12_345, get("mStopPosition"));
        assertEquals(true, get("mRestoringSession"));
        assertEquals(false, get("mMetadataReady"));

        // A second route notification during replacement preparation must wait for metadata.
        IMediaPlayer replacement = mock(IMediaPlayer.class);
        set("mMediaPlayer", replacement);
        set("mCurrentState", 1); // PREPARING
        application.when(CustomApplication::getAudioOutputSignature).thenReturn("HDMI-C");
        player.onAudioOutputChanged();
        verifyNoInteractions(replacement);
        verify(player, times(1)).openVideo();
    }

    private void set(String name, Object value) throws Exception {
        field(name).set(player, value);
    }

    private Object get(String name) throws Exception {
        return field(name).get(player);
    }

    private Field field(String name) throws Exception {
        Field field = Player.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
