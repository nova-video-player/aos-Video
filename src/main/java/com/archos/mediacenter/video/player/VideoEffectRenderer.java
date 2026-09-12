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
import android.util.Log;
import android.view.Surface;

import 	java.util.concurrent.ArrayBlockingQueue;


public class VideoEffectRenderer extends TextureSurfaceRenderer implements SurfaceTexture.OnFrameAvailableListener
{

    private final static String TAG="VideoEffectRenderer";
    
    private static final Boolean mTrue = Boolean.TRUE;

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

    ArrayBlockingQueue<Boolean> mSourceFrameAvailable = new ArrayBlockingQueue<Boolean>(1);
    
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
            needUpdate = (mSourceFrameAvailable.take()).booleanValue();
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

        // Register this Surface/size with SubtitleEngine right away, before the first real
        // video frame necessarily arrives. Without this, a style change made while a video
        // is opened already paused would have no cached redraw target until onFrameAvailable
        // fires at least once -- see SubtitleEngine.primeThreeDSurface()'s doc comment.
        if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
            Player.sPlayer.getSubtitleEngine().primeThreeDSurface(mUISurface, mViewWidth, mViewHeight);
        }
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

    /**
     * Wakes the GL draw loop for one pass without a real video frame having arrived.
     *
     * Queuing a fresh buffer into mUISurfaceTexture (what SubtitleEngine.draw3DSubtitles()
     * does via lockCanvas()/unlockCanvasAndPost()) is only HALF the pipeline. Nothing
     * actually CONSUMES that buffer -- mUISurfaceTexture.updateTexImage() + mEffect.draw()
     * (the GL composite) + the eventual eglSwapBuffers() that puts it on screen -- unless
     * draw() below runs, and draw() is gated entirely behind mSourceFrameAvailable.take(),
     * which normally only the video decoder's onFrameAvailable() feeds. While the video is
     * paused, that callback never fires, so a style change's freshly-queued subtitle buffer
     * just sits in the queue, unseen, no matter how promptly the native/Java side produced
     * it -- this is what SubtitleEngine.redraw3DIfNeeded() alone could not fix.
     *
     * Call this AFTER the fresh subtitle buffer has already been queued (SubtitleEngine
     * does so before calling here). This method doesn't draw anything itself, it only
     * unblocks the consumer side, mirroring exactly what onFrameAvailable() does for a real
     * video frame. Re-latching the same (frozen) video frame via
     * mVideoSurfaceTexture.updateTexImage() when nothing new has arrived is safe -- it just
     * re-presents whatever's already there.
     *
     * Uses offer() rather than put(): mSourceFrameAvailable is a one-element wake latch, not
     * a work queue, so a wake that's already pending makes any further wake redundant -- the
     * draw loop is going to run and pick up the latest queued buffer regardless. put() would
     * block this (UI) thread until draw() drains the queue, which can stall a style-setter
     * call for no benefit; offer() drops the duplicate instead and returns immediately. This
     * is also what makes it safe to call wakeDrawLoop() while the renderer is stopping/paused
     * and nothing is draining the queue -- offer() simply returns false rather than hanging.
     */
    public void wakeDrawLoop() {
        mSourceFrameAvailable.offer(mTrue);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        try {
            // Check if 3D mode is active and trigger the synchronous readback draw
            if (Player.sPlayer != null && Player.sPlayer.getSubtitleEngine() != null) {
                SubtitleEngine eng = Player.sPlayer.getSubtitleEngine();
                if (eng.is3DMode()) {
                    eng.draw3DSubtitles(mUISurface, mViewWidth, mViewHeight);
                }
            }
            mSourceFrameAvailable.put(mTrue);
        } catch (Exception e) {
            Log.e(TAG, "FrameAvailable missed", e);
        }
    }
}
