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
import android.graphics.Canvas;
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
    BackgroundManager mBackgroundManager;

    private static final Logger log = LoggerFactory.getLogger(PicassoBackgroundManagerTarget.class);

    public PicassoBackgroundManagerTarget(BackgroundManager backgroundManager) {
        log.debug("PicassoBackgroundManagerTarget:  set backgroundManager {}", backgroundManager);
        this.mBackgroundManager = backgroundManager;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
        if (this.mBackgroundManager.isAttached()) { // try to fix some cases of "java.lang.IllegalStateException: Must attach before setting background drawable"
            Bitmap newBitmap = bitmap.copy(bitmap.getConfig(), true);
            Canvas canvas = new Canvas(newBitmap);

            canvas.drawARGB(32, 0, 0, 0);
            log.debug("onBitmapLoaded: Setting new bitmap with size {}x{}", newBitmap.getWidth(), newBitmap.getHeight());
            this.mBackgroundManager.setBitmap(newBitmap);
        } else {
            log.warn("onBitmapLoaded: BackgroundManager is not attached, cannot set bitmap");
        }
    }

    @Override
    public void onBitmapFailed(Exception e, Drawable drawable) {
        if (this.mBackgroundManager.isAttached()) { // try to fix some cases of "java.lang.IllegalStateException: Must attach before setting background drawable"
            log.debug("onBitmapFailed: Setting drawable as background", e);
            this.mBackgroundManager.setDrawable(drawable);
        } else {
            log.warn("onBitmapFailed: BackgroundManager is not attached, cannot set drawable", e);
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
