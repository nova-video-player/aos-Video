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
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.preference.PreferenceManager;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesGrid;

/**
 * Created by alexandre on 26/10/15.
 */
public class Metafile2GridPresenter extends Metafile2Presenter{
    public Metafile2GridPresenter(Context context) {
        super(context, AdapterDefaultValuesGrid.INSTANCE);
    }

    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        super.bindView(view, object, thumbnailResult, positionInAdapter);
        ViewHolder holder = (ViewHolder) view.getTag();

        //set gridview thumbnail Width & Height
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean drawerIsNull = prefs.getBoolean("drawerIsNull", true);
        boolean mIsLandscapeMode = mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean mIsPortraitMode = mContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        boolean IsTablet = mContext.getResources().getConfiguration().isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE);

        //width subtraction when number of columns is 5 && mIsLandscapeMode && drawerIsNull
        int categoryWidth = (int) mContext.getResources().getDimension(R.dimen.categories_list_width);
        int TotalHorizontalSpacingLandscapeNullDrawer = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_landscape_null_drawer);
        int subtraction = categoryWidth + TotalHorizontalSpacingLandscapeNullDrawer;

        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int windowWidth = displayMetrics.widthPixels;
        int TotalHorizontalSpacingPortrait = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_portrait);
        int TotalHorizontalSpacingLandscape = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_landscape);

        int TotalHorizontalSpacingTabletPortrait = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_tablet_portrait);
        int TotalHorizontalSpacingTabletLandscape = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_tablet_landscape);
        int subtractionTablet = categoryWidth + TotalHorizontalSpacingTabletLandscape;
        int subtractionTabletPortrait = categoryWidth + TotalHorizontalSpacingTabletPortrait;

        int width;
        if(!IsTablet){
            if(mIsPortraitMode){
                width = windowWidth - TotalHorizontalSpacingPortrait;
            }else if(mIsLandscapeMode && drawerIsNull){
                width = windowWidth - subtraction;
            }else{
                width = windowWidth - TotalHorizontalSpacingLandscape;
            }
        }else{
            if(mIsLandscapeMode){
                width = windowWidth - subtractionTablet;
            }else{
                if(drawerIsNull){
                    width = windowWidth - subtractionTabletPortrait;
                }else{
                    width = windowWidth - TotalHorizontalSpacingTabletPortrait;
                }
            }
        }

        int columnWidth;
        if(!IsTablet){
            if(mIsPortraitMode){
                columnWidth = width / 3 ;
            }else if(mIsLandscapeMode && drawerIsNull){
                columnWidth = width / 5 ;
            }else{
                columnWidth = width / 6 ;
            }
        }else{
            if(mIsLandscapeMode){
                columnWidth = width / 8;
            }else{
                if(drawerIsNull){
                    columnWidth = width / 4;
                }else{
                    columnWidth = width / 5;
                }
            }
        }

        int height = columnWidth / 2;
        int columnHeight = height * 3;
        holder.thumbnail.setLayoutParams(new RelativeLayout.LayoutParams(columnWidth, columnHeight));

        return view;
    }
}
