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
import android.provider.BaseColumns;

import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class AllTvshowsLoader extends VideoLoader {

    private static final String TAG = "AllTvshowsLoader";

    public final static String COLUMN_SEASON_COUNT = "season_count";
    public final static String COLUMN_EPISODE_COUNT = "episode_count";
    public final static String COLUMN_EPISODE_WATCHED_COUNT = "episode_watched_count";
    public final static String SORT_COUMN_LAST_ADDED = "max_date";
    private String mSortOrder;

    private boolean mShowWatched;

    /**
     * List all shows
     * @param context
     */
    public AllTvshowsLoader(Context context) {
        this(context, TvshowSortOrderEntries.DEFAULT_SORT, true);
    }

    public AllTvshowsLoader(Context context, String SortOrder, boolean showWatched) {
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
                VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + " AS " + BaseColumns._ID,
                VideoStore.Video.VideoColumns.SCRAPER_TITLE,
                VideoStore.Video.VideoColumns.SCRAPER_S_COVER,
                VideoStore.Video.VideoColumns.SCRAPER_S_POSTER_ID,
                VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
                VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED,
                VideoStore.Video.VideoColumns.SCRAPER_S_STUDIOS,
                VideoStore.Video.VideoColumns.SCRAPER_S_PLOT,
                VideoStore.Video.VideoColumns.SCRAPER_E_ACTORS,
                VideoStore.Video.VideoColumns.SCRAPER_S_RATING,
                "max(" + VideoStore.Video.VideoColumns.DATE_ADDED + ") AS " + SORT_COUMN_LAST_ADDED,
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + ") AS " + COLUMN_SEASON_COUNT,
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + " || ',' || " + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ") AS " + COLUMN_EPISODE_COUNT,
                "COUNT(CASE "+VideoStore.Video.VideoColumns.BOOKMARK+" WHEN "+PlayerActivity.LAST_POSITION_END+" THEN 1 ELSE NULL END) AS " + COLUMN_EPISODE_WATCHED_COUNT,
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
        sb.append( VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + " > '0'");
        if (!mShowWatched) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }
        sb.append(") GROUP BY (");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID);
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }
}
