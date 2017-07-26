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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.adapter.object.Shortcut;

/**
 * Created by vapillon on 10/04/15.
 */
public class NetworkShortcutPresenter extends Presenter {

    public class NetworkShortcutViewHolder extends ViewHolder {
        final BaseCardView mCard;
        final ImageView mImageView;
        final TextView mNameTv;
        final TextView mPathTv;

        public NetworkShortcutViewHolder(ViewGroup parent) {
            super(new BaseCardView(parent.getContext()));
            mCard = (BaseCardView)view;
            mCard.setFocusable(true);
            mCard.setFocusableInTouchMode(true);

            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_smb_item, mCard, false);
            mImageView = (ImageView)view.findViewById(R.id.image);
            mNameTv = (TextView)view.findViewById(R.id.primary);
            mPathTv = (TextView)view.findViewById(R.id.secondary);

            BaseCardView.LayoutParams lp = new BaseCardView.LayoutParams(view.getLayoutParams());
            lp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_MAIN;
            mCard.addView(view);
        }
        public ImageView getImageView() {
            return mImageView;
        }
        public TextView getNameTextView() {
            return mNameTv;
        }
        public TextView getPathTextView() {
            return mPathTv;
        }
    }

    @Override
    public NetworkShortcutViewHolder onCreateViewHolder(ViewGroup parent) {
        return new NetworkShortcutViewHolder(parent);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        NetworkShortcutViewHolder vh = (NetworkShortcutViewHolder)viewHolder;
        Shortcut shortcut = (Shortcut)item;
        vh.getImageView().setImageResource(shortcut.getImage());
        vh.getNameTextView().setText(shortcut.getName());
        vh.getPathTextView().setVisibility(View.GONE);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
