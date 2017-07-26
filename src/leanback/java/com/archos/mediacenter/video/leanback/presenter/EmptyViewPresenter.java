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

import android.graphics.Color;
import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.EmptyView;

/**
 * Created by vapillon on 10/04/15.
 */
public class EmptyViewPresenter extends Presenter {

    public class ViewHolder extends Presenter.ViewHolder {
        public BaseCardView mCardView;
        ImageView mImageView;
        TextView mTextView;

        public ViewHolder(ViewGroup parent) {
            super(new BaseCardView(parent.getContext()));
            mCardView = (BaseCardView)view;
            LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_row_emptyview, (ViewGroup)mCardView);
            mTextView = (TextView)mCardView.findViewById(R.id.text);

            mCardView.setClickable(false);
            mCardView.setFocusable(true);
            mCardView.setFocusableInTouchMode(true);
            mCardView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        Presenter.ViewHolder vh = new ViewHolder(parent);
        return vh;
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder)viewHolder;
        EmptyView emptyView = (EmptyView)item;
        vh.mTextView.setText(emptyView.getMessage());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }
}
