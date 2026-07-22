// Copyright 2021 Courville Software
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

import android.view.KeyEvent;
import androidx.fragment.app.Fragment;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.SingleFragmentActivity;

public class AllAnimeCollectionsGridActivity extends SingleFragmentActivity {
    @Override
    public Fragment getFragmentInstance() {
        return new AllAnimeCollectionsGridFragment();
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
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (fragment instanceof AllAnimeCollectionsGridFragment) {
                    ((AllAnimeCollectionsGridFragment)fragment).onKeyDown(keyCode);
                    return true;
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }
}
