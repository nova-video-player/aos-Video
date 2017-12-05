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
import android.graphics.*;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import 	java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;


public class VideoEffectRenderer extends TextureSurfaceRenderer implements SurfaceTexture.OnFrameAvailableListener
{

    private final static String TAG="VideoEffectRenderer";
    
    private static final Boolean mTrue = new Boolean(Boolean.TRUE);

    private int mViewWidth;
    private int mViewHeight;
    private float[] mTransformMatrix;
    private float[] mHeadTransform;
    private SurfaceTexture mVideoSurfaceTexture;
    private SurfaceTexture mUISurfaceTexture;
    private Surface mUISurface;
    private Context mContext;

    private VideoEffect mEffect;
    
    private SurfaceTexture mUIOverlay;

    ArrayBlockingQueue mSourceFrameAvailable = new ArrayBlockingQueue<Boolean>(1);
    
    private Object texSync = new Object();
    private Object initDone = new Object();
    
    private void waitInit() {
        wait(initDone);
    }
    
    private void notifyInit() {
        notify(initDone);
    }
    
    private void wait(Object obj) {
        synchronized(obj) {
            try {
                obj.wait();
             } catch (InterruptedException e) {}
        }    
    }
    
    private void notify(Object obj) {
        synchronized(obj) {
            obj.notify();
        }
    }
    
    public VideoEffectRenderer(Context context, int effectType)
    {
        super();
        int type = effectType & VideoEffect.EFFECT_CLASS_TYPE;
        int mode = effectType & VideoEffect.EFFECT_CLASS_MODE;
        mContext = context;
        setEffectType(effectType);
        if (mEffect != null)
            mEffect.setEffectMode(mode);
        mTransformMatrix = new float[16];
        mHeadTransform = new float[16];
    }
    
    public void setTexture(SurfaceTexture surface, int width, int height) {
        super.setTexture(surface, width, height);
        mViewWidth = width;
        mViewHeight = height;
        waitInit();
    }
    
    public int getEffectType() {
        if (mEffect != null)
            return mEffect.getEffectType();
        else
            return VideoEffect.getDefaultType();
    }
    
    public int getEffectMode() {
        if (mEffect != null)
            return mEffect.getEffectMode();
        else
            return VideoEffect.getDefaultMode();
    }
    
    public void setSurfaceSize(int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
        if (mEffect != null) mEffect.setViewPort(mViewWidth, mViewHeight);
    }
    
    public void setEffectMode(int mode) {
        if (mEffect != null) {
            mEffect.setEffectMode(mode);
        }
    }
    
    public void setEffectType(int type) {
        if ((mEffect == null) || (mEffect.getEffectType() != type)){
            switch (type) {
                case VideoEffect.EFFECT_STEREO_SPLIT:
                    mEffect = new StereoDiveEffect(mContext);
                    break;
                case VideoEffect.EFFECT_STEREO_MERGE_ARCHOS:
                    mEffect = new StereoMergeArchosEffect(mContext);
                    break;
                case VideoEffect.EFFECT_STEREO_MERGE:
                    mEffect = new StereoMergeEffect(mContext);
                    break;
                case VideoEffect.EFFECT_NONE:
                default:
                    mEffect = new NoneEffect(mContext);
                break;
            }
        }
    }
    
    public int getUIMode() {
        if (mEffect != null)
            return mEffect.getUIMode();
        else
            return VideoEffect.NORMAL_2D_MODE;
    }
    
    public void onPlay() {
        play();
        if (mEffect != null) {
            mEffect.onPlay();
        }
    }

    @Override
    protected boolean draw()
    {
        boolean needUpdate = false;
        try {
            needUpdate = ((Boolean)mSourceFrameAvailable.take()).booleanValue();
        } catch (InterruptedException ie) {}
        if (needUpdate) {
            int offset = 0;
            mVideoSurfaceTexture.updateTexImage();
            mUISurfaceTexture.updateTexImage();
            mVideoSurfaceTexture.getTransformMatrix(mTransformMatrix);
            if (mEffect != null) {
            mEffect.setVideoTransform(mTransformMatrix);
            mEffect.setHeadTransform(mHeadTransform);
            mEffect.draw();
            }
            return true;
         } else {
            return false;
         }
    }

    @Override
    protected synchronized void initGLComponents()
    {
        if (mEffect != null) {
        mEffect.initGLComponents();
        int videoTexture = mEffect.getVideoTexture();
        mVideoSurfaceTexture = new SurfaceTexture(videoTexture);
        mVideoSurfaceTexture.setOnFrameAvailableListener(this);
        
        int uiTexture = mEffect.getUIOverlayTexture();
        mUISurfaceTexture = new SurfaceTexture(uiTexture);
        mUISurfaceTexture.setDefaultBufferSize(mViewWidth, mViewHeight);
        mUISurface = new Surface(mUISurfaceTexture);
        try {
            Canvas c = mUISurface.lockCanvas(null);
            c.drawColor(0x0);
            mUISurface.unlockCanvasAndPost(c);
        } catch (Exception e) { }
        }
        notifyInit();
    }

    public void setVideoSize(int videoWidth, int videoHeight, double aspect) {
        if (mEffect != null) {
            mEffect.setVideoSize(videoWidth, videoHeight, aspect);
        }
    }
    
    @Override
    protected synchronized void deinitGLComponents()
    {
        if (mEffect != null) {
	mEffect.deinitGLComponents();
        mVideoSurfaceTexture.release();
        mVideoSurfaceTexture.setOnFrameAvailableListener(null);
        }
    }

    public SurfaceTexture getVideoTexture()
    {
        return mVideoSurfaceTexture;
    }
    
    public Surface getUISurface()
    {
        return mUISurface;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture)
    {
	try {
            mSourceFrameAvailable.put(mTrue);
        } catch (Exception e) {
            Log.e(TAG, "FrameAvailable missed");
        }
    }
}