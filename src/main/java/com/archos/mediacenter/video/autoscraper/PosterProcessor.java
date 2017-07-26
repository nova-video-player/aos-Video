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

package com.archos.mediacenter.video.autoscraper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.archos.mediacenter.utils.BitmapUtils;
import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.LoadTaskItem;
import com.archos.mediacenter.utils.imageview.LoadResult.Status;

public class PosterProcessor extends ImageProcessor {
    private static final boolean DBG = false;
    private static final String TAG = PosterProcessor.class.getSimpleName();

    private final int mWidth;
    private final int mHeight;
    //private final int mLoadingColorFilter;

    public PosterProcessor(int width, int height/*, int loadingColorFilter*/) {
        mWidth = width;
        mHeight = height;
        //mLoadingColorFilter = loadingColorFilter;
    }

    @Override
    public void loadBitmap(LoadTaskItem taskItem) {
        if (taskItem.loadObject instanceof String) {
            String file = (String) taskItem.loadObject;
            Bitmap bm = BitmapFactory.decodeFile(file);
            if (bm != null) {
                // A valid poster is available => resize it to the expected size
                Bitmap scaled = BitmapUtils.scaleThumbnailCenterCrop(bm, mWidth, mHeight);
                taskItem.result.bitmap = scaled;
            }
            taskItem.result.status = (bm != null) ? Status.LOAD_OK : Status.LOAD_ERROR;
        } else {
            taskItem.result.status = Status.LOAD_BAD_OBJECT;
        }
        if (DBG) Log.d(TAG, "loadBitmap : " + taskItem.result.status.name());
    }

    @Override
    public String getKey(Object loadObject) {
        String key = loadObject instanceof String ? (String) loadObject : null;
        if (DBG) Log.d(TAG, "getKey : " + key);
        return key;
    }

    @Override
    public void setResult(ImageView imageView, LoadTaskItem taskItem) {
        if (DBG) Log.d(TAG, "setResult : " + taskItem.result.bitmap);
        imageView.setImageBitmap(taskItem.result.bitmap);
        imageView.clearColorFilter();
    }

    @Override
    public void setLoadingDrawable(ImageView imageView, Drawable drawable) {
        if (DBG) Log.d(TAG, "setLoadingDrawable");
        imageView.setImageDrawable(drawable);
        //imageView.setColorFilter(mLoadingColorFilter);
    }

    @Override
    public boolean canHandle(Object loadObject) {
        boolean ret = loadObject instanceof String;
        if (DBG) Log.d(TAG, "canHandle : " + loadObject + " = " + ret);
        return ret;
    }
}
