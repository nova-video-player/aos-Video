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
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragmentWithLessTopOffset;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Toast;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.EpisodesLoader;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.BackdropTask;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.scrapping.ManualShowScrappingActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ShowTags;
import com.squareup.picasso.Picasso;

import java.io.IOException;


public class TvshowFragment extends DetailsFragmentWithLessTopOffset implements  LoaderManager.LoaderCallbacks<Cursor> {

    private static final boolean DBG = false;
    private static final String TAG = "TvshowFragment";

    public static final String EXTRA_TVSHOW = "TVSHOW";
    public static final String SHARED_ELEMENT_NAME = "hero";

    public static final int SEASONS_LOADER_ID = -42;

    public static final int REQUEST_CODE_MORE_DETAILS = 8574; // some random integer may be useful for grep/debug...
    public static final int REQUEST_CODE_CHANGE_TVSHOW = 8575; // some random integer may be useful for grep/debug...
    public static final int REQUEST_CODE_VIDEO = 8576;

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

    private DetailsOverviewRowPresenter mOverviewRowPresenter;
    private TvshowDetailsDescriptionPresenter mDescriptionPresenter;

    private Overlay mOverlay;
    private int mColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTopOffsetRatio(0.6f);

        mTvshow = (Tvshow) getActivity().getIntent().getSerializableExtra(EXTRA_TVSHOW);
        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mDescriptionPresenter = new TvshowDetailsDescriptionPresenter();
        mOverviewRowPresenter = new DetailsOverviewRowPresenter(mDescriptionPresenter);
        mOverviewRowPresenter.setSharedElementEnterTransition(getActivity(), SHARED_ELEMENT_NAME, 1000);
        mOverviewRowPresenter.setBackgroundColor(getResources().getColor(R.color.leanback_details_background));
        mOverviewRowPresenter.setStyleLarge(true);
        mOverviewRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == TvshowActionAdapter.ACTION_PLAY) {
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
                else if (action.getId() == TvshowActionAdapter.ACTION_MORE_DETAILS) {
                    Intent intent = new Intent(getActivity(), TvshowMoreDetailsActivity.class);
                    intent.putExtra(TvshowMoreDetailsFragment.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
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
                    startActivity(intent);
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_UNINDEX) {
                    Intent intent = new Intent(getActivity(), SeasonActivity.class);
                    intent.putExtra(SeasonFragment.EXTRA_ACTION_ID, action.getId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_ID, mTvshow.getTvshowId());
                    intent.putExtra(SeasonFragment.EXTRA_TVSHOW_NAME, mTvshow.getName());
                    startActivity(intent);
                }
                else if (action.getId() == TvshowActionAdapter.ACTION_CHANGE_INFO) {
                    if (!ArchosUtils.isNetworkConnected(getActivity())) {
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
                    startActivity(intent);
                }
            }
        });

        ClassPresenterSelector ps = new ClassPresenterSelector();
        ps.addClassPresenter(DetailsOverviewRow.class, mOverviewRowPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());

        mRowsAdapter = new ArrayObjectAdapter(ps);

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
    public void onStop() {
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
        super.onResume();

        mOverlay.resume();

        // Start loading the detailed info about the show if needed
        if (mTvshow.getShowTags()==null) {
            mFullScraperTagsTask = new FullScraperTagsTask().execute(mTvshow);
        }
        if (mBackdropTask!=null) {
            mBackdropTask.cancel(true);
        }
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mTvshow.getShowTags());

    }

    @Override
    public void onPause() {
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
        if ((requestCode == REQUEST_CODE_MORE_DETAILS || requestCode == REQUEST_CODE_VIDEO) && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Get RESULT_OK from TvshowMoreDetailsFragment");

            // Only Poster and/or backdrop has been changed.
            // But the ShowTags must be recomputed as well.
            // The simpler is to reload everything...
            // Well for now at least, because the result is a big ugly glitch...
            mRowsAdapter.removeItems(0, mRowsAdapter.size());
            if (mFullScraperTagsTask!=null) {
                mFullScraperTagsTask.cancel(true);
            }
            mFullScraperTagsTask = new FullScraperTagsTask().execute(mTvshow);
        }
        else if (requestCode == REQUEST_CODE_CHANGE_TVSHOW && resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Get RESULT_OK from ManualShowScrappingActivity");
            // Whole show has been changed, need to reload everything
            // First update the TvShow instance we have here with the data returned by ManualShowScrappingActivity
            String newName = data.getStringExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_NAME);
            Long newId = data.getLongExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_ID, -1);
            mTvshow = new Tvshow(newId, newName, null, mTvshow.getSeasonCount(), mTvshow.getEpisodeCount());
            // Clear all the loader managers because they need to be recreated with the new ID
            getLoaderManager().destroyLoader(SEASONS_LOADER_ID);
            if (mSeasonAdapters != null){
                for (int i = 0; i < mSeasonAdapters.size(); i++) {
                    getLoaderManager().destroyLoader(mSeasonAdapters.keyAt(i));
                }
            }
            // Clear the rows
            mRowsAdapter.removeItems(0, mRowsAdapter.size());

        }
    }

    /** Fill the ShowTags in the given TvShow instance */
    private class FullScraperTagsTask extends AsyncTask<Tvshow, Void, Tvshow> {

        @Override
        protected Tvshow doInBackground(Tvshow... tvshows) {
            mTvshow.setShowTags( (ShowTags)tvshows[0].getFullScraperTags(getActivity()));
            return mTvshow;
        }

        protected void onPostExecute(Tvshow tvshow) {
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
            getLoaderManager().restartLoader(SEASONS_LOADER_ID, null, TvshowFragment.this);
        }
    }

    //--------------------------------------------

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        if (id==SEASONS_LOADER_ID) {
            return new SeasonsLoader(getActivity(), mTvshow.getTvshowId());
        }
        else {
            // The season number is put in the id argument
            return new EpisodesLoader(getActivity(), mTvshow.getTvshowId(), id, true);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (cursorLoader.getId()==SEASONS_LOADER_ID) {
            //TODO:
            // CAUTION: we get an update here each time a single episode resume point is changed...
            // Basic solution: fill the season rows only the first time, i.e. guess it does not change over
            // time, which is true with the current feature set at least...

            cursor.moveToFirst();
            final int seasonNumberColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);

            mSeasonAdapters = new SparseArray<CursorObjectAdapter>();

            // Build one row for each season
            while (!cursor.isAfterLast()) {
                int seasonNumber = cursor.getInt(seasonNumberColumn);
                CursorObjectAdapter seasonAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity(), PosterImageCardPresenter.EpisodeDisplayMode.FOR_SEASON_LIST));
                seasonAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
                mSeasonAdapters.put(seasonNumber, seasonAdapter);
                mRowsAdapter.add(new ListRow(seasonNumber,
                        new HeaderItem(seasonNumber, getString(R.string.episode_season) + " " + seasonNumber),
                        seasonAdapter));
                getLoaderManager().restartLoader(seasonNumber, null, this);
                cursor.moveToNext();
            }
            cursor.close();
        }
        else {
            // We got the list of episode for one season, load it
            mSeasonAdapters.get(cursorLoader.getId()).changeCursor(cursor);
            
            if (cursor.getCount() == 0) {
                for (int i = 0; i < mRowsAdapter.size(); i++) {
                    Row row = (Row)mRowsAdapter.get(i);

                    if (row.getId() == cursorLoader.getId()) {
                        mRowsAdapter.remove(row);

                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }

    //--------------------------------------------

    private class DetailRowBuilderTask extends AsyncTask<Tvshow, Integer, DetailsOverviewRow> {

        @Override
        protected DetailsOverviewRow doInBackground(Tvshow... shows) {
            Tvshow tvshow = shows[0];

            // Buttons
            DetailsOverviewRow detailsRow = new DetailsOverviewRow(tvshow);
            detailsRow.setActionsAdapter(new TvshowActionAdapter(getActivity(), tvshow));

            Bitmap bitmap = null;
            try {
                // Poster: we must take the poster from the showTags because they are updated (in case they have
                // been changed in TvshowMoreDetailsFragment) while the Tvshow instance has not been updated.
                Uri posterUri;
                if (tvshow.getShowTags()!=null) {
                    posterUri = Uri.parse("file://"+tvshow.getShowTags().getDefaultPoster().getLargeFile());
                } else {
                    posterUri = tvshow.getPosterUri(); // fallback
                }

                if (posterUri != null) {
                    bitmap = Picasso.get()
                            .load(posterUri)
                            .noFade() // no fade since we are using activity transition anyway
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
                    mColor = palette.getDarkVibrantColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background));
                    mOverviewRowPresenter.setBackgroundColor(mColor);
                    detailsRow.setImageBitmap(getActivity(), bitmap);
                    detailsRow.setImageScaleUpAllowed(true);
                }
            }

            return detailsRow;
        }

        @Override
        protected void onPostExecute(DetailsOverviewRow detailRow) {
            BackgroundManager.getInstance(getActivity()).setDrawable(new ColorDrawable(VideoInfoCommonClass.getDarkerColor(mColor)));
            mRowsAdapter.add(INDEX_DETAILS, detailRow);
            setAdapter(mRowsAdapter);
        }
    }
}
