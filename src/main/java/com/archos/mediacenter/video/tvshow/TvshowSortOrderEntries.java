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

package com.archos.mediacenter.video.tvshow;

import android.content.Context;
import android.util.SparseArray;

import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;
import java.util.List;


public class TvshowSortOrderEntries {

    public final static String DEFAULT_SORT = VideoStore.Video.VideoColumns.SCRAPER_TITLE + " ASC";

    static private class sortOrderEntry {
        int mId;
        String mSortOrder;

        public sortOrderEntry(int id, String sortOrder) {
            mId = id;
            mSortOrder = sortOrder;
        }
    }

    static private SparseArray<sortOrderEntry> sortOrderIndexer = new SparseArray<sortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new sortOrderEntry(R.string.sort_by_name_asc,                    VideoStore.Video.VideoColumns.SCRAPER_TITLE + " ASC"));
        sortOrderIndexer.put(1, new sortOrderEntry(R.string.sort_by_date_premiered_desc,         VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED + " DESC"));
        sortOrderIndexer.put(2, new sortOrderEntry(R.string.sort_by_rating_asc,                  "IFNULL(" + VideoStore.Video.VideoColumns.SCRAPER_S_RATING + ", 0) DESC"));
        sortOrderIndexer.put(3, new sortOrderEntry(R.string.sort_by_recently_added_episode_desc, "max(" + VideoStore.Video.VideoColumns.DATE_ADDED + ") DESC"));
    }

    static public CharSequence[] getSortOrderEntries(Context context) {
        List<CharSequence> entries = new ArrayList<>();
        for (int index=0; index<sortOrderIndexer.size(); index++) {
            entries.add(context.getResources().getString(sortOrderIndexer.get(index).mId));
        }
        return entries.toArray(new CharSequence[entries.size()]);
    }

    static public CharSequence[] getSortOrderEntryValues(Context context) {
        List<CharSequence> entries = new ArrayList<>();
        for (int index=0; index<sortOrderIndexer.size(); index++) {
            entries.add(sortOrderIndexer.get(index).mSortOrder);
        }
        return entries.toArray(new CharSequence[entries.size()]);
    }

}
