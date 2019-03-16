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
import android.support.v17.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;



/**
 * Created by alexandre on 20/02/17.
 */

public class ArchosDetailsOverviewRowPresenter extends FullWidthDetailsOverviewRowPresenter {


    private final Presenter mDetailsPresenter;
    private ViewHolder mViewHolder;

    /**
     * Constructor for a DetailsOverviewRowPresenter.
     *
     * @param detailsPresenter The {@link Presenter} used to render the detailed
     *                         description of the row.
     */
    public ArchosDetailsOverviewRowPresenter(Presenter detailsPresenter) {
        super(detailsPresenter);
        mDetailsPresenter = detailsPresenter;
    }
    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        mViewHolder = (ViewHolder)super.createRowViewHolder(parent);
        return mViewHolder;
    }

    @Override
    protected void onLayoutLogo(ViewHolder viewHolder, int oldState, boolean logoChanged) {
        View v = viewHolder.getLogoViewHolder().view;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                v.getLayoutParams();
        switch (getAlignmentMode()) {
            case ALIGN_MODE_START:
            default:
                lp.setMarginStart(v.getResources().getDimensionPixelSize(
                        android.support.v17.leanback.R.dimen.lb_details_v2_logo_margin_start));
                break;
            case ALIGN_MODE_MIDDLE:
                lp.setMarginStart(v.getResources().getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_v2_left)
                        - lp.width);
                break;
        }
        lp.topMargin = v.getResources().getDimensionPixelSize(
                    android.support.v17.leanback.R.dimen.lb_details_v2_blank_height) + v.getResources()
                    .getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_v2_actions_height) + v
                    .getResources().getDimensionPixelSize(
                    android.support.v17.leanback.R.dimen.lb_details_v2_description_margin_top);
        v.setLayoutParams(lp);
    }

    @Override
    protected void onLayoutOverviewFrame(ViewHolder viewHolder, int oldState, boolean logoChanged) {
        boolean wasBanner = oldState == STATE_SMALL;
        boolean isBanner = viewHolder.getState() == STATE_SMALL;
        if (wasBanner != isBanner || logoChanged) {
            Resources res = viewHolder.view.getResources();
            int frameMarginStart;
            int descriptionMarginStart = 0;
            int logoWidth = viewHolder.getLogoViewHolder().view.getLayoutParams().width;
            switch (getAlignmentMode()) {
                case ALIGN_MODE_START:
                default:
                    if (isBanner) {
                        frameMarginStart = res.getDimensionPixelSize(
                                android.support.v17.leanback.R.dimen.lb_details_v2_logo_margin_start);
                        descriptionMarginStart = logoWidth;
                    } else {
                        frameMarginStart = 0;
                        descriptionMarginStart = logoWidth + res.getDimensionPixelSize(
                                android.support.v17.leanback.R.dimen.lb_details_v2_logo_margin_start);
                    }
                    break;
                case ALIGN_MODE_MIDDLE:
                    if (isBanner) {
                        frameMarginStart = res.getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_v2_left)
                                - logoWidth;
                        descriptionMarginStart = logoWidth;
                    } else {
                        frameMarginStart = 0;
                        descriptionMarginStart = res.getDimensionPixelSize(
                                android.support.v17.leanback.R.dimen.lb_details_v2_left);
                    }
                    break;
            }
            MarginLayoutParams lpFrame =
                    (MarginLayoutParams) viewHolder.getOverviewView().getLayoutParams();
            lpFrame.topMargin = isBanner ? 0
                    : res.getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_v2_blank_height);
            lpFrame.leftMargin = lpFrame.rightMargin = frameMarginStart;
            viewHolder.getOverviewView().setLayoutParams(lpFrame);
            View description = viewHolder.getDetailsDescriptionFrame();
            MarginLayoutParams lpDesc = (MarginLayoutParams) description.getLayoutParams();
            lpDesc.setMarginStart(descriptionMarginStart);
            description.setLayoutParams(lpDesc);
            View action = viewHolder.getActionsRow();
            MarginLayoutParams lpActions = (MarginLayoutParams) action.getLayoutParams();
            lpActions.setMarginStart(res.getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_overview_margin_start));
            lpActions.setMarginEnd(res.getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_overview_margin_end));
            lpActions.height =
                    isBanner ? 0 : res.getDimensionPixelSize(android.support.v17.leanback.R.dimen.lb_details_v2_actions_height);
            action.setLayoutParams(lpActions);
        }
    }

    public void updateBackgroundColor(int color) {
        setBackgroundColor(color);

        if (mViewHolder != null)
            mViewHolder.getOverviewView().setBackgroundColor(color);
    }

    public void updateActionsBackgroundColor(int color) {
        setActionsBackgroundColor(color);

        if (mViewHolder != null)
            mViewHolder.getOverviewView().findViewById(android.support.v17.leanback.R.id.details_overview_actions_background).setBackgroundColor(color);
    }

    public void moveSelectedPosition(int offset) {
        if (mViewHolder != null) {
            int position = ((HorizontalGridView)mViewHolder.getActionsRow()).getSelectedPosition();

            ((HorizontalGridView)mViewHolder.getActionsRow()).setSelectedPosition(position + offset);
        }
    }

    public int getState() {
        return mViewHolder.getState();
    }
}
