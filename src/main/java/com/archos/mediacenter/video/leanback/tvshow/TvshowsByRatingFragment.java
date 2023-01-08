// Copyright 2019 Courville Sofware
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

package com.archos.mediacenter.video.leanback.tvshow;

import android.content.Context;
import androidx.loader.content.Loader;

import android.database.Cursor;
import android.os.Bundle;
import android.util.SparseArray;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.TvshowsByRatingLoader;
import com.archos.mediacenter.video.browser.loader.TvshowsNoAnimeByRatingLoader;
import com.archos.mediaprovider.video.VideoStore;

public class TvshowsByRatingFragment extends TvshowsByFragment {

    private static final String SORT_PARAM_KEY = TvshowsByRatingFragment.class.getName() + "_SORT";

    private CharSequence[] mSortOrderEntries;

    private static SparseArray<TvshowsSortOrderEntry> sortOrderIndexer = new SparseArray<TvshowsSortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new TvshowsSortOrderEntry(R.string.sort_by_rating_asc,      "IFNULL(" + VideoStore.Video.VideoColumns.SCRAPER_S_RATING + ", 0) DESC"));
        sortOrderIndexer.put(1, new TvshowsSortOrderEntry(R.string.sort_by_name_asc,        VideoStore.Video.VideoColumns.SCRAPER_TITLE + " COLLATE LOCALIZED ASC"));
        sortOrderIndexer.put(2, new TvshowsSortOrderEntry(R.string.sort_by_date_added_desc, "max(" + VideoStore.Video.VideoColumns.DATE_ADDED + ") DESC"));
        sortOrderIndexer.put(3, new TvshowsSortOrderEntry(R.string.sort_by_date_played_desc, "max(" + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + ") DESC"));
        sortOrderIndexer.put(4, new TvshowsSortOrderEntry(R.string.sort_by_date_premiered_desc,       VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED + " DESC"));
        sortOrderIndexer.put(5, new TvshowsSortOrderEntry(R.string.sort_by_date_aired_desc, "max(" + VideoStore.Video.VideoColumns.SCRAPER_E_AIRED + ") DESC"));
    }

    public TvshowsByRatingFragment() {
        super("IFNULL(" + VideoStore.Video.VideoColumns.SCRAPER_S_RATING + ", 0) DESC");
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setTitle(getString(R.string.tvshows_by_rating));
        mSortOrderEntries = TvshowsSortOrderEntry.getSortOrderEntries(getActivity(), sortOrderIndexer);
    }

    @Override
    protected Loader<Cursor> getSubsetLoader(Context context) {
        if (mSeparateAnimeFromShowMovie) return new TvshowsNoAnimeByRatingLoader(context);
        else return new TvshowsByRatingLoader(context);
    }

    @Override
    protected CharSequence[] getSortOrderEntries() {
        return mSortOrderEntries;
    }

    @Override
    protected String item2SortOrder(int item) {
        return TvshowsSortOrderEntry.item2SortOrder(item, sortOrderIndexer);
    }

    @Override
    protected int sortOrder2Item(String sortOrder) {
        return TvshowsSortOrderEntry.sortOrder2Item(sortOrder, sortOrderIndexer);
    }

    @Override
    protected String getSortOrderParamKey() {
        return SORT_PARAM_KEY;
    }

}
