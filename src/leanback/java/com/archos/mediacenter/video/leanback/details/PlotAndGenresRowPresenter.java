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

package com.archos.mediacenter.video.leanback.details;

import android.content.res.Resources;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

/**
 * Created by vapillon on 16/07/15.
 */
public class PlotAndGenresRowPresenter extends FullWidthRowPresenter implements BackgroundColorPresenter{

    private int mColor;
    Resources mR;
    final int mMaxLines;

    private PlotAndGenresViewHolder mHolder;

    public class PlotAndGenresViewHolder extends RowPresenter.ViewHolder {
        /** the parent viewholder */
        final ViewHolder mFullWidthViewHolder;
        final TextView mPlotTv;
        final View mGenresLayout;
        final TextView mGenresTv;

        public PlotAndGenresViewHolder(ViewHolder parentViewHolder, View contentView) {
            super(parentViewHolder.view);

            mFullWidthViewHolder = parentViewHolder;
            mPlotTv = (TextView)contentView.findViewById(R.id.plot);
            if (mMaxLines>0) {
                mPlotTv.setMaxLines(mMaxLines);
                mPlotTv.setEllipsize(TextUtils.TruncateAt.END);
            }
            mGenresLayout = contentView.findViewById(R.id.genres_layout);
            mGenresTv = (TextView)contentView.findViewById(R.id.genres);
        }
    }

    public PlotAndGenresRowPresenter() {
        super();
        mMaxLines = -1;
        setHeaderPresenter(new RowHeaderPresenter());
    }

    @Override
    public void setBackgroundColor(int color) {
        mColor = color;

        if (mHolder != null)
            mHolder.mFullWidthViewHolder.getMainContainer().setBackgroundColor(color);
    }

    public PlotAndGenresRowPresenter(int maxLines, int color) {
        super();
        mColor = color;
        mMaxLines = maxLines;
        setHeaderPresenter(new RowHeaderPresenter());
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.changeSelectLevel(holder, ((PlotAndGenresViewHolder) holder).mFullWidthViewHolder);
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        mR = parent.getResources();

        // We create the base class view holder first
        ViewHolder fullWidthViewHolder = (ViewHolder)super.createRowViewHolder(parent);

        // We expand the info view and put it inside the parent fullwidth container
        ViewGroup fullwidthContainer = (ViewGroup)fullWidthViewHolder.getMainContainer();
        View detailsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_details_plot_and_genres_group, fullwidthContainer, false);
        fullwidthContainer.addView(detailsView);

        fullwidthContainer.setBackgroundColor(mColor);

        return new PlotAndGenresViewHolder(fullWidthViewHolder, detailsView);
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);
        PlotAndGenresViewHolder vh = (PlotAndGenresViewHolder) holder;
        vh.mFullWidthViewHolder.getMainContainer().setBackgroundColor(mColor);

        PlotAndGenresRow row = (PlotAndGenresRow) item;

        vh.mPlotTv.setText(row.getPlot());

        if (row.getGenres()!=null && !row.getGenres().isEmpty()) {
            vh.mGenresTv.setText(row.getGenres());
            vh.mGenresLayout.setVisibility(View.VISIBLE);
        } else {
            vh.mGenresLayout.setVisibility(View.GONE);
        }

        mHolder = vh;
    }
}
