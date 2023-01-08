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

package com.archos.mediacenter.video.leanback.tvshow;

import android.app.Activity;
import android.app.ActivityOptions;
import androidx.loader.app.LoaderManager;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.loader.content.Loader;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
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
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.browser.loader.EpisodesLoader;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.browser.loader.TvshowLoader;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.BackdropTask;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.details.ArchosDetailsOverviewRowPresenter;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.PresenterUtils;
import com.archos.mediacenter.video.leanback.scrapping.ManualShowScrappingActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ShowTags;
import com.squareup.picasso.Picasso;

import java.io.IOException;

public class TvshowFragment extends DetailsFragmentWithLessTopOffset implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final boolean DBG = false;
    private static final String TAG = "TvshowFragment";

    public static final String EXTRA_TVSHOW = "TVSHOW";
    public static final String EXTRA_TV_SHOW_ID = "tv_show_id";
    public static final String SHARED_ELEMENT_NAME = "hero";

    public static final int SEASONS_LOADER_ID = -42;

    public static final int REQUEST_CODE_MORE_DETAILS = 8574; // some random integer may be useful for grep/debug...
    public static final int REQUEST_CODE_CHANGE_TVSHOW = 8575; // some random integer may be useful for grep/debug...
    public static final int REQUEST_CODE_VIDEO = 8576;
    public static final int REQUEST_CODE_MARK_WATCHED = 8577;

    private static final int INDEX_DETAILS = 0;
    private static final int INDEX_FIRST_SEASON = 1;

    /** The show we're displaying */
    private Tvshow mTvshow;

    private DetailsOverviewRow mDetailsOverviewRow;
    private ArrayObjectAdapter mRowsAdapter;
    private SparseArray<CursorObjectAdapter> mSeasonAdapters;

    private AsyncTask mBackdropTask;
    private AsyncTask mFullScraperTagsTask;
    private AsyncTask mDetailRowBuilderTask;
    private AsyncTask mRefreshTvshowBitmapTask;

    private ArchosDetailsOverviewRowPresenter mOverviewRowPresenter;
    private TvshowDetailsDescriptionPresenter mDescriptionPresenter;

    private Overlay mOverlay;
    private int mColor;
    private static int dominantColor = 0;
    private Handler mHandler;
    private int oldPos = 0;
    private int oldSelectedSubPosition = 0;
    private boolean mHasDetailRow;

    private void setmTvshow(long id) {
        if (DBG) Log.d(TAG, "setTvshow: for id=" + id);
        if (id != -1) {
            // TvshowLoader is a CursorLoader
            TvshowLoader tvshowLoader = new TvshowLoader(getActivity(), id);
            Cursor cursor = tvshowLoader.loadInBackground();
            if(cursor != null && cursor.getCount()>0) {
                cursor.moveToFirst();
                TvshowCursorMapper tvshowCursorMapper = new TvshowCursorMapper();
                tvshowCursorMapper.bindColumns(cursor);
                mTvshow = (Tvshow) tvshowCursorMapper.bind(cursor);
                if (DBG) Log.d(TAG, "setTvshow: poster is " + mTvshow.getPosterUri());
                cursor.close();
            }
        } else {
            Log.w(TAG, "setTvshow not done!");
        }
    }

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
        mTvshow = (Tvshow) intent.getSerializableExtra(EXTRA_TVSHOW);

        if (mTvshow == null) {
            long tvShowId = intent.getLongExtra(EXTRA_TV_SHOW_ID, -1);
            if (DBG) Log.d(TAG, "onCreate: tvShowId=" + tvShowId);
            setmTvshow(tvShowId);
        }

        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mHandler = new Handler();
        mDescriptionPresenter = new TvshowDetailsDescriptionPresenter();
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
                if (action.getId() == TvshowActionAdapter.ACTION_PLAY) {
                    playEpisode();
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_MORE_DETAILS) {
                    Intent intent = new Intent(getActivity(), TvshowMoreDetailsActivity.class);
                    intent.putExtra(TvshowMoreDetailsFragment.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
                    intent.putExtra(TvshowMoreDetailsFragment.EXTRA_TVSHOW_WATCHED, mTvshow.isWatched());
                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            getView().findViewById(R.id.details_overview_image),
                            TvshowMoreDetailsFragment.SHARED_ELEMENT_NAME).toBundle();
                    startActivityForResult(intent, REQUEST_CODE_MORE_DETAILS, bundle);
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED) {
                    Intent intent = new Intent(getActivity(), SeasonActivity.class);
                    intent.putExtra(SeasonFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_NAME, mTvshow.getName());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_POSTER, mTvshow.getPosterUri() != null ? mTvshow.getPosterUri().toString() : null);
                    startActivityForResult(intent, REQUEST_CODE_MARK_WATCHED);
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_UNINDEX) {
                    Intent intent = new Intent(getActivity(), SeasonActivity.class);
                    intent.putExtra(SeasonFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_NAME, mTvshow.getName());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_POSTER, mTvshow.getPosterUri() != null ? mTvshow.getPosterUri().toString() : null);
                    startActivity(intent);
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_CHANGE_INFO) {
                    if (!NetworkState.isNetworkConnected(getActivity())) {
                        Toast.makeText(getActivity(), R.string.scrap_no_network, Toast.LENGTH_SHORT).show();
                    } else {
                        Intent intent = new Intent(getActivity(), ManualShowScrappingActivity.class);
                        intent.putExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_NAME, mTvshow.getName());
                        intent.putExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
                        startActivityForResult(intent, REQUEST_CODE_CHANGE_TVSHOW);
                    }
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_DELETE) {
                    Intent intent = new Intent(getActivity(), SeasonActivity.class);
                    intent.putExtra(SeasonFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_NAME, mTvshow.getName());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_POSTER, mTvshow.getPosterUri() != null ? mTvshow.getPosterUri().toString() : null);
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
                    boolean animate =!((item instanceof Episode)&&((Episode)item).getPictureUri()!=null);
                    VideoViewClickedListener.showVideoDetails(getActivity(), (Video) item, itemViewHolder, animate, false, false, -1, TvshowFragment.this, REQUEST_CODE_VIDEO);
                }
            }
        });
    }

    private void playEpisode() {
        if (mSeasonAdapters != null) {
            Episode resumeEpisode = null;
            Episode firstEpisode = null;
            int i = 0;

            while(i < mSeasonAdapters.size() && (resumeEpisode == null || resumeEpisode != null && resumeEpisode.getSeasonNumber() == 0)) {
                CursorObjectAdapter seasonAdapter = mSeasonAdapters.valueAt(i);
                int j = 0;

                while (j < seasonAdapter.size() && (resumeEpisode == null || resumeEpisode != null && resumeEpisode.getSeasonNumber() == 0)) {
                    Episode episode = (Episode)seasonAdapter.get(j);

                    if (episode.getResumeMs() != PlayerActivity.LAST_POSITION_END
                            && (resumeEpisode == null || resumeEpisode != null && episode.getEpisodeDate() < resumeEpisode.getEpisodeDate())) {
                        resumeEpisode = episode;
                    }

                    if (firstEpisode == null || (firstEpisode != null && firstEpisode.getSeasonNumber() == 0 && episode.getEpisodeDate() < firstEpisode.getEpisodeDate()))
                        firstEpisode = episode;

                    j++;
                }

                i++;
            }

            if (resumeEpisode != null)
                PlayUtils.startVideo(getActivity(), (Video)resumeEpisode, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
            else if (firstEpisode != null)
                PlayUtils.startVideo(getActivity(), (Video)firstEpisode, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
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
        if (mTvshow.getShowTags()==null) {
            if (DBG) Log.d(TAG, "onResume: mTvshow.getShowTags()==null -> FullScraperTagsTask");
            mFullScraperTagsTask = new FullScraperTagsTask().execute(mTvshow);
        }
        if (mBackdropTask!=null) {
            if (DBG) Log.d(TAG, "onResume: mBackdropTask!=null -> cancel");
            mBackdropTask.cancel(true);
        }
        if (DBG) Log.d(TAG, "onResume: new backdropTask");
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mTvshow.getShowTags());
    }

    @Override
    public void onPause() {
        if (DBG) Log.d(TAG, "onPause");
        super.onPause();
        mOverlay.pause();
    }

    /**
     * Getting RESULT_OK from REQUEST_CODE_MORE_DETAILS means that the poster and/or the backdrop has been changed
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_CODE_MARK_WATCHED || requestCode == REQUEST_CODE_VIDEO) && resultCode == Activity.RESULT_OK) {
            // TvshowLoader is a CursorLoader
            TvshowLoader tvshowLoader = new TvshowLoader(getActivity(), mTvshow.getTvshowId());
            Cursor cursor = tvshowLoader.loadInBackground();
            if(cursor != null && cursor.getCount()>0) {
                cursor.moveToFirst();
                TvshowCursorMapper tvshowCursorMapper = new TvshowCursorMapper();
                tvshowCursorMapper.bindColumns(cursor);
                Tvshow tvshow = (Tvshow) tvshowCursorMapper.bind(cursor);
                tvshow.setShowTags(mTvshow.getShowTags());
                mTvshow = tvshow;
                if (mRefreshTvshowBitmapTask != null) mRefreshTvshowBitmapTask.cancel(true);
                mRefreshTvshowBitmapTask = new RefreshTvshowBitmapTask().execute(mTvshow);
                refreshActivity();
                cursor.close();
            }
            // sometimes mTvshow is null (tracepot)
            if (mTvshow != null)
                mDetailsOverviewRow.setItem(mTvshow);
        }
        if ((requestCode == REQUEST_CODE_MORE_DETAILS || requestCode == REQUEST_CODE_VIDEO) && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "onActivityResult: got RESULT_OK from TvshowMoreDetailsFragment/VideoDetailsFragment");

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
            Log.d(TAG, "onActivityResult: got RESULT_OK from ManualShowScrappingActivity");
            // Whole show has been changed, need to reload everything
            // First update the TvShow instance we have here with the data returned by ManualShowScrappingActivity
            String newName = data.getStringExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_NAME);
            Long newId = data.getLongExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_ID, -1);

            if (DBG) Log.d(TAG, "onActivityResult: newName=" + newName + ", newId=" + newId);
            // doing this assumes same number of seasons/episodes and results in null getPosterUri...
            //mTvshow = new Tvshow(newId, newName, null, mTvshow.getSeasonCount(), mTvshow.getEpisodeCount(), mTvshow.getEpisodeWatchedCount());
            setmTvshow(newId);

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
    }

    private void slightlyDelayedFinish() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity activity = getActivity();
                if (activity != null) activity.finish(); // better safe than sorry
            }
        }, 200);
    }

    /** Fill the ShowTags in the given TvShow instance */
    private class FullScraperTagsTask extends AsyncTask<Tvshow, Void, Tvshow> {

        @Override
        protected Tvshow doInBackground(Tvshow... tvshows) {
            mTvshow.setShowTags( (ShowTags)tvshows[0].getFullScraperTags(getActivity()));
            if (DBG) Log.d(TAG, "FullScraperTagsTask:doInBackground:" + (mTvshow != null ? mTvshow.getName() + " " + mTvshow.getPosterUri(): "null"));
            return mTvshow;
        }

        protected void onPostExecute(Tvshow tvshow) {
            if (DBG) Log.d(TAG, "FullScraperTagsTask:onPostExecute:" + (tvshow != null ? tvshow.getName() + " " + tvshow.getPosterUri(): "null") + ", rebuild and restart loader");

            if (tvshow.getShowTags()==null) {
                Log.e(TAG, "FullScraperTagsTask failed to get ShowTags for "+mTvshow);
                return;
            }
            // Load the details view
            if (mDetailRowBuilderTask != null) {
                mDetailRowBuilderTask.cancel(true);
            }
            mDetailRowBuilderTask = new DetailRowBuilderTask().execute(tvshow);

            // Launch backdrop task in BaseTags-as-arguments mode
            if (mBackdropTask!=null) {
                mBackdropTask.cancel(true);
            }
            mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(tvshow.getShowTags());

            // Start loading the list of seasons
            LoaderManager.getInstance(TvshowFragment.this).restartLoader(SEASONS_LOADER_ID, null, TvshowFragment.this);
        }
    }

    //--------------------------------------------

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        if (DBG) Log.d(TAG, "onCreateLoader id=" + id);
        if (id == SEASONS_LOADER_ID) {
            return new SeasonsLoader(getActivity(), mTvshow.getTvshowId());
        } else {
            // The season number is put in the id argument
            return new EpisodesLoader(getActivity(), mTvshow.getTvshowId(), id, true);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (DBG) Log.d(TAG, "onLoadFinished");
        if (getActivity() == null) return;
        if (cursorLoader.getId()==SEASONS_LOADER_ID) {
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
            // We got the list of episode for one season, load it
            if (mSeasonAdapters != null) {
                CursorObjectAdapter seasonAdapter = mSeasonAdapters.get(cursorLoader.getId());

                if (seasonAdapter != null)
                    seasonAdapter.changeCursor(cursor);
                else
                    LoaderManager.getInstance(this).destroyLoader(cursorLoader.getId());
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }

    //--------------------------------------------

    private class DetailRowBuilderTask extends AsyncTask<Tvshow, Void, Pair<Tvshow, Bitmap>> {

        @Override
        protected Pair<Tvshow, Bitmap> doInBackground(Tvshow... shows) {
            Tvshow tvshow = shows[0];
            if (DBG) Log.d(TAG, "DetailRowBuilderTask: tvshow posterUri " + tvshow.getPosterUri());
            Bitmap bitmap = generateTvshowBitmap(tvshow.getPosterUri(), tvshow.isWatched());
            return new Pair<>(tvshow, bitmap);
        }

        @Override
        protected void onPostExecute(Pair<Tvshow, Bitmap> result) {
            Tvshow tvshow = result.first;
            Bitmap bitmap = result.second;

            if (DBG) Log.d(TAG, "DetailRowBuilderTask:onPostExecute: tvshow " + tvshow.getName());

            // Buttons
            if (mDetailsOverviewRow == null) {
                mDetailsOverviewRow = new DetailsOverviewRow(tvshow);
                mDetailsOverviewRow.setActionsAdapter(new TvshowActionAdapter(getActivity(), tvshow));
            }
            else {
                mDetailsOverviewRow.setItem(tvshow);
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
                playEpisode();
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
            if (mTvshow != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String sortOrder = prefs.getString(AllTvshowsGridFragment.SORT_PARAM_KEY, TvshowSortOrderEntries.DEFAULT_SORT);
                boolean showWatched = prefs.getBoolean(AllTvshowsGridFragment.SHOW_WATCHED_KEY, true);
                loader = new AllTvshowsLoader(getActivity(), sortOrder, showWatched);
            }
            if (loader != null) {
                // Using a CursorLoader but outside of the LoaderManager : need to make sure the Looper is ready
                if (Looper.myLooper()==null) Looper.prepare();
                Cursor c = loader.loadInBackground();
                Tvshow tvshow = null;
                for (int i = 0; i < c.getCount(); i++) {
                    c.moveToPosition(i);
                    Tvshow t = (Tvshow)new CompatibleCursorMapperConverter(new TvshowCursorMapper()).convert(c);
                    if (t.getTvshowId() == mTvshow.getTvshowId()) {
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
                        Tvshow nt = (Tvshow)new CompatibleCursorMapperConverter(new TvshowCursorMapper()).convert(c);
                        if (nt.getTvshowId() != t.getTvshowId())
                            tvshow = nt;
                        break;
                    }
                }
                c.close();
                if (tvshow != null) {
                    if (direction == Gravity.LEFT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.RIGHT));
                    else if (direction == Gravity.RIGHT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.LEFT));
                    final Intent intent = new Intent(getActivity(), TvshowActivity.class);
                    intent.putExtra(TvshowFragment.EXTRA_TVSHOW, tvshow);
                    intent.putExtra(TvshowActivity.SLIDE_TRANSITION_EXTRA, true);
                    intent.putExtra(TvshowActivity.SLIDE_DIRECTION_EXTRA, direction);
                    // Launch next activity with slide animation
                    // Starting from lollipop we need to give an empty "SceneTransitionAnimation" for this to work
                    mOverlay.hide(); // hide the top-right overlay else it slides across the screen!
                    startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle());
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

    private void refreshActivity() {
        if (mTvshow != null) {
            if (DBG) Log.d(TAG, "refreshActivity: collection is not empty " + mTvshow.getName());
            ((TvshowActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(mTvshow);
            mDetailsOverviewRow.setItem(mTvshow);
        } else {
            if (DBG) Log.d(TAG, "refreshActivity: collection is null exit!");
            getActivity().finish();
        }
    }

    private Bitmap generateTvshowBitmap(Uri posterUri, boolean isWatched) {
        if (DBG) Log.d(TAG, "generateTvshowBitmap: posterUri=" + posterUri);
        Bitmap bitmap = null;
        try {
            if (posterUri != null) {
                bitmap = Picasso.get()
                        .load(posterUri)
                        .noFade() // no fade since we are using activity transition anyway
                        .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                        .centerCrop()
                        .get();
                if (DBG) Log.d(TAG, "------ "+bitmap.getWidth()+"x"+bitmap.getHeight()+" ---- "+posterUri);
            }
        } catch (IOException e) {
            Log.d(TAG, "generateTvshowBitmap Picasso load exception", e);
        } catch (NullPointerException e) { // getDefaultPoster() may return null (seen once at least)
            Log.d(TAG, "generateTvshowBitmap doInBackground exception", e);
        } finally {
            if (bitmap!=null) {
                Palette palette = Palette.from(bitmap).generate();
                if (palette.getDarkVibrantSwatch() != null)
                    mColor = palette.getDarkVibrantSwatch().getRgb();
                else if (palette.getDarkMutedSwatch() != null)
                    mColor = palette.getDarkMutedSwatch().getRgb();
                else
                    mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                dominantColor = mColor;
                if (isWatched)
                    bitmap = PresenterUtils.addWatchedMark(bitmap, getContext());
            }
        }
        return bitmap;
    }

    private class RefreshTvshowBitmapTask extends AsyncTask<Tvshow, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Tvshow... tvshows) {
            Tvshow tvshow = tvshows[0];
            if (DBG) Log.d(TAG, "RefreshTvshowBitmapTask.doInBackground tvshow " + tvshow.getName());
            Bitmap bitmap = generateTvshowBitmap(tvshow.getPosterUri(), tvshow.isWatched());
            return bitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap!=null) {
                mOverviewRowPresenter.updateBackgroundColor(mColor);
                mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));
                mDetailsOverviewRow.setImageBitmap(getActivity(), bitmap);
                mDetailsOverviewRow.setImageScaleUpAllowed(true);
                if (mHasDetailRow) {
                    mRowsAdapter.replace(INDEX_DETAILS, mDetailsOverviewRow);
                    setAdapter(mRowsAdapter);
                }
            }
        }
    }

    public static int getDominantColor() {
        return dominantColor;
    }
}
