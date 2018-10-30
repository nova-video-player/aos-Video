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
import com.archos.mediascraper.EpisodeTags;

/**
 * Created by vapillon on 10/04/15.
 */
public class NextEpisodeLoader extends VideoLoader {

    private static final String TAG = "NextEpisodeLoader";

    private final long mShowId;
    private final int mSeasonNumber;
    private final int mEpisodeNumber;

    /**
     * Get next episode for a given episode
     * @param context
     * @param episodeTags the current episode
     */
    public NextEpisodeLoader(Context context, EpisodeTags episodeTags) {
        super(context);
        mShowId = episodeTags.getShowId();
        mSeasonNumber = episodeTags.getSeason();
        mEpisodeNumber = episodeTags.getEpisode()+1; // look for the next episode
        init();
    }

    @Override
    public String getSortOrder() {
        return VideoStore.Video.VideoColumns.SCRAPER_E_SEASON;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + "=?");
        sb.append(" AND ( (");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + "=?");
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + "=?");
        sb.append(" ) OR ( ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + "=?");
        sb.append(" AND ");
        sb.append(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + "=?");
        sb.append(" ) ) ");
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[] {
                Long.toString(mShowId),
                Integer.toString(mSeasonNumber),
                Integer.toString(mEpisodeNumber),
                Integer.toString(mSeasonNumber + 1),
                Integer.toString(1)
        };
    }
}
