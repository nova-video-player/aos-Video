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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.Player;
import com.archos.mediacenter.video.player.PlayerService;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration;
import com.google.android.libraries.cast.companionlibrary.cast.MediaQueue;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumer;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.player.MediaAuthService;
import com.google.android.libraries.cast.companionlibrary.cast.tracks.OnTracksSelectedListener;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;
import com.google.android.libraries.cast.companionlibrary.widgets.IMiniController;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGD;
import static com.google.android.libraries.cast.companionlibrary.utils.LogUtils.LOGE;

/**
 * Created by alexandre on 28/06/16.
 */
public class ArchosVideoCastManager implements IMiniController, MiniController.OnMiniControllerChangedListener{
    private static final String TAG = "ArchosVideoCastManager";
    public static final String POSTER_PATH = "poster_path";
    private static ArchosVideoCastManager sInstance;
    private final VideoCastManager mCastManager;
    private final Context mContext;
    private final MediaRouter mMediaRouter;
    private final MediaRouteSelector mMediaRouteSelector;
    private Collection<IMiniController> mMiniControllers;
    public String appId;
    private MediaRouter.RouteInfo mSelectedRoute;
    private long[] mSelectedTracks;
    private boolean mIsRemoteDisplayConnected = false;
    private MediaInfo mMediaInfo;
    private MiniController.OnMiniControllerChangedListener mOnMiniControllerChangedListener;
    private List<ArchosCastManagerListener> mArchosCastManagerListeners;
    private MediaRouter.Callback mRouteSelectorCallback;
    private boolean mIsSwitching;
    private static final long PROGRESS_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private VideoCastConsumer mCastConsumer = new VideoCastConsumerImpl() {

    };
    private List<VideoCastConsumer> mCastConsumers = new ArrayList<>();
    private boolean mDoNotUnselectRoute;
    private boolean mIsPreparing;
    private Timer mProgressTimer;
    private UpdateProgressTask mProgressTask;
    private int mLastVisibility;

    public void seekNext() throws TransientNetworkDisconnectionException, NoConnectionException {
        if(Player.sPlayer!=null && canSeekForward()){
            if(Player.sPlayer.getCurrentPosition()+10000<Player.sPlayer.getDuration())
                Player.sPlayer.seekTo(Player.sPlayer.getCurrentPosition()+10000);
        }
        else if(mCastManager.isConnected()){
            mCastManager.seek((int) (mCastManager.getCurrentMediaPosition()+10000));
        }
    }

    public void seekPrev() throws TransientNetworkDisconnectionException, NoConnectionException {
        if(Player.sPlayer!=null && canSeekBackward()){
            if(Player.sPlayer.getCurrentPosition()-10000>0)
                Player.sPlayer.seekTo(Player.sPlayer.getCurrentPosition()-10000);
            else
                Player.sPlayer.seekTo(0);
        }
        else if(mCastManager.isConnected()){
            mCastManager.seek((int) (mCastManager.getCurrentMediaPosition()-10000));
        }
    }

    public void notifySubsUpdated() {
        if(mIsRemoteDisplayConnected&& Player.sPlayer!=null)
            Player.sPlayer.checkSubtitles();
        else if(CastService.sCastService!=null&&mCastManager.isConnected()){
            CastService.sCastService.reloadSubs();
        }
    }


    private class UpdateProgressTask extends TimerTask {

