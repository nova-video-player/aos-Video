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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.RepeatingImageButton;
import com.archos.mediacenter.utils.seekbar.ArchosProgressSlider;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.Subtitle;

/**
 * Created by alexandre on 27/08/15.
 */
public class FloatingPlayerService extends Service implements AppState.OnForeGroundListener, PlayerService.PlayerFrontend {

    private static final int MIN_WIDTH = 200; //in dip
    private static final int STARTING_WIDTH = 300; //in dip
    private static final int STARTING_HEIGHT = 200; //in dip
    private static final float MOVE_THRESHOLD = 100;
    private static final int MSG_PROGRESS_VISIBLE = 1;
    private static final int MSG_TORRENT_UPDATE = 2;
    private static final int MSG_HIDE_CONTROLLER = 3;

    private WindowManager mWindowManager;
    private View mFloatingPlayerRootView;
    public static FloatingPlayerService sFloatingPlayerService;
    private boolean contains;
    private SurfaceController mSurfaceController;
    private SubtitleManager mSubtitleManager;
    private int mSubtitleSizeDefault;
    private int mSubtitleVPosDefault;
    private WindowManager.LayoutParams mParamsF;
    private View mProgressView;
    private View mPlayerController;
    private ImageView mPausePlayButton;
    private ImageView mFullscreenButton;
    private ArchosProgressSlider mProgress;
    private SeekBar mVolumeLevel;
    private AudioManager mAudioManager;
    private RepeatingImageButton mVolumeDownButton;
    private RepeatingImageButton mVolumeUpButton;




    private ServiceConnection mPlayerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
    private ImageView mHideButton;


    private BroadcastReceiver mReceiver;
    private int mLastWidth;
    private int mLastHeight;
    private int mSubtitleColorDefault;
    private int mSize = -1;
    private int mVPos;
    private ImageView mDiscreteButton;
    private ImageView mNormalButton;
    private WindowManager.LayoutParams mNormalButtonLayoutParams;

