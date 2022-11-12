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
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;

/**
 * Created by vapillon on 10/04/15.
 */
public class VideoCursorMapper implements CompatibleCursorMapper {

    private static final String TAG = "VideoCursorMapper";
    private static final boolean DBG = false;

    int mIdColumn, mScraperTypeColumn, mPathColumn, mNameColumn, mPosterPathColumn, mDateColumn, mRatingColumn, mContentRatingColumn, mPlotColumn;
    int mDurationColumn, mResumeColumn, mBookmarkColumn, m3dColumn, mGuessedDefinitionColumn;
    int mMovieIdColumn;
    int mCollectionIdColumn;
    int mCollectionNameColumn;
    int mSizeColumn;
    int mBackdropUrlColumn, mBackdropFileColumn;
    int mEpisodeIdColumn, mEpisodeSeasonColumn, mEpisodeNumberColumn, mEpisodeNameColumn, mShowNameColumn;
    int mTraktSeenColumn;
    int mUserHiddenColumn;
    int mTraktLibraryColumn;
    private int mSubsColumn;
    private int mEpisodePictureColumn;
    private int mOnlineIdColumn;
    private int mLastTimePlayedColumn;
    private int mCalculatedBestAudiotrackColumn;
    private int mBestAudioFormatColumn;
    private int mVideoFormatColumn;
    private int mCalculatedHeightColumn;
    private int mCalculatedWidthColumn;
    private int mGuessedAudioFormatColumn;
    private int mGuessedVideoFormatColumn;
    private int mCountColumn;
    private int mPinnedColumn;

    public void bindColumns(Cursor c) {
        mIdColumn = c.getColumnIndex(BaseColumns._ID);
        mScraperTypeColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE);

