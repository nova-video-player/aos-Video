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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.widget.ImageView;

import androidx.annotation.IdRes;
import androidx.leanback.app.BackgroundManager;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the backdrop layer of a Leanback details activity.
 *
 * The bitmap is deliberately displayed in an activity-owned ImageView instead of through
 * BackgroundManager.  Leanback keeps a fixed-duration bitmap transition and can queue updates
 * across activity transitions; the dedicated layer gives all details screens the same predictable
 * 180 ms reveal and task lifecycle.
 */
public final class DetailsBackdropController {
    private static final Logger log = LoggerFactory.getLogger(DetailsBackdropController.class);

    private final Activity mActivity;
    private final @IdRes int mBackdropViewId;
    private int mFallbackColor;

    private BackdropTask mTask;
    private Object mRestoreInput;
    private File mCurrentlyDisplayedFile;
    private boolean mRequestStarted;
    private boolean mNeedsRestore;

    public DetailsBackdropController(Activity activity, @IdRes int backdropViewId, int fallbackColor) {
        mActivity = activity;
        mBackdropViewId = backdropViewId;
        mFallbackColor = fallbackColor;
    }

    /** Attaches Leanback only for the immediate flat fallback colour. */
    public void attach() {
        BackgroundManager backgroundManager = BackgroundManager.getInstance(mActivity);
        if (!backgroundManager.isAttached()) backgroundManager.attach(mActivity.getWindow());
        backgroundManager.setColor(mFallbackColor);
    }

    public void setFallbackColor(int color) {
        mFallbackColor = color;
        BackgroundManager.getInstance(mActivity).setColor(color);
    }

    /** Starts the first normal request for this visible details screen. */
    public void loadIfIdle(Object input) {
        if (mRequestStarted) return;
        mRequestStarted = true;
        start(input);
    }

    /** Replaces the image after an explicit content or backdrop selection change. */
    public void replace(Object input) {
        cancelCurrent();
        mRequestStarted = true;
        start(input);
    }

    /** Evaluates input for backdrop changes, retaining identical images and cross-fading different ones. */
    public void replaceIfDifferent(Object input) {
        if (mTask != null) {
            if (mTask.getLoadedFile() != null) {
                mCurrentlyDisplayedFile = mTask.getLoadedFile();
            }
            mTask.cancelTaskOnly();
            mTask = null;
        }
        mRequestStarted = true;
        start(input);
    }

    /** Cancels work when stopped and remembers the current input for a genuine restore. */
    public void onStop(boolean restoreNeeded, Object restoreInput) {
        if (mTask != null) {
            cancelCurrent();
            mNeedsRestore = restoreNeeded;
            mRestoreInput = restoreInput;
        }
        mRequestStarted = false;
    }

    /** Does nothing during normal first entry; only restores a stopped, still-live activity. */
    public void restoreIfNeeded() {
        if (!mNeedsRestore) return;
        mNeedsRestore = false;
        if (mRestoreInput != null) {
            if (log.isDebugEnabled()) log.debug("restoring details backdrop after stopped state");
            loadIfIdle(mRestoreInput);
        }
    }

    public void setCurrentlyDisplayedFile(File file) {
        mCurrentlyDisplayedFile = file;
    }

    public File getCurrentlyDisplayedFile() {
        if (mTask != null && mTask.getLoadedFile() != null) {
            mCurrentlyDisplayedFile = mTask.getLoadedFile();
        }
        return mCurrentlyDisplayedFile;
    }

    public void cancel() {
        cancelCurrent();
    }

    private void start(Object input) {
        ImageView backdropView = mActivity.findViewById(mBackdropViewId);
        if (backdropView == null) {
            log.error("details backdrop view {} is unavailable", mBackdropViewId);
            return;
        }
        if (mTask != null && mTask.getLoadedFile() != null) {
            mCurrentlyDisplayedFile = mTask.getLoadedFile();
        }
        mTask = new BackdropTask(mActivity, backdropView, mFallbackColor, mCurrentlyDisplayedFile) {
            @Override
            public BackdropTask execute(Object in) {
                super.execute(in);
                return this;
            }
        };
        mTask.execute(input);
    }

    private void cancelCurrent() {
        if (mTask != null) {
            if (mTask.getLoadedFile() != null) {
                mCurrentlyDisplayedFile = mTask.getLoadedFile();
            }
            mTask.cancel();
            mTask = null;
        }
    }
}
