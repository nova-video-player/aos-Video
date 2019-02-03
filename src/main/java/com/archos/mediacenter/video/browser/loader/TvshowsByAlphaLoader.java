// Copyright 2019 Courville Software
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

import android.content.Context;

import com.archos.mediacenter.video.R;

public class TvshowsByAlphaLoader extends TvshowsByLoader {

    public static final String DEFAULT_SORT = COLUMN_SUBSET_NAME+" COLLATE NOCASE ASC";

    public TvshowsByAlphaLoader(Context context) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        setSelection(getSelection(context));
    }

    public TvshowsByAlphaLoader(Context context, String sortOrder) {
        super(context);
        mSortOrder = sortOrder;
        setSelection(getSelection(context));
    }

    public String getSelection(Context context) {
        return "SELECT\n" +
                "    UNICODE(SUBSTR(s_name,1,1)) AS "+COLUMN_SUBSET_ID+",\n" +
                "    CASE\n" +
                "        WHEN s_name NOT NULL THEN UPPER(SUBSTR(s_name,1,1))\n" +
                "        ELSE '"+context.getString(R.string.scrap_status_unknown)+"' \n" +
                "    END AS "+COLUMN_SUBSET_NAME+",\n" +
                "    group_concat( s_id ) AS "+COLUMN_LIST_OF_TVSHOWS_IDS+", -- show id list\n" +
                "    group_concat( s_po_large_file ) AS "+COLUMN_LIST_OF_POSTER_FILES+", -- show poster files list\n" +
                "    count( s_id ) AS "+COLUMN_NUMBER_OF_TVSHOWS+"   -- number of shows in list\n" +
                "FROM  ( \n" +
                "  SELECT s_id, s_po_large_file, s_name FROM video\n"+
                "  WHERE s_id IS NOT NULL"+ getCommonSelection()+"\n"+
                "  GROUP BY s_name COLLATE NOCASE\n" +
                ") \n" +
                "GROUP BY "+COLUMN_SUBSET_NAME+"\n" +
                " ORDER BY " + mSortOrder;
    }
}
