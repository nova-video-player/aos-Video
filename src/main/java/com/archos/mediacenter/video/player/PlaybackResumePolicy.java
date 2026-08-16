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

/** Pure resume decisions kept outside Android lifecycle code so regressions can be unit tested. */
final class PlaybackResumePolicy {

    enum StartupSource {
        NONE,
        CHECKPOINT,
        EXPLICIT,
        RETAINED_LIVE
    }

    private PlaybackResumePolicy() {}

    static StartupSource chooseStartupSource(boolean continuingSession, int checkpointPosition,
                                             int explicitPosition, int lastKnownPosition) {
        if (checkpointPosition >= 0) return StartupSource.CHECKPOINT;
        if (!continuingSession && explicitPosition > 0) return StartupSource.EXPLICIT;
        if (continuingSession && lastKnownPosition >= 0) return StartupSource.RETAINED_LIVE;
        if (explicitPosition > 0) return StartupSource.EXPLICIT;
        return StartupSource.NONE;
    }

    static boolean isDatabaseResumeEligible(long lastTimePlayed, int resumeMode,
                                            int remoteResumeMode) {
        return lastTimePlayed > 0 || resumeMode == remoteResumeMode;
    }

    static boolean shouldPreserveLiveSelection(boolean requestedDefault, boolean selectedLive) {
        return requestedDefault && selectedLive;
    }

    static boolean startsNewExternalSession(int checkpointPosition, boolean externalPlayerLaunch) {
        // A lifecycle reattachment carries the service checkpoint. Without it, an external
        // ACTION_VIEW is a new command even when it happens to target the same URI.
        return checkpointPosition < 0 && externalPlayerLaunch;
    }

    static boolean shouldPromoteSameUriToLastPosition(int resumeMode, int remoteResumeMode,
                                                      boolean freshExternalPositionCommand) {
        // A supplied external position key is an explicit command even when its value is zero or
        // malformed. In that case RESUME_NO means start over and must not be replaced by a stale
        // database resume merely because PlayerService still has metadata for the same URI.
        return resumeMode != remoteResumeMode && !freshExternalPositionCommand;
    }

    static int chooseExternalResultPosition(int servicePosition, int playerPosition,
                                            int checkpointPosition) {
        // Negative values mean that source is unavailable. A real zero is valid and must not
        // fall through to an older source such as the lifecycle checkpoint.
        if (servicePosition >= 0) return servicePosition;
        if (playerPosition >= 0) return playerPosition;
        if (checkpointPosition >= 0) return checkpointPosition;
        return 0;
    }

    static boolean isPausedSession(boolean userPaused, int checkpointPosition,
                                   String pausedUri, String currentUri) {
        return userPaused && checkpointPosition >= 0 && currentUri != null
                && currentUri.equals(pausedUri);
    }
}
