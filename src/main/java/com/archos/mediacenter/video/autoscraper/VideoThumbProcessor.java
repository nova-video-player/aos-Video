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

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.archos.mediacenter.utils.BitmapUtils;
import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.LoadTaskItem;
import com.archos.mediacenter.utils.imageview.LoadResult.Status;
import com.archos.mediaprovider.video.VideoStore.Video;

public class VideoThumbProcessor extends ImageProcessor {
    private static final boolean DBG = false;
    private static final String TAG = VideoThumbProcessor.class.getSimpleName();

    private final int mWidth;
    private final int mHeight;
    private final int mLoadingColorFilter;
    private final ContentResolver mCr;

    public VideoThumbProcessor(ContentResolver cr, int width, int height, int loadingColorFilter) {
        mCr = cr;
        mWidth = width;
        mHeight = height;
        mLoadingColorFilter = loadingColorFilter;
    }

    @Override
    public void loadBitmap(LoadTaskItem taskItem) {
        if (taskItem.loadObject instanceof Long) {
            long id = ((Long) taskItem.loadObject).longValue();
            Bitmap bm = Video.Thumbnails.getThumbnail(mCr, id, Video.Thumbnails.MINI_KIND, null);
            if (bm != null) {
                // A valid thumb is available => resize it to the expected size
                if (DBG) Log.d(TAG, "Thumbnail : destination size=" + mWidth + "x" + mHeight);
                Bitmap scaled = BitmapUtils.scaleThumbnailCenterCrop(bm, mWidth, mHeight);
                taskItem.result.bitmap = scaled;
            }
            taskItem.result.status = bm != null ? Status.LOAD_OK : Status.LOAD_ERROR;
        } else {
            taskItem.result.status = Status.LOAD_BAD_OBJECT;
        }
    }

    @Override
    public boolean canHandle(Object loadObject) {
        return loadObject instanceof Long;
    }

    @Override
    public String getKey(Object loadObject) {
        return loadObject instanceof Long ? String.valueOf(loadObject) : null;
    }

    @Override
    public void setResult(ImageView imageView, LoadTaskItem taskItem) {
        imageView.setImageBitmap(taskItem.result.bitmap);
        imageView.clearColorFilter();
    }

    @Override
    public void setLoadingDrawable(ImageView imageView, Drawable drawable) {
        imageView.setImageDrawable(drawable);
        imageView.setColorFilter(mLoadingColorFilter);
    }

}
