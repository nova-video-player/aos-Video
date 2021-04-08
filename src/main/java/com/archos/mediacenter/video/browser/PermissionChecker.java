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

import android.annotation.TargetApi;
import android.app.Activity;
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

/**
 * Created by alexandre on 16/09/15.
 */
public class PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionChecker.class);

    private static final int PERMISSION_REQUEST = 1;
    private static PermissionChecker sPermissionChecker;
    Activity mActivity;

    public boolean isDialogDisplayed = false;

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
        if(Build.VERSION.SDK_INT<30) {
            if (!isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log.debug("checkAndRequestPermission: requesting WRITE_EXTERNAL_STORAGE");
                mActivity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
                isDialogDisplayed = true;
            }
        } else {
            log.debug("checkAndRequestPermission: is MANAGE_EXTERNAL_STORAGE granted? " + Environment.isExternalStorageManager());
            if (!isDialogDisplayed && !Environment.isExternalStorageManager()) {
                log.debug("checkAndRequestPermission: requesting MANAGE_EXTERNAL_STORAGE");
                mActivity.requestPermissions(new String[]{android.Manifest.permission.MANAGE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
                isDialogDisplayed = true;
            }
        }
    }

    public boolean hasExternalPermission(AppCompatActivity activity){
        mActivity = activity;
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return true;
        else {
            if(Build.VERSION.SDK_INT<30) {
                return ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            } else {
                return Environment.isExternalStorageManager();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults, Activity activity) {
        log.debug("onRequestPermissionsResult: requesting MANAGE_EXTERNAL_STORAGE");

        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return;
        mActivity = activity;
        if(Build.VERSION.SDK_INT<30) {
            if (isDialogDisplayed && ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                log.debug("onRequestPermissionsResult: MANAGE_EXTERNAL_STORAGE permission not granted");
                new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(R.string.error_permission_storage).setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // finish();
                                isDialogDisplayed = false;
                                if (!ActivityCompat.shouldShowRequestPermissionRationale(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                                    Intent in = new Intent();
                                    in.setAction("android.intent.action.MANAGE_APP_PERMISSIONS");
                                    in.putExtra("android.intent.extra.PACKAGE_NAME", mActivity.getPackageName());
                                    try {
                                        mActivity.startActivity(in);
                                    } catch (java.lang.SecurityException e) {
                                        // Create intent to start new activity
                                        in.setData(Uri.parse("package:" + mActivity.getPackageName()));
                                        in.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        // start new activity to display extended information
                                        mActivity.startActivity(in);
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
                log.debug("onRequestPermissionsResult: WRITE_EXTERNAL_STORAGE permission granted, launching scan");
                // inform import service about the event
                Intent serviceIntent = new Intent(mActivity, VideoStoreImportService.class);
                serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED);
                mActivity.startService(serviceIntent);
                if (mListener != null)
                    mListener.onPermissionGranted();
                isDialogDisplayed = false;
            }
        } else {
            if (isDialogDisplayed && ! Environment.isExternalStorageManager()) {
                log.debug("onRequestPermissionsResult: ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION permission not granted");
                new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(R.string.error_permission_all_file_access).setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // finish();
                                isDialogDisplayed = false;
                                if (!ActivityCompat.shouldShowRequestPermissionRationale(mActivity, android.Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
                                    Intent in = new Intent();
                                    in.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                    in.putExtra("android.intent.extra.PACKAGE_NAME", mActivity.getPackageName());
                                    try {
                                        mActivity.startActivity(in);
                                    } catch (java.lang.SecurityException e) {
                                        // Create intent to start new activity
                                        in.setData(Uri.parse("package:" + mActivity.getPackageName()));
                                        in.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        // start new activity to display extended information
                                        mActivity.startActivity(in);
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
                log.debug("onRequestPermissionsResult: ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION permission granted, launching scan");
                // inform import service about the event
                Intent serviceIntent = new Intent(mActivity, VideoStoreImportService.class);
                serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED);
                mActivity.startService(serviceIntent);
                if (mListener != null)
                    mListener.onPermissionGranted();
                isDialogDisplayed = false;
            }
        }
    }


}
