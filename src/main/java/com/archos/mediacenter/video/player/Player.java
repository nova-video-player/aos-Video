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

package com.archos.mediacenter.video.player;

import android.annotation.SuppressLint;

import static com.archos.mediacenter.video.utils.CodecDiscovery.displaySupportsDoVi;
import static com.archos.mediacenter.video.utils.CodecDiscovery.displaySupportsHdr10;
import static com.archos.mediacenter.video.utils.CodecDiscovery.displaySupportsHdr10Plus;
import static com.archos.mediacenter.video.utils.CodecDiscovery.displaySupportsHdrHLG;
import static com.archos.mediacenter.video.utils.CodecDiscovery.getHdrScreenCapabilities;
import static com.archos.mediacenter.video.utils.CodecDiscovery.resetHdrCapabilities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;

import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.content.pm.ActivityInfo;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.CodecDiscovery;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.MediaFactory;
import com.archos.medialib.MediaMetadata;
import com.archos.medialib.Subtitle;
import com.archos.mediaprovider.ArchosMediaCommon;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

import android.view.Display.Mode;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Displays a video file.  The PlayerView class
 * can load images from various sources (such as resources or content
 * providers), takes care of computing its measurement from the video so that
 * it can be used in any layout manager, and provides various display options
 * such as scaling and tinting.
 */
