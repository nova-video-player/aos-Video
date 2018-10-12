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

package com.archos.mediacenter.video.leanback;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.IconItemRowPresenter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.Log;
import android.view.View;

import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.DensityTweak;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.browser.loader.LastAddedLoader;
import com.archos.mediacenter.video.browser.loader.LastPlayedLoader;
import com.archos.mediacenter.video.browser.loader.NonScrapedVideosCountLoader;
import com.archos.mediacenter.video.leanback.adapter.object.Box;
import com.archos.mediacenter.video.leanback.adapter.object.EmptyView;
import com.archos.mediacenter.video.leanback.adapter.object.Icon;
import com.archos.mediacenter.video.leanback.filebrowsing.ExtStorageListingActivity;
import com.archos.mediacenter.video.leanback.filebrowsing.LocalListingActivity;
import com.archos.mediacenter.video.leanback.movies.AllMoviesGridActivity;
import com.archos.mediacenter.video.leanback.movies.AllMoviesIconBuilder;
import com.archos.mediacenter.video.leanback.movies.MoviesByGenreActivity;
import com.archos.mediacenter.video.leanback.movies.MoviesByYearActivity;
import com.archos.mediacenter.video.leanback.network.NetworkRootActivity;
import com.archos.mediacenter.video.leanback.nonscraped.NonScrapedVideosActivity;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.BoxItemPresenter;
import com.archos.mediacenter.video.leanback.presenter.IconItemPresenter;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.search.VideoSearchActivity;
import com.archos.mediacenter.video.leanback.tvshow.AllTvshowsGridActivity;
import com.archos.mediacenter.video.leanback.tvshow.AllTvshowsIconBuilder;
import com.archos.mediacenter.video.leanback.tvshow.TvshowsByAlphaActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowsByGenreActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediacenter.video.utils.WebUtils;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStore;

