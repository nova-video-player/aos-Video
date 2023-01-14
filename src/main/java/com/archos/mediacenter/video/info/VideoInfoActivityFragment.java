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

package com.archos.mediacenter.video.info;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.ToolbarWidgetWrapper;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.palette.graphics.Palette;

import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.ImageViewSetter;
import com.archos.mediacenter.utils.imageview.ImageViewSetterConfiguration;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.utils.videodb.XmlDb;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.FileManagerService;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.dialogs.DialogRetrieveSubtitles;
import com.archos.mediacenter.video.browser.dialogs.Paste;
import com.archos.mediacenter.video.browser.filebrowsing.BrowserByFolder;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.DelayedBackgroundLoader;
import com.archos.mediacenter.video.utils.ExternalPlayerResultListener;
import com.archos.mediacenter.video.utils.ExternalPlayerWithResultStarter;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.StoreRatingDialogBuilder;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediacenter.video.utils.TrailerServiceIconFactory;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStoreImportImpl;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScraperTrailer;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.VideoTags;
import com.archos.mediascraper.xml.MovieScraper3;
import com.archos.mediascraper.xml.ShowScraper4;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollView;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollViewCallbacks;
import com.github.ksoichiro.android.observablescrollview.ScrollState;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.squareup.picasso.Picasso;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static com.archos.mediacenter.video.utils.VideoUtils.getFileUriStringFromContentUri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A placeholder fragment containing a simple view.
 */
