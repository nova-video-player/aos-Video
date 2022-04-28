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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesGrid;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;

/**
 * Created by alexandre on 27/10/15.
 */
public class TvshowGridPresenter extends TvShowPresenter{
    public TvshowGridPresenter(Context context, ExtendedClickListener listener) {
        this(context, AdapterDefaultValuesGrid.INSTANCE, listener);
    }

    public TvshowGridPresenter(Context context, AdapterDefaultValues defaultValues, ExtendedClickListener listener) {
        super(context, defaultValues, listener);
    }

    @Override
    public View bindView(View view, Object object, ThumbnailEngine.Result result, int positionInAdapter) {
        Tvshow tvShow = (Tvshow) object;

        ViewHolder holder = (ViewHolder) view.getTag();
        super.bindView(view,object, result, positionInAdapter);
        if(holder.secondLine!=null)
            holder.secondLine.setVisibility(View.VISIBLE);

        holder.name.setText(tvShow.getName());

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

        //set gridview thumbnail Width & Height
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean drawerOpen = prefs.getBoolean("drawerOpen", true);
        boolean mIsLandscapeMode = mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean mIsPortraitMode = mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        //width subtraction when number of columns is 5 && mIsLandscapeMode && drawerOpen
        int categoryWidth = (int) mContext.getResources().getDimension(R.dimen.categories_list_width);
        int subtraction = categoryWidth + 84;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int windowWidth = displayMetrics.widthPixels;
        int width;
        if(mIsPortraitMode){
            width = windowWidth - 56;
        }else if(mIsLandscapeMode && drawerOpen){
            width = windowWidth - subtraction;
        }else{
            width = windowWidth - 98;
        }
        int columnWidth;
        if(mIsPortraitMode){
            columnWidth = width / 3 ;
        }else if(mIsLandscapeMode && drawerOpen){
            columnWidth = width / 5 ;
        }else{
            columnWidth = width / 6 ;
        }
        int height = columnWidth / 2;
        int columnHeight = height * 3;
        holder.thumbnail.setLayoutParams(new RelativeLayout.LayoutParams(columnWidth, columnHeight));

        boolean hideGridviewInfo = prefs.getBoolean("hide_gridview_info", false);
        int bottomPadding = (int) mContext.getResources().getDimension(R.dimen.gridview_root_bottom_padding);
        if(hideGridviewInfo){
            holder.ItemViewRoot.setPadding(0,0,0,0);
            holder.secondLine.setVisibility(View.GONE);
            holder.name.setVisibility(View.GONE);
        }else{
            holder.ItemViewRoot.setPadding(0,0,0,bottomPadding);
            holder.secondLine.setVisibility(View.VISIBLE);
            holder.name.setVisibility(View.VISIBLE);
        }

        holder.EmptyProgress.setVisibility(View.GONE);

        return view;
    }
}
