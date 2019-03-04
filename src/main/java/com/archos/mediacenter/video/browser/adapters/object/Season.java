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

import android.net.Uri;

import com.archos.mediascraper.ShowTags;

import java.io.Serializable;

/**
 * Created by vapillon on 10/04/15.
 */
public class Season extends Base implements Serializable {

    private long mShowId;
    private String mShowName;
    private int mSeasonNumber;
    private int mEpisodeTotalCount;
    private int mEpisodeWatchedCount;

    /**
     * Not computed by this class but only a place to store it.
     * Need to be set with setShowTags()
     */
    private ShowTags mShowTags;

    public Season(long showId, String showName, Uri posterUri, int seasonNumber, int episodeTotalCount, int episodeWatchedCount) {
        super(showName, posterUri);
        mShowId = showId;
        mSeasonNumber = seasonNumber;
        mEpisodeTotalCount = episodeTotalCount;
        mEpisodeWatchedCount = episodeWatchedCount;
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
}
