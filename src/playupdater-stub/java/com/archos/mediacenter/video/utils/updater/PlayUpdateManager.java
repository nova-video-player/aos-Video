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

package com.archos.mediacenter.video.utils.updater;

import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

/**
 * No-op {@link PlayUpdateManager} for builds that must not ship proprietary Play
 * Core: Amazon, F-Droid (-Puniversal) and GitHub (-Psponsor). Same public API as
 * the real implementation in src/playupdater.
 */
public final class PlayUpdateManager {

    /** Mirrors the real implementation's result code; never delivered in stub builds. */
    public static final int RESULT_IN_APP_UPDATE_FAILED = 1;

    public interface CheckCallback {
        void onUpdateAvailable(long availableVersionCode);
        void onUpToDate();
        void onError(String message);
    }

    public PlayUpdateManager(Context context) {
    }

    public void checkForUpdate(final CheckCallback callback) {
        callback.onUpToDate();
    }

    public boolean startImmediateUpdate(ActivityResultLauncher<IntentSenderRequest> launcher) {
        return false;
    }

    public static void openPlayStore(Context context) {
        // No Play Store on these builds.
    }
}
