// Copyright 2021 Courville Software
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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.BaseCardView;
import androidx.leanback.widget.Presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.WebPageLink;

public class WebLinkPresenter extends Presenter {

    private int mColor = Color.TRANSPARENT;

    ColorFilter mDimColorFilter = new PorterDuffColorFilter(0x90000000, PorterDuff.Mode.SRC_ATOP);

    public WebLinkPresenter(int color) {
        mColor = color;
    }

    public class ViewHolder extends Presenter.ViewHolder {

        final public BaseCardView mBaseCard;
        final public ImageView mImageView;
        final public View mSelectionView;

        public ViewHolder(ViewGroup parent) {
            super(new BaseCardView(parent.getContext()));
            final Context c = parent.getContext();

            mBaseCard = (BaseCardView)view;
            mBaseCard.setFocusable(true);
            mBaseCard.setFocusableInTouchMode(true);
            mBaseCard.setBackgroundColor(mColor);

            LayoutInflater.from(c).inflate(R.layout.leanback_weblink_item, mBaseCard);

            View shell = mBaseCard.findViewById(R.id.icon_item_shell);
            mImageView = (ImageView)shell.findViewById(R.id.image);
            mSelectionView = shell.findViewById(R.id.selection);

            // HACK: Allow us to get this ViewHolder while in IconItemRowPresenter, in order to apply dim effect with setDimmed
            shell.setTag(ViewHolder.this);

            // init in non-selected state
            mSelectionView.setAlpha(0);
            // change selection visibility on focus
            view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean focus) {
                    mSelectionView.animate().alpha(focus ? 1f : 0f).setDuration(200);
                }
            });

            // init in dimmed state
            setDimmed(false);
        }

        /**
         * HACK: Did not manage to handle dimming with regular use of the ListRowPresenter, hence an ugly hack
         * to get this ViewHolder in the presenter to call this method
         * @param dimmed
         */
        public void setDimmed(boolean dimmed) {
            if (dimmed) {
                mImageView.setColorFilter(mDimColorFilter);
            } else {
                mImageView.clearColorFilter();
            }
        }
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder)viewHolder;
        WebPageLink webPageLink = (WebPageLink)item;
        if (webPageLink.getUrl().contains("themoviedb"))
            vh.mImageView.setImageResource(R.drawable.tmdb_banner);
        if (webPageLink.getUrl().contains("imdb"))
            vh.mImageView.setImageResource(R.drawable.imdb_logo);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }
}
