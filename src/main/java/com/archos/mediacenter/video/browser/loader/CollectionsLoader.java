// Copyright 2020 Courville Software
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
import android.provider.BaseColumns;

import com.archos.mediacenter.video.collections.CollectionsSortOrderEntries;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

public class CollectionsLoader extends VideoLoader {

    private static final String TAG = "CollectionsLoader";

    public final static String COLUMN_COLLECTION_COUNT = "collection_count";
    public final static String COLUMN_COLLECTION_MOVIE_COUNT = "collection_movie_count";
    public final static String COLUMN_COLLECTION_MOVIE_WATCHED_COUNT = "collection_movie_watched_count";
    private String mSortOrder;

    private boolean mShowWatched;

    /**
     * List all movie collections
     * @param context
     */
    public CollectionsLoader(Context context) {
        this(context, CollectionsSortOrderEntries.DEFAULT_SORT, true);
    }

    public CollectionsLoader(Context context, String SortOrder, boolean showWatched) {
        super(context);
        mSortOrder = SortOrder;
        mShowWatched = showWatched;
        init();
    }

    @Override
    public String getSortOrder() {
        return mSortOrder;
    }

    @Override
    public String[] getProjection() {
        return new String[] {
                VideoStore.MediaColumns.DATA,
                VideoStore.Video.VideoColumns.SCRAPER_C_ID + " AS " + BaseColumns._ID,
                VideoStore.Video.VideoColumns.SCRAPER_C_NAME + " AS " + COLUMN_NAME,
                VideoStore.Video.VideoColumns.SCRAPER_C_DESCRIPTION,
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_C_ID + ") AS " + COLUMN_COLLECTION_COUNT,
                // TODO MARC check this one... supposed to be the number of movies in all collections or per cid... ???
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_C_ID + " || ',' || " + VideoStore.Video.VideoColumns.SCRAPER_M_IMDB_ID + ") AS " + COLUMN_COLLECTION_MOVIE_COUNT,
                "COUNT(CASE "+VideoStore.Video.VideoColumns.BOOKMARK+" WHEN "+ PlayerActivity.LAST_POSITION_END+" THEN 1 ELSE NULL END) AS " + COLUMN_COLLECTION_MOVIE_WATCHED_COUNT,
                VideoStore.Video.VideoColumns.SCRAPER_C_POSTER_LARGE_FILE,
                VideoStore.Video.VideoColumns.SCRAPER_C_POSTER_THUMB_FILE,
                VideoStore.Video.VideoColumns.SCRAPER_C_BACKDROP_LARGE_FILE,
                VideoStore.Video.VideoColumns.SCRAPER_C_BACKDROP_THUMB_FILE,
                getTraktProjection(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN),
                getTraktProjection(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY),
                VideoStore.Video.VideoColumns.NOVA_PINNED
        };
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append( VideoStore.Video.VideoColumns.SCRAPER_C_ID + " > '0'");
        if (!mShowWatched) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }
        sb.append(") GROUP BY (");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_C_ID);
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }
}
