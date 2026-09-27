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
import android.os.Looper;

import com.archos.mediacenter.video.player.Player;

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

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 30)
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioRouteNotificationTest {
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
