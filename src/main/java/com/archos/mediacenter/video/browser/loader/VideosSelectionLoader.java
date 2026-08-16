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

package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.util.Log;

import androidx.loader.content.AsyncTaskLoader;

import com.archos.mediaprovider.video.VideoStore;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by vapillon on 10/04/15.
 */
public class VideosSelectionLoader extends MoviesLoader {

    private static final String TAG = "VideosSelectionLoader";

    protected final String mListOfIds;
    protected String mSortOrder;

    public VideosSelectionLoader(Context context, String listOfMoviesIds) {
        this(context, listOfMoviesIds, DEFAULT_SORT);
    }

    public VideosSelectionLoader(Context context, String listOfIds, String SortOrder) {
        super(context, true);
        // self introspection to use another Executor than AsyncTaskLoader which has 128 threads but a total queue of 10... cf. https://github.com/nova-video-player/aos-AVP/issues/141
        if (VideoLoader.VIDEOSELECTION_CUSTOM_EXECUTOR) {
            try {
                Field f = AsyncTaskLoader.class.getDeclaredField("mExecutor");
                f.setAccessible(true);
                f.set(this, VideoLoader.videoSelectionLoaderExecutor);
            } catch (NoSuchFieldException e) {
                Log.w(TAG, "VideoLoader caught NoSuchFieldException ", e);
            } catch (IllegalAccessException e) {
                Log.w(TAG, "VideoLoader caught IllegalAccessException ", e);
            }
        }
        if (VideoLoader.VIDEOSELECTION_THROTTLE) setUpdateThrottle(VideoLoader.VIDEOSELECTION_THROTTLE_DELAY);
        mListOfIds = listOfIds;
        mSortOrder = SortOrder;
        init();
    }

    @Override
    public String getSortOrder() {
        return mSortOrder;
    }

    @Override
    public String getSelection() {
        return VideoStore.Video.VideoColumns._ID + " IN (" + mListOfIds + ")";
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }

}
