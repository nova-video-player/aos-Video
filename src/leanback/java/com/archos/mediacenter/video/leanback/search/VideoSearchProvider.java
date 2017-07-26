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

package com.archos.mediacenter.video.leanback.search;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.VideoStore;


public class VideoSearchProvider extends ContentProvider {

    // primary text, = scraper title with fallback to plain filename based title
    private static final String LINE1 = "coalesce(" + VideoStore.Video.VideoColumns.SCRAPER_TITLE + "," + VideoStore.MediaColumns.TITLE
            + ") AS " + SearchManager.SUGGEST_COLUMN_TEXT_1;

    // secondary text for shows only "S1E4 'Episode Title'"
    private static final String QUOTED_CONTENT = "'||" + VideoStore.Video.VideoColumns.SCRAPER_E_NAME + "||'";
    private static final String LINE2_START = "'S'||" + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + "||'E'||"
            + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + "||' ";
    private static final String LINE2_END = "' AS " + SearchManager.SUGGEST_COLUMN_TEXT_2;

    // icon = scraper cover or nothing
    protected static final String ICON = "coalesce("
            + "'file://'||" + VideoStore.Video.VideoColumns.SCRAPER_COVER + ",'') AS " + SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE;

    // content id = database id
    private static final String CONTENT_ID = BaseColumns._ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID;

    // search based on scraper title and file title
    private static final String SELECTION = VideoStore.Video.VideoColumns.SCRAPER_TITLE + " LIKE ? OR "
            + VideoStore.MediaColumns.TITLE + " LIKE ?";

    // sort based on scraper title, file title, season number and episode number (video without cover at end to keep poster format)
    private static final String SORT = VideoStore.Video.VideoColumns.SCRAPER_COVER + " IS NOT NULL DESC,"
            + "coalesce(" + VideoStore.Video.VideoColumns.SCRAPER_TITLE + "," + VideoStore.MediaColumns.TITLE + ") COLLATE NOCASE ASC,"
            + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + " ASC,"
            + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + " ASC";

    private String[] mProjection;

    @Override
    public boolean onCreate() {
        buildProjection();
        return true;
    }

    private void buildProjection() {
        String quoted = getContext().getString(R.string.quotation_format, QUOTED_CONTENT);
        String line2 = LINE2_START + quoted + LINE2_END;

        mProjection = new String []{
                BaseColumns._ID,
                LINE1,
                line2,
                ICON,
                CONTENT_ID
        };
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor matrixCursor = null;

        if (selectionArgs != null && selectionArgs.length > 0 && !selectionArgs[0].isEmpty()) {
            String[] extendedSelectionArgs = {
                    "%" + selectionArgs[0] + "%",
                    "%" + selectionArgs[0] + "%"
            };

            Uri u = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
            ContentResolver cr = getContext().getContentResolver();
            Cursor result = cr.query(u, mProjection, SELECTION, extendedSelectionArgs, SORT);

            if (result.moveToFirst()) {
                matrixCursor = new MatrixCursor(result.getColumnNames());
                int numColumns = result.getColumnCount();
                int iconIdx = result.getColumnIndexOrThrow(SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE);

                do {
                    Object[] currRow = new Object[numColumns];
                    for (int i=0 ; i<numColumns ; i++) {
                        currRow[i] = result.getString(i);
                    }
                    String icon = result.getString(iconIdx);
                    if (icon == null || icon.isEmpty()) {
                        // video without poster, put thumbail in place of poster
                        long id = result.getLong(result.getColumnIndexOrThrow(BaseColumns._ID));
                        Cursor c = cr.query(VideoStore.Video.Thumbnails.EXTERNAL_CONTENT_URI,
                                            new String[]{VideoStore.Video.Thumbnails._ID},
                                            VideoStore.Video.Thumbnails.VIDEO_ID + "=" + id, null, null);
                        if (c != null && c.moveToFirst()) {
                            int thumbIdIdx = c.getColumnIndexOrThrow(VideoStore.Video.Thumbnails._ID);
                            currRow[iconIdx] = VideoStore.Video.Thumbnails.EXTERNAL_CONTENT_URI + "/" + c.getString(thumbIdIdx);
                        }
                    }
                    matrixCursor.addRow(currRow);
                }
                while (result.moveToNext());
            }
        }
        return matrixCursor;
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
