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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

import androidx.leanback.app.BackgroundManager;

import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediascraper.BaseTags;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;
import com.squareup.picasso.Callback;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Argument can be a BaseTags instance. In that case it is directly used (very quick)
 * Argument can be a Video instance. In that case the BaseTags are computed (longer)
* Created by vapillon on 15/04/15.
*/
public class BackdropTask {

    private static final Logger log = LoggerFactory.getLogger(BackdropTask.class);

    private final Activity mContext;
    private final PicassoBackgroundManagerTarget mBackgroundTarget;
    private final ImageView mBackdropView;
    private final DisplayMetrics mMetrics;
    private final Drawable mDefaultBackground;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean isCancelled = false;

    public BackdropTask(Activity activity, int backgroundDefaultColor) {
        if (log.isDebugEnabled()) log.debug("backdrop task created");
        mContext = activity;
        mDefaultBackground = new ColorDrawable(backgroundDefaultColor);
        mMetrics = activity.getResources().getDisplayMetrics();
        BackgroundManager backgroundManager = BackgroundManager.getInstance(activity);
        if (!backgroundManager.isAttached()) {
            backgroundManager.attach(activity.getWindow());
        }
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);
        mBackdropView = null;
        mCurrentFile = null;
    }

    private final File mCurrentFile;

    /**
     * Details-screen variant. The view is activity-owned and therefore remains a strong Picasso
     * target for the complete screen lifetime; it avoids BackgroundManager's fixed slow fade.
     */
    public BackdropTask(Activity activity, ImageView backdropView, int backgroundDefaultColor, File currentFile) {
        if (log.isDebugEnabled()) log.debug("details backdrop task created");
        mContext = activity;
        mDefaultBackground = new ColorDrawable(backgroundDefaultColor);
        mMetrics = activity.getResources().getDisplayMetrics();
        mBackgroundTarget = null;
        mBackdropView = backdropView;
        mCurrentFile = currentFile;
    }

    public BackdropTask(Activity activity, ImageView backdropView, int backgroundDefaultColor) {
        this(activity, backdropView, backgroundDefaultColor, null);
    }

    private volatile File mLoadedFile = null;

    public File getLoadedFile() {
        return mLoadedFile;
    }

    public BackdropTask execute(Object input) {
        if (log.isDebugEnabled()) log.debug("backdrop lookup started: source={}", input == null ? "null" : input.getClass().getSimpleName());
        executor.execute(() -> {
            File result = null;
            try {
                if (isCancelled || Thread.currentThread().isInterrupted()) return;
                result = doInBackground(input);
            } catch (Exception e) {
                log.error("backdrop lookup failed", e);
            } finally {
                executor.shutdown();
            }
            if (isCancelled) return;
            final File finalResult = result;
            handler.post(() -> {
                if (isCancelled || mContext.isDestroyed()) return;
                if (finalResult != null) {
                    boolean isSameFile = mCurrentFile != null && mCurrentFile.equals(finalResult);
                    if (isSameFile) {
                        mLoadedFile = finalResult;
                        if (log.isDebugEnabled()) log.debug("details backdrop is identical to current: retaining without reload file={}", finalResult.getPath());
                        if (mBackdropView != null) {
                            mBackdropView.setVisibility(View.VISIBLE);
                            mBackdropView.setAlpha(1f);
                        }
                        return;
                    }
                    if (log.isDebugEnabled()) log.debug("backdrop Picasso decode requested: file={}", finalResult.getPath());
                    com.squareup.picasso.RequestCreator request = Picasso.get()
                            .load(finalResult)
                            .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                            .transform(new BackdropDarkeningTransformation())
                            .error(mDefaultBackground);
                    if (mBackdropView != null) {
                        Picasso.get().cancelRequest(mBackdropView);
                        request.into(mBackdropView, new Callback() {
                            @Override
                            public void onSuccess() {
                                if (isCancelled || mContext.isDestroyed()) return;
                                mLoadedFile = finalResult;
                                if (log.isDebugEnabled()) log.debug("details backdrop bitmap displayed");
                                mBackdropView.animate().cancel();
                                mBackdropView.setVisibility(View.VISIBLE);
                                mBackdropView.setAlpha(0f);
                                mBackdropView.animate().alpha(1f).setDuration(180).start();
                            }

                            @Override
                            public void onError(Exception e) {
                                if (log.isDebugEnabled()) log.debug("details backdrop Picasso load failed", e);
                                clearBackdropView();
                            }
                        });
                    } else {
                        request.into(mBackgroundTarget);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("backdrop file unavailable; using fallback color");
                    if (mBackdropView != null) {
                        clearBackdropView();
                    } else if (mBackgroundTarget != null) {
                        mBackgroundTarget.setFallbackDrawable(mDefaultBackground);
                    }
                }
            });
        });
        return this;
    }

    private File doInBackground(Object input) {
        if (log.isDebugEnabled()) log.debug("backdrop lookup running");
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

    public void cancelTaskOnly() {
        isCancelled = true;
        if (log.isDebugEnabled()) log.debug("backdrop request cancelled (view preserved)");
        if (mBackdropView != null) {
            Picasso.get().cancelRequest(mBackdropView);
        } else if (mBackgroundTarget != null) {
            Picasso.get().cancelRequest(mBackgroundTarget);
        }
        executor.shutdownNow();
    }

    public void cancelAndClear() {
        isCancelled = true;
        if (log.isDebugEnabled()) log.debug("backdrop request cancelled and cleared");
        if (mBackdropView != null) {
            Picasso.get().cancelRequest(mBackdropView);
            clearBackdropView();
        } else if (mBackgroundTarget != null) {
            Picasso.get().cancelRequest(mBackgroundTarget);
        }
        executor.shutdownNow();
    }

    public void cancel() {
        cancelAndClear();
    }

    private void clearBackdropView() {
        if (mBackdropView == null) return;
        mBackdropView.animate().cancel();
        mBackdropView.setAlpha(0f);
        mBackdropView.setImageDrawable(null);
    }

    /** Applies the existing dark scrim while Picasso is still decoding, not on the UI thread. */
    public static final class BackdropDarkeningTransformation implements Transformation {
        @Override
        public Bitmap transform(Bitmap source) {
            Bitmap.Config config = source.getConfig() != null ? source.getConfig() : Bitmap.Config.ARGB_8888;
            Bitmap result = source.copy(config, true);
            Canvas canvas = new Canvas(result);
            canvas.drawARGB(32, 0, 0, 0);
            if (result != source) source.recycle();
            return result;
        }

        @Override
        public String key() {
            return "backdrop-darkening-v1";
        }
    }
}
