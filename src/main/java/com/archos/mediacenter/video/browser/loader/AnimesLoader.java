// Copyright 2021 Courville SA
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

import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

public class AnimesLoader extends VideoLoader {

    static public String DEFAULT_SORT = "name COLLATE LOCALIZED ASC";
    private final boolean mGroupByOnlineId;

    private String mSortOrder;

    private boolean mShowWatched;

    private static Context mContext;

    public AnimesLoader(Context context, boolean groupbyOnlineId) {
        this(context, DEFAULT_SORT, true, groupbyOnlineId, false, 0);
    }

    public AnimesLoader(Context context, boolean groupbyOnlineId, boolean applyThrottleDelay, int throttleDelay) {
        this(context, DEFAULT_SORT, true, groupbyOnlineId, applyThrottleDelay, throttleDelay);
    }

    public AnimesLoader(Context context, String SortOrder, boolean showWatched, boolean groupByOnlineId, boolean applyThrottleDelay, int throttleDelay) {
        super(context);
        mContext = context;
        mGroupByOnlineId = groupByOnlineId;
        mSortOrder = SortOrder;
        mShowWatched = showWatched;
        init();
        if (applyThrottleDelay) setUpdateThrottle(throttleDelay);
    }

    @Override
    protected void init() {
        super.init();
        if(mGroupByOnlineId) {
            setUri(getUri().buildUpon().appendQueryParameter("group",
                    "CASE\n"+
                            "WHEN " + VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID + ">0 THEN " + VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID+"\n" +
                            " ELSE "+VideoStore.Video.VideoColumns._ID+"\n" +
                            "END"


            ).build());
        }
    }
    @Override
    public String[] getProjection() {
        if(mGroupByOnlineId)
            return  concatTwoStringArrays(super.getProjection(),new String[]{ "max("+VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED+") as last",COUNT });
        else
            return super.getProjection();
    }

    @Override
    public String getSortOrder() {
        return mSortOrder;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL");
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_M_GENRES + " LIKE '%" + mContext.getString(com.archos.medialib.R.string.movie_genre_animation) + "%'");
        if (!mShowWatched) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }

}
