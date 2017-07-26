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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.Html;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.TagsFactory;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class EpisodeInfo extends BaseInfo {

    private final static String TITLE_FORMAT = "%s  S%dE%d";

    private static final String TAG = "EpisodeInfo";
    private static final boolean DBG = false;

    private String mEpisodeTitle;
    private int mSeasonNumber;
    private int mEpisodeNumber;
    private float mEpisodeRating;
    private String mEpisodePlot;
    private String mAired;

    private long mTVShowId;

    private String mShowTitle;
    private File mShowCoverFile;
    private String mShowGenres;
    private float mShowRating;
    private String mActors;
    private String mDirectors;

    /**
     * Get episode infos (title, season, number, etc.). It performs two database
     * requests: one for the Episode and one for the related Show
     *
     * @param cr: a content resolver
     * @param episodeID: the Scraper ID for this episode
     */
    public EpisodeInfo(ContentResolver cr, long episodeID) {

        if (DBG)
            Log.d(TAG, "EpisodeInfo constructor for episodeID=" + episodeID);

        try {
            EpisodeTags episodeTags = null;
            String selection = VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + "=? AND "
                    + VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE + "=" + ScraperStore.SCRAPER_TYPE_SHOW;
            String[] selectionArgs = { String.valueOf(episodeID) };
            Cursor c = cr.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, TagsFactory.VIDEO_COLUMNS, selection, selectionArgs, null);
            List<BaseTags> tagsList = TagsFactory.buildTagsFromVideoCursor(c);
            if (c != null)
                c.close();
            if (!tagsList.isEmpty() && tagsList.get(0) instanceof EpisodeTags) {
                episodeTags = (EpisodeTags) tagsList.get(0);
            }
            if (episodeTags != null) {
                mEpisodeTitle = episodeTags.getTitle();
               // The scraper sometimes returns this stupid value
               if (mEpisodeTitle == null || mEpisodeTitle.equals("null")) {
                   mEpisodeTitle = "";
               }
               mSeasonNumber = episodeTags.getSeason();
               mEpisodeNumber = episodeTags.getEpisode();

               if (episodeTags.getAired() != null) {
                   Date airedDate = episodeTags.getAired();
                   if (airedDate.getTime() > 0) {
                       DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
                       mAired = df.format(episodeTags.getAired());
                   } else {
                       mAired = "";
                   }
               } else {
                   mAired = "";
               }
               mEpisodePlot = episodeTags.getPlot();
               mEpisodeRating = episodeTags.getRating();

               mTVShowId = episodeTags.getShowId();
               File episodeCover = episodeTags.getCover();
               if (episodeCover.exists())
                   mShowCoverFile = episodeCover;
               ShowTags showTags = episodeTags.getShowTags();
               if (showTags != null) {
                   mShowTitle = showTags.getTitle();
                   File showCover = showTags.getCover();
                   if (mShowCoverFile == null && showCover != null && showCover.exists())
                       mShowCoverFile = showCover;
                   mShowRating = showTags.getRating();
                   mActors = showTags.getActorsFormatted();
                   mDirectors = episodeTags.getDirectorsFormatted();
                   mShowGenres = showTags.getGenresFormatted();
                   mValid = true;
               }
            }
        } catch (Exception e) {
            if (DBG)
                Log.e(TAG, "Failed to get ScraperStore info for episodeID=" + episodeID, e);
        }
    }

    /* ------------- Advanced Getters -------------- */
    public String getSXEY() {
        return "S" + mSeasonNumber + "E" + mEpisodeNumber;
    }

    /* ----------------- Getters ------------------- */

    @Override
    public CharSequence getFormattedTitle(Context context, int viewMode) {
        String ret = null;
        if (mShowTitle != null) {
            // Build the first part of the title: "show name + SxEy"
            ret = String.format(TITLE_FORMAT, mShowTitle, mSeasonNumber, mEpisodeNumber);

            if (viewMode == VideoUtils.VIEW_MODE_LIST) {
                // Append the episode name in italics: " <<episode name>>"
                String episodeNameFormat = context.getString(R.string.quotation_format);
                String episodeName = String.format(episodeNameFormat, mEpisodeTitle);
                return Html.fromHtml(ret + " <i>" + episodeName + "</i>");
            }
        }
        return ret;
    }

    public String getEpisodeTitle() {
        return mEpisodeTitle;
    }

    public int getSeasonNumber() {
        return mSeasonNumber;
    }

    public int getEpisodeNumber() {
        return mEpisodeNumber;
    }

    public String getEpisodePlot() {
        return mEpisodePlot;
    }

    public float getEpisodeRating() {
        return mEpisodeRating;
    }

    public String getShowTitle() {
        return mShowTitle;
    }

    public File getShowCover() {
        return mShowCoverFile;
    }

    public String getShowGenres() {
        return mShowGenres;
    }

    public float getShowRating() {
        return mShowRating;
    }

    public String getActors() {
        return mActors;
    }

    public String getDirectors() {
        return mDirectors;
    }

    public long getTVShowId() {
        return mTVShowId;
    }

    public String getAired() {
        return mAired;
    }

    /**
     * Base class implementation
     */
    public File getCover() {
        return getShowCover();
    }

    public void setDetailName(TextView view, Resources res) {
        if (mShowTitle != null) {
            view.setText(mShowTitle);
            view.setEllipsize(TruncateAt.END);
        }
    }

    public void setDetailLineOne(TextView view, Resources res) {
        view.setText(getEpisodeIdentificationString(res, mSeasonNumber, mEpisodeNumber));
    }

    public void setDetailLineTwo(TextView view, Resources res) {
        if (mEpisodeTitle == null) {
            view.setText(EMPTY);
        } else {
	        // read from R.string.quotation_format, keep a space on
	        // the right side of the string
            String quotation = res.getString(R.string.quotation_format);
            view.setText(String.format(quotation, mEpisodeTitle));
        }
        view.setSingleLine(true);
    }

    public void setDetailLineThree(TextView view, Resources res) {
        if (mEpisodePlot == null) {
            view.setText(EMPTY);
        } else {
            view.setText(mEpisodePlot);
        }
        view.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        view.setSingleLine(false);
        view.setMaxLines(3);
    }

    public float getDetailLineRating() {
        return mEpisodeRating;
    }

    public void setDetailLineReleaseDate(TextView view, Resources res) {
        if (mAired == null) {
            view.setText(EMPTY);
        } else {
            view.setText(res.getString(R.string.scrap_aired_format, mAired));
        }
    }

    public static String getEpisodeIdentificationString(Resources resources, int seasonNumber, int episodeNumber) {
        String ret;
        if (seasonNumber > 0 && episodeNumber > 0)
            ret = resources.getString(R.string.episode_identification, seasonNumber, episodeNumber);
        else {
            ret = resources.getString(R.string.episode_identification)
                    .replace("%d", "\u2007"); // space
        }

        return ret;
    }
}
