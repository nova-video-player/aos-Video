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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainActivityLeanback extends LeanbackActivity {

    private static final Logger log = LoggerFactory.getLogger(MainActivityLeanback.class);

    public static final int ACTIVITY_REQUEST_CODE_PREFERENCES = 101;

    private String mCurrentUiModeLeanback;
    private PermissionChecker mPermissionChecker;
    private ActivityResultLauncher<Intent> mPreferencesLauncher;

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @Override
    public void onResumeFragments(){
        log.debug("onResumeFragments");
        super.onResumeFragments();
        CustomApplication.loadLocale(getResources());

        new DensityTweak(this)
                .applyUserDensity();
        mPermissionChecker.checkAndRequestPermission(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log.debug("onCreate");
        ((CustomApplication) getApplication()).loadLocale();
        super.onCreate(savedInstanceState);

        // Handle the return from VideoSettingsActivity, check if the UiMode has been changed or if the zoom dialog must be displayed
        mPreferencesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    int resultCode = result.getResultCode();
                    // Handle the result as in onActivityResult
                    if (resultCode == VideoPreferencesCommon.ACTIVITY_RESULT_UI_MODE_CHANGED) {
                        String newUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this)
                                .getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
                        if (!newUiModeLeanback.equals(mCurrentUiModeLeanback)) {
                            finish();
                            startActivity(new Intent(this, EntryActivity.class));
                        }
                        mCurrentUiModeLeanback = null;
                    } else if (resultCode == VideoPreferencesCommon.ACTIVITY_RESULT_UI_ZOOM_CHANGED) {
                        new DensityTweak(this).forceDensityDialogAtNextStart();
                        finish();
                        startActivity(new Intent(this, EntryActivity.class));
                    }
                }
        );

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
        log.debug("onDestroy");
        super.onDestroy();
        UnavailablePosterBroadcastReceiver.unregisterReceiver(this);
    }

    /**
     * This method is called from VideoViewClickedListener.
     * This is ugly I know. It's because VideoViewClickedListener has lost a lot of context...
     */
    public void startPreferencesActivity() {
        mPreferencesLauncher.launch(new Intent(this, VideoSettingsActivity.class));
        mCurrentUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
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
