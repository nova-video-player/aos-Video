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

public class VideoEffect {

    public static final int EFFECT_CLASS_MODE = 0x0000FFFF;
    public static final int EFFECT_NEED_OPENGL= 0x01000000;
    public static final int EFFECT_CLASS_TYPE = 0x00FF0000 | EFFECT_NEED_OPENGL;
    
    public static final int EFFECT_NONE                = 0x00000000;
    public static final int EFFECT_STEREO_MERGE        = 0x00010000;
    public static final int EFFECT_STEREO_MERGE_ARCHOS = 0x00020000;
    public static final int EFFECT_STEREO_SPLIT        = 0x00040000 | EFFECT_NEED_OPENGL;

    public static final int NORMAL_2D_MODE = 0x000000000;
    public static final int STEREO_2D_MODE = 0x000000001;
    public static final int SBS_MODE       = 0x000000002;
    public static final int TB_MODE        = 0x000000004;
    public static final int ANAGLYPH_MODE  = 0x000000008;
    
    private static boolean mStereoForced = false;
    
    private static int mStereoModeForce = -1;
    
    public static void resetForcedMode() {
        mStereoModeForce = -1;
    }
    
    public static void setForcedMode(int mode) {
        mStereoModeForce = mode & EFFECT_CLASS_MODE;
    }
    
    public static boolean isInForcedMode() {
        return (mStereoModeForce != -1);
    }
    
    private static int getForcedMode() {
        return mStereoModeForce;
    }
    
    public static final boolean isStereoEffectOn(int type) {
        return ((type ==  EFFECT_STEREO_SPLIT) || (type ==  EFFECT_STEREO_MERGE_ARCHOS) || (type ==  EFFECT_STEREO_MERGE) || isInForcedMode());
    }
    
    public static final void setStereoForced(boolean force) {
        mStereoForced = force;
    }
    
    public void initGLComponents(){};
    public void deinitGLComponents(){};
    public int getVideoTexture(){return -1;};
    public int getUIOverlayTexture(){return -1;};
    public void setVideoTransform(float[] mTransform){};
    public void setHeadTransform(float[] mTransform){};
    public void onPlay(){};
    public void setVideoSize(int videoWidth, int videoHeight, double aspect){};
    public void setViewPort(int width, int height){};
    public void draw(){};
    
    private int mMode = getDefaultMode();
    protected int mType = getDefaultType();

    public void setEffectMode(int mode){
        mMode = mode & EFFECT_CLASS_MODE;
    };
    
    public int getEffectMode() {
        if (isInForcedMode()) return getForcedMode();
        else return mMode;
    }
    
    public static int getDefaultMode() {
        return mStereoForced?STEREO_2D_MODE:NORMAL_2D_MODE;
    }
    
    public void setEffectType(int type){
        mType = type & EFFECT_CLASS_TYPE;
    };
    
    public int getEffectType() {
        return mType;
    }
    
    public static int getDefaultType() {
        return EFFECT_NONE;
    }
    
    public static boolean openGLRequested(int type) {
        return ((type & VideoEffect.EFFECT_NEED_OPENGL) == VideoEffect.EFFECT_NEED_OPENGL);
    }
    
    public boolean openGLRequested() {
        return ((mType & VideoEffect.EFFECT_NEED_OPENGL) == VideoEffect.EFFECT_NEED_OPENGL);
    }
    
    public int getUIMode() {
        return VideoEffect.NORMAL_2D_MODE;
    }
}
 
