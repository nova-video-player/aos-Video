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

import com.archos.mediacenter.video.R;

/**
 * Created by vapillon on 10/04/15.
 */
public class MoviesByYearLoader extends MoviesByLoader {

    private static final String DEFAULT_SORT = COLUMN_SUBSET_NAME+" COLLATE NOCASE DESC";

    public MoviesByYearLoader(Context context) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        setSelection(getSelection(context));
    }

    public MoviesByYearLoader(Context context, String sortOrder) {
        super(context);
        mSortOrder = sortOrder;
        setSelection(getSelection(context));
    }

    public String getSelection(Context context) {
        return "SELECT\n" +
                "    m_year as _id,\n" +
                "    m_year as name,\n" +
                "    group_concat( m_id ) AS list,\n" +
                "    group_concat( m_po_large_file ) AS po_file_list,\n" +
                "    count( m_id ) AS number\n" +
                "FROM  ( \n" +
                "  SELECT m_id, m_po_large_file, m_year FROM video\n"+
                "  WHERE m_id IS NOT NULL AND m_year > 0" + getCommonSelection()+"\n"+
                ") \n" +
                "GROUP BY name\n" +
                " ORDER BY "+mSortOrder;
    }
}
