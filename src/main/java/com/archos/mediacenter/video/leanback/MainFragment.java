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
import androidx.loader.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.IconItemRowPresenter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import androidx.loader.content.Loader;
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
import com.archos.mediacenter.video.browser.loader.WatchingUpNextLoader;
import com.archos.mediacenter.video.browser.loader.NonScrapedVideosCountLoader;
import com.archos.mediacenter.video.leanback.adapter.object.Box;
import com.archos.mediacenter.video.leanback.adapter.object.EmptyView;
import com.archos.mediacenter.video.leanback.adapter.object.Icon;
import com.archos.mediacenter.video.leanback.filebrowsing.ExtStorageListingActivity;
import com.archos.mediacenter.video.leanback.filebrowsing.LocalListingActivity;
import com.archos.mediacenter.video.leanback.movies.AllMoviesGridActivity;
import com.archos.mediacenter.video.leanback.movies.AllMoviesIconBuilder;
import com.archos.mediacenter.video.leanback.movies.MoviesByAlphaActivity;
import com.archos.mediacenter.video.leanback.movies.MoviesByGenreActivity;
import com.archos.mediacenter.video.leanback.movies.MoviesByRatingActivity;
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
import com.archos.mediacenter.video.leanback.tvshow.EpisodesByDateActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowsByAlphaActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowsByGenreActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowsByRatingActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.WebUtils;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.ImportState;
import com.archos.mediaprovider.video.NetworkScannerReceiver;
import com.archos.mediascraper.AutoScrapeService;

