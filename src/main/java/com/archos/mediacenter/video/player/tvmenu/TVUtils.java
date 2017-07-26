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
import android.preference.PreferenceManager;
import android.view.KeyEvent;

import com.archos.environment.ArchosFeatures;

public class TVUtils {
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
        return ArchosFeatures.isTV(ct);
    }
}
