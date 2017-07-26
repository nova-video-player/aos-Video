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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.Player;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.PlayerController;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.player.SubtitleManager;
import com.archos.mediacenter.video.player.SurfaceController;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.medialib.Subtitle;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastRemoteDisplayLocalService;
import com.google.android.gms.cast.MediaTrack;

/**
 * Created by alexandre on 27/08/15.
 */
public class CastPlayerService extends CastRemoteDisplayLocalService implements AppState.OnForeGroundListener, PlayerService.PlayerFrontend, FirstScreenPresentation.OnCreateCallback {

    private static final int MSG_TORRENT_UPDATE = 2;
    private static final int MSG_HIDE_CONTROLLER = 3;
    private static final String TAG = "CastPlayerService";

    private WindowManager mWindowManager;
    public static CastPlayerService sCastPlayerService;
    private boolean contains;
    private SurfaceController mSurfaceController;
    private int mSubtitleSizeDefault;
    private int mSubtitleVPosDefault;
    private WindowManager.LayoutParams mParamsF;
    private AudioManager mAudioManager;


    private boolean isServiceConnected;
    private ServiceConnection mPlayerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isServiceConnected = true;
            Log.d(TAG,"onServiceConnected");

            onFirstScreenPresentationCreatedAndServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    private BroadcastReceiver mReceiver;
    private FirstScreenPresentation mFirstScreenPresentation;
    private Intent mIntent;
    private Display mDisplay;
    private RemoteInterface mRemoteInterface;
    private View mProgressView;
    private View mRootView;
    private MediaRouter mMediaRouter;
    private MediaRouter.Callback mMediaRouterCallback;
    private OnDestroyListener mOnDestroyListener;
    private SubtitleManager mSubtitleManager;
    private int mSubtitleColorDefault;
    private View mBackgroundWarning;

    public SubtitleManager getSubtitleManager() {
        return mSubtitleManager;
    }

    public SurfaceController getSurfaceManager() {
        return mSurfaceController;
    }

    public interface OnDestroyListener{
        void onServiceDestroy();
    }

    public void onCreate() {
        super.onCreate();

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());

