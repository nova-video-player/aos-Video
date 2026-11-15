// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Looper;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.ArrayObjectAdapter;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.MediaFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class SubtitleDetailsPreparationTest {
    private VideoDetailsFragment fragment;
    private FragmentActivity activity;
    private Video video;
    private ArrayObjectAdapter adapter;
    private final AtomicReference<VideoMetadata> displayed = new AtomicReference<>();
    private final HashMap<String, VideoMetadata> metadataCache = new HashMap<>();
    private final HashMap<Uri, List<SubtitleManager.SubtitleFile>> externalCache = new HashMap<>();
    private final Uri uri = Uri.parse("file:///video.mkv");

    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(FragmentActivity.class).get();
        fragment = mock(VideoDetailsFragment.class, CALLS_REAL_METHODS);
        doReturn(activity).when(fragment).getActivity();
        doReturn(true).when(fragment).isAdded();
        video = mock(Video.class);
        when(video.getFilePath()).thenReturn(uri.toString());
        when(video.getFileUri()).thenReturn(uri);
        when(video.getMetadata()).thenAnswer(call -> displayed.get());
        doAnswer(call -> { displayed.set(call.getArgument(0)); return null; }).when(video).setMetadata(any());
        adapter = mock(ArrayObjectAdapter.class);
        set(fragment, "mVideo", video);
        set(fragment, "mAdapter", adapter);
        set(fragment, "mVideoMetadateCache", metadataCache);
        set(fragment, "mSubtitleListCache", externalCache);
        set(fragment, "mDescriptionPresenter", mock(VideoDetailsDescriptionPresenter.class));
        set(fragment, "INDEX_SUBTITLES", 1);
        set(fragment, "INDEX_FILEDETAILS", 2);
    }

    @Test public void internalTracksDoNotWaitForExternalDiscovery() throws Exception {
        displayed.set(new VideoMetadata());
        invoke(fragment, "updateSubtitleRowWhenReady", new Class<?>[0]);
        ArgumentCaptor<SubtitlesDetailsRow> row = ArgumentCaptor.forClass(SubtitlesDetailsRow.class);
        verify(adapter).replace(eq(1), row.capture());
        assertSame(video, row.getValue().getVideo());
        assertNull(row.getValue().getExternalSubs());

        List<SubtitleManager.SubtitleFile> external = Collections.singletonList(mock(SubtitleManager.SubtitleFile.class));
        externalCache.put(uri, external);
        clearInvocations(adapter);
        invoke(fragment, "updateSubtitleRowWhenReady", new Class<?>[0]);
        verify(adapter).replace(eq(1), row.capture());
        assertSame(external, row.getValue().getExternalSubs());
    }

    @Test public void ordinaryMetadataIsPublishedBeforeInferenceAndEnrichedAfterwards() throws Exception {
        VideoMetadata ordinary = mock(VideoMetadata.class);
        when(ordinary.hasUnknownSubtitleLanguages()).thenReturn(true);
        when(ordinary.getSubtitleTrackNb()).thenReturn(1);
        metadataCache.put(uri.toString(), ordinary);
        IMediaMetadataRetriever retriever = mock(IMediaMetadataRetriever.class);
        when(retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_NB_VIDEO_TRACK)).thenReturn("1");
        when(retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn("1");
        when(retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK
                + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE)).thenReturn("fr");
        when(retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE)).thenReturn("3");
        when(retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE)).thenReturn("94");
        when(retriever.guessSubtitleLanguages()).thenAnswer(call -> {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertSame("TEXT metadata must be visible before inference completes", ordinary, displayed.get());
            verify(adapter).replace(eq(1), any(SubtitlesDetailsRow.class));
            return true;
        });
        // The copy constructor reads the source fields, so provide an actual source path.
        Field source = VideoMetadata.class.getDeclaredField("mRemotePath");
        source.setAccessible(true);
        source.set(ordinary, uri.toString());
        Object task = task();
        // Execute deterministically on this test thread; production uses its own executor.
        ExecutorService direct = mock(ExecutorService.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(direct).execute(any());
        set(task, "executor", direct);
        try (MockedStatic<MediaFactory> factory = mockStatic(MediaFactory.class)) {
            factory.when(() -> MediaFactory.createMetadataRetriever(any(Context.class))).thenReturn(retriever);
            invoke(task, "execute", new Class<?>[]{Video.class}, video);
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("fr", displayed.get().getSubtitleTrack(0).language);
        assertEquals(94, displayed.get().getSubtitleTrack(0).languageConfidence);
        assertSame(displayed.get(), metadataCache.get(uri.toString()));
        verify(retriever).release();
        assertFalse((Boolean) invoke(task, "isLoading", new Class<?>[]{Video.class}, video));
    }

    @Test public void cancellationStopsNativeWorkAndRejectsQueuedUpdates() throws Exception {
        Object task = task();
        set(task, "startingPath", uri.toString());
        invoke(task, "publish", new Class<?>[]{VideoMetadata.class, boolean.class}, new VideoMetadata(), false);
        invoke(task, "cancel", new Class<?>[0]);
        CancellationSignal cancellation = (CancellationSignal) get(task, "cancellation");
        assertTrue(cancellation.isCanceled());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        verifyNoInteractions(adapter);
        assertNull(displayed.get());
    }

    @Test public void navigationRejectsResultForPreviousVideo() throws Exception {
        Object task = task();
        set(task, "startingPath", "file:///previous.mkv");
        invoke(task, "publish", new Class<?>[]{VideoMetadata.class, boolean.class}, new VideoMetadata(), true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        verifyNoInteractions(adapter);
        assertTrue(metadataCache.isEmpty());
        invoke(task, "cancel", new Class<?>[0]);
    }

    private Object task() throws Exception {
        Class<?> type = Class.forName(VideoDetailsFragment.class.getName() + "$VideoInfoTask");
        Constructor<?> constructor = type.getDeclaredConstructor(VideoDetailsFragment.class);
        constructor.setAccessible(true);
        Object task = constructor.newInstance(fragment);
        set(fragment, "mVideoInfoTask", task);
        return task;
    }

    private static Field field(Object target, String name) throws Exception {
        Class<?> type = target instanceof VideoDetailsFragment ? VideoDetailsFragment.class : target.getClass();
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = target instanceof VideoDetailsFragment ? VideoDetailsFragment.class : target.getClass();
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
