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

package com.archos.mediacenter.video.leanback.nonscraped;

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
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
import android.widget.Toast;

import com.archos.customizedleanback.app.MyVerticalGridFragment;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.DisplayMode;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.browser.loader.NonScrapedVideosLoader;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.VideoListPresenter;
import com.archos.mediacenter.video.leanback.search.VideoSearchActivity;
import com.archos.mediacenter.video.utils.SortOrder;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.AutoScrapeService;


public class NonScrapedVideosFragment extends MyVerticalGridFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "NonScrapedVideosFragment";

    private static final String PREF_NON_SCRAPED_VIDEOS_DISPLAY_MODE = "PREF_NON_SCRAPED_VIDEOS_DISPLAY_MODE";

    private static final String SORT_PARAM_KEY = NonScrapedVideosFragment.class.getName() + "_SORT";

    private CursorObjectAdapter mNonScrapedAdapter;
    private DisplayMode mDisplayMode;
    private SharedPreferences mPrefs;
    private Overlay mOverlay;

    private int mSortOrderItem;
    private String mSortOrder;
    private CharSequence[] mSortOrderEntries;

    private static SparseArray<NonScrapedSortOrderEntry> sortOrderIndexer = new SparseArray<NonScrapedSortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new NonScrapedSortOrderEntry(R.string.sort_by_name_asc,        "name COLLATE NOCASE ASC"));
        sortOrderIndexer.put(1, new NonScrapedSortOrderEntry(R.string.sort_by_date_added_desc, VideoStore.MediaColumns.DATE_ADDED + " DESC"));
        sortOrderIndexer.put(2, new NonScrapedSortOrderEntry(R.string.sort_by_duration_asc,    SortOrder.DURATION.getAsc()));
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int displayModeIndex = mPrefs.getInt(PREF_NON_SCRAPED_VIDEOS_DISPLAY_MODE, -1);
        if (displayModeIndex < 0) {
            mDisplayMode = DisplayMode.GRID; // default
        } else {
            mDisplayMode = DisplayMode.values()[displayModeIndex];
        }
        mSortOrder = mPrefs.getString(SORT_PARAM_KEY, NonScrapedVideosLoader.DEFAULT_SORT);
        mSortOrderEntries = NonScrapedSortOrderEntry.getSortOrderEntries(getActivity(), sortOrderIndexer);

        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached()) {
            bgMngr.attach(getActivity().getWindow());
            bgMngr.setColor(getResources().getColor(R.color.leanback_background));
        }

        setTitle(getString(R.string.non_scraped_videos));
        setEmptyTextMessage(getString(R.string.you_have_no_non_scraped_videos));
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

        mNonScrapedAdapter = new CursorObjectAdapter(filePresenter);
        mNonScrapedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        setAdapter(mNonScrapedAdapter);

        setGridPresenter(vgp);
        Bundle args = new Bundle();
        args.putString("sort", mSortOrder);
        getLoaderManager().restartLoader(0, args, NonScrapedVideosFragment.this);
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
                intent.putExtra(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_NON_SCRAPED);
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
                mPrefs.edit().putInt(PREF_NON_SCRAPED_VIDEOS_DISPLAY_MODE, mDisplayMode.ordinal()).commit();
                // Reload a brand new fragment
                getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new NonScrapedVideosFragment())
                        .commit();
            }
        });

        // Set third orb action
        getTitleView().setOnOrb3ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSortOrderItem = NonScrapedSortOrderEntry.sortOrder2Item(mSortOrder, sortOrderIndexer);
                new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(mSortOrderEntries, mSortOrderItem, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mSortOrderItem != which) {
                                    mSortOrderItem = which;
                                    mSortOrder = NonScrapedSortOrderEntry.item2SortOrder(mSortOrderItem, sortOrderIndexer);
                                    Bundle args = new Bundle();
                                    args.putString("sort", mSortOrder);
                                    getLoaderManager().restartLoader(0, args, NonScrapedVideosFragment.this);
                                }
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
        getTitleView().setOnOrb4ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(),AutoScrapeService.class);
                intent.putExtra(AutoScrapeService.RESCAN_EVERYTHING, true);
                intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, true);
                getActivity().startService(intent);
                Toast.makeText(getActivity(), R.string.rescrap_in_progress, Toast.LENGTH_SHORT).show();

            }
        });
        getTitleView().setOnOrb4Description(getString(R.string.rescrap_all_title));
        getTitleView().setOrb4IconResId(R.drawable.orb_rescan);
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
                return new NonScrapedVideosLoader(getActivity());
            } else {
                return new NonScrapedVideosLoader(getActivity(), args.getString("sort"));
            }
        }
        else return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (cursorLoader.getId()==0) {
            mNonScrapedAdapter.swapCursor(cursor);
            setEmptyViewVisiblity(cursor.getCount()<1);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mNonScrapedAdapter.swapCursor(null);
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
        }
    }

}