        mMediaRouterCallback = new MediaRouter.Callback (){

            public void onRoutePresentationDisplayChanged(MediaRouter router, MediaRouter.RouteInfo route) {
                Log.d(TAG,"onRoutePresentationDisplayChanged ");
                if(CastPlayerService.sCastPlayerService !=null&&route.getPresentationDisplay()!=null)
                    CastPlayerService.sCastPlayerService.createDisplayContext(route.getPresentationDisplay());
            }

            @Override
            public void onRouteSelected(MediaRouter router, final MediaRouter.RouteInfo info) {
                final CastDevice device = CastDevice.getFromBundle(info.getExtras());
                if(device==null)
                    return;

                ArchosVideoCastManager.getInstance().setSelectedRoute(info);

            }

            @Override
            public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
                ArchosVideoCastManager.getInstance().setSelectedRoute(info);
            }
        };
        sCastPlayerService = this;
        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mSubtitleSizeDefault = getResources().getInteger(R.integer.player_pref_subtitle_size_default);
        mSubtitleColorDefault = Color.parseColor(getResources().getString(R.string.subtitle_color_default));
        mSubtitleVPosDefault = getResources().getInteger(R.integer.player_pref_subtitle_vpos_default);
        Log.d(TAG,"onCreate");

        bindService(new Intent(this, PlayerService.class), mPlayerServiceConnection, BIND_AUTO_CREATE);
        if(PlayerService.sPlayerService!=null)
            onFirstScreenPresentationCreatedAndServiceConnected();
        mWindowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        AppState.addOnForeGroundListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction("DISPLAY_FLOATING_PLAYER");
        filter.addAction(PlayerService.PLAY_INTENT);
        filter.addAction(PlayerService.PAUSE_INTENT);
        filter.addAction(PlayerService.EXIT_INTENT);
        filter.addAction(PlayerService.FULLSCREEN_INTENT);


    }
    @Override
    public int onStartCommand(Intent intent,int flags, int startID){

        Log.d(TAG,"onStartCommand");
        return super.onStartCommand(intent,flags, startID);
    }

    @Override
    public void onCreatePresentation(Display display) {
        Log.d(TAG,"onCreatePresentation");
        if(mIntent!=null)
        addFloatingView(display);
        mDisplay = display;

    }
    public void forceIntent(Intent intent){
        mIntent = intent;
        if(mDisplay!=null)
            addFloatingView(mDisplay);

    }
    @Override
    public void onDismissPresentation() {
        if(mFirstScreenPresentation!=null&&mFirstScreenPresentation.isShowing())
        mFirstScreenPresentation.dismiss();
    }

    public void onDestroy(){
        super.onDestroy();
        sCastPlayerService = null;
        if(PlayerService.sPlayerService!=null)
            PlayerService.sPlayerService.removePlayerFrontend(this,false);
        unbindService(mPlayerServiceConnection);
        if(mOnDestroyListener!=null)
        mOnDestroyListener.onServiceDestroy();
    }
    @Override
    public void onStart(Intent intent, int startID){
        super.onStart(intent, startID);
    }

    @Nullable
    public void addFloatingView(Display display) {
        Log.d(TAG,"addFloating");
        if(!contains) {

            LayoutInflater li = LayoutInflater.from(this);
            mFirstScreenPresentation = new FirstScreenPresentation(this, display, this);

            try {
                mFirstScreenPresentation.show();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.e(TAG, "Unable to show presentation, display was " +
                        "removed.", ex);
            }
        }

    }



    private void updateSizes() {


        mSurfaceController.setScreenSize(mRootView.getMeasuredWidth(), mRootView.getMeasuredHeight());
        mSubtitleManager.setScreenSize(mRootView.getMeasuredWidth(), mRootView.getMeasuredHeight());


    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TORRENT_UPDATE :
                    try {
                        String toParse = (String)msg.obj;
                        String[] parsed = toParse.split(";");
                        String toDisplay = parsed[0]+" peers "+
                                (Long.parseLong(parsed[1])>=0?parsed[1]+" seeds ":"")+
                                Long.parseLong(parsed[2])/1024+" kB/s "+
                                Long.parseLong(parsed[4])/1024/1024+"MB/"
                                +Long.parseLong(parsed[5])/1024/1024+"MB";

                        View torrent_status = mProgressView.findViewById(R.id.torrent_status);
                        torrent_status.setVisibility(View.VISIBLE);
                        ((TextView)torrent_status).setText(toDisplay);

                    } catch(NumberFormatException e) {
                        Log.d("AVP", "Display update", e);
                    } catch(java.lang.ArrayIndexOutOfBoundsException e) {
                        Log.d("AVP", "Display update, out of bound", e);
                    }
                    break;
            }
        }
    };


    @Override
    public void onForeGroundState(Context applicationContext, boolean foreground) {
        if(mBackgroundWarning!=null){
            if(foreground)
                mBackgroundWarning.setVisibility(View.GONE);
            else
                mBackgroundWarning.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onAudioError(boolean isNotSupported, String msg) {   }

    @Override
    public void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo) {
        if(PlayerService.sPlayerService!=null)
         PlayerService.sPlayerService.setVideoInfo(info);
    }

    @Override
    public void setUri(Uri mUri, Uri streamingUri) {  }

    @Override
    public void setVideoInfo(VideoDbInfo mVideoInfo) {   }

    @Override
    public void onEnd() {
        ArchosVideoCastManager.getInstance().onPlayingStateChanged();
        ArchosVideoCastManager.getInstance().setDoNotUnselectRoute(true); //onrouteunselect will be called, so ignore it (we go back to video stream mode)
        stopService();
    }


    @Override
    public void onTorrentUpdate(String daemonString) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_TORRENT_UPDATE , daemonString));
    }

    @Override
    public void onTorrentNotEnoughSpace() {  }

    @Override
    public void onFrontendDetached() {
        PlayerService.sPlayerService.stopStatusbarNotification();
        if(contains) {
            contains = false;
        }
    }

    @Override
    public void onFirstPlay() {
        
    }

    @Override
    public void onPrepared() {
        mProgressView.setVisibility(View.GONE);
        updateSizes();
        ArchosVideoCastManager.getInstance().onPlayingStateChanged();
    }

    @Override
    public void onCompletion() {
        ArchosVideoCastManager.getInstance().onPlayingStateChanged();
        mNextSeek  = -1;
    }

    @Override
    public boolean onError(int errorCode, int errorQualCode, String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        ArchosVideoCastManager.getInstance().setDoNotUnselectRoute(true); //onrouteunselect will be called, so ignore it (we go back to video stream mode)
        stopService();
        return false;
    }

    @Override
    public void onSeekStart(int pos) {

    }

    @Override
    public void onSeekComplete() {

    }

    @Override
    public void onAllSeekComplete() {
        ArchosVideoCastManager.getInstance().onPlayingStateChanged();
    }

    @Override
    public void onPlay() {
        ArchosVideoCastManager.getInstance().onPlayingStateChanged();
    }

    @Override
    public void onPause() {
        ArchosVideoCastManager.getInstance().onPlayingStateChanged();

    }


    @Override
    public void onOSDUpdate() {   }

    @Override
    public void onVideoMetadataUpdated(VideoMetadata vMetadata) {

    }

    @Override
    public void onAudioMetadataUpdated(VideoMetadata vMetadata, int currentAudio) {
        if (PlayerService.sPlayerService.getVideoInfo() == null) {
            //  mNewSubtitleTrack = newSubtitleTrack;
            // mAudioSubtitleNeedUpdate = true;
            return;
        }
        int nbTrack = vMetadata.getAudioTrackNb();
        ArchosVideoCastManager.getInstance().clearAudioTracks();


        int noneTrack = nbTrack+1;
        if (nbTrack != 0) {
            for (int i = 0; i < nbTrack; ++i) {
                VideoMetadata.AudioTrack audio = vMetadata.getAudioTrack(i);
                CharSequence summary = VideoUtils.getLanguageString(this, audio.format);
                ArchosVideoCastManager.getInstance().getMediaInfo().getMediaTracks().add(new MediaTrack.Builder(ArchosVideoCastManager.getInstance().getMediaInfo().getMediaTracks().size(), MediaTrack.TYPE_AUDIO).setContentId(""+i).setName((String) VideoUtils.getLanguageString(this, audio.name)+" ("+summary+")").build());

            }
            nbTrack++;

        }
        ArchosVideoCastManager.getInstance().onRemoteMediaPlayerMetadataUpdated();

    }

    @Override
    public void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int currentSubtitle) {
        if (PlayerService.sPlayerService.getVideoInfo() == null) {
          //  mNewSubtitleTrack = newSubtitleTrack;
           // mAudioSubtitleNeedUpdate = true;
            return;
        }
        int nbTrack = vMetadata.getSubtitleTrackNb();
        ArchosVideoCastManager.getInstance().clearSubtitleTracks();


        int noneTrack = nbTrack+1;
        if (nbTrack != 0) {
            for (int i = 0; i < nbTrack; ++i) {

                ArchosVideoCastManager.getInstance().getMediaInfo().getMediaTracks().add(new MediaTrack.Builder(ArchosVideoCastManager.getInstance().getMediaInfo().getMediaTracks().size(), MediaTrack.TYPE_TEXT).setContentId(""+i).setName((String) VideoUtils.getLanguageString(this, vMetadata.getSubtitleTrack(i).name)).build());
            }
            nbTrack++;

        }
        ArchosVideoCastManager.getInstance().onRemoteMediaPlayerMetadataUpdated();
        if (nbTrack != 0) {
           mSubtitleManager.start();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            int size = preferences.getInt(PlayerActivity.KEY_SUBTITLE_SIZE, mSubtitleSizeDefault);
            int vpos = preferences.getInt(PlayerActivity.KEY_SUBTITLE_VPOS, mSubtitleVPosDefault);
            int color = preferences.getInt(PlayerActivity.KEY_SUBTITLE_COLOR, mSubtitleColorDefault);
            mSubtitleManager.setSize(size);
            mSubtitleManager.setColor(color);
            mSubtitleManager.setVerticalPosition(vpos);


        }

    }

    @Override
    public void onBufferingUpdate(int percent) {   }

    @Override
    public void onSubtitle(Subtitle subtitle) {
        mSubtitleManager.addSubtitle(subtitle);
    }

    public void setUIExternalSurface(Surface uiSurface) {
        mSubtitleManager.setUIExternalSurface(uiSurface);
    }


    private boolean mDragging;
    private boolean mSeekWasPlaying;
    private boolean mSeekComplete;
    private int mLastRelativePosition;
    private long mLastProgressTime;
    private int mLastProgress;
    private int mLastSeek;
    private int mNextSeek = -1;
    private SeekBar.OnSeekBarChangeListener mProgressListener = new SeekBar.OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {

            mHandler.removeMessages(MSG_HIDE_CONTROLLER);


            mDragging = true;
            mSeekComplete = false;

            // By removing these pending progress messages we make sure
            // that a) we won't update the progress while the user adjusts
            // the seekbar and b) once the user is done dragging the thumb
            // we will post one of these messages to the queue again and
            // this ensures that there will be exactly one message queued up.
            if (Player.sPlayer.isPlaying()) {
                mSeekWasPlaying = true;
                Player.sPlayer.pause();
            }
            else
                mSeekWasPlaying=false;
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = Player.sPlayer.getDuration();
            long newposition;

            if (duration > 0) {
                newposition = (duration * progress) / 1000L;
            } else {
                newposition = progress;
                mLastRelativePosition = Player.sPlayer.getRelativePosition();

            }


            long currentProgressTime = System.currentTimeMillis();

            // don't try to seek too much
            if (Player.sPlayer.isLocalVideo() &&
                    (currentProgressTime - mLastProgressTime > PlayerController.SEEK_PROGRESS_TIME_THRESHOLD) &&
                    (Math.abs(progress - mLastProgress) > PlayerController.SEEK_PROGRESS_THRESHOLD)) {
                mSeekComplete = false;
                Player.sPlayer.seekTo((int) newposition);
                mLastSeek = (int) newposition;
                mLastProgressTime = currentProgressTime;
                mLastProgress = progress;
            }
            mNextSeek = (int) newposition;

        }

        public void onStopTrackingTouch(SeekBar bar) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLLER, 3000);
            mDragging = false;

            if (mNextSeek != -1 && mNextSeek != mLastSeek) {
                Player.sPlayer.seekTo(mNextSeek);
            }
            mLastSeek = -1;
            if(mSeekWasPlaying)
                Player.sPlayer.start();

        }
    };

    @Override
    public void onFirstScreenPresentationCreated(View rootView) {

        Log.d(TAG,"onFirstScreenPresentationCreated");
        mRootView = rootView;
        mSubtitleManager = new SubtitleManager(this, (ViewGroup)mRootView.findViewById(R.id.subtitle_root_view), (WindowManager)getSystemService(WINDOW_SERVICE),true);

        mProgressView = rootView.findViewById(R.id.progress_indicator);
        mBackgroundWarning = rootView.findViewById(R.id.warning_background);
        mSurfaceController = new SurfaceController(rootView);
        contains = true;
        onFirstScreenPresentationCreatedAndServiceConnected();

    }

    private void onFirstScreenPresentationCreatedAndServiceConnected() {
        if(contains&&isServiceConnected){
            Log.d(TAG,"onFirstScreenPresentationCreatedAndServiceConnected");

            PlayerService.sPlayerService.switchPlayerFrontend(this);
            if(mRemoteInterface!=null)
                PlayerService.sPlayerService.setIndexHelper(mRemoteInterface.getIndexHelper());
            new Player(this, null, mSurfaceController,false);

            PlayerService.sPlayerService.setPlayer();
            updateSizes();
            PlayerService.sPlayerService.onStart(mIntent);
        }
    }

    public void registerRemoteInterface(RemoteInterface remoteInterface){
        mRemoteInterface = remoteInterface;
        if(PlayerService.sPlayerService!=null)
            PlayerService.sPlayerService.setIndexHelper(mRemoteInterface.getIndexHelper());

    }

    public void setOnDestroyedListener(OnDestroyListener onDestroyListener) {
        mOnDestroyListener = onDestroyListener;
    }

    public interface RemoteInterface{
        public IndexHelper getIndexHelper();
    }
}
