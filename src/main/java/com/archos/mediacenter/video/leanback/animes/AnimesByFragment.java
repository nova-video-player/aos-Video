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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.database.CursorMapper;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SearchOrbView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.loader.AnimesByLoader;
import com.archos.mediacenter.video.browser.loader.AnimesLoader;
import com.archos.mediacenter.video.browser.loader.AnimesSelectionLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.player.PrivateMode;

import java.util.ArrayList;

public abstract class AnimesByFragment extends BrowseSupportFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "AnimesByFragment";

    private ArrayObjectAdapter mRowsAdapter;
    private Overlay mOverlay;
    private SharedPreferences mPrefs;
    private TextView mEmptyView;

    private int mSortOrderItem;
    private String mSortOrder;

    /**
     * We can have a single instance of presenter and mapper used for all the subset rows created
     */
    private Presenter mVideoPresenter;
    private CursorMapper mVideoMapper;

    /**
     * keep a reference of the cursor containing the categories to check if there is actually an update when we get a new one
     */
    private Cursor mCurrentCategoriesCursor;

    /**
     * Map to update the adapter when we get the onLoadFinished() callback
     */
    SparseArray<CursorObjectAdapter> mAdaptersMap = new SparseArray<>();

    abstract protected Loader<Cursor> getSubsetLoader(Context context);

    abstract protected CharSequence[] getSortOrderEntries();
    abstract protected String item2SortOrder(int item);
    abstract protected int sortOrder2Item(String sortOrder);
    abstract protected String getSortOrderParamKey();

    private BackgroundManager bgMngr = null;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);

        SearchOrbView searchOrbView = (SearchOrbView) getView().findViewById(R.id.title_orb);
        if (searchOrbView != null) {
            searchOrbView.setOrbIcon(ContextCompat.getDrawable(getActivity(), R.drawable.orb_sort));
        } else {
            throw new IllegalArgumentException("Did not find R.id.title_orb in BrowseFragment! Need to update the orbview hack!");
        }

        ViewGroup container = (ViewGroup) getView().findViewById(R.id.browse_frame);
        if (container != null) {
            LayoutInflater.from(getActivity()).inflate(R.layout.leanback_empty_view, container, true);
            mEmptyView = (TextView) container.findViewById(R.id.empty_view);
            mEmptyView.setText(R.string.you_have_no_movies);
        } else {
            throw new IllegalArgumentException("Did not find R.id.browse_frame in BrowseFragment! Need to update the emptyview hack!");
        }
    }

    @Override
    public void onDestroyView() {
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        // Save the sort mode
        mPrefs.edit().putString(getSortOrderParamKey(), mSortOrder).commit();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();
        updateBackground();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mSortOrder = mPrefs.getString(getSortOrderParamKey(), AnimesLoader.DEFAULT_SORT);

        Resources r = getResources();

        updateBackground();

        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getActivity(), R.color.leanback_side));

        // set search icon color
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.lightblueA200));

        setupEventListeners();

        RowPresenter rowPresenter = new ListRowPresenter();
        rowPresenter.setHeaderPresenter(new RowHeaderPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowPresenter);
        setAdapter(mRowsAdapter);

        mVideoPresenter = new PosterImageCardPresenter(getActivity());
        mVideoMapper = new CompatibleCursorMapperConverter(new VideoCursorMapper());

        LoaderManager.getInstance(this).initLoader(-1, null, this);
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {
            public void onClick(View view) {
                mSortOrderItem = sortOrder2Item(mSortOrder);
                new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(getSortOrderEntries(), mSortOrderItem, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mSortOrderItem != which) {
                                    mSortOrderItem = which;
                                    mSortOrder = item2SortOrder(mSortOrderItem);
                                    loadCategoriesRows(mCurrentCategoriesCursor);
                                }
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
        setOnItemViewClickedListener(new VideoViewClickedListener(getActivity()));
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == -1) {
            // List of categories
            return getSubsetLoader(getActivity());
        } else {
            // One of the row
            return new AnimesSelectionLoader(getActivity(), args.getString("ids"), args.getString("sort"));
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor c) {
        if (getActivity() == null) return;
        // List of categories
        if (cursorLoader.getId() == -1) {

            // Empty view visibility
            mEmptyView.setVisibility(c.getCount() > 0 ? View.GONE : View.VISIBLE);

            if (mCurrentCategoriesCursor != null) {
                if (!isCategoriesListModified(mCurrentCategoriesCursor, c)) {
                    // no actual modification, no need to rebuild all the rows
                    mCurrentCategoriesCursor = c; // keep the reference to the new cursor because the old one won't be valid anymore
                    return;
                }
            }
            mCurrentCategoriesCursor = c;
            loadCategoriesRows(c);
        }
        // One of the row
        else {
            CursorObjectAdapter adapter = mAdaptersMap.get(cursorLoader.getId());
            if (adapter != null) {
                adapter.changeCursor(c);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }


    private boolean isCategoriesListModified(Cursor oldCursor, Cursor newCursor) {

        // Modified for sure if has different length
        if (oldCursor.getCount() != newCursor.getCount()) {
            Log.d(TAG, "Difference found in the category list (size changed)");
            return true;
        }

        // these two column index are the same but it looks nicer like this :-)
        final int oldSubsetNameColumn = oldCursor.getColumnIndex(AnimesByLoader.COLUMN_SUBSET_NAME);
        final int newSubsetNameColumn = newCursor.getColumnIndex(AnimesByLoader.COLUMN_SUBSET_NAME);

        // Check all names
        oldCursor.moveToFirst();
        newCursor.moveToFirst();
        while (!oldCursor.isAfterLast() && !newCursor.isAfterLast()) {
            final String oldName = oldCursor.getString(oldSubsetNameColumn);
            final String newName = newCursor.getString(newSubsetNameColumn);
            if (oldName != null && !oldName.equals(newName)) {
                // difference found
                Log.d(TAG, "Difference found in the category list (" + oldName + " vs " + newName + ")");
                return true;
            }
            oldCursor.moveToNext();
            newCursor.moveToNext();
        }
        // no difference found
        Log.d(TAG, "No difference found in the category list");
        return false;
    }

    private void loadCategoriesRows(Cursor c) {
        if (c == null) return;
        int subsetIdColumn = c.getColumnIndex(AnimesByLoader.COLUMN_SUBSET_ID);
        int subsetNameColumn = c.getColumnIndex(AnimesByLoader.COLUMN_SUBSET_NAME);
        int listOfAnimeIdsColumn = c.getColumnIndex(AnimesByLoader.COLUMN_LIST_OF_MOVIE_IDS);

        mRowsAdapter.clear();
        mAdaptersMap.clear();

        // NOTE: A first version was using a CursorObjectAdapter for the rows.
        // The problem was that when any DB update occurred (resume point...) I found no way
        // to not update all the rows. Hence the selection position on the current row was lost.
        // I tried to not update but the older cursor was closed by the LoaderManager (I think), leading to crashes.
        // Solution implemented here is to "convert" the cursor into an array. No performance issue since the
        // number of categories is always quite limited (~100 max)

        // Build the array of categories from the cursor
        ArrayList<ListRow> rows = new ArrayList<>(c.getCount());
        c.moveToFirst();
        while(!c.isAfterLast())
        {
            int subsetId = (int) c.getLong(subsetIdColumn);
            String subsetName = c.getString(subsetNameColumn);
            String listOfAnimeIds = c.getString(listOfAnimeIdsColumn);

            // Build the row
            CursorObjectAdapter subsetAdapter = new CursorObjectAdapter(mVideoPresenter);
            subsetAdapter.setMapper(mVideoMapper);
            rows.add(new ListRow(new HeaderItem(subsetName), subsetAdapter));
            mAdaptersMap.append(subsetId, subsetAdapter);

            // Start the loader manager for this row
            Bundle args = new Bundle();
            args.putString("ids", listOfAnimeIds);
            args.putString("sort", mSortOrder);
            try {
                LoaderManager.getInstance(this).restartLoader(subsetId, args, this);
            } catch (Exception e) {
                Log.w(TAG, "caught exception in loadCategoriesRows ",e);
            }

            c.moveToNext();
        }

        mRowsAdapter.addAll(0,rows);
    }

    private void updateBackground() {
        Resources r = getResources();

        bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached())
            bgMngr.attach(getActivity().getWindow());

        if (PrivateMode.isActive()) {
            bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.private_mode));
            bgMngr.setDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.private_background));
        } else {
            bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.leanback_background));
            bgMngr.setDrawable(new ColorDrawable(ContextCompat.getColor(getActivity(), R.color.leanback_background)));
        }
    }

}
