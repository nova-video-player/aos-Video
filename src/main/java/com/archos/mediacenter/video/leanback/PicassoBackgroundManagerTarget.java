/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.archos.mediacenter.video.leanback;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.leanback.app.BackgroundManager;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picasso target for updating default_background images
 */
public class PicassoBackgroundManagerTarget implements Target {
    private static final Logger log = LoggerFactory.getLogger(PicassoBackgroundManagerTarget.class);

    private final BackgroundManager mBackgroundManager;

    public PicassoBackgroundManagerTarget(BackgroundManager backgroundManager) {
        this.mBackgroundManager = backgroundManager;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
        if (!mBackgroundManager.isAttached() || bitmap == null) return;
        if (log.isDebugEnabled()) log.debug("backdrop bitmap passed to BackgroundManager");
        // BackgroundManager itself waits for an opaque activity layer before applying and fading
        // the bitmap. Do not duplicate that transition gating here.
        mBackgroundManager.setBitmap(bitmap);
    }

    @Override
    public void onBitmapFailed(Exception e, Drawable drawable) {
        setFallbackDrawable(drawable);
    }

    public void setFallbackDrawable(Drawable drawable) {
        if (mBackgroundManager.isAttached()) { // try to fix some cases of "java.lang.IllegalStateException: Must attach before setting background drawable"
            mBackgroundManager.setDrawable(drawable);
        }
    }

    @Override
    public void onPrepareLoad(Drawable drawable) {
        // Do nothing, default_background manager has its own transitions
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PicassoBackgroundManagerTarget that = (PicassoBackgroundManagerTarget) o;

        if (!mBackgroundManager.equals(that.mBackgroundManager))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return mBackgroundManager.hashCode();
    }
}
