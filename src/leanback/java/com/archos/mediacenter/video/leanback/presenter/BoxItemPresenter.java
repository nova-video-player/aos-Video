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

import android.support.v17.leanback.widget.BaseCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.Box;

/**
 * Created by vapillon on 10/04/15.
 */
public class BoxItemPresenter extends Presenter {

    protected class BoxViewHolder extends ViewHolder {
        ViewGroup mRoot;
        BaseCardView mCard;
        ViewGroup mImageViewContainer;
        ImageView mImageView;
        TextView mTextView;

        public BoxViewHolder(ViewGroup parent) {
            super(new CustomBaseCardview(parent.getContext()));
            mCard = (BaseCardView)view;
            mCard.setFocusable(true);
            mCard.setFocusableInTouchMode(true);

            mRoot = (ViewGroup)LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_box, mCard, false);
            mImageViewContainer = (ViewGroup)mRoot.findViewById(R.id.image_container);
            mImageView = (ImageView)mImageViewContainer.findViewById(R.id.image);
            mTextView = (TextView)mRoot.findViewById(R.id.name);

            BaseCardView.LayoutParams lp = new BaseCardView.LayoutParams(mRoot.getLayoutParams());
            lp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_MAIN;
            mCard.addView(mRoot);
        }
        ImageView getImageView() {
            return mImageView;
        }
        TextView getTextView() {
            return mTextView;
        }
    }

    @Override
    public BoxViewHolder onCreateViewHolder(ViewGroup parent) {
        return new BoxViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        final BoxViewHolder vh = (BoxViewHolder)viewHolder;
        Box box = (Box)item;
        vh.getTextView().setText(box.getName());

        if (box.getBitmap()!=null) {
            vh.getImageView().setImageBitmap(box.getBitmap());
        } else {
            vh.getImageView().setImageResource(box.getIconResId());
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
