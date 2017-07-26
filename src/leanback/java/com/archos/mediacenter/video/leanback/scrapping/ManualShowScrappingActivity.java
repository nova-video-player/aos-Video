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

package com.archos.mediacenter.video.leanback.scrapping;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v17.leanback.app.SearchFragment;
import android.view.KeyEvent;

import com.archos.mediacenter.video.R;

public class ManualShowScrappingActivity extends Activity {

    public static final String EXTRA_TVSHOW_ID = "TVSHOW_ID";
    public static final String EXTRA_TVSHOW_NAME = "TVSHOW_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leanback_manual_show_scrapping_activity);
    }

    /**
     * Catch the SEARCH key so that the general Android TV search is not launched.
     * Try to relaunch the show name search instead
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
            Fragment f = getFragmentManager().findFragmentById(R.id.fragment);
            if (f instanceof SearchFragment) {
                ((SearchFragment)f).startRecognition();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event); // default
    }
}
