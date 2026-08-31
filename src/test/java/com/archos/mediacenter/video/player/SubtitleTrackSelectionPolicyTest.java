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
 * Dedicated unit tests verifying the Subtitle Display & Track Selection specification
 * defined in Video/doc/SUBTITLES.md.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SubtitleTrackSelectionPolicyTest {

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
    // Specification Examples Table (Video/doc/SUBTITLES.md lines 201-213)
    // -------------------------------------------------------------------------

    @Test
    public void testSpecTable_Row1_EnglishNative_FullAndForced_SelectsForcedOnly() throws Exception {
        // Active audio: English | System locale: English | favSubLang: English | Hide: off
        // Available: English full (0); English forced (1) -> Expected: English forced (1)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("English Full", "eng", "", 0, false, 0),
                new SubFixture("English Forced", "eng", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "en", "en", false);
        assertEquals("English native with full and forced must select forced only", 1, sub);
    }

    @Test
    public void testSpecTable_Row2_EnglishNative_FullOnly_SelectsNone() throws Exception {
        // Active audio: English | System locale: English | favSubLang: English | Hide: off
        // Available: English full (0) -> Expected: None (-1)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("English Full", "eng", "", 0, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "en", "en", false);
        assertEquals("English native with full subtitles only must select none", -1, sub);
    }

    @Test
    public void testSpecTable_Row3_FrenchNative_FullAndForced_SelectsForcedOnly() throws Exception {
        // Active audio: French | System locale: French | favSubLang: French | Hide: off
        // Available: French full (0); French forced (1) -> Expected: French forced (1)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("French", "fra", 1, true),
                new SubFixture("French Full", "fra", "", 0, false, 0),
                new SubFixture("French Forced", "fra", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", false);
        assertEquals("French native with full and forced must select forced only", 1, sub);
    }

    @Test
    public void testSpecTable_Row4_FrenchNative_FullOnly_SelectsNone() throws Exception {
        // Active audio: French | System locale: French | favSubLang: French | Hide: off
        // Available: French full (0) -> Expected: None (-1)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("French", "fra", 1, true),
                new SubFixture("French Full", "fra", "", 0, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", false);
        assertEquals("French native with full subtitles only must select none", -1, sub);
    }

    @Test
    public void testSpecTable_Row5_FrenchUser_EnglishAudio_FullFrenchAndEnglishForced_SelectsFrenchFull() throws Exception {
        // Active audio: English | System locale: French | favSubLang: French | Hide: off
        // Available: French full (0); English forced (1) -> Expected: French full (0)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("French Full", "fra", "", 0, false, 0),
                new SubFixture("English Forced", "eng", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", false);
        assertEquals("French user watching English audio must receive full French subtitles", 0, sub);
    }

    @Test
    public void testSpecTable_Row6_FrenchUser_EnglishSubPref_FrenchAudio_SelectsEnglishFull() throws Exception {
        // Active audio: French | System locale: French | favSubLang: English | Hide: off
        // Available: English full (0); French forced (1) -> Expected: English full (0)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("French", "fra", 1, true),
                new SubFixture("English Full", "eng", "", 0, false, 0),
                new SubFixture("French Forced", "fra", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "en", false);
        assertEquals("French user with English sub pref watching French audio must receive full English subtitles", 0, sub);
    }

    @Test
    public void testSpecTable_Row7_JapaneseAudio_FrenchUser_EnglishFullOnly_SelectsEnglishFull() throws Exception {
        // Active audio: Japanese | System locale: French | favSubLang: French | Hide: off
        // Available: English full only (0) -> Expected: English full (0) via fallback
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("Japanese", "jpn", 1, true),
                new SubFixture("English Full", "eng", "", 0, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", false);
        assertEquals("Foreign audio without matching preferred subtitle must fall back to English full subtitle", 0, sub);
    }

    @Test
    public void testSpecTable_Row8_EnglishAudio_ExactSidecarDummySrt_SelectsDummySrtOverCompetingTaggedSub() throws Exception {
        // Active audio: English | System locale: English | favSubLang: English | Hide: off
        // Available: dummy.srt (0, exact sidecar); dummy.fre.srt (1, French tagged external sub)
        // Expected: dummy.srt (0) via selectDefaultExternalTextSubtitleTrack
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("dummy.srt", "", "/dummy.srt", 0, false, 0),
                new SubFixture("dummy.fre.srt", "fra", "/dummy.fre.srt", 0, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "en", "en", false);
        assertEquals("Exact sidecar dummy.srt must be selected for native English audio over non-exact tagged track", 0, sub);
    }

    @Test
    public void testSpecTable_Row9_EnglishAudio_FrenchLocale_HideOn_EnglishForcedOnly_SelectsEnglishForced() throws Exception {
        // Active audio: English | System locale: French | favSubLang: French | Hide: on
        // Available: English forced (0) -> Expected: English forced (0)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("English Forced", "eng", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", true);
        assertEquals("Hide subtitles on must still select forced subtitle matching active audio", 0, sub);
    }

    @Test
    public void testSpecTable_Row10_SingleAudio_UntaggedForcedTrack_HideOn_SelectsUntaggedForced() throws Exception {
        // Active audio: French (single track) | System locale: French | favSubLang: French | Hide: on
        // Available: Forced with no language (0) -> Expected: Forced track (0)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("French", "fra", 1, true),
                new SubFixture("Forced Track", "und", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", true);
        assertEquals("Single audio track allows untagged forced subtitle when hide is on", 0, sub);
    }

    // -------------------------------------------------------------------------
    // Edge Cases & Policy Rules
    // -------------------------------------------------------------------------

    @Test
    public void testMultiAudio_RejectsUntaggedForcedTrack() throws Exception {
        // Multiple audio tracks: untagged forced track must NOT be selected to prevent wrong language
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture[]{
                        new AudioFixture("French", "fra", 1, true),
                        new AudioFixture("English", "eng", 0, true)
                },
                new SubFixture[]{
                        new SubFixture("Forced Track", "und", "", 64, false, 0)
                }
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", true);
        assertEquals("Multiple audio tracks must reject untagged forced track", -1, sub);
    }

    @Test
    public void testPreservesValidSavedSubtitleSelection() throws Exception {
        // Saved subtitle index 1 with language 'fr' still matches -> keep saved selection
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("English Full", "eng", "", 0, false, 0),
                new SubFixture("French Full", "fra", "", 0, false, 0)
        );
        VideoDbInfo videoInfo = new VideoDbInfo();
        videoInfo.audioTrack = 0;
        videoInfo.subtitleTrack = 1;
        videoInfo.subtitleLanguage = "fr";

        int sub = runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, "fr", "en", false);
        assertEquals("Valid saved subtitle selection must be preserved", 1, sub);
    }

    @Test
    public void testForcedTrackNeverUsedAsFallbackForFullSubtitle() throws Exception {
        // User wants French full subtitles, but only English forced is available -> None
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("Japanese", "jpn", 1, true),
                new SubFixture("English Forced", "eng", "", 64, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "fr", "fr", false);
        assertEquals("Forced track must never be used as a fallback for requested full subtitles", -1, sub);
    }

    @Test
    public void testAudioTrackChange_SwitchesMatchingForcedSubtitles() throws Exception {
        // Video with 2 audio tracks (0: English, 1: French) and 2 forced subtitle tracks (0: English forced, 1: French forced)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture[]{
                        new AudioFixture("English Audio", "eng", 1, true),
                        new AudioFixture("French Audio", "fra", 0, true)
                },
                new SubFixture[]{
                        new SubFixture("English Forced", "eng", "", 64, false, 0),
                        new SubFixture("French Forced", "fra", "", 64, false, 0)
                }
        );
        VideoDbInfo videoInfo = new VideoDbInfo();
        videoInfo.audioTrack = 0; // English audio
        videoInfo.subtitleTrack = -1;
        videoInfo.subtitleLanguage = null;

        // 1. Initial selection with English audio (hideSubtitles = true) -> English forced (0)
        int initialSub = runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, "en", "en", true);
        assertEquals("Initial selection on English audio must pick English forced", 0, initialSub);

        // 2. User changes audio track to French audio (track 1)
        videoInfo.audioTrack = 1;
        videoInfo.subtitleTrack = -1; // reset for re-evaluation on audio change
        videoInfo.subtitleLanguage = null;

        int updatedSub = runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, "en", "en", true);
        assertEquals("Audio switch to French must update subtitle to French forced", 1, updatedSub);
    }

    @Test
    public void testAudioTrackChange_DisablesSubtitlesWhenNoMatchingForcedTrackExists() throws Exception {
        // Video with 2 audio tracks (0: English, 1: Spanish) and only English forced subtitle (0)
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture[]{
                        new AudioFixture("English Audio", "eng", 1, true),
                        new AudioFixture("Spanish Audio", "spa", 0, true)
                },
                new SubFixture[]{
                        new SubFixture("English Forced", "eng", "", 64, false, 0)
                }
        );
        VideoDbInfo videoInfo = new VideoDbInfo();
        videoInfo.audioTrack = 0; // English audio
        videoInfo.subtitleTrack = -1;
        videoInfo.subtitleLanguage = null;

        // 1. Initial selection on English audio -> English forced (0)
        int initialSub = runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, "en", "en", false);
        assertEquals("Initial selection on English audio must pick English forced", 0, initialSub);

        // 2. Switch audio to Spanish (track 1) where no Spanish forced track exists
        videoInfo.audioTrack = 1;
        videoInfo.subtitleTrack = -1;
        videoInfo.subtitleLanguage = null;

        int updatedSub = runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, "en", "en", true);
        assertEquals("Audio switch without matching forced sub must disable subtitles", -1, updatedSub);
    }

    @Test
    public void testAudioTrackChange_PreservesManualOrSavedFullSubtitleSelection() throws Exception {
        // User manually selected French full subtitle (1). Changing audio track from English to Spanish must keep French full subtitle.
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture[]{
                        new AudioFixture("English Audio", "eng", 1, true),
                        new AudioFixture("Spanish Audio", "spa", 0, true)
                },
                new SubFixture[]{
                        new SubFixture("English Forced", "eng", "", 64, false, 0),
                        new SubFixture("French Full", "fra", "", 0, false, 0)
                }
        );
        VideoDbInfo videoInfo = new VideoDbInfo();
        videoInfo.audioTrack = 1; // Spanish audio
        videoInfo.subtitleTrack = 1; // User selected French full
        videoInfo.subtitleLanguage = "fr";

        int sub = runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, "en", "en", false);
        assertEquals("Audio change must preserve existing manual / valid saved full subtitle selection", 1, sub);
    }

    @Test
    public void testSubtitleDefaultDispositionTieBreaker() throws Exception {
        // Two French full tracks: Track 0 (normal), Track 1 (default flag) -> prefer default
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("French Track 1", "fra", "", 0, false, 0),
                new SubFixture("French Track 2 (Default)", "fra", "", 1, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "en", "fr", false);
        assertEquals("Preferred language candidate marked default must be selected over first match", 1, sub);
    }

    @Test
    public void testChineseSubtitleVariantTieBreaker() throws Exception {
        // Traditional vs Simplified for zh-tw locale
        VideoMetadata vMetadata = createMetadata(
                new AudioFixture("English", "eng", 1, true),
                new SubFixture("Simplified", "chi", "", 0, false, 0),
                new SubFixture("Traditional", "chi", "", 0, false, 0)
        );
        int sub = runSubtitleSelection(vMetadata, 0, "zh-tw", "zh-tw", false);
        assertEquals("Traditional Chinese variant must be selected for zh-tw preference", 1, sub);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int runSubtitleSelection(VideoMetadata vMetadata, int activeAudioTrack,
                                     String uiLocale, String favSubLang, boolean hideSubtitles) throws Exception {
        VideoDbInfo videoInfo = new VideoDbInfo();
        videoInfo.audioTrack = activeAudioTrack;
        videoInfo.subtitleTrack = -1;
        videoInfo.subtitleLanguage = null;
        return runSubtitleSelectionWithDbInfo(vMetadata, videoInfo, uiLocale, favSubLang, hideSubtitles);
    }

    private int runSubtitleSelectionWithDbInfo(VideoMetadata vMetadata, VideoDbInfo videoInfo,
                                               String uiLocale, String favSubLang, boolean hideSubtitles) throws Exception {
        Locale.setDefault(Locale.forLanguageTag(uiLocale));
        Mockito.when(mMockPlayer.getVideoMetadata()).thenReturn(vMetadata);

        setPrivateField(mService, "mVideoInfo", videoInfo);
        setPrivateField(mService, "mSubsFavoriteLanguage", favSubLang);
        setPrivateField(mService, "mHideSubtitles", hideSubtitles);
        setPrivateField(mService, "firstTimeSubCalled", true);
        setPrivateField(mService, "mIsPreparingSubs", false);

        mService.onSubtitleMetadataUpdated(vMetadata, -1);

        int actualSubtitle = videoInfo.subtitleTrack;
        int nbSubTracks = vMetadata.getSubtitleTrackNb();
        return (actualSubtitle == nbSubTracks) ? -1 : actualSubtitle;
    }

    private static class AudioFixture {
        final String name;
        final String lang;
        final int disposition;
        final boolean supported;

        AudioFixture(String name, String lang, int disposition, boolean supported) {
            this.name = name;
            this.lang = lang;
            this.disposition = disposition;
            this.supported = supported;
        }
    }

    private static class SubFixture {
        final String name;
        final String lang;
        final String path;
        final int disposition;
        final boolean isGfx;
        final int format;

        SubFixture(String name, String lang, String path, int disposition, boolean isGfx, int format) {
            this.name = name;
            this.lang = lang;
            this.path = path;
            this.disposition = disposition;
            this.isGfx = isGfx;
            this.format = format;
        }
    }

    private VideoMetadata createMetadata(AudioFixture audio, SubFixture... subFixtures) throws Exception {
        return createMetadata(new AudioFixture[]{audio}, subFixtures);
    }

    private VideoMetadata createMetadata(AudioFixture[] audioFixtures, SubFixture[] subFixtures) throws Exception {
        VideoMetadata metadata = new VideoMetadata("/dummy.mkv");

        List<VideoMetadata.AudioTrack> audioList = new ArrayList<>();
        for (AudioFixture a : audioFixtures) {
            VideoMetadata.AudioTrack track = (VideoMetadata.AudioTrack) allocateInstance(VideoMetadata.AudioTrack.class);
            setFinalField(track, "name", a.name);
            setFinalField(track, "language", a.lang);
            setFinalField(track, "format", "AC3");
            setFinalField(track, "disposition", a.disposition);
            setFinalField(track, "supported", a.supported);
            setFinalField(track, "bitRate", 0);
            setFinalField(track, "sampleRate", 0);
            setFinalField(track, "channels", "");
            setFinalField(track, "vbr", false);
            audioList.add(track);
        }
        setPrivateField(metadata, "mAudioTrackList", audioList.toArray(new VideoMetadata.AudioTrack[0]));

        List<VideoMetadata.SubtitleTrack> subList = new ArrayList<>();
        for (SubFixture s : subFixtures) {
            VideoMetadata.SubtitleTrack track = (VideoMetadata.SubtitleTrack) allocateInstance(VideoMetadata.SubtitleTrack.class);
            setFinalField(track, "name", s.name);
            setFinalField(track, "path", s.path);
            setFinalField(track, "isExternal", (s.path != null && !s.path.isEmpty()));
            setFinalField(track, "isGfx", s.isGfx);
            setFinalField(track, "format", s.format);
            setFinalField(track, "language", s.lang);
            setFinalField(track, "disposition", s.disposition);
            subList.add(track);
        }
        setPrivateField(metadata, "mSubtitleTrackList", subList.toArray(new VideoMetadata.SubtitleTrack[0]));

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
