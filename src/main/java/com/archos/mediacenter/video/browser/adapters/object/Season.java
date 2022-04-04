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

package com.archos.mediacenter.video.browser.adapters.object;

import android.content.Context;
import android.net.Uri;

import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.SeasonData;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.TagsFactory;

import java.io.Serializable;
import java.util.List;

/**
 * Created by vapillon on 10/04/15.
 */
public class Season extends Base implements Serializable {

    private long mShowId;
    private String mShowName;
    private int mSeasonNumber;
    private int mEpisodeTotalCount;
    private int mEpisodeWatchedCount;
    private String  mSeasonTags;

    /**
     * Not computed by this class but only a place to store it.
     * Need to be set with setShowTags()
     */
    private ShowTags mShowTags;

    public Season(long showId, String showName, Uri posterUri, int seasonNumber, int episodeTotalCount, int episodeWatchedCount, String seasontags) {
        super(showName, posterUri);
        mShowId = showId;
        mSeasonNumber = seasonNumber;
        mEpisodeTotalCount = episodeTotalCount;
        mEpisodeWatchedCount = episodeWatchedCount;
        mSeasonTags = seasontags;
    }

    public long getShowId() {
        return mShowId;
    }

    public int getSeasonNumber() {
        return mSeasonNumber;
    }

    public int getEpisodeTotalCount() {
        return mEpisodeTotalCount;
    }

    public int getEpisodeWatchedCount() {
        return mEpisodeWatchedCount;
    }

    public int getEpisodeUnwatchedCount() {
        return mEpisodeTotalCount - mEpisodeWatchedCount;
    }

    public boolean allEpisodesWatched() {
        return mEpisodeWatchedCount>=mEpisodeTotalCount;
    }

    public boolean allEpisodesNotWatched() {
        return mEpisodeWatchedCount==0 && mEpisodeTotalCount>0;
    }
    public BaseTags getFullScraperTags(Context context) {
        return TagsFactory.buildShowTags(context, mShowId);
    }
    public String getSeasonTags() {
        return mSeasonTags;
    }
}