        mNameColumn = c.getColumnIndex(VideoLoader.COLUMN_NAME);
        mCountColumn = c.getColumnIndex(VideoLoader.COLUMN_COUNT);
        mOnlineIdColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID);
        mSizeColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SIZE);
        // Episodes stuff
        mEpisodeIdColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID);
        mEpisodeSeasonColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
        mEpisodeNumberColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
        mEpisodeNameColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_NAME);
        mEpisodePictureColumn  = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_PICTURE);

        // Movies stuff
        mMovieIdColumn =  c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID);
        mCollectionIdColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_C_ID);

        // Movies/Episodes common stuff
        mBackdropUrlColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_URL);
        mBackdropFileColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_BACKDROP_LARGE_FILE);
        mDateColumn = c.getColumnIndex(VideoLoader.COLUMN_DATE);
        mRatingColumn = c.getColumnIndex(VideoLoader.COLUMN_RATING);
        mPlotColumn = c.getColumnIndex(VideoLoader.COLUMN_PLOT);
        mContentRatingColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_CONTENT_RATING);

        mShowNameColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
        mPathColumn = c.getColumnIndex(VideoStore.MediaColumns.DATA);
        mPosterPathColumn = c.getColumnIndex(VideoLoader.COLUMN_COVER_PATH);
        mDurationColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.DURATION);
        mLastTimePlayedColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED);
        mResumeColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.BOOKMARK);
        mBookmarkColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK);
        mSubsColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SUBTITLE_COUNT_EXTERNAL);
        m3dColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_VIDEO_STEREO);
        mGuessedDefinitionColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_VIDEO_DEFINITION);
        mCalculatedBestAudiotrackColumn =c.getColumnIndex( VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_CHANNELS);
        mBestAudioFormatColumn =c.getColumnIndex( VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT);
        mGuessedAudioFormatColumn =c.getColumnIndex( VideoStore.Video.VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT);
        mGuessedVideoFormatColumn =c.getColumnIndex( VideoStore.Video.VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT);
        mVideoFormatColumn =c.getColumnIndex( VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_VIDEO_FORMAT);
        mCalculatedHeightColumn =c.getColumnIndex( MediaStore.Video.VideoColumns.HEIGHT);
        mCalculatedWidthColumn =c.getColumnIndex( MediaStore.Video.VideoColumns.WIDTH);

        // Trakt
        mTraktSeenColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN);
        mTraktLibraryColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY);
        // User can show/hide some files
        mUserHiddenColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER);

        mPinnedColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.NOVA_PINNED);
    }


    public Object bind(Cursor c) {
        final int scraperType = c.getInt(mScraperTypeColumn);
        final String filePath = c.getString(mPathColumn);

        int calculatedBestAudiotrack = -1;
        String bestAudioFormat= null;
        String videoFormat = null;
        String guessedAudioFormat= null;
        String guessedVideoFormat = null;
        int calculatedHeight = -1;
        int calculatedWidth = -1;

       if(mCalculatedBestAudiotrackColumn>=0){
               calculatedBestAudiotrack = c.getInt(mCalculatedBestAudiotrackColumn);
        }
        if(mBestAudioFormatColumn >=0){
            try {
            bestAudioFormat = c.getString(mBestAudioFormatColumn);
            }catch (java.lang.NumberFormatException e){}
        }
        if(mVideoFormatColumn >=0){
            try {
            videoFormat = c.getString(mVideoFormatColumn);
        }catch (java.lang.NumberFormatException e){}
        }

        if(mGuessedAudioFormatColumn >=0){
            try {
                guessedAudioFormat = c.getString(mGuessedAudioFormatColumn);
            }catch (java.lang.NumberFormatException e){}
        }
        if(mGuessedVideoFormatColumn >=0){
            try {
                guessedVideoFormat = c.getString(mGuessedVideoFormatColumn);
            }catch (java.lang.NumberFormatException e){}
        }

        if(mCalculatedHeightColumn>=0){
            try {
                calculatedHeight = Integer.valueOf(c.getString(mCalculatedHeightColumn));
            }catch (java.lang.NumberFormatException e){}

        }
        if(mCalculatedWidthColumn>=0){
            try {
                calculatedWidth = Integer.valueOf(c.getString(mCalculatedWidthColumn));
            }catch (java.lang.NumberFormatException e){}
        }
        int count = 1;
        if(mCountColumn>=0){
            count = c.getInt(mCountColumn);
        }

        if (scraperType == BaseTags.TV_SHOW) {
            return new Episode(c.getLong(mIdColumn),
                    c.getLong(mEpisodeIdColumn),
                    c.getInt(mEpisodeSeasonColumn),
                    c.getInt(mEpisodeNumberColumn),
                    c.getString(mEpisodeNameColumn),
                    c.getLong(mDateColumn),
                    c.getFloat(mRatingColumn),
                    c.getString(mContentRatingColumn),
                    c.getString(mPlotColumn),
                    c.getString(mShowNameColumn),
                    filePath,
                    getPosterUri(c.getString(mEpisodePictureColumn)),
                    getPosterUri(c.getString(mPosterPathColumn)),
                    c.getInt(mDurationColumn),
                    c.getInt(mResumeColumn),
                    c.getInt(m3dColumn),
                    c.getInt(mGuessedDefinitionColumn),
                    isTraktSeenOrLibrary(c, mTraktSeenColumn),
                    isTraktSeenOrLibrary(c, mTraktLibraryColumn),
                    c.getInt(mSubsColumn)>0,
                    isUserHidden(c),
                    c.getLong(mOnlineIdColumn),
                    c.getLong(mLastTimePlayedColumn),
                    calculatedWidth,
                    calculatedHeight,
                    bestAudioFormat,
                    videoFormat,
                    guessedVideoFormat,
                    guessedAudioFormat,
                    calculatedBestAudiotrack,
                    count, c.getLong(mSizeColumn));
        } else if (scraperType==BaseTags.MOVIE) {
            return new Movie(
                    c.getLong(mIdColumn),
                    filePath,
                    c.getString(mNameColumn),
                    c.getLong(mMovieIdColumn),
                    c.getString(mPlotColumn),
                    c.getInt(mDateColumn),
                    c.getFloat(mRatingColumn),
                    c.getString(mContentRatingColumn),
                    getPosterUri(c.getString(mPosterPathColumn)),
                    c.getInt(mDurationColumn),
                    c.getInt(mResumeColumn),
                    c.getInt(m3dColumn),
                    c.getInt(mGuessedDefinitionColumn),
                    isTraktSeenOrLibrary(c, mTraktSeenColumn),
                    isTraktSeenOrLibrary(c, mTraktLibraryColumn),
                    c.getInt(mSubsColumn)>0,
                    isUserHidden(c),
                    c.getLong(mOnlineIdColumn),
                    c.getLong(mLastTimePlayedColumn),
                    calculatedWidth,
                    calculatedHeight,
                    bestAudioFormat,
                    guessedVideoFormat,
                    guessedAudioFormat,
                    videoFormat,
                    calculatedBestAudiotrack,
                    count, c.getLong(mSizeColumn),
                    c.getLong(mPinnedColumn));
        } else {
            return new Video(
                    c.getLong(mIdColumn),
                    filePath,
                    c.getString(mNameColumn),
                    getPosterUri(c.getString(mPosterPathColumn)),
                    c.getInt(mDurationColumn),
                    c.getInt(mResumeColumn),
                    c.getInt(m3dColumn),
                    c.getInt(mGuessedDefinitionColumn),
                    isTraktSeenOrLibrary(c, mTraktSeenColumn),
                    isTraktSeenOrLibrary(c, mTraktLibraryColumn),
                    c.getInt(mSubsColumn)>0,
                    isUserHidden(c),
                    c.getLong(mLastTimePlayedColumn), c.getInt(mSizeColumn));
        }
    }

    private Uri getPosterUri(String path) {
        if (path!=null && !path.isEmpty()) {
            return Uri.parse("file://"+path);
        } else {
            return null;
        }
    }

    /**
     * I'm reusing this class from somewhere else in the code, need a public version...
     * @param c
     */
    public void publicBindColumns(Cursor c) {
        bindColumns(c);
    }

    /**
     * I'm reusing this class from somewhere else in the code, need a public version...
     * @param c
     * @return
     */
    public Object publicBind(Cursor c) {
        return bind(c);
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
