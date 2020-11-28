// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.leanback.collections;

import android.os.Bundle;
import android.transition.Slide;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import androidx.fragment.app.Fragment;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.LeanbackActivity;

public class CollectionActivity extends LeanbackActivity {

    public static final String SLIDE_TRANSITION_EXTRA = "slide_transition";
    public static final String SLIDE_DIRECTION_EXTRA = "slide_direction";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set the enter animation only when asked
        if (getIntent().getBooleanExtra(SLIDE_TRANSITION_EXTRA, false)) {
            int direction = getIntent().getIntExtra(SLIDE_DIRECTION_EXTRA, Gravity.RIGHT);
            getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);
            getWindow().setEnterTransition(new Slide(direction));
        }
        setContentView(R.layout.androidtv_collection_activity);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_browse_fragment);
                if (fragment instanceof CollectionFragment) {
                    ((CollectionFragment)fragment).onKeyDown(keyCode);
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }
}
