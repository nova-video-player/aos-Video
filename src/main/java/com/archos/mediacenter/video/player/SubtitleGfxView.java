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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubtitleGfxView extends View {

    private static final Logger log = LoggerFactory.getLogger(SubtitleGfxView.class);

    // set to true to use the subtitle bounding box coordinates as they are in the file or false to center sub rectangle at bottom center of the screen
    // advantage of false is to allow repositioning and scaling but you loose possible shifted position to evade from text appearing on the screen
    // note that true conflicts with setting shift subs position up

    public final static boolean RECT_COORDINATES = true;
    private int mSize = -1;
    private int mDrawWidth;
    private int mDrawHeight;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mFrameWidth; // original width of gfx sub frame (1920 for pgs, 720 for vobsub)
    private int mFrameHeight; // original height of gfx sub frame (1080 for pgs, 576 for vobsub)
    private int mDrawX;
    private int mDrawY;
    private int mOriginalWidth;
    private int mOriginalHeight;
    private Bitmap mBitmap;
    private Paint mPaint;
    private Context mContext;
    private int mScreenDpi;
    private Rect mSubtitleOriginalBounds;
    private Rect mScaledSubtitlesBounds;
    private Rect mSubtitleOriginalRect;
    float mScaleFactor = 1.0f;
    int mVerticalMargin = 0;
    int mHorizontalMargin = 0;

    private Surface mExternalSurface = null;
    // Subtitle size is multiplied with a ratio, Range to be set here
    private static final double RATIO_MODIFIER_MIN = 0.5;
    private static final double RATIO_MODIFIER_MAX = 1.5;
    private static final double RATIO_MODIFIER_RANGE = RATIO_MODIFIER_MAX - RATIO_MODIFIER_MIN;

    // Used to adjust the size of the subtitles depending on the screen dpi
    // (the multiplication factor will be of 1.0 for the provided density)
    private static final int SCREEN_REFERENCE_DPI = 220;    // experimental value

    public SubtitleGfxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        log.debug("SubtitleGfxView onLayout: width=" + getWidth() + ", height=" + getHeight());
    }

    @Override
    public void setVisibility(int visibility) {
        log.debug("setVisibility: visibility={}", visibility);
        super.setVisibility(visibility);
        if (mExternalSurface != null)  {
            try {
                Canvas c = mExternalSurface.lockCanvas(null);
                c.save();
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                c.restore();
                mExternalSurface.unlockCanvasAndPost(c);
            } catch (Exception e) {
                log.error("setVisibility: cannot lock canvas!!!!");
            }
        } else {
            log.debug("setVisibility: no external surface");
        }
    }
    
    private void init() {
        log.debug("init");
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mPaint.setAntiAlias(true);

        // Get the screen density
        if (mContext instanceof PlayerActivity) {
            // Should always be the case
            DisplayMetrics metrics = new DisplayMetrics();
            ((PlayerActivity)mContext).getWindowManager().getDefaultDisplay().getMetrics(metrics);
            float density = metrics.density;
            mScreenDpi = metrics.densityDpi;
        }
        else {
            mScreenDpi = SCREEN_REFERENCE_DPI;
        }
    }
    
    public void setRenderingSurface(Surface s) {
        log.debug("setRenderingSurface: {}", s);
        mExternalSurface = s;
    }

    /**
     * Maps size [0..100] to [RATIO_MODIFIER_MIN..RATIO_MODIFIER_MAX]
     */
    private static double sizeToRatioModifier(int size) {
        // assure size [0..100]
        int tmp = size;
        if (tmp < 0)
            tmp = 0;
        if (tmp > 100)
            tmp = 100;

        return (tmp / 100.0) * RATIO_MODIFIER_RANGE + RATIO_MODIFIER_MIN;
    }

    public void setSubtitle(Bitmap bitmap, Rect subtitleOriginalBounds, int frameWidth, int frameHeight) {

        mBitmap = bitmap;
        mSubtitleOriginalBounds = subtitleOriginalBounds;
        mSubtitleOriginalRect = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        mOriginalWidth = subtitleOriginalBounds.right - subtitleOriginalBounds.left; // subtitle width using frameWidth resolution
        mOriginalHeight = subtitleOriginalBounds.bottom - subtitleOriginalBounds.top; // subtitle height using frameHeight resolution
        mFrameWidth = frameWidth;
        mFrameHeight = frameHeight;

        log.debug("setSubtitle: bitmap={}, subtitleOriginalBounds={}, frameWidth={}, frameHeight={}", bitmap == null ? "null" : "not null", subtitleOriginalBounds, frameWidth, frameHeight);
        if (mBitmap == null) {
            log.debug("setSubtitle: no bitmap, ignore");
            setVisibility(View.INVISIBLE);
            return;
        }

        //Rect mFrameBounds = new Rect(0, 0, frameWidth, frameHeight);
        //Rect mScreenBounds = new Rect(0, 0, mDisplayWidth, mDisplayHeight);
        //Rect mScaledFrameBounds;

        float frameRatio = mFrameWidth / (float) mFrameHeight;
        float screenRatio = mDisplayWidth / (float) mDisplayHeight;

        if (frameRatio > screenRatio) {
            // frame to be scaled to fill full width and centered in height with equal margin on top and bottom
            mScaleFactor = mDisplayWidth / (float) mFrameWidth;
            int scaledHeight = (int) (frameHeight * mScaleFactor); // Calculate the scaled height based on width
            mVerticalMargin = (mDisplayHeight - scaledHeight) / 2;
            //mScaledFrameBounds = new Rect(0, mVerticalMargin, mDisplayWidth, mVerticalMargin + scaledHeight);
            mScaledSubtitlesBounds = new Rect(
                    (int) (mSubtitleOriginalBounds.left * mScaleFactor),
                    mVerticalMargin + (int) (mSubtitleOriginalBounds.top * mScaleFactor),
                     (int) (mSubtitleOriginalBounds.right * mScaleFactor),
                    mVerticalMargin + (int) (mSubtitleOriginalBounds.bottom * mScaleFactor)
            );
        } else {
            // frame to be scaled to fill full height and centered in width with equal margin on left and right
            // covers portrait modes
            mScaleFactor = mDisplayHeight / (float) mFrameHeight;
            int scaledWidth = (int) (frameWidth * mScaleFactor); // Calculate the scaled width based on height
            mHorizontalMargin = (mDisplayWidth - scaledWidth) / 2;
            //mScaledFrameBounds = new Rect(mHorizontalMargin, 0, mHorizontalMargin + scaledWidth, mDisplayHeight);
            mScaledSubtitlesBounds = new Rect(
                    mHorizontalMargin + (int) (mSubtitleOriginalBounds.left * mScaleFactor),
                    (int) (mSubtitleOriginalBounds.top * mScaleFactor),
                    mHorizontalMargin + (int) (mSubtitleOriginalBounds.right * mScaleFactor),
                    (int) (mSubtitleOriginalBounds.bottom * mScaleFactor)
            );
        }

        log.debug("setSubtitle: ({}x{})->({},{}), mSubtitlesOriginalBounds={}, mScaledSubtitlesBounds={}, mScaleFactor={}, mVerticalMargin={}, mHorizontalMargin={}",
                mFrameWidth, mFrameHeight, mDisplayWidth, mDisplayHeight, mSubtitleOriginalBounds, mScaledSubtitlesBounds, mScaleFactor, mVerticalMargin, mHorizontalMargin);

        double ratio;
        if (mOriginalWidth > mOriginalHeight) {
            // Original size = landscape => compare the longest sizes
            int longestDisplaySize = (mDisplayWidth > mDisplayHeight) ? mDisplayWidth : mDisplayHeight;
            ratio = longestDisplaySize / (float) mOriginalWidth;
        }
        else {
            // Original size = portrait => compare the shortest sizes
            int shortestDisplaySize = (mDisplayWidth > mDisplayHeight) ? mDisplayHeight : mDisplayWidth;
            ratio = shortestDisplaySize / (float) mOriginalWidth;
        }

        if (RECT_COORDINATES) {
            mDrawWidth = (int) (mBitmap.getWidth() * mScaleFactor);
            mDrawHeight = (int) (mBitmap.getHeight() * mScaleFactor);
        } else {
            ratio *= sizeToRatioModifier(mSize);
            // Apply a multiplication factor to compensate the screen density
            if (mScreenDpi != SCREEN_REFERENCE_DPI) {
                ratio *= (double)mScreenDpi / (double)SCREEN_REFERENCE_DPI;
            }
            mDrawWidth = (int) (mOriginalWidth * mScaleFactor * ratio);
            mDrawHeight = (int) (mOriginalHeight * mScaleFactor * ratio);
        }
        mDrawX = mScaledSubtitlesBounds.left;
        mDrawY = RECT_COORDINATES ? mScaledSubtitlesBounds.top : 0;

        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
        }
        requestLayout();
        // need to Invalidate to force a refresh of this view
        postInvalidate();
    }

    /* Must be called in UI thread */
    public void remove() {
        log.debug("remove");
        mBitmap = null;
        setVisibility(View.INVISIBLE);
        // need to Invalidate to force a refresh of this view
        postInvalidate();
    }
    
    /**
     * @param size in Range [0..100]
     */
    public void setSize(int size, int displayWidth, int displayHeight) {
        log.debug("setSize: size={}, displayWidth={}, displayHeight={}", size, displayWidth, displayHeight);
        mSize = size;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        if (mBitmap != null) {
            setSubtitle(mBitmap, mSubtitleOriginalBounds, mFrameWidth, mFrameHeight);
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mBitmap == null) {
            setMeasuredDimension(0, 0);
        } else {
            if (RECT_COORDINATES) { // use full screen
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
            } else { // use subtitle size
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                        getPaddingTop() + getPaddingBottom() + mDrawHeight);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        log.debug("onDraw" + (mBitmap == null ? ": no bitmap" : "") + (mBitmap != null && mBitmap.isRecycled() ? ": bitmap is recycled" : ""));
        if (mBitmap != null && !mBitmap.isRecycled()) {
            Canvas c = canvas;
            if (mExternalSurface != null) {
                try {
                    Rect r = new Rect();
                    r = canvas.getClipBounds();
                    int [] location = new int[2];
                    getLocationOnScreen(location);
                    r.offsetTo(location[0],location[1]);
                    log.debug("onDraw: location={}, clipBounds={}", location, r);
                    c = mExternalSurface.lockCanvas(null);
                    c.save();
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    c.clipRect(r);
                    c.translate(location[0],location[1]);
                } catch (Exception e) {
                    log.error("onDraw: cannot lock canvas!!!!");
                }
            } else {
                log.debug("onDraw: no external surface");
            }

            log.debug("onDraw: draw bitmap at ({},{}) size ({},{})", mDrawX, mDrawY, mDrawWidth, mDrawHeight);
            c.drawBitmap(mBitmap, mSubtitleOriginalRect, mScaledSubtitlesBounds, mPaint);
            if (c != canvas) {
                c.restore();
                mExternalSurface.unlockCanvasAndPost(c);
            }
        } else {
            log.debug("onDraw: no bitmap to draw or bitmap is recycled");
        }
    }
}
