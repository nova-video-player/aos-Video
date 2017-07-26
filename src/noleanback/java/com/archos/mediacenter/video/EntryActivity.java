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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.archos.mediacenter.video.browser.MainActivity;

/**
 * Created by vapillon on 08/06/15.
 */
public class EntryActivity extends Activity {

    private static final String TAG = "EntryActivity";

    /**
     * @return true if this APK build integrate the leanback UI
     */
    public static boolean isLeanbackUiAvailable() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        final Intent originIntent = getIntent();
        Intent i = new Intent();
        i.setClass(this, MainActivity.class);

        if (originIntent.getData()!=null) {
            i.setData(originIntent.getData());
        }

        if (originIntent.getExtras()!=null) {
            i.putExtras(originIntent.getExtras());
        }

        startActivity(i);;

        finish();
    }
}
