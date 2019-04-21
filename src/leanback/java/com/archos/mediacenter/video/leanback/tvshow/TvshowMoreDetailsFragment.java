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
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import androidx.leanback.app.DetailsFragmentWithLessTopOffset;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ItemAlignmentFacet;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.BackdropTask;
import com.archos.mediacenter.video.leanback.adapter.object.WebPageLink;
import com.archos.mediacenter.video.leanback.details.ArchosDetailsOverviewRowPresenter;
import com.archos.mediacenter.video.leanback.details.BackgroundColorPresenter;
import com.archos.mediacenter.video.leanback.details.CastRow;
import com.archos.mediacenter.video.leanback.details.CastRowPresenter;
import com.archos.mediacenter.video.leanback.details.PlotAndGenresRow;
import com.archos.mediacenter.video.leanback.details.PlotAndGenresRowPresenter;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.ScraperImageBackdropPresenter;
import com.archos.mediacenter.video.leanback.presenter.ScraperImagePosterPresenter;
import com.archos.mediacenter.video.utils.WebUtils;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.TagsFactory;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.IOException;
import java.util.List;


public class TvshowMoreDetailsFragment extends DetailsFragmentWithLessTopOffset {

    private static final String TAG = "TvshowMoreDetails";

    public static final String EXTRA_TVSHOW_ID = "TVSHOW_ID";
    public static final String EXTRA_TVSHOW_WATCHED = "TVSHOW_WATCHED";
    public static final String SHARED_ELEMENT_NAME = "hero";

    /** The show we're displaying */
    private long mShowId;
    private boolean mShowWatched;

    /** all the data about this show in the DB */
    private ShowTags mShowTags;

    private DetailsOverviewRow mDetailsRow;
    private PlotAndGenresRow mPlotAndGenresRow;
    private CastRow mCastRow;
    private ListRow mPostersRow;
    private ListRow mBackdropsRow;
    private ListRow mWebLinksRow;
    private ArrayObjectAdapter mRowsAdapter;

    private AsyncTask mBackdropTask;
    private AsyncTask mFullScraperTagsTask;
    private AsyncTask mBuildRowsTask;
    private AsyncTask mShowPosterSaverTask;
    private AsyncTask mBackdropSaverTask;

    private Overlay mOverlay;
    private ArchosDetailsOverviewRowPresenter mOverviewRowPresenter;
    private TvshowMoreDetailsDescriptionPresenter mDescriptionPresenter;
    private int mColor;
    private Handler mHandler;
    private int oldPos = 0;
    private int oldSelectedSubPosition = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTopOffsetRatio(0.6f);

