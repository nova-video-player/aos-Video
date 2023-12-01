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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import com.archos.mediacenter.utils.AppState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by alexandre on 03/03/17.
 */

public class PlayerBrightnessManager implements AppState.OnForeGroundListener {

    private static final Logger log = LoggerFactory.getLogger(PlayerBrightnessManager.class);

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

    public static float getBrightness(Window window) { // get screen brightness float value between 0 and 1
        log.debug("getBrightness: " + window.getAttributes().screenBrightness);
        return window.getAttributes().screenBrightness;
    }

    public static int getLinearBrightness(Window window) { // get the brightness value between 0 and 30
        float currentBrightness = getBrightness(window);
        if (currentBrightness == -1) return 10; // when brightness is set to auto, return a default value
        else return brightnessToLevelGamma(currentBrightness);
    }

    public static void setLinearBrightness(int level, boolean direction, Window window) {
        float newBrightness = levelToBrightnessGamma(level);
        float curBrightness = getBrightness(window);
        log.debug("setLinearBrightness: level=" + level + ", direction=" + direction + ", curBrightness=" + curBrightness + ", newBrightness=" + newBrightness);
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        // only apply change is it goes into the right increase/decrease direction to avoid glitch
        if (direction) {
            if (newBrightness > curBrightness) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                layoutParams.screenBrightness = newBrightness;
                window.setAttributes(layoutParams);
                window.addFlags(WindowManager.LayoutParams.FLAGS_CHANGED);
            } else {
                log.debug("setLinearBrightness: not applying change, because increasing newBrightness=" + newBrightness + " < curBrightness=" + curBrightness);
            }
        } else {
            if (newBrightness < curBrightness) {
                layoutParams.screenBrightness = newBrightness;
                window.setAttributes(layoutParams);
                window.addFlags(WindowManager.LayoutParams.FLAGS_CHANGED);
            } else {
                log.debug("setLinearBrightness: not applying change, because decreasing newBrightness=" + newBrightness + " > curBrightness=" + curBrightness);
            }
        }
    }

    private static float levelToBrightness(final int level) {
        final double d = 0.064 + 0.936 / (double) 30 * (double) level;
        return Math.max(0f, Math.min((float) (d * d), 1f));
    }

    private static int brightnessToLevel(final float brightness) {
        double d = Math.sqrt(brightness);
        double level = (d - 0.064) * 30 / 0.936;
        return (int) Math.max(0, Math.min((int) Math.round(level), 30));
    }

    private static float levelToBrightnessGamma(final int level) {
        float gamma = 2.2f; // Gamma value for brightness correction
        final float brightness = (float) Math.pow((float) level / 30f, gamma);
        return Math.max(0f, Math.min(brightness, 1f));
    }

    private static int brightnessToLevelGamma(final float brightness) {
        final float gamma = 2.2f; // Gamma value for brightness correction
        final int brightnessLevel = Math.round(30f * (float) Math.pow(brightness, 1 / gamma));
        return Math.max(0, Math.min(brightnessLevel, 30));
    }
}
