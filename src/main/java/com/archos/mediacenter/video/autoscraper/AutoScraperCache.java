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

/*-
 *   ^    |---------|
 *   |    |         |
 *   |    |---------|
 * Poster |      <--|---- Thumbnail
 *   |    |         |
 *   |    |---------|
 *   |    |         |
 *   v    |---------|
 *
 * - POSTER mode => full poster or the thumbnail completed with black padding above and below
 * - THUMBNAIL mode => full thumbnail
 */

package com.archos.mediacenter.video.autoscraper;

import com.archos.mediacenter.utils.BitmapUtils;
import com.archos.mediacenter.video.autoscraper.AutoScraperActivity.FileProperties;
import com.archos.mediaprovider.video.VideoStore.Video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.HashMap;


public class AutoScraperCache {
    private final static String TAG = "AutoScraperCache";
    private final static boolean DBG = false;

    public final static int TYPE_THUMBNAIL = 0;
    public final static int TYPE_POSTER = 1;

    private int mThumbnailWidth;
    private int mThumbnailHeight;
    private int mPosterWidth;
    private int mPosterHeight;
    private final Context mContext;

    private final HashMap<String, Bitmap> mThumbnailCache;
    private final HashMap<String, Bitmap> mPosterCache;


    public AutoScraperCache(Context context) {
        mContext = context;

        mThumbnailCache = new HashMap<String, Bitmap>();
        mPosterCache = new HashMap<String, Bitmap>();
    }

    public void setThumbnailSize(int width, int height) {
        if (DBG) Log.d(TAG, "setThumbnailSize : " + width + "x" + height);
        mThumbnailWidth = width;
        mThumbnailHeight = height;
    }

    public void setPosterSize(int width, int height) {
        if (DBG) Log.d(TAG, "setPosterSize : " + width + "x" + height);
        mPosterWidth = width;
        mPosterHeight = height;
    }

    public void clear(int type) {
        if (type == TYPE_THUMBNAIL) {
            if (DBG) Log.d(TAG, "clear thumbnail cache");
            mThumbnailCache.clear();
        }
        else {
            if (DBG) Log.d(TAG, "clear poster cache");
            mPosterCache.clear();
        }
    }

    public boolean containsThumbnail(String path) {
        return mThumbnailCache.containsKey(path);
    }

    public boolean containsPoster(String path) {
        return mPosterCache.containsKey(path);
    }

    public Bitmap getThumbnail(String path) {
        return mThumbnailCache.get(path);
    }

    public Bitmap getPoster(String path) {
        return mPosterCache.get(path);
    }

    public void buildThumbnail(int id, String path) {
        if (TextUtils.isEmpty(path) || mThumbnailCache.containsKey(path)) return;
        // Extract the thumbnail from the database
        Bitmap thumbnail = Video.Thumbnails.getThumbnail(mContext.getContentResolver(), id, Video.Thumbnails.MINI_KIND, null);
        if (thumbnail != null) {
            // Resize the thumbnail to the expected size
            Bitmap scaledThumbnail = BitmapUtils.scaleThumbnailCenterCrop(thumbnail, mThumbnailWidth, mThumbnailHeight);

            // Store the resized thumbnail in the cache
            mThumbnailCache.put(path, scaledThumbnail);
        }
    }

    public void buildPoster(FileProperties prop) {
        String posterPath = prop.posterPath;
        if (TextUtils.isEmpty(posterPath) || mPosterCache.containsKey(posterPath)) return;
        Bitmap posterBitmap = BitmapFactory.decodeFile(posterPath);
        if (posterBitmap != null) {
            // A valid poster is available => resize it to the expected size
            if (DBG) Log.d(TAG, "buildPoster : destination size=" + mPosterWidth + "x" + mPosterHeight);
            Bitmap scaledPoster = BitmapUtils.scaleThumbnailCenterCrop(posterBitmap, mPosterWidth, mPosterHeight);

            // Store the resized poster in the cache
            mPosterCache.put(posterPath, scaledPoster);
        }
    }

    public void putThumbnail(String path, Bitmap bitmap) {
        mThumbnailCache.put(path, bitmap);
    }

    public void putPoster(String path, Bitmap bitmap) {
        mPosterCache.put(path, bitmap);
    }
}
