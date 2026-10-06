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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.ActivityResult;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real Google Play in-app updates wrapper (Play-distributed flavors only), the
 * counterpart of {@link UpdateManager}. Its public API deliberately exposes no
 * Play types so the no-op stub in src/playupdater-stub is a drop-in replacement.
 *
 * In-app update flows are unsupported on Android TV, so callers fall back to
 * {@link #openPlayStore(Context)} there.
 */
public final class PlayUpdateManager {

    private static final Logger log = LoggerFactory.getLogger(PlayUpdateManager.class);

    /** Result code sent by the Play update flow when it fails (distinct from user cancel). */
    public static final int RESULT_IN_APP_UPDATE_FAILED = ActivityResult.RESULT_IN_APP_UPDATE_FAILED;

    public interface CheckCallback {
        void onUpdateAvailable(long availableVersionCode);
        void onUpToDate();
        void onError(String message);
    }

    private final AppUpdateManager manager;
    private AppUpdateInfo appUpdateInfo;

    public PlayUpdateManager(Context context) {
        manager = AppUpdateManagerFactory.create(context.getApplicationContext());
    }

    public void checkForUpdate(final CheckCallback callback) {
        manager.getAppUpdateInfo()
                .addOnSuccessListener(info -> {
                    int availability = info.updateAvailability();
                    if (availability == UpdateAvailability.UPDATE_AVAILABLE
                            || availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        appUpdateInfo = info;
                        callback.onUpdateAvailable(info.availableVersionCode());
                    } else {
                        appUpdateInfo = null;
                        callback.onUpToDate();
                    }
                })
                .addOnFailureListener(e -> {
                    log.warn("checkForUpdate failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /**
     * Start an immediate (full-screen) update. Returns false when the platform does
     * not allow it (e.g. Android TV), so the caller can open the store listing.
     */
    public boolean startImmediateUpdate(ActivityResultLauncher<IntentSenderRequest> launcher) {
        if (appUpdateInfo == null || launcher == null) return false;
        if (!appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return false;
        try {
            manager.startUpdateFlowForResult(appUpdateInfo, launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build());
            return true;
        } catch (Exception e) {
            log.warn("startImmediateUpdate failed", e);
            return false;
        }
    }

    /** Open the Play Store listing, falling back to the web listing when Play is absent. */
    public static void openPlayStore(Context context) {
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(market);
        } catch (ActivityNotFoundException e) {
            Intent web = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(web);
            } catch (ActivityNotFoundException e2) {
                log.warn("openPlayStore: no store activity available");
            }
        }
    }
}
