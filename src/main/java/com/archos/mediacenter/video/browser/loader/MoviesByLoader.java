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

import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public abstract class MoviesByLoader extends CursorLoader implements CompatAndSDKCursorLoaderFactory {

    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_SUBSET_ID = "_id";
    public static final String COLUMN_LIST_OF_POSTER_FILES = "po_file_list";
    public static final String COLUMN_SUBSET_NAME = "name";
    public static final String COLUMN_LIST_OF_MOVIE_IDS = "list";
    public static final String COLUMN_NUMBER_OF_MOVIES = "number";
    protected static String COUNT = "COUNT(*) as "+COLUMN_COUNT;

    protected String mSortOrder;
    private boolean mForceHideVideos;

    public MoviesByLoader(Context context) {
        super(context);
        setUri(VideoStore.RAW_QUERY.buildUpon().appendQueryParameter("group",
                "CASE\n"+
                        "WHEN " + VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID + ">0 THEN " + VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID+"\n" +
                        " ELSE "+VideoStore.Video.VideoColumns._ID+"\n" +
                        "END"
        ).build());
        setSelectionArgs(null);
        // before VideoLoader.THROTTLE_DELAY_LONG but caused EpisodesByDateFragment/MoviesBy*Fragment to display no content (for long time?)
        if (VideoLoader.THROTTLE) setUpdateThrottle(VideoLoader.THROTTLE_DELAY);
    }

    abstract protected String getSelection(Context context);

    protected String getCommonSelection() {
        StringBuilder sb = new StringBuilder();

        if (LoaderUtils.mustHideUserHiddenObjects()) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);
        }

        if (LoaderUtils.mustHideWatchedVideo()||mForceHideVideos) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }

        return sb.toString();
    }

    public Loader<Cursor> getV4CursorLoader(boolean detailed, boolean hideWatchedVideos){
        mForceHideVideos = hideWatchedVideos;
        return new CursorLoader(getContext(),
                getUri(), getProjection(), getSelection(), getSelectionArgs(),
                getSortOrder());
    }
}
