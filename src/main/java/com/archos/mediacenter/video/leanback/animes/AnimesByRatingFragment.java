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

package com.archos.mediacenter.video.leanback.animes;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import androidx.loader.content.Loader;
import android.util.SparseArray;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.AnimesByRatingLoader;
import com.archos.mediacenter.video.leanback.VideosByFragment;
import com.archos.mediacenter.video.utils.SortOrder;
import com.archos.mediaprovider.video.VideoStore;


public class AnimesByRatingFragment extends VideosByFragment {

    private static final String SORT_PARAM_KEY = AnimesByRatingFragment.class.getName() + "_SORT";

    private CharSequence[] mSortOrderEntries;

    private static SparseArray<AnimesSortOrderEntry> sortOrderIndexer = new SparseArray<AnimesSortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new AnimesSortOrderEntry(R.string.sort_by_rating_asc,      SortOrder.SCRAPER_M_RATING.getDesc()));
        sortOrderIndexer.put(1, new AnimesSortOrderEntry(R.string.sort_by_name_asc,        "name COLLATE LOCALIZED ASC"));
        sortOrderIndexer.put(2, new AnimesSortOrderEntry(R.string.sort_by_date_added_desc, VideoStore.MediaColumns.DATE_ADDED + " DESC"));
        sortOrderIndexer.put(3, new AnimesSortOrderEntry(R.string.sort_by_year_desc,       VideoStore.Video.VideoColumns.SCRAPER_M_YEAR + " DESC"));
        sortOrderIndexer.put(4, new AnimesSortOrderEntry(R.string.sort_by_duration_asc,    SortOrder.DURATION.getAsc()));
    }

    public AnimesByRatingFragment() {
        super(SortOrder.SCRAPER_M_RATING.getDesc());
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(getString(R.string.movies_by_rating));

        mSortOrderEntries = AnimesSortOrderEntry.getSortOrderEntries(getActivity(), sortOrderIndexer);
    }

    @Override
    protected Loader<Cursor> getSubsetLoader(Context context) {
        return new AnimesByRatingLoader(context);
    }

    @Override
    protected CharSequence[] getSortOrderEntries() {
        return mSortOrderEntries;
    }

    @Override
    protected String item2SortOrder(int item) {
        return AnimesSortOrderEntry.item2SortOrder(item, sortOrderIndexer);
    }

    @Override
    protected int sortOrder2Item(String sortOrder) {
        return AnimesSortOrderEntry.sortOrder2Item(sortOrder, sortOrderIndexer);
    }

    @Override
    protected String getSortOrderParamKey() {
        return SORT_PARAM_KEY;
    }

}
