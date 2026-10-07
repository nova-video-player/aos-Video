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

package com.archos.mediacenter.video;

import android.app.Application;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.os.Looper;

import com.archos.environment.ArchosFeatures;
import com.archos.mediacenter.video.player.Player;
import com.archos.medialib.LibAvos;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

/**
 * Covers shared TV speaker/ARC capability selection, exclusive private routes,
 * and debounced playback notifications when output capabilities change.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 30)
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioRouteNotificationTest {
    private static AudioDeviceInfo device(int id, int type, int... encodings) {
        AudioDeviceInfo device = mock(AudioDeviceInfo.class);
        when(device.getId()).thenReturn(id);
        when(device.getType()).thenReturn(type);
        when(device.getEncodings()).thenReturn(encodings);
        return device;
    }

    @Test
    @Config(sdk = 28)
    public void tvSpeakerCallbackRetainsArcInventoryButPrivateRoutesStayExclusive() throws Exception {
        CustomApplication app = new CustomApplication();
        Method select = CustomApplication.class.getDeclaredMethod("selectedMediaDevice",
                AudioDeviceInfo[].class, String.class);
        select.setAccessible(true);
        AudioDeviceInfo speaker = device(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        AudioDeviceInfo arc = device(257, AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3);
        try (MockedStatic<LibAvos> nativeApi = mockStatic(LibAvos.class);
             MockedStatic<ArchosFeatures> features = mockStatic(ArchosFeatures.class)) {
            features.when(() -> ArchosFeatures.isAndroidTV(app)).thenReturn(true);
            nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
            // null keeps the existing connected-output capability scan. This
            // must match pre-play selection to avoid reopening Mode 2 as PCM.
            AudioDeviceInfo[] connected = {speaker, arc};
            assertNull(select.invoke(app, connected, "trackRoute"));
            assertNull(select.invoke(app, connected, "devicesAdded"));
            for (int type : new int[] {AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET}) {
                AudioDeviceInfo headphones = device(3, type);
                connected = new AudioDeviceInfo[] {speaker, arc, headphones};
                // Also prefer private listening while the speaker report is ambiguous.
                assertSame(headphones, select.invoke(app, connected, "trackRoute"));
                nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(headphones);
                assertSame(headphones, select.invoke(app, connected, "trackRoute"));
                nativeApi.when(LibAvos::getRoutedAudioDevice).thenReturn(speaker);
            }
            // ARC unplug/restricted capabilities and phone speakers remain definitive.
            assertSame(speaker, select.invoke(app, new AudioDeviceInfo[] {speaker}, "trackRoute"));
            AudioDeviceInfo pcmArc = device(257, AudioDeviceInfo.TYPE_HDMI_ARC, AudioFormat.ENCODING_PCM_16BIT);
            assertSame(speaker, select.invoke(app, new AudioDeviceInfo[] {speaker, pcmArc}, "trackRoute"));
            AudioDeviceInfo hdmi = device(257, AudioDeviceInfo.TYPE_HDMI, AudioFormat.ENCODING_E_AC3);
            assertSame(speaker, select.invoke(app, new AudioDeviceInfo[] {speaker, hdmi}, "trackRoute"));
            features.when(() -> ArchosFeatures.isAndroidTV(app)).thenReturn(false);
            assertSame(speaker, select.invoke(app, new AudioDeviceInfo[] {speaker, arc}, "trackRoute"));
        }
    }

    @Test
    public void reconnectDeviceIdsDoNotChangeOutputConfiguration() throws Exception {
        Field deviceId = CustomApplication.class.getDeclaredField("selectedAudioDeviceId");
        deviceId.setAccessible(true);
        int previousId = deviceId.getInt(null);
        try (MockedStatic<CustomApplication> application = mockStatic(CustomApplication.class)) {
            application.when(CustomApplication::getAudioOutputSignature).thenCallRealMethod();
            application.when(CustomApplication::isPassthroughSupported).thenReturn(true);
            String signature = CustomApplication.getAudioOutputSignature();
            // The first playback in nova-43.log traversed these IDs with identical capabilities.
            for (int id : new int[] {0, 69, 0, 82}) {
                deviceId.setInt(null, id);
                assertEquals(signature, CustomApplication.getAudioOutputSignature());
            }
            application.when(CustomApplication::getNativeAudioCodecsFlag).thenReturn(64L);
            assertNotEquals(signature, CustomApplication.getAudioOutputSignature());
            application.when(CustomApplication::getNativeAudioCodecsFlag).thenReturn(0L);
            application.when(CustomApplication::isPassthroughSupported).thenReturn(false);
            assertNotEquals(signature, CustomApplication.getAudioOutputSignature());
        } finally {
            deviceId.setInt(null, previousId);
        }
    }

    @Test
    public void routeFlappingSettlesAndDuplicateObservationsDoNotPostponeNotification() throws Exception {
        CustomApplication app = new CustomApplication(); // no onCreate/native initialization
        Method publish = CustomApplication.class.getDeclaredMethod("publishAudioRouteChange");
        publish.setAccessible(true);
        Player previous = Player.sPlayer;
        Player player = mock(Player.class);
        Player.sPlayer = player;
        try (MockedStatic<CustomApplication> application = mockStatic(CustomApplication.class)) {
            application.when(CustomApplication::getAudioOutputSignature).thenReturn("HDMI");
            publish.invoke(app);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            application.when(CustomApplication::getAudioOutputSignature).thenReturn("disconnected");
            publish.invoke(app);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            application.when(CustomApplication::getAudioOutputSignature).thenReturn("HDMI");
            publish.invoke(app);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            verifyNoInteractions(player);
            publish.invoke(app); // same capabilities: retain the existing deadline
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            verify(player, times(1)).onAudioOutputChanged();
            publish.invoke(app);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
            verifyNoMoreInteractions(player);
        } finally {
            Player.sPlayer = previous;
        }
    }
}
