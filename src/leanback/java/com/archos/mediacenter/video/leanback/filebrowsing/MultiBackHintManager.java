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

package com.archos.mediacenter.video.leanback.filebrowsing;

import android.content.Context;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.archos.mediacenter.video.R;

/**
 * Singleton handling the display of a toast when user quickly press several times on BACK key
 * Created by vapillon on 10/06/15.
 */
public class MultiBackHintManager {

    private final static String HIDE_HINT_PREFERENCE_KEY = "MultiBackHintManager_hide";
    private final static int DETECTION_PERIOD = 2000; // 2 seconds

    final Context mContext;
    private long mPreviousPressTime = 0; // press -1
    private long mPreviousPreviousPressTime = 0; // press -2

    static MultiBackHintManager sSingleton = null;

    /** static to avoid reading the Preference each time */
    static boolean sHideHint = false;

    static public MultiBackHintManager getInstance(Context context) {
        if (sSingleton!=null) {
            return sSingleton;
        }
        sSingleton = new MultiBackHintManager(context.getApplicationContext()); // use Application context to avoid activity leak
        return sSingleton;
    }

    private MultiBackHintManager(Context context) {
        mContext = context;
        sHideHint = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(HIDE_HINT_PREFERENCE_KEY, sHideHint);
    }

    /** Must be called each time a BACK press is detected */
    public void onBackPressed() {
        long now = SystemClock.elapsedRealtime();
        if (!sHideHint && (now - mPreviousPreviousPressTime < DETECTION_PERIOD)) {
            Toast.makeText(mContext, R.string.leanback_hint_long_press_on_back, Toast.LENGTH_SHORT).show();
            // reset timings to not have toast twice when pressing back 4 times
            mPreviousPressTime = mPreviousPreviousPressTime = 0;
        }
        mPreviousPreviousPressTime = mPreviousPressTime;
        mPreviousPressTime = now;
    }

    /** Must be called each time a BACK long press is detected */
    public void onBackLongPressed() {
        if (!sHideHint) {
            // keep a static to avoid reading the Preference each time
            sHideHint = true;
            // No need to display anymore once the user found out
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(HIDE_HINT_PREFERENCE_KEY, sHideHint).apply();
        }
    }
}
