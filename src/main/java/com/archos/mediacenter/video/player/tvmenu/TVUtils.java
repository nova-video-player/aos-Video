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

package com.archos.mediacenter.video.player.tvmenu;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;

import com.archos.environment.ArchosFeatures;

public class TVUtils {
    /**
     * Propagate an unhandled key event to the enclosing TVCardView/TVCardDialog.
     * TV menu items and pickers use this so that keys they do not handle bubble up
     * to the card overlay that contains them.
     *
     * @return true if the enclosing card handled the event
     */
    public static boolean dispatchToCardParent(View view, int keyCode, KeyEvent event) {
        ViewParent parent;
        View v = view;
        while ((parent = v.getParent()) != null) {
            if (parent instanceof TVCardView)
                return ((TVCardView) parent).onKeyDown(keyCode, event);
            else if (parent instanceof TVCardDialog)
                return ((TVCardDialog) parent).onKeyDown(keyCode, event);
            else if (parent instanceof View)
                v = (View) parent;
            else
                break;
        }
        return false;
    }
    public static boolean isOKKey(int keyCode){
        if((keyCode==KeyEvent.KEYCODE_ENTER
                ||keyCode==KeyEvent.KEYCODE_BUTTON_R2
                ||keyCode==KeyEvent.KEYCODE_BUTTON_L2
                ||keyCode == KeyEvent.KEYCODE_SOFT_RIGHT
                ||keyCode==KeyEvent.KEYCODE_DPAD_CENTER
                ||keyCode==KeyEvent.KEYCODE_BUTTON_A)
                ) {
            return true;
        }
        return false;
    }
    public static boolean isTV(Context ct){
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(ct);
        String mode = mPreferences.getString("uimode", "0");
        if (mode.equals("1"))
            return false;
        if (mode.equals("2"))
            return true;
        // make chromeOS considered as being TV
        return (ArchosFeatures.isAndroidTV(ct) || ArchosFeatures.isChromeOS(ct));
    }
}
