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

package com.archos.mediacenter.video.utils.credentialsmanager;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import android.view.MenuItem;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.PrivateMode;


public class CredentialsManagerPreferenceActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean darkModeActive = prefs.getBoolean("dark_mode", false);
        if (PrivateMode.isActive()){
            setTheme(R.style.PrivateDarkBlueTheme);
        }else if (darkModeActive) {
            setTheme(R.style.DarkBlueTheme);
        } else {
            setTheme(R.style.ArchosThemeBlue);
        }
        setContentView(R.layout.credentials_manager_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportFragmentManager().beginTransaction().add(R.id.root,new CredentialsManagerPreferencesFragment()).commit();


        // Custom ActionBar title
        TextView tv = new TextView(this);
        tv.setText(getTitle()); // or set your own string
        tv.setTextSize(24); // Set your desired size
        tv.setTypeface(ResourcesCompat.getFont(this, R.font.nhaasgroteskdspro_95blk)); // Set your desired font
        tv.setTextColor(ContextCompat.getColor(this, R.color.green_accent)); // Set your desired color
        tv.setLayoutParams(new ActionBar.LayoutParams(
                ActionBar.LayoutParams.WRAP_CONTENT,
                ActionBar.LayoutParams.WRAP_CONTENT));
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(tv);

        // Horizontal offset of title
        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * getApplicationContext().getResources().getDisplayMetrics().density);
        tv.setTranslationX(dpToPx.apply(-16));
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
