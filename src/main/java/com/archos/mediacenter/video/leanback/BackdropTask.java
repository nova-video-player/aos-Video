// Copyright 2017 Archos SA and 2024 Courville Software
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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import androidx.leanback.app.BackgroundManager;

import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediascraper.BaseTags;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Argument can be a BaseTags instance. In that case it is directly used (very quick)
 * Argument can be a Video instance. In that case the BaseTags are computed (longer)
* Created by vapillon on 15/04/15.
*/
public class BackdropTask {

    private static final Logger log = LoggerFactory.getLogger(BackdropTask.class);

    private final Activity mContext;
    private final Target mBackgroundTarget;
    private final DisplayMetrics mMetrics;
    private final Drawable mDefaultBackground;
    private final ExecutorService mExecutor;
    private Future<?> mFuture;
    private final BackgroundManager mBackgroundManager;

    public BackdropTask(Activity activity, int backgroundDefaultColor) {
        log.debug("BackdropTask");
        mContext = activity;
        mMetrics = new DisplayMetrics();
        mDefaultBackground = new ColorDrawable(backgroundDefaultColor);
        activity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        mBackgroundManager = BackgroundManager.getInstance(activity);
        if(!mBackgroundManager.isAttached()) {
            log.debug("BackdropTask: backgroundManager not yet attached");
            mBackgroundManager.attach(activity.getWindow());
        } else {
            log.debug("BackdropTask: backgroundManager already attached");
        }
        mBackgroundTarget = new PicassoBackgroundManagerTarget(mBackgroundManager);
        mExecutor = Executors.newSingleThreadExecutor();
        mFuture = null;
    }

    public void execute(final Object... objects) {
        if (mFuture != null) {
            mFuture.cancel(true);
            log.warn("execute: cancelling previous task");
        }
        mFuture = mExecutor.submit(() -> {
            final File file = getBackdropFile(objects.length > 0 ? objects[0] : null);
            mContext.runOnUiThread(() -> {
                // It is on purpose that we have the error case when file is null (like a fallback)
                if (file != null) {
                    log.debug("execute: file " + file.getPath());
                    Picasso.get()
                            .load(file)
                            .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                            .error(mDefaultBackground)
                            .into(mBackgroundTarget);
                } else {
                    log.debug("execute: file is null, default background");
                    mBackgroundManager.setDrawable(mDefaultBackground);
                }
            });
        });
    }

    private File getBackdropFile(Object obj) {
        if (obj == null) {
            return null;
        }
        BaseTags tags = null;
        if (obj instanceof Collection) {
            // when dealing with collection, it has already been scraped and backdrop downloaded
            Collection collection = (Collection) obj;
            log.debug("getBackdropFile: collection " + collection.getBackdropUri());
            if (collection.getBackdropUri() == null) return null;
            return new File(collection.getBackdropUri().getPath());
        } else if (obj instanceof BaseTags) {
            tags = (BaseTags) obj;
            log.debug("getBackdropFile: basetag " + tags.getBackdrops());
        } else if (obj instanceof Base) {
            tags = ((Base) obj).getFullScraperTags(mContext);
            log.debug("getBackdropFile: base " + tags.getBackdrops());
        }
        return tags != null ? tags.downloadGetDefaultBackdropFile(mContext) : null;
    }

    public void cancel(boolean releaseBackgroundManager) {
        log.debug("cancel: " + releaseBackgroundManager);
        if (mFuture != null && !mFuture.isDone()) mFuture.cancel(true);
        Picasso.get().cancelRequest(mBackgroundTarget);
        if (releaseBackgroundManager) releaseBackgroundManager();
    }

    private void releaseBackgroundManager() {
        if (mBackgroundManager != null && mBackgroundManager.isAttached()) {
            mBackgroundManager.release();
        }
    }

    public boolean isDone() {
        if (mFuture == null) return true;
        else return mFuture.isDone();
    }
}
