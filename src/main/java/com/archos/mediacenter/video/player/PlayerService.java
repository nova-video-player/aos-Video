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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.SystemPropertiesProxy;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.StreamUriFinder;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.medialib.Subtitle;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStoreImportImpl;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.environment.ArchosUtils;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

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



















    public static final String PLAYER_SERVICE_STARTED = "PLAYER_SERVICE_STARTED";

    public static final int RESUME_NO = 0;
    public static final int RESUME_FROM_LAST_POS = 1;
    public static final int RESUME_FROM_BOOKMARK = 2;
    public static final int RESUME_FROM_REMOTE_POS = 3;
    public static final int RESUME_FROM_LOCAL_POS = 4;
    public static final String RESUME = "resume";
    private static final String TAG = "PlayerService";
    private static final boolean DBG = false;
    public static final String PLAY_INTENT = "playerservice.play";
    public static final String PAUSE_INTENT = "playerservice.pause";
    public static final String EXIT_INTENT = "playerservice.exit";
    public static final String FULLSCREEN_INTENT = "playerservice.fullscreen";
    public static final String PLAYLIST_ID = "playlist_id";
    public static final String VIDEO = "extra_video";
    private static final long AUTO_SAVE_INTERVAL = 30000;
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


    public static final int LAST_POSITION_UNKNOWN = -1;
    public static final int LAST_POSITION_END = -2;

    public static final String KEY_ORIGINAL_TORRENT_URL = "original_torrent_uri";

    private static final String KEY_HIDE_SUBTITLES = "subtitles_hide_default";
    private static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";
    private static final String KEY_SUBTITLES_FAVORITE_LANGUAGE = "favSubLang";
    private static final String VIDEO_PLAYER_DEMO_MODE_EXTRA = "demo_mode";
    private boolean mForceSingleRepeatMode;

    private boolean mNetworkBookmarksEnabled;
    private int mLastPosition = -1;
    private boolean mIsChangingSurface;
    private int mResume;
    private boolean firstTimeCalled;
    private boolean mHideSubtitles = false;
    private int mNewSubtitleTrack;
    private boolean mAudioSubtitleNeedUpdate;
    private Intent mIntent;
    public int mPlayMode=0;
    private int mAudioDelay;
    private int mNewAudioTrack;
    public int mAudioFilt;
    public boolean mNightModeOn;
    private static final String KEY_AUDIO_FILT = "pref_audio_filt_int_key"; // used to be "pref_audio_filt_key", containing a string
    private static final String KEY_AUDIO_FILT_NIGHT = "pref_audio_filt_night_int_key";
    private boolean mCallOnDataUriOKWhenVideoInfoIsSet;
    private boolean mIsPreparingSubs;
    private static final int PLAYER_NOTIFICATION_ID = 5;
    private NotificationCompat.Builder mNotificationBuilder;
    private String mSubsFavoriteLanguage;
    private boolean mDestroyed;
    private Runnable mAutoSaveTask;

    public enum PlayerState {
        INIT,
        PREPARING,
        PREPARED,
        PLAYING,
        PAUSED,
        STOPPED,
    }




    /* Torrent */


    private TorrentObserverService mTorrent;
    private int mTorrentFilePosition;
    private String mTorrentURL;
    private ServiceConnection mTorrentObserverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            mTorrent  =  ((TorrentObserverService.TorrentServiceBinder) binder).getService();
            mTorrent. setParameters(mTorrentURL, mTorrentFilePosition);
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
            ;
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
    private void updateNextVideo (boolean repeatFolder, boolean sync) {

        // reset to nothing
        mNextUri = null;
        mNextVideoId = -1;
        if (mUpdateNextTask != null) {
            mUpdateNextTask.cancel(false);
            mUpdateNextTask.setListener(null);
        }
        mUpdateNextTask = new UpdateNextTask(getContentResolver(),(Video)mIntent.getSerializableExtra(VIDEO), mUri, null, -1, mIntent.getLongExtra(PLAYLIST_ID, -1));
        if (!sync) {
            mUpdateNextTask.setListener(new UpdateNextTask.Listener() {
                @Override
                public void onResult(Uri uri, long id) {
                    Log.d(TAG, "onResult: " + uri + ", id: "+id);
                    mNextUri = uri;
                    mNextVideoId = id;
                    mUpdateNextTask = null;
                }
            });
            mUpdateNextTask.execute(repeatFolder);
        } else {
            mUpdateNextTask.execute(repeatFolder);
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
        Log.d(TAG, "new Playmode: " + newPlaymode);
        if (PLAYMODE_REPEAT_SINGLE == newPlaymode || mForceSingleRepeatMode) {
            mPlayer.setLooping(true);
            // just in Case we drop out to OnCompletion
            mNextUri = mUri;
            mNextVideoId = mVideoId;
        } else {
            mPlayer.setLooping(false);
            if (PLAYMODE_SINGLE==newPlaymode) {
                // clear next
                mNextUri = null;
                mNextVideoId = -1;
            } else if (PLAYMODE_FOLDER == newPlaymode) {
                updateNextVideo(false, wait);
            } else if (PLAYMODE_REPEAT_FOLDER == newPlaymode) {
                updateNextVideo(true, wait);
            } else {
                Log.d(TAG, "unknown Playmode: " + newPlaymode);
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
        Log.d(TAG, "onCreate()");
        sPlayerService = this;
        mHandler = new Handler();
        mAutoSaveTask = new Runnable(){

            @Override
            public void run() {
                saveVideoStateIfReady();
                mHandler.postDelayed(mAutoSaveTask, AUTO_SAVE_INTERVAL);
            }
        };
        mVideoObserver = new VideoObserver(new Handler());
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (Trakt.isTraktV2Enabled(this, mPreferences) && !PrivateMode.isActive()) {
            mTraktClient = new TraktService.Client(this, mTraktListener, false);
        }
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
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }
    
    public void onStart(Intent intent) {
        if (intent == null || intent.getData() == null)
            return;

        Log.d(TAG, "onStart() ");
        mCallOnDataUriOKWhenVideoInfoIsSet = true;
        mIntent = intent;
        boolean isDemoMode = (intent.getIntExtra(VIDEO_PLAYER_DEMO_MODE_EXTRA, 0) == 1);
        mNetworkBookmarksEnabled = mPreferences.getBoolean(KEY_NETWORK_BOOKMARKS, true);
        mSubsFavoriteLanguage = mPreferences.getString(KEY_SUBTITLES_FAVORITE_LANGUAGE, Locale.getDefault().getISO3Language());
        mAudioFilt = mPreferences.getInt(KEY_AUDIO_FILT, 0);
        mNightModeOn = mPreferences.getBoolean(KEY_AUDIO_FILT_NIGHT, false);
        mForceSingleRepeatMode = isDemoMode;
        mPlayOnResume = true;
        mHideSubtitles = mPreferences.getBoolean(KEY_HIDE_SUBTITLES, false);
        mPlayMode = mPreferences.getInt(KEY_PLAY_MODE, PLAYMODE_SINGLE);
        mResume = intent.getIntExtra(RESUME, RESUME_NO);
        mUri = intent.getData();
        mTorrentURL = mIntent.getStringExtra(PlayerActivity.KEY_TORRENT_URL);
        if(mIntent.hasExtra(KEY_ORIGINAL_TORRENT_URL)){
            mUri = Uri.parse(mIntent.getStringExtra(KEY_ORIGINAL_TORRENT_URL));
        }
        if(mUri.toString().startsWith("file://")) {
            mUri = Uri.parse(mUri.toString().substring("file://".length())); // we need to remove "file://"
        }
        Log.d(TAG, "onStart() "+mUri);
        mStreamingUri = intent.getParcelableExtra(KEY_STREAMING_URI);
        if(mPlayerFrontend!=null)
            mPlayerFrontend.setUri(mUri, mStreamingUri);
        mVideoId = intent.getIntExtra("id", -1);
        mTorrentFilePosition = mIntent.getIntExtra(PlayerActivity.KEY_TORRENT, -1);

        // when mVideoInfo uri is the same as intent uri -> info has already been retrieved !
        if(mVideoInfo!=null&&mVideoInfo.uri.equals(mUri)){
            mResume = RESUME_FROM_LAST_POS;
            mDatabaseInfoHasBeenRetrieved = true;
        }
        else {
            mVideoInfo = null; //reset info
            mDatabaseInfoHasBeenRetrieved = false;
            mAudioDelay = mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0);
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
                Log.d(TAG, "correctedUri " + correctedUri);
                mUri = correctedUri;
            }
        }
        Log.d(TAG, "mIndexHelper != null " + String.valueOf(mIndexHelper != null));

        if(mIndexHelper!=null&&mVideoInfo==null)
            requestVideoDb();
        else if(mVideoInfo!=null){
            mPlayerFrontend.onVideoDb(mVideoInfo, null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            setNowPlayingCard();
        }
    }

    public Uri getStreamingUri() {
        return mStreamingUri;
    }

    private static final String notifChannelId = "PlayerService_id";
    private static final String notifChannelName = "PlayerService";
    private static final String notifChannelDescr = "PlayerService";
    private NotificationManager notificationManager;
    public void stopStatusbarNotification(){
        stopForeground(true);
    }
    public void startStatusbarNotification(boolean isDicreteOrMinimized) {
        Intent notificationIntent = new Intent("DISPLAY_FLOATING_PLAYER");
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mNotifChannel = new NotificationChannel(notifChannelId, notifChannelName,
                    notificationManager.IMPORTANCE_LOW);
            mNotifChannel.setDescription(notifChannelDescr);
            if (notificationManager != null)
                notificationManager.createNotificationChannel(mNotifChannel);
        }
        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);
        mNotificationBuilder = new NotificationCompat.Builder(this, notifChannelId);
        int icon = R.drawable.video2;
        mNotificationBuilder.setSmallIcon(icon);
        mNotificationBuilder.setTicker(null);
        mNotificationBuilder.setOnlyAlertOnce(true);
        String title = "";
        if(getVideoInfo()!=null&&PlayerService.sPlayerService.getVideoInfo().scraperTitle!=null&&!PlayerService.sPlayerService.getVideoInfo().scraperTitle.isEmpty())
            title = sPlayerService.getVideoInfo().scraperTitle;
        else if(getVideoInfo()!=null&&getVideoInfo().title!=null)
            title = getVideoInfo().title;
        else
            title = FileUtils.getFileNameWithoutExtension(mUri);
        mNotificationBuilder.setContentTitle(getString(R.string.now_playing));
        mNotificationBuilder.setContentText(title);
        mNotificationBuilder.setContentIntent(contentIntent);
        if(mPlayer!=null){
            if(mPlayer.isPlaying())
                mNotificationBuilder.addAction(new NotificationCompat.Action(R.drawable.video_pause, getString(R.string.floating_player_pause), PendingIntent.getBroadcast(this, 0, new Intent(PAUSE_INTENT),0)));
            else if(mPlayer.isInPlaybackState())
                mNotificationBuilder.addAction(new NotificationCompat.Action(R.drawable.video_play, getString(R.string.floating_player_play),  PendingIntent.getBroadcast(this, 0, new Intent(PLAY_INTENT),0)));

        }
        if(isDicreteOrMinimized)
            mNotificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_menu_unfade, getString(R.string.floating_player_restore), contentIntent));
        else
            mNotificationBuilder.addAction(new NotificationCompat.Action(R.drawable.video_format_fullscreen, getString(R.string.format_fullscreen), PendingIntent.getBroadcast(this, 0, new Intent(FULLSCREEN_INTENT),0)));
        mNotificationBuilder.setDeleteIntent(PendingIntent.getBroadcast(this, 0, new Intent(EXIT_INTENT), 0));
        mNotificationBuilder.setOngoing(false);
        mNotificationBuilder.setDefaults(0);
        mNotificationBuilder.setAutoCancel(true);
        Notification notif = mNotificationBuilder.build();

        //notif.bigContentView = new RemoteViews(getPackageName(), R.layout.notification_controls);

        startForeground(PLAYER_NOTIFICATION_ID, notif);
    }


    private int getLastPosition(VideoDbInfo videoInfo, int resume) {
        int lastPosition = 0;
        if (resume != RESUME_NO && videoInfo.lastTimePlayed > 0) {
            if (mResume == RESUME_FROM_LAST_POS || mResume == RESUME_FROM_REMOTE_POS || mResume ==  RESUME_FROM_LOCAL_POS)
                lastPosition = videoInfo.resume;
            else if (mResume == RESUME_FROM_BOOKMARK)
                lastPosition = videoInfo.bookmark;
            if (lastPosition <= 0)
                return 0;
        }
        return lastPosition;
    }

    private void onDataUriOK() {
        if (DBG) Log.d(TAG, "onDataUriOK "+mUri);
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
        if(!mIsPreparingSubs) {
            mIsPreparingSubs = true;
            com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager subtitleManager =
                    new com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager(this, new SubtitleManager.Listener() {
                        @Override
                        public void onAbort() {
                            mIsPreparingSubs = false;
                        }

                        @Override
                        public void onError(Uri uri, Exception e) {
                            mIsPreparingSubs = false;
                        }

                        @Override
                        public void onSuccess(Uri uri) {
                            mIsPreparingSubs = false;
                            if(mUri.equals(uri))
                                mPlayer.checkSubtitles();
                        }

                        @Override
                        public void onNoSubtitlesFound(Uri uri) {
                            mIsPreparingSubs = false;
                        }
                    });
            subtitleManager.preFetchHTTPSubtitlesAndPrepareUpnpSubs(mUri, mStreamingUri);
        }
    }

    private void onStreamingUriOK() {
        Log.d(TAG, "onStreamingUriOK " + mStreamingUri);
        if(mTorrentFilePosition==-1)
            prepareSubs();
        if(mPlayerFrontend!=null)
            mPlayerFrontend.setUri(mUri, mStreamingUri);

        mPlayer.setVideoURI(mStreamingUri, null);
    }

    public void setPlayer(){
        if(mPlayer!=null)
            mPlayer.setListener(null);
        if(Player.sPlayer==null)
            Player.sPlayer = new Player(this, null, null,false);
        Player.sPlayer.setListener(this);
        mPlayer = Player.sPlayer;
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
    }


    public void removePlayerFrontend(PlayerFrontend playerFrontend, boolean prepareForSurfaceSwitch) {
        if(mPlayerFrontend!=playerFrontend)
            return;
        Log.d(TAG, "removePlayerFrontend "+String.valueOf(prepareForSurfaceSwitch));
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

    /**
     *
     * @return player progress in milli when available
     */
    private int getBookmarkPosition() {
        if (mPlayer.getDuration() != 0) {
            /* resume a little before */
            int position = mPlayer.getCurrentPosition();
            return position > 3000 ? position - 3000 : 0;
        } else {
            return mPlayer.getRelativePosition();
        }
    }

    public void saveVideoStateIfReady(){
        if(mIndexHelper!=null) {
            if ((mPlayerState != PlayerState.INIT && mPlayerState != PlayerState.PREPARING)) {// if it has really been played at least once, otherwise it would overwrite lastresume with 0
                Log.d(TAG, "saveVideoStateIfReady");
                if (mLastPosition != LAST_POSITION_END) //if last position, we went there through "onCompletion"
                    mLastPosition = getBookmarkPosition();
                if (mVideoInfo != null && !PrivateMode.isActive()) {
                    mVideoInfo.resume = mLastPosition;
                    int duration = mPlayer.getDuration();
                    if (duration > 0)
                        mVideoInfo.duration = duration;
                    mVideoInfo.lastTimePlayed = Long.valueOf(System.currentTimeMillis() / 1000L);
                    mIndexHelper.writeVideoInfo(mVideoInfo, mNetworkBookmarksEnabled);
                    startTrakt(); //this writes mVideoInfo.traktResume

                    Intent intent = new Intent(BootupRecommandationService.UPDATE_ACTION);
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    sendBroadcast(intent);

                }
            }
        }
    }

    public void stopAndSaveVideoState(){
        Log.d(TAG, "stopAndSaveVideoState");
        if(mIndexHelper!=null) {
            mIndexHelper.abort(); //too late : do not retrieve db info
            saveVideoStateIfReady();
            if ((mPlayerState != PlayerState.INIT && mPlayerState != PlayerState.PREPARING))
                stopTrakt();
            if (mUpdateNextTask != null) {
                mUpdateNextTask.cancel(false);
                mUpdateNextTask.setListener(null);
                mUpdateNextTask = null;
            }
            mHandler.removeCallbacks(mAutoSaveTask);
            mPlayer.pause();
            mPlayer.stopPlayback();
            mPlayerState = PlayerState.STOPPED;
            TorrentObserverService.staticExitProcess();
            TorrentObserverService.killProcess();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
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
        if (DBG) Log.d(TAG, "switchPlayerFrontend " + String.valueOf(mVideoInfo != null));

        if(mVideoInfo!=null)
            playerFrontend.setVideoInfo(mVideoInfo);

        mIsChangingSurface = false;



    }


    /**
     *
     * @return player progress in percentage
     */
    private int getPlayerProgress() {
        if (Player.sPlayer == null || mVideoInfo == null)
            return 0;
        if (mLastPosition == LAST_POSITION_END)
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

    private final Runnable mTraktWatchingRunnable = new Runnable() {
        @Override
        public void run() {
            if (Player.sPlayer != null) {
                mTraktClient.watching(mVideoInfo, getPlayerProgress());
                mHandler.postDelayed(mTraktWatchingRunnable, Trakt.WATCHING_DELAY_MS);
            }
        }
    };

    private void startTrakt() {
        if (mTraktClient != null) {
            mTraktError = false;
            mTraktLiveScrobblingEnabled = Trakt.isLiveScrobblingEnabled(mPreferences);
            if (mTraktLiveScrobblingEnabled) {
                 int progress = getPlayerProgress();
                mVideoInfo.traktResume = -progress;
                mVideoInfo.duration = Player.sPlayer.getDuration();
                mTraktClient.watching(mVideoInfo, progress);
                mHandler.postDelayed(mTraktWatchingRunnable, Trakt.WATCHING_DELAY_MS);
                mTraktWatching = true;
            }
        }
    }

    private void stopTrakt() {
        if (mTraktClient != null) {
            if (mTraktWatching) {
                if (DBG) Log.d(TAG, "Trakt: stopTrakt");
                mHandler.removeCallbacks(mTraktWatchingRunnable);
                int progress = getPlayerProgress();
                if (progress >= 0){
                    mVideoInfo.traktResume = - progress;
                    mTraktClient.watchingStop(mVideoInfo, progress);

                }
                mTraktWatching = false;
            } else if (!mTraktError && Trakt.shouldMarkAsSeen(getPlayerProgress())) {
                mTraktClient.markAs(mVideoInfo, Trakt.ACTION_SEEN);
            }
        }
        // We now use the DB flag ARCHOS_TRAKT_SEEN even if there is no sync with trakt
        else {
            if (mVideoId>=0 && Trakt.shouldMarkAsSeen(getPlayerProgress()) && !PrivateMode.isActive()) {
                final ContentValues cv = new ContentValues(1);
                cv.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_MARKED);
                String where = VideoStore.Video.VideoColumns._ID + " = ?";
                String[] whereArgs = new String[] {Long.toString(mVideoId)};
                getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, where, whereArgs);
            }
        }
    }

    private void pauseTrakt() {
        if (mTraktClient != null) {
            if (mTraktWatching) {
                int progress = getPlayerProgress();
                if (progress > 0) {
                    mVideoInfo.traktResume = - progress;
                    mTraktClient.watchingStop(mVideoInfo, progress);
                }
                mTraktWatching = false;
            }
        }
    }

    private final TraktService.Client.Listener mTraktListener = new TraktService.Client.Listener() {
        @Override
        public void onResult(Trakt.Result result) {
            if (result.status == Trakt.Status.ERROR) {
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

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            && ArchosFeatures.isAndroidTV(PlayerService.this) && !PrivateMode.isActive())
                    updateNowPlayingMetadata();
                    // check if it has been scraped
                }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && ArchosFeatures.isAndroidTV(PlayerService.this) && !PrivateMode.isActive())
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
        mVideoInfo = videoInfo;
        mLastPosition = getLastPosition(mVideoInfo, mResume);
        if (!PrivateMode.isActive()&&mIndexHelper!=null) {
            mVideoInfo.lastTimePlayed = Long.valueOf(System.currentTimeMillis() / 1000L);
            mIndexHelper.writeVideoInfo(mVideoInfo, mNetworkBookmarksEnabled);
        }
        mUri = mVideoInfo.uri;
        mIntent.setData(mUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
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
        if(mIndexHelper!=null)
            mIndexHelper.abort();
        mDestroyed = true;
        sPlayerService=null;
        if(mTorrent!=null)
            try {
                unbindService(mTorrentObserverServiceConnection);
            }catch(java.lang.IllegalArgumentException e) {}
        Log.d(TAG, "onDestroy");

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
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onVideoDb(info, remoteInfo);
        }
    }

    @Override
    public void onScraped(ScrapeDetailResult result) {

    }

    private void postPreparedAndVideoDb() {
        if(mVideoInfo!=null&&mPlayerState==PlayerState.PREPARED) {
            if (mLastPosition == mPlayer.getDuration())
                mLastPosition = 0;
            Player.sPlayer.seekTo(mLastPosition); //mLastPosition = mVideoInfo.resume when first start of service OR position on stop when switching player
            Log.d(TAG, "seek to "+mLastPosition);
            setAudioDelay(mAudioDelay, true);
            if(mPlayOnResume) {
                mPlayerFrontend.onFirstPlay();
                Player.sPlayer.start();
                PlayerService.sPlayerService.mPlayerState = PlayerService.PlayerState.PLAYING;
            }
            if(mAudioSubtitleNeedUpdate){ // when we have info about subs or audio track BEFORE mVideoInfo is set
                onSubtitleMetadataUpdated(mPlayer.getVideoMetadata(), mNewSubtitleTrack);
                onAudioMetadataUpdated(mPlayer.getVideoMetadata(), mNewAudioTrack);
                mAudioSubtitleNeedUpdate = false;
            }
            setPlayMode(mPlayMode, false); //look for next uri
            setAudioFilt();
            mHandler.postDelayed(mAutoSaveTask, AUTO_SAVE_INTERVAL);
        }
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
        Log.d(TAG, "onPrepared()");
        mPlayerState = PlayerState.PREPARED;
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        mLastPosition = LAST_POSITION_END;
        if (mNextUri != null) {
            stopAndSaveVideoState();
            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onCompletion();
            }
            mUri = mNextUri;
            mStreamingUri=mUri;
            mIntent.setData(mUri);
            mIntent.putExtra(KEY_STREAMING_URI, mStreamingUri);
            mIntent.putExtra(RESUME, RESUME_NO);
            mVideoId = mNextVideoId;
            mNextUri = null;
            mNextVideoId = -1;
            mLastPosition = 0;
            onStart(mIntent);

        } else {
            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onEnd();
            }
        }

    }

    @Override
    public boolean onError(int errorCode, int errorQualCode, String msg) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
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
    public void onPlay() {
        startTrakt();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();

        }
        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onPlay();
        }
    }

    @Override
    public void onPause() {
        pauseTrakt();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && ArchosFeatures.isAndroidTV(this) && !PrivateMode.isActive()) {
            updateNowPlayingState();
        }
        if(mPlayerFrontend!=null){
            mPlayerFrontend.onPause();
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
            return;
        }
        int nbTrack = vMetadata.getAudioTrackNb();
        boolean supported = true;
        if (mVideoInfo.audioTrack < 0 || mVideoInfo.audioTrack >= nbTrack 
                || !vMetadata.getAudioTrack(mVideoInfo.audioTrack).supported) {
            for (int i = 0; i < nbTrack; ++i) {
                if (vMetadata.getAudioTrack(i).supported) {
                    mVideoInfo.audioTrack = i;
                    supported = true;
                    break;
                } else if (!vMetadata.getAudioTrack(i).supported)
                    supported = false;
            }
        }
        if (mVideoInfo.audioTrack == -1)
            mVideoInfo.audioTrack = newAudioTrack;

        if (mVideoInfo.audioTrack != newAudioTrack && !mPlayer.setAudioTrack(mVideoInfo.audioTrack))
            supported = false;
        if (nbTrack>0 && !supported) {
            Log.e(TAG, "audio not supported!");
            mVideoInfo.audioTrack = 0;
            VideoMetadata.AudioTrack at = mPlayer.getVideoMetadata().getAudioTrack(0);
            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onAudioError(true, at != null ? at.format : "unknown");
            }

        }

        if(mPlayerFrontend!=null) {
            mPlayerFrontend.onAudioMetadataUpdated(vMetadata, newAudioTrack);
        }
    }

    @Override
    public void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int newSubtitleTrack) {
        if (mVideoInfo == null) {
            mNewSubtitleTrack = newSubtitleTrack;
            mAudioSubtitleNeedUpdate = true;
            return;
        }
        int nbTrack = vMetadata.getSubtitleTrackNb();
        if (nbTrack != 0) {
            // none track
            int noneTrack = nbTrack;
    
            if (mVideoInfo.subtitleTrack == -1) {
                if (mHideSubtitles)
                    mVideoInfo.subtitleTrack = noneTrack;
                else {
                    Locale locale = new Locale(mSubsFavoriteLanguage);
                    for (int i = 0; i < nbTrack; ++i) {
                        if (VideoUtils.getLanguageString(this,vMetadata.getSubtitleTrack(i).name).toString().equalsIgnoreCase(locale.getDisplayLanguage())){
                                mVideoInfo.subtitleTrack = i;
                            break;
                        }
                    }
                    if (mVideoInfo.subtitleTrack == -1)
                        mVideoInfo.subtitleTrack = newSubtitleTrack;

                }
            }


            if (mVideoInfo.subtitleTrack >= 0 && mVideoInfo.subtitleTrack < nbTrack+1) {
                if (newSubtitleTrack != mVideoInfo.subtitleTrack &&
                        !mPlayer.setSubtitleTrack(mVideoInfo.subtitleTrack))
                    mVideoInfo.subtitleTrack = noneTrack;
                    if (DBG) Log.d(TAG, "SubtitleDelay = "+String.valueOf(mVideoInfo.subtitleDelay));
                    mPlayer.setSubtitleDelay(mVideoInfo.subtitleDelay);
                    if (mVideoInfo.subtitleRatio >= 0) {
                        mPlayer.setSubtitleRatio(mVideoInfo.subtitleRatio);

                }
            }


            if(mPlayerFrontend!=null) {
                mPlayerFrontend.onSubtitleMetadataUpdated(vMetadata, newSubtitleTrack);
            }
            firstTimeCalled=false;
        }
        else
            firstTimeCalled = true;
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
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setNowPlayingCard() {
        /**
         * in case our activity has been stopped, relaunch the video
         */
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(mUri);
        intent.setClass(getApplicationContext(), PlayerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 99, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        if (mSession == null) {
            mSession = new MediaSession(this, "PlayerActivity");
            MediaSession.Callback mediaSessionCallback = new  MediaSession.Callback() {
                @Override
                public void onPlay() {
                    super.onPlay();
                    Player.sPlayer.start();
                    updateNowPlayingState();
                }

                @Override
                public void onPause() {
                    super.onPause();
                    Player.sPlayer.pause();
                    updateNowPlayingState();
                }
            };
            mSession.setCallback(mediaSessionCallback);
            mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mSession.setActive(true);
        }

        mSession.setSessionActivity(pi);;
    }

    /**
     * update state of now playing card to play / buffering / stop
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            stopNowPlayingCard();
        }
    }





    /**
     * set now playing card to stop
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopNowPlayingCard() {
        if(mSession==null)
            return;
        if (DBG) Log.d(TAG, "stopNowPlayingCard");

        PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
                .setActions(getAvailableActions());
        stateBuilder.setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f);
        mSession.setPlaybackState(stateBuilder.build());
    }

    /**
     * Update title and pic on now playing card
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updateNowPlayingMetadata() {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        String title = mVideoInfo.scraperTitle!=null?mVideoInfo.scraperTitle:mVideoInfo.title!=null?mVideoInfo.title:FileUtils.getFileNameWithoutExtension(mUri);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                title);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE,title);
        metadataBuilder.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
                mVideoInfo.scraperCover);
        Bitmap bitmap = BitmapFactory.decodeFile(mVideoInfo.scraperCover);
        if (bitmap == null&&mVideoInfo.id >= 0) { //if no scrapped poster, try to get a thumbnail
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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


    public int getAudioDelay() {
        return mAudioDelay;
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

    public static boolean isAudioFilterEnabled() {
        // Only add audio filtering in debug builds or when persist.archos.audiofilter=true
        // (always enabled for now)
        if (true ||
                SystemPropertiesProxy.get("ro.build.type").equals("eng") ||
                SystemPropertiesProxy.get("persist.archos.audiofilter").equals("true")
                ) {
            return true;
        }
        return false;
    }

    public void setAudioFilt() {
        if (isAudioFilterEnabled()) {
            mPlayer.setAudioFilter(mAudioFilt, mNightModeOn);
        }
    }

}