public class VideoInfoActivityFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,
        View.OnClickListener, PlayUtils.SubtitleDownloadListener, XmlDb.ParseListener,
        Toolbar.OnMenuItemClickListener, Delete.DeleteListener, ObservableScrollViewCallbacks, Animation.AnimationListener, ExternalPlayerWithResultStarter {

    private static final boolean DBG_LISTENER = false;

    private static final Logger log = LoggerFactory.getLogger(VideoInfoActivityFragment.class);

    /** A serialized com.archos.mediacenter.video.leanback.adapter.object.Video */
    public static final String EXTRA_VIDEO = "VIDEO";
    public static final String EXTRA_FORCE_VIDEO_SELECTION = VideoInfoActivity.EXTRA_FORCE_VIDEO_SELECTION;
    /** The id of the video in the MediaDB (long) */
    public static final String EXTRA_VIDEO_ID = VideoInfoActivity.EXTRA_VIDEO_ID;

    public static final String EXTRA_LAUNCHED_FROM_PLAYER = VideoInfoActivity.EXTRA_LAUNCHED_FROM_PLAYER;
    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final String EXTRA_METADATA_CACHE = "metadata_cache";
    public static final String EXTRA_SUBTITLE_CACHE = "subtitle_cache";
    public static final int REQUEST_CODE_SUBTITLES_DOWNLOADER_ACTIVITY      = 987;
    public static final int REQUEST_BACKDROP_ACTIVITY      = 988;
    private static final int PLAY_ACTIVITY_REQUEST_CODE = 989;

    private static final int DELETE_GROUP = 1;
    private View mRoot;

    private static Context mContext;

    // need to be static otherwise ActivityResultLauncher find them null
    private static Delete delete;
    private static List<Uri> deleteUrisList;

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

    private Video mCurrentVideo;
    private Boolean mIsVideoMovie = null;
    private AsyncTask<Video, Void, Pair<Bitmap, Video>> mThumbnailTask;

    private HashMap<String, VideoMetadata> mVideoMetadateCache;
    private HashMap<String, List<SubtitleManager.SubtitleFile>> mSubtitleListCache;
    private AsyncTask<Video, Integer, VideoMetadata> mVideoInfoTask;
    private AsyncTask<Video, Void, List<SubtitleManager.SubtitleFile>> mSubtitleFilesListerTask;
    private AsyncTask<Video, Void, BaseTags> mFullScraperTagsTask;

    private boolean mIsLeavingPlayerActivity = false;

    private int mColor; //dark background color for cardview

    /** pre-play subtitle download dialog is displayed only in case the wait is long than DIALOG_LAUNCH_DELAY_MS */
    private static final int DIALOG_LAUNCH_DELAY_MS = 2000;
    private boolean mDownloadingSubs = false;
    private DialogRetrieveSubtitles mDialogRetrieveSubtitles;
    private List<Video> mVideoList;

    private boolean mSelectCurrentVideo;

    //poster
    private View mWatchedView;

    //scrap details
    private TextView mCastTextView;
    private TextView mScrapDirector;
    private TextView mScrapDirectorTitle;
    private TextView mScrapWriter;
    private TextView mScrapWriterTitle;
    private View mIMDBIcon;
    private View mTMDBIcon;
    private View mTVDBIcon;
    private LinearLayout mScrapTrailers;
    private LinearLayout mSourceLayout;
    private VideoBadgePresenter mVideoBadgePresenter;
    private View mScrapDetailsCard;
    private CardView mScrapTrailersContainer;
    private View mScraperContainer;
    private TextView mCastTextViewTitle;


    // Backdrop
    private ImageViewSetter mBackgroundSetter;
    private ImageProcessor mBackgroundLoader;
    private ImageView mApplicationBackdrop;

    //file info
    private View mFileInfoContent;
    private TextView mFileInfoHeader;
    private TextView mFilePathTextView;
    private TextView mDecoderTextView;
    private View mFileInfoContainerLoading;
    private View mFileInfoAudioVideoContainer;
    private TextView mAudioTrackTextView;
    private TextView mFileSize;
    private TextView mDuration;
    private TextView mVideoTrackTextView;
    private CardView mFileInfoContainer;

    //subs
    private View mSubtitleContent;
    private TextView mSubtitleHeader;
    private View mSubtitleDownloadButton;
    private CardView mSubtitleContainer;
    private TextView mSubtitleTrack ;

    //plot card
    private CardView mScraperPlotContainer;
    private TextView mPlotTextView;
    private TextView mScrapStudio;
    private TextView mScrapYear;
    private TextView mScrapDuration;
    private TextView mScrapRating;
    private View mScrapStudioContainer;
    private TextView mScrapContentRating;
    private View mScrapContentRatingContainer;


    //play buttons and poster

    private CardView mActionButtonsContainer;
    private Button mRemoteResumeButton;
    private FloatingActionButton mGenericPlayButton;
    private Button mResumeLocalButton;
    private Button mPlayButton;
    private ImageView mPosterImageView;


    //
    private CardView mButtonsContainer;
    private Button mScrapButton;
    private Button mIndexButton;

    //titlebars
    private Toolbar mTitleBar;
    private TextView mSecondaryEpisodeTitleView;
    private TextView mSecondaryEpisodeSeasonView;
    private TextView mSecondaryTitleTextView;
    private View mTitleBarContent;
    private View mToolbarContainer;
    private ViewGroup mSecondaryTitleBar;
    private TextView mTitleTextView;
    private TextView mEpisodeSeasonView;
    private TextView mEpisodeTitleView;

    private ObservableScrollView mScrollView;

    private Animation mFABShowAnimation, mFABHideAnimation, mToolbarShowAnimation;
    private FABAnimationManager mFABManager;

    private String mIMDBId;
    private long mTMDBId;
    private long mTVDBId;
    private long mOnlineId = -1;
    private Bitmap mBitmap;
    private String mPath ;
    private boolean mIsLaunchFromPlayer;
    private long mVideoIdFromPlayer;
    private String mVideoPathFromPlayer;
    private TextView mGenreTextView;
    private BaseTags mTags;
    private int mPlayerType;
    private boolean mWatchedStatus;
    private int mHeaderHeight;
    private boolean mIsPortraitMode;

    private static boolean isFileManagerServiceBound = false;

    private NetworkState networkState = null;
    private PropertyChangeListener propertyChangeListener = null;
    private boolean mNetworkStateListenerAdded = false;

    private Paste mPasteDialog;    //download dialog
    private Uri mLastIndexed;   //keep last index uri to avoid asking it twice (for example when leaving fragment and coming back while video hasn't yet been indexed)
    private VideoMetadata mVideoMetadataFromPlayer;
    private TextView mFileError;
    private ToolbarWidgetWrapper mToolbarWidgetWrapper;

    private boolean isFilePlayable = true;

    public static VideoInfoActivityFragment getInstance(Video video, Uri path, long id, boolean forceVideoSelection){
        log.debug("VideoInfoActivityFragment for uri=" + path);
        Bundle arguments = new Bundle();
        arguments.putSerializable(EXTRA_VIDEO, video);
        if(path!=null)
            arguments.putString(EXTRA_VIDEO_PATH, path.toString());
        arguments.putBoolean(EXTRA_FORCE_VIDEO_SELECTION,forceVideoSelection);
        arguments.putLong(EXTRA_VIDEO_ID, id);
        VideoInfoActivityFragment fragment = new VideoInfoActivityFragment();
        fragment.setArguments(arguments);
        return fragment;
    }
    public VideoInfoActivityFragment() {
    }

    public void onCreate(Bundle save){
        log.debug("onCreate");
        super.onCreate(save);
        // pass the right deleteLauncher linked to activity
        FileUtilsQ.setDeleteLauncher(deleteLauncher);
        CustomApplication.resetLastVideoPlayed();
        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mVideoList = new ArrayList<>();
        mBackgroundLoader = new DelayedBackgroundLoader(getActivity(), 0, 0.2f);
        ImageViewSetterConfiguration config = ImageViewSetterConfiguration.Builder.createNew()
                .setUseCache(false)
                .build();
        mBackgroundSetter = new ImageViewSetter(getActivity(), config);
        mSubtitleListCache = new HashMap<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        log.debug("onCreateView");
        mRoot = inflater.inflate(R.layout.video_info2_fragment, container, false);
        mScrollView = (ObservableScrollView) mRoot.findViewById(R.id.scrollView);
        mIsPortraitMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mGenericPlayButton = (FloatingActionButton)mRoot.findViewById(R.id.play_toolbar);
        mGenericPlayButton.setVisibility(View.GONE);
        mContext = getContext();
        mFABShowAnimation = AnimationUtils.loadAnimation(mContext, R.anim.fab_show_anim);
        mFABHideAnimation = AnimationUtils.loadAnimation(mContext, R.anim.fab_hide_anim);
        mToolbarShowAnimation = AnimationUtils.loadAnimation(mContext, R.anim.video_info_toolbar_show);
        mToolbarShowAnimation.setAnimationListener(this);
        mFABManager = new FABAnimationManager(mGenericPlayButton,mFABHideAnimation,mFABShowAnimation);
        mTitleBar = (Toolbar) mRoot.findViewById(R.id.titlebar);
        mTitleBarContent = mRoot.findViewById(R.id.titlebar_content);

        mSecondaryTitleBar = (ViewGroup) mRoot.findViewById(R.id.secondary_titlebar);
        if(mSecondaryTitleBar!=null) {
            mToolbarContainer = mRoot.findViewById(R.id.toolbar_container);
            mTitleBarContent.setVisibility(View.GONE);
            mSecondaryEpisodeTitleView = (TextView) mSecondaryTitleBar.findViewById(R.id.episode_title_view);
            mSecondaryEpisodeSeasonView = (TextView) mSecondaryTitleBar.findViewById(R.id.s_e_text_view);
            mSecondaryTitleTextView = (TextView) mSecondaryTitleBar.findViewById(R.id.title_view);

        }
        mTitleBar.setOnMenuItemClickListener(this);
        mToolbarWidgetWrapper = new ToolbarWidgetWrapper(mTitleBar, false);
        mToolbarWidgetWrapper.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP);
        mTitleBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().onBackPressed();
            }
        });
        mEpisodeTitleView =(TextView) mTitleBarContent.findViewById(R.id.episode_title_view);
        mEpisodeSeasonView =(TextView) mTitleBarContent.findViewById(R.id.s_e_text_view);

        mTitleTextView = (TextView) mTitleBarContent.findViewById(R.id.title_view);


        mTitleTextView = (TextView) mTitleBarContent.findViewById(R.id.title_view);
        setBackdropToApplicationBackground();

        mFileInfoContent = mRoot.findViewById(R.id.file_info_content);
        mFileInfoHeader = (TextView) mRoot.findViewById(R.id.file_info_header);
        mFileInfoHeader.setOnClickListener(this);
        mDecoderTextView = (TextView) mRoot.findViewById(R.id.video_decoder);
        mSubtitleHeader  = (TextView) mRoot.findViewById(R.id.subtitle_header);
        mSubtitleHeader.setOnClickListener(this);
        mSubtitleContent  =  mRoot.findViewById(R.id.subtitle_content);
        mSubtitleContainer  =  (CardView)mRoot.findViewById(R.id.subtitles_container);
        mSubtitleDownloadButton = mSubtitleContent.findViewById(R.id.subtitles_online);
        mSubtitleDownloadButton.setOnClickListener(this);
        mResumeLocalButton = (Button) mRoot.findViewById(R.id.resume);
        mPlayButton = (Button) mRoot.findViewById(R.id.play);
        mActionButtonsContainer = (CardView) mRoot.findViewById(R.id.action_buttons_container);
        mResumeLocalButton.setOnClickListener(this);
        mPlayButton.setOnClickListener(this);
        mRemoteResumeButton = (Button) mRoot.findViewById(R.id.remote_resume);
        mRemoteResumeButton.setOnClickListener(this);
        mSourceLayout = (LinearLayout)mRoot.findViewById(R.id.source_layout);
        mFileInfoContainer = (CardView)mRoot.findViewById(R.id.info_file_container);
        mFilePathTextView = (TextView)mFileInfoContainer.findViewById(R.id.fullpath);
        mFileSize = (TextView)mRoot.findViewById(R.id.filesize);
        mFileError = (TextView)mRoot.findViewById(R.id.file_error);
        mDuration = (TextView)mRoot.findViewById(R.id.duration);
        mFileInfoAudioVideoContainer = mRoot.findViewById(R.id.audio_video_info);
        mSubtitleTrack = (TextView)mRoot.findViewById(R.id.subtitle_track);
        mSubtitleTrack.setVisibility(View.GONE);
        mFileInfoContainerLoading = mRoot.findViewById(R.id.audio_video_info_processing);
        mPosterImageView = (ImageView)mRoot.findViewById(R.id.poster);
        mPosterImageView.setOnClickListener(this);
        mWatchedView = mRoot.findViewById(R.id.trakt_watched);
        //poster animation
        mPosterImageView.setTransitionName(VideoInfoActivity.SHARED_ELEMENT_NAME);
        mVideoTrackTextView = (TextView) mRoot.findViewById(R.id.video_track);
        mAudioTrackTextView = (TextView) mRoot.findViewById(R.id.audio_track);
        mIndexButton = (Button) mRoot.findViewById(R.id.index_button);
        mIndexButton.setOnClickListener(this);
        mButtonsContainer = (CardView) mRoot.findViewById(R.id.buttons_container);
        //scrap

        mScraperContainer = mRoot.findViewById(R.id.scraper_container);
        mIMDBIcon = mRoot.findViewById(R.id.scrap_link_imdb);
        mIMDBIcon.setOnClickListener(this);
        mTMDBIcon = mRoot.findViewById(R.id.scrap_link_tmdb);
        mTMDBIcon.setOnClickListener(this);
        mTVDBIcon = mRoot.findViewById(R.id.scrap_link_tvdb);
        mTVDBIcon.setOnClickListener(this);
        mScraperPlotContainer = (CardView)mRoot.findViewById(R.id.scraper_plot_container);
        mPlotTextView = (TextView) mRoot.findViewById(R.id.scrap_plot);
        mGenreTextView = (TextView) mRoot.findViewById(R.id.scrap_genre);
        mCastTextView = (TextView) mRoot.findViewById(R.id.scrap_cast);
        mCastTextViewTitle = (TextView) mRoot.findViewById(R.id.scrap_cast_title);
        mScrapButton = (Button) mRoot.findViewById(R.id.scrap_button);
        mScrapButton.setOnClickListener(this);
        mScrapDirector =(TextView) mRoot.findViewById(R.id.scrap_director);
        mScrapDirectorTitle =(TextView) mRoot.findViewById(R.id.scrap_director_title);
        mScrapWriter =(TextView) mRoot.findViewById(R.id.scrap_writer);
        mScrapWriterTitle =(TextView) mRoot.findViewById(R.id.scrap_writer_title);
        mScrapYear =(TextView) mRoot.findViewById(R.id.scrap_date);
        mScrapDuration =(TextView) mRoot.findViewById(R.id.scrap_duration);
        mScrapRating =(TextView) mRoot.findViewById(R.id.scrap_rating);
        mScrapTrailers =(LinearLayout) mRoot.findViewById(R.id.trailer_layout);
        mScrapTrailersContainer =(CardView)mRoot.findViewById(R.id.scrap_trailer_container);
        mScrapDetailsCard =mRoot.findViewById(R.id.scrap_details_container);
        mScrapStudio =(TextView) mRoot.findViewById(R.id.scrap_studio);
        mScrapStudioContainer = mRoot.findViewById(R.id.scrap_studio_container);
        mScrapContentRating = mRoot.findViewById(R.id.content_rating);
        mScrapContentRatingContainer = mRoot.findViewById(R.id.content_rating_container);

        mFileInfoAudioVideoContainer.setVisibility(View.GONE);
        mFileInfoContainerLoading.setVisibility(View.VISIBLE);
        if(getActivity().getIntent()!=null){
            mPlayerType = getActivity().getIntent().getIntExtra(VideoInfoActivity.EXTRA_PLAYER_TYPE,-1);
            mVideoMetadataFromPlayer = (VideoMetadata)getActivity().getIntent().getSerializableExtra(VideoInfoActivity.EXTRA_USE_VIDEO_METADATA);

        }
        Bundle bundle = null;
        if(savedInstanceState!=null)
            bundle = savedInstanceState;
        else if(getArguments()!=null)
            bundle = getArguments();
        if(bundle!=null) {
            if(bundle.getSerializable(EXTRA_METADATA_CACHE)!=null){
                mVideoMetadateCache = (HashMap<String, VideoMetadata>) savedInstanceState.getSerializable(EXTRA_METADATA_CACHE);
            }
            else
                mVideoMetadateCache = new HashMap<>();

            if(bundle.getSerializable(EXTRA_SUBTITLE_CACHE)!=null){
                mSubtitleListCache = (HashMap<String, List<SubtitleManager.SubtitleFile>>) savedInstanceState.getSerializable(EXTRA_SUBTITLE_CACHE);
            }
            else
                mVideoMetadateCache = new HashMap<>();
            mSelectCurrentVideo = bundle.getBoolean(EXTRA_FORCE_VIDEO_SELECTION,false);
            mIsLaunchFromPlayer = getActivity().getIntent().getExtras().getBoolean(EXTRA_LAUNCHED_FROM_PLAYER, false);
            Video video = (Video) bundle.get(EXTRA_VIDEO);
            if(video == null){

                mVideoIdFromPlayer = bundle.getLong(EXTRA_VIDEO_ID, -1);
                if (mVideoIdFromPlayer == -1) {
                    mPath = bundle.getString(EXTRA_VIDEO_PATH);
                    String nPath = getFileUriStringFromContentUri(mContext, mPath);
                    if (nPath != null) mPath = nPath;
                }

                CursorLoader loader = (CursorLoader) onCreateLoader(1, null);
                if (loader == null) {
                    log.warn("onCreateView loader is null");
                } else {
                    Cursor cursor = loader.loadInBackground();
                    if (cursor != null && cursor.moveToFirst()) {
                        VideoCursorMapper videoCursorMapper = new VideoCursorMapper();
                        videoCursorMapper.bindColumns(cursor);
                        video = (Video) videoCursorMapper.publicBind(cursor);
                    }
                    if (cursor != null)
                        cursor.close();
                }
            }
            if(video!=null)
                setCurrentVideo(video);
            LoaderManager.getInstance(this).restartLoader(1, null, this);
        }
        setBackground();
        mTitleBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                updateHeaderHeight();
            }
        });

        ((ObservableScrollView)mRoot.findViewById(R.id.scrollView)).setScrollViewCallbacks(this);

        if(mIsLaunchFromPlayer) //hide play button
            mActionButtonsContainer.setVisibility(View.GONE);
        return mRoot;
    }

    private void updateGenericButtonAction() {
        log.debug("updateGenericButtonAction");
        int resume = 0;
        int resumePos = -1;
        if(mCurrentVideo.getResumeMs()>0 && mCurrentVideo.getRemoteResumeMs()<=mCurrentVideo.getResumeMs()){
            resume = PlayerService.RESUME_FROM_LOCAL_POS;
            resumePos = mCurrentVideo.getResumeMs();
            mGenericPlayButton.setImageResource(R.drawable.button_icon_resume);
        }
        else if (mCurrentVideo.getRemoteResumeMs()>0){
            resume = PlayerService.RESUME_FROM_REMOTE_POS;
            resumePos = mCurrentVideo.getRemoteResumeMs();
            mGenericPlayButton.setImageResource(R.drawable.button_icon_network);
        }
        else {
            resume = PlayerService.RESUME_NO;
            mGenericPlayButton.setImageResource(R.drawable.button_icon_play);
        }
        final int finalResume = resume;
        final int finalResumePos = resumePos;
        log.debug("updateGenericButtonAction: resume=" + resume + ", resumePos=" + resumePos);
        mGenericPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mIsLeavingPlayerActivity = true;
                isFilePlayable = true;
                VideoMetadata mMetadata = mCurrentVideo.getMetadata();
                if (mMetadata != null) {
                    if (mMetadata.getFileSize() == 0 && mMetadata.getVideoTrack() == null && mMetadata.getAudioTrackNb() == 0) {
                        // sometimes metadata are set to zero but the file is there, can be due to libavosjni not loaded
                        isFilePlayable = false;
                    }
                }
                if (isFilePlayable) {
                    PlayUtils.startVideo(getActivity(),
                            mCurrentVideo,
                            finalResume,
                            false,
                            finalResumePos,
                            VideoInfoActivityFragment.this,
                            getActivity().getIntent().getLongExtra(VideoInfoActivity.EXTRA_PLAYLIST_ID, -1));
                } else {
                    Toast.makeText(getActivity(), R.string.player_err_cantplayvideo, Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    private void updateHeaderHeight() {
        log.debug("updateHeaderHeight");
        mHeaderHeight = mTitleBar.getMeasuredHeight();
        if (mHeaderHeight == 0)
            log.debug("Warning updateHeaderHeight sets mHeaderHeight to zero!");
        if (mIsPortraitMode) {
            View scrollView = mRoot.findViewById(R.id.scroll_content);
            scrollView.setPadding(scrollView.getPaddingLeft(), mHeaderHeight, scrollView.getPaddingRight(), scrollView.getPaddingBottom());
        }
    }

    private void updateUI() {
        if (getActivity() != null)
            getActivity().runOnUiThread(() -> {
                log.debug("updateUI");
                // run this on UI thread
                // close activity if
                //   not localfile (i.e. remote)
                //   && (not connected || (no local connection && not ftp (i.e. smb/upnp))
                //   && fragment is added
                //   && not fragment detached
                if (mCurrentVideo!=null&&
                        !FileUtils.isLocal(mCurrentVideo.getFileUri())&&
                        (!networkState.isConnected()||
                                !networkState.hasLocalConnection()&&!FileUtils.isSlowRemote(mCurrentVideo.getFileUri()))&&
                        isAdded()&&
                        !isDetached()) {
                    getActivity().finish();
                }
            });
    }

    @Override
    public void onAttach(Context context){
        log.debug("onAttach");
        super.onAttach(context);
        mContext = context;
        // handles NetworkState changes
        networkState = NetworkState.instance(getContext());
        if (propertyChangeListener == null)
            propertyChangeListener = evt -> {
                if (evt.getOldValue() != evt.getNewValue()) {
                    log.debug("NetworkState for " + evt.getPropertyName() + " changed:" + evt.getOldValue() + " -> " + evt.getNewValue());
                    updateUI();
                }
            };
        updateUI(); // be sure to be on right state
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        log.debug("onActivityResult");
        if (requestCode == REQUEST_CODE_SUBTITLES_DOWNLOADER_ACTIVITY && resultCode == Activity.RESULT_OK) {
            log.debug("onActivityResult, get RESULT_OK from SubtitlesDownloaderActivity");
            // Update the subtitle row
            if (mSubtitleFilesListerTask != null) {
                mSubtitleFilesListerTask.cancel(true);
            }
            //invalidate cache
            mSubtitleListCache.remove(mCurrentVideo.getFilePath());
            mSubtitleFilesListerTask = new SubtitleFilesListerTask(getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mCurrentVideo);
        }
        else if(requestCode == REQUEST_BACKDROP_ACTIVITY && resultCode == Activity.RESULT_OK){
            if(mFullScraperTagsTask!=null)
                mFullScraperTagsTask.cancel(true);
            mFullScraperTagsTask = new FullScraperTagsTask(getActivity());
            mFullScraperTagsTask.execute(mCurrentVideo);
        }
        else if (requestCode == PLAY_ACTIVITY_REQUEST_CODE){
            ExternalPlayerResultListener.getInstance().onActivityResult(requestCode, resultCode, data);
        }
        else super.onActivityResult(requestCode,resultCode, data);
    }

    private void updateActionButtons(){
        log.debug("updateActionButtons: RemoteResumeMs=" + mCurrentVideo.getRemoteResumeMs() + ", getResumeMs=" + mCurrentVideo.getResumeMs());
        if(mCurrentVideo.getRemoteResumeMs()>0&&mCurrentVideo.getResumeMs()!=mCurrentVideo.getRemoteResumeMs()) {
            mRemoteResumeButton.setVisibility(View.VISIBLE);
            mRemoteResumeButton.setText(getResources().getString(R.string.remote_resume)+" "+MediaUtils.formatTime(mCurrentVideo.getRemoteResumeMs()));
        }
        else mRemoteResumeButton.setVisibility(View.GONE);

        if(mCurrentVideo.getResumeMs()>0) {
            mResumeLocalButton.setVisibility(View.VISIBLE);
            mResumeLocalButton.setText(getResources().getString(R.string.resume) + " " + MediaUtils.formatTime(mCurrentVideo.getResumeMs()));
        }
        else mResumeLocalButton.setVisibility(View.GONE);
        updateGenericButtonAction();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle){
        log.debug("onSaveInstanceState: mCurrentVideo.getFilePath()=" + ((mCurrentVideo!=null) ? mCurrentVideo.getFilePath() : "null"));
        bundle.putSerializable(EXTRA_METADATA_CACHE, mVideoMetadateCache);
        bundle.putSerializable(EXTRA_SUBTITLE_CACHE, mSubtitleListCache);
        bundle.putBoolean(EXTRA_FORCE_VIDEO_SELECTION, true);
        bundle.putSerializable(EXTRA_VIDEO, mCurrentVideo);
    }

    private void setBackdropToApplicationBackground() {
        mApplicationBackdrop = (ImageView) mRoot.findViewById(R.id.backdrop);
    }

    private void setBackground() {
        mButtonsContainer.setCardBackgroundColor(mColor);
        mFileInfoContainer.setCardBackgroundColor(mColor);
        mSubtitleContainer.setCardBackgroundColor(mColor);
        ((CardView) mScrapDetailsCard).setCardBackgroundColor(mColor);
        mScrapTrailersContainer.setCardBackgroundColor(mColor);
        ((CardView)mPosterImageView.getParent().getParent()).setCardBackgroundColor(mColor);
        mScraperPlotContainer.setCardBackgroundColor(mColor);
        mActionButtonsContainer.setCardBackgroundColor(mColor);
        if(mSecondaryTitleBar!=null)
            mTitleBarContent.setBackgroundColor(mColor);
        if(!mIsLaunchFromPlayer)
            mRoot.setBackgroundColor(VideoInfoCommonClass.getDarkerColor(mColor));
        else
            mRoot.setBackgroundColor(VideoInfoCommonClass.getAlphaColor(VideoInfoCommonClass.getDarkerColor(mColor),160));
        if(mGenericPlayButton!=null)
            mGenericPlayButton.setBackgroundTintList(new ColorStateList(new int[][]{new int[]{0}}, new int[]{VideoInfoCommonClass.getClearerColor(mColor)}));
    }


    private void setCurrentVideo(Video video){
        updateWatchedStatus(); //independant of current video
        log.debug( "setCurrentVideo: mCurrentVideo.getFilePath()=" + ((mCurrentVideo!=null) ? mCurrentVideo.getFilePath() : "null"));
        if(shouldChangeVideo(mCurrentVideo, video)) {
            log.debug("setCurrentVideo: should change video");
            mTitleBar.getMenu().clear();

            Video oldVideo = mCurrentVideo;
            mCurrentVideo = video;
            String name = null;
            if(video instanceof Episode){
                log.debug( "setCurrentVideo: new video and it is an episode");
                Episode episode = (Episode) video;
                if(episode.getName()!=null) {
                    setTextOrHideContainer(mEpisodeTitleView, episode.getName(), mEpisodeTitleView);
                    if(mSecondaryEpisodeTitleView!=null)
                            setTextOrHideContainer(mSecondaryEpisodeTitleView, episode.getName(), mSecondaryEpisodeTitleView);
                }
                if(((Episode) video).getShowName()!=null){
                    name = episode.getShowName();
                }
                setTextOrHideContainer(mEpisodeSeasonView, getContext().getString(R.string.leanback_episode_SXEX_code, episode.getSeasonNumber(), episode.getEpisodeNumber()), mEpisodeSeasonView);
                log.debug("setCurrentVideo: " + name + "-s" +
                        episode.getSeasonNumber()+ "e" + episode.getEpisodeNumber() + " " + episode.getName());

                if(mSecondaryEpisodeSeasonView!=null)
                    setTextOrHideContainer(mSecondaryEpisodeSeasonView, getContext().getString(R.string.leanback_episode_SXEX_code, episode.getSeasonNumber(), episode.getEpisodeNumber()), mSecondaryEpisodeSeasonView);
            }
            else{
                log.debug("setCurrentVideo: new video and it is NOT an episode");
                if(video.getName()!=null)
                    name = video.getName();
                mEpisodeSeasonView.setVisibility(View.GONE);
                if(mSecondaryEpisodeSeasonView!=null)
                    mSecondaryEpisodeSeasonView.setVisibility(View.GONE);
                mEpisodeTitleView.setVisibility(View.GONE);
                if(mSecondaryEpisodeTitleView!=null)
                    mSecondaryEpisodeTitleView.setVisibility(View.GONE);
            }

            if(name!=null) {
                if (name.length() > 30) {
                    mTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.video_info_big_text));
                    if(mSecondaryTitleTextView!=null)
                        mSecondaryTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.video_info_big_text));
                }
                else {
                    mTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.video_info_very_big_text));
                    if(mSecondaryTitleTextView!=null)
                        mSecondaryTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.video_info_very_big_text));

                }
                mTitleTextView.setText(name);
                if(mSecondaryTitleTextView!=null)
                    mSecondaryTitleTextView.setText(name);
            }

            //fill usual info

            mFilePathTextView.setText(video.getFilePath());

            updateActionButtons();

            //picasso should be executed in a separated thread however we don't want fragment to be displayed before fragment loads
            if(oldVideo == null|| oldVideo.getPosterUri()==null||!oldVideo.getPosterUri().equals(mCurrentVideo.getPosterUri()))
                getThumbnailSync(mCurrentVideo);

            if (mBitmap!= null) {
                mPosterImageView.setImageBitmap(mBitmap);
                mPosterImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                mPosterImageView.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video_poster));
                mPosterImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            }
            setBackground();
            //execute async task BEFORE xml parsing
            startAsyncTasks();
            if(!mIsLaunchFromPlayer&&!FileUtils.isLocal(video.getFileUri())&& UriUtils.isCompatibleWithRemoteDB(video.getFileUri())) {
                log.debug("addParseListener");
                XmlDb.getInstance().addParseListener(this);
                XmlDb.getInstance().parseXmlLocation(video.getFileUri());
            }
            if (mFullScraperTagsTask != null)
                mFullScraperTagsTask.cancel(true);
            if (mCurrentVideo.hasScraperData()) {
                mFullScraperTagsTask = new FullScraperTagsTask(getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mCurrentVideo);
            }
            if (mThumbnailTask != null)
                mThumbnailTask.cancel(true);
            mThumbnailTask = new ThumbnailAsyncTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mCurrentVideo);
            if(mVideoMetadateCache.containsKey(video.getFilePath())){
                setFileInfo(mVideoMetadateCache.get(video.getFilePath()));
            }
            updateGenericButtonAction();
            if(!mCurrentVideo.isLocalFile()&&UriUtils.isImplementedByFileCore(mCurrentVideo.getFileUri()))
                addMenu(0,R.string.copy_on_device,0,R.string.copy_on_device);

            if (video.isIndexed()) {
                goToIndexed();
                if (video.hasScraperData()) {
                    goToScraped();
                } else
                    goToNotScraped();
            }
            else {
                goToNotIndexed();
                requestIndexAndScrap();
            }
            if(!mIsLaunchFromPlayer && mCurrentVideo.locationSupportsDelete())
                addMenu(0, R.string.delete, DELETE_GROUP, R.string.delete);
        } else {
            log.debug("setCurrentVideo: should not change video");
        }
    }

    private void addMenu(int i, int i2, int i3, int i4) {
        mTitleBar.getMenu().add(i, i2, i3, i4);
      /*  if(mSecondaryTitleBar!=null)
            mSecondaryTitleBar.getMenu().add(i, i2, i3, i4);*/
    }

    private void getThumbnailSync(final Video video) {
        Thread t = new Thread(){
            public void run(){
                getThumbnail(video, false);
            }

        };
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Bitmap getThumbnail(Video video, boolean createThumbnail) {
        mBitmap = null;
        Uri imageUri = null;
        boolean hasTriedThumb = false;
        if (video.isIndexed()){
            if(video.hasScraperData()&&video.getPosterUri()!=null) {
                imageUri = video.getPosterUri();
            }
            if(imageUri==null){
                if(!createThumbnail)
                    imageUri = ThumbnailRequestHandler.buildUriNoThumbCreation(video.getId()); // Thumbnail
                else {
                    imageUri = ThumbnailRequestHandler.buildUri(video.getId()); // Thumbnail
                }
                hasTriedThumb = true;
            }

        }
        if (imageUri!=null) {
            try {
                mBitmap = Picasso.get()
                        .load(imageUri)
                        .resize(getResources().getDimensionPixelSize(R.dimen.video_info_poster_width), getResources().getDimensionPixelSize(R.dimen.video_info_poster_height))
                        .centerCrop()
                        .get();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(mBitmap == null&&!hasTriedThumb){
                    //try with thumb
                    if(!createThumbnail)
                        imageUri = ThumbnailRequestHandler.buildUriNoThumbCreation(video.getId()); // Thumbnail
                    else {
                        imageUri = ThumbnailRequestHandler.buildUri(video.getId()); // Thumbnail
                    }
                    if (imageUri!=null) {
                        try {
                            mBitmap = Picasso.get()
                                    .load(imageUri)
                                    .resize(getResources().getDimensionPixelSize(R.dimen.video_info_poster_width), getResources().getDimensionPixelSize(R.dimen.video_info_poster_height))
                                    .centerCrop()
                                    .get();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if(mBitmap!=null) {
                    Palette palette = Palette.from(mBitmap).generate();
                    if(video.hasScraperData()&&video.getPosterUri()!=null)
                        mColor = palette.getDarkVibrantColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background));
                    else
                        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                }

        }
        return mBitmap;
    }

    private boolean shouldChangeVideo(Video v1, Video v2) {
        log.debug("shouldChangeVideo: called on videos " + ((v1 == null) ? "null" : v1.getFilePath()) + " and " + ((v2 == null) ? "null" : v2.getFilePath()));
        if (v1==null || v2==null) {log.debug("foundDifferencesRequiringDetailsUpdate null"); return true;}
        if (v1.getClass() != v2.getClass()) {log.debug("foundDifferencesRequiringDetailsUpdate class"); return true;}
        if (v1.getId() != v2.getId()) {log.debug("foundDifferencesRequiringDetailsUpdate id"); return true;}
        if (v1.hasScraperData() != v2.hasScraperData()) {log.debug("foundDifferencesRequiringDetailsUpdate hasScraperData"); return true;}
        if (v1.getResumeMs() != v2.getResumeMs()) {log.debug("foundDifferencesRequiringDetailsUpdate resumeMs"); return true;}
        if (v1.isWatched() != v2.isWatched()) {log.debug("foundDifferencesRequiringDetailsUpdate isWatched"); return true;}
        if (v1.isUserHidden() != v2.isUserHidden()) {log.debug("foundDifferencesRequiringDetailsUpdate isUserHidden"); return true;}
        if (v1.getPosterUri()!=null&&!v1.getPosterUri().equals(v2.getPosterUri())
                ||v2.getPosterUri()!=null&&!v2.getPosterUri().equals(v1.getPosterUri())) {log.debug("foundDifferencesRequiringDetailsUpdate getPosterUri"); return true;}
        //if (v1.subtitleCount() != v2.subtitleCount()) {log.debug("foundDifferencesRequiringDetailsUpdate subtitleCount"); return true;}
        //if (v1.externalSubtitleCountexternalSubtitleCount() != v2.externalSubtitleCount()) {log.debug("foundDifferencesRequiringDetailsUpdate externalSubtitleCount"); return true;}
        return false;
    }

    private void goToNotIndexed() {
        if (UriUtils.isIndexable(mCurrentVideo.getFileUri())) {
            mIndexButton.setVisibility(View.VISIBLE);
            mButtonsContainer.setVisibility(View.VISIBLE);
        }
        else
            mButtonsContainer.setVisibility(View.GONE);
        mScraperContainer.setVisibility(View.GONE);
        mScrapButton.setVisibility(View.GONE);
        mScraperPlotContainer.setVisibility(View.GONE);
    }

    private void goToIndexed() {
        log.debug("goToIndexed");
        if(mCurrentVideo.hasScraperData())
            mButtonsContainer.setVisibility(View.GONE);
        mIndexButton.setVisibility(View.GONE);
        mScraperContainer.setVisibility(View.GONE);
        if(mCurrentVideo.isWatched())
            addMenu(0, R.string.mark_as_not_watched, 0, R.string.mark_as_not_watched);

        else addMenu(0, R.string.mark_as_watched, 0, R.string.mark_as_watched);
        addMenu(0, R.string.video_browser_unindex_file, DELETE_GROUP, R.string.video_browser_unindex_file);
    }

    public void requestIndexAndScrap(){
        log.debug("requestIndexAndScrap");
        if (!PrivateMode.isActive()) {

            if (mCurrentVideo.getId() == -1&&mCurrentVideo.getFileUri()!=null&&!mCurrentVideo.getFileUri().equals(mLastIndexed)) {
                mLastIndexed = mCurrentVideo.getFileUri();
                if(UriUtils.isIndexable(mCurrentVideo.getFileUri())) {
                    final Uri uri = mCurrentVideo.getFileUri();
                    new Thread() {
                        public void run() {
                            if (!VideoStoreImportImpl.isNoMediaPath(uri)) {
                                log.debug("requestIndexAndScrap: isNoMediaPath asking VideoStore.requestIndexing " + uri);
                                VideoStore.requestIndexing(uri, getActivity(),false);
                            }
                        }
                    }.start();
                }
            }
        }
    }
    private void goToNotScraped() {
        log.debug("goToNotScraped");
        mButtonsContainer.setVisibility(View.VISIBLE);
        mScraperContainer.setVisibility(View.GONE);
        mScrapButton.setVisibility(View.VISIBLE);
        mScraperPlotContainer.setVisibility(View.GONE);
        mColor =  ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
    }

    private void goToScraped() {
        log.debug("goToScraped");
        mButtonsContainer.setVisibility(View.GONE);
        mScrapButton.setVisibility(View.GONE);
        mScraperContainer.setVisibility(View.VISIBLE);
        mScraperPlotContainer.setVisibility(View.VISIBLE);

        addMenu(0, R.string.info_menu_backdrop_select, 0, R.string.info_menu_backdrop_select);
        addMenu(0, R.string.info_menu_poster_select, 0, R.string.info_menu_poster_select);
        addMenu(0, R.string.nfo_export_button, 0, R.string.nfo_export_button);
        addMenu(0, R.string.scrap_remove, DELETE_GROUP, R.string.scrap_remove);
    }

    private void setVisibilityFileError() {
        mFileError.setVisibility(View.VISIBLE);
        mFileInfoContainerLoading.setVisibility(View.GONE);
        mFileInfoAudioVideoContainer.setVisibility(View.GONE);
        mDuration.setVisibility(View.GONE);
        mFileSize.setVisibility(View.GONE);
    }

    private void setFileInfo(VideoMetadata videoMetadata){
        log.debug("setFileInfo");
        // Special error case (99.9% of the time it happens when the specified file is not reachable)
        if (videoMetadata == null) {
            setVisibilityFileError();
        } else {
            if (videoMetadata.getFileSize()==0 && videoMetadata.getVideoTrack()==null && videoMetadata.getAudioTrackNb()==0) {
                // sometimes metadata are set to zero but the file is there, can be due to libavosjni not loaded
                setVisibilityFileError();
            } else {
                mFileError.setVisibility(View.GONE);
                if (videoMetadata.getVideoTrack() != null) {
                    mVideoTrackTextView.setText(VideoInfoCommonClass.getVideoTrackString(videoMetadata, getResources()));
                }
                mFileInfoAudioVideoContainer.setVisibility(View.VISIBLE);
                mFileInfoContainerLoading.setVisibility(View.GONE);
                mDuration.setVisibility(View.VISIBLE);
                mFileSize.setVisibility(View.VISIBLE);
                mFileSize.setText(Formatter.formatFileSize(getActivity(), videoMetadata.getFileSize()));
                mDuration.setText(MediaUtils.formatTime(videoMetadata.getDuration()));
                String decoder = VideoInfoCommonClass.getDecoder(videoMetadata, getResources(), mPlayerType);
                setTextOrHideContainer(mDecoderTextView, decoder, mDecoderTextView);
                String audiotrack = VideoInfoCommonClass.getAudioTrackString(videoMetadata, getResources(), getActivity());
                setTextOrHideContainer(mAudioTrackTextView, audiotrack, mRoot.findViewById(R.id.audio_row));
            }
        }
    }

    private void updateSubtitleInfo(VideoMetadata videoMetadata, List<SubtitleManager.SubtitleFile> externalSubs){
        log.debug("updateSubtitleInfo");
        // Subtitles tracks info
        int subtitleTrackNb = videoMetadata!=null?videoMetadata.getSubtitleTrackNb():0;

        if (subtitleTrackNb > 0 || externalSubs!=null&&externalSubs.size()>0) {
            log.debug("updateAudioVideoInfo: subtitle");
            ArrayList<CharSequence> lines = new ArrayList<>();
            int totSubs = 0;
            if(videoMetadata!=null) {
                for (int i = 0; i < subtitleTrackNb; ++i) {
                    if (!videoMetadata.getSubtitleTrack(i).isExternal) { //manage external subs with sub manager
                        lines.add((totSubs + 1) + ": " + VideoUtils.getLanguageString(getActivity(), videoMetadata.getSubtitleTrack(i).name));
                        totSubs++;
                    }
                }
            }
            if(externalSubs!=null) {
                for (SubtitleManager.SubtitleFile sub : externalSubs) {
                    lines.add((totSubs + 1) + ": " + VideoUtils.getLanguageString(getActivity(), sub.mName));
                    totSubs++;
                }
            }
            mSubtitleTrack.setVisibility(View.VISIBLE);
            mSubtitleTrack.setText(TextUtils.join("\n", lines));
        } else {
            mSubtitleTrack.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        log.debug("onClick");
        if(view == mPlayButton || view == mResumeLocalButton || view == mRemoteResumeButton) {
            int resume = 0;
            int resumePos = -1;
            if (view == mPlayButton) {
                resume = PlayerService.RESUME_NO;
            } else if (view == mResumeLocalButton) {
                resume = PlayerService.RESUME_FROM_LOCAL_POS;
                resumePos = mCurrentVideo.getResumeMs();
                log.debug("onClick: resume from local resumePos=" + resumePos);
            } else if (view == mRemoteResumeButton) {
                resume = PlayerService.RESUME_FROM_REMOTE_POS;
                resumePos = mCurrentVideo.getRemoteResumeMs();
                log.debug("onClick: resume from remote resumePos=" + resumePos);
            }
            mIsLeavingPlayerActivity = true;
            VideoMetadata mMetadata = mCurrentVideo.getMetadata();
            isFilePlayable = true;
            if (mMetadata != null) {
                if (mMetadata.getFileSize() == 0 && mMetadata.getVideoTrack() == null && mMetadata.getAudioTrackNb() == 0) {
                    // sometimes metadata are set to zero but the file is there, can be due to libavosjni not loaded
                    isFilePlayable = false;
                }
            }
            if (isFilePlayable) {
                log.debug("onClick: startVideo resumePos=" + resumePos);
                // note to self: resumePos only used for external player...
                // real resume for local file is VideoDbInfo.resume
                PlayUtils.startVideo(
                        getActivity(),
                        mCurrentVideo,
                        resume,
                        false,
                        resumePos,
                        this,
                        getActivity().getIntent().getLongExtra(VideoInfoActivity.EXTRA_PLAYLIST_ID, -1));
            } else {
                Toast.makeText(getActivity(), R.string.player_err_cantplayvideo, Toast.LENGTH_SHORT).show();
            }
        }
        else if(view == mIndexButton){
            log.debug("onClick: mIndexButton " + mCurrentVideo.getFileUri());
            VideoStore.requestIndexing(mCurrentVideo.getFileUri(), getActivity());

        }else if(view == mScrapButton) {
            log.debug("onClick: mScrapButton " + mCurrentVideo.getFileUri());
            Intent intent = new Intent(getActivity(), VideoInfoScraperActivity.class);
            intent.putExtra(VideoInfoScraperActivity.EXTRA_VIDEO, mCurrentVideo);
            startActivity(intent);
        }
        else if(view == mFileInfoHeader){
            // toogleVisibility(mFileInfoHeader, mFileInfoContent);
        }
        else if(view == mSubtitleHeader){
            //toogleVisibility(mSubtitleHeader, mSubtitleContent);
        }else if(view == mSubtitleDownloadButton){

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(getActivity(), SubtitlesDownloaderActivity.class);
            intent.putExtra(SubtitlesDownloaderActivity.FILE_URL, mCurrentVideo.getFilePath());
            startActivityForResult(intent, REQUEST_CODE_SUBTITLES_DOWNLOADER_ACTIVITY);
        }else if(view == mTMDBIcon){
            // Format TMDB URL with movie ID and preferred language
            final String language, tmdbUrl;
            if (mIsVideoMovie) {
                language = MovieScraper3.getLanguage(getActivity());
                tmdbUrl = String.format(getResources().getString(R.string.tmdb_movie_title_url), Long.toString(mTMDBId), language);
            } else {
                language = ShowScraper4.getLanguage(getActivity());
                tmdbUrl = String.format(getResources().getString(R.string.tmdb_tvshow_title_url), Long.toString(mOnlineId), language);
            }
            log.debug("onClick: mTMDBId=" + mTMDBId + ", tmdbUrl=" + tmdbUrl);
            // Breaks AndroidTV acceptance
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(tmdbUrl));
            startActivity(it);
            //WebUtils.openWebLink(getActivity(), tmdbUrl);
        }else if(view == mTVDBIcon){
            final String language;
            // Format TVDB URL with movie ID and preferred language
            language = ShowScraper4.getLanguage(getActivity());
            final String tvdbUrl = String.format(getResources().getString(R.string.tvdb_title_url), Long.toString(mTVDBId), language);
            // Breaks AndroidTV acceptance
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(tvdbUrl));
            startActivity(it);
            //WebUtils.openWebLink(getActivity(), tvdbUrl);
        }
        else if(view == mIMDBIcon){
            final String imdbUrl = getResources().getString(R.string.imdb_title_url) + mIMDBId;
            // Breaks AndroidTV acceptance but required to open link in app instead of browser
            Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(imdbUrl));
            startActivity(it);
            //WebUtils.openWebLink(getActivity(), imdbUrl);
        }
        else if(view == mPosterImageView){
            if(!mCurrentVideo.hasScraperData())
                return;
            selectNewPoster();

        }
    }

    private void selectNewPoster() {
        Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
        intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mCurrentVideo);
        intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_BACKDROP, false);
        startActivity(intent);
    }

    /**
     * Implements PlayUtils.SubtitleDownloadListener
     */
    @Override
    public void onDownloadStart(final SubtitleManager downloader) {
        mDownloadingSubs=true;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mDownloadingSubs)
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

    @Override
    public void onParseFail(XmlDb.ParseResult parseResult) {
        log.debug("onParseFail");
        log.debug("onParseFail");
        XmlDb.getInstance().removeParseListener(this);
    }

    @Override
    public void onParseOk(XmlDb.ParseResult result) {
        log.debug("onParseOk");
        XmlDb.getInstance().removeParseListener(this);
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
            videoInfo = xmlDb.getEntry(mCurrentVideo.getFileUri());
            if(videoInfo!=null){
                log.debug("videoInfo!=null "+videoInfo.resume);
                mCurrentVideo.setRemoteResumeMs(videoInfo.resume);
                updateActionButtons();
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        log.debug("onMenuItemClick: " + item.getItemId());
        switch(item.getItemId()){
            case R.string.video_browser_unindex_file :
                DbUtils.markAsHiddenByUser(getActivity(), mCurrentVideo);
                break;
            case R.string.scrap_remove:
                DbUtils.deleteScraperInfo(getActivity(), mCurrentVideo);
                break;
            case R.string.info_menu_backdrop_select:
                Intent intent = new Intent(getActivity(), VideoInfoPosterBackdropActivity.class);
                intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO, mCurrentVideo);
                intent.putExtra(VideoInfoPosterBackdropActivity.EXTRA_CHOOSE_BACKDROP, true);
                startActivityForResult(intent, REQUEST_BACKDROP_ACTIVITY);
                break;
            case R.string.delete:
                deleteFile_async(mCurrentVideo);
                log.debug("onMenuItemClick: deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
                break;
            case R.string.nfo_export_button:
                NfoWriter.ExportContext exportContext = new NfoWriter.ExportContext();
                try {
                    NfoWriter.export(mCurrentVideo.getFileUri(), mTags, exportContext);
                    Toast.makeText(getActivity(),R.string.nfo_export_exporting, Toast.LENGTH_LONG).show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case R.string.info_menu_poster_select:
                selectNewPoster();
                break;
            case R.string.mark_as_not_watched:
                DbUtils.markAsNotRead(getActivity(), mCurrentVideo);
                break;
            case R.string.mark_as_watched:
                DbUtils.markAsRead(getActivity(), mCurrentVideo);
                break;
            case R.string.copy_on_device:

                List<Uri> list = new ArrayList<Uri>();
                list.add(mCurrentVideo.getFileUri());
                if(FileManagerService.fileManagerService==null) {
                    log.debug("onMenuItemClick download video: binding FileManagerService since FileManagerService.fileManagerService==null");
                    isFileManagerServiceBound = getContext().bindService(new Intent(getContext(), FileManagerService.class), new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder service) {
                            log.debug("onMenuItemClick: FileManagerService connected");
                            FileManagerService.fileManagerService.copyUri(list, Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)));
                            mPasteDialog = new Paste(getActivity());
                            mPasteDialog.show();
                        }
                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            log.debug("onMenuItemClick: FileManagerService disconnected");
                        }
                    }, Context.BIND_AUTO_CREATE);
                } else {
                    log.debug("onMenuItemClick: FileManagerService exists, download video and show paste dialog..");
                    FileManagerService.fileManagerService.copyUri(list, Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)));
                    mPasteDialog = new Paste(getActivity());
                    mPasteDialog.show();
                }
                break;
        }

        return true;
    }

    public void startAsyncTasks() {
        log.debug("startAsyncTasks with " + mCurrentVideo.getFilePath());
        //do not execute file info task when torrent file
        if((mCurrentVideo.getFileUri() != null &&
                mCurrentVideo.getFileUri().getLastPathSegment() != null &&
                !mCurrentVideo.getFileUri().getLastPathSegment().endsWith("torrent")) ||
                mIsLaunchFromPlayer) {
            log.debug("startAsyncTasks not a torrent or mIsLaunchFromPlayer starting VideoInfoTask for " + mCurrentVideo.getFilePath());
            if (mVideoInfoTask != null)
                mVideoInfoTask.cancel(true);
            mVideoInfoTask = new VideoInfoTask().execute(mCurrentVideo);//crash when different executor (can't run 2, when leaving activity and launching another)
        }
        else{
            log.debug("startAsyncTasks torrent and not mIsLaunchFromPlayer removing views " + mCurrentVideo.getFilePath());
            mFileInfoAudioVideoContainer.setVisibility(View.GONE);
            mFileError.setVisibility(View.GONE);
            mFileInfoContainerLoading.setVisibility(View.GONE);
        }
        if (mSubtitleFilesListerTask != null)
            mSubtitleFilesListerTask.cancel(true);
        mSubtitleFilesListerTask = new SubtitleFilesListerTask(getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mCurrentVideo);
    }

    @Override
    public void onScrollChanged(int i, boolean b, boolean b1) {
        updateHeaderBackground(i, true);

    }

    private void updateHeaderBackground(int scroll, boolean animate) {
        // belt and suspenders to be sure that mHeaderHeight is not null
        float coeff = 1;
        if (mHeaderHeight != 0)
            coeff = (float) scroll / (float) mHeaderHeight;
        else {
            log.debug("updateHeaderBackground Warning mHeaderHeight is null!!! Generating stacktrace...", new Exception());
            coeff = 1;
        }
        if (coeff > 1)
            coeff = 1;
        if (coeff < 0)
            coeff=0;
        int alpha = (int) (coeff * 255);
        if(mIsPortraitMode) {
            mTitleBar.setBackgroundColor(VideoInfoCommonClass.getAlphaColor(mColor, alpha));
            ViewCompat.setElevation(mTitleBar, coeff * 5);
        }
            if (!mIsLaunchFromPlayer &&scroll >=  (!mIsPortraitMode?-mHeaderHeight:0)+getResources().getDimension(R.dimen.video_info_poster_height) + getResources().getDimension(R.dimen.video_info_margin_half)) {
                mFABManager.showFAB(animate);
            } else if(!mIsLaunchFromPlayer) {
                mFABManager.hideFAB(animate);
            }

        if(!mIsPortraitMode){
            Rect bounds = new Rect();
            mTitleBar.getDrawingRect(bounds);
            bounds.top = bounds.top - (bounds.bottom-bounds.top)/2;
            Rect scrollBounds = new Rect(mScrollView.getScrollX(), mScrollView.getScrollY(),
                    mScrollView.getScrollX() + mScrollView.getWidth(), mScrollView.getScrollY() + mScrollView.getHeight());

            if(Rect.intersects(scrollBounds, bounds)||scroll==0) // when titlebar is displayed, hide secondary bar
            {
                if(mTitleBarContent.getAnimation()!=null)
                    mTitleBarContent.getAnimation().cancel();
                ViewCompat.setElevation(mToolbarContainer, 0);
                mToolbarContainer.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent));
                mTitleBarContent.setAnimation(null);
                mTitleBarContent.setVisibility(View.GONE);
            }
            else if(mTitleBarContent.getVisibility()!=View.VISIBLE){
                mTitleBarContent.setVisibility(View.VISIBLE);
                if(mTitleBarContent.getAnimation()!=mToolbarShowAnimation) {
                    if (mTitleBarContent.getAnimation() != null)
                        mTitleBarContent.getAnimation().cancel();
                    if (animate) {
                        mTitleBarContent.startAnimation(mToolbarShowAnimation);
                    }
                    else
                        onAnimationEnd(mToolbarShowAnimation);
                }
            }
        }
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        ViewCompat.setElevation(mToolbarContainer, 5);
        mToolbarContainer.setBackgroundColor(mColor); //elevation needs a background color
    }


    @Override
    public void startActivityWithResultListener(Intent intent) {
        log.debug("startActivityWithResultListener");
        startActivityForResult(intent, PLAY_ACTIVITY_REQUEST_CODE);
    }

    //retrieve info on file such as codecs, etc
    private class VideoInfoTask extends AsyncTask<Video, Integer, VideoMetadata> {
        protected void onPreExecute() {

            mFileInfoAudioVideoContainer.setVisibility(View.GONE);
            mFileError.setVisibility(View.GONE);
            mFileInfoContainerLoading.setVisibility(View.VISIBLE);
        }

        @Override
        protected VideoMetadata doInBackground(Video... videos) {
            if(mIsLaunchFromPlayer&&mVideoMetadataFromPlayer!=null&&mVideoMetadataFromPlayer.getVideoTrack()!=null)//use this video info only when video track is available
                return mVideoMetadataFromPlayer;
            Video video = videos[0];
            String startingPath= video.getFilePath();

            log.debug("VideoInfoTask doInBackground for " + startingPath);


            if(mVideoMetadateCache.containsKey(startingPath)){
                log.debug( "VideoInfoTask doInBackground, metadata retrieved from cache "+startingPath);
                return mVideoMetadateCache.get(startingPath);
            }
            else {

                // Get metadata from file
                VideoMetadata videoMetaData = VideoInfoCommonClass.retrieveMetadata(video, getActivity());
                if(video!=null&&video.isIndexed()) {
                    log.debug("VideoInfoTask doInBackground, saving "+startingPath);

                    videoMetaData.save(getActivity(), startingPath);
                    log.debug("VideoInfoTask doInBackground, saved " + startingPath);


                }
                mVideoMetadateCache.put(startingPath, videoMetaData);
                log.debug("VideoInfoTask doInBackground, set MetaData " + startingPath);
                video.setMetadata(videoMetaData);
                return videoMetaData;
            }
        }

        protected void onPostExecute(VideoMetadata videoInfo) {
            log.debug("onPostExecute");
            if(isCancelled())
                return;
            // Update the video object with the computed metadata
            if(mCurrentVideo!=null)
                mCurrentVideo.setMetadata(videoInfo);

            setFileInfo(videoInfo);
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
            if(mSubtitleListCache.containsKey(video.getFilePath()))
                return mSubtitleListCache.get(video.getFilePath());
            SubtitleManager lister = new SubtitleManager(mActivity,null );
            log.debug("SubtitleFilesListerTask:doInBackground listLocalAndRemotesSubtitles");
            List<SubtitleManager.SubtitleFile> list = lister.listLocalAndRemotesSubtitles(video.getFileUri(), true);
            mSubtitleListCache.put(video.getFilePath(), list);
            return list;
        }

        @Override
        protected void onPostExecute(List<SubtitleManager.SubtitleFile> subtitleFiles) {
            if(isCancelled())
                return;

            updateSubtitleInfo(mCurrentVideo.getMetadata(), subtitleFiles);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        log.debug("onViewStateRestored");
        super.onViewStateRestored(savedInstanceState);
        //seems that at this point mHeaderHeight is null even if force measured via updateHeaderHeight(), thus do not do it here
        //updateHeaderBackground(mScrollView.getCurrentScrollY(), false );
    }

    @Override
    public void onDetach(){
        log.debug("onDetach");
        super.onDetach();
        if(mVideoInfoTask!=null)
            mVideoInfoTask.cancel(true);
        if(mThumbnailTask!=null)
            mThumbnailTask.cancel(true);
        if(mSubtitleFilesListerTask!=null)
            mSubtitleFilesListerTask.cancel(true);
        if(mFullScraperTagsTask!=null)
            mFullScraperTagsTask.cancel(true);
        removeNetworkListener();
    }
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        log.debug("onCreateLoader for id=" + id);

        // If we don't have the video object
        if(mCurrentVideo==null){
            log.debug("onCreateLoader, current video object null, searching");
            if(mVideoIdFromPlayer!=-1){
                log.debug("onCreateLoader, mVideoIdFromPlayer!=-1, SingleVideoLoader on mVideoIdFromPlayer=" + mVideoIdFromPlayer);
                return new SingleVideoLoader(getActivity(),mVideoIdFromPlayer).getV4CursorLoader(true, false);
            }
            if(mPath!=null){
                log.debug("onCreateLoader, mVideoIdFromPlayer==-1, SingleVideoLoader on mPath=" + mPath);
                return new SingleVideoLoader(getActivity(),mPath).getV4CursorLoader(true, false);
            }
        }
        else {
            if (mCurrentVideo.isIndexed()) {
                log.debug("onCreateLoader, dealing with non indexed video id " + mCurrentVideo.getId());
                return new MultipleVideoLoader(getActivity(), mCurrentVideo.getId()).getV4CursorLoader(true, false);
            } else {
                log.debug("onCreateLoader, dealing with idexed video path " + mCurrentVideo.getFilePath());
                return new MultipleVideoLoader(getActivity(), mCurrentVideo.getFilePath()).getV4CursorLoader(true, false);
            }
        }
        return null;
    }


    private void updateSourceList(){
        if(mVideoBadgePresenter == null)
            mVideoBadgePresenter = new VideoBadgePresenter(getActivity());
        mVideoBadgePresenter.setSelectedBackgroundColor(mColor);
        log.debug("updateSourceList, mCurrentVideo.getFileUri()=" + mCurrentVideo.getFileUri());
        mVideoBadgePresenter.setSelectedUri(mCurrentVideo.getFileUri());
        mSourceLayout.removeAllViews();
        if(mVideoList.size()>1){
            for(final Video video: mVideoList){
                log.debug("updateSourceList, mVideoList.size()>1 video.getFilepath()=" + video.getFilePath());
                View view = mVideoBadgePresenter.getView(mSourceLayout, video,null);
                mVideoBadgePresenter.bindView(view, video, null, 0);
                mSourceLayout.addView(view);
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setSelectedSource(video);
                    }
                });
            }
        }
    }

    private void setSelectedSource(Video video) {
        log.debug("setSelectedSource video.getFilepath()=" + video.getFilePath());
        setCurrentVideo(video);
        LoaderManager.getInstance(this).restartLoader(1, null, this);
    }

    @Override
    public void onLoadFinished(Loader loader, Cursor cursor) {
        Video oldVideoObject = mCurrentVideo;
        Video newVideo =null;
        List<Video> oldVideoList = new ArrayList<>(mVideoList);
        mVideoList.clear();

        // Getting an empty cursor here means that the video is not indexed
        if (cursor.getCount()<1) {
            // we're changing from indexed case to non-indexed case (user probably unindexed file some milliseconds ago)
            if (oldVideoObject!=null) {
                log.debug("onLoadFinished: " + ((oldVideoObject == null) ? "null" : oldVideoObject.getFilePath()) );
                // building a new unindexed video object using the Uri and name we had in the previous video object
                newVideo = new NonIndexedVideo( oldVideoObject.getStreamingUri(),oldVideoObject.getFileUri(), oldVideoObject.getName(), oldVideoObject.getPosterUri() );

                // If the video was indexed we did a query based on its ID.
                // It is not indexed anymore hence we need to change our query and have it based on the path now
                // (else a new indexing would need to no cursor loader update callback)
                if (oldVideoObject.isIndexed()) {
                    LoaderManager.getInstance(this).restartLoader(1, null, this);
                }
            }
            // If we have no Video object (case it's launched from player with path only)
            else {
                newVideo = new NonIndexedVideo(mPath); // TODO corner case BUG: gte only cryptic name from url for non-indexed UPnP when Details are opened from player
                log.debug("onLoadFinished: " + ((newVideo == null) ? "null" : newVideo.getFilePath()) );
            }

            //TODO remove sources list
        } else {
            log.debug("onLoadFinished: found " + cursor.getCount() + " videos");
            // Build video objects from the new cursor data

            cursor.moveToFirst();
            newVideo = null;
            VideoCursorMapper cursorMapper = new VideoCursorMapper();
            cursorMapper.publicBindColumns(cursor);
            do {

                Video video =  (Video) cursorMapper.publicBind(cursor);
                log.debug("onLoadFinished: " + ((video == null) ? "null" : video.getFilePath()) );
                mOnlineId = cursor.getLong(cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID));
                log.debug("online id " + mOnlineId);
                mVideoList.add(video);
                video.setMetadata(mVideoMetadateCache.get(video.getFilePath()));
                log.debug("found video : " + video.getFileUri());
                if(!mSelectCurrentVideo){ // get most advanced video
                    if(video.getLastPlayed()>0&&newVideo==null||newVideo!=null&&video.getLastPlayed()>newVideo.getLastPlayed()){
                        newVideo = video;
                    }
                }
                else if(oldVideoObject!=null&&video.getFileUri().equals(oldVideoObject.getFileUri())){
                    newVideo = video;
                }
            }while (cursor.moveToNext());
            Collections.sort(mVideoList, new SortByFavoriteSources(oldVideoList));
            mSelectCurrentVideo = true;
            if(newVideo == null)
                newVideo = mVideoList.get(0);
        }
        // Keep the video decoder metadata if we already have it (we don't want to compute it again, it can be long)
        VideoMetadata alreadyComputedVideoMetadata = null;
        if (newVideo.getFileUri() != null)
            alreadyComputedVideoMetadata = mVideoMetadateCache.get(newVideo.getFileUri().toString());
        // Keep the video decoder metadata if we already have it
        newVideo.setMetadata(alreadyComputedVideoMetadata); // may be null (fyi)
        log.debug("onLoadFinished: setCurrentVideo " + ((newVideo == null) ? "null" : newVideo.getFilePath()) );
        setCurrentVideo(newVideo);

        updateSourceList();
    }

    private void updateWatchedStatus() {
        mWatchedStatus = false;
        if((mVideoList == null || mVideoList.size()==0)){
            if(mCurrentVideo!=null) {
                log.debug("updateWatchedStatus for mCurrentVideo=" + mCurrentVideo.getFilePath());
                mWatchedStatus = mCurrentVideo.isWatched();
            }
        }
        else{
            for(Video video : mVideoList){
                mWatchedStatus = video.isWatched();
                log.debug("updateWatchedStatus for multiple videos, video=" + video.getFilePath());
                if(mWatchedStatus)
                    break;
            }
        }
        mWatchedView.setVisibility(mWatchedStatus?View.VISIBLE:View.GONE);
    }

    @Override
    public void onLoaderReset(Loader loader) {
        log.debug("onLoaderReset, do nothing?");
    }

    private class ThumbnailAsyncTask extends AsyncTask<Video, Void, Pair<Bitmap,Video>> {



        @Override
        protected Pair<Bitmap,Video> doInBackground(Video... videos) {
            Video video = videos[0];
            Bitmap bitmap = getThumbnail(video, true);

            return new Pair<>(bitmap, video);
        }

        @Override
        protected void onPostExecute(Pair<Bitmap,Video> result) {
            if(isCancelled())
                return;
            if(result.second==mCurrentVideo) {
                if (result.first != null) {
                    mPosterImageView.setImageBitmap(result.first);
                    mPosterImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                } else {
                    mPosterImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.filetype_new_video_poster));
                    mPosterImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
                setBackground();
                updateSourceList();
            }

        }
    }

    private class FullScraperTagsTask extends AsyncTask<Video, Void, BaseTags> {
        private final Activity mActivity;
        private List<ScraperTrailer> mTrailers;

        public FullScraperTagsTask(Activity activity){
            mActivity = activity;
        }
        private Activity getActivity(){
            return mActivity;
        }
        protected void onPreExecute() {
            mTags = null;
        }
        @Override
        protected BaseTags doInBackground(Video... videos) {

            Video video = videos[0];
            BaseTags tags = video.getFullScraperTags(getActivity());
            if (tags!=null && !isCancelled())
                mTrailers = tags.getAllTrailersInDb(getActivity());
            else
                mTrailers = null;
            return tags;
        }

        protected void onPostExecute(BaseTags tags) {
            if(isCancelled()||!isAdded()||isDetached())
                return;
            mTags = tags;
            if (tags!=null) {

                // Plot & Genres
                final String plot = tags.getPlot();
                if(!mIsLaunchFromPlayer)
                    mBackgroundSetter.set(mApplicationBackdrop, mBackgroundLoader, tags.getDefaultBackdrop());
                String genres = null;
                if (tags instanceof VideoTags) {
                    mIsVideoMovie = null;
                    genres = ((VideoTags) tags).getGenresFormatted();
                }
                setTextOrHideContainer(mPlotTextView, plot, mPlotTextView);
                setTextOrHideContainer(mGenreTextView, genres, mGenreTextView);
                // Cast
                String cast = tags.getActorsFormatted();
                // If cast is null and this is an episode, get the cast of the Show
                String studio = null;

                if (cast == null & tags instanceof EpisodeTags) {
                    ShowTags showTags = ((EpisodeTags) tags).getShowTags();
                    cast = showTags != null ? showTags.getActorsFormatted() : null;
                }
                setTextOrHideContainer(mCastTextView, cast, mCastTextView, mCastTextViewTitle);
                setTextOrHideContainer(mScrapDirector, tags.getDirectorsFormatted(), mScrapDirector, mScrapDirectorTitle);
                setTextOrHideContainer(mScrapWriter, tags.getWritersFormatted(), mScrapWriter, mScrapWriterTitle);
                String date = null;
                if(tags instanceof EpisodeTags){
                    mIsVideoMovie = false;
                    mTVDBIcon.setVisibility(View.GONE);
                    DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
                    if (((EpisodeTags) tags).getAired() != null && ((EpisodeTags) tags).getAired().getTime() > 0) {
                        // Display the aired date of the current episode
                        date = df.format(((EpisodeTags) tags).getAired());
                    } else if (((EpisodeTags) tags).getShowTags() != null && ((EpisodeTags) tags).getShowTags().getPremiered() != null && ((EpisodeTags) tags).getShowTags().getPremiered().getTime() > 0) {
                        // Aired date not available => try at least the premiered date
                        date = df.format(((EpisodeTags) tags).getShowTags().getPremiered());
                    }
                    if (((EpisodeTags) tags).getShowTags() != null)
                        studio = ((EpisodeTags) tags).getShowTags().getStudiosFormatted();
                    //tags.getOnlineId() is the episodeId not the show Id thus using mOnlineID
                    //mTMDBIcon.setVisibility(tags.getOnlineId()>=0?View.VISIBLE:View.GONE);
                    mTMDBIcon.setVisibility(mOnlineId>=0?View.VISIBLE:View.GONE);
                    //mTMDBId = tags.getOnlineId();
                    mTMDBId = mOnlineId;
                    log.debug("FullScraperTagsTask:onPostExecute: mTMDBId=" + mTMDBId);
                }
                else if(tags instanceof MovieTags){
                    mIsVideoMovie = true;
                    mTVDBIcon.setVisibility(View.GONE);
                    mTMDBIcon.setVisibility(tags.getOnlineId()>=0?View.VISIBLE:View.GONE);
                    mTMDBId = tags.getOnlineId();
                    date = ((MovieTags) tags).getYear()+"";
                    studio = ((MovieTags) tags).getStudiosFormatted();
                    log.debug("FullScraperTagsTask:onPostExecute: mTMDBId=" + mTMDBId);
                }
                // set content rating
                if (tags.getContentRating()==null || tags.getContentRating().isEmpty()) {
                    mScrapContentRating.setVisibility(View.GONE);
                    mScrapContentRatingContainer.setVisibility(View.GONE);
                } else {
                    setTextOrHideContainer(mScrapContentRating, tags.getContentRating());
                }
                mIMDBId = tags.getImdbId();
                if(mIMDBId==null||mIMDBId.isEmpty())
                    mIMDBIcon.setVisibility(View.GONE);
                setTextOrHideContainer(mScrapStudio, studio,mScrapStudioContainer);
                setTextOrHideContainer(mScrapYear, date, mScrapYear);
                setTextOrHideContainer(mScrapDuration, MediaUtils.formatTime(mCurrentVideo.getDurationMs()),mScrapDuration);
                setTextOrHideContainer(mScrapRating, String.valueOf(tags.getRating()), mScrapRating);
                if(tags.getRating()==0)
                    mScrapRating.setVisibility(View.GONE);
                if((plot == null||plot.isEmpty())&&(studio==null||studio.isEmpty())&&(date==null||date.isEmpty())&&mCurrentVideo.getDurationMs()==0&&tags.getRating()==0&&(genres==null||genres.isEmpty()))
                    mScraperPlotContainer.setVisibility(View.GONE);
                mScrapTrailers =(LinearLayout) mScraperContainer.findViewById(R.id.trailer_layout);
                mScrapTrailers.removeAllViews();
                if(mTrailers!=null&&!mTrailers.isEmpty()) {
                    mScrapTrailersContainer.setVisibility(View.VISIBLE);
                    for (final ScraperTrailer trailer : mTrailers) {
                        Button button = new Button(getContext());
                        button.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Breaks AndroidTV acceptance but required to open link in app instead of browser
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, trailer.getUrl());
                                startActivity(browserIntent);
                                //WebUtils.openWebLink(getActivity(), trailer.getUrl().toString());
                            }
                        });
                        button.setText(trailer.mName);
                        Drawable img = ContextCompat.getDrawable(getContext(), TrailerServiceIconFactory.getIconForService(trailer.mSite));
                        img.setBounds(0, 0, 60, 60);
                        button.setCompoundDrawablePadding(10);
                        button.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                        button.setBackgroundResource(R.drawable.transparent_ripple);
                        button.setCompoundDrawables(img, null, null, null);
                        button.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                        mScrapTrailers.addView(button);
                    }

                }
                else{
                    mScrapTrailersContainer.setVisibility(View.GONE);
                }
            } else { // tag is null
                mScrapContentRating.setVisibility(View.GONE);
                mScrapContentRatingContainer.setVisibility(View.GONE);
            }
        }
    }

    private void setTextOrHideContainer(TextView textView, String text, View... toHideOrShow) {
        if(text!=null&&!text.isEmpty()) {
            textView.setText(text);
            if(toHideOrShow!=null){
                for(View v : toHideOrShow)
                    v.setVisibility(View.VISIBLE);
            }
        }
        else  if(toHideOrShow!=null){
            for(View v : toHideOrShow)
                v.setVisibility(View.GONE);
        }

    }

    //not used implementations
    public void onDownMotionEvent() {   }
    public void onUpOrCancelMotionEvent(ScrollState scrollState) {  }
    public void onAnimationStart(Animation animation) {    }
    public void onAnimationRepeat(Animation animation) {   }

    /* delete */
    private void deleteFile_async(Video video) {
        delete = new Delete(this, getActivity());
        deleteUrisList = new ArrayList<>(Arrays.asList(video.getFileUri()));
        log.debug("deleteFile_async: " + video.getFilePath() + ", deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
        delete.startDeleteProcess(video.getFileUri());
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
                                delete = new Delete(VideoInfoActivityFragment.this, getActivity());
                                deleteUrisList = Collections.singletonList(folder);
                                log.debug("onVideoFileRemoved: " + folder + ", deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
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
        }
    }

    private void sendDeleteResult(Uri file){
        log.debug("sendDeleteResult: " + file);
        Intent intent = new Intent();
        intent.setData(file);
        getActivity().setResult(BrowserByFolder.RESULT_FILE_DELETED, intent);
        slightlyDelayedFinish();
    }

    private void slightlyDelayedFinish() {
        log.debug("slightlyDelayedFinish");
        getActivity().finish();
    }

    @Override
    public void onDeleteVideoFailed(Uri videoFile) {
        log.debug("onDeleteVideoFailed: " + videoFile);
        if (getActivity() != null) {
            Toast.makeText(getActivity(), R.string.delete_error, Toast.LENGTH_SHORT).show();
            // close the fragment anyway because the un-indexing may work even if the actual delete fails
            slightlyDelayedFinish();
        }
    }

    @Override
    public void onFolderRemoved(Uri folder) {
        log.debug("onFolderRemoved: " + folder);
        if (getActivity() != null) {
            Toast.makeText(getActivity(), R.string.delete_done, Toast.LENGTH_SHORT).show();
            sendDeleteResult(folder);
        }
    }

    @Override
    public void onDeleteSuccess() {}

    @Override
    public void onResume() {
        super.onResume();
        // do not forget this one otherwise com.android.providers.media.PermissionActivity NullPointerException Unable to destroy activity
        FileUtilsQ.setDeleteLauncher(deleteLauncher);
        // update video in case of binge watching or repeat mode
        log.debug("onResume: mIsLeavingPlayerActivity " + mIsLeavingPlayerActivity);
        long playerVideoId = CustomApplication.getLastVideoPlayedId();
        Uri playerVideoUri = CustomApplication.getLastVideoPlayedUri();
        if (mCurrentVideo != null) log.debug("onResume: current mCurrentVideo " + mCurrentVideo.getFileUri() + "(" + mCurrentVideo.getId() +
                "), playerVideo " + playerVideoUri + "(" + playerVideoId +"), mVideoIdFromPlayer " + mVideoIdFromPlayer +
                ", mVideoFromPlayer " + mVideoPathFromPlayer + "(" + mVideoIdFromPlayer + ")");
        else log.debug("onResume: current mVideo is null");

        if ((playerVideoId != -42 && mCurrentVideo.getId() != playerVideoId) ||
                (playerVideoUri != null && mCurrentVideo.getFileUri() != playerVideoUri)) {
            Video mNewVideo;
            mVideoPathFromPlayer = playerVideoUri.toString();
            mVideoIdFromPlayer = playerVideoId;
            log.debug("onResume: not the same video than before (repeat mode?) target is " + mVideoPathFromPlayer);
            // get mVideo set to new video
            CursorLoader loader = new MultipleVideoLoader(getActivity(), mVideoPathFromPlayer);
            Cursor c = loader.loadInBackground();
            if (c.getCount()>0) {
                c.moveToFirst();
                mNewVideo = (Video) new CompatibleCursorMapperConverter(new VideoCursorMapper()).convert(c);
                log.debug("onResume: yay we get a new video " + mNewVideo.getFilePath());
                setSelectedSource(mNewVideo);
                //setCurrentVideo(mNewVideo);
                //updateSourceList();
            } else {
                log.debug("onResume: oops no video found");
            }
            c.close();
            // TODO: refresh overall UI and preserve below?
            //LoaderManager.getInstance(this).restartLoader(1, null, this);
            //mFirstOnResume = true; // trigger reload of the info
        }

        if(mIsLeavingPlayerActivity)
            StoreRatingDialogBuilder.displayStoreRatingDialogIfNeeded(getContext());
        mIsLeavingPlayerActivity = false;
        addNetworkListener();
        updateUI(); // be sure to be on right state
        if (mCurrentVideo != null) {
            log.debug("onResume: mCurrentVideo.getName()=" + mCurrentVideo.getName());
        } else {
            log.debug("onResume: mCurrentVideo=null");
        }
    }

    @Override
    public void onPause() {
        log.debug("onPause");
        removeNetworkListener();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");
        removeNetworkListener();
        super.onDestroy();
    }

    private void addNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(getContext());
        if (!mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (DBG_LISTENER) log.debug("addNetworkListener: networkState.addPropertyChangeListener");
            networkState.addPropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = true;
        }
    }

    private void removeNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(getContext());
        if (mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (DBG_LISTENER) log.debug("removeListener: networkState.removePropertyChangeListener");
            networkState.removePropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = false;
        }
    }

}
