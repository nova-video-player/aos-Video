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

import com.archos.mediacenter.video.utils.VideoPreferencesFragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class StereoMergeArchosEffect extends VideoEffect
{

    // keep in sync with res/values/arrays.xml - pref_s3d_mode_entries
    private static final String S3D_OFF = "off";
    private static final String S3D_SBS = "sbs";
    private static final String S3D_TOPBOTTOM = "topbottom";
    private static final String S3D_ANAGLYPH = "anaglyph";
    private static final String[] S3D_MODE = { S3D_OFF, S3D_SBS, S3D_TOPBOTTOM, S3D_ANAGLYPH };
    
    private static final String SET_S3D_MODE_INTENT = "archos.intent.s3d.mode";
    
    private Context mContext;
    private SharedPreferences mPreferences;
    
    private final static String TAG="StereoMergeArchosEffect";

    public StereoMergeArchosEffect(Context context) {
        mContext = context;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    @Override
    public void setEffectMode(int mode){
        super.setEffectMode(mode);
        mode = getEffectMode();
        if (mode != 0) mode = Integer.numberOfTrailingZeros(mode);
        if(mPreferences.getBoolean(VideoPreferencesFragment.KEY_ACTIVATE_3D_SWITCH, false)){
            String msg = S3D_MODE[mode];
            Intent intent = new Intent (SET_S3D_MODE_INTENT);
            intent.putExtra ("mode", msg);
            intent.putExtra ("fix_input_ratio", false);
            mContext.sendBroadcast (intent);
        }
    }
    
    @Override
    public int getEffectType() {
        return VideoEffect.EFFECT_STEREO_MERGE_ARCHOS;
    }
    
    public int getUIMode() {
        if(!mPreferences.getBoolean(VideoPreferencesFragment.KEY_ACTIVATE_3D_SWITCH, false)){
            switch (getEffectMode()) {
                case VideoEffect.STEREO_2D_MODE:
                case VideoEffect.SBS_MODE:
                    return VideoEffect.SBS_MODE;
                case VideoEffect.TB_MODE:
                    return VideoEffect.TB_MODE;
                case VideoEffect.ANAGLYPH_MODE:
                case VideoEffect.NORMAL_2D_MODE:
                default:
                    return VideoEffect.NORMAL_2D_MODE;
           }
       } else {
           return super.getUIMode();
       }
    }
}