package com.archos.mediacenter.video.player;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 30)
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackNetworkRetryTest {
    @Test
    public void failedSeekRetriesLatestTargetAfterPlayerTeardown() throws Exception {
        PlayerService service = new PlayerService();
        Player player = mock(Player.class);
        PlayerService.PlayerFrontend frontend = mock(PlayerService.PlayerFrontend.class);
        set(service, "mPlayer", player);
        set(service, "mPlayerFrontend", frontend);
        Object session = get(service, "mPlaybackSession");
        set(session, "lastKnownPositionMs", 726_809);

        service.onSeekStart(731_892);
        service.onSeekStart(806_892);
        verify(frontend).onSeekStart(806_892);
        // onError has already released the native player and cleared its URI.
        when(player.isInPlaybackState()).thenReturn(false);
        when(player.hasSuspendedPosition()).thenReturn(false);

        assertEquals(806_892, service.prepareRetryFromCurrentPosition());
        assertEquals(806_892, get(session, "selectedStartPositionMs"));
        assertEquals(PlayerService.ResumeSource.LIVE, get(session, "selectedSource"));
        verify(player, never()).getCurrentPosition();
    }

    @Test
    public void retryReopensSourceAndRetainsLivePositionAfterFrontendPause() throws Exception {
        PlayerService service = new PlayerService();
        Player player = mock(Player.class);
        Handler handler = spy(new Handler(Looper.getMainLooper()));
        Uri uri = Uri.parse("sftp://server/video.mp4");
        CountDownLatch subtitles = new CountDownLatch(1);
        set(service, "mPlayer", player);
        set(service, "mHandler", handler);
        set(service, "mStreamingUri", uri);
        set(service, "mIsPreparingSubs", true);
        set(service, "mSubtitlesReadyLatch", subtitles);
        when(player.hasSuspendedPosition()).thenReturn(true);
        when(player.getCurrentPosition()).thenReturn(428_941);
        Object session = get(service, "mPlaybackSession");
        set(session, "startPositionApplied", true);

        assertEquals(428_941, service.prepareRetryFromCurrentPosition());
        // PlayerActivity.stop() pauses the frontend before requesting the reopen.
        service.setPlayOnResume(false);
        service.mPlayerState = PlayerService.PlayerState.PAUSED;
        service.retryPlayback();

        assertTrue(service.isPlayOnResume());
        assertEquals(PlayerService.PlayerState.PREPARING, service.mPlayerState);
        assertEquals(PlayerService.ResumeSource.LIVE, get(session, "selectedSource"));
        assertEquals(428_941, get(session, "selectedStartPositionMs"));
        assertEquals(false, get(session, "startPositionApplied"));
        verify(player, never()).setVideoURI(any(), any());

        subtitles.countDown();
        verify(handler, timeout(2000)).post(any(Runnable.class));
        shadowOf(Looper.getMainLooper()).idle();
        verify(player).setVideoURI(uri, null);
    }

    private static Object get(Object object, String name) throws Exception {
        return field(object, name).get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        field(object, name).set(object, value);
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