public class MainFragment extends BrowseSupportFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "MainFragment";
    private static final String PREF_PRIVATE_MODE = "PREF_PRIVATE_MODE";

    final static int LOADER_ID_WATCHING_UP_NEXT = 47;
    final static int LOADER_ID_LAST_ADDED = 42;
    final static int LOADER_ID_LAST_PLAYED = 43;
    final static int LOADER_ID_ALL_MOVIES = 46;
    final static int LOADER_ID_ALL_TV_SHOWS = 44;
    final static int LOADER_ID_NON_SCRAPED_VIDEOS_COUNT = 45;

    final static int ROW_ID_WATCHING_UP_NEXT = 1008;
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
    private CursorObjectAdapter mWatchingUpNextAdapter;
    private CursorObjectAdapter mLastAddedAdapter;
    private CursorObjectAdapter mLastPlayedAdapter;
    private ArrayObjectAdapter mFileBrowsingRowAdapter;
    private ArrayObjectAdapter mPreferencesRowAdapter;

    private ListRow mWatchingUpNextRow;
    private ListRow mLastAddedRow;
    private ListRow mLastPlayedRow;
    private ListRow mMoviesRow;
    private ListRow mTvshowsRow;
    private ListRow mMovieRow;
    private ListRow mTvshowRow;
    
    private boolean mShowWatchingUpNextRow;
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

    private boolean mNeedBuildAllMoviesBox = false;
    private boolean mNeedBuildAllTvshowsBox = false;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
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
        mShowWatchingUpNextRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_WATCHING_UP_NEXT_ROW, VideoPreferencesCommon.SHOW_WATCHING_UP_NEXT_ROW_DEFAULT);
        mShowLastAddedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_ADDED_ROW, VideoPreferencesCommon.SHOW_LAST_ADDED_ROW_DEFAULT);
        mShowLastPlayedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_PLAYED_ROW, VideoPreferencesCommon.SHOW_LAST_PLAYED_ROW_DEFAULT);
        mShowMoviesRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_MOVIES_ROW, VideoPreferencesCommon.SHOW_ALL_MOVIES_ROW_DEFAULT);
        mMovieSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);
        mShowTvshowsRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_TV_SHOWS_ROW, VideoPreferencesCommon.SHOW_ALL_TV_SHOWS_ROW_DEFAULT);
        mTvShowSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);

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
        LoaderManager.getInstance(getActivity()).initLoader(LOADER_ID_WATCHING_UP_NEXT, null, this);
        LoaderManager.getInstance(getActivity()).initLoader(LOADER_ID_LAST_ADDED, null, this);
        LoaderManager.getInstance(getActivity()).initLoader(LOADER_ID_LAST_PLAYED, null, this);
        
        Bundle movieArgs = new Bundle();

        movieArgs.putString("sort", mMovieSortOrder);
        LoaderManager.getInstance(getActivity()).initLoader(LOADER_ID_ALL_MOVIES, movieArgs, this);
        
        Bundle tvshowArgs = new Bundle();

        tvshowArgs.putString("sort", mTvShowSortOrder);
        LoaderManager.getInstance(getActivity()).initLoader(LOADER_ID_ALL_TV_SHOWS, tvshowArgs, this);
        LoaderManager.getInstance(getActivity()).initLoader(LOADER_ID_NON_SCRAPED_VIDEOS_COUNT, null, this);
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

        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_WATCHING_UP_NEXT).startLoading();
        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_LAST_ADDED).startLoading();
        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_LAST_PLAYED).startLoading();
        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_ALL_MOVIES).startLoading();

        boolean newShowWatchingUpNextRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_WATCHING_UP_NEXT_ROW, VideoPreferencesCommon.SHOW_WATCHING_UP_NEXT_ROW_DEFAULT);

        if (newShowWatchingUpNextRow != mShowWatchingUpNextRow) {
            mShowWatchingUpNextRow = newShowWatchingUpNextRow;
            updateWatchingUpNextRow(null);
        }
        
        boolean newShowLastAddedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_ADDED_ROW, VideoPreferencesCommon.SHOW_LAST_ADDED_ROW_DEFAULT);

        if (newShowLastAddedRow != mShowLastAddedRow) {
            mShowLastAddedRow = newShowLastAddedRow;
            updateLastAddedRow(null);
        }

        boolean newShowLastPlayedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_PLAYED_ROW, VideoPreferencesCommon.SHOW_LAST_PLAYED_ROW_DEFAULT);

        if (newShowLastPlayedRow != mShowLastPlayedRow) {
            mShowLastPlayedRow = newShowLastPlayedRow;
            updateLastPlayedRow(null);
        }

        boolean newShowMoviesRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_MOVIES_ROW, VideoPreferencesCommon.SHOW_ALL_MOVIES_ROW_DEFAULT);
        
        if (newShowMoviesRow != mShowMoviesRow) {
            mShowMoviesRow = newShowMoviesRow;
            updateMoviesRow(null);
        }

        boolean newShowTvshowsRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_TV_SHOWS_ROW, VideoPreferencesCommon.SHOW_ALL_TV_SHOWS_ROW_DEFAULT);

        if (newShowTvshowsRow != mShowTvshowsRow) {
            mShowTvshowsRow = newShowTvshowsRow;
            updateTvShowsRow(null);
        }

        String newMovieSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);

        if (!newMovieSortOrder.equals(mMovieSortOrder)) {
            mMovieSortOrder = newMovieSortOrder;
            Bundle args = new Bundle();

            args.putString("sort", mMovieSortOrder);
            LoaderManager.getInstance(getActivity()).restartLoader(LOADER_ID_ALL_MOVIES, args, this);
        }

        String newTvShowSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);

        if (!newTvShowSortOrder.equals(mTvShowSortOrder)) {
            mTvShowSortOrder = newTvShowSortOrder;
            Bundle args = new Bundle();

            args.putString("sort", mTvShowSortOrder);
            LoaderManager.getInstance(getActivity()).restartLoader(LOADER_ID_ALL_TV_SHOWS, args, this);
        }

        findAndUpdatePrivateModeIcon();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
        getActivity().unregisterReceiver(mUpdateReceiver);

        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_WATCHING_UP_NEXT).stopLoading();
        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_LAST_ADDED).stopLoading();
        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_LAST_PLAYED).stopLoading();
        LoaderManager.getInstance(getActivity()).getLoader(LOADER_ID_ALL_MOVIES).stopLoading();
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

        mWatchingUpNextAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mWatchingUpNextAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mWatchingUpNextRow = new ListRow(ROW_ID_WATCHING_UP_NEXT, new HeaderItem(getString(R.string.watching_up_next)), mWatchingUpNextAdapter);

        mLastAddedAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mLastAddedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mLastAddedRow = new ListRow(ROW_ID_LAST_ADDED, new HeaderItem(getString(R.string.recently_added)), mLastAddedAdapter);

        mLastPlayedAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
        mLastPlayedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mLastPlayedRow = new ListRow(ROW_ID_LAST_PLAYED, new HeaderItem(getString(R.string.recently_played)), mLastPlayedAdapter);

        boolean showByRating = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_BY_RATING, VideoPreferencesCommon.SHOW_BY_RATING_DEFAULT);

        ArrayObjectAdapter movieRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        movieRowAdapter.add(buildAllMoviesBox());
        //movieRowAdapter.add(new Box(Box.ID.MOVIES_BY_ALPHA, getString(R.string.movies_by_alpha), R.drawable.alpha_banner));
        movieRowAdapter.add(new Box(Box.ID.MOVIES_BY_GENRE, getString(R.string.movies_by_genre), R.drawable.genres_banner));

        if (showByRating)
            movieRowAdapter.add(new Box(Box.ID.MOVIES_BY_RATING, getString(R.string.movies_by_rating), R.drawable.ratings_banner));

        movieRowAdapter.add(new Box(Box.ID.MOVIES_BY_YEAR, getString(R.string.movies_by_year), R.drawable.years_banner_2019));
        mMovieRow = new ListRow(ROW_ID_MOVIES, new HeaderItem(getString(R.string.movies)), movieRowAdapter);

        ArrayObjectAdapter tvshowRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        tvshowRowAdapter.add(buildAllTvshowsBox());
        //tvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_ALPHA, getString(R.string.tvshows_by_alpha), R.drawable.alpha_banner));
        tvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_GENRE, getString(R.string.tvshows_by_genre), R.drawable.genres_banner));

        if (showByRating)
            tvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_RATING, getString(R.string.tvshows_by_rating), R.drawable.ratings_banner));
        
        tvshowRowAdapter.add(new Box(Box.ID.EPISODES_BY_DATE, getString(R.string.episodes_by_date), R.drawable.years_banner_2019));
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

    private void updateWatchingUpNextRow(Cursor cursor) {
        if (cursor != null)
            mWatchingUpNextAdapter.changeCursor(cursor);
        else
            cursor = mWatchingUpNextAdapter.getCursor();

        int currentPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT);

        if (cursor.getCount() == 0 || !mShowWatchingUpNextRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
        }
        else {
            if (currentPosition == -1) {
                int newPosition = 0;
                
                mRowsAdapter.add(newPosition, mWatchingUpNextRow);
            }
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

                if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                
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
                else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                
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
                else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                
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
                else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                
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
                else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                
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
                else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                
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
        if (id == LOADER_ID_WATCHING_UP_NEXT) {
            return new WatchingUpNextLoader(getActivity());
        }
        else if (id == LOADER_ID_LAST_ADDED) {
            return new LastAddedLoader(getActivity());
        }
        else if (id == LOADER_ID_LAST_PLAYED) {
            return new LastPlayedLoader(getActivity());
        }
        else if (id == LOADER_ID_ALL_MOVIES) {
            if (args == null) {
                return new MoviesLoader(getActivity(), true);
            } else {
                return new MoviesLoader(getActivity(), args.getString("sort"), true, true);
            }
        }
        else if (id == LOADER_ID_ALL_TV_SHOWS) {
            if (args == null) {
                return new AllTvshowsLoader(getActivity());
            } else {
                return new AllTvshowsLoader(getActivity(), args.getString("sort"), true);
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
        if (cursorLoader.getId() == LOADER_ID_WATCHING_UP_NEXT) {
            updateWatchingUpNextRow(cursor);
            
            if (mWatchingUpNextInitFocus == InitFocus.NOT_FOCUSED)
                mWatchingUpNextInitFocus = cursor.getCount() > 0 ? InitFocus.NEED_FOCUS : InitFocus.NO_NEED_FOCUS;
        }
        else if (cursorLoader.getId() == LOADER_ID_LAST_ADDED) {
            updateLastAddedRow(cursor);
            
            if (mLastAddedInitFocus == InitFocus.NOT_FOCUSED)
                mLastAddedInitFocus = cursor.getCount() > 0 ? InitFocus.NEED_FOCUS : InitFocus.NO_NEED_FOCUS;
        }
        else if (cursorLoader.getId() == LOADER_ID_LAST_PLAYED) {
            updateLastPlayedRow(cursor);
            
            if (mLastPlayedInitFocus == InitFocus.NOT_FOCUSED)
                mLastPlayedInitFocus = cursor.getCount() > 0 ? InitFocus.NEED_FOCUS : InitFocus.NO_NEED_FOCUS;
        }
        else if (cursorLoader.getId() == LOADER_ID_ALL_MOVIES) {
            if (!mNeedBuildAllMoviesBox && isVideosListModified(mMoviesAdapter.getCursor(), cursor))
                mNeedBuildAllMoviesBox = true;
            
            boolean scanningOnGoing = NetworkScannerReceiver.isScannerWorking() || AutoScrapeService.isScraping() || ImportState.VIDEO.isInitialImport();

            if (mNeedBuildAllMoviesBox && !scanningOnGoing) {
                ((ArrayObjectAdapter)mMovieRow.getAdapter()).replace(0, buildAllMoviesBox());

                mNeedBuildAllMoviesBox = false;
            }
            
            updateMoviesRow(cursor);
        }
        else if (cursorLoader.getId() == LOADER_ID_ALL_TV_SHOWS) {
            if (!mNeedBuildAllTvshowsBox && isVideosListModified(mTvshowsAdapter.getCursor(), cursor))
                mNeedBuildAllTvshowsBox = true;
            
            boolean scanningOnGoing = NetworkScannerReceiver.isScannerWorking() || AutoScrapeService.isScraping() || ImportState.VIDEO.isInitialImport();

            if (mNeedBuildAllTvshowsBox && !scanningOnGoing) {
                ((ArrayObjectAdapter)mTvshowRow.getAdapter()).replace(0, buildAllTvshowsBox());

                mNeedBuildAllTvshowsBox = false;
            }
            
            updateTvShowsRow(cursor);
        }
        else if (cursorLoader.getId() == LOADER_ID_NON_SCRAPED_VIDEOS_COUNT) {
            updateNonScrapedVideosVisibility(cursor);
        }

        checkInitFocus();
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

    private enum InitFocus {
        NOT_FOCUSED, NO_NEED_FOCUS, NEED_FOCUS, FOCUSED
    }

    private InitFocus mWatchingUpNextInitFocus = InitFocus.NOT_FOCUSED;
    private InitFocus mLastAddedInitFocus = InitFocus.NOT_FOCUSED;
    private InitFocus mLastPlayedInitFocus = InitFocus.NOT_FOCUSED;

    private void checkInitFocus() {
        if (mWatchingUpNextInitFocus == InitFocus.NEED_FOCUS) {
            mWatchingUpNextInitFocus = InitFocus.FOCUSED;
            mLastAddedInitFocus = InitFocus.NO_NEED_FOCUS;
            mLastPlayedInitFocus = InitFocus.NO_NEED_FOCUS;
        }
        else if (mLastAddedInitFocus == InitFocus.NEED_FOCUS) {
            mLastAddedInitFocus = InitFocus.FOCUSED;
            mLastPlayedInitFocus = InitFocus.NO_NEED_FOCUS;
        }
        else if (mLastPlayedInitFocus == InitFocus.NEED_FOCUS) {
            mLastPlayedInitFocus = InitFocus.FOCUSED;
        }
        else {
            return;
        }

        setSelectedPosition(0);
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

        mFileBrowsingRowAdapter.add(new Box(Box.ID.VIDEOS_BY_LISTS, getString(R.string.video_lists), R.drawable.filetype_new_playlist));
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
                    case MOVIES_BY_ALPHA:
                        mActivity.startActivity(new Intent(mActivity, MoviesByAlphaActivity.class));
                        break;
                    case MOVIES_BY_GENRE:
                        mActivity.startActivity(new Intent(mActivity, MoviesByGenreActivity.class));
                        break;
                    case MOVIES_BY_RATING:
                        mActivity.startActivity(new Intent(mActivity, MoviesByRatingActivity.class));
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
                    case TVSHOWS_BY_RATING:
                        mActivity.startActivity(new Intent(mActivity, TvshowsByRatingActivity.class));
                        break;
                    case EPISODES_BY_DATE:
                        mActivity.startActivity(new Intent(mActivity, EpisodesByDateActivity.class));
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
