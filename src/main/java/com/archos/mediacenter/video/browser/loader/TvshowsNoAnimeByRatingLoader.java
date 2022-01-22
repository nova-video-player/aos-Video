// Copyright 2021 Courville Software
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

public class TvshowsNoAnimeByRatingLoader extends TvshowsNoAnimeByLoader {

    public static final String DEFAULT_SORT = COLUMN_SUBSET_NAME+" COLLATE LOCALIZED DESC";

    public TvshowsNoAnimeByRatingLoader(Context context) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        setSelection(getSelection(context));
    }

    public TvshowsNoAnimeByRatingLoader(Context context, String sortOrder) {
        super(context);
        mSortOrder = sortOrder;
        setSelection(getSelection(context));
    }

    public String getSelection(Context context) {
        return "SELECT\n" +
                "    CAST(s_rating as int) AS _id,\n" +
                "    CAST(s_rating as int) AS name,\n" +
                "    group_concat( s_id ) AS list,\n" +
                "    group_concat( s_po_large_file ) AS po_file_list,\n" +
                "    count( s_id ) AS number\n" +
                "FROM  ( \n" +
                "  SELECT s_id, s_po_large_file, s_rating FROM video\n"+
                "  WHERE s_id IS NOT NULL AND s_rating > 0"+ getCommonSelection()+"\n"+
                ") \n" +
                "GROUP BY name\n" +
                " ORDER BY " + mSortOrder;
    }
}
