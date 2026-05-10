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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.leanback.app.BackgroundManager;

import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediascraper.BaseTags;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Argument can be a BaseTags instance. In that case it is directly used (very quick)
 * Argument can be a Video instance. In that case the BaseTags are computed (longer)
* Created by vapillon on 15/04/15.
*/
public class BackdropTask {

    private static final boolean DBG = false;
    private static final String TAG = "BackdropTask";

    private final Activity mContext;
    private final Target mBackgroundTarget;
    private final DisplayMetrics mMetrics;
    private final Drawable mDefaultBackground;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean isCancelled = false;

    public BackdropTask(Activity activity, int backgroundDefaultColor) {
        if (DBG) Log.d(TAG, "BackdropTask");
        mContext = activity;
        mMetrics = new DisplayMetrics();
        mDefaultBackground = new ColorDrawable(backgroundDefaultColor);
        activity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        BackgroundManager backgroundManager = BackgroundManager.getInstance(activity);
        if(!backgroundManager.isAttached())
        backgroundManager.attach(activity.getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);
    }

    public BackdropTask execute(Object input) {
        executor.execute(() -> {
            File result = null;
            try {
                if (isCancelled || Thread.currentThread().isInterrupted()) return;
                result = doInBackground(input);
            } catch (Exception e) {
                Log.e(TAG, "BackdropTask failed", e);
            } finally {
                executor.shutdown();
            }
            if (isCancelled) return;
            final File finalResult = result;
            handler.post(() -> {
                if (isCancelled || mContext.isDestroyed()) return;
                if (finalResult != null) {
                    if (DBG) Log.d(TAG, "onPostExecute: file " + finalResult.getPath());
                    Picasso.get()
                            .load(finalResult)
                            .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                            .error(mDefaultBackground)
                            .into(mBackgroundTarget);
                } else {
                    if (DBG) Log.d(TAG, "onPostExecute: file is null, default background");
                    BackgroundManager.getInstance(mContext).setDrawable(mDefaultBackground);
                }
            });
        });
        return this;
    }

    private File doInBackground(Object input) {
        if (DBG) Log.d(TAG, "doInBackground");

        if (input == null) {
            return null;
        }

        BaseTags tags = null;

        if (input instanceof Collection) {
            // when dealing with collection, it has already been scraped and backdrop downloaded
            Collection collection = (Collection) input;
            if (collection.getBackdropUri() == null) return null;
            return new File(collection.getBackdropUri().getPath());
        }
        else if (input instanceof BaseTags) {
            tags = (BaseTags) input;
        }
        else if (input instanceof Base) {
            tags = ((Base) input).getFullScraperTags(mContext);
        }

        if (tags != null) {
            return tags.downloadGetDefaultBackdropFile(mContext);
        } else {
            return null;
        }
    }

    public void cancel() {
        isCancelled = true;
        Picasso.get().cancelRequest(mBackgroundTarget);
        executor.shutdownNow();
    }
}
