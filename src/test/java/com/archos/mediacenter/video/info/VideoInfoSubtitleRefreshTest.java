// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.MediaMetadata;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class VideoInfoSubtitleRefreshTest {
    @Test public void metadataRefreshShowsTextBeforeAnalysisAndReplacesLabelsInPlace() throws Exception {
        FragmentActivity activity = Robolectric.buildActivity(FragmentActivity.class).get();
        VideoInfoActivityFragment fragment = mock(VideoInfoActivityFragment.class, CALLS_REAL_METHODS);
        doReturn(activity).when(fragment).getActivity();
        doReturn(activity).when(fragment).getContext();
        doReturn(activity.getResources()).when(fragment).getResources();
        Video video = mock(Video.class);
        when(video.getFilePath()).thenReturn("/video.mkv");
        field(fragment, "mCurrentVideo", video);
        // External discovery has not completed. Internal rows must still appear.
        field(fragment, "mSubtitleListCache", new HashMap<>());
        for (String name : new String[]{"mFileError", "mDuration", "mFileSize", "mDecoderTextView", "mAudioTrackTextView"})
            field(fragment, name, new TextView(activity));
        for (String name : new String[]{"mFileInfoAudioVideoContainer", "mFileInfoContainerLoading"})
            field(fragment, name, new View(activity));
        FrameLayout root = new FrameLayout(activity);
        View audioRow = new View(activity);
        audioRow.setId(R.id.audio_row);
        root.addView(audioRow);
        field(fragment, "mRoot", root);
        TextView subtitles = new TextView(activity);
        subtitles.setVisibility(View.GONE);
        field(fragment, "mSubtitleTrack", subtitles);
        Method refresh = VideoInfoActivityFragment.class.getDeclaredMethod("setFileInfo", VideoMetadata.class);
        refresh.setAccessible(true);

        refresh.invoke(fragment, metadata(false));
        assertEquals(View.VISIBLE, subtitles.getVisibility());
        String[] initial = subtitles.getText().toString().split("\n");
        assertEquals(2, initial.length);
        assertTrue(initial[0], initial[0].contains("1: TEXT"));
        assertTrue(initial[1], initial[1].contains("2: TEXT"));

        refresh.invoke(fragment, metadata(true));
        String[] detected = subtitles.getText().toString().split("\n");
        assertEquals("Analysis must not append a status row", 2, detected.length);
        assertTrue(detected[0], detected[0].contains("1: "));
        assertTrue(detected[0], detected[0].contains("(AI 94%)"));
        assertEquals("Unresolved row keeps its original layout", initial[1], detected[1]);
    }

    private VideoMetadata metadata(boolean detected) {
        MediaMetadata data = mock(MediaMetadata.class);
        when(data.has(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn(true);
        when(data.getInt(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn(2);
        for (int i = 0; i < 2; ++i) {
            int name = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + i * IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX
                    + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_NAME;
            when(data.has(name)).thenReturn(true);
            when(data.getString(name)).thenReturn("TEXT");
        }
        if (detected) {
            int language = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE;
            when(data.has(language)).thenReturn(true);
            when(data.getString(language)).thenReturn("fr");
            when(data.has(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE)).thenReturn(true);
            when(data.getInt(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE)).thenReturn(3);
            when(data.has(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE)).thenReturn(true);
            when(data.getInt(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE)).thenReturn(94);
        }
        VideoMetadata metadata = spy(new VideoMetadata("/video.mkv"));
        metadata.setData(data);
        doReturn(1L).when(metadata).getFileSize();
        return metadata;
    }

    private void field(Object target, String name, Object value) throws Exception {
        Field field = VideoInfoActivityFragment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
