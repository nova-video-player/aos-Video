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

package com.archos.mediacenter.video.leanback;

import static com.archos.filecorelibrary.FileUtils.hasManageExternalStoragePermission;

import android.content.Intent;

import android.os.Build;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import android.view.KeyEvent;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.DensityTweak;
import com.archos.mediacenter.video.EntryActivity;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.browser.PermissionChecker;
import com.archos.mediacenter.video.leanback.settings.VideoSettingsActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.leanback.channels.ChannelManager;


import com.archos.mediascraper.AutoScrapeService;
import com.archos.environment.ArchosUtils;

public class MainActivityLeanback extends LeanbackActivity {

    public static final int ACTIVITY_REQUEST_CODE_PREFERENCES = 101;

    private String mCurrentUiModeLeanback;
    private PermissionChecker mPermissionChecker;

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @Override
    public void onResumeFragments(){
        super.onResumeFragments();
        new DensityTweak(this)
                .applyUserDensity();
        mPermissionChecker.checkAndRequestPermission(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UnavailablePosterBroadcastReceiver.registerReceiver(this);
        mPermissionChecker = new PermissionChecker(hasManageExternalStoragePermission(getApplicationContext()));
        new DensityTweak(this)
                .applyUserDensity()
                .showDensityChoiceIfNeeded();

        setContentView(R.layout.androidtv_root_activity);
        AutoScrapeService.registerObserver(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ChannelManager.refreshChannels(this);
        else {
            Intent intent = new Intent(BootupRecommandationService.UPDATE_ACTION);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent);
        }
        CustomApplication.showChangelogDialog(CustomApplication.getChangelog(this.getApplicationContext()), this);
    }


    @Override
    protected void onDestroy(){
        super.onDestroy();
        UnavailablePosterBroadcastReceiver.unregisterReceiver(this);
    }

    /**
     * This method is called from VideoViewClickedListener.
     * This is ugly I know. It's because VideoViewClickedListener has lost a lot of context...
     */
    public void startPreferencesActivity() {
        startActivityForResult(new Intent(this, VideoSettingsActivity.class), ACTIVITY_REQUEST_CODE_PREFERENCES);
        // Save the uimode_leanback to check if it changed when back from preferences
        mCurrentUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this).getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
    }

    /**
     * Handle the return from VideoSettingsActivity, check if the UiMode has been changed or if
     * the zoom dialog must be displayed
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Preference activity sets RESULT_OK if something need to be checked when back
        if (requestCode == ACTIVITY_REQUEST_CODE_PREFERENCES) {
            if (resultCode == VideoPreferencesCommon.ACTIVITY_RESULT_UI_MODE_CHANGED) {
                // Check if the UI mode changed
                String newUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this).getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
                if (!newUiModeLeanback.equals(mCurrentUiModeLeanback)) {
                    // ui mode changed -> quit the current activity and restart
                    finish();
                    startActivity(new Intent(this, EntryActivity.class));
                }
                mCurrentUiModeLeanback = null; // reset
            } else if (resultCode == VideoPreferencesCommon.ACTIVITY_RESULT_UI_ZOOM_CHANGED) {
                new DensityTweak(this)
                        .forceDensityDialogAtNextStart();
                // restart the leanback activity for user to change the zoom
                finish();
                startActivity(new Intent(this, EntryActivity.class));
            }
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            startPreferencesActivity();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }
}
