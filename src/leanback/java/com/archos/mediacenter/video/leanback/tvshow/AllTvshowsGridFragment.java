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

package com.archos.mediacenter.video.leanback.tvshow;

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.FocusHighlight;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridPresenter;
import android.util.SparseArray;
import android.view.View;

import com.archos.customizedleanback.app.MyVerticalGridFragment;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.DisplayMode;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.VideoListPresenter;
import com.archos.mediacenter.video.leanback.search.VideoSearchActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediaprovider.video.VideoStore;

public class AllTvshowsGridFragment extends MyVerticalGridFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "AllTvshowsGridFragment";

    private static final String PREF_ALL_TVSHOWS_DISPLAY_MODE = "PREF_ALL_TVSHOWS_DISPLAY_MODE";

    private static final String SORT_PARAM_KEY = AllTvshowsGridFragment.class.getName() + "_SORT";

    private CursorObjectAdapter mTvshowsAdapter;
    private DisplayMode mDisplayMode;
    private SharedPreferences mPrefs;
    private Overlay mOverlay;

    private int mSortOrderItem;
    private String mSortOrder;
    private CharSequence[] mSortOrderEntries;
    private BackgroundManager bgMngr = null;

    public static SparseArray<TvshowsSortOrderEntry> sortOrderIndexer = new SparseArray<TvshowsSortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new TvshowsSortOrderEntry(R.string.sort_by_name_asc,        VideoStore.Video.VideoColumns.SCRAPER_TITLE + " ASC"));
        sortOrderIndexer.put(1, new TvshowsSortOrderEntry(R.string.sort_by_date_added_desc, "max(" + VideoStore.Video.VideoColumns.DATE_ADDED + ") DESC"));
        sortOrderIndexer.put(2, new TvshowsSortOrderEntry(R.string.sort_by_date_premiered_desc,       VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED + " DESC"));
        sortOrderIndexer.put(3, new TvshowsSortOrderEntry(R.string.sort_by_rating_asc,      "IFNULL(" + VideoStore.Video.VideoColumns.SCRAPER_S_RATING + ", 0) DESC"));
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int displayModeIndex = mPrefs.getInt(PREF_ALL_TVSHOWS_DISPLAY_MODE, -1);
        if (displayModeIndex<0) {
            mDisplayMode = DisplayMode.GRID; // default
        } else {
            mDisplayMode = DisplayMode.values()[displayModeIndex];
        }
        mSortOrder = mPrefs.getString(SORT_PARAM_KEY, TvshowSortOrderEntries.DEFAULT_SORT);
        mSortOrderEntries = TvshowsSortOrderEntry.getSortOrderEntries(getActivity(), sortOrderIndexer);

        updateBackground();

        setTitle(getString(R.string.all_tvshows));
        setEmptyTextMessage(getString(R.string.you_have_no_tv_shows));
        setOnItemViewClickedListener(new VideoViewClickedListener(getActivity()));
        setOnItemViewSelectedListener(new ItemViewSelectedListener());

        initGridOrList();
    }

    private void initGridOrList() {
        VerticalGridPresenter vgp = null;
        Presenter filePresenter = null;

        switch (mDisplayMode) {
            case GRID:
                filePresenter = new PosterImageCardPresenter(getActivity());
                vgp = new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false); // No focus dimmer
                vgp.setNumberOfColumns(6);
                break;
            case LIST:
                filePresenter = new VideoListPresenter(false);
                vgp = new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_SMALL, false); // No focus dimmer
                vgp.setNumberOfColumns(1);
                break;
        }

        mTvshowsAdapter = new CursorObjectAdapter(filePresenter);
        mTvshowsAdapter.setMapper(new CompatibleCursorMapperConverter(new TvshowCursorMapper()));
        setAdapter(mTvshowsAdapter);

        setGridPresenter(vgp);
        Bundle args = new Bundle();
        args.putString("sort", mSortOrder);
        getLoaderManager().restartLoader(0, args, AllTvshowsGridFragment.this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mOverlay = new Overlay(this);

        // Set orb icon
        switch (mDisplayMode) {
            case GRID: getTitleView().setOrb2IconResId(R.drawable.orb_list); break;
            case LIST: getTitleView().setOrb2IconResId(R.drawable.orb_grid);  break;
        }

        getTitleView().setOrb3IconResId(R.drawable.orb_sort);

        // Set orb color
        setSearchAffordanceColor(getResources().getColor(R.color.lightblueA200));

        // Set first orb action
        getTitleView().setOnOrb1ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), VideoSearchActivity.class);
                
                intent.putExtra(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_EPISODE);
                startActivity(intent);
            }
        });

        // Set second orb action
        getTitleView().setOnOrb2ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (mDisplayMode) {
                    case GRID:
                        mDisplayMode = DisplayMode.LIST;
                        break;
                    case LIST:
                        mDisplayMode = DisplayMode.GRID;
                        break;
                }
                // Save the new setting
                mPrefs.edit().putInt(PREF_ALL_TVSHOWS_DISPLAY_MODE, mDisplayMode.ordinal()).commit();
                // Reload a brand new fragment
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AllTvshowsGridFragment())
                        .commit();
            }
        });

        // Set third orb action
        getTitleView().setOnOrb3ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSortOrderItem = TvshowsSortOrderEntry.sortOrder2Item(mSortOrder, sortOrderIndexer);
                new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(mSortOrderEntries, mSortOrderItem, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mSortOrderItem != which) {
                                    mSortOrderItem = which;
                                    mSortOrder = TvshowsSortOrderEntry.item2SortOrder(mSortOrderItem, sortOrderIndexer);
                                    Bundle args = new Bundle();
                                    args.putString("sort", mSortOrder);
                                    getLoaderManager().restartLoader(0, args, AllTvshowsGridFragment.this);
                                }
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
    }

    @Override
    public void onDestroy() {
        // Save the sort mode
        mPrefs.edit().putString(SORT_PARAM_KEY, mSortOrder).commit();
        super.onDestroy();
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
        updateBackground();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == 0) {
            if (args == null) {
                return new AllTvshowsLoader(getActivity());
            } else {
                return new AllTvshowsLoader(getActivity(), args.getString("sort"));
            }
        }
        else return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (cursorLoader.getId()==0) {
            mTvshowsAdapter.swapCursor(cursor);
            setEmptyViewVisiblity(cursor.getCount()<1);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mTvshowsAdapter.swapCursor(null);
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            /*if (item instanceof Movie) {
                mBackgroundURI = ((Movie) item).getBackgroundImageURI();
                startBackgroundTimer();
            }*/
        }
    }

    private void updateBackground() {
        Resources r = getResources();

        bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached())
            bgMngr.attach(getActivity().getWindow());

        if (PrivateMode.isActive()) {
            bgMngr.setColor(r.getColor(R.color.private_mode));
            bgMngr.setDrawable(r.getDrawable(R.drawable.private_background));
        } else {
            bgMngr.setColor(r.getColor(R.color.leanback_background));
            bgMngr.setDrawable(new ColorDrawable(r.getColor(R.color.leanback_background)));
        }
    }

}
