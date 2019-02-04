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

import android.content.Context;

/**
 * Created by vapillon on 10/04/15.
 */
public class MoviesByGenreLoader extends MoviesByLoader {

    private static final String DEFAULT_SORT = COLUMN_SUBSET_NAME+" COLLATE NOCASE";

    public MoviesByGenreLoader(Context context) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        setSelection(getSelection(context));
    }
    public MoviesByGenreLoader(Context context, String sortOrder) {
        super(context);
        mSortOrder = sortOrder;
        setSelection(getSelection(context));
    }
    @Override
    protected String getSelection(Context context) {
        return "SELECT\n" +
                "       _id,\n" +
                "       name_genre AS name,\n" +
                "       group_concat( m_id ) AS list,\n" +
                "       group_concat( po_large_file ) AS po_file_list,\n" +
                "       count( m_id ) AS number\n" +
                "  FROM  (\n" +
                "    SELECT\n" +
                "           genre._id,\n" +
                "           genre.name_genre,\n" +
                "           po_large_file,\n"+
                "           m_id\n" +
                "      FROM video\n" +
                "           JOIN belongs_movie\n" +
                "             ON ( m_id = movie_belongs )\n" +
                "           JOIN genre\n" +
                "             ON ( genre._id = genre_belongs )\n" +
                "     WHERE m_id IS NOT NULL" + getCommonSelection()+"\n"+
                ")\n" +
                " GROUP BY _id\n" +
                " ORDER BY "+mSortOrder;
    }
}
