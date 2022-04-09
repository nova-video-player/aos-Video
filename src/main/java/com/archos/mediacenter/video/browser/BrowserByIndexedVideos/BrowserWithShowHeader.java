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


package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.loader.app.LoaderManager;
import androidx.core.content.ContextCompat;
import androidx.loader.content.Loader;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.ContextMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.mediacenter.video.browser.adapters.CastAdapter;
import com.archos.mediacenter.video.browser.adapters.CastData;
import com.archos.mediacenter.video.browser.adapters.SeasonsData;
import com.archos.mediacenter.video.browser.adapters.SeriesTags;
import com.archos.mediacenter.video.browser.adapters.ShowNetworkAdapter;
import com.archos.mediacenter.video.browser.adapters.StudioAdapter;
import com.archos.mediascraper.MediaScraper;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.ImageViewSetter;
import com.archos.mediacenter.utils.imageview.ImageViewSetterConfiguration;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.HeaderGridView;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.browser.loader.TvshowLoader;
import com.archos.mediacenter.video.info.VideoInfoPosterBackdropActivity;
import com.archos.mediacenter.video.info.VideoInfoScraperActivity;
import com.archos.mediacenter.video.info.VideoInfoShowScraperFragment;
import com.archos.mediacenter.video.utils.DelayedBackgroundLoader;
import com.archos.mediacenter.video.utils.SerialExecutor;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ShowTags;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public abstract class BrowserWithShowHeader extends CursorBrowserByVideo  {

    private static final Logger log = LoggerFactory.getLogger(BrowserWithShowHeader.class);

    static final private String BROWSER_SHOW = BrowserListOfEpisodes.class.getName();
    public static final String EXTRA_SHOW_ITEM = "show_item";
    private final static int SUBMENU_ITEM_LIST_INDEX = 0;
    private final static int SUBMENU_ITEM_GRID_INDEX = 1;

    protected long mShowId;
    private View mHeaderView;
    protected Tvshow mShow;
    protected int SHOW_LOADER_ID = 1;
    private AsyncTask<Object, Void, TvShowAsyncTask.Result> mTvShowAsyncTask;
    private final static int SCRAPER_REQUEST = 0;
    private int mCurrentPlotLines = 5;

    private ImageViewSetter mBackgroundSetter;
    private ImageProcessor mBackgroundLoader;
    private ImageProcessor mSeriesBackdropLoader;
    private ImageView mApplicationBackdrop;
    private SerialExecutor mSerialExecutor;
    private int mColor;
    protected View mApplicationFrameLayout;
    private boolean mPlotIsFullyDisplayed;
    private RecyclerView networkLogos;
    private RecyclerView studioLogos;
    private RecyclerView actors;
    private SeasonsData seasonsData;

    public BrowserWithShowHeader() {
        log.debug("BrowserBySeason()");
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHideOption = false;
        mSerialExecutor = new SerialExecutor();
        //be aware that argument can be changed in activityresult
        mShowId = getArguments().getLong(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, 0);
        mShow = (Tvshow) getArguments().getSerializable(EXTRA_SHOW_ITEM);
        mHideWatched = false;

        mBackgroundLoader = new DelayedBackgroundLoader(getActivity(), 800, 0.2f);
        mSeriesBackdropLoader = new DelayedBackgroundLoader(getActivity(), 0, 1);

    }
    public void onViewCreated(View v, Bundle save){
        super.onViewCreated(v, save);
        ImageViewSetterConfiguration config = ImageViewSetterConfiguration.Builder.createNew()
                .setUseCache(false)
                .build();
        mApplicationFrameLayout =  ((MainActivity) getActivity()).getGlobalBackdropView();
        mApplicationBackdrop = (ImageView) (((MainActivity) getActivity()).getGlobalBackdropView().findViewById(R.id.backdrop));
        mApplicationBackdrop.setAlpha(0f);
        mBackgroundSetter = new ImageViewSetter(getActivity(), config);
        mHeaderView = LayoutInflater.from(getContext()).inflate(R.layout.browser_item_header_show, new RelativeLayout(mContext), false);
        mHeaderView.findViewById(R.id.loading).setVisibility(View.VISIBLE);
        addHeaderView();
    }

    @Override
    public int getEmptyMessage() {
        return R.string.scraper_no_episode_found;
    }

    protected void applySelectedViewMode(int newMode) {
        if(mHeaderView!=null) { //removing headerview
            if (mArchosGridView instanceof ListView) {
                ((ListView) mArchosGridView).removeHeaderView(mHeaderView);
                ((AdapterView)mHeaderView.getParent()).removeViewInLayout(mHeaderView);
            }
            if(mArchosGridView instanceof HeaderGridView)
                ((HeaderGridView) mArchosGridView).removeHeaderView(mHeaderView);

        }
        // Save the current position variables before changing the view mode
        setPosition();
        mSelectedPosition = mArchosGridView.getFirstVisiblePosition();
        setViewMode(newMode);
        //replacing headerView

            addHeaderView();
        bindAdapter();
    }

    private void addHeaderView() {
            if (mHeaderView != null) {

                if (mArchosGridView instanceof ListView) {
                    //to avoid cast exception
                    mHeaderView.setLayoutParams(new ListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                    ((ListView) mArchosGridView).addHeaderView(mHeaderView);

                }
                if (mArchosGridView instanceof HeaderGridView) {
                    //to avoid cast exception
                    mHeaderView.setLayoutParams(new HeaderGridView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
                    ((HeaderGridView) mArchosGridView).addHeaderView(mHeaderView);

                }
            }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if(mShow!=null){
            menu.add(0,R.string.scrap_series_change, 0, R.string.scrap_series_change);
            menu.add(0,R.string.info_menu_series_backdrop_select, 0, R.string.info_menu_series_backdrop_select);
            menu.add(0,R.string.info_menu_series_clearlogo_select, 0, R.string.info_menu_series_clearlogo_select);
            menu.add(0,R.string.info_menu_series_poster_select, 0, R.string.info_menu_series_poster_select);

        }
        super.onCreateOptionsMenu(menu, inflater);
        if (mBrowserAdapter != null
                && !mBrowserAdapter.isEmpty()) {
            mDisplayModeSubmenu.clear();
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_list_mode2, R.string.view_mode_list, 0);
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_grid_mode, R.string.view_mode_grid, 0);
            mDisplayModeSubmenu.selectSubmenuItem(mViewMode == VideoUtils.VIEW_MODE_GRID
                    ? SUBMENU_ITEM_GRID_INDEX : SUBMENU_ITEM_LIST_INDEX);
        }

    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == Activity.RESULT_OK){
            long newShowID = data.getIntExtra(VideoInfoShowScraperFragment.SHOW_ID, -1);
            if(newShowID!=-1&&newShowID!=mShowId){
                mShowId = newShowID;
                getArguments().putLong(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, mShowId);
                LoaderManager.getInstance(this).restartLoader(0, null, this);
                LoaderManager.getInstance(this).restartLoader(SHOW_LOADER_ID, null, this);
            }
        }
    }
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.string.scrap_series_change){
            Intent intent = new Intent(getActivity(), VideoInfoScraperActivity.class);
            intent.putExtra(VideoInfoScraperActivity.EXTRA_SHOW, mShow);
            startActivityForResult(intent, SCRAPER_REQUEST);
            return true;
        }else if(item.getItemId()==R.string.info_menu_series_backdrop_select){
            Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
            intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mShow);
            intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_BACKDROP, true);
            startActivity(intent);
            return  true;
        }else if(item.getItemId()==R.string.info_menu_series_clearlogo_select){
            Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
            intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mShow);
            intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_CLEARLOGO, true);
            startActivity(intent);
            return  true;
        }else if(item.getItemId()==R.string.info_menu_series_poster_select){
            selectSeriesPoster();
            return  true;
        }
        else
            return super.onOptionsItemSelected(item);
    }

    protected void selectSeriesPoster(){
        Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
        intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mShow);
        intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_BACKDROP, false);
        startActivity(intent);
    }
    @Override
    public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
        switch (position) {
            case SUBMENU_ITEM_LIST_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_LIST) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_LIST);
                }
                break;

            case SUBMENU_ITEM_GRID_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_GRID) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_GRID);
                }
                break;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        log.debug("onLoadFinished");
        super.onLoadFinished(loader, cursor);
        if (getActivity() == null) return;
        if(loader.getId()==SHOW_LOADER_ID) {
            cursor.moveToFirst();
            TvshowCursorMapper mTvShowMapper = new TvshowCursorMapper();
            mTvShowMapper.bindColumns(cursor);
            Tvshow newShow = (Tvshow) mTvShowMapper.bind(cursor);
            if(needToReload(mShow, newShow)) {
                mShow = newShow;
                getArguments().putSerializable(EXTRA_SHOW_ITEM, mShow); //saving in arguments
                if (mTvShowAsyncTask != null)
                    mTvShowAsyncTask.cancel(true);
                // FIXME: for some unexplained reason when doing BrowserAllTvShows->BrowserListOfSeasons->BrowserAllMovies->open movie
                // calls at the end BrowserWithShowHeaders for no apparent reason (tried to determine code path)
                // since this happens when getActivity() == null avoid doing UI stuff hides the issue that still remains to be fixed properly
                if (getActivity() != null) {
                    log.debug("onLoadFinished: activity not null");
                    mTvShowAsyncTask = new TvShowAsyncTask().executeOnExecutor(mSerialExecutor,getPosterUri(),mShow);
                    getActivity().invalidateOptionsMenu();
                } else {
                    log.debug("onLoadFinished: FIXME onLoadFinished getActivity is null");
                }
            }

        }
    }

    private boolean needToReload(Tvshow oldShow, Tvshow newShow) {
        if (oldShow==null || newShow==null) {log.debug("foundDifferencesRequiringDetailsUpdate null"); return true;}
        if (oldShow.getClass() != newShow.getClass()) {log.debug("foundDifferencesRequiringDetailsUpdate class"); return true;}
        if (oldShow.getTvshowId() != newShow.getTvshowId()) {log.debug("foundDifferencesRequiringDetailsUpdate getTvshowId"); return true;}
        if (oldShow.isTraktSeen() != newShow.isTraktSeen()) {log.debug("foundDifferencesRequiringDetailsUpdate isTraktSeen"); return true;}
        if (oldShow.getPosterUri()!=null&&!oldShow.getPosterUri().equals(newShow.getPosterUri())||newShow.getPosterUri()!=null&&newShow.getPosterUri().equals(oldShow.getPosterUri())) {log.debug("foundDifferencesRequiringDetailsUpdate getPosterUri"); return true;}
        return false;
    }

    @Override
    public void onStop(){
        super.onStop();
        if(mTvShowAsyncTask!=null)
            mTvShowAsyncTask.cancel(true);
        mBackgroundSetter.stopLoading(mApplicationBackdrop);
        mApplicationBackdrop.animate().cancel();
        mApplicationBackdrop.setAlpha(0f);
    }

    private  class TvShowAsyncTask extends AsyncTask<Object, Void,TvShowAsyncTask.Result>{
        public class Result{
            public Bitmap bitmap;
            public Tvshow show;
            public ShowTags tags;
            public Result(Tvshow show, Bitmap bitmap, ShowTags tags){
                this.show = show;
                this.bitmap = bitmap;
                this.tags = tags;
            }
        }
        @Override
        protected TvShowAsyncTask.Result doInBackground(Object... postersUri) {
            Tvshow show = (Tvshow) postersUri[1];
            Uri posterUri = (Uri) postersUri[0];
            Bitmap bitmap = null;
            try {
                log.debug("TvShowAsyncTask.Result show " + show.getName() + " postersUri " + posterUri);
                if (posterUri != null) {
                    bitmap = Picasso.get()
                            .load(posterUri)
                            .resizeDimen(R.dimen.video_details_poster_width,R.dimen.video_details_poster_height)
                            .noFade() // no fade since we are using activity transition anyway
                            .get();
                    log.debug("TvShowAsyncTask.Result: "+bitmap.getWidth()+"x"+bitmap.getHeight()+" ---- "+posterUri);
                    if(bitmap!=null) {
                        Palette palette = Palette.from(bitmap).generate();
                        mColor = palette.getDarkVibrantColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background));
                    }
                }
            }
            catch (IOException e) {
                log.error("DetailsOverviewRow: caught IOException, Picasso load exception", e);
            }
            catch (NullPointerException e) { // getDefaultPoster() may return null (seen once at least)
                log.error("DetailsOverviewRow: caught NullPointerException doInBackground exception", e);
            }

            return new TvShowAsyncTask.Result(show, bitmap,(ShowTags) show.getFullScraperTags(getActivity()));
        }
        protected void onPostExecute(TvShowAsyncTask.Result result) {
            Tvshow show = result.show;
            BaseTags tags = result.tags;
            ShowTags showTags = result.tags;

            final TextView plotTv = mHeaderView.findViewById(R.id.series_plot);
            mHeaderView.findViewById(R.id.loading).setVisibility(View.GONE);

            TextView tvpg = mHeaderView.findViewById(R.id.content_rating);
            View tvpgContainer = mHeaderView.findViewById(R.id.content_rating_container);
            if (tags.getContentRating()==null || tags.getContentRating().isEmpty()) {
                tvpg.setVisibility(View.GONE);
                tvpgContainer.setVisibility(View.GONE);
            } else {
                tvpg.setText(tags.getContentRating());
            }

            // Utilizing the unused series director as a pipeline for series created by tag
            TextView createdBy = mHeaderView.findViewById(R.id.created_by);
            createdBy.setText(tags.getDirectorsFormatted());
            LinearLayout createdbyContainer = mHeaderView.findViewById(R.id.created_by_container);
            if (tags.getDirectorsFormatted() == null)
                createdbyContainer.setVisibility(View.GONE);
            createdBy.setMaxLines(2);
            createdBy.setTag(true);
            createdBy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Boolean) createdBy.getTag())) {
                        createdBy.setMaxLines(Integer.MAX_VALUE);
                        createdBy.setTag(false);
                    } else {
                        createdBy.setMaxLines(2);
                        createdBy.setTag(true);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                }
            });


            TextView producer = mHeaderView.findViewById(R.id.producer);
            producer.setText(tags.getProducersFormatted());
            LinearLayout producerContainer = mHeaderView.findViewById(R.id.producer_container);
            if (tags.getProducersFormatted() == null)
                producerContainer.setVisibility(View.GONE);
            producer.setMaxLines(2);
            producer.setTag(true);
            producer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Boolean) producer.getTag())) {
                        producer.setMaxLines(Integer.MAX_VALUE);
                        producer.setTag(false);
                    } else {
                        producer.setMaxLines(2);
                        producer.setTag(true);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                }
            });

            // set Original Music Composer
            TextView mMusiccomposer = mHeaderView.findViewById(R.id.musiccomposer);
            LinearLayout mMusiccomposerContainer = mHeaderView.findViewById(R.id.musiccomposer_container);
            if (tags.getMusiccomposersFormatted() == null || tags.getMusiccomposersFormatted().isEmpty()) {
                mMusiccomposer.setVisibility(View.GONE);
                mMusiccomposerContainer.setVisibility(View.GONE);
            } else {
                mMusiccomposer.setText(tags.getMusiccomposersFormatted());
            }

            TextView network = mHeaderView.findViewById(R.id.network);
            network.setText(show.getStudio());
            network.setMaxLines(1);
            network.setTag(true);
            network.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Boolean) network.getTag())) {
                        network.setMaxLines(Integer.MAX_VALUE);
                        network.setTag(false);
                    } else {
                        network.setMaxLines(1);
                        network.setTag(true);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                }
            });

            TextView Premiered = mHeaderView.findViewById(R.id.premiered);
            String pattern = "MMMM dd, yyyy";
            DateFormat df = new SimpleDateFormat(pattern);
            Date date = showTags.getPremiered();
            String dateAsString = df.format(date);
            Premiered.setText(dateAsString);

            TextView PremieredYear = mHeaderView.findViewById(R.id.premiered_year);
            PremieredYear.setText(Integer.toString(showTags.getPremieredYear()));

            TextView seriesGenres = mHeaderView.findViewById(R.id.series_genres);
            seriesGenres.setText(showTags.getGenresFormatted());

            TextView mSeasonPlot = mHeaderView.findViewById(R.id.season_plot);
            TextView seasonPlotHeader = mHeaderView.findViewById(R.id.season_plot_header);
            TextView seasonAirDate = mHeaderView.findViewById(R.id.season_airdate);
            LinearLayout seasonAirDateContainer = mHeaderView.findViewById(R.id.season_airdate_container);
            List <String>  seasonPlots = showTags.getSeasonPlots();
            List <SeasonsData>  finalSeasonPlots = new ArrayList<>();
            for (int i = 0; i < seasonPlots.size(); i++) {
                String seasonPlot = seasonPlots.get(i);
                List <String>  seasonPlotsFormatted;
                seasonPlotsFormatted = Arrays.asList(seasonPlot.split("\\s*=&%#\\s*"));
                seasonsData = new SeasonsData();
                seasonsData.setSeasonNumber(seasonPlotsFormatted.get(0));
                seasonsData.setSeasonPlot(seasonPlotsFormatted.get(1));
                seasonsData.setSeasonName(seasonPlotsFormatted.get(2));
                seasonsData.setSeasonAirdate(seasonPlotsFormatted.get(3).replaceAll("&&&&####", ""));
                finalSeasonPlots.add(seasonsData);
            }
            Bundle args = getArguments();
            int currentSeason = args.getInt(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON, 0);
            for (int i = 0; i < finalSeasonPlots.size(); i++) {
                String seasonNumber = finalSeasonPlots.get(i).getSeasonNumber();
                if (currentSeason == Integer.parseInt(seasonNumber)){
                    mSeasonPlot.setText(finalSeasonPlots.get(i).getSeasonPlot());
                    if(finalSeasonPlots.get(i).getSeasonPlot().isEmpty()){
                        mSeasonPlot.setVisibility(View.GONE);
                        seasonPlotHeader.setVisibility(View.GONE);
                    }
                    seasonAirDate.setText(finalSeasonPlots.get(i).getSeasonAirdate());
                    if (finalSeasonPlots.get(i).getSeasonAirdate().isEmpty())
                        seasonAirDateContainer.setVisibility(View.GONE);
                    //set season name
                    TextView mSeason = mHeaderView.findViewById(R.id.season);
                    String seasonName = finalSeasonPlots.get(i).getSeasonName();
                    mSeason.setText(seasonName);
                    setSeason(mSeason);
                }
            }
            setSeasonPlot(mHeaderView.findViewById(R.id.season_plot));
            setSeasonAirDateContainer(mHeaderView.findViewById(R.id.season_airdate_container));
            setSeriesPremieredContainer(mHeaderView.findViewById(R.id.premiered_container));

            TextView seriesRating = mHeaderView.findViewById(R.id.series_rating);
            seriesRating.setText(String.valueOf(showTags.getRating()));

            // set series backdrop at top
            ImageView seriesBackdrop = mHeaderView.findViewById(R.id.series_backdrop);
            mBackgroundSetter.set(seriesBackdrop, mSeriesBackdropLoader, tags.getDefaultBackdrop());
            seriesBackdrop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
                    intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mShow);
                    intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_BACKDROP, true);
                    startActivity(intent);
                }
            });

            ImageView networkLogo = mHeaderView.findViewById(R.id.network_logo);

            // setting Networks RecyclerView
            String baseNetworkPath = MediaScraper.getNetworkLogoDirectory(mContext).getPath() + "/";
            String extension = ".png";
            List<ScraperImage> networkImage = showTags.getNetworkLogos();
            networkLogos = mHeaderView.findViewById(R.id.net_logo_rv);
            List<String> NetworkLogoPaths = new ArrayList<>();
            for (int i = 0; i < tags.getNetworkLogosLargeFileF().size(); i++) {
                String avaialbeLogopath = String.valueOf(tags.getNetworkLogosLargeFileF().get(i));
                NetworkLogoPaths.add(avaialbeLogopath);}
            LinearLayoutManager layoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
            networkLogos.setLayoutManager(layoutManager);
            ShowNetworkAdapter.OnItemClickListener indicatorCallback = new ShowNetworkAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(String item) {
                }

                @Override
                public void onItemLongClick(int position) {
                    String path = NetworkLogoPaths.get(position);
                    String clicked_logoName = path.replace(baseNetworkPath, "").replace(extension, "");
                    LayoutInflater inflater = LayoutInflater.from(mContext);
                    View layout = inflater.inflate(R.layout.custom_toast,
                            mHeaderView.findViewById(R.id.toast_layout_root));
                    TextView header = layout.findViewById(R.id.header);
                    TextView newLogoText = layout.findViewById(R.id.new_logo_text);
                    ImageView newLogoImage = layout.findViewById(R.id.toast_logo_image);
                    Picasso.get().load(showTags.getNetworkLogosLargeFileF().get(position)).fit().centerInside().into(newLogoImage);
                    header.setText(getResources().getString(R.string.networklogo_changed));
                    newLogoText.setText(clicked_logoName);
                    Toast toast = new Toast(mContext);
                    toast.setGravity(Gravity.BOTTOM, 0, 50);
                    toast.setDuration(Toast.LENGTH_SHORT);
                    toast.setView(layout);
                    toast.show();
                    Picasso.get().load(showTags.getNetworkLogosLargeFileF().get(position)).fit().centerInside().into(networkLogo);
                    ScraperImage clickedImage = networkImage.get(position);
                    new BrowserWithShowHeader.LogoSaver(mContext).execute(clickedImage);
                }
            };
            final ShowNetworkAdapter logoAdapter = new ShowNetworkAdapter(NetworkLogoPaths,indicatorCallback);
            networkLogos.setAdapter(logoAdapter);
            // if only one or zero logo available locally hide networkLogos
            List<File> availableLogos = new ArrayList<>();
            int size;
            for (int i = 0; i < NetworkLogoPaths.size(); i++) {
                String st = NetworkLogoPaths.get(i);
                File file = new File(st);
                if (file.exists()){
                    availableLogos.add(file);
                }
            }
            size = availableLogos.size();
            if (size <= 1){
                networkLogos.setVisibility(View.GONE);
            }
            // Set default series network logo
            if (tags.getNetworkLogo() != null){
                File networkFile = new File(tags.getNetworkLogo().getPath());
                if (networkFile.exists()){
                    Picasso.get().load(tags.getNetworkLogo()).fit().centerInside().into(networkLogo);
                } else {
                    for (int i = 0; i < availableLogos.size(); i++) {
                        Picasso.get().load(availableLogos.get(0)).fit().centerInside().into(networkLogo);
                    }
                }
            }

            // setting Studio Logo RecyclerView
            studioLogos = mHeaderView.findViewById(R.id.studio_logo_rv);
            List<String> StudioLogoPaths = new ArrayList<>();
            for (int i = tags.getStudioLogosLargeFileF().size() - 1; i >= 0; i--) {
                String studioLogoPath = tags.getStudioLogosLargeFileF().get(i).getPath();
                StudioLogoPaths.add(studioLogoPath);}
            LinearLayoutManager studioLogoLayoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
            studioLogos.setLayoutManager(studioLogoLayoutManager);
            StudioAdapter.OnItemClickListener studioLogoCallback = new StudioAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(String item) {
                }

                @Override
                public void onItemLongClick(int position) {

                }
            };
            final StudioAdapter studioAdapter = new StudioAdapter(StudioLogoPaths,studioLogoCallback);
            studioLogos.setAdapter(studioAdapter);
            // if no Studio file found locally hide studios
            List<File> availableStudioLogos = new ArrayList<>();
            int studiosSize;
            for (int i = 0; i < StudioLogoPaths.size(); i++) {
                String path = StudioLogoPaths.get(i);
                File studioFile = new File(path);
                if (studioFile.exists()){
                    availableStudioLogos.add(studioFile);
                }
            }
            studiosSize = availableStudioLogos.size();
            if (studiosSize == 0){
                studioLogos.setVisibility(View.GONE);
            }

            String studioNames = "";
            String names = "";
            String baseStudioPath = MediaScraper.getStudioLogoDirectory(mContext).getPath() + "/";
            for (int i = tags.getStudioLogosLargeFileF().size() - 1; i >= 0; i--) {
                names = names + tags.getStudioLogosLargeFileF().get(i).getPath().replaceAll(baseStudioPath, "").replaceAll(".png", "") + ", ";
                studioNames = names.substring(0, names.length() - 2);
            }
            TextView studio = mHeaderView.findViewById(R.id.studio);
            studio.setText(studioNames);
            LinearLayout studioNamesContainer = mHeaderView.findViewById(R.id.studio_container);
            if (studioNames.isEmpty())
                studioNamesContainer.setVisibility(View.GONE);
            studio.setMaxLines(1);
            studio.setTag(true);
            studio.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Boolean) studio.getTag())) {
                        studio.setMaxLines(Integer.MAX_VALUE);
                        studio.setTag(false);
                    } else {
                        studio.setMaxLines(1);
                        studio.setTag(true);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                }
            });


            // setting Actors RecyclerView
            actors = mHeaderView.findViewById(R.id.actor_photos);
            List<CastData> seriesActors = new ArrayList<>();
            CastData castData;
            for (int i = 0; i < tags.getWriters().size(); i++) {
                String actor = tags.getWriters().get(i);
                List <String>  actorsFormatted;
                actorsFormatted = Arrays.asList(actor.split("\\s*=&%#\\s*"));
                castData = new CastData();
                castData.setName(actorsFormatted.get(0));
                castData.setCharacter(actorsFormatted.get(1));
                castData.setPhotoPath(MediaScraper.getActorPhotoDirectory(mContext).getPath() + actorsFormatted.get(2));
                seriesActors.add(castData);
            }
            LinearLayoutManager actorsLayoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
            actors.setLayoutManager(actorsLayoutManager);
            CastAdapter.OnItemClickListener actorCallback = new CastAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(int position) {
                }
            };
            final CastAdapter actorAdapter = new CastAdapter(seriesActors,actorCallback);
            actors.setAdapter(actorAdapter);
            // add space between actors
            int spacing = getResources().getDimensionPixelSize(R.dimen.cast_spacing);
            if (actors.getItemDecorationCount() < 1) {
                actors.addItemDecoration(new CastAdapter.SpacesItemDecoration(spacing));
            }
            // hide actors rv & cast header if size = 0
            TextView actorsHeader = mHeaderView.findViewById(R.id.actors_header);
            int actorsSize = seriesActors.size();
            if (actorsSize == 0){
                actorsHeader.setVisibility(View.GONE);
                actors.setVisibility(View.GONE);
            }

            List<SeriesTags> tvShowTags = new ArrayList<>();
            SeriesTags seriesTags;
            for (int i = 0; i < tags.getTaglines().size(); i++) {
                String TvTags = tags.getTaglines().get(i);
                List <String>  TvTagsFormatted;
                TvTagsFormatted = Arrays.asList(TvTags.split("\\s*=&%#\\s*"));
                seriesTags = new SeriesTags();
                seriesTags.setTagline(TvTagsFormatted.get(0));
                seriesTags.setType(TvTagsFormatted.get(1));
                seriesTags.setStatus(TvTagsFormatted.get(2));
                seriesTags.setVotes(TvTagsFormatted.get(3));
                seriesTags.setPopularity(TvTagsFormatted.get(4));
                seriesTags.setRuntime(TvTagsFormatted.get(5));
                seriesTags.setOriginallanguage(TvTagsFormatted.get(6));
                tvShowTags.add(seriesTags);
            }
            TextView tagline = mHeaderView.findViewById(R.id.series_tagline);
            tagline.setText(tvShowTags.get(0).getTagline());
            if (tvShowTags.get(0).getTagline().isEmpty()){
                tagline.setVisibility(View.GONE);
            }

            TextView votes = mHeaderView.findViewById(R.id.vote_count);
            String voteCountReady = tvShowTags.get(0).getVotes() + " " + getResources().getString(R.string.votes);
            if (tvShowTags.get(0).getVotes().isEmpty()){
                votes.setVisibility(View.GONE);
            }else{
                votes.setText(voteCountReady);
            }

            TextView status = mHeaderView.findViewById(R.id.series_status);
            status.setText(tvShowTags.get(0).getStatus());
            if (tvShowTags.get(0).getStatus().isEmpty()){
                status.setVisibility(View.GONE);
            }

            TextView episodeRuntime = mHeaderView.findViewById(R.id.episode_runtime);
            String runtimeReady = tvShowTags.get(0).getRuntime() + " " + getResources().getString(R.string.minutes);
            episodeRuntime.setText(runtimeReady);
            if (tvShowTags.get(0).getRuntime().isEmpty()){
                episodeRuntime.setVisibility(View.GONE);
            }

            TextView showType = mHeaderView.findViewById(R.id.showtype);
            showType.setText(tvShowTags.get(0).getType());
            if (tvShowTags.get(0).getType().isEmpty()){
                showType.setVisibility(View.GONE);
            }

            // set Original language
            Locale loc = new Locale(tvShowTags.get(0).getOriginallanguage());
            String name = loc.getDisplayLanguage(loc);
            TextView mOriginalLanguage = mHeaderView.findViewById(R.id.scrap_original_language);
            LinearLayout mOriginalLanguageContainer = mHeaderView.findViewById(R.id.scrap_original_language_container);
            if (tvShowTags.get(0).getOriginallanguage().isEmpty()) {
                mOriginalLanguage.setVisibility(View.GONE);
                mOriginalLanguageContainer.setVisibility(View.GONE);
            } else {
                mOriginalLanguage.setText(name);
            }

            // set production countries
            TextView mCountries = mHeaderView.findViewById(R.id.scrap_production_countries);
            LinearLayout mCountriesContainer = mHeaderView.findViewById(R.id.scrap_production_countries_container);
            if (showTags.getCountriesFormatted() == null || showTags.getCountriesFormatted().isEmpty()){
                mCountries.setVisibility(View.GONE);
                mCountriesContainer.setVisibility(View.GONE);
            } else {
                mCountries.setText(showTags.getCountriesFormatted());
            }

            // set spoken languages
            TextView mSpokenLanguages = mHeaderView.findViewById(R.id.scrap_spoken_languages);
            LinearLayout mSpokenLanguagesContainer = mHeaderView.findViewById(R.id.scrap_spoken_languages_container);
            if (showTags.getSpokenlanguagesFormatted() == null || showTags.getSpokenlanguagesFormatted().isEmpty()){
                mSpokenLanguages.setVisibility(View.GONE);
                mSpokenLanguagesContainer.setVisibility(View.GONE);
            } else {
                mSpokenLanguages.setText(showTags.getSpokenlanguagesFormatted());
            }

            ImageView posterView = mHeaderView.findViewById(R.id.thumbnail);
            posterView.setImageBitmap(result.bitmap);
            posterView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onPosterClick();
                }
            });

            setColor(mColor);

            ((TextView)mHeaderView.findViewById(R.id.name)).setText(show.getName());
            ImageView seriesClearLogo = mHeaderView.findViewById(R.id.show_clearlogo);
            if (tags.getClearLogo() != null){
                Picasso.get().load(tags.getClearLogo()).into(seriesClearLogo);
                mHeaderView.findViewById(R.id.name).setVisibility(View.GONE);
            } else {
                seriesClearLogo.setVisibility(View.GONE);
            }
            seriesClearLogo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
                    intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mShow);
                    intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_CLEARLOGO, true);
                    startActivity(intent);
                }
            });

            plotTv.setText(show.getPlot());
            plotTv.setMaxLines(mContext.getResources().getInteger(R.integer.show_details_max_lines));
            mSeasonPlot.setMaxLines(mContext.getResources().getInteger(R.integer.show_details_max_lines));
            plotTv.setTag(true);
            mSeasonPlot.setTag(true);

            setSeasonPlotHeader(mHeaderView.findViewById(R.id.season_plot_header));
            plotTv.setVisibility(View.VISIBLE);

            //notify browser adapter that the views are filled with data for wrap content to work
            mBrowserAdapter.notifyDataSetChanged();

            plotTv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Boolean) plotTv.getTag())) {
                        plotTv.setMaxLines(Integer.MAX_VALUE);
                        plotTv.setTag(false);
                    } else {
                        plotTv.setMaxLines(mContext.getResources().getInteger(R.integer.show_details_max_lines));
                        plotTv.setTag(true);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                }
            });

            mSeasonPlot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (((Boolean) mSeasonPlot.getTag())) {
                        mSeasonPlot.setMaxLines(Integer.MAX_VALUE);
                        mSeasonPlot.setTag(false);
                    } else {
                        mSeasonPlot.setMaxLines(mContext.getResources().getInteger(R.integer.show_details_max_lines));
                        mSeasonPlot.setTag(true);
                    }
                    mBrowserAdapter.notifyDataSetChanged();
                }
            });

            if(result.tags!=null&&result.tags.getDefaultBackdrop()!=null)
                mBackgroundSetter.set(mApplicationBackdrop, mBackgroundLoader, result.tags.getDefaultBackdrop());

        }
    }

    public class LogoSaver extends AsyncTask<ScraperImage, Void, Void> {
        public LogoSaver(Context context) {
            mContext = context;
        }
        @Override
        protected Void doInBackground(ScraperImage... params) {
            if (params != null && params.length > 0) {
                params[0].setAsDefault(mContext);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
        }
    }

    protected abstract void setSeason(TextView seasonView);

    protected abstract void setSeasonPlot(TextView seasonPlotView);

    protected abstract void setSeasonPlotHeader(TextView seasonPlotHeaderView);

    protected abstract void setSeasonAirDateContainer(LinearLayout seasonAirDateContainer);

    protected abstract void setSeriesPremieredContainer(LinearLayout seriesPremieredContainer);

    protected abstract void setColor(int color);

    protected abstract void onPosterClick();

    public void onResume(){
        super.onResume();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            log.error("onCreateContextMenu: bad menuInfo", e);
            return;
        }
        // This can be null sometimes, don't crash...
        if (info == null) {
            log.error("onCreateContextMenu: bad menuInfo");
            return;
        }

        info.position = correctedPosition(info.position);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    protected abstract Uri getPosterUri();

    @Override
    protected void postBindAdapter() {
        super.postBindAdapter();
        if(mCursor.getCount()==0) {
            //cannot be done in onLoadFinished
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    getActivity().onBackPressed();
                }
            });
        }
        else {
            if (mShow != null) {
                if (mTvShowAsyncTask != null)
                    mTvShowAsyncTask.cancel(true);

                mTvShowAsyncTask = new TvShowAsyncTask().executeOnExecutor(mSerialExecutor, getPosterUri(), mShow);
            }
            LoaderManager.getInstance(this).restartLoader(SHOW_LOADER_ID, null, this);
        }
    }

    protected int correctedPosition(int position) {
        if(mArchosGridView instanceof HeaderGridView){
            if(position<((HeaderGridView)mArchosGridView).getOffset())
                return -1;
            position = position - ((HeaderGridView)mArchosGridView).getOffset();
        }
        else if(mArchosGridView instanceof ListView){
            if(position<((ListView)mArchosGridView).getHeaderViewsCount())
                return -1;
            position = position - ((ListView)mArchosGridView).getHeaderViewsCount();
        }
        return position;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(id==0) {
            return new SeasonsLoader(getContext(), mShowId).getV4CursorLoader(true, false);
        }
        else if(id== SHOW_LOADER_ID){
            return  new TvshowLoader(getContext(), mShowId).getV4CursorLoader(true, false);
        }
        return null;
    }

}
