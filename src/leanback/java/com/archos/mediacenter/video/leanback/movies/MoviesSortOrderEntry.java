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

package com.archos.mediacenter.video.leanback.movies;

import android.content.Context;
import android.util.SparseArray;

import com.archos.mediacenter.video.browser.loader.MoviesLoader;

import java.util.ArrayList;
import java.util.List;


public class MoviesSortOrderEntry {

    int mId;
    String mSortOrder;

    public MoviesSortOrderEntry(int id, String sortOrder) {
        mId = id;
        mSortOrder = sortOrder;
    }

    static public CharSequence[] getSortOrderEntries(Context context, SparseArray<MoviesSortOrderEntry> indexer) {
        List<CharSequence> entries = new ArrayList<>();
        for (int index=0; index<indexer.size(); index++) {
            entries.add(context.getResources().getString(indexer.get(index).mId));
        }
        return entries.toArray(new CharSequence[entries.size()]);
    }
    
    static public CharSequence[] getSortOrderEntryValues(Context context, SparseArray<MoviesSortOrderEntry> indexer) {
        List<CharSequence> entries = new ArrayList<>();
        for (int index=0; index<indexer.size(); index++) {
            entries.add(indexer.get(index).mSortOrder);
        }
        return entries.toArray(new CharSequence[entries.size()]);
    }

    static protected String item2SortOrder(int item, SparseArray<MoviesSortOrderEntry> indexer) {
        if (item >= 0 && item < indexer.size()) {
            return indexer.get(item).mSortOrder;
        } else {
            return MoviesLoader.DEFAULT_SORT;
        }
    }

    static protected int sortOrder2Item(String sortOrder, SparseArray<MoviesSortOrderEntry> indexer) {
        int item = -1;
        for (int i=0 ; i<indexer.size(); i++) {
            if (indexer.get(i).mSortOrder.equals(sortOrder)) {
                item = indexer.keyAt(i);
                break;
            }
        }
        return Math.max(item, 0);
    }

}
