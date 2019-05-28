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

public class EpisodesByDateLoader extends MoviesByLoader {

    public static enum DateView {
        WEEK, MONTH, YEAR
    }

    private static final String DEFAULT_SORT = COLUMN_SUBSET_ID+" DESC";

    private DateView mDateView;

    public EpisodesByDateLoader(Context context, DateView dateView) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        mDateView = dateView;
        setSelection(getSelection(context));
    }

    public EpisodesByDateLoader(Context context, String sortOrder, DateView dateView) {
        super(context);
        mSortOrder = sortOrder;
        mDateView = dateView;
        setSelection(getSelection(context));
    }

    public String getSelection(Context context) {
        String formatter = "e_aired";
        switch (mDateView) {
            case WEEK:
                formatter = "strftime('%Y %m/%d', e_aired / 1000, 'unixepoch', 'localtime', '-6 days', 'weekday 1') || strftime('-%m/%d', e_aired / 1000, 'unixepoch', 'localtime', 'weekday 0')";
                break;
            case MONTH:
                formatter = "strftime('%Y/%m', e_aired / 1000, 'unixepoch', 'localtime')";
                break;
            case YEAR:
                formatter = "strftime('%Y', e_aired / 1000, 'unixepoch', 'localtime')";
                break;
        }
        return "SELECT\n" +
                "    e_aired as _id,\n" +
                "    " + formatter + " as name,\n" +
                "    group_concat( e_id ) AS list,\n" +
                "    group_concat( e_po_large_file ) AS po_file_list,\n" +
                "    count( e_id ) AS number\n" +
                "FROM  ( \n" +
                "  SELECT e_id, e_po_large_file, e_aired FROM video\n"+
                "  WHERE e_id IS NOT NULL AND e_aired > 0" + getCommonSelection()+"\n"+
                ") \n" +
                "GROUP BY name\n" +
                " ORDER BY "+mSortOrder;
    }
}
