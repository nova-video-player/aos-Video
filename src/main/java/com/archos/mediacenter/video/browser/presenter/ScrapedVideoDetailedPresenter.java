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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesDetails;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.squareup.picasso.Picasso;

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
        LinearLayout ratingContainer;
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
        holder.ratingContainer = (LinearLayout) view.findViewById(R.id.rating_container);
        return view;

    }

    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        super.bindView(view, object, thumbnailResult, positionInAdapter);
        ViewHolderDetails holder = (ViewHolderDetails) view.getTag();
        Video video = (Video) object;

        long date = -1;
        float rating = -1;
        String ratingFormated;
        String detailedLineOne = "";
        String detailedLineTwo = "";
        String detailedLineThree = "";
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.info.getLayoutParams();
        if(video instanceof Movie) {
            Movie movie = (Movie) video;
            rating = movie.getRating();
            if (rating >= 0.0f) {
                ratingFormated = mNumberFormat.format(rating);
            } else {
                ratingFormated = "";
            }
            if (ratingFormated.isEmpty() || ratingFormated.equalsIgnoreCase("0.0")){
                holder.rating.setVisibility(View.GONE);
                holder.ratingContainer.setVisibility(View.GONE);
                params.removeRule(RelativeLayout.ABOVE);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                holder.info.setLayoutParams(params);
            }else{
                holder.rating.setVisibility(View.VISIBLE);
                holder.ratingContainer.setVisibility(View.VISIBLE);
                holder.rating.setText(ratingFormated);
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.ABOVE, R.id.rating_container);
                holder.info.setLayoutParams(params);
            }

            detailedLineOne = mContext.getResources().getString(R.string.scrap_director)+" "+movie.getDirector();
            detailedLineTwo = movie.getDescriptionBody();
            detailedLineThree = mContext.getResources().getString(R.string.scrap_cast)+" "+movie.getActors();
            holder.detailLineOne.setText(detailedLineOne);
            holder.detailLineOne.setVisibility(View.GONE);
            holder.detailLineThree.setText(detailedLineThree);
            holder.detailLineThree.setVisibility(View.GONE);
            holder.detailLineTwo.setText(detailedLineTwo);
            holder.detailLineTwo.setVisibility(View.VISIBLE);

            date = ((Movie)video).getYear();
            if (date > 0) {
                holder.release_date.setText(String.valueOf(date));
            } else {
                holder.release_date.setText(mContext.getResources().getString(R.string.scrap_year));
            }
            holder.detailLineTwo.setSingleLine(false);
            holder.detailLineTwo.setMaxLines(4);

            // Set thumbnail.
            if (movie.getPosterUri() != null) {
                Picasso.get().load(String.valueOf(movie.getPosterUri())).into(holder.thumbnail);
                holder.thumbnail.clearColorFilter();
                holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
            holder.release_date.setVisibility(View.VISIBLE);

        }else if(video instanceof  Episode){
            Episode episode = (Episode)video;
            rating = episode.getEpisodeRating();
            if (rating >= 0.0f) {
                ratingFormated = mNumberFormat.format(rating);
            } else {
                ratingFormated = "";
            }
            if (ratingFormated.isEmpty() || ratingFormated.equalsIgnoreCase("0.0")){
                holder.rating.setVisibility(View.GONE);
                holder.ratingContainer.setVisibility(View.GONE);
                params.removeRule(RelativeLayout.ABOVE);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                holder.info.setLayoutParams(params);
            }else{
                holder.rating.setVisibility(View.VISIBLE);
                holder.ratingContainer.setVisibility(View.VISIBLE);
                holder.rating.setText(ratingFormated);
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.addRule(RelativeLayout.ABOVE, R.id.rating_container);
                holder.info.setLayoutParams(params);
            }

            detailedLineOne = mContext.getResources().getString(R.string.episode_season)+" "
                    +episode.getSeasonNumber()+" "
                    + mContext.getResources().getString(R.string.episode_name)+" "
                    +episode.getEpisodeNumber();

            detailedLineTwo = episode.getEpisodeName();
            detailedLineThree = episode.getDescriptionBody();
            holder.detailLineOne.setText(detailedLineOne);
            holder.detailLineOne.setVisibility(View.GONE);
            holder.detailLineThree.setVisibility(View.VISIBLE);
            holder.detailLineThree.setText(detailedLineThree);
            holder.detailLineTwo.setText(detailedLineTwo);
            holder.detailLineTwo.setVisibility(View.GONE);

            date = ((Episode)video).getEpisodeDate();
            if (date > 0) {
                holder.release_date.setText(mContext.getResources().getString(R.string.scrap_aired_format, mDateFormat.format(new Date(date))));
                holder.release_date.setVisibility(View.VISIBLE);
            } else {
                holder.release_date.setVisibility(View.GONE);
            }
            holder.detailLineTwo.setSingleLine(true);
            holder.detailLineThree.setSingleLine(false);
            holder.detailLineThree.setMaxLines(4);

            // Set thumbnail.
            if (episode.getPosterUri() != null) {
                Picasso.get().load(String.valueOf(episode.getPosterUri())).into(holder.thumbnail);
                holder.thumbnail.clearColorFilter();
                holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }else{
            params.removeRule(RelativeLayout.ABOVE); // Remove the above rule
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); // Anchor to the bottom instead
            holder.info.setLayoutParams(params); // Apply the changes

            holder.rating.setVisibility(View.GONE);
            holder.ratingContainer.setVisibility(View.GONE);
            holder.detailLineThree.setVisibility(View.GONE);
            holder.release_date.setVisibility(View.GONE);
            holder.detailLineTwo.setVisibility(View.GONE);
        }

        return view;
    }
}
