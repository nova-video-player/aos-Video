// Copyright 2026 Courville Software
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
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

public class SkipProgressDrawable extends Drawable {

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mHeight;
    private final float mInset;
    private final float mBottomInset;
    private final float mRadius;
    private int mAlpha = 255;

    public SkipProgressDrawable(Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        mHeight = 6f * density;
        mInset = 4f * density;
        mBottomInset = 5f * density;
        mRadius = mHeight / 2f;
        mPaint.setColor(accentColor(context));
        mPaint.setAlpha(mAlpha);
    }

    private static int accentColor(Context context) {
        final TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorAccent, tv, true)
                || context.getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
        }
        return 0xFFFFFFFF;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        final float left = bounds.left + mInset;
        final float right = bounds.right - mInset;
        if (right <= left) return;
        final float progress = getLevel() / 10000f;
        final float end = left + (right - left) * progress;
        if (end <= left) return;
        final float bottom = bounds.bottom - mBottomInset;
        canvas.drawRoundRect(left, bottom - mHeight, end, bottom, mRadius, mRadius, mPaint);
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        if (mAlpha == alpha) return;
        mAlpha = alpha;
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
