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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
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
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.RepeatingImageButton;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.tvmenu.TVCardDialog;
import com.archos.mediacenter.video.player.tvmenu.TVCardView;
import com.archos.mediacenter.video.player.tvmenu.TVMenuAdapter;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;


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

public class PlayerController implements View.OnTouchListener, OnGenericMotionListener
{
    private static final String TAG = "PlayerController";
    private static final boolean DBG = false;
    private static final boolean DBG_ALWAYS_SHOW = false;

    private static final int MSG_FADE_OUT = 1;
    private static final int MSG_SHOW_PROGRESS = 2;
    private static final int MSG_SEEK = 3;
    private static final int MSG_SEEK_RESUME = 4;
    private static final int MSG_SWITCH_VIDEO_FORMAT = 5;
    private static final int MSG_HIDE_SYSTEM_BAR = 6;
    private static final int MSG_OVERLAY_FADE_OUT = 7;


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
    private Toast               mToast;
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

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) { /* JB and more */
            mSystemUiVisibility = 0x00000400 /* View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN */
                    | 0x00000200 /* View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION */
                    | 0x00000100 /* View.SYSTEM_UI_FLAG_LAYOUT_STABLE */;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                mSystemUiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
            mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
            manualVisibilityChange=true;
        }

        /*
         * Hack when using one window for the player controller:
         * The window is fullscreen and can't send events to the ActionBar.
         * So, manually get the size of the ActionBar to dispatch touch events when it's needed.
         */
        
        if (PlayerConfig.useOneWindowPerView()) {
            ViewTreeObserver observer = mPlayerView.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mActionBarHeight = mActionBar.isShowing() ? mActionBar.getHeight() : 0;
                    Rect rect = new Rect();
                    mWindow.getDecorView().getWindowVisibleDisplayFrame(rect);
                    mActionBarHeight += rect.top;
                }
            });
        }

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
        if(ArchosFeatures.isChromeOS(mContext)) {
            volumeBar.setVisibility(View.GONE);
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
            if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
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
            }
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

    public void setSizes(int layoutWidth, int layoutHeight, int displayWidth, int displayHeight) {
        Log.d(TAG, "layout: " + layoutWidth + "x" + layoutHeight + " / display: " + displayWidth + "x" + displayHeight);
        mSystemBarHeight = PlayerConfig.canSystemBarHide() ? displayHeight - layoutHeight : 0;
        mLayoutWidth = layoutWidth;
        mLayoutHeight = layoutHeight;

        if (mControllerView != null) {
            // size changed and maybe orientation too, recreate the whole layout
            detachWindow();
            attachWindow();

        }
    }

    private void attachWindow() {
        if (DBG) Log.d(TAG, "attachWindow");

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

        if (PlayerConfig.useOneWindowPerView()) {
            WindowManager wm = mWindow.getWindowManager();

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();

            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.width = mLayoutWidth;
            lp.height = mLayoutHeight;
            lp.x = 0;
            lp.y = 0;
            lp.format = PixelFormat.TRANSLUCENT;
            lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.token = null;
            lp.windowAnimations = 0;

            wm.addView(mControllerView, lp);
            Log.d(TAG, "wm.addView");
        } else {
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(mLayoutWidth, mLayoutHeight);
            mPlayerView.addView(mControllerView, params);
            updateOrientation();
            Log.d(TAG, "mPlayerView.addView");
        }

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
            int orientation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            if(orientation== Surface.ROTATION_270)
                ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            else
                ((RelativeLayout.LayoutParams) mControllerView.getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_LEFT);
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
        if (DBG) Log.d(TAG, "detachWindow");
        if (PlayerConfig.useOneWindowPerView()) {
            try {
                mWindow.getWindowManager().removeView(mControllerView);
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "already removed");
            }
        } else {
            mPlayerView.removeView(mControllerView);
        }
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
            if (PlayerConfig.hasArchosEnhancement()) {
                Method setShowHideAnimationEnabled;
                try {
                    setShowHideAnimationEnabled = ActionBar.class.getMethod("setShowHideAnimationEnabled", boolean.class);
                    setShowHideAnimationEnabled.invoke(mActionBar, false);
                } catch (Exception e) {
                    //Method doesn't exist, no big deal
                }
            }
            if (show) {

                if(mVolumeBar!=null){
                    mVolumeBar.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);                    
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)  mVolumeBar.getLayoutParams();
                  
                    lp.topMargin=getStatusBarHeight();
                    mVolumeBar.setLayoutParams(lp);
                }
                if(mVolumeBar2!=null){
                    mVolumeBar2.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);                    
                    RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)  mVolumeBar2.getLayoutParams();
                    lp.topMargin=getStatusBarHeight();
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
        if (PlayerConfig.hasHackedFullScreen()) {
            final int STATUS_BAR_GONE =  (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1 ? 0x00000004 : 0x000008);
            if (show)
                mSystemUiVisibility &= ~STATUS_BAR_GONE;
            else
                mSystemUiVisibility |= STATUS_BAR_GONE;
            manualVisibilityChange=true;
            mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
            mSystemBarGone = !show;
        } else {
            int systemUiFlag = View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) { /* ICS and less */
                if (show)
                    mWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                else
                    mWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else { /* JB and more */
                systemUiFlag |= 0x00000004 /* View.SYSTEM_UI_FLAG_FULLSCREEN */;
            }
            if (show) {
                mSystemUiVisibility &= ~systemUiFlag;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    mSystemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE;
            }
            else {
                mSystemUiVisibility |= systemUiFlag;
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                        mSystemUiVisibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
            }
            mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
            manualVisibilityChange=true;
            mSystemBarGone = false;
            if (PlayerConfig.canSystemBarHide()) {
                if (!show)
                    mHandler.sendEmptyMessageDelayed(MSG_HIDE_SYSTEM_BAR, 1000);
            }
        }
        mSystemBarShowing = show;
    }

    private void showControlBar(boolean show) {
        if (mControlBar != null && mControlBarShowing != show) {
            Log.d(TAG, "showControlBar "+String.valueOf(show));
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
            Log.d(TAG, "volume2");
            setVisibility(mVolumeBar, show, true);
            if(mVolumeBar2!=null&&splitView){
                setVisibility(mVolumeBar2, show, true);
                Log.d(TAG, "showing volume bar2");
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
            else if (PlayerConfig.canSystemBarHide() && !mSystemBarGone)
                height = mSystemBarHeight;
            else
                height = 0;
            mOnShowHideListener.onBottomBarHeightChange(height);
        }
    }

    private void setOSDVisibility(boolean visible, int flags) {
        Log.d(TAG, "visiblity "+flags);
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
            Log.d(TAG, "volume");
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
        if (DBG) Log.d(TAG, "show(" +flags+ ", " +timeout+")");
        Log.d(TAG, "show(" +flags+ ", " +timeout+")");
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
        if (DBG) Log.d(TAG, "hide("+flags+")");
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
            mToast.setText(resId);
        }
        mToast.show();

    }

    public void updateToast(CharSequence text) {
        if (Player.sPlayer.isInPlaybackState()) {
            if (mToast == null) {
                mToast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
            } else {
                mToast.setText(text);
            }
            mToast.show();
        }
    }

    public void cancelToast() {
        if (mToast != null) {
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
                    hide();
                    break;
                case MSG_SHOW_PROGRESS:
                    pos = setProgress();
                    if (!mDragging && mControlBarShowing && Player.sPlayer.isPlaying()) {
                        msg = obtainMessage(MSG_SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case MSG_SEEK:
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
                            Log.d(TAG, "current pos is " + Player.sPlayer.getCurrentPosition() + " seek to " + mNextSeek);
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
                            Log.d(TAG, "current pos is " + Player.sPlayer.getCurrentPosition() + " seek to " + mNextSeek);
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
                        Player.sPlayer.start();
                        mSeekWasPlaying = false;
                    }
                    updatePausePlay();
                    updateButtons();
                    show(FLAG_SIDE_CONTROL_BAR, SHOW_TIMEOUT);
                    break;
                case MSG_SWITCH_VIDEO_FORMAT:
                    mSurfaceController.switchVideoFormat();
                    updateFormat();
                    break;
                case MSG_HIDE_SYSTEM_BAR:
                    mSystemUiVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                    mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
                    manualVisibilityChange=true;
                    break;

                case MSG_OVERLAY_FADE_OUT:
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
                        Log.d(TAG, "hidding 1");
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
                            Log.d(TAG, "hidding 2");
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
                endText = stringForTime(duration-position > 0 ? duration-position : 0);
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
            case SurfaceController.VideoFormat.AUTO:
                mFormatButton.setImageResource(R.drawable.video_format_auto_selector);
                break;
        }
    }

    private void doPauseResume() {
        if (mIsStopped)
            return;
        Log.d(TAG, "doPauseResume: " + Player.sPlayer.isPlaying() + " - " + mSeekWasPlaying);
        if (mNextSeek != -1) {
            mSeekWasPlaying = !mSeekWasPlaying;
        } else {
            if (Player.sPlayer.isPlaying()) {
                show(FLAG_SIDE_CONTROL_BAR, 0);
                Player.sPlayer.pause();
            } else {
                Player.sPlayer.start();
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
        Log.d(TAG, "start");
        attachWindow();
        setEnabled(true);
        if (DBG_ALWAYS_SHOW)
            show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, 0);
    }

    public void stop() {
        Log.d(TAG, "stop");

        if (mIsStopped)
            return;
        mIsStopped = true;

        mHandler.removeCallbacksAndMessages(null);

        mPlayer = null;
        cancelToast();
        detachWindow();
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        if (DBG) Log.d(TAG, "onWindowFocusChanged: " + hasFocus);
        if (!mIsStopped) {
            /* volume can be changed by an other application: update it */
            if (hasFocus) {
                updateVolumeBar();

                if (mActionBarShowing) {
                    show(FLAG_SIDE_ACTION_BAR, SHOW_TIMEOUT);
                }
            } else {
                /* notification panel is showing, don't hide the status bar */
                if (mActionBarShowing) {
                    show(FLAG_SIDE_ACTION_BAR, 0);
                }
            }
        }
    }

    private void onSeekAndDraggingComplete() {
        if (DBG) Log.d(TAG, "onSeekAndDraggingComplete: " + mSeekWasPlaying);
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
        if (DBG) Log.d(TAG, "onAllSeekComplete");
        if (mIsStopped)
            return;

        mSeekComplete = true;
        if (mSeekComplete && !mDragging) {
            onSeekAndDraggingComplete();
        }
    }

    public void onSeekComplete() {
        if (DBG) Log.d(TAG, "onSeekComplete");
        setProgress();
    }

    public void resumePosition(int position, boolean playOnResume) {
        if (DBG) Log.d(TAG, "resumePositionresumePosition: " + playOnResume + " pos: " + position);
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
                Player.sPlayer.start();
                updatePausePlay();
                hide();
            } else {
                show(FLAG_SIDE_CONTROL_BAR, 0);
                updatePausePlay();
            }
        }
    }

    private void setNextSeekPos(int way) {
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
        cancelFadeOut();
        mHandler.removeMessages(MSG_SHOW_PROGRESS);
        mHandler.removeMessages(MSG_SEEK_RESUME);
        mHandler.removeMessages(MSG_SEEK);



        if (!mSeekWasPlaying) {
            if (Player.sPlayer.isPlaying()) {
                mSeekWasPlaying = true;
                Player.sPlayer.pause();
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
                    Player.sPlayer.pause();
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
    /* View.OnTouchListener */
    public boolean onTouch(View v, MotionEvent event) {
        if (isTVMenuDisplayed) mLastTouchEventTime = event.getEventTime();
        if(mControllerViewLeft!=null){
            View overlay = mControllerViewLeft.findViewById(R.id.help_overlay);
    
            if(event.getAction()==KeyEvent.ACTION_DOWN&&overlay!=null&&overlay.getVisibility()==View.VISIBLE){
                sendOverlayFadeOut(0); 
                return true;
            }
        }
        
        if (mControllerView == null)
            return false;
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

        if ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY)!=0) return false;

        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
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

    public boolean hasFocus() {
        // TODO Auto-generated method stub
        return mControlBar.isFocused() || mVolumeBar.isFocused();
    }

    public boolean onKey(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKey()");
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
                    Log.d(TAG, "showing menu");
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
                            Log.d(TAG, "dpad down");
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            Log.d(TAG, "sending");
                            if (Player.sPlayer.canSeekForward() && mSeekKeyDirection != 1) {
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                                mSeekKeyDirection = 1;
                                onSeek(1, true);
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            if (Player.sPlayer.canSeekBackward() && mSeekKeyDirection != -1) {
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                                mSeekKeyDirection = -1;
                                onSeek(-1, true);
                            }
                            return true;
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
                            if (!Player.sPlayer.isPlaying()) {
                                Player.sPlayer.start();
                                updatePausePlay();
                                show();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            if (Player.sPlayer.isPlaying()) {
                                Player.sPlayer.pause();
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
                                Player.sPlayer.pause();
                                updatePausePlay();
                                show(FLAG_SIDE_CONTROL_BAR|FLAG_SIDE_ACTION_BAR|FLAG_SIDE_SYSTEM_BAR, 0);
                            } else {
                                Player.sPlayer.start();
                                updatePausePlay();
                                hide();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_STOP:
                            if (Player.sPlayer.isPlaying()) {
                                Player.sPlayer.pause();
                                updatePausePlay();
                                show();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            mSeekKeyDirection = 0;
                            Log.d(TAG, "button up");
                            return true;
                        case KeyEvent.KEYCODE_O:
                            if (isShowing())
                                hide();
                            else
                                show(FLAG_SIDE_ALL_EXCEPT_UNLOCK_INSTRUCTIONS, 0);
                            return true;
                        case KeyEvent.KEYCODE_F:
                            mSurfaceController.switchVideoFormat();
                            return true;
                        case KeyEvent.KEYCODE_J:
                        case KeyEvent.KEYCODE_S:
                            mSettings.switchSubtitleTrack();
                            return true;
                        case KeyEvent.KEYCODE_POUND:
                        case KeyEvent.KEYCODE_A:
                            mSettings.switchAudioTrack();
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

    public boolean onGenericMotion(View v, MotionEvent event) {
        if(!isTVMenuDisplayed){
            if (DBG)
                Log.d(TAG, "onGenericMotion : event=" + event);
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

    public void handleJoystickEvent(int joystickZone) {
        if (mControllerView == null)
            return;
        mJoystickZone = joystickZone;

        if ((mJoystickZone == MediaUtils.JOYSTICK_ZONE_RIGHT || mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_RIGHT) && !mJoystickSeekingActive) {
            // Only call onSeek() once when starting to seek but set longPress=true so that
            // the seek event will be sent periodically until the joystick is released
            if (DBG) Log.d(TAG, "Joystick moved to the right => start seeking forward");
            if(mControlBar.isFocused()){
                mJoystickSeekingActive = true;
                onSeek(1, true);
            }
        }
        else if ((mJoystickZone == MediaUtils.JOYSTICK_ZONE_LEFT || mJoystickZone == MediaUtils.JOYSTICK_ZONE_FAR_LEFT) && !mJoystickSeekingActive) {
            // Only call onSeek() once when starting to seek but set longPress=true so that
            // the seek event will be sent periodically until the joystick is released
            if (DBG) Log.d(TAG, "Joystick moved to the left => start seeking backward");
            if(mControlBar.isFocused()){
                mJoystickSeekingActive = true;
                onSeek(-1, true);
            }
        }
        else if (mJoystickZone == MediaUtils.JOYSTICK_ZONE_CENTER && mJoystickSeekingActive) {
            // The joystick is released (i.e. is back in the dead zone)
            if (DBG) Log.d(TAG, "Joystick released => stop seeking");
            mJoystickSeekingActive = false;
            mSeekKeyDirection = 0;
        }
    }

    public void enableAllNotifications() {
        if (DBG) Log.d(TAG, "Enable all notifications");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    public void enableNotificationAlerts() {
        if (DBG) Log.d(TAG, "Enable notification alerts only");
        mSystemUiVisibility = mPlayerView.getSystemUiVisibility();
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_ICONS;
        mSystemUiVisibility |= STATUS_BAR_DISABLE_NOTIFICATION_TICKER;
        mSystemUiVisibility &= ~STATUS_BAR_DISABLE_NOTIFICATION_ALERTS;
        mPlayerView.setSystemUiVisibility(mSystemUiVisibility);
        manualVisibilityChange=true;
    }

    public void disableNotifications() {
        if (DBG) Log.d(TAG, "Disable all notifications");
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
        if (resourceId > 0) {
            result = mContext.getResources().getDimensionPixelSize(resourceId);
        } 
        return result;
    } 
    public View getActionBarView() {
        View v = mWindow.getDecorView();
        return v.findViewById(R.id.action_bar_container );
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
        Log.d(TAG, "setting ui mode "+mode);
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
