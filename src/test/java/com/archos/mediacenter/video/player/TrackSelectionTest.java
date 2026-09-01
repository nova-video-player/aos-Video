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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.net.Uri;

import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.utils.VideoMetadata;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrackSelectionTest {

    private Locale mOriginalLocale;
    private PlayerService mService;
    private Player mMockPlayer;

    @Before
    public void setUp() throws Exception {
        mOriginalLocale = Locale.getDefault();
        mService = Robolectric.buildService(PlayerService.class).get();
        mMockPlayer = Mockito.mock(Player.class);
        Mockito.when(mMockPlayer.setAudioTrack(Mockito.anyInt())).thenReturn(true);
        Mockito.when(mMockPlayer.setSubtitleTrack(Mockito.anyInt())).thenReturn(true);
        setPrivateField(mService, "mPlayer", mMockPlayer);
        setPrivateField(mService, "mUri", Uri.parse("file:///dummy.mkv"));
    }

    @After
    public void tearDown() {
        Locale.setDefault(mOriginalLocale);
    }

    @Test
    public void testCsvTrackSelectionSuite() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("track_selection_tests.csv");
        assertNotNull("Could not find track_selection_tests.csv in test resources", is);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] parts = parseCsvLine(line);
            if (parts.length < 9) {
                throw new AssertionError("Malformed CSV row at line " + lineNum + " (expected at least 9 fields, got " + parts.length + "): " + line);
            }

            String testName = parts[0];
            String audioTracksStr = parts[1];
            String subtitleTracksStr = parts[2];
            String uiLocaleStr = parts[3];
            String favAudioLang = parts[4];
            String favSubLang = parts[5];
            boolean hideSubtitles = Boolean.parseBoolean(parts[6]);
            boolean preferOriginalAudio = false;
            String originalAudioLang = "";
            int expectedAudio;
            int expectedSubtitle;

            if (parts.length >= 11) {
                preferOriginalAudio = Boolean.parseBoolean(parts[7]);
                originalAudioLang = parts[8];
                expectedAudio = Integer.parseInt(parts[9]);
                expectedSubtitle = Integer.parseInt(parts[10]);
            } else {
                expectedAudio = Integer.parseInt(parts[7]);
                expectedSubtitle = Integer.parseInt(parts[8]);
            }

            runSelectionTest(testName, audioTracksStr, subtitleTracksStr, uiLocaleStr,
                    favAudioLang, favSubLang, hideSubtitles, preferOriginalAudio, originalAudioLang,
                    expectedAudio, expectedSubtitle);
        }
        reader.close();
    }

    private void runSelectionTest(String testName, String audioTracksStr, String subtitleTracksStr,
                                  String uiLocaleStr, String favAudioLang, String favSubLang,
                                  boolean hideSubtitles, boolean preferOriginalAudio,
                                  String originalAudioLang, int expectedAudio, int expectedSubtitle) throws Exception {
        Locale.setDefault(Locale.forLanguageTag(uiLocaleStr));

        VideoMetadata vMetadata = createVideoMetadata(audioTracksStr, subtitleTracksStr);
        Mockito.when(mMockPlayer.getVideoMetadata()).thenReturn(vMetadata);

        VideoDbInfo videoInfo = new VideoDbInfo();
        videoInfo.audioTrack = -1;
        videoInfo.subtitleTrack = -1;
        videoInfo.subtitleLanguage = null;
        if (originalAudioLang != null && !originalAudioLang.isEmpty()) {
            videoInfo.scraperOriginalLanguage = originalAudioLang;
        }

        setPrivateField(mService, "mVideoInfo", videoInfo);
        setPrivateField(mService, "mAudioTrackFavoriteLanguage", favAudioLang);
        setPrivateField(mService, "mSubsFavoriteLanguage", favSubLang);
        setPrivateField(mService, "mHideSubtitles", hideSubtitles);
        setPrivateField(mService, "mPreferOriginalAudioTrack", preferOriginalAudio);
        setPrivateField(mService, "firstTimeAudioCalled", true);
        setPrivateField(mService, "firstTimeSubCalled", true);
        setPrivateField(mService, "mIsPreparingSubs", false);

        // 1. Run audio track selection
        mService.onAudioMetadataUpdated(vMetadata, -1);

        // 2. Run subtitle track selection
        mService.onSubtitleMetadataUpdated(vMetadata, -1);

        int actualAudio = videoInfo.audioTrack;
        int actualSubtitle = videoInfo.subtitleTrack;
        int nbSubTracks = vMetadata.getSubtitleTrackNb();

        // noneTrack in PlayerService is set to nbSubTracks internally (which maps to -1 player track)
        int normalizedActualSubtitle = (actualSubtitle == nbSubTracks) ? -1 : actualSubtitle;

        String actualAudioStr = formatAudioTrackInfo(vMetadata, actualAudio);
        String expectedAudioStr = formatAudioTrackInfo(vMetadata, expectedAudio);
        String actualSubStr = formatSubtitleTrackInfo(vMetadata, normalizedActualSubtitle);
        String expectedSubStr = formatSubtitleTrackInfo(vMetadata, expectedSubtitle);

        System.out.println("TEST [" + testName + "] Result -> Audio: " + actualAudioStr + " (expected: " + expectedAudioStr + "), Subtitle: " + actualSubStr + " (expected: " + expectedSubStr + ")");
        assertEquals(testName + " [Audio Track Selection]", expectedAudio, actualAudio);
        assertEquals(testName + " [Subtitle Track Selection]", expectedSubtitle, normalizedActualSubtitle);
    }

    private String formatAudioTrackInfo(VideoMetadata vMetadata, int index) {
        if (index < 0 || index >= vMetadata.getAudioTrackNb()) {
            return "none (" + index + ")";
        }
        VideoMetadata.AudioTrack track = vMetadata.getAudioTrack(index);
        String lang = (track.language != null && !track.language.isEmpty()) ? track.language : "und";
        return index + " [int, " + lang + "]";
    }

    private String formatSubtitleTrackInfo(VideoMetadata vMetadata, int index) {
        if (index < 0 || index >= vMetadata.getSubtitleTrackNb()) {
            return "none (-1)";
        }
        VideoMetadata.SubtitleTrack track = vMetadata.getSubtitleTrack(index);
        String type = track.isExternal ? "ext" : "int";
        String lang = (track.language != null && !track.language.isEmpty()) ? track.language : "und";
        return index + " [" + type + ", " + lang + "]";
    }

    private VideoMetadata createVideoMetadata(String audioTracksStr, String subtitleTracksStr) throws Exception {
        VideoMetadata metadata = new VideoMetadata("/dummy.mkv");

        // Parse Audio Tracks
        if (audioTracksStr != null && !audioTracksStr.trim().isEmpty()) {
            String[] audioEntries = audioTracksStr.split("\\|");
            List<VideoMetadata.AudioTrack> audioList = new ArrayList<>();
            for (String entry : audioEntries) {
                String[] f = entry.split(";", -1);
                String name = f.length > 0 ? f[0] : "";
                String lang = f.length > 1 ? f[1] : "und";
                String format = f.length > 2 ? f[2] : "AC3";
                int disp = f.length > 3 && !f[3].isEmpty() ? Integer.parseInt(f[3]) : 0;
                boolean supported = f.length <= 4 || Boolean.parseBoolean(f[4]);

                VideoMetadata.AudioTrack audioTrack = instantiateAudioTrack(name, format, lang, disp, supported);
                audioList.add(audioTrack);
            }
            VideoMetadata.AudioTrack[] audioArr = audioList.toArray(new VideoMetadata.AudioTrack[0]);
            setPrivateField(metadata, "mAudioTrackList", audioArr);
        }

        // Parse Subtitle Tracks
        if (subtitleTracksStr != null && !subtitleTracksStr.trim().isEmpty()) {
            String[] subEntries = subtitleTracksStr.split("\\|");
            List<VideoMetadata.SubtitleTrack> subList = new ArrayList<>();
            for (String entry : subEntries) {
                String[] f = entry.split(";", -1);
                String name = f.length > 0 ? f[0] : "";
                String lang = f.length > 1 ? f[1] : "und";
                String path = f.length > 2 ? f[2] : "";
                int disp = f.length > 3 && !f[3].isEmpty() ? Integer.parseInt(f[3]) : 0;
                boolean isGfx = f.length > 4 && Boolean.parseBoolean(f[4]);
                int format = f.length > 5 && !f[5].isEmpty() ? Integer.parseInt(f[5]) : 0;

                VideoMetadata.SubtitleTrack subTrack = instantiateSubtitleTrack(name, path, isGfx, format, lang, disp);
                subList.add(subTrack);
            }
            VideoMetadata.SubtitleTrack[] subArr = subList.toArray(new VideoMetadata.SubtitleTrack[0]);
            setPrivateField(metadata, "mSubtitleTrackList", subArr);
        }

        return metadata;
    }

    private VideoMetadata.AudioTrack instantiateAudioTrack(String name, String format, String lang,
                                                          int disposition, boolean supported) throws Exception {
        VideoMetadata.AudioTrack track = (VideoMetadata.AudioTrack) allocateInstance(VideoMetadata.AudioTrack.class);
        setFinalField(track, "name", name);
        setFinalField(track, "format", format);
        setFinalField(track, "language", lang);
        setFinalField(track, "disposition", disposition);
        setFinalField(track, "supported", supported);
        setFinalField(track, "bitRate", 0);
        setFinalField(track, "sampleRate", 0);
        setFinalField(track, "channels", "");
        setFinalField(track, "vbr", false);
        return track;
    }

    private VideoMetadata.SubtitleTrack instantiateSubtitleTrack(String name, String path, boolean isGfx,
                                                                int format, String lang, int disposition) throws Exception {
        VideoMetadata.SubtitleTrack track = (VideoMetadata.SubtitleTrack) allocateInstance(VideoMetadata.SubtitleTrack.class);
        setFinalField(track, "name", name);
        setFinalField(track, "path", path);
        setFinalField(track, "isExternal", (path != null && !path.isEmpty()));
        setFinalField(track, "isGfx", isGfx);
        setFinalField(track, "format", format);
        setFinalField(track, "language", lang);
        setFinalField(track, "disposition", disposition);
        return track;
    }

    private Object allocateInstance(Class<?> clazz) throws Exception {
        try {
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Method allocateInstanceMethod = unsafe.getClass().getMethod("allocateInstance", Class.class);
            return allocateInstanceMethod.invoke(unsafe, clazz);
        } catch (Exception e) {
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                c.setAccessible(true);
                Object[] args = new Object[c.getParameterTypes().length];
                try {
                    return c.newInstance(args);
                } catch (Exception ignored) {}
            }
            throw new RuntimeException("Could not allocate instance of " + clazz, e);
        }
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setFinalField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String[] parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString().trim());
        return parts.toArray(new String[0]);
    }
}
