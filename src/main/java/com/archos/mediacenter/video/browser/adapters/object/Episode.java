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
import java.text.DateFormat;
import java.util.Date;

/**
 * Created by vapillon on 10/04/15.
 */
public class Episode extends Video implements Serializable {

    final long mEpisodeId;
    final int mSeasonNumber;
    final int mEpisodeNumber;
    final String mEpisodeName;
    final long mEpisodeDate;
    final float mEpisodeRating;
    final String mEpisodePlot;
    final String mShowName;
    private final String mPictureUri; /*picture uri is an official thumbnail*/
    private final long mOnlineId;


    public Episode(long id, long episodeId, int seasonNumber, int episodeNumber, String episodeName, long episodeDate, float episodeRating, String episodePlot,String showName,
                   String filePath, Uri pictureUri, Uri posterUri, int duration, int resume,
                   int video3dMode, int guessedDefinition, boolean traktSeen, boolean isTraktLibrary,boolean hasSubs, boolean isUserHidden, long onlineId, long lastTimePlayed,
                   int calculatedWidth, int calculatedHeight, String bestAudioFormat, String videoFormat,String guessedAudioFormat, String guessedVideoFormat, int calculatedBestAudiotrack, int occurencies, long size) {
        super(id, filePath, episodeName, posterUri, duration, resume, video3dMode, guessedDefinition,  traktSeen, isTraktLibrary, hasSubs, isUserHidden,lastTimePlayed,
        calculatedWidth, calculatedHeight, bestAudioFormat, videoFormat, guessedAudioFormat, guessedVideoFormat,  calculatedBestAudiotrack,occurencies,size);

        mPictureUri =(pictureUri!=null) ? pictureUri.toString() : null;
        mEpisodeId = episodeId;
        mSeasonNumber = seasonNumber;
        mEpisodeNumber = episodeNumber;
        mEpisodeName = episodeName;
        mEpisodeDate = episodeDate;
        mEpisodeRating = episodeRating;
        mEpisodePlot = episodePlot;
        mShowName = showName;
        mOnlineId = onlineId;
    }

    public long getOnlineId(){
        return mOnlineId;
    }

    public int getSeasonNumber() {
        return mSeasonNumber;
    }

    public int getEpisodeNumber() {
        return mEpisodeNumber;
    }

    public String getEpisodeName() {
        return mEpisodeName;
    }

    public String getShowName() {
        return mShowName;
    }

    public long getEpisodeDate() {
        return mEpisodeDate;
    }

    /** returns null if the date is not valid */
    public String getEpisodeDateFormatted() {
        if (mEpisodeDate>0) {
            Date date = new Date(mEpisodeDate);
            if (date != null) {
                DateFormat formatter = DateFormat.getDateInstance();
                return formatter.format(date);
            }
        }
        return null;
    }

    public float getEpisodeRating() {
        return mEpisodeRating;
    }

    @Override
    public String getDescriptionBody() {
        return mEpisodePlot;
    }

    @Override
    public BaseTags getFullScraperTags(Context context) {
        return TagsFactory.buildEpisodeTags(context, mEpisodeId);
    }

    public Uri getPictureUri() {
        if(mPictureUri!=null)
            return Uri.parse(mPictureUri);
        else
            return null;
    }

    public String getDirector() {
        return "";
    }
}
