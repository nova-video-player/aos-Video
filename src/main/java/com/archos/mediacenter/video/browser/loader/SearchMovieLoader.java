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

import com.archos.mediaprovider.video.VideoStore;

public class SearchMovieLoader extends VideoLoader {

    // search based on scraper title and file title
    private static final String SELECTION = VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL AND ("
                                          + VideoStore.Video.VideoColumns.SCRAPER_TITLE + " LIKE ? OR "
                                          + VideoStore.MediaColumns.TITLE + " LIKE ? )";

    private static final String DEFAULT_QUERY = "";
    private static final String DEFAULT_SORT = "name COLLATE NOCASE";

    private String mSortOrder = DEFAULT_SORT;
    private String mQuery = DEFAULT_QUERY;

    public SearchMovieLoader(Context context) {
        super(context);
        init();
    }

    public void setQuery(String query) {
        mQuery = query;
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
        sb.append(SELECTION);

        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[]{ "%" + mQuery + "%", "%" + mQuery + "%" };
    }

}

