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
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.leanback.MainActivityLeanback;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

import java.util.Locale;

/**
 * Hub activity to launch either the leanback activity or the regular tablet/phone activity
 * depending on the user preferences
 * Created by vapillon on 08/06/15.
 */
public class EntryActivity extends AppCompatActivity {

    private static final String TAG = "EntryActivity";
    private static boolean DBG = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        ((CustomApplication) getApplication()).loadLocale();

        super.onCreate(savedInstanceState);

        if (DBG) Log.d(TAG, "onCreate");

        Class activityToLaunch = null;
        if (UiChoiceDialog.applicationIsInLeanbackMode(this)) {
            activityToLaunch = MainActivityLeanback.class;
        } else {
            activityToLaunch = MainActivity.class;
        }

        final Intent originIntent = getIntent();
        Intent i = new Intent();
        i.setClass(this, activityToLaunch);

        if (originIntent.getData()!=null) {
            i.setData(originIntent.getData());
        }

        if (originIntent.getExtras()!=null) {
            i.putExtras(originIntent.getExtras());
        }

        startActivity(i);;

        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((CustomApplication) getApplication()).loadLocale();
    }

}
