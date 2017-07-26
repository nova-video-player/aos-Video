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

package com.archos.mediacenter.video.browser.adapters.mappers;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class SeasonCursorMapper implements CompatibleCursorMapper {

    int mShowIdColumn;
    int mShowNameColumn;
    int mSeasonPosterPathColumn;
    int mSeasonNumberColumn;
    int mEpisodeTotalCountColumn;
    int mEpisodeWatchedCountColumn;

    public SeasonCursorMapper() {
    }

    @Override
    public void bindColumns(Cursor cursor) {
        mShowIdColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID);
        mShowNameColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
        mSeasonPosterPathColumn = cursor.getColumnIndexOrThrow(VideoLoader.COLUMN_COVER_PATH);
        mSeasonNumberColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
        mEpisodeTotalCountColumn = cursor.getColumnIndexOrThrow(SeasonsLoader.COLUMN_EPISODE_TOTAL_COUNT);
        mEpisodeWatchedCountColumn =  cursor.getColumnIndexOrThrow(SeasonsLoader.COLUMN_EPISODE_WATCHED_COUNT);
    }

    @Override
    public Object bind(Cursor cursor) {
        return new Season(
                cursor.getLong(mShowIdColumn),
                cursor.getString(mShowNameColumn),
                getPosterUri(cursor),
                cursor.getInt(mSeasonNumberColumn),
                cursor.getInt(mEpisodeTotalCountColumn),
                cursor.getInt(mEpisodeWatchedCountColumn)
        );
    }

    private Uri getPosterUri(Cursor c) {
        String path = c.getString(mSeasonPosterPathColumn);
        if (path!=null && !path.isEmpty()) {
            return Uri.parse("file://"+path);
        } else {
            return null;
        }
    }
}
