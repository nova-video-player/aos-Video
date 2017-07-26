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

import android.content.Context;
import android.util.Log;

public class StereoMergeEffect extends VideoEffect
{

    private final static String TAG="StereoMergeEffect";

    public StereoMergeEffect(Context context) {
    }

    @Override
    public int getEffectType() {
        return VideoEffect.EFFECT_STEREO_MERGE;
    }
    
    public int getUIMode() {
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
    }
}