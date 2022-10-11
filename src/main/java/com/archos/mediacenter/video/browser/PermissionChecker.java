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
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStoreImportService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Created by alexandre on 16/09/15.
 */
public class PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionChecker.class);

    private static boolean hasManageExternalStoragePermission = false;

    private static final int PERM_REQ_RW = 1;
    private static final int PERM_REQ_MANAGE = 2;

    private static PermissionChecker sPermissionChecker;
    Activity mActivity;

    public boolean isDialogDisplayed = false;

    public PermissionChecker(boolean hasPermission) {
        hasManageExternalStoragePermission = hasPermission;
    }

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

        log.debug("checkAndRequestPermission: hasManageExternalStoragePermission=" + hasManageExternalStoragePermission);

        if(Build.VERSION.SDK_INT>29 && hasManageExternalStoragePermission) {
            log.debug("checkAndRequestPermission: is MANAGE_EXTERNAL_STORAGE granted? " + Environment.isExternalStorageManager());
            if (!isDialogDisplayed && !Environment.isExternalStorageManager()) {
                log.debug("checkAndRequestPermission: requesting MANAGE_EXTERNAL_STORAGE");
                ActivityCompat.requestPermissions(
                        activity,
                        new String[] { // MANAGE_EXTERNAL_STORAGE provides READ thus no need to ask both except for legacy code?
                                //Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO
                        },
                        PERM_REQ_MANAGE
                );
                isDialogDisplayed = true;
            }
        } else {
            if (!isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log.debug("checkAndRequestPermission: requesting WRITE_EXTERNAL_STORAGE");
                ActivityCompat.requestPermissions(
                        activity,
                        new String[] {
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO
                        },
                        PERM_REQ_RW
                );
                isDialogDisplayed = true;
            }
        }
    }

    public boolean hasExternalPermission(AppCompatActivity activity){
        mActivity = activity;
        boolean result = false;
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M) {
            log.debug("hasExternalPermission: API<23 -> true");
            return true;
        } else {
            if (Build.VERSION.SDK_INT>29 && hasManageExternalStoragePermission) { // this is already the case
                result = Environment.isExternalStorageManager();
                log.debug("hasExternalPermission: API>29 -> " + result);
                return result;
            } else {
                result = ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                log.debug("hasExternalPermission: 22<API<30 -> " + result);
                return result;
            }
        }
    }

    static String permissionToRequest = "";
    static int errorMessage = 0;
    static String action = "";
    static Intent intent;

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
            for (int result:grantResults)
                isGranted = isGranted && (result == PackageManager.PERMISSION_GRANTED);
        }

        log.debug("onRequestPermissionsResult: isGranted " + isGranted);

        switch (requestCode) {
            case PERM_REQ_RW:
                log.debug("configuring PERM_REQ_RW");
                permissionToRequest = android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
                action = "android.intent.action.MANAGE_APP_PERMISSIONS";
                errorMessage = R.string.error_permission_storage;
                intent = new Intent();
                ;;
            case PERM_REQ_MANAGE:
                log.debug("configuring PERM_REQ_MANAGE");
                if(Build.VERSION.SDK_INT>29 && hasManageExternalStoragePermission) {
                    permissionToRequest = android.Manifest.permission.MANAGE_EXTERNAL_STORAGE;
                    action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
                    errorMessage = R.string.error_permission_all_file_access;
                }
                ;;
        }
        if (! isGranted) {
            // launch activity dialog to request
            new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(errorMessage).setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // finish();
                            isDialogDisplayed = false;
                            if (!ActivityCompat.shouldShowRequestPermissionRationale(mActivity, permissionToRequest)) {
                                log.debug("onRequestPermissionsResult: packageName=" + mActivity.getPackageName());
                                try {
                                    mActivity.startActivity(new Intent(action, Uri.parse("package:" + mActivity.getPackageName())));
                                } catch (SecurityException | ActivityNotFoundException e) {
                                    if (log.isDebugEnabled()) log.warn("onRequestPermissionsResult: caught exception", e);
                                    // start new activity to display extended information
                                    mActivity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + mActivity.getPackageName())));
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
            launchScan();
        }
    }

    public void launchScan() {
        log.debug("launchScan: launching scan");
        // inform import service about the event
        Intent serviceIntent = new Intent(mActivity, VideoStoreImportService.class);
        serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED);
        mActivity.startService(serviceIntent);
        if (mListener != null)
            mListener.onPermissionGranted();
        isDialogDisplayed = false;
    }
}
