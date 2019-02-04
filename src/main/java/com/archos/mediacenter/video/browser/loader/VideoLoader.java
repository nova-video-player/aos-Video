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
import android.content.CursorLoader;
import android.provider.MediaStore;

import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public abstract class VideoLoader extends CursorLoader implements CompatAndSDKCursorLoaderFactory {

    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_COVER_PATH = "cover";
    public static final String COLUMN_PLOT = "plot";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_RATING = "rating";

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
            DATE, RATING, PLOT,

            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,

            // Movie specific values
            VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID,

            // Episode specific values
            VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID,
            VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_NAME,
            VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
            VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE,
            VideoStore.Video.VideoColumns.SCRAPER_E_PICTURE
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

    public VideoLoader(Context context) {

        super(context);
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

    public android.support.v4.content.Loader getV4CursorLoader(boolean detailed, boolean hideWatchedVideos){
        mIsDetailed = detailed;
        mForceHideVideos = hideWatchedVideos;
        return  new android.support.v4.content.CursorLoader(getContext(),
                getUri(), getProjection(), getSelection(), getSelectionArgs(),
                getSortOrder());
    }

}
