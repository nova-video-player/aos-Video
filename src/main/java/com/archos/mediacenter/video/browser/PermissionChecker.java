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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStoreImportService;

/**
 * Created by alexandre on 16/09/15.
 */
public class PermissionChecker {
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
        if(!isDialogDisplayed&&ContextCompat.checkSelfPermission(mActivity,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
            mActivity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
            isDialogDisplayed = true;
        }
    }

    public boolean hasExternalPermission(Activity activity){
        mActivity = activity;
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return true;
        else
            return ContextCompat.checkSelfPermission(mActivity,android.Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }


    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults, Activity activity) {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return;
        mActivity = activity;
        if(isDialogDisplayed&&ContextCompat.checkSelfPermission(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED) {
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


        }
        else {
            // inform import service about the event
            Intent serviceIntent = new Intent(mActivity, VideoStoreImportService.class);
            serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_STORAGE_PERMISSION_GRANTED);
            mActivity.startService(serviceIntent);
            if(mListener!=null)
                mListener.onPermissionGranted();
            isDialogDisplayed = false;

        }
    }


}
