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

    public static final boolean CUSTOM_EXECUTOR = true;
    public static final boolean THROTTLE = false;
    public static final int THROTTLE_DELAY = 5000; // 5s

    protected final String mListOfIds;
    protected String mSortOrder;

    public VideosSelectionLoader(Context context, String listOfMoviesIds) {
        this(context, listOfMoviesIds, DEFAULT_SORT);
    }

    // cf. https://github.com/nova-video-player/aos-AVP/issues/141
    // limit to 1 thread for less epileptic visual effect when loading categories for VideoByFragment (make categories to appear linearly) and use a queue of 5200 = 100 years of 52 weeks
    // For ref sake currently ModernAsyncTask default cursorLoader executor is ThreadPoolExecutor(5, 128, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(10), tocheck)
    // In the past used to be ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(5200));
    // i.e. limit to 1 thread for less epileptic visual effect and a queue of 5200 = 100 years of 52 weeks

    //private final static Executor loaderExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
    //        new LinkedBlockingQueue<Runnable>(5200));
    //private final static Executor loaderExecutor = new ThreadPoolExecutor(1, 100, 20, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(256));
    private final static Executor loaderExecutor = new ThreadPoolExecutor(1, 4, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(256));


    public VideosSelectionLoader(Context context, String listOfIds, String SortOrder) {
        super(context, true);
        // self introspection to use another Executor than AsyncTaskLoader which has 128 threads but a total queue of 10... cf. https://github.com/nova-video-player/aos-AVP/issues/141
        if (CUSTOM_EXECUTOR) {
            try {
                Field f = AsyncTaskLoader.class.getDeclaredField("mExecutor");
                f.setAccessible(true);
                f.set(this, loaderExecutor);
            } catch (NoSuchFieldException e) {
                Log.w(TAG, "VideoLoader caught NoSuchFieldException ", e);
            } catch (IllegalAccessException e) {
                Log.w(TAG, "VideoLoader caught IllegalAccessException ", e);
            }
        }
        if (THROTTLE) setUpdateThrottle(THROTTLE_DELAY);
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
