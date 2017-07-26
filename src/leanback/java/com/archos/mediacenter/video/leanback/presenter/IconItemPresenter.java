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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.Icon;

/**
 * Created by vapillon on 10/04/15.
 */
public class IconItemPresenter extends Presenter {

    ColorFilter mDimColorFilter = new PorterDuffColorFilter(0x90000000, PorterDuff.Mode.SRC_ATOP);


    public class ViewHolder extends Presenter.ViewHolder {

        final public BaseCardView mBaseCard;
        final public ImageView mImageView;
        final public View mSelectionView;
        final public TextView mTitleView;
        final int mTextColorRegular;
        final int mTextColorDimmed;

        public ViewHolder(ViewGroup parent) {
            super(new BaseCardView(parent.getContext()));
            final Context c = parent.getContext();

            mTextColorRegular = c.getResources().getColor(R.color.lb_basic_card_title_text_color);
            mTextColorDimmed = 0xFF707070; // MAGICAL

            mBaseCard = (BaseCardView)view;
            mBaseCard.setFocusable(true);
            mBaseCard.setFocusableInTouchMode(true);
            mBaseCard.setBackgroundColor(Color.TRANSPARENT);

            LayoutInflater.from(c).inflate(R.layout.leanback_icon_item, mBaseCard);

            View shell = mBaseCard.findViewById(R.id.icon_item_shell);
            mImageView = (ImageView)shell.findViewById(R.id.image);
            mSelectionView = shell.findViewById(R.id.selection);
            mTitleView = (TextView)shell.findViewById(R.id.title);

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
            setDimmed(true);
        }

        /**
         * HACK: Did not manage to handle dimming with regular use of the ListRowPresenter, hence an ugly hack
         * to get this ViewHolder in the presenter to call this method
         * @param dimmed
         */
        public void setDimmed(boolean dimmed) {
            if (dimmed) {
                mImageView.setColorFilter(mDimColorFilter);
                mTitleView.setTextColor(mTextColorDimmed);
            } else {
                mImageView.clearColorFilter();
                mTitleView.setTextColor(mTextColorRegular);
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
        Icon icon = (Icon)item;
        vh.mImageView.setImageResource(icon.getIconResId());
        vh.mTitleView.setText(icon.getName());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }
}
