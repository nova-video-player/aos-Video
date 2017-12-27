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
import android.os.AsyncTask;
import android.support.v17.leanback.app.BackgroundManager;
import android.util.DisplayMetrics;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediascraper.BaseTags;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.File;

/**
 * Argument can be a BaseTags instance. In that case it is directly used (very quick)
 * Argument can be a Video instance. In that case the BaseTags are computed (longer)
* Created by vapillon on 15/04/15.
*/
public class BackdropTask extends AsyncTask<Object, Integer, File> {
    private final Activity mContext;
    private final Target mBackgroundTarget;
    private final DisplayMetrics mMetrics;
    private final Drawable mDefaultBackground;

    public BackdropTask(Activity activity, int backgroundDefaultColor) {
        super();
        mContext = activity;
        mMetrics = new DisplayMetrics();
        mDefaultBackground = new ColorDrawable(backgroundDefaultColor);
        activity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        BackgroundManager backgroundManager = BackgroundManager.getInstance(activity);
        if(!backgroundManager.isAttached())
        backgroundManager.attach(activity.getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);
    }

    @Override
    protected File doInBackground(Object... objects) {
        if (objects[0]==null) {
            return null;
        }

        BaseTags tags = null;

        if (objects[0] instanceof BaseTags) {
            tags = (BaseTags)objects[0];
        }
        else if (objects[0] instanceof Base) {
            tags = ((Base)objects[0]).getFullScraperTags(mContext);
        }

        if (tags!=null) {
            return tags.downloadGetDefaultBackdropFile(mContext);
        } else {
            return null;
        }
    }

    @Override
    protected void onCancelled() {
        Picasso.get().cancelRequest(mBackgroundTarget);
        super.onCancelled();
    }

    @Override
    protected void onPostExecute(File file) {
        super.onPostExecute(file);
        if(isCancelled()||mContext.isDestroyed())
            return;
        // It is on purpose that we have the error case when file is null (like a fallback)
        if (file!=null) {
            Picasso.get()
                    .load(file)
                    .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                    .error(mDefaultBackground)
                    .into(mBackgroundTarget);
        }
        else {
            BackgroundManager.getInstance(mContext).setDrawable(mDefaultBackground);
        }
    }
}
