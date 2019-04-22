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
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowPresenter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 20/02/17.
 */

public class ArchosDetailsOverviewRowPresenter extends FullWidthDetailsOverviewRowPresenter {


    private final Presenter mDetailsPresenter;
    private ViewHolder mViewHolder;
    private boolean mHideActions;

    /**
     * Constructor for a DetailsOverviewRowPresenter.
     *
     * @param detailsPresenter The {@link Presenter} used to render the detailed
     *                         description of the row.
     */
    public ArchosDetailsOverviewRowPresenter(Presenter detailsPresenter) {
        this(detailsPresenter, false);
    }

    public ArchosDetailsOverviewRowPresenter(Presenter detailsPresenter, boolean hideActions) {
        super(detailsPresenter);
        mDetailsPresenter = detailsPresenter;
        mHideActions = hideActions;
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        mViewHolder = (ViewHolder)holder;
        
        updateBackgroundColor(getBackgroundColor());
        updateActionsBackgroundColor(getActionsBackgroundColor());
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
                        androidx.leanback.R.dimen.lb_details_v2_logo_margin_start));
                break;
            case ALIGN_MODE_MIDDLE:
                lp.setMarginStart(v.getResources().getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_left)
                        - lp.width);
                break;
        }
        lp.topMargin = v.getResources().getDimensionPixelSize(
                    androidx.leanback.R.dimen.lb_details_v2_blank_height) + v
                    .getResources().getDimensionPixelSize(
                    androidx.leanback.R.dimen.lb_details_v2_description_margin_top);
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
                                androidx.leanback.R.dimen.lb_details_v2_logo_margin_start);
                        descriptionMarginStart = logoWidth;
                    } else {
                        frameMarginStart = 0;
                        descriptionMarginStart = logoWidth + res.getDimensionPixelSize(
                                androidx.leanback.R.dimen.lb_details_v2_logo_margin_start);
                    }
                    break;
                case ALIGN_MODE_MIDDLE:
                    if (isBanner) {
                        frameMarginStart = res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_left)
                                - logoWidth;
                        descriptionMarginStart = logoWidth;
                    } else {
                        frameMarginStart = 0;
                        descriptionMarginStart = res.getDimensionPixelSize(
                                androidx.leanback.R.dimen.lb_details_v2_left);
                    }
                    break;
            }
            MarginLayoutParams lpRoot =
                    (MarginLayoutParams) ((ViewGroup)viewHolder.getOverviewView().getParent()).getLayoutParams();
            lpRoot.leftMargin = res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_overview_margin_start);
            lpRoot.rightMargin = res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_overview_margin_end);
            MarginLayoutParams lpFrame =
                    (MarginLayoutParams) viewHolder.getOverviewView().getLayoutParams();
            lpFrame.topMargin = isBanner ? 0
                    : res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_blank_height);
            lpFrame.leftMargin = lpFrame.rightMargin = frameMarginStart;
            if (mHideActions) {
                lpFrame.height = res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_card_height)
                        - res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_actions_height);
            }
            viewHolder.getOverviewView().setLayoutParams(lpFrame);
            View description = viewHolder.getDetailsDescriptionFrame();
            MarginLayoutParams lpDesc = (MarginLayoutParams) description.getLayoutParams();
            lpDesc.setMarginStart(descriptionMarginStart);
            description.setLayoutParams(lpDesc);
            View action = viewHolder.getActionsRow();
            MarginLayoutParams lpActions = (MarginLayoutParams) action.getLayoutParams();
            lpActions.setMarginStart(res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_logo_margin_start));
            lpActions.setMarginEnd(res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_description_margin_end));
            lpActions.height =
                    isBanner ? 0 : res.getDimensionPixelSize(androidx.leanback.R.dimen.lb_details_v2_actions_height);
            if (mHideActions)
                lpActions.height = 0;
            action.setLayoutParams(lpActions);
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.leanback_details_overview;
    }

    public void updateBackgroundColor(int color) {
        setBackgroundColor(color);

        if (mViewHolder != null)
            mViewHolder.getOverviewView().setBackgroundColor(color);
    }

    public void updateActionsBackgroundColor(int color) {
        setActionsBackgroundColor(color);

        if (mViewHolder != null)
            mViewHolder.getOverviewView().findViewById(androidx.leanback.R.id.details_overview_actions_background).setBackgroundColor(color);
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
