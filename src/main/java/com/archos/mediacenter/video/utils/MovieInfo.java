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


package com.archos.mediacenter.video.utils;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.TagsFactory;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.util.List;

public class MovieInfo extends BaseInfo {

    private static final String TAG = "MovieInfo";
    private static final boolean DBG = false;

    private String mTitle;
    private int mYear;
    private float mRating;
    private String mPlot;
    private String mDirectors;
    private String mActors;
    private String mGenres;

    private File mCoverFile;

    /**
     * Get episode infos (title, season, number, etc.). It performs two database
     * requests: one for the Episode and one for the related Show
     *
     * @param cr: a content resolver
     * @param episodeID: the Scraper ID for this episode
     */
    public MovieInfo(ContentResolver cr, long movieID) {

        if (DBG)
            Log.d(TAG, "MovieInfo constructor for movieID=" + movieID);

        Cursor cur = null;

        try {
            String selection = VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + "=? AND "
                    + VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + "=" + ScraperStore.SCRAPER_TYPE_MOVIE;
            String[] selectionArgs = { String.valueOf(movieID) };
            cur = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, TagsFactory.VIDEO_COLUMNS,
                    selection, selectionArgs, null);

            List<BaseTags> tags = TagsFactory.buildTagsFromVideoCursor(cur);

            if (!tags.isEmpty() && tags.get(0) instanceof MovieTags) {
                MovieTags movieTag = (MovieTags) tags.get(0);
                mCoverFile = movieTag.getCover();
                mTitle = movieTag.getTitle();
                mYear = movieTag.getYear();
                mRating = movieTag.getRating();
                mDirectors = movieTag.getDirectorsFormatted();
                mActors = movieTag.getActorsFormatted();
                mGenres = movieTag.getGenresFormatted();
                mPlot = movieTag.getPlot();
                mValid = true;
            }
        } catch (Exception e) {
            if (DBG)
                Log.e(TAG, "Failed to get ScraperStore info for movieID=" + movieID, e);
        } finally {
            if (cur != null)
                cur.close();
        }
    }

    /* ----------------- Getters ------------------- */

    public CharSequence getFormattedTitle(Context context, int viewMode) {
        return mTitle;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getYear() {
        return mYear;
    }

    public float getRating() {
        return mRating;
    }

    public String getPlot() {
        return mPlot;
    }

    public String getActors() {
        return mActors;
    }

    public String getDirectors() {
        return mDirectors;
    }

    public String getGenres() {
        return mGenres;
    }

    public File getCover() {
        return mCoverFile;
    }

    public void setDetailName(TextView view, Resources res) {
    }

    public void setDetailLineOne(TextView view, Resources res) {
        if (mDirectors == null) {
            view.setText(EMPTY);
        } else {
            view.setText(res.getString(R.string.scrap_director_format, mDirectors));
        }
    }

    public void setDetailLineTwo(TextView view, Resources res) {
        if (mPlot == null) {
            view.setText(EMPTY);
        } else {
            view.setText(mPlot);
            view.setSingleLine(false);
            view.setMaxLines(3);
        }
    }

    public void setDetailLineThree(TextView view, Resources res) {
        if (mActors == null) {
            view.setText(EMPTY);
        } else {
            view.setText(res.getString(R.string.scrap_cast_format, mActors));
            view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            view.setSingleLine(true);
        }
    }

    public float getDetailLineRating() {
        return mRating;
    }

    public void setDetailLineReleaseDate(TextView view, Resources res) {
        if (mYear > 0) {
            view.setText(res.getString(R.string.scrap_year_format, mYear));
        } else {
            view.setText(res.getString(R.string.scrap_year));
        }
    }
}
