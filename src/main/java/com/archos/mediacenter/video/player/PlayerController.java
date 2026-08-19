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

import static androidx.core.content.ContextCompat.getDrawable;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;

import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.mediacenter.utils.RepeatingImageButton;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.tvmenu.TVCardDialog;
import com.archos.mediacenter.video.player.tvmenu.TVCardView;
import com.archos.mediacenter.video.player.tvmenu.TVMenuAdapter;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

import com.archos.environment.ArchosFeatures;
import static com.archos.environment.ArchosFeatures.isChromeOS;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_PLAYBACK_SPEED;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A view containing controls for a MediaPlayer. Typically contains the
 * buttons like "Play/Pause", "Rewind", "Fast Forward" and a progress
 * slider. It takes care of synchronizing the controls with the state
 * of the MediaPlayer.
 * <p>
 * The way to use this class is to instantiate it programatically.
 * The PlayerController will create a default set of controls
 * and put them in a window floating above your application. Specifically,
 * the controls will float above the view specified with setAnchorView().
 * The window will disappear if left idle for three seconds and reappear
 * when the user touches the anchor view.
 * <p>
 * Functions like show() and hide() have no effect when PlayerController
 * is created in an xml layout.
 *
 * PlayerController will hide and
 * show the buttons according to these rules:
 * </ul>
 * <li> The "previous" and "next" buttons are hidden until setPrevNextListeners()
 *   has been called
 * <li> The "previous" and "next" buttons are visible but disabled if
 *   setPrevNextListeners() was called with null listeners
 * <li> The "rewind" and "fastforward" buttons are shown unless requested
 *   otherwise by using the PlayerController(Context, boolean) constructor
 *   with the boolean set to false
 * </ul>
 */

public class PlayerController implements View.OnTouchListener, OnGenericMotionListener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener
{
    private static final Logger log = LoggerFactory.getLogger(PlayerController.class);
    private static final boolean DBG_ALWAYS_SHOW = false;

    private static final int MSG_FADE_OUT = 1;
    private static final int MSG_SHOW_PROGRESS = 2;
    private static final int MSG_SEEK = 3;
    private static final int MSG_SEEK_RESUME = 4;
    private static final int MSG_SWITCH_VIDEO_FORMAT = 5;
    private static final int MSG_HIDE_SYSTEM_BAR = 6;
    private static final int MSG_OVERLAY_FADE_OUT = 7;
    private static final int MSG_SWITCH_FULLSCREEN_WITH_CUTOUT = 8;

    // introducing states to inform listeners/player that it is in some specific state
    public static final int STATE_NORMAL = 0;
    public static final int STATE_SEEK = 1;
    public static final int STATE_OTHER = 42;

    public static final int SEEK_LONG_INIT_DELAY = 200;
    public static final int SEEK_LONG_DELAY = 50;
    private static final int SEEK_ACCEL_MSECS[]  = { 2000, 6000, 30000, 60000};
    private static final int SEEK_SHORT_MSEC     = SEEK_ACCEL_MSECS[0];
    private static final int SEEK_ACCEL_PERMIL[] = {10, 20, 40, 80};
    private static final int SEEK_SHORT_PERMIL   = SEEK_ACCEL_PERMIL[0];
    private static final int SEEK_ACCEL_STEPS[]  = {0, 3000, 6000, 9000};
    
    private static final int SEEK_RESUME_DELAY = 80;
    public static final int SEEK_PROGRESS_THRESHOLD = 4;
    public static final int SEEK_PROGRESS_TIME_THRESHOLD = 10; //ms

    protected static final int SHOW_TIMEOUT = 3000;
    protected static final float SIDE_SIZE_RATIO = (float) 0.25;

    protected static final int  FLAG_SIDE_CONTROL_BAR = 0x01;
    protected static final int  FLAG_SIDE_SYSTEM_BAR = 0x02;
    protected static final int  FLAG_SIDE_ACTION_BAR = 0x04;
    protected static final int  FLAG_SIDE_VOLUME_BAR = 0x08;
    protected static final int  FLAG_SIDE_UNLOCK_INSTRUCTIONS = 0X10;
    protected static final int FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS = FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_SYSTEM_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_VOLUME_BAR;
    private  static final int  FLAG_SIDE_ALL = FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS | FLAG_SIDE_UNLOCK_INSTRUCTIONS;
    private static final String PLAYER_HELP_OVERLAY_KEY = "play_help_overlay";

    private static final int STATUS_BAR_DISABLE_NOTIFICATION_ICONS = 0x00020000;
    private static final int STATUS_BAR_DISABLE_NOTIFICATION_ALERTS = 0x00040000;
    private static final int STATUS_BAR_DISABLE_NOTIFICATION_TICKER = 0x00080000;

    final private Context       mContext;
    private IPlayerControl      mPlayer;
    private Window              mWindow;
    private int                 mLayoutWidth, mLayoutHeight;
    public int floatingPlayerSize;

    private int                 mActionBarHeight = 0;
    private int                 mSystemBarHeight = 0;
    private SurfaceController   mSurfaceController;
    private Settings            mSettings;
    private View                mRootView, mControllerView, mControllerViewLeft,mControllerViewRight;
    private ViewGroup           mPlayerView;
    private ActionBar           mActionBar;

    private TextView            mVideoTitle;

    private View                mControlBar;
    private View                mControlBar2;
    private ImageButton         mPauseButton;
    private ImageButton         mPauseButton2;
    private ImageButton         mBackwardButton;
    private ImageButton         mForwardButton;
    private ImageButton         mFormatButton;
    private ImageButton         mFullscreenWithCutoutButton;
    //private ImageButton         mBackwardButton2;
    //private ImageButton         mForwardButton2;
    //private ImageButton         mFormatButton2;
    private TextView            mOsdLeftTextView;
    private TextView            mOsdRightTextView;
    private ImageView           mCentralPlayIcon;
    private float               scrollGestureVertical = 0f;
    private float               scrollGestureHorizontal = 0f;
    private final float         BORDER_WIDTH = MiscUtils.dp2Px(24);
    private final float         SCROLL_THRESHOLD = MiscUtils.dp2Px(16);
    private final float         SCROLL_ANGLE = 30;
    private SeekBar             mProgress;
    private SeekBar             mProgress2;
    private TextView            mEndTime, mCurrentTime;
    private TextView            mEndTime2, mCurrentTime2;
    private View                mSeekState;
    private View                mSeekState2;
    private TextView            mClock;
    private SimpleDateFormat    mDateFormat;
    private boolean				splitView;

    private View                mVolumeBar;
    private View                mVolumeBar2;
    private SeekBar             mVolumeLevel;
    private RepeatingImageButton mVolumeUpButton;
    private RepeatingImageButton mVolumeDownButton;
    private SeekBar             mVolumeLevel2;
    private RepeatingImageButton mVolumeUpButton2;
    private RepeatingImageButton mVolumeDownButton2;

    private StringBuilder       mFormatBuilder;
    private Formatter           mFormatter;
    private boolean             mEnabled;
    private boolean             mDragging;
    private boolean             mSeekComplete;
    private int                 mSeekKeyDirection;
    private int                 mBarXYIconResource = R.drawable.video_format_arrow_horizontal;

    private static boolean      mControlBarShowing, mSystemBarShowing, mSystemBarGone, mActionBarShowing, mVolumeBarShowing, mNavigationBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing;
    private static int          mGestureAreaHeight, mControlBarHeight;

    private boolean             mVolumeBarEnabled = false;

    private boolean             mSeekWasPlaying;
    private boolean             mIsStopped = true;
    private boolean             mJoystickSeekingActive = false;
    private int                 mJoystickZone = MediaUtils.JOYSTICK_ZONE_CENTER;
    private int                 mSeekAccelStepCount = SEEK_ACCEL_STEPS.length;

    private AudioManager        mAudioManager;
    private Toast               mToast = null;
    private int                 mNextSeek;
    private int                 mSeekDir;
    private int                 mLongSeekTime;
    private int                 mLastRelativePosition;
    private int                 mLastSeek;
    private long                mLastProgressTime;
    private int                 mLastProgress;
    private Rect                mLastCrop = new Rect();
    private int                 mSystemUiVisibility;
    private WindowInsetsControllerCompat mInsetsController;
    private int 				UIMode;
    private int 				testSwitchView=0;

    private TVMenuAdapter mTVMenuAdapter;
    private View				mTVMenuView;
    private View                mTVMenuView2;
    private boolean				isTVMenuDisplayed;
    private View                mTVMenuContainer;
    private View                mTVMenuContainer2;
    private boolean             manualVisibilityChange;
    private FrameLayout         playerControllersContainer;
    private TVCardDialog tvCardDialog = null;
    private boolean             isTVMode = false;
    private long                mLastTouchEventTime = -1;
    private View mPlayPauseTouchZone;
    private boolean mPlayPauseOnTouchActivated = false;
    private static boolean mFullScreenWithCutout = true;

    private GestureDetector gestureDetector;
    private float currentBrightness;
    public static int pauseTimeout = 0;

    public interface Settings {
        void switchSubtitleTrack();
        void switchAudioTrack();
        void setSubtitleDelay(int delay);
    }

