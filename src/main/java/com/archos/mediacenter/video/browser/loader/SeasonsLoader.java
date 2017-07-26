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
import android.util.Log;

import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class SeasonsLoader extends VideoLoader {

    private static final String TAG = "SeasonsLoader";

    public final static String COLUMN_EPISODE_TOTAL_COUNT = "episode_total_count";
    public final static String COLUMN_EPISODE_WATCHED_COUNT = "episode_watched_count";

    private final long mShowId;

    /**
     * List seasons of a given show
     * @param context
     * @param showId must be a VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID
     */
    public SeasonsLoader(Context context, long showId) {
        super(context);
        mShowId = showId;
        Log.d(TAG, "SeasonsLoader() "+mShowId);
        init();
    }

    @Override
    public String getSortOrder() {
        return VideoStore.Video.VideoColumns.SCRAPER_E_SEASON;
    }

    @Override
    public String[] getProjection() {
        Log.d(TAG, "getProjection mShowId = " + mShowId);

        return new String[] {
                VideoStore.MediaColumns.DATA,
                VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID,
                VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + " AS " + BaseColumns._ID,
                VideoStore.Video.VideoColumns.SCRAPER_TITLE,
                VideoLoader.COVER,
                VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
                "COUNT(DISTINCT " + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ") AS " + COLUMN_EPISODE_TOTAL_COUNT,
                "COUNT(CASE "+VideoStore.Video.VideoColumns.BOOKMARK+" WHEN "+PlayerActivity.LAST_POSITION_END+" THEN 1 ELSE NULL END) AS " + COLUMN_EPISODE_WATCHED_COUNT
        };

        // count() - count(CASE Archos_traktSeen WHEN 0 THEN 0 ELSE NULL END) AS watched,
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append( VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + "=?");
        sb.append(") GROUP BY (");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[] {
                Long.toString(mShowId)
        };

    }
}
