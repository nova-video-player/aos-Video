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

package com.archos.mediacenter.video.leanback.details;

import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.transition.Slide;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import androidx.fragment.app.Fragment;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.leanback.LeanbackActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoDetailsActivity extends LeanbackActivity {

    private static final Logger log = LoggerFactory.getLogger(VideoDetailsActivity.class);

    public static final String SHARED_ELEMENT_NAME = "hero";
    public static final String SLIDE_TRANSITION_EXTRA = "slide_transition";
    public static final String SLIDE_DIRECTION_EXTRA = "slide_direction";

    private void traceDetails(String event) {
        if (!log.isDebugEnabled()) return;
        long launchUptimeMs = getIntent().getLongExtra(
                VideoDetailsFragment.EXTRA_DETAILS_LAUNCH_UPTIME_MS, -1);
        String elapsed = launchUptimeMs >= 0
                ? String.valueOf(SystemClock.elapsedRealtime() - launchUptimeMs)
                : "n/a";
        log.debug("details timing: event={}, sinceTapMs={}", event, elapsed);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        traceDetails("activity-onCreate");

        // Lollipop only :-(
        getWindow().requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS);

        // Always set the exit transition because the "Next Episode" transition may be needed (we don't know yet)
        getWindow().setExitTransition(new Slide(Gravity.LEFT));

        // Set the enter animation only when asked (i.e. it is a "Next Episode" transition)
        if (getIntent().getBooleanExtra(SLIDE_TRANSITION_EXTRA, false)) {
            int direction = getIntent().getIntExtra(SLIDE_DIRECTION_EXTRA, Gravity.RIGHT);
            getWindow().setEnterTransition(new Slide(direction));
        }

        setContentView(R.layout.androidtv_details_activity);
    }

    public void onPause(){
        traceDetails("activity-onPause");
        super.onPause();
        if(getIntent().getBooleanExtra(VideoDetailsFragment.EXTRA_LAUNCHED_FROM_PLAYER, false))
            TorrentObserverService.paused(this);
    }
    public void onResume(){
        super.onResume();
        traceDetails("activity-onResume");
        if(getIntent().getBooleanExtra(VideoDetailsFragment.EXTRA_LAUNCHED_FROM_PLAYER, false))
            TorrentObserverService.resumed(this);
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
                if (fragment instanceof VideoDetailsFragment) {
                    ((VideoDetailsFragment)fragment).onKeyDown(keyCode);
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }
}
