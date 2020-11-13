// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.leanback.collections;

import android.app.Activity;
import android.app.ActivityOptions;
import androidx.loader.app.LoaderManager;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.loader.content.Loader;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsFragmentWithLessTopOffset;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import android.transition.Slide;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.loader.content.CursorLoader;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.MovieCollectionAdapter;
import com.archos.mediacenter.video.browser.adapters.mappers.CollectionCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.AllCollectionsLoader;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.browser.loader.CollectionLoader;
import com.archos.mediacenter.video.browser.loader.EpisodesLoader;
import com.archos.mediacenter.video.browser.loader.MovieCollectionLoader;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.browser.loader.TvshowLoader;
import com.archos.mediacenter.video.collections.CollectionsSortOrderEntries;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.BackdropTask;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.details.ArchosDetailsOverviewRowPresenter;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.scrapping.ManualShowScrappingActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ShowTags;
import com.squareup.picasso.Picasso;

import java.io.IOException;


public class CollectionFragment extends DetailsFragmentWithLessTopOffset implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final boolean DBG = true;
    private static final String TAG = "CollectionFragment";

    public static final String EXTRA_COLLECTION = "COLLECTION";
    public static final String EXTRA_COLLECTION_ID = "collection_id";
    public static final String SHARED_ELEMENT_NAME = "hero";

    // TODO MARC give another number for distinction
    public static final int COLLECTION_LOADER_ID = -43;

    public static final int REQUEST_CODE_MORE_DETAILS = 8574; // some random integer may be useful for grep/debug...
    public static final int REQUEST_CODE_CHANGE_TVSHOW = 8575; // some random integer may be useful for grep/debug...
    public static final int REQUEST_CODE_VIDEO = 8576;
    public static final int REQUEST_CODE_MARK_WATCHED = 8577;

    private static final int INDEX_DETAILS = 0;
    private static final int INDEX_FIRST_SEASON = 1;

    /** The collection we're displaying */
    private Collection mCollection;

    private DetailsOverviewRow mDetailsOverviewRow;
    private ArrayObjectAdapter mRowsAdapter;
    private SparseArray<CursorObjectAdapter> mMovieCollectionAdapters;
    private MovieCollectionAdapter mMovieCollectionAdapter;

    private AsyncTask mBackdropTask;
    private AsyncTask mFullScraperTagsTask;
    private AsyncTask mDetailRowBuilderTask;

    private ArchosDetailsOverviewRowPresenter mOverviewRowPresenter;
    private CollectionDetailsDescriptionPresenter mDescriptionPresenter;

    private Overlay mOverlay;
    private int mColor;
    private Handler mHandler;
    private int oldPos = 0;
    private int oldSelectedSubPosition = 0;
    private boolean mHasDetailRow;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DBG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        Object transition = TransitionHelper.getEnterTransition(getActivity().getWindow());
        if(transition!=null) {
            TransitionHelper.addTransitionListener(transition, new TransitionListener() {
                @Override
                public void onTransitionStart(Object transition) {
                    mOverlay.hide();
                }

                @Override
                public void onTransitionEnd(Object transition) {
                    mOverlay.show();
                }
            });
        }

        setTopOffsetRatio(0.6f);

        Intent intent = getActivity().getIntent();
        mCollection = (Collection) intent.getSerializableExtra(EXTRA_COLLECTION);

        if (mCollection == null) {
            long collectonId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1);

            if (collectonId != -1) {
                // CollectionLoader is a CursorLoader
                CollectionLoader collectionLoader = new CollectionLoader(getActivity(), collectonId);
                Cursor cursor = collectionLoader.loadInBackground();
                if(cursor != null && cursor.getCount()>0) {
                    if (DBG) Log.d(TAG, "onCreate" + DatabaseUtils.dumpCursorToString(cursor));
                    cursor.moveToFirst();
                    CollectionCursorMapper collectionCursorMapper = new CollectionCursorMapper();
                    collectionCursorMapper.bindColumns(cursor);
                    mCollection = (Collection) collectionCursorMapper.bind(cursor);
                }
            }
        }

        if (DBG) Log.d(TAG, "onCreate: " + mCollection.getCollectionId());

        // TODO MARC WARNING before always called FullScraperTagsTask -->> not the case
        // TODO MARC THIS HAS BEEN ADDED to load all movies related to the collection
        //LoaderManager.getInstance(CollectionFragment.this).restartLoader(COLLECTION_LOADER_ID, null, CollectionFragment.this);


        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mHandler = new Handler();
        mDescriptionPresenter = new CollectionDetailsDescriptionPresenter();
        mOverviewRowPresenter = new ArchosDetailsOverviewRowPresenter(mDescriptionPresenter);
        //be aware of a hack to avoid fullscreen overview : cf onSetRowStatus
        FullWidthDetailsOverviewSharedElementHelper helper = new FullWidthDetailsOverviewSharedElementHelper();
        helper.setSharedElementEnterTransition(getActivity(), SHARED_ELEMENT_NAME, 1000);
        mOverviewRowPresenter.setListener(helper);
        mOverviewRowPresenter.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background));
        mOverviewRowPresenter.setActionsBackgroundColor(getDarkerColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background)));
        mOverviewRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == CollectionActionAdapter.ACTION_PLAY) {
                    playMovie();
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_WATCHED) {
                    Intent intent = new Intent(getActivity(), MovieCollectionActivity.class);
                    intent.putExtra(MovieCollectionFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_ID, mCollection.getCollectionId());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_NAME, mCollection.getName());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_POSTER, mCollection.getPosterUri() != null ? mCollection.getPosterUri().toString() : null);
                    startActivityForResult(intent, REQUEST_CODE_MARK_WATCHED);
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_UNINDEX) {
                    Intent intent = new Intent(getActivity(), MovieCollectionActivity.class);
                    intent.putExtra(MovieCollectionFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_ID, mCollection.getCollectionId());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_NAME, mCollection.getName());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_POSTER, mCollection.getPosterUri() != null ? mCollection.getPosterUri().toString() : null);
                    startActivity(intent);
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_DELETE) {
                    Intent intent = new Intent(getActivity(), MovieCollectionActivity.class);
                    intent.putExtra(MovieCollectionFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_ID, mCollection.getCollectionId());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_NAME, mCollection.getName());
                    intent.putExtra(MovieCollectionFragment.EXTRA_COLLECTION_POSTER, mCollection.getPosterUri() != null ? mCollection.getPosterUri().toString() : null);
                    startActivity(intent);
                }
            }
        });

        ClassPresenterSelector ps = new ClassPresenterSelector();
        ps.addClassPresenter(DetailsOverviewRow.class, mOverviewRowPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());

        mRowsAdapter = new ArrayObjectAdapter(ps);
        mHasDetailRow = false;

        // WORKAROUND: at least one instance of BackdropTask must be created soon in the process (onCreate ?)
        // else it does not work later.
        // --> This instance of BackdropTask() will not be used but it must be created here!
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor));

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Video) {
                    //animate only if episode picture isn't displayed
                    boolean animate =!((item instanceof Video)&&((Video)item).getPosterUri()!=null);
                    VideoViewClickedListener.showVideoDetails(getActivity(), (Video) item, itemViewHolder, animate, false, false, -1, CollectionFragment.this, REQUEST_CODE_VIDEO);
}
            }
        });
    }

    // TODO MARC mSeasonAdapters -> mMovieCollectionAdapters NOPE mMovieCollectionAdapter and there is only one!, TvshowFragement -> CollectionFragment

    private void playMovie() {
        if (mMovieCollectionAdapter != null) {
            Movie resumeMovie = null;
            Movie firstMovie = null;
            int i = 0;
            while(i < mMovieCollectionAdapter.getCount() && resumeMovie == null) {
                Movie movie = (Movie)mMovieCollectionAdapter.getItem(i);
                if (movie.getResumeMs() != PlayerActivity.LAST_POSITION_END && resumeMovie == null) {
                    resumeMovie = movie;
                }
                if (firstMovie == null)
                    firstMovie = movie;
                i++;
            }
            if (resumeMovie != null)
                PlayUtils.startVideo(getActivity(), (Video)resumeMovie, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
            else if (firstMovie != null)
                PlayUtils.startVideo(getActivity(), (Video)firstMovie, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
        }
    }

    private int getDarkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f;
        return Color.HSVToColor(hsv);
    }

    //hack to avoid fullscreen overview
    @Override
    protected void onSetRowStatus(RowPresenter presenter, RowPresenter.ViewHolder viewHolder, int
            adapterPosition, int selectedPosition, int selectedSubPosition) {
        super.onSetRowStatus(presenter, viewHolder, adapterPosition, selectedPosition, selectedSubPosition);
        if(selectedPosition == 0 && selectedSubPosition != 0) {
            if (oldPos == 0 && oldSelectedSubPosition == 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setSelectedPosition(1);
                    }
                });
            } else if (oldPos == 1) {
                setSelectedPosition(1);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setSelectedPosition(0);
                    }
                });
            }
        }
        oldPos = selectedPosition;
        oldSelectedSubPosition = selectedSubPosition;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (DBG) Log.d(TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        if (DBG) Log.d(TAG, "onDestroyView");
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onStop() {
        if (DBG) Log.d(TAG, "onStop");
        mBackdropTask.cancel(true);
        if (mFullScraperTagsTask!=null) {
            mFullScraperTagsTask.cancel(true);
        }
        if (mDetailRowBuilderTask!=null) {
            mDetailRowBuilderTask.cancel(true);
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        if (DBG) Log.d(TAG, "onResume");
        super.onResume();
        mOverlay.resume();
        // Start loading the detailed info about the show if needed
        // TODO MARC no showtags issue with backdroptask again...
        /*
        if (mTvshow.getShowTags()==null) {
            mFullScraperTagsTask = new FullScraperTagsTask().execute(mTvshow);
        }
         */

        // Load the details view
        if (mDetailRowBuilderTask != null) {
            mDetailRowBuilderTask.cancel(true);
        }
        mDetailRowBuilderTask = new DetailRowBuilderTask().execute(mCollection);

        // Launch backdrop task in BaseTags-as-arguments mode
        if (mBackdropTask!=null) {
            mBackdropTask.cancel(true);
        }
        // TODO MARC WARNING issue with tags!!!
        // what we need here is the backdrop i.e. the image generated
        //mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(tvshow.getShowTags());
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mCollection);

        // Start loading the list of seasons
        LoaderManager.getInstance(CollectionFragment.this).restartLoader(COLLECTION_LOADER_ID, null, CollectionFragment.this);

        if (mBackdropTask!=null) {
            mBackdropTask.cancel(true);
        }
        //mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mTvshow.getShowTags());
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mCollection);
    }

    @Override
    public void onPause() {
        if (DBG) Log.d(TAG, "onPause");
        super.onPause();
        mOverlay.pause();
    }

    // TODO MARC HERE TO CONTINUE MODIFYING!!!

    /**
     * Getting RESULT_OK from REQUEST_CODE_MORE_DETAILS means that the poster and/or the backdrop has been changed
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) Log.d(TAG, "onActivityResult requestCode " + requestCode);
        if ((requestCode == REQUEST_CODE_MARK_WATCHED || requestCode == REQUEST_CODE_VIDEO) && resultCode == Activity.RESULT_OK) {
            if (DBG) Log.d(TAG, "onActivityResult processing requestCode");
            // CollectionLoader is a CursorLoader
            CollectionLoader collectionLoader = new CollectionLoader(getActivity(), mCollection.getCollectionId());
            Cursor cursor = collectionLoader.loadInBackground();
            if(cursor != null && cursor.getCount()>0) {
                cursor.moveToFirst();
                CollectionCursorMapper collectionCursorMapper = new CollectionCursorMapper();
                collectionCursorMapper.bindColumns(cursor);
                Collection collection = (Collection) collectionCursorMapper.bind(cursor);
                // TODO MARC should do something (change tag watched on video?)
                //collection.setShowTags(mCollection.getShowTags());
                mCollection = collection;
            }
            // sometimes mTvshow is null (tracepot)
            if (mCollection != null)
                mDetailsOverviewRow.setItem(mCollection);
        } else {
            if (DBG) Log.d(TAG, "onActivityResult NOT processing requestCode");
        }

        // TODO MARC: there is no CollectionMoreDetailsFragment there is no rescrap --> remove this logic everywhere and simplify
        /*
        if ((requestCode == REQUEST_CODE_MORE_DETAILS || requestCode == REQUEST_CODE_VIDEO) && resultCode == Activity.RESULT_OK) {
            if (DBG) Log.d(TAG, "Get RESULT_OK from TvshowMoreDetailsFragment/VideoDetailsFragment");

            // Only Poster and/or backdrop has been changed.
            // But the ShowTags must be recomputed as well.
            // The simpler is to reload everything...
            // Well for now at least, because the result is a big ugly glitch...
            for (int i = 0; i < mRowsAdapter.size(); i++) {
                if (i != INDEX_DETAILS)
                    mRowsAdapter.removeItems(i, 1);
            }

            mSeasonAdapters = null;

            if (mFullScraperTagsTask!=null) {
                mFullScraperTagsTask.cancel(true);
            }
            mFullScraperTagsTask = new FullScraperTagsTask().execute(mTvshow);
        }
        else if (requestCode == REQUEST_CODE_CHANGE_TVSHOW && resultCode == Activity.RESULT_OK) {
            if (DBG) Log.d(TAG, "Get RESULT_OK from ManualShowScrappingActivity");
            // Whole show has been changed, need to reload everything
            // First update the TvShow instance we have here with the data returned by ManualShowScrappingActivity
            String newName = data.getStringExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_NAME);
            Long newId = data.getLongExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_ID, -1);
            mTvshow = new Tvshow(newId, newName, null, mTvshow.getSeasonCount(), mTvshow.getEpisodeCount(), mTvshow.getEpisodeWatchedCount());
            // Clear all the loader managers because they need to be recreated with the new ID
            LoaderManager.getInstance(this).destroyLoader(SEASONS_LOADER_ID);
            if (mSeasonAdapters != null){
                for (int i = 0; i < mSeasonAdapters.size(); i++) {
                    LoaderManager.getInstance(this).destroyLoader(mSeasonAdapters.keyAt(i));
                }
            }
            // Clear the rows
            mRowsAdapter.removeItems(0, mRowsAdapter.size());

            mSeasonAdapters = null;
            mHasDetailRow = false;
        }
         */
    }

    private void slightlyDelayedFinish() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        }, 200);
    }

    /** Fill the ShowTags in the given TvShow instance */
    private class FullScraperTagsTask extends AsyncTask<Collection, Void, Collection> {
        // TODO MARC HERE CAN BE REMOVED BECAUSE ALREADY HAVE TAGS

        @Override
        protected Collection doInBackground(Collection... collections) {
            // TODO MARC!!!
            //mCollection.setShowTags( (ShowTags)collections[0].getFullScraperTags(getActivity()));
            return mCollection;
        }

        protected void onPostExecute(Collection collection) {
            // TODO MARC remove
            /*
            if (tvshow.getShowTags()==null) {
                Log.e(TAG, "FullScraperTagsTask failed to get ShowTags for "+mTvshow);
                return;
            }
             */
            // Load the details view
            if (mDetailRowBuilderTask != null) {
                mDetailRowBuilderTask.cancel(true);
            }
            mDetailRowBuilderTask = new DetailRowBuilderTask().execute(collection);

            // Launch backdrop task in BaseTags-as-arguments mode
            if (mBackdropTask!=null) {
                mBackdropTask.cancel(true);
            }
            // TODO MARC WARNING issue with tags!!!
            // what we need here is the backdrop i.e. the image generated
            //mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(tvshow.getShowTags());
            mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(collection);

            // Start loading the list of seasons
            //LoaderManager.getInstance(CollectionFragment.this).restartLoader(COLLECTION_LOADER_ID, null, CollectionFragment.this);
        }
    }

    //--------------------------------------------

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        if (DBG) {
            Log.d(TAG, "onCreateLoader: dump bundle");
            // TODO MARC DEBUG
            if (bundle != null) {
                for (String key : bundle.keySet()) {
                    Log.e(TAG, key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
                }
            }
        }
        return new MovieCollectionLoader(getActivity(), mCollection.getCollectionId());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        // TODO MARC DEBUG
        if (DBG) Log.d(TAG, "onLoadFinished" + DatabaseUtils.dumpCursorToString(cursor));
        if (getActivity() == null) return;
        /*
        if (cursorLoader.getId()==COLLECTION_LOADER_ID) {
            final int seasonNumberColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);

            SparseArray<CursorObjectAdapter> seasonAdapters = new SparseArray<CursorObjectAdapter>();
            int i = 0;

            cursor.moveToFirst();

            // Build one row for each season
            while (!cursor.isAfterLast()) {
                int seasonNumber = cursor.getInt(seasonNumberColumn);
                CursorObjectAdapter seasonAdapter;

                if (mSeasonAdapters != null && mSeasonAdapters.get(seasonNumber) != null) {
                    seasonAdapter = mSeasonAdapters.get(seasonNumber);
                }
                else {
                    seasonAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity(), PosterImageCardPresenter.EpisodeDisplayMode.FOR_SEASON_LIST));
                    seasonAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
                }

                seasonAdapters.put(seasonNumber, seasonAdapter);

                ListRow row = new ListRow(seasonNumber,
                        new HeaderItem(seasonNumber, seasonNumber != 0 ? getString(R.string.episode_season) + " " + seasonNumber : getString(R.string.episode_specials)),
                        seasonAdapter);
                
                if (mHasDetailRow && i == INDEX_DETAILS)
                    i++;

                if (i >= mRowsAdapter.size())
                    mRowsAdapter.add(row);
                else if (row.getId() != ((ListRow)mRowsAdapter.get(i)).getId())
                    mRowsAdapter.replace(i, row);

                i++;

                cursor.moveToNext();
            }

            mSeasonAdapters = seasonAdapters;

            for (int j = i; j < mRowsAdapter.size(); j++) {
                if (!mHasDetailRow || (mHasDetailRow && j != INDEX_DETAILS))
                    mRowsAdapter.removeItems(j, 1);
            }

            if (mSeasonAdapters.size() == 0) {
                slightlyDelayedFinish();
            }
            else {
                for (int k = 0; k < mSeasonAdapters.size(); k++)
                    LoaderManager.getInstance(this).restartLoader(mSeasonAdapters.keyAt(k), null, this);
            }  
        }
        else {
         */
        /*
            // We got the list of movies for one collection, load it
            if (mMovieCollectionAdapter != null) {
                MovieCollectionAdapter collectionAdapter = mMovieCollectionAdapter;
                // TODO MARC remove this should be not an array but just one
                //CursorObjectAdapter seasonAdapter = mSeasonAdapters.get(cursorLoader.getId());

                if (collectionAdapter != null)
                    collectionAdapter.changeCursor(cursor);
                else
                    LoaderManager.getInstance(this).destroyLoader(cursorLoader.getId());
            }
         */

        CursorObjectAdapter movieCollectionAdapter;
        movieCollectionAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity(), PosterImageCardPresenter.EpisodeDisplayMode.FOR_SEASON_LIST));
        movieCollectionAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));

        ListRow row = new ListRow(0,
                new HeaderItem(0, getString(R.string.movies)),
                movieCollectionAdapter);
        mRowsAdapter.add(row);

        //TODO WARNING MARC: todo???
        //LoaderManager.getInstance(this).restartLoader(COLLECTION_LOADER_ID, null, this);

        movieCollectionAdapter.changeCursor(cursor);

        /*
        if (mMovieCollectionAdapter != null) {
            if (DBG) Log.d(TAG, "onLoadFinished changing mMovieCollectionAdapter to cursor");
            mMovieCollectionAdapter.changeCursor(cursor);
        } else {
            if (DBG) Log.d(TAG, "onLoadFinished destroyLoader!!!");
            LoaderManager.getInstance(this).destroyLoader(cursorLoader.getId());
        }
         */

    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }

    //--------------------------------------------

    private class DetailRowBuilderTask extends AsyncTask<Collection, Void, Pair<Collection, Bitmap>> {

        @Override
        protected Pair<Collection, Bitmap> doInBackground(Collection... collections) {
            Collection collection = collections[0];

            Bitmap bitmap = null;
            try {
                // TODO MARC WARNING remove this comment!!!
                // Poster: we must take the poster from the showTags because they are updated (in case they have
                // been changed in TvshowMoreDetailsFragment) while the Tvshow instance has not been updated.
                Uri posterUri = collection.getPosterUri(); // fallback

                if (posterUri != null) {
                    bitmap = Picasso.get()
                            .load(posterUri)
                            .noFade() // no fade since we are using activity transition anyway
                            // TODO MARC no resize?
                            .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                            .centerCrop()
                            .get();
                    if (DBG) Log.d("XXX", "------ "+bitmap.getWidth()+"x"+bitmap.getHeight()+" ---- "+posterUri);
                }
            }
            catch (IOException e) {
                Log.d(TAG, "DetailsOverviewRow Picasso load exception", e);
            }
            catch (NullPointerException e) { // getDefaultPoster() may return null (seen once at least)
                Log.d(TAG, "DetailsOverviewRow doInBackground exception", e);
            }
            finally {
                if (bitmap!=null) {
                    Palette palette = Palette.from(bitmap).generate();
                    if (palette.getDarkVibrantSwatch() != null)
                        mColor = palette.getDarkVibrantSwatch().getRgb();
                    else if (palette.getDarkMutedSwatch() != null)
                        mColor = palette.getDarkMutedSwatch().getRgb();
                    else
                        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                }
            }

            return new Pair<>(collection, bitmap);
        }

        @Override
        protected void onPostExecute(Pair<Collection, Bitmap> result) {
            Collection collection = result.first;
            Bitmap bitmap = result.second;

            // Buttons
            if (mDetailsOverviewRow == null) {
                mDetailsOverviewRow = new DetailsOverviewRow(collection);
                mDetailsOverviewRow.setActionsAdapter(new CollectionActionAdapter(getActivity(), collection));
            }
            else {
                mDetailsOverviewRow.setItem(collection);
            }

            if (bitmap!=null) {
                mOverviewRowPresenter.updateBackgroundColor(mColor);
                mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));
                mDetailsOverviewRow.setImageBitmap(getActivity(), bitmap);
                mDetailsOverviewRow.setImageScaleUpAllowed(true);
            }
            else {
                mDetailsOverviewRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video));
                mDetailsOverviewRow.setImageScaleUpAllowed(false);
            }

            if (!mHasDetailRow) {
                BackgroundManager.getInstance(getActivity()).setDrawable(new ColorDrawable(VideoInfoCommonClass.getDarkerColor(mColor)));
                mRowsAdapter.add(INDEX_DETAILS, mDetailsOverviewRow);
            
                setAdapter(mRowsAdapter);

                mHasDetailRow = true;
            }
        }
    }

    public void onKeyDown(int keyCode) {
        int direction = -1;

        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                setSelectedPosition(0);
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                playMovie();
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                direction = Gravity.RIGHT;
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                direction = Gravity.LEFT;
                break;
        }

        if (direction != -1) {
            CursorLoader loader = null;
            if (mCollection != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String sortOrder = prefs.getString(AllCollectionsGridFragment.SORT_PARAM_KEY, CollectionsSortOrderEntries.DEFAULT_SORT);
                boolean showWatched = prefs.getBoolean(AllCollectionsGridFragment.COLLECTION_WATCHED_KEY, true);
                loader = new AllCollectionsLoader(getActivity(), sortOrder, showWatched);
            }
            if (loader != null) {
                // Using a CursorLoader but outside of the LoaderManager : need to make sure the Looper is ready
                if (Looper.myLooper()==null) Looper.prepare();
                Cursor c = loader.loadInBackground();
                Collection collection = null;
                for (int i = 0; i < c.getCount(); i++) {
                    c.moveToPosition(i);
                    Collection mc = (Collection)new CompatibleCursorMapperConverter(new CollectionCursorMapper()).convert(c);
                    if (mc.getCollectionId() == mCollection.getCollectionId()) {
                        if (direction == Gravity.LEFT) {
                            if (i - 1 >= 0)
                                c.moveToPosition(i - 1);
                            else
                                c.moveToPosition(c.getCount() - 1);
                        }
                        else if (direction == Gravity.RIGHT) {
                            if (i + 1 <= c.getCount() - 1)
                                c.moveToPosition(i + 1);
                            else
                                c.moveToPosition(0);
                        }
                        Collection nc = (Collection)new CompatibleCursorMapperConverter(new CollectionCursorMapper()).convert(c);
                        if (nc.getCollectionId() != mc.getCollectionId())
                            collection = nc;
                        break;
                    }
                }
                c.close();
                if (collection != null) {
                    if (direction == Gravity.LEFT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.RIGHT));
                    else if (direction == Gravity.RIGHT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.LEFT));
                    final Intent intent = new Intent(getActivity(), CollectionActivity.class);
                    intent.putExtra(CollectionFragment.EXTRA_COLLECTION, collection);
                    intent.putExtra(CollectionActivity.SLIDE_TRANSITION_EXTRA, true);
                    intent.putExtra(CollectionActivity.SLIDE_DIRECTION_EXTRA, direction);
                    // Launch next activity with slide animation
                    // Starting from lollipop we need to give an empty "SceneTransitionAnimation" for this to work
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mOverlay.hide(); // hide the top-right overlay else it slides across the screen!
                        startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle());
                    } else {
                        startActivity(intent);
                    }
                    // Delay the finish the "old" activity, else it breaks the animation
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            if (getActivity()!=null) // better safe than sorry
                                getActivity().finish();
                        }
                    }, 1000);
                }
            }
        }
    }
}
