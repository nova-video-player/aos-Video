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

import com.archos.mediacenter.video.R;

import com.archos.medialib.IMediaPlayer;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

public class SurfaceController {
    private static String TAG = "SurfaceController";

    private boolean mEffectEnable = false;

    public void setAlpha(float i) {
        mView.setAlpha(i);
    }

    public class VideoFormat {
        public static final int ORIGINAL = 0;
        public static final int FULLSCREEN = 1;
        /*
         *  for 2:35 video on 4/3 screen:
         *  intermediate surface height in order to don't crop too much video
         */
        public static final int AUTO = 2;
        public static final double VIDEO_FORMAT_AUTO_THRES = 0.7;

        private final int[] mode = {ORIGINAL, FULLSCREEN, AUTO};
        private final int max;
        private int idx;
        public VideoFormat(int max) {
            this.max = max;
            this.idx = 0;
        }

        private int getFmt() {
            return mode[idx];
        }
        private void setFmt(int fmt) {
            for (int i = 0; i < max; ++i) {
                if (mode[i] == fmt) {
                    idx = i;
                    return;
                }
            }
            idx = 0;
        }
        private int switchFmt() {
            idx = (idx + 1) % max;
            return mode[idx];
        }

        private int getNextFmt() {
            return mode[(idx + 1) % max];
        }
        public int getMax(){
            return max;
        }
    }
    public interface Listener {
        void onSwitchVideoFormat(int fmt, int autoFmt);
    }

    private View mView;
    private SurfaceView mSurfaceView = null;
    private TextureView mEffectView = null;
    private IMediaPlayer mMediaPlayer = null;
    private SurfaceController.Listener      mSurfaceListener;
    private int         mLcdWidth = 0;
    private int         mLcdHeight = 0;
    private boolean     mHdmiPlugged = false;
    private int         mHdmiWidth = 0;
    private int         mHdmiHeight = 0;
    private int         mVideoWidth = 0;
    private int         mVideoHeight = 0;
    private double      mVideoAspect = 1.0f;
    private VideoFormat mVideoFormat = new VideoFormat(2);
    private VideoFormat mAutoVideoFormat = new VideoFormat(3);
    
    private int mEffectMode = VideoEffect.getDefaultMode();
    private int mEffectType = VideoEffect.getDefaultType();

    public SurfaceController(View rootView) {
        ViewGroup mLp = (ViewGroup)rootView;
 
        mEffectView =  (TextureView) mLp.findViewById(R.id.gl_surface_view);
        mSurfaceView =  (SurfaceView) mLp.findViewById(R.id.surface_view);
        if (mEffectEnable) {
            mView = mEffectView;
            mSurfaceView.setVisibility(View.GONE);
         } else {
             mView = mSurfaceView;
             mEffectView.setVisibility(View.GONE);
        }
    }
  
    public void setGLSupportEnabled(boolean enable){
        if (mEffectEnable == enable) return;
        mView.setVisibility(View.GONE);
        if (enable) {
            //Need openGL, let's use TextureView
            mView = mEffectView;
         } else {
             //Do not need openGL, let's use SurfaceView
             mView = mSurfaceView;
        }
        mView.setVisibility(View.VISIBLE);
    	mEffectEnable = enable;
    	updateSurface();
    }
    synchronized public void setMediaPlayer(IMediaPlayer player) {
        mMediaPlayer = player;
        updateSurface();
    }

    public void setSurfaceCallback(SurfaceHolder.Callback callback) {
        if (mSurfaceView != null)
            mSurfaceView.getHolder().addCallback(callback);
    }
    
    public boolean supportOpenGLVideoEffect() {
        return (mEffectView == mView) && (VideoEffect.openGLRequested(mEffectType));
    }

    public void setTextureCallback(TextureView.SurfaceTextureListener callback) {
        if (mEffectView != null)
            mEffectView.setSurfaceTextureListener(callback);
    }

    public void setHdmiPlugged(boolean plugged, int hdmiWidth, int hdmiHeight) {
        if (plugged != mHdmiPlugged) {
            mHdmiPlugged = plugged;
            mHdmiWidth = hdmiWidth;
            mHdmiHeight = hdmiHeight;
            updateSurface();
        }
    }

    public void setScreenSize(int lcdWidth, int lcdHeight) {
        mLcdWidth = lcdWidth;
        mLcdHeight = lcdHeight;
        updateSurface();
    }

    public void setVideoSize(int videoWidth, int videoHeight, double aspect) {
        if (mVideoWidth != videoWidth || mVideoHeight != videoHeight || mVideoAspect != aspect) {
            mVideoWidth = videoWidth;
            mVideoHeight = videoHeight;
            mVideoAspect = aspect;
            updateSurface();
        }
    }

