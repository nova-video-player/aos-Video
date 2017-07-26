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

package com.archos.mediacenter.video.leanback.tvshow;

import android.app.LoaderManager;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.View;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.SeasonCursorMapper;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.SeasonPresenter;

/**
 * Created by vapillon on 16/06/15.
 */
public class MarkAsWatchedFragment extends BrowseFragment implements  LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "MarkAsWatchedFragment";

    public static final String EXTRA_TVSHOW_ID = "TVSHOW_ID";
    public static final String EXTRA_TVSHOW_NAME = "TVSHOW_NAME";

    /** The show we're displaying */
    private long mTvshowId;
    private String mTvshowName;

    private ArrayObjectAdapter mRowsAdapter;
    private CursorObjectAdapter mSeasonsAdapter;
    private ListRow mSeasonsListRow;

    private Overlay mOverlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTvshowId = getActivity().getIntent().getLongExtra(EXTRA_TVSHOW_ID, -1);
        mTvshowName = getActivity().getIntent().getStringExtra(EXTRA_TVSHOW_NAME);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Just need to attach the background manager to keep the background of the parent activity
        BackgroundManager bm = BackgroundManager.getInstance(getActivity());
        bm.attach(getActivity().getWindow());
        // Adding a very dark dim to increase the difference with TvShowFragment + to improve the visibility of the row header (it contains a short how-to)
        bm.setDimLayer(getActivity().getResources().getDrawable(R.color.leanback_very_dark_dim_for_background_manager));

        setTitle(mTvshowName);
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(false);

        loadRows();

        setOnItemViewClickedListener(mOnItemViewClickedListener);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();

        // Start loading the list of seasons
        getLoaderManager().restartLoader(1, null, this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    private void loadRows() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        mSeasonsAdapter = new CursorObjectAdapter(new SeasonPresenter(getActivity()));
        mSeasonsAdapter.setMapper(new CompatibleCursorMapperConverter(new SeasonCursorMapper()));
        mSeasonsListRow = new ListRow(
                new HeaderItem(getString(R.string.how_to_mark_season_watched)),
                mSeasonsAdapter);
        mRowsAdapter.add(mSeasonsListRow);

        setAdapter(mRowsAdapter);
    }

    //--------------------------------------------

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        return new SeasonsLoader(getActivity(), mTvshowId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mSeasonsAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }

    //--------------------------------------------

    private OnItemViewClickedListener mOnItemViewClickedListener = new OnItemViewClickedListener() {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            Season season = (Season)item;
            if (season.allEpisodesWatched()) {
                DbUtils.markAsNotRead(getActivity(), season);
            }
            else {
                DbUtils.markAsRead(getActivity(), season);
            }
        }
    };
}
