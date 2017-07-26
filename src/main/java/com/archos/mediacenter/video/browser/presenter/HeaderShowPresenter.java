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

package com.archos.mediacenter.video.browser.presenter;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesList;
import com.archos.mediacenter.video.browser.adapters.ItemViewType;
import com.archos.mediacenter.video.browser.adapters.object.Season;

/**
 * Created by alexandre on 28/10/15.
 */
public class HeaderShowPresenter extends CommonPresenter {
    public HeaderShowPresenter(Context context) {
        super(context, AdapterDefaultValuesList.INSTANCE, null );
    }
    @Override
    public int getItemType() {
        return ItemViewType.ITEM_VIEW_TYPE_HEADER_SHOW;
    }
    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {

        ViewHolder holder = (ViewHolder) view.getTag();
        Season season = (Season) object;
        // Set TV show title
        holder.name.setText(season.getName());
        holder.name.setEllipsize(TextUtils.TruncateAt.END);
        // Set thumbnail.
        if (thumbnailResult == null || thumbnailResult.getThumbnail() == null) {
            holder.thumbnail.setImageResource(mDefaultValues.getDefaultVideoThumbnail());
            //holder.thumbnail.setColorFilter(mDefaultIconsColor);
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER); // thumbnail may be smaller, must not be over scaled
        } else {
            holder.thumbnail.setImageBitmap(thumbnailResult.getThumbnail());
            holder.thumbnail.clearColorFilter();
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP); // poster must be scaled in detailled view
        }
        // Set TV show season
        if(holder.number!=null)
            holder.number.setText(mContext.getResources()   .getString(R.string.episode_season) + " " + ((Season) object).getSeasonNumber());
        return view;
    }


}
