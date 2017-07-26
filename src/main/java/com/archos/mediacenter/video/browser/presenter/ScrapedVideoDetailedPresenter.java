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

package com.archos.mediacenter.video.browser.presenter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesDetails;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;

import httpimage.HttpImageManager;

/**
 * Created by alexandre on 26/10/15.
 */
public class ScrapedVideoDetailedPresenter extends VideoListPresenter{
    private static final String ITALIC = "</i>";
    private final NumberFormat mNumberFormat;
    private final DateFormat mDateFormat;

    public ScrapedVideoDetailedPresenter(Context context, ExtendedClickListener onExtendedClick, HttpImageManager imageManager) {
        super(context, AdapterDefaultValuesDetails.INSTANCE,  onExtendedClick,imageManager);

        mNumberFormat = NumberFormat.getInstance();
        mNumberFormat.setMinimumFractionDigits(1);
        mNumberFormat.setMaximumFractionDigits(1);
        mDateFormat = DateFormat.getDateInstance(DateFormat.LONG);
    }
    static class ViewHolderDetails extends ViewHolder {
        TextView detailLineOne;
        TextView detailLineTwo;
        TextView detailLineThree;
        TextView rating;
        TextView release_date;
    }
    @Override
    public ViewHolderDetails getNewViewHolder() {
        return new ViewHolderDetails();
    }
    @Override
    public View getView(ViewGroup parent, Object object, View view){
        view = super.getView(parent, object, view);
        ViewHolderDetails holder = (ViewHolderDetails) view.getTag();
        holder.detailLineOne = (TextView) view.findViewById(R.id.detail_line_one);
        holder.detailLineTwo = (TextView) view.findViewById(R.id.detail_line_two);
        holder.detailLineThree = (TextView) view.findViewById(R.id.detail_line_three);
        holder.rating = (TextView) view.findViewById(R.id.rating);
        holder.release_date = (TextView) view.findViewById(R.id.release_date);
        return view;

    }

    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        super.bindView(view, object, thumbnailResult, positionInAdapter);
        ViewHolderDetails holder = (ViewHolderDetails) view.getTag();
        Video video = (Video) object;



        long date = -1;
        float rating = -1;
        String detailedLineOne = "";
        String detailedLineTwo = "";
        String detailedLineThree = "";
        if(video instanceof Movie) {
            Movie movie = (Movie) video;
            rating = movie.getRating();
            detailedLineOne = mContext.getResources().getString(R.string.scrap_director)+" "+movie.getDirector();
            detailedLineTwo = movie.getDescriptionBody();
            detailedLineThree = mContext.getResources().getString(R.string.scrap_cast)+" "+movie.getActors();
        }
        else if(video instanceof  Episode){
            Episode episode = (Episode)video;
            rating = episode.getEpisodeRating();
            detailedLineOne = mContext.getResources().getString(R.string.episode_season)+" "
                    +episode.getSeasonNumber()+" "
                    + mContext.getResources().getString(R.string.episode_name)+" "
                    +episode.getEpisodeNumber();

            detailedLineTwo = episode.getEpisodeName();
            detailedLineThree = episode.getDescriptionBody();
        }
        holder.detailLineOne.setText(detailedLineOne);
        holder.detailLineTwo.setText(detailedLineTwo);
        holder.detailLineThree.setText(detailedLineThree);
        String ratingFormated;
        if (rating >= 0.0f) {
            ratingFormated = mNumberFormat.format(rating);
        } else {
            ratingFormated = "";
        }
        holder.rating.setText(ratingFormated);

        if(video instanceof Movie) {
            date = ((Movie)video).getYear();
            if (date > 0) {
                holder.release_date.setText(String.valueOf(date))   ;
            } else {
                holder.release_date.setText(mContext.getResources().getString(R.string.scrap_year));
            }
            holder.detailLineTwo.setSingleLine(false);
            holder.detailLineTwo.setMaxLines(3);
            holder.detailLineThree.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            holder.detailLineThree.setSingleLine(true);
        }
        else if (video instanceof Episode) {
            date = ((Episode)video).getEpisodeDate();
            if (date > 0) {
                holder.release_date.setText(mContext.getResources().getString(
                        R.string.scrap_aired_format, mDateFormat.format(new Date(date))));
            } else {
                holder.release_date.setText(R.string.scrap_aired);
            }
            holder.detailLineTwo.setSingleLine(true);
            holder.detailLineThree.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            holder.detailLineThree.setSingleLine(false);
            holder.detailLineThree.setMaxLines(3);
        }
        return view;
    }
}
