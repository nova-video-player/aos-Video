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
 * Created by vapillon on 10/04/15.
 */
public class EpisodesLoader extends VideoLoader {

    private static final String TAG = "EpisodesLoader";

    private final long mShowId;
    private final int mSeasonNumber;
    private final boolean mGroupByOnlineId;

    /**
     * List episodes of a given season of a given show
     * @param context
     * @param showId must be a VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID
     * @param seasonNumber number of the season
     * @param groupByOnlineId
     */
    public EpisodesLoader(Context context, long showId, int seasonNumber, boolean groupByOnlineId) {
        super(context);
        mGroupByOnlineId = groupByOnlineId;
        mShowId = showId;
        mSeasonNumber = seasonNumber;
        init();
        if(groupByOnlineId)
            setUri(getUri().buildUpon().appendQueryParameter("group",  "CASE\n"+
                    "WHEN " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + ">0 THEN " + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID+"\n" +
                    " ELSE "+VideoStore.Video.VideoColumns._ID+"\n" +
                    "END").build());
    }

    @Override
    public String getSortOrder() {
        return VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE;
    }

    @Override
    public String[] getProjection() {
        if(mGroupByOnlineId)
            return  concatTwoStringArrays(super.getProjection(),new String[]{ "max("+VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED+") as last",COUNT });
        else
            return super.getProjection();
    }
    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + "=?");
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + "=?");
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[] {
                Long.toString(mShowId),
                Integer.toString(mSeasonNumber)
        };
    }
}
