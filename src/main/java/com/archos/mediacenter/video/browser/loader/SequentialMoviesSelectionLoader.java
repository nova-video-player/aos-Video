// Copyright 2022 Courville Software
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

public class SequentialMoviesSelectionLoader extends MoviesSelectionLoader {

    private static final String TAG = "SequentialMoviesSelectionLoader";

    // cf. https://github.com/nova-video-player/aos-AVP/issues/141
    // limit to 1 thread for less epileptic visual effect and a queue of 5200 = 100 years of 52 weeks --> update no need to specify capacity
    // Note that now it is handled by androidx and should be "bug free" --> remove this hack for now --> need to put it back
    // For ref sake currently cursorLoader executor by default is ThreadPoolExecutor(5, 128, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(10), tocheck)

    private final static Executor moviesLoaderExecutor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    public SequentialMoviesSelectionLoader(Context context, String listOfMoviesIds) {
        super(context, listOfMoviesIds);
        // self introspection to use another Executor than AsyncTaskLoader which has 128 threads but a total queue of 10... cf. https://github.com/nova-video-player/aos-AVP/issues/141
        try {
            Field f = AsyncTaskLoader.class.getDeclaredField("mExecutor");
            f.setAccessible(true);
            f.set(this, moviesLoaderExecutor);
        }  catch (NoSuchFieldException e) {
            Log.w(TAG, "SequentialVideoLoader caught NoSuchFieldException ", e);
        } catch (IllegalAccessException e) {
            Log.w(TAG, "SequentialVideoLoader caught IllegalAccessException ", e);
        }
    }

    public SequentialMoviesSelectionLoader(Context context, String listOfIds, String SortOrder) {
        super(context, listOfIds, SortOrder);
    }

}
