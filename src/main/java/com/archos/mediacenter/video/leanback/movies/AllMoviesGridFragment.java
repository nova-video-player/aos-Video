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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.FilmsLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.DisplayMode;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.VideoListPresenter;
import com.archos.mediacenter.video.leanback.search.VideoSearchActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.SortOrder;
import com.archos.mediaprovider.video.VideoStore;


public class AllMoviesGridFragment extends MyVerticalGridFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "AllMoviesGridFragment";

    private static final String PREF_ALL_MOVIES_DISPLAY_MODE = "PREF_ALL_MOVIES_DISPLAY_MODE";

    public static final String SORT_PARAM_KEY = AllMoviesGridFragment.class.getName() + "_SORT";

    public static final String SHOW_WATCHED_KEY = AllMoviesGridFragment.class.getName() + "_SHOW_WATCHED";

    private CursorObjectAdapter mMoviesAdapter;
    private DisplayMode mDisplayMode;
    private SharedPreferences mPrefs;
    private Overlay mOverlay;

    private int mSortOrderItem;
    private String mSortOrder;
    private CharSequence[] mSortOrderEntries;
    private BackgroundManager bgMngr = null;

    private boolean mShowWatched;

    private static Context mContext;

    public static SparseArray<MoviesSortOrderEntry> sortOrderIndexer = new SparseArray<MoviesSortOrderEntry>();
    static {
        sortOrderIndexer.put(0, new MoviesSortOrderEntry(R.string.sort_by_name_asc,        "name COLLATE LOCALIZED ASC"));
        sortOrderIndexer.put(1, new MoviesSortOrderEntry(R.string.sort_by_date_added_desc, VideoStore.MediaColumns.DATE_ADDED + " DESC"));
        sortOrderIndexer.put(2, new MoviesSortOrderEntry(R.string.sort_by_year_desc,       VideoStore.Video.VideoColumns.SCRAPER_M_YEAR + " DESC"));
        sortOrderIndexer.put(3, new MoviesSortOrderEntry(R.string.sort_by_duration_asc,    SortOrder.DURATION.getAsc()));
        sortOrderIndexer.put(4, new MoviesSortOrderEntry(R.string.sort_by_rating_asc,      SortOrder.SCRAPER_M_RATING.getDesc()));
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this.getContext();

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int displayModeIndex = mPrefs.getInt(PREF_ALL_MOVIES_DISPLAY_MODE, -1);
        if (displayModeIndex<0) {
            mDisplayMode = DisplayMode.GRID; // default
        } else {
            mDisplayMode = DisplayMode.values()[displayModeIndex];
        }
        mSortOrder = mPrefs.getString(SORT_PARAM_KEY, FilmsLoader.DEFAULT_SORT);
        mSortOrderEntries = MoviesSortOrderEntry.getSortOrderEntries(getActivity(), sortOrderIndexer);

        mShowWatched = mPrefs.getBoolean(SHOW_WATCHED_KEY, true);

        updateBackground();

        setTitle(getString(R.string.all_movies));
        setEmptyTextMessage(getString(R.string.you_have_no_movies));
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
                if (mMoviesAdapter != null) {
                    Movie movie = (Movie)mMoviesAdapter.get(getSelectedPosition());
                    if (movie != null) {
                        if (!movie.isPinned())
                            DbUtils.markAsPinned(getActivity(), movie);
                        else
                            DbUtils.markAsNotPinned(getActivity(), movie);
                        Bundle args = new Bundle();
                        args.putString("sort", mSortOrder);
                        args.putBoolean("showWatched", mShowWatched);
                        LoaderManager.getInstance(AllMoviesGridFragment.this).restartLoader(0, args, AllMoviesGridFragment.this);
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

        mMoviesAdapter = new CursorObjectAdapter(filePresenter);
        mMoviesAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        setAdapter(mMoviesAdapter);

        setGridPresenter(vgp);
        Bundle args = new Bundle();
        args.putString("sort", mSortOrder);
        args.putBoolean("showWatched", mShowWatched);
        LoaderManager.getInstance(this).restartLoader(0, args, AllMoviesGridFragment.this);
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
                intent.putExtra(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_MOVIE);
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
                mPrefs.edit().putInt(PREF_ALL_MOVIES_DISPLAY_MODE, mDisplayMode.ordinal()).commit();
                // Reload a brand new fragment
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new AllMoviesGridFragment())
                        .commit();
            }
        });

        // Set third orb action
        getTitleView().setOnOrb3ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSortOrderItem = MoviesSortOrderEntry.sortOrder2Item(mSortOrder, sortOrderIndexer);
                new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(mSortOrderEntries, mSortOrderItem, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mSortOrderItem != which) {
                                    mSortOrderItem = which;
                                    mSortOrder = MoviesSortOrderEntry.item2SortOrder(mSortOrderItem, sortOrderIndexer);
                                    // Save the sort mode
                                    mPrefs.edit().putString(SORT_PARAM_KEY, mSortOrder).commit();
                                    Bundle args = new Bundle();
                                    args.putString("sort", mSortOrder);
                                    args.putBoolean("showWatched", mShowWatched);
                                    LoaderManager.getInstance(AllMoviesGridFragment.this).restartLoader(0, args, AllMoviesGridFragment.this);
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
                LoaderManager.getInstance(AllMoviesGridFragment.this).restartLoader(0, args, AllMoviesGridFragment.this);
            }
        });

        // Set fifth orb action
        getTitleView().setOnOrb5ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), MoviesByAlphaActivity.class);
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
                return new FilmsLoader(getActivity(), true);
            } else {
                return new FilmsLoader(getActivity(), VideoStore.Video.VideoColumns.NOVA_PINNED + " DESC, " + args.getString("sort"), args.getBoolean("showWatched"), true);
            }
        }
        else return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (getActivity() == null) return;
        if (cursorLoader.getId()==0) {
            mMoviesAdapter.swapCursor(cursor);
            setEmptyViewVisiblity(cursor.getCount()<1);

            if (mShowWatched)
                setTitle(getString(R.string.all_movies_format, cursor.getCount()));
            else
                setTitle(getString(R.string.not_watched_movies_format, cursor.getCount()));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mMoviesAdapter.swapCursor(null);
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
                if (!getTitleView().isShown() && mMoviesAdapter != null && mMoviesAdapter.size() > 0)
                    setSelectedPosition(0);
                if (!getTitleView().isFocused())
                    getTitleView().requestFocus();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (mMoviesAdapter != null) {
                    Video video = (Video)mMoviesAdapter.get(getSelectedPosition());
                    if (video != null)
                        PlayUtils.startVideo(getActivity(), video, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (mMoviesAdapter != null && mMoviesAdapter.size() > 0) {
                    setSelectedPosition(mMoviesAdapter.size() - 1);
                    if (!getView().isFocused())
                        getView().requestFocus();
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (mMoviesAdapter != null && mMoviesAdapter.size() > 0) {
                    setSelectedPosition(0);
                    if (!getView().isFocused())
                        getView().requestFocus();
                }
                break;
        }
    }
}
