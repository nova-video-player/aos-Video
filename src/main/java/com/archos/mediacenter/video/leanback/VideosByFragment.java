// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.leanback;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
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
import com.archos.mediacenter.video.browser.loader.MoviesByLoader;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.NetworkScannerReceiver;
import com.archos.mediacenter.video.utils.ThemeManager;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.MoviesSelectionLoader;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.PrivateModeUIHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public abstract class VideosByFragment extends BrowseSupportFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final Logger log = LoggerFactory.getLogger(VideosByFragment.class);

    // attempts to not have refresh of categories/list while scanning but both causes crashes
    private static boolean STOP_LOADING = false;
    // causes crashes AndroidRuntime: java.lang.RuntimeException: Unable to destroy activity {org.courville.nova/com.archos.mediacenter.video.leanback.tvshow.EpisodesByDateActivity}: java.lang.IllegalStateException: Observer androidx.leanback.app.ListRowDataAdapter$SimpleDataObserver@e52e40c was not registered.
    private static boolean UNREGISTER_OBSERVERS = false;

    private ArrayObjectAdapter mRowsAdapter;
    private Overlay mOverlay;
    private SharedPreferences mPrefs;
    protected TextView mEmptyView;

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
    private boolean mRowsLoadDeferred;
    private boolean mBackgroundWorkWasOngoing;

    private String mDefaultSort;

    /**
     * Map to update the adapter when we get the onLoadFinished() callback
     */
    SparseArray<CursorObjectAdapter> mAdaptersMap = new SparseArray<>();

    BackgroundManager bgMngr = null;
    private SharedPreferences.OnSharedPreferenceChangeListener mThemeChangeListener;

    abstract protected Loader<Cursor> getSubsetLoader(Context context);

    abstract protected CharSequence[] getSortOrderEntries();
    abstract protected String item2SortOrder(int item);
    abstract protected int sortOrder2Item(String sortOrder);
    abstract protected String getSortOrderParamKey();

    protected boolean shouldDeferRowLoadersDuringBackgroundWork() {
        return false;
    }

    public VideosByFragment() {
        this(MoviesLoader.DEFAULT_SORT);
    }

    public VideosByFragment(String defaultSort) {
        mDefaultSort = defaultSort;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (log.isDebugEnabled()) log.debug("onViewCreated");
        super.onViewCreated(view, savedInstanceState);

        // Add private mode indicator overlay
        PrivateModeUIHelper.addPrivateModeIndicator(getActivity(), view);

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

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mSortOrder = mPrefs.getString(getSortOrderParamKey(), mDefaultSort);

        Resources r = getResources();
        updateBackground();

        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // Apply theme-aware colors
        ThemeManager themeManager = ThemeManager.getInstance(getActivity());
        // set fastLane (or headers) background color based on theme
        setBrandColor(themeManager.getLeanbackHeaderColor());

        // set search icon color
        setSearchAffordanceColor(ThemeManager.getInstance(getActivity()).getSearchAffordanceColor());

        setupEventListeners();

        RowPresenter rowPresenter = new ListRowPresenter();
        rowPresenter.setHeaderPresenter(new RowHeaderPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowPresenter);
        setAdapter(mRowsAdapter);

        mVideoPresenter = new PosterImageCardPresenter(getActivity());
        mVideoMapper = new CompatibleCursorMapperConverter(new VideoCursorMapper());

        LoaderManager.getInstance(this).initLoader(-1, null, this);

        // Setup theme change listener
        setupThemeListener();
    }

    @Override
    public void onDestroyView() {
        if (log.isDebugEnabled()) log.debug("onDestroyView");
        clearRowAdapters();
        mCurrentCategoriesCursor = null;
        mOverlay.destroy();
        // Unregister theme change listener
        if (mThemeChangeListener != null) {
            ThemeManager.getInstance(getActivity()).unregisterThemeChangeListener(mThemeChangeListener);
        }
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        if (log.isDebugEnabled()) log.debug("onResume");
        super.onResume();
        mBackgroundWorkWasOngoing = isBackgroundWorkOngoing();
        mOverlay.resume();
    }

    @Override
    public void onPause() {
        if (log.isDebugEnabled()) log.debug("onPause");
        super.onPause();
        mOverlay.pause();
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
                                    // Save the sort mode
                                    mPrefs.edit().putString(getSortOrderParamKey(), mSortOrder).apply();
                                    boolean deferRowLoaders = shouldDeferRowLoadersDuringBackgroundWork() && isBackgroundWorkOngoing();
                                    loadCategoriesRows(mCurrentCategoriesCursor, !deferRowLoaders);
                                    mRowsLoadDeferred = deferRowLoaders;
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
        if (log.isDebugEnabled()) log.debug("onCreateLoader id=", id);
        if (id == -1) {
            // List of categories with video ids per category
            return getSubsetLoader(getActivity());
        } else {
            // One of the row
            return new MoviesSelectionLoader(getActivity(), args.getString("ids"), args.getString("sort"));
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor c) {
        if (log.isDebugEnabled()) log.debug("onLoadFinished id=", cursorLoader.getId());
        if (getActivity() == null) {
            if (log.isDebugEnabled()) log.debug("onLoadFinished: activity null exiting");
            return;
        }
        boolean backgroundWorkOngoing = isBackgroundWorkOngoing();
        if (mRowsLoadDeferred && mBackgroundWorkWasOngoing && !backgroundWorkOngoing) {
            if (log.isDebugEnabled()) log.debug("onLoadFinished: background work finished, forcing category reload");
            mBackgroundWorkWasOngoing = false;
            LoaderManager.getInstance(this).restartLoader(-1, null, this);
            return;
        }
        mBackgroundWorkWasOngoing = backgroundWorkOngoing;
        // List of categories
        if (cursorLoader.getId() == -1) {
            boolean deferRowLoaders = shouldDeferRowLoadersDuringBackgroundWork() && backgroundWorkOngoing;
            if (deferRowLoaders) {
                showDeferredLoadingState();
                mCurrentCategoriesCursor = c;
                mRowsLoadDeferred = true;
                return;
            }
            mEmptyView.setText(R.string.you_have_no_movies);
            mEmptyView.setVisibility(c.getCount() > 0 ? View.GONE : View.VISIBLE);
            if (mCurrentCategoriesCursor != null) {
                if (!mRowsLoadDeferred && !isCategoriesListModified(mCurrentCategoriesCursor, c)) {
                    // no actual modification, no need to rebuild all the rows
                    mCurrentCategoriesCursor = c; // keep the reference to the new cursor because the old one won't be valid anymore
                    return;
                }
            }
            mCurrentCategoriesCursor = c;
            loadCategoriesRows(c, !deferRowLoaders);
            mRowsLoadDeferred = deferRowLoaders;
            if (STOP_LOADING) cursorLoader.stopLoading();
        }
        // One of the row
        else {
            CursorObjectAdapter adapter = mAdaptersMap.get(cursorLoader.getId());
            if (adapter != null) {
                adapter.changeCursor(c);
                // do not get any other update because complex views should not be updated while scanning to prevent crash
                if (STOP_LOADING) cursorLoader.stopLoading();
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        if (log.isDebugEnabled()) log.debug("onLoaderReset");
        if (cursorLoader.getId() == -1) {
            mCurrentCategoriesCursor = null;
            mRowsLoadDeferred = false;
            mBackgroundWorkWasOngoing = false;
            return;
        }
        CursorObjectAdapter adapter = mAdaptersMap.get(cursorLoader.getId());
        if (adapter != null) {
            adapter.changeCursor(null);
        }
    }

    private void clearRowAdapters() {
        for (int i = 0; i < mAdaptersMap.size(); i++) {
            CursorObjectAdapter adapter = mAdaptersMap.valueAt(i);
            if (adapter != null) {
                adapter.changeCursor(null);
            }
        }
        mAdaptersMap.clear();
    }

    private boolean isCategoriesListModified(Cursor oldCursor, Cursor newCursor) {

        // Modified for sure if has different length
        if (oldCursor.getCount() != newCursor.getCount()) {
            if (log.isDebugEnabled()) log.debug("Difference found in the category list (size changed)");
            return true;
        }

        // these two column index are the same but it looks nicer like this :-)
        final int oldSubsetNameColumn = oldCursor.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_NAME);
        final int newSubsetNameColumn = newCursor.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_NAME);

        // Check all names
        oldCursor.moveToFirst();
        newCursor.moveToFirst();
        while (!oldCursor.isAfterLast() && !newCursor.isAfterLast()) {
            final String oldName = oldCursor.getString(oldSubsetNameColumn);
            final String newName = newCursor.getString(newSubsetNameColumn);
            if (oldName != null && !oldName.equals(newName)) {
                // difference found
                if (log.isDebugEnabled()) log.debug("Difference found in the category list ({} vs {})", oldName, newName);
                return true;
            }
            oldCursor.moveToNext();
            newCursor.moveToNext();
        }
        // no difference found
        if (log.isDebugEnabled()) log.debug("No difference found in the category list");
        return false;
    }

    private void loadCategoriesRows(Cursor c, boolean loadSubsetRows) {
        if (c == null) {
            if (log.isDebugEnabled()) log.debug("loadCategoriesRows: cursor null exiting");
            return;
        }
        int subsetIdColumn = c.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_ID);
        int subsetNameColumn = c.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_NAME);
        int listOfMovieIdsColumn = c.getColumnIndex(MoviesByLoader.COLUMN_LIST_OF_MOVIE_IDS);

        mRowsAdapter.clear();
        clearRowAdapters();
        
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
            String listOfMovieIds = c.getString(listOfMovieIdsColumn);

            // Build the row
            CursorObjectAdapter subsetAdapter = new CursorObjectAdapter(mVideoPresenter);
            subsetAdapter.setMapper(mVideoMapper);
            rows.add(new ListRow(subsetId, new HeaderItem(subsetName), subsetAdapter));
            mAdaptersMap.append(subsetId, subsetAdapter);

            if (loadSubsetRows) {
                // Start the loader manager for this row
                Bundle args = new Bundle();
                args.putString("ids", listOfMovieIds);
                args.putString("sort", mSortOrder);
                // cf. https://github.com/nova-video-player/aos-AVP/issues/141
                try {
                    LoaderManager.getInstance(this).restartLoader(subsetId, args, this);
                } catch (Exception e) {
                    log.warn("caught exception in loadCategoriesRows ",e);
                }
            }

            c.moveToNext();
        }

        mRowsAdapter.addAll(0,rows);
        // unregister observer to not get notifications of content change
        if (UNREGISTER_OBSERVERS) mRowsAdapter.unregisterAllObservers();
    }

    private void showDeferredLoadingState() {
        mRowsAdapter.clear();
        clearRowAdapters();
        mEmptyView.setText(R.string.not_available_during_media_scanning);
        mEmptyView.setVisibility(View.VISIBLE);
    }

    private boolean isBackgroundWorkOngoing() {
        return NetworkScannerReceiver.isScannerWorking()
                || LoaderUtils.getScrapeInProgress()
                || ImportState.VIDEO.isInitialImport();
    }

    private void updateBackground() {
        if (log.isDebugEnabled()) log.debug("updateBackground");

        // Update private mode indicator visibility
        PrivateModeUIHelper.updatePrivateModeIndicator(getView());

        bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached())
            bgMngr.attach(getActivity().getWindow());

        if (PrivateMode.isActive()) {
            int privateModeColor = ThemeManager.getInstance(getActivity()).getPrivateModeColor();
            bgMngr.setColor(privateModeColor);
            bgMngr.setDrawable(new ColorDrawable(privateModeColor));
        } else {
            // Use ThemeManager to get the appropriate background color for the current theme
            int backgroundColor = ThemeManager.getInstance(getActivity()).getLeanbackBackgroundColor();
            bgMngr.setColor(backgroundColor);
            bgMngr.setDrawable(new ColorDrawable(backgroundColor));
        }
    }

    private void setupThemeListener() {
        // Guard against duplicate registration
        if (mThemeChangeListener != null) {
            return;
        }
        ThemeManager themeManager = ThemeManager.getInstance(getActivity());
        mThemeChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (VideoPreferencesCommon.KEY_APP_THEME.equals(key)) {
                    if (log.isDebugEnabled()) log.debug("Theme changed, updating background and colors");
                    updateBackground();
                    // Update brand color
                    setBrandColor(themeManager.getLeanbackHeaderColor());
                }
            }
        };
        themeManager.registerThemeChangeListener(mThemeChangeListener);
    }
    
    public ArrayObjectAdapter getRowsAdapter() {
        return mRowsAdapter;
    }
}