        final Intent intent = getActivity().getIntent();
        mShowId = intent.getLongExtra(EXTRA_TVSHOW_ID, -1);
        mShowWatched = intent.getBooleanExtra(EXTRA_TVSHOW_WATCHED, false);
        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mHandler = new Handler();
        mDescriptionPresenter = new TvshowMoreDetailsDescriptionPresenter(mShowWatched);
        mOverviewRowPresenter = new ArchosDetailsOverviewRowPresenter(mDescriptionPresenter, true);
        //be aware of a hack to avoid fullscreen overview : cf onSetRowStatus
        FullWidthDetailsOverviewSharedElementHelper helper = new FullWidthDetailsOverviewSharedElementHelper();
        helper.setSharedElementEnterTransition(getActivity(), SHARED_ELEMENT_NAME, 1000);
        mOverviewRowPresenter.setListener(helper);
        mOverviewRowPresenter.setBackgroundColor(getResources().getColor(R.color.leanback_details_background));
        mOverviewRowPresenter.setOnActionClickedListener(null);



        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof ScraperImage) {
                    if (row == mPostersRow) {
                        mShowPosterSaverTask = new ShowPosterSaverTask().execute((ScraperImage) item);
                    }
                    else if (row == mBackdropsRow) {
                        mBackdropSaverTask = new BackdropSaverTask().execute((ScraperImage) item);
                    }
                }
                else if (item instanceof WebPageLink) {
                    WebPageLink link = (WebPageLink)item;
                    WebUtils.openWebLink(getActivity(), link.getUrl());
                }
            }
        });

        // WORKAROUND: at least one instance of BackdropTask must be created soon in the process (onCreate ?)
        // else it does not work later.
        // --> This instance of BackdropTask() will not be used but it must be created here!
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor));
    }

    @Override
    protected void setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter presenter) {
        ItemAlignmentFacet facet = new ItemAlignmentFacet();
        // by default align details_frame to half window height
        ItemAlignmentFacet.ItemAlignmentDef alignDef1 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef1.setItemAlignmentViewId(R.id.details_frame);
        alignDef1.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_actions)
                - getResources().getDimensionPixelSize(R.dimen.lb_details_v2_actions_height));
        alignDef1.setItemAlignmentOffsetPercent(0);
        // when description is selected, align details_frame to top edge
        ItemAlignmentFacet.ItemAlignmentDef alignDef2 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef2.setItemAlignmentViewId(R.id.details_frame);
        alignDef2.setItemAlignmentFocusViewId(R.id.details_overview_description);
        alignDef2.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_description));
        alignDef2.setItemAlignmentOffsetPercent(0);
        ItemAlignmentFacet.ItemAlignmentDef[] defs =
                new ItemAlignmentFacet.ItemAlignmentDef[] {alignDef1, alignDef2};
        facet.setAlignmentDefs(defs);
        presenter.setFacet(ItemAlignmentFacet.class, facet);
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
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        // Cancel all the async tasks
        for (AsyncTask task : new AsyncTask[] { mBackdropTask, mFullScraperTagsTask, mBuildRowsTask, mShowPosterSaverTask, mBackdropSaverTask}) {
            if (task!=null) {
                task.cancel(true);
            }
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();

        // Start loading the detailed info about the show if needed
        if (mShowTags==null) {
            mFullScraperTagsTask = new FullScraperTagsTask().execute(mShowId);
        }

        if (mBackdropTask!=null) {
            mBackdropTask.cancel(true);
        }
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mShowTags);
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    //--------------------------------------------

    /** Get the ShowTags */
    private class FullScraperTagsTask extends AsyncTask<Long, Void, ShowTags> {

        @Override
        protected ShowTags doInBackground(Long... ids) {
            final long showId = ids[0];
            return TagsFactory.buildShowTags(getActivity(), showId);
        }

        protected void onPostExecute(ShowTags showTags) {
            mShowTags = showTags;

            // Launch backdrop task in BaseTags-as-arguments mode
            if (mBackdropTask!=null) {
                mBackdropTask.cancel(true);
            }
            mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(showTags);

            // Build and load the rows
            if (mBuildRowsTask != null) {
                mBuildRowsTask.cancel(true);
            }
            mBuildRowsTask = new BuildRowsTask().execute(mShowTags);
        }
    }

    private class BuildRowsTask extends AsyncTask<ShowTags, Void, Void> {

        @Override
        protected Void doInBackground(ShowTags... showTagsArray) {
            ShowTags tags = showTagsArray[0];

            // Details ---------------
            mDetailsRow = new DetailsOverviewRow(tags);
            Bitmap bitmap = null;
            try {
                if (tags.getDefaultPoster() != null) {
                    // Poster
                    File file = tags.getDefaultPoster().getLargeFileF();
                    if (file != null) {
                        bitmap = Picasso.get()
                                .load(file)
                                .noFade() // no fade since we are using activity transition anyway
                                .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                                .centerCrop()
                                .get();
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "TvshowMoreDetailsFragment Picasso load exception", e);
            } catch (NullPointerException e) { // getDefaultPoster() may return null (seen once at least)
                Log.d(TAG, "TvshowMoreDetailsFragment doInBackground exception", e);
            } finally {
                if (bitmap != null) {
                    Palette palette = Palette.from(bitmap).generate();
                    if (palette.getDarkVibrantSwatch() != null)
                        mColor = palette.getDarkVibrantSwatch().getRgb();
                    else if (palette.getDarkMutedSwatch() != null)
                        mColor = palette.getDarkMutedSwatch().getRgb();
                    else
                        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                    mDetailsRow.setImageBitmap(getActivity(), bitmap);
                    mDetailsRow.setImageScaleUpAllowed(true);
                }
                else {
                    mDetailsRow.setImageDrawable(getResources().getDrawable(R.drawable.filetype_new_video));
                    mDetailsRow.setImageScaleUpAllowed(false);
                }
            }

            // Plot and cast
            if (tags.getPlot()!=null) {
                mPlotAndGenresRow = new PlotAndGenresRow(getString(R.string.scrap_plot), tags.getPlot(), tags.getGenresFormatted());
            } else {
                mPlotAndGenresRow = null;
            }

            if (tags.getSpannableActorsFormatted()!=null) {
                mCastRow = new CastRow(getString(R.string.scrap_cast), tags.getSpannableActorsFormatted(), "");
            }
            else {
                mCastRow = null;
            }

            if (isCancelled()) {
                return null;
            }

            // Posters
            List<ScraperImage> posters = tags.getAllPostersInDb(getActivity());
            if (!posters.isEmpty()) {
                ArrayObjectAdapter postersRowAdapter = new ArrayObjectAdapter(new ScraperImagePosterPresenter(getContext(), posters));
                postersRowAdapter.addAll(0, posters);
                mPostersRow = new ListRow( new HeaderItem(getString(R.string.leanback_posters_header)), postersRowAdapter);
            } else {
                mPostersRow = null;
            }


            if (isCancelled()) {
                return null;
            }

            // Backdrops
            List<ScraperImage> backdrops = tags.getAllBackdropsInDb(getActivity());
            if (!backdrops.isEmpty()) {
                ArrayObjectAdapter backdropsRowAdapter = new ArrayObjectAdapter(new ScraperImageBackdropPresenter(getContext(), backdrops));
                backdropsRowAdapter.addAll(0, backdrops);
                mBackdropsRow = new ListRow( new HeaderItem(getString(R.string.leanback_backdrops_header)), backdropsRowAdapter);
            } else {
                mBackdropsRow = null;
            }

            // Web links
            /*
            final String imdbId = tags.getImdbId();
            if ((imdbId!=null) && (imdbId.length()>0)) {
                final String imdbUrl = getResources().getString(R.string.imdb_title_url) + imdbId;
                ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(new WebPageLinkPresenter());
                rowAdapter.add(new WebPageLink(imdbUrl));
                mWebLinksRow = new ListRow( new HeaderItem(getString(R.string.leanback_weblinks_header)), rowAdapter);
            } else {
                mWebLinksRow = null;
            }*/
            mWebLinksRow = null; // No web links for now to be sure to get "leanback certification"

            return null;
        }

        @Override
        protected void onPostExecute(Void avoid) {
            ClassPresenterSelector ps = new ClassPresenterSelector();
            ps.addClassPresenter(DetailsOverviewRow.class, mOverviewRowPresenter);
            ps.addClassPresenter(PlotAndGenresRow.class, new PlotAndGenresRowPresenter(14,mColor)); // 14 lines max to fit on screen
            ps.addClassPresenter(CastRow.class, new CastRowPresenter(17,mColor)); // 17 lines max to fit on screen
            ps.addClassPresenter(ListRow.class, new ListRowPresenter());
            mOverviewRowPresenter.updateBackgroundColor(mColor);
            mRowsAdapter = new ArrayObjectAdapter(ps);
            mRowsAdapter.clear();
            // Add all the non-null rows
            for (Row row : new Row[] {mDetailsRow, mPlotAndGenresRow, mCastRow, mPostersRow, mBackdropsRow, mWebLinksRow}) {
                if (row!=null) {
                    mRowsAdapter.add(row);
                }
            }
            setAdapter(mRowsAdapter);
        }
    }


    /** Saves a Poster as default poster for a show and update the current poster */
    private class ShowPosterSaverTask extends AsyncTask<ScraperImage, Void, Bitmap[]> {

        @Override
        protected Bitmap[] doInBackground(ScraperImage... params) {
            ScraperImage poster = params[0];

            // Save in DB and download
            if (poster.setAsDefault(getActivity(), -1)) { // -1 means for the whole show (not for a given season)
                poster.download(getActivity());
            }
            // Update the bitmap
            Bitmap bitmap=null;
            try {
                bitmap = Picasso.get()
                        .load(poster.getLargeFileF())
                        .noFade()
                        .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                        .centerCrop()
                        .get();

            } catch (IOException e) {
                Log.d(TAG, "ShowPosterSaverTask Picasso load exception", e);
            }

            Bitmap paletteBitmap=null;
            try {
                paletteBitmap = Picasso.get()
                        .load(poster.getLargeFileF())
                        .noFade()
                        .get();

            } catch (IOException e) {
                Log.d(TAG, "ShowPosterSaverTask Picasso load exception", e);
            }

            return new Bitmap[] { bitmap, paletteBitmap };
        }

        @Override
        protected void onPostExecute(Bitmap[] results) {
            if (results[0] != null && results[1] != null) {
                mDetailsRow.setImageBitmap(getActivity(), results[0]);
                mDetailsRow.setImageScaleUpAllowed(true);

                Palette palette = Palette.from(results[1]).generate();
                int color;
                
                if (palette.getDarkVibrantSwatch() != null)
                    color = palette.getDarkVibrantSwatch().getRgb();
                else if (palette.getDarkMutedSwatch() != null)
                    color = palette.getDarkMutedSwatch().getRgb();
                else
                    color = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);

                if (color != mColor) {
                    mColor = color;

                    mOverviewRowPresenter.updateBackgroundColor(color);

                    for (Presenter pres : mRowsAdapter.getPresenterSelector().getPresenters()){
                        if (pres instanceof BackgroundColorPresenter)
                            ((BackgroundColorPresenter) pres).setBackgroundColor(color);
                    }
                }

                Toast.makeText(getActivity(), R.string.leanback_poster_changed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
            }

            // The activity result is set to OK if the poster or backdrop is changed
            getActivity().setResult(Activity.RESULT_OK);
        }
    }

    /** Saves a Backdrop as default for a video and update the current backdrop */
    private class BackdropSaverTask extends AsyncTask<ScraperImage, Void, ShowTags> {

        @Override
        protected ShowTags doInBackground(ScraperImage... params) {
            ScraperImage backdrop = params[0];
            // Save in DB and download
            if (backdrop.setAsDefault(getActivity())) {
                backdrop.download(getActivity());
            }
            // Simplier to rebuild the full ShowTags here in order for the BackdropTask to get the new backdrop in it
            return TagsFactory.buildShowTags(getActivity(), mShowId);
        }

        @Override
        protected void onPostExecute(ShowTags result) {
            mShowTags = result;

            // Update backdrop
            if (mBackdropTask!=null) {
                mBackdropTask.cancel(true);
            }
            mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mShowTags);
            Toast.makeText(getActivity(), R.string.leanback_backdrop_changed, Toast.LENGTH_SHORT).show();

            // The activity result is set to OK if the poster or backdrop is changed
            getActivity().setResult(Activity.RESULT_OK);
       }
    }

}