public class Player implements IPlayerControl,
                               IMediaPlayer.OnPreparedListener,
                               IMediaPlayer.OnCompletionListener,
                               IMediaPlayer.OnInfoListener,
                               IMediaPlayer.OnErrorListener,
                               IMediaPlayer.OnBufferingUpdateListener,
                               IMediaPlayer.OnRelativePositionUpdateListener,
                               IMediaPlayer.OnSeekCompleteListener,
                               IMediaPlayer.OnVideoSizeChangedListener,
                               IMediaPlayer.OnSubtitleListener,
                               SurfaceHolder.Callback,
                               TextureView.SurfaceTextureListener{

    private static final Logger log = LoggerFactory.getLogger(Player.class);

    @SuppressLint("StaticFieldLeak")
    public static Player sPlayer;
    // settable by the client
    private Uri         mUri;
    private Uri         mSaveUri;
    private Map<String, String> mExtraMap;
    private int         mDuration;

    // all possible internal states
    private static final int STATE_ERROR              = -1;
    private static final int STATE_IDLE               = 0;
    private static final int STATE_PREPARING          = 1;
    private static final int STATE_PREPARED           = 2;
    private static final int STATE_REFRESH_PREPARED   = 3;
    private static final int STATE_SURFACE_PREPARED   = 4;
    private static final int STATE_PLAYING            = 5;
    private static final int STATE_PAUSED             = 6;
    private static final int STATE_PLAYBACK_COMPLETED = 7;
    
    // mCurrentState is a PlayerView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the PlayerView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState  = STATE_IDLE;

    private static final int SCREEN_ON_FLAGS = (
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
         //| WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
         //| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
    );

    // All the stuff we need for playing and showing a video
    private Context     mContext = null;
    private SurfaceController mSurfaceController;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceTexture mVideoTexture;
    private Surface mUISurface;
    private int         mSurfaceWidth;
    private int         mSurfaceHeight;
    private IMediaPlayer mMediaPlayer;
    private int mOpenGeneration;
    private boolean mSessionPrepared;
    private boolean mRestoringSession;
    private boolean mMetadataReady;
    private boolean mHasAudio;
    private SurfaceTexture mDisplayTexture;
    private int mFocusEpoch;
    private AudioManager.OnAudioFocusChangeListener mFocusListener;
    private boolean mFocusGranted;
    private boolean mFocusSuspended;
    private boolean mResumeAfterFocusLoss;
    private String mAudioOutputSignature;

    private int         mVideoWidth;
    private int         mVideoHeight;
    private double      mVideoAspect;
    private boolean     mCanPause;
    private boolean     mCanSeekBack;
    private boolean     mCanSeekForward;
    private boolean     mIsLocalVideo;
    private boolean     mIsTorrent;
    private boolean     mWaitForNewRate;
    private final int   NUMBER_RETRIES = 5;
    private int         numberRetries = NUMBER_RETRIES;
    private static final float REFRESH_RATE_EPSILON = 0.01f;
    private static final float EPSILON = 0.00001f;
    private float       mRefreshRate;
    private int         wantedModeId;
    private Window      mWindow;
    private AudioManager mAudioManager;
    private AudioFocusRequest mAudioFocusRequest = null;
    private static float mCurrentRefreshRate = 0.0f;
    private static float mCurrentFps = 0.0f;

    private VideoEffectRenderer mEffectRenderer;

    /*
     * Archos
     */
    private Listener mPlayerListener;
    private VideoMetadata   mVideoMetadata;
    private boolean     mIsBusy;
    private int         mBufferPosition;
    private int         mRelativePosition;
    private int         mStopPosition;
    private int         mSaveStopPosition;
    private boolean     mUpdateMetadata;

    private Handler     mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPreparedAsync = new Runnable() {
        public void run() {

            if (mCurrentState == STATE_REFRESH_PREPARED) {
                mCurrentState = STATE_SURFACE_PREPARED;
                if (mRestoringSession) {
                    mRestoringSession = false;
                    if (mTargetState == STATE_PLAYING) start(PlayerController.STATE_OTHER);
                    else pause(PlayerController.STATE_OTHER);
                    if (mPlayerListener != null) mPlayerListener.onOSDUpdate();
                } else {
                    mSessionPrepared = true;
                    if (mPlayerListener != null) mPlayerListener.onPrepared();
                }
            }
        }
    };

    private Runnable mRefreshRateCheckerAsync = new Runnable() {
        public void run() {
            if (log.isDebugEnabled()) log.debug("mRefreshRateCheckerAsync");
            if (mCurrentState == STATE_PREPARED) {
                if (mWaitForNewRate && mWindow != null) {
                    View v = mWindow.getDecorView();
                    Display d = v != null ? v.getDisplay() : null;
                    if (d == null) return;
                    Display.Mode currentMode = d.getMode();
                    if (currentMode == null) return;
                    int currentModeId = currentMode.getModeId();
                    if (numberRetries > 0) { // only try NUMBER_RETRIES
                        if (currentModeId != wantedModeId) {
                            if (log.isDebugEnabled()) log.debug("CONFIG current modeId rate is {} trying to switch to {}, number of retries={}", currentModeId, wantedModeId, numberRetries);
                            numberRetries--;
                            mHandler.postDelayed(mRefreshRateCheckerAsync, 200);
                            return;
                        }
                        if (log.isDebugEnabled()) log.debug("CONFIG modeId before video start is {}", currentModeId);
                        mCurrentRefreshRate = d.getRefreshRate();
                    } else {
                        log.warn("CONFIG failed to set modeId to {} it is still {}", wantedModeId, currentModeId);
                        Toast.makeText(mContext, R.string.refreshrate_failed, Toast.LENGTH_SHORT).show();
                    }
                }

                mCurrentState = STATE_REFRESH_PREPARED;

                if (mVideoWidth != 0 && mVideoHeight != 0) {
                    mSurfaceController.setVideoSize(mVideoWidth, mVideoHeight, mVideoAspect);
                    if (mEffectRenderer != null)
                        mEffectRenderer.setVideoSize(mVideoWidth, mVideoHeight, mVideoAspect);
                    mHandler.removeCallbacks(mPreparedAsync);
                    mHandler.post(mPreparedAsync);

                } else {
                    // We don't know the video size yet, but should start anyway.
                    // The video size might be reported to us later, or not at all.
                    // Wait 1 second, and play a video with no surface (sound only).
                    mHandler.postDelayed(mPreparedAsync, 1000);
                }
            }
        }
    };

    private boolean mForceSoftwareDecoding;
    private int mLastExistState = -1;


    private class ResumeCtx {
        private boolean mSubtitleTrackSet;
        private int     mSeek;
        private int     mSubtitleTrack;
        private int     mSubtitleDelay;
        private int     mSubtitleRatioN;
        private int     mSubtitleRatioD;
        private int     mAudioTrack;
        private int     mAudioFilter;
        private int     mNightModeOn;
        private int     mAvDelay;
        private float   mAvSpeed;

        public ResumeCtx() {}

        public void reset() {
            mSeek = -1;
            mSubtitleTrack = -1;
            mSubtitleTrackSet = false;
            mSubtitleDelay = 0;
            mSubtitleRatioN = -1;
            mSubtitleRatioD = -1;
            mAudioTrack = -1;
            mAudioFilter = 0;
            mNightModeOn = 0;
            mAvDelay = 0;
            mAvSpeed = 1.0f;
        }
        public void onPrepared() {
            if (mSeek != -1)
                seekTo(mSeek);
            if (log.isDebugEnabled()) log.debug("onPrepared: subtitleTrack={}", mSubtitleTrack);
            if (mSubtitleTrackSet)
                mMediaPlayer.setSubtitleTrack(mSubtitleTrack);
            if (mSubtitleDelay != 0)
                mMediaPlayer.setSubtitleDelay(mSubtitleDelay);
            if (mSubtitleRatioN != -1 && mSubtitleRatioD != -1)
                mMediaPlayer.setSubtitleRatio(mSubtitleRatioN, mSubtitleRatioD);
            if ((mAudioFilter != 0) || (mNightModeOn != 0))
                mMediaPlayer.setAudioFilter(mAudioFilter, mNightModeOn);
            if (mAvDelay != 0)
                mMediaPlayer.setAvDelay(mAvDelay);
            if (mAvSpeed != 1.0f)
                mMediaPlayer.setAvSpeed(mAvSpeed);
            if (log.isTraceEnabled()) log.trace("onPrepared: audioTrack={}", mAudioTrack);
            if (mAudioTrack != -1)
                mMediaPlayer.setAudioTrack(mAudioTrack);
            mSeek = -1;
        }
        public void setSeek(int seek) {
            mSeek = seek;
        }
        public int getSeek() {
            return mSeek;
        }
        public void setSubtitleTrack(int subtitleTrack) {
            if (log.isDebugEnabled()) log.debug("setSubtitleTrack: {}", subtitleTrack);
            mSubtitleTrack = subtitleTrack;
            mSubtitleTrackSet = true;
        }
        public void setSubtitleDelay(int subtitleDelay) {
            mSubtitleDelay = subtitleDelay;
        }
        public void setAudioTrack(int audioTrack) {
            if (log.isDebugEnabled()) log.debug("setAudioTrack: {}", audioTrack);
            mAudioTrack = audioTrack;
        }
        public void setSubtitleRatio(int n, int d) {
            mSubtitleRatioN = n;
            mSubtitleRatioD = d;
        }
        public void setAudioFilter(int n, int enable) {
            mAudioFilter = n;
            mNightModeOn = enable;
        }
        public void setAvDelay(int delay) {
            mAvDelay = delay;
        }
        public void setAvSpeed(float speed) {
            mAvSpeed = speed;
        }
    }

    public ResumeCtx mResumeCtx = new ResumeCtx();

    private void reset() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        mVideoAspect = 1.0f;
        mRelativePosition = -1;
        mBufferPosition = -1;
        mStopPosition = -1;
        mUpdateMetadata = false;
        mCurrentState = STATE_IDLE;
        mTargetState  = STATE_IDLE;
        mIsBusy = false;
        mVideoMetadata = new VideoMetadata();
        mDuration = -1;
    }

    public Player(Context context, Window window, SurfaceController surfaceController, boolean forceSoftwareDecoding) { //force software decoding is specific for floating player
        sPlayer = this;
        if (log.isDebugEnabled()) log.debug("Player");
        reset();
        mSurfaceHolder = null;
        mVideoTexture = null;
        mForceSoftwareDecoding =forceSoftwareDecoding;
        mMediaPlayer = null;
        mUri = null;
        mIsTorrent = false;
        mExtraMap = null;
        mResumeCtx.reset();
        mContext = context;
        mWindow = window;
        mAudioManager = (AudioManager) mContext.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mEffectRenderer = new VideoEffectRenderer(mContext, VideoEffect.getDefaultType());
        setSurfaceController(surfaceController);
    }
    public void setWindow(Window window){
        mWindow = window;
    }
    public void setSurfaceController(SurfaceController surfaceController){
        if(surfaceController == mSurfaceController)
            return;
        if (mDisplayTexture != null) onSurfaceTextureDestroyed(mDisplayTexture);
        else suspendForSurface();
        if (mSurfaceController != null) mSurfaceController.clearCallbacks(this, this);
        mSurfaceHolder = null;
        mDisplayTexture = null;
        mVideoTexture = null;
        mSurfaceController = surfaceController;
        if (mSurfaceController != null) {
            mSurfaceController.setTextureCallback(this);
            mSurfaceController.setSurfaceCallback(this);
        }
    }
    private void setGLSupportEnabled(boolean enable) {
        if (log.isDebugEnabled()) log.debug("setGLSupportEnabled {}", enable);
        int target = mTargetState;
        saveUri();
        suspendForSurface();
        mSurfaceController.setGLSupportEnabled(enable);
        restoreUri(false); // the new surface callback performs the reopen
        mTargetState = target;
    }
    
    public int getEffectType() {
        if (mEffectRenderer != null)
            return mEffectRenderer.getEffectType();
        else return VideoEffect.getDefaultType();
    }
    
    public int getEffectMode() {
        if (mEffectRenderer != null)
            return mEffectRenderer.getEffectMode();
        else return VideoEffect.getDefaultMode();
    }
    
    public void setEffect(int type, int mode) {
        boolean needToSupportGLEffect = VideoEffect.openGLRequested(type);
        if (needToSupportGLEffect ^ mSurfaceController.supportOpenGLVideoEffect()) setGLSupportEnabled(needToSupportGLEffect);
        if (mEffectRenderer != null) {
            mEffectRenderer.setEffectType(type);
            mEffectRenderer.setEffectMode(mode);
        }
        mSurfaceController.setEffectType(type);
        mSurfaceController.setEffectMode(mode);
    }

    public int getUIMode() {
        if (mEffectRenderer != null) return mEffectRenderer.getUIMode();
        else return VideoEffect.NORMAL_2D_MODE;
    }

    public void setVideoURI(Uri uri, Map<String, String> extraMap) {
        stopPlayback();
        reset();
        mMetadataReady = false;
        mHasAudio = false;
        mUri = uri;
        mExtraMap = extraMap;
        String scheme = mUri.getScheme();
        mIsLocalVideo = false;
        if (scheme == null || scheme.equals("file")) {
            mIsLocalVideo = true;
        } else {
            if (scheme.equals("content")) {
                try {
                    if (Integer.parseInt(FileUtils.getName(mUri)) <= ArchosMediaCommon.SCANNED_ID_OFFSET)
                        mIsLocalVideo = true;
                } catch (NumberFormatException e) {}
            }
        }

        if (uri.getPath() != null) {
            // FIXME: "smb://hello/world" .getPath() will return something like "hello/world".
            // fixing this the quick way will break all sort of things
            mVideoMetadata.setFile(uri.getPath());
        }
        if (log.isDebugEnabled()) log.debug("setVideoURI: {}", uri);
        openVideo();
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        ++mFocusEpoch;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest != null) mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
            mAudioFocusRequest = null;
        } else {
            if (mFocusListener != null) mAudioManager.abandonAudioFocus(mFocusListener);
        }
        mFocusListener = null;
        mFocusGranted = false;
    }

    private void closeCurrentPlayer() {
        ++mOpenGeneration;
        mHandler.removeCallbacks(mPreparedAsync);
        mHandler.removeCallbacks(mRefreshRateCheckerAsync);
        IMediaPlayer old = mMediaPlayer;
        mMediaPlayer = null; // callbacks from this instance are now obsolete
        mCurrentState = STATE_IDLE;
        mIsBusy = false;
        mUpdateMetadata = false;
        if (mSurfaceController != null) mSurfaceController.setMediaPlayer(null);
        if (mEffectRenderer != null) mEffectRenderer.pause();
        if (old != null) old.release(); // interrupts prepare/seek and joins rendering
    }

    private void suspendForSurface() {
        if (isInPlaybackState()) {
            mStopPosition = getCurrentPosition();
            mDuration = getDuration();
            if (mCanSeekBack || mCanSeekForward) mResumeCtx.setSeek(mStopPosition);
        }
        mRestoringSession = mSessionPrepared;
        closeCurrentPlayer();
        stayAwake(false);
    }

    public void stopPlayback() {
        if (isInPlaybackState()) {
            mStopPosition = getCurrentPosition();
            mDuration = getDuration();
        }
        closeCurrentPlayer();
        mUri = null;
        mTargetState = STATE_IDLE;
        mSessionPrepared = mRestoringSession = false;
        mResumeAfterFocusLoss = mFocusSuspended = false;
        abandonFocus();
        stayAwake(false);
        mResumeCtx.reset();
    }

    private final AudioManager.OnAudioFocusChangeListener afChangeListener = focusChange -> {
        if (!mHasAudio || mUri == null) return;
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            mFocusGranted = true;
            mFocusSuspended = false;
            boolean resume = mResumeAfterFocusLoss && mTargetState == STATE_PLAYING;
            mResumeAfterFocusLoss = false;
            if (resume) start(PlayerController.STATE_OTHER);
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                   focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            mFocusGranted = false;
            mFocusSuspended = true;
            mResumeAfterFocusLoss = mTargetState == STATE_PLAYING;
            pauseForFocus();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            mFocusGranted = false;
            mFocusSuspended = true;
            mResumeAfterFocusLoss = false;
            pause(PlayerController.STATE_OTHER);
            abandonFocus();
        }
    };

    private void pauseForFocus() {
        pausePlayback(PlayerController.STATE_OTHER);
    }

    public void onAudioBecomingNoisy() {
        if ((!mMetadataReady || mHasAudio) && mTargetState == STATE_PLAYING)
            pause(PlayerController.STATE_NORMAL);
    }

    public void openVideo() {
        if (mUri == null || (mSurfaceHolder == null && mVideoTexture == null)) {
            // not ready for playback just yet, will try again later
            return;
        }

        int doViMode = CodecDiscovery.DOVI_MODE_AUTO;
        try {
            String doViModeValue = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getString(VideoPreferencesCommon.KEY_DOLBY_VISION_MODE, null);
            if ("off".equals(doViModeValue)) {
                doViMode = CodecDiscovery.DOVI_MODE_OFF;
            } else if ("force".equals(doViModeValue)) {
                doViMode = CodecDiscovery.DOVI_MODE_FORCE;
            } else if (doViModeValue == null) {
                boolean isDoViDisabled = PreferenceManager.getDefaultSharedPreferences(mContext)
                        .getBoolean(VideoPreferencesCommon.KEY_DISABLE_DOLBY_VISION, false);
                doViMode = isDoViDisabled ? CodecDiscovery.DOVI_MODE_OFF : CodecDiscovery.DOVI_MODE_AUTO;
            }
        } catch (ClassCastException e) {
            boolean isDoViDisabled = PreferenceManager.getDefaultSharedPreferences(mContext)
                    .getBoolean(VideoPreferencesCommon.KEY_DISABLE_DOLBY_VISION, false);
            doViMode = isDoViDisabled ? CodecDiscovery.DOVI_MODE_OFF : CodecDiscovery.DOVI_MODE_AUTO;
        }
        CodecDiscovery.setDoViMode(doViMode);

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        log.info("openVideo: " + mUri);
        closeCurrentPlayer();
        if (mStopPosition != -1) {
            mResumeCtx.setSeek(mStopPosition);
        }
        final int generation = mOpenGeneration;
        final Uri uri = mUri;
        final Map<String, String> headers = mExtraMap == null ? null : new HashMap<>(mExtraMap);
        final IMediaPlayer player;
        try { player = MediaFactory.createPlayer(mContext, mForceSoftwareDecoding); }
        catch (RuntimeException ex) { onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0, ex.getMessage()); return; }
        mMediaPlayer = player;
        mCurrentState = STATE_PREPARING;
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnInfoListener(this);
        player.setOnErrorListener(this);
        player.setOnBufferingUpdateListener(this);
        player.setOnRelativePositionUpdateListener(this);
        player.setOnSeekCompleteListener(this);
        player.setOnVideoSizeChangedListener(this);
        player.setOnSubtitleListener(this);
        mAudioOutputSignature = CustomApplication.getAudioOutputSignature();
        CustomApplication.applyAudioOutputToNative(mContext);
        new Thread(() -> {
            try {
                if (headers != null) player.setDataSource(mContext, uri, headers);
                else player.setDataSource(mContext, uri);
                mHandler.post(() -> {
                    if (generation != mOpenGeneration || player != mMediaPlayer) return;
                    try {
                        if (mSurfaceHolder != null && mSurfaceHolder.getSurface().isValid()) {
                            player.setDisplay(mSurfaceHolder);
                        } else if (mVideoTexture != null) {
                            Surface surface = new Surface(mVideoTexture);
                            try { player.setSurface(surface); } finally { surface.release(); }
                        } else {
                            suspendForSurface();
                            return;
                        }
                        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        player.setScreenOnWhilePlaying(true);
                        if (mResumeCtx.getSeek() != -1 && !mSurfaceController.supportOpenGLVideoEffect()
                                && player.setStartTime(mResumeCtx.getSeek())) mResumeCtx.setSeek(-1);
                        player.prepareAsync();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        onError(player, IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0, ex.getMessage());
                    }
                });
            } catch (IOException | RuntimeException ex) {
                mHandler.post(() -> {
                    if (generation == mOpenGeneration && player == mMediaPlayer)
                        onError(player, IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0, ex.getMessage());
                });
            }
        }, "Player-source-" + generation).start();
    }

    /*
     * The behaviour of mMediaPlayer.setWakeMode is weird, use stayAwake instead
     * stay awake during video opening and video playback, don't stay awake when local video is paused
     */
    public void stayAwake(boolean awake) {
        if(mWindow == null) return;
        mWindow.getDecorView().post(new Runnable() {
            @Override
            public void run() {
                if (mWindow == null) return;
                LayoutParams lp = mWindow.getAttributes();
                if (awake) {
                    lp.flags |= SCREEN_ON_FLAGS;
                } else {
                    lp.flags &= ~SCREEN_ON_FLAGS;
                }
                mWindow.setAttributes(lp);
            }
        });
    }
    public void setIsTorrent(boolean isTorrent){
        mIsTorrent = isTorrent;
    }
    public boolean isTorrent(){
        return mIsTorrent;
    }
    /* TextureView.SurfaceTextureListener */
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        if (log.isDebugEnabled()) log.debug("CONFIG onSurfaceTextureUpdated");
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (log.isDebugEnabled()) log.debug("CONFIG onSurfaceTextureSizeChanged: {}x{}", width, height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        if (mEffectRenderer != null) mEffectRenderer.setSurfaceSize(width, height);
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (surface != mDisplayTexture) return true;
        suspendForSurface();
        mDisplayTexture = null;
        mVideoTexture = null;
        mUISurface = null;
        if(mContext instanceof PlayerActivity)
            ((PlayerActivity) mContext).setUIExternalSurface(null);
        if(mContext instanceof FloatingPlayerService)
            ((FloatingPlayerService) mContext).setUIExternalSurface(null);
        if(mEffectRenderer!=null){
            mEffectRenderer.stop();
            mEffectRenderer = null;
        }
        return true;
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (log.isDebugEnabled()) log.debug("CONFIG onSurfaceTextureAvailable: {}x{}", width, height);
        if (surface == mDisplayTexture && mVideoTexture != null) return;
        if (mSurfaceHolder != null) {
            suspendForSurface();
            mSurfaceHolder = null;
        }
        if (mDisplayTexture != null && mDisplayTexture != surface)
            onSurfaceTextureDestroyed(mDisplayTexture);
        mDisplayTexture = surface;
        if(mEffectRenderer==null)
            mEffectRenderer = new VideoEffectRenderer(mContext, VideoEffect.getDefaultType());

        try { mEffectRenderer.setTexture(surface, width, height); }
        catch (RuntimeException ex) {
            onError(mMediaPlayer, IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0, ex.getMessage());
            return;
        }
        mVideoTexture = mEffectRenderer.getVideoTexture();
        mUISurface = mEffectRenderer.getUISurface();
        if(mContext instanceof PlayerActivity)
            ((PlayerActivity) mContext).setUIExternalSurface(mUISurface);
        if(mContext instanceof FloatingPlayerService)
            ((FloatingPlayerService) mContext).setUIExternalSurface(mUISurface);
        mSurfaceWidth = width;
        mSurfaceHeight = height;
        openVideo();
        
    }

    /* SurfaceHolder.Callback */
    public void surfaceChanged(SurfaceHolder holder, int format,
                                int w, int h)
    {
        if (log.isDebugEnabled()) log.debug("CONFIG surfaceChanged: {}x{}", w, h);
        mSurfaceWidth = w;
        mSurfaceHeight = h;
        boolean isValidState = (mCurrentState == STATE_PREPARED);
        boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
        if (mMediaPlayer != null && isValidState && hasValidSize) {
            mHandler.removeCallbacks(mPreparedAsync);
            mHandler.post(mPreparedAsync);
        }
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        if (log.isDebugEnabled()) log.debug("CONFIG surfaceCreated");
        if (holder == mSurfaceHolder && mMediaPlayer != null) return;
        if (mDisplayTexture != null) onSurfaceTextureDestroyed(mDisplayTexture);
        mSurfaceHolder = holder;
        openVideo();
    }

    public void surfaceDestroyed(SurfaceHolder holder)
    {
        if (holder != mSurfaceHolder) return;
        suspendForSurface(); // joins native consumers before returning to Android
        mSurfaceHolder = null;
        mSurfaceWidth = 0;
        mSurfaceHeight = 0;
    }

    private void saveUri() {
        if (mMediaPlayer != null) {
            try {
                mStopPosition = getCurrentPosition();
                mDuration = mMediaPlayer.getDuration();
            } catch (IllegalStateException e) { }
        }
        mSaveUri = mUri;
        mSaveStopPosition = mStopPosition;
    }
    
    private void restoreUri(boolean restartVideo) {
        if (log.isDebugEnabled()) log.debug("restoreUri");
        mUri = mSaveUri;
        mStopPosition = mSaveStopPosition;
        if (restartVideo) openVideo();
    }
    
    @SuppressWarnings("deprecation")
    private boolean acquireFocus() {
        if (!mHasAudio) return true;
        if (mFocusGranted) return true;
        if (mFocusSuspended) return false;
        if (mFocusListener == null) {
            final int epoch = ++mFocusEpoch;
            mFocusListener = change -> {
                if (epoch == mFocusEpoch) afChangeListener.onAudioFocusChange(change);
            };
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mAudioFocusRequest == null) {
                mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(mFocusListener, mHandler).build();
            }
            result = mAudioManager.requestAudioFocus(mAudioFocusRequest);
        } else {
            result = mAudioManager.requestAudioFocus(mFocusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        return mFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    public void start(int state) {
        mTargetState = STATE_PLAYING;
        if (state == PlayerController.STATE_NORMAL) {
            mFocusSuspended = false;
            mResumeAfterFocusLoss = false;
        }
        if (!isInPlaybackState() || !mMetadataReady) return;
        if (!acquireFocus()) {
            mMediaPlayer.pause();
            mCurrentState = STATE_PAUSED;
            if (mPlayerListener != null) mPlayerListener.onPause(PlayerController.STATE_OTHER);
            stayAwake(false);
            return;
        }
        mMediaPlayer.start();
        mCurrentState = STATE_PLAYING;
        stayAwake(true);
        if (mPlayerListener != null) mPlayerListener.onPlay(state);
        if (mEffectRenderer != null) mEffectRenderer.onPlay();
    }

    public void pause(int state) {
        mResumeAfterFocusLoss = false;
        mTargetState = STATE_PAUSED;
        pausePlayback(state);
    }

    private void pausePlayback(int state) {
        // TODO used to have if (PlayerService.sPlayerService != null) PlayerService.sPlayerService.saveVideoStateIfReady();
        if (log.isDebugEnabled()) log.debug("pause");
        if (isInPlaybackState()) {
            // Queue pause even if a preceding native start has not completed yet.
            mMediaPlayer.pause();
            mCurrentState = STATE_PAUSED;
        }
        if (mPlayerListener != null) {
            mPlayerListener.onPause(state);
        } else {
            if (log.isDebugEnabled()) log.debug("pause: no listener");
        }
        /* on pause, Don't suspend when video is non local or can't seek */
        //if (!isTorrent() && isLocalVideo() && canSeekBackward() && canSeekForward()) {
        if (!isTorrent() && canSeekBackward() && canSeekForward()) {
            if (log.isDebugEnabled()) log.debug("pause: allow to go to sleep");
            stayAwake(false);
        } else {
            if (log.isDebugEnabled()) log.debug("pause: do not sleep");
        }
    }

    // cache duration as mDuration for faster access
    public int getDuration() {
        if (isInPlaybackState()) {
            mDuration = mMediaPlayer.getDuration();
        }
        return mDuration;
    }

    public boolean hasSuspendedPosition() {
        return mUri != null && mStopPosition >= 0;
    }

    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            int currentPos = mMediaPlayer.getCurrentPosition();
            if (log.isDebugEnabled()) log.debug("getCurrentPosition: {}", currentPos);
            return currentPos;
        } else if (mStopPosition != -1) {
            return mStopPosition;
        }
        return 0;
    }

    public int getBufferPosition() {
        return mBufferPosition;
    }

    public int getRelativePosition() {
        return mRelativePosition;
    }
    
    public void seekTo(int msec) {
        mStopPosition = msec;
        if (log.isDebugEnabled()) log.debug("seekTo: {} ms", msec);
        if (isInPlaybackState()) {
            if (mPlayerListener != null) {
                mPlayerListener.onSeekStart(msec);
            }
            mMediaPlayer.seekTo(msec);
            mIsBusy = true;
        } else {
            mResumeCtx.setSeek(msec);
        }
    }
    
    /*
     * return true if MediaPlayer is seeking on network videos,
     * in that case, avoid to call any MediaPlayer method in order to prevent freeze.
     */
    public boolean isBusy() {
        return mIsBusy && !isLocalVideo();
    }
            
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    public boolean isPauseRequested() {
        return mTargetState == STATE_PAUSED;
    }

    public boolean isPaused() {
        return isInPlaybackState() && ! mMediaPlayer.isPlaying();
    }

    public boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    public void setLooping(boolean enable) {
        if (mMediaPlayer != null) mMediaPlayer.setLooping(enable);
    }
    
    public boolean isLocalVideo() {
        return mIsLocalVideo;
    }

    public boolean canPause() {
        return mCanPause;
    }

    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    public boolean canSeekForward() {
        return mCanSeekForward;
    }
    
    public Bitmap screenshot() {
        return null;
    }

    /* 
     * Archos Part
     */

    public int getType() {
        if (mMediaPlayer == null)
            return -1;
        else
            return mMediaPlayer.getType();
    }

    public VideoMetadata getVideoMetadata() {
        return mVideoMetadata;
    }


    public String getErrorDesc() {
        return null;
    }

    public void checkSubtitles() {
        if (isInPlaybackState())
            mMediaPlayer.checkSubtitles(); // note that player checks also cache directory with avos
    }

    public boolean setSubtitleTrack(int stream) {
        mResumeCtx.setSubtitleTrack(stream);
        if (log.isDebugEnabled()) log.debug("setSubtitleTrack: select stream {}", stream);
        if (isInPlaybackState()) {
            return mMediaPlayer.setSubtitleTrack(stream);
        }
        return true;
    }

    public int checkCurrentFileExists(){
        return  mMediaPlayer!=null?mMediaPlayer.doesCurrentFileExists():mLastExistState;
    }

    public void setSubtitleDelay(int delay) {
        mResumeCtx.setSubtitleDelay(delay);
        if (isInPlaybackState()) {
            mMediaPlayer.setSubtitleDelay(delay);
        }
    }

    /**
     * @param ratio
     * <ul>
     * <li> 1 : ntsc2pal : n = 25025, d = 24000
     * <li> 2 : pal2ntsc : n = 24000, d = 25025
     * <li> otherwise n=d=24000
     * </ul>
     */
    public void setSubtitleRatio(int ratio) {
        switch (ratio) {
            case 1:
                setSubtitleRatio(25025, 24000);
                break;
            case 2:
                setSubtitleRatio(24000, 25025);
                break;
            default:
                setSubtitleRatio(24000, 24000);
                break;
        }
    }

    private void setSubtitleRatio(int n, int d) {
        mResumeCtx.setSubtitleRatio(n, d);
        if (isInPlaybackState()) {
            try {
                mMediaPlayer.setSubtitleRatio(n, d);
            } catch (IllegalStateException e) {
                log.error("setSubtitleRatio fail", e);
            }
        }
    }
    
    public boolean setAudioFilter(int n, boolean nightOn) {
        int enable = nightOn?1:0;
        mResumeCtx.setAudioFilter(n, enable);
        if (isInPlaybackState()) {
            mMediaPlayer.setAudioFilter(n, enable);
            return true;
        }
        return true;
    }

    public void setAvDelay(int delay) {
        mResumeCtx.setAvDelay(delay);
        if (isInPlaybackState()) {
            mMediaPlayer.setAvDelay(delay);
        }
    };

    public void setAvSpeed(float speed) {
        mResumeCtx.setAvSpeed(speed);
        if (isInPlaybackState()) {
            mMediaPlayer.setAvSpeed(speed);
        }
    };

    public boolean setAudioTrack(int stream) {
        mResumeCtx.setAudioTrack(stream);
        if (log.isDebugEnabled()) log.debug("setAudioTrack: select stream {}", stream);
        if (isInPlaybackState()) {
            return mMediaPlayer.setAudioTrack(stream);
        }
        return true;
    }

    public void refreshAudioOutput() {
        if (isInPlaybackState() && mHasAudio) {
            mMediaPlayer.refreshAudioOutput();
        }
    }

    public void onAudioOutputChanged() {
        String signature = CustomApplication.getAudioOutputSignature();
        if (signature.equals(mAudioOutputSignature)) return;
        if (mMediaPlayer == null || !mMetadataReady) return; // prepare applies the newest snapshot
        mAudioOutputSignature = signature;
        if (!mHasAudio) return;
        // Close serializes with seek/track switching and preserves the pending seek target.
        suspendForSurface();
        openVideo();
    }

    private void handleMetadata(IMediaPlayer mp) {
        if (log.isDebugEnabled()) log.debug("handleMetadata");
        MediaMetadata data = mp.getMediaMetadata(IMediaPlayer.METADATA_ALL,
                                       IMediaPlayer.BYPASS_METADATA_FILTER);
        if (data != null) {
            if (data.has(IMediaPlayer.METADATA_KEY_NB_AUDIO_TRACK)) {
                mHasAudio = data.getInt(IMediaPlayer.METADATA_KEY_NB_AUDIO_TRACK) > 0;
                if (data.has(IMediaPlayer.METADATA_KEY_CURRENT_AUDIO_TRACK))
                    mHasAudio &= data.getInt(IMediaPlayer.METADATA_KEY_CURRENT_AUDIO_TRACK) >= 0;
                mMetadataReady = true;
                if (!mHasAudio) { abandonFocus(); mFocusSuspended = mResumeAfterFocusLoss = false; }
            }
            boolean enabledUpdate = false;

            if (data.has(IMediaPlayer.METADATA_KEY_PAUSE_AVAILABLE)) {
                mCanPause = data.getBoolean(IMediaPlayer.METADATA_KEY_PAUSE_AVAILABLE);
                enabledUpdate = true;
            }
            if (data.has(IMediaPlayer.METADATA_KEY_SEEK_BACKWARD_AVAILABLE)) {
                mCanSeekBack = data.getBoolean(IMediaPlayer.METADATA_KEY_SEEK_BACKWARD_AVAILABLE);
                enabledUpdate = true;
            }
            if (data.has(IMediaPlayer.METADATA_KEY_SEEK_FORWARD_AVAILABLE)) {
                mCanSeekForward = data.getBoolean(IMediaPlayer.METADATA_KEY_SEEK_FORWARD_AVAILABLE);
                enabledUpdate = true;
            }
            if (enabledUpdate && mPlayerListener != null) {
                mPlayerListener.onOSDUpdate();
            }
            mVideoMetadata.setData(data);

            if (mPlayerListener != null) {
                if (data.has(IMediaPlayer.METADATA_KEY_NB_VIDEO_TRACK))
                    mPlayerListener.onVideoMetadataUpdated(mVideoMetadata);
                if (data.has(IMediaPlayer.METADATA_KEY_NB_AUDIO_TRACK)) {
                    int currentAudio = -1;
                    if (data.has(IMediaPlayer.METADATA_KEY_CURRENT_AUDIO_TRACK))
                        currentAudio = data.getInt(IMediaPlayer.METADATA_KEY_CURRENT_AUDIO_TRACK);
                    if (log.isDebugEnabled()) log.debug("handleMetadata: currentAudioTrack -1 -> {}, calling onAudioMetadataUpdated", currentAudio);
                    mPlayerListener.onAudioMetadataUpdated(mVideoMetadata, currentAudio);
                }
                if (data.has(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)) {
                    int currentSubtitle = -1;
                    if (data.has(IMediaPlayer.METADATA_KEY_CURRENT_SUBTITLE_TRACK))
                        currentSubtitle = data.getInt(IMediaPlayer.METADATA_KEY_CURRENT_SUBTITLE_TRACK);
                    if (log.isDebugEnabled()) log.debug("handleMetadata: currentSubtitleTrack -1 -> {}, calling onSubtitleMetadataUpdated", currentSubtitle);
                    mPlayerListener.onSubtitleMetadataUpdated(mVideoMetadata, currentSubtitle);
                }
            }
        }
    }

    /* IMediaPlayer.Listener */
    public void onPrepared(IMediaPlayer mp) {
        if (mp != mMediaPlayer) return;
        mCurrentState = STATE_PREPARED;
        if (mSurfaceController != null)
            mSurfaceController.setMediaPlayer(mMediaPlayer);

        // Get the capabilities of the player for this stream
        mCanPause = mCanSeekForward = mCanSeekBack = true;
        if (log.isDebugEnabled()) log.debug("onPrepared: mCanPause={}, mCanSeekForward={}, mCanSeekBack={} -> handleMetadata", mCanPause, mCanSeekForward, mCanSeekBack);
        handleMetadata(mMediaPlayer);

        // No audio tracks is a valid video session, including failed/absent audio decode.
        mMetadataReady = true;
        if (mHasAudio && !CustomApplication.getAudioOutputSignature().equals(mAudioOutputSignature)) {
            onAudioOutputChanged();
            return;
        }
        mResumeCtx.onPrepared();

        if (mWindow != null) {

            View v = mWindow.getDecorView();
            Display d = v.getDisplay();

            setHdrCapabilities();

            int refreshRateSwitchMode = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(mContext).getString("enable_tv_refreshrate_switch_mode","0"));
            boolean refreshRateSwitchEnabled = (refreshRateSwitchMode!= 0);

            CustomApplication.setSupportedRefreshRates(getSupportedRefreshRates());

            VideoMetadata.VideoTrack video = mVideoMetadata.getVideoTrack();
            if (video != null) {
                float wantedFps = (float) ((double) video.fpsRate / (double) video.fpsScale);
                mCurrentFps = wantedFps;

                if (refreshRateSwitchEnabled) {
                    LayoutParams lp = mWindow.getAttributes();
                    mWaitForNewRate = false;
                    if (lp != null && video != null && video.fpsRate > 0 && video.fpsScale > 0) {
                        if (log.isDebugEnabled()) log.debug("CONFIG video.fpsRate={}, video.fpsScale={} -> wantedFps={}", video.fpsRate, video.fpsScale, wantedFps);
                        if (refreshRateSwitchMode == 2 && Build.VERSION.SDK_INT >= 31 && mSurfaceHolder != null) {
                            if (log.isDebugEnabled()) log.debug("CONFIG setting frame rate to {} fps through setFrameRate Android 12+ API", wantedFps);
                            Surface videoSurface = mSurfaceHolder.getSurface();
                            // Surface.CHANGE_FRAME_RATE_ALWAYS is needed to get the refresh rate switch
                            videoSurface.setFrameRate(wantedFps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS);
                        } else {
                            // select display mode of highest refresh rate matching 0 modulo fps
                            Display.Mode[] supportedModes = d.getSupportedModes();
                            Display.Mode currentMode = d.getMode();
                            int currentModeId = currentMode.getModeId();
                            if (log.isDebugEnabled()) {
                                log.debug("CONFIG current display mode is {}", currentMode);
                                for (Mode mode : supportedModes)
                                    if (log.isDebugEnabled()) log.debug("CONFIG display supported mode {}", mode);
                            }

                            wantedModeId = 0;
                            // find corresponding wantedModeId for wantedFps
                            Mode sM;
                            int metric = 0;
                            boolean foundMatch = false;
                            int fps = Math.round(1001 * wantedFps);
                            int rhz = 0;
                            int maxRhz = 0;

                            // minimize judder in 2 passes selecting:
                            // highest rr matching rr%fr=0
                            // else highest rr maximizing number of glitches per second
                            if (log.isDebugEnabled()) log.debug("CONFIG min judder targeting {} fps video", wantedFps);
                            if (log.isDebugEnabled()) log.debug("CONFIG min judder: highest rr matching rr%fr=0 pass");
                            for (int i = 0; i < supportedModes.length; i++) {
                                sM = supportedModes[i];
                                rhz = Math.round(1001 * sM.getRefreshRate());
                                if (rhz >= fps) { // no frame drop
                                    metric = rhz % fps;
                                    if (log.isDebugEnabled()) log.debug("CONFIG evaluating {}x{}({}Hz) metric = {}", sM.getPhysicalWidth(), sM.getPhysicalHeight(), sM.getRefreshRate(), metric);
                                    // be more tolerant on metric == 0 check since on firestick roundings make it not null
                                    if (sM.getPhysicalWidth() == currentMode.getPhysicalWidth() && sM.getPhysicalHeight() == currentMode.getPhysicalHeight() &&
                                            metric < 10 && rhz >= maxRhz) {
                                        foundMatch = true;
                                        maxRhz = rhz;
                                        wantedModeId = sM.getModeId();
                                        if (log.isDebugEnabled()) log.debug("CONFIG selecting modeId {} for {} Hz and {} fps (metric = {})", wantedModeId, rhz, fps, metric);
                                    }
                                }
                            }

                            if (!foundMatch) {
                                int k, kp, g;
                                maxRhz = 0;
                                int maxG = 0; // init with lowest number easy to beat
                                if (log.isDebugEnabled()) log.debug("CONFIG min judder: highest rr maximizing number of glitches pass");
                                for (int i = 0; i < supportedModes.length; i++) {
                                    sM = supportedModes[i];
                                    rhz = Math.round(1001 * sM.getRefreshRate());
                                    if (rhz >= fps) { // no frame drop
                                        k = rhz % fps;
                                        kp = fps - k;
                                        g = Math.min(k, kp); // number of glitches (uneven image duration) in 1001s
                                        if (log.isDebugEnabled()) log.debug("CONFIG evaluating {}x{}({}Hz) glitches = {}", sM.getPhysicalWidth(), sM.getPhysicalHeight(), sM.getRefreshRate(), g);
                                        if (sM.getPhysicalWidth() == currentMode.getPhysicalWidth() && sM.getPhysicalHeight() == currentMode.getPhysicalHeight() &&
                                                g >= maxG && rhz >= maxRhz) {
                                            foundMatch = true;
                                            maxRhz = rhz;
                                            maxG = g;
                                            wantedModeId = sM.getModeId();
                                            if (log.isDebugEnabled()) log.debug("CONFIG selecting modeId {} for {} Hz and {} fps (glitches = {})", wantedModeId, rhz, fps, g);
                                        }
                                    }
                                }
                            }

                            if (wantedModeId != 0 && wantedModeId != currentModeId) {
                                // apply new display mode
                                mWaitForNewRate = true;
                                numberRetries = NUMBER_RETRIES;
                                lp.preferredDisplayModeId = wantedModeId;
                                mWindow.setAttributes(lp);
                            }
                        }
                    }
                }
            } else {
                log.warn("onPrepared: videoTrack is null!");
            }
        }
        mHandler.post(mRefreshRateCheckerAsync);

        if (log.isDebugEnabled()) log.debug("CONFIG technicalInfo: {}", CodecDiscovery.getTechnicalInfo(mContext));
    }

    public void onCompletion(IMediaPlayer mp) {
        if (mp != mMediaPlayer) return;
        mCurrentState = STATE_PLAYBACK_COMPLETED;
        mTargetState = STATE_PLAYBACK_COMPLETED;
        if (mPlayerListener != null) {
            mPlayerListener.onCompletion();
        }
    }
    public int getVideoWidth(){
        return mVideoWidth;
    }
    public int getVideoHeight(){
        return mVideoHeight;
    }
    public double getVideoAspect() { return mVideoAspect; }
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height) {
        if (mp != mMediaPlayer) return;
        mVideoWidth = width;
        mVideoHeight = height;
        if (log.isDebugEnabled()) log.debug("CONFIG OnVideoSizeChanged: {}x{}", mVideoWidth, mVideoHeight);
        mSurfaceController.setVideoSize(mVideoWidth, mVideoHeight, mVideoAspect);
        if (mEffectRenderer != null)
                mEffectRenderer.setVideoSize(mVideoWidth, mVideoHeight, mVideoAspect);
    }

    public void onVideoAspectChanged(IMediaPlayer mp, double aspect) {
        if (mp != mMediaPlayer) return;
        mVideoAspect = aspect;
        if (log.isDebugEnabled()) log.debug("CONFIG OnVideoAspectChanged: {}", mVideoAspect);
        mSurfaceController.setVideoSize(mVideoWidth, mVideoHeight, mVideoAspect);
        if (mEffectRenderer != null)
                mEffectRenderer.setVideoSize(mVideoWidth, mVideoHeight, mVideoAspect);
    }

    public void onSeekComplete(IMediaPlayer mp) {
        if (mp != mMediaPlayer) return;
        if (log.isDebugEnabled()) log.debug("onSeekComplete");
        if (mPlayerListener != null) {
            mPlayerListener.onSeekComplete();
        }
    }

    public void onAllSeekComplete(IMediaPlayer mp) {
        if (mp != mMediaPlayer) return;
        mIsBusy = false;
        if (mUpdateMetadata) {
            if (log.isDebugEnabled()) log.debug("onAllSeekComplete: mUpdateMetadata = true -> handleMetadata");
            handleMetadata(mp);
            mUpdateMetadata = false;
        } else {
            if (log.isDebugEnabled()) log.debug("onAllSeekComplete: mUpdateMetadata = false");
        }
        if (mPlayerListener != null) {
            mPlayerListener.onAllSeekComplete();
        }
    }

    public void onRelativePositionUpdate(IMediaPlayer mp, int permil) {
        if (mp != mMediaPlayer) return;
        mRelativePosition = permil;
    }

    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        if (mp != mMediaPlayer) return true;
        if (log.isDebugEnabled()) log.debug("onInfo: {} {}", what, extra);
        switch(what) {
        case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
            if (mIsBusy) {
                if (log.isDebugEnabled()) log.debug("onInfo: mIsBusy set mUpdateMetadata = true");
                mUpdateMetadata = true;
            } else {
                if (log.isDebugEnabled()) log.debug("onInfo: handleMetadata");
                handleMetadata(mp);
            }
            return true;
        case IMediaPlayer.MEDIA_INFO_AUDIO_SPEED_APPLIED:
            float appliedSpeed = extra / 1000.0f;
            if (log.isDebugEnabled()) log.debug("onInfo: audio speed applied {}", appliedSpeed);
            if (mPlayerListener != null) {
                mPlayerListener.onAudioSpeedApplied(appliedSpeed);
            }
            return true;
        case IMediaPlayer.MEDIA_INFO_AUDIO_TRACK_APPLIED:
        case IMediaPlayer.MEDIA_INFO_AUDIO_TRACK_FAILED:
            if (mPlayerListener != null) {
                mPlayerListener.onAudioTrackSelectionCompleted(extra,
                        what == IMediaPlayer.MEDIA_INFO_AUDIO_TRACK_APPLIED);
            }
            return true;
        case IMediaPlayer.MEDIA_INFO_SUBTITLE_TRACK_APPLIED:
        case IMediaPlayer.MEDIA_INFO_SUBTITLE_TRACK_FAILED:
            if (mPlayerListener != null) {
                mPlayerListener.onSubtitleTrackSelectionCompleted(extra,
                        what == IMediaPlayer.MEDIA_INFO_SUBTITLE_TRACK_APPLIED);
            }
            return true;
        default:
            return false;
        }
    }

    public boolean onError(IMediaPlayer mp, int errorCode, int errorQualCode, String msg) {
        if (mp != mMediaPlayer) return true;
        log.warn("onError: Error: {},{}", errorCode, errorQualCode);
        mCurrentState = STATE_ERROR;
        mTargetState = STATE_ERROR;

        if (mp != null) {
            if (log.isDebugEnabled()) log.debug("onError: handleMetadata");
            handleMetadata(mp);
        }
        //save "exist" state, may be useful later
        mLastExistState = mMediaPlayer!=null?mMediaPlayer.doesCurrentFileExists():-1;
        stopPlayback();
        /* If an error handler has been supplied, use it and finish. */
        if (mPlayerListener != null) {
            return mPlayerListener.onError(errorCode, errorQualCode, msg);
        } else {
            return false;
        }
    }

    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        if (mp != mMediaPlayer) return;
        mBufferPosition = percent * 10;
        if (mPlayerListener != null) {
            mPlayerListener.onBufferingUpdate(percent);
        }
    }

    public void onSubtitle(IMediaPlayer mp, Subtitle subtitle) {
        if (mp != mMediaPlayer) return;
        if (log.isDebugEnabled()) log.debug("onSubtitle");
        if (mPlayerListener != null) {
            mPlayerListener.onSubtitle(subtitle);
        }
    }

    public void setListener(Listener listener) {
        mPlayerListener = listener;
    }

    public interface Listener {
        void onPrepared();
        void onCompletion();
        boolean onError( int errorCode, int errorQualCode, String msg);
        void onSeekStart(int pos);
        void onSeekComplete();
        void onAllSeekComplete();
        void onPlay(int state);
        void onPause(int state);
        void onOSDUpdate();
        void onVideoMetadataUpdated(VideoMetadata vMetadata);
        void onAudioMetadataUpdated(VideoMetadata vMetadata, int currentAudio);
        void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int currentSubtitle);
        void onBufferingUpdate(int percent);
        void onSubtitle(Subtitle subtitle);
        default void onAudioSpeedApplied(float speed) {}
        default void onAudioTrackSelectionCompleted(int track, boolean success) {}
        default void onSubtitleTrackSelectionCompleted(int track, boolean success) {}
    }

    public void finishActivity() {
        if(mContext instanceof PlayerActivity) ((PlayerActivity)mContext).finish();
    }

    public String getSupportedRefreshRates() {
        if (mWindow == null) return "";
        View view = mWindow.getDecorView();
        if (view == null) return "";
        Display display = view.getDisplay();
        if (display == null) return "";
        StringBuilder refreshRates = new StringBuilder();

        Display.Mode currentMode = display.getMode();
        if (currentMode == null) return "";
        Display.Mode[] supportedModes = display.getSupportedModes();
        if (supportedModes == null) return "";
        if (mCurrentRefreshRate < 1) mCurrentRefreshRate = currentMode.getRefreshRate();
        int currentWidth = currentMode.getPhysicalWidth();
        int currentHeight = currentMode.getPhysicalHeight();
        // Use TreeSet to maintain sorted order
        Set<Float> uniqueRefreshRates = new TreeSet<>();
        for (Display.Mode mode : supportedModes) {
            if (mode.getPhysicalWidth() == currentWidth && mode.getPhysicalHeight() == currentHeight) {
                // round refreshRate to 2 decimal places
                float refreshRate = Math.round(mode.getRefreshRate() * 100.0f) / 100.0f;
                uniqueRefreshRates.add(refreshRate);
            }
        }
        // Build the refresh rates string
        for (Float rate : uniqueRefreshRates) {
            refreshRates.append(rate).append("Hz, ");
        }
        // Remove trailing ", " in refreshRates and put the result into parenthesis i.e. ( at start and ) at end
        if (refreshRates.length() > 0) {
            refreshRates.setLength(refreshRates.length() - 2);
            refreshRates.insert(0, "(");
            refreshRates.append(")");
        }
        return refreshRates.toString();
    }


    public static String getRefreshRate() {
        return Math.round(mCurrentRefreshRate * 100.0f) / 100.0f + "Hz";
    }

    public static String getFps() {
        return Math.round(mCurrentFps * 100.0f) / 100.0f + "fps";
    }

    @SuppressWarnings("deprecation") // HdrCapabilities.getSupportedHdrTypes() deprecated API 34; Display.Mode.getSupportedHdrTypes() (API 30) is the replacement but this path covers API 24-29
    private void setHdrCapabilities() {
        resetHdrCapabilities();
        if (mWindow != null) {

            View v = mWindow.getDecorView();
            Display d = v != null ? v.getDisplay() : null;

            if (Build.VERSION.SDK_INT >= 24 && d != null) { // HDR capability check

                if (Build.VERSION.SDK_INT >= 26 && d.isHdr()) if (log.isDebugEnabled()) log.debug("CONFIG HDR display detected");

                Display.HdrCapabilities hdrCapabilities = d.getHdrCapabilities();
                if (hdrCapabilities != null) {
                    int[] hdrSupportedTypes = hdrCapabilities.getSupportedHdrTypes();
                    if (hdrSupportedTypes != null) {
                        for (int hdrSupportedType : hdrSupportedTypes) {
                            switch (hdrSupportedType) {
                                case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION:
                                    if (log.isDebugEnabled()) log.debug("CONFIG HDR dolby vision supported");
                                    displaySupportsDoVi(true);
                                    break;
                                case Display.HdrCapabilities.HDR_TYPE_HDR10:
                                    if (log.isDebugEnabled()) log.debug("CONFIG HDR10 supported");
                                    displaySupportsHdr10(true);
                                    break;
                                case Display.HdrCapabilities.HDR_TYPE_HLG:
                                    if (log.isDebugEnabled()) log.debug("CONFIG HDR HLG supported");
                                    displaySupportsHdrHLG(true);
                                    break;
                                case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS:
                                    if (log.isDebugEnabled()) log.debug("CONFIG HDR10+ supported");
                                    displaySupportsHdr10Plus(true);
                                    break;
                            }
                        }
                    }
                }
            } else {
                displaySupportsDoVi(false);
                displaySupportsHdr10(false);
                displaySupportsHdrHLG(false);
                displaySupportsHdr10Plus(false);
            }
            if (Build.VERSION.SDK_INT >= 26) {
                mWindow.setColorMode(ActivityInfo.COLOR_MODE_HDR);
                if (log.isDebugEnabled()) log.debug("CONFIG setColorMode COLOR_MODE_HDR requested");
            }
            // Explicitly set the SurfaceView layer dataspace so SurfaceFlinger engages its HDR
            // tone-mapping pipeline from the start. On some devices (e.g. Google TV Streamer),
            // the surface starts as SDR (dataspace 259 set by MediaCodec at configure time) and
            // the HWC2 does not dynamically switch to HDR composition when the dataspace later
            // changes via the producer. Setting it here (consumer side) ensures correct setup.
            if (mSurfaceController != null) {
                VideoMetadata.VideoTrack videoTrack = mVideoMetadata != null ? mVideoMetadata.getVideoTrack() : null;
                int colorTrc = videoTrack != null ? videoTrack.colorTrc : 0;
                int dataSpace = 0; // default: reset to unknown
                if (colorTrc == 16) {       // AVCOL_TRC_SMPTE2084 (PQ/HDR10)
                    dataSpace = 0x10C00000; // HAL_DATASPACE_BT2020_PQ
                } else if (colorTrc == 18) { // AVCOL_TRC_ARIB_STD_B67 (HLG)
                    dataSpace = 0x12060000; // HAL_DATASPACE_BT2020_HLG
                }
                if (log.isDebugEnabled()) log.debug("CONFIG setSurfaceDataSpace: colorTrc={} dataSpace=0x{}", colorTrc, Integer.toHexString(dataSpace));
                mSurfaceController.setSurfaceDataSpace(dataSpace);
            } else {
                if (log.isDebugEnabled()) log.debug("CONFIG setSurfaceDataSpace: skipped, mSurfaceController is null");
            }
        } else {
            displaySupportsDoVi(false);
            displaySupportsHdr10(false);
            displaySupportsHdrHLG(false);
            displaySupportsHdr10Plus(false);
        }
    }

    public static String getHdr(Context context) { // only works with API34...
        Window window = sPlayer == null ? null : sPlayer.mWindow;
        if (window == null || Build.VERSION.SDK_INT < 34) {
            return "";
        }
        View v = window.getDecorView();
        if (v == null) {
            return "";
        }
        Display d = v.getDisplay();
        if (d == null) {
            return "";
        }
        Display.Mode currentMode = d.getMode();
        if (currentMode == null) {
            return "";
        }
        int[] hdrSupportedTypes = currentMode.getSupportedHdrTypes();
        if (hdrSupportedTypes == null) {
            return "";
        }
        int hdrBitMask = 0;
        for (int hdrSupportedType : hdrSupportedTypes) {
            switch (hdrSupportedType) {
                case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION:
                    hdrBitMask |= 8;
                    break;
                case Display.HdrCapabilities.HDR_TYPE_HDR10:
                    hdrBitMask |= 1;
                    break;
                case Display.HdrCapabilities.HDR_TYPE_HLG:
                    hdrBitMask |= 2;
                    break;
                case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS:
                    hdrBitMask |= 4;
                    break;
            }
        }
        return getHdrScreenCapabilities(context, hdrBitMask);
    }

    public int getSurfaceControllerHeight() {
        if (mSurfaceController == null) return 0;
        return mSurfaceController.getViewHeight();
    }
    public int getSurfaceControllerWidth() {
        if (mSurfaceController == null) return 0;
        return mSurfaceController.getViewWidth();
    }
    public boolean isFloatingPlayer() { return mContext instanceof FloatingPlayerService; }

}
