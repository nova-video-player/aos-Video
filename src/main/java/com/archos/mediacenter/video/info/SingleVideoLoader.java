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

package com.archos.mediacenter.video.info;

import android.content.Context;

import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class SingleVideoLoader extends VideoLoader {

    final long mVideoId;
    final String mPath;

    /**
     * Query one video, based on its DB id
     * @param context
     * @param videoId
     */
    public SingleVideoLoader(Context context, long videoId) {
        super(context);
        mVideoId = videoId;
        mPath = null;
        init();
    }

    /**
     * Query one video, based on its path
     * @param context
     * @param path
     */
    public SingleVideoLoader(Context context, String path) {
        super(context);
        mVideoId = -1;
        mPath = path;
        init();
    }

    @Override
    public String getSortOrder() {
        return null;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        if (LoaderUtils.mustHideUserHiddenObjects()) {
            sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);
            sb.append(" AND ");
        }
        if (mPath==null) {
            sb.append(VideoStore.Video.VideoColumns._ID + " = ? ");
        }
        else {
            sb.append(VideoStore.Video.VideoColumns.DATA + " = ? ");
        }
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        if (mPath==null) {
            return new String[]{Long.toString(mVideoId)};
        }
        else {
            return new String[]{mPath};
        }
    }
}
