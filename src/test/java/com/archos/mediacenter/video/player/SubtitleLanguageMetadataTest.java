// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.player;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.MediaMetadata;
import com.archos.medialib.SubtitleLanguage;
import com.archos.mediacenter.video.utils.VideoMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class SubtitleLanguageMetadataTest {
    @Test public void compactResultsApplyByInternalTrackOrderWithoutOverridingMetadata() {
        MediaMetadata data = mock(MediaMetadata.class);
        when(data.has(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn(true);
        when(data.getInt(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn(3);
        int language = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE;
        when(data.has(language)).thenReturn(true);
        when(data.getString(language)).thenReturn("en");
        when(data.has(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE)).thenReturn(true);
        when(data.getInt(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE)).thenReturn(SubtitleLanguage.METADATA);
        int external = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX
                + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_PATH;
        when(data.has(external)).thenReturn(true);
        when(data.getString(external)).thenReturn("/external.srt");
        VideoMetadata video = new VideoMetadata("/dummy.mkv");
        video.setData(data);
        android.content.Context context = org.robolectric.RuntimeEnvironment.getApplication();
        try (org.mockito.MockedStatic<com.archos.medialib.SubtitleLanguageStore> store = mockStatic(com.archos.medialib.SubtitleLanguageStore.class)) {
            store.when(() -> com.archos.medialib.SubtitleLanguageStore.load(context, "file:///dummy.mkv"))
                    .thenReturn(com.archos.medialib.SubtitleLanguageResult.fromCsv("de,fra", "98,99"));
            video.applyStoredSubtitleLanguages(context, "/dummy.mkv");
            assertEquals("en", video.getSubtitleTrack(0).language);
            assertEquals(SubtitleLanguage.METADATA, video.getSubtitleTrack(0).languageSource);
            assertTrue(video.getSubtitleTrack(1).isExternal);
            assertEquals(SubtitleLanguage.UNKNOWN, video.getSubtitleTrack(1).languageSource);
            assertEquals("fra", video.getSubtitleTrack(2).language);
            assertEquals(99, video.getSubtitleTrack(2).languageConfidence);
            assertEquals(SubtitleLanguage.FASTTEXT, video.getSubtitleTrack(2).languageSource);
            video.setData(data);
            store.when(() -> com.archos.medialib.SubtitleLanguageStore.load(context, "file:///dummy.mkv"))
                    .thenReturn(com.archos.medialib.SubtitleLanguageResult.fromCsv("fr", "99"));
            video.applyStoredSubtitleLanguages(context, "/dummy.mkv");
            assertEquals(SubtitleLanguage.UNKNOWN, video.getSubtitleTrack(2).languageSource);
        }
    }

    private MediaMetadata metadata() {
        MediaMetadata data = mock(MediaMetadata.class);
        when(data.has(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn(true);
        when(data.getInt(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)).thenReturn(2);
        int language = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE;
        when(data.has(language)).thenReturn(true);
        when(data.getString(language)).thenReturn("en");
        when(data.has(language + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX)).thenReturn(true);
        when(data.getString(language + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX)).thenReturn("fr");
        when(data.has(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE + 1)).thenReturn(true);
        when(data.getInt(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE + 1)).thenReturn(SubtitleLanguage.FASTTEXT);
        when(data.has(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE + 1)).thenReturn(true);
        when(data.getInt(IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE + 1)).thenReturn(94);
        return data;
    }

    @Test public void independentKeysPreserveTrackStrideAndOldMetadata() {
        assertEquals(6, IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX);
        VideoMetadata video = new VideoMetadata("/dummy.mkv");
        video.setData(metadata());
        assertEquals("en", video.getSubtitleTrack(0).language);
        assertNotEquals(SubtitleLanguage.FASTTEXT, video.getSubtitleTrack(0).languageSource);
        assertEquals("fr", video.getSubtitleTrack(1).language);
        assertEquals(SubtitleLanguage.FASTTEXT, video.getSubtitleTrack(1).languageSource);
        assertEquals(94, video.getSubtitleTrack(1).languageConfidence);
        String label = com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.generateSubtitleTrackName(
                org.robolectric.RuntimeEnvironment.getApplication(), video.getSubtitleTrack(1), "TEXT");
        assertTrue(label, label.endsWith(" (AI 94%)"));
        assertEquals(com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.generateTrackName(
                org.robolectric.RuntimeEnvironment.getApplication(), video.getSubtitleTrack(1).name,
                "fr", "TEXT", video.getSubtitleTrack(1).disposition, false) + " (AI 94%)", label);
    }

    @Test public void retrieverPreservesInferenceProvenanceBeforePlayback() throws Exception {
        com.archos.medialib.IMediaMetadataRetriever retriever = mock(com.archos.medialib.IMediaMetadataRetriever.class);
        int index = 12;
        when(retriever.extractMetadata(com.archos.medialib.IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE + index))
                .thenReturn("3");
        when(retriever.extractMetadata(com.archos.medialib.IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE + index))
                .thenReturn("94");
        int languageKey = com.archos.medialib.IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK
                + index * com.archos.medialib.IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_MAX
                + com.archos.medialib.IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE;
        when(retriever.extractMetadata(languageKey)).thenReturn("fr");
        java.lang.reflect.Constructor<VideoMetadata.SubtitleTrack> constructor = VideoMetadata.SubtitleTrack.class
                .getDeclaredConstructor(com.archos.medialib.IMediaMetadataRetriever.class, int.class);
        constructor.setAccessible(true);
        VideoMetadata.SubtitleTrack track = constructor.newInstance(retriever, index);
        assertEquals("fr", track.language);
        assertEquals(SubtitleLanguage.FASTTEXT, track.languageSource);
        assertEquals(94, track.languageConfidence);
        assertEquals(12, track.index);
    }

    @Test public void languageRefreshDuringSeekUsesLabelOnlyCallback() throws Exception {
        Player player = mock(Player.class, CALLS_REAL_METHODS);
        IMediaPlayer nativePlayer = mock(IMediaPlayer.class);
        Player.Listener listener = mock(Player.Listener.class);
        MediaMetadata data = metadata();
        when(nativePlayer.getMediaMetadata(anyBoolean(), anyBoolean())).thenReturn(data);
        field(player, "mMediaPlayer", nativePlayer);
        field(player, "mPlayerListener", listener);
        field(player, "mVideoMetadata", new VideoMetadata("/dummy.mkv"));
        field(player, "mIsBusy", true);
        player.onInfo(nativePlayer, IMediaPlayer.MEDIA_INFO_METADATA_UPDATE,
                IMediaPlayer.METADATA_UPDATE_SUBTITLE_LANGUAGES);
        verify(listener, never()).onSubtitleLanguagesUpdated(any(), anyInt());
        player.onAllSeekComplete(nativePlayer);
        verify(listener).onSubtitleLanguagesUpdated(any(), eq(-1));
        verify(listener, never()).onSubtitleMetadataUpdated(any(), anyInt());
        verify(listener, never()).onAudioMetadataUpdated(any(), anyInt());

        // A generic update coalesced during a later seek must retain the full callback.
        clearInvocations(listener);
        field(player, "mIsBusy", true);
        player.onInfo(nativePlayer, IMediaPlayer.MEDIA_INFO_METADATA_UPDATE,
                IMediaPlayer.METADATA_UPDATE_SUBTITLE_LANGUAGES);
        player.onInfo(nativePlayer, IMediaPlayer.MEDIA_INFO_METADATA_UPDATE, 0);
        player.onAllSeekComplete(nativePlayer);
        verify(listener).onSubtitleMetadataUpdated(any(), eq(-1));
        verify(listener, never()).onSubtitleLanguagesUpdated(any(), anyInt());
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = Player.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
