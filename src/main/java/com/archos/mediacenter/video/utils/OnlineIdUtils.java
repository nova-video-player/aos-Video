// Copyright 2023 Courville Software
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

package com.archos.mediacenter.video.utils;

import android.content.ContentResolver;
import android.database.Cursor;

import com.archos.mediaprovider.video.VideoStore;

public class OnlineIdUtils {

    private static final String[] onlineIdProjection = {
            VideoStore.Video.VideoColumns._ID,
            VideoStore.Video.VideoColumns.SCRAPER_IMDB_ID,
            VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID
    };
    private static final String WHERE = VideoStore.Video.VideoColumns.DATA + "=?";

    public static OnlineId getOnlineId(String path, ContentResolver cr) {
        String[] selection = {path};
        Cursor cursor = cr.query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI, // The content URI of the words table
                onlineIdProjection, // The columns to return for each row
                WHERE, // Selection criteria
                selection, // Selection
                null); // The sort order for the returned rows
        if (cursor == null || cursor.getCount() < 1){
            if (cursor != null) cursor.close();
            return null;
        } else {
            cursor.moveToFirst();
            OnlineId onlineId = new OnlineId(cursor.getString(1), cursor.getString(2));
            cursor.close();
            return onlineId;
        }
    }
}
