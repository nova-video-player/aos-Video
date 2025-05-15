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
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.SeasonsBrowserData;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesList;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesListSeason;
import com.archos.mediacenter.video.browser.adapters.SeasonsData;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediaprovider.video.VideoStore;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by alexandre on 27/10/15.
 */
public class SeasonListPresenter extends SeasonPresenter{
    private final NumberFormat mNumberFormat;
    private final DateFormat mDateFormat;

    public SeasonListPresenter(Context context, ExtendedClickListener listener) {
        super(context, AdapterDefaultValuesListSeason.INSTANCE, listener);
        mNumberFormat = NumberFormat.getInstance();
        mNumberFormat.setMinimumFractionDigits(1);
        mNumberFormat.setMaximumFractionDigits(1);
        mDateFormat = DateFormat.getDateInstance(DateFormat.LONG);
    }


    @Override
    public View getView(ViewGroup parent, Object object, View view) {
        view = super.getView(parent, object, view);
        return view;
    }

    @Override
    public View bindView(View view, Object object, ThumbnailEngine.Result result, int positionInAdapter) {
        super.bindView(view,object, result, positionInAdapter);
        ViewHolder holder = (ViewHolder) view.getTag();
        Season season = (Season) object;
        List<SeasonsBrowserData> finalSeasonTags = new ArrayList<>();
        // Ensure `season.getSeasonTags()` contains multiple JSON objects in a JSON array format
        try {
            JSONArray jsonArray = new JSONArray(season.getSeasonTags()); // Parse the full JSON array

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i); // Get each season object

                String airdate = jsonObject.optString("airdate", "");
                String overview = jsonObject.optString("overview", "");
                String seasonNumber = jsonObject.optString("seasonNumber", "");
                String name = jsonObject.optString("name", "");

                SeasonsBrowserData seasonsBrowserData = new SeasonsBrowserData();
                seasonsBrowserData.setSeasonNumber(seasonNumber);
                seasonsBrowserData.setSeasonName(name);
                seasonsBrowserData.setSeasonPlot(overview);
                seasonsBrowserData.setSeasonAirdate(airdate);
                finalSeasonTags.add(seasonsBrowserData); // Add each season to the list
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String seasonText = mContext.getResources().getString(R.string.episode_season);
        int currentSeason = season.getSeasonNumber();
        for (int i = 0; i < finalSeasonTags.size(); i++) {
            String seasonNumber = finalSeasonTags.get(i).getSeasonNumber();
            if (currentSeason == Integer.parseInt(seasonNumber)) {
                holder.seasonPlot.setText(finalSeasonTags.get(i).getSeasonPlot());
                holder.seasonPlot.setMaxLines(4);
                holder.seasonAirDate.setText(finalSeasonTags.get(i).getSeasonAirdate());
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
        // Get the RelativeLayout view for applying the background
        RelativeLayout relativeLayout = view.findViewById(R.id.relative_layout_season);


        //setting a corner radius programmatically
        float density = mContext.getResources().getDisplayMetrics().density;
        float cornerRadius = 8 * density; // equivalent to 6dp
        // Create a MaterialShapeDrawable for rounded corners and transparent background
        MaterialShapeDrawable materialShapeDrawable = new MaterialShapeDrawable();
        materialShapeDrawable.setShapeAppearanceModel(
                new ShapeAppearanceModel()
                        .toBuilder()
                        .setAllCorners(CornerFamily.ROUNDED, cornerRadius) // Set the corner radius to 6dp
                        .build()
        );

        // Set the transparent background
        materialShapeDrawable.setFillColor(ColorStateList.valueOf(Color.argb(80, 0, 0, 0))); // semi-transparent black


        // Optionally, set a stroke (border)
        //materialShapeDrawable.setStroke(2f, Color.BLACK); // Black border with 2px width

        // Apply the MaterialShapeDrawable to the RelativeLayout
        relativeLayout.setBackground(materialShapeDrawable);

        // Clip the content to the outline
        relativeLayout.setClipToOutline(true);
        return view;
    }

}
