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

package com.archos.mediacenter.video.browser.loader;

import android.app.SearchManager;
import android.content.Context;
import android.content.res.Configuration;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediaprovider.video.VideoStore;

public class SearchViewVideoLoader extends SearchVideoLoader {

    // primary text, = scraper title with fallback to plain filename based title
    private static final String LINE1 = "coalesce(" + VideoStore.Video.VideoColumns.SCRAPER_TITLE + "," + VideoStore.MediaColumns.TITLE
            + ") AS " + SearchManager.SUGGEST_COLUMN_TEXT_1;

    // secondary text for shows only "S1E4 'Episode Title'"
    private static final String QUOTED_CONTENT = "'||" + VideoStore.Video.VideoColumns.SCRAPER_E_NAME + "||'";
    private static final String LINE2_START = "'S'||" + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + "||'E'||"
            + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + "||' ";
    private static final String LINE2_END = "' AS " + SearchManager.SUGGEST_COLUMN_TEXT_2;

    // icon = scraper cover or nothing
    private static final String ICON = "'file://'||" + VideoStore.Video.VideoColumns.SCRAPER_COVER + " AS "
            + SearchManager.SUGGEST_COLUMN_ICON_1;

    // content id = database id
    private static final String CONTENT_ID = BaseColumns._ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID;




    public SearchViewVideoLoader(Context context) {
        super(context);
    }




    @Override
    public String[] getProjection() {
        // build the following
        // 'S'||e_season||'E'||e_episode||' "'||e_name||'"'
        // quotation_format is "%s"
        // -> use '||e_name||' as format arg to get that inside the quotation marks
        String quoted = getContext().getString(R.string.quotation_format, QUOTED_CONTENT);
        // quoted = "'||e_name||'"
        // build ['S'||e_season||'E'||e_episode||' ] + ["'||e_name||'"] + [']
        String line2 = LINE2_START + quoted + LINE2_END;
        return concatTwoStringArrays(super.getProjection(),new String []{
                LINE1,
                line2,
                ICON,
                CONTENT_ID
        });
    }

}
