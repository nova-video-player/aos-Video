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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Checks the latest GitHub release, downloads the APK matching the device CPU
 * architecture, verifies it and installs it through the PackageInstaller.
 *
 * Only used by sponsor builds (see BuildConfig.ENABLE_SPONSOR) where
 * REQUEST_INSTALL_PACKAGES is declared.
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    private static final String USER_AGENT = "NovaVideoPlayer-Updater";
    private static final String UPDATE_DIR = "updates";

    public interface CheckCallback {
        void onSuccess(GitHubRelease release);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(GitHubRelease release, ReleaseAsset asset, File apk);
        void onError(String message);
    }

    public interface InstallCallback {
        void onResult(boolean success, String message);
    }

    private static volatile UpdateManager sInstance;

    private final OkHttpClient httpClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile Call currentCall;
    private volatile boolean cancelled;

    private volatile InstallCallback installCallback;

    private final Object checkLock = new Object();
    private boolean checkInFlight;
    private final List<CheckCallback> pendingCheckCallbacks = new ArrayList<>();

    private UpdateManager() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public static UpdateManager getInstance() {
        if (sInstance == null) {
            synchronized (UpdateManager.class) {
                if (sInstance == null) sInstance = new UpdateManager();
            }
        }
        return sInstance;
    }

    /**
     * Ask GitHub for the latest release. Concurrent callers (e.g. the settings screen being
     * reopened while a check is still running) are coalesced into a single network request;
     * every registered callback receives the result.
     */
    public void checkForUpdate(final CheckCallback callback) {
        boolean startNow;
        synchronized (checkLock) {
            pendingCheckCallbacks.add(callback);
            startNow = !checkInFlight;
            if (startNow) checkInFlight = true;
        }
        if (!startNow) return;

        executor.execute(() -> {
            GitHubRelease release = null;
            String error = null;
            try {
                release = UpdateChecker.fetchLatestRelease(httpClient);
                if (release == null) error = "no release found";
            } catch (IOException | JSONException e) {
                log.warn("checkForUpdate failed", e);
                error = e.getMessage();
            }
            final GitHubRelease result = release;
            final String errorMessage = error;
            final List<CheckCallback> callbacks;
            synchronized (checkLock) {
                callbacks = new ArrayList<>(pendingCheckCallbacks);
                pendingCheckCallbacks.clear();
                checkInFlight = false;
            }
            mainHandler.post(() -> {
                for (CheckCallback cb : callbacks) {
                    if (result != null) cb.onSuccess(result);
                    else cb.onError(errorMessage != null ? errorMessage : "check failed");
                }
            });
        });
    }

    /** Select the asset matching the most preferred supported ABI. */
    public ReleaseAsset chooseAsset(GitHubRelease release) {
        return UpdateChecker.selectAsset(release, Build.SUPPORTED_ABIS);
    }

    public void downloadUpdate(final Context context, final GitHubRelease release, final ReleaseAsset asset,
                               final DownloadCallback callback) {
        cancelled = false;
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            final File out = new File(downloadDirectory(appContext), asset.name);
            if (out.exists() && !out.delete()) {
                log.warn("downloadUpdate: could not delete stale {}", out);
            }
            Request request = new Request.Builder()
                    .url(asset.downloadUrl)
                    .header("User-Agent", USER_AGENT)
                    .build();
            currentCall = httpClient.newCall(request);
            try (Response response = currentCall.execute()) {
                if (!response.isSuccessful()) {
                    postDownloadError(callback, "HTTP " + response.code());
                    return;
                }
                long total = response.body().contentLength();
                if (total <= 0) total = asset.size;
                try (InputStream in = response.body().byteStream();
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buffer = new byte[16384];
                    long read = 0;
                    int len;
                    int lastPercent = -1;
                    while ((len = in.read(buffer)) > 0) {
                        if (cancelled) {
                            out.delete();
                            postDownloadError(callback, "cancelled");
                            return;
                        }
                        os.write(buffer, 0, len);
                        read += len;
                        if (total > 0) {
                            int percent = (int) (read * 100 / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final int reported = percent;
                                mainHandler.post(() -> callback.onProgress(reported));
                            }
                        }
                    }
                }
                if (total > 0 && out.length() < total) {
                    out.delete();
                    postDownloadError(callback, "incomplete download");
                    return;
                }
                ApkVerifier.Result result = ApkVerifier.verify(appContext, out);
                if (!result.isInstallable()) {
                    out.delete();
                    String reason = result.error != null ? result.error
                            : (!result.packageMatches ? "downloaded file is not Nova"
                            : "signature does not match the installed app");
                    postDownloadError(callback, reason);
                    return;
                }
                final File apk = out;
                mainHandler.post(() -> callback.onSuccess(release, asset, apk));
            } catch (IOException e) {
                if (cancelled) {
                    out.delete();
                    postDownloadError(callback, "cancelled");
                } else {
                    log.warn("downloadUpdate failed", e);
                    postDownloadError(callback, e.getMessage());
                }
            } finally {
                currentCall = null;
            }
        });
    }

    public void cancelDownload() {
        cancelled = true;
        Call call = currentCall;
        if (call != null) call.cancel();
    }

    public boolean canInstallPackages(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public Intent buildUnknownSourcesIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    public Intent buildUnknownSourcesFallbackIntent() {
        return new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
    }

    /**
     * Install the verified APK. The caller is responsible for checking
     * {@link #canInstallPackages(Context)} and directing the user to the unknown
     * sources screen first when needed.
     */
    public void installUpdate(Context context, File apk, InstallCallback callback) {
        installCallback = callback;
        final Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            PackageInstaller installer = appContext.getPackageManager().getPackageInstaller();
            PackageInstaller.Session session = null;
            try {
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(appContext.getPackageName());
                params.setSize(apk.length());
                int sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);
                try (OutputStream out = session.openWrite("nova-update.apk", 0, apk.length());
                     InputStream in = new FileInputStream(apk)) {
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                    session.fsync(out);
                }
                session.commit(buildStatusIntentSender(appContext).getIntentSender());
                session.close();
                session = null;
            } catch (Exception e) {
                log.warn("installUpdate failed", e);
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {
                        // best effort
                    }
                }
                postInstallResult(false, e.getMessage());
            }
        });
    }

    private PendingIntent buildStatusIntentSender(Context context) {
        Intent intent = new Intent(context, UpdateInstallReceiver.class);
        intent.setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS);
        intent.setPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    /**
     * Whether a caller is currently waiting for the install result. Used by
     * {@link UpdateInstallReceiver} to avoid showing a second Toast when the settings UI
     * is alive and will report the failure itself.
     */
    public boolean hasPendingInstallCallback() {
        return installCallback != null;
    }

    /** Called by {@link UpdateInstallReceiver} when the package installer reports a status. */
    public void deliverInstallResult(int status, String message) {
        postInstallResult(status == PackageInstaller.STATUS_SUCCESS, message);
    }

    private void postInstallResult(final boolean success, final String message) {
        final InstallCallback callback = installCallback;
        installCallback = null;
        if (callback != null) {
            mainHandler.post(() -> callback.onResult(success, message));
        }
    }

    private static File downloadDirectory(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, UPDATE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("downloadDirectory: could not create {}", dir);
        }
        return dir;
    }

    private void postDownloadError(final DownloadCallback callback, final String message) {
        mainHandler.post(() -> callback.onError(message));
    }
}
