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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Surface;

import androidx.appcompat.widget.AppCompatTextView;

public class SubtitleTextView extends AppCompatTextView {

    private static final String TAG = "SubtitleTextView";
   
    private Surface mExternalSurface = null;

    private static boolean mOutline = false;
    private final Paint mBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mShowBackground;
    private int mBgOpacity;

    private Rect r = new Rect();
    private int[] location = new int[2];
    private final RectF mBgRect = new RectF();

    public SubtitleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mBgPaint.setStyle(Paint.Style.FILL);
        mBgPaint.setColor(android.graphics.Color.argb(128, 0, 0, 0));
        setLineSpacing(0f, 1.15f);
    }

    public void setBackgroundState(boolean show) { 
        mShowBackground = show; 
        invalidate(); 
    }

    public void setBackgroundOpacity(int opacity) { 
        mBgOpacity = opacity;
        mBgPaint.setAlpha(mBgOpacity);
        invalidate(); 
    }

    public void setOutlineState(boolean outline) { mOutline = outline; }
    
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
            } catch (Exception ignored) {
            }
        }
    }
    
    public void setRenderingSurface(Surface s) {
        mExternalSurface = s;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Canvas c = canvas;
        if (mExternalSurface != null) {
            try {
                canvas.getClipBounds(r);
                getLocationOnScreen(location);
                r.offsetTo(location[0],location[1]);
                c = mExternalSurface.lockCanvas(null);
                c.save();
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                c.clipRect(r);
                c.translate(location[0],location[1]);
            } catch (Exception ignored) {
            }
        }
        
        // Draw black background behind subtitle text line-by-line
        if (mShowBackground && getLayout() != null) {
            android.text.Layout layout = getLayout();
            
            float padX = getTextSize() * 0.25f;
            float padY = getTextSize() * 0.02f;
            float radius = getTextSize() * 0.15f;
            float gapY = getTextSize() * 0.08f;
            float offsetX = getTotalPaddingLeft() + getScrollX();
            float offsetY = getTotalPaddingTop() + getScrollY();

            int lastTextLine = -1;
            for (int i = 0; i < layout.getLineCount(); i++) {
                if (layout.getLineRight(i) > layout.getLineLeft(i)) {
                    lastTextLine = i;
                }
            }

            for (int i = 0; i < layout.getLineCount(); i++) {
                float left = layout.getLineLeft(i);
                float right = layout.getLineRight(i);
                
                if (right > left) {
                    float top = layout.getLineTop(i);
                    float bottom = layout.getLineBottom(i);
                    // Reduce the bottom of all lines EXCEPT the last one
                    if (i < lastTextLine) {
                        bottom -= gapY; 
                    }
                    mBgRect.set(
                        left + offsetX - padX,
                        top + offsetY - padY,
                        right + offsetX + padX,
                        bottom + offsetY + padY
                    );

                    c.drawRoundRect(mBgRect, radius, radius, mBgPaint); 
                }
            }
        }

        // disable for now nice outline since it is too slow on lowend CPU/GPU (e.g. RK3128)
        if (mOutline) {
            TextPaint paint = getPaint();
            int color = getCurrentTextColor();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.MITER);
            paint.setStrokeMiter(1.0f);
            paint.setStrokeWidth(4.0f);
            setTextColor(Color.BLACK);
            super.onDraw(c);
            paint.setStyle(Paint.Style.FILL);
            setTextColor(color);
        }
        super.onDraw(c);
        if (c != canvas) {
            c.restore();
            mExternalSurface.unlockCanvasAndPost(c);
        }
    }
}