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
import com.archos.mediacenter.video.leanback.adapter.object.SmbShare;
import com.archos.mediacenter.video.leanback.adapter.object.UpnpServer;

/**
* Created by vapillon on 10/04/15.
*/
public class SmbSharePresenter extends Presenter {

    private class SmbShortcutViewHolder extends ViewHolder {
        BaseCardView mCard;
        ImageView mImageView;
        TextView mNameTv;
        TextView mWorkgroupTv;

        public SmbShortcutViewHolder(ViewGroup parent) {
            super(new BaseCardView(parent.getContext()));
            mCard = (BaseCardView)view;
            mCard.setFocusable(true);
            mCard.setFocusableInTouchMode(true);

            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.leanback_smb_item, mCard, false);
            mImageView = (ImageView)view.findViewById(R.id.image);
            mImageView.setImageResource(R.drawable.filetype_video_server); // always the same icon
            mNameTv = (TextView)view.findViewById(R.id.primary);
            mWorkgroupTv = (TextView)view.findViewById(R.id.secondary);

            BaseCardView.LayoutParams lp = new BaseCardView.LayoutParams(view.getLayoutParams());
            lp.viewType = BaseCardView.LayoutParams.VIEW_TYPE_MAIN;
            mCard.addView(view);
        }
        ImageView getImageView() {
            return mImageView;
        }
        TextView getNameTextView() {
            return mNameTv;
        }
        TextView getWorkgroupTextView() {
            return mWorkgroupTv;
        }
    }

   @Override
   public SmbShortcutViewHolder onCreateViewHolder(ViewGroup parent) {
       return new SmbShortcutViewHolder(parent);
   }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SmbShortcutViewHolder vh = (SmbShortcutViewHolder)viewHolder;
        if (item instanceof SmbShare) {
            SmbShare share = (SmbShare) item;
            vh.getNameTextView().setText(share.getDisplayName());
            vh.getWorkgroupTextView().setText(share.getWorkgroup());
        } else if (item instanceof UpnpServer) {
            UpnpServer server = (UpnpServer)item;
            vh.getNameTextView().setText(server.getName());
            vh.getWorkgroupTextView().setText(server.getModelName());
        }
    }

   @Override
   public void onUnbindViewHolder(ViewHolder viewHolder) {
   }
}
