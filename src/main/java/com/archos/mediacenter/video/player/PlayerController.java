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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;

import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
    private int                 mLayoutWidth;
    private int                 mLayoutHeight;
    final private boolean       mSw600dp; // are we using the sw600dp ressources?

    private int                 mActionBarHeight = 0;
    private int                 mSystemBarHeight = 0;
    private SurfaceController   mSurfaceController;
    private Settings            mSettings;
    private OnShowHideListener  mOnShowHideListener = null;
    private View                mControllerView;
    private View                mControllerViewLeft;
    private View                mControllerViewRight;
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
    private ImageButton         mBackwardButton2;
    private ImageButton         mForwardButton2;
    private ImageButton         mFormatButton2;
    private TextView            mOsdLeftTextView;
    private TextView            mOsdRightTextView;
    private float               scrollGestureVertical = 0f;
    private float               scrollGestureHorizontal = 0f;
    private final float         BORDER_WIDTH = MiscUtils.dp2Px(24);
    private final float         SCROLL_THRESHOLD = MiscUtils.dp2Px(16);
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

    private boolean             mControlBarShowing;
    private boolean             mSystemBarShowing;
    private boolean             mSystemBarGone;
    private boolean             mActionBarShowing;
    private boolean             mVolumeBarShowing;

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

    private GestureDetector gestureDetector;
    private float currentBrightness;

    public interface Settings {
        void switchSubtitleTrack();
        void switchAudioTrack();
        void setSubtitleDelay(int delay);
    }

    public interface OnShowHideListener {
        void onShow();
        void onHide();
        void onBottomBarHeightChange(int height);
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

        mSw600dp = mContext.getResources().getConfiguration().smallestScreenWidthDp >= 600;

        mVideoTitle = (TextView) inflater.inflate(R.layout.video_title, null);
        // Default to GONE, enable it if you want it
        mVideoTitle.setVisibility(View.GONE);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(mVideoTitle);
        manualVisibilityChange=false;

        mSystemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        mSystemUiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
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
        }, getActionBarView());
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
    }

    public void addToMenuContainer(View v){
        View container1 = mControllerViewLeft.findViewById(R.id.tv_menu_container);
        if(container1!=null && container1 instanceof FrameLayout){
            ((FrameLayout)container1).addView(v);
        }
        if(mControllerViewRight!=null){
            View container2 = mControllerViewRight.findViewById(R.id.tv_menu_container);
            if(container2!=null && container2 instanceof FrameLayout){
                //then we have to inflate a slave view
                if(v.findViewById(R.id.card_view)!=null&&v.findViewById(R.id.card_view)instanceof TVCardDialog){
                    tvCardDialog  =(TVCardDialog) v.findViewById(R.id.card_view);
                    ((FrameLayout)container2).addView(((TVCardDialog)v.findViewById(R.id.card_view)).createSlaveView());
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
            this.mControlBar=mControlBar;
            this.mPauseButton=mPauseButton;
            this.mFormatButton=mFormatButton ;
            this.mBackwardButton=mBackwardButton ;
            this.mForwardButton=mForwardButton ;
            this.mProgress=mProgress ;
            mEndTime = (TextView) v.findViewById(R.id.time);
            mSeekState = v.findViewById(R.id.seek_state);
            mCurrentTime = (TextView) v.findViewById(R.id.time_current);
            // The clock is only for actual leanback devices
            if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK) || isChromeOS(mContext)) {
                log.debug("initControllerView: FEATURE_LEANBACK");
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
            } else log.debug("initControllerView: no FEATURE_LEANBACK");
        }
        else{
            this.mUnlockInstructions2 = unlockInstructions;
            this.mVolumeBar2 = mVolumeBar;
            this.mControlBar2=mControlBar;
            this.mPauseButton2=mPauseButton; 
            this.mFormatButton2=mFormatButton;
            this.mBackwardButton2=mBackwardButton;
            this.mProgress2=mProgress;
            this.mForwardButton2=mForwardButton ;
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
        log.debug("CONFIG setSizes layout: " + mLayoutWidth + "x" + mLayoutHeight + " / display: " + displayWidth + "x" + displayHeight + ", systemBarHeight: " + mSystemBarHeight);
        if (mControllerView != null) {
            log.debug("CONFIG setSizes, mControllerView != null, recreate whole layout");
            // size changed and maybe orientation too, recreate the whole layout
            detachWindow();
            attachWindow();
        } else {
            log.debug("CONFIG setSizes, mControllerView == null, doing nothing");
        }
    }

    public int getNavigationBarHeight() {
        int navigationBarHeight = 0;
        Resources resources = mContext.getResources();
        int resourceIdNavBarHeight = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        // check if navigation bar is displayed because chromeos reports a navigation_bar_height of 84 but there is none displayed
        if (resourceIdNavBarHeight > 0 && hasNavigationBar(resources))
            navigationBarHeight = resources.getDimensionPixelSize(resourceIdNavBarHeight);
        log.debug("getNavigationBarHeight: navigationBarHeight=" + navigationBarHeight);
        return navigationBarHeight;
    }

    private void attachWindow() {

        log.debug("CONFIG attachWindow getStatusBarHeight=" + getStatusBarHeight() +
                ", getNavigationBarHeight=" + getNavigationBarHeight() +
                ", getActionBarHeight=" + getActionBarHeight() +
                ", ");

        if (mControllerView != null)
            return;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        int layoutID=R.layout.player_controller;
        mControllerView = inflater.inflate(layoutID, null);

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


        playerControllersContainer = (FrameLayout)mControllerView.findViewById(R.id.playerControllersContainer);
        playerControllersContainer.addView(mControllerViewLeft);

        log.debug("CONFIG attachWindow: layout WxH " + mLayoutWidth + "x" + mLayoutHeight);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(mLayoutWidth, mLayoutHeight);
        mPlayerView.addView(mControllerView, params);
        log.debug("CONFIG attachWindow, updateOrientation();");
        updateOrientation();
        log.debug("CONFIG attachWindow, mPlayerView.addView");

        initMenuAdapter(mControllerViewLeft);
        switchMode(TVUtils.isTV(mContext));
        setUIMode(UIMode);

        //help overlay
        if(mContext instanceof PlayerActivity){
            if(((PlayerActivity)mContext).isPluggedOnTv()){
                SharedPreferences mPreferences =  mContext.getSharedPreferences(MediaUtils.SHARED_PREFERENCES_NAME, mContext.MODE_PRIVATE);
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

    public void updateOrientation() {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N_MR1) {
            int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            log.debug("CONFIG updateOrientation, orientation is " + PlayerActivity.getHumanReadableRotation(rotation) + "(" + rotation + "), isRotationLocked " + PlayerActivity.isRotationLocked());

            if (PlayerActivity.isRotationLocked()) { // if rotation is locked pick forced orientation rotation
                log.debug("CONFIG updateSizes RotationLocked overriding rotation from " + rotation + " to " + PlayerActivity.getLockedRotation());
                rotation = PlayerActivity.getLockedRotation();
            }

            SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
            RelativeLayout.LayoutParams relativeParams = ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams());
            int shiftUp = 0;
            int shiftLeft = 0;
            switch (rotation) {
                case Surface.ROTATION_270:
                    log.debug("CONFIG updateOrientation, rotation is 270 shifting right from getNavigationBarHeight=" + getNavigationBarHeight());
                    ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    if (isSystemBarOnBottom(mContext)) {
                        log.debug("CONFIG updateOrientation, SystemBarOnBottom shifting up by getNavigationBarHeight=" + getNavigationBarHeight());
                        shiftUp += getNavigationBarHeight();
                    } else {
                        log.debug("CONFIG updateOrientation, ! SystemBarOnBottom shifting left/right by getNavigationBarHeight=" + getNavigationBarHeight());
                        shiftLeft += getNavigationBarHeight();
                    }
                    relativeParams.setMargins(shiftLeft, 0, 0, shiftUp);
                    break;
                case Surface.ROTATION_90:
                    log.debug("CONFIG updateOrientation, rotation is 90");
                    // FIXME: ALIGN_PARENT_RIGHT should have been simpler but results in shifted layout by safeInsetRight+safeInsetLeft+navigationBarHeight
                    ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    if(mPreferences.getBoolean("enable_cutout_mode_short_edges", true)) {
                        log.debug("CONFIG updateOrientation, shifting right PlayerActivity.safeInsetLeft=" + PlayerActivity.safeInset.get(0));
                        relativeParams.setMargins(PlayerActivity.safeInset.get(0), 0, 0, 0); // safeInset.get(0) is safeInsetLeft
                    }
                    break;
                case Surface.ROTATION_0:
                    log.debug("CONFIG updateOrientation, rotation is 0 shifting up from getNavigationBarHeight=" + getNavigationBarHeight());
                    // FIXME: this is the only way found to get in portrait the seekbar on top of navigationBar
                    if(mPreferences.getBoolean("enable_cutout_mode_short_edges", true))
                        ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    else
                        ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    relativeParams.setMargins(0, 0, 0, getNavigationBarHeight());
                    break;
                case Surface.ROTATION_180:
                    log.debug("CONFIG updateOrientation, rotation is 180");
                    ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    if(mPreferences.getBoolean("enable_cutout_mode_short_edges", true)) {
                        log.debug("CONFIG updateOrientation, shifting right PlayerActivity.safeInsetTop=" + PlayerActivity.safeInset.get(1));
                        shiftUp += PlayerActivity.safeInset.get(1); // safeInset.get(1) is safeInsetTop
                    }
                    if (isSystemBarOnBottom(mContext)) {
                        log.debug("CONFIG updateOrientation, SystemBarOnBottom shifting up by getNavigationBarHeight=" + getNavigationBarHeight());
                        shiftUp += getNavigationBarHeight();
                    }
                    if (shiftUp>0)
                        relativeParams.setMargins(0, 0, 0, shiftUp);
                    break;
            }
            mControllerView.setLayoutParams(relativeParams);
            mControllerView.requestLayout();
        }
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
        log.debug("detachWindow");
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

    public void setOnShowHideListener(OnShowHideListener onShowHideListener) {
        mOnShowHideListener = onShowHideListener;
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

    public boolean isControlBarShowing() {
        return mControlBarShowing;
    }

    private void setVisibility(final View view, boolean show, boolean doAnim) {
        if (mIsStopped) {
            return;
        }
        if (show) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private void showActionBar(boolean show) {
        if (mActionBarShowing != show) {
            if (show) {
                if(mVolumeBar!=null){
                    mVolumeBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);                    
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)  mVolumeBar.getLayoutParams();
                  
                    lp.topMargin=getStatusBarHeight();
                    log.debug("showActionBar getStatusBarHeight=" + lp.topMargin);
                    mVolumeBar.setLayoutParams(lp);
                }
                if(mVolumeBar2!=null){
                    mVolumeBar2.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);                    
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)  mVolumeBar2.getLayoutParams();
                    lp.topMargin=getStatusBarHeight();
                    log.debug("showActionBar getStatusBarHeight=" + lp.topMargin);
                    mVolumeBar2.setLayoutParams(lp);
                }
                mActionBar.show();
            } else {
                if(mVolumeBar!=null){
                    mVolumeBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);                    
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)  mVolumeBar.getLayoutParams();
                    lp.topMargin=0;
                    mVolumeBar.setLayoutParams(lp);
                }
                if(mVolumeBar2!=null){
                    mVolumeBar2.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);                    
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)  mVolumeBar2.getLayoutParams();
                    lp.topMargin=0;
                    mVolumeBar2.setLayoutParams(lp);
                }
                mActionBar.hide();
            }
            mActionBarShowing = show;
        }
    }
    public View getVolumeBar(){
        return mVolumeBar;
    }
    public View getControlBar(){
        return mControlBar;
    }
    protected void showSystemBar(boolean show) {
        if (mSystemBarShowing == show) return;
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        int systemUiFlag = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        systemUiFlag |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (show) {
            mSystemUiVisibility &= ~systemUiFlag;
            mSystemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
        }
        else {
            mSystemUiVisibility |= systemUiFlag;
            mSystemUiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
        }
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
        mSystemBarGone = false;
        if (!show)
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_SYSTEM_BAR, 1000);
        mSystemBarShowing = show;
    }

    private void showControlBar(boolean show) {
        if (mControlBar != null && mControlBarShowing != show) {
            log.debug("showControlBar "+String.valueOf(show));
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
        if (mVolumeBarEnabled && mVolumeBarShowing != show) {
            log.debug("showVolumeBar, volume2");
            setVisibility(mVolumeBar, show, true);
            if(mVolumeBar2!=null&&splitView){
                setVisibility(mVolumeBar2, show, true);
                log.debug("showVolumeBar, showing volume bar2");
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

    private void updateBottomBarHeight() {
        if (mOnShowHideListener != null) {
            int height;
            if (mControlBarShowing)
                height = mControlBar.getHeight() + mSystemBarHeight;
            else if (!mSystemBarGone)
                height = mSystemBarHeight;
            else
                height = 0;
            mOnShowHideListener.onBottomBarHeightChange(height);
        }
    }

    private void setOSDVisibility(boolean visible, int flags) {
        log.debug("setOSDVisibility, visiblity "+flags);
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
            log.debug("setOSDVisibility, volume");
            showVolumeBar(visible);
        }
        if((flags & FLAG_SIDE_UNLOCK_INSTRUCTIONS)!=0){
            showUnlockInstructions(visible);
        }
        updateBottomBarHeight();
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 'timeout' milliseconds of inactivity.
     * @param timeout The timeout in milliseconds. Use 0 to show
     * the controller until hide() is called.
     */
    private void show(int flags, int timeout) {
        log.debug("show(" +flags+ ", " +timeout+")");
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

        if (mOnShowHideListener != null)
            mOnShowHideListener.onShow();

        if (timeout != 0 && Player.sPlayer.isPlaying()) {
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
        log.debug("hide("+flags+")");
        if (mIsStopped || DBG_ALWAYS_SHOW)
            return;

        setOSDVisibility(false, flags);

        if (!isShowing()) {
            mHandler.removeMessages(MSG_FADE_OUT);
            if (mOnShowHideListener != null)
                mOnShowHideListener.onHide();
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
            log.debug("cancelToast: canceling toast");
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

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int pos;
            switch (msg.what) {
                case MSG_FADE_OUT:
                    log.debug("Handle: MSG_FADE_OUT");
                    hide();
                    break;
                case MSG_SHOW_PROGRESS:
                    log.debug("Handle: MSG_SHOW_PROGRESS");
                    pos = setProgress();
                    if (!mDragging && mControlBarShowing && Player.sPlayer.isPlaying()) {
                        msg = obtainMessage(MSG_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case MSG_SEEK:
                    log.debug("Handle: MSG_SEEK");
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
                            log.debug("current pos is " + Player.sPlayer.getCurrentPosition() + " seek to " + mNextSeek);
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
                            log.debug("current pos is " + Player.sPlayer.getCurrentPosition() + " seek to " + mNextSeek);
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
                        log.debug("Handle: MSG_SEEK_RESUME");
                        Player.sPlayer.start(PlayerController.STATE_SEEK);
                        mSeekWasPlaying = false;
                    }
                    updatePausePlay();
                    updateButtons();
                    show(FLAG_SIDE_CONTROL_BAR, SHOW_TIMEOUT);
                    break;
                case MSG_SWITCH_VIDEO_FORMAT:
                    log.debug("Handle: MSG_SWITCH_VIDEO_FORMAT");
                    mSurfaceController.switchVideoFormat();
                    updateFormat();
                    break;
                case MSG_HIDE_SYSTEM_BAR:
                    log.debug("Handle: MSG_HIDE_SYSTEM_BAR");
                    mSystemUiVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                    mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
                    manualVisibilityChange=true;
                    break;
                case MSG_OVERLAY_FADE_OUT:
                    log.debug("Handle: MSG_OVERLAY_FADE_OUT");
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
                        log.debug("hidding 1");
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
                            log.debug("hidding 2");
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
        log.debug("setProgress player position/duration=" + position + "/" + duration);
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
                log.debug("setProgress player currentText=" + currentText);

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
            mPauseButton.setImageResource(R.drawable.video_pause_selector);
            if(mPauseButton2!=null)
                mPauseButton2.setImageResource(R.drawable.video_pause_selector);
        } else {
            mPauseButton.setImageResource(R.drawable.video_play_selector);
            if(mPauseButton2!=null)
                mPauseButton2.setImageResource(R.drawable.video_play_selector);
        }
    }

    private void updateFormat() {
        if (mFormatButton == null)
            return;

        switch (mSurfaceController.getNextVideoFormat()) {
            case SurfaceController.VideoFormat.ORIGINAL:
                mFormatButton.setImageResource(R.drawable.video_format_original_selector);
                break;
            case SurfaceController.VideoFormat.FULLSCREEN:
                mFormatButton.setImageResource(R.drawable.video_format_fullscreen_selector);
                break;
            case SurfaceController.VideoFormat.STRETCHED:
                mFormatButton.setImageResource(R.drawable.video_format_stretched_selector);
                break;
            case SurfaceController.VideoFormat.AUTO:
                mFormatButton.setImageResource(R.drawable.video_format_auto_selector);
                break;
        }
    }

    private void doPauseResume() {
        if (mIsStopped)
            return;
        log.debug("doPauseResume: " + Player.sPlayer.isPlaying() + " - " + mSeekWasPlaying);
        if (mNextSeek != -1) {
            mSeekWasPlaying = !mSeekWasPlaying;
        } else {
            if (Player.sPlayer.isPlaying()) {
                show(FLAG_SIDE_CONTROL_BAR, 0);
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
        log.debug("start");
        attachWindow();
        setEnabled(true);
        if (DBG_ALWAYS_SHOW)
            show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, 0);
    }

    public void stop() {
        log.debug("stop");

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
        log.debug("onWindowFocusChanged: " + hasFocus);
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
        log.debug("onSeekAndDraggingComplete: " + mSeekWasPlaying);
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
        log.debug("onAllSeekComplete");
        if (mIsStopped)
            return;

        mSeekComplete = true;
        if (mSeekComplete && !mDragging) {
            onSeekAndDraggingComplete();
        }
    }

    public void onSeekComplete() {
        log.debug("onSeekComplete");
        setProgress();
    }

    public void resumePosition(int position, boolean playOnResume) {
        log.debug("resumePositionresumePosition: " + playOnResume + " pos: " + position);
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
        log.debug("setNextSeekPos " + way);
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
        log.debug("onSeek " + way);
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
            log.debug("onStartTrackingTouch");
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
            setMusicVolume(volume);
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
        if (isTVMenuDisplayed) mLastTouchEventTime = event.getEventTime();
        return false;
    }

    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        log.debug("onSingleTapConfirmed");
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
            log.debug("onSingleTapConfirmed: BUTTON_SECONDARY");
            return false;
        }

        int flags = FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS;

        if (!mVolumeBarEnabled) {
            flags &= ~FLAG_SIDE_VOLUME_BAR;
        }
        if (mIsStopped || !Player.sPlayer.isInPlaybackState()) {
            flags &= ~(FLAG_SIDE_CONTROL_BAR);
        }

        toggleMediaControlsVisiblity(flags);
        
        switchMode(false);
        return true;
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

        if(mIsLocked){
            showUnlockInstructions(true);
            return true;
        }

        float deltaY = e2.getY() - e1.getY();

        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics displayMetrics = new DisplayMetrics();
        display.getMetrics(displayMetrics);
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

        if (Math.abs(scrollGestureVertical) > SCROLL_THRESHOLD) {
            if (e1.getX() < halfWidth) { // left screen part
                log.debug("onScroll: left screen part, direction=" + (scrollGestureVertical > 0 ? "up" : "down"));
                scrollIncrementalBrightnessUpdate(scrollGestureVertical > 0);
            } else { // right screen part
                log.debug("onScroll: left screen part, direction=" + (scrollGestureVertical > 0 ? "up" : "down"));
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
            log.debug("onDoubleTapEvent");
            float x = e.getX();
            if (mControllerView == null) return false;
            float viewWidth = (float) mControllerView.getWidth();
            if (x < viewWidth / 2) { // left region fast rewind
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
            } else { // right region fast forward
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
            }
        }
        return true;
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent event) {
        if(!isTVMenuDisplayed){
            log.debug("onGenericMotion : event=" + event);
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

    private Handler hideOsdHandler = new Handler();
    private Runnable hideOsdRunnable = new Runnable() {
        @Override
        public void run() {
            log.debug("hideOsdRunnable");
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
        log.debug("scrollIncrementalVolumeUpdate: increase=" + increase + ", currentVolume=" + currentVolume + ", maxVolume=" + maxVolume + ", newVolume=" + newVolume);
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
        log.debug("scrollIncrementalBrightnessUpdate: increase=" + increase + ", currentBrightness=" + currentBrightness + ", maxBrightness=" + 30 + ", newBrightness=" + newIntBrightness);
        hideOsdHandler.removeCallbacks(hideOsdRunnable);
        hideOsdHandler.postDelayed(hideOsdRunnable, 300);
    }

    public boolean hasFocus() {
        // TODO Auto-generated method stub
        return mControlBar.isFocused() || mVolumeBar.isFocused();
    }

    public boolean onKey(int keyCode, KeyEvent event) {
        log.debug("onKey()");
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
                    log.debug("onKey, showing menu");
                    return true;
                }
            }
        }

            if (mVolumeBarEnabled) {
                switch(keyCode) {
                    case KeyEvent.KEYCODE_VOLUME_DOWN:
                    case KeyEvent.KEYCODE_D:
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            changeVolumeBy(-1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_UP:
                    case KeyEvent.KEYCODE_U:
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            changeVolumeBy(1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_VOLUME_MUTE:
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
                            log.debug("onKey: dpad down");
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                            log.debug("onKey: next");
                            if (Player.sPlayer.canSeekForward() && mSeekKeyDirection != 1) {
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                                mSeekKeyDirection = 1;
                                onSeek(1, true);
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                            log.debug("onKey: previous");
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
                            log.debug("onKey: play");
                            if (!Player.sPlayer.isPlaying()) {
                                Player.sPlayer.start(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                show();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            if (Player.sPlayer.isPlaying()) {
                                log.debug("onKey: pause");
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
                                log.debug("onKey: play/pause: pause");
                                Player.sPlayer.pause(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                            } else {
                                log.debug("onKey: play/pause: play");
                                Player.sPlayer.start(PlayerController.STATE_NORMAL);
                                updatePausePlay();
                                hide();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            if (Player.sPlayer.isPlaying()) {
                                log.debug("onKey: stop, thus pause");
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
                            log.debug("onKey, button up");
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
                            if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(KEY_PLAYBACK_SPEED,false)) {
                                PlayerService.sPlayerService.decrementAudioSpeed();
                                Toast mAudioSpeedDown = Toast.makeText(mContext, mContext.getString(R.string.set_audio_speed_to, String.format("%.2f", PlayerService.sPlayerService.getAudioSpeed())), Toast.LENGTH_SHORT);
                                mAudioSpeedDown.show();
                            }
                            return true;
                        case KeyEvent.KEYCODE_CHANNEL_UP:
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            if (PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(KEY_PLAYBACK_SPEED,false)) {
                                PlayerService.sPlayerService.incrementAudioSpeed();
                                Toast mAudioSpeedUp = Toast.makeText(mContext, mContext.getString(R.string.set_audio_speed_to, String.format("%.2f", PlayerService.sPlayerService.getAudioSpeed())), Toast.LENGTH_SHORT);
                                mAudioSpeedUp.show();
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

        if(mContext instanceof PlayerActivity&&!tv)
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
            log.debug("handleJoystickEvent, Joystick moved to the right => start seeking forward");
            if(mControlBar.isFocused()){
                mJoystickSeekingActive = true;
                onSeek(1, true);
            }
        }
        else if ((mJoystickZone == MediaUtils.JOYSTICK_ZONE_LEFT || mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_LEFT) && !mJoystickSeekingActive) {
            // Only call onSeek() once when starting to seek but set longPress=true so that
            // the seek event will be sent periodically until the joystick is released
            log.debug("handleJoystickEvent, Joystick moved to the left => start seeking backward");
            if(mControlBar.isFocused()){
                mJoystickSeekingActive = true;
                onSeek(-1, true);
            }
        }
        else if (mJoystickZone == MediaUtils.JOYSTICK_ZONE_CENTER && mJoystickSeekingActive) {
            // The joystick is released (i.e. is back in the dead zone)
            log.debug("handleJoystickEvent, Joystick released => stop seeking");
            mJoystickSeekingActive = false;
            mSeekKeyDirection = 0;
        }
    }

    public void enableAllNotifications() {
        log.debug("Enable all notifications");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    public void enableNotificationAlerts() {
        log.debug("Enable notification alerts only");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    public void disableNotifications() {
        log.debug("Disable all notifications");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    public int getStatusBarHeight() {
        int result = 0;
        int resourceId = mContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0)
            result = mContext.getResources().getDimensionPixelSize(resourceId);
        return result;
    }

    private static boolean hasNavigationBar(Resources resources) {
        int navBarId = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        log.debug("hasNavigationBar: navBarId=" + navBarId + ", hasNavBar=" + resources.getBoolean(navBarId));
        return navBarId > 0 && resources.getBoolean(navBarId);
    }

    public boolean isNavBarAtBottom() {
        // detect navigation bar orientation https://stackoverflow.com/questions/21057035/detect-android-navigation-bar-orientation
        final boolean isNavAtBottom = (mContext.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                || (mContext.getResources().getConfiguration().smallestScreenWidthDp >= 600);
        log.debug("isNavBarAtBottom: NavBarAtBottom=" + isNavAtBottom);
        return isNavAtBottom;
    }

    public static boolean isSystemBarOnBottom(Context mContext) {
        Resources res=mContext.getResources();
        Configuration cfg=res.getConfiguration();
        DisplayMetrics dm=res.getDisplayMetrics();
        boolean canMove=(dm.widthPixels != dm.heightPixels &&
                cfg.smallestScreenWidthDp < 600);
        return(!canMove || dm.widthPixels < dm.heightPixels);
    }

    public static boolean hasNavBar(Resources resources) {
        int id = resources.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) return resources.getBoolean(id);
        else return false;
    }

    public static int getSystemBarHeight(Resources resources) {
        if (!hasNavBar(resources))
            return 0;
        int orientation = resources.getConfiguration().orientation;
        //Only phone between 0-599 has navigationbar can move
        boolean isSmartphone = resources.getConfiguration().smallestScreenWidthDp < 600;
        if (isSmartphone && Configuration.ORIENTATION_LANDSCAPE == orientation) return 0;
        int id = resources
                .getIdentifier(orientation == Configuration.ORIENTATION_PORTRAIT ? "navigation_bar_height" : "navigation_bar_height_landscape", "dimen", "android");
        if (id > 0) return resources.getDimensionPixelSize(id);
        return 0;
    }

    public static int getSystemBarWidth(Resources resources) {
        if (hasNavBar(resources)) return 0;
        int orientation = resources.getConfiguration().orientation;
        //Only phone between 0-599 has navigationbar can move
        boolean isSmartphone = resources.getConfiguration().smallestScreenWidthDp < 600;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE && isSmartphone) {
            int id = resources.getIdentifier("navigation_bar_width", "dimen", "android");
            if (id > 0) return resources.getDimensionPixelSize(id);
        }
        return 0;
    }

    public int getActionBarHeight() {
        int actionBarHeight = 0;
        final TypedArray styledAttributes = mContext.getTheme().obtainStyledAttributes(
            new int[] { android.R.attr.actionBarSize }
        );
        actionBarHeight = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();
        return actionBarHeight;
    }

    public View getActionBarView() {
        View v = mWindow.getDecorView();
        return v.findViewById(R.id.action_bar_container);
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
        log.debug("setUIMode, setting ui mode "+mode);
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
            if(tvCardDialog!=null)
                tvCardDialog.exitDialog();
        }
        isTVMenuDisplayed=show;
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
}
