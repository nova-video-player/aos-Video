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

import android.os.Build;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SurfaceController {

    private static final Logger log = LoggerFactory.getLogger(SurfaceController.class);

    private boolean mEffectEnable = false;

    public void setAlpha(float i) {
        mView.setAlpha(i);
    }

    public class VideoFormat {
        public static final int ORIGINAL = 0;
        public static final int STRETCH_XY = 1;
        /*
         *  for 2:35 video on 4/3 screen:
         *  intermediate surface height in order to don't crop too much video
         */
        public static final int FULL_SCREEN = 2;
        public static final int FORCE43 = 3;
        public static final int FORCE169 = 4;
        public static final int FORCE185 = 5;
        public static final int FORCE239 = 6;
        public static final int AUTO = 7;
        
        public static final double VIDEO_FORMAT_AUTO_THRES = 0.7;

        private final int[] mode = {ORIGINAL, STRETCH_XY, FULL_SCREEN, FORCE43, FORCE169, FORCE185, FORCE239, AUTO};
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
    private TextureView mSubtitleView = null; // NEW: The OpenGL Subtitle Layer
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
    private VideoFormat mVideoFormat = new VideoFormat(7);
    private VideoFormat mAutoVideoFormat = new VideoFormat(8);
    private int         mSurfaceWidth = 0;
    private int         mSurfaceHeight = 0;
    public boolean willStretchY;
    private int mEffectMode = VideoEffect.getDefaultMode();
    private int mEffectType = VideoEffect.getDefaultType();

    private int mCutoutLeft = 0;
    private int mCutoutTop = 0;
    private int mCutoutRight = 0;
    private int mCutoutBottom = 0;
    private int mMarginLeft = 0;
    private int mMarginTop = 0;
    private boolean mCutoutBugToasted = false;
    public boolean mFullScreenWithCutout = false;
    public boolean mCutBothSidesX = false;

    public SurfaceController(View rootView) {
        ViewGroup mLp = (ViewGroup)rootView;
 
        mEffectView =  (TextureView) mLp.findViewById(R.id.gl_surface_view);
        mSurfaceView =  (SurfaceView) mLp.findViewById(R.id.surface_view);
        mSubtitleView = (TextureView) mLp.findViewById(R.id.gl_subtitle_view); // NEW
        // --- NATIVE OPENGL UPGRADE FIX ---
        // CRITICAL: TextureViews are opaque by default! If we don't set this to false,
        // Android thinks this view is a solid black box, optimizes out the 3D video
        // underneath it, and causes the hardware MediaCodec to stall and crash!
        if (mSubtitleView != null) {
            mSubtitleView.setOpaque(false);
        }
        if (mEffectEnable) {
            mView = mEffectView;
            mSurfaceView.setVisibility(View.GONE);
         } else {
             mView = mSurfaceView;
             mEffectView.setVisibility(View.GONE);
        }
    }
  
    public void setGLSupportEnabled(boolean enable){
        if (log.isDebugEnabled()) log.debug("setGLSupportEnabled: {}", enable);
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
        if (log.isDebugEnabled()) log.debug("supportOpenGLVideoEffect: {}", (mEffectView == mView) && (VideoEffect.openGLRequested(mEffectType)));
        return (mEffectView == mView) && (VideoEffect.openGLRequested(mEffectType));
    }

    public void setTextureCallback(TextureView.SurfaceTextureListener callback) {
        if (mEffectView != null)
            mEffectView.setSurfaceTextureListener(callback);
    }

    public void setSubtitleTextureCallback(TextureView.SurfaceTextureListener callback) {
        if (mSubtitleView != null)
            mSubtitleView.setSurfaceTextureListener(callback);
    }

    public int getSubtitleViewWidth() { return mSubtitleView != null ? mSubtitleView.getWidth() : 0; }
    public int getSubtitleViewHeight() { return mSubtitleView != null ? mSubtitleView.getHeight() : 0; }

    public void setHdmiPlugged(boolean plugged, int hdmiWidth, int hdmiHeight) {
        if (log.isDebugEnabled()) log.debug("setHdmiPlugged: plugged={}, hdmi=({},{})", plugged, hdmiWidth, hdmiHeight);
        if (plugged != mHdmiPlugged || (plugged && (mHdmiWidth != hdmiWidth || mHdmiHeight != hdmiHeight))) {
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

    public void setCutoutMetrics(int cutoutLeft, int cutoutTop, int cutoutRight, int cutoutBottom) {
        if (mCutoutLeft != cutoutLeft || mCutoutTop != cutoutTop || mCutoutRight != cutoutRight || mCutoutBottom != cutoutBottom) {
            mCutoutLeft = cutoutLeft;
            mCutoutTop = cutoutTop;
            mCutoutRight = cutoutRight;
            mCutoutBottom = cutoutBottom;
            updateSurface();
        }
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
        if (log.isDebugEnabled()) log.debug("getCurrentFormat: {}", getVideoFormat().getFmt());
        return getVideoFormat().getFmt();
    }
    private VideoFormat getVideoFormat() {
        if (!mHdmiPlugged && ((mVideoWidth / (double) mVideoHeight) - (mLcdWidth / (double) mLcdHeight) > VideoFormat.VIDEO_FORMAT_AUTO_THRES)) {
            // on special screen sizes that are closer to 4:3 then enable the "optimized" aspect ratio
            if (log.isDebugEnabled()) log.debug("getVideoFormat: return mAutoVideoFormat");
            return mAutoVideoFormat;
        } else {
            if (log.isDebugEnabled()) log.debug("getVideoFormat: return mVideoFormat");
            return mVideoFormat;
        }
    }

    public void switchVideoFormat() {
        if (log.isDebugEnabled()) log.debug("switchVideoFormat");
        getVideoFormat().switchFmt();
        updateSurface();
        if (mSurfaceListener != null) {
            mSurfaceListener.onSwitchVideoFormat(mVideoFormat.getFmt(), mAutoVideoFormat.getFmt());
        }
    }
    public void setVideoFormat(int fmt) {
        if (log.isDebugEnabled()) log.debug("setVideoFormat fmt={}", fmt);
        getVideoFormat().setFmt(fmt);
        updateSurface();
        if (mSurfaceListener != null) {
            mSurfaceListener.onSwitchVideoFormat(mVideoFormat.getFmt(), mAutoVideoFormat.getFmt());
        }
    }
    public void setVideoFormat(int fmt, int autoFmt) {
        if (log.isDebugEnabled()) log.debug("setVideoFormat fmt={}, autoFmt={}", fmt, autoFmt);
        mVideoFormat.setFmt(fmt);
        mAutoVideoFormat.setFmt(autoFmt);
        updateSurface();
    }

    public int getNextVideoFormat() {
        return getVideoFormat().getNextFmt();
    }

    public int getCurrentVideoFormat() {
        return getVideoFormat().getFmt();
    }

    public void setEffectMode(int mode) {
        mEffectMode = mode;
        updateSurface();
    }
    
    public void setEffectType(int type) {
        mEffectType = type;
        updateSurface();
    }
    
    public void setProjectorMode(boolean projectorMode){
        //Get the layout paramters for the Views.
        FrameLayout.LayoutParams paramsEffect = (FrameLayout.LayoutParams) mEffectView.getLayoutParams();
        FrameLayout.LayoutParams paramsSurface = (FrameLayout.LayoutParams) mSurfaceView.getLayoutParams();

        // Set gravity to top and center horizontally if we are in projector mode.
        paramsEffect.gravity = projectorMode ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER;
        paramsSurface.gravity = projectorMode ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER;

        // Set the new layout parameters
        mSurfaceView.setLayoutParams(paramsSurface);
        mEffectView.setLayoutParams(paramsEffect);
    }
    
    synchronized public void updateSurface() {
        if (log.isDebugEnabled()) log.debug("updateSurface");
        // get screen size
        int dw, dh, vw, vh, fmt, dcw, dch;
        float cropW = 1.0f;
        float cropH = 1.0f;
        
        //Get the Video Size
        vw = mVideoWidth;
        vh = mVideoHeight;

        // calculate aspect ratio
        double sar = (double) vw / (double) vh; // sar = source aspect ratio (video)
        
        //Get the Pixel Aspect Ratio
        double par = mVideoAspect;

        //Get the applied cutout size, since it can be changed on the fly now.
        int cutoutLeft, cutoutTop, cutoutRight, cutoutBottom = 0;
        if (mFullScreenWithCutout) 
            cutoutLeft = cutoutTop = cutoutRight = cutoutBottom = 0;
        else {
            cutoutLeft = mCutoutLeft;
            cutoutTop = mCutoutTop;
            cutoutRight = mCutoutRight;
            cutoutBottom = mCutoutBottom;

            //If we have the Cut both sides option on, apply it now.
            //This doesnt need to be done on Y, X is where the screen rounding kills screen symmetry
            if (mCutBothSidesX)  {
                if (cutoutLeft > 0 && cutoutRight == 0)
                    cutoutRight = cutoutLeft;
                else if (cutoutRight > 0 && cutoutLeft == 0)
                    cutoutLeft = cutoutRight;
            }
        } 

        //Get the Display size, with and wihtout cutout.
        if (mHdmiPlugged) {
            dw = mHdmiWidth;
            dh = mHdmiHeight;
            dcw = dw;
            dch = dh;
        } else {
            dw = mLcdWidth;
            dh = mLcdHeight;
            dcw =  dw - cutoutLeft - cutoutRight;
            dch =  dh - cutoutTop - cutoutBottom;
        }

        if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: v=({},{})", vw, vh);

        //Get the Display aspect ratio, with and without cutouts.
        double dar = (double) dw / (double) dh;     // display aspect ratio
        double dcar = (double) dcw / (double) dch;  // display aspect ratio without cutout
        
        //Early exit in case of error or nothing to do (yet)
        if (mMediaPlayer == null) log.warn("updateSurface: mMediaPlayer is null!");
        if (vw <= 0 || vh <= 0 || dcw <= 0 || dch <= 0 || mMediaPlayer == null)
            return;
        
        //Do the Aspect Ratio Override if required.
        fmt = getVideoFormat().getFmt();
        double ar = switch (fmt) {
            case VideoFormat.FORCE43 -> 4f / 3f;
            case VideoFormat.FORCE169 -> 16f / 9f;
            case VideoFormat.FORCE185 -> 1.85f;
            case VideoFormat.FORCE239 -> 2.39f;
            default -> par * sar;
        };

        //Is the STRETCH_XY doing Y? 
        willStretchY = (dcar < ar) ;

        if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: sar={}, ar={}, dar={}, dcar={}", sar, ar, dar, dcar);

        //Apply any Video Format Effects, stretch to the right size.
        switch (fmt) {
            case VideoFormat.ORIGINAL, VideoFormat.FORCE43, VideoFormat.FORCE169, VideoFormat.FORCE185, VideoFormat.FORCE239:
                if (dcar < ar) {
                    //4:3 movie on 16:9 screen or 16:9 movie on portrait screen
                    dch = (int) (dcw/ (ar));
                    if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: VideoFormat.ORIGINAL dcar<ar dch={}", dch);
                } else {
                    //16:9 movie on 4:3 screen
                    cutoutLeft = cutoutTop = cutoutRight = cutoutBottom = 0;
                    dcw = (int) (dch * ar);
                    if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: VideoFormat.ORIGINAL dcar>=ar dcw={}", dcw);
                }
                break;
            case VideoFormat.STRETCH_XY:
                //Height can go over the screen top, but set width.
                // Now we have a remove black bar in portrait too...
                if (willStretchY) {
                    //THERE IS NO POSSIBLE WAY TO AVOID THE CUTOUT, AND KEEP ASPECT.
                    //NOT KEEPING ASPECT MAKES THIS FULL_SCREEN.
                    //I HATE THIS CASE!
                    
                    //I have now made it so that if cutouts are not enabled, this works normally
                    //It also works normally in Portrait, since the problem is a landscape only issue
                    //If cutouts are enabled, you cannot stretch Cinema Vertically on Phone.
                    //If I allowed this, the rules would not be respected and Cutout pref would not be honored.
                     if (dcar < 1 || mFullScreenWithCutout)
                        dcw = (int) (dch * (ar));
                        //cropW = (float) dcar / (float) ar;        //Cropping won't help you! We need a way to not draw the left and r-ecentre, cutting equal left and right. 
                    else {
                        //WE ARE FULLSCREEN, turn Video with Cutouts ON to FIX!
                        if (!mCutoutBugToasted) Toast.makeText(mView.getContext(), R.string.toast_cutout_aspect_ratio_fix, Toast.LENGTH_SHORT).show();
                        mCutoutBugToasted = true;
                    }
                    
                } else
                    //This stops the Fullscreen Video with Cutouts button from moving Video side to side
                    //It doesnt take up the whole width, so we will just set cutouts to 0 and have same postition
                    dch = (int) (dcw / (ar));
                break;
            case VideoFormat.FULL_SCREEN: { // display on full screen resolution stretched: keep dcw and dch
                //cropW = 1.0f;
                //cropH = 1.0f;
                //if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: VideoFormat.FULL_SCREEN dc=({},{}), crop=({},{})", dcw, dch, cropW, cropH);
                break;
            }
            case VideoFormat.AUTO: {
                //cropW = 1.0f;
                //cropH = 1.0f;
                if (dcar > ar) {
                    dcw = dcw + (((int) (dch * ar)) - dcw) / 2;
                    cropH = (float) dch / (float) (dcw / ar);
                    if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: VideoFormat.AUTO dcar>ar dc=({},{})", dcw, dch);
                } else {
                    dch = dch + (((int) (dcw / ar)) - dch) / 2;
                    cropW = (float) dcw / (float) (dch * ar);
                    if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: VideoFormat.AUTO dcar<=ar dc=({},{})", dcw, dch);
                }
                break;
            }
        }

        if (((mEffectMode & VideoEffect.TB_MODE)!=0) && (ar <= 1.5)) dcw *= 2;
        if (((mEffectMode & VideoEffect.SBS_MODE)!=0) && (ar >= 3.0)) dch *= 2;

        if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: setFixedSize({},{})", vw, vh);

        if (mSurfaceView != null) mSurfaceView.getHolder().setFixedSize(vw, vh);

        dcw = Math.round(dcw  / cropW);
        dch = Math.round(dch / cropH);

        if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: setLayoutParams({},{})", dcw, dch);

        // margins to avoid cutout
        // When HDMI is plugged, do not apply phone's cutout margins to external display
        mMarginLeft = mHdmiPlugged || mFullScreenWithCutout ? 0 : (int)((cutoutLeft - cutoutRight)/ 2.0f);
        mMarginTop = mHdmiPlugged || mFullScreenWithCutout ? 0 : (int)((cutoutTop - cutoutBottom)/ 2.0f);

        ViewGroup.LayoutParams lp = mView.getLayoutParams();
        ViewGroup.LayoutParams subLp = mSubtitleView != null ? mSubtitleView.getLayoutParams() : null; // NEW
        if (lp instanceof ViewGroup.MarginLayoutParams marginParams) {
            if (log.isDebugEnabled()) log.debug("MARC works with MarginLayoutParams"); // TODO MARC it works!!!
            lp.width = dcw;
            lp.height = dch;
            // video view is centered on the screen, in order to avoid cutout it needs to be shifted slightly
            marginParams.setMargins(mMarginLeft, mMarginTop, 0, 0);
            mView.setLayoutParams(marginParams);

            // NEW: Apply identical margins to the Native Subtitle View
            if (subLp instanceof ViewGroup.MarginLayoutParams subMarginParams) {
                subMarginParams.width = dcw;
                subMarginParams.height = dch;
                subMarginParams.setMargins(mMarginLeft, mMarginTop, 0, 0);
                mSubtitleView.setLayoutParams(subMarginParams);
            }
        } else {
            if (log.isDebugEnabled()) log.debug("MARC works with LayoutParams NO MARGIN");
            lp.width = dcw;
            lp.height = dch;
            mView.setLayoutParams(lp);
            // NEW: Apply identical dimensions to the Native Subtitle View
            if (subLp != null) {
                subLp.width = dcw;
                subLp.height = dch;
                mSubtitleView.setLayoutParams(subLp);
            }
        }
        mView.invalidate();
        if (mSubtitleView != null) mSubtitleView.invalidate(); // NEW

        mSurfaceWidth = dcw;
        mSurfaceHeight = dch;
        if (log.isDebugEnabled()) log.debug("CONFIG updateSurface: ({},{})->({},{}) / formatCrop: ({},{}) / mEffectMode: {}", vw, vh, dcw, dch, cropW, cropH, mEffectMode);
    }

    public int getViewWidth() { return mSurfaceWidth; }
    public int getViewHeight() { return mSurfaceHeight; }
    public int getMarginLeft() { return mMarginLeft; }
    public int getMarginTop() { return mMarginTop; }

    /**
     * Sets the dataspace on the SurfaceView's SurfaceControl layer so SurfaceFlinger can set up
     * its HDR composition pipeline from the start, before MediaCodec produces any frames.
     * Without this, the surface starts as SDR (dataspace 259) and some HWC2 implementations
     * (e.g. Google TV Streamer) don't dynamically switch to HDR tone-mapping when the dataspace
     * changes later via the producer (MediaCodec).
     * @param dataSpace HAL dataspace constant (e.g. 0x10C00000 for BT2020_PQ, 0 to reset)
     */
    public void setSurfaceDataSpace(int dataSpace) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (log.isDebugEnabled()) log.debug("setSurfaceDataSpace: skipped, API {} < 33", Build.VERSION.SDK_INT);
            return;
        }
        if (mSurfaceView == null) {
            if (log.isDebugEnabled()) log.debug("setSurfaceDataSpace: skipped, mSurfaceView is null");
            return;
        }
        SurfaceControl sc = mSurfaceView.getSurfaceControl();
        if (sc == null || !sc.isValid()) {
            if (log.isDebugEnabled()) log.debug("setSurfaceDataSpace: skipped, SurfaceControl null or invalid");
            return;
        }
        new SurfaceControl.Transaction()
                .setDataSpace(sc, dataSpace)
                .apply();
        if (log.isDebugEnabled()) log.debug("setSurfaceDataSpace: applied dataSpace=0x{}", Integer.toHexString(dataSpace));
    }
}
