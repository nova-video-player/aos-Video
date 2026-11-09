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
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.LibAvos;
import com.archos.medialib.MediaMetadata;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = 30)
public class PlaybackDecoderFallbackTest {
    private Player player;
    private IMediaPlayer media;
    private MediaMetadata metadata;
    private Toast toast;
    private MockedStatic<Toast> toasts;
    private static final int DECODER_KEY = IMediaPlayer.METADATA_KEY_VIDEO_TRACK
            + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_DECODER;

    @Before
    public void setUp() throws Exception {
        player = mock(Player.class, CALLS_REAL_METHODS);
        media = mock(IMediaPlayer.class);
        metadata = mock(MediaMetadata.class);
        Context context = mock(Context.class);
        set("mContext", context);
        set("mMediaPlayer", media);
        set("mVideoMetadata", mock(VideoMetadata.class));
        set("mHandler", new Handler(Looper.getMainLooper()));
        set("mPreparedAsync", mock(Runnable.class));
        set("mRefreshRateCheckerAsync", mock(Runnable.class));
        when(media.getMediaMetadata(IMediaPlayer.METADATA_ALL,
                IMediaPlayer.BYPASS_METADATA_FILTER)).thenReturn(metadata);
        toast = mock(Toast.class);
        toasts = mockStatic(Toast.class);
        toasts.when(() -> Toast.makeText(context, R.string.video_software_fallback,
                Toast.LENGTH_LONG)).thenReturn(toast);
    }

    @After
    public void tearDown() {
        toasts.close();
    }

    @Test
    public void hardwareFallbackWarnsOnlyOnceDespiteMetadataUpdates() {
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        verifyNoInteractions(toast);
        update(LibAvos.MP_DECODER_SW);
        update(LibAvos.MP_DECODER_SW);
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        update(LibAvos.MP_DECODER_SW);
        verify(toast).show();
    }

    @Test
    public void softwareOnlyAndSuccessfulHardwareRetryDoNotWarn() {
        update(LibAvos.MP_DECODER_SW);
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        update(LibAvos.MP_DECODER_HW_OMX);
        update(LibAvos.MP_DECODER_ANY);
        verifyNoInteractions(toast);
    }

    @Test
    public void explicitSoftwareAndStalePlayerEventsDoNotWarn() throws Exception {
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        when(metadata.getInt(DECODER_KEY)).thenReturn(LibAvos.MP_DECODER_SW);
        player.onInfo(mock(IMediaPlayer.class), IMediaPlayer.MEDIA_INFO_METADATA_UPDATE, 0);
        set("mForceSoftwareDecoding", true);
        update(LibAvos.MP_DECODER_SW);
        verifyNoInteractions(toast);
    }

    @Test
    public void reopeningDoesNotCarryHardwareEvidenceOrRepeatWarning() throws Exception {
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        closeCurrentPlayer();
        set("mMediaPlayer", media);
        update(LibAvos.MP_DECODER_SW);
        verifyNoInteractions(toast);
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        update(LibAvos.MP_DECODER_SW);
        closeCurrentPlayer();
        set("mMediaPlayer", media);
        update(LibAvos.MP_DECODER_HW_MEDIACODEC);
        update(LibAvos.MP_DECODER_SW);
        verify(toast).show();
    }

    private void closeCurrentPlayer() throws Exception {
        Method method = Player.class.getDeclaredMethod("closeCurrentPlayer");
        method.setAccessible(true);
        method.invoke(player);
    }

    private void update(int decoder) {
        when(metadata.has(DECODER_KEY)).thenReturn(true);
        when(metadata.getInt(DECODER_KEY)).thenReturn(decoder);
        player.onInfo(media, IMediaPlayer.MEDIA_INFO_METADATA_UPDATE, 0);
    }

    private void set(String name, Object value) throws Exception {
        Field field = Player.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(player, value);
    }
}
