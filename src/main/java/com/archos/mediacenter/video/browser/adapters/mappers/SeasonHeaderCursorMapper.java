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

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class SeasonHeaderCursorMapper implements CompatibleCursorMapper {

    private static final String TAG = "VideoCursorMapper";

    int mIdColumn, mScraperTypeColumn, mPathColumn, mNameColumn, mPosterPathColumn, mDateColumn, mRatingColumn, mPlotColumn;
    int mDurationColumn, mResumeColumn, mBookmarkColumn, m3dColumn, mGuessedDefinitionColumn;
    int mMovieIdColumn;
    int mBackdropUrlColumn, mBackdropFileColumn;
    int mEpisodeIdColumn, mEpisodeSeasonColumn, mEpisodeNumberColumn, mEpisodeNameColumn, mShowNameColumn;
    int mTraktSeenColumn;
    int mUserHiddenColumn;
    int mTraktLibraryColumn;

    @Override
    public void bindColumns(Cursor c) {
        mIdColumn = c.getColumnIndex(BaseColumns._ID);
        mScraperTypeColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE);

        mNameColumn = c.getColumnIndex(VideoLoader.COLUMN_NAME);

        // Episodes stuff
        mEpisodeIdColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID);
        mEpisodeSeasonColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
        mEpisodeNumberColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
        mEpisodeNameColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_NAME);

        // Movies stuff
        mMovieIdColumn =  c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID);

        // Movies/Episodes common stuff
        mBackdropUrlColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_URL);
        mBackdropFileColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_FILE);
        mDateColumn = c.getColumnIndex(VideoLoader.COLUMN_DATE);
        mRatingColumn = c.getColumnIndex(VideoLoader.COLUMN_RATING);
        mPlotColumn = c.getColumnIndex(VideoLoader.COLUMN_PLOT);

        mShowNameColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
        mPathColumn = c.getColumnIndex(VideoStore.MediaColumns.DATA);
        mPosterPathColumn = c.getColumnIndex(VideoLoader.COLUMN_COVER_PATH);
        mDurationColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.DURATION);
        mResumeColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.BOOKMARK);
        mBookmarkColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK);
        m3dColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_VIDEO_STEREO);
        mGuessedDefinitionColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_VIDEO_DEFINITION);

        // Trakt
        mTraktSeenColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN);
        mTraktLibraryColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY);
        // User can show/hide some files
        mUserHiddenColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER);
    }

    @Override
    public Object bind(Cursor c) {
        final int scraperType = c.getInt(mScraperTypeColumn);
        final String filePath = c.getString(mPathColumn);



            return new Season(-1, c.getString(mShowNameColumn), getPosterUri(c), c.getInt(mEpisodeSeasonColumn), 0, 0);

    }



    private Uri getPosterUri(Cursor c) {
        String path = c.getString(mPosterPathColumn);
        if (path!=null && !path.isEmpty()) {
            return Uri.parse("file://"+path);
        } else {
            return null;
        }
    }


    public static boolean isTraktSeenOrLibrary(Cursor c, int traktSeenColumn) {
        int val = c.getInt(traktSeenColumn);
        return (val == Trakt.TRAKT_DB_MARKED);
    }

    private boolean isUserHidden(Cursor c) {
        int val = c.getInt(mUserHiddenColumn);
        return (val > 0);
    }
}
