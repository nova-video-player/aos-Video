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

import com.archos.mediaprovider.video.VideoStore;

public class MovieCollectionLoader extends VideoLoader {

    private static final String TAG = "MovieCollectionLoader";

    // sort by year
    static public String DEFAULT_SORT = VideoStore.Video.VideoColumns.SCRAPER_M_YEAR + " ASC";

    private String mSortOrder;
    private long mCollectionId;
    /**
     * List all videos in one movie collection
     * @param context
     */

    public MovieCollectionLoader(Context context, long collectionId) {
        this(context, collectionId, DEFAULT_SORT);
    }

    public MovieCollectionLoader(Context context, long collectionId, String SortOrder) {
        super(context);
        mCollectionId = collectionId;
        mSortOrder = SortOrder;
        init();
    }

    @Override
    public String getSortOrder() {
        return mSortOrder;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent
        if (sb.length()>0) { sb.append(" AND "); }
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL");
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_C_ID + " = ?");
        sb.append(") GROUP BY (");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_M_IMDB_ID);
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[]{Long.toString(mCollectionId)};
    }

}
