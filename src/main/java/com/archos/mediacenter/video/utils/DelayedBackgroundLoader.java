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

package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.archos.mediacenter.utils.BitmapUtils;
import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.LoadTaskItem;
import com.archos.mediacenter.utils.imageview.LoadResult.Status;
import com.archos.mediascraper.ScraperImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DelayedBackgroundLoader extends ImageProcessor {

    private static final Logger log = LoggerFactory.getLogger(DelayedBackgroundLoader.class);

    private final Context mContext;
    private final long mSleep;
    private float mBackgroundAlpha;
    private String lastLoaded;

    public DelayedBackgroundLoader(Context context, long sleep, float backgroundAlpha) {
        mContext = context;
        mSleep = sleep;
        mBackgroundAlpha = backgroundAlpha;
    }

    @Override
    public void loadBitmap(LoadTaskItem taskItem) {
        if (log.isDebugEnabled()) log.debug("background:loadBitmap");
        long time = System.currentTimeMillis();
        if (taskItem.loadObject instanceof ScraperImage) {
            ScraperImage image = (ScraperImage) taskItem.loadObject;
            String file = image.getLargeFile();
            if (file != null) {
                image.download(mContext);
                try {
                    taskItem.result.bitmap = BitmapUtils.decodeSampledBitmapFromFile(file, 1920, 1080);
                    if (log.isDebugEnabled()) log.debug("background:loadBitmap - loaded.");
                } catch (OutOfMemoryError e) {
                    log.error("background:loadBitmap - No more memory!", e);
                }

                boolean needSleep = !file.equals(lastLoaded);
                if (needSleep && taskItem.result.bitmap != null) {
                    long sleep = mSleep - (System.currentTimeMillis() - time);
                    if (sleep > 0) try {
                        if (log.isDebugEnabled()) log.debug("background:loadBitmap - extra sleep of {}", sleep);
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        if (log.isDebugEnabled()) log.debug("DelayedBackgroundLoader interrupted in sleep()");
                    }
                }
            }
            taskItem.result.status = taskItem.result.bitmap != null ?
                    Status.LOAD_OK : Status.LOAD_ERROR;
        } else {
            taskItem.result.status = Status.LOAD_BAD_OBJECT;
        }
    }

    @Override
    public boolean canHandle(Object loadObject) {
        return loadObject instanceof ScraperImage;
    }

    @Override
    public String getKey(Object loadObject) {
        if (loadObject instanceof ScraperImage) {
            ScraperImage image = (ScraperImage) loadObject;
            // using the url here since images from same url may be used
            // as different files. But for cache reasons urls are a better
            // key
            return image.getLargeUrl();
        }
        return null;
    }

    @Override
    public void setResult(ImageView imageView, LoadTaskItem taskItem) {
        if (log.isDebugEnabled()) log.debug("setResult");
        boolean animate = true;
        if (taskItem.loadObject instanceof ScraperImage) {
            ScraperImage image = (ScraperImage) taskItem.loadObject;
            String file = image.getLargeFile();
            if (file != null && file.equals(lastLoaded)) {
                //!!!animate = false;
                if (log.isDebugEnabled()) log.debug("setResult animate false");
            }
            lastLoaded = file;
        }

        imageView.setImageBitmap(taskItem.result.bitmap);
        if (animate) {
            if (log.isDebugEnabled()) log.debug("   animate backdrop alpha {}", mBackgroundAlpha);
            imageView.animate().alpha(mBackgroundAlpha).setDuration(600);
        }
        else {
            if (log.isDebugEnabled()) log.debug("   set backdrop alpha {}", mBackgroundAlpha);
            imageView.setAlpha(mBackgroundAlpha);
        }
    }

    @Override
    public boolean handleLoadError(ImageView imageView, LoadTaskItem taskItem) {
        if (log.isDebugEnabled()) log.debug("set Null animated");
        if (imageView.getAlpha() > 0f)
            imageView.animate().alpha(0f).setDuration(300);
        lastLoaded = null;
        return true;
    }

    @Override
    public void setLoadingDrawable(ImageView imageView, Drawable drawable) {
        // don't want to overwrite the current drawable, so do nothing here.
    }
}