        @Override
        public void run() {
            int currentPos;
            if (getPlaybackStatus() == MediaStatus.PLAYER_STATE_BUFFERING || !isConnected()) {
                return;
            }
            try {
                int duration = (int) getMediaDuration();
                if (duration > 0) {
                    currentPos = (int) getCurrentMediaPosition();
                    updateProgress(currentPos, duration);
                }
            } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                LOGE(TAG, "Failed to update the progress tracker due to network issues", e);
            }
        }
    }

    public boolean canSeek() {
        if(Player.sPlayer !=null){
            return Player.sPlayer.canSeekBackward()|| Player.sPlayer.canSeekForward();
        }
        else if(mCastManager.isConnected()){
            return true;
        }
        return false;
    }

    public boolean canSeekForward() {
        if(Player.sPlayer !=null){
            return Player.sPlayer.canSeekForward();
        }
        if(mCastManager.isConnected()){
            return true;
        }
        return false;
    }

    public boolean canSeekBackward() {
        if(Player.sPlayer !=null){
            return Player.sPlayer.canSeekBackward();
        }else if(mCastManager.isConnected()){
            return true;
        }
        return false;
    }

    public  boolean shouldKeepConnection() {
        return isConnected(); //route hasn't been unselected
    }

    public MediaRouter.Callback getRouteSelectorCallback() {
        return mRouteSelectorCallback;
    }

    public boolean isRemoteDisplayConnected() {
        return mIsRemoteDisplayConnected;
    }

    public void setIsSwitching(boolean b) {
        mIsSwitching = b;
        if(mIsSwitching)
            mDoNotUnselectRoute = true;
        refreshCastListeners();
    }

    public void setDoNotUnselectRoute(boolean b) {
        mDoNotUnselectRoute = b;
    }

    public void switchToDisplayCast() {
        Log.d(TAG, "switchToDisplayCast mIsSwitching "+mIsSwitching);
        Log.d(TAG, "switchToDisplayCast mIsRemoteDisplayConnected "+mIsRemoteDisplayConnected);

        if(!mIsSwitching&&!mIsRemoteDisplayConnected){
            CastService.sCastService.switchToFallback(true);
        }
    }

    public void switchToVideoCast() {
        if(!mIsSwitching&&mIsRemoteDisplayConnected){
            CastService.sCastService.startVideoCast(true);
        }
    }

    public void clearSubtitleTracks() {
        List<MediaTrack> mediaTracks = new ArrayList<>(mMediaInfo.getMediaTracks());
        for(MediaTrack mediaTrack : mediaTracks){
            if(mediaTrack.getType()==MediaTrack.TYPE_TEXT){
                mMediaInfo.getMediaTracks().remove(mediaTrack);
            }
        }
    }

    public void clearAudioTracks() {
        List<MediaTrack> mediaTracks = new ArrayList<>(mMediaInfo.getMediaTracks());
        for(MediaTrack mediaTrack : mediaTracks){
            if(mediaTrack.getType()==MediaTrack.TYPE_AUDIO){
                mMediaInfo.getMediaTracks().remove(mediaTrack);
            }
        }
    }

    public void setIsPreparing(boolean isPreparing) {
        mIsPreparing = isPreparing;
        if(isPreparing)
            setVisibility(View.VISIBLE);
        refreshCastListeners();
    }


    public interface ArchosCastManagerListener{
        public void updateUI();
        public void switchCastMode();
    }

    public ArchosVideoCastManager(Context context){
        mMiniControllers = new ArrayList();
        mContext = context;
        reset();
        mCastManager = VideoCastManager.getInstance();
        mCastManager.addMiniController(this);//this will set mOnMiniControllerChangedListener
        mCastManager.addVideoCastConsumer(mCastConsumer);
        mCastManager.addTracksSelectedListener(new OnTracksSelectedListener() {
            @Override
            public void onTracksSelected(List<MediaTrack> tracks) {
                ArchosVideoCastManager.getInstance().setSelectedTracks(tracks);
            }
        });
        mArchosCastManagerListeners = new ArrayList<>();
        mRouteSelectorCallback = new MediaRouter.Callback(){
            @Override
            public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
                setSelectedRoute(route);
                if(CastDebug.DBG){
                    CastDebug.log("onRouteSelected null ? "+(route==null));
                }
            }

            public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo route) {
                Log.d(TAG, "route removed");
                if(CastDebug.DBG){
                    CastDebug.log("onRouteRemoved "+route.getName());
                }
                //sometimes onRouteRemoved is called when doing screencast, even when it still works

            }

            public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route, int reason) {
                Log.d(TAG, "onRouteUnselected "+mDoNotUnselectRoute);
                if(CastDebug.DBG){
                    CastDebug.log("onRouteUnselected mDoNotUnselectRoute ?"+(mDoNotUnselectRoute));

                }
                if(mDoNotUnselectRoute){
                    mDoNotUnselectRoute = false;
                }
                else {
                    if(CastDebug.DBG){
                        //check if deco is normal
                        if(route!=null&&route.getExtras()!=null) {
                            CastDevice device = CastDevice.getFromBundle(route.getExtras());
                            if (device.getFriendlyName() != null && device.getFriendlyName().equals(getDeviceName())) {
                                CastDebug.log("WARNING: current device removed : " + getDeviceName());
                            }
                        }
                    }
                    setSelectedRoute(null);
                    reset();
                    refreshCastListeners();
                }
            }

        };
        mMediaRouter = MediaRouter.getInstance(mContext);
        mMediaRouteSelector = new MediaRouteSelector.Builder().addControlCategory(
                CastMediaControlIntent.categoryForCast(mContext.getString(R.string.app_id))).build();

        mMediaRouter.addCallback(mMediaRouteSelector, mRouteSelectorCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

    }

    private void reset() {
        mLastVisibility = View.GONE;
        mIsRemoteDisplayConnected = false;
        mIsSwitching = false;
        mIsPreparing= false;
        mDoNotUnselectRoute = false;
    }

    public static  synchronized ArchosVideoCastManager initialize(Context context, CastConfiguration options){
        VideoCastManager.initialize(context,options);
        if(sInstance==null)  sInstance = new ArchosVideoCastManager(context);
        return sInstance;
    }
    public static synchronized ArchosVideoCastManager getInstance(){
        return sInstance;
    }

    public void setMediaInfo(MediaInfo mediaInfo){
        mMediaInfo = mediaInfo;
        if(mediaInfo==null)
            return;
        for(IMiniController controller:mMiniControllers) {
            MediaMetadata mm = mediaInfo.getMetadata();
            controller.setStreamType(mediaInfo.getStreamType());
            controller.setPlaybackStatus(getPlaybackStatus(), getIdleReason());
            controller.setSubtitle(mContext.getResources().getString(com.google.android.libraries.cast.companionlibrary.R.string.ccl_casting_to_device,
                    getSelectedDevice().getFriendlyName()));
            controller.setTitle(mm.getString(MediaMetadata.KEY_TITLE));
            setIcon(controller);
        }
    }
    public void addMiniController(IMiniController miniController,
                                  MiniController.OnMiniControllerChangedListener onChangedListener) {
        if (miniController != null) {
            boolean result;
            synchronized (mMiniControllers) {
                result = mMiniControllers.add(miniController);
            }

            if (result) {
                miniController.setOnMiniControllerChangedListener(onChangedListener == null ? this
                        : onChangedListener);
                try {
                    if (isConnected()) {
                        updateMiniController(miniController);
                        miniController.setVisibility(shouldMiniControllerBeVisible()?View.VISIBLE:View.GONE);
                    }
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                }
            } else {

            }
        }
    }

    /*
        Is visible when a media is currently played
     */
    private boolean shouldMiniControllerBeVisible() {

        Log.d(TAG, "shouldMiniControllerBeVisible");
        try {
            /*
            when switching cast mode, castmanager first send setVisibility(visible)
            but when checking in  mCastManager.isRemoteMediaLoaded() || mCastManager.getPlaybackStatus() == MediaStatus.PLAYER_STATE_BUFFERING;
            result is false
         */
            if(mCastManager.isConnected()&&(mLastVisibility == View.VISIBLE&&mMediaInfo!=null||mIsPreparing))
                return true;
            boolean remote = mCastManager.isRemoteMediaLoaded() || mCastManager.getPlaybackStatus() == MediaStatus.PLAYER_STATE_BUFFERING;
            if(remote) {
                Log.d(TAG, "remote=true");
                return remote;
            }
        } catch (TransientNetworkDisconnectionException e) {
            e.printStackTrace();
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }
        if(Player.sPlayer != null) {
            Log.d(TAG, "Player.sPlayer != null = true");
            return Player.sPlayer != null;
        }
        Log.d(TAG, "mIsPreparing||mIsSwitching = "+String.valueOf(mIsPreparing||mIsSwitching));
        return mIsPreparing||mIsSwitching;
    }

    public void onPlayingStateChanged(){
        refreshCastListeners();
    }
    public void addMiniController(IMiniController miniController) {
        addMiniController(miniController, null);
    }

    public void removeMiniController(IMiniController listener) {
        if (listener != null) {
            listener.setOnMiniControllerChangedListener(null);
            synchronized (mMiniControllers) {
                mMiniControllers.remove(listener);
            }
        }
    }

    private void updateMiniController(IMiniController controller)
            throws TransientNetworkDisconnectionException, NoConnectionException {
        if (getMediaDuration() > 0) {
            MediaInfo mediaInfo = getMediaInfo();
            MediaMetadata mm = mediaInfo.getMetadata();
            controller.setStreamType(mediaInfo.getStreamType());
            controller.setPlaybackStatus(getPlaybackStatus(), getIdleReason());
            controller.setSubtitle( mContext.getResources().getString(com.google.android.libraries.cast.companionlibrary.R.string.ccl_casting_to_device,
                    getSelectedDevice().getFriendlyName()));
            controller.setTitle(mm.getString(MediaMetadata.KEY_TITLE));
            setIcon(controller);
        }
    }

    private void setIcon(IMiniController controller) {
        if(mMediaInfo.getMetadata().getString(POSTER_PATH)!=null)
            controller.setIcon(Uri.parse(mMediaInfo.getMetadata().getString(POSTER_PATH)));
        else {
            Bitmap bitmap = null;
            controller.setIcon(bitmap);
        }
    }

    public int getMediaDuration() throws TransientNetworkDisconnectionException, NoConnectionException {
        if(mIsRemoteDisplayConnected&&Player.sPlayer!=null)
            return Player.sPlayer.getDuration();
        else if(mCastManager.isConnected())
                return (int) mCastManager.getMediaDuration();
        return -1;
    }


    public boolean isConnected(){
        if(CastDebug.DBG) {
            CastDebug.log("checking if connected : " + String.valueOf(mSelectedRoute != null));
            if (mSelectedRoute == null) {
                if(mCastManager.isConnected()){
                    CastDebug.log("ERROR!!! should be shown as connected ");

                }
            }
        }
        return mSelectedRoute!=null;
    }


    public void setIsRemoteDisplayConnected(boolean isRemoteDisplayConnected){
        mDoNotUnselectRoute = false;
        mIsRemoteDisplayConnected = isRemoteDisplayConnected;
        if(isRemoteDisplayConnected)
            mIsSwitching = false; //end of switch
        setVisibility(shouldMiniControllerBeVisible()?View.VISIBLE:View.GONE);
        refreshSwitchListeners();
    }

    private void refreshSwitchListeners() {
        synchronized (mArchosCastManagerListeners) {
            for (final ArchosCastManagerListener listener : mArchosCastManagerListeners) {
                listener.switchCastMode();
            }
        }
    }

    public void setSelectedRoute(MediaRouter.RouteInfo device){
        mSelectedRoute = device;
        if(CastDebug.DBG){
            CastDebug.log("setSelectedRoute: device is null ?"+(device==null));
        }
        if(device==null)
            onDisconnect();
    }


    private void onDisconnect() {
        if(CastDebug.DBG){
            CastDebug.log("onDisconnect()");
        }
        stopProgressTimer();
    }

    public CastDevice getSelectedDevice() {
        return mSelectedRoute!=null?CastDevice.getFromBundle(mSelectedRoute.getExtras()):null;
    }

    public MediaRouter.RouteInfo getSelectedRoute() {
        return mSelectedRoute;
    }

    public synchronized void setSelectedTracks(List<MediaTrack> tracks) {
        int selectedAudio = 0;
        int selectedSub = 1;
        long[] tracksArray;
        if (tracks.isEmpty()) {
            tracksArray = new long[]{};
        } else {
            tracksArray = new long[tracks.size()];

            for (int i = 0; i < tracks.size(); i++) {

                tracksArray[i] = tracks.get(i).getId();
                try {
                    if (tracks.get(i).getType() == MediaTrack.TYPE_TEXT)
                        selectedSub = Integer.valueOf(tracks.get(i).getContentId());
                    else if (tracks.get(i).getType() == MediaTrack.TYPE_AUDIO)
                        selectedAudio = Integer.valueOf(tracks.get(i).getContentId());
                }catch (NumberFormatException e) {

                }
            }

        }
        if(mIsRemoteDisplayConnected){
            VideoDbInfo videoInfo = PlayerService.sPlayerService.getVideoInfo();
            if (selectedSub != videoInfo.subtitleTrack) {
                if (Player.sPlayer.setSubtitleTrack(selectedSub)) {
                    videoInfo.subtitleTrack = selectedSub;
                }

            }
            if (selectedAudio != videoInfo.audioTrack) {
                if (Player.sPlayer.setAudioTrack(selectedAudio)) {
                    videoInfo.audioTrack = selectedAudio;
                }
            }
        }else if(mCastManager.isConnected())
            mCastManager.setActiveTrackIds(tracksArray);
            //be aware that getSelectedTracks won't return mSelectedTracks but will select tracks selected by playerservice
        mSelectedTracks = tracksArray;
    }


    //be aware that getSelectedTracks won't return mSelectedTracks but will select tracks selected by playerservice
    public synchronized long[] getSelectedTracks(){
        if(PlayerService.sPlayerService!=null&&PlayerService.sPlayerService.getVideoInfo()!=null){

            List<Long> selectedTracks = new ArrayList<>();
            if(PlayerService.sPlayerService.getVideoInfo().subtitleTrack>=0){
                for (MediaTrack mediaTrack : mMediaInfo.getMediaTracks()) { //convert subtitletrack position into Mediatrack ID
                    try {

                        if (mediaTrack.getType() == MediaTrack.TYPE_TEXT&&
                                Integer.valueOf(mediaTrack.getContentId()).equals(PlayerService.sPlayerService.getVideoInfo().subtitleTrack))   {
                            selectedTracks.add(mediaTrack.getId());

                        }
                    }catch (NumberFormatException e) {}
                }

            }
            if(PlayerService.sPlayerService.getVideoInfo().audioTrack>=0){
                for (MediaTrack mediaTrack : mMediaInfo.getMediaTracks()) { //convert audiotrack position into Mediatrack ID
                    try {
                        if (mediaTrack.getType() == MediaTrack.TYPE_AUDIO&&
                                Integer.valueOf(mediaTrack.getContentId())==PlayerService.sPlayerService.getVideoInfo().audioTrack){
                            selectedTracks.add(mediaTrack.getId());
                        }
                    }catch (NumberFormatException e) {}
                }
            }
            long[] selectedTracksArray = new long[selectedTracks.size()];
            int i = 0;
            for(Long select : selectedTracks){
                selectedTracksArray[i] = select;
                i++;
            }
            return selectedTracksArray;
        }
        else if(mCastManager.isConnected())
            return mSelectedTracks;
        return null;
    }

    public MediaInfo getMediaInfo() {
        try {
            return mMediaInfo!=null?mMediaInfo:mCastManager.getRemoteMediaInformation();
        } catch (TransientNetworkDisconnectionException e) {
            e.printStackTrace();
        } catch (NoConnectionException e) {
            e.printStackTrace();
        }
        return null;
    }






    private void refreshCastListeners() {
        synchronized (mArchosCastManagerListeners) {
            for (final ArchosCastManagerListener listener : mArchosCastManagerListeners) {
                listener.updateUI();
            }
        }
        refreshMiniControllers();
    }

    private void refreshMiniControllers(){
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setPlaybackStatus(getPlaybackStatus(), getIdleReason());
            }
        }
    }

    @Override
    public void setIcon(Uri uri) {
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                if(mMediaInfo!=null&&mMediaInfo.getMetadata().getString(POSTER_PATH)!=null)
                    setIcon(controller);
            }
        }
    }

    @Override
    public void setIcon(Bitmap bitmap) {
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setIcon(bitmap);
            }
        }
    }

    @Override
    public void setTitle(String title) {

    }

    @Override
    public void setSubtitle(String subtitle) {
        if(mIsRemoteDisplayConnected)
            return;
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setSubtitle(subtitle);
            }
        }
    }

    @Override
    public void setPlaybackStatus(int state, int idleReason) {
        if(!mIsRemoteDisplayConnected) {
            synchronized (mMiniControllers) {
                for (final IMiniController controller : mMiniControllers) {
                    controller.setPlaybackStatus(state, idleReason);
                }
            }

        }
    }

    @Override
    public void setVisibility(int visibility) {

        mLastVisibility = visibility;
        if(visibility==View.VISIBLE) { //when already streaming with another device, we need to reset mediainfo
            try {
                if (mMediaInfo == null || VideoCastManager.getInstance().getRemoteMediaInformation() != null) {
                    setMediaInfo(VideoCastManager.getInstance().getRemoteMediaInformation());
                    ArchosVideoCastManager.getInstance().restartProgressTimer();
                }
            } catch (TransientNetworkDisconnectionException e) {
            } catch (NoConnectionException e) {
            }
        }
        Log.d(TAG, "setvisibility "+visibility);
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                    controller.setVisibility(shouldMiniControllerBeVisible()?View.VISIBLE:View.GONE); //set visible when video is preparing
            }
        }
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public void setOnMiniControllerChangedListener(MiniController.OnMiniControllerChangedListener listener) {
        mOnMiniControllerChangedListener = listener;
    }

    @Override
    public void setStreamType(int streamType) {
        if(mCastManager.isConnected())
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setStreamType(streamType);
            }
        }
    }

    @Override
    public void setProgress(int progress, int duration) {
        // manage by ourself in updateProgress

    }
    private void updateProgress(int progress, int duration){
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setProgress(progress, duration);
            }
        }
    }
    public void stopProgressTimer() {
        LOGD(TAG, "Stopped TrickPlay Timer");
        if(CastDebug.DBG){
            CastDebug.log("stopProgressTimer()");
            isConnected();
        }

        if (mProgressTask != null) {
            mProgressTask.cancel();
            mProgressTask = null;
        }
        if (mProgressTimer != null) {
            mProgressTimer.cancel();
            mProgressTimer = null;
        }
    }

    public void restartProgressTimer() {
        stopProgressTimer();
        mProgressTimer = new Timer();
        mProgressTask = new UpdateProgressTask();
        mProgressTimer.scheduleAtFixedRate(mProgressTask, 100, PROGRESS_UPDATE_INTERVAL_MS);
        LOGD(TAG, "Restarted Progress Timer");
    }

    @Override
    public void setProgressVisibility(boolean visible) {
        if(mCastManager.isConnected())
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setProgressVisibility(visible);
            }
        }
    }

    @Override
    public void setUpcomingVisibility(boolean visible) {
        if(mCastManager.isConnected())
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setUpcomingVisibility(visible);
            }
        }
    }

    @Override
    public void setUpcomingItem(MediaQueueItem item) {
        if(mCastManager.isConnected())
        synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setUpcomingItem(item);
            }
        }
    }

    @Override
    public void setCurrentVisibility(boolean visible) {
        if(mCastManager.isConnected())
            synchronized (mMiniControllers) {
            for (final IMiniController controller : mMiniControllers) {
                controller.setCurrentVisibility(visible);
            }
        }
    }


    @Override
    public void onPlayPauseClicked(View v) throws CastException, TransientNetworkDisconnectionException, NoConnectionException {
        if(mCastManager.isConnected()&&mOnMiniControllerChangedListener!=null)
            mOnMiniControllerChangedListener.onPlayPauseClicked(v);
        else if(mIsRemoteDisplayConnected){
            if (Player.sPlayer.isPlaying())
                Player.sPlayer.pause();
            else
                Player.sPlayer.start();
            refreshCastListeners();
        }

    }

    @Override
    public void onTargetActivityInvoked(Context context) throws TransientNetworkDisconnectionException, NoConnectionException {

        context.startActivity(getPlayerActivityIntent(context));
    }

    public Intent getPlayerActivityIntent(Context context){
        Intent intent = new Intent(context, mCastManager.getTargetActivity());
        intent.putExtra(VideoCastManager.EXTRA_MEDIA, Utils.mediaInfoToBundle(getMediaInfo()));
        return intent;
    }

    @Override
    public void onUpcomingPlayClicked(View v, MediaQueueItem upcomingItem) {
        if(mCastManager.isConnected()&&mOnMiniControllerChangedListener!=null)
            mOnMiniControllerChangedListener.onUpcomingPlayClicked(v, upcomingItem);
    }

    @Override
    public void onUpcomingStopClicked(View view, MediaQueueItem upcomingItem) {
        if(mCastManager.isConnected()&&mOnMiniControllerChangedListener!=null)
            mOnMiniControllerChangedListener.onUpcomingStopClicked(view, upcomingItem);
    }

    @Override
    public void onFailed(int resourceId, int statusCode) {
        if(mCastManager.isConnected()&&mOnMiniControllerChangedListener!=null)
            mOnMiniControllerChangedListener.onFailed(resourceId, statusCode);
    }

    public long getCurrentMediaPosition() throws TransientNetworkDisconnectionException, NoConnectionException {
        if(mIsRemoteDisplayConnected)
            return Player.sPlayer.getCurrentPosition();
        if (mCastManager.isConnected()){
            return mCastManager.getCurrentMediaPosition();
        }else
            return 0;
    }

    public MediaQueue getMediaQueue() {
        return null;
    }

    public boolean isRemoteMediaPlaying() throws TransientNetworkDisconnectionException, NoConnectionException {
        if(mIsRemoteDisplayConnected)
            return Player.sPlayer.isPlaying()&&!Player.sPlayer.isBusy();
        else if (mCastManager.isConnected()){
            return mCastManager.isRemoteMediaPlaying()&&mCastManager.getPlaybackStatus()!=MediaStatus.PLAYER_STATE_BUFFERING;
        }else
            return false;
    }

    public void loadMedia(MediaInfo mSelectedMedia, boolean b, int startPoint, JSONObject customData) {

    }
    public void loadMedia(MediaInfo mSelectedMedia, boolean b, int startPoint) {

    }
    public boolean isFeatureEnabled(int featureCaptionsPreference) {
        return true;
    }

    public MediaAuthService getMediaAuthService() {
        if (mCastManager.isConnected()){
            return mCastManager.getMediaAuthService();
        }
        return null;
    }

    public int getPlaybackStatus() {
        if(mIsPreparing) {
            return ArchosMediaStatus.PLAYER_STATE_PREPARING;
        }
        if(mIsSwitching) {
            return ArchosMediaStatus.PLAYER_STATE_SWITCHING;
        }
        if(Player.sPlayer !=null){
            if(Player.sPlayer.isBusy()){
                return MediaStatus.PLAYER_STATE_PLAYING;
            }
            if (Player.sPlayer.isPlaying()){
                return MediaStatus.PLAYER_STATE_PLAYING;
            }
            if(!Player.sPlayer.isPlaying()&&Player.sPlayer.isInPlaybackState()){
                return MediaStatus.PLAYER_STATE_PAUSED;
            }
        }else if (mCastManager.isConnected()){
            return mCastManager.getPlaybackStatus();
        }
        return MediaStatus.PLAYER_STATE_IDLE;
    }

    public MediaStatus getMediaStatus() {
        if (mCastManager.isConnected()){
            return mCastManager.getMediaStatus();
        }
        return null;
    }

    public int getIdleReason() {

        if(mIsSwitching||mIsRemoteDisplayConnected)
            return MediaStatus.IDLE_REASON_INTERRUPTED;
        else if(mCastManager.isConnected())
            return mCastManager.getIdleReason();
        return MediaStatus.IDLE_REASON_FINISHED;
    }

    public boolean isRemoteStreamLive()  throws TransientNetworkDisconnectionException, NoConnectionException{
        return false;
    }

    public boolean isRemoteMediaPaused() throws TransientNetworkDisconnectionException, NoConnectionException {
        if(Player.sPlayer!=null)
            return !Player.sPlayer.isPlaying()&&!Player.sPlayer.isBusy();
        else if (mCastManager.isConnected()){
            return mCastManager.isRemoteMediaPaused();
        }
        return false;
    }

    public MediaInfo getRemoteMediaInformation() {

        return getMediaInfo();
    }

    public boolean isConnecting() {
        if (mCastManager.isConnected()){
            return mCastManager.isConnecting();
        }
        return false;
    }

    public void addVideoCastConsumer(VideoCastConsumer mCastConsumer) {
        synchronized (mCastConsumers) {
            mCastManager.addVideoCastConsumer(mCastConsumer);
        }
        mCastConsumers.add(mCastConsumer);
    }

    public void incrementUiCounter() {
        mCastManager.incrementUiCounter();
    }

    public void removeVideoCastConsumer(VideoCastConsumer mCastConsumer) {

        mCastManager.removeVideoCastConsumer(mCastConsumer);
        synchronized (mCastConsumers) {
            mCastConsumers.remove(mCastConsumer);
        }
        mCastManager.onRemoteMediaPlayerMetadataUpdated();
    }

    public void onRemoteMediaPlayerMetadataUpdated(){
        synchronized (mCastConsumers){
            for(VideoCastConsumer castConsumer : mCastConsumers)
                castConsumer.onRemoteMediaPlayerMetadataUpdated();
        }
    }

    public void decrementUiCounter() {
        mCastManager.decrementUiCounter();
    }

    public void play(int progress) throws TransientNetworkDisconnectionException, NoConnectionException {
        if (mCastManager.isConnected()){
            mCastManager.play(progress);
        } else if(Player.sPlayer!=null) {
            Player.sPlayer.seekTo(progress);
            refreshCastListeners();
        }
    }

    public void seek(int progress) throws TransientNetworkDisconnectionException, NoConnectionException {
        if(Player.sPlayer!=null) {
            Player.sPlayer.seekTo(progress);
            refreshCastListeners();
        }
        else if (mCastManager.isConnected()){
            mCastManager.seek(progress);
        }
    }

    public void pause() throws CastException, TransientNetworkDisconnectionException, NoConnectionException {
        if(Player.sPlayer!=null) {
            Player.sPlayer.pause();
        }else if (mCastManager.isConnected()){
            mCastManager.pause();
        }
        refreshCastListeners();
    }
    public void play() throws CastException, TransientNetworkDisconnectionException, NoConnectionException {
        if(Player.sPlayer!=null){
            Player.sPlayer.start();
        }
        else if (mCastManager.isConnected()){
            mCastManager.play();
        }
    }



    public void setActiveTracks(List<MediaTrack> tracks) {
        setSelectedTracks(tracks);
    }

    public void removeMediaAuthService() {
        if (mCastManager.isConnected()){
            mCastManager.removeMediaAuthService();
        }
    }

    public void removeTracksSelectedListener(VideoCastControllerFragment videoCastControllerFragment) {

    }

    public void queueNext(Object o) {

    }

    public void queuePrev(Object o) {

    }

    public String getDeviceName() {
        CastDevice selected = getSelectedDevice();
        return selected!=null?selected.getFriendlyName():null;
    }

    public void addTracksSelectedListener(VideoCastControllerFragment videoCastControllerFragment) {

    }

    public void addArchosCastManagerListener(ArchosCastManagerListener archosCastManagerListener) {
        synchronized (mArchosCastManagerListeners) {
            mArchosCastManagerListeners.add(archosCastManagerListener);
        }
    }

    public void removeArchosCastManagerListener(ArchosCastManagerListener archosCastManagerListener) {
        synchronized (mArchosCastManagerListeners) {
            mArchosCastManagerListeners.remove(archosCastManagerListener);
        }
    }
}
