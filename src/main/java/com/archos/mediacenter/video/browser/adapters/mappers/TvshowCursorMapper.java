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

import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class TvshowCursorMapper implements CompatibleCursorMapper {

    int mIdColumn;
    int mNameColumn;
    int mPosterPathColumn;
    int mSeasonCountColumn;
    int mEpisodeCountColumn;
    int mTraktSeenColumn;
    int mTraktLibraryColumn;
    int mActorsColumn;
    int mYearColumn;
    int mPlotColumn;
    int mStudioColumn;
    int mRatingColumn;
    public TvshowCursorMapper() {
    }


    public void bindColumns(Cursor cursor) {
        mIdColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        mNameColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
        mPosterPathColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_S_COVER);
        mSeasonCountColumn = cursor.getColumnIndexOrThrow(AllTvshowsLoader.COLUMN_SEASON_COUNT);
        mEpisodeCountColumn = cursor.getColumnIndexOrThrow(AllTvshowsLoader.COLUMN_EPISODE_COUNT);
        mTraktSeenColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN);
        mTraktLibraryColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY);
        mYearColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED);
        mStudioColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.SCRAPER_S_STUDIOS);
        mPlotColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.SCRAPER_S_PLOT);
        mActorsColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.SCRAPER_E_ACTORS);
        mRatingColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.SCRAPER_S_RATING);
    }


    public Object bind(Cursor cursor) {
        return new Tvshow(
                cursor.getLong(mIdColumn),
                cursor.getString(mNameColumn),
                getPosterUri(cursor),
                cursor.getInt(mSeasonCountColumn),
                cursor.getInt(mEpisodeCountColumn),
                VideoCursorMapper.isTraktSeenOrLibrary(cursor, mTraktSeenColumn),
                VideoCursorMapper.isTraktSeenOrLibrary(cursor, mTraktLibraryColumn),
                cursor.getString(mPlotColumn),
                cursor.getString(mStudioColumn),
                cursor.getString(mActorsColumn),
                cursor.getInt(mYearColumn),
                cursor.getFloat(mRatingColumn)

        );
    }


    private Uri getPosterUri(Cursor c) {
        String path = c.getString(mPosterPathColumn);
        if (path!=null && !path.isEmpty()) {
            return Uri.parse("file://"+path);
        } else {
            return null;
        }
    }
}