    public void setListener(SurfaceController.Listener listener) {
        mSurfaceListener = listener;
    }
    public int getMax(){
        return getVideoFormat().getMax();
    }
    public int getCurrentFormat(){
        return getVideoFormat().getFmt();
    }
    private VideoFormat getVideoFormat() {
        if (!mHdmiPlugged && ((mVideoWidth / (double) mVideoHeight) - (mLcdWidth / (double) mLcdHeight) > VideoFormat.VIDEO_FORMAT_AUTO_THRES))
            return mAutoVideoFormat;
        else
            return mVideoFormat;
    }

    public void switchVideoFormat() {
        getVideoFormat().switchFmt();
        updateSurface();
        if (mSurfaceListener != null) {
            mSurfaceListener.onSwitchVideoFormat(mVideoFormat.getFmt(), mAutoVideoFormat.getFmt());
        }
    }
    public void setVideoFormat(int fmt) {
        getVideoFormat().setFmt(fmt);
        updateSurface();
        if (mSurfaceListener != null) {
            mSurfaceListener.onSwitchVideoFormat(mVideoFormat.getFmt(), mAutoVideoFormat.getFmt());
        }
    }
    public void setVideoFormat(int fmt, int autoFmt) {
        mVideoFormat.setFmt(fmt);
        mAutoVideoFormat.setFmt(autoFmt);
        updateSurface();
    }

    public int getNextVideoFormat() {
        return getVideoFormat().getNextFmt();
    }

    public void setEffectMode(int mode) {
        mEffectMode = mode;
        updateSurface();
    }
    
    public void setEffectType(int type) {
        mEffectType = type;
        updateSurface();
    }
    
    synchronized private void updateSurface() {
        // get screen size
        int dw, dh, vw, vh, fmt;
        float cropW = 1.0f;
        float cropH = 1.0f;
        double par = mVideoAspect;

        if (mHdmiPlugged) {
            dw = mHdmiWidth;
            dh = mHdmiHeight;
        } else {
            dw = mLcdWidth;
            dh = mLcdHeight;
        }

        vw = mVideoWidth;
        vh = mVideoHeight;

        if (vw <= 0 || vh <= 0 || dw <= 0 || dh <= 0 || mMediaPlayer == null)
            return;
        fmt = getVideoFormat().getFmt();

        if (mEffectEnable) {
            fmt = VideoFormat.FULLSCREEN; //only FULLSCREEN FORMAT is currently supported in OpenGL rendering
        }

        // calculate aspect ratio
        double sar = (double) vw / (double) vh;
        double ar = par * sar;
        // calculate display aspect ratio
        double dar = (double) dw / (double) dh;

        cropW = cropH = 1.0f;
        switch (fmt) {
            case VideoFormat.ORIGINAL:
                if (dar < ar) {
                    //4:3 movie on 16:9 screen
                    dh = (int) (dw/ (ar));
                } else {
                    //16:9 movie on 4:3 screen
                    dw = (int) (dh * ar);
                }
                break;
            case VideoFormat.FULLSCREEN:
                if (dar < ar) {
                    //4:3 movie on 16:9 screen
                    cropW = (float)dar / (float)ar;
                    cropH = 1.0f;

                } else {
                    //16:9 movie on 4:3 screen
                    cropH = (float)ar / (float)dar;
                    cropW = 1.0f;
                }
                break;
            case VideoFormat.AUTO: {
                cropW = 1.0f;
                cropH = 1.0f;
                if (dar > ar) {
                    dw = dw + (((int) (dh * ar)) - dw) / 2;
                    cropH = (float)dh / (float)(dw / ar);
                }
                else {
                    dh = dh + (((int) (dw / ar)) - dh) / 2;
                    cropW = (float)dw / (float)(dh * ar);
                }
                break;
            }
        }

        if (((mEffectMode & VideoEffect.TB_MODE)!=0) && (ar <= 1.5)) {
            dw *= 2;
        }
        if (((mEffectMode & VideoEffect.SBS_MODE)!=0) && (ar >= 3.0)) {
            dh *= 2;
        }
        
        if (mSurfaceView != null)
            mSurfaceView.getHolder().setFixedSize(vw, vh);

        dw = Math.round(dw  / cropW);
        dh = Math.round(dh / cropH);
        ViewGroup.LayoutParams lp = mView.getLayoutParams();
        lp.width = dw;
        lp.height = dh;
        mView.setLayoutParams(lp);

        mView.invalidate();

        Log.d(TAG, "updateSurface: " + vw + "x" + vh + " -> " + dw + "x" + dh + " / formatCrop: " + cropW + "x" + cropH + " / mEffectMode: "+mEffectMode);
    }
}