    public void onCreate() {
        super.onCreate();
        sFloatingPlayerService = this;
        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mSubtitleSizeDefault = getResources().getInteger(R.integer.player_pref_subtitle_size_default);
        mSubtitleVPosDefault = getResources().getInteger(R.integer.player_pref_subtitle_vpos_default);
        mSubtitleColorDefault = Color.parseColor(getResources().getString(R.string.subtitle_color_default));
        bindService(new Intent(this, PlayerService.class), mPlayerServiceConnection, BIND_AUTO_CREATE);
        mWindowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        AppState.addOnForeGroundListener(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction("DISPLAY_FLOATING_PLAYER");
        filter.addAction(PlayerService.PLAY_INTENT);
        filter.addAction(PlayerService.PAUSE_INTENT);
        filter.addAction(PlayerService.EXIT_INTENT);
        filter.addAction(PlayerService.FULLSCREEN_INTENT);
        mReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent) {
                if("DISPLAY_FLOATING_PLAYER".equals(intent.getAction())) {
                    if (mParamsF.width == 1) {
                        mParamsF.width = mLastWidth;
                        mParamsF.height = mLastHeight;
                    }
                    mParamsF.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                    mFloatingPlayerRootView.setAlpha(1);
                    mSubtitleManager.setScreenSize(mParamsF.width, mParamsF.height);
                    mWindowManager.updateViewLayout(mFloatingPlayerRootView, mParamsF);

                    PlayerService.sPlayerService.startStatusbarNotification(false);

                }
                else if(PlayerService.PLAY_INTENT.equals(intent.getAction())){
                    Player.sPlayer.start();
                }
                else if(PlayerService.PAUSE_INTENT.equals(intent.getAction())){
                    Player.sPlayer.pause();
                }
                else if(PlayerService.EXIT_INTENT.equals(intent.getAction())){
                    removeFloatingView(false);
                }
                else if(PlayerService.FULLSCREEN_INTENT.equals(intent.getAction())){
                    startPlayerActivity();
                }
            }
        }        ;
        registerReceiver(mReceiver, filter);

    }
    @Override
    public int onStartCommand(Intent intent,int flags, int startID){

        if(intent != null && !"DISPLAY_FLOATING_PLAYER".equals(intent.getAction()))
            addFloatingView();

        return super.onStartCommand(intent,flags, startID);
    }
    public void onDestroy(){
        super.onDestroy();
        sFloatingPlayerService = null;
        unbindService(mPlayerServiceConnection);
        unregisterReceiver(mReceiver);
    }
    @Override
    public void onStart(Intent intent, int startID){
        super.onStart(intent, startID);
    }

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

    private void changeVolumeBy(int amount) {
        if (mVolumeLevel == null)
            return;
        mVolumeLevel.incrementProgressBy(amount);
        setMusicVolume(mVolumeLevel.getProgress());
    }




    @Nullable
    public void addFloatingView() {
        PlayerService.sPlayerService.startStatusbarNotification(false);

        if(!contains) {

            LayoutInflater li = LayoutInflater.from(this);
            mFloatingPlayerRootView = li.inflate(R.layout.floating_player, null);
            mPlayerController = mFloatingPlayerRootView.findViewById(R.id.player_controller);
            mPausePlayButton = (ImageView) mPlayerController.findViewById(R.id.play_button);
            mFullscreenButton = (ImageView) mPlayerController.findViewById(R.id.fullscreen_button);

            mDiscreteButton = (ImageView) mPlayerController.findViewById(R.id.discrete_button);
            mDiscreteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PlayerService.sPlayerService.startStatusbarNotification(true);
                    mParamsF.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    mFloatingPlayerRootView.setAlpha((float) 0.75);
                    mPlayerController.setVisibility(View.GONE);
                    mWindowManager.updateViewLayout(mFloatingPlayerRootView, mParamsF);

                }
            });
            mHideButton = (ImageView) mPlayerController.findViewById(R.id.hide_button);
            mHideButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PlayerService.sPlayerService.startStatusbarNotification(true);
                    mLastWidth = mParamsF.width;
                    mLastHeight = mParamsF.height;
                    mParamsF.width = 1;
                    mParamsF.height = 1;
                    mSubtitleManager.setScreenSize(mParamsF.width, mParamsF.height);
                    mWindowManager.updateViewLayout(mFloatingPlayerRootView, mParamsF);

                }
            });
            mProgress = (ArchosProgressSlider)mPlayerController.findViewById(R.id.seek_progress);
            mProgress.setMax(1000);
            mProgress.setOnSeekBarChangeListener(mProgressListener);
            mPlayerController.findViewById(R.id.exit_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removeFloatingView(false);
                    stopSelf();
                }
            });
            mFullscreenButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                   startPlayerActivity();
                }
            });



            mProgressView = mFloatingPlayerRootView.findViewById(R.id.progress_indicator);
            mSurfaceController = new SurfaceController(mFloatingPlayerRootView);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                mParamsF = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSPARENT);
            } else {
                mParamsF = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                        PixelFormat.TRANSPARENT);
            }
            mParamsF.gravity = Gravity.TOP | Gravity.LEFT;
            mParamsF.x = 0;
            mParamsF.y = 100;
            mFloatingPlayerRootView.findViewById(R.id.volume_bar).setVisibility(View.VISIBLE);
            mVolumeLevel = (SeekBar) mFloatingPlayerRootView.findViewById(R.id.volume_level);
            mVolumeLevel.setMax(mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            mVolumeLevel.setOnSeekBarChangeListener(mVolumeLevelListener);
            mVolumeUpButton = (RepeatingImageButton) mFloatingPlayerRootView.findViewById(R.id.volume_up);
            mVolumeUpButton.setOnClickListener(mVolumeUpListener);
            mVolumeUpButton.setRepeatListener(mVolumeUpRepeatListener, 100);
            mVolumeDownButton = (RepeatingImageButton) mFloatingPlayerRootView.findViewById(R.id.volume_down);
            mVolumeDownButton.setOnClickListener(mVolumeDownListener);
            mVolumeDownButton.setRepeatListener(mVolumeDownRepeatListener, 100);
            mFloatingPlayerRootView.findViewById(R.id.surface_view).setAlpha((float) 0.5);
            mWindowManager.addView(mFloatingPlayerRootView, mParamsF);
            contains = true;
            try {

                mFloatingPlayerRootView.setOnTouchListener(new View.OnTouchListener() {
                    public int DRAG = 0;
                    public int ZOOM = 1;
                    public int SINGLE = 2;
                    public int mode;
                    public int initialWidth;
                    public int initialHeight;
                    float oldDist = 1f;
                    private int initialX;
                    private int initialY;
                    private float initialTouchX;
                    private float initialTouchY;

                    //*******************Determine the space between the first two fingers
                    private float spacing(MotionEvent event) {
                        if (event.getPointerCount() < 2)
                            return -1;
                        float x = event.getX(0) - event.getX(1);
                        float y = event.getY(0) - event.getY(1);
                        return spacing(x, y);
                    }

                    private float spacing(float x, float y) {

                        return (float) Math.sqrt(x * x + y * y);
                    }

                    public boolean onTouch(View v, MotionEvent event) {

                        // Dump touch event to log
                        // Handle touch events here...
                        switch (event.getAction() & MotionEvent.ACTION_MASK) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = mParamsF.x;
                                initialY = mParamsF.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                initialWidth = mParamsF.width;
                                initialHeight = mParamsF.height;

                                mode = SINGLE;
                                break;
                            case MotionEvent.ACTION_POINTER_DOWN:
                                oldDist = spacing(event);
                                if (oldDist > 10f) {
                                    mode = ZOOM;
                                }
                                break;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_POINTER_UP:
                                if (mode == SINGLE) {
                                    if (PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PLAYING
                                            || PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PAUSED) {
                                        updatePlayPauseButton();

                                        if (mPlayerController.getVisibility() == View.VISIBLE) {
                                            if (Player.sPlayer.isPlaying())
                                                Player.sPlayer.pause();
                                            else
                                                Player.sPlayer.start();
                                            updatePlayPauseButton();
                                        }
                                        mPlayerController.setVisibility(View.VISIBLE);
                                        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
                                        mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLLER, 3000);
                                        setProgress();
                                        updateVolumeBar();
                                    }
                                }
                                //reinit when pointer up
                                initialX = mParamsF.x;
                                initialY = mParamsF.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                initialWidth = mParamsF.width;
                                initialHeight = mParamsF.height;
                                break;
                            case MotionEvent.ACTION_MOVE:
                                if (event.getPointerCount() == 1 && spacing((event.getRawX() - initialTouchX), (event.getRawY() - initialTouchY)) > MOVE_THRESHOLD) {
                                    mode = DRAG;
                                }
                                if (mode == DRAG) {
                                    Display display = mWindowManager.getDefaultDisplay();
                                    Point size = new Point();
                                    display.getSize(size);
                                    int x = initialX + (int) (event.getRawX() - initialTouchX);
                                    int y = initialY + (int) (event.getRawY() - initialTouchY);
                                    if (x + mParamsF.width <= size.x && x >= 0)
                                        mParamsF.x = x;
                                    else if (x + mParamsF.width > size.x) {

                                        mParamsF.x = size.x - mParamsF.width;
                                        initialTouchX = -size.x + initialX + event.getRawX() + mParamsF.width; //so that moving finger left or right will immediately move the window
                                    } else {
                                        mParamsF.x = 0;
                                        initialTouchX = initialX + event.getRawX(); //so that moving finger left or right will immediately move the window
                                    }

                                    if (y + mParamsF.height <= size.y && y >= 0)
                                        mParamsF.y = y;
                                    else if (y + mParamsF.height > size.y) {
                                        mParamsF.y = size.y - mParamsF.height;
                                        initialTouchY = -size.y + initialY + event.getRawY() + mParamsF.height; //so that moving finger left or right will immediately move the window
                                    } else {
                                        mParamsF.y = 0;
                                        initialTouchY = initialY + event.getRawY(); //so that moving finger left or right will immediately move the window
                                    }
                                    mWindowManager.updateViewLayout(v, mParamsF);

                                } else if (mode == ZOOM) {
                                    mPlayerController.setVisibility(View.GONE);
                                    float newDist = spacing(event);
                                    if (newDist > 10f) {
                                        int width = (int) (initialWidth + (newDist - oldDist));
                                        int minWidth = (int) dipToPixels(MIN_WIDTH);
                                        if (width < minWidth)
                                            width = minWidth;
                                        mParamsF.width = width;
                                        mParamsF.height = getHeight(width);
                                        mSubtitleManager.setScreenSize(mParamsF.width, mParamsF.height);
                                        mWindowManager.updateViewLayout(v, mParamsF);
                                        updateSubsSize();

                                    }

                                }
                                break;
                        }


                        return true; // indicate event was handled
                    }

                });


            } catch (Exception e) {
                e.printStackTrace();
            }
            PlayerService.sPlayerService.switchPlayerFrontend(this);
            new Player(this, null, mSurfaceController,false);

            PlayerService.sPlayerService.setPlayer();
            Player.sPlayer.setEffect(VideoEffect.EFFECT_STEREO_SPLIT, VideoEffect.NORMAL_2D_MODE);
            mSubtitleManager = new SubtitleManager(this, (ViewGroup) mFloatingPlayerRootView.findViewById(R.id.subtitle_root_view), mWindowManager, true);
            updateSizes(mParamsF);
            PlayerService.sPlayerService.onStart(PlayerService.sPlayerService.getLastIntent());

        }

    }

    private void startPlayerActivity() {
        Intent intent = new Intent(FloatingPlayerService.this, PlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(PlayerActivity.LAUNCH_FROM_FLOATING_PLAYER, true);
        intent.putExtras(PlayerService.sPlayerService.getLastIntent());
        intent.setData(PlayerService.sPlayerService.getLastIntent().getData());
        startActivity(intent);
    }

    private void updatePlayPauseButton() {
        mPausePlayButton.setImageResource(Player.sPlayer.isPlaying()?R.drawable.video_pause:R.drawable.video_play);
    }

    public float dipToPixels(float dipValue) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics);
    }
    private int getHeight(int i) {
        if(Player.sPlayer.getVideoWidth()==0||Player.sPlayer.getVideoHeight()==0)
            return (int) dipToPixels(STARTING_HEIGHT);
        return (int) (((float)((float)i/(float)Player.sPlayer.getVideoWidth()))*Player.sPlayer.getVideoHeight());
    }
    private void updateSizes(WindowManager.LayoutParams paramsF) {

        int width, height;

        width = (int) dipToPixels(STARTING_WIDTH);;


        height = getHeight(width);
        paramsF.width = width;
        paramsF.height = height;
        mWindowManager.updateViewLayout(mFloatingPlayerRootView, paramsF);
        mSubtitleManager.setScreenSize(width, height);

        updateSubsSize();


    }
    public void updateSubsSize(){
        if(mSize>=0) {
            Display display = mWindowManager.getDefaultDisplay();
            Point point = new Point();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                display.getRealSize(point);
            display.getSize(point);
            int size = (int) ((mParamsF.width / (float)(point.y<point.x ?point.x:point.y)) * mSize);
            int vpos = (int) ((mParamsF.height / (float)(point.y<point.x ?point.y:point.x)) * mVPos);
            mSubtitleManager.setSize(size);
            mSubtitleManager.setVerticalPosition(vpos);
        }
    }
    public void removeFloatingView(boolean isStartingPlayerActivity) {
        if(contains) {

            if (PlayerService.sPlayerService != null)
                PlayerService.sPlayerService.removePlayerFrontend(this,isStartingPlayerActivity);
            try {
                mWindowManager.removeViewImmediate(mFloatingPlayerRootView);
            }catch (java.lang.IllegalArgumentException e){}
            contains = false;
        }
    }







    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_HIDE_CONTROLLER:
                    mPlayerController.setVisibility(View.GONE);
                    break;
                case MSG_PROGRESS_VISIBLE:
                    if (mProgressView != null)
                        mProgressView.setVisibility(View.VISIBLE);
                    break;

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
    public void onForeGroundState(Context applicationContext, boolean foreground) {}

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onAudioError(boolean isNotSupported, String msg) {   }

    @Override
    public void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo) {
        PlayerService.sPlayerService.setVideoInfo(info);
    }

    @Override
    public void setUri(Uri mUri, Uri streamingUri) {  }

    @Override
    public void setVideoInfo(VideoDbInfo mVideoInfo) {   }

    @Override
    public void onEnd() {
        removeFloatingView(false);
        stopSelf();
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
            mWindowManager.removeViewImmediate(mFloatingPlayerRootView);
            contains = false;
        }
    }

    @Override
    public void onFirstPlay() {
        
    }

    @Override
    public void onPrepared() {
        mProgressView.setVisibility(View.GONE);
        updatePlayPauseButton();
        updateSizes(mParamsF);
    }

    @Override
    public void onCompletion() {
        mNextSeek  = -1;
    }

    @Override
    public boolean onError(int errorCode, int errorQualCode, String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        removeFloatingView(false);
        return false;
    }

    @Override
    public void onSeekStart(int pos) {
        if (mSubtitleManager != null)
            mSubtitleManager.onSeekStart(pos);
    }

    @Override
    public void onSeekComplete() {
        setProgress();
    }

    @Override
    public void onAllSeekComplete() {

    }

    @Override
    public void onPlay() {
        if (mSubtitleManager != null)
            mSubtitleManager.onPlay();
        setProgress();
        PlayerService.sPlayerService.startStatusbarNotification(isDiscrete());
    }

    @Override
    public void onPause() {
        if (mSubtitleManager != null)
            mSubtitleManager.onPause();
        PlayerService.sPlayerService.startStatusbarNotification(isDiscrete());
    }


    @Override
    public void onOSDUpdate() {   }

    @Override
    public void onVideoMetadataUpdated(VideoMetadata vMetadata) {
        if(mSubtitleManager!=null) {
            mSubtitleManager.start();

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            mSize = preferences.getInt(PlayerActivity.KEY_SUBTITLE_SIZE, mSubtitleSizeDefault);
            mVPos = preferences.getInt(PlayerActivity.KEY_SUBTITLE_VPOS, mSubtitleVPosDefault);
            int color = preferences.getInt(PlayerActivity.KEY_SUBTITLE_COLOR, mSubtitleColorDefault);
            //mSubtitleManager.setSize(mSize);
            mSubtitleManager.setColor(color);

        }
    }

    @Override
    public void onAudioMetadataUpdated(VideoMetadata vMetadata, int currentAudio) {    }

    @Override
    public void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int currentSubtitle) {    }

    @Override
    public void onBufferingUpdate(int percent) {   }

    @Override
    public void onSubtitle(Subtitle subtitle) {
        if (mSubtitleManager != null)
            mSubtitleManager.addSubtitle(subtitle);
    }

    public void setUIExternalSurface(Surface uiSurface) {
       // mSubtitleManager.setUIExternalSurface(uiSurface); do not enable this with floating player
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



    private void setProgress() {
        int position = mNextSeek == -1 ? Player.sPlayer.getCurrentPosition() : mNextSeek;
        if (position < 0) {
            position = 0;
        }
        int duration = Player.sPlayer.getDuration();

        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mProgress.setProgress((int) pos);


            } else {
                if (mDragging || !mSeekComplete) {
                    mProgress.setProgress(position);


                } else {
                    int relativePosition = Player.sPlayer.getRelativePosition();
                    if (relativePosition != mLastRelativePosition && relativePosition >= 0) {
                        mLastRelativePosition = relativePosition;
                        mProgress.setProgress(mLastRelativePosition);
                    }
                }
            }
            if (!Player.sPlayer.isLocalVideo()) {
                int bufferPosition = Player.sPlayer.getBufferPosition();
                if (bufferPosition >= 0) {
                    mProgress.setSecondaryProgress(bufferPosition);
                }
            }



        }
    }

    private void setMusicVolume(int index) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0);
        /*
         * Since android jb 4.3, the safe volume warning dialog is displayed only with FLAG_SHOW_UI flag.
         * We don't want to always show the default ui volume, so show it only when volume is not set.
         */
        int newIndex = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (index != newIndex)
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI);

    }

    private void updateVolumeBar() {
        if (mVolumeLevel != null && mAudioManager != null) {
            int volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mVolumeLevel.setProgress(volume);
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
            mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
            mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLLER, 3000);
        }
    };

    public boolean isDiscrete() {
        return (mParamsF.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)== WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE||mParamsF.width==1;
    }
}
