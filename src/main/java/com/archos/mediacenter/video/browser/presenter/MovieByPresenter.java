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
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;

/**
 * Created by alexandre on 26/10/15.
 */
public class MovieByPresenter extends CommonPresenter{



    public MovieByPresenter(Context context, AdapterDefaultValues defaultValues, ExtendedClickListener onExtendedClick) {
        super(context, defaultValues,onExtendedClick);

    }


    @Override
    public View getView(ViewGroup parent, Object object, View view) {
        View v = super.getView(parent, object, view);
        ViewHolder holder = (ViewHolder) v.getTag();
        holder.expanded.setVisibility(View.GONE);
        holder.resume.setVisibility(View.GONE);
        holder.secondLine.setVisibility(View.VISIBLE);
        holder.network.setVisibility(View.GONE);
        holder.subtitle.setVisibility(View.GONE);
        return v;
    }

    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        super.bindView(view, object, thumbnailResult, positionInAdapter);
        ViewHolder holder = (ViewHolder) view.getTag();




        // ------------------------------------------------
        // File-based item => fill the ViewHolder fields depending
        // on the file type (file, folder or shortcut)
        // ------------------------------------------------


        // Set name.


        // Set duration.
        //if(holder.info!=null)
        //    holder.info.setText(video.getInfo());


        // Set thumbnail.
        if(holder.thumbnail!=null) {
            if (thumbnailResult == null || thumbnailResult.getThumbnail() == null) {
                holder.thumbnail.setImageResource(mDefaultValues.getDefaultVideoThumbnail());

            } else {
                holder.thumbnail.setImageBitmap(thumbnailResult.getThumbnail());
                holder.thumbnail.clearColorFilter();
                holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP); // poster must be scaled in detailled view
            }
        }


        holder.info.setText(((Pair<String,String>)object).second);
        holder.name.setText(((Pair<String,String>)object).first);
        if(holder.expanded!=null)
            holder.expanded.setVisibility(View.GONE);


        return view;
    }

}
