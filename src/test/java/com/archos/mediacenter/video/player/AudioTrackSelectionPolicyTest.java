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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated unit tests verifying the Audio Track Selection specification
 * defined in Video/doc/AUDIO_TRACK_SELECTION.md.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AudioTrackSelectionPolicyTest {

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

    // -------------------------------------------------------------------------
    // 1. Scope & Preservation of User Choices
    // -------------------------------------------------------------------------

    @Test
    public void testPreservesValidSavedAudioTrack() throws Exception {
        // Saved audio track index 1 is valid and supported -> must not be overridden
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("English", "eng", "AC3", 1, true),
                new AudioFixture("French", "fra", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(1, null);

        runAudioSelection(vMetadata, videoInfo, "eng", false, null);

        assertEquals("Saved valid audio track must be retained", 1, videoInfo.audioTrack);
    }

    @Test
    public void testReplacesInvalidNegativeSavedAudioTrack() throws Exception {
        // Saved audio track is -1 (first playback / unselected) -> auto-selection runs
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("English", "eng", "AC3", 0, true),
                new AudioFixture("French", "fra", "AC3", 1, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, null);

        runAudioSelection(vMetadata, videoInfo, "fra", false, null);

        assertEquals("Unselected track (-1) must trigger auto-selection of favorite language", 1, videoInfo.audioTrack);
    }

    @Test
    public void testReplacesOutOfRangeSavedAudioTrack() throws Exception {
        // Saved track index 5 is out of bounds for a 2-track file
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("English", "eng", "AC3", 0, true),
                new AudioFixture("French", "fra", "AC3", 1, true)
        );
        VideoDbInfo videoInfo = createDbInfo(5, null);

        runAudioSelection(vMetadata, videoInfo, "fra", false, null);

        assertEquals("Out-of-range track index must trigger auto-selection", 1, videoInfo.audioTrack);
    }

    @Test
    public void testReplacesUnsupportedSavedAudioTrack() throws Exception {
        // Saved track 0 is not supported -> auto-selection replaces it with favorite language (English)
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("Unsupported French", "fra", "UNKNOWN", 1, false),
                new AudioFixture("Supported English", "eng", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(0, null);

        runAudioSelection(vMetadata, videoInfo, "eng", false, null);

        assertEquals("Unsupported saved track must trigger selection of a supported track matching favorite", 1, videoInfo.audioTrack);
    }

    // -------------------------------------------------------------------------
    // 2. Automatic Selection Order
    // -------------------------------------------------------------------------

    @Test
    public void testOriginalLanguagePreferredOverFavoriteLanguageWhenEnabled() throws Exception {
        // Prefer original audio is ON, scraper original language is Japanese (ja), favorite is French
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("French Dub", "fra", "AC3", 1, true),
                new AudioFixture("Japanese VO", "jpn", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, "ja");

        runAudioSelection(vMetadata, videoInfo, "fra", true, "ja");

        assertEquals("Prefer VO must select Japanese VO over favorite French dub", 1, videoInfo.audioTrack);
    }

    @Test
    public void testFavoriteLanguageUsedWhenPreferOriginalAudioIsDisabled() throws Exception {
        // Prefer original audio is OFF, scraper original language is Japanese (ja), favorite is French
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("French Dub", "fra", "AC3", 1, true),
                new AudioFixture("Japanese VO", "jpn", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, "ja");

        runAudioSelection(vMetadata, videoInfo, "fra", false, "ja");

        assertEquals("When Prefer VO is off, favorite language (French) must be selected", 0, videoInfo.audioTrack);
    }

    @Test
    public void testFallbackToFavoriteLanguageWhenOriginalLanguageHasNoMatch() throws Exception {
        // Prefer original audio is ON with scraper original language 'de', but tracks are English and French
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("French Dub", "fra", "AC3", 0, true),
                new AudioFixture("English Dub", "eng", "AC3", 1, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, "de");

        runAudioSelection(vMetadata, videoInfo, "eng", true, "de");

        assertEquals("When VO has no match, fall back to favorite language (English)", 1, videoInfo.audioTrack);
    }

    @Test
    public void testFallbackToFirstSupportedTrackWhenNeitherLanguageMatches() throws Exception {
        // No match for VO (de) or favorite (es) -> select first supported track (0)
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("First Italian", "ita", "AC3", 0, true),
                new AudioFixture("Second German", "deu", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, null);

        runAudioSelection(vMetadata, videoInfo, "spa", false, null);

        assertEquals("Fallback to first supported track when no language matches", 0, videoInfo.audioTrack);
    }

    @Test
    public void testUndeterminedOriginalLanguageDoesNotTriggerVoMatching() throws Exception {
        // Scraper original language 'und' is invalid -> ignore VO preference, use favorite language
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("French Dub", "fra", "AC3", 0, true),
                new AudioFixture("English Dub", "eng", "AC3", 1, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, "und");

        runAudioSelection(vMetadata, videoInfo, "fra", true, "und");

        assertEquals("Original language 'und' must be ignored and favorite language selected", 0, videoInfo.audioTrack);
    }

    // -------------------------------------------------------------------------
    // 3. Language Matching & Tie Breakers
    // -------------------------------------------------------------------------

    @Test
    public void testOriginalLanguageDefaultDispositionTieBreaker() throws Exception {
        // Two Japanese VO tracks: track 0 (regular), track 1 (default flag) -> prefer default
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("Japanese Stereo", "jpn", "AC3", 0, true),
                new AudioFixture("Japanese 5.1 (Default)", "jpn", "AC3", 1, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, "ja");

        runAudioSelection(vMetadata, videoInfo, "eng", true, "ja");

        assertEquals("Original language match should prefer track marked default", 1, videoInfo.audioTrack);
    }

    @Test
    public void testFavoriteLanguageDefaultDispositionTieBreaker() throws Exception {
        // Two English tracks: track 0 (regular), track 1 (default) -> prefer default
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("English Stereo", "eng", "AC3", 0, true),
                new AudioFixture("English Surround (Default)", "eng", "AC3", 1, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, null);

        runAudioSelection(vMetadata, videoInfo, "eng", false, null);

        assertEquals("Favorite language match should prefer track marked default", 1, videoInfo.audioTrack);
    }

    @Test
    public void testChineseTitleVariantTieBreakerForMandarin() throws Exception {
        // Favorite language zh-cn with tracks: Cantonese (0) and Mandarin (1)
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("Cantonese", "chi", "AC3", 0, true),
                new AudioFixture("Mandarin", "chi", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, null);

        runAudioSelection(vMetadata, videoInfo, "zh-cn", false, null);

        assertEquals("Favorite language zh-cn must prefer Mandarin title match", 1, videoInfo.audioTrack);
    }

    @Test
    public void testChineseTitleVariantTieBreakerForCantonese() throws Exception {
        // Favorite language zh-hk with tracks: Mandarin (0) and Cantonese (1)
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("Mandarin", "chi", "AC3", 0, true),
                new AudioFixture("Cantonese", "chi", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, null);

        runAudioSelection(vMetadata, videoInfo, "zh-hk", false, null);

        assertEquals("Favorite language zh-hk must prefer Cantonese title match", 1, videoInfo.audioTrack);
    }

    @Test
    public void testSkipsUnsupportedTracksEvenIfLanguageMatches() throws Exception {
        // Track 0 matches favorite language but is unsupported -> select track 1
        VideoMetadata vMetadata = createAudioMetadata(
                new AudioFixture("Unsupported French", "fra", "UNKNOWN", 1, false),
                new AudioFixture("Supported French", "fra", "AC3", 0, true)
        );
        VideoDbInfo videoInfo = createDbInfo(-1, null);

        runAudioSelection(vMetadata, videoInfo, "fra", false, null);

        assertEquals("Unsupported track must be skipped", 1, videoInfo.audioTrack);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void runAudioSelection(VideoMetadata vMetadata, VideoDbInfo videoInfo,
                                   String favAudioLang, boolean preferVo, String originalAudioLang) throws Exception {
        Mockito.when(mMockPlayer.getVideoMetadata()).thenReturn(vMetadata);

        setPrivateField(mService, "mVideoInfo", videoInfo);
        setPrivateField(mService, "mAudioTrackFavoriteLanguage", favAudioLang);
        setPrivateField(mService, "mPreferOriginalAudioTrack", preferVo);
        setPrivateField(mService, "firstTimeAudioCalled", true);

        mService.onAudioMetadataUpdated(vMetadata, -1);
    }

    private VideoDbInfo createDbInfo(int savedAudioTrack, String originalLanguage) {
        VideoDbInfo info = new VideoDbInfo();
        info.audioTrack = savedAudioTrack;
        info.subtitleTrack = -1;
        info.subtitleLanguage = null;
        info.scraperOriginalLanguage = originalLanguage;
        return info;
    }

    private static class AudioFixture {
        final String name;
        final String lang;
        final String format;
        final int disposition;
        final boolean supported;

        AudioFixture(String name, String lang, String format, int disposition, boolean supported) {
            this.name = name;
            this.lang = lang;
            this.format = format;
            this.disposition = disposition;
            this.supported = supported;
        }
    }

    private VideoMetadata createAudioMetadata(AudioFixture... fixtures) throws Exception {
        VideoMetadata metadata = new VideoMetadata("/dummy.mkv");
        List<VideoMetadata.AudioTrack> list = new ArrayList<>();
        for (AudioFixture f : fixtures) {
            VideoMetadata.AudioTrack track = (VideoMetadata.AudioTrack) allocateInstance(VideoMetadata.AudioTrack.class);
            setFinalField(track, "name", f.name);
            setFinalField(track, "language", f.lang);
            setFinalField(track, "format", f.format);
            setFinalField(track, "disposition", f.disposition);
            setFinalField(track, "supported", f.supported);
            setFinalField(track, "bitRate", 0);
            setFinalField(track, "sampleRate", 0);
            setFinalField(track, "channels", "");
            setFinalField(track, "vbr", false);
            list.add(track);
        }
        setPrivateField(metadata, "mAudioTrackList", list.toArray(new VideoMetadata.AudioTrack[0]));
        setPrivateField(metadata, "mSubtitleTrackList", new VideoMetadata.SubtitleTrack[0]);
        return metadata;
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
}
