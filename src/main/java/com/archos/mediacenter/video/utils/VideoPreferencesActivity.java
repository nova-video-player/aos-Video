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

package com.archos.mediacenter.video.utils;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.view.MenuItem;
import android.view.View;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;

public class VideoPreferencesActivity extends AppCompatActivity {

	final public static String ALLOW_3RD_PARTY_PLAYER = "allow_3rd_party_player";
	final public static boolean ALLOW_3RD_PARTY_PLAYER_DEFAULT = false;

    final public static String FOLDER_BROWSING_DEFAULT_FOLDER = "folder_browsing_default_folder";
    final public static String FOLDER_BROWSING_DEFAULT_FOLDER_DEFAULT = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath();
    public final static int    FOLDER_PICKER_REQUEST_CODE=2;
    public static final String EXTRA_LAUNCH_INAPP_PURCHASE = "extra_launch_inapp_purchase";

    private static VideoPreferencesActivity mInstanceActivity;
    public static VideoPreferencesActivity getmInstanceActivity() {
        return mInstanceActivity;
    }

    public void setDarkMode() {
        setTheme(R.style.DarkBlueTheme);
    }

    public void setNormalMode() {
        setTheme(R.style.ArchosThemeBlue);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean darkModeActive = prefs.getBoolean("dark_mode", false);
        if (darkModeActive) {
            setDarkMode();
        } else {
            setNormalMode();
        }
        setContentView(R.layout.preferences_video);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        mInstanceActivity = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mInstanceActivity = null;
    }

    public void videoPreferenceOsClick(View v) {
        //remove because not appropriate links displayed
        //WebUtils.openWebLink(this,"https://www.opensubtitles.org");
    }
    public void videoPreferenceTmdbClick(View v) {
        // Breaks AndroidTV acceptance: text is cut on edges
        //WebUtils.openWebLink(this,"https://www.themoviedb.org");
    }
    public void videoPreferenceTvdbClick(View v) {
        // Breaks AndroidTV acceptance: contains non fullscreen ads
        //WebUtils.openWebLink(this,"https://thetvdb.com");
    }
    public void videoPreferenceTraktClick(View v) {
        WebUtils.openWebLink(this,"https://trakt.tv/");
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(getSupportFragmentManager().findFragmentById(R.id.preferencesFragment)!=null){
            ((VideoPreferencesFragment)getSupportFragmentManager().findFragmentById(R.id.preferencesFragment)).onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case android.R.id.home:
                    onBackPressed();
                break;
        }
        return ret;
    }

}
