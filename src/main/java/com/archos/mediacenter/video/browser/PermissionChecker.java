// Copyright 2017 Archos SA
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

package com.archos.mediacenter.video.browser;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStoreImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import io.sentry.SentryLevel;

/**
 * Created by alexandre on 16/09/15.
 */
public class PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionChecker.class);

    private static boolean hasManageExternalStoragePermission = CustomApplication.isManageExternalStoragePermissionInManifest();

    private static final int PERM_REQ_RW = 1;
    private static final int PERM_REQ_MANAGE = 2;

    private static PermissionChecker sPermissionChecker;
    Activity mActivity;

    public boolean isDialogDisplayed = false;

    public PermissionChecker(boolean hasPermission) {
        hasManageExternalStoragePermission = hasPermission;
    }

    // Note: this activity exists on API>=31 for phones and API>=32 for TVs
    static boolean activityToRequestManageStorageExists = false;

    public interface PermissionListener{
        public void onPermissionGranted();
    }
    PermissionListener mListener;

    public void setListener(PermissionListener listener){
        mListener = listener;
    }

    /**
     * will create checker only when permission isn't granted ?
     * @param activity
     */

    @TargetApi(Build.VERSION_CODES.M)
    public void checkAndRequestPermission(Activity activity) {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return;

        mActivity= activity;

        log.debug("checkAndRequestPermission: hasManageExternalStoragePermission=" + hasManageExternalStoragePermission + ", isDialogDisplayed=" + isDialogDisplayed);

        // MANAGE_EXTERNAL_STORAGE is supported for API>=30, needs to be granted by user on API>=31 via android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION that does not exist on Android TV until API>=34
        // WRITE_EXTERNAL_STORAGE is maxSdkVersion 29, auto-granted API<=22, requires explicit user permission 23<=API<=32, i.e. not required to be in manifest 30<=API<=32 but programmatically requested
        // READ_EXTERNAL_STORAGE is maxSdkVersion 32, auto-granted API<=22, requires explicit user permission 23<=API<=32
        // for API>=33 instead of using WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE, more granularity is required and READ_MEDIA_(VIDEO|AUDIO|IMAGE) need to be requested when MANAGE_EXTERNAL_STORAGE is not used
        // for API>=33, POST_NOTIFICATIONS permission is also required
        // Environment.isExternalStorageManager() used to check MANAGE_EXTERNAL_STORAGE is for API>=30
        // for API>=34 FOREGROUND_SERVICE_DATA_SYNC and FOREGROUND_SERVICE_MEDIA_PLAYBACK are required

        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + mActivity.getPackageName()));
        activityToRequestManageStorageExists = intent.resolveActivity(mActivity.getPackageManager()) != null;
        log.debug("checkAndRequestPermission: hasManageExternalStoragePermissionactivityToRequestManageStorageExists=" + activityToRequestManageStorageExists);

        if(Build.VERSION.SDK_INT >= 30 && hasManageExternalStoragePermission && activityToRequestManageStorageExists) { // MANAGE_EXTERNAL_STORAGE has it all and is introduced in API>=31 except for TV
            log.debug("checkAndRequestPermission: is MANAGE_EXTERNAL_STORAGE granted? " + Environment.isExternalStorageManager());
            if (!isDialogDisplayed && !Environment.isExternalStorageManager()) {
                log.debug("checkAndRequestPermission: requesting MANAGE_EXTERNAL_STORAGE");
                if(Build.VERSION.SDK_INT >= 34) { // Need FOREGROUND_SERVICE_DATA_* too
                    ActivityCompat.requestPermissions(
                            activity,
                            new String[]{ // MANAGE_EXTERNAL_STORAGE provides READ thus no need to ask both except for legacy code?
                                    Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                                    Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
                            },
                            PERM_REQ_MANAGE
                    );
                    isDialogDisplayed = true;
                } else {
                    if (Build.VERSION.SDK_INT >= 33) { // Need POST_NOTIFICATIONS too
                        ActivityCompat.requestPermissions(
                                activity,
                                new String[]{ // MANAGE_EXTERNAL_STORAGE provides READ thus no need to ask both except for legacy code?
                                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                        Manifest.permission.RECORD_AUDIO
                                },
                                PERM_REQ_MANAGE
                        );
                        isDialogDisplayed = true;
                    } else {
                        ActivityCompat.requestPermissions(
                                activity,
                                new String[]{ // MANAGE_EXTERNAL_STORAGE provides READ thus no need to ask both except for legacy code?
                                        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                                        Manifest.permission.RECORD_AUDIO
                                },
                                PERM_REQ_MANAGE
                        );
                        isDialogDisplayed = true;
                    }
                }
            }
        } else {

            if (Build.VERSION.SDK_INT >= 34) { // API>=33 no WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE needs extra media granularity
                if (!isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                    log.debug("checkAndRequestPermission: API>=33 requesting READ_MEDIA_VIDEO");
                    ActivityCompat.requestPermissions(
                            activity,
                            new String[]{
                                    Manifest.permission.READ_MEDIA_VIDEO,
                                    Manifest.permission.READ_MEDIA_AUDIO,
                                    Manifest.permission.READ_MEDIA_IMAGES,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC,
                                    Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
                            },
                            PERM_REQ_RW
                    );
                    isDialogDisplayed = true;
                }
            } else {
                if (Build.VERSION.SDK_INT >= 33) { // API>=33 no WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE needs extra media granularity
                    if (!isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                        log.debug("checkAndRequestPermission: API>=33 requesting READ_MEDIA_VIDEO");
                        ActivityCompat.requestPermissions(
                                activity,
                                new String[]{
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_AUDIO,
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                        Manifest.permission.RECORD_AUDIO
                                },
                                PERM_REQ_RW
                        );
                        isDialogDisplayed = true;
                    }
                } else {
                    if (Build.VERSION.SDK_INT <= 29) { // 23<=API<=29 WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE auto-granted (READ should be auto-granted via WRITE)
                        if (!isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            log.debug("checkAndRequestPermission: 23<=API<30 requesting (READ|WRITE)_EXTERNAL_STORAGE");
                            ActivityCompat.requestPermissions(
                                    activity,
                                    new String[]{
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            Manifest.permission.RECORD_AUDIO
                                    },
                                    PERM_REQ_RW
                            );
                            isDialogDisplayed = true;
                        }
                    } else { // 30<=API<33 WRITE_EXTERNAL_STORAGE does nothing and is not in manifest thus only READ_EXTERNAL_STORAGE
                        if (!isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            log.debug("checkAndRequestPermission: 30<=API<33 requesting READ_EXTERNAL_STORAGE");
                            ActivityCompat.requestPermissions(
                                    activity,
                                    new String[]{
                                            Manifest.permission.READ_EXTERNAL_STORAGE,
                                            Manifest.permission.RECORD_AUDIO
                                    },
                                    PERM_REQ_RW
                            );
                            isDialogDisplayed = true;
                        }
                    }
                }
            }
        }
    }

    public boolean hasExternalPermission(AppCompatActivity activity){
          mActivity = activity;
        boolean result = false;
        if(Build.VERSION.SDK_INT <= 22) { // API<=22
            log.debug("hasExternalPermission: API<=22 -> true");
            return true;
        } else {
            if (Build.VERSION.SDK_INT >= 30 && hasManageExternalStoragePermission && activityToRequestManageStorageExists) { // this is already the case
                result = Environment.isExternalStorageManager();
                log.debug("hasExternalPermission: API>=30 and hasManagedExternalStoragePermission -> " + result);
                return result;
            } else {
                // Good reading https://stackoverflow.com/questions/64221188/write-external-storage-when-targeting-android-10
                if (Build.VERSION.SDK_INT >= 33) { // API>=33
                    result = ContextCompat.checkSelfPermission(mActivity, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
                    log.debug("hasExternalPermission: API>=33 READ_MEDIA_VIDEO -> " + result);
                    return result;
                } else {
                    if (Build.VERSION.SDK_INT <= 29) { // 23<=API<=29
                        result = ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                        log.debug("hasExternalPermission: 23<=API<=29 WRITE_EXTERNAL_STORAGE -> " + result);
                        return result;
                    } else { // API 30<=API<=32
                        result = ContextCompat.checkSelfPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                        log.debug("hasExternalPermission: 30<=API<=32 READ_EXTERNAL_STORAGE -> " + result);
                        return result;
                    }
                }
            }
        }
    }

    static String permissionToRequest = "";
    static int errorMessage = 0;
    static String action = "";

    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults, Activity activity) {
        // grantResults 0 for granted -1 for denied
        log.debug("onRequestPermissionsResult: requestCode " + requestCode +
                ", permissions " + Arrays.toString(permissions) +
                ", grantResults " + Arrays.toString(grantResults));
        mActivity = activity;
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return;
        boolean isGranted = false;
        if (grantResults != null) {
            isGranted = true;
        }
        for (int i = 0; i < permissions.length; i++) {
            // RECORD_AUDIO is optional
            isGranted = isGranted && (grantResults[i] == PackageManager.PERMISSION_GRANTED || permissions[i].equals(Manifest.permission.RECORD_AUDIO));
            if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                log.warn("onRequestPermissionsResult: permission " + permissions[i] + " not granted");
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                log.debug("onRequestPermissionsResult: permission " + permissions[i] + " granted");
        }
        log.debug("onRequestPermissionsResult: isGranted " + isGranted);
        errorMessage = R.string.error; // be sure it is initialized
        switch (requestCode) {
            case PERM_REQ_RW:
                if (Build.VERSION.SDK_INT >= 33) { // API>=33
                    log.debug("onRequestPermissionsResult: PERM_REQ_RW API>=33 request READ_MEDIA_VIDEO");
                    permissionToRequest = Manifest.permission.READ_MEDIA_VIDEO;
                    action = "android.intent.action.MANAGE_APP_PERMISSIONS";
                    errorMessage = R.string.error_permission_storage;
                } else {
                    if (Build.VERSION.SDK_INT <= 29) { // API<=29
                        // between 23<=API<=29 WRITE_EXTERNAL_STORAGE grants READ_EXTERNAL_STORAGE
                        log.debug("onRequestPermissionsResult: PERM_REQ_RW 23<=API<30 request WRITE_EXTERNAL_STORAGE");
                        permissionToRequest = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
                        action = "android.intent.action.MANAGE_APP_PERMISSIONS";
                        errorMessage = R.string.error_permission_storage;
                    } else { // 30<=API<=32
                        // between 30<=API<=32 only READ_EXTERNAL_STORAGE is possible
                        log.debug("onRequestPermissionsResult: PERM_REQ_RW 30<=API<33 request READ_EXTERNAL_STORAGE");
                        permissionToRequest = android.Manifest.permission.READ_EXTERNAL_STORAGE;
                        action = "android.intent.action.MANAGE_APP_PERMISSIONS";
                        errorMessage = R.string.error_permission_storage;
                    }
                }
                break;
            case PERM_REQ_MANAGE:
                log.debug("configuring PERM_REQ_MANAGE");
                if(Build.VERSION.SDK_INT>=30 && hasManageExternalStoragePermission && activityToRequestManageStorageExists) { // no need to request MANAGE_EXTERNAL_STORAGE on API=30 it is auto-granted when declared in AndroidManifest but do it for consistency
                    permissionToRequest = android.Manifest.permission.MANAGE_EXTERNAL_STORAGE;
                    action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
                    errorMessage = R.string.error_permission_all_file_access;
                }
                break;
        }
        if (! isGranted) {
            // launch activity dialog to request
            new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(errorMessage).setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // finish();
                            isDialogDisplayed = false;
                            log.debug("onRequestPermissionsResult: requesting permission " + permissionToRequest + " with intent action " + action);
                            if (!ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permissionToRequest)) {
                                log.debug("onRequestPermissionsResult: packageName=" + mActivity.getPackageName());
                                // try/catch dance: android dev documentation says these intents/activities are there since dawn of age but
                                // "In some cases, a matching Activity may not exist, so ensure you safeguard against this."
                                try {
                                    Intent intent = new Intent(action, Uri.parse("package:" + mActivity.getPackageName()));
                                    if (intent.resolveActivity(mActivity.getPackageManager()) == null)
                                        log.warn("onRequestPermissionsResult: intent not callable for action=" + action);
                                    mActivity.startActivity(intent);
                                } catch (SecurityException | ActivityNotFoundException e) {
                                    if (log.isDebugEnabled()) log.warn("onRequestPermissionsResult: caught exception trying ACTION_APPLICATION_DETAILS_SETTINGS", e);
                                    try {
                                        // start new activity to display extended information
                                        mActivity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + mActivity.getPackageName()))); // since API9
                                    } catch (ActivityNotFoundException eanf) {
                                        // ACTION_APPLICATION_DETAILS_SETTINGS does not exist on Android 6 TCL/SMART_TV or Android 7 MiTV4A and other obscure RK
                                        // cf. https://sentry.io/organizations/nova-video-player/issues/3697512720
                                        log.warn("onRequestPermissionsResult: caught exception, no Activity found to handle intent ACTION_APPLICATION_DETAILS_SETTINGS trying ACTION_MANAGE_APPLICATIONS_SETTINGS", eanf);
                                        try {
                                            mActivity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)); // since API3
                                        } catch (ActivityNotFoundException eanf2) {
                                            log.warn("onRequestPermissionsResult: caught exception, no Activity found to handle intent ACTION_MANAGE_APPLICATIONS_SETTINGS trying ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS", eanf2);
                                            try {
                                                mActivity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)); // since API9
                                            } catch (ActivityNotFoundException eanf3) {
                                                log.warn("onRequestPermissionsResult: caught exception, no Activity found to handle intent ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS trying ACTION_APPLICATION_SETTINGS", eanf3);
                                                try {
                                                    mActivity.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)); // since API1
                                                } catch (ActivityNotFoundException eanf4) {
                                                    log.warn("onRequestPermissionsResult: caught exception, no Activity found to handle intent ACTION_APPLICATION_SETTINGS: we are out of option...", eanf4);
                                                }
                                            }
                                        }
                                    }
                                }
                            } else checkAndRequestPermission(mActivity);
                        }
                    }
            ).setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            android.os.Process.killProcess(android.os.Process.myPid());
                        }
                    }
            ).setCancelable(false).show();
        } else {
            log.debug("onRequestPermissionsResult: permission granted, launch scan");
            launchScan();
        }
    }

    public void launchScan() {
        log.debug("launchScan: launching scan");
        // inform import service about the event
        Intent serviceIntent = new Intent(mActivity, VideoStoreImportService.class);
        ArchosUtils.addBreadcrumb(SentryLevel.INFO, "PermissionChecker.launchScan", "intent VideoStoreImportService action ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED");
        log.debug("launchScan: PermissionChecker.launchScan intent VideoStoreImportService action ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED");
        serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED);
        mActivity.startService(serviceIntent);
        if (mListener != null)
            mListener.onPermissionGranted();
        isDialogDisplayed = false;
    }
}
