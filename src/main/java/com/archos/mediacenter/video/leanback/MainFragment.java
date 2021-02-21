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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
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
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.DensityTweak;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.browser.loader.AnimesLoader;
import com.archos.mediacenter.video.browser.loader.LastAddedLoader;
import com.archos.mediacenter.video.browser.loader.LastPlayedLoader;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.NonScrapedVideosCountLoader;
import com.archos.mediacenter.video.leanback.adapter.object.Box;
import com.archos.mediacenter.video.leanback.adapter.object.EmptyView;
import com.archos.mediacenter.video.leanback.adapter.object.Icon;
import com.archos.mediacenter.video.leanback.animes.AllAnimesGridActivity;
import com.archos.mediacenter.video.leanback.animes.AllAnimesIconBuilder;
import com.archos.mediacenter.video.leanback.animes.AnimesByGenreActivity;
import com.archos.mediacenter.video.leanback.animes.AnimesByYearActivity;
import com.archos.mediacenter.video.leanback.collections.AllCollectionsGridActivity;
import com.archos.mediacenter.video.leanback.collections.CollectionsIconBuilder;
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
import com.archos.mediacenter.video.leanback.tvshow.AllAnimeShowsGridActivity;
import com.archos.mediacenter.video.leanback.tvshow.AllAnimeShowsIconBuilder;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainFragment extends BrowseSupportFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final Logger log = LoggerFactory.getLogger(MainFragment.class);

    private static final String PREF_PRIVATE_MODE = "PREF_PRIVATE_MODE";

    final static int LOADER_ID_WATCHING_UP_NEXT = 47;
    final static int LOADER_ID_LAST_ADDED = 42;
    final static int LOADER_ID_LAST_PLAYED = 43;
    final static int LOADER_ID_ALL_MOVIES = 46;
    final static int LOADER_ID_ALL_ANIMES = 48;
    final static int LOADER_ID_ALL_TV_SHOWS = 44;
    final static int LOADER_ID_NON_SCRAPED_VIDEOS_COUNT = 45;

    final static int ROW_ID_WATCHING_UP_NEXT = 1008;
    final static int ROW_ID_LAST_ADDED = 1000;
    final static int ROW_ID_LAST_PLAYED = 1001;
    final static int ROW_ID_MOVIES = 1002;
    final static int ROW_ID_TVSHOW2 = 1003;
    final static int ROW_ID_ALL_MOVIES = 1007;
    final static int ROW_ID_ALL_ANIMES = 1010;
    final static int ROW_ID_TVSHOWS = 1004;
    final static int ROW_ID_FILES = 1005;
    final static int ROW_ID_PREFERENCES = 1006;
    final static int ROW_ID_ANIMES = 1009;

    // Need these row indexes to update the full ListRow object
    final static int ROW_INDEX_UNSET = -1;
    int rowIndexLastAdded = ROW_INDEX_UNSET;
    int rowIndexLastPlayed = ROW_INDEX_UNSET;
    int rowIndexTvShows = ROW_INDEX_UNSET;

    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mMoviesRowsAdapter;
    private ArrayObjectAdapter mAnimeRowAdapter;
    private ArrayObjectAdapter mTvshowRowAdapter;
    private CursorObjectAdapter mMoviesAdapter;
    private CursorObjectAdapter mAnimesAdapter;
    private static CursorObjectAdapter mTvshowsAdapter;
    // TODO: disabled until issue #186 is fixed
    //private CursorObjectAdapter mWatchingUpNextAdapter;
    private CursorObjectAdapter mLastAddedAdapter;
    private CursorObjectAdapter mLastPlayedAdapter;
    private ArrayObjectAdapter mFileBrowsingRowAdapter;
    private ArrayObjectAdapter mPreferencesRowAdapter;

    // TODO: disabled until issue #186 is fixed
    //private ListRow mWatchingUpNextRow;
    private ListRow mLastAddedRow;
    private ListRow mLastPlayedRow;
    private static ListRow mMoviesRow; // row for all movie posters
    private static ListRow mAnimesRow; // row for all anime posters
    private static ListRow mTvshowsRow; // row for all tvshow posters
    private static ListRow mMovieRow; // row for movie tiles
    private static ListRow mAnimeRow; // row for anime tiles
    private static ListRow mTvshowRow; // row for tvshow tiles

    private static Box mAllMoviesBox;
    private static Box mAllAnimesBox;
    private static Box mAllTvshowsBox;
    private static Box mAllCollectionsBox;
    private static Box mAllAnimeShowsBox;

    // TODO: disabled until issue #186 is fixed
    //private boolean mShowWatchingUpNextRow;
    private boolean mShowLastAddedRow;
    private boolean mShowLastPlayedRow;
    private boolean mShowMoviesRow;
    private String mMovieSortOrder;
    private boolean mShowTvshowsRow;
    private boolean mShowAnimesRow;
    private boolean mEnableSponsor;
    private String mAnimesSortOrder;
    private String mTvShowSortOrder;

    private Box mNonScrapedVideosItem;

    private SharedPreferences mPrefs;
    private Overlay mOverlay;
    private BroadcastReceiver mUpdateReceiver;
    private IntentFilter mUpdateFilter;

    private BackgroundManager bgMngr;

    private AsyncTask mBuildAllMoviesBoxTask;
    private AsyncTask mBuildAllAnimesBoxTask;
    private AsyncTask mBuildAllTvshowsBoxTask;
    private AsyncTask mBuildAllCollectionsBoxTask;
    private AsyncTask mBuildAllAnimeShowsBoxTask;

    private static Activity mActivity;

    private boolean mNeedBuildAllMoviesBox = false;
    private boolean mNeedBuildAllAnimesBox = false;
    private boolean mNeedBuildAllTvshowsBox = false;
    private boolean mNeedBuildAllCollectionsBox = false;
    private boolean mNeedBuildAllAnimeShowsBox = false;

    @Override
    public void onAttach(Context context) {
        log.debug("onAttach");
        super.onAttach(context);
        mActivity = getActivity();
        IntentFilter intentFilter = new IntentFilter(ExtStorageReceiver.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_CHANGED);
        intentFilter.addDataScheme("file");
        intentFilter.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);//new android nougat send UriExposureException when scheme = file
        mActivity.registerReceiver(mExternalStorageReceiver, intentFilter);
    }

    public void onCreate(Bundle bundle){
        log.debug("onCreate");
        super.onCreate(bundle);
        mUpdateReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent!=null&& ArchosMediaIntent.ACTION_VIDEO_SCANNER_SCAN_FINISHED.equals(intent.getAction())) {
                    // in case of usb hdd update also last played row
                    LoaderManager.getInstance(MainFragment.this).restartLoader(LOADER_ID_LAST_PLAYED, null, MainFragment.this);
                    // prepare first row to be displayed and lock on if new context after scan
                    LoaderManager.getInstance(MainFragment.this).restartLoader(LOADER_ID_LAST_ADDED, null, MainFragment.this);
                    log.debug("manual reload");
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
        mActivity.unregisterReceiver(mExternalStorageReceiver);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        log.debug("onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        // TODO: disabled until issue #186 is fixed
        //mShowWatchingUpNextRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_WATCHING_UP_NEXT_ROW, VideoPreferencesCommon.SHOW_WATCHING_UP_NEXT_ROW_DEFAULT);
        mShowLastAddedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_ADDED_ROW, VideoPreferencesCommon.SHOW_LAST_ADDED_ROW_DEFAULT);
        mShowLastPlayedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_PLAYED_ROW, VideoPreferencesCommon.SHOW_LAST_PLAYED_ROW_DEFAULT);
        mShowMoviesRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_MOVIES_ROW, VideoPreferencesCommon.SHOW_ALL_MOVIES_ROW_DEFAULT);
        mMovieSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);
        mShowTvshowsRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_TV_SHOWS_ROW, VideoPreferencesCommon.SHOW_ALL_TV_SHOWS_ROW_DEFAULT);
        mShowAnimesRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_ANIMES_ROW, VideoPreferencesCommon.SHOW_ALL_ANIMES_ROW_DEFAULT);
        mAnimesSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_ANIMES_SORT_ORDER, AnimesLoader.DEFAULT_SORT);
        mTvShowSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);

        if (mPrefs.getBoolean(PREF_PRIVATE_MODE, false) !=  PrivateMode.isActive()) {
            PrivateMode.toggle();
            findAndUpdatePrivateModeIcon();
        }

        updateBackground();

        Resources r = getResources();
        setBadgeDrawable(ContextCompat.getDrawable(mActivity, R.drawable.leanback_title));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(mActivity, R.color.leanback_side));

        // set search icon color
        setSearchAffordanceColor(ContextCompat.getColor(mActivity, R.color.lightblueA200));

        setupEventListeners();

        loadRows();
        // TODO: disabled until issue #186 is fixed
        //LoaderManager.getInstance(this).initLoader(LOADER_ID_WATCHING_UP_NEXT, null, this);
        LoaderManager.getInstance(this).initLoader(LOADER_ID_LAST_ADDED, null, this);
        LoaderManager.getInstance(this).initLoader(LOADER_ID_LAST_PLAYED, null, this);
        if (mShowMoviesRow) {
            Bundle movieArgs = new Bundle();
            movieArgs.putString("sort", mMovieSortOrder);
            LoaderManager.getInstance(this).initLoader(LOADER_ID_ALL_MOVIES, movieArgs, this);
        }
        if (mShowTvshowsRow) {
            Bundle tvshowArgs = new Bundle();
            tvshowArgs.putString("sort", mTvShowSortOrder);
            LoaderManager.getInstance(this).initLoader(LOADER_ID_ALL_TV_SHOWS, tvshowArgs, this);
        }
        LoaderManager.getInstance(this).initLoader(LOADER_ID_NON_SCRAPED_VIDEOS_COUNT, null, this);
        if (mShowAnimesRow) {
            Bundle animesArgs = new Bundle();
            animesArgs.putString("sort", mAnimesSortOrder);
            LoaderManager.getInstance(this).initLoader(LOADER_ID_ALL_ANIMES, animesArgs, this);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        log.debug("onDestroyView");
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        log.debug("onResume");
        super.onResume();
        mOverlay.resume();
        updateBackground();
        mActivity.registerReceiver(mUpdateReceiver, mUpdateFilter);
        // TODO: disabled until issue #186 is fixed
        /*
        boolean newShowWatchingUpNextRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_WATCHING_UP_NEXT_ROW, VideoPreferencesCommon.SHOW_WATCHING_UP_NEXT_ROW_DEFAULT);
        if (newShowWatchingUpNextRow != mShowWatchingUpNextRow) {
            mShowWatchingUpNextRow = newShowWatchingUpNextRow;
        }
        updateWatchingUpNextRow(null);
         */
        boolean newShowLastAddedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_ADDED_ROW, VideoPreferencesCommon.SHOW_LAST_ADDED_ROW_DEFAULT);
        if (newShowLastAddedRow != mShowLastAddedRow) {
            log.debug("onResume: preference changed, display last added row: " + newShowLastAddedRow + " -> updating");
            mShowLastAddedRow = newShowLastAddedRow;
            updateLastAddedRow(null);
        }
        boolean newShowLastPlayedRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_LAST_PLAYED_ROW, VideoPreferencesCommon.SHOW_LAST_PLAYED_ROW_DEFAULT);
        if (newShowLastPlayedRow != mShowLastPlayedRow) {
            log.debug("onResume: preference changed, display last player row: " + newShowLastPlayedRow + " -> updating");
            mShowLastPlayedRow = newShowLastPlayedRow;
            updateLastPlayedRow(null);
        }
        boolean newShowMoviesRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_MOVIES_ROW, VideoPreferencesCommon.SHOW_ALL_MOVIES_ROW_DEFAULT);
        if (newShowMoviesRow != mShowMoviesRow) {
            log.debug("onResume: preference changed, display all movies row: " + newShowMoviesRow + " -> updating");
            mShowMoviesRow = newShowMoviesRow;
            // TODO MARC: what is missing here is to relaunch loader if show

        }
        updateMoviesRow(null);
        boolean newShowTvshowsRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_TV_SHOWS_ROW, VideoPreferencesCommon.SHOW_ALL_TV_SHOWS_ROW_DEFAULT);
        if (newShowTvshowsRow != mShowTvshowsRow) {
            log.debug("onResume: preference changed, display all tv shows row: " + newShowTvshowsRow + " -> updating");
            mShowTvshowsRow = newShowTvshowsRow;
        }
        updateTvShowsRow(null);
        boolean newShowAnimesRow = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_ALL_ANIMES_ROW, VideoPreferencesCommon.SHOW_ALL_ANIMES_ROW_DEFAULT);
        if (newShowAnimesRow != mShowAnimesRow) {
            log.debug("onResume: preference changed, display all animes row: " + newShowAnimesRow + " -> updating");
            mShowAnimesRow = newShowAnimesRow;
        }
        // /!\ TODO MARC relaunch loader in one of the if if it has changed and showanimesrow and combine with the one below and it is called updateRow is called anyway on loader change
        // TODO MARC check that loader not called always!
        updateAnimesRow(null);
        String newMovieSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);
        if (mShowMoviesRow && !newMovieSortOrder.equals(mMovieSortOrder)) {
            log.debug("onResume: preference changed, showing movie row and sort order changed -> updating");
            log.debug("onResume: restart ALL_MOVIES loader");
            mMovieSortOrder = newMovieSortOrder;
            Bundle args = new Bundle();
            args.putString("sort", mMovieSortOrder);
            LoaderManager.getInstance(this).restartLoader(LOADER_ID_ALL_MOVIES, args, this);
        }
        String newTvShowSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);
        if (mShowTvshowsRow && !newTvShowSortOrder.equals(mTvShowSortOrder)) {
            log.debug("onResume: preference changed, showing tv show row and sort order changed -> updating");
            log.debug("onResume: restart ALL_TVSHOWS loader");
            mTvShowSortOrder = newTvShowSortOrder;
            Bundle args = new Bundle();
            args.putString("sort", mTvShowSortOrder);
            LoaderManager.getInstance(this).restartLoader(LOADER_ID_ALL_TV_SHOWS, args, this);
        }
        String newAnimesSortOrder = mPrefs.getString(VideoPreferencesCommon.KEY_ANIMES_SORT_ORDER, AnimesLoader.DEFAULT_SORT);
        if (mShowAnimesRow && !newAnimesSortOrder.equals(mAnimesSortOrder)) {
            log.debug("onResume: preference changed, showing animes row and sort order changed -> updating");
            log.debug("onResume: restart ALL_ANIMES loader");
            mAnimesSortOrder = newAnimesSortOrder;
            Bundle args = new Bundle();
            args.putString("sort", mAnimesSortOrder);
            LoaderManager.getInstance(this).restartLoader(LOADER_ID_ALL_ANIMES, args, this);
        }
        findAndUpdatePrivateModeIcon();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
        mActivity.unregisterReceiver(mUpdateReceiver);
    }

    private void updateBackground() {
        Resources r = getResources();

        bgMngr = BackgroundManager.getInstance(mActivity);
        if(!bgMngr.isAttached())
            bgMngr.attach(mActivity.getWindow());

        if (PrivateMode.isActive()) {
            bgMngr.setColor(ContextCompat.getColor(mActivity, R.color.private_mode));
            bgMngr.setDrawable(ContextCompat.getDrawable(mActivity, R.drawable.private_background));
        } else {
            bgMngr.setColor(ContextCompat.getColor(mActivity, R.color.leanback_background));
            bgMngr.setDrawable(new ColorDrawable(ContextCompat.getColor(mActivity, R.color.leanback_background)));
        }
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intent = new Intent(mActivity, VideoSearchActivity.class);
                intent.putExtra(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_ALL);
                startActivity(intent);
            }
        });
        setOnItemViewClickedListener(new MainViewClickedListener(mActivity));
    }

    private void loadRows() {
        log.debug("loadRows()");
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

        // TODO: disabled until issue #186 is fixed
        /*
        mWatchingUpNextAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(mActivity));
        mWatchingUpNextAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mWatchingUpNextRow = new ListRow(ROW_ID_WATCHING_UP_NEXT, new HeaderItem(getString(R.string.watching_up_next)), mWatchingUpNextAdapter);
         */

        mLastAddedAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(mActivity));
        mLastAddedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mLastAddedRow = new ListRow(ROW_ID_LAST_ADDED, new HeaderItem(getString(R.string.recently_added)), mLastAddedAdapter);

        mLastPlayedAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(mActivity));
        mLastPlayedAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        mLastPlayedRow = new ListRow(ROW_ID_LAST_PLAYED, new HeaderItem(getString(R.string.recently_played)), mLastPlayedAdapter);

        boolean showByRating = mPrefs.getBoolean(VideoPreferencesCommon.KEY_SHOW_BY_RATING, VideoPreferencesCommon.SHOW_BY_RATING_DEFAULT);

        mMoviesRowsAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        buildAllMoviesBox();
        mMoviesRowsAdapter.add(mAllMoviesBox);
        //mMoviesRowsAdapter.add(new Box(Box.ID.MOVIES_BY_ALPHA, getString(R.string.movies_by_alpha), R.drawable.alpha_banner));
        mMoviesRowsAdapter.add(new Box(Box.ID.MOVIES_BY_GENRE, getString(R.string.movies_by_genre), R.drawable.genres_banner));

        if (showByRating)
            mMoviesRowsAdapter.add(new Box(Box.ID.MOVIES_BY_RATING, getString(R.string.movies_by_rating), R.drawable.ratings_banner));

        mMoviesRowsAdapter.add(new Box(Box.ID.MOVIES_BY_YEAR, getString(R.string.movies_by_year), R.drawable.years_banner_2021));
        mMovieRow = new ListRow(ROW_ID_MOVIES, new HeaderItem(getString(R.string.movies)), mMoviesRowsAdapter);
        buildAllCollectionsBox();
        mMoviesRowsAdapter.add(mAllCollectionsBox);

        mAnimeRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        mAnimeRow = new ListRow(ROW_ID_ANIMES, new HeaderItem(getString(R.string.animes)), mAnimeRowAdapter);
        buildAllAnimesBox();
        mAnimeRowAdapter.add(mAllAnimesBox);
        mAnimeRowAdapter.add(new Box(Box.ID.ANIMES_BY_GENRE, getString(R.string.animes_by_genre), R.drawable.genres_banner));
        mAnimeRowAdapter.add(new Box(Box.ID.ANIMES_BY_YEAR, getString(R.string.animes_by_year), R.drawable.years_banner_2021));

        buildAllAnimeShowsBox();
        mAnimeRowAdapter.add(mAllAnimeShowsBox);

        mTvshowRowAdapter = new ArrayObjectAdapter(new BoxItemPresenter());
        buildAllTvshowsBox();
        mTvshowRowAdapter.add(mAllTvshowsBox);
        //tvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_ALPHA, getString(R.string.tvshows_by_alpha), R.drawable.alpha_banner));
        mTvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_GENRE, getString(R.string.tvshows_by_genre), R.drawable.genres_banner));

        if (showByRating)
            mTvshowRowAdapter.add(new Box(Box.ID.TVSHOWS_BY_RATING, getString(R.string.tvshows_by_rating), R.drawable.ratings_banner));

        mTvshowRowAdapter.add(new Box(Box.ID.EPISODES_BY_DATE, getString(R.string.episodes_by_date), R.drawable.years_banner_2021));
        mTvshowRow = new ListRow(ROW_ID_TVSHOW2, new HeaderItem(getString(R.string.all_tv_shows)), mTvshowRowAdapter);

        // TODO MARC can have this as long as there is no loader invoked
        // this is for the movies row not the movie row
        //if (mShowMoviesRow) {
            mMoviesAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(mActivity));
            mMoviesAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
            mMoviesRow = new ListRow(ROW_ID_ALL_MOVIES, new HeaderItem(getString(R.string.all_movies)), mMoviesAdapter);
        //}

        //if (mShowTvshowsRow) {
            mTvshowsAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(mActivity));
            mTvshowsAdapter.setMapper(new CompatibleCursorMapperConverter(new TvshowCursorMapper()));
            mTvshowsRow = new ListRow(ROW_ID_TVSHOWS, new HeaderItem(getString(R.string.all_tvshows)), mTvshowsAdapter);
        //}

        //if (mShowAnimesRow) {
            mAnimesAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(mActivity));
            mAnimesAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
            mAnimesRow = new ListRow(ROW_ID_ALL_ANIMES, new HeaderItem(getString(R.string.all_animes)), mAnimesAdapter);
        //}

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

        mEnableSponsor = mPrefs.getBoolean(VideoPreferencesCommon.KEY_ENABLE_SPONSOR, VideoPreferencesCommon.ENABLE_SPONSOR_DEFAULT);
        if ((! ArchosUtils.isInstalledfromPlayStore(mActivity.getApplicationContext())) || mEnableSponsor) {
            mPreferencesRowAdapter.add(new Icon(Icon.ID.SPONSOR, getString(R.string.sponsor), R.drawable.piggy_bank_leanback_256));
        }
        // Must use an IconListRow to have the dedicated presenter used (see ClassPresenterSelector above)
        mRowsAdapter.add(new IconListRow(ROW_ID_PREFERENCES,
                new HeaderItem(getString(R.string.preferences)),
                mPreferencesRowAdapter));

        setAdapter(mRowsAdapter);
    }

    private void buildAllMoviesBox() {
        log.debug("buildAllMoviesBox");
        mAllMoviesBox = new Box(Box.ID.ALL_MOVIES, getString(R.string.all_movies), R.drawable.movies_banner);
        if (mBuildAllMoviesBoxTask != null) mBuildAllMoviesBoxTask.cancel(true);
        mBuildAllMoviesBoxTask = new buildAllMoviesBoxTask().execute();
    }

    private static class buildAllMoviesBoxTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap iconBitmap = new AllMoviesIconBuilder(mActivity).buildNewBitmap();
            return iconBitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && mAllMoviesBox != null && mMovieRow != null) {
                mAllMoviesBox.setBitmap(bitmap);
                ((ArrayObjectAdapter)mMovieRow.getAdapter()).replace(0, mAllMoviesBox);
            }
        }
    }

    private void buildAllCollectionsBox() {
        log.debug("buildAllCollectionsBox");
        mAllCollectionsBox = new Box(Box.ID.COLLECTIONS, getString(R.string.movie_collections), R.drawable.movies_banner);
        if (mBuildAllCollectionsBoxTask != null) mBuildAllCollectionsBoxTask.cancel(true);
        mBuildAllCollectionsBoxTask = new buildAllCollectionsBoxTask().execute();
    }

    private static class buildAllCollectionsBoxTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap iconBitmap = new CollectionsIconBuilder(mActivity).buildNewBitmap();
            return iconBitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && mAllCollectionsBox != null && mMovieRow != null) {
                mAllCollectionsBox.setBitmap(bitmap);
                ((ArrayObjectAdapter)mMovieRow.getAdapter()).replace(3, mAllCollectionsBox);
            }
        }
    }

    private void buildAllAnimesBox() {
        log.debug("buildAllAnimesBox");
        mAllAnimesBox = new Box(Box.ID.ALL_ANIMES, getString(R.string.all_animes), R.drawable.movies_banner);
        if (mBuildAllAnimesBoxTask != null) mBuildAllAnimesBoxTask.cancel(true);
        mBuildAllAnimesBoxTask = new buildAllAnimesBoxTask().execute();
    }

    private static class buildAllAnimesBoxTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap iconBitmap = new AllAnimesIconBuilder(mActivity).buildNewBitmap();
            return iconBitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && mAllAnimesBox != null && mAnimeRow != null) {
                mAllAnimesBox.setBitmap(bitmap);
                ((ArrayObjectAdapter)mAnimeRow.getAdapter()).replace(0, mAllAnimesBox);
            }
        }
    }

    private void buildAllAnimeShowsBox() {
        log.debug("buildAllAnimesBox");
        mAllAnimeShowsBox = new Box(Box.ID.ALL_ANIMESHOWS, getString(R.string.all_animeshows), R.drawable.movies_banner);
        if (mBuildAllAnimeShowsBoxTask != null) mBuildAllAnimeShowsBoxTask.cancel(true);
        mBuildAllAnimeShowsBoxTask = new buildAllAnimeShowsBoxTask().execute();
    }

    private static class buildAllAnimeShowsBoxTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap iconBitmap = new AllAnimeShowsIconBuilder(mActivity).buildNewBitmap();
            return iconBitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && mAllAnimeShowsBox != null && mAnimeRow != null) {
                mAllAnimeShowsBox.setBitmap(bitmap);
                ((ArrayObjectAdapter)mAnimeRow.getAdapter()).replace(0, mAllAnimeShowsBox);
            }
        }
    }

    private void buildAllTvshowsBox() {
        log.debug("buildTvshowsMoviesBox");
        mAllTvshowsBox = new Box(Box.ID.ALL_TVSHOWS, getString(R.string.all_tvshows), R.drawable.movies_banner);
        if (mBuildAllTvshowsBoxTask != null) mBuildAllTvshowsBoxTask.cancel(true);
        mBuildAllTvshowsBoxTask = new buildAllTvshowsBoxTask().execute();
    }

    private static class buildAllTvshowsBoxTask extends AsyncTask<Void, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap iconBitmap = new AllTvshowsIconBuilder(mActivity).buildNewBitmap();
            return iconBitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null && mAllTvshowsBox != null && mMovieRow != null) {
                mAllTvshowsBox.setBitmap(bitmap);
                ((ArrayObjectAdapter)mTvshowRow.getAdapter()).replace(0, mAllTvshowsBox);
            }
        }
    }

    // TODO: disabled until issue #186 is fixed
    /*
    boolean isWatchingUpNextRowVisible = false;
    private void updateWatchingUpNextRow(Cursor cursor) {
        log.debug("updateWatchingUpNextRow");
        if (cursor != null) mWatchingUpNextAdapter.changeCursor(cursor);
        else cursor = mWatchingUpNextAdapter.getCursor();
        int currentPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT);
        if (cursor.getCount() == 0 || !mShowWatchingUpNextRow) {
            if (currentPosition != -1) {
                mRowsAdapter.removeItems(currentPosition, 1);
                isWatchingUpNextRowVisible = false;
            }
        }
        else {
            if (currentPosition == -1) {
                int newPosition = 0;
                mRowsAdapter.add(newPosition, mWatchingUpNextRow);
                isWatchingUpNextRowVisible = true;
            }
        }
    }
     */

    private void updateLastAddedRow(Cursor cursor) {
        log.debug("updateLastAddedRow");
        if (cursor != null) mLastAddedAdapter.changeCursor(cursor);
        else cursor = mLastAddedAdapter.getCursor();
        int currentPosition = getRowPosition(ROW_ID_LAST_ADDED);
        if (cursor.getCount() == 0 || !mShowLastAddedRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
        }
        else {
            if (currentPosition == -1) {
                int newPosition = 0;
                // TODO: disabled until issue #186 is fixed
                /*
                if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
                mRowsAdapter.add(newPosition, mLastAddedRow);
            }
        }
    }

    private void updateLastPlayedRow(Cursor cursor) {
        log.debug("updateLastPlayedRow");
        if (cursor != null) mLastPlayedAdapter.changeCursor(cursor);
        else cursor = mLastPlayedAdapter.getCursor();
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
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
                mRowsAdapter.add(newPosition, mLastPlayedRow);
            }
        }
    }

    private void updateMoviesRow(Cursor cursor) {
        log.debug("updateMoviesRow");
        if (cursor != null) mMoviesAdapter.changeCursor(cursor);
        else cursor = mMoviesAdapter.getCursor();
        // TODO MARC
        //else if (mMoviesAdapter != null) cursor = mMoviesAdapter.getCursor();
        int currentPosition = getRowPosition(ROW_ID_ALL_MOVIES);
        if ((cursor ==null || cursor.getCount() == 0) || !mShowMoviesRow) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
            if (getRowPosition(ROW_ID_MOVIES) == -1) {
                int newPosition = 0;
                if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
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
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
                mRowsAdapter.add(newPosition, mMoviesRow);
            }
        }
    }

    private void updateTvShowsRow(Cursor cursor) {
        log.debug("updateTvShowsRow");
        if (cursor != null) mTvshowsAdapter.changeCursor(cursor);
        // TODO MARC WORKS FOR MOVIES BUT NOT TVSHOWS AND ANIMES... find where init is different --> same for all
        else cursor = mTvshowsAdapter.getCursor();
        //else if (mTvshowsAdapter != null) cursor = mTvshowsAdapter.getCursor();
        int currentPosition = getRowPosition(ROW_ID_TVSHOWS);
        if ((cursor == null || cursor.getCount() == 0) || !mShowTvshowsRow) {
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
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
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
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
                mRowsAdapter.add(newPosition, mTvshowsRow);
            }
        }
    }

    private void updateAnimesRow(Cursor cursor) {
        log.debug("updateAnimesRow");
        if (cursor != null) mAnimesAdapter.changeCursor(cursor);
        //else cursor = mAnimesAdapter.getCursor();
        // TODO MARC
        else cursor = mAnimesAdapter.getCursor();
        //else if (mAnimesAdapter != null) cursor = mAnimesAdapter.getCursor();
        int currentPosition = getRowPosition(ROW_ID_ALL_ANIMES);
        if (cursor == null || cursor.getCount() == 0) {
            if (currentPosition != -1)
                mRowsAdapter.removeItems(currentPosition, 1);
            if (getRowPosition(ROW_ID_ANIMES) == -1) {
                int newPosition = 0;

                if (getRowPosition(ROW_ID_TVSHOW2) != -1)
                    newPosition = getRowPosition(ROW_ID_TVSHOW2) + 1;
                else if (getRowPosition(ROW_ID_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
                mRowsAdapter.add(newPosition, mAnimeRow);
            }
        }
        else {
            if (getRowPosition(ROW_ID_ANIMES) != -1)
                mRowsAdapter.removeItems(getRowPosition(ROW_ID_ANIMES), 1);
            if (currentPosition == -1) {
                int newPosition = 0;
                if (getRowPosition(ROW_ID_TVSHOW2) != -1)
                    newPosition = getRowPosition(ROW_ID_TVSHOW2) + 1;
                else if (getRowPosition(ROW_ID_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_ALL_MOVIES) != -1)
                    newPosition = getRowPosition(ROW_ID_ALL_MOVIES) + 1;
                else if (getRowPosition(ROW_ID_LAST_PLAYED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_PLAYED) + 1;
                else if (getRowPosition(ROW_ID_LAST_ADDED) != -1)
                    newPosition = getRowPosition(ROW_ID_LAST_ADDED) + 1;
                // TODO: disabled until issue #186 is fixed
                /* else if (getRowPosition(ROW_ID_WATCHING_UP_NEXT) != -1)
                    newPosition = getRowPosition(ROW_ID_WATCHING_UP_NEXT) + 1;
                 */
                mRowsAdapter.add(newPosition, mAnimesRow);
            }
        }
    }


    private void updateNonScrapedVideosVisibility(Cursor cursor) {
        log.debug("updateNonScrapedVideosVisibility");
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
        switch (id) {
            // TODO: disabled until issue #186 is fixed
            //case LOADER_ID_WATCHING_UP_NEXT:
            //    return new WatchingUpNextLoader(mActivity);
            case LOADER_ID_LAST_ADDED:
                log.debug("onCreateLoader LAST_ADDED");
                return new LastAddedLoader(mActivity);
            case LOADER_ID_LAST_PLAYED:
                log.debug("onCreateLoader LAST_PLAYED");
                return new LastPlayedLoader(mActivity);
            case LOADER_ID_ALL_MOVIES:
                log.debug("onCreateLoader ALL_MOVIES");
                if (args == null) return new MoviesLoader(mActivity, true);
                else return new MoviesLoader(mActivity, args.getString("sort"), true, true);
            case LOADER_ID_ALL_TV_SHOWS:
                log.debug("onCreateLoader ALL_TV_SHOWS");
                if (args == null) return new AllTvshowsLoader(mActivity);
                else return new AllTvshowsLoader(mActivity, args.getString("sort"), true);
            case LOADER_ID_ALL_ANIMES:
                log.debug("onCreateLoader ALL_ANIMES");
                if (args == null) return new AnimesLoader(mActivity, true);
                else return new AnimesLoader(mActivity, args.getString("sort"), true, true);
            case LOADER_ID_NON_SCRAPED_VIDEOS_COUNT:
                log.debug("onCreateLoader NON_SCRAPED");
                return new NonScrapedVideosCountLoader(mActivity);
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        log.debug("onLoadFinished: cursor id=" + cursorLoader.getId());
        if (mActivity == null) return;
        boolean scanningOnGoing = NetworkScannerReceiver.isScannerWorking() || AutoScrapeService.isScraping() || ImportState.VIDEO.isInitialImport();
        switch (cursorLoader.getId()) {
            // TODO: disabled until issue #186 is fixed
            /*
            case LOADER_ID_WATCHING_UP_NEXT:
                log.debug("onLoadFinished: WatchingUpNext cursor ready with " + cursor.getCount() + " entries and " + mLastAddedInitFocus + ", updating row");
                mInitWatchingUpNextCount = cursor.getCount();
                if (mWatchingUpNextInitFocus == InitFocus.NOT_FOCUSED)
                    mWatchingUpNextInitFocus = cursor.getCount() > 0 ? InitFocus.NEED_FOCUS : InitFocus.NO_NEED_FOCUS;
                updateWatchingUpNextRow(cursor);
             */
            case LOADER_ID_LAST_ADDED:
                if (mLastAddedInitFocus == InitFocus.NOT_FOCUSED)
                    mLastAddedInitFocus = cursor.getCount() > 0 ? InitFocus.NEED_FOCUS : InitFocus.NO_NEED_FOCUS;
                log.debug("onLoadFinished: LastAdded cursor ready with " + cursor.getCount() + " entries and " + mLastAddedInitFocus + ", updating row");
                updateLastAddedRow(cursor);
                // TODO MARC: should we set mNeedBuildAllMoviesBox mNeedBuildAllAnimesBox
                //  mNeedBuildAllMoviesBox mNeedBuildAllTvshowsBox and mNeedBuildAllAnimeShowsBox
                // if not scanning here? or resume enough???
                break;
            case LOADER_ID_LAST_PLAYED:
                if (mLastPlayedInitFocus == InitFocus.NOT_FOCUSED)
                    mLastPlayedInitFocus = cursor.getCount() > 0 ? InitFocus.NEED_FOCUS : InitFocus.NO_NEED_FOCUS;
                log.debug("onLoadFinished: LastPlayed cursor ready with " + cursor.getCount() + " entries and " + mLastAddedInitFocus + ", updating row");
                updateLastPlayedRow(cursor);
                break;
            case LOADER_ID_ALL_MOVIES:
                if (!mNeedBuildAllMoviesBox && isVideosListModified(mMoviesAdapter.getCursor(), cursor))
                    mNeedBuildAllMoviesBox = true;
                if (mNeedBuildAllMoviesBox && !scanningOnGoing) {
                    buildAllMoviesBox();
                    buildAllCollectionsBox();
                    mNeedBuildAllMoviesBox = false;
                }
                log.debug("onLoadFinished: AllMovies cursor ready with " + cursor.getCount() + " entries, updating row/box");
                updateMoviesRow(cursor);
                break;
            case LOADER_ID_ALL_TV_SHOWS:
                if (!mNeedBuildAllTvshowsBox && isVideosListModified(mTvshowsAdapter.getCursor(), cursor))
                    mNeedBuildAllTvshowsBox = true;
                if (mNeedBuildAllTvshowsBox && !scanningOnGoing) {
                    buildAllTvshowsBox();
                    mNeedBuildAllTvshowsBox = false;
                }
                log.debug("onLoadFinished: AllTvShows cursor ready with " + cursor.getCount() + "entries, updating row/box");
                updateTvShowsRow(cursor);
                break;
            case LOADER_ID_ALL_ANIMES:
                if (!mNeedBuildAllAnimesBox && isVideosListModified(mAnimesAdapter.getCursor(), cursor))
                    mNeedBuildAllAnimesBox = true;
                if (mNeedBuildAllAnimesBox && !scanningOnGoing) {
                    buildAllAnimesBox();
                    mNeedBuildAllAnimesBox = false;
                }
                log.debug("onLoadFinished: AllAnimes cursor ready with " + cursor.getCount() + "entries, updating row/box");
                updateAnimesRow(cursor);
                break;
            case LOADER_ID_NON_SCRAPED_VIDEOS_COUNT:
                log.debug("onLoadFinished: NonScrapedVideos cursor ready with " + cursor.getCount());
                updateNonScrapedVideosVisibility(cursor);
                break;
        }
        checkInitFocus();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) { }

    private boolean isVideosListModified(Cursor oldCursor, Cursor newCursor) {
        if ((oldCursor == null && newCursor != null) || (oldCursor != null && newCursor == null))
            return true;
        return oldCursor.getCount() != newCursor.getCount();
        // estimate
    }

    private enum InitFocus {
        NOT_FOCUSED, NO_NEED_FOCUS, NEED_FOCUS, FOCUSED
    }

    /**
     * When opening, WatchingUpNext, LastAdded and LastPlayed rows are not created yet, hence selection is on Movies.
     * Here we wait for the Loaders to return their results to know if we need to select the first row again (which will be WatchingUpNext, LastAdded or LastPlayed)
     */
    private InitFocus mWatchingUpNextInitFocus = InitFocus.NOT_FOCUSED;
    private InitFocus mLastAddedInitFocus = InitFocus.NOT_FOCUSED;
    private InitFocus mLastPlayedInitFocus = InitFocus.NOT_FOCUSED;

    private void checkInitFocus() {
        // Check if we have WatchingUpNext, LastAdded and LastPlayed loader results

        log.debug("checkInitFocus: mLastAddedInitFocus="+ mLastAddedInitFocus +
                ", mLastPlayedInitFocus="+ mLastPlayedInitFocus+
                ", mWatchingUpNextInitFocus=" + mWatchingUpNextInitFocus);

        // TODO: disabled until issue #186 is fixed
        /* if (mWatchingUpNextInitFocus == InitFocus.NEED_FOCUS) {
            log.debug("checkInitFocus: WatchingUpNext loader ready and needs focus");
            log.debug("checkInitFocus: isWatchingUpNextRowVisible=" + isWatchingUpNextRowVisible);
            if (isWatchingUpNextRowVisible) { // check if row is visible to avoid selecting network & files in case of slow row display
                mWatchingUpNextInitFocus = InitFocus.FOCUSED;
                //mLastAddedInitFocus = InitFocus.NO_NEED_FOCUS;
                //mLastPlayedInitFocus = InitFocus.NO_NEED_FOCUS;
            }
        } else */
        if (mLastAddedInitFocus == InitFocus.NEED_FOCUS) {
            log.debug("checkInitFocus: LastAdded loader ready and needs focus");
            mLastAddedInitFocus = InitFocus.FOCUSED;
            // removing for now since on miproj it causes first row not to be focused since isLastAddedRowVisible=true but in reality it is Network&Files displayed
            //mLastPlayedInitFocus = InitFocus.NO_NEED_FOCUS;
        } else if (mLastPlayedInitFocus == InitFocus.NEED_FOCUS) { // check if row is visible to avoid selecting network & files in case of slow row display
            log.debug("checkInitFocus: LastPlayed loader ready and needs focus");
            mLastPlayedInitFocus = InitFocus.FOCUSED;
        } else {
            log.debug("checkInitFocus: there was a cursor update on one that is tagged with FOCUSED OR NO_NEED_FOCUS");
            return; /// if nobody needs focus then exit
        }
        log.debug("checkInitFocus: sets focus on row 0 with animation if above rows were not visible it happens on network first");
        this.setSelectedPosition(0, true);
    }

    /**
     * Update (un)mount sdcard/usb host
     */
    private final BroadcastReceiver mExternalStorageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ExtStorageReceiver.ACTION_MEDIA_MOUNTED)){
                log.debug("mExternalStorageReceiver: ACTION_MEDIA_MOUNTED");
                // Remove "file://"
                String path = null;
                if(intent.getDataString().startsWith("file"))
                    path = intent.getDataString().substring("file".length());
                else if (intent.getDataString().startsWith(ExtStorageReceiver.ARCHOS_FILE_SCHEME))
                    path = intent.getDataString().substring(ExtStorageReceiver.ARCHOS_FILE_SCHEME.length());

                if (path == null || path.isEmpty())
                    return;
                updateUsbAndSdcardVisibility();
            }
            else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_CHANGED)){
                updateUsbAndSdcardVisibility();
            }
            else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED)){
                log.debug("mExternalStorageReceiver: ACTION_MEDIA_UNMOUNTED");
                updateUsbAndSdcardVisibility();
            }
        }
    };

    private void updateUsbAndSdcardVisibility() {
        log.debug("updateUsbAndSdcardVisibility");
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        final boolean hasExternal = storageManager.hasExtStorage();

        //TODO make it beautiful
        mFileBrowsingRowAdapter.clear();
        mFileBrowsingRowAdapter.add(new Box(Box.ID.NETWORK, getString(R.string.network_storage), R.drawable.filetype_new_server));
        mFileBrowsingRowAdapter.add(new Box(Box.ID.FOLDERS, getString(R.string.internal_storage), R.drawable.filetype_new_folder));

        if (hasExternal) {
            for(String s : storageManager.getExtSdcards()) {
                Box item = new Box(Box.ID.SDCARD, getString(R.string.sd_card_storage) + getVolumeDescription(s), R.drawable.filetype_new_sdcard, s);
                mFileBrowsingRowAdapter.add(item);
            }
            for(String s : storageManager.getExtUsbStorages()) {
                Box item = new Box(Box.ID.USB, getString(R.string.usb_host_storage) + getVolumeDescription(s), R.drawable.filetype_new_usb, s);
                mFileBrowsingRowAdapter.add(item);
            }
            for(String s : storageManager.getExtOtherStorages()) {
                Box item = new Box(Box.ID.OTHER, getString(R.string.other_storage) + getVolumeDescription(s), R.drawable.filetype_new_folder, s);
                mFileBrowsingRowAdapter.add(item);
            }
        }
        mFileBrowsingRowAdapter.add(new Box(Box.ID.VIDEOS_BY_LISTS, getString(R.string.video_lists), R.drawable.filetype_new_playlist));
    }

    private String getVolumeDescription(String s) {
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        String volLabel = storageManager.getVolumeLabel(s);
        String volDescr = storageManager.getVolumeDesc(s);
        String descr = "";
        if (volDescr != null && ! volDescr.isEmpty()) descr = volDescr;
        if (volLabel != null && ! volLabel.isEmpty())
            descr = (descr.isEmpty()) ? volLabel : volLabel + " (" + descr + ")"; // volume label is the primary if it exists otherwise volume description is the one
        if (! descr.isEmpty()) descr = ": " + descr;
        return descr;
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
                    case ALL_ANIMES:
                        mActivity.startActivity(new Intent(mActivity, AllAnimesGridActivity.class));
                        break;
                    case ANIMES_BY_GENRE:
                        mActivity.startActivity(new Intent(mActivity, AnimesByGenreActivity.class));
                        break;
                    case ANIMES_BY_YEAR:
                        mActivity.startActivity(new Intent(mActivity, AnimesByYearActivity.class));
                        break;
                    case ALL_ANIMESHOWS:
                        mActivity.startActivity(new Intent(mActivity, AllAnimeShowsGridActivity.class));
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
                    case COLLECTIONS:
                        mActivity.startActivity(new Intent(mActivity, AllCollectionsGridActivity.class));
                        break;

                }
            }
            else if (item instanceof Icon) {
                Icon icon = (Icon)item;
                switch (icon.getId()) {
                    case PREFERENCES:
                        if (mActivity instanceof MainActivityLeanback)
                            ((MainActivityLeanback)mActivity).startPreferencesActivity(); // I know this is ugly (and i'm ashamed...)
                        else
                            throw new IllegalStateException("Sorry developer, this ugly code can work with a MainActivityLeanback only for now!");
                        break;
                    case PRIVATE_MODE:
                        if (!PrivateMode.isActive() && PrivateMode.canShowDialog(mActivity))
                            PrivateMode.showDialog(mActivity);
                        PrivateMode.toggle();
                        mPrefs.edit().putBoolean(PREF_PRIVATE_MODE, PrivateMode.isActive()).apply();
                        updatePrivateMode(icon);
                        break;
                    case LEGACY_UI:
                        new DensityTweak(mActivity)
                                .temporaryRestoreDefaultDensity();
                        mActivity.startActivity(new Intent(mActivity, MainActivity.class));
                        break;
                    case HELP_FAQ:
                        WebUtils.openWebLink(mActivity,getString(R.string.faq_url));
                        break;
                    case SPONSOR:
                        WebUtils.openWebLink(mActivity,getString(R.string.sponsor_url));
                        break;
                }
            }
            else {
                super.onItemClicked(itemViewHolder, item, rowViewHolder, row);
            }
        }

    }

}
