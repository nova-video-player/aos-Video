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

public class TvshowsByGenreLoader extends TvshowsByLoader {

    public static final String DEFAULT_SORT = COLUMN_SUBSET_NAME+" COLLATE NOCASE ASC";

    public TvshowsByGenreLoader(Context context) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        setSelection(getSelection(context));
    }

    public TvshowsByGenreLoader(Context context, String sortOrder) {
        super(context);
        mSortOrder = sortOrder;
        setSelection(getSelection(context));
    }

    protected String getSelection(Context context) {
        return "SELECT\n" +
                "       _id,\n" +
                "       name_genre AS name,\n" +
                "       group_concat( s_id ) AS list,\n" +
                "       group_concat( s_po_large_file ) AS po_file_list,\n" +
                "       count( s_id ) AS number\n" +
                "  FROM  (\n" +
                "    SELECT\n" +
                "           genre._id,\n" +
                "           genre.name_genre,\n" +
                "           s_po_large_file,\n"+
                "           s_id\n" +
                "      FROM video\n" +
                "           JOIN belongs_show\n" +
                "             ON ( s_id = show_belongs )\n" +
                "           JOIN genre\n" +
                "             ON ( genre._id = genre_belongs )\n" +
                "     WHERE s_id IS NOT NULL" + getCommonSelection()+"\n"+
                ")\n" +
                " GROUP BY _id\n" +
                " ORDER BY "+mSortOrder;
    }
}
