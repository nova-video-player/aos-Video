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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackResumePolicyTest {

    @Test
    public void backgroundCheckpointWinsOverOriginalExplicitPosition() {
        assertEquals(PlaybackResumePolicy.StartupSource.CHECKPOINT,
                PlaybackResumePolicy.chooseStartupSource(false, 25_000, 10_000, -1));
    }

    @Test
    public void continuingSessionKeepsLivePositionInsteadOfOriginalExplicitPosition() {
        assertEquals(PlaybackResumePolicy.StartupSource.RETAINED_LIVE,
                PlaybackResumePolicy.chooseStartupSource(true, -1, 10_000, 25_000));
    }

    @Test
    public void freshLaunchStillUsesExplicitPosition() {
        assertEquals(PlaybackResumePolicy.StartupSource.EXPLICIT,
                PlaybackResumePolicy.chooseStartupSource(false, -1, 10_000, -1));
    }

    @Test
    public void retainedLiveSelectionSurvivesDefaultDatabaseMetadata() {
        assertTrue(PlaybackResumePolicy.shouldPreserveLiveSelection(true, true));
        assertFalse(PlaybackResumePolicy.shouldPreserveLiveSelection(false, true));
    }

    @Test
    public void externalLaunchStartsANewSessionUnlessThisIsLifecycleReattachment() {
        assertTrue(PlaybackResumePolicy.startsNewExternalSession(-1, true));
        assertFalse(PlaybackResumePolicy.startsNewExternalSession(25_000, true));
        assertFalse(PlaybackResumePolicy.startsNewExternalSession(-1, false));
    }

    @Test
    public void externalStartOverIsNotReplacedBySameUriDatabaseResume() {
        assertFalse(PlaybackResumePolicy.shouldPromoteSameUriToLastPosition(0, 3, true));
        assertTrue(PlaybackResumePolicy.shouldPromoteSameUriToLastPosition(0, 3, false));
        assertFalse(PlaybackResumePolicy.shouldPromoteSameUriToLastPosition(3, 3, false));
    }

    @Test
    public void databaseResumeRequiresPriorPlayUnlessRemoteWasExplicitlyRequested() {
        assertFalse(PlaybackResumePolicy.isDatabaseResumeEligible(0, 1, 3));
        assertTrue(PlaybackResumePolicy.isDatabaseResumeEligible(1, 1, 3));
        assertTrue(PlaybackResumePolicy.isDatabaseResumeEligible(0, 3, 3));
    }

    @Test
    public void pauseIsRestoredOnlyForARecreatedCheckpointOfTheSameUri() {
        assertTrue(PlaybackResumePolicy.isPausedSession(true, 25_000, "file", "file"));
        assertFalse(PlaybackResumePolicy.isPausedSession(true, -1, "file", "file"));
        assertFalse(PlaybackResumePolicy.isPausedSession(true, 25_000, "old", "new"));
    }
}
