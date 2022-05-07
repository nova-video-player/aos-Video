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
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.RelativeLayout;

import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.SeasonsBrowserData;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesGrid;
import com.archos.mediacenter.video.browser.adapters.object.Season;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by alexandre on 27/10/15.
 */
public class SeasonGridPresenter extends SeasonPresenter{
    public SeasonGridPresenter(Context context, ExtendedClickListener listener) {
        this(context, AdapterDefaultValuesGrid.INSTANCE, listener);
    }

    protected SeasonGridPresenter(Context context,AdapterDefaultValues defaultValues, ExtendedClickListener listener) {
        super(context, defaultValues, listener);
    }

    @Override
    public View bindView(View view, Object object, ThumbnailEngine.Result result, int positionInAdapter) {
        super.bindView(view,object, result, positionInAdapter);
        Season season = (Season) object;
        ViewHolder holder = (ViewHolder) view.getTag();
        List<String> seasonTags = Arrays.asList(season.getSeasonTags().split("\\s*&&&&####,\\s*"));
        List <SeasonsBrowserData>  finalSeasonTags = new ArrayList<>();
        for (int i = 0; i < seasonTags.size(); i++) {
            String seasonPlot = seasonTags.get(i);
            List <String>  seasonPlotsFormatted;
            seasonPlotsFormatted = Arrays.asList(seasonPlot.split("\\s*=&%#\\s*"));
            SeasonsBrowserData seasonsBrowserData = new SeasonsBrowserData();
            seasonsBrowserData.setSeasonNumber(seasonPlotsFormatted.get(0));
            seasonsBrowserData.setSeasonName(seasonPlotsFormatted.get(2));
            finalSeasonTags.add(seasonsBrowserData);
        }

        String seasonText = mContext.getResources().getString(R.string.episode_season);
        int currentSeason = season.getSeasonNumber();
        for (int i = 0; i < finalSeasonTags.size(); i++) {
            String seasonNumber = finalSeasonTags.get(i).getSeasonNumber();
            if (currentSeason == Integer.parseInt(seasonNumber)) {
                String name = "";
                if (finalSeasonTags.get(i).getSeasonName().isEmpty()){
                    name = seasonText+" "+season.getSeasonNumber();
                }else{
                    name = finalSeasonTags.get(i).getSeasonName();
                }
                if(holder.name!=null) {
                    holder.name.setText(name);
                    holder.name.setEllipsize(TextUtils.TruncateAt.END);
                }
            }
        }
        if(holder.secondLine!=null)
            holder.secondLine.setVisibility(View.VISIBLE);

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

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int windowWidth = displayMetrics.widthPixels;
        int TotalHorizontalSpacingPortrait = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_portrait);
        int TotalHorizontalSpacingLandscape = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_landscape);

        int TotalHorizontalSpacingTabletPortrait = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_tablet_portrait);
        int TotalHorizontalSpacingTabletLandscape = (int) mContext.getResources().getDimension(R.dimen.total_horizontal_spacing_tablet_landscape);
        int subtractionTablet = categoryWidth + TotalHorizontalSpacingTabletLandscape;

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
                width = windowWidth - TotalHorizontalSpacingTabletPortrait;
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
                columnWidth = width / 5;
            }
        }
        int height = columnWidth / 2;
        int columnHeight = height * 3;
        holder.thumbnail.setLayoutParams(new RelativeLayout.LayoutParams(columnWidth, columnHeight));

        int bottomPadding = (int) mContext.getResources().getDimension(R.dimen.gridview_root_bottom_padding);
        holder.ItemViewRoot.setPadding(0,0,0,bottomPadding);

        holder.EmptyProgress.setVisibility(View.GONE);

        return view;
    }
}
