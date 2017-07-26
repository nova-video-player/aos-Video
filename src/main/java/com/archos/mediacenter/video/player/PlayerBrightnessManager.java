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

package com.archos.mediacenter.video.player;

import android.app.Activity;
import android.content.Context;
import android.view.WindowManager;

import com.archos.mediacenter.utils.AppState;

/**
 * Created by alexandre on 03/03/17.
 */

public class PlayerBrightnessManager implements AppState.OnForeGroundListener {

    private static PlayerBrightnessManager sPlayerBrightnessManager;
    private int mBrightness = -1;

    public static PlayerBrightnessManager getInstance(){
        if(sPlayerBrightnessManager==null) {
            sPlayerBrightnessManager = new PlayerBrightnessManager();
            AppState.addOnForeGroundListener(sPlayerBrightnessManager);
        }
        return sPlayerBrightnessManager;
    }

    public void setBrightness(Activity activity, int brightness){
        mBrightness = brightness;
        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        lp.screenBrightness = brightness==-1?WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE:(float)brightness / 255f;
        activity.getWindow().setAttributes(lp);
    }

    public void restoreBrightness(Activity activity){
        setBrightness(activity, mBrightness);
    }

    @Override
    public void onForeGroundState(Context applicationContext, boolean foreground) {
        if(!foreground)
            mBrightness = -1;
    }
}
