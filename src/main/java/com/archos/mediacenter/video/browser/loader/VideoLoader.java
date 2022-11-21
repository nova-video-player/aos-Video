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

package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;

import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by vapillon on 10/04/15.
 */
public abstract class VideoLoader extends CursorLoader implements CompatAndSDKCursorLoaderFactory {

    private static final String TAG = "VideoLoader";

    public static final boolean THROTTLE = true;
    public static final int THROTTLE_DELAY = 5000; // 5s
    public static final int THROTTLE_DELAY_LONG = 3600000; // 1h

    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_COVER_PATH = "cover";
    public static final String COLUMN_PLOT = "plot";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_RATING = "rating";
    public static final String COLUMN_CONTENT_RATING = "content_rating";

    public static final String DEFAULT_SORT = COLUMN_NAME + ", "
            + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + ", "
            + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE;

    protected static final String COALESCE = "COALESCE(";
    protected static final String COVER = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_COVER + ",'') AS " + COLUMN_COVER_PATH;
    protected static final String DATE = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_M_YEAR + ","
            + VideoStore.Video.VideoColumns.SCRAPER_E_AIRED + ") as " + COLUMN_DATE;
    public static final String NAME = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_TITLE
            + "," + VideoStore.MediaColumns.TITLE + ") AS " + COLUMN_NAME;
    protected static final String RATING = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_RATING + ",'-1')" + " AS " + COLUMN_RATING;
    protected static final String CONTENT_RATING = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_CONTENT_RATING + ",'')" + " AS " + COLUMN_CONTENT_RATING;
    protected static final String PLOT = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_M_PLOT + ","
            + VideoStore.Video.VideoColumns.SCRAPER_E_PLOT + ") as " + COLUMN_PLOT;

    private static final String DETAIL_LINE_ONE = COALESCE + "'%s ' ||"
            + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + " ||' %s ' ||"
            + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ",'%s ' ||"
            + VideoStore.Video.VideoColumns.SCRAPER_DIRECTORS + ") AS detail_line_one";
    private static final String DETAIL_LINE_TWO = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_E_NAME + ", "
            + VideoStore.Video.VideoColumns.SCRAPER_M_PLOT + ") AS detail_line_two";
    private static final String DETAIL_LINE_THREE = COALESCE
            + VideoStore.Video.VideoColumns.SCRAPER_E_PLOT + ", '%s ' ||"
            + VideoStore.Video.VideoColumns.SCRAPER_ACTORS + ") AS detail_line_three";
    protected static String COUNT = "COUNT(*) as "+COLUMN_COUNT;

    public static String[] mProjection = {
            VideoStore.Video.VideoColumns._ID,
            "poster_id",
            // Columns for all video files
            VideoStore.Video.VideoColumns.DATA,
            NAME,

            VideoStore.Video.VideoColumns.BOOKMARK,
            VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK,
            VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED,
            COVER,
            VideoStore.Video.VideoColumns.DURATION,
            VideoStore.Video.VideoColumns.SIZE,
            VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN,
            VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY,
            VideoStore.Video.VideoColumns.ARCHOS_VIDEO_STEREO,
            VideoStore.Video.VideoColumns.ARCHOS_VIDEO_DEFINITION,
            VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER,
            VideoStore.Video.VideoColumns.SUBTITLE_COUNT_EXTERNAL,
            VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID,
            // Columns common to Movies and TVShows
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
            VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_TITLE,
            VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_URL,
            VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_FILE,
            DATE, RATING, CONTENT_RATING, PLOT,

            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,

            // Movie specific values
            VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_C_ID,

            // Episode specific values
            VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID,
            VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_NAME,
            VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
            VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE,
            VideoStore.Video.VideoColumns.SCRAPER_E_PICTURE,

            VideoStore.Video.VideoColumns.NOVA_PINNED
    };
    public static String[] mDetailedProjection = concatTwoStringArrays(mProjection,new String[] {
            DETAIL_LINE_ONE,DETAIL_LINE_TWO, DETAIL_LINE_THREE,
            VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT,
            VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_VIDEO_FORMAT,
            VideoStore.Video.VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT,
            VideoStore.Video.VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT,
            VideoStore.Video.VideoColumns.ARCHOS_VIDEO_BITRATE,
            VideoStore.Video.VideoColumns.ARCHOS_FRAMES_PER_THOUSAND_SECONDS,
            MediaStore.Video.VideoColumns.HEIGHT,
            MediaStore.Video.VideoColumns.WIDTH

    });
    protected boolean mIsDetailed;
    private boolean mForceHideVideos;
    // used by BrowserAllTVShow and BrowserBySeason