    public PlayerController(Context context, Window window, ViewGroup playerView, SurfaceController surfaceController, Settings settings, ActionBar actionBar) {
        mContext = context;
        mSurfaceController = surfaceController;
        splitView = false;
        mSettings = settings;

        gestureDetector = new GestureDetector(mContext, this);
        UIMode	= VideoEffect.getDefaultMode();
        mWindow = window;

        mAudioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mPlayerView = playerView;
        mActionBar = actionBar;

        mVideoTitle = (TextView) inflater.inflate(R.layout.video_title, null);
        // Default to GONE, enable it if you want it
        mVideoTitle.setVisibility(View.GONE);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(mVideoTitle);
        manualVisibilityChange=false;

        mSystemUiVisibility = 0;
        WindowCompat.setDecorFitsSystemWindows(mWindow, false);
        mWindow.setStatusBarColor(Color.TRANSPARENT); // FLAG_TRANSLUCENT_STATUS replacement: keep status bar transparent when visible
        mInsetsController = WindowCompat.getInsetsController(mWindow, mWindow.getDecorView());
        mInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // Do not hide bars here — old code only set LAYOUT_* flags + IMMERSIVE behavior, not FULLSCREEN|HIDE_NAVIGATION.
        // Bars are hidden later via showSystemBar(false) / MSG_HIDE_SYSTEM_BAR.
        manualVisibilityChange=true;

        /* Hack:
         * dispatch touch events to the VolumeBar that is under the ActionBar
         */
        setRecursiveOnTouchListener(new OnTouchListener() {
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // TODO Auto-generated method stub
                if (mVolumeBar!= null && event.getX() <= mVolumeBar.getWidth()) {
                    return mVolumeBar.dispatchTouchEvent(event);
                }
                return false;
            }
        }, MiscUtils.getActionBarView(mWindow));
        mActionBar.getCustomView().setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mVolumeBar!= null && event.getX() <= mVolumeBar.getWidth()) {
                    return mVolumeBar.dispatchTouchEvent(event);
                }
                return false;
            }
        });
    }

    private boolean isInfoActivityDisplayed() {
        if(mContext instanceof PlayerActivity)
            return ((PlayerActivity)mContext).isInfoActivityDisplayed();
        return false;
    }

    public void reset() {
        mDragging = false;
        mSeekWasPlaying = false;
        mIsStopped = false;
        mSeekComplete = true;
        mLastRelativePosition = mNextSeek = mLastSeek = -1;
        mLastCrop.bottom = mLastCrop.left = mLastCrop.right = mLastCrop.top = -1;
        mActionBarShowing = mControlBarShowing = mVolumeBarShowing = mSystemBarGone = false;
        mSystemBarShowing = true;
        mSeekKeyDirection = 0;
        mLastProgressTime = mLastProgress = 0;
        if (mControlBar != null)
            mControlBar.setVisibility(View.INVISIBLE);
        if (mVolumeBar != null) {
            mVolumeBar.setVisibility(View.GONE);
        }
        if (mControlBar2 != null)
            mControlBar2.setVisibility(View.INVISIBLE);
        if (mVolumeBar2 != null) {
            mVolumeBar2.setVisibility(View.GONE);
        }
        if (mCentralPlayIcon != null) {
            mCentralPlayIcon.setVisibility(View.GONE);
        }
        if (mPlayPauseTouchZone != null) {
            mPlayPauseTouchZone.setVisibility(View.INVISIBLE);
        }
    }

    public void addToMenuContainer(View v){
        View container1 = mControllerViewLeft.findViewById(R.id.tv_menu_container);
        if(container1!=null && container1 instanceof FrameLayout){
            ((FrameLayout)container1).addView(v);
        }
        TVCardDialog dialog = null;
        View cardView = v.findViewById(R.id.card_view);
        if (cardView instanceof TVCardDialog) {
            dialog = (TVCardDialog) cardView;
            // Keep the primary dialog on every TV layout. Previously this was assigned only
            // when a split-screen slave controller existed, leaving normal Android TV playback
            // unable to dismiss the overlay through the predictive-back callback.
            tvCardDialog = dialog;
        }
        if(mControllerViewRight!=null){
            View container2 = mControllerViewRight.findViewById(R.id.tv_menu_container);
            if(container2!=null && container2 instanceof FrameLayout){
                //then we have to inflate a slave view
                if(dialog != null){
                    ((FrameLayout)container2).addView(dialog.createSlaveView());
                }
            }
        }
    }

    private void initMenuAdapter(View v){
        mTVMenuView = v.findViewById(R.id.my_recycler_view);
        if(mTVMenuView!=null){
            mTVMenuAdapter = new TVMenuAdapter((FrameLayout)mTVMenuView,mContext, mWindow );
            mTVMenuAdapter.setOnFocusOutListener(new TVCardView.onFocusOutListener() {
                @Override
                public boolean onFocusOut(int keyCode) {
                    // TODO Auto-generated method stub
                    if(keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode==KeyEvent.KEYCODE_DPAD_DOWN){
                        //showTVMenu(false);
                        return true;
                    }

                    return false;
                }
            });
           
        }
        showTVMenu(false);
    }

    /*
     * 
     * Init controller initialize what is in player_controller_inside.xml. Main view means that this 
     * part can have focus (for TV). Any secondary view will just reproduce the same visual behavior 
     * for split view, like side by side
     * 
     */

    private void initControllerView(View v, boolean isMainView) {
        // next TV menu

        View mControlBar = v.findViewById(R.id.control_bar);

        if(mControlBar!=null &&isMainView){
            mControlBar.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    // TODO Auto-generated method stub
                }
            });
        }
        ImageButton mPauseButton = (ImageButton) v.findViewById(R.id.pause);
        if (mPauseButton != null) {

            mPauseButton.setOnClickListener(mPauseListener);
        }

        View playPauseTouchZone = v.findViewById(R.id.play_touch_zone);
        if(playPauseTouchZone != null &&mPlayPauseOnTouchActivated)
            playPauseTouchZone.setOnClickListener(mPauseListener);

        // Find central play icon
        ImageView centralPlayIcon = (ImageView) v.findViewById(R.id.central_play_icon);
        if (log.isDebugEnabled()) log.debug("initControllerView: looking for central_play_icon in v, found = {}", (centralPlayIcon != null));

        ImageButton mForwardButton = (ImageButton) v.findViewById(R.id.forward);
        if (mForwardButton != null) {
            mForwardButton.setOnClickListener(mForwardListener);
            mForwardButton.setOnLongClickListener(mForwardLongListener);
        }


        ImageButton mBackwardButton = (ImageButton) v.findViewById(R.id.backward);
        if (mBackwardButton != null) {
            mBackwardButton.setOnClickListener(mBackwardListener);
            mBackwardButton.setOnLongClickListener(mBackwardLongListener);
        }


        ImageButton mFormatButton = (ImageButton) v.findViewById(R.id.format);
        if (mFormatButton != null) {
            mFormatButton.setOnClickListener(mFormatListener);
        }

        ImageButton fullscreenWithCutoutButton = (ImageButton) v.findViewById(R.id.fullscreen_cutouts);
        if (fullscreenWithCutoutButton != null) {
            //If we have a Cutout, show the Fullscreen with Cutouts Button
            PlayerActivity playerActivity = ((PlayerActivity) mContext);
            if (playerActivity.mCutoutLeft + playerActivity.mCutoutRight + playerActivity.mCutoutTop + playerActivity.mCutoutBottom > 0) {
                setFullscreenWithCutoutButtonIcon(mSurfaceController.mFullScreenWithCutout);
                fullscreenWithCutoutButton.setVisibility(View.VISIBLE);
                fullscreenWithCutoutButton.setOnClickListener(fullscreenWithCutoutListener);
            } else {
                fullscreenWithCutoutButton.setVisibility(View.GONE);
                fullscreenWithCutoutListener = null;
                fullscreenWithCutoutButton = null;
            }
        }

        SeekBar mProgress = (SeekBar) v.findViewById(R.id.seek_progress);
        if (mProgress != null) {
            mProgress.setOnSeekBarChangeListener(mProgressListener);
            
            mProgress.setMax(1000);
        }

        View volumeBar = v.findViewById(R.id.volume_bar);

        if(isChromeOS(mContext)) {
            if (volumeBar != null) volumeBar.setVisibility(View.GONE);
            mControlBar.setPadding(0,0,0,0);
            volumeBar = null;
        }

        final View finalVolumeBar = volumeBar;
        if (finalVolumeBar != null) {
            if(isMainView){
                //mVolumeBar.setFocusable(true);
                finalVolumeBar.setOnFocusChangeListener(new OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        // The focus display is handled by mPlayerController
                        int resId = hasFocus ? R.drawable.volume_bar_background_focused : R.drawable.volume_bar_background;
                        finalVolumeBar.setBackgroundResource(resId);
                        if(mVolumeBar2!=null)
                            mVolumeBar2.setBackgroundResource(resId);

                    }
                });


            }
            mVolumeBarEnabled = true;
            SeekBar mVolumeLevel = (SeekBar) v.findViewById(R.id.volume_level);
            if (mVolumeLevel != null) {
                mVolumeLevel.setMax(mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                mVolumeLevel.setOnSeekBarChangeListener(mVolumeLevelListener);

            }
            RepeatingImageButton mVolumeUpButton = (RepeatingImageButton) v.findViewById(R.id.volume_up);
            mVolumeUpButton.setOnClickListener(mVolumeUpListener);
            mVolumeUpButton.setRepeatListener(mVolumeUpRepeatListener, 100);
            RepeatingImageButton mVolumeDownButton = (RepeatingImageButton) v.findViewById(R.id.volume_down);
            mVolumeDownButton.setOnClickListener(mVolumeDownListener);
            mVolumeDownButton.setRepeatListener(mVolumeDownRepeatListener, 100);

            if(isMainView){

                this.mVolumeUpButton=mVolumeUpButton;
                this.mVolumeDownButton=mVolumeDownButton;
                this.mVolumeLevel = mVolumeLevel;
            }
            else{
                this.mVolumeUpButton2=mVolumeUpButton;
                this.mVolumeDownButton2=mVolumeDownButton;
                this.mVolumeLevel2 = mVolumeLevel;
            }


        } else {
            mVolumeBarEnabled = false;
        }
        View unlockInstructions = v.findViewById(R.id.unlock_instructions);
        View lock = null;

        if (isMainView) {
            this.mUnlockInstructions = unlockInstructions;
            this.mVolumeBar = volumeBar;
            this.mPlayPauseTouchZone = playPauseTouchZone;
            this.mCentralPlayIcon = centralPlayIcon;
            this.mControlBar=mControlBar;
            this.mPauseButton=mPauseButton;
            this.mFormatButton=mFormatButton ;
            this.mFullscreenWithCutoutButton = fullscreenWithCutoutButton;
            setFullscreenWithCutoutButtonIcon(mSurfaceController.mFullScreenWithCutout);
            this.mBackwardButton=mBackwardButton ;
            this.mForwardButton=mForwardButton ;
            this.mProgress=mProgress ;
            mEndTime = (TextView) v.findViewById(R.id.time);
            mSeekState = v.findViewById(R.id.seek_state);
            mCurrentTime = (TextView) v.findViewById(R.id.time_current);
            // The clock is only for actual leanback devices
            if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK) || isChromeOS(mContext)) {
                if (log.isDebugEnabled()) log.debug("initControllerView: FEATURE_LEANBACK");
                mClock = (TextView) v.findViewById(R.id.clock);
                if(mClock!=null) {
                    // in the player we change the typeface and add shadow to improve visibility over the video plane
                    mClock.setShadowLayer(2, 0, 0, Color.BLACK);
                    mClock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    mClock.setTextColor(Color.WHITE);
                    if (DateFormat.is24HourFormat(mContext)) {
                        mDateFormat = new SimpleDateFormat("HH:mm");
                    } else {
                        mDateFormat = new SimpleDateFormat("h:mm");
                    }
                    updateClock();
                }
            } else if (log.isDebugEnabled()) log.debug("initControllerView: no FEATURE_LEANBACK");
        }
        else{
            this.mUnlockInstructions2 = unlockInstructions;
            this.mVolumeBar2 = mVolumeBar;
            this.mControlBar2=mControlBar;
            this.mPauseButton2=mPauseButton;
            //this.mFormatButton2=mFormatButton;
            //this.mBackwardButton2=mBackwardButton;
            this.mProgress2=mProgress;
            //this.mForwardButton2=mForwardButton ;
            mCurrentTime2 = (TextView) v.findViewById(R.id.time_current);
            mEndTime2 = (TextView) v.findViewById(R.id.time);
            mSeekState2 = v.findViewById(R.id.seek_state);
        }

        updateVolumeBar();
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());

        mActionBar.hide();

        if(mIsLocked)
            lock();
    }

    public void setSizes(int displayWidth, int displayHeight, int layoutWidth, int layoutHeight) {
        // to debug nvplog | grep Player | grep -i -E 'size|height|width'
        // on phones layoutWidth is smaller than MATCH_PARENT by navigationBarHeight thus cannot use ViewGroup.LayoutParams.MATCH_PARENT=-1
        mLayoutWidth = layoutWidth;
        mLayoutHeight = layoutHeight;
        mSystemBarHeight = displayHeight - mLayoutHeight;
        if (log.isDebugEnabled()) log.debug("CONFIG setSizes layout: {}x{} / display: {}x{}, systemBarHeight: {}", mLayoutWidth, mLayoutHeight, displayWidth, displayHeight, mSystemBarHeight);
        if (mControllerView != null) {
            if (log.isDebugEnabled()) log.debug("CONFIG setSizes, mControllerView != null, recreate whole layout");
            // size changed and maybe orientation too, recreate the whole layout
            detachWindow();
            attachWindow();
        } else {
            if (log.isDebugEnabled()) log.debug("CONFIG setSizes, mControllerView == null, doing nothing");
        }
    }



    // setOnSystemUiVisibilityChangeListener is the only reliable way to track transient bar visibility;
    // no WindowInsetsControllerCompat equivalent exists for this use case.
    @SuppressWarnings("deprecation")
    private void attachWindow() {
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (mPreferences != null) mFullScreenWithCutout = mPreferences.getBoolean("enable_cutout_mode_short_edges", true);

        if (log.isDebugEnabled()) log.debug("CONFIG attachWindow getStatusBarHeight={}, getNavigationBarHeight={}, getActionBarHeight={}, getGestureAreaHeight={}, isGestureAreaDisplayed={}, ",
                MiscUtils.getStatusBarHeight(mContext), MiscUtils.getNavigationBarHeight(mContext), MiscUtils.getActionBarHeight(mContext),
                MiscUtils.getGestureAreaHeight(mContext), MiscUtils.isGestureAreaDisplayed(mContext));

        if (mControllerView != null) return;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int layoutID=R.layout.player_controller;
        mControllerView = inflater.inflate(layoutID, null);
        if (mControllerView == null) return;

        mControllerView.setOnTouchListener(this);
        mControllerView.setOnGenericMotionListener(this);
        // twice for sidebyside and topbottom view
        mControllerViewLeft= inflater.inflate(R.layout.player_controller_inside, null);
        if (mControllerViewLeft != null) {
            mOsdLeftTextView = mControllerViewLeft.findViewById(R.id.osd_left);
            mOsdRightTextView = mControllerViewLeft.findViewById(R.id.osd_right);
            mControllerViewLeft.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    // TODO Auto-generated method stub
                    return PlayerController.this.onKey(keyCode, event);
                }
            });
        }

        initControllerView(mControllerViewLeft, true);
        if (mControllerViewLeft != null) {
            mControllerViewLeft.setOnKeyListener(new View.OnKeyListener() {

                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    // TODO Auto-generated method stub
                    return PlayerController.this.onKey(keyCode, event);
                }
            });
        }


        if (mControllerView == null) return;

        playerControllersContainer = (FrameLayout)mControllerView.findViewById(R.id.playerControllersContainer);
        if (playerControllersContainer != null && mControllerViewLeft != null)
            playerControllersContainer.addView(mControllerViewLeft);

        if (log.isDebugEnabled()) log.debug("CONFIG attachWindow: layout WxH {}x{}", mLayoutWidth, mLayoutHeight);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(mLayoutWidth, mLayoutHeight);
        mPlayerView.addView(mControllerView, params);
        if (log.isDebugEnabled()) log.debug("CONFIG attachWindow, adjustControllerViewForInsets();");

        if (mControllerView != null) {
            mRootView = mControllerView.getRootView();
            // note OnApplyWindowInsetsListener does not update when navigation bar fades away, OnGlobalLayoutListener or addOnPreDrawListener are constantly triggering -> only setOnSystemUiVisibilityChangeListener works
            // however setOnSystemUiVisibilityChangeListener is unreliable on Android 6.0 thus use addOnLayoutChangeListener
            // in reality we need to do combination of setOnApplyWindowInsetsListener to get insets but not updated when UI mode changes and thus combine with setOnSystemUiVisibilityChangeListener

            // insets observer is needed for rotation
            mControllerView.setOnApplyWindowInsetsListener((v, insets) -> {
                if (log.isDebugEnabled()) log.debug("attachWindow, onApplyWindowInsetsListener");
                adjustView();
                return insets;
            });

            // ui visibility listener is needed for UI mode changes
            // No WindowInsetsControllerCompat equivalent for transient bar visibility tracking;
            // setOnSystemUiVisibilityChangeListener remains the only reliable option here.
            //noinspection deprecation
            mRootView.setOnSystemUiVisibilityChangeListener(visibility -> {
                //noinspection deprecation
                mNavigationBarShowing = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
                //noinspection deprecation
                mSystemBarShowing = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
                // SYSTEM_UI_FLAG_LOW_PROFILE is no longer set by WindowInsetsControllerCompat;
                // mActionBarShowing is tracked directly by showActionBar(), so skip update here.
                mIsNavBarOnBottom = MiscUtils.isNavigationBarOnBottom(mRootView, mContext);
                mIsGestureAreaShowing = MiscUtils.isGestureAreaDisplayed(mContext);
                mGestureAreaHeight = MiscUtils.getGestureAreaHeight(mContext);
                // assume that controlBar (seek bar + controls) is visible if mNavigationBarShowing || mIsGestureAreaShowing
                mControlBarHeight = (mNavigationBarShowing || mIsGestureAreaShowing ? mControlBar.getHeight() : 0);
                if (log.isDebugEnabled()) log.debug("attachWindow, setOnSystemUiVisibilityChangeListener: mNavigationBarShowing={}, mSystemBarShowing={}, mActionBarShowing={}, mControlBarShowing={}, mIsNavBarOnBottom={}, mIsGestureAreaShowing={}",
                        mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mControlBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing);
                adjustView();
            });

        }
        if (log.isDebugEnabled()) log.debug("CONFIG attachWindow, mPlayerView.addView");

        initMenuAdapter(mControllerViewLeft);
        switchMode(TVUtils.isTV(mContext));
        setUIMode(UIMode);

        //help overlay
        if(mContext instanceof PlayerActivity){
            if(((PlayerActivity)mContext).isPluggedOnTv()){
                mPreferences =  mContext.getSharedPreferences(MediaUtils.SHARED_PREFERENCES_NAME, mContext.MODE_PRIVATE);
                if(!mPreferences.getBoolean(PLAYER_HELP_OVERLAY_KEY, false)){
                    showHelpOverlay(mControllerViewLeft);
                    if(mControllerViewRight!=null && UIMode!=VideoEffect.NORMAL_2D_MODE){
                        showHelpOverlay(mControllerViewRight);
                    }
                    Editor ed = mPreferences.edit();
                    ed.putBoolean(PLAYER_HELP_OVERLAY_KEY, true);
                    ed.commit();
                    sendOverlayFadeOut(6000);
                }
            }
        }
        showControlBar(mControlBarShowing);
    }

    private void adjustView() {
        MiscUtils.adjustViewLayoutForInsets(mContext, mRootView, mControllerView,"mControllerView",
                mNavigationBarShowing, mSystemBarShowing, mActionBarShowing, mControlBarShowing, mIsNavBarOnBottom, mIsGestureAreaShowing,
                0, 0,
                true, true, true, true,
                true, true, true, true, false, false);
    }

    public static int getControlBarCurrentHeight() {
        return mControlBarHeight;
    }

    public static boolean isControlBarShowing() {
        return mControlBarShowing;
    }

    public static boolean isActionBarShowing() {
        return mActionBarShowing;
    }

    private void showHelpOverlay(View controllerView) {
        View overlay = controllerView.findViewById(R.id.help_overlay);
        if(overlay!=null){
            overlay.setAlpha(0);
            overlay.setVisibility(View.VISIBLE);
            overlay.animate().alpha(1);
            ImageView icon = (ImageView)overlay.findViewById(R.id.up_arrow_image);
            if (icon.getBackground() instanceof AnimationDrawable) {
                ((AnimationDrawable)icon.getBackground()).start();
            }
        }
    }

    private void detachWindow() {
        if (mControllerView == null)
            return;
        if (log.isDebugEnabled()) log.debug("detachWindow");
        mPlayerView.removeView(mControllerView);
        mControllerView = null;
        mControllerViewLeft=null;
        mControllerViewRight=null;
    }

    public void setMediaPlayer(IPlayerControl player) {
        reset();

        mPlayer = player;
        updatePausePlay();
    }

    /**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaControlExt
     */
    private void updatePauseButton() {
        if (mSeekState == null || mPauseButton == null || mFormatButton == null)
            return;
        if (Player.sPlayer.isBusy()) {
            mSeekState.setVisibility(View.VISIBLE);
        } else {
            mSeekState.setVisibility(View.GONE);
        }
        if (mPauseButton != null) {
            mPauseButton.setEnabled(mEnabled && Player.sPlayer.canPause() && !Player.sPlayer.isBusy());
            if(mPauseButton2!=null)
                mPauseButton2.setEnabled(mEnabled && Player.sPlayer.canPause() && !Player.sPlayer.isBusy());
        }
        if (mFormatButton != null) {
            mFormatButton.setEnabled(mEnabled);
        }
    }

    private void updateButtons() {
        if (mIsStopped)
            return;
        updatePauseButton();

        if (mBackwardButton != null) {
            mBackwardButton.setEnabled(mEnabled && Player.sPlayer.canSeekBackward());
        }
        if (mForwardButton != null) {
            mForwardButton.setEnabled(mEnabled && Player.sPlayer.canSeekForward());
        }
        if (mProgress != null) {
            mProgress.setEnabled(mEnabled && (Player.sPlayer.canSeekForward() || Player.sPlayer.canSeekBackward()));
        }
    }

    public boolean isShowing() {
        return mControlBarShowing || mActionBarShowing || mVolumeBarShowing;
    }

    private void setVisibility(final View view, boolean show, boolean doAnim) {
        if (mIsStopped) return;
        if (show) view.setVisibility(View.VISIBLE);
        else view.setVisibility(View.GONE);
    }

    private void showActionBar(boolean show) {
        if (isTVMode || TVUtils.isTV(mContext)) show = false;
        if (mActionBarShowing != show || mActionBar.isShowing() != show) {
            if (show) mActionBar.show();
            else mActionBar.hide();
        }
        mActionBarShowing = show;
    }
    public View getVolumeBar(){
        return mVolumeBar;
    }
    public View getControlBar(){
        return mControlBar;
    }
    protected void showSystemBar(boolean show) {
        if (log.isDebugEnabled()) log.debug("showSystemBar {}", show);
        if (show) {
            mInsetsController.show(WindowInsetsCompat.Type.systemBars());
            mNavigationBarShowing = true;
        } else {
            mInsetsController.hide(WindowInsetsCompat.Type.statusBars());
        }
        manualVisibilityChange=true;
        mSystemBarGone = false;
        if (!show)
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_SYSTEM_BAR, 1000);
        mSystemBarShowing = show;
    }

    private void showControlBar(boolean show) {
        if (log.isDebugEnabled()) log.debug("showControlBar {}", show);
        if (mControlBar != null && (mControlBarShowing != show
                || mControlBar.getVisibility() != (show ? View.VISIBLE : View.GONE))) {
            if (log.isDebugEnabled()) log.debug("showControlBar {}", String.valueOf(show));
            setVisibility(mControlBar, show, true);
            if(mPlayPauseTouchZone!=null){
                setVisibility(mPlayPauseTouchZone, show, false);
            }
            if (mControlBar2 != null && splitView ) {
                setVisibility(mControlBar2, show, true);
            }

            if (mClock!=null) {
                setVisibility(mClock, show && !splitView, true);
            }

            if (show) {
                setProgress();

                updateButtons();
                updatePausePlay();
                updateFormat();
            }

            mControlBarShowing = show;
        }
    }

    private void showVolumeBar(boolean show) {
        if (log.isDebugEnabled()) log.debug("showVolumeBar {}", show);
        if (mVolumeBarEnabled && mVolumeBarShowing != show) {
            if (log.isDebugEnabled()) log.debug("showVolumeBar, volume2");
            setVisibility(mVolumeBar, show, true);
            if(mVolumeBar2!=null&&splitView){
                setVisibility(mVolumeBar2, show, true);
                if (log.isDebugEnabled()) log.debug("showVolumeBar, showing volume bar2");
            }
            mVolumeBarShowing = show;
        }
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 3 seconds of inactivity.
     */
    public void show() {
        show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, SHOW_TIMEOUT);
    }

    private void setOSDVisibility(boolean visible, int flags) {
        if (log.isDebugEnabled()) log.debug("setOSDVisibility, visiblity {}", flags);
        if ((flags & FLAG_SIDE_CONTROL_BAR) != 0) {
            showControlBar(visible);
            // On phone we don't know how to display control bar without volume bar nicely, so force volume bar
            
        }
        if ((flags & FLAG_SIDE_SYSTEM_BAR) != 0) {
            showSystemBar(visible);
        }
        if ((flags & FLAG_SIDE_ACTION_BAR) != 0) {
            showActionBar(visible);
        }
        if ((flags & FLAG_SIDE_VOLUME_BAR) != 0) {
            if (log.isDebugEnabled()) log.debug("setOSDVisibility, volume");
            showVolumeBar(visible);
        }
        if((flags & FLAG_SIDE_UNLOCK_INSTRUCTIONS)!=0){
            showUnlockInstructions(visible);
        }
        if (!visible && mCentralPlayIcon != null) mCentralPlayIcon.setVisibility(View.GONE);
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 'timeout' milliseconds of inactivity.
     * @param timeout The timeout in milliseconds. Use 0 to show
     * the controller until hide() is called.
     */
    private void show(int flags, int timeout) {
        if (log.isDebugEnabled()) log.debug("show({}, {})", flags, timeout);
        if (mIsStopped)
            return;

        // cause the progress bar to be updated even if mShowing
        // was already true.  This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mHandler.removeMessages(MSG_SHOW_PROGRESS);
        mHandler.sendEmptyMessage(MSG_SHOW_PROGRESS);
        mHandler.removeMessages(MSG_FADE_OUT);

        if (mHandler.hasMessages(MSG_HIDE_SYSTEM_BAR)) {
            mHandler.removeMessages(MSG_HIDE_SYSTEM_BAR);
            flags |= FLAG_SIDE_SYSTEM_BAR;
        }
        setOSDVisibility(true, flags);

        if ((timeout == 5000) || (timeout != 0 && Player.sPlayer.isPlaying())) {
            sendFadeOut(timeout);
        }
    }

    private void sendFadeOut(int timeout) {
        mHandler.removeMessages(MSG_FADE_OUT);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_FADE_OUT), timeout);
    }
    private void sendOverlayFadeOut(int timeout) {
        mHandler.removeMessages(MSG_FADE_OUT);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_OVERLAY_FADE_OUT), timeout);
    }
    private void cancelFadeOut() {
        mHandler.removeMessages(MSG_FADE_OUT);
    }

    public void hide() {
        hide(FLAG_SIDE_ALL);
    }

    /**
     * Remove the controller from the screen.
     */
    public void hide(int flags) {
        if (log.isDebugEnabled()) log.debug("hide({})", flags);
        if (mIsStopped || DBG_ALWAYS_SHOW)
            return;

        setOSDVisibility(false, flags);

        if (!isShowing()) {
            mHandler.removeMessages(MSG_FADE_OUT);
        }
    }

    public void updateBookmarkToast(int position) {
        updateToast(mContext.getResources().getText(R.string.player_bookmark_set) + " " +
                stringForTime(position));
    }

    public void updateToast(int resId) {
        if (mToast == null) {
            mToast = Toast.makeText(mContext, resId, Toast.LENGTH_SHORT);
        } else {
            mToast.cancel();
            mToast = Toast.makeText(mContext, resId, Toast.LENGTH_SHORT);
        }
        mToast.show();

    }

    public void updateToast(CharSequence text) {
        if (Player.sPlayer.isInPlaybackState()) {
            if (mToast == null) {
                mToast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
            } else {
                mToast.cancel();
                mToast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
            }
            mToast.show();
        }
    }

    public void cancelToast() {
        if (mToast != null) {
            if (log.isDebugEnabled()) log.debug("cancelToast: canceling toast");
            mToast.cancel();
        }
    }

    private final int getSeekAccelStep(int time) {
        int i;

        for (i = SEEK_ACCEL_STEPS.length - 1; i >= 0; i-- ) {
            if( time >= SEEK_ACCEL_STEPS[i] )
                break;
        }
        return i;
    }

    public boolean isSeekPressed() {
        return mSeekKeyDirection != 0 || mJoystickSeekingActive ||
                (mForwardButton != null && mForwardButton.isPressed()) ||
                (mBackwardButton != null && mBackwardButton.isPressed());
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            int pos;
            switch (msg.what) {
                case MSG_FADE_OUT:
                    if (log.isDebugEnabled()) log.debug("Handle: MSG_FADE_OUT");
                    hide();
                    break;
                case MSG_SHOW_PROGRESS:
                    if (log.isDebugEnabled()) log.debug("Handle: MSG_SHOW_PROGRESS");
                    pos = setProgress();
                    if (!mDragging && mControlBarShowing && Player.sPlayer.isPlaying()) {
                        msg = obtainMessage(MSG_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case MSG_SEEK:
                    if (log.isDebugEnabled()) log.debug("Handle: MSG_SEEK");
                    if (mNextSeek >= 0) {
                        boolean stop = false;

                        int step;
                        if (mJoystickSeekingActive) {
                            // Seeking with a joystick => set either a slow or a fast seeking speed depending on
                            // how much the joystick is moved. Make slow speed match  the first acceleration step
                            // and fast speed match the last acceleration step.
                            step = (mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_LEFT || mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_RIGHT) ? mSeekAccelStepCount - 1 : 0;
                        } else {
                            // Seeking with keys => make seeking accelerate while keeping the key pressed a long time
                            step = getSeekAccelStep(mLongSeekTime);
                        }

                        if (mLastRelativePosition == -1) {
                            do {
                                mNextSeek += SEEK_ACCEL_MSECS[step] * mSeekDir;
                            }
                            while ((mSeekDir > 0) ? (mNextSeek <= Player.sPlayer.getCurrentPosition()) : (mNextSeek >= Player.sPlayer.getCurrentPosition()));

                            if (mNextSeek < 0) {
                                /* beginning of the video, don't seek anymore */
                                mNextSeek = 0;
                                stop = true;
                            } else if (mNextSeek >= Player.sPlayer.getDuration() - 2000) {
                                /* end of the video, don't seek anymore */
                                mNextSeek = Player.sPlayer.getDuration() - 2000;
                                stop = true;
                            }
                        } else {
                            do {
                                mNextSeek += SEEK_ACCEL_PERMIL[step] * mSeekDir;
                            }
                            while ((mSeekDir > 0) ? (mNextSeek <= Player.sPlayer.getRelativePosition()) : (mNextSeek >= Player.sPlayer.getRelativePosition()));

                            if (mNextSeek < 0) {
                                /* beginning of the video, don't seek anymore */
                                mNextSeek = 0;
                                stop = true;
                            } else if (mNextSeek >= 1000) {
                                /* end of the video, don't seek anymore */
                                mNextSeek = 980;
                                stop = true;
                            }
                        }
                        if (mSeekComplete && isSeekPressed()) {
                            mSeekComplete = false;
                            if (log.isDebugEnabled()) log.debug("current pos is {} seek to {}", Player.sPlayer.getCurrentPosition(), mNextSeek);
                            Player.sPlayer.seekTo((int) mNextSeek);
                            updatePauseButton();
                        }
                        mLongSeekTime += SEEK_LONG_DELAY;
                        if (stop) {
                            mNextSeek = -1;
                        }
                    }
                    if (isSeekPressed()) {
                        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SEEK),
                                SEEK_LONG_DELAY);
                    } else {
                        mDragging = false;
                        if (mNextSeek > 0 && mLongSeekTime > 2 * SEEK_LONG_DELAY) {
                            mSeekComplete = false;
                            if (log.isDebugEnabled()) log.debug("current pos is {} seek to {}", Player.sPlayer.getCurrentPosition(), mNextSeek);
                            Player.sPlayer.seekTo((int) mNextSeek);
                            updatePauseButton();
                        }
                        if (mSeekComplete && !mDragging) {
                            onSeekAndDraggingComplete();
                        }

                    }
                    setProgress();
                    break;
                case MSG_SEEK_RESUME:
                    if (mSeekWasPlaying) {
                        if (log.isDebugEnabled()) log.debug("Handle: MSG_SEEK_RESUME");
                        Player.sPlayer.start(PlayerController.STATE_SEEK);
                        mSeekWasPlaying = false;
                    }
                    updatePausePlay();
                    updateButtons();
                    show(FLAG_SIDE_CONTROL_BAR, SHOW_TIMEOUT);
                    break;
                case MSG_SWITCH_FULLSCREEN_WITH_CUTOUT:
                    if (!mSurfaceController.mFullScreenWithCutout && mSurfaceController.mCutBothSidesX) {
                        // Go to Fullscreen with Cutout
                        mSurfaceController.mFullScreenWithCutout = true;
                        mSurfaceController.mCutBothSidesX = true;
                    } else if (mSurfaceController.mFullScreenWithCutout && mSurfaceController.mCutBothSidesX) {
                        // Go to Fullscreen with Cutouts off 
                        mSurfaceController.mFullScreenWithCutout = false;
                        mSurfaceController.mCutBothSidesX = false;
                    } else if (!mSurfaceController.mFullScreenWithCutout && !mSurfaceController.mCutBothSidesX) {
                        // Go to Fullscreen with Cutouts off (euqal size)
                        mSurfaceController.mFullScreenWithCutout = false;
                        mSurfaceController.mCutBothSidesX = true;
                     } else {
                        // Invalid combo fallback → reset to normal
                        mSurfaceController.mFullScreenWithCutout = false;
                        mSurfaceController.mCutBothSidesX = false;
                    }

                    setFullscreenWithCutoutButtonIcon(mSurfaceController.mFullScreenWithCutout);
                    mSurfaceController.updateSurface();
                    break;
                case MSG_SWITCH_VIDEO_FORMAT:
                    if (log.isDebugEnabled()) log.debug("Handle: MSG_SWITCH_VIDEO_FORMAT");
                    mSurfaceController.switchVideoFormat();
                    updateFormat();
                    break;
                case MSG_HIDE_SYSTEM_BAR:
                    if (log.isDebugEnabled()) log.debug("Handle: MSG_HIDE_SYSTEM_BAR");
                    mInsetsController.hide(WindowInsetsCompat.Type.navigationBars());
                    mNavigationBarShowing = false;
                    manualVisibilityChange=true;
                    break;
                case MSG_OVERLAY_FADE_OUT:
                    if (log.isDebugEnabled()) log.debug("Handle: MSG_OVERLAY_FADE_OUT");
                    final View overlay1 = mControllerViewLeft.findViewById(R.id.help_overlay);
                    if(overlay1!=null){
                        overlay1.animate().alpha(0).setListener(new AnimatorListener() {
                            public void onAnimationStart(Animator animation) {}
                            public void onAnimationRepeat(Animator animation) {}
                            public void onAnimationEnd(Animator animation) {
                                if(overlay1!=null){
                                    overlay1.setVisibility(View.GONE);
                                }
                            }
                            public void onAnimationCancel(Animator animation) {}
                        });
                        if (log.isDebugEnabled()) log.debug("hidding 1");
                    }
                    if(mControllerViewRight!=null){
                        final View overlay2 = mControllerViewRight.findViewById(R.id.help_overlay);
                        if(overlay2!=null){
                            overlay2.animate().alpha(0).setListener(new AnimatorListener() {
                                public void onAnimationStart(Animator animation) {}
                                public void onAnimationRepeat(Animator animation) {}
                                public void onAnimationEnd(Animator animation) {
                                    if(overlay2!=null){
                                        overlay2.setVisibility(View.GONE);
                                    }
                                }
                                public void onAnimationCancel(Animator animation) {}
                            });
                            if (log.isDebugEnabled()) log.debug("hidding 2");
                        }
                    }
            }
        }

    };

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private int setProgress() {
        if (mIsStopped) {
            return 0;
        }
        int position = mNextSeek == -1 ? Player.sPlayer.getCurrentPosition() : mNextSeek;
        if (position < 0) {
            position = 0;
        }
        int duration = Player.sPlayer.getDuration();
        if (log.isDebugEnabled()) log.debug("setProgress player position/duration={}/{}", position, duration);
        CharSequence endText = "";
        CharSequence currentText = "";

        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);
                if(mProgress2!=null)
                    mProgress2.setProgress((int) pos);
                currentText = stringForTime(position);
                if (log.isDebugEnabled()) log.debug("setProgress player currentText={}", currentText);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                boolean makeTimeNegative = prefs.getBoolean(VideoPreferencesCommon.KEY_MAKE_TIME_NEGATIVE, VideoPreferencesCommon.MAKE_TIME_NEGATIVE_DEFAULT);

                endText = (!makeTimeNegative ? "" : "-") + stringForTime(duration-position > 0 ? duration-position : 0);
            } else {
                if (mDragging || !mSeekComplete) {
                    mProgress.setProgress(position);
                    if(mProgress2!=null)
                        mProgress2.setProgress(position);
                    currentText = String.valueOf(position / 10) + "%";
                } else {
                    int relativePosition = Player.sPlayer.getRelativePosition();
                    if (relativePosition != mLastRelativePosition && relativePosition >= 0) {
                        mLastRelativePosition = relativePosition;
                        mProgress.setProgress(mLastRelativePosition);
                        if(mProgress2!=null)
                            mProgress2.setProgress(mLastRelativePosition);
                    }
                    currentText = stringForTime(position);
                }
            }
            if (!Player.sPlayer.isLocalVideo()) {
                int bufferPosition = Player.sPlayer.getBufferPosition();
                if (bufferPosition >= 0) {
                    mProgress.setSecondaryProgress(bufferPosition);
                    if(mProgress2!=null)
                        mProgress2.setSecondaryProgress(bufferPosition);
                }
            }
        }

        if (mEndTime != null) {
            mEndTime.setText(endText);
            if(mEndTime2!=null)
                mEndTime2.setText(endText);
        }
        if (mCurrentTime != null) {
            mCurrentTime.setText(currentText);
            if(mCurrentTime2!=null)
                mCurrentTime2.setText(currentText);
        }

        return position;
    }

    private void updatePausePlay() {
        if (mPauseButton == null)
            return;

        if (mSeekWasPlaying || Player.sPlayer.isPlaying()) {
            if (log.isDebugEnabled()) log.debug("updatePausePlay: video is playing, hiding central play icon");
            mPauseButton.setImageResource(R.drawable.video_pause_selector);
            if(mPauseButton2!=null)
                mPauseButton2.setImageResource(R.drawable.video_pause_selector);
            // Hide central play icon when playing
            if (mCentralPlayIcon != null) {
                mCentralPlayIcon.setVisibility(View.GONE);
                if (mPlayPauseTouchZone != null)
                    mPlayPauseTouchZone.setVisibility(View.INVISIBLE);
            }
        } else {
            if (log.isDebugEnabled()) log.debug("updatePausePlay: video is paused, showing central play icon (locked={})", mIsLocked);
            mPauseButton.setImageResource(R.drawable.video_play_selector);
            if(mPauseButton2!=null)
                mPauseButton2.setImageResource(R.drawable.video_play_selector);
            // Show central play icon when paused, but only if not locked
            if (mCentralPlayIcon != null && !mIsLocked && !isTVMode) {
                if (log.isDebugEnabled()) log.debug("updatePausePlay: making play touch zone and central icon visible");
                mPlayPauseTouchZone.setVisibility(View.VISIBLE);
                mCentralPlayIcon.setVisibility(View.VISIBLE);
            } else {
                if (log.isDebugEnabled()) log.debug("updatePausePlay: central play icon is null or device is locked");
            }
        }
    }

    private void updateFormat() {
        if (mFormatButton == null)
            return;
        
        mBarXYIconResource = mSurfaceController.willStretchY ? R.drawable.video_format_arrow_vertical : R.drawable.video_format_arrow_horizontal;
        switch (mSurfaceController.getCurrentVideoFormat()) {
            case SurfaceController.VideoFormat.ORIGINAL:
                mFormatButton.setImageResource(R.drawable.video_format_0);
                break;
            case SurfaceController.VideoFormat.STRETCH_XY:
                mFormatButton.setImageResource(mBarXYIconResource);
                break;
            case SurfaceController.VideoFormat.FULL_SCREEN:
                mFormatButton.setImageResource(R.drawable.video_format_arrow_xy);
                break;
            case SurfaceController.VideoFormat.FORCE43:
                mFormatButton.setImageResource(R.drawable.video_format_43);
                break;
            case SurfaceController.VideoFormat.FORCE169:
                mFormatButton.setImageResource(R.drawable.video_format_169);
                break;
            case SurfaceController.VideoFormat.FORCE185:
                mFormatButton.setImageResource(R.drawable.video_format_185);
                break;
            case SurfaceController.VideoFormat.FORCE239:
                mFormatButton.setImageResource(R.drawable.video_format_239);
                break;
            case SurfaceController.VideoFormat.AUTO:
                mFormatButton.setImageResource(R.drawable.video_format_a);
                break;
        }
    }

    public void setStretchXYIcon() {
        mBarXYIconResource = mSurfaceController.willStretchY ? R.drawable.video_format_arrow_vertical : R.drawable.video_format_arrow_horizontal;
        if (mFormatButton != null && mSurfaceController.getCurrentVideoFormat() == SurfaceController.VideoFormat.STRETCH_XY)
            mFormatButton.setImageResource(mBarXYIconResource);
    }

    private void doPauseResume() {
        if (mIsStopped)
            return;
        if (log.isDebugEnabled()) log.debug("doPauseResume: {} - {}", Player.sPlayer.isPlaying(), mSeekWasPlaying);
        if (mNextSeek != -1) {
            mSeekWasPlaying = !mSeekWasPlaying;
        } else {
            if (Player.sPlayer.isPlaying()) {
                show(FLAG_SIDE_CONTROL_BAR, pauseTimeout);
                Player.sPlayer.pause(PlayerController.STATE_NORMAL);
            } else {
                Player.sPlayer.start(PlayerController.STATE_NORMAL);
                show(FLAG_SIDE_CONTROL_BAR, SHOW_TIMEOUT);
            }
           // setVisibility(mAdView, !Player.sPlayer.isPlaying(), true);
        }
        updatePausePlay();
    }

    public void setEnabled(boolean enabled) {
        if (mControllerView == null)
            return;
        mEnabled = enabled;
        updateButtons();
    }

    public void setVideoTitleEnabled(boolean enable) {
        if (mVideoTitle != null) {
            if (enable) {
                mVideoTitle.setVisibility(View.VISIBLE);

            } else {
                mVideoTitle.setVisibility(View.GONE);
            }
        }
    }

    public void setVideoTitle(String title) {
        if (mVideoTitle != null && title != null && !title.isEmpty()) {
            mVideoTitle.setText(title);
        }
    }

    public void start() {
        if (log.isDebugEnabled()) log.debug("start");
        attachWindow();
        setEnabled(true);
        if (DBG_ALWAYS_SHOW)
            show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, 0);
    }

    public void stop() {
        if (log.isDebugEnabled()) log.debug("stop");

        if (mIsStopped)
            return;
        mIsStopped = true;

        mHandler.removeCallbacksAndMessages(null);
        if (hideOsdHandler != null) hideOsdHandler.removeCallbacksAndMessages(hideOsdRunnable);

        mPlayer = null;
        cancelToast();
        detachWindow();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (log.isDebugEnabled()) log.debug("onWindowFocusChanged: {}", hasFocus);
        if (!mIsStopped) {
            /* volume can be changed by an other application: update it */
            if (hasFocus) {
                updateVolumeBar();
                if (mActionBarShowing) show(FLAG_SIDE_ACTION_BAR, SHOW_TIMEOUT);
            } else {
                /* notification panel is showing, don't hide the status bar */
                if (mActionBarShowing) show(FLAG_SIDE_ACTION_BAR, 0);
            }
        }
    }

    private void onSeekAndDraggingComplete() {
        if (log.isDebugEnabled()) log.debug("onSeekAndDraggingComplete: {}", mSeekWasPlaying);
        if (mIsStopped)
            return;
        mNextSeek = -1;
        if (mSeekWasPlaying) {
            mHandler.removeMessages(MSG_SEEK_RESUME);
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SEEK_RESUME), SEEK_RESUME_DELAY);
        } else {
            updatePausePlay();
            updateButtons();
        }
    }

    public void onAllSeekComplete() {
        if (log.isDebugEnabled()) log.debug("onAllSeekComplete");
        if (mIsStopped)
            return;

        mSeekComplete = true;
        if (mSeekComplete && !mDragging) {
            onSeekAndDraggingComplete();
        }
    }

    public void onSeekComplete() {
        if (log.isDebugEnabled()) log.debug("onSeekComplete");
        setProgress();
    }

    public void resumePosition(int position, boolean playOnResume) {
        if (log.isDebugEnabled()) log.debug("resumePositionresumePosition: {} pos: {}", playOnResume, position);
        if (position > 0) {
            mSeekWasPlaying = playOnResume;
            if (Player.sPlayer.getCurrentPosition() > 0) {
                /*
                 * case where mPlayer started at a time, no need to re-seek
                 */
                mHandler.removeMessages(MSG_SEEK_RESUME);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SEEK_RESUME));
            } else {
                mSeekComplete = false;
                Player.sPlayer.seekTo(position);
                show(FLAG_SIDE_CONTROL_BAR, 0);
            }
        } else {
            if (playOnResume) {
                Player.sPlayer.start(PlayerController.STATE_SEEK);
                updatePausePlay();
                hide();
            } else {
                show(FLAG_SIDE_CONTROL_BAR, 0);
                updatePausePlay();
            }
        }
    }

    private void setNextSeekPos(int way) {
        if (log.isDebugEnabled()) log.debug("setNextSeekPos {}", way);
        mSeekDir = way;
        if (mLastRelativePosition == -1) {
            if (mNextSeek == -1) {
                mNextSeek = Player.sPlayer.getCurrentPosition();
            }
            do {
                mNextSeek += way * SEEK_SHORT_MSEC;
            } while ((mSeekDir>0)?(mNextSeek <= Player.sPlayer.getCurrentPosition()):(mNextSeek >= Player.sPlayer.getCurrentPosition()));

            } else {
            if (mNextSeek == -1) {
                mNextSeek = mLastRelativePosition = Player.sPlayer.getRelativePosition();
            }
            do {
                mNextSeek += way * SEEK_SHORT_PERMIL;
            } while ((mSeekDir>0)?(mNextSeek <= Player.sPlayer.getRelativePosition()):(mNextSeek >= Player.sPlayer.getRelativePosition()));

        }
    }

    private void onSeek(int way, boolean longPress) {
        if (log.isDebugEnabled()) log.debug("onSeek {}", way);
        cancelFadeOut();
        mHandler.removeMessages(MSG_SHOW_PROGRESS);
        mHandler.removeMessages(MSG_SEEK_RESUME);
        mHandler.removeMessages(MSG_SEEK);

        if (!mSeekWasPlaying) {
            if (Player.sPlayer.isPlaying()) {
                mSeekWasPlaying = true;
                Player.sPlayer.pause(PlayerController.STATE_SEEK);
            }
        }

        if (mSeekComplete) {
            mLongSeekTime = 0;
            mDragging = longPress;
            mSeekComplete = false;
            setNextSeekPos(way);
            Player.sPlayer.seekTo(mNextSeek);
        }
        updatePauseButton();
        if (longPress) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SEEK), SEEK_LONG_INIT_DELAY);
        }
        setProgress();
    }

    // There are two scenarios that can trigger the seekbar listener to trigger:
    //
    // The first is the user using the touchpad to adjust the posititon of the
    // seekbar's thumb. In this case onStartTrackingTouch is called followed by
    // a number of onProgressChanged notifications, concluded by onStopTrackingTouch.
    // We're setting the field "mDragging" to true for the duration of the dragging
    // session to avoid jumps in the position in case of ongoing playback.
    //
    // The second scenario involves the user operating the scroll ball, in this
    // case there WON'T BE onStartTrackingTouch/onStopTrackingTouch notifications,
    // we will simply apply the updated position without suspending regular updates.
    private SeekBar.OnSeekBarChangeListener mProgressListener = new SeekBar.OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            if (log.isDebugEnabled()) log.debug("onStartTrackingTouch");
            if (mIsStopped)
                return;
            show(FLAG_SIDE_CONTROL_BAR, 0);
            mHandler.removeMessages(MSG_SHOW_PROGRESS);
            mHandler.removeMessages(MSG_SEEK_RESUME);

            mDragging = true;
            mSeekComplete = false;

            // By removing these pending progress messages we make sure
            // that a) we won't update the progress while the user adjusts
            // the seekbar and b) once the user is done dragging the thumb
            // we will post one of these messages to the queue again and
            // this ensures that there will be exactly one message queued up.
            if (!mSeekWasPlaying) {
                if (Player.sPlayer.isPlaying()) {
                    mSeekWasPlaying = true;
                    Player.sPlayer.pause(PlayerController.STATE_SEEK);
                }
            }
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || mIsStopped) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = Player.sPlayer.getDuration();
            long newposition;
            CharSequence currentText;
            CharSequence endText;

            if (duration > 0) {
                newposition = (duration * progress) / 1000L;
                currentText = stringForTime( (int) newposition);
                endText = stringForTime( (int) (duration-newposition));
            } else {
                newposition = progress;
                mLastRelativePosition = Player.sPlayer.getRelativePosition();
                if (newposition >= 0) {
                    currentText = String.valueOf(newposition / 10) + "%";
                } else {
                    currentText = stringForTime( (int) newposition);
                }
                endText = "";
            }


            long currentProgressTime = System.currentTimeMillis();

            // don't try to seek too much
            if (Player.sPlayer.isLocalVideo() &&
                    (currentProgressTime - mLastProgressTime > SEEK_PROGRESS_TIME_THRESHOLD) &&
                    (Math.abs(progress -  mLastProgress) > SEEK_PROGRESS_THRESHOLD)) {
                mSeekComplete = false;
                Player.sPlayer.seekTo((int) newposition);
                updatePauseButton();
                mLastSeek = (int) newposition;
                mLastProgressTime = currentProgressTime;
                mLastProgress = progress;
            }
            mNextSeek = (int) newposition;

            if (mEndTime != null) {
                mEndTime.setText(endText);
            }
            if (mCurrentTime != null) {
                mCurrentTime.setText(currentText);
            }
        }

        public void onStopTrackingTouch(SeekBar bar) {
            if (mIsStopped)
                return;
            mDragging = false;

            if (mNextSeek != -1 && mNextSeek != mLastSeek) {
                Player.sPlayer.seekTo(mNextSeek);
                updatePauseButton();
            }
            mLastSeek = -1;

            if (mSeekComplete && !mDragging) {
                onSeekAndDraggingComplete();
            }
        }
    };

    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };

    private View.OnClickListener mBackwardListener = new View.OnClickListener() {
        public void onClick(View v) {
            onSeek(-1, false);
        }
    };

    private View.OnLongClickListener mBackwardLongListener = new View.OnLongClickListener() {
        public boolean onLongClick(View v) {
            if (!mIsStopped) {
                onSeek(-1, true);
                return true;
            } else {
                return false;
            }
        }
    };

    private View.OnClickListener mForwardListener = new View.OnClickListener() {
        public void onClick(View v) {
            onSeek(1, false);
        }
    };

    private View.OnLongClickListener mForwardLongListener = new View.OnLongClickListener() {
        public boolean onLongClick(View v) {
            if (!mIsStopped) {
                onSeek(1, true);
                return true;
            } else {
                return false;
            }
        }
    };

    private View.OnClickListener mFormatListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (!mIsStopped) {
                sendFadeOut(SHOW_TIMEOUT);
                /* video format change can take long time: exec asynchronously */
                mHandler.removeMessages(MSG_SWITCH_VIDEO_FORMAT);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SWITCH_VIDEO_FORMAT));
            }
        }
    };

    private View.OnClickListener fullscreenWithCutoutListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (!mIsStopped) {
                sendFadeOut(SHOW_TIMEOUT);
                /* video format change can take long time: exec asynchronously */
                mHandler.removeMessages(MSG_SWITCH_FULLSCREEN_WITH_CUTOUT);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_SWITCH_FULLSCREEN_WITH_CUTOUT));
            }
        }
    };

    private void setMusicVolume(int index) {
        //nvidia shield on android 7 has only one global volume that merged
        // mute and DND state (the latter needs a specific permission)
        try {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);

        /*
         * Since android jb 4.3, the safe volume warning dialog is displayed only with FLAG_SHOW_UI flag.
         * We don't want to always show the default ui volume, so show it only when volume is not set.
         */
            int newIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (index != newIndex)
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI);

            setVolumeBarMuteUI(index == 0);
        } catch (SecurityException ignored) {}
    }

    private void changeVolumeBy(int amount) {
        if (mIsStopped || mVolumeLevel == null)
            return;
        mVolumeLevel.incrementProgressBy(amount);
        if(mVolumeLevel2!=null)
            mVolumeLevel2.setProgress(mVolumeLevel.getProgress());
        setMusicVolume(mVolumeLevel.getProgress());
        show(FLAG_SIDE_VOLUME_BAR, Player.sPlayer.isPlaying() ? SHOW_TIMEOUT : 0);
    }

    private void updateVolumeBar() {
        if (mVolumeLevel != null && mAudioManager != null) {
            int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mVolumeLevel.setProgress(volume);
            if(mVolumeLevel2!=null)
                mVolumeLevel2.setProgress(volume);
            setVolumeBarMuteUI(volume == 0);
        }
    }

    private SeekBar.OnSeekBarChangeListener mVolumeLevelListener = new SeekBar.OnSeekBarChangeListener() {
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if( !fromuser || mAudioManager == null)
                return;

            setMusicVolume(progress);
            //updating secondary volume view

            updateVolumeBar();
        }

        public void onStartTrackingTouch(SeekBar seekBar) {
            show(FLAG_SIDE_VOLUME_BAR, 0);
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
            show(FLAG_SIDE_VOLUME_BAR, SHOW_TIMEOUT);
        }
    };

    private View.OnClickListener mVolumeUpListener = new View.OnClickListener() {
        public void onClick(View v) {
            changeVolumeBy(1);
        }
    };

    private View.OnClickListener mVolumeDownListener = new View.OnClickListener() {
        public void onClick(View v) {
            changeVolumeBy(-1);
        }
    };

    private RepeatingImageButton.RepeatListener mVolumeUpRepeatListener =
            new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            changeVolumeBy(1);
        }
    };

    private RepeatingImageButton.RepeatListener mVolumeDownRepeatListener =
            new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            changeVolumeBy(-1);
        }
    };


    private void toggleMediaControlsVisiblity(int flags) {
        if (flags == FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS && isShowing()) {
            hide(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS);
        } else if (((flags & FLAG_SIDE_CONTROL_BAR) != 0 && !mControlBarShowing) ||
                ((flags & FLAG_SIDE_SYSTEM_BAR) != 0 && !mSystemBarShowing) ||
                ((flags & FLAG_SIDE_ACTION_BAR) != 0 && !mActionBarShowing) ||
                ((flags & FLAG_SIDE_VOLUME_BAR) != 0 && !mVolumeBarShowing)) {
            show(flags, SHOW_TIMEOUT);
        } else {
            hide(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS);
        }
    }
    
    public void showControlBar() {
        show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, SHOW_TIMEOUT);
    }
    //this will be sent by activity
    public boolean onTouch(MotionEvent event){
        if (event == null) return false;
        if (isTVMenuDisplayed) mLastTouchEventTime = event.getEventTime();
        return false;
    }

    public boolean onTouch(View v, MotionEvent event) {
        if (event == null) return false;
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        if (event == null) return false;
        if (log.isDebugEnabled()) log.debug("onSingleTapConfirmed");
        if (isTVMenuDisplayed) mLastTouchEventTime = event.getEventTime();
        if(mControllerViewLeft!=null){
            View overlay = mControllerViewLeft.findViewById(R.id.help_overlay);
            if(event.getAction()==KeyEvent.ACTION_DOWN&&overlay!=null&&overlay.getVisibility()==View.VISIBLE){
                sendOverlayFadeOut(0); 
                return true;
            }
        }
        
        if (mControllerView == null) {
            return false;
        }
        if(isTVMenuDisplayed){
            showTVMenu(false);
            return false;
        }

        if(mIsLocked){
            showUnlockInstructions(true);
            return true;
        }

        if (mActionBarShowing && event.getY() < mActionBarHeight) {
            mWindow.superDispatchTouchEvent(event);
            return true;
        }

        if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY)!=0) {
            if (log.isDebugEnabled()) log.debug("onSingleTapConfirmed: BUTTON_SECONDARY");
            return false;
        }

        // Check if touch is in central screen zone (middle third both horizontally and vertically)
        if (isTouchInCentralZone(event)) {
            if (log.isDebugEnabled()) log.debug("onSingleTapConfirmed: central zone touch");
            handleCentralZoneTouch();
        } else {
            // Original behavior for touches outside central zone
            int flags = FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS;

            if (!mVolumeBarEnabled) {
                flags &= ~FLAG_SIDE_VOLUME_BAR;
            }
            if (mIsStopped || !Player.sPlayer.isInPlaybackState()) {
                flags &= ~(FLAG_SIDE_CONTROL_BAR);
            }

            toggleMediaControlsVisiblity(flags);
        }
        
        switchMode(false);
        return true;
    }

    private boolean isTouchInCentralZone(MotionEvent event) {
        if (mControllerView == null) return false;
        
        int viewWidth = mControllerView.getWidth();
        int viewHeight = mControllerView.getHeight();

        // Define central zone as middle third horizontally and Vertically.
        float centralZoneLeft = viewWidth / 3.0f;
        float centralZoneRight = viewWidth * 2.0f / 3.0f;
        float centralZoneTop = viewHeight / 4.0f;
        float centralZoneBottom = viewHeight * 3.0f / 4.0f;
        
        float touchX = event.getX();
        float touchY = event.getY();
        
        return (touchX >= centralZoneLeft && touchX <= centralZoneRight) && (touchY >= centralZoneTop && touchY <= centralZoneBottom) ;
    }

    private void handleCentralZoneTouch() {
        if (mIsStopped || !Player.sPlayer.isInPlaybackState()) {
            // If not in playback state, just toggle OSD visibility
            int flags = FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS;
            if (!mVolumeBarEnabled) {
                flags &= ~FLAG_SIDE_VOLUME_BAR;
            }
            toggleMediaControlsVisiblity(flags);
            return;
        }

        // Central zone behavior: always toggle play/pause, show OSD when pausing, hide when resuming
        if (Player.sPlayer.isPlaying()) {
            //Update the controls visibility 1st, so the Fadeout takes effect.
            int flags = FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS;
            if (!mVolumeBarEnabled) {
                flags &= ~FLAG_SIDE_VOLUME_BAR;
            }
            show(flags, pauseTimeout);

            // If playing, pause and show controls (keep them visible)
            Player.sPlayer.pause(PlayerController.STATE_NORMAL);
            updatePausePlay();
        } else {
            // If paused, resume and hide controls
            Player.sPlayer.start(PlayerController.STATE_NORMAL);
            updatePausePlay();
            hide();
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        scrollGestureVertical = 0f;
        scrollGestureHorizontal = 0f;
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {}

    @Override
    public boolean onSingleTapUp(MotionEvent e) { return false; }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (e1 == null || e2 == null) return false;

        if(mIsLocked){
            showUnlockInstructions(true);
            return true;
        }

        float deltaY = e2.getY() - e1.getY();

        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        if (scrollGestureHorizontal == 0 || scrollGestureVertical == 0) {
            scrollGestureHorizontal = 0.0001f;
            scrollGestureVertical = 0.0001f;
            return false;
        }

        // Exclude border
        if (e1.getY() < BORDER_WIDTH || e1.getX() < BORDER_WIDTH ||
                e1.getY() > screenHeight - BORDER_WIDTH || e1.getX() > screenWidth - BORDER_WIDTH)
            return false;

        scrollGestureHorizontal += distanceX;
        scrollGestureVertical += distanceY;
        float halfWidth = screenWidth / 2f;

        //Check the angle of the scroll, and make sure its in range.
        float angleRadians = (float) Math.atan2(distanceY, distanceX);
        float angleDegrees = (float) Math.toDegrees(angleRadians);
        if (angleDegrees < 0)
            angleDegrees += 360;

        // Define upward/downward angle ranges
        boolean isVerticalScroll = (angleDegrees >= 90-SCROLL_ANGLE && angleDegrees <= 90+SCROLL_ANGLE) || (angleDegrees >= 270-SCROLL_ANGLE && angleDegrees <= 270+SCROLL_ANGLE);
        //boolean isVerticalScroll = (Math.abs(distanceY) > Math.abs(distanceX));

        if (Math.abs(scrollGestureVertical) > SCROLL_THRESHOLD && isVerticalScroll) {
            if (e1.getX() < halfWidth) { // left screen part
                if (log.isDebugEnabled()) log.debug("onScroll: left screen part, direction={}", (scrollGestureVertical > 0 ? "up" : "down"));
                scrollIncrementalBrightnessUpdate(scrollGestureVertical > 0);
            } else { // right screen part
                if (log.isDebugEnabled()) log.debug("onScroll: left screen part, direction={}", (scrollGestureVertical > 0 ? "up" : "down"));
                scrollIncrementalVolumeUpdate(scrollGestureVertical > 0);
            }
            scrollGestureVertical = 0.0001f;
        }

        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {}

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) { return false; }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {

        if(mIsLocked){
            showUnlockInstructions(true);
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (log.isDebugEnabled()) log.debug("onDoubleTapEvent");
            float x = e.getX();
            float y = e.getY();
            if (mControllerView == null) return false;
            float viewWidth = (float) mControllerView.getWidth();
            float viewHeight = (float) mControllerView.getHeight();
            
            // Determine if we're in top 75% (seek) or bottom 25% (audio speed) zone
            boolean isTopZone = y < (viewHeight * 0.75f);
            
            if (x < viewWidth / 3) { // left region
                if (isTopZone) {
                    // Top 75% left: fast rewind
                    if (Player.sPlayer.canSeekBackward() && mSeekKeyDirection != -1) {
                        if (mOsdLeftTextView != null) {
                            mOsdLeftTextView.setText("");
                            Drawable osdIcon = getDrawable(mContext, R.drawable.media_fast_rewind);
                            mOsdLeftTextView.setCompoundDrawablesWithIntrinsicBounds(osdIcon, null, null, null);
                            mOsdLeftTextView.setVisibility(View.VISIBLE);
                            hideOsdHandler.removeCallbacks(hideOsdRunnable);
                            hideOsdHandler.postDelayed(hideOsdRunnable, 300);
                        }
                        Player.sPlayer.seekTo(Player.sPlayer.getCurrentPosition() - 10000);
                    }
                } else {
                    // Bottom 25% left: decrease audio speed
                    if (VideoPreferencesCommon.isAudioSpeedEnabled(mContext)) {
                        PlayerService.sPlayerService.decrementAudioSpeed();
                        showAudioSpeedOSD(PlayerService.sPlayerService.getAudioSpeed());
                    }
                }
            } else if (x > viewWidth / 1.5) {  // right region
                if (isTopZone) {
                    // Top 75% right: fast forward
                    if (Player.sPlayer.canSeekBackward() && mSeekKeyDirection != 1) {
                        if (mOsdRightTextView != null) {
                            mOsdRightTextView.setText("");
                            Drawable osdIcon = getDrawable(mContext, R.drawable.media_fast_forward);
                            mOsdRightTextView.setCompoundDrawablesWithIntrinsicBounds(osdIcon, null, null, null);
                            mOsdRightTextView.setVisibility(View.VISIBLE);
                            hideOsdHandler.removeCallbacks(hideOsdRunnable);
                            hideOsdHandler.postDelayed(hideOsdRunnable, 300);
                        }
                        Player.sPlayer.seekTo(Player.sPlayer.getCurrentPosition() + 10000);
                    }
                } else {
                    // Bottom 25% right: increase audio speed
                    if (VideoPreferencesCommon.isAudioSpeedEnabled(mContext)) {
                        PlayerService.sPlayerService.incrementAudioSpeed();
                        showAudioSpeedOSD(PlayerService.sPlayerService.getAudioSpeed());
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {
        if(!isTVMenuDisplayed){
            if (log.isDebugEnabled()) log.debug("onGenericMotion : event={}", event);
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.M&&event.getActionButton()==MotionEvent.BUTTON_PRIMARY) //
                return false;
            int action = event.getAction();

            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_EXIT) {
                // Ignore events sent by the remote control when it is in pointer mode
                return false;
            }

            show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, 3000);

            return true;
        }
        return false;
    }

    private Handler hideOsdHandler = new Handler(Looper.getMainLooper());
    private Runnable hideOsdRunnable = new Runnable() {
        @Override
        public void run() {
            if (log.isDebugEnabled()) log.debug("hideOsdRunnable");
            // Hide both the fast forward and fast backward icons
            if (mOsdLeftTextView != null) mOsdLeftTextView.setVisibility(View.INVISIBLE);
            if (mOsdRightTextView != null) mOsdRightTextView.setVisibility(View.INVISIBLE);
        }
    };

    private void scrollIncrementalVolumeUpdate(boolean increase) {
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int newVolume;
        Drawable volumeIcon;
        if (currentVolume == 0) volumeIcon = getDrawable(mContext, R.drawable.ic_volume_off);
        else volumeIcon = getDrawable(mContext, R.drawable.ic_volume);
        if (mOsdLeftTextView != null) {
            mOsdLeftTextView.setCompoundDrawablesWithIntrinsicBounds(volumeIcon, null, null, null);
            mOsdLeftTextView.setText(String.valueOf(currentVolume));
            mOsdLeftTextView.setVisibility(View.VISIBLE);
        }
        if (increase) newVolume = currentVolume + 1;
        else newVolume = currentVolume - 1;
        newVolume = Math.max(0, Math.min(newVolume, maxVolume)); // Constrain the value between 0 and maxVolume
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
        if (mOsdLeftTextView != null) mOsdLeftTextView.setText(String.valueOf(newVolume));
        if (log.isDebugEnabled()) log.debug("scrollIncrementalVolumeUpdate: increase={}, currentVolume={}, maxVolume={}, newVolume={}", increase, currentVolume, maxVolume, newVolume);
        if (newVolume == 0) volumeIcon = getDrawable(mContext, R.drawable.ic_volume_off);
        else volumeIcon = getDrawable(mContext, R.drawable.ic_volume);
        if (mOsdLeftTextView != null) mOsdLeftTextView.setCompoundDrawablesWithIntrinsicBounds(volumeIcon, null, null, null);
        hideOsdHandler.removeCallbacks(hideOsdRunnable);
        hideOsdHandler.postDelayed(hideOsdRunnable, 300);
        updateVolumeBar();
    }

    private void scrollIncrementalBrightnessUpdate(boolean increase) {
        float currentBrightness = PlayerBrightnessManager.getBrightness(mWindow);
        int currentIntBrightness= PlayerBrightnessManager.getLinearBrightness(mWindow);
        int newIntBrightness;
        Drawable brightnessIcon = getDrawable(mContext, R.drawable.ic_brightness);
        if (mOsdRightTextView != null) {
            mOsdRightTextView.setCompoundDrawablesWithIntrinsicBounds(brightnessIcon, null, null, null);
            mOsdRightTextView.setText(String.valueOf(currentIntBrightness));
            mOsdRightTextView.setVisibility(View.VISIBLE);
        }
        if (increase) newIntBrightness = currentIntBrightness + 1;
        else newIntBrightness = currentIntBrightness - 1;
        newIntBrightness = Math.max(0, Math.min(newIntBrightness, 30)); // Constrain the brightness between 0 and maxBrightness
        PlayerBrightnessManager.setLinearBrightness(newIntBrightness, increase, mWindow);
        if (mOsdRightTextView != null) mOsdRightTextView.setCompoundDrawablesWithIntrinsicBounds(brightnessIcon, null, null, null);
        if (log.isDebugEnabled()) log.debug("scrollIncrementalBrightnessUpdate: increase={}, currentBrightness={}, maxBrightness={}, newBrightness={}", increase, currentBrightness, 30, newIntBrightness);
        hideOsdHandler.removeCallbacks(hideOsdRunnable);
        hideOsdHandler.postDelayed(hideOsdRunnable, 300);
    }

    public void showAudioSpeedOSD(float audioSpeed) {
        String speedText = String.format("%.2fx", audioSpeed);
        if (mOsdRightTextView != null) {
            mOsdRightTextView.setText(speedText);
            mOsdRightTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            mOsdRightTextView.setVisibility(View.VISIBLE);
            hideOsdHandler.removeCallbacks(hideOsdRunnable);
            hideOsdHandler.postDelayed(hideOsdRunnable, 300);
        }
        if (log.isDebugEnabled()) log.debug("showAudioSpeedOSD: audioSpeed={}", audioSpeed);
    }

    public boolean hasFocus() {
        // TODO Auto-generated method stub
        return mControlBar.isFocused() || mVolumeBar.isFocused();
    }

    public boolean onKey(int keyCode, KeyEvent event) {
        if (log.isDebugEnabled()) log.debug("onKey()");
        if (mLastTouchEventTime == event.getEventTime()) {
            return true;
        }
        if(mIsLocked){
            showUnlockInstructions(true);
            return true;
        }
        switchMode(true);
        
        if (isTVMenuDisplayed) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && mTVMenuAdapter != null) {
                switch(keyCode) {
                    case KeyEvent.KEYCODE_ESCAPE:
                    case KeyEvent.KEYCODE_BACK:
                    case KeyEvent.KEYCODE_MENU:
                        showTVMenu(false);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:    
                        mTVMenuAdapter.goToPrevious();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        mTVMenuAdapter.goToNext();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_UP:
                        return false;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        return false;
                }
            }
        }

        if (mControllerViewLeft != null) {
            View overlay = mControllerViewLeft.findViewById(R.id.help_overlay);
    
            if (event.getAction() == KeyEvent.ACTION_DOWN && overlay != null && overlay.getVisibility() == View.VISIBLE) {
                sendOverlayFadeOut(0);
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_MENU))  {
                if (!mTVMenuAdapter.isCreated() && mContext instanceof PlayerActivity) {
                    ((PlayerActivity) mContext).createPlayerTVMenu();
                }
                if (!isTVMenuDisplayed) {
                    showTVMenu(true);
                    if (log.isDebugEnabled()) log.debug("onKey, showing menu");
                    return true;
                }
            }
        }

            if (mVolumeBarEnabled) {
                final boolean passThroughHardwareVolumeKeys = ArchosFeatures.isAndroidTV(mContext);
                switch(keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                        // Let framework handle TV+HDMI keys so CEC/ARC controls external sink.
                        if (passThroughHardwareVolumeKeys) return false;
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            changeVolumeBy(-1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_D:
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            changeVolumeBy(-1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_UP:
                        if (passThroughHardwareVolumeKeys) return false;
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            changeVolumeBy(1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_U:
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            changeVolumeBy(1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_MUTE:
                        if (passThroughHardwareVolumeKeys) return false;
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            setMusicVolume(0);
                            // Show volume slider
                            show(FLAG_SIDE_VOLUME_BAR, Player.sPlayer.isPlaying() ? SHOW_TIMEOUT : 0);
                            return true;
                        }
                        return true;
                }
            }

            if (!mIsStopped && Player.sPlayer.isInPlaybackState()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_0:
                        case KeyEvent.KEYCODE_1:
                        case KeyEvent.KEYCODE_2:
                        case KeyEvent.KEYCODE_3:
                        case KeyEvent.KEYCODE_4:
                        case KeyEvent.KEYCODE_5:
                        case KeyEvent.KEYCODE_6:
                        case KeyEvent.KEYCODE_7:
                        case KeyEvent.KEYCODE_8:
                        case KeyEvent.KEYCODE_9:
                            seekToPercent((keyCode - KeyEvent.KEYCODE_0)*10);
                            return true;

                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            // Don't want to start music if the VideoPlayer is running
                            return true;
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            showControlBar();
                            if (log.isDebugEnabled()) log.debug("onKey: dpad down");
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            if (log.isDebugEnabled()) log.debug("onKey: next");
                            if (Player.sPlayer.canSeekForward() && mSeekKeyDirection != 1) {
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                                mSeekKeyDirection = 1;
                                onSeek(1, true);
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            if (log.isDebugEnabled()) log.debug("onKey: previous");
                            if (Player.sPlayer.canSeekBackward() && mSeekKeyDirection != -1) {
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                                mSeekKeyDirection = -1;
                                onSeek(-1, true);
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            return true; // do nothing
                        case KeyEvent.KEYCODE_Z:
                            mSettings.setSubtitleDelay(-100);
                            return true;
                        case KeyEvent.KEYCODE_X:
                            mSettings.setSubtitleDelay(100);
                            return true;
                        case KeyEvent.KEYCODE_SLASH:
                            if (mVolumeBarEnabled)
                                changeVolumeBy(-1);
                            return true;
                        case KeyEvent.KEYCODE_STAR:
                            if (mVolumeBarEnabled)
                                changeVolumeBy(1);
                            return true;
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_MENU:
                            if (isTVMode || TVUtils.isTV(mContext)) {
                                return true;
                            }
                            if (Player.sPlayer.isBusy()) {
                                /* Don't show the menu if the MediaPlayer is busy */
                                return true;
                            }
                            if (!mActionBarShowing) {
                                showActionBar(true);
                                // don't consume, needs to trigger menu
                                return false;
                            }
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            if (log.isDebugEnabled()) log.debug("onKey: play");
                            if (!Player.sPlayer.isPlaying()) {
                                Player.sPlayer.start(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                show();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            if (Player.sPlayer.isPlaying()) {
                                if (log.isDebugEnabled()) log.debug("onKey: pause");
                                Player.sPlayer.pause(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                show();
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        case KeyEvent.KEYCODE_P:
                        case KeyEvent.KEYCODE_SPACE:
                            if (Player.sPlayer.isPlaying()) {
                                if (log.isDebugEnabled()) log.debug("onKey: play/pause: pause");
                                Player.sPlayer.pause(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, pauseTimeout);
                            } else {
                                if (log.isDebugEnabled()) log.debug("onKey: play/pause: play");
                                Player.sPlayer.start(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                hide();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            if (Player.sPlayer.isPlaying()) {
                                if (log.isDebugEnabled()) log.debug("onKey: stop, thus pause");
                                Player.sPlayer.pause(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                show();
                                Player.sPlayer.finishActivity();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            mSeekKeyDirection = 0;
                            if (log.isDebugEnabled()) log.debug("onKey, button up");
                            return true;
                        case KeyEvent.KEYCODE_O:
                        case KeyEvent.KEYCODE_PROG_RED:
                            if (isShowing())
                                hide();
                            else
                                show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, 0);
                            return true;
                        case KeyEvent.KEYCODE_F:
                        case KeyEvent.KEYCODE_PROG_GREEN:
                            mSurfaceController.switchVideoFormat();
                            return true;
                        case KeyEvent.KEYCODE_J:
                        case KeyEvent.KEYCODE_S:
                        case KeyEvent.KEYCODE_PROG_YELLOW:
                            mSettings.switchSubtitleTrack();
                            return true;
                        case KeyEvent.KEYCODE_POUND:
                        case KeyEvent.KEYCODE_A:
                        case KeyEvent.KEYCODE_PROG_BLUE:
                            mSettings.switchAudioTrack();
                            return true;
                        case KeyEvent.KEYCODE_CHANNEL_DOWN:
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        case KeyEvent.KEYCODE_G:
                            if (VideoPreferencesCommon.isAudioSpeedEnabled(mContext)) {
                                PlayerService.sPlayerService.decrementAudioSpeed();
                                showAudioSpeedOSD(PlayerService.sPlayerService.getAudioSpeed());
                            }
                            return true;
                        case KeyEvent.KEYCODE_CHANNEL_UP:
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                        case KeyEvent.KEYCODE_H:
                            if (VideoPreferencesCommon.isAudioSpeedEnabled(mContext)) {
                                PlayerService.sPlayerService.incrementAudioSpeed();
                                showAudioSpeedOSD(PlayerService.sPlayerService.getAudioSpeed());
                            }
                            return true;
                    }
                }
            }

        return false;
    }
    Toast mSeekToast = null;
    private void seekToPercent(int percent) {
        if(mSeekToast!=null)
            mSeekToast.cancel();
        if (Player.sPlayer.canSeekForward()&&Player.sPlayer.canSeekBackward()){
            Player.sPlayer.seekTo(Player.sPlayer.getDuration()/100*percent);
            mSeekToast = Toast.makeText(mContext, mContext.getString(R.string.seek_to_percentage, String.valueOf(percent)), Toast.LENGTH_SHORT);
            mSeekToast.show();
        }
    }

    private void switchMode(boolean tv) {
        if (TVUtils.isTV(mContext)) {
            tv = true;
        }
        isTVMode=tv;
        // TODO Auto-generated method stub
        //for view not to be split before window attached
        if(mControllerViewLeft!=null && !(tv && mControlBarShowing)){
            if(tv)
                setUIMode(UIMode);
            else{
                setNormalMode();
                showTVMenu(false);
            }
            //hide action menu
            //hide button (pause, etc)
            if( mControllerViewLeft.findViewById(R.id.pause)!=null)
                mControllerViewLeft.findViewById(R.id.pause).setVisibility(tv?View.INVISIBLE:View.VISIBLE);
            if( mControllerViewLeft.findViewById(R.id.backward)!=null)
                mControllerViewLeft.findViewById(R.id.backward).setVisibility(tv?View.GONE:View.VISIBLE);
            if( mControllerViewLeft.findViewById(R.id.forward)!=null)
                mControllerViewLeft.findViewById(R.id.forward).setVisibility(tv?View.GONE:View.VISIBLE);
            if( mControllerViewLeft.findViewById(R.id.format)!=null)
                mControllerViewLeft.findViewById(R.id.format).setVisibility(tv?View.INVISIBLE:View.VISIBLE);
            if(mControllerViewRight!=null){
                if( mControllerViewRight.findViewById(R.id.pause)!=null)
                    mControllerViewRight.findViewById(R.id.pause).setVisibility(tv?View.INVISIBLE:View.VISIBLE);
                if( mControllerViewRight.findViewById(R.id.backward)!=null)
                    mControllerViewRight.findViewById(R.id.backward).setVisibility(tv?View.GONE:View.VISIBLE);
                if( mControllerViewRight.findViewById(R.id.forward)!=null)
                    mControllerViewRight.findViewById(R.id.forward).setVisibility(tv?View.GONE:View.VISIBLE);
                if( mControllerViewRight.findViewById(R.id.format)!=null)
                    mControllerViewRight.findViewById(R.id.format).setVisibility(tv?View.INVISIBLE:View.VISIBLE);
            }
            
        }

        if(mContext instanceof PlayerActivity)
            ((PlayerActivity)mContext).switchMode(tv);
    }
    
    private void setVolumeBarMuteUI(boolean mute) {
        if (mVolumeBar==null) return; //better safe than...
        if (mute) {
            mVolumeBar.setAlpha(0.5f);
        } else {
            mVolumeBar.setAlpha(1.0f);
        }
    }

    public void handleJoystickEvent(int joystickZone) {
        if (mControllerView == null)
            return;
        mJoystickZone = joystickZone;

        if ((mJoystickZone == MediaUtils.JOYSTICK_ZONE_RIGHT || mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_RIGHT) && !mJoystickSeekingActive) {
            // Only call onSeek() once when starting to seek but set longPress=true so that
            // the seek event will be sent periodically until the joystick is released
            if (log.isDebugEnabled()) log.debug("handleJoystickEvent, Joystick moved to the right => start seeking forward");
            if(mControlBar.isFocused()){
                mJoystickSeekingActive = true;
                onSeek(1, true);
            }
        }
        else if ((mJoystickZone == MediaUtils.JOYSTICK_ZONE_LEFT || mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_LEFT) && !mJoystickSeekingActive) {
            // Only call onSeek() once when starting to seek but set longPress=true so that
            // the seek event will be sent periodically until the joystick is released
            if (log.isDebugEnabled()) log.debug("handleJoystickEvent, Joystick moved to the left => start seeking backward");
            if(mControlBar.isFocused()){
                mJoystickSeekingActive = true;
                onSeek(-1, true);
            }
        }
        else if (mJoystickZone == MediaUtils.JOYSTICK_ZONE_CENTER && mJoystickSeekingActive) {
            // The joystick is released (i.e. is back in the dead zone)
            if (log.isDebugEnabled()) log.debug("handleJoystickEvent, Joystick released => stop seeking");
            mJoystickSeekingActive = false;
            mSeekKeyDirection = 0;
        }
    }

    // STATUS_BAR_DISABLE_* are internal system flags with no public API replacement;
    // they have no effect for regular (non-system) apps on modern Android.
    @SuppressWarnings("deprecation")
    public void enableAllNotifications() {
        if (log.isDebugEnabled()) log.debug("Enable all notifications");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    @SuppressWarnings("deprecation")
    public void enableNotificationAlerts() {
        if (log.isDebugEnabled()) log.debug("Enable notification alerts only");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    @SuppressWarnings("deprecation")
    public void disableNotifications() {
        if (log.isDebugEnabled()) log.debug("Disable all notifications");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    private void setRecursiveOnTouchListener(OnTouchListener t, View v){
        for(int i=0; i<((ViewGroup)v).getChildCount(); ++i) {
            View nextChild = ((ViewGroup)v).getChildAt(i);
            nextChild.setOnTouchListener(t);
        }
    }

    private void inflateRightView(){
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mControllerViewRight= inflater.inflate(R.layout.player_controller_inside, null);
        initControllerView(mControllerViewRight, false);
        playerControllersContainer.addView( mControllerViewRight);
        mTVMenuView2 = mControllerViewRight.findViewById(R.id.my_recycler_view);
        if(mTVMenuAdapter!=null && !mTVMenuAdapter.hasSlaverView())
            mTVMenuAdapter.initializeSlaveView((FrameLayout)mTVMenuView2);
    }
    public void setSideBySide() {
        // TODO Auto-generated method stub 	
        if(mControllerViewLeft!=null){
            if(mControllerViewRight==null){
                inflateRightView();
                mTVMenuAdapter.refocus();
            }
            PlayerControlsRelativeLayout plc = (PlayerControlsRelativeLayout)mControllerViewLeft.findViewById(R.id.playerControllers);
            PlayerControlsRelativeLayout plc2 = (PlayerControlsRelativeLayout)mControllerViewRight.findViewById(R.id.playerControllers);
            //right menu initialization

            if(plc!=null&&plc2!=null){
                splitView=true;
                plc.setSideBySide(true,1);
                plc2.setSideBySide(true,2);
                if(mControlBar2!=null&&mControlBar!=null)
                    setVisibility(mControlBar2, mControlBar.getVisibility()==View.VISIBLE, true);
                if(mVolumeBar2!=null&&mVolumeBar!=null)
                    setVisibility(mVolumeBar2, mVolumeBar.getVisibility()==View.VISIBLE, true);
                if(mTVMenuView2!=null)
                    setVisibility(mTVMenuView2, isTVMenuDisplayed, true);
                if(mControllerViewRight!=null)
                    mControllerViewRight.setVisibility(View.VISIBLE);
               
            }
        }

    }
    public void setTopBottom(){
        if(mControllerViewLeft!=null){
            if(mControllerViewRight==null){
                inflateRightView();
                mTVMenuAdapter.refocus();
            }
            PlayerControlsRelativeLayout plc = (PlayerControlsRelativeLayout)mControllerViewLeft.findViewById(R.id.playerControllers);
            PlayerControlsRelativeLayout plc2 = (PlayerControlsRelativeLayout)mControllerViewRight.findViewById(R.id.playerControllers);
            if(plc!=null&&plc2!=null){
                splitView=true;
                plc.setTopBottom(true, 1);
                plc2.setTopBottom(true, 2);
                if(mControlBar2!=null&&mControlBar!=null)
                    setVisibility(mControlBar2, mControlBar.getVisibility()==View.VISIBLE, true);
                if(mVolumeBar2!=null&&mVolumeBar!=null)
                    setVisibility(mVolumeBar2, mVolumeBar.getVisibility()==View.VISIBLE, true);
                if(mTVMenuView2!=null)
                    setVisibility(mTVMenuView2, isTVMenuDisplayed, true);
                if(mControllerViewRight!=null)
                    mControllerViewRight.setVisibility(View.VISIBLE);
                
            }
        }
    }
    public void setNormalMode(){
        if(mControllerViewLeft!=null && mControllerViewRight!=null){
            PlayerControlsRelativeLayout plc = (PlayerControlsRelativeLayout)mControllerViewLeft.findViewById(R.id.playerControllers);
            PlayerControlsRelativeLayout plc2 = (PlayerControlsRelativeLayout)mControllerViewRight.findViewById(R.id.playerControllers);
            if(plc!=null&&plc2!=null){
                plc.setSideBySide(false, 1);
                plc2.setSideBySide(false, 2);
            }
            splitView=false;
            if(mControllerViewRight!=null){
                if(mControlBar2!=null)
                    mControlBar2.setVisibility(View.GONE);
                if(mVolumeBar2!=null)
                    mVolumeBar2.setVisibility(View.GONE);
                if(mTVMenuView2!=null)
                    mTVMenuView2.setVisibility(View.GONE);           
                if(mControllerViewRight!=null)
                   mControllerViewRight.setVisibility(View.GONE);   
                mControllerViewRight=null;
                mControlBar2=null;
                mVolumeBar2=null;
                mTVMenuView2=null;
                mTVMenuAdapter.removeSlaveViews();
                playerControllersContainer.removeView(mControllerViewRight);
                
            }
        }
    }

    public void setUIMode(int mode) {
        UIMode = mode;
        if (log.isDebugEnabled()) log.debug("setUIMode, setting ui mode {}", mode);
        // TODO Auto-generated method stub
        if(mode==VideoEffect.SBS_MODE){
        	  if(!mControlBarShowing)
        		  setSideBySide();
        }
        else if(mode==VideoEffect.TB_MODE){
        	  if(!mControlBarShowing)
        		  setTopBottom();
        }
        else{   
           setNormalMode();
        }   
    }

    public void showTVMenu(boolean show){
        if (show && mContext instanceof PlayerActivity) {
            ((PlayerActivity) mContext).refreshPlayModeIntroSummary();
        }
        if (mTVMenuView != null) {
            mTVMenuView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show && !isTVMenuDisplayed) {
                isTVMenuDisplayed = show;
                showControlBar(false);
                mTVMenuAdapter.focusStart();
            }
            else if (!show && isTVMenuDisplayed) {
                mTVMenuAdapter.hideAnimation();
            }
            else if (show && isTVMenuDisplayed) {
                mTVMenuAdapter.refocus();
            }
            if (mClock!=null) {
                setVisibility(mClock, show && !splitView, true);
            }
        }
        if(mTVMenuView2!=null){  
            mTVMenuView2.setVisibility(show&&splitView?View.VISIBLE:View.GONE);
        }
        if(show){
            mSeekKeyDirection = 0;
            hide();
        }
        else{
            //destroy dialogs
            dismissTVCardDialog();
        }
        isTVMenuDisplayed=show;
    }

    private boolean dismissTVCardDialog() {
        if (tvCardDialog == null || tvCardDialog.getVisibility() != View.VISIBLE)
            return false;
        log.info("Back navigation: active TV card dialog found");
        TVCardDialog dialog = tvCardDialog;
        tvCardDialog = null;
        dialog.handleBackPressed();
        return true;
    }

    public boolean handleBackPressed() {
        log.info("Back navigation: TV menu displayed={}, card dialog active={}",
                isTVMenuDisplayed,
                tvCardDialog != null && tvCardDialog.getVisibility() == View.VISIBLE);
        if (dismissTVCardDialog())
            return true;
        if (isTVMenuDisplayed) {
            showTVMenu(false);
            return true;
        }
        return false;
    }

    public boolean isTVMenuDisplayed() {
        // TODO Auto-generated method stub
        return isTVMenuDisplayed;
    }
    public TVMenuAdapter getTVMenuAdapter(){
        return mTVMenuAdapter;
    }


    public void updateClock() {
        if (mClock!=null) {
            mClock.setText(mDateFormat.format(new Date()));
        }
    }

    private View mUnlockInstructions;
    private View mUnlockInstructions2;
    private boolean mIsLocked;

    public void lock(){
        mIsLocked = true;
        // Hide central play icon when locked
        if (mCentralPlayIcon != null) {
            mCentralPlayIcon.setVisibility(View.GONE);
            if (mPlayPauseTouchZone != null)
                mPlayPauseTouchZone.setVisibility(View.INVISIBLE);
        }
        hide();
        showUnlockInstructions(true);
    }
    public void showUnlockInstructions(boolean visible) {
        if(visible) {

            final View.OnLongClickListener listener = new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    mUnlockInstructions.setVisibility(View.GONE);
                    mIsLocked = false;
                    // Restore central play icon if video is paused
                    updatePausePlay();
                    show();
                    return false;
                }
            };
            if (mUnlockInstructions != null) {
                mUnlockInstructions.setVisibility(View.VISIBLE);
                mUnlockInstructions.findViewById(R.id.unlock_button).setOnLongClickListener(listener);
                sendFadeOut(SHOW_TIMEOUT);
            }

            if(mUnlockInstructions2!=null){
                mUnlockInstructions2.setVisibility(View.VISIBLE);
                mUnlockInstructions2.findViewById(R.id.unlock_button).setOnLongClickListener(listener);
            }
        }
        else{
            if (mUnlockInstructions != null)
                mUnlockInstructions.setVisibility(View.GONE);
            if (mUnlockInstructions2 != null)
                mUnlockInstructions2.setVisibility(View.GONE);
        }
    }
    
    public void setFullscreenWithCutoutButtonIcon(boolean isFullscreenWithCutout) {
        if (mFullscreenWithCutoutButton != null)
            mFullscreenWithCutoutButton.setImageResource(isFullscreenWithCutout ? R.drawable.video_fullscreen_cutout_on : mSurfaceController.mCutBothSidesX ? R.drawable.video_fullscreen_cutout_off_equal : R.drawable.video_fullscreen_cutout_off);
    }
}
