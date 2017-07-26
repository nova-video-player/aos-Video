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

package com.archos.mediacenter.video.leanback.network;

import android.app.Fragment;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.SingleFragmentActivity;

public class NetworkRootActivity extends SingleFragmentActivity {
    @Override
    public Fragment getFragmentInstance() {
        return new NetworkRootFragment();
    }

    /**
     * Temp stuff to show the rescan button that is for now hidden (not ready for prime-time)
     * (to remove once re-scan feature is published)
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_DPAD_UP) {
            keyupCount++;
            if (keyupTrainStartTimeMs==0) {
                keyupTrainStartTimeMs = SystemClock.elapsedRealtime();
            }
            // Check for 5 press in less than 1 second
            if (keyupCount>4 && (SystemClock.elapsedRealtime()-keyupTrainStartTimeMs<1000)) {
                // display re-scan item
                Fragment f = getFragmentManager().findFragmentById(R.id.fragment_container);
                if (f!=null && f instanceof NetworkRootFragment) {
                    ((NetworkRootFragment)f).displayRescanItem();
                    Toast.makeText(this, "Displaying Re-scan item (caution it is Debug only for now)", Toast.LENGTH_SHORT).show();
                }
            }
        }
        else {
            // reset if another key is pressed
            keyupCount=0;
            keyupTrainStartTimeMs=0;
        }
        return super.onKeyDown(keyCode, event);
    }

    private int keyupCount = 0;
    private long keyupTrainStartTimeMs = 0;
}
