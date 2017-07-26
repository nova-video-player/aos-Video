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

/**
 * Load the 100 latest videos
 * Created by vapillon on 10/04/15.
 */
public class VideosInFolderLoader extends VideoLoader {

    private static final String TAG = "VideosInFolderLoader";

    private String mPathForVideoDbQuery;

    public VideosInFolderLoader(Context context, String path) {
        super(context);

        mPathForVideoDbQuery = path;
        if (!mPathForVideoDbQuery.endsWith("/")&&!mPathForVideoDbQuery.startsWith("content")) {
            mPathForVideoDbQuery += "/";
        }
        init();
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.DATA + " NOT LIKE ?");
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.DATA + " LIKE ?");
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[]{
                mPathForVideoDbQuery + "%/%", // not like
                mPathForVideoDbQuery + "%.%" // like
        };
    }
}