public class MainFragment extends BrowseFragment  implements  LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "MainFragment";
    private static final String PREF_PRIVATE_MODE = "PREF_PRIVATE_MODE";

    final static int LOADER_ID_LAST_ADDED = 42;
    final static int LOADER_ID_LAST_PLAYED = 43;
    final static int LOADER_ID_ALL_MOVIES = 46;
    final static int LOADER_ID_ALL_TV_SHOWS = 44;
    final static int LOADER_ID_NON_SCRAPED_VIDEOS_COUNT = 45;

    final static int ROW_ID_LAST_ADDED = 1000;
    final static int ROW_ID_LAST_PLAYED = 1001;
    final static int ROW_ID_MOVIES = 1002;
    final static int ROW_ID_TVSHOW2 = 1003;
    final static int ROW_ID_ALL_MOVIES = 1007;
    final static int ROW_ID_TVSHOWS = 1004;
    final static int ROW_ID_FILES = 1005;
    final static int ROW_ID_PREFERENCES = 1006;

    // Need these row indexes to update the full ListRow object
    final static int ROW_INDEX_UNSET = -1;
    int rowIndexLastAdded = ROW_INDEX_UNSET;
    int rowIndexLastPlayed = ROW_INDEX_UNSET;
    int rowIndexTvShows = ROW_INDEX_UNSET;

    private ArrayObjectAdapter mRowsAdapter;
    private CursorObjectAdapter mMoviesAdapter;
    private CursorObjectAdapter mTvshowsAdapter;
    private CursorObjectAdapter mLastAddedAdapter;
    private CursorObjectAdapter mLastPlayedAdapter;
    private ArrayObjectAdapter mFileBrowsingRowAdapter;
    private ArrayObjectAdapter mPreferencesRowAdapter;

    private ListRow mLastAddedRow;
    private ListRow mLastPlayedRow;
    private ListRow mMoviesRow;
    private ListRow mTvshowsRow;
    private ListRow mMovieRow;
    private ListRow mTvshowRow;
    
    private boolean mShowLastAddedRow;
    private boolean mShowLastPlayedRow;
    private boolean mShowMoviesRow;
    private String mMovieSortOrder;
    private boolean mShowTvshowsRow;
    private String mTvShowSortOrder;

    private Box mNonScrapedVideosItem;

    private SharedPreferences mPrefs;
    private Overlay mOverlay;
    private BroadcastReceiver mUpdateReceiver;
    private IntentFilter mUpdateFilter;

    private BackgroundManager bgMngr;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        IntentFilter intentFilter = new IntentFilter(ExtStorageReceiver.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_CHANGED);
        intentFilter.addDataScheme("file");
        intentFilter.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);//new android nougat send UriExposureException when scheme = file
        getActivity().registerReceiver(mExternalStorageReceiver, intentFilter);
    }

    public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        mUpdateReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent!=null&& ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED.equals(intent.getAction())) {
                    getLoaderManager().restartLoader(LOADER_ID_LAST_ADDED, null, MainFragment.this);
                    Log.d(TAG, "manual reload");
                }
            }
        };

        mUpdateFilter = new IntentFilter();
        for(String scheme : UriUtils.sIndexableSchemes){
            mUpdateFilter.addDataScheme(scheme);
        }
        mUpdateFilter.addAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        getActivity().unregisterReceiver(mExternalStorageReceiver);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mShowLastAddedRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_LAST_ADDED_ROW, VideoPreferencesFragment.SHOW_LAST_ADDED_ROW_DEFAULT);
        mShowLastPlayedRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_LAST_PLAYED_ROW, VideoPreferencesFragment.SHOW_LAST_PLAYED_ROW_DEFAULT);
        mShowMoviesRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_ALL_MOVIES_ROW, VideoPreferencesFragment.SHOW_ALL_MOVIES_ROW_DEFAULT);
        mMovieSortOrder = mPrefs.getString(VideoPreferencesFragment.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);
        mShowTvshowsRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_ALL_TV_SHOWS_ROW, VideoPreferencesFragment.SHOW_ALL_TV_SHOWS_ROW_DEFAULT);
        mTvShowSortOrder = mPrefs.getString(VideoPreferencesFragment.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);

        if (mPrefs.getBoolean(PREF_PRIVATE_MODE, false) !=  PrivateMode.isActive()) {
            PrivateMode.toggle();
            findAndUpdatePrivateModeIcon();
        }

        updateBackground();

        Resources r = getResources();
        setBadgeDrawable(r.getDrawable(R.drawable.leanback_title));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(r.getColor(R.color.leanback_side));

        // set search icon color
        setSearchAffordanceColor(r.getColor(R.color.lightblueA200));

        setupEventListeners();

        loadRows();
        getLoaderManager().initLoader(LOADER_ID_LAST_ADDED, null, this);
        getLoaderManager().initLoader(LOADER_ID_LAST_PLAYED, null, this);
        
        Bundle movieArgs = new Bundle();

        movieArgs.putString("sort", mMovieSortOrder);
        getLoaderManager().initLoader(LOADER_ID_ALL_MOVIES, movieArgs, this);
        
        Bundle tvshowArgs = new Bundle();

        tvshowArgs.putString("sort", mTvShowSortOrder);
        getLoaderManager().initLoader(LOADER_ID_ALL_TV_SHOWS, tvshowArgs, this);
        getLoaderManager().initLoader(LOADER_ID_NON_SCRAPED_VIDEOS_COUNT, null, this);
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

        updateBackground();

        getActivity().registerReceiver(mUpdateReceiver, mUpdateFilter);
        
        boolean newShowLastAddedRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_LAST_ADDED_ROW, VideoPreferencesFragment.SHOW_LAST_ADDED_ROW_DEFAULT);

        if (newShowLastAddedRow != mShowLastAddedRow) {
            mShowLastAddedRow = newShowLastAddedRow;
            updateLastAddedRow(null);
        }

        boolean newShowLastPlayedRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_LAST_PLAYED_ROW, VideoPreferencesFragment.SHOW_LAST_PLAYED_ROW_DEFAULT);

        if (newShowLastPlayedRow != mShowLastPlayedRow) {
            mShowLastPlayedRow = newShowLastPlayedRow;
            updateLastPlayedRow(null);
        }

        boolean newShowMoviesRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_ALL_MOVIES_ROW, VideoPreferencesFragment.SHOW_ALL_MOVIES_ROW_DEFAULT);
        
        if (newShowMoviesRow != mShowMoviesRow) {
            mShowMoviesRow = newShowMoviesRow;
            updateMoviesRow(null);
        }

        boolean newShowTvshowsRow = mPrefs.getBoolean(VideoPreferencesFragment.KEY_SHOW_ALL_TV_SHOWS_ROW, VideoPreferencesFragment.SHOW_ALL_TV_SHOWS_ROW_DEFAULT);

        if (newShowTvshowsRow != mShowTvshowsRow) {
            mShowTvshowsRow = newShowTvshowsRow;
            updateTvShowsRow(null);
        }

        String newMovieSortOrder = mPrefs.getString(VideoPreferencesFragment.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);

        if (!newMovieSortOrder.equals(mMovieSortOrder)) {
            mMovieSortOrder = newMovieSortOrder;
            Bundle args = new Bundle();

            args.putString("sort", mMovieSortOrder);
            getLoaderManager().restartLoader(LOADER_ID_ALL_MOVIES, args, this);
        }

        String newTvShowSortOrder = mPrefs.getString(VideoPreferencesFragment.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);

        if (!newTvShowSortOrder.equals(mTvShowSortOrder)) {
            mTvShowSortOrder = newTvShowSortOrder;
            Bundle args = new Bundle();

            args.putString("sort", mTvShowSortOrder);
            getLoaderManager().restartLoader(LOADER_ID_ALL_TV_SHOWS, args, this);
        }

        findAndUpdatePrivateModeIcon();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
        getActivity().unregisterReceiver(mUpdateReceiver);
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

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), VideoSearchActivity.class);
                intent.putExtra(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_ALL);
                startActivity(intent);
            }
        });
        setOnItemViewClickedListener(new MainViewClickedListener(getActivity()));
    }

    private void loadRows() {
        Log.d(TAG,"loadRows()");
        // Two different row presenters, one standard for regular cards, one special for the icon items
        ListRowPresenter listRowPresenter = new ListRowPresenter();
        IconItemRowPresenter iconItemRowPresenter = new IconItemRowPresenter();

        // Only way I found to use two different presenter is using a ClassPresenterSelector, hence i needed
        // to create a dummy IconListRow that does nothing more than a regular ListRow
        ClassPresenterSelector rowsPresenterSelector = new ClassPresenterSelector();
        rowsPresenterSelector.addClassPresenter(ListRow.class, listRowPresenter);
        rowsPresenterSelector.addClassPresenter(IconListRow.class, iconItemRowPresenter);

        // Basic header presenter for both row presenters
        listRowPresenter.setHeaderPresenter(new RowHeaderPresenter());
        iconItemRowPresenter.setHeaderPresenter(new RowHeaderPresenter());

        mRowsAdapter = new ArrayObjectAdapter(rowsPresenterSelector);

        mLastAddedAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mLastAddedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mLastAddedRow = new ListRow(ROW_ID_LAST_ADDED, new HeaderItem(getString(R.string.recently_added)), mLastAddedAdapter);

        mLastPlayedAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mLastPlayedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mLastPlayedRow = new ListRow(ROW_ID_LAST_PLAYED, new HeaderItem(getString(R.string.recently_played)), mLastPlayedAdapter);

        ArrayObjectAdapter movieRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        movieRowAdapter.add(buildAllMoviesBox());
        movieRowAdapter.add(new Box(Box.ID.MOVIES_BY_GENRE, getString(R.string.movies_by_genre), R.drawable.genres_banner));
        movieRowAdapter.add(new Box(Box.ID.MOVIES_BY_YEAR, getString(R.string.movies_by_year), R.drawable.years_banner_2019));
        mMovieRow = new ListRow(ROW_ID_MOVIES, new HeaderItem(getString(R.string.movies)), movieRowAdapter);

        ArrayObjectAdapter tvshowRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        tvshowRowAdapter.add(buildAllTvshowsBox());
        tvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_ALPHA, getString(R.string.tvshows_by_alpha), R.drawable.alpha_banner));
        tvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_GENRE, getString(R.string.tvshows_by_genre), R.drawable.genres_banner));
        mTvshowRow = new ListRow(ROW_ID_TVSHOW2, new HeaderItem(getString(R.string.all_tv_shows)), tvshowRowAdapter);
        
        mMoviesAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mMoviesAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mMoviesRow = new ListRow(ROW_ID_ALL_MOVIES, new HeaderItem(getString(R.string.all_movies)), mMoviesAdapter);

        mTvshowsAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mTvshowsAdapter.setMapper(new CompatibleCursorMapperConverter(new TvshowCursorMapper()));
        mTvshowsRow = new ListRow(ROW_ID_TVSHOWS, new HeaderItem(getString(R.string.all_tvshows)), mTvshowsAdapter);

        mFileBrowsingRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        mFileBrowsingRowAdapter.add(new Box(Box.ID.NETWORK, getString(R.string.network_storage), R.drawable.filetype_new_server));
        mFileBrowsingRowAdapter.add(new Box(Box.ID.FOLDERS, getString(R.string.internal_storage), R.drawable.filetype_new_folder));
        mFileBrowsingRowAdapter.add(new Box(Box.ID.VIDEOS_BY_LISTS, getString(R.string.video_lists), R.drawable.filetype_new_playlist));

        mNonScrapedVideosItem = new Box(Box.ID.NON_SCRAPED_VIDEOS, getString(R.string.non_scraped_videos), R.drawable.filetype_new_unscraped_video);
        // Add USB and SDcard items at init ?depending of their availability
        updateUsbAndSdcardVisibility();

        mRowsAdapter.add(new ListRow(ROW_ID_FILES,
                new HeaderItem(getString(R.string.leanback_browsing)),
                mFileBrowsingRowAdapter));

        mPreferencesRowAdapter = new ArrayObjectAdapter(new IconItemPresenter());
        mPreferencesRowAdapter.add(new Icon(Icon.ID.PREFERENCES, getString(R.string.preferences), R.drawable.lollipop_settings));
        mPreferencesRowAdapter.add(new Icon(Icon.ID.PRIVATE_MODE, getString(R.string.private_mode_is_on), getString(R.string.private_mode_is_off),
                                            R.drawable.private_mode,  R.drawable.private_mode_off, PrivateMode.isActive()));
        mPreferencesRowAdapter.add(new Icon(Icon.ID.LEGACY_UI, getString(R.string.leanback_legacy_ui), R.drawable.legacy_ui_icon));
        mPreferencesRowAdapter.add(new Icon(Icon.ID.HELP_FAQ, getString(R.string.help_faq), R.drawable.lollipop_help));

        // Must use an IconListRow to have the dedicated presenter used (see ClassPresenterSelector above)
        mRowsAdapter.add(new IconListRow(ROW_ID_PREFERENCES,
                new HeaderItem(getString(R.string.preferences)),
                mPreferencesRowAdapter));

        setAdapter(mRowsAdapter);
    }

    private Box buildAllMoviesBox() {
        Bitmap iconBitmap = new AllMoviesIconBuilder(getActivity()).buildNewBitmap();
        if (iconBitmap!=null) {
            return new Box(Box.ID.ALL_MOVIES, getString(R.string.all_movies), iconBitmap);
        }
        else {
            // fallback to regular default icon
            return new Box(Box.ID.ALL_MOVIES, getString(R.string.all_movies), R.drawable.movies_banner);
        }
    }

    private Box buildAllTvshowsBox() {
        Bitmap iconBitmap = new AllTvshowsIconBuilder(getActivity()).buildNewBitmap();
        if (iconBitmap!=null) {
            return new Box(Box.ID.ALL_TVSHOWS, getString(R.string.all_tvshows), iconBitmap);
        }
        else {
            // fallback to regular default icon
            return new Box(Box.ID.ALL_TVSHOWS, getString(R.string.all_tvshows), R.drawable.movies_banner);
        }
    }

    private void updateLastAddedRow(Cursor cursor) {
        if (cursor != null)
            mLastAddedAdapter.changeCursor(cursor);
        else
            cursor = mLastAddedAdapter.getCursor();

        int currentPosition = getRowPosition(ROW_ID_LAST_ADDED);

        if (cursor.getCount() == 0 || !mShowLastAddedRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
        }
        else {
            if (currentPosition == -1) {
                int newPosition = 0;
                
                mRowsAdapter.add(newPosition, mLastAddedRow);
            }
        }
    }

    private void updateLastPlayedRow(Cursor cursor) {
        if (cursor != null)
            mLastPlayedAdapter.changeCursor(cursor);
        else
            cursor = mLastPlayedAdapter.getCursor();

        int currentPosition = getRowPosition(ROW_ID_LAST_PLAYED);

        if (cursor.getCount() == 0 || !mShowLastPlayedRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
        }
        else {
            if (currentPosition == -1) {
                int newPosition = 0;

                if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                
                mRowsAdapter.add(newPosition, mLastPlayedRow);
            }
        }
    }

    private void updateMoviesRow(Cursor cursor) {
        if (cursor != null)
            mMoviesAdapter.changeCursor(cursor);
        else
            cursor = mMoviesAdapter.getCursor();

        int currentPosition = getRowPosition(ROW_ID_ALL_MOVIES);

        if (cursor.getCount() == 0 || !mShowMoviesRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
            if (getRowPosition(ROW_ID_MOVIES) == -1) {
                int newPosition = 0;

                if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                
                mRowsAdapter.add(newPosition, mMovieRow);
            }
        }
        else {
            if (getRowPosition(ROW_ID_MOVIES) != -1)
                mRowsAdapter.removeItems(getRowPosition(ROW_ID_MOVIES), 1);
            if (currentPosition == -1) {
                int newPosition = 0;

                if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                
                mRowsAdapter.add(newPosition, mMoviesRow);
            }
        }
    }

    private void updateTvShowsRow(Cursor cursor) {
        if (cursor != null)
            mTvshowsAdapter.changeCursor(cursor);
        else
            cursor = mTvshowsAdapter.getCursor();

        int currentPosition = getRowPosition(ROW_ID_TVSHOWS);

        if (cursor.getCount() == 0 || !mShowTvshowsRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
            if (getRowPosition(ROW_ID_TVSHOW2) == -1) {
                int newPosition = 0;

                if (getRowPosition(ROW_ID_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_ALL_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_ALL_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                
                mRowsAdapter.add(newPosition, mTvshowRow);
            }
        }
        else {
            if (getRowPosition(ROW_ID_TVSHOW2) != -1)
                mRowsAdapter.removeItems(getRowPosition(ROW_ID_TVSHOW2), 1);
            if (currentPosition == -1) {
                int newPosition = 0;

                if (getRowPosition(ROW_ID_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_ALL_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_ALL_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                
                mRowsAdapter.add(newPosition, mTvshowsRow);
            }
        }
    }

    private void updateNonScrapedVideosVisibility(Cursor cursor) {
        if (NonScrapedVideosCountLoader.getNonScrapedVideoCount(cursor) > 0) {
            if (mFileBrowsingRowAdapter.indexOf(mNonScrapedVideosItem) < 0) {
                mFileBrowsingRowAdapter.add(mNonScrapedVideosItem);
            }
        } else {
            mFileBrowsingRowAdapter.remove(mNonScrapedVideosItem);
        }
    }

    /**
     *
     * @param rowId
     * @return -1 if the row is not present
     */
    private int getRowPosition(int rowId) {
        for (int i=0; i<mRowsAdapter.size(); i++) {
            Object o = mRowsAdapter.get(i);
            if (o instanceof ListRow) {
                if (((ListRow)o).getId() == rowId) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * @param adapter
     * @return true if the adapter is an ArrayObjectAdapter containing just one EmptyView object
     */
    private boolean isEmptyViewRow(ObjectAdapter adapter) {
        if (adapter instanceof ArrayObjectAdapter) {
            ArrayObjectAdapter arrayObjectAdapter = (ArrayObjectAdapter)adapter;
            if (arrayObjectAdapter.size()==1) {
                if (arrayObjectAdapter.get(0) instanceof EmptyView) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_ID_LAST_ADDED) {
            return new LastAddedLoader(getActivity());
        }
        else if (id == LOADER_ID_LAST_PLAYED) {
            return new LastPlayedLoader(getActivity());
        }
        else if (id == LOADER_ID_ALL_MOVIES) {
            if (args == null) {
                return new MoviesLoader(getActivity(), true);
            } else {
                return new MoviesLoader(getActivity(), args.getString("sort"), true);
            }
        }
        else if (id == LOADER_ID_ALL_TV_SHOWS) {
            if (args == null) {
                return new AllTvshowsLoader(getActivity());
            } else {
                return new AllTvshowsLoader(getActivity(), args.getString("sort"));
            }
        }
        else if (id == LOADER_ID_NON_SCRAPED_VIDEOS_COUNT) {
            return new NonScrapedVideosCountLoader(getActivity());
        }
        else return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Log.d(TAG,"onLoadFinished() cursor id="+cursorLoader.getId());
        if (cursorLoader.getId() == LOADER_ID_LAST_ADDED) {
            updateLastAddedRow(cursor);
            mInitLastAddedCount = cursor.getCount();
        }
        else if (cursorLoader.getId() == LOADER_ID_LAST_PLAYED) {
            updateLastPlayedRow(cursor);
            mInitLastPlayedCount = cursor.getCount();
        }
        else if (cursorLoader.getId() == LOADER_ID_ALL_MOVIES) {
            if (isVideosListModified(mMoviesAdapter.getCursor(), cursor))
                ((ArrayObjectAdapter)mMovieRow.getAdapter()).replace(0, buildAllMoviesBox());
            
            updateMoviesRow(cursor);
        }
        else if (cursorLoader.getId() == LOADER_ID_ALL_TV_SHOWS) {
            if (isVideosListModified(mTvshowsAdapter.getCursor(), cursor))
                ((ArrayObjectAdapter)mTvshowRow.getAdapter()).replace(0, buildAllTvshowsBox());
            
            updateTvShowsRow(cursor);
        }
        else if (cursorLoader.getId() == LOADER_ID_NON_SCRAPED_VIDEOS_COUNT) {
            updateNonScrapedVideosVisibility(cursor);
        }

        checkFocusInitialization();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) { }

    private boolean isVideosListModified(Cursor oldCursor, Cursor newCursor) {
        if ((oldCursor == null && newCursor != null) || (oldCursor != null && newCursor == null))
            return true;

        if (oldCursor.getCount() != newCursor.getCount())
            return true;
        
        // estimate
        return false;
    }

    /**
     * When opening, LastAdded and LastPlayed rows are not created yet, hence selection is on Movies.
     * Here we wait for the Loaders to return their results to know if we need to select the first row again (which will be LastAdded or LastPlayed)
     */
    private int mInitLastAddedCount = -1;
    private int mInitLastPlayedCount = -1;
    private boolean mFocusInitializationDone = false;

    private void checkFocusInitialization() {
        // Check if we have both Last Added and Last Played loader results
        if (!mFocusInitializationDone && mInitLastAddedCount>-1 && mInitLastPlayedCount>-1) {
            // If at least one of them is non empty we select the first line (which contains one of them)
            if (mInitLastAddedCount>0 || mInitLastPlayedCount>0) {
                this.setSelectedPosition(0, true);
                mFocusInitializationDone = true; // this must be done only once
            }
        }
    }

    /**
     * Update (un)mount sdcard/usb host
     */
    private final BroadcastReceiver mExternalStorageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ExtStorageReceiver.ACTION_MEDIA_MOUNTED)){
                // Remove "file://"
                String path = null;
                if(intent.getDataString().startsWith("file"))
                    path = intent.getDataString().substring("file".length());
                else if (intent.getDataString().startsWith(ExtStorageReceiver.ARCHOS_FILE_SCHEME))
                    path = intent.getDataString().substring(ExtStorageReceiver.ARCHOS_FILE_SCHEME.length());

                if (path == null || path.isEmpty()) {
                    return;
                }
                updateUsbAndSdcardVisibility();
            }
            else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_CHANGED)){
                updateUsbAndSdcardVisibility();
            }
            else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED)){
                final String path = intent.getDataString();
                if (path == null || path.isEmpty()) {
                    return;
                }
                updateUsbAndSdcardVisibility();
            }
        }
    };

    private void updateUsbAndSdcardVisibility() {
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        final boolean hasExternal = storageManager.hasExtStorage();

        //TODO make it beautifull
        mFileBrowsingRowAdapter.clear();
        mFileBrowsingRowAdapter.add(new Box(Box.ID.NETWORK, getString(R.string.network_storage), R.drawable.filetype_new_server));
        mFileBrowsingRowAdapter.add(new Box(Box.ID.FOLDERS, getString(R.string.internal_storage), R.drawable.filetype_new_folder));
        mFileBrowsingRowAdapter.add(new Box(Box.ID.VIDEOS_BY_LISTS, getString(R.string.video_lists), R.drawable.filetype_new_playlist));

        if (hasExternal) {
            for(String s : storageManager.getExtSdcards()) {
                Box item = new Box(Box.ID.SDCARD, getString(R.string.sd_card_storage), R.drawable.filetype_new_sdcard, s);
                mFileBrowsingRowAdapter.add(item);
            }
            for(String s : storageManager.getExtUsbStorages()) {
                Box item = new Box(Box.ID.USB, getString(R.string.usb_host_storage), R.drawable.filetype_new_usb, s);
                mFileBrowsingRowAdapter.add(item);
            }
            for(String s : storageManager.getExtOtherStorages()) {
                Box item = new Box(Box.ID.OTHER, getString(R.string.other_storage), R.drawable.filetype_new_folder, s);
                mFileBrowsingRowAdapter.add(item);
            }
        }
    }

    private void updatePrivateMode(Icon icon) {
        icon.setActive(PrivateMode.isActive());
        mPreferencesRowAdapter.replace(mPreferencesRowAdapter.indexOf(icon), icon);
        updateBackground();
    }

    private void findAndUpdatePrivateModeIcon() {
        if (mPreferencesRowAdapter != null) {
            for(int i=0 ; i<mPreferencesRowAdapter.size() ; i++) {
                Object item = mPreferencesRowAdapter.get(i);
                if (item instanceof Icon) {
                    Icon icon = (Icon) item;
                    if (icon.getId() == Icon.ID.PRIVATE_MODE) {
                        if (icon.isActive() != PrivateMode.isActive()) {
                            updatePrivateMode(icon);
                        }
                        break;
                    }
                }
            }
        }
    }

    public class MainViewClickedListener extends VideoViewClickedListener {

        final private Activity mActivity;

        public MainViewClickedListener(Activity activity) {
            super(activity);
            mActivity = activity;
        }

        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Box) {
                Box box = (Box)item;
                switch (box.getBoxId()) {
                    case ALL_MOVIES:
                        mActivity.startActivity(new Intent(mActivity, AllMoviesGridActivity.class));
                        break;
                    case MOVIES_BY_GENRE:
                        mActivity.startActivity(new Intent(mActivity, MoviesByGenreActivity.class));
                        break;
                    case MOVIES_BY_YEAR:
                        mActivity.startActivity(new Intent(mActivity, MoviesByYearActivity.class));
                        break;
                    case VIDEOS_BY_LISTS:
                        mActivity.startActivity(new Intent(mActivity, VideosByListActivity.class));
                        break;
                    case FOLDERS:
                        mActivity.startActivity(new Intent(mActivity, LocalListingActivity.class));
                        break;
                    case SDCARD:
                    case USB:
                    case OTHER:
                        Intent i = new Intent(mActivity, ExtStorageListingActivity.class);
                        i.putExtra(ExtStorageListingActivity.MOUNT_POINT, box.getPath());
                        i.putExtra(ExtStorageListingActivity.STORAGE_NAME, box.getName());
                        mActivity.startActivity(i);
                        break;
                    case NETWORK:
                        mActivity.startActivity(new Intent(mActivity, NetworkRootActivity.class));
                        break;
                    case NON_SCRAPED_VIDEOS:
                        mActivity.startActivity(new Intent(mActivity, NonScrapedVideosActivity.class));
                        break;
                    case ALL_TVSHOWS:
                        mActivity.startActivity(new Intent(mActivity, AllTvshowsGridActivity.class));
                        break;
                    case TVSHOWS_BY_ALPHA:
                        mActivity.startActivity(new Intent(mActivity, TvshowsByAlphaActivity.class));
                        break;
                    case TVSHOWS_BY_GENRE:
                        mActivity.startActivity(new Intent(mActivity, TvshowsByGenreActivity.class));
                        break;

                }
            }
            else if (item instanceof Icon) {
                Icon icon = (Icon)item;
                switch (icon.getId()) {
                    case PREFERENCES:
                        if (mActivity instanceof MainActivityLeanback) {
                            ((MainActivityLeanback)mActivity).startPreferencesActivity(); // I know this is ugly (and i'm ashamed...)
                        } else {
                            throw  new IllegalStateException("Sorry developper, this ugly code can work with a MainActivityLeanback only for now!");
                        }
                        break;
                    case PRIVATE_MODE:
                        if (!PrivateMode.isActive() && PrivateMode.canShowDialog(getActivity())) {
                            PrivateMode.showDialog(getActivity());
                        }
                        PrivateMode.toggle();
                        mPrefs.edit().putBoolean(PREF_PRIVATE_MODE, PrivateMode.isActive()).commit();
                        updatePrivateMode(icon);
                        break;
                    case LEGACY_UI:
                        new DensityTweak(getActivity())
                                .temporaryRestoreDefaultDensity();
                        mActivity.startActivity(new Intent(mActivity, MainActivity.class));
                        break;
                    case HELP_FAQ:
                        WebUtils.openWebLink(mActivity,getString(R.string.faq_url));
                        break;
                }
            }
            else {
                super.onItemClicked(itemViewHolder, item, rowViewHolder, row);
            }
        }

    }

}
