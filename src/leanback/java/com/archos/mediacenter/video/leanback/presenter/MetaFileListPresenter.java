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
import android.view.View;
import android.widget.ImageView;

import com.archos.filecorelibrary.MetaFile2;

/**
 * ListPresenter for MetaFile objects (used for folders and non-video files)
 * Created by vapillon on 10/04/15.
 */
public class MetaFileListPresenter extends ListPresenter {

    private Context mContext;

    @Override
    public void onBindListViewHolder(ListViewHolder viewHolder, Object item) {
        ListViewHolder vh = (ListViewHolder)viewHolder;
        MetaFile2 file = (MetaFile2)item;

        vh.setTitleText(file.getName());
        vh.getImageView().setImageResource(PresenterUtils.getIconResIdFor(file));
        vh.getImageView().setScaleType(ImageView.ScaleType.CENTER);
        vh.setContentTextVisibility(View.GONE);
    }
}
