// Copyright 2017 Archos SA
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

package com.archos.mediacenter.video.leanback.details;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.transition.Slide;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsFragmentWithLessTopOffset;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
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
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.palette.graphics.Palette;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.utils.videodb.XmlDb;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.lists.ListDialog;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.adapters.mappers.TvshowCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.EpisodesLoader;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.NextEpisodeLoader;
import com.archos.mediacenter.video.browser.loader.TvshowLoader;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.info.MultipleVideoLoader;
import com.archos.mediacenter.video.info.SortByFavoriteSources;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.BackdropTask;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.adapter.object.Icon;
import com.archos.mediacenter.video.leanback.adapter.object.WebPageLink;
import com.archos.mediacenter.video.leanback.channels.ChannelManager;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.movies.AllMoviesGridFragment;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.IconItemPresenter;
import com.archos.mediacenter.video.leanback.presenter.PresenterUtils;
import com.archos.mediacenter.video.leanback.presenter.ScraperImageBackdropPresenter;
import com.archos.mediacenter.video.leanback.presenter.ScraperImagePosterPresenter;
import com.archos.mediacenter.video.leanback.presenter.TrailerPresenter;
import com.archos.mediacenter.video.leanback.presenter.VideoBadgePresenter;
import com.archos.mediacenter.video.leanback.presenter.WebLinkPresenter;
import com.archos.mediacenter.video.leanback.presenter.WebPageLinkPresenter;
import com.archos.mediacenter.video.leanback.scrapping.ManualVideoScrappingActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowFragment;
import com.archos.mediacenter.video.leanback.wizard.SubtitlesWizardActivity;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.ExternalPlayerResultListener;
import com.archos.mediacenter.video.utils.ExternalPlayerWithResultStarter;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.StoreRatingDialogBuilder;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.WebUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStoreImportImpl;
import com.archos.mediaprovider.video.VideoStoreInternal;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ScraperTrailer;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.VideoTags;
import com.archos.mediascraper.xml.MovieScraper3;
import com.archos.mediascraper.xml.ShowScraper4;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class VideoDetailsFragment extends DetailsFragmentWithLessTopOffset implements LoaderManager.LoaderCallbacks<Cursor>, PlayUtils.SubtitleDownloadListener, SubtitleInterface, Delete.DeleteListener, XmlDb.ResumeChangeListener, ExternalPlayerWithResultStarter {

    private static final Logger log = LoggerFactory.getLogger(VideoDetailsFragment.class);

    /** A serialized com.archos.mediacenter.video.leanback.adapter.object.Video */
    public static final String EXTRA_VIDEO = "VIDEO";
    public static final String EXTRA_LIST_ID = "list_id";
    public static final String EXTRA_FORCE_VIDEO_SELECTION = "force_video_selection";
    /** The id of the video in the MediaDB (long) */
    public static final String EXTRA_VIDEO_ID = VideoInfoActivity.EXTRA_VIDEO_ID;

    public static final String EXTRA_LAUNCHED_FROM_PLAYER = VideoInfoActivity.EXTRA_LAUNCHED_FROM_PLAYER;

    public static final String EXTRA_SHOULD_LOAD_BACKDROP = "should_load_backdrop";


    public static final int REQUEST_CODE_LOCAL_RESUME_AFTER_ADS_ACTIVITY = 985;
    public static final int REQUEST_CODE_REMOTE_RESUME_AFTER_ADS_ACTIVITY = 986;
    public static final int REQUEST_CODE_SUBTITLES_ACTIVITY                 = 987;
    public static final int REQUEST_CODE_RESUME_AFTER_ADS_ACTIVITY          = 988;
    public static final int REQUEST_CODE_PLAY_FROM_BEGIN_AFTER_ADS_ACTIVITY = 989;
    public static final int PLAY_ACTIVITY_REQUEST_CODE = 990;

    private static final int INDEX_MAIN = 0;
    private static final int INDEX_FILELIST =1;
    private   int INDEX_SUBTITLES = 1;
    private   int INDEX_FILEDETAILS = 2;

    /** pre-play subtitle download dialog is displayed only in case the wait is long than DIALOG_LAUNCH_DELAY_MS */
    private static final int DIALOG_LAUNCH_DELAY_MS = 2000;

    /** The video for which we are displaying the details. This object is updated each time we have a DB update */
    private Video mVideo;
    private static boolean mIsVideoWatched = false; // TOFIX: adding internal state since trakt sync can occur much later

    /** DB id of the video to display (in case we do not get the video object directly)*/
    private long mVideoIdFromPlayer;

    /** If we don't have the video object and we don't have the video ID, we must at least have the file path (non indexed case)*/
    private String mVideoPathFromPlayer;

    /** given by PlayerActivity when we are launched by it */
    private boolean mLaunchedFromPlayer;

    /** given by PlayerActivity when we are launched by it */
    private VideoMetadata mVideoMetadataFromPlayer;

    /** given by PlayerActivity when we are launched by it */
    private int mPlayerType;

    private long mOnlineId = -1;

    /**
     * Flag to update all when back from player, because a lot of things may have been changed in the Video Details
     * launched from the player (VideoDetailsOverlayActivity)
     * In that case we did not get the update from the Loader because we were in background
     */
    private boolean mResumeFromPlayer;

    private boolean mFirstOnResume = true;

    private Overlay mOverlay;

    private ArchosDetailsOverviewRowPresenter mOverviewRowPresenter;
    private VideoDetailsDescriptionPresenter mDescriptionPresenter;
    private ArrayObjectAdapter mAdapter;
    private FileDetailsRow mFileDetailsRow;
    private SubtitlesDetailsRow mSubtitlesDetailsRow;
    private Row mPlotAndGenresRow;
    private Row mCastRow;
    private Row mPostersRow;
    private Row mBackdropsRow;

    private DetailsOverviewRow mDetailsOverviewRow;

    private AsyncTask mDetailRowBuilderTask;
    private AsyncTask mBackdropTask;
    private AsyncTask mVideoInfoTask;
    private AsyncTask mFullScraperTagsTask;
    private AsyncTask mSubtitleFilesListerTask;
    private AsyncTask mPosterSaverTask;
    private AsyncTask mBackdropSaverTask;
    private AsyncTask mRefreshVideoBitmapTask;
    private DialogRetrieveSubtitles mDialogRetrieveSubtitles;
    private boolean mDownloadingSubs;

    List<SubtitleManager.SubtitleFile> mExternalSubtitles;

    /** the next episode, if there is one */
    Episode mNextEpisode;
    private boolean mIsTvEpisode = false;
    private boolean mHasRetrievedDetails;
    private Handler mHandler;
    private ListRow mTrailersRow;
    private AsyncTask<Video, Void, Pair<Bitmap,Video>> mThumbnailAsyncTask;
    private Bitmap mThumbnail;
    private boolean mAnimationIsRunning;

    private int mColor;
    private static int dominantColor = 0;
    private ArrayList<Video> mVideoList;
    private ArrayObjectAdapter mFileListAdapter;
    private SelectableListRow mFileListRow;
    private boolean giveOldVideo;
    private SelectableListRowPresenter mFileListRowPresenter;
    private boolean mSelectCurrentVideo;
    private Bitmap mPoster;
    private HashMap<Uri, List<SubtitleManager.SubtitleFile>> mSubtitleListCache;
    private HashMap<String, VideoMetadata> mVideoMetadateCache;
    private VideoBadgePresenter mVideoBadgePresenter;
    private boolean mShouldLoadBackdrop;
    private boolean mFirst = true;
    private VideoActionAdapter mVideoActionAdapter;
    private Uri mLastIndexed;
    private boolean mShouldUpdateRemoteResume;
    private boolean mShouldDisplayRemoveFromList;
    private boolean mShouldDisplayConfirmDelete = false;

    private boolean isFilePlayable = true;
    private int oldPos = 0;
    private int oldSelectedSubPosition = 0;

    // need to be static otherwise ActivityResultLauncher find them null
    private static Delete delete;
    private static List<Uri> deleteUrisList = null;

    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> { // result can be RESULT_OK, RESULT_CANCELED
                Context context = getActivity();
                log.debug("ActivityResultLauncher deleteLauncher: result " + result.toString());
                if (result.getResultCode() == Activity.RESULT_OK) {
                    log.debug("ActivityResultLauncher deleteLauncher: OK, deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
                    if (delete != null && deleteUrisList != null && deleteUrisList.size() >= 1) {
                        log.debug("ActivityResultLauncher deleteLauncher: calling delete.deleteOK on " + deleteUrisList.get(0));
                        delete.deleteOK(deleteUrisList.get(0));
                    }
                } else {
                    log.debug("ActivityResultLauncher deleteLauncher: NO, deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
                    if (delete != null && deleteUrisList != null && deleteUrisList.size() > 1)
                        delete.deleteNOK(deleteUrisList.get(0));
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.debug("onCreate");
        // pass the right deleteLauncher linked to activity
        FileUtilsQ.setDeleteLauncher(deleteLauncher);
        CustomApplication.resetLastVideoPlayed();

        mSubtitleListCache = new HashMap<>();
        mVideoMetadateCache = new HashMap<>();
        mShouldDisplayRemoveFromList = getActivity().getIntent().getLongExtra(EXTRA_LIST_ID, -1) != -1;

        Object transition = TransitionHelper.getEnterTransition(getActivity().getWindow());
        if(transition!=null) {
            mAnimationIsRunning = false;
            TransitionHelper.addTransitionListener(transition, new TransitionListener() {
                @Override
                public void onTransitionStart(Object transition) {
                    mAnimationIsRunning = true;
                    mOverlay.hide();
                }

                @Override
                public void onTransitionEnd(Object transition) {
                    mAnimationIsRunning = false;
                    if (mThumbnail != null) {
                        mDetailsOverviewRow.setImageBitmap(getActivity(), mThumbnail);
                        mDetailsOverviewRow.setImageScaleUpAllowed(true);

                    }
                    mOverlay.show();
                }
            });
        }

        mVideoList = new ArrayList<>();
        mHandler = new Handler();
        setTopOffsetRatio(0.5f);
        XmlDb.getInstance().addResumeChangeListener(this);
        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mDescriptionPresenter = new VideoDetailsDescriptionPresenter();
        mOverviewRowPresenter = new ArchosDetailsOverviewRowPresenter(mDescriptionPresenter);
        //be aware of a hack to avoid fullscreen overview : cf onSetRowStatus
        FullWidthDetailsOverviewSharedElementHelper helper = new FullWidthDetailsOverviewSharedElementHelper();
        helper.setSharedElementEnterTransition(getActivity(), VideoDetailsActivity.SHARED_ELEMENT_NAME, 1000);
        mOverviewRowPresenter.setListener(helper);
        mOverviewRowPresenter.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background));
        mOverviewRowPresenter.setActionsBackgroundColor(getDarkerColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background)));
        mOverviewRowPresenter.setOnActionClickedListener(mOnActionClickedListener);
        mVideoBadgePresenter = new VideoBadgePresenter(getActivity());
        mFileListAdapter = new ArrayObjectAdapter(mVideoBadgePresenter);
        mFileListRow = new SelectableListRow(new HeaderItem(getString(R.string.video_sources)),mFileListAdapter);

        // allow Video Badges Animation at end of enter transition to prevent a huge animation glitch when opening VideoDetails
        /*
        //getActivity().getWindow().getEnterTransition().addListener(new TransitionListener() {
        TransitionHelper.addTransitionListener(
                TransitionHelper.getEnterTransition(getActivity().getWindow()),
                new TransitionListener() {
                    public void onTransitionCancel(Transition transition) {}
                    public void onTransitionStart(Transition transition) {}
                    public void onTransitionPause(Transition transition) {}
                    public void onTransitionResume(Transition transition) {}
                    public void onTransitionEnd(Transition transition) {
                        if (mDescriptionPresenter != null) {
                            mDescriptionPresenter.allowVideoBadgesAnimation();
                        }
                    }
                }
        );
         */
        Intent intent = getActivity().getIntent();
        mSelectCurrentVideo = intent.getBooleanExtra(EXTRA_FORCE_VIDEO_SELECTION, false) ;

        // Easiest case when called from the leanback browser
        mVideo = (Video)intent.getSerializableExtra(EXTRA_VIDEO);

        // When called from the player we don't have the Video object, but we may have the video id if it is indexed
        if (mVideo==null) {
            mVideoIdFromPlayer = intent.getLongExtra(EXTRA_VIDEO_ID, -1);
            if (mVideoIdFromPlayer == -1) {
                mVideoPathFromPlayer = intent.getData().toString();
            }
        } else { // initialization of watched state
            mIsVideoWatched = mVideo.isWatched();
            log.debug("onCreate: init mIsVideoWatched=" + mIsVideoWatched);
        }

        mLaunchedFromPlayer = intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_PLAYER, false);
        mShouldLoadBackdrop = intent.getBooleanExtra(EXTRA_SHOULD_LOAD_BACKDROP, true);
        mPlayerType = intent.getIntExtra(VideoInfoActivity.EXTRA_PLAYER_TYPE, -1);
        mVideoMetadataFromPlayer = (VideoMetadata)intent.getSerializableExtra(VideoInfoActivity.EXTRA_USE_VIDEO_METADATA);
        
        // WORKAROUND: at least one instance of BackdropTask must be created soon in the process (onCreate ?)
        // else it does not work later.
        // --> This instance of BackdropTask() will not be used but it must be created here!
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor));

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if(item instanceof ScraperTrailer){
                    // Breaks AndroidTV acceptance but needed to launch scraper in Youtube app instead of browser
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, ((ScraperTrailer)item).getUrl());
                    ActivityInfo activityInfo = browserIntent.resolveActivityInfo(getActivity().getPackageManager(), browserIntent.getFlags());
                    if (activityInfo == null) log.debug("onCreate.onItemClicked: activity identified null");
                    else log.debug("onCreate.onItemClicked: activity identified " + activityInfo.processName);
                    if (activityInfo != null) {
                        // not used to exclude !activityInfo.processName.equals("com.google.android.tv.frameworkpackagestubs") but was preventing opening youtube on ADTV
                        log.debug("onCreate.onItemClicked: browserintent on " + ((ScraperTrailer)item).getUrl() + " with activityInfo " + activityInfo.processName);
                        startActivity(browserIntent);
                    }
                    else {
                        String url = ((ScraperTrailer)item).getUrl().toString().replace("https://www.youtube.com/watch", "https://www.youtube.com/tv#/watch");
                        log.debug("onCreate.onItemClicked: open url " + url);
                        WebUtils.openWebLink(getActivity(), url);
                    }
                }
                else if (item instanceof ScraperImage) {
                    if (row == mPostersRow) {
                        int season = -1;
                        if (mVideo instanceof Episode) {
                            season = ((Episode) mVideo).getSeasonNumber();
                        }
                        mPosterSaverTask = new PosterSaverTask(getActivity(),season).execute((ScraperImage) item);
                    } else if (row == mBackdropsRow) {
                        mBackdropSaverTask = new BackdropSaverTask(getActivity()).execute((ScraperImage) item);
                    }
                }

                else if (item instanceof Video) {
                    if (row == mFileListRow) {
                        Video old = mVideo;
                        mVideo = (Video) item;
                        log.debug("Video selected");
                        mShouldUpdateRemoteResume = true;
                        if(!smoothUpdateVideo(mVideo, old)){
                            // Full update if this is not a smooth update case
                            if (mDetailRowBuilderTask != null) {
                                mDetailRowBuilderTask.cancel(true);
                            }

                            fullyReloadVideo(mVideo,mPoster);

                        }
                        LoaderManager.getInstance(VideoDetailsFragment.this).restartLoader(1, null, VideoDetailsFragment.this);

                    }
                }
                else if (item instanceof WebPageLink) {
                    // launch of web browser
                    WebPageLink link = (WebPageLink)item;
                    WebUtils.openWebLink(getActivity(), link.getUrl());
                }
            }
        });


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
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
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
        // Cancel all the async tasks
        // please be aware that even after stopping, the async task continues until background task has finished
        for (AsyncTask task : new AsyncTask[] { mDetailRowBuilderTask, mBackdropTask, mVideoInfoTask, mFullScraperTagsTask,
                mSubtitleFilesListerTask, mPosterSaverTask, mBackdropSaverTask, mThumbnailAsyncTask}) {
            if (task!=null) {
                task.cancel(true);
            }
        }
        //do not update remote resume
        log.debug("removeParseListener");
        XmlDb.getInstance().removeParseListener(mRemoteDbObserver);
        XmlDb.getInstance().removeResumeChangeListener(this);
        super.onStop();
    }


    @Override
    public void onResume() {
        super.onResume();
        log.debug("onResume");
        mShouldUpdateRemoteResume = true;
        mOverlay.resume();
        if(mResumeFromPlayer && ArchosUtils.isAmazonApk()) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    StoreRatingDialogBuilder.displayStoreRatingDialogIfNeeded(getActivity());
                }
            },2000);
        }

        // update video in case of binge watching or repeat mode
        log.debug("onResume: mFirstOnResume " + mFirstOnResume + ", mResumeFromPlayer " + mResumeFromPlayer);
        long playerVideoId = CustomApplication.getLastVideoPlayedId();
        Uri playerVideoUri = CustomApplication.getLastVideoPlayedUri();
        if (mVideo != null) log.debug("onResume: current mVideo " + mVideo.getFileUri() + "(" + mVideo.getId() +
                "), playerVideo " + playerVideoUri + "(" + playerVideoId +"), mVideoIdFromPlayer " + mVideoIdFromPlayer +
                ", mVideoFromPlayer " + mVideoPathFromPlayer + "(" + mVideoIdFromPlayer + ")");
        else log.debug("onResume: current mVideo is null");
        if ((playerVideoId != -42 && mVideo.getId() != playerVideoId) ||
            (playerVideoUri != null && mVideo.getFileUri() != playerVideoUri)) {
            mVideoPathFromPlayer = playerVideoUri.toString();
            mVideoIdFromPlayer = playerVideoId;
            log.debug("onResume: not the same video than before (repeat mode?) target is " + mVideoPathFromPlayer);
            // get mVideo set to new video
            CursorLoader loader = new MultipleVideoLoader(getActivity(), mVideoPathFromPlayer);
            Cursor c = loader.loadInBackground();
            if (c.getCount()>0) {
                c.moveToFirst();
                mVideo = (Video) new CompatibleCursorMapperConverter(new VideoCursorMapper()).convert(c);
                log.debug("onResume: yay we get a new video " + mVideo.getFilePath());
            } else {
                log.debug("onResume: oops no video found");
            }
            c.close();
            mFirstOnResume = true; // trigger reload of the info
        }

        // Launch the first details task
        if (mFirstOnResume) {
            if (mVideo!=null) {
                if(mThumbnailAsyncTask!=null)
                    mThumbnailAsyncTask.cancel(true);
                // We have enough data to display details right now
                mDetailRowBuilderTask = new DetailRowBuilderTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,mVideo);
            }
            // We start the loader in all cases to get DB updates
            LoaderManager.getInstance(this).restartLoader(1, null, this);
        }
        // Update the details when back from player (we may have miss some DB updates while in background)
        else if (mResumeFromPlayer) {
            LoaderManager.getInstance(this).restartLoader(1, null, this);

            if (mSubtitleFilesListerTask !=null) {
                mSubtitleFilesListerTask.cancel(true);
            }
            mSubtitleFilesListerTask = new SubtitleFilesListerTask(getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,mVideo);
        }

        // reset flags
        mResumeFromPlayer = false;
        mFirstOnResume = false;

        if (mBackdropTask!=null) {
            mBackdropTask.cancel(true);
        }
        if (!mLaunchedFromPlayer) { // in player case the player is displayed in the background, not the backdrop
            mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mVideo);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        log.debug("onPause");
        mOverlay.pause();
    }

    final OnActionClickedListener mOnActionClickedListener = new OnActionClickedListener() {
        @Override
        public void onActionClicked(Action action) {
            VideoMetadata mMetadata = mVideo.getMetadata();
            isFilePlayable = true;
            // test from FileDetailsRowPresenter to check if file is playable
            if (mMetadata != null) {
                if (mMetadata.getFileSize() == 0 && mMetadata.getVideoTrack() == null && mMetadata.getAudioTrackNb() == 0) {
                    // sometimes metadata are set to zero but the file is there, can be due to libavosjni not loaded
                    isFilePlayable = false;
                }
            }
            if(action.getId() == VideoActionAdapter.ACTION_LOCAL_RESUME){
                if (isFilePlayable) {
                    startAds(REQUEST_CODE_LOCAL_RESUME_AFTER_ADS_ACTIVITY);
                } else {
                    Toast.makeText(getActivity(), R.string.player_err_cantplayvideo, Toast.LENGTH_SHORT).show();
                }
            }
            if (action.getId() == VideoActionAdapter.ACTION_RESUME) {
                if (isFilePlayable) {
                    startAds(REQUEST_CODE_RESUME_AFTER_ADS_ACTIVITY);
                } else {
                    Toast.makeText(getActivity(), R.string.player_err_cantplayvideo, Toast.LENGTH_SHORT).show();
                }
            }
            if (action.getId() == VideoActionAdapter.ACTION_REMOTE_RESUME) {
                startAds(REQUEST_CODE_REMOTE_RESUME_AFTER_ADS_ACTIVITY);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_PLAY_FROM_BEGIN || action.getId() == VideoActionAdapter.ACTION_PLAY) {
                if (isFilePlayable) {
                    startAds(REQUEST_CODE_PLAY_FROM_BEGIN_AFTER_ADS_ACTIVITY);
                } else {
                    Toast.makeText(getActivity(), R.string.player_err_cantplayvideo, Toast.LENGTH_SHORT).show();
                }
            }
            else if (action.getId() == VideoActionAdapter.ACTION_LIST_EPISODES) {
                // In this case mVideo is a tvshow Episode
                Episode mEpisode = (Episode) mVideo;
                // ShowId is obtained via EpisodeTags
                EpisodeTags tagsE = (EpisodeTags) mVideo.getFullScraperTags(getActivity());
                long mShowId = tagsE.getShowId();
                // TvshowLoader is a CursorLoader
                TvshowLoader mTvshowLoader = new TvshowLoader(getActivity(), mShowId);
                Cursor mCursor = mTvshowLoader.loadInBackground();
                if(mCursor != null && mCursor.getCount()>0) {
                    mCursor.moveToFirst();
                    TvshowCursorMapper mTvShowCursorMapper = new TvshowCursorMapper();
                    mTvShowCursorMapper.bindColumns(mCursor);
                    Tvshow mTvshow = (Tvshow) mTvShowCursorMapper.bind(mCursor);
                    final Intent intent = new Intent(getActivity(), TvshowActivity.class);
                    intent.putExtra(TvshowFragment.EXTRA_TVSHOW, mTvshow);
                    // Launch next activity with slide animation
                    // Starting from lollipop we need to give an empty "SceneTransitionAnimation" for this to work
                    mOverlay.hide(); // hide the top-right overlay else it slides across the screen!
                    startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle());
                    // Delay the finish the "old" activity, else it breaks the animation
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            Activity activity = getActivity();
                            if (activity != null) activity.finish(); // better safe than sorry
                        }
                    }, 1000);
                 }
            }
            else if (action.getId() == VideoActionAdapter.ACTION_NEXT_EPISODE) {
                final Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO, mNextEpisode);
                intent.putExtra(VideoDetailsActivity.SLIDE_TRANSITION_EXTRA, true);
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
            else if (action.getId() == VideoActionAdapter.ACTION_INDEX) {
                VideoStore.requestIndexing(mVideo.getFileUri(), getActivity());
            }
            else if (action.getId() == VideoActionAdapter.ACTION_UNINDEX) {
                DbUtils.markAsHiddenByUser(getActivity(), mVideo);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_DELETE) {
                mShouldDisplayConfirmDelete = true;
                
                ((VideoActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(mVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_CONFIRM_DELETE) {
                deleteFile_async(mVideo);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_SCRAP) {
                if (!NetworkState.isNetworkConnected(getActivity())) {
                    Toast.makeText(getActivity(), R.string.scrap_no_network, Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(getActivity(), ManualVideoScrappingActivity.class);
                    intent.putExtra(ManualVideoScrappingActivity.EXTRA_VIDEO, mVideo);
                    startActivity(intent);
                }
            } else if (action.getId() == VideoActionAdapter.ACTION_UNSCRAP) {
                DbUtils.deleteScraperInfo(getActivity(),mVideo); // TODO should probably be in an async task
            }
            else if (action.getId() == VideoActionAdapter.ACTION_HIDE) {
                DbUtils.markAsHiddenByUser(getActivity(), mVideo);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_ADD_TO_LIST) {
                Bundle bundle = new Bundle();
                bundle.putSerializable(ListDialog.EXTRA_VIDEO, mVideo);
                ListDialog dialog = new ListDialog();
                dialog.setArguments(bundle);
                dialog.show(getParentFragmentManager(), "list_dialog");
            }
            else if (action.getId() == VideoActionAdapter.ACTION_REMOVE_FROM_LIST) {
                BaseTags metadata = mVideo.getFullScraperTags(getActivity());
                boolean isEpisode = metadata instanceof EpisodeTags;
                VideoStore.VideoList.VideoItem videoItem  = new VideoStore.VideoList.VideoItem(-1,!isEpisode?(int)metadata.getOnlineId():-1, isEpisode?(int)metadata.getOnlineId():-1, VideoStore.List.SyncStatus.STATUS_DELETED);
                getActivity().getContentResolver().update(VideoStore.List.getListUri(getActivity().getIntent().getLongExtra(EXTRA_LIST_ID,-1)), videoItem.toContentValues(),  videoItem.getDBWhereString(), videoItem.getDBWhereArgs());
                mShouldDisplayRemoveFromList = false;
                mDetailsOverviewRow.setActionsAdapter(new VideoActionAdapter(getActivity(), mVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode));
                TraktService.sync(ArchosUtils.getGlobalContext(), TraktService.FLAG_SYNC_AUTO);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_UNHIDE) {
                DbUtils.markAsNotHiddenByUser(getActivity(), mVideo);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_MARK_AS_WATCHED) {
                int offset = 0;

                if (mVideo.getResumeMs() > 0)
                    offset--;
                
                if (mVideo.getRemoteResumeMs() > 0 && mVideo.getRemoteResumeMs() != mVideo.getResumeMs())
                    offset--;

                mOverviewRowPresenter.moveSelectedPosition(offset);
                DbUtils.markAsRead(getActivity(), mVideo);
                getActivity().setResult(Activity.RESULT_OK);
            }
            else if (action.getId() == VideoActionAdapter.ACTION_MARK_AS_NOT_WATCHED) {
                int offset = 0;

                if (mVideo.getResumeMs() > 0)
                    offset--;
                
                if (mVideo.getRemoteResumeMs() > 0 && mVideo.getRemoteResumeMs() != mVideo.getResumeMs())
                    offset--;

                mOverviewRowPresenter.moveSelectedPosition(offset);
                DbUtils.markAsNotRead(getActivity(), mVideo);
                getActivity().setResult(Activity.RESULT_OK);
            }
        }
    };

    //--------------------------------------------------------------------
    // Implements LoaderCallbacks<Cursor>
    // We use a SingleVideoLoader to get updated from the DB using the Loader framework
    //--------------------------------------------------------------------
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        // If we don't have the video object
        if (mVideo==null) {
            if (mVideoIdFromPlayer >=0) {
                log.debug("onCreateLoader: mVideo is null, working from mVideoIdFromPlayer " + mVideoIdFromPlayer);
                return new MultipleVideoLoader(getActivity(), mVideoIdFromPlayer);
            } else {
                log.debug("onCreateLoader: mVideo is null, working from mVideoPathFromPlayer " + mVideoPathFromPlayer);
                return new MultipleVideoLoader(getActivity(), mVideoPathFromPlayer);
            }
        }
        // If we already have the Video object
        else if (mVideo.isIndexed()) {
            log.debug("onCreateLoader: mVideo is known and indexed with id " + mVideo.getId());
            return new MultipleVideoLoader(getActivity(), mVideo.getId());
        } else {
            log.debug("onCreateLoader: mVideo is known not indexed with path " + mVideo.getFilePath());
            return new MultipleVideoLoader(getActivity(), mVideo.getFilePath());
        }
    }

    private XmlDb.ParseListener mRemoteDbObserver = new XmlDb.ParseListener(){

        @Override
        public void onParseFail(XmlDb.ParseResult parseResult) {
            XmlDb.getInstance().removeParseListener(this);
        }

        @Override
        public void onParseOk(XmlDb.ParseResult result) {

            log.debug("onParseOk");
            XmlDb xmlDb = XmlDb.getInstance();
            //xmlDb.removeParseListener(this);
            if(getActivity()==null) { //too late

                log.debug("getActivity is null, leaving");
                return;
            }
            VideoDbInfo videoInfo = null;
            if (result.success) {
                log.debug("result.success");
                videoInfo = xmlDb.getEntry(mVideo.getFileUri());
                if(videoInfo!=null){
                    log.debug("videoInfo!=null "+videoInfo.resume);
                    mVideo.setRemoteResumeMs(videoInfo.resume);
                    // Update the action adapter if there is a network resume
                    if (mDetailsOverviewRow!=null) {
                        ((VideoActionAdapter) mDetailsOverviewRow.getActionsAdapter()).updateRemoteResume(getActivity(), mVideo);
                    }
                }
            }

        }

    };
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (getActivity() == null) return;
        long start = System.currentTimeMillis();
        Video oldVideoObject = mVideo;
        List<Video> oldVideoList = new ArrayList<>(mVideoList);
        mVideoList.clear();

        // Getting an empty cursor here means that the video is not indexed
        if (cursor.getCount()<1) {
            // we're changing from indexed case to non-indexed case (user probably unindexed file some milliseconds ago)
            if (oldVideoObject!=null) {
                // building a new unindexed video object using the Uri and name we had in the previous video object
                mVideo = new NonIndexedVideo( oldVideoObject.getStreamingUri(),oldVideoObject.getFileUri(), oldVideoObject.getName(), oldVideoObject.getPosterUri() );

                // If the video was indexed we did a query based on its ID.
                // It is not indexed anymore hence we need to change our query and have it based on the path now
                // (else a new indexing would need to no cursor loader update callback)
                if (oldVideoObject.isIndexed()) {
                    LoaderManager.getInstance(this).restartLoader(1, null, this);
                }
            }
            // If we have no Video object (case it's launched from player with path only)
            else {
                mVideo = new NonIndexedVideo(mVideoPathFromPlayer); // TODO corner case BUG: gte only cryptic name from url for non-indexed UPnP when Details are opened from player
            }

            //TODO remove sources list
        }
        else {
            log.debug("onLoadFinished: found " + cursor.getCount() + " videos");
            // Build video objects from the new cursor data

            mVideoBadgePresenter.setDisplay3dBadge(false);
            cursor.moveToFirst();
            mVideo = null;
            VideoCursorMapper cursorMapper = new VideoCursorMapper();
            cursorMapper.publicBindColumns(cursor);
            do {

                Video video =  (Video) cursorMapper.publicBind(cursor);
                if(video.is3D())
                    mVideoBadgePresenter.setDisplay3dBadge(true);
                mOnlineId = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID));
                log.debug("onLoadFinished: online id " + mOnlineId);
                mVideoList.add(video);
                log.debug("onLoadFinished: found video : " + video.getFileUri());
                if(!mSelectCurrentVideo){ // get most advanced video
                    if(video.getLastPlayed()>0&&mVideo==null||mVideo!=null&&video.getLastPlayed()>mVideo.getLastPlayed()){
                        mVideo = video;
                    }
                }
                else if(oldVideoObject!=null&&video.getFileUri().equals(oldVideoObject.getFileUri())){
                    mVideo = video;
                }
            }while (cursor.moveToNext());
            Collections.sort(mVideoList, new SortByFavoriteSources(oldVideoList));

            mSelectCurrentVideo = true;
            if(mVideo == null)
                mVideo = mVideoList.get(0);
            if(mVideoList.size()>1){
                int i = 0;
                for(Video video : mVideoList) {
                    if(mFileListAdapter.size()>i)
                        mFileListAdapter.replace(i, video);
                    else
                        mFileListAdapter.add(i, video);
                    i++;
                }
                if(i<mFileListAdapter.size()){
                    mFileListAdapter.removeItems(i,mFileListAdapter.size() -i);
                }
            }


        }

        // Keep the video decoder metadata if we already have it (we don't want to compute it again, it can be long)
        VideoMetadata alreadyComputedVideoMetadata = mVideoMetadateCache.get(mVideo.getFilePath());

        // Keep the video decoder metadata if we already have it
        if(alreadyComputedVideoMetadata!=null)
            mVideo.setMetadata(alreadyComputedVideoMetadata);
        mVideoBadgePresenter.setSelectedUri(mVideo.getFileUri());

        if(!smoothUpdateVideo(mVideo, giveOldVideo ? oldVideoObject : null)||mVideoList.size()>1&&mAdapter!=null&&mAdapter.indexOf(mFileListRow)==-1) {

            if (mDetailRowBuilderTask != null) {
                mDetailRowBuilderTask.cancel(true);
            }
            if (mThumbnailAsyncTask != null)
                mThumbnailAsyncTask.cancel(true);
            mDetailRowBuilderTask = new DetailRowBuilderTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,mVideo);
        }

        giveOldVideo = true;
    }


    public void requestIndexAndScrap(){

        if (!PrivateMode.isActive()) {
            if (mVideo.getId() == -1 && mVideo.getFileUri() != null && !mVideo.getFileUri().equals(mLastIndexed)) {
                mLastIndexed = mVideo.getFileUri();
                if(UriUtils.isIndexable(mVideo.getFileUri())) {
                    final Uri uri = mVideo.getFileUri();
                    new Thread() {
                        public void run() {
                            if (!VideoStoreImportImpl.isNoMediaPath(uri))
                                VideoStore.requestIndexing(uri, getActivity(),false);
                        }
                    }.start();
                }
            }
        }
    }

    /**
     * display current video and returns whether we should or shouldn't rebuild full rows
     * @param currentVideo
     * @param oldVideoObject
     * @return
     */
    private boolean smoothUpdateVideo(Video currentVideo, Video oldVideoObject) {
        log.debug("smoothUpdateVideo");
        boolean smoothUpdate = false;
        // Check if we really need to update the fragment
        boolean needToUpdateDetailsOverview;
        if (mDetailsOverviewRow==null && mDetailRowBuilderTask==null) { // if no row yet and no async task building it yet
            needToUpdateDetailsOverview = true; // first time
        } else {
            needToUpdateDetailsOverview = foundDifferencesRequiringDetailsUpdate(oldVideoObject, currentVideo); // update
        }

        // Update if needed
        if (needToUpdateDetailsOverview) {
            requestIndexAndScrap();
            // mDetailsOverviewRow can be null and need to be initialized but perhaps should call fullyReloadVideo instead
            if (mDetailsOverviewRow == null) mDetailsOverviewRow = new DetailsOverviewRow(currentVideo);
            // First check if we can do a smooth/smart update (when unscrapping and/or unindexing)
            // smooth update when unscrapping
            if (oldVideoObject!=null && oldVideoObject.hasScraperData() && !currentVideo.hasScraperData()) {
                // remove scraper related rows
                if(mAdapter.indexOf(mFileListRow)>=0) {
                    mAdapter.remove(mFileListRow);
                    INDEX_SUBTITLES--;
                    INDEX_FILEDETAILS--;
                }
                mAdapter.remove(mPlotAndGenresRow);
                mAdapter.remove(mCastRow);
                mAdapter.remove(mTrailersRow);
                mAdapter.remove(mPostersRow);
                mAdapter.remove(mBackdropsRow);
                // update details presenter and actions
                mDescriptionPresenter.update(currentVideo);
                if(mDetailsOverviewRow.getActionsAdapter()==null)
                    mDetailsOverviewRow.setActionsAdapter(new VideoActionAdapter(getActivity(), currentVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode));
                else ((VideoActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(currentVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode);
                // update poster
                mDetailsOverviewRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video));
                mDetailsOverviewRow.setImageScaleUpAllowed(false);
                smoothUpdate = true;
            }
            // smooth update when unindexing
            if (oldVideoObject!=null && oldVideoObject.isIndexed() && !currentVideo.isIndexed()) {
                // update details presenter and actions
                mDescriptionPresenter.update(currentVideo);
                if(mDetailsOverviewRow.getActionsAdapter()==null)
                    mDetailsOverviewRow.setActionsAdapter(new VideoActionAdapter(getActivity(), currentVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode));
                else ((VideoActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(currentVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode);

                // update poster
                mDetailsOverviewRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video));
                mDetailsOverviewRow.setImageScaleUpAllowed(false);
                smoothUpdate = true;
            }

            if (smoothUpdate) {
                mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                
                mVideoBadgePresenter.setSelectedBackgroundColor(mColor);
                mOverviewRowPresenter.updateBackgroundColor(mColor);
                mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));

                for (Presenter pres : mAdapter.getPresenterSelector().getPresenters()){
                    if (pres instanceof BackgroundColorPresenter)
                        ((BackgroundColorPresenter) pres).setBackgroundColor(mColor);
                }
                // this is required to remove backdrop after removal of description
                if (needToUpdateDetailsOverview) mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(currentVideo);

            }
        }else {
            smoothUpdate = true;
        }
        if(mShouldUpdateRemoteResume) {
            //before that, we couldn't be sure to have the right file uri. Now that we are, try to get remote resume
            currentVideo.setRemoteResumeMs(-1);//reset remote resume
            if (!mLaunchedFromPlayer && !FileUtils.isLocal(currentVideo.getFileUri()) && UriUtils.isCompatibleWithRemoteDB(currentVideo.getFileUri())) {
                log.debug("addParseListener");
                XmlDb.getInstance().addParseListener(mRemoteDbObserver);
                XmlDb.getInstance().parseXmlLocation(currentVideo.getFileUri());
            }
            mShouldUpdateRemoteResume = false;
        }else if(oldVideoObject!=null) {
            // should not set the currentVideo remoteResumeMs to oldVideoObject one otherwise we inherit old network resume and get two buttons
            //currentVideo.setRemoteResumeMs(oldVideoObject.getRemoteResumeMs());
        }
        mHasRetrievedDetails = true;
        return smoothUpdate;
    }

    private boolean foundDifferencesRequiringDetailsUpdate(Video v1, Video v2) {
        if (v1==null || v2==null) {log.debug("foundDifferencesRequiringDetailsUpdate null"); mShouldLoadBackdrop = true; return true;}
        if (v1.getClass() != v2.getClass()) {log.debug("foundDifferencesRequiringDetailsUpdate class"); mShouldLoadBackdrop = true; return true;}
        if (v1.getId() != v2.getId()) {log.debug("foundDifferencesRequiringDetailsUpdate id"); mShouldLoadBackdrop = true; return true;}
        if (v1.hasScraperData() != v2.hasScraperData()) {log.debug("foundDifferencesRequiringDetailsUpdate hasScraperData"); mShouldLoadBackdrop = true; return true;}
        if (v1.getResumeMs() != v2.getResumeMs()) {log.debug("foundDifferencesRequiringDetailsUpdate resumeMs"); return true;}
        if (v1.isWatched() != v2.isWatched()) {log.debug("foundDifferencesRequiringDetailsUpdate isWatched"); return true;}
        if (v1.isUserHidden() != v2.isUserHidden()) {log.debug("foundDifferencesRequiringDetailsUpdate isUserHidden"); return true;}
        //if (v1.subtitleCount() != v2.subtitleCount()) {log.debug("foundDifferencesRequiringDetailsUpdate subtitleCount"); return true;}
        //if (v1.externalSubtitleCount() != v2.externalSubtitleCount()) {log.debug("foundDifferencesRequiringDetailsUpdate externalSubtitleCount"); return true;}
        return false;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {}

    @Override
    public void onResumeChange(Uri videoFile, int resumePercent) {
        log.debug("onResumeChange()");
        if(mHasRetrievedDetails&&isAdded()&&!isDetached()&&videoFile.equals(mVideo.getFileUri())){
            VideoDbInfo info = XmlDb.getEntry(videoFile);
            if(info!=null) {
                mVideo.setRemoteResumeMs(info.resume);
                // Update the action adapter if there is a network resume
                if (mDetailsOverviewRow != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mDetailsOverviewRow.getActionsAdapter()==null)
                                mDetailsOverviewRow.setActionsAdapter(new VideoActionAdapter(getActivity(), mVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode));
                            else ((VideoActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(mVideo, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode);
                        }
                    });

                }
            }
        }
    }

    @Override
    public void startActivityWithResultListener(Intent intent) {
        startActivityForResult(intent, PLAY_ACTIVITY_REQUEST_CODE);
    }

    //putting in thread to avoid async tasks to be locked

    private class ThumbnailAsyncTask extends AsyncTask<Video, Void, Pair<Bitmap,Video>> {

            @Override
            protected Pair<Bitmap,Video> doInBackground(Video... videos) {
                Video video = videos[0];

                Bitmap bitmap = null;
                try {
                    Uri imageUri = null;
                    if (video.isIndexed()) {
                        imageUri = ThumbnailRequestHandler.buildUri(video.getId()); // Thumbnail
                    }
                    if (imageUri!=null) {
                        bitmap = Picasso.get()
                                .load(imageUri)
                                .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                                .centerCrop()
                                .get();
                    }
                }
                catch (IOException e) {
                    log.error("DetailsOverviewRow Picasso load exception", e);
                }
                return new Pair<>(bitmap, video);
            }

            @Override
            protected void onPostExecute(Pair<Bitmap,Video> result) {
                if(isCancelled())
                    return;
                if(mVideo.getPosterUri()==null||mVideo.getPosterUri().equals(result.second.getPosterUri())) {
                    Bitmap bitmap = result.first;
                    if (result.first != null) {
                        if (mVideo.isWatched() || mIsVideoWatched)
                            bitmap = PresenterUtils.addWatchedMark(bitmap, getContext());
                        if(!mAnimationIsRunning) {
                            mDetailsOverviewRow.setImageBitmap(getActivity(), bitmap);
                            mDetailsOverviewRow.setImageScaleUpAllowed(true);
                        }
                        else
                            mThumbnail = bitmap;
                    } else {
                        mDetailsOverviewRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video));
                        mDetailsOverviewRow.setImageScaleUpAllowed(false);
                    }
                }

            }
    }

    /**
     * will reload every row with video params EXCEPT poster
     * @param video
     */

    private void fullyReloadVideo(Video video, Bitmap poster) {
        log.debug("fullyReloadVideo: mShouldLoadBackdrop=" + mShouldLoadBackdrop);
        if(mShouldLoadBackdrop)
            BackgroundManager.getInstance(getActivity()).setDrawable(new ColorDrawable(VideoInfoCommonClass.getDarkerColor(mColor)));
        mSubtitlesDetailsRow = new SubtitlesDetailsRow(getActivity(), video, null);
        mFileDetailsRow = new FileDetailsRow(getActivity(), video, mPlayerType);
        if(mDetailsOverviewRow==null)
            mDetailsOverviewRow = new DetailsOverviewRow(video);
        else
            mDetailsOverviewRow.setItem(video);
        if(mAdapter == null) {
            mFileListRowPresenter = new SelectableListRowPresenter();

            ClassPresenterSelector ps = new ClassPresenterSelector();
            ps.addClassPresenter(DetailsOverviewRow.class, mOverviewRowPresenter);
            ps.addClassPresenter(SubtitlesDetailsRow.class, new SubtitlesDetailsRowPresenter(VideoDetailsFragment.this, mColor));
            ps.addClassPresenter(FileDetailsRow.class,  new FileDetailsRowPresenter(mColor));
            ps.addClassPresenter(ListRow.class, new ListRowPresenter());
            ps.addClassPresenter(SelectableListRow.class, mFileListRowPresenter);
            ps.addClassPresenter(PlotAndGenresRow.class, new PlotAndGenresRowPresenter(14,mColor)); // 14 lines max to fit on screen
            ps.addClassPresenter(CastRow.class, new CastRowPresenter(14,mColor)); // 14 lines max to fit on screen
            mAdapter = new ArrayObjectAdapter(ps);
            setAdapter(mAdapter);
            // Buttons

            mAdapter.add(INDEX_MAIN, mDetailsOverviewRow);
            if(mVideoList.size()>1) {
                mAdapter.add(INDEX_FILELIST, mFileListRow);
                INDEX_SUBTITLES ++;
                INDEX_FILEDETAILS ++;
            }
            mAdapter.add(INDEX_SUBTITLES, mSubtitlesDetailsRow);
            mAdapter.add(INDEX_FILEDETAILS, mFileDetailsRow);
        }
        else{
           // mAdapter.replace(INDEX_MAIN, mDetailsOverviewRow);
            if(mVideoList.size()>1) {

                if(mAdapter.indexOf(mFileListRow)==-1) {
                    mAdapter.replace(INDEX_FILELIST, mFileListRow);
                    INDEX_SUBTITLES ++;
                    INDEX_FILEDETAILS ++;
                }
            }
            if(mAdapter.size()>INDEX_SUBTITLES)
                mAdapter.replace(INDEX_SUBTITLES, mSubtitlesDetailsRow);
            else
                mAdapter.add(INDEX_SUBTITLES, mSubtitlesDetailsRow);

            if(mAdapter.size()>INDEX_FILEDETAILS)
                mAdapter.replace(INDEX_FILEDETAILS, mFileDetailsRow);
            else
                mAdapter.add(INDEX_FILEDETAILS, mFileDetailsRow);
            if(mAdapter.size()>INDEX_FILEDETAILS+1)
                mAdapter.removeItems(INDEX_FILEDETAILS+1, mAdapter.size()-INDEX_FILEDETAILS-1);
        }

        mVideoBadgePresenter.setSelectedBackgroundColor(mColor);
        mOverviewRowPresenter.updateBackgroundColor(mColor);
        mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));

        //set background color :
        for(Presenter pres : mAdapter.getPresenterSelector().getPresenters()){
            if(pres instanceof BackgroundColorPresenter)
                ((BackgroundColorPresenter) pres).setBackgroundColor(mColor);
        }

        if(mVideoList.size()>1) {
            //selectItem
            mFileListRow.setStartingSelectedPosition(mVideoList.indexOf(video));
        }

        mIsTvEpisode = video instanceof Episode;

        if(mDetailsOverviewRow.getActionsAdapter()==null || !(mDetailsOverviewRow.getActionsAdapter() instanceof  VideoActionAdapter))
            mDetailsOverviewRow.setActionsAdapter(new VideoActionAdapter(getActivity(), video, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode));
        else{
            ((VideoActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(video, mLaunchedFromPlayer, mShouldDisplayRemoveFromList, mShouldDisplayConfirmDelete, mNextEpisode, mIsTvEpisode);
        }

        // Plot, Cast, Posters, Backdrops, Links rows will be added after, once we get the Scraper Tags

        // Start the scraper related task (backdrop, poster list, backdrop list, web links)

        if (video.hasScraperData()) {
            if(mFullScraperTagsTask!=null&&!mFullScraperTagsTask.isCancelled())
                mFullScraperTagsTask.cancel(true);
            mFullScraperTagsTask = new FullScraperTagsTask(getActivity()).execute(video);
        }
        else {
            // Backdrop must be done in non-scrap case because there may be a backdrop remaining from previous scrap data than need to be removed (when removing scrap data)
            mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(video);
        }

        // Check subtitles. Better to do it before VideoInfoTask because it should be quicker and it is displayed higher in the Fragment
        if(mSubtitleListCache.get(video.getFileUri())==null)
             mSubtitleFilesListerTask = new SubtitleFilesListerTask(getActivity()).execute(video);
        else {
            updateSubtitleRowWhenReady();
        }

        // Start the video info task only now that the DB UI is ready to setup
        // special case : for upnp:// we need the streaming uri (http)
        String path = video.getFilePath();
        if(mVideoInfoTask!=null)
            mVideoInfoTask.cancel(true);
        if(mVideoMetadateCache.containsKey(path)){
            video.setMetadata(mVideoMetadateCache.get(path));
            updateMetadataWhenReady();
            updateSubtitleRowWhenReady();

        }
        //do not execute file info task when torrent file
        if(video.getFileUri() == null || mLaunchedFromPlayer) // avoid NPE on .getLastPathSegment()
            mVideoInfoTask = new VideoInfoTask().execute(video);
        else if(!video.getFileUri().getLastPathSegment().endsWith("torrent"))
            mVideoInfoTask = new VideoInfoTask().execute(video);

        if (poster == null) {
            log.debug("fullyReloadVideo: no poster, generate it");

            mDetailsOverviewRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video));
            mDetailsOverviewRow.setImageScaleUpAllowed(false);
            mThumbnailAsyncTask = new ThumbnailAsyncTask().execute(mVideo);
        }else{
            log.debug("fullyReloadVideo: should put watched mark on poster " + (mVideo.isWatched() || mIsVideoWatched));
            if (mVideo.isWatched() || mIsVideoWatched)
                poster = PresenterUtils.addWatchedMark(poster, getContext());
            mDetailsOverviewRow.setImageBitmap(getActivity(), poster);
            mDetailsOverviewRow.setImageScaleUpAllowed(true);
        }

    }

    //--------- ------------------------------------------
    private class DetailRowBuilderTask extends AsyncTask<Video, Integer, Bitmap> {

        @Override
        protected Bitmap doInBackground(Video... videos) {
            Video video = videos[0];
            mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
            mVideoBadgePresenter.setSelectedBackgroundColor(mColor);

            Uri imageUri = null;
            if (video.getPosterUri()!=null) {
                imageUri = video.getPosterUri();
            }
            else if (video.isIndexed()) {
                imageUri = ThumbnailRequestHandler.buildUriNoThumbCreation(video.getId()); // Thumbnail
            }
            if(imageUri != null){
                try {
                    Bitmap bitmap  = Picasso.get().load(imageUri)
                            .noFade() // no fade since we are using activity transition anyway
                            .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                            .centerCrop()
                            .get();
                    if(bitmap!=null) {
                        return bitmap;
                    }
                } catch (IOException e) {
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if(isCancelled())
                return;
            mPoster = result;
            if(result!=null) {
                Palette palette = Palette.from(result).generate();
                if (palette.getDarkVibrantSwatch() != null)
                    mColor = palette.getDarkVibrantSwatch().getRgb();
                else if (palette.getDarkMutedSwatch() != null)
                    mColor = palette.getDarkMutedSwatch().getRgb();
                else
                    mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                dominantColor = mColor;
                mVideoBadgePresenter.setSelectedBackgroundColor(mColor);
                mOverviewRowPresenter.updateBackgroundColor(mColor);
                mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));
            }
            fullyReloadVideo(mVideo,result);
        }
    }

    private class VideoInfoTask extends AsyncTask<Video, Integer, VideoMetadata> {
        @Override
        protected VideoMetadata doInBackground(Video... videos) {
            Video video = videos[0];
            String startingPath= video.getFilePath();
            if(mLaunchedFromPlayer && mVideoMetadataFromPlayer!=null && mVideoMetadataFromPlayer.getVideoTrack()!=null)
                return mVideoMetadataFromPlayer;
            else if(mVideoMetadateCache.containsKey(startingPath)){
                log.debug("metadata retrieved from cache "+startingPath);
                return mVideoMetadateCache.get(startingPath);
            }
            else {
                VideoMetadata videoMetaData = VideoInfoCommonClass.retrieveMetadata(video,getActivity());
                if(video!=null&&video.isIndexed())
                    videoMetaData.save(getActivity(), startingPath);
                mVideoMetadateCache.put(startingPath, videoMetaData);
                return videoMetaData;
            }
        }

        protected void onPostExecute(VideoMetadata videoInfo) {
            if(isCancelled())
                return;
            // Update the video object with the computed metadata
            if(mVideo!=null)
                mVideo.setMetadata(videoInfo);

            // Integrated subtitle list is in the metadata
            updateSubtitleRowWhenReady();
            updateMetadataWhenReady();
        }
    }

    private void updateMetadataWhenReady(){
        if(mVideo.getMetadata()!=null) {
            // Tell presenter to update the badges according to the metadata
            mDescriptionPresenter.displayActualVideoBadges(mVideo);

            // update the details row and replace it in the adapter
            mFileDetailsRow = new FileDetailsRow(getActivity(), mVideo, mPlayerType);
            mAdapter.replace(INDEX_FILEDETAILS, mFileDetailsRow);
        }
    }

    private class FullScraperTagsTask extends AsyncTask<Video, Void, BaseTags> {
        private final Activity mActivity;
        List<ScraperImage> mPosters;
        List<ScraperImage> mBackdrops;
        private List<ScraperTrailer> mTrailers;

        public FullScraperTagsTask(Activity activity){
            mActivity = activity;
        }
        private Activity getActivity(){
            return mActivity;
        }
        @Override
        protected BaseTags doInBackground(Video... videos) {
            Video video = videos[0];
            BaseTags tags = video.getFullScraperTags(getActivity());

            // Posters
            if (tags!=null && !isCancelled()) {
                mPosters = tags.getAllPostersInDb(getActivity());
            } else {
                mPosters = null;
            }
            // Backdrops
            if (tags!=null && !isCancelled()) {
                mBackdrops = tags.getAllBackdropsInDb(getActivity());
            } else {
                mBackdrops = null;
            }
            if (tags!=null && !isCancelled())
                mTrailers = tags.getAllTrailersInDb(getActivity());
            else
                mTrailers = null;
            // Check if we have the next episode
            if (tags instanceof EpisodeTags) {
                // Using a CursorLoader but outside of the LoaderManager : need to make sure the Looper is ready
                if (Looper.myLooper()==null) Looper.prepare();
                CursorLoader loader = new NextEpisodeLoader(getActivity(), (EpisodeTags)tags);
                Cursor c = loader.loadInBackground();
                if (c.getCount()>0) {
                    c.moveToFirst();
                    mNextEpisode = (Episode)new CompatibleCursorMapperConverter(new VideoCursorMapper()).convert(c);
                }
                c.close();
            }

            return tags;
        }

        protected void onPostExecute(BaseTags tags) {
            log.debug("onPostExecute");
            if(isCancelled()||getActivity().isDestroyed())
                return;
            // Update the action adapter if there is a next episode
            ((VideoActionAdapter) mDetailsOverviewRow.getActionsAdapter()).setNextEpisodeStatus(mNextEpisode != null);
            ((VideoActionAdapter) mDetailsOverviewRow.getActionsAdapter()).setListEpisodesStatus(mIsTvEpisode);
            // Launch backdrop task in BaseTags-as-arguments mode
            if (mBackdropTask!=null) {
                mBackdropTask.cancel(true);
            }
            if (tags!=null && !mLaunchedFromPlayer) { // in player case the player is displayed in the background, not the backdrop
                if(mShouldLoadBackdrop) {
                    log.debug("onPostExecute: loading backdrop");
                    mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,tags);
                    mShouldLoadBackdrop = false;
                } else {
                    log.debug("onPostExecute: should not load backdrop");
                }
            }
            if (tags!=null) {
                // Plot & Genres
                final String plot = tags.getPlot();
                String genres = null;
                if (tags instanceof VideoTags) {
                    genres = ((VideoTags) tags).getGenresFormatted();
                }
                // Keep it simple: we do not display the row if plot==null && genres!=null (very unlikely and not a big deal)
                if (plot != null && !plot.isEmpty()) {
                    if(mPlotAndGenresRow!=null&&mAdapter.indexOf(mPlotAndGenresRow)!=-1)
                        mAdapter.remove(mPlotAndGenresRow);

                    mPlotAndGenresRow = new PlotAndGenresRow(getString(R.string.scrap_plot), plot, genres);
                    mAdapter.add(mPlotAndGenresRow);
                }

                // Cast
                SpannableString cast = tags.getSpannableActorsFormatted();
                // If cast is null and this is an episode, get the cast of the Show
                if (cast == null & tags instanceof EpisodeTags) {
                    ShowTags showTags = ((EpisodeTags) tags).getShowTags();
                    cast = showTags != null ? showTags.getSpannableActorsFormatted() : null;
                }
                // Keep it simple: we do not display the row if cast==null && directors!=null (very unlikely and not a big deal)
                if (cast != null && cast.length() > 0) {
                    if(mCastRow!=null&&mAdapter.indexOf(mCastRow)!=-1)
                        mAdapter.remove(mCastRow);
                    mCastRow = new CastRow(getString(R.string.scrap_cast), cast, tags.getDirectorsFormatted());
                    mAdapter.add(mCastRow);
                }
            }
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean hideTrailerRow = prefs.getBoolean(VideoPreferencesCommon.KEY_HIDE_TRAILER_ROW, VideoPreferencesCommon.HIDE_TRAILER_ROW_DEFAULT);

            if(mTrailers!=null&&mTrailers.size()>0 && !hideTrailerRow){
                ArrayObjectAdapter postersRowAdapter = new ArrayObjectAdapter(new TrailerPresenter(getActivity()));
                postersRowAdapter.addAll(0, mTrailers);

                if(mTrailersRow!=null&&mAdapter.indexOf(mTrailersRow)!=-1)
                    mAdapter.remove(mTrailersRow);
                mTrailersRow = new ListRow(
                        new HeaderItem(getString(R.string.scrap_trailer)),
                        postersRowAdapter);
                mAdapter.add(mTrailersRow);

            }
            // Posters
            if (mPosters!=null && !mPosters.isEmpty()) {
                ArrayObjectAdapter postersRowAdapter = new ArrayObjectAdapter(new ScraperImagePosterPresenter());
                postersRowAdapter.addAll(0, mPosters);
                if(mPostersRow!=null&&mAdapter.indexOf(mPostersRow)!=-1)
                    mAdapter.remove(mPostersRow);
                mPostersRow = new ListRow(
                        new HeaderItem(getString(!mIsTvEpisode ? R.string.leanback_posters_header : R.string.leanback_season_posters_header)),
                        postersRowAdapter);
                mAdapter.add(mPostersRow);
            }

            // Backdrops
            if (mBackdrops!=null && !mBackdrops.isEmpty()) {
                ArrayObjectAdapter backdropsRowAdapter = new ArrayObjectAdapter(new ScraperImageBackdropPresenter());
                backdropsRowAdapter.addAll(0, mBackdrops);
                if(mBackdropsRow!=null&&mAdapter.indexOf(mBackdropsRow)!=-1)
                    mAdapter.remove(mBackdropsRow);
                mBackdropsRow = new ListRow(
                        new HeaderItem(getString(!mIsTvEpisode ? R.string.leanback_backdrops_header : R.string.leanback_tvshow_backdrops_header)),
                        backdropsRowAdapter);
                mAdapter.add(mBackdropsRow);
            }

            // Web links
            List<String> links = getWebLinks(tags);
            if (links.size()>0) {
                // less bling bling
                //ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(new WebPageLinkPresenter());
                ArrayObjectAdapter rowAdapter = new ArrayObjectAdapter(new WebLinkPresenter(mColor));
                for (String link : links) {
                    rowAdapter.add(new WebPageLink(link));
                }
                mAdapter.add(new ListRow( new HeaderItem(getString(R.string.leanback_weblinks_header)), rowAdapter));
            }
        }
    }

    private class SubtitleFilesListerTask extends AsyncTask<Video, Void, List<SubtitleManager.SubtitleFile>> {

        private final Activity mActivity;

        public SubtitleFilesListerTask(Activity activity){
            mActivity = activity;
        }

        private Activity getActivity(){
            return mActivity;
        }

        @Override
        protected List<SubtitleManager.SubtitleFile> doInBackground(Video... videos) {
            Video video = videos[0];
            SubtitleManager lister = new SubtitleManager(getActivity(),null );
            List<SubtitleManager.SubtitleFile> list = lister.listLocalAndRemotesSubtitles(video.getFileUri(), true);
            mSubtitleListCache.put(video.getFileUri(), list);
            return list;
        }

        @Override
        protected void onPostExecute(List<SubtitleManager.SubtitleFile> subtitleFiles) {
            if(isCancelled())
                return;
            mExternalSubtitles = subtitleFiles;

            updateSubtitleRowWhenReady();
        }
    }

    /**
     * Update subtitles details when both metadata (for integrated subs) and external sub checking is done
     */
    private void updateSubtitleRowWhenReady() {
        if ((mVideo.getMetadata()!=null) && (mSubtitleListCache.get(mVideo.getFileUri())!=null)) {
            mSubtitlesDetailsRow = new SubtitlesDetailsRow(getActivity(), mVideo, mSubtitleListCache.get(mVideo.getFileUri()));
            mAdapter.replace(INDEX_SUBTITLES, mSubtitlesDetailsRow);
        }
    }

    /** Implements SubtitleInterface */
    @Override
    public void performSubtitleDownload() {
            HashMap<String, Long> videoSizes = new HashMap<>();
            if (mVideo.getMetadata()!=null)
                videoSizes.put(mVideo.getFilePath(), mVideo.getMetadata().getFileSize());
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(getActivity(), SubtitlesDownloaderActivity.class);
            intent.putExtra(SubtitlesDownloaderActivity.FILE_URL, mVideo.getFilePath());
            intent.putExtra(SubtitlesDownloaderActivity.FILE_SIZES, videoSizes);
            startActivityForResult(intent, REQUEST_CODE_SUBTITLES_ACTIVITY);

    }

    /** Implements SubtitleInterface */
    @Override
    public void performSubtitleChoose() {
        Intent intent = new Intent(Intent.ACTION_MAIN);

        intent.setClass(getActivity(), SubtitlesWizardActivity.class);
        intent.setData(mVideo.getFileUri());
        startActivityForResult(intent, REQUEST_CODE_SUBTITLES_ACTIVITY);
    }

    private void startAds(int requestCode) {
        int resume = PlayerActivity.RESUME_FROM_LAST_POS;
        int resumePos = -1;
        switch (requestCode){
            case REQUEST_CODE_LOCAL_RESUME_AFTER_ADS_ACTIVITY:
                resume = PlayerActivity.RESUME_FROM_LOCAL_POS;
                resumePos = mVideo.getResumeMs();
                break;
            case REQUEST_CODE_RESUME_AFTER_ADS_ACTIVITY:
                resume = PlayerActivity.RESUME_FROM_LAST_POS;
                resumePos = mVideo.getResumeMs();
                break;
            case REQUEST_CODE_REMOTE_RESUME_AFTER_ADS_ACTIVITY:
                resume = PlayerActivity.RESUME_FROM_REMOTE_POS;
                resumePos = mVideo.getRemoteResumeMs();
                break;
            case REQUEST_CODE_PLAY_FROM_BEGIN_AFTER_ADS_ACTIVITY:
                resume =  PlayerActivity.RESUME_NO;
                break;
        }
        mResumeFromPlayer = true;
        PlayUtils.startVideo(getActivity(), mVideo, resume, false,resumePos, this, getActivity().getIntent().getLongExtra(EXTRA_LIST_ID, -1));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SUBTITLES_ACTIVITY && resultCode == Activity.RESULT_OK) {
            log.debug("Get RESULT_OK from SubtitlesDownloaderActivity/SubtitlesWizardActivity");
            // Update the subtitle row
            if (mSubtitleFilesListerTask !=null) {
                mSubtitleFilesListerTask.cancel(true);
            }
            mSubtitleFilesListerTask = new SubtitleFilesListerTask(getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,mVideo);
        }
        else if(requestCode == PLAY_ACTIVITY_REQUEST_CODE){
            ExternalPlayerResultListener.getInstance().onActivityResult(requestCode,resultCode,data);
        }
        else super.onActivityResult(requestCode,resultCode,data);
    }

    /** Saves a Poster as default poster for a video and update the current poster */
    private class PosterSaverTask extends AsyncTask<ScraperImage, Void, Bitmap> {
        private final int mSeason;
        private final Activity mActivity;

        public PosterSaverTask(Activity activity, int season){
            mActivity = activity;
            mSeason = season;
        }

        private Activity getActivity(){
            return mActivity;
        }

        @Override
        protected Bitmap doInBackground(ScraperImage... params) {
            ScraperImage poster = params[0];
            if(mVideo instanceof Movie) {
                poster.setOnlineId(((Movie)mVideo).getOnlineId());
            }
            else if(mVideo instanceof Episode){
                poster.setOnlineId(((Episode)mVideo).getOnlineId());
            }
            // Save in DB and download
            if (poster.download(getActivity())) {
                poster.setAsDefault(getActivity(), mSeason);
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
                log.error("PosterSaverTask Picasso load exception", e);
            }

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if(isCancelled())
                return;
            if (result != null) {
                mPoster = result;

                Palette palette = Palette.from(result).generate();
                int color;

                if (mVideo.isWatched() || mIsVideoWatched)
                    result = PresenterUtils.addWatchedMark(result, getContext());
                mDetailsOverviewRow.setImageBitmap(getActivity(), result);
                mDetailsOverviewRow.setImageScaleUpAllowed(true);

                if (palette.getDarkVibrantSwatch() != null)
                    color = palette.getDarkVibrantSwatch().getRgb();
                else if (palette.getDarkMutedSwatch() != null)
                    color = palette.getDarkMutedSwatch().getRgb();
                else
                    color = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);

                if (color != mColor) {
                    mColor = color;

                    mVideoBadgePresenter.setSelectedBackgroundColor(color);
                    mOverviewRowPresenter.updateBackgroundColor(color);
                    mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(color));

                    for (Presenter pres : mAdapter.getPresenterSelector().getPresenters()){
                        if (pres instanceof BackgroundColorPresenter)
                            ((BackgroundColorPresenter) pres).setBackgroundColor(color);
                    }
                }

                Toast.makeText(getActivity(), R.string.leanback_poster_changed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Saves a Backdrop as default for a video and update the current backdrop */
    private class BackdropSaverTask extends AsyncTask<ScraperImage, Void, Void> {

        private final Activity mActivity;

        public BackdropSaverTask(Activity activity){
            mActivity = activity;
        }

        private Activity getActivity(){
            return mActivity;
        }

        @Override
        protected Void doInBackground(ScraperImage... params) {
            ScraperImage backdrop = params[0];
            // Save in DB and download
            if (backdrop.setAsDefault(getActivity())) {
                backdrop.download(getActivity());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if(isCancelled())
                return;
            // Update backdrop
            if (mBackdropTask!=null) {
                mBackdropTask.cancel(true);
            }
            if (!mLaunchedFromPlayer) { // in player case the player is displayed in the background, not the backdrop
                mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mVideo);
            }
            Toast.makeText(getActivity(), R.string.leanback_backdrop_changed, Toast.LENGTH_SHORT).show();
            getActivity().setResult(Activity.RESULT_OK);
        }
    }


    //---------------------------------------------------

    /**
     * Implements PlayUtils.SubtitleDownloadListener
     */
    @Override
    public void onDownloadStart(final SubtitleManager downloader) {
        mDownloadingSubs=true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mDownloadingSubs)
                    showSubtitleDialog(downloader);
            }
        }, DIALOG_LAUNCH_DELAY_MS);
    }

    /**
     * Implements PlayUtils.SubtitleDownloadListener
     */
    @Override
    public void onDownloadEnd() {
        mDownloadingSubs=false;
        if(mDialogRetrieveSubtitles!=null)
            mDialogRetrieveSubtitles.dismiss();
    }

    public void showSubtitleDialog(SubtitleManager downloader){
        mDialogRetrieveSubtitles = new DialogRetrieveSubtitles();
        mDialogRetrieveSubtitles.show(getParentFragmentManager(), null);
        mDialogRetrieveSubtitles.setDownloader(downloader);
    }

    public static class DialogRetrieveSubtitles extends DialogFragment {
        private SubtitleManager mDownloader;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            /*
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setCancelable(false);
            View dialogView = inflater.inflate(R.layout.dialog_signin, null);
            builder.setView(R.layout.spinner_dialog);
            final AlertDialog mProgressBarAlertDialog = builder.create();
             */
            NovaProgressDialog npd = new NovaProgressDialog(getActivity());
            npd.setMessage(getString(R.string.dialog_subloader_copying));
            npd.setIndeterminate(true);
            npd.setCancelable(true);
            npd.setCanceledOnTouchOutside(false);
            return npd;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            mDownloader.abort();
        }

        public void setDownloader(SubtitleManager downloader) {
            mDownloader = downloader;
        }
    }

    @Override
    public void onVideoFileRemoved(final Uri videoFile,boolean askForFolderRemoval, final Uri folder) {
        log.debug("onVideoFileRemoved: " + videoFile);
        if (getActivity() != null) {
            Toast.makeText(getActivity(), R.string.delete_done, Toast.LENGTH_SHORT).show();
            if (askForFolderRemoval) {
                AlertDialog.Builder b = new AlertDialog.Builder(getActivity()).setTitle("");
                b.setIcon(R.drawable.filetype_new_folder);
                b.setMessage(R.string.confirm_delete_parent_folder);
                b.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                sendDeleteResult(videoFile);
                            }
                        })
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                delete = new Delete(VideoDetailsFragment.this, getActivity());
                                deleteUrisList = Collections.singletonList(folder);
                                delete.deleteFolder(folder);
                            }
                        });
                b.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        sendDeleteResult(videoFile);
                    }
                });
                b.create().show();
            } else {
                sendDeleteResult(videoFile);
            }
        } else {
            sendDeleteResult(videoFile);
        }

    }

    private void sendDeleteResult(Uri file){
        log.debug("sendDeleteResult: " + file);
        Intent intent = new Intent();
        intent.setData(file);
        getActivity().setResult(ListingActivity.RESULT_FILE_DELETED, intent);
        // TODO: do not finish if there are multiple videos under same name check counter
        // TODO: do the same for phoneUI
        slightlyDelayedFinish();
    }

    @Override
    public void onDeleteVideoFailed(Uri videoFile) {
        log.debug("onDeleteVideoFailed: " + videoFile);
        if (getActivity() != null) Toast.makeText(getActivity(),R.string.delete_error, Toast.LENGTH_SHORT).show();

        // close the fragment anyway because the un-indexing may work even if the actual delete fails
        slightlyDelayedFinish();
    }

    @Override
    public void onFolderRemoved(Uri folder) {
        log.debug("onFolderRemoved: " + folder);
        if (getActivity() != null) Toast.makeText(getActivity(), R.string.delete_done, Toast.LENGTH_SHORT).show();
        sendDeleteResult(folder);
    }

    @Override
    public void onDeleteSuccess() {
        log.debug("onDeleteSuccess");
    }
    //---------------------------------------------------

    private void deleteFile_async(Video video) {
        log.debug("deleteFile_async: " + video.getFileUri());
        delete = new Delete(this, getActivity());
        deleteUrisList = new ArrayList<>(Arrays.asList(video.getFileUri()));
        delete.startDeleteProcess(video.getFileUri());
    }

    private void deleteScraperInfo(Video video) {
        log.debug("deleteScraperInfo: " + video.getFileUri());
        // Reset the scraper fields for this item in the medialib
        // (set them to -1 because there is no need to search it again when running the automated task)
        // this also deletes the scraper data
        ContentValues values = new ContentValues(2);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID, "-1");
        values.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, "-1");
        final String selection = VideoStore.MediaColumns._ID + "=?";
        final String[] selectionArgs =new String[]{Long.toString(video.getId())};

        getActivity().getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);
        /*delete nfo files and posters*/
        delete = new Delete(null,getActivity());
        deleteUrisList = Collections.singletonList(video.getFileUri());
        delete.deleteAssociatedNfoFiles(video.getFileUri());
    }

    private void unindexRemoteVideo(Video video) {
        int numberDeleted = getActivity().getContentResolver().delete(VideoStoreInternal.FILES_SCANNED,
                VideoStore.MediaColumns.DATA + " = ?",
                new String[]{mVideo.getFilePath()});
        // BootupRecommendationService is for before Android O otherwise TV channels are used
        if (ArchosFeatures.isAndroidTV(getContext()))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Intent intent = new Intent(BootupRecommandationService.UPDATE_ACTION);
                intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                getActivity().sendBroadcast(intent);
            } else
                ChannelManager.refreshChannels(getContext());
        if (numberDeleted!=1) {
            if (getActivity() != null) Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> getWebLinks(BaseTags tags) {
        List<String> list = new LinkedList<String>();

        // TMDB
        if (tags instanceof MovieTags) {
            final long onlineId = tags.getOnlineId();
            //log.debug("tags.getOnlineId() = " + onlineId);
            if (onlineId > 0) {
                final String language = MovieScraper3.getLanguage(getActivity());
                list.add(String.format(getResources().getString(R.string.tmdb_movie_title_url), onlineId, language));
            }
        } else if (tags instanceof EpisodeTags) {
            if (mOnlineId >0) {
                final String language = ShowScraper4.getLanguage(getActivity());
                list.add(String.format(getResources().getString(R.string.tmdb_tvshow_title_url), mOnlineId, language));
            }
        }

        // IMDB (valid for both movies and episodes)
        String imdbId = null;
        if (tags != null) imdbId = tags.getImdbId();
        //log.debug("tags.getImdbId() = "+imdbId);
        if ((imdbId!=null) && (!imdbId.isEmpty())) {
            list.add(getResources().getString(R.string.imdb_title_url) + imdbId);
        }

        return list;
    }

    private void slightlyDelayedFinish() {
        log.debug("slightlyDelayedFinish");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        }, 200);
    }

    public void onKeyDown(int keyCode) {
        int direction = -1;

        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                setSelectedPosition(0);
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                VideoMetadata mMetadata = mVideo.getMetadata();
                isFilePlayable = true;
                // test from FileDetailsRowPresenter to check if file is playable
                if (mMetadata != null) {
                    if (mMetadata.getFileSize() == 0 && mMetadata.getVideoTrack() == null && mMetadata.getAudioTrackNb() == 0) {
                        // sometimes metadata are set to zero but the file is there, can be due to libavosjni not loaded
                        isFilePlayable = false;
                    }
                }
                if (isFilePlayable) {
                    startAds(REQUEST_CODE_RESUME_AFTER_ADS_ACTIVITY);
                } else {
                    Toast.makeText(getActivity(), R.string.player_err_cantplayvideo, Toast.LENGTH_SHORT).show();
                }
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
            if (mVideo instanceof Movie) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String sortOrder = prefs.getString(AllMoviesGridFragment.SORT_PARAM_KEY, MoviesLoader.DEFAULT_SORT);
                boolean showWatched = prefs.getBoolean(AllMoviesGridFragment.SHOW_WATCHED_KEY, true);
                loader = new MoviesLoader(getActivity(), sortOrder, showWatched, true);
            }
            else if (mVideo instanceof Episode) {
                EpisodeTags tags = (EpisodeTags)mVideo.getFullScraperTags(getActivity());
                loader = new EpisodesLoader(getActivity(), tags.getShowId(), -1, true);
            }
            if (loader != null) {
                // Using a CursorLoader but outside of the LoaderManager : need to make sure the Looper is ready
                if (Looper.myLooper()==null) Looper.prepare();
                Cursor c = loader.loadInBackground();
                Video video = null;
                for (int i = 0; i < c.getCount(); i++) {
                    c.moveToPosition(i);
                    Video v = (Video)new CompatibleCursorMapperConverter(new VideoCursorMapper()).convert(c);
                    if (v.getId() == mVideo.getId()) {
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
                        Video nv = (Video)new CompatibleCursorMapperConverter(new VideoCursorMapper()).convert(c);
                        if (nv.getId() != v.getId())
                            video = nv;
                        break;
                    }
                }
                c.close();
                if (video != null) {
                    if (direction == Gravity.LEFT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.RIGHT));
                    else if (direction == Gravity.RIGHT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.LEFT));
                    final Intent intent = new Intent(getActivity(), VideoDetailsActivity.class);
                    intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO, video);
                    intent.putExtra(VideoDetailsActivity.SLIDE_TRANSITION_EXTRA, true);
                    intent.putExtra(VideoDetailsActivity.SLIDE_DIRECTION_EXTRA, direction);
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

    public static void setWatchState(Boolean isVideoWatched) {
        log.debug("setWatchState to " + isVideoWatched);
        mIsVideoWatched = isVideoWatched;
    }

    public static int getDominantColor() {
        return dominantColor;
    }
}
