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

package com.archos.mediacenter.video.player;

import android.annotation.SuppressLint;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.IntentCompat;
import androidx.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;

import com.archos.environment.ArchosFeatures;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.StreamUriFinder;
import com.archos.mediacenter.utils.ISO639codes;
import com.archos.mediacenter.utils.introdb.IntroDbManager;
import com.archos.mediacenter.utils.introdb.IntroDbQueryParams;
import com.archos.mediacenter.utils.introdb.IntroSegments;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.leanback.channels.ChannelManager;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediacenter.video.utils.AdditionalServiceSingleton;
import com.archos.medialib.Subtitle;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStoreImportImpl;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.environment.ArchosUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static com.archos.filecorelibrary.FileUtils.removeFileSlashSlash;
import static com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.generateTrackName;
import static com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager.getSubLanguageFromSubPathAndVideoPath;
import com.archos.medialib.LibAvos;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_AUDIO_SPEED_AUDIOTRACK;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_PLAYBACK_SPEED;
import static com.archos.mediascraper.StringUtils.stringContainsForced;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by alexandre on 28/08/15.
 */
public class PlayerService extends Service implements Player.Listener, IndexHelper.Listener {
    /*
        some explanations

        start playeractivity with specific Uri
        start PlayerService with the same intent
        PlayerService search for videoInfodb
        PlayerService send videoInfoDb to playerActivity which makes a choice between local and remote
        playerActivity send back chosen videoInfoDb
        PlayerService starts reading Video

        -

        PlayerActivity goes to floating mode :
            remove itself ad frontend with transition (removeFrontend(true))
            start floatingplayerservice
            FloatingPlayer binds to playerservice
            PlayerActivity unbind from playerservice
            PlayerActivity destroys itself
        addPlayerFrontend
        PlayerService starts itself with last intent
        onPrepared is called
        seeks to last position
     */

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    public static final String PLAYER_SERVICE_STARTED = "PLAYER_SERVICE_STARTED";

    public static final boolean AUDIO_SPEED_ON_THE_FLY = true;
    public static final boolean USE_NONETRACK_IF_SUB_LANG_NOT_FOUND = false;
    public static final int RESUME_NO = 0;
    public static final int RESUME_FROM_LAST_POS = 1;
    public static final int RESUME_FROM_BOOKMARK = 2;
    public static final int RESUME_FROM_REMOTE_POS = 3;
    public static final int RESUME_FROM_LOCAL_POS = 4;
    public static final String RESUME = "resume";
    public static final String LAUNCH_GENERATION = "player_service_launch_generation";
    public static final String SESSION_POSITION = "player_service_session_position";
    public static final String PREFERENCE_USER_PAUSED_VIDEO = "user_paused_video";
    public static final String PREFERENCE_USER_PAUSED_URI = "user_paused_video_uri";

    /*
     * Resume ownership workflow:
     * 1. PlayerService parses launch/external positions and keeps every provider as an independent
     *    candidate (local DB, network XML, Trakt, bookmark, explicit intent and live playback).
     * 2. PlayerActivity may ask the user which candidate to use, but only PlayerService selects
     *    and applies the start position.
     * 3. Before Home/screensaver teardown, PlayerService writes its live checkpoint into the
     *    frontend intent. A recreated service restores that checkpoint before consulting the DB.
     * 4. Pause/stop/completion persistence is captured and ordered by the service/IndexHelper;
     *    frontends never maintain a competing runtime resume position.
     */
    public enum ResumeSource {
        NONE,
        LOCAL,
        NETWORK,
        TRAKT,
        BOOKMARK,
        EXPLICIT,
        LIVE
    }

    public static final class PlaybackSnapshot {
        private final int positionMs;
        private final int durationMs;
        private final boolean completed;
        private final boolean paused;
        private final ResumeSource resumeSource;

        private PlaybackSnapshot(int positionMs, int durationMs, boolean completed,
                                 boolean paused, ResumeSource resumeSource) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.completed = completed;
            this.paused = paused;
            this.resumeSource = resumeSource;
        }

