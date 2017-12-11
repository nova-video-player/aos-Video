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

package com.archos.mediacenter.video.player.cast;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.widget.Toast;

import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.info.SingleVideoLoader;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.player.cast.subconverter.IOClass;
import com.archos.mediacenter.video.player.cast.subconverter.subtitleFile.FormatSRT;
import com.archos.mediacenter.video.player.cast.subconverter.subtitleFile.TimedTextObject;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediascraper.ScrapeDetailResult;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastRemoteDisplayLocalService;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.common.images.WebImage;
import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CastService extends Service implements CastPlayerService.RemoteInterface, CastPlayerService.OnDestroyListener, ArchosVideoCastManager.ArchosCastManagerListener, IndexHelper.Listener {

    public static final String SHARED_ELEMENT_NAME = "poster";
    public boolean mReconnect;
    public static final String EXTRA_VIDEO = "VIDEO";
    public static final String EXTRA_DEVICE = "device";
    private static final String TAG = "CastActivity";
    private static final int LOADER_INDEX = 12;
    private static final String ACTION_PLAY_PAUSE = "ACTION_PLAY_PAUSE";
    private GoogleApiClient mApiClient;
    private GoogleApiClient.ConnectionCallbacks mConnectionCallbacks;
    private Cast.Listener mCastClientListener;
    private CastDevice device;
    private Uri contentUri;
    private Uri streamingUri;
    public STATE mState;
    private VideoTask mVideoTask;
    private Intent mIntent;
    private boolean mStartOnConnect;
    public boolean mStartFallback;
    private BroadcastReceiver mBroadcastReceiver;
    public static CastService sCastService;
    private String mType;
    private IndexHelper mIndexHelper;
    private VideoDbInfo mVideoDB;
    private boolean mForceLocalResume;
    private StreamOverHttp mStreamServer;
    private AsyncTask<Object, Void, List<MediaTrack>> mReloadSubsTask;

    @Override
    public void updateUI() {
        if(ArchosVideoCastManager.getInstance().isConnected()){

            if(ArchosVideoCastManager.getInstance().isRemoteDisplayConnected()){
                //we don't update notification by ourself, giving it to player cast service
                if(CastPlayerService.sCastPlayerService!=null)
                    CastPlayerService.sCastPlayerService.startForeground(R.id.cast_notification_id, getNotification());
            }
            else if(VideoCastManager.getInstance().isConnected()){
                startForeground(R.id.cast_notification_id, getNotification());
            }
        }
    }

    @Override
    public void switchCastMode() {

    }

    @Override
    public void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo) {
        int resume = getIntent().getIntExtra(PlayerService.RESUME, PlayerService.RESUME_NO);
        if(resume==PlayerService.RESUME_FROM_REMOTE_POS){
            mVideoDB = remoteInfo;
        }
        else
            mVideoDB = info;
        //start video
        mVideoTask = new VideoTask();
        startForeground(R.id.cast_notification_id, getNotification());
        //loader need to be created in ui thread
        Long id = null;
        if (getIntent().getData() != null && "content".equals(getIntent().getData().getScheme())) {
            id = Long.parseLong(getIntent().getData().getLastPathSegment());
        }
        //replacing Uri in intent like PlayerService does
        if(mVideoDB!=null)
            getIntent().setData(mVideoDB.uri);


        if (id != null)
            mVideoTask.execute(getIntent(), new SingleVideoLoader(CastService.this, id));
        else
            mVideoTask.execute(getIntent(), new SingleVideoLoader(CastService.this, getIntent().getData().toString()));

    }

    @Override
    public void onScraped(ScrapeDetailResult result) {}


    enum STATE{
        READY,
        PLAYING,
        LOADING,
        PREPARING_SUBS,
        ERROR
    }

    private android.os.Handler mHandler = new android.os.Handler();
    private VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {


        @Override
        public void onFailed(int resourceId, int statusCode) {

            String reason = "Not Available";
            if(CastDebug.DBG) {
                CastDebug.getInstance(CastService.this).logFailure("onFailed is called");
                CastDebug.log("onFailed is called");
            }
            mVideoDB = null;
            if (resourceId > 0) {
                reason = getString(resourceId);
            }
            Log.d(TAG, "Action failed, reason:  " + reason + ", status code: " + statusCode);
            if(!mStartFallback)
                switchToFallback(false);


        }

        @Override
        public void onMediaLoadResult(int statusCode) {
            Log.d(TAG, "onMediaLoadResult " + statusCode);
            if(statusCode == 2100){
                if(!mStartFallback)
                    switchToFallback(false);
            }
            else {
                mState = STATE.PLAYING;
                ArchosVideoCastManager.getInstance().setIsSwitching(false);
            }
        }
        @Override
        public void onDeviceSelected(CastDevice device, MediaRouter.RouteInfo routeInfo) {}
        @Override
        public void onApplicationConnected(ApplicationMetadata appMetadata, String sessionId,
                                           boolean wasLaunched) {
            Log.d(TAG, "onApplicationConnected() was called");
            //start video on chromecast when app connects
            if  (mStartOnConnect){
                mHandler.post(new Runnable() {
                    @Override
                    public void run(){
                        startVideo();
                    }
                });
                mStartOnConnect = false;
            }
            startForeground(R.id.cast_notification_id, getNotification());
        }
        @Override
        public void onRemoteMediaPlayerMetadataUpdated() {
            //update notification
            if(VideoCastManager.getInstance().isConnected()&&!(VideoCastManager.getInstance().getPlaybackStatus()==MediaStatus.PLAYER_STATE_IDLE&&
                    (VideoCastManager.getInstance().getIdleReason()==MediaStatus.IDLE_REASON_INTERRUPTED
                            ||VideoCastManager.getInstance().getIdleReason()==MediaStatus.IDLE_REASON_FINISHED))) {
                startForeground(R.id.cast_notification_id, getNotification());
            }
        }


        @Override
        public void onDisconnected() {
            mVideoDB = null;
            ArchosVideoCastManager.getInstance().stopProgressTimer();
            CastDebug.log("stream cast : onDisconnected()");
            if(mStartFallback){
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onFailed2();
                    }
                },1000); //delay to avoid crash on nvidia shield :(
                mStartFallback = false;

            }
            else {
                CastDebug.log("stopping cast service");
                stopSelf();
            }
        }
        @Override
        public void onRemoteMediaPlayerStatusUpdated() {
            //update notification

           //save position
            if(VideoCastManager.getInstance().getPlaybackStatus()==MediaStatus.PLAYER_STATE_IDLE&&
                       (VideoCastManager.getInstance().getIdleReason()==MediaStatus.IDLE_REASON_INTERRUPTED
                            ||VideoCastManager.getInstance().getIdleReason()==MediaStatus.IDLE_REASON_FINISHED)) {
                saveVideoState(VideoCastManager.getInstance().getPlaybackStatus() == MediaStatus.IDLE_REASON_FINISHED);
                stopForeground(true);
            }else {
                //restart progress timer otherwise won't update when app was already connected before
                ArchosVideoCastManager.getInstance().restartProgressTimer();
                startForeground(R.id.cast_notification_id, getNotification());
            }

        }

        @Override
        public void onCastAvailabilityChanged(boolean castPresent) {
           CastDebug.log("onCastAvailabilityChanged "+castPresent);
        }
    };

    public void switchToFallback(boolean isManualSwitch) {
        Log.d(TAG,"switchToFallback "+String.valueOf(isManualSwitch));
        CastDebug.log("switchToFallback("+String.valueOf(isManualSwitch)+")");
        mStartFallback = true;
        mForceLocalResume = isManualSwitch; //when manual switch, resume from last position
        if(isManualSwitch) {
            saveVideoState(false);

        }else{
            Toast.makeText(this, R.string.cast_fallback_message, Toast.LENGTH_LONG).show();
        }
        ArchosVideoCastManager.getInstance().setIsSwitching(true);
        new android.os.Handler().post(new Runnable() {
            @Override
            public void run() {
                VideoCastManager.getInstance().disconnect();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent,int flags, int startID){
        int ret = super.onStartCommand(intent,flags, startID);
        Log.d(TAG,"onStartCommand");
        mIntent = intent;
        //save type because can be lost in PlayerService with setData.
        mType = intent.getType();
        startVideoCast(false);
        return ret;
    }
    public void onCreate(){
        super.onCreate();
        sCastService = this;
        final VideoCastManager castManager = VideoCastManager.getInstance();
        mReconnect = true;
        if (null != castManager) {
            castManager.addVideoCastConsumer(mCastConsumer);
            castManager.incrementUiCounter();
        }
        ArchosVideoCastManager.getInstance().addArchosCastManagerListener(this);
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(ACTION_PLAY_PAUSE.equals(intent.getAction())){

                        try {
                            if(ArchosVideoCastManager.getInstance().isRemoteMediaPaused())
                                ArchosVideoCastManager.getInstance().play();
                            else if(ArchosVideoCastManager.getInstance().isRemoteMediaPlaying())
                                ArchosVideoCastManager.getInstance().pause();
                        } catch (CastException e) {
                            e.printStackTrace();
                        } catch (TransientNetworkDisconnectionException e) {
                            e.printStackTrace();
                        } catch (NoConnectionException e) {
                            e.printStackTrace();
                        }
                }

            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY_PAUSE);
        registerReceiver(mBroadcastReceiver, filter);

    }
    public void startVideoCast(boolean isManualSwitch) {
        CastDebug.log("startVideoCast");
        if(isManualSwitch)
            mForceLocalResume = true;
        if(mVideoTask !=null)
            mVideoTask.cancel(true);
        mVideoTask = null;
        if(!VideoCastManager.getInstance().isConnected()&&ArchosVideoCastManager.getInstance().getSelectedRoute()!=null){
            mStartOnConnect = true;
            if(mApiClient!=null&&mApiClient.isConnected()) {
                CastDebug.log("disconnecting remote display");
                Log.d(TAG, "disconnecting remote display");
                ArchosVideoCastManager.getInstance().setIsSwitching(true);
                ArchosVideoCastManager.getInstance().stopProgressTimer();
                CastRemoteDisplayLocalService.stopService();
            }
            else
                connectVideoPlayer();
        }else {
            startVideo();
        }
    }

    private void connectVideoPlayer() {
        CastDebug.log("connectVideoPlayer");
        resetApiClient();
        VideoCastManager.getInstance().getPreferenceAccessor().saveStringToPreference(
                BaseCastManager.PREFS_KEY_ROUTE_ID, ArchosVideoCastManager.getInstance().getSelectedRoute().getId());
        VideoCastManager.getInstance().onDeviceSelected(ArchosVideoCastManager.getInstance().getSelectedDevice(),ArchosVideoCastManager.getInstance().getSelectedRoute());
        setRoute();
    }

    private Intent getIntent() {
        return mIntent;
    }

    private void startVideo() {
        CastDebug.log("startVideo from castService");
        ArchosVideoCastManager.getInstance().setIsPreparing(true);
        if(mVideoDB != null) {
            saveVideoState(false);
        }
        mVideoDB = null;
        if(mVideoTask !=null)
            mVideoTask.cancel(true);
        //retrieve video info
        mIndexHelper = getIndexHelper();
        CastDebug.log("requestVideoDb");
        mIndexHelper.requestVideoDb((Uri)getIntent().getData(),-1, null, this, false, true);

    }

    private void saveVideoState(boolean hasCompleted) {
        if(mVideoDB != null&&mIndexHelper!=null&&!PrivateMode.isActive()){
            long lastPosition = 0;
                try {
                    lastPosition = VideoCastManager.getInstance().getCurrentMediaPosition();
                    if(lastPosition==0&&hasCompleted){
                        lastPosition = -2;
                    }
                    mVideoDB.resume = (int) lastPosition;
                    mIndexHelper.writeVideoInfo(mVideoDB, true);
                } catch (TransientNetworkDisconnectionException e) {
                    e.printStackTrace();
                } catch (NoConnectionException e) {
                    e.printStackTrace();
                }


        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        TorrentObserverService.staticExitProcess();
        TorrentObserverService.killProcess();
        VideoCastManager castManager = VideoCastManager.getInstance();
        castManager.removeBaseCastConsumer(mCastConsumer);
        ArchosVideoCastManager.getInstance().removeArchosCastManagerListener(this);
        unregisterReceiver(mBroadcastReceiver);


    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public IndexHelper getIndexHelper() {
        return new IndexHelper(getApplicationContext(), null, 2);
    }

    public void setRoute(){

        try {
            Field f = MediaRouter.class.getDeclaredField("sGlobal");
            f.setAccessible(true);
            if(f.isAccessible()){
                // Object obj
                Object obj = f.get(MediaRouter.getInstance(CastService.this));
                Field f2 = obj.getClass().getDeclaredField("mSelectedRoute");

                f2.setAccessible(true);
                f2.set(obj,ArchosVideoCastManager.getInstance().getSelectedRoute());
            }
        } catch (NoSuchFieldException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SecurityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }



    public void resetApiClient(){
        try {
            Field f = BaseCastManager.class.getDeclaredField("mApiClient");
            f.setAccessible(true);
            if(f.isAccessible()){
                 f.set(VideoCastManager.getInstance(), null);
            }
        } catch (NoSuchFieldException e) {
        } catch (SecurityException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }

    }
    public void onFailed2() {
        CastDebug.log("starting remote display mode");
        MediaInfo current = ArchosVideoCastManager.getInstance().getMediaInfo();
        //reset subtracks and audiotracks : will be stup in CastPlayerService
        current.getMediaTracks().clear();

        device = ArchosVideoCastManager.getInstance().getSelectedDevice();
        //CastHelper.getInstance().appId = getString(R.string.app_id_surface);

        mConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
            public boolean mWaitingForReconnect;

            @Override
            public void onConnected(Bundle connectionHint) {

                try {
                    Cast.CastApi.launchApplication(mApiClient,getString(R.string.app_id_surface), false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            if (status.isSuccess()) {

                                                setRoute();
                                            }
                                        }
                                    });

                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch application", e);
                }

            }

            @Override
            public void onConnectionSuspended(int cause) {
                if(CastDebug.DBG){
                    CastDebug.log("screen cast onConnectionSuspended cause "+(cause));

                }
                mWaitingForReconnect = true;
                ArchosVideoCastManager.getInstance().setIsRemoteDisplayConnected(false);
            }
        };
        mCastClientListener = new Cast.Listener(){

        };
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(device, mCastClientListener);

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(mConnectionCallbacks)
                .build();

        mApiClient.connect();


        Intent intent = new Intent(this,
                MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent notificationPendingIntent = PendingIntent.getActivity(
                this, 0, intent, 0);

        CastRemoteDisplayLocalService.NotificationSettings settings =
                new CastRemoteDisplayLocalService.NotificationSettings.Builder()
                        .setNotification(getNotification()) //notification is here managed by android
                        .build();

        CastRemoteDisplayLocalService.startService(
                this.getApplicationContext(),
                CastPlayerService.class, getString(R.string.app_id_surface),
                device, settings,
                new CastRemoteDisplayLocalService.Callbacks() {
                    @Override
                    public void onServiceCreated(CastRemoteDisplayLocalService castRemoteDisplayLocalService) {
                        Log.d(TAG,"onServiceCreated ");
                        ((CastPlayerService)castRemoteDisplayLocalService).registerRemoteInterface(CastService.this);
                        //correct resume if we need to force when manually switching
                        if(mForceLocalResume){
                            getIntent().putExtra(PlayerService.RESUME, PlayerService.RESUME_FROM_LOCAL_POS);
                            mForceLocalResume = false;
                        }
                        ((CastPlayerService)castRemoteDisplayLocalService).forceIntent(getIntent());
                        ArchosVideoCastManager.getInstance().restartProgressTimer();
                        ((CastPlayerService)castRemoteDisplayLocalService).setOnDestroyedListener(CastService.this);
                    }

                    @Override
                    public void onRemoteDisplaySessionStarted(
                            CastRemoteDisplayLocalService service) {
                        // initialize sender UI
                        Log.d(TAG,"onRemoteDisplaySessionStarted ");
                        setRoute();
                        ArchosVideoCastManager.getInstance().setIsRemoteDisplayConnected(true);
                    }

                    @Override
                    public void onRemoteDisplaySessionError(
                            Status errorReason){
                        Log.d(TAG,"error : "+errorReason);
                        ArchosVideoCastManager.getInstance().setIsRemoteDisplayConnected(false);

                    }
                });



    }

    private Notification getNotification() {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle(getString(R.string.ccl_casting_to_device, ArchosVideoCastManager.getInstance().getDeviceName()));
        builder.setSmallIcon(R.drawable.video2);
        Intent playerActivityIntent = ArchosVideoCastManager.getInstance().getPlayerActivityIntent(this);
        PendingIntent startActivity = PendingIntent.getActivity(this,0,playerActivityIntent ,PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(startActivity);
        try {
            Notification.Action.Builder action1;
            PendingIntent playPause = PendingIntent.getBroadcast(this,0, new Intent(ACTION_PLAY_PAUSE),0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if(ArchosVideoCastManager.getInstance().isRemoteMediaPlaying())
                    builder.addAction(R.drawable.video_pause_selector, getString(R.string.floating_player_pause), playPause) ;
                else if(ArchosVideoCastManager.getInstance().isRemoteMediaPaused())
                    builder.addAction(R.drawable.video_play_selector, getString(R.string.floating_player_play), playPause);
            }
        } catch (TransientNetworkDisconnectionException e) {
            e.printStackTrace();
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            return builder.build();
        }
        else
            return builder.getNotification();
    }

    @Override
    public void onServiceDestroy() {
        CastDebug.log("remote service destroyed : onServiceDestroy");
        if(!mStartFallback)
            stopForeground(true);
        ArchosVideoCastManager.getInstance().setIsRemoteDisplayConnected(false);
        new android.os.Handler().post(new Runnable() {
            @Override
            public void run() {
                if(ArchosVideoCastManager.getInstance().shouldKeepConnection()) {
                    mVideoDB = null;
                    connectVideoPlayer();
                    if  (!mStartOnConnect&&CastDebug.DBG)
                        CastDebug.getInstance(CastService.this).goToNextVideo();
                }
            }
        });
    }

    private class Result{
        boolean isSupported;
        MediaInfo mediaInfo;
        int resume=0;
    }
    private class VideoTask extends AsyncTask<Object,Void, Result> {
        private Intent mIntent;
        private SingleVideoLoader mVideoLoader;
        protected void onPostExecute(Result result) {

            if(result!=null)
            {

                try {
                    CastDebug.log("loading media");
                    ArchosVideoCastManager.getInstance().setMediaInfo(result.mediaInfo);
                    ArchosVideoCastManager.getInstance().setIsPreparing(false);
                    CastDebug.is_media_loading = true;
                    if(result.isSupported)
                        VideoCastManager.getInstance().loadMedia(result.mediaInfo, true, result.resume);
                    else
                        switchToFallback(false);
                    mState = STATE.LOADING;
                } catch (TransientNetworkDisconnectionException e) {
                    CastDebug.log("TransientNetworkDisconnectionException");
                    e.printStackTrace();

                } catch (NoConnectionException e) {
                    CastDebug.log("NoConnectionException");

                    e.printStackTrace();
                }
            }

        }
        private Intent getIntent(){
            return mIntent;
        }
        @Override
        protected Result doInBackground(Object... objects) {
            mIntent = (Intent) objects[0];
            if(objects.length>1)
            mVideoLoader = (SingleVideoLoader) objects[1];
            Uri fileUri = getIntent().getParcelableExtra(PlayerService.KEY_STREAMING_URI);
            if(fileUri==null)
                fileUri = getIntent().getData();
            streamingUri = fileUri;

            Log.d(TAG, "fileuri = " +fileUri );

            CastDebug.log("starting video task with file "+streamingUri);
            String title = fileUri.getLastPathSegment();
            Uri posterLocalUri = null;
            String posterStreamingUri = null;
            int position = 0;
            Cursor c = mVideoLoader.loadInBackground();
            String ipAddress = SambaDiscovery.getLocalIpAddress();
            if(ipAddress==null)
                return null;
            if(c.getCount()>0){
                VideoCursorMapper cursorMapper = new VideoCursorMapper();
                cursorMapper.publicBindColumns(c);
                c.moveToFirst();
                Video video = (Video) cursorMapper.publicBind(c);
                if(video!=null){
                    title = video.getName();
                    posterLocalUri = video.getPosterUri();
                }
            }
            if(getIntent().getIntExtra(PlayerService.RESUME, PlayerService.RESUME_NO)!=PlayerService.RESUME_NO||mForceLocalResume) {
                position = mVideoDB.resume;
                mForceLocalResume = false;
            }
            List<MediaTrack> tracks = new ArrayList<>();
             try {
                    mStreamServer = new StreamOverHttp(fileUri, "");
                    mStreamServer.setLocalSubFolder(com.archos.mediacenter.utils.Utils.getSubsDir(CastService.this).getAbsolutePath());
                    if(!"upnp".equals(fileUri.getScheme())&&!"https".equals(fileUri.getScheme())&&!"http".equals(fileUri.getScheme()))
                        contentUri = mStreamServer.getUri(fileUri.getLastPathSegment());
                    else if(streamingUri!=null&&!"upnp".equals(streamingUri.getScheme())){ //when upnp, try to open streamingUri
                        contentUri = streamingUri;
                    }

                    posterStreamingUri= mStreamServer.setPosterUri(posterLocalUri,R.drawable.filetype_new_video).toString();
                    mState = STATE.PREPARING_SUBS;
                    tracks = prepareSubs(fileUri, mStreamServer, ipAddress);

                } catch (IOException e) {
                    Log.e(TAG, "Failed to start " + fileUri + e);
                }

            if (ipAddress != null) {
                Log.d(TAG, "content uri is "+ contentUri);
                String castUri = contentUri.toString();
                if(!"upnp".equals(fileUri.getScheme()))
                  castUri = contentUri.toString().replace("localhost", ipAddress);
                VideoMetadata videoMetaData = new VideoMetadata(castUri.toString());
                videoMetaData.fillFromRetriever(CastService.this);
                boolean isSupported=false;
                if(CastSupportedFormats.isVideoSupported(videoMetaData.getVideoTrack().format)){
                    for(int i = 0; i<videoMetaData.getAudioTrackNb();i++){
                        if(CastSupportedFormats.isAudioSupported(videoMetaData.getAudioTrack(i).format)){
                            isSupported = true;
                            break;
                        }
                    }
                }
                               Log.d(TAG, "cast uri is "+ castUri);
                MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
                if(posterStreamingUri!=null) {
                    metadata.addImage(new WebImage(Uri.parse(posterStreamingUri.replace("localhost", ipAddress))));
                    metadata.addImage(new WebImage(Uri.parse(posterStreamingUri.replace("localhost", ipAddress))));
                    if(posterLocalUri!=null)
                        metadata.putString(ArchosVideoCastManager.POSTER_PATH,posterLocalUri.toString());
                }
                metadata.putString(MediaMetadata.KEY_TITLE,title);
                CastDebug.log("creating mediainfo with castUri "+castUri);

                MediaInfo mediaInfo = new MediaInfo.Builder(castUri)
                        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                        .setContentType(mType!=null?mType:"video/*")
                        .setMetadata(metadata)
                        .setMediaTracks(tracks)
                        .build();
                Result result = new Result();
                result.isSupported = isSupported;
                result.resume = position;
                result.mediaInfo = mediaInfo;
                return result;
            }
            return null;
        }
    }
    //call only in stream mode, will reload list of subs
    public void reloadSubs(){
        if(mStreamServer!=null) {
        if(mReloadSubsTask!=null)
            mReloadSubsTask.cancel(true);
        mReloadSubsTask = new  AsyncTask<Object,Void, List<MediaTrack> >() {

            protected void onPostExecute(List<MediaTrack> tracks) {
                if (tracks != null) {
                    try {
                        int initialSize = ArchosVideoCastManager.getInstance().getRemoteMediaInformation().getMediaTracks().size();
                        long resume  = ArchosVideoCastManager.getInstance().getCurrentMediaPosition();
                        if(initialSize!=tracks.size()) {
                            ArchosVideoCastManager.getInstance().getRemoteMediaInformation().getMediaTracks().clear();
                            ArchosVideoCastManager.getInstance().getRemoteMediaInformation().getMediaTracks().addAll(tracks);
                            VideoCastManager.getInstance().loadMedia(ArchosVideoCastManager.getInstance().getRemoteMediaInformation(), true, (int)resume);
                        }
                    } catch (TransientNetworkDisconnectionException e) {
                        e.printStackTrace();
                    } catch (NoConnectionException e) {
                        e.printStackTrace();
                    }
                }

            }

            @Override
            protected List<MediaTrack>  doInBackground(Object... objects) {
                String ipAddress = SambaDiscovery.getLocalIpAddress();
                if(ipAddress==null)
                    return null;
                List<MediaTrack> tracks = prepareSubs(streamingUri, mStreamServer,ipAddress);
                return tracks;
            }
        };
        mReloadSubsTask.execute();
        }
    }

    private List<MediaTrack> prepareSubs(Uri fileUri, StreamOverHttp streamServer, String ipAddress) {
        List<MediaTrack> tracks = new ArrayList<>();

        SubtitleManager subtitleManager = new SubtitleManager(CastService.this, new SubtitleManager.Listener() {
            @Override
            public void onAbort() {                    }
            @Override
            public void onError(Uri uri, Exception e) {                   }
            @Override
            public void onSuccess(Uri uri) {                    }
            @Override
            public void onNoSubtitlesFound(Uri uri) {                    }
        });
        List<SubtitleManager.SubtitleFile> subs = subtitleManager.listLocalAndRemotesSubtitles(fileUri);
        if(subs!=null){
            int i= 0;
            for(SubtitleManager.SubtitleFile file : subs){
                if(file.mFile.getExtension().equals("srt")){
                    boolean shouldConvert = true;
                    boolean shouldAdd = true;
                    for(SubtitleManager.SubtitleFile file2 : subs){

                        if(file2.mFile.getName().equals(file.mFile.getNameWithoutExtension()+".xml")){
                            //no need to convert
                            shouldConvert = false;
                            break;
                        }
                    }
                    if(shouldConvert){
                        try {
                            Log.d(TAG, "converting "+file.mFile.getName());
                            FormatSRT ttff = new FormatSRT();
                            TimedTextObject tto = ttff.parseFile(file.mFile.getName(), file.mFile.getFileEditorInstance(null).getInputStream(), file.mFile.getFileEditorInstance(null).getInputStream());
                            File outfile =new File(com.archos.mediacenter.utils.Utils.getSubsDir(CastService.this).getAbsolutePath(), file.mFile.getNameWithoutExtension()+".xml");
                            IOClass.writeFileTxt(outfile.getAbsolutePath(), tto.toTTML());

                        } catch (Exception e1) {
                            shouldAdd = false;

                        }

                    }
                    if(shouldAdd) {
                        Log.d(TAG, "adding srt " + file.mFile.getName());
                        MediaTrack frenchAudio = new MediaTrack.Builder(i, MediaTrack.TYPE_TEXT)
                                .setName(file.mName)
                                .setContentId(streamServer.getUri(file.mFile.getNameWithoutExtension() + ".xml").toString().replace("localhost", ipAddress))
                                .setLanguage("fr")
                                .setSubtype(MediaTrack.SUBTYPE_CAPTIONS)
                                .setContentType("application/ttml+xml")
                                .build();
                        tracks.add(frenchAudio);
                        i++;
                    }
                }
            }
        }
        return tracks;
    }
}
