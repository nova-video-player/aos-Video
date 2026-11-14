// Copyright 2026 Nova Video Player Project
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
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.activity.result.ActivityResultLauncher;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.simkl.Simkl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimklSigninDialogPreference extends Preference {

    private static final Logger log = LoggerFactory.getLogger(SimklSigninDialogPreference.class);
    private static final int REQUEST_CODE_SIMKL_DEVICE_AUTH = 1002;

    private DialogInterface.OnDismissListener mOnDismissListener;
    private ActivityResultLauncher<Intent> mSimklAuthLauncher;

    public void setLauncher(ActivityResultLauncher<Intent> launcher) {
        mSimklAuthLauncher = launcher;
    }

    public void onAuthCompleted(boolean success) {
        if (success) {
            if (log.isDebugEnabled()) log.debug("onAuthCompleted: device auth successful");
            notifyChanged();
            if (mOnDismissListener != null) {
                mOnDismissListener.onDismiss(null);
            }
        } else {
            if (log.isDebugEnabled()) log.debug("onAuthCompleted: device auth cancelled or failed");
        }
    }

    public SimklSigninDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimklSigninDialogPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.setKey(Simkl.KEY_SIMKL_USER);
    }

    public SharedPreferences getSharedPreferences() {
        if (super.getSharedPreferences() == null)
            return PreferenceManager.getDefaultSharedPreferences(getContext());
        else
            return super.getSharedPreferences();
    }

    @Override
    protected void onClick() {
        performDeviceAuth();
    }

    public void performDeviceAuth() {
        if (log.isDebugEnabled()) log.debug("performDeviceAuth: SimklSigninDialogPreference clicked!");

        Activity activity = getActivityFromContext(getContext());
        if (activity == null) {
            log.error("performDeviceAuth: could not resolve Activity from context type {}", getContext() != null ? getContext().getClass().getName() : "null");
            return;
        }

        if (activity.isFinishing() || activity.isDestroyed()) {
            if (log.isDebugEnabled()) log.debug("performDeviceAuth: activity is finishing or destroyed");
            return;
        }

        if (log.isDebugEnabled()) log.debug("performDeviceAuth: launching SimklDeviceAuthActivity");

        Intent intent = new Intent(activity, SimklDeviceAuthActivity.class);
        if (mSimklAuthLauncher != null) {
            mSimklAuthLauncher.launch(intent);
        } else {
            launchLegacy(activity, intent);
        }
    }

    @SuppressWarnings("deprecation")
    private void launchLegacy(Activity activity, Intent intent) {
        activity.startActivityForResult(intent, REQUEST_CODE_SIMKL_DEVICE_AUTH);
    }

    @SuppressWarnings("deprecation")
    public void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == REQUEST_CODE_SIMKL_DEVICE_AUTH) {
            onAuthCompleted(resultCode == Activity.RESULT_OK);
        }
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener onDismissListener) {
        mOnDismissListener = onDismissListener;
    }

    private Activity getActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
