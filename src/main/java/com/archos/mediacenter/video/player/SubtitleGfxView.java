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

public class SubtitleGfxView extends View {
    private String TAG = "GfxSubtitleView";
    private int mSize = -1;
    private int mDrawWidth;
    private int mDrawHeight;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mDrawX;
    private int mOriginalWidth;
    private int mOriginalHeight;
    private Bitmap mBitmap;
    private Paint mPaint;
    private Context mContext;
    private int mScreenDpi;

    private Surface mExternalSurface = null;
    // Subtitle size is multiplied with a ratio, Range to be set here
    private static final double RATIO_MODIFIER_MIN = 0.5;
    private static final double RATIO_MODIFIER_MAX = 1.5;
    private static final double RATIO_MODIFIER_RANGE = RATIO_MODIFIER_MAX - RATIO_MODIFIER_MIN;

    // Used to ajust the size of the subtitles depending on the screen dpi
    // (the multiplication factor will be of 1.0 for the provided density)
    private static final int SCREEN_REFERENCE_DPI = 220;    // experimental value

    public SubtitleGfxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mExternalSurface != null)  {
            try {
                Canvas c = mExternalSurface.lockCanvas(null);
                c.save();
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                c.restore();
                mExternalSurface.unlockCanvasAndPost(c);
            } catch (Exception e) {
            }
        }
    }
    
    private void init() {
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

    public void setSubtitle(Bitmap bitmap, int originalWidth, int originalHeight) {
        mBitmap = bitmap;
        mOriginalWidth = originalWidth;
        mOriginalHeight = originalHeight;
        if (mBitmap == null)
            return;

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

        ratio *= sizeToRatioModifier(mSize);

        // Apply a multiplication factor to compensate the screen density
        if (mScreenDpi != SCREEN_REFERENCE_DPI) {
            ratio *= (double)mScreenDpi / (double)SCREEN_REFERENCE_DPI;
        }
        
        mDrawWidth = (int) (mBitmap.getWidth() * ratio);
        mDrawHeight = (int) (mBitmap.getHeight() * ratio);
        mDrawX = (mDisplayWidth - mDrawWidth) / 2;

        setVisibility(View.VISIBLE);
        requestLayout();
        // need to Invalidate to force a refresh of this view
        postInvalidate();
    }

    /* Must be called in UI thread */
    public void remove() {
        mBitmap = null;
        setVisibility(View.INVISIBLE);
        // need to Invalidate to force a refresh of this view
        postInvalidate();
    }
    
    /**
     * @param size in Range [0..100]
     */
    public void setSize(int size, int displayWidth, int displayHeight) {
        mSize = size;
        mDisplayWidth = displayWidth;
        mDisplayHeight = displayHeight;
        if (mBitmap != null) {
            setSubtitle(mBitmap, mOriginalWidth, mOriginalHeight);
        }
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mBitmap == null) {
            setMeasuredDimension(0, 0);
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                                 getPaddingTop() + getPaddingBottom() + mDrawHeight);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap != null) {
            Canvas c = canvas;
            if (mExternalSurface != null) {
                try {
                    Rect r = new Rect();
                    r = canvas.getClipBounds();
                    int [] location = new int[2];
                    getLocationOnScreen(location);
                    r.offsetTo(location[0],location[1]);
                    c = mExternalSurface.lockCanvas(null);
                    c.save();
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                    c.clipRect(r);
                    c.translate(location[0],location[1]);
                } catch (Exception e) {
                    Log.d(TAG, "Can not lock canvas!!!!");
                }
            }

            Bitmap draw = Bitmap.createScaledBitmap(mBitmap, mDrawWidth, mDrawHeight, true);
            c.drawBitmap(draw, mDrawX, 0, mPaint);
            draw.recycle();
            if (c != canvas) {
                c.restore();
                mExternalSurface.unlockCanvasAndPost(c);
            }
        }
    }
}
