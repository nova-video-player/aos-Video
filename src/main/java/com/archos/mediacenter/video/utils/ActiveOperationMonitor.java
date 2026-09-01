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

package com.archos.mediacenter.video.utils;

import android.app.Activity;
import android.view.WindowManager;

import com.archos.mediacenter.video.browser.FileManagerService;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.NetworkScannerReceiver;

/**
 * Monitors active background operations (network scanning, metadata scraping,
 * media importing, and file copy/transfers) to manage activity window keep-screen-on state.
 */
public class ActiveOperationMonitor {

    private ActiveOperationMonitor() {
        /* static utility */
    }

    /**
     * Returns true if any long-running operation is currently active.
     */
    public static boolean isOperationActive() {
        boolean isScanning = NetworkScannerReceiver.isScannerWorking();
        boolean isScraping = LoaderUtils.getScrapeInProgress();
        boolean isInitialImport = ImportState.VIDEO.isInitialImport();
        boolean isRegularImport = ImportState.VIDEO.isRegularImport();
        boolean isFileCopy = FileManagerService.fileManagerService != null
                && FileManagerService.fileManagerService.isPastingInProgress();

        return isScanning || isScraping || isInitialImport || isRegularImport || isFileCopy;
    }

    /**
     * Updates the activity's window FLAG_KEEP_SCREEN_ON based on active operations.
     */
    public static void updateKeepScreenOn(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (isOperationActive()) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * Explicitly clears FLAG_KEEP_SCREEN_ON from the activity window (e.g. onPause).
     */
    public static void clearKeepScreenOn(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
