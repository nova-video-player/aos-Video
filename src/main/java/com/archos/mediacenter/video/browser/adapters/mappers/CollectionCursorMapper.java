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

package com.archos.mediacenter.video.browser.adapters.mappers;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.loader.CollectionLoader;
import com.archos.mediaprovider.video.VideoStore;

public class CollectionCursorMapper implements CompatibleCursorMapper {

    private static final String TAG = "CollectionCursorMapper";
    private static final boolean DBG = true;

    int mIdColumn;
    int mNameColumn;
    int mPosterPathColumn;
    int mBackdropPathColumn;
    int mCollectionCountColumn;
    int mCollectionMovieCountColumn;
    int mCollectionMovieWatchedCountColumn;
    int mTraktSeenColumn;
    int mTraktLibraryColumn;
    int mPlotColumn;
    int mPinnedColumn;

    public CollectionCursorMapper() {
    }

    public void bindColumns(Cursor cursor) {
        mIdColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID);
        mNameColumn = cursor.getColumnIndexOrThrow(CollectionLoader.COLUMN_NAME);
        mPosterPathColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_C_POSTER_LARGE_FILE);
        mBackdropPathColumn = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_C_BACKDROP_LARGE_FILE);
        mCollectionCountColumn = cursor.getColumnIndexOrThrow(CollectionLoader.COLUMN_COLLECTION_COUNT);
        mCollectionMovieCountColumn = cursor.getColumnIndexOrThrow(CollectionLoader.COLUMN_COLLECTION_MOVIE_COUNT);
        mCollectionMovieWatchedCountColumn = cursor.getColumnIndex(CollectionLoader.COLUMN_COLLECTION_MOVIE_WATCHED_COUNT);
        mTraktSeenColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN);
        mTraktLibraryColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.ARCHOS_TRAKT_LIBRARY);
        mPlotColumn = cursor.getColumnIndexOrThrow( VideoStore.Video.VideoColumns.SCRAPER_C_DESCRIPTION);
        mPinnedColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.NOVA_PINNED);
    }

    public Object bind(Cursor cursor) {
        return new Collection(
                cursor.getLong(mIdColumn),
                cursor.getString(mNameColumn),
                getPosterUri(cursor),
                getBackdropUri(cursor),
                cursor.getInt(mCollectionCountColumn),
                cursor.getInt(mCollectionMovieCountColumn),
                cursor.getInt(mCollectionMovieWatchedCountColumn),
                VideoCursorMapper.isTraktSeenOrLibrary(cursor, mTraktSeenColumn),
                VideoCursorMapper.isTraktSeenOrLibrary(cursor, mTraktLibraryColumn),
                cursor.getString(mPlotColumn),
                mPinnedColumn != -1 ? cursor.getLong(mPinnedColumn) : -1
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

    private Uri getBackdropUri(Cursor c) {
        String path = c.getString(mBackdropPathColumn);
        if (path!=null && !path.isEmpty()) {
            return Uri.parse("file://"+path);
        } else {
            return null;
        }
    }
}
