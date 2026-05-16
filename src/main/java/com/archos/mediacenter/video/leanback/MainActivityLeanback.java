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
import android.content.SharedPreferences;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

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
import com.archos.mediacenter.video.utils.ThemeManager;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.leanback.channels.ChannelManager;
import com.archos.mediaprovider.video.LoaderUtils;

import com.archos.mediascraper.AutoScrapeService;
import com.archos.environment.ArchosUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainActivityLeanback extends LeanbackActivity {

    private static final Logger log = LoggerFactory.getLogger(MainActivityLeanback.class);

    public static final int ACTIVITY_REQUEST_CODE_PREFERENCES = 101;

    private String mCurrentUiModeLeanback;
    private PermissionChecker mPermissionChecker;
    private SharedPreferences.OnSharedPreferenceChangeListener mThemeChangeListener;

    private final ActivityResultLauncher<Intent> preferencesLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == VideoPreferencesCommon.ACTIVITY_RESULT_UI_MODE_CHANGED) {
                    String newUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this)
                            .getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
                    if (!newUiModeLeanback.equals(mCurrentUiModeLeanback)) {
                        finish();
                        startActivity(new Intent(this, EntryActivity.class));
                    }
                    mCurrentUiModeLeanback = null;
                } else if (result.getResultCode() == VideoPreferencesCommon.ACTIVITY_RESULT_UI_ZOOM_CHANGED) {
                    new DensityTweak(this).forceDensityDialogAtNextStart();
                    finish();
                    startActivity(new Intent(this, EntryActivity.class));
                } else if (result.getResultCode() == VideoPreferencesCommon.ACTIVITY_RESULT_THEME_CHANGED) {
                    ThemeManager.getInstance(this).applyWindowTheme(this);
                    MainFragment mainFragment = (MainFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.main_browse_fragment);
                    if (mainFragment != null) {
                        mainFragment.refreshTheme();
                    }
                }
            });

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
    @Override
    public void onResumeFragments(){
        if (log.isDebugEnabled()) log.debug("onResumeFragments");
        super.onResumeFragments();
        CustomApplication.loadLocale(getResources());

        new DensityTweak(this)
                .applyUserDensity();
        mPermissionChecker.checkAndRequestPermission(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        log.warn("onCreate: MainActivityLeanback instance created: {}", this.hashCode());
        
        // Apply correct theme before super.onCreate() to avoid visual flash
        ThemeManager themeManager = ThemeManager.getInstance(this);
        if (themeManager.isBlackTheme()) {
            setTheme(R.style.MyLeanbackTheme_Black);
        }
        
        ((CustomApplication) getApplication()).loadLocale();

        super.onCreate(savedInstanceState);

        // Check if user disabled "Always start in TV interface" - if so, redirect to phone UI
        if (!UiChoiceDialog.applicationIsInLeanbackMode(this)) {
            if (log.isDebugEnabled()) log.debug("onCreate: User disabled leanback mode, redirecting to MainActivity");
            Intent i = new Intent(this, com.archos.mediacenter.video.browser.MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (getIntent().getData() != null) {
                i.setData(getIntent().getData());
            }
            if (getIntent().getExtras() != null) {
                i.putExtras(getIntent().getExtras());
            }
            startActivity(i);
            finish();
            return;
        }

        // Batch all SharedPreferences operations into single apply
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        // Update uimode/uimode_leanback to reflect we're in leanback mode
        UiChoiceDialog.updateUiModePreferencesInEditor(this, editor);

        // If we are starting the Browser again, we aren't unpausing a Video
        editor.putBoolean("user_paused_video", false);

        // Apply all changes at once
        editor.apply();

        // Setup preferences before we start activities - read after batch write
        LoaderUtils.mMustHideWatchedVideo = prefs.getBoolean("hide_watched", false);
        LoaderUtils.mSmartRecentlyRows = prefs.getBoolean("smart_recently_rows", false);

        UnavailablePosterBroadcastReceiver.registerReceiver(this);
        mPermissionChecker = new PermissionChecker(hasManageExternalStoragePermission(getApplicationContext()));
        new DensityTweak(this)
                .applyUserDensity()
                .showDensityChoiceIfNeeded();

        setContentView(R.layout.androidtv_root_activity);
        AutoScrapeService.registerObserver(this);

        // Register theme change listener to update window immediately when theme changes
        mThemeChangeListener = (sharedPreferences, key) -> {
            if (VideoPreferencesCommon.KEY_APP_THEME.equals(key)) {
                // Apply theme immediately - this updates the window visible behind translucent settings
                ThemeManager.getInstance(this).applyWindowTheme(this);
            }
        };
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(mThemeChangeListener);

        // Defer heavy database operations until after window is visible
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ChannelManager.refreshChannels(MainActivityLeanback.this);
            else {
                Intent intent = new Intent(BootupRecommandationService.UPDATE_ACTION);
                intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                sendBroadcast(intent);
            }
        });

        CustomApplication.showChangelogDialog(CustomApplication.getChangelog(this.getApplicationContext()), this);
    }


    @Override
    protected void onDestroy(){
        log.warn("onDestroy: MainActivityLeanback instance destroyed: {}", this.hashCode());
        // Unregister theme change listener
        if (mThemeChangeListener != null) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .unregisterOnSharedPreferenceChangeListener(mThemeChangeListener);
        }
        UnavailablePosterBroadcastReceiver.unregisterReceiver(this);
        super.onDestroy();
    }

    /**
     * This method is called from VideoViewClickedListener.
     * This is ugly I know. It's because VideoViewClickedListener has lost a lot of context...
     */
    public void startPreferencesActivity() {
        // Save the uimode_leanback to check if it changed when back from preferences
        mCurrentUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this).getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
        preferencesLauncher.launch(new Intent(this, VideoSettingsActivity.class));
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
