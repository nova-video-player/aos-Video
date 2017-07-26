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


package com.archos.mediacenter.video.browser;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.SearchViewVideoLoader;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

public class SearchProviderVideo extends ContentProvider {

    // primary text, = scraper title with fallback to plain filename based title
    private static final String LINE1 = "coalesce(" + VideoColumns.SCRAPER_TITLE + "," + MediaColumns.TITLE
            + ") AS " + SearchManager.SUGGEST_COLUMN_TEXT_1;

    // secondary text for shows only "S1E4 'Episode Title'"
    private static final String QUOTED_CONTENT = "'||" + VideoColumns.SCRAPER_E_NAME + "||'";
    private static final String LINE2_START = "'S'||" + VideoColumns.SCRAPER_E_SEASON + "||'E'||"
            + VideoColumns.SCRAPER_E_EPISODE + "||' ";
    private static final String LINE2_END = "' AS " + SearchManager.SUGGEST_COLUMN_TEXT_2;

    // icon = scraper cover or nothing
    private static final String ICON = "'file://'||" + VideoColumns.SCRAPER_COVER + " AS "
            + SearchManager.SUGGEST_COLUMN_ICON_1;

    // content id = database id
    private static final String CONTENT_ID = BaseColumns._ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID;

    // search based on scraper title and file title
    private static final String SELECTION = VideoColumns.SCRAPER_TITLE + " LIKE ? OR "
            + MediaColumns.TITLE + " LIKE ?";

    private String[] mProjection;
    private SearchViewVideoLoader mSearchViewVideoLoader;

    @Override
    public boolean onCreate() {
        mSearchViewVideoLoader = new SearchViewVideoLoader(getContext());
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    }



    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor result = null;

        if (selectionArgs != null && selectionArgs.length > 0 && !selectionArgs[0].isEmpty()) {
            // SQL regexp == *selectionArgs[0]*, twice because we have 2 '?'
            String[] extendedSelectionArgs = {
                "%" + selectionArgs[0] + "%",
                "%" + selectionArgs[0] + "%"
            };
            mSearchViewVideoLoader.setQuery(selectionArgs[0]);
            Uri u = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
            ContentResolver cr = getContext().getContentResolver();
            result = cr.query(u, mSearchViewVideoLoader.getProjection(), mSearchViewVideoLoader.getSelection(), mSearchViewVideoLoader.getSelectionArgs(), mSearchViewVideoLoader.getSortOrder());
        }
        return result;
    }

    @Override
    public String getType(Uri uri) {
        return "";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
