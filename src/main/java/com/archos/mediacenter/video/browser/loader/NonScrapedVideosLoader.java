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
import android.os.Environment;

import com.archos.mediaprovider.video.VideoStore;

public class NonScrapedVideosLoader extends VideoLoader {

    private static final String SELECTION = VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NULL AND " +
                                            VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + " IS NULL AND " +
                                            VideoStore.MediaColumns.DATA + " NOT LIKE ?"; // not in camera path
    private static final String CAMERA_PATH_ARG = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera/%";
    private static final String[] SELECTION_ARGS = { CAMERA_PATH_ARG };

    static public String DEFAULT_SORT = "name COLLATE NOCASE ASC";

    private String mSortOrder;

    public NonScrapedVideosLoader(Context context) {
        this(context, DEFAULT_SORT);
    }

    public NonScrapedVideosLoader(Context context, String SortOrder) {
        super(context);
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
        if (sb.length() > 0) {
            sb.append(" AND ");
        }
        sb.append(SELECTION);
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return SELECTION_ARGS;
    }

}
