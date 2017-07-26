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

package com.archos.mediacenter.video.leanback.details;

import android.support.v17.leanback.widget.HeaderItem;

/**
 * Created by vapillon on 16/07/15.
 */
public class PlotAndGenresRow extends FullWidthRow {

    final private String mPlot;
    final private String mGenres;

    public PlotAndGenresRow(String header, String plot, String genres) {
        super(new HeaderItem(header));
        mPlot = plot;
        mGenres = genres;
    }

    public String getPlot() {
        return mPlot;
    }
    public String getGenres() {
        return mGenres;
    }
}