    public static String[] concatTwoStringArrays(String[] s1, String[] s2){
        String[] result = new String[s1.length+s2.length];
        int i;
        for (i=0; i<s1.length; i++)
            result[i] = s1[i];
        int tempIndex =s1.length;
        for (i=0; i<s2.length; i++)
            result[tempIndex+i] = s2[i];
        return result;
    }
    protected static String getTraktProjection(String traktType) {
        return "CASE WHEN "
                + "TOTAL(" + traktType + ") >= "
                + "COUNT(" + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ") "
                + "THEN 1 ELSE 0 END AS " + traktType;
    }

    // cf. https://github.com/nova-video-player/aos-AVP/issues/141
    // limit to 1 thread for less epileptic visual effect and a queue of 5200 = 100 years of 52 weeks
    // Note that now it is handled by androidx and should be "bug free" --> remove this hack for now
    // For ref sake currently cursorLoader executor by default is ThreadPoolExecutor(5, 128, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(10), tocheck)
    private final static Executor videoLoaderExecutor = new ThreadPoolExecutor(5, 128, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>());

    public VideoLoader(Context context) {
        super(context);
        // self introspection to use another Executor than AsyncTaskLoader which has 128 threads but a total queue of 10... cf. https://github.com/nova-video-player/aos-AVP/issues/141
        try {
            Field f = AsyncTaskLoader.class.getDeclaredField("mExecutor");
            f.setAccessible(true);
            f.set(this, videoLoaderExecutor);
        }  catch (NoSuchFieldException e) {
            Log.w(TAG, "VideoLoader caught NoSuchFieldException ", e);
        } catch (IllegalAccessException e) {
            Log.w(TAG, "VideoLoader caught IllegalAccessException ", e);
        }
    }

    @Override
    public String[] getProjection() {
        if(!mIsDetailed)
            return mProjection;
        else
            return mDetailedProjection;
    }

    /** MUST be called at the end of the constructor.
     * Using this "trick" to allow child classes to initialize their own variable in their constructor
     */
    protected void init() {
        setUri(VideoStore.Video.Media.EXTERNAL_CONTENT_URI);
        setProjection(getProjection());
        setSelection(getSelection());
        setSelectionArgs(getSelectionArgs());
        setSortOrder(getSortOrder());
        // TODO check if this has no perf penalty it is for https://github.com/nova-video-player/aos-AVP/issues/134
        // only updates the CursorLoader on data change every 2s
        // disable for now and only deal with it in master loaders i.e. LastAddedLoader and LaspPlayedLoader NonScrapedVideosCountLoader
        //setUpdateThrottle(2000);
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();

        if (LoaderUtils.mustHideUserHiddenObjects()) {
            sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);
        }

        if (LoaderUtils.mustHideWatchedVideo()||mForceHideVideos) {
            if (sb.length()>0) { sb.append(" AND "); }
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }

        return sb.toString();
    }

    public Loader<Cursor> getV4CursorLoader(boolean detailed, boolean hideWatchedVideos){
        mIsDetailed = detailed;
        mForceHideVideos = hideWatchedVideos;
        return  new CursorLoader(getContext(),
                getUri(), getProjection(), getSelection(), getSelectionArgs(),
                getSortOrder());
    }

}