        public int getPositionMs() { return positionMs; }
        public int getDurationMs() { return durationMs; }
        public boolean isCompleted() { return completed; }
        public boolean isPaused() { return paused; }
        public ResumeSource getResumeSource() { return resumeSource; }
    }

    private static final class PlaybackSession {
        private Uri uri;
        private String launchGeneration;
        private final EnumMap<ResumeSource, Integer> resumeCandidates =
                new EnumMap<>(ResumeSource.class);
        private final EnumMap<ResumeSource, Boolean> eligibleCandidates =
                new EnumMap<>(ResumeSource.class);
        private ResumeSource selectedSource = ResumeSource.NONE;
        private int selectedStartPositionMs = 0;
        private int lastKnownPositionMs = LAST_POSITION_UNKNOWN;
        private boolean completed;
        private boolean startPositionApplied;

        private void reset(Uri newUri, String newLaunchGeneration) {
            uri = newUri;
            launchGeneration = newLaunchGeneration;
            resumeCandidates.clear();
            eligibleCandidates.clear();
            selectedSource = ResumeSource.NONE;
            selectedStartPositionMs = 0;
            lastKnownPositionMs = LAST_POSITION_UNKNOWN;
            completed = false;
            startPositionApplied = false;
        }

        private void setCandidate(ResumeSource source, int positionMs) {
            setCandidate(source, positionMs, true);
        }

        private void setCandidate(ResumeSource source, int positionMs, boolean eligible) {
            if (positionMs > 0) {
                resumeCandidates.put(source, positionMs);
                eligibleCandidates.put(source, eligible);
            } else {
                resumeCandidates.remove(source);
                eligibleCandidates.remove(source);
            }
        }

        private int getCandidate(ResumeSource source) {
            if (!Boolean.TRUE.equals(eligibleCandidates.get(source))) return 0;
            Integer position = resumeCandidates.get(source);
            return position != null ? position : 0;
        }

        private void select(ResumeSource source, int positionMs) {
            selectedSource = source;
            selectedStartPositionMs = Math.max(positionMs, 0);
            if (positionMs >= 0) lastKnownPositionMs = positionMs;
            completed = false;
            startPositionApplied = false;
        }
    }

    private static final boolean PERIODIC_BOOKMARK_SAVE = false;

    public static final String PLAY_INTENT = "playerservice.play";
    public static final String PAUSE_INTENT = "playerservice.pause";
    public static final String EXIT_INTENT = "playerservice.exit";
    public static final String FULLSCREEN_INTENT = "playerservice.fullscreen";
    public static final String PLAYLIST_ID = "playlist_id";
    public static final String VIDEO = "extra_video";
    private static final long AUTO_SAVE_INTERVAL = 30000;
    @SuppressLint("StaticFieldLeak")
    public static PlayerService sPlayerService;
    private SharedPreferences mPreferences;
    private PlayerFrontend mPlayerFrontend;
    private Handler mHandler;
    private Player mPlayer;
    public static final String KEY_STREAMING_URI = "streaming_uri";
    private Uri mUri;
    private Uri mStreamingUri;
    private boolean mDatabaseInfoHasBeenRetrieved;
    private boolean mPlayOnResume;
    private long mVideoId;
    public PlayerState  mPlayerState = PlayerState.INIT;
    private MediaSession mSession;
    private UpdateNextTask mUpdateNextTask;
    private Uri mNextUri;
    private long mNextVideoId;
    // keep in sync with res/values/arrays.xml - pref_play_mode_entries
    private static final int PLAYMODE_SINGLE = 0;
    private static final int PLAYMODE_FOLDER = 1;
    private static final int PLAYMODE_REPEAT_SINGLE = 2;
    private static final int PLAYMODE_REPEAT_FOLDER = 3;
    private static final int PLAYMODE_BINGE = 4;

    public static final int LAST_POSITION_UNKNOWN = -1;
    public static final int LAST_POSITION_END = -2;

    public static final String KEY_ORIGINAL_TORRENT_URL = "original_torrent_uri";

    private static final String KEY_HIDE_SUBTITLES = "subtitles_hide_default";
    private static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";
    private static final String KEY_SUBTITLES_FAVORITE_LANGUAGE = "favSubLang";
    private static final String KEY_AUDIO_TRACK_FAVORITE_LANGUAGE = "favAudioLang";
    private static final String VIDEO_PLAYER_DEMO_MODE_EXTRA = "demo_mode";
    private boolean mForceSingleRepeatMode;

    public static final String PREFERENCE_LAST_TIME_VIDEO_PLAYED_UTC = "last_time_video_played_utc";

    private boolean mNetworkBookmarksEnabled;
    private final PlaybackSession mPlaybackSession = new PlaybackSession();
    private boolean mIsChangingSurface;
    private int mResume;
    private boolean firstTimeSubCalled = true;
    private boolean firstTimeAudioCalled = true;

    private boolean mHideSubtitles = false;
    private int mNewSubtitleTrack;
    private boolean mAudioSubtitleNeedUpdate;
    private Intent mIntent;
    public int mPlayMode=0;
    private int mAudioDelay;
    private float mAudioSpeed = 1.0f;
    private int mNewAudioTrack;
    public int mAudioFilt;
    public boolean mNightModeOn;
    private static final String KEY_AUDIO_FILT = "pref_audio_filt_int_key"; // used to be "pref_audio_filt_key", containing a string
    private static final String KEY_AUDIO_FILT_NIGHT = "pref_audio_filt_night_int_key";
    private static final String[] GENERIC_TEXT_SUBTITLE_FORMATS = {"srt", "vtt"};
    private boolean mCallOnDataUriOKWhenVideoInfoIsSet;
    private boolean mIsPreparingSubs;
    private String mSubsFavoriteLanguage;
    private String mAudioTrackFavoriteLanguage;
    private boolean mPreferOriginalAudioTrack;
    private boolean mDestroyed;
    private Runnable mAutoSaveTask;
    private CountDownLatch mSubtitlesReadyLatch = null;
    private static final long SUBTITLE_ENUMERATION_TIMEOUT_MS = 5000; // 5 second timeout for subtitle enumeration before starting video

    public enum PlayerState {
        INIT,
        PREPARING,
        PREPARED,
        PLAYING,
        PAUSED,
        STOPPED,
    }

    /* private static final int PLAYER_NOTIFICATION_ID = 5;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "PlayerService_id";
    private static final String notifChannelName = "PlayerService";
    private static final String notifChannelDescr = "PlayerService"; */

    private boolean isSeeking = false;

    public boolean isSeeking() {
        return isSeeking;
    }

    public void setSeeking(boolean seek) {
        isSeeking = seek;
    }

    /* Torrent */


    private TorrentObserverService mTorrent;
    private int mTorrentFilePosition;
    private String mTorrentURL;
    private ServiceConnection mTorrentObserverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            mTorrent  =  ((TorrentObserverService.TorrentServiceBinder) binder).getService();
            mTorrent.setParameters(mTorrentURL, mTorrentFilePosition);
            mTorrent.setObserver(mTorrentThreadObserver);
            mTorrent.start();
            // start();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mTorrent = null;
        }
    };


    private TorrentObserverService.TorrentThreadObserver mTorrentThreadObserver = new TorrentObserverService.TorrentThreadObserver() {
        @Override
        public void setPort(int port) {
            if(mIntent.getData()!=null){
                mStreamingUri = Uri.parse("http://localhost:"+port+mIntent.getData().getPath());
            }
        } // we have to get the new port

        @Override
        public void setFilesList(ArrayList<String> files) {
            mTorrent.selectFile(mTorrentFilePosition);
        }

        @Override
        public void notifyDaemonStreaming() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onDataUriOK();
                }
            });
        }

        @Override
        public void onEndOfTorrentProcess() {
        }

        @Override
        public void notifyObserver(String daemonString) {
            if(mPlayerFrontend!=null)
                mPlayerFrontend.onTorrentUpdate(daemonString);
        }

        @Override
        public void warnOnNotEnoughSpace() {
            if(mPlayerFrontend!=null)
                mPlayerFrontend.onTorrentNotEnoughSpace();
        }
    };

    /* Everything to update playmode */

    private static final String KEY_PLAY_MODE = "pref_play_mode_int_key"; // used to be "pref_play_mode_key", containing a string

    /**
     * Updates mNextUri and mNextVideoId based on the media db or by scanning the directory
     * @param repeatFolder <ul>
     *              <li><b>true</b> starts again with first in folder<br>
     *              <li><b>false</b> ends after last in folder
     *              </ul>
     */
    @SuppressWarnings("deprecation") // getSerializableExtra: API 33+ branch uses typed form; else branch cast suppressed
    private void updateNextVideo(boolean repeatFolder, boolean binge, boolean sync) {
        if (log.isDebugEnabled()) log.debug("updateNextVideo: repeatfolder {}, binge {}, sync {}", repeatFolder, binge, sync);
        // reset to nothing
        mNextUri = null;
        mNextVideoId = -1;
        if (mUpdateNextTask != null) {
            mUpdateNextTask.cancel(false);
            mUpdateNextTask.setListener(null);
        }
        mUpdateNextTask = new UpdateNextTask(getContentResolver(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? mIntent.getSerializableExtra(VIDEO, Video.class)
                        : (Video) mIntent.getSerializableExtra(VIDEO),
                mUri, null, -1, mIntent.getLongExtra(PLAYLIST_ID, -1));
        if (!sync) { // seems to be always the case in the calls
            mUpdateNextTask.setListener(new UpdateNextTask.Listener() {
                @Override
                public void onResult(Uri uri, long id) {
                    if (log.isDebugEnabled()) log.debug("updateNextVideo: UpdateNextTask onResult: next video and id {}, id: {}", uri, id);
                    mNextUri = uri;
                    mNextVideoId = id;
                    mUpdateNextTask = null;
                }
            });
            mUpdateNextTask.execute(repeatFolder, binge);
        } else {
            mUpdateNextTask.execute(repeatFolder, binge);
            UpdateNextTask.Result result = null;
            try {
                result = mUpdateNextTask.get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            }
            if (result != null) {
                mNextUri = result.uri;
                mNextVideoId = result.id;
            } else {
                mNextUri = null;
                mNextVideoId = -1;
            }
            mUpdateNextTask = null;
        }
    }
    public void menuChangePlayMode(int which) {
        int newPlaymode = which; // Caution here, playmode values must be [0,n[
        if (newPlaymode != mPlayMode) {
            mPreferences.edit()
                    .putInt(KEY_PLAY_MODE, newPlaymode)
                    .apply(); // commit is blocking.. avoid!
            PlayerService.sPlayerService.setPlayMode(newPlaymode, false);
            mPlayMode = newPlaymode;
        }
    }

    public void setPlayMode(int newPlaymode, boolean wait) {
        mPlayMode = newPlaymode;
        if (log.isDebugEnabled()) log.debug("setPlaymode: new Playmode {}", newPlaymode);
        if (PLAYMODE_REPEAT_SINGLE == newPlaymode || mForceSingleRepeatMode) {
            mPlayer.setLooping(true);
            // just in Case we drop out to OnCompletion
            mNextUri = mUri;
            mNextVideoId = mVideoId;
        } else {
            mPlayer.setLooping(false);
            if (PLAYMODE_SINGLE == newPlaymode) {
                if (log.isDebugEnabled()) log.debug("setPlaymode: PLAYMODE_SINGLE");
                // clear next
                mNextUri = null;
                mNextVideoId = -1;
            } else if (PLAYMODE_FOLDER == newPlaymode) {
                if (log.isDebugEnabled()) log.debug("setPlaymode: PLAYMODE_FOLDER");
                updateNextVideo(false, false, wait);
            } else if (PLAYMODE_REPEAT_FOLDER == newPlaymode) {
                if (log.isDebugEnabled()) log.debug("setPlaymode: PLAYMODE_REPEAT_FOLDER");
                updateNextVideo(true, false, wait);
            } else if (PLAYMODE_BINGE == newPlaymode) {
                if (log.isDebugEnabled()) log.debug("setPlaymode: PLAYMODE_BINGE");
                updateNextVideo(false, true, wait);
            } else {
                if (log.isDebugEnabled()) log.debug("unknown Playmode: {}", newPlaymode);
            }
        }
    }

    /*
        TO TEST
        Indexing
        Scraping
        Trakt
        do not save on network when network resume disabled
     */
    @Override
    public void onCreate() {
        super.onCreate();

        AdditionalServiceSingleton.getInstance().bindToService(getApplicationContext());
        if (log.isDebugEnabled()) log.debug("onCreate()");
        sPlayerService = this;
        mHandler = new Handler(Looper.getMainLooper());
        if (PERIODIC_BOOKMARK_SAVE) {
            mAutoSaveTask = new Runnable() {
                @Override
                public void run() {
                    saveVideoStateIfReady();
                    mHandler.postDelayed(mAutoSaveTask, AUTO_SAVE_INTERVAL);
                }
            };
        }
        mVideoObserver = new VideoObserver(new Handler(Looper.getMainLooper()));
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // IntroDB: init the GET-only multi-provider facade (enables the shared OkHttp disk cache)
        IntroDbManager.init(getApplicationContext());
        mAutoSkipTask = new Runnable() {
            @Override
            public void run() {
                autoSkipIfNeeded();
                mHandler.postDelayed(mAutoSkipTask, AUTO_SKIP_INTERVAL);
            }
        };

        if (Trakt.isTraktV2Enabled(this, mPreferences) && !PrivateMode.isActive()) {
            mTraktClient = new TraktService.Client(this, mTraktListener, false);
        }

        if (log.isDebugEnabled()) log.debug("onCreate: register headsetPluggedReceiver");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(headsetPluggedReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(headsetPluggedReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
        setPlayer();
        Intent intent = new Intent(PLAYER_SERVICE_STARTED);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);
    }

    /* init variables from intent

        if dataUri is ok, start player loading process
        if mVideoInfo isn't null + uri of video info equals to intent uri : start video with videoinfo and current resume

     */

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (log.isDebugEnabled()) log.debug("onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    public void onStart(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        firstTimeAudioCalled = true;
        firstTimeSubCalled = true;
        if (log.isDebugEnabled()) log.debug("onStart() ");
        mCallOnDataUriOKWhenVideoInfoIsSet = true;
        mIntent = intent;
        boolean isDemoMode = (intent.getIntExtra(VIDEO_PLAYER_DEMO_MODE_EXTRA, 0) == 1);
        mNetworkBookmarksEnabled = mPreferences.getBoolean(KEY_NETWORK_BOOKMARKS, true);
        mSubsFavoriteLanguage = mPreferences.getString(KEY_SUBTITLES_FAVORITE_LANGUAGE, Locale.getDefault().getISO3Language());
        mAudioTrackFavoriteLanguage = mPreferences.getString(KEY_AUDIO_TRACK_FAVORITE_LANGUAGE, Locale.getDefault().getISO3Language());
        mPreferOriginalAudioTrack = mPreferences.getBoolean(VideoPreferencesCommon.KEY_PREFER_ORIGINAL_AUDIO_TRACK, false);
        mAudioFilt = mPreferences.getInt(KEY_AUDIO_FILT, 0);
        mNightModeOn = mPreferences.getBoolean(KEY_AUDIO_FILT_NIGHT, false);
        mForceSingleRepeatMode = isDemoMode;
        mHideSubtitles = mPreferences.getBoolean(KEY_HIDE_SUBTITLES, false);
        mPlayMode = mPreferences.getInt(KEY_PLAY_MODE, PLAYMODE_SINGLE);
        // Any start clears the binge-transition flag; onCompletion re-sets it when auto-advancing.
        mArrivedViaBingeTransition = false;
        mResume = intent.getIntExtra(RESUME, RESUME_NO);
        if (log.isDebugEnabled()) log.debug("PlayerService.onStart: read mResume={} from intent", mResume);

        if (log.isTraceEnabled()) log.trace("PlayerService.onStart: {}",
                ExternalResumeIntent.describeForTrace(intent));
        int sessionPosition = ExternalResumeIntent.readPosition(intent, SESSION_POSITION);
        int explicitPosition = ExternalResumeIntent.readLaunchPosition(intent);
        String launchGeneration = intent.getStringExtra(LAUNCH_GENERATION);
        boolean externalPlayerLaunch = intent.getBooleanExtra(
                ExternalResumeIntent.EXTERNAL_PLAYER_LAUNCH, false);
        // This marker describes this command, not the reusable intent retained for frontend
        // handoffs (Activity <-> floating player).
        intent.removeExtra(ExternalResumeIntent.EXTERNAL_PLAYER_LAUNCH);

        mUri = intent.getData();
        mTorrentURL = mIntent.getStringExtra(PlayerActivity.KEY_TORRENT_URL);
        if(mIntent.hasExtra(KEY_ORIGINAL_TORRENT_URL)){
            mUri = Uri.parse(mIntent.getStringExtra(KEY_ORIGINAL_TORRENT_URL));
        }
        mUri = Uri.parse(removeFileSlashSlash(mUri.toString())); // we need to remove "file://"

        boolean hasCurrentSession = mPlaybackSession.uri != null;
        if (!PlaybackResumePolicy.mayRestoreCheckpoint(hasCurrentSession,
                mPlaybackSession.launchGeneration, launchGeneration)) {
            // A checkpoint from an older Activity must not override a newer command which has
            // already taken ownership of the service, even when both commands use the same URI.
            sessionPosition = LAST_POSITION_UNKNOWN;
        }

        boolean userPausedVideo = mPreferences.getBoolean(PREFERENCE_USER_PAUSED_VIDEO, false);
        String pausedUri = mPreferences.getString(PREFERENCE_USER_PAUSED_URI, null);
        // A scoped checkpoint distinguishes lifecycle recreation from a fresh launch of the
        // same URI, so an old paused preference cannot pause a later independent playback.
        boolean pausedSession = PlaybackResumePolicy.isPausedSession(
                userPausedVideo, sessionPosition, pausedUri, mUri.toString());
        mPlayOnResume = !pausedSession;
        if (log.isDebugEnabled()) log.debug("PlayerService.onStart: pausedSession={} pausedUri={} uri={}",
                pausedSession, pausedUri, mUri);

        boolean continuingSession = PlaybackResumePolicy.isContinuingLaunch(
                Objects.equals(mPlaybackSession.uri, mUri),
                mPlaybackSession.launchGeneration, launchGeneration);
        // An external application may issue a second command for the same URI. Treat it as a
        // fresh playback (including position=0/start-over); only a service checkpoint identifies
        // Home/screensaver reattachment and is allowed to retain the live session instead.
        boolean startsNewExternalSession = PlaybackResumePolicy.startsNewExternalSession(
                sessionPosition, externalPlayerLaunch);
        boolean freshExternalPositionCommand = startsNewExternalSession
                && ExternalResumeIntent.hasPositionExtra(intent);
        if (startsNewExternalSession) {
            continuingSession = false;
        }
        if (!continuingSession) {
            mPlaybackSession.reset(mUri, launchGeneration);
        }
        PlaybackResumePolicy.StartupSource startupSource = PlaybackResumePolicy.chooseStartupSource(
                continuingSession, sessionPosition, explicitPosition,
                mPlaybackSession.lastKnownPositionMs);
        switch (startupSource) {
            case CHECKPOINT:
                mPlaybackSession.setCandidate(ResumeSource.LIVE, sessionPosition);
                mPlaybackSession.select(ResumeSource.LIVE, sessionPosition);
                if (log.isDebugEnabled()) log.debug("PlayerService.onStart: restored session checkpoint={}", sessionPosition);
                break;
            case EXPLICIT:
                mPlaybackSession.setCandidate(ResumeSource.EXPLICIT, explicitPosition);
                mPlaybackSession.select(ResumeSource.EXPLICIT, explicitPosition);
                if (log.isDebugEnabled()) log.debug("PlayerService.onStart: explicit resume position={}", explicitPosition);
                break;
            case RETAINED_LIVE:
                mPlaybackSession.setCandidate(ResumeSource.LIVE, mPlaybackSession.lastKnownPositionMs);
                mPlaybackSession.select(ResumeSource.LIVE, mPlaybackSession.lastKnownPositionMs);
                break;
            case NONE:
                break;
        }
        if (log.isDebugEnabled()) log.debug("onStart() {}", mUri);
        mStreamingUri = IntentCompat.getParcelableExtra(intent, KEY_STREAMING_URI, Uri.class);
        if(mPlayerFrontend!=null)
            mPlayerFrontend.setUri(mUri, mStreamingUri);
        mVideoId = intent.getIntExtra("id", -1);
        if (log.isDebugEnabled()) log.debug("onStart mVideoId={}", mVideoId);
        mTorrentFilePosition = mIntent.getIntExtra(PlayerActivity.KEY_TORRENT, -1);

        // when mVideoInfo uri is the same as intent uri -> info has already been retrieved !
        if(mVideoInfo!=null&&mVideoInfo.uri.equals(mUri)){
            // Reusing metadata must not turn an explicit external position=0 (or malformed
            // position command) into a database resume for a new playback of the same URI.
            if (PlaybackResumePolicy.shouldPromoteSameUriToLastPosition(
                    mResume, RESUME_FROM_REMOTE_POS, freshExternalPositionCommand)) {
                mResume = RESUME_FROM_LAST_POS;
            }
            if (log.isDebugEnabled()) log.debug("PlayerService.onStart: URI matches existing mVideoInfo, mResume={}", mResume);
            mDatabaseInfoHasBeenRetrieved = true;
            // Always reload audio settings from preferences even when replaying same video
            mAudioDelay = mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0);
            mAudioSpeed = getAudioSpeedFromPreferences();
            if (log.isDebugEnabled()) log.debug("onStart: mAudioSpeed={}", mAudioSpeed);
        }
        else {
            mVideoInfo = null; //reset info
            mDatabaseInfoHasBeenRetrieved = false;
            mAudioDelay = mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0);
            mAudioSpeed = getAudioSpeedFromPreferences();
            if (log.isDebugEnabled()) log.debug("onStart: mAudioSpeed={}", mAudioSpeed);
        }
        if(mTorrentFilePosition>=0){
            mCallOnDataUriOKWhenVideoInfoIsSet = false;
            mPlayer.setIsTorrent(true);
            mPlayer.stayAwake(true);
            if(mTorrent==null){
                Intent connectionIntent = new Intent(this, TorrentObserverService.class);
                bindService(connectionIntent, mTorrentObserverServiceConnection,
                        Context.BIND_AUTO_CREATE);
            }
            else {
                mTorrent.setObserver(mTorrentThreadObserver);
                mTorrent.start();
            }
        }
        else if(!"content".equals(mUri.getScheme()))
            onDataUriOK();
        else if(mVideoId==-1){
            Uri correctedUri = FileUtils.getRealUriFromVideoURI(this,mUri);
            if(correctedUri!=null) {
                if (log.isDebugEnabled()) log.debug("onStart: correctedUri {}", correctedUri);
                mUri = correctedUri;
            }
        }
        if (log.isDebugEnabled()) log.debug("onStart: mIndexHelper != null {}", String.valueOf(mIndexHelper != null));

        // store file that is playing: this is too soon at this point because information is not available do it later in onStreamingUriOK
        if (log.isDebugEnabled()) log.debug("onStart videoUri {}, videoId {}, mVideoInfo.id={}, mVideoInfo.uri={}", mUri, mVideoId, (mVideoInfo != null ? mVideoInfo.id : "null"), (mVideoInfo != null ? mVideoInfo.uri : "null"));
        //CustomApplication.setLastVideoPlayedId(mVideoId);
        //CustomApplication.setLastVideoPlayedUri(mUri);

        if(mIndexHelper!=null&&mVideoInfo==null) {
            if (log.isDebugEnabled()) log.debug("onStart: mIndexHelper != null, call requestVideoDb()");
            requestVideoDb();
        } else if(mVideoInfo!=null){
            if (log.isDebugEnabled()) log.debug("onStart: mVideoInfo != null, call mPlayerFrontend.onVideoDb");
            mPlayerFrontend.onVideoDb(mVideoInfo, null);
        }
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            setNowPlayingCard();
        }
        mPlayerState = PlayerState.PREPARING;
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
    }

    public Uri getStreamingUri() {
        return mStreamingUri;
    }

    /* public void stopStatusbarNotification(){
        nm.cancel(PLAYER_NOTIFICATION_ID);
        stopForeground(true);
    }*/ 

    /* public void startStatusbarNotification(boolean isDicreteOrMinimized) {

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    NotificationManager.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(R.drawable.nova_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);

        Intent playIntent = new Intent(PLAY_INTENT);
        PendingIntent playPendingIntent = PendingIntent.getBroadcast(this, 1, playIntent,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        Intent pauseIntent = new Intent(PAUSE_INTENT);
        PendingIntent pausePendingIntent = PendingIntent.getBroadcast(this, 2, pauseIntent,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        Intent fullscreenIntent = new Intent(FULLSCREEN_INTENT);
        PendingIntent fullscreenPendingIntent = PendingIntent.getBroadcast(this, 3, fullscreenIntent,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        Intent notificationIntent = new Intent("DISPLAY_FLOATING_PLAYER");
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent exitIntent = PendingIntent.getBroadcast(this, 4, new Intent(EXIT_INTENT),
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        nb.setContentIntent(contentIntent);
        String title = "";
        if(getVideoInfo()!=null&&PlayerService.sPlayerService.getVideoInfo().scraperTitle!=null&&!PlayerService.sPlayerService.getVideoInfo().scraperTitle.isEmpty())
            title = sPlayerService.getVideoInfo().scraperTitle;
        else if(getVideoInfo()!=null&&getVideoInfo().title!=null)
            title = getVideoInfo().title;
        else
            title = FileUtils.getFileNameWithoutExtension(mUri);
        nb.setContentTitle(getString(R.string.now_playing));
        nb.setContentText(title);
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) {
                nb.addAction(new NotificationCompat.Action(R.drawable.video_pause, getString(R.string.floating_player_pause), pausePendingIntent));
            } else if (mPlayer.isInPlaybackState()) {
                nb.addAction(new NotificationCompat.Action(R.drawable.video_play, getString(R.string.floating_player_play), playPendingIntent));
            }
        }

        if (isDicreteOrMinimized) {
            nb.addAction(new NotificationCompat.Action(R.drawable.ic_menu_unfade, getString(R.string.floating_player_restore), contentIntent));
        } else {
            nb.addAction(new NotificationCompat.Action(R.drawable.video_format_fullscreen, getString(R.string.format_fullscreen), fullscreenPendingIntent));
        }

        nb.setDeleteIntent(exitIntent);

        //notif.bigContentView = new RemoteViews(getPackageName(), R.layout.notification_controls);
        ServiceCompat.startForeground(this, PLAYER_NOTIFICATION_ID, nb.build(),
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0
        );
    } */ 


    private void updateResumeCandidates(VideoDbInfo localInfo, VideoDbInfo networkInfo) {
        if (localInfo != null) {
            boolean eligible = PlaybackResumePolicy.isDatabaseResumeEligible(
                    localInfo.lastTimePlayed, mResume, RESUME_FROM_REMOTE_POS);
            mPlaybackSession.setCandidate(ResumeSource.LOCAL, localInfo.resume, eligible);
            mPlaybackSession.setCandidate(ResumeSource.BOOKMARK, localInfo.bookmark, eligible);
            int traktPosition = localInfo.duration > 0
                    ? Math.abs((int) (localInfo.traktResume * (double) localInfo.duration / 100))
                    : 0;
            mPlaybackSession.setCandidate(ResumeSource.TRAKT, traktPosition);
        }
        if (networkInfo != null) {
            boolean eligible = PlaybackResumePolicy.isDatabaseResumeEligible(
                    networkInfo.lastTimePlayed, mResume, RESUME_FROM_REMOTE_POS);
            mPlaybackSession.setCandidate(ResumeSource.NETWORK, networkInfo.resume, eligible);
        }
    }

    public int getResumeCandidate(ResumeSource source) {
        return mPlaybackSession.getCandidate(source);
    }

    private void selectInitialResumePosition(VideoDbInfo videoInfo, ResumeSource requestedSource) {
        ResumeSource source = requestedSource;
        int position = mPlaybackSession.getCandidate(source);

        if (mPlaybackSession.getCandidate(ResumeSource.EXPLICIT) > 0) {
            source = ResumeSource.EXPLICIT;
            position = mPlaybackSession.getCandidate(source);
        } else if (source == ResumeSource.NONE) {
            if (mResume == RESUME_FROM_BOOKMARK) source = ResumeSource.BOOKMARK;
            else if (mResume == RESUME_FROM_REMOTE_POS) source = ResumeSource.NETWORK;
            else if (mResume == RESUME_FROM_LAST_POS || mResume == RESUME_FROM_LOCAL_POS) source = ResumeSource.LOCAL;
            position = mPlaybackSession.getCandidate(source);
        }

        boolean videoInfoEligible = videoInfo != null
                && PlaybackResumePolicy.isDatabaseResumeEligible(
                        videoInfo.lastTimePlayed, mResume, RESUME_FROM_REMOTE_POS);
        if (position <= 0 && videoInfoEligible && mResume != RESUME_NO) {
            position = mResume == RESUME_FROM_BOOKMARK ? videoInfo.bookmark : videoInfo.resume;
        }
        mPlaybackSession.select(source, Math.max(position, 0));
        if (log.isDebugEnabled()) log.debug("selectInitialResumePosition: source={} position={}", source, position);
    }

    private void onDataUriOK() {
        if (log.isDebugEnabled()) log.debug("onDataUriOK {}", mUri);
        mCallOnDataUriOKWhenVideoInfoIsSet = false;
        //we check if we have a streaming uri, if we don't, streaming Uri must be equal to mUri
        if (mStreamingUri == null) {
            mStreamingUri = mUri;
        }
        //streamingUri shouldn't start by upnp otherwise player won't be able to play it
        if ("upnp".equals(mStreamingUri.getScheme())) {
            StreamUriFinder streamUriFinder = new StreamUriFinder(mStreamingUri, this);
            streamUriFinder.setListener(new StreamUriFinder.Listener() {
                @Override
                public void onUriFound(Uri uri) {
                    mStreamingUri = uri;
                    onStreamingUriOK();
                }

                @Override
                public void onError() {
                    //TODO error
                    //mHandler.sendEmptyMessage(MSG_ERROR_UPNP);
                }
            });
            streamUriFinder.start();
        }
        else
            onStreamingUriOK();
    }

    private void prepareSubs() {
        if (log.isDebugEnabled()) log.debug("prepareSubs: checking cache for mUri={}", mUri);
        if(!mIsPreparingSubs) {
            mIsPreparingSubs = true;
            // Initialize latch to synchronize subtitle enumeration with video playback
            // This ensures video doesn't start until subtitles are enumerated
            mSubtitlesReadyLatch = new CountDownLatch(1);

            // Only use cache for local files. Remote files (SMB, FTP, etc.) need to be copied locally
            // by privatePrefetchSub() before AVOS can access them. Skipping this causes subtitles to disappear.
            // See: https://github.com/nova-video-player/aos-AVP/issues/1605
            boolean isLocalFile = FileUtils.isLocal(mUri);
            List<SubtitleManager.SubtitleFile> cachedFiles = null;
            if (isLocalFile) {
                // Check if raw subtitle files are cached (within 10 second TTL)
                // If cached, we can skip the expensive enumeration
                cachedFiles = SubtitleManager.getCachedSubtitleFiles(mUri);
            } else {
                if (log.isDebugEnabled()) log.debug("prepareSubs: skipping cache for remote file (requires local copy): {}", mUri);
            }

            if (cachedFiles != null) {
                if (log.isDebugEnabled()) log.debug("prepareSubs: cache HIT - found {} cached subtitle files, skipping enumeration", cachedFiles.size());
                // Check if AVOS metadata is also cached
                VideoMetadata cachedMetadata =
                        SubtitleManager.getCachedProcessedMetadata(mUri);
                if (cachedMetadata != null) {
                    if (log.isDebugEnabled()) log.debug("prepareSubs: cache HIT for both files AND metadata - using cached AVOS metadata, skipping checkSubtitles entirely");
                    // Both file list and AVOS result cached - skip all processing
                    onSubtitleMetadataUpdated(cachedMetadata, cachedMetadata.getSubtitleTrack(0) != null ? 0 : -1);
                    mIsPreparingSubs = false;
                    if (mSubtitlesReadyLatch != null) {
                        mSubtitlesReadyLatch.countDown();
                    }
                    return;
                } else {
                    if (log.isDebugEnabled()) log.debug("prepareSubs: cache HIT for files, but not metadata - will call checkSubtitles");
                    // Files cached but not AVOS result - call checkSubtitles to process them
                    mIsPreparingSubs = false;
                    mPlayer.checkSubtitles();
                    if (mSubtitlesReadyLatch != null) {
                        mSubtitlesReadyLatch.countDown();
                    }
                    return;
                }
            }

            if (log.isDebugEnabled()) log.debug("prepareSubs: cache MISS - proceeding with normal enumeration");
            SubtitleManager subtitleManager =
                    new SubtitleManager(this, new SubtitleManager.Listener() {
                        @Override
                        public void onAbort() {
                            mIsPreparingSubs = false;
                            if (log.isDebugEnabled()) log.debug("prepareSubs: onAbort - signaling subtitles ready latch");
                            if (mSubtitlesReadyLatch != null) {
                                mSubtitlesReadyLatch.countDown();
                            }
                        }

                        @Override
                        public void onError(Uri uri, Exception e) {
                            mIsPreparingSubs = false;
                            if (log.isDebugEnabled()) log.debug("prepareSubs: onError - signaling subtitles ready latch");
                            if (mSubtitlesReadyLatch != null) {
                                mSubtitlesReadyLatch.countDown();
                            }
                        }

                        @Override
                        public void onSuccess(Uri uri) {
                            if (log.isDebugEnabled()) log.debug("prepareSubs: onSuccess request player to check subs if {} = {}", mUri, uri);
                            mIsPreparingSubs = false;
                            if(mUri.equals(uri)) {
                                // Check if AVOS metadata is cached (within 10 second TTL)
                                VideoMetadata cachedMetadata =
                                        SubtitleManager.getCachedProcessedMetadata(mUri);
                                if (cachedMetadata != null) {
                                    if (log.isDebugEnabled()) log.debug("prepareSubs: onSuccess - using cached AVOS metadata, skipping checkSubtitles");
                                    // Use cached metadata directly instead of querying AVOS again
                                    onSubtitleMetadataUpdated(cachedMetadata, cachedMetadata.getSubtitleTrack(0) != null ? 0 : -1);
                                } else {
                                    if (log.isDebugEnabled()) log.debug("prepareSubs: onSuccess - no cached metadata, calling checkSubtitles");
                                    mPlayer.checkSubtitles(); // will trigger subs reload
                                }
                            }
                            // Signal that subtitles are ready after checkSubtitles completes
                            if (log.isDebugEnabled()) log.debug("prepareSubs: onSuccess - signaling subtitles ready latch");
                            if (mSubtitlesReadyLatch != null) {
                                mSubtitlesReadyLatch.countDown();
                            }
                        }

                        @Override
                        public void onNoSubtitlesFound(Uri uri) {
                            mIsPreparingSubs = false;
                            if (log.isDebugEnabled()) log.debug("prepareSubs: onNoSubtitlesFound - signaling subtitles ready latch");
                            if (mSubtitlesReadyLatch != null) {
                                mSubtitlesReadyLatch.countDown();
                            }
                        }
                    });
            subtitleManager.preFetchHTTPSubtitlesAndPrepareUpnpSubs(mUri, mStreamingUri);
        } else {
            if (log.isDebugEnabled()) log.debug("prepareSubs: already preparing subs");
        }
    }

    private void onStreamingUriOK() {
        if (log.isDebugEnabled()) log.debug("onStreamingUriOK");
        if(mTorrentFilePosition==-1)
            prepareSubs();
        if(mPlayerFrontend!=null)
            mPlayerFrontend.setUri(mUri, mStreamingUri);
        new Thread(() -> {
            // Wait for subtitle enumeration to complete before starting video playback
            // This prevents glitches caused by subtitle track selection during playback
            if (mSubtitlesReadyLatch != null) {
                try {
                    if (log.isDebugEnabled()) log.debug("onStreamingUriOK: waiting for subtitles enumeration (timeout={}ms)", SUBTITLE_ENUMERATION_TIMEOUT_MS);
                    boolean finished = mSubtitlesReadyLatch.await(SUBTITLE_ENUMERATION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (finished) {
                        if (log.isDebugEnabled()) log.debug("onStreamingUriOK: subtitles enumeration completed successfully");
                    } else {
                        log.warn("onStreamingUriOK: subtitles enumeration timeout after {}ms, proceeding with video start", SUBTITLE_ENUMERATION_TIMEOUT_MS);
                    }
                } catch (InterruptedException e) {
                    log.error("onStreamingUriOK: interrupted while waiting for subtitles", e);
                    Thread.currentThread().interrupt();
                }
            }
            mPlayer.setVideoURI(mStreamingUri, null);
        }).start();
    }

    public void setPlayer(){
        if(mPlayer!=null)
            mPlayer.setListener(null);
        else if (log.isDebugEnabled()) log.debug("setPlayer: mPlayer is null");
        if(Player.sPlayer==null) {
            if (log.isDebugEnabled()) log.debug("setPlayer: Player.sPlayer is null allocating a new one");
            Player.sPlayer = new Player(this, null, null, false);
        } else {
            if (log.isDebugEnabled()) log.debug("setPlayer: Player.sPlayer is not null");
        }
        mPlayer = Player.sPlayer;
        mPlayer.setListener(this);
    }

    /*
        Player
     */

    public interface PlayerFrontend extends Player.Listener{
        void onAudioError(boolean isNotSupported, String msg);

        void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo);

        void setUri(Uri mUri, Uri streamingUri);

        void setVideoInfo(VideoDbInfo mVideoInfo);

        void onEnd();

        void onTorrentUpdate(String daemonString);

        void onTorrentNotEnoughSpace();

        void onFrontendDetached();

        void onFirstPlay();
        // mHandler.sendMessage(mHandler.obtainMessage(MSG_TORRENT_STARTED));

        // Called on the main thread once intro/outro segments have been fetched, so the
        // UI (e.g. the Play mode tile summary) can refresh if it is already displayed.
        void onIntroDbReady();
    }

    public void removePlayerFrontend(PlayerFrontend playerFrontend, boolean prepareForSurfaceSwitch) {
        if(mPlayerFrontend!=playerFrontend)
            return;
        if (log.isDebugEnabled()) log.debug("removePlayerFrontend {}", String.valueOf(prepareForSurfaceSwitch));
        mIsChangingSurface = prepareForSurfaceSwitch;
        mPlayerFrontend = null;
        stopAndSaveVideoState();
        Player.sPlayer.setListener(null);
        Player.sPlayer = null;
        playerFrontend.onFrontendDetached();
        if (!prepareForSurfaceSwitch) {
            mVideoInfo = null;
            stopSelf();
        }
    }

    private int captureCurrentPosition(boolean rewindForResume) {
        int position = mPlaybackSession.lastKnownPositionMs;
        boolean capturedFromPlayer = mPlayer != null && mPlayer.isInPlaybackState();
        if (capturedFromPlayer) {
            position = mPlayer.getDuration() != 0
                    ? mPlayer.getCurrentPosition()
                    : mPlayer.getRelativePosition();
        }
        if (position < 0) position = 0;
        if (capturedFromPlayer && rewindForResume && position > 3000) position -= 1000;
        mPlaybackSession.lastKnownPositionMs = position;
        mPlaybackSession.setCandidate(ResumeSource.LIVE, position);
        return position;
    }

    public PlaybackSnapshot getPlaybackSnapshot() {
        int position = captureCurrentPosition(false);
        int duration = mVideoInfo != null ? mVideoInfo.duration : -1;
        if (mPlayer != null && mPlayer.isInPlaybackState() && mPlayer.getDuration() > 0) {
            duration = mPlayer.getDuration();
        }
        boolean paused = mPlayer != null && mPlayer.isPaused();
        return new PlaybackSnapshot(position, duration, mPlaybackSession.completed,
                paused, mPlaybackSession.selectedSource);
    }

    /** Store a service-owned checkpoint in a frontend intent before the service may be recreated. */
    public void checkpointPlaybackIntent(Intent intent) {
        if (intent == null || mPlaybackSession.uri == null || !mPlaybackSession.startPositionApplied
                || mPlaybackSession.completed
                || mPlayerState == PlayerState.INIT || mPlayerState == PlayerState.PREPARING) {
            return;
        }
        intent.putExtra(SESSION_POSITION, captureCurrentPosition(false));
        if (mPlaybackSession.launchGeneration != null) {
            intent.putExtra(LAUNCH_GENERATION, mPlaybackSession.launchGeneration);
        }
    }

    private static void clearPositionExtras(Intent intent) {
        intent.removeExtra(SESSION_POSITION);
        intent.removeExtra(ExternalResumeIntent.FLOATING_POSITION);
        intent.removeExtra(ExternalResumeIntent.START_FROM);
        intent.removeExtra(ExternalResumeIntent.POSITION);
        intent.removeExtra(ExternalResumeIntent.RESUME_POSITION);
    }

    public int prepareRetryFromCurrentPosition() {
        int position = captureCurrentPosition(true);
        mPlaybackSession.select(ResumeSource.LIVE, position);
        if (mVideoInfo != null) {
            mVideoInfo.resume = position;
            if (mPlayer != null && mPlayer.getDuration() > 0) mVideoInfo.duration = mPlayer.getDuration();
        }
        return position;
    }

    public void persistVideoInfoFromFrontend(VideoDbInfo videoInfo) {
        if (videoInfo == null || mIndexHelper == null) return;
        if (Objects.equals(mPlaybackSession.uri, videoInfo.uri)) {
            int position = mPlaybackSession.completed
                    ? LAST_POSITION_END
                    : captureCurrentPosition(mPlayer != null && !mPlayer.isPaused());
            videoInfo.resume = position;
        }
        mIndexHelper.writeVideoInfo(videoInfo, mNetworkBookmarksEnabled);
    }

    public void saveVideoStateIfReady(){
        if(mIndexHelper!=null) {
            if ((mPlayerState != PlayerState.INIT && mPlayerState != PlayerState.PREPARING)) {// if it has really been played at least once, otherwise it would overwrite lastresume with 0
                if (log.isDebugEnabled()) log.debug("saveVideoStateIfReady");
                int resumePosition = mPlaybackSession.completed
                        ? LAST_POSITION_END
                        : captureCurrentPosition(mPlayer != null && !mPlayer.isPaused());
                if (log.isDebugEnabled()) log.debug("saveVideoStateIfReady: source={} position={} completed={}",
                        mPlaybackSession.selectedSource, resumePosition, mPlaybackSession.completed);
                if (mVideoInfo != null && !PrivateMode.isActive()) {
                    mVideoInfo.resume = resumePosition;
                    int duration = mPlayer.getDuration();
                    if (duration > 0)
                        mVideoInfo.duration = duration;
                    long utcSeconds = System.currentTimeMillis() / 1000L;
                    // Save UTC seconds only if the video has been scraped (has media information)
                    if (mVideoInfo.scraperTitle != null && !mVideoInfo.scraperTitle.isEmpty()) {
                        PreferenceManager.getDefaultSharedPreferences(this)
                                .edit()
                                .putLong(PREFERENCE_LAST_TIME_VIDEO_PLAYED_UTC, utcSeconds)
                                .apply();
                        if (log.isDebugEnabled()) log.debug("saveVideoStateIfReady: saved last video played UTC timestamp {}", utcSeconds);
                    }
                    // saving seconds since the Unix epoch (January 1, 1970, 00:00:00 UTC) and this value is in UTC
                    // traktResume is set to -resume unless synced
                    mVideoInfo.lastTimePlayed = utcSeconds;
                    log.info("saveVideoStateIfReady: save bookmark at {} for videoId {}", mVideoInfo.lastTimePlayed, mVideoInfo.id);
                    mIndexHelper.writeVideoInfo(mVideoInfo, mNetworkBookmarksEnabled);
                    // disable periodic trakt save this should be done with pauseTrakt() anyway
                    //stopTrakt(); //this writes mVideoInfo.traktResume
                    // BootupRecommendationService is for before Android O otherwise TV channels are used
                    if (ArchosFeatures.isAndroidTV(this))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            Intent intent = new Intent(BootupRecommandationService.UPDATE_ACTION);
                            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                            sendBroadcast(intent);
                        } else
                            ChannelManager.refreshChannels(this);
                }
            }
        }
    }

    public void stopAndSaveVideoState(){
        if (log.isDebugEnabled()) log.debug("stopAndSaveVideoState");
        if(mIndexHelper!=null) {
            mIndexHelper.abort(); //too late : do not retrieve db info
            // stopTrakt() must run before saveVideoStateIfReady() so that it sets
            // mVideoInfo.traktResume = -progress synchronously before the async DB write captures it
            if ((mPlayerState != PlayerState.INIT && mPlayerState != PlayerState.PREPARING)) {
                if (log.isDebugEnabled()) log.debug("stopAndSaveVideoState: stopTrakt");
                stopTrakt();
            }
            saveVideoStateIfReady();
            if (mUpdateNextTask != null) {
                mUpdateNextTask.cancel(false);
                mUpdateNextTask.setListener(null);
                mUpdateNextTask = null;
            }
            if (PERIODIC_BOOKMARK_SAVE)
                mHandler.removeCallbacks(mAutoSaveTask);
            if (mAutoSkipTask != null)
                mHandler.removeCallbacks(mAutoSkipTask);
            mPlayer.pause(PlayerController.STATE_OTHER);
            mPlayer.stopPlayback();
            mPlayerState = PlayerState.STOPPED;
            TorrentObserverService.staticExitProcess();
            TorrentObserverService.killProcess();
            if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
                stopNowPlayingCard();
            }
        }
    }
    public Intent getLastIntent(){
        return mIntent;

    }

    /**
     * be aware that if playerFrontend != mPlayerFrontend, Player.sPlayer will be set to null
     * @param playerFrontend
     */
    public void switchPlayerFrontend(PlayerFrontend playerFrontend) {
        if(mPlayerFrontend!=null&&mPlayerFrontend!=playerFrontend)
            removePlayerFrontend(mPlayerFrontend, true);
        mPlayerFrontend= playerFrontend;

        if(mUri!=null)
            playerFrontend.setUri(mUri,mStreamingUri);
        if (log.isDebugEnabled()) log.debug("switchPlayerFrontend {}", String.valueOf(mVideoInfo != null));

        if(mVideoInfo!=null)
            playerFrontend.setVideoInfo(mVideoInfo);

        mIsChangingSurface = false;
    }

    /**
     *
     * @return player progress in percentage
     */
    private int getPlayerProgress() {
        if (log.isDebugEnabled()) log.debug("getPlayerProgress");
        if (Player.sPlayer == null || mVideoInfo == null)
            return 0;
        if (mPlaybackSession.completed)
            return 100;
        int progress = 0;
        int position = Player.sPlayer.getCurrentPosition();
        int duration = mVideoInfo.duration;
        if (duration <= 0) {
            duration = Player.sPlayer.getDuration();
        }
        if (position >= 0 && duration >= 0)
            progress = (int) (position / (double) duration * 100);

        return progress;
    }


    /*
        Trakt
     */

    // TODO MARC not sure we need to do this: only on pause
    private final Runnable mTraktWatchingRunnable = new Runnable() {
        @Override
        public void run() {
            if (log.isDebugEnabled()) log.debug("mTraktWatchingRunnable");
            if (Player.sPlayer != null) {
                mTraktClient.watching(mVideoInfo, getPlayerProgress());
                mHandler.postDelayed(mTraktWatchingRunnable, Trakt.WATCHING_DELAY_MS);
            }
        }
    };

    /**
     * Returns the best available video duration in milliseconds: prefers mVideoInfo.duration (set at
     * startTrakt()), falls back to the live player value so threshold calculations are accurate even
     * when mVideoInfo has a stale or zero duration.
     */
    private long getEffectiveDurationMs() {
        if (mVideoInfo != null && mVideoInfo.duration > 0) return mVideoInfo.duration;
        if (Player.sPlayer != null) return Player.sPlayer.getDuration();
        return 0;
    }

    private void startTrakt() {
        if (log.isDebugEnabled()) log.debug("startTrakt");
        if (mTraktClient != null) {
            mTraktError = false;
            mTraktLiveScrobblingEnabled = Trakt.isLiveScrobblingEnabled(mPreferences);
            if (mTraktLiveScrobblingEnabled) {
                if (mVideoInfo == null) {
                    log.error("startTrakt: mVideoInfo is null, cannot start Trakt!");
                    return;
                }
                int progress = getPlayerProgress();
                // Do not set traktResume here: the pending DB marker is set by pauseTrakt()/stopTrakt()
                // after threshold validation. Setting it at start (typically 0%) would leave a stale
                // negative value if the video is closed below threshold.
                mVideoInfo.duration = Player.sPlayer.getDuration();
                if (log.isDebugEnabled()) log.debug("startTrakt: trakt watching progress={}", progress);
                mTraktClient.watching(mVideoInfo, progress);
                mHandler.postDelayed(mTraktWatchingRunnable, Trakt.WATCHING_DELAY_MS);
                mTraktWatching = true;
            }
        }
    }

    private void stopTrakt() {
        if (log.isDebugEnabled()) log.debug("stopTrakt");
        if (mTraktClient != null) {
            if (log.isDebugEnabled()) log.debug("stopTrakt: mTraktClient != null, mTraktWatching={}", mTraktWatching);
            if (mTraktWatching) {
                mHandler.removeCallbacks(mTraktWatchingRunnable);
                int progress = getPlayerProgress();
                if (progress >= 0){
                    // Only record traktResume if progress meets the minimum threshold: max(30s, 1%).
                    // Still call watchingStop so Trakt closes the session cleanly.
                    final long effectiveDuration = getEffectiveDurationMs();
                    final float minPercent = effectiveDuration > 0
                            ? Math.max(Trakt.RESUME_THRESHOLD_PERCENT, Trakt.RESUME_THRESHOLD_MS * 100.0f / effectiveDuration)
                            : Trakt.RESUME_THRESHOLD_PERCENT;
                    if (progress >= minPercent) {
                        mVideoInfo.traktResume = -progress; // traktResume is set to -resume unless synced with trakt
                    } else {
                        // Below threshold: clear any stale pending marker so saveVideoStateIfReady()
                        // does not persist a negative (pending-sync) value from an earlier state.
                        if (mVideoInfo.traktResume < 0) mVideoInfo.traktResume = 0;
                        if (log.isDebugEnabled()) log.debug("stopTrakt: progress {}% below threshold {}%, clearing pending traktResume", progress, minPercent);
                    }
                    if (log.isDebugEnabled()) log.debug("stopTrakt: watchingStop progress={}", progress);
                    mTraktClient.watchingStop(mVideoInfo, progress);
                }
                if (log.isDebugEnabled()) log.debug("stopTrakt: progress negative not doing anything, progress={}", progress);
                mTraktWatching = false;
            } else if (!mTraktError && Trakt.shouldMarkAsSeen(getPlayerProgress())) {
                if (log.isDebugEnabled()) log.debug("stopTrakt: Trakt.ACTION_SEEN");
                mTraktClient.markAs(mVideoInfo, Trakt.ACTION_SEEN);
            } else {
                if (log.isDebugEnabled()) log.debug("stopTrakt: mTraktWatching=false and should not mark as seend, doing nothing!!!");
            }
        }
        // We now use the DB flag ARCHOS_TRAKT_SEEN even if there is no sync with trakt
        else {
            if (log.isDebugEnabled()) log.debug("stopTrakt: mTraktClient == null, not sending watchStop");
            if (mVideoInfo != null) {
                if (mVideoInfo.id >= 0 && Trakt.shouldMarkAsSeen(getPlayerProgress()) && !PrivateMode.isActive()) {
                    if (log.isDebugEnabled()) log.debug("stopTrakt: marking video {} as seen in VideoStore", mVideoInfo.id);
                    final ContentValues cv = new ContentValues(1);
                    cv.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_MARKED);
                    String where = VideoStore.Video.VideoColumns._ID + " = ?";
                    String[] whereArgs = new String[]{Long.toString(mVideoInfo.id)};
                    getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, where, whereArgs);
                } else {
                    if (log.isDebugEnabled()) log.debug("stopTrakt: not marking video mVideoInfo.id={} any resume", mVideoInfo.id);
                }
            } else {
                log.warn("stopTrakt: mVideoInfo is null");
            }
        }
    }

    private void pauseTrakt() {
        if (log.isDebugEnabled()) log.debug("pauseTrakt");
        if (mTraktClient != null) {
            if (mTraktWatching) {
                int progress = getPlayerProgress();
                if (progress > 0) {
                    // Only record traktResume if progress meets the minimum threshold: max(30s, 1%).
                    // Still call watchingPause so Trakt closes the session cleanly.
                    final long effectiveDuration = getEffectiveDurationMs();
                    final float minPercent = effectiveDuration > 0
                            ? Math.max(Trakt.RESUME_THRESHOLD_PERCENT, Trakt.RESUME_THRESHOLD_MS * 100.0f / effectiveDuration)
                            : Trakt.RESUME_THRESHOLD_PERCENT;
                    if (progress >= minPercent) {
                        mVideoInfo.traktResume = -progress; // traktResume is set to -resume unless synced with trakt
                    } else {
                        // Below threshold: clear any stale pending marker so saveVideoStateIfReady()
                        // does not persist a negative (pending-sync) value from an earlier state.
                        if (mVideoInfo.traktResume < 0) mVideoInfo.traktResume = 0;
                        if (log.isDebugEnabled()) log.debug("pauseTrakt: progress {}% below threshold {}%, clearing pending traktResume", progress, minPercent);
                    }
                    if (log.isDebugEnabled()) log.debug("pauseTrakt: watchingPause progress={}", progress);
                    mTraktClient.watchingPause(mVideoInfo, progress);
                }
                // consider that in pause we are still watching it is only on stop that it is the end
                //mTraktWatching = false;
            }
        }
    }

    private final TraktService.Client.Listener mTraktListener = new TraktService.Client.Listener() {
        @SuppressWarnings("deprecation") // getSerializable: API 33+ branch uses typed form; else branch cast suppressed
        @Override
        public void onResult(Bundle bundle) {
            Trakt.Status status = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? bundle.getSerializable("status", Trakt.Status.class)
                    : (Trakt.Status) bundle.getSerializable("status");
            if (status == Trakt.Status.ERROR) {
                mTraktWatching = false;
                mTraktError = true;
                mHandler.removeCallbacks(mTraktWatchingRunnable);
            }
        }
    };

    /*
        Indexing
     */
    private VideoObserver mVideoObserver;
    private boolean mHasRequestedIndexing;
    private VideoDbInfo mVideoInfo;

    /*
        IntroDB skip intro/outro (GET only, proof of concept)
     */
    // auto-skip toggle, shared with the in-player play-mode tile/menu (TV + phone/tablet).
    // Recap has no separate toggle: it is skipped automatically when this toggle is on AND we
    // are genuinely binge-watching (PLAYMODE_BINGE on an auto-advanced episode), see autoSkipIfNeeded.
    public static final String KEY_INTRODB_ENABLED = "introdb_enabled";
    public static final boolean DEFAULT_INTRODB_ENABLED = false;
    // how often the auto-skip task checks the playback position against the fetched segments
    private static final long AUTO_SKIP_INTERVAL = 1000;
    // Safety buffer (ms) subtracted from segment end so skipping lands slightly before intro ends
    private static final int AUTO_SKIP_BUFFER_MS = 2000;
    // If a skip target lands within this margin of the media end, treat it as end-of-video
    // (seeking right to EOF fails and breaks playback) and complete naturally instead.
    private static final int AUTO_SKIP_END_MARGIN_MS = 5000;
    // normalized segments fetched once per playback (fused from all providers by IntroDbManager),
    // read from the player tick for auto-skip
    private volatile IntroSegments mIntroSegments;
    // uri the cached result (or in-flight fetch) belongs to, guards against duplicate/stale fetches
    private volatile Uri mIntroDbFetchedUri;
    // periodic task that auto-skips intro/recap segments during playback
    private Runnable mAutoSkipTask;
    // end (ms) of the last segment we auto-skipped, so we don't fight a user who seeks back into it
    private long mLastAutoSkippedEndMs = -1;
    // true when the current episode was reached by auto-advancing from the previous one (binge),
    // not by the user manually starting it. Recap auto-skip only fires when this is set.
    private boolean mArrivedViaBingeTransition = false;
    private BaseTags mScraperTag;
    private IndexHelper mIndexHelper;
    private TraktService.Client mTraktClient = null;
    private boolean mTraktLiveScrobblingEnabled = false;
    private boolean mTraktWatching = false;
    private boolean mTraktError = false;

    /**
     * if new uri, retrieve its database info
     * @param indexHelper
     */
    public void setIndexHelper(IndexHelper indexHelper){
        mIndexHelper = indexHelper;
        if(!mDatabaseInfoHasBeenRetrieved&&mUri!=null) {
            requestVideoDb();
        }
    }

    private void requestVideoDb() {
        if (log.isDebugEnabled()) log.debug("requestVideoDb");
        mDatabaseInfoHasBeenRetrieved= true;
        mIndexHelper.requestVideoDb(mUri, mVideoId,
                null,
                this, false, true);
    }

    private class VideoObserver extends ContentObserver implements IndexHelper.ScraperTask.Listener {

        public VideoObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mVideoInfo != null && (mVideoInfo.id==-1||mVideoInfo.scraperId <=0)&&!PrivateMode.isActive()){ // if we need to update
                if (log.isDebugEnabled()) log.debug("VideoObserver onChange: update from db");
                VideoDbInfo info = VideoDbInfo.fromUri(getContentResolver(), mVideoInfo.uri);
                if (info != null) {
                    info.subtitleTrack = mVideoInfo.subtitleTrack;
                    info.audioTrack = mVideoInfo.audioTrack;
                    mVideoInfo = info;
                    if (mPlayerFrontend != null)
                        mPlayerFrontend.setVideoInfo(mVideoInfo);

                    if(mVideoInfo.lastTimePlayed<=0&&mVideoInfo.id!=-1) {
                        mVideoInfo.lastTimePlayed = Long.valueOf(System.currentTimeMillis() / 1000L);
                        mIndexHelper.writeVideoInfo(mVideoInfo, true);
                    }

                    if (ArchosFeatures.isAndroidTV(PlayerService.this) && !PrivateMode.isActive())
                        updateNowPlayingMetadata();
                    // check if it has been scraped
                }
            } else {
                if (log.isDebugEnabled()) log.debug("VideoObserver onChange: no need to update");
            }
        }

        @Override
        public void onScraperTaskResult(ScrapeDetailResult result) {
            /*if (mVideoInfo != null && result.tag != null) {
                mVideoInfo.updateFromScraper(result);
                mVideoInfo.title = result.tag.getTitle();
                if(result.tag.getCover()!=null)
                    mVideoInfo.scraperCover = result.tag.getCover().getPath();
                if (mVideoInfo.id != -1) {
                    result.tag.save(PlayerService.this, mVideoInfo.id);
                }
                else
                    mScraperTag = result.tag; // save it, it will be retrieved when index has worked
                if (ArchosFeatures.isAndroidTV(PlayerService.this) && !PrivateMode.isActive())
                updateNowPlayingMetadata();
            }*/
        }
    }

    /**
     * Record last played + starts playing video
     * we should go to this method only on first start of service, NOT when switching player frontend
     * otherwise when playing in private mode, resume will be set to DB resume instead of the real last position
     * @param videoInfo
     */
    public void setVideoInfo(VideoDbInfo videoInfo){
        setVideoInfo(videoInfo, ResumeSource.NONE);
    }

    public void setVideoInfo(VideoDbInfo videoInfo, ResumeSource resumeSource){
        if (log.isDebugEnabled()) log.debug("setVideoInfo: videoInfo.id={}, videoInfo.uri={}", (videoInfo != null ? videoInfo.id : "null"), (videoInfo != null ? videoInfo.uri : "null"));
        mVideoInfo = videoInfo;
        if (mVideoInfo != null) {
            if (log.isDebugEnabled()) log.debug("setVideoInfo: setLastVideoPlayed");
            CustomApplication.setLastVideoPlayedId(mVideoInfo.id);
            CustomApplication.setLastVideoPlayedUri(mVideoInfo.uri);
        }
        // Network metadata is a separate resume candidate; do not overwrite the local candidate
        // when the user chooses it.
        if (resumeSource != ResumeSource.NETWORK) updateResumeCandidates(mVideoInfo, null);
        boolean retainedLivePosition = PlaybackResumePolicy.shouldPreserveLiveSelection(
                resumeSource == ResumeSource.NONE,
                mPlaybackSession.selectedSource == ResumeSource.LIVE);
        if (resumeSource != ResumeSource.NONE
                || (!mPlaybackSession.startPositionApplied && !retainedLivePosition)) {
            selectInitialResumePosition(mVideoInfo, resumeSource);
        }
        // Do not write videoInfo here to avoid race condition overwriting resume position
        // when returning quickly from screensaver (issue #1590). The lastTimePlayed will
        // be updated when the video is actually stopped via saveVideoStateIfReady().
        // Simply update the in-memory timestamp for now.
        if (!PrivateMode.isActive()&&mVideoInfo!=null) {
            mVideoInfo.lastTimePlayed = Long.valueOf(System.currentTimeMillis() / 1000L);
        }
        mUri = mVideoInfo.uri;
        mIntent.setData(mUri);
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingMetadata();
        }
        if(mCallOnDataUriOKWhenVideoInfoIsSet)
            onDataUriOK();
        else //when onDataUriOk has been called or when torrent file
            postPreparedAndVideoDb();
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        if (log.isDebugEnabled()) log.debug("onDestroy");
        if (mAutoSkipTask != null)
            mHandler.removeCallbacks(mAutoSkipTask);
        saveVideoStateIfReady();
        if (log.isDebugEnabled()) log.debug("onDestroy: release mediaSessionCompat");
        if (mSession != null) {
            stopNowPlayingCard();
            mSession.setActive(false);
            mSession.release();
        }
        if(mIndexHelper!=null)
            mIndexHelper.abort();
        mDestroyed = true;
        if (log.isDebugEnabled()) log.debug("onDestroy: unregister headsetPluggedReceiver");
        unregisterReceiver(headsetPluggedReceiver);
        sPlayerService=null;
        if(mTorrent!=null)
            try {
                unbindService(mTorrentObserverServiceConnection);
            }catch(java.lang.IllegalArgumentException e) {}
        if (log.isDebugEnabled()) log.debug("onDestroy");
    }

    /**
     * When we receive videoDbInfo, just propose player frontend which one to choose
     * @param info
     * @param remoteInfo
     */
    @Override
    public void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo) {
        if(mDestroyed) //will perhaps fix some weird crashes on playstore console
            return;
        updateResumeCandidates(info, remoteInfo);
        if(mPlayerFrontend!=null) {
            if (log.isDebugEnabled()) log.debug("onVideoDb: mPlayerFrontend.onVideoDb");
            mPlayerFrontend.onVideoDb(info, remoteInfo);
        }
    }

    @Override
    public void onScraped(ScrapeDetailResult result) {}

    private void postPreparedAndVideoDb() {
        if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb");
        if(mVideoInfo!=null&&mPlayerState==PlayerState.PREPARED) {
            int startPosition = mPlaybackSession.selectedStartPositionMs;
            if (startPosition == mPlayer.getDuration()) startPosition = 0;
            mPlaybackSession.lastKnownPositionMs = startPosition;
            if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb: seeking to {} position {}",
                    mPlaybackSession.selectedSource, startPosition);
            Player.sPlayer.seekTo(startPosition);
            mPlaybackSession.startPositionApplied = true;
            // Explicit intent positions are one-shot inputs. Keep the selected source for
            // diagnostics, but do not let a later metadata refresh seek backward again.
            mPlaybackSession.setCandidate(ResumeSource.EXPLICIT, 0);
            setAudioDelay(mAudioDelay, true);
            // no audio_speed if in passthrough
            if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb: setAudioSpeed force {}", mAudioSpeed);
            setAudioSpeed(mAudioSpeed, true);
            if(mPlayOnResume) {
                mPlayerFrontend.onFirstPlay();
                if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb: player start PlayerController.STATE_NORMAL");
                Player.sPlayer.start(PlayerController.STATE_NORMAL);
                PlayerService.sPlayerService.mPlayerState = PlayerService.PlayerState.PLAYING;
            }
            if(mAudioSubtitleNeedUpdate){ // when we have info about subs or audio track BEFORE mVideoInfo is set
                if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb: audiotrack onAudioMetadataUpdated {}", mNewAudioTrack);
                onAudioMetadataUpdated(mPlayer.getVideoMetadata(), mNewAudioTrack);
                if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb: subtitletrack onSubtitleMetadataUpdated {}", mNewSubtitleTrack);
                onSubtitleMetadataUpdated(mPlayer.getVideoMetadata(), mNewSubtitleTrack);
                mAudioSubtitleNeedUpdate = false;
            }
            if (log.isDebugEnabled()) log.debug("postPreparedAndVideoDb: mPlayerFrontend.onPrepared, setPlaMode {}", mPlayMode);
            setPlayMode(mPlayMode, false); //look for next uri
            setAudioFilt();
            if (PERIODIC_BOOKMARK_SAVE)
                mHandler.postDelayed(mAutoSaveTask, AUTO_SAVE_INTERVAL);
            fetchIntroDbIfNeeded();
            mHandler.removeCallbacks(mAutoSkipTask);
            mHandler.postDelayed(mAutoSkipTask, AUTO_SKIP_INTERVAL);
        }
    }

    /**
     * Fetch intro/outro segments for the current video via the multi-provider IntroDbManager
     * (GET only, no api key). Runs at most once per video on a background thread; the fused,
     * provider-agnostic result is cached in mIntroSegments for the auto-skip check in the tick.
     */
    private void fetchIntroDbIfNeeded() {
        if (mVideoInfo == null) return;
        if (!mPreferences.getBoolean(KEY_INTRODB_ENABLED, DEFAULT_INTRODB_ENABLED)) return;
        if (!mVideoInfo.isScraped) {
            if (log.isDebugEnabled()) log.debug("fetchIntroDbIfNeeded: not scraped, skipping");
            return;
        }
        final Uri uri = mVideoInfo.uri;
        if (uri == null) return;
        // already fetched (or fetch in flight) for this video
        if (uri.equals(mIntroDbFetchedUri)) return;
        mIntroDbFetchedUri = uri;
        mIntroSegments = null; // drop any stale data from the previous video
        mLastAutoSkippedEndMs = -1;

        final IntroDbQueryParams query = buildIntroDbQuery();
        if (query == null) return;

        if (log.isDebugEnabled()) log.debug("fetchIntroDbIfNeeded: querying {}", query);
        new Thread("IntroDbFetch") {
            public void run() {
                IntroSegments segments = IntroDbManager.fetch(query);
                if (uri.equals(mIntroDbFetchedUri)) {
                    mIntroSegments = segments;
                    if (log.isDebugEnabled()) log.debug("fetchIntroDbIfNeeded: stored {}", segments);
                    if (segments != null) {
                        showIntroDbDebugToast(segments.toDebugString(introLabels(getApplicationContext())));
                        mHandler.post(() -> { if (mPlayerFrontend != null) mPlayerFrontend.onIntroDbReady(); });
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("fetchIntroDbIfNeeded: video changed, dropping result");
                }
            }
        }.start();
    }

    /**
     * Build the unified query for IntroDbManager from the current scraped metadata, carrying the
     * identifiers both providers need (tmdb id for theintrodb.org, show imdb id + season/episode
     * for introdb.app). Returns null when no usable identifier is available.
     */
    private IntroDbQueryParams buildIntroDbQuery() {
        final boolean isShow = mVideoInfo.isShow;
        IntroDbQueryParams params = new IntroDbQueryParams();
        params.setIsShow(isShow);
        try {
            String idStr = isShow ? mVideoInfo.scraperShowId : mVideoInfo.scraperMovieId;
            if (idStr != null && !idStr.isEmpty()) params.setTmdbId(Integer.valueOf(idStr));
        } catch (NumberFormatException e) {
            log.warn("buildIntroDbQuery: invalid tmdb id");
        }
        if (isShow) {
            // show imdb id is what introdb.app keys on
            String imdbId = mVideoInfo.scraperShowImdbId;
            if (imdbId != null && !imdbId.isEmpty()) params.setImdbId(imdbId);
            if (mVideoInfo.scraperSeasonNr > 0) params.setSeason(mVideoInfo.scraperSeasonNr);
            if (mVideoInfo.scraperEpisodeNr > 0) params.setEpisode(mVideoInfo.scraperEpisodeNr);
        }
        long durationMs = getEffectiveDurationMs();
        if (durationMs > 0) params.setDurationMs(durationMs);
        if (!params.hasIdentifier()) {
            if (log.isDebugEnabled()) log.debug("buildIntroDbQuery: no identifier, skipping");
            return null;
        }
        return params;
    }

    /** Fused intro/outro segments for the current video, or null if not available yet. */
    public IntroSegments getIntroSegments() {
        return mIntroSegments;
    }

    /**
     * Trace-only: surface the consolidated segment timings on screen once when TRACE logging is
     * enabled. Safe to call from a background fetch thread (posts to the main handler).
     */
    private void showIntroDbDebugToast(final String message) {
        if (!log.isTraceEnabled() || message == null || message.isEmpty()) return;
        mHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    /**
     * Auto-skip check, run periodically while playing. If the current position falls inside an
     * intro or recap segment, seek to the end of that segment. Credits/outro/preview are
     * intentionally left out of the proof of concept (skipping them could jump to end of media).
     */
    private void autoSkipIfNeeded() {
        IntroSegments segments = mIntroSegments;
        if (segments == null) {
            if (log.isTraceEnabled()) log.trace("autoSkipIfNeeded: no data yet");
            return;
        }
        boolean playing = mPlayer != null && Player.sPlayer != null && mPlayer.isPlaying();
        int position = (Player.sPlayer != null) ? Player.sPlayer.getCurrentPosition() : -1;
        if (log.isDebugEnabled())
            log.debug("autoSkipIfNeeded: tick pos={} playing={} pref={}",
                    position, playing, mPreferences.getBoolean(KEY_INTRODB_ENABLED, DEFAULT_INTRODB_ENABLED));
        if (mPlayer == null || Player.sPlayer == null || !mPlayer.isPlaying()) return;
        boolean introEnabled = mPreferences.getBoolean(KEY_INTRODB_ENABLED, DEFAULT_INTRODB_ENABLED);
        if (!introEnabled) return;
        // Recap has no separate toggle: it is additionally skipped only while actually
        // binge-watching, i.e. in binge mode on an episode we auto-advanced into (so the very
        // first episode of the binge keeps its recap).
        boolean recapEnabled = mPlayMode == PLAYMODE_BINGE && mArrivedViaBingeTransition;
        if (position < 0) return;
        IntroSegments.Skip skip = segments.findSkip(position, introEnabled, recapEnabled);
        if (skip == null) return;
        long targetMs = Math.max(0, skip.endMs - AUTO_SKIP_BUFFER_MS);
        if (targetMs <= position) return;
        // don't re-skip the same segment if the user deliberately seeks back into it
        if (skip.endMs == mLastAutoSkippedEndMs) return;
        mLastAutoSkippedEndMs = skip.endMs;
        // When the skip target reaches the end of the media (e.g. an outro that runs to the
        // file end), seeking there fails ("stream_seek time err") and breaks playback. End the
        // video naturally instead, which advances to the next episode (binge) or stops cleanly.
        int duration = Player.sPlayer.getDuration();
        if (duration > 0 && targetMs >= duration - AUTO_SKIP_END_MARGIN_MS) {
            if (log.isDebugEnabled()) log.debug("autoSkipIfNeeded: skipping {} from {} reaches end ({}), completing", skip.type, position, duration);
            showAutoSkipToast(skip.type);
            onCompletion();
            return;
        }
        if (log.isDebugEnabled()) log.debug("autoSkipIfNeeded: skipping {} from {} to {} (segment end {})", skip.type, position, targetMs, skip.endMs);
        Player.sPlayer.seekTo((int) targetMs);
        showAutoSkipToast(skip.type);
    }

    // User-facing feedback when an auto-skip fires (the user opted in via the Play mode toggle).
    private void showAutoSkipToast(final IntroSegments.Type type) {
        final String message = getString(R.string.introdb_autoskip) + ": " + introLabels(getApplicationContext()).get(type);
        mHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    // Translatable display labels for each segment type, resolved from string resources.
    public static Map<IntroSegments.Type, String> introLabels(Context ctx) {
        Map<IntroSegments.Type, String> labels = new EnumMap<>(IntroSegments.Type.class);
        labels.put(IntroSegments.Type.INTRO, ctx.getString(R.string.introdb_segment_intro));
        labels.put(IntroSegments.Type.RECAP, ctx.getString(R.string.introdb_segment_recap));
        labels.put(IntroSegments.Type.OUTRO, ctx.getString(R.string.introdb_segment_outro));
        labels.put(IntroSegments.Type.CREDITS, ctx.getString(R.string.introdb_segment_credits));
        labels.put(IntroSegments.Type.PREVIEW, ctx.getString(R.string.introdb_segment_preview));
        return labels;
    }

    public void requestIndexAndScrap(){
        if (!PrivateMode.isActive()) {
            if (mVideoInfo.id == -1) {
                mHasRequestedIndexing = true;

                getContentResolver().registerContentObserver(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, true, mVideoObserver);
                if(UriUtils.isIndexable(mVideoInfo.uri)) {
                    final Uri uri = mVideoInfo.uri;
                    new Thread() {
                        public void run() {
                            if (!VideoStoreImportImpl.isNoMediaPath(uri))
                                VideoStore.requestIndexing(uri, PlayerService.this);
                        }
                    }.start();
                }
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public void onPrepared() {
        if (log.isDebugEnabled()) log.debug("onPrepared()");
        mPlayerState = PlayerState.PREPARED;
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onPrepared();
        }
        postPreparedAndVideoDb();
    }

    /**
     * save video state and look for the next video to play
     */
    @Override
    public void onCompletion() {
        if (log.isDebugEnabled()) log.debug("onCompletion");
        mPlayerState = PlayerState.STOPPED;

        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        mPlaybackSession.completed = true;
        if (mNextUri != null) {
            if (log.isDebugEnabled()) log.debug("onCompletion: we have a new video {}", mNextUri);
            stopAndSaveVideoState();
            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onCompletion();
            }
            mUri = mNextUri;
            mStreamingUri=mUri;
            mIntent.setData(mUri);
            mIntent.putExtra(KEY_STREAMING_URI, mStreamingUri);
            mIntent.putExtra(RESUME, RESUME_NO);
            clearPositionExtras(mIntent); // Never carry a resume point into the next playback.
            // Auto-next and repeat are new playback sessions too. Rotate the generation so a
            // delayed checkpoint from the completed item cannot reattach to the new session.
            mIntent.putExtra(LAUNCH_GENERATION, UUID.randomUUID().toString());
            mVideoId = mNextVideoId;
            mNextUri = null;
            mNextVideoId = -1;
            // Repeat-single legitimately starts the same URI, but it is still a new playback.
            mPlaybackSession.reset(null, null);
            onStart(mIntent);
            // onStart() cleared the flag; mark that this episode was auto-advanced into, so recap
            // auto-skip may apply (it additionally requires PLAYMODE_BINGE).
            mArrivedViaBingeTransition = true;
        } else {
            if (log.isDebugEnabled()) log.debug("onCompletion: we have no new video after {} mVideoInfo.id {}", mVideoId, mVideoInfo.id);
            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onEnd();
            }
        }
    }

    @Override
    public boolean onError(int errorCode, int errorQualCode, String msg) {
        if (log.isDebugEnabled()) log.debug("onError");
        mPlayerState = PlayerState.STOPPED;
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onError(errorCode, errorQualCode, msg);
        }
        return false;
    }

    @Override
    public void onSeekStart(int pos) {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onSeekStart(pos);
        }
    }

    @Override
    public void onSeekComplete() {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onSeekComplete();
        }
    }

    @Override
    public void onAllSeekComplete() {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onAllSeekComplete();
        }
    }

    @Override
    public void onPlay(int state) {
        if (log.isDebugEnabled()) log.debug("onPlay");
        mPlayerState = PlayerState.PLAYING;
        if (state == PlayerController.STATE_NORMAL) {
            if (log.isDebugEnabled()) log.debug("onPlay: PlayerController.STATE_NORMAL -> startTrakt()");
            startTrakt();
        } else {
            if (log.isDebugEnabled()) log.debug("onPlay: !PlayerController.STATE_NORMAL -> not startTrakt()!");
        }
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onPlay(state);
        }
    }

    @Override
    public void onPause(int state) {
        if (log.isDebugEnabled()) log.debug("onPause");
        mPlayerState = PlayerState.PAUSED;
        // pauseTrakt() must run before saveVideoStateIfReady() so that it sets
        // mVideoInfo.traktResume = -progress synchronously before the async DB write captures it
        if (state == PlayerController.STATE_NORMAL) {
            if (log.isDebugEnabled()) log.debug("onPause: normal state thus pauseTrakt()!");
            pauseTrakt();
        } else {
            if (log.isDebugEnabled()) log.debug("onPause: other/seek state thus not doing pauseTrakt()!");
        }
        saveVideoStateIfReady();
        if (ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        if(mPlayerFrontend!=null){
            mPlayerFrontend.onPause(state);
        }
    }

    @Override
    public void onOSDUpdate() {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onOSDUpdate();
        }
    }

    @Override
    public void onVideoMetadataUpdated(VideoMetadata vMetadata) {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onVideoMetadataUpdated(vMetadata);
        }
    }

    @Override
    public void onAudioMetadataUpdated(VideoMetadata vMetadata, int newAudioTrack) {
        /*
         * if current audio track is invalid or not supported, choose the first supported one
         */

        if (mVideoInfo == null) {
            mNewAudioTrack = newAudioTrack;
            mAudioSubtitleNeedUpdate = true;
            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: mVideoInfo == null, mNewAudioTrack={} newAudioTrack={} mAudioSubtitleNeedUpdate={}", mNewAudioTrack, newAudioTrack, mAudioSubtitleNeedUpdate);
            return;
        } else {
            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: mVideoInfo != null, mVideoInfo.audioTrack={} newAudioTrack={} mAudioSubtitleNeedUpdate={}", mVideoInfo.audioTrack, newAudioTrack, mAudioSubtitleNeedUpdate);
        }
        int nbTrack = vMetadata.getAudioTrackNb();
        boolean supported = true;

        String trackName = "";
        Integer firstSupportedTrack = null;
        Integer languageMatchTrack = null;
        Integer languageDefaultMatchTrack = null;
        Integer languageVariantMatchTrack = null;
        Integer originalLanguageMatchTrack = null;
        Integer originalLanguageDefaultMatchTrack = null;
        String originalLanguage = mVideoInfo.scraperOriginalLanguage;
        boolean chooseInitialAudioTrack = (mVideoInfo.audioTrack < 0 || mVideoInfo.audioTrack >= nbTrack || !vMetadata.getAudioTrack(mVideoInfo.audioTrack).supported) && firstTimeAudioCalled;
        boolean preferOriginalLanguage = mPreferOriginalAudioTrack && originalLanguage != null
                && !originalLanguage.isEmpty() && !"und".equalsIgnoreCase(originalLanguage);
        supported = false;

        // VideoDbInfo sets audioTrack to -1 when file has not been played or restores playerParams
        for (int i = 0; i < nbTrack; ++i) {
            if (vMetadata.getAudioTrack(i).supported) {
                trackName = generateTrackName(getApplicationContext(), vMetadata.getAudioTrack(i).name, vMetadata.getAudioTrack(i).language, vMetadata.getAudioTrack(i).format, vMetadata.getAudioTrack(i).disposition, true);
                if (firstSupportedTrack == null) {
                    if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: identify firstSupportedTrack={}({})", i, trackName);
                    firstSupportedTrack = i;
                    supported = true;
                }
                if (chooseInitialAudioTrack) { // track has not been selected yet and it is the first time video is played
                    String trackLanguage = vMetadata.getAudioTrack(i).language;
                    if (preferOriginalLanguage && ISO639codes.isFavoriteLanguageMatch(originalLanguage, trackLanguage)) {
                        if (originalLanguageMatchTrack == null) {
                            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: track #{} -> {} matches original audio language {}", i, trackName, originalLanguage);
                            originalLanguageMatchTrack = i;
                        }
                        if (originalLanguageDefaultMatchTrack == null && (vMetadata.getAudioTrack(i).disposition & VideoUtils.AV_DISPOSITION_DEFAULT) != 0) {
                            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: track #{} -> {} matches original audio language {} and is marked default", i, trackName, originalLanguage);
                            originalLanguageDefaultMatchTrack = i;
                        }
                    }
                    if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: trying to match favorite audioTrack language {} against track #{} language {} ({})", mAudioTrackFavoriteLanguage, i, trackLanguage, trackName);
                    if (ISO639codes.isFavoriteLanguageMatch(mAudioTrackFavoriteLanguage, trackLanguage)) {
                        if (languageMatchTrack == null) {
                            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: track #{} -> {} matches favorite audioTrack language {}", i, trackName, mAudioTrackFavoriteLanguage);
                            languageMatchTrack = i;
                        }
                        // among same-language tracks (e.g. several untitled/generic Chinese tracks),
                        // the container's own "default" disposition flag is the best signal of which
                        // one is the most probable/intended track when no title hint disambiguates it
                        if (languageDefaultMatchTrack == null && (vMetadata.getAudioTrack(i).disposition & VideoUtils.AV_DISPOSITION_DEFAULT) != 0) {
                            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: track #{} -> {} matches favorite audioTrack language {} and is marked default", i, trackName, mAudioTrackFavoriteLanguage);
                            languageDefaultMatchTrack = i;
                        }
                        // best-effort disambiguation between Chinese sub-variants (Mainland/HK/Taiwan)
                        // that share the same "chi"/"zho" code, based on the track's free-text title
                        if (languageVariantMatchTrack == null && ISO639codes.titleMatchesChineseVariant(mAudioTrackFavoriteLanguage, vMetadata.getAudioTrack(i).name)) {
                            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: track #{} -> {} matches favorite Chinese variant {}", i, trackName, mAudioTrackFavoriteLanguage);
                            languageVariantMatchTrack = i;
                        }
                    } else {
                        if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: skip track: #{} -> {} not matching favorite audioTrack language {}", i, trackName, mAudioTrackFavoriteLanguage);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: not trying to find {} in {} because mVideoInfo.audioTrack={} out of range or not supported or firstTimeAudioCalled={}", mAudioTrackFavoriteLanguage, trackName, mVideoInfo.audioTrack, firstTimeAudioCalled);
                }
            }
        }
        // prefer a Chinese-variant title match (Mandarin/Cantonese/Taiwan) if found, otherwise the
        // language-matching track marked "default" by the container, otherwise the first track
        // whose language code matches the favorite audio language
        Integer selectedOriginalAudioTrack = ISO639codes.selectPreferredTrack(null, originalLanguageDefaultMatchTrack, originalLanguageMatchTrack);
        Integer selectedFavoriteAudioTrack = ISO639codes.selectPreferredTrack(languageVariantMatchTrack, languageDefaultMatchTrack, languageMatchTrack);
        Integer selectedAudioTrack = selectedOriginalAudioTrack != null ? selectedOriginalAudioTrack : selectedFavoriteAudioTrack;
        if (selectedOriginalAudioTrack != null && log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: preferring original audio language {} on track #{}", originalLanguage, selectedOriginalAudioTrack);
        if (selectedAudioTrack != null) mVideoInfo.audioTrack = selectedAudioTrack;

        // if no valid audioTrack selected revert to firstSupportedTrack if it exists
        if ((mVideoInfo.audioTrack < 0 || mVideoInfo.audioTrack >= nbTrack) && firstTimeAudioCalled && firstSupportedTrack != null)
            mVideoInfo.audioTrack = firstSupportedTrack;

        firstTimeAudioCalled = false;

        if (mVideoInfo.audioTrack == -1)
            mVideoInfo.audioTrack = newAudioTrack;

        mNewAudioTrack = mVideoInfo.audioTrack; // this trigs the change in player

        if (mVideoInfo.audioTrack != newAudioTrack && !mPlayer.setAudioTrack(mVideoInfo.audioTrack))
            supported = false;
        if (nbTrack>0 && !supported) {
            log.error("audio not supported!");
            mVideoInfo.audioTrack = 0;
            VideoMetadata.AudioTrack at = mPlayer.getVideoMetadata().getAudioTrack(0);
            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onAudioError(true, at != null ? at.format : "unknown");
            }
        }

        if(mPlayerFrontend!=null) {
            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: mPlayerFrontend.onAudioMetadataUpdated");
            mPlayerFrontend.onAudioMetadataUpdated(vMetadata, newAudioTrack);
        }
    }

    @Override
    public void onAudioTrackSelectionCompleted(int track, boolean success) {
        if (success || mVideoInfo == null || mVideoInfo.audioTrack != track) {
            return;
        }

        log.error("onAudioTrackSelectionCompleted: failed to apply track {}", track);
        int fallbackTrack = 0;
        mVideoInfo.audioTrack = fallbackTrack;
        mNewAudioTrack = fallbackTrack;
        if (track != fallbackTrack && !mPlayer.setAudioTrack(fallbackTrack)) {
            log.error("onAudioTrackSelectionCompleted: failed to queue fallback track {}", fallbackTrack);
        }

        VideoMetadata.AudioTrack audioTrack = mPlayer.getVideoMetadata().getAudioTrack(fallbackTrack);
        if (mPlayerFrontend != null) {
            mPlayerFrontend.onAudioError(true, audioTrack != null ? audioTrack.format : "unknown");
        }
    }

    @Override
    public void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int newSubtitleTrack) {
        // note: if mIsPreparingSubs = true, onSubtitleMetadataUpdated can be called even if in not final state
        // subs could be still being copied in cache directory
        // however in a replay context cache is not  updated since all the subs have already been copied
        // in this case prepareSubs() will call mediaPlayer.checkSubtitles() but avos will not notify handleMetadata()
        // and onSubtitleMetadataUpdated will not been called a second time resulting in only internal subs being displayed

        if (mVideoInfo == null) {
            mNewSubtitleTrack = newSubtitleTrack;
            mAudioSubtitleNeedUpdate = true;
            return;
        }

        // /!\ IMPORTANT: this is only for the player part, setting the UI subtitle track is done in PlayerActivity thus the two must be in sync

        int nbTrack = vMetadata.getSubtitleTrackNb(); // this contains the number of subtitlesTracks not including the none track
        Integer fallbackTextTrack = null; // track for external text subs with no language provided
        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: nbTrack={} newSubtitleTrack={} mVideoInfo.subtitleTrack={} mHideSubtitles={} mSubsFavoriteLanguage={} firstTimeSubCalled={} mIsPreparingSubs={}", nbTrack, newSubtitleTrack, mVideoInfo.subtitleTrack, mHideSubtitles, mSubsFavoriteLanguage, firstTimeSubCalled, mIsPreparingSubs);
        // selection logic
        if (nbTrack != 0) {
            int noneTrack = nbTrack; // here it tracks mVideoInfo.subtitleTrack that are the tracks of the video (not the none from menu)
            // Validate saved subtitle language against current track at saved index
            if (mVideoInfo.subtitleTrack >= 0 && mVideoInfo.subtitleLanguage != null) {
                VideoMetadata.SubtitleTrack trackAtIndex = vMetadata.getSubtitleTrack(mVideoInfo.subtitleTrack);
                if (trackAtIndex != null) {
                    String currentLanguage = null;
                    if (trackAtIndex.isExternal) {
                        currentLanguage = getSubLanguageFromSubPathAndVideoPath(getApplicationContext(), trackAtIndex.path, vMetadata.getFile().getPath());
                    } else {
                        currentLanguage = ISO639codes.getLanguageNameForLetterCode(trackAtIndex.language);
                    }
                    String currentLang2Letter = extractLanguageCode(currentLanguage).toLowerCase(Locale.ROOT);
                    String savedLang2Letter = mVideoInfo.subtitleLanguage.toLowerCase(Locale.ROOT);
                    if (!savedLang2Letter.equals(currentLang2Letter)) {
                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: subtitle language mismatch at index {}: saved={}, current={}", mVideoInfo.subtitleTrack, savedLang2Letter, currentLang2Letter);
                        mVideoInfo.subtitleTrack = -1;
                        mVideoInfo.subtitleLanguage = null;
                    }
                }
            }
            // do the scan for preferred lang at second call since onSubtitleMetadataUpdated is called twice and the first time it does not get all subtracks when there are a Subs/ dir with a lot of subs
            if (mVideoInfo.subtitleTrack == -1 && (firstTimeSubCalled || ! mIsPreparingSubs)) { // means no track has been selected before
                String currentLocaleLanguage = Locale.getDefault().getLanguage();
                boolean selectedAudioIsInCurrentLocale =
                        isSelectedAudioTrackInLanguage(vMetadata, currentLocaleLanguage);
                boolean selectedAudioIsOutsideCurrentLocale =
                        isSelectedAudioTrackOutsideLanguage(vMetadata, currentLocaleLanguage);
                Integer defaultExternalTextTrack = mHideSubtitles ? null
                        : selectDefaultExternalTextSubtitleTrack(vMetadata);
                if (mHideSubtitles) {
                    Integer forcedTrack = selectForcedSubtitleTrack(vMetadata);
                    mVideoInfo.subtitleTrack = Objects.requireNonNullElse(forcedTrack, noneTrack);
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: hide regular subtitles -> selected forced track {}", mVideoInfo.subtitleTrack);
                } else if (selectedAudioIsInCurrentLocale
                        && ISO639codes.isFavoriteLanguageMatch(mSubsFavoriteLanguage, currentLocaleLanguage)) {
                    Integer forcedTrack = selectForcedSubtitleTrack(vMetadata);
                    mVideoInfo.subtitleTrack = Objects.requireNonNullElse(forcedTrack,
                            Objects.requireNonNullElse(defaultExternalTextTrack, noneTrack));
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: active audio and preferred subtitles match current locale {} -> selected forced or untagged default external track {}", currentLocaleLanguage, mVideoInfo.subtitleTrack);
                } else {
                    Locale locale = Locale.forLanguageTag(mSubsFavoriteLanguage);
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: favorite locale {}, current locale {}", locale.getDisplayLanguage(), Locale.getDefault().getDisplayLanguage());
                    String trackName = "";
                    String lang = null;
                    Integer languageMatchTrack = null;
                    Integer languageDefaultMatchTrack = null;
                    Integer languageVariantMatchTrack = null;
                    Integer englishMatchTrack = null;
                    Integer englishDefaultMatchTrack = null;
                    for (int i = 0; i < nbTrack; ++i) { // select default track
                        trackName = vMetadata.getSubtitleTrack(i).name;
                        // select default locale and avoid forced subs
                        if (vMetadata.getSubtitleTrack(i).isExternal) {
                            // this returns the subtitle format (e.g. SRT/VTT) if subFileName is video.<ext> and videoFileName is video.mkv
                            lang = getSubLanguageFromSubPathAndVideoPath(getApplicationContext(), vMetadata.getSubtitleTrack(i).path, vMetadata.getFile().getPath());
                            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: subtrack {}, ext sub found lang={} ({})", i, lang, vMetadata.getSubtitleTrack(i).path);
                        } else {
                            lang = ISO639codes.getLanguageNameForLetterCode(vMetadata.getSubtitleTrack(i).language);
                            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: subtrack {}, int sub found lang={} ({})", i, lang, trackName);
                        }
                        if (lang == null || lang.isEmpty()) {
                            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: no language found in track/file name -> set it to unknown");
                            lang = getText(R.string.unknown_track_name).toString();
                        } else {
                            if (isForcedSubtitleTrack(vMetadata.getSubtitleTrack(i))) {
                                if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: skip track: {} with identified lang: {} because it contains forced sub", trackName, lang);
                            } else {
                                if (lang.toLowerCase(Locale.getDefault()).contains(locale.getDisplayLanguage().toLowerCase(Locale.getDefault()))) {
                                    if (languageMatchTrack == null) {
                                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: track {} identified lang: {} matches locale language {}", trackName, lang, locale.getDisplayLanguage());
                                        languageMatchTrack = i;
                                    }
                                    // among same-language tracks (e.g. Simplified/Traditional Chinese subs both
                                    // tagged "Chinese"), the container's own "default" disposition flag is the
                                    // best signal of the intended track when no title hint disambiguates it
                                    if (languageDefaultMatchTrack == null && (vMetadata.getSubtitleTrack(i).disposition & VideoUtils.AV_DISPOSITION_DEFAULT) != 0) {
                                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: track {} identified lang: {} matches locale language {} and is marked default", trackName, lang, locale.getDisplayLanguage());
                                        languageDefaultMatchTrack = i;
                                    }
                                    // best-effort disambiguation between Chinese sub-variants (Mainland/HK/Taiwan)
                                    // sharing the same "Chinese" language, based on the track's free-text title
                                    // (e.g. "Simplified"/"Traditional")
                                    if (languageVariantMatchTrack == null && ISO639codes.titleMatchesChineseVariant(mSubsFavoriteLanguage, trackName)) {
                                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: track {} identified lang: {} matches favorite Chinese variant {}", trackName, lang, mSubsFavoriteLanguage);
                                        languageVariantMatchTrack = i;
                                    }
                                } else {
                                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: skip track: {} identified lang: {} != locale language {}", trackName, lang, locale.getDisplayLanguage());
                                }
                                if (selectedAudioIsOutsideCurrentLocale
                                        && isSubtitleTrackInLanguage(vMetadata, i, "en")) {
                                    if (englishMatchTrack == null) {
                                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: track {} identified lang: {} matches English fallback", trackName, lang);
                                        englishMatchTrack = i;
                                    }
                                    if (englishDefaultMatchTrack == null
                                            && (vMetadata.getSubtitleTrack(i).disposition & VideoUtils.AV_DISPOSITION_DEFAULT) != 0) {
                                        englishDefaultMatchTrack = i;
                                    }
                                }
                            }
                        }
                        if (vMetadata.getSubtitleTrack(i).isExternal && isGenericTextSubtitleFormat(lang)) {
                            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: found external text track {} ({}) -> setting fallbackTextTrack={}", i, lang, i);
                            fallbackTextTrack = i;
                        }
                    }
                    // prefer a Chinese-variant title match (Simplified/Traditional) if found, otherwise
                    // the language-matching track marked "default" by the container, otherwise the
                    // first track whose language matches the favorite subtitle language. If none
                    // matches and the active audio is foreign, use the English fallback below.
                    Integer selectedSubtitleTrack = ISO639codes.selectPreferredTrack(languageVariantMatchTrack, languageDefaultMatchTrack, languageMatchTrack);
                    if (selectedSubtitleTrack == null && selectedAudioIsOutsideCurrentLocale) {
                        selectedSubtitleTrack = ISO639codes.selectPreferredTrack(null, englishDefaultMatchTrack, englishMatchTrack);
                        if (selectedSubtitleTrack != null && log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: no favorite-language subtitle found -> selected English fallback track {}", selectedSubtitleTrack);
                    }
                    if (selectedSubtitleTrack != null) mVideoInfo.subtitleTrack = selectedSubtitleTrack;
                    if (!mHideSubtitles && mVideoInfo.subtitleTrack == -1) { // selects newSubtitleTrack (could be noneTrack) if language not found
                        int newTrack = 0;
                        String revertTrackName = "";
                        if (USE_NONETRACK_IF_SUB_LANG_NOT_FOUND) {
                            newTrack = Objects.requireNonNullElse(fallbackTextTrack, noneTrack); // strategy to put no subs if lang not found
                            revertTrackName = "noneTrack";
                        } else {
                            newTrack = Objects.requireNonNullElse(fallbackTextTrack, newSubtitleTrack); // strategy to revert to newSubtitleTrack if lang not found (legacy)
                            revertTrackName = "newSubtitleTrack";
                        }
                        if (newTrack < nbTrack && isForcedSubtitleTrack(vMetadata.getSubtitleTrack(newTrack))) {
                            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: fallback {} is forced -> selected none track", newTrack);
                            newTrack = noneTrack;
                            revertTrackName = "noneTrack";
                        }
                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: no default sub found mVideoInfo.subtitleTrack: {} -> setting {} or external text ({}) track if exists -> videoInfo.subtitleTrack={}", mVideoInfo.subtitleTrack, revertTrackName, fallbackTextTrack, newTrack);
                        mVideoInfo.subtitleTrack = newTrack;
                    }
                    if (mVideoInfo.subtitleTrack == noneTrack) { // if none track selected, player gets -1 track
                        // nonTrack is nbTracks
                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: hideSubs or noneTrack -> player.setSubtitleTrack(-1) and  videoInfo.subtitleTrack={}", noneTrack);
                        mVideoInfo.subtitleTrack = noneTrack;
                        mPlayer.setSubtitleTrack(-1);
                    }
                    // at this stage mVideoInfo.subtitleTrack is the track number without the none track 0<=mVideoInfo.subtitleTrack<nbTrack but belt and suspenders spirit set it to none track
                    if (mVideoInfo.subtitleTrack < 0 || mVideoInfo.subtitleTrack > nbTrack) {
                        log.error("onSubtitleMetadataUpdated: invalid subtitle track number {} -> setting none track", mVideoInfo.subtitleTrack);
                        mVideoInfo.subtitleTrack = noneTrack;
                    }
                }
                // Extract and save the selected track's language for future validation on re-enumeration
                if (mVideoInfo.subtitleTrack >= 0 && mVideoInfo.subtitleTrack < nbTrack) {
                    VideoMetadata.SubtitleTrack track = vMetadata.getSubtitleTrack(mVideoInfo.subtitleTrack);
                    if (track != null) {
                        String language = null;
                        if (track.isExternal) {
                            language = getSubLanguageFromSubPathAndVideoPath(getApplicationContext(), track.path, vMetadata.getFile().getPath());
                        } else {
                            language = ISO639codes.getLanguageNameForLetterCode(track.language);
                        }
                        mVideoInfo.subtitleLanguage = extractLanguageCode(language).toLowerCase(Locale.ROOT);
                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: auto-selected track {} with language={}", mVideoInfo.subtitleTrack, mVideoInfo.subtitleLanguage);
                    }
                } else {
                    // None track selected or invalid, clear language
                    mVideoInfo.subtitleLanguage = null;
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: none track or invalid selected, clearing language");
                }
            }
            // application logic
            // mVideoInfo.subtitleTrack is the track number without the none track 0<=mVideoInfo.subtitleTrack<=nbTrack, nbTrack is the noneTrack position
            if (mVideoInfo.subtitleTrack >= 0 && mVideoInfo.subtitleTrack <= nbTrack) {
                if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: newSubtitleTrack={}, mVideoInfo.subtitleTrack={}", newSubtitleTrack, mVideoInfo.subtitleTrack);

                if (!mPlayer.setSubtitleTrack(mVideoInfo.subtitleTrack)) {
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: setSubtitleTrack failed, setting none track");
                    mVideoInfo.subtitleTrack = noneTrack;
                } else {
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: setSubtitleTrack done to track {}", mVideoInfo.subtitleTrack);
                }
                if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: subtitleDelay = {}", mVideoInfo.subtitleDelay);
                mPlayer.setSubtitleDelay(mVideoInfo.subtitleDelay);
                if (mVideoInfo.subtitleRatio >= 0) {
                    mPlayer.setSubtitleRatio(mVideoInfo.subtitleRatio);
                }
            } else {
                log.error("onSubtitleMetadataUpdated: invalid subtitle track number={}, this cannot be!", mVideoInfo.subtitleTrack);
            }
            firstTimeSubCalled = false;
        } else {
            firstTimeSubCalled = true;
            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: no subtitle track found");
        }

        // the way to set a new track in the player is to redefine mNewSubtitleTrack: this is the one taken into account
        mNewSubtitleTrack = mVideoInfo.subtitleTrack;

        if(mPlayerFrontend!=null) {
            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: subtitletrack onSubtitleMetadataUpdated {} -> {}", newSubtitleTrack, mVideoInfo.subtitleTrack);
            mPlayerFrontend.onSubtitleMetadataUpdated(vMetadata, newSubtitleTrack);
        }

        // Cache the AVOS metadata result for future playback within 10 second TTL
        SubtitleManager.cacheProcessedMetadata(mUri, vMetadata);
        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: cached AVOS metadata for {}", mUri);
    }

    @Override
    public void onSubtitleTrackSelectionCompleted(int track, boolean success) {
        if (success || mVideoInfo == null || mVideoInfo.subtitleTrack != track) {
            return;
        }

        int noneTrack = mPlayer.getVideoMetadata().getSubtitleTrackNb();
        log.error("onSubtitleTrackSelectionCompleted: failed to apply track {}, falling back to none", track);
        mVideoInfo.subtitleTrack = noneTrack;
        mVideoInfo.subtitleLanguage = null;
        mNewSubtitleTrack = noneTrack;
        if (track != noneTrack && !mPlayer.setSubtitleTrack(noneTrack)) {
            log.error("onSubtitleTrackSelectionCompleted: failed to queue none-track fallback {}", noneTrack);
        }
    }

    private boolean isSelectedAudioTrackInLanguage(VideoMetadata videoMetadata, String language) {
        int audioTrackIndex = mVideoInfo.audioTrack;
        if (audioTrackIndex < 0 || audioTrackIndex >= videoMetadata.getAudioTrackNb()) {
            return false;
        }
        VideoMetadata.AudioTrack audioTrack = videoMetadata.getAudioTrack(audioTrackIndex);
        return audioTrack != null
                && audioTrack.supported
                && ISO639codes.isFavoriteLanguageMatch(language, audioTrack.language);
    }

    private boolean isSelectedAudioTrackOutsideLanguage(VideoMetadata videoMetadata, String language) {
        int audioTrackIndex = mVideoInfo.audioTrack;
        if (audioTrackIndex < 0 || audioTrackIndex >= videoMetadata.getAudioTrackNb()) {
            return false;
        }
        VideoMetadata.AudioTrack audioTrack = videoMetadata.getAudioTrack(audioTrackIndex);
        return audioTrack != null
                && audioTrack.supported
                && !ISO639codes.isFavoriteLanguageMatch(language, audioTrack.language);
    }

    /**
     * A sidecar named exactly like its video (for example {@code movie.srt}) has no language
     * suffix, but conventionally represents the user's default full subtitle. It is eligible
     * only when it is not forced; a forced track remains subject to forced-track selection.
     */
    private @Nullable Integer selectDefaultExternalTextSubtitleTrack(VideoMetadata videoMetadata) {
        for (int i = 0; i < videoMetadata.getSubtitleTrackNb(); i++) {
            VideoMetadata.SubtitleTrack subtitleTrack = videoMetadata.getSubtitleTrack(i);
            if (subtitleTrack == null || !subtitleTrack.isExternal
                    || isForcedSubtitleTrack(subtitleTrack)) {
                continue;
            }
            String language = getSubLanguageFromSubPathAndVideoPath(
                    getApplicationContext(), subtitleTrack.path, videoMetadata.getFile().getPath());
            if (isGenericTextSubtitleFormat(language)) {
                return i;
            }
        }
        return null;
    }

    private @Nullable Integer selectForcedSubtitleTrack(VideoMetadata videoMetadata) {
        int audioTrackIndex = mVideoInfo.audioTrack;
        if (audioTrackIndex < 0 || audioTrackIndex >= videoMetadata.getAudioTrackNb()) {
            return null;
        }
        VideoMetadata.AudioTrack audioTrack = videoMetadata.getAudioTrack(audioTrackIndex);
        if (audioTrack == null || !audioTrack.supported) {
            return null;
        }

        Integer matchingTrack = null;
        Integer matchingDefaultTrack = null;
        Integer unknownLanguageTrack = null;
        Integer unknownLanguageDefaultTrack = null;
        for (int i = 0; i < videoMetadata.getSubtitleTrackNb(); i++) {
            VideoMetadata.SubtitleTrack subtitleTrack = videoMetadata.getSubtitleTrack(i);
            if (!isForcedSubtitleTrack(subtitleTrack)) {
                continue;
            }
            boolean isDefault = (subtitleTrack.disposition & VideoUtils.AV_DISPOSITION_DEFAULT) != 0;
            if (isSubtitleTrackInLanguage(videoMetadata, i, audioTrack.language)) {
                if (matchingTrack == null) matchingTrack = i;
                if (matchingDefaultTrack == null && isDefault) matchingDefaultTrack = i;
            } else if (videoMetadata.getAudioTrackNb() == 1
                    && isSubtitleTrackLanguageUndetermined(videoMetadata, subtitleTrack)) {
                if (unknownLanguageTrack == null) unknownLanguageTrack = i;
                if (unknownLanguageDefaultTrack == null && isDefault) unknownLanguageDefaultTrack = i;
            }
        }
        if (matchingDefaultTrack != null) return matchingDefaultTrack;
        if (matchingTrack != null) return matchingTrack;
        if (unknownLanguageDefaultTrack != null) return unknownLanguageDefaultTrack;
        return unknownLanguageTrack;
    }

    private boolean isForcedSubtitleTrack(@Nullable VideoMetadata.SubtitleTrack subtitleTrack) {
        return subtitleTrack != null
                && ((subtitleTrack.disposition & VideoUtils.AV_DISPOSITION_FORCED) != 0
                || stringContainsForced(subtitleTrack.name)
                || (subtitleTrack.isExternal && stringContainsForced(subtitleTrack.path)));
    }

    private boolean isSubtitleTrackLanguageUndetermined(VideoMetadata videoMetadata,
            VideoMetadata.SubtitleTrack subtitleTrack) {
        String language = subtitleTrack.language;
        if (subtitleTrack.isExternal) {
            language = getSubLanguageFromSubPathAndVideoPath(
                    getApplicationContext(), subtitleTrack.path, videoMetadata.getFile().getPath());
        }
        if (language == null || language.isEmpty() || isGenericTextSubtitleFormat(language)
                || stringContainsForced(language)) {
            return true;
        }
        return "und".equalsIgnoreCase(language) || "unknown".equalsIgnoreCase(language);
    }

    private boolean isSubtitleTrackInLanguage(VideoMetadata videoMetadata, int subtitleTrackIndex,
            String language) {
        VideoMetadata.SubtitleTrack subtitleTrack = videoMetadata.getSubtitleTrack(subtitleTrackIndex);
        if (subtitleTrack == null) {
            return false;
        }
        String subtitleLanguage = subtitleTrack.language;
        if (subtitleTrack.isExternal) {
            subtitleLanguage = getSubLanguageFromSubPathAndVideoPath(
                    getApplicationContext(), subtitleTrack.path, videoMetadata.getFile().getPath());
        }
        return ISO639codes.isFavoriteLanguageMatch(language, extractLanguageCode(subtitleLanguage));
    }

    private static boolean isGenericTextSubtitleFormat(String lang) {
        if (lang == null) return false;
        for (String format : GENERIC_TEXT_SUBTITLE_FORMATS) {
            if (format.equalsIgnoreCase(lang)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract 2-letter ISO 639-1 language code from a language name or code string.
     * Uses ISO639codes utility to handle conversions from different code formats and also
     * recognizes language names localized for the device (for example French "Anglais").
     * Returns lowercase 2-letter code or empty string if unable to extract.
     */
    private static String extractLanguageCode(String languageStr) {
        if (languageStr == null || languageStr.isEmpty()) {
            return "";
        }
        // Handle special cases for known subtitle formats (e.g. SRT, VTT)
        if (isGenericTextSubtitleFormat(languageStr)) {
            return languageStr.toLowerCase(Locale.ROOT);
        }
        // Use ISO639codes utility to convert any format (2-letter, 3-letter, or full name) to 2-letter code
        String code = com.archos.mediacenter.utils.ISO639codes.getISO6391ForLetterCode(languageStr);
        if (code != null && !code.isEmpty()) {
            return code.toLowerCase(Locale.ROOT);
        }
        for (String iso6391 : Locale.getISOLanguages()) {
            Locale locale = Locale.forLanguageTag(iso6391);
            if (languageStr.equalsIgnoreCase(locale.getDisplayLanguage())
                    || languageStr.equalsIgnoreCase(locale.getDisplayLanguage(Locale.ENGLISH))) {
                return iso6391.toLowerCase(Locale.ROOT);
            }
        }
        // Fallback: if it's already a 2-letter code, use it
        if (languageStr.length() == 2 && !languageStr.contains(" ")) {
            return languageStr.toLowerCase(Locale.ROOT);
        }
        // Last resort: return first 2 chars
        return languageStr.substring(0, Math.min(2, languageStr.length())).toLowerCase(Locale.ROOT);
    }

    @Override
    public void onBufferingUpdate(int percent) {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onBufferingUpdate(percent);
        }
    }

    @Override
    public void onSubtitle(Subtitle subtitle) {
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onSubtitle(subtitle);
        }
    }

    public VideoDbInfo getVideoInfo() {
        return mVideoInfo;
    }

    /**
     * displays now playing card on android TV
     */
    private void setNowPlayingCard() {
        /**
         * in case our activity has been stopped, relaunch the video
         */
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(mUri);
        intent.setClass(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 99, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (mSession == null) {
            mSession = new MediaSession(this, "PlayerActivity");
            MediaSession.Callback mediaSessionCallback = new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    super.onPlay();
                    if (log.isDebugEnabled()) log.debug("setNowPlayingCards.onPlay PlayerController.STATE_OTHER");
                    if (Player.sPlayer != null) {
                        Player.sPlayer.start(PlayerController.STATE_OTHER);
                        updateNowPlayingState();
                    }
                }

                @Override
                public void onPause() {
                    super.onPause();
                    if (log.isDebugEnabled()) log.debug("setNowPlayingCards.onPause PlayerController.STATE_OTHER");
                    if (Player.sPlayer != null) {
                        Player.sPlayer.pause(PlayerController.STATE_OTHER);
                        updateNowPlayingState();
                    }
                }
            };
            mSession.setCallback(mediaSessionCallback);
            // Set initial stopped state to avoid reporting undefined/playing state
            PlaybackState.Builder initialStateBuilder = new PlaybackState.Builder()
                    .setActions(getAvailableActions());
            initialStateBuilder.setState(PlaybackState.STATE_NONE, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f);
            mSession.setPlaybackState(initialStateBuilder.build());
        }

        mSession.setSessionActivity(pi);
    }

    /**
     * update state of now playing card to play / buffering / pause / idle
     */
    private void updateNowPlayingState() {
        if(mSession==null)
            return;
        if (mPlayer != null && mPlayer.isPlaying()) {
            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setActions(getAvailableActions());
            stateBuilder.setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            mSession.setPlaybackState(stateBuilder.build());
        }
        else if (mPlayerState==PlayerState.PREPARING) {
            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setActions(getAvailableActions());
            stateBuilder.setState(PlaybackState.STATE_BUFFERING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
            mSession.setPlaybackState(stateBuilder.build());
        }
        else if (mPlayerState==PlayerState.PREPARED) {
            // Video is prepared but not yet playing (e.g. during refresh rate switch)
            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setActions(getAvailableActions());
            stateBuilder.setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f);
            mSession.setPlaybackState(stateBuilder.build());
        }
        else if (mPlayer != null && mPlayer.isPaused()) {
            // Video is paused - report PAUSED state, not STOPPED
            if (!mSession.isActive()) {
                mSession.setActive(true);
            }
            PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                    .setActions(getAvailableActions());
            stateBuilder.setState(PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f);
            mSession.setPlaybackState(stateBuilder.build());
        }
        else stopNowPlayingCard();
    }

    /**
     * set now playing card to stop
     */
    private void stopNowPlayingCard() {
        if(mSession==null)
            return;
        if (log.isDebugEnabled()) log.debug("stopNowPlayingCard");

        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        stateBuilder.setState(PlaybackState.STATE_NONE, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f);
        mSession.setPlaybackState(stateBuilder.build());
        mSession.setActive(false);
    }

    /**
     * Update title and pic on now playing card
     */
    private void updateNowPlayingMetadata() {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        String title = mVideoInfo.scraperTitle!=null?mVideoInfo.scraperTitle:mVideoInfo.title!=null?mVideoInfo.title:FileUtils.getFileNameWithoutExtension(mUri);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                title);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE,title);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                mVideoInfo.scraperCover);
        Bitmap bitmap = null;
        if (mVideoInfo.scraperCover != null && !mVideoInfo.scraperCover.isEmpty()) {
            // Video has a poster, try to decode it
            bitmap = com.archos.mediacenter.utils.BitmapUtils.decodeSampledBitmapFromFile(mVideoInfo.scraperCover, 512, 512);
        }
        if (bitmap == null && mVideoInfo.id >= 0 && (mVideoInfo.scraperCover == null || mVideoInfo.scraperCover.isEmpty())) {
            // No poster available, generate thumbnail
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            bitmap = VideoStore.Video.Thumbnails.getThumbnail(getContentResolver(),mVideoInfo.id, VideoStore.Video.Thumbnails.MINI_KIND, options);
        }
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.widget_default_video);
        }
        metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap);
        mSession.setMetadata(metadataBuilder.build());
    }

    public long getAvailableActions() {
        long availableActions = PlaybackState.ACTION_PLAY;
        if(mPlayer!=null){
            if(mPlayer.isPlaying())
                availableActions =PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP;
        }
        return availableActions;
    }

    public void setAudioDelay(int delay, boolean force) {
        boolean delayChanged = delay != mAudioDelay||force;
        if (delayChanged) {
            mAudioDelay = delay;
            mPlayer.setAvDelay(mAudioDelay * -1); // change sign because we want an audio delay
        }
    }

    protected float mAudioSpeedStep = 0.05f;
    protected float mAudioSpeedMin = 0.50f;
    protected float mAudioSpeedMax = 2.0f;
    private final float epsilon = 1e-5f;

    public void incrementAudioSpeed() {
        float modulo = (mAudioSpeed + mAudioSpeedStep) % mAudioSpeedStep;
        if (Math.abs(Math.abs(modulo) - mAudioSpeedStep) < epsilon)
            modulo = 0;
        float speed = (float)(mAudioSpeed + mAudioSpeedStep - modulo);
        setAudioSpeed(speed > mAudioSpeedMax ? mAudioSpeedMax : speed, false);
    }

    public void decrementAudioSpeed() {
        float modulo = (mAudioSpeed - mAudioSpeedStep) % mAudioSpeedStep;
        if (Math.abs(Math.abs(modulo) - mAudioSpeedStep) < epsilon)
            modulo = 0;
        float speed = (float)(mAudioSpeed - mAudioSpeedStep - modulo);
        setAudioSpeed(speed < mAudioSpeedMin ? mAudioSpeedMin : speed, false);
    }

    public void setAudioSpeed(float speed, boolean force) {
        boolean speedChanged = speed != mAudioSpeed || force;
        if (speedChanged && VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences) &&
                speed > 0.45f && speed < 2.05f) { // min granularity is 0.05
            if (log.isDebugEnabled()) log.debug("setAudioSpeed: audio speed changed from {} to {}", mAudioSpeed, speed);
            mAudioSpeed = speed;
            if ((AUDIO_SPEED_ON_THE_FLY && VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences)) || force) {
                mPlayer.setAvSpeed(mAudioSpeed);
            }
        }
        if (!VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences)) {
            if (log.isDebugEnabled()) log.debug("setAudioSpeed does nothing coz audio speed disabled");
            mAudioSpeed = 1.0f;
        }
    }

    public int getAudioDelay() {
        return mAudioDelay;
    }

    public float getAudioSpeed() { // no audio_speed if audio speed disabled
        if (VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences)) {
            if (log.isDebugEnabled()) log.debug("getAudioSpeed: {}", mAudioSpeed);
            return mAudioSpeed;
        } else {
            if (log.isDebugEnabled()) log.debug("getAudioSpeed: {}", 1.0f);
            return 1.0f;
        }
    }

    @Override
    public void onAudioSpeedApplied(float appliedSpeed) {
        if (Math.abs(appliedSpeed - mAudioSpeed) > 1e-5f) {
            if (log.isWarnEnabled()) log.warn("onAudioSpeedApplied: native applied {} instead of requested {}",
                    appliedSpeed, mAudioSpeed);
        } else if (log.isDebugEnabled()) {
            log.debug("onAudioSpeedApplied: {}", appliedSpeed);
        }
        mAudioSpeed = appliedSpeed;
    }

    public float getAudioSpeedFromPreferences() { // no audio_speed if audio speed disabled
        if (VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences)) {
            if (log.isDebugEnabled()) log.debug("getAudioSpeedFromPreferences: {}", mPreferences.getFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f));
            return mPreferences.getFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f);
        } else {
            if (log.isDebugEnabled()) log.debug("getAudioSpeedFromPreferences: {}", 1.0f);
            return 1.0f;
        }
    }

    public void setAudioFilt(int which) {
        int newAudioFilt = which; // Caution here, audiofilt values must be [0,n[
        if (newAudioFilt != mAudioFilt) {
            mPreferences.edit()
                    .putInt(KEY_AUDIO_FILT, newAudioFilt)
                    .apply(); // commit is blocking.. avoid!
            mAudioFilt = newAudioFilt;
            setAudioFilt();
        }
    }

    public void setNightMode(boolean enable) {
        boolean newNightMode = enable;
        if (mNightModeOn != newNightMode) {
            mPreferences.edit()
                    .putBoolean(KEY_AUDIO_FILT_NIGHT, newNightMode)
                    .apply(); // commit is blocking.. avoid!
            mNightModeOn = newNightMode;
            setAudioFilt();
        }
    }

    public void setAudioFilt() {
        mPlayer.setAudioFilter(mAudioFilt, mNightModeOn);
    }

    public void setPlayOnResume(boolean playOnResume) {
        if (log.isDebugEnabled()) log.debug("setPlayOnResume: {}", playOnResume);
        mPlayOnResume = playOnResume;
    }

    public boolean isPlayOnResume() {
        return mPlayOnResume;
    }

    public static void pausePlayer() {
        if (Player.sPlayer != null && Player.sPlayer.isPlaying()) Player.sPlayer.pause(PlayerController.STATE_NORMAL);
    }

    public static void playPausePlayer() {
        if (Player.sPlayer != null) {
            if (Player.sPlayer.isPlaying())
                Player.sPlayer.pause(PlayerController.STATE_NORMAL);
            else if (Player.sPlayer.isPaused())
                Player.sPlayer.start(PlayerController.STATE_NORMAL);
        }
    }

    public static void startPlayer() {
        if (Player.sPlayer != null && Player.sPlayer.isPaused()) Player.sPlayer.start(PlayerController.STATE_NORMAL);
    }

    // Pause when wired headset is disconnected
    private final BroadcastReceiver headsetPluggedReceiver = new BroadcastReceiver() {
        private static final int UNPLUGGED = 0;
        private static final int PLUGGED = 1;
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isInitialStickyBroadcast()) {
                // intent received just after started reflects only the current state do not process it
                return;
            }
            if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                if (log.isDebugEnabled()) log.debug("headsetPluggedReceiver: headset plug event: {}", state);
                if (state != -1) {
                    if (state == UNPLUGGED) {
                        if (log.isDebugEnabled()) log.debug("headsetPluggedReceiver: headset unplugged during playback");
                        if (mPlayer != null && mPlayer.isPlaying()) mPlayer.pause(PlayerController.STATE_NORMAL);
                    } else if (state == PLUGGED) {
                        if (log.isDebugEnabled()) log.debug("headsetPluggedReceiver: headset plugged during playback");
                    }
                } else {
                    log.error("headsetPluggedReceiver: received invalid ACTION_HEADSET_PLUG intent");
                }
            }
        }
    };
}
