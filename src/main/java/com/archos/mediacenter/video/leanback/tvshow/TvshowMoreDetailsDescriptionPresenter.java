/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.archos.mediacenter.video.leanback.tvshow;

import androidx.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.ShowTags;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TvshowMoreDetailsDescriptionPresenter extends Presenter {

    private static final String TAG = "TvshowMoreDetailsDescriptionPresenter";

    private boolean mShowWatched;
    
    public static class ViewHolder extends Presenter.ViewHolder {
        final TextView mTitle;
        final TextView mDate;
        final TextView mRating;
        final View mContentRatingContainer;
        final TextView mContentRating;
        final ImageView mTraktWatched;

        public ViewHolder(final View view) {
            super(view);

            mTitle = (TextView) view.findViewById(androidx.leanback.R.id.lb_details_description_title);
            mDate = (TextView) view.findViewById(R.id.date);
            mRating = (TextView) view.findViewById(R.id.rating);
            mContentRatingContainer = (View) view.findViewById(R.id.content_rating_container);
            mContentRating = (TextView) view.findViewById(R.id.content_rating);
            mTraktWatched = (ImageView) view.findViewById(R.id.trakt_watched);
        }
    }

    public TvshowMoreDetailsDescriptionPresenter(boolean showWatched) {
        mShowWatched = showWatched;
    }
    
    @Override
    public final ViewHolder onCreateViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_tvshow_more_details_description, parent, false);
        
        return new ViewHolder(v);
    }
    
    @Override
    public final void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder)viewHolder;
        ShowTags tags = (ShowTags)item;

        vh.mTitle.setText(tags.getTitle());
        setTextOrSetGoneIfEmpty(vh.mDate, getYearFormatted(tags.getPremiered()));
        setTextOrSetGoneIfZero(vh.mRating, tags.getRating());
        setTextOrSetGoneIfEmpty(vh.mContentRating, tags.getContentRating());
        vh.mContentRating.setTextColor(TvshowMoreDetailsFragment.getDominantColor());
        String contentRating = tags.getContentRating();
        if (contentRating == null || contentRating.isEmpty()) vh.mContentRatingContainer.setVisibility(View.GONE);
        vh.mTraktWatched.setVisibility(mShowWatched ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ViewHolder vh = (ViewHolder)viewHolder;
    }
    
    private void setTextOrSetGoneIfEmpty(TextView mTextView, String text) {
        if (text==null || text.isEmpty()) {
            mTextView.setVisibility(View.GONE);
        } else {
            mTextView.setText(text);
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    private void setTextOrSetGoneIfZero(TextView mTextView, float value) {
        if (value == 0f) {
            mTextView.setVisibility(View.GONE);
        } else {
            mTextView.setText( Float.toString(value));
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    private String getYearFormatted(Date date) {
        if (date != null && date.getTime() > 0) {
            return new SimpleDateFormat("yyyy").format(date);
        }
        return null;
    }
}
