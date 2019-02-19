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

package com.archos.mediacenter.video.leanback;

import android.content.res.Resources;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.details.FullWidthRowPresenter;

/**
 * Created by vapillon on 16/04/15.
 */
public class FullTextRowPresenter extends FullWidthRowPresenter {

    private int mColor;
    Resources mR;
    final int mMaxLines;

    public class FullTextRowViewHolder extends RowPresenter.ViewHolder {
        /** the parent viewholder */
        final ViewHolder mFullWidthViewHolder;
        final TextView mTextTv;

        public FullTextRowViewHolder(ViewHolder parentViewHolder, View contentView) {
            super(parentViewHolder.view);

            mFullWidthViewHolder = parentViewHolder;
            mTextTv = (TextView)contentView.findViewById(R.id.text);
            if (mMaxLines>0) {
                mTextTv.setMaxLines(mMaxLines);
                mTextTv.setEllipsize(TextUtils.TruncateAt.END);
            }
        }
    }

    public FullTextRowPresenter() {
        super();
        mMaxLines = -1;
        setHeaderPresenter(new RowHeaderPresenter());
    }

    public FullTextRowPresenter(int maxLines, int color) {
        super();
        mMaxLines = maxLines;
        mColor = color;
        setHeaderPresenter(new RowHeaderPresenter());
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.changeSelectLevel(holder, ((FullTextRowViewHolder) holder).mFullWidthViewHolder);
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        mR = parent.getResources();

        // We create the base class view holder first
        ViewHolder fullWidthViewHolder = (ViewHolder)super.createRowViewHolder(parent);

        // We expand the info view and put it inside the parent fullwidth container
        ViewGroup fullwidthContainer = (ViewGroup)fullWidthViewHolder.getMainContainer();
        View detailsView = LayoutInflater.from(parent.getContext()).inflate(R.layout.androidtv_details_text_only_group, fullwidthContainer, false);
        fullwidthContainer.addView(detailsView);

        fullwidthContainer.setBackgroundColor(mColor);

        return new FullTextRowViewHolder(fullWidthViewHolder, detailsView);
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        FullTextRowViewHolder vh = (FullTextRowViewHolder) holder;
        FullTextRow row = (FullTextRow) item;

        vh.mTextTv.setText(row.getText());
    }
}
