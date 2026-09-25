// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.player.tvmenu;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.view.KeyEvent;
import android.widget.TextView;
import com.archos.mediacenter.video.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = android.app.Application.class)
public class TVMenuItemTest {
    @Test
    public void disabledItemRejectsRemoteAndChildClicksThenRestoresListener() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        TVMenuItem item = new TVMenuItem(activity);
        TextView text = new TextView(activity);
        text.setId(R.id.info_text);
        item.addView(text);
        int[] clicks = {0};
        item.setOnClickListener(view -> clicks[0]++);
        item.setDisabled(true);
        text.performClick();
        item.onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(0, clicks[0]);

        // Also cover the audio menu's order: disable first, then set the listener.
        item.setOnClickListener(view -> clicks[0]++);
        text.performClick();
        item.onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(0, clicks[0]);

        item.setDisabled(false);
        text.performClick();
        item.onKeyUp(KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals(2, clicks[0]);
    }
}
