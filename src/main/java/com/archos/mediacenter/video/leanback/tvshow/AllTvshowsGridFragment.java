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

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridPresenter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;

import com.archos.customizedleanback.app.MyVerticalGridFragment;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.loader.AllTvshowsNoAnimeLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.DisplayMode;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.VideoListPresenter;
import com.archos.mediacenter.video.leanback.search.VideoSearchActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediaprovider.video.VideoStore;

public class AllTvshowsGridFragment extends MyVerticalGridFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "AllTvshowsGridFragment";

    private static final String PREF_ALL_TVSHOWS_DISPLAY_MODE = "PREF_ALL_TVSHOWS_DISPLAY_MODE";

    public static final String SORT_PARAM_KEY = AllTvshowsGridFragment.class.getName() + "_SORT";

    public static final String SHOW_WATCHED_KEY = AllTvshowsGridFragment.class.getName() + "_SHOW_WATCHED";

    private CursorObjectAdapter mTvshowsAdapter;
    private DisplayMode mDisplayMode;
    private SharedPreferences mPrefs;
    private Overlay mOverlay;

    private int mSortOrderItem;
    private String mSortOrder;
    private CharSequence[] mSortOrderEntries;
    private BackgroundManager bgMngr = null;

    private boolean mShowWatched;

    public static SparseArray<TvshowsSortOrderEntry> sortOrderIndexer = new SparseArray<TvshowsSortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new TvshowsSortOrderEntry(R.string.sort_by_name_asc,        VideoStore.Video.VideoColumns.SCRAPER_TITLE + " ASC"));
        sortOrderIndexer.put(1, new TvshowsSortOrderEntry(R.string.sort_by_date_added_desc, "max(" + VideoStore.Video.VideoColumns.DATE_ADDED + ") DESC"));
        sortOrderIndexer.put(2, new TvshowsSortOrderEntry(R.string.sort_by_date_played_desc, "max(" + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + ") DESC"));
        sortOrderIndexer.put(3, new TvshowsSortOrderEntry(R.string.sort_by_date_premiered_desc,       VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED + " DESC"));
        sortOrderIndexer.put(4, new TvshowsSortOrderEntry(R.string.sort_by_date_aired_desc, "max(" + VideoStore.Video.VideoColumns.SCRAPER_E_AIRED + ") DESC"));
        sortOrderIndexer.put(5, new TvshowsSortOrderEntry(R.string.sort_by_rating_asc,      "IFNULL(" + VideoStore.Video.VideoColumns.SCRAPER_S_RATING + ", 0) DESC"));
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

        mShowWatched = mPrefs.getBoolean(SHOW_WATCHED_KEY, true);

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
        View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mTvshowsAdapter != null) {
                    Tvshow tvshow = (Tvshow)mTvshowsAdapter.get(getSelectedPosition());
                    if (tvshow != null) {
                        if (!tvshow.isPinned())
                            DbUtils.markAsPinned(getActivity(), tvshow);
                        else
                            DbUtils.markAsNotPinned(getActivity(), tvshow);
                        Bundle args = new Bundle();
                        args.putString("sort", mSortOrder);
                        args.putBoolean("showWatched", mShowWatched);
                        LoaderManager.getInstance(AllTvshowsGridFragment.this).restartLoader(0, args, AllTvshowsGridFragment.this);
                    }
                }

                return true;
            }
        };

        switch (mDisplayMode) {
            case GRID:
                filePresenter = new PosterImageCardPresenter(getActivity(), longClickListener);
                vgp = new VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_LARGE, false); // No focus dimmer
                vgp.setNumberOfColumns(6);
                break;
            case LIST:
                filePresenter = new VideoListPresenter(false, longClickListener);
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
        args.putBoolean("showWatched", mShowWatched);
        LoaderManager.getInstance(this).restartLoader(0, args, AllTvshowsGridFragment.this);
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

        if (mShowWatched)
            getTitleView().setOrb4IconResId(R.drawable.orb_hide);
        else
            getTitleView().setOrb4IconResId(R.drawable.orb_show);

        getTitleView().setOrb5IconResId(R.drawable.orb_alpha);

        // Set orb color
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), R.color.lightblueA200));

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
                getParentFragmentManager().beginTransaction()
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
                                    // Save the sort mode
                                    mPrefs.edit().putString(SORT_PARAM_KEY, mSortOrder).commit();
                                    Bundle args = new Bundle();
                                    args.putString("sort", mSortOrder);
                                    args.putBoolean("showWatched", mShowWatched);
                                    LoaderManager.getInstance(AllTvshowsGridFragment.this).restartLoader(0, args, AllTvshowsGridFragment.this);
                                }
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });

        // Set fourth orb action
        getTitleView().setOnOrb4ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mShowWatched = !mShowWatched;
                // Save the new setting
                mPrefs.edit().putBoolean(SHOW_WATCHED_KEY, mShowWatched).commit();

                if (mShowWatched)
                    getTitleView().setOrb4IconResId(R.drawable.orb_hide);
                else
                    getTitleView().setOrb4IconResId(R.drawable.orb_show);
                
                // Reload
                Bundle args = new Bundle();
                args.putString("sort", mSortOrder);
                args.putBoolean("showWatched", mShowWatched);
                LoaderManager.getInstance(AllTvshowsGridFragment.this).restartLoader(0, args, AllTvshowsGridFragment.this);
            }
        });

        // Set fifth orb action
        getTitleView().setOnOrb5ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), TvshowsByAlphaActivity.class);
                startActivity(intent);
            }
        });
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
                return new AllTvshowsNoAnimeLoader(getActivity());
            } else {
                return new AllTvshowsNoAnimeLoader(getActivity(), VideoStore.Video.VideoColumns.NOVA_PINNED + " DESC, " + args.getString("sort"), args.getBoolean("showWatched"));
            }
        }
        else return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (getActivity() == null) return;
        if (cursorLoader.getId()==0) {
            mTvshowsAdapter.swapCursor(cursor);
            setEmptyViewVisiblity(cursor.getCount()<1);

            if (mShowWatched)
                setTitle(getString(R.string.all_tvshows_format, cursor.getCount()));
            else
                setTitle(getString(R.string.not_watched_tvshows_format, cursor.getCount()));
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

    public void onKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                if (!getTitleView().isShown() && mTvshowsAdapter != null && mTvshowsAdapter.size() > 0)
                    setSelectedPosition(0);
                if (!getTitleView().isFocused())
                    getTitleView().requestFocus();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                // TODO
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (mTvshowsAdapter != null && mTvshowsAdapter.size() > 0) {
                    setSelectedPosition(mTvshowsAdapter.size() - 1);
                    if (!getView().isFocused())
                        getView().requestFocus();
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (mTvshowsAdapter != null && mTvshowsAdapter.size() > 0) {
                    setSelectedPosition(0);
                    if (!getView().isFocused())
                        getView().requestFocus();
                }
                break;
        }
    }
}
