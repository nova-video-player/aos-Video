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
import com.archos.mediascraper.TagsFactory;

import java.io.Serializable;

/**
 * Created by vapillon on 10/04/15.
 */
public class Movie extends Video implements Serializable {

    final long mMovieId;
    final int mYear;
    final float mRating;
    final String mContentRating;
    final String mPlot;
    private final long mOnlineId;
    private long mPinned;

    public Movie(long id, String filePath, String name, long movieId, String plot, int year, float rating, String contentRating, Uri posterUri, int duration, int resume,
                 int video3dMode, int guessedDefinition, boolean traktSeen, boolean isTraktLibrary,boolean hasSubs, boolean isUserHidden, long onlineId, long lastTimePlayed,
                 int calculatedWidth, int calculatedHeight, String audioFormat, String videoFormat, String guessedAudioFormat, String guessedVideoFormat,  int calculatedBestAudiotrack, int occurencies, long size, long pinned) {
        super(id, filePath, name, posterUri,
                duration, resume, video3dMode, guessedDefinition,  traktSeen, isTraktLibrary,hasSubs, isUserHidden,lastTimePlayed,
                calculatedWidth, calculatedHeight, audioFormat, videoFormat, guessedAudioFormat, guessedVideoFormat, calculatedBestAudiotrack,occurencies,size);
        mMovieId = movieId;
        mYear = year;
        mRating = rating;
        mContentRating = contentRating;
        mPlot = plot;
        mOnlineId = onlineId;
        mPinned = pinned;
    }

    public long getOnlineId(){
        return mOnlineId;
    }

    @Override
    public String getDescriptionBody() {
        return mPlot;
    }

    public int getYear() {
        return mYear;
    }

    public float getRating() {
        return mRating;
    }

    public String getContentRating() { return mContentRating;}

    @Override
    public BaseTags getFullScraperTags(Context context) {
        return TagsFactory.buildMovieTags(context, mMovieId);
    }

    public String getDirector() {
        return "";
    }

    public String getWriter() {
        return "";
    }

    public String getActors() {
        return "";
    }

    public boolean isPinned() {
        return mPinned > 0;
    }

    public long getMovieId() {
        return mMovieId;
    }
}
