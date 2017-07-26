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

import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;


public class MultipleVideoLoader extends VideoLoader {

    private final long mId;
    private final String mPath;

    /**
     * Query videos, based on its DB path
     * @param context
     */
    public MultipleVideoLoader(Context context, String path) {
        super(context);
        mIsDetailed = true;
        mPath = path;
        mId = -1;
        init();
    }

    public MultipleVideoLoader(Context context, long id) {
        super(context);
        mIsDetailed = true;
        mId = id;
        mPath = null;
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
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID + " = ");
        sb.append("(SELECT " + VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID + " FROM video WHERE "
                + (mPath != null ? VideoStore.Video.VideoColumns.DATA : VideoStore.Video.VideoColumns._ID )+" = ?");
        if (LoaderUtils.mustHideUserHiddenObjects()) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);
        }
        sb.append(")");

        sb.append(" AND "+VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID+" > 0");
        sb.append(" OR "+(mPath!=null?VideoStore.Video.VideoColumns.DATA:VideoStore.Video.VideoColumns._ID) +" = ? ");
        if (LoaderUtils.mustHideUserHiddenObjects()) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);

        }
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {

            return new String[]{String.valueOf(mPath!=null?mPath:mId),String.valueOf(mPath!=null?mPath:mId)};

    }
}
