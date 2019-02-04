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
                "    UNICODE(SUBSTR(s_name,1,1)) AS _id,\n" +
                "    UPPER(SUBSTR(s_name,1,1)) AS name,\n" +
                "    group_concat( s_id ) AS list,\n" +
                "    group_concat( s_po_large_file ) AS po_file_list,\n" +
                "    count( s_id ) AS number\n" +
                "FROM  ( \n" +
                "  SELECT s_id, s_po_large_file, s_name FROM video\n"+
                "  WHERE s_id IS NOT NULL AND s_name IS NOT NULL"+ getCommonSelection()+"\n"+
                "  GROUP BY s_name COLLATE NOCASE\n" +
                ") \n" +
                "GROUP BY name\n" +
                " ORDER BY " + mSortOrder;
    }
}
