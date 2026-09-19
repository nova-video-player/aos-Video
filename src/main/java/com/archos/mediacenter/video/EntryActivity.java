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

package com.archos.mediacenter.video;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.leanback.MainActivityLeanback;
import com.archos.mediacenter.video.utils.ThemeManager;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Hub activity to launch either the leanback activity or the regular tablet/phone activity
 * depending on the user preferences
 * Created by vapillon on 08/06/15.
 */
public class EntryActivity extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(EntryActivity.class);
    private SharedPreferences.OnSharedPreferenceChangeListener mThemeChangeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate() to avoid visual flash
        ThemeManager themeManager = ThemeManager.getInstance(this);
        // EntryActivity uses launcher theme which is already neutral black
        // The actual theme will be set in the launched activity (MainActivity or MainActivityLeanback)
        ((CustomApplication) getApplication()).loadLocale();
        super.onCreate(savedInstanceState);
        
        // Apply window theme after super.onCreate()
        themeManager.applyWindowTheme(this);

        if (log.isDebugEnabled()) log.debug("onCreate");

        Class activityToLaunch = null;
        if (UiChoiceDialog.applicationIsInLeanbackMode(this)) {
            activityToLaunch = MainActivityLeanback.class;
        } else {
            activityToLaunch = MainActivity.class;
        }

        final Intent originIntent = getIntent();
        Intent i = new Intent();
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.setClass(this, activityToLaunch);

        if (originIntent.getData()!=null) {
            i.setData(originIntent.getData());
        }

        if (originIntent.getExtras()!=null) {
            i.putExtras(originIntent.getExtras());
        }

        startActivity(i);;

        finish();

        mThemeChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (VideoPreferencesCommon.KEY_APP_THEME.equals(key)) {
                    recreate();
                }
            }
        };
        ThemeManager.getInstance(this).registerThemeChangeListener(mThemeChangeListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((CustomApplication) getApplication()).loadLocale();
    }

    @Override
    protected void onDestroy() {
        if (mThemeChangeListener != null) {
            ThemeManager.getInstance(this).unregisterThemeChangeListener(mThemeChangeListener);
        }
        super.onDestroy();
    }

}
