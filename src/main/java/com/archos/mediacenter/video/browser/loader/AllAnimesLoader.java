// Copyright 2021 Courville Software
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
import android.provider.BaseColumns;

import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

public class AllAnimesLoader extends VideoLoader {

    private static final String TAG = "AllAnimesLoader";

    static public String DEFAULT_SORT = "name COLLATE NOCASE ASC";

    private String mSortOrder;
    private static Context mContext;
    private boolean mShowWatched;
    private final boolean mGroupByOnlineId;

    /**
     * List all anime shows and movies
     * @param context
     */
    public AllAnimesLoader(Context context, boolean groupbyOnlineId) {
        this(context, DEFAULT_SORT, true, groupbyOnlineId);
    }

    public AllAnimesLoader(Context context, String SortOrder, boolean showWatched, boolean groupByOnlineId) {
        super(context);
        mContext = context;
        mSortOrder = SortOrder;
        mShowWatched = showWatched;
        mGroupByOnlineId = groupByOnlineId;
        init();
    }

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

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append("( " + VideoStore.Video.VideoColumns.SCRAPER_S_GENRES + " LIKE '%" + mContext.getString(com.archos.medialib.R.string.tvshow_genre_animation) + "%' OR " +
                VideoStore.Video.VideoColumns.SCRAPER_M_GENRES + " LIKE '%" + mContext.getString(com.archos.medialib.R.string.movie_genre_animation) + "%' )");
        if (!mShowWatched) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }
        return sb.toString();
    }

}
