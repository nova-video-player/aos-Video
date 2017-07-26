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

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.TagsFactory;

import java.io.Serializable;

/**
 * Created by vapillon on 10/04/15.
 */
public class Tvshow extends Base implements Serializable {



    private final boolean mIsTraktSeen;
    private final boolean mIsTraktLibrary;
    private final float mRating;

    public int getYear() {
        return mYear;
    }

    public String getPlot() {
        return mPlot;
    }

    public String getActors() {
        return mActors;
    }

    public String getStudio() {
        return mStudio;
    }

    private final int mYear;
    private final String mStudio;
    private final String mActors;
    private final String mPlot;
    private long mTvshowId;
    private int mSeasonCount;
    private int mEpisodeCount;

    /**
     * Not computed by this class but only a place to store it.
     * Need to be set with setShowTags()
     */
    private ShowTags mShowTags;

    public Tvshow(long tvshowId, String name, Uri posterUri, int seasonCount, int episodeCount) {
        this(tvshowId,name, posterUri, seasonCount, episodeCount, false, false, null, null,null, -1,-1);
    }


    public Tvshow(long tvshowId,
                  String name,
                  Uri posterUri,
                  int seasonCount,
                  int episodeCount,
                  boolean traktSeen,
                  boolean traktLibrary,
                  String plot,
                  String studio,
                  String actors,
                  int year,
                  float rating) {
        super(name, posterUri);
        mTvshowId = tvshowId;
        mSeasonCount = seasonCount;
        mIsTraktSeen = traktSeen;
        mIsTraktLibrary = traktLibrary;
        mEpisodeCount = episodeCount;
        mStudio = studio;
        mPlot = plot;
        mActors = actors;
        mYear = year;
        mRating = rating;

    }

    public String getCountString(Context context) {
        if (mSeasonCount > 1) {
            return String.format(context.getResources().getQuantityText(R.plurals.Nseasons, mSeasonCount).toString(), mSeasonCount);
        } else {
            return String.format(context.getResources().getQuantityText(R.plurals.Nepisodes, mEpisodeCount).toString(), mEpisodeCount);
        }
    }

    public int getSeasonCount() {
        return mSeasonCount;
    }

    public int getEpisodeCount() {
        return mEpisodeCount;
    }

    public long getTvshowId() {
        return mTvshowId;
    }

    @Override
    public BaseTags getFullScraperTags(Context context) {
        return TagsFactory.buildShowTags(context, mTvshowId);
    }

    public void setShowTags(ShowTags showTags) {
        mShowTags = showTags;
    }

    /**
     *
     * @return null if you did not set the show tags with @link:setShowTags before
     */
    public ShowTags getShowTags() {
        return mShowTags;
    }

    public boolean isTraktSeen() {
        return mIsTraktSeen;
    }

    public boolean isTraktLibrary() {
        return mIsTraktLibrary;
    }

    public float getRating() {
        return mRating;
    }
}
