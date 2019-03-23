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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;

/**
 * Created by alexandre on 27/10/15.
 */
public class TvShowPresenter extends CommonPresenter implements Presenter {

    private static final boolean DBG = false;

    public TvShowPresenter(Context context, AdapterDefaultValues defaultValues, ExtendedClickListener listener) {
        super(context, defaultValues, listener);
    }

    @Override
    public View bindView(View view, Object object, ThumbnailEngine.Result result, int positionInAdapter) {
        super.bindView(view, object, result, positionInAdapter);
        Tvshow tvShow = (Tvshow) object;
                // ------------------------------------------------
                // File-based item => fill the ViewHolder fields depending
                // on the file type (file, folder or shortcut)
                // ------------------------------------------------
        ViewHolder holder = (ViewHolder) view.getTag();

        if(holder.secondLine!=null)
            holder.secondLine.setVisibility(View.VISIBLE);
        // Set thumbnail.
        if (result == null || result.getThumbnail() == null) {
            holder.thumbnail.setImageResource(mDefaultValues.getDefaultVideoThumbnail());
            //holder.thumbnail.setColorFilter(mDefaultIconsColor);
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER); // thumbnail may be smaller, must not be over scaled

        } else {
            holder.thumbnail.setImageBitmap(result.getThumbnail());
            holder.thumbnail.clearColorFilter();
            holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP); // poster must be scaled in detailled view

        }

        int count = tvShow.getSeasonCount();
        if (DBG) Log.d("XXX", "getInfo() count=" + count);
        String format;
        if (count > 1) {
            format = mContext.getResources().getQuantityText(R.plurals.Nseasons, count).toString();
        } else {
            count = tvShow.getEpisodeCount();
            format = mContext.getResources().getQuantityText(R.plurals.Nepisodes, count).toString();
        }
        if(holder.info!=null)
            holder.info.setText(String.format(format, count));
        //no resume in tv shows
        holder.bookmark.setVisibility(View.VISIBLE);
        holder.bookmark.setEnabled(false);
        holder.subtitle.setVisibility(View.VISIBLE);
        holder.subtitle.setEnabled(false);
        holder.resume.setVisibility(View.GONE);
        if(holder.expanded!=null)
            holder.expanded.setVisibility(View.GONE);

        if (holder.traktWatched != null)
            holder.traktWatched.setVisibility(tvShow.isTraktSeen() ? View.VISIBLE : View.GONE);
        if (holder.traktLibrary != null)
            holder.traktLibrary.setVisibility(tvShow.isTraktLibrary() ? View.VISIBLE : View.GONE);


        return view;
    }
}
