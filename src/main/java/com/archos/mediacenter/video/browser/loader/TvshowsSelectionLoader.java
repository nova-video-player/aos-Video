// Copyright 2019 Courville Software
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

import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediaprovider.video.VideoStore;


public class TvshowsSelectionLoader extends VideoLoader {

    private static final String TAG = "TvshowsSelectionLoader";

    static public String DEFAULT_SORT = TvshowSortOrderEntries.DEFAULT_SORT;

    public final static String COLUMN_SEASON_COUNT = "season_count";
    public final static String COLUMN_EPISODE_COUNT = "episode_count";
    public final static String SORT_COUMN_LAST_ADDED = "max_date";
    public static final String COLUMN_NAME = "name";
    protected final String mListOfIds;
    private String mSortOrder;

    public static final String NAME = VideoStore.Video.VideoColumns.SCRAPER_S_NAME + " AS " + COLUMN_NAME;

    /**
     * List all shows with id in listOfTvshowsIds
     * @param context
     * @param listOfTvshowsIds
     */
    public TvshowsSelectionLoader(Context context, String listOfTvshowsIds) {
        this(context, listOfTvshowsIds, TvshowSortOrderEntries.DEFAULT_SORT);
    }

    public TvshowsSelectionLoader(Context context, String listOfIds, String SortOrder) {
        super(context);
        mListOfIds = listOfIds;
        mSortOrder = SortOrder;
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
                NAME,
                VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + " AS " + BaseColumns._ID,
                VideoStore.Video.VideoColumns.SCRAPER_TITLE,
                VideoStore.Video.VideoColumns.SCRAPER_S_COVER,
                VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
                VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED,
                VideoStore.Video.VideoColumns.SCRAPER_S_STUDIOS,
                VideoStore.Video.VideoColumns.SCRAPER_S_PLOT,
                VideoStore.Video.VideoColumns.SCRAPER_E_ACTORS,
                VideoStore.Video.VideoColumns.SCRAPER_S_RATING,
                "max(" + VideoStore.Video.VideoColumns.DATE_ADDED + ") AS " + SORT_COUMN_LAST_ADDED,
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + ") AS " + COLUMN_SEASON_COUNT,
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ") AS " + COLUMN_EPISODE_COUNT,
                getTraktProjection(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN),
                getTraktProjection(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY),
        };
    }

    protected static String getTraktProjection(String traktType) {
        return "CASE WHEN "
                + "TOTAL(" + traktType + ") >= "
                + "COUNT(" + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ") "
                + "THEN 1 ELSE 0 END AS " + traktType;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append( VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + " IN (" + mListOfIds + ")");
        sb.append(") GROUP BY (");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID);

        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }
}

