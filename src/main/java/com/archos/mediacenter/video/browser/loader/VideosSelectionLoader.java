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
