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
import android.view.View;
import android.view.ViewGroup;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.browser.SeasonsBrowserData;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesList;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesListSeason;
import com.archos.mediacenter.video.browser.adapters.SeasonsData;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediaprovider.video.VideoStore;

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
        List<String> seasonTags = Arrays.asList(season.getSeasonTags().split("\\s*&&&&####,\\s*"));


        List <SeasonsBrowserData>  finalSeasonPlots = new ArrayList<>();
        for (int i = 0; i < seasonTags.size(); i++) {
            String seasonPlot = seasonTags.get(i);
            List <String>  seasonPlotsFormatted;
            seasonPlotsFormatted = Arrays.asList(seasonPlot.split("\\s*=&%#\\s*"));
            SeasonsBrowserData seasonsBrowserData = new SeasonsBrowserData();
            seasonsBrowserData.setSeasonNumber(seasonPlotsFormatted.get(0));
            seasonsBrowserData.setSeasonPlot(seasonPlotsFormatted.get(1));
            seasonsBrowserData.setSeasonAirdate(seasonPlotsFormatted.get(3).replaceAll("&&&&####", ""));
            finalSeasonPlots.add(seasonsBrowserData);
        }

        int currentSeason = season.getSeasonNumber();
        for (int i = 0; i < finalSeasonPlots.size(); i++) {
            String seasonNumber = finalSeasonPlots.get(i).getSeasonNumber();
            if (currentSeason == Integer.parseInt(seasonNumber)) {
                holder.seasonPlot.setText(finalSeasonPlots.get(i).getSeasonPlot());
                holder.seasonPlot.setMaxLines(5);
            }
        }
        return view;
    }

}
