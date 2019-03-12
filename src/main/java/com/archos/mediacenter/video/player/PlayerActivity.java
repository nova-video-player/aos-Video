package com.archos.mediacenter.video.player;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.provider.Settings.SettingNotFoundException;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosIntents;
import com.archos.environment.SystemPropertiesProxy;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.PermissionChecker;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.player.TrackInfoController.TrackInfoListener;
import com.archos.mediacenter.video.player.tvmenu.AudioDelayTVPicker;
import com.archos.mediacenter.video.player.tvmenu.SubtitleDelayTVPicker;
import com.archos.mediacenter.video.player.tvmenu.TVCardDialog;
import com.archos.mediacenter.video.player.tvmenu.TVCardView;
import com.archos.mediacenter.video.player.tvmenu.TVMenu;
import com.archos.mediacenter.video.player.tvmenu.TVMenuAdapter;
import com.archos.mediacenter.video.player.tvmenu.TVMenuItem;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;
import com.archos.mediacenter.video.player.tvmenu.TimerDelayTVPicker;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediacenter.video.utils.SubtitlesWizardActivity;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoMetadata.AudioTrack;
import com.archos.mediacenter.video.utils.VideoMetadata.SubtitleTrack;
import com.archos.mediacenter.video.utils.VideoMetadata.VideoTrack;
import com.archos.mediacenter.video.utils.VideoPreferencesActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.LibAvos;
import com.archos.medialib.Subtitle;
import com.archos.mediaprovider.NetworkState;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.environment.ArchosUtils;

import java.io.FileReader;
import java.io.IOException;
import java.lang.Override;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class PlayerActivity extends AppCompatActivity implements PlayerController.Settings,
SubtitleDelayPickerDialog.OnDelayChangeListener, AudioDelayPickerDialog.OnAudioDelayChangeListener,
DialogInterface.OnDismissListener, TrackInfoListener,
IndexHelper.Listener, PermissionChecker.PermissionListener {

    private static final boolean DBG = true;
    private static final String TAG = "PlayerActivity";

    public static final int RESUME_NO = 0;
    public static final int RESUME_FROM_LAST_POS = 1;
    public static final int RESUME_FROM_BOOKMARK = 2;
    public static final int RESUME_FROM_REMOTE_POS = 3;
    public static final int RESUME_FROM_LOCAL_POS = 4;
    public static final String RESUME = "resume";
    public static final int LAST_POSITION_UNKNOWN = -1;
    public static final int LAST_POSITION_END = -2;

    public static final String STARTED_VIDEO_INTENT = "archos.intent.video.started";
    public static final String STOPPED_VIDEO_INTENT = "archos.intent.video.stopped";

    private static final int DIALOG_NO = -1;
    private static final int DIALOG_SUBTITLE_DELAY = 2;
    private static final int DIALOG_ERROR = 3;
    private static final int DIALOG_BRIGHTNESS = 4;
    private static final int DIALOG_SUBTITLE_SETTINGS = 5;
    private static final int DIALOG_CODEC_NOT_SUPPORTED = 6;
    private static final int DIALOG_WRONG_DEVICE_KINDLE = 7;
    private static final int DIALOG_AUDIO_DELAY = 8;
    private static final int DIALOG_NOT_ENOUGHT_SPACE = 9;

    // accessed from SubtitleSettingsDialog
    /* package */ public static final String KEY_SUBTITLE_SIZE = "pref_play_subtitle_size_key";
    /* package */ public static final String KEY_SUBTITLE_VPOS = "pref_play_subtitle_vpos_key";
    public static final String KEY_SUBTITLE_OUTLINE = "pref_play_subtitle_outline_key";
    public static final String KEY_SUBTITLE_COLOR = "pref_play_subtitle_color_key";
    private static final String KEY_PLAYER_FORMAT = "player_pref_format_key";
    private static final String KEY_PLAYER_AUTO_FORMAT = "player_pref_auto_format_key";
    private static final String KEY_AUDIO_FILT = "pref_audio_filt_int_key"; // used to be "pref_audio_filt_key", containing a string
    private static final String KEY_AUDIO_FILT_NIGHT = "pref_audio_filt_night_int_key";
    private static final String KEY_NOTIFICATIONS_MODE = "notifications_mode";
    private static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";
    private static final String KEY_SUBTITLES_FAVORITE_LANGUAGE = "favSubLang";
    private static final String KEY_LOCK_ROTATION = "pref_lock_rotation";
    private static final String KEY_HIDE_SUBTITLES = "subtitles_hide_default";
    public static final String KEY_ADVANCED_VIDEO_ENABLED = "preferences_advanced_video_enabled";

    public static final String INDEXED_URI = "indexed_uri";
    public static final String KEY_TORRENT="torrent";
    public static final String KEY_STREAMING_URI = "streaming_uri";
    public static final String KEY_TORRENT_URL = "torrent_url";
    public static final String KEY_TORRENT_SELECTED_FILE="torrent_seletected_file";
    public static final String LAUNCH_FROM_FLOATING_PLAYER = "launch_from_floating_player";
    public static final String KEY_FORCE_SW = "force_software_decoding";


    private static final int SUBTITLE_MENU_DELAY = 0;
    private static final int SUBTITLE_MENU_SETTINGS = 1;
    private static final int SUBTITLE_MENU_DOWNLOAD = 2;

    // Menu items management
    private static final int MENU_FILE_ACTIONS_GROUP = 10;
    private static final int MENU_INFO_ID = 101;
    private static final int MENU_BOOKMARK_ID = 102;

    private static final int MENU_GLOBAL_ACTIONS_GROUP = 20;

    private static final int MENU_BRIGHTNESS_ID = 201;
    private static final int MENU_NOTIFICATION_MANAGEMENT_ID = 202;
    private static final int MENU_LOCK_ROTATION_ID = 203;
    private static final int MENU_LOCK_ID = 204;
    private static final int MENU_OTHER_GROUP = 30;
    private static final int MENU_PLAYMODE_ID = 301;
    private static final int MENU_AUDIO_FILTER_ID = 302;
    private static final int MENU_S3D_ID = 303;
    private static final int MENU_WINDOW_MODE = 304;
    private static final int MENU_PREFERENCES = 305;
    private static final int MENU_AUDIO_DELAY_ID = 306;

    // Notification types (keep in sync with res/values/arrays.xml:pref_notification_mode_entries)
    private static final int NOTIFICATION_MODE_ALL = 0;
    private static final int NOTIFICATION_MODE_ALERTS = 1;
    private static final int NOTIFICATION_MODE_NONE = 2;

    private static final int MSG_PROGRESS_VISIBLE = 1;
    private static final int MSG_TORRENT_STARTED = 2;
    private static final int MSG_TORRENT_UPDATE = 3;
    private static final int MSG_TORRENT_NOT_ENOUGH_SPACE = 5;
    private static final int MSG_ERROR_UPNP = 4;
    private static final int MSG_SLEEP = 6;

    private static final int PROGRESS_VISIBLE_DELAY = 500;

    private static final int LOADER_INDEX = 0;

    private static final String VIDEO_PLAYER_DEMO_MODE_EXTRA = "demo_mode";
    public static final String VIDEO_PLAYER_LEGACY_EXTRA = "legacy";

    // from WindowManagerPolicy.java ; should be aligned in case of change
    private static final String ACTION_HDMI_PLUGGED = "android.intent.action.HDMI_PLUGGED";
    private static final String EXTRA_HDMI_PLUGGED_STATE = "state";
    private static final int SUBTITLE_REQUEST = 0;
    

    private boolean mHasAskedFloatingPermission;
    private boolean mIsInfoActivityDisplayed;
    private boolean mLaunchFloatingPlayer;
    private boolean mIsReadytoStart;
    private PermissionChecker mPermissionChecker;



    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PROGRESS_VISIBLE:
                    if (mProgressView != null)
                        mProgressView.setVisibility(View.VISIBLE);
                    break;
                case MSG_TORRENT_STARTED:
                    start();
                    break;
                case MSG_ERROR_UPNP:
                    showDialog(DIALOG_ERROR);
                    break;
                case MSG_TORRENT_NOT_ENOUGH_SPACE:
                    showDialog(DIALOG_NOT_ENOUGHT_SPACE);
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
                case MSG_SLEEP:
                    finish();
                    break;
            }
        }
    };

    private Context mContext;

    private View                mRootView;
    private PlayerController    mPlayerController;
    private View                mPlayerControllerPlaceholder;
    private Player              mPlayer;
    private SurfaceController   mSurfaceController;
    private SubtitleManager     mSubtitleManager = null;
    private View                mProgressView;
    private TextView            mBufferView;
    private Uri                 mUri;
    private Uri                 mStreamingUri;
    private String              mTitle;
    Map<String, String>         mExtraMap = null;
    private String              mMovieOrShowName;
    private String              mEpisode;
    private Bitmap              mThumbnail;
    private int                 mThumbnailDone;
    private boolean             mPoster;
    private String              mPosterPath;

    private int                 mResume;
    private long                mVideoId;
    private int                 mErrorCode = 0;
    private int                 mErrorQualCode = 0;
    private String              mErrorMsg = null;
    private int                 mShowingDialogId;
    private boolean             mNetworkFailed = false;
    private boolean             mPaused;

    private Resources           mResources;
    private SharedPreferences   mPreferences;
    private TrackInfoController mAudioInfoController;
    private TrackInfoController mSubtitleInfoController;

    // State maintained for proper onPause/OnResume behaviour.
    private boolean mResumeFromLast;
    private boolean mNetworkBookmarksEnabled;
    private int mRemotePosition =-1;
    private int mLastPosition;
    private int mForceAudioTrack = -1;
    private boolean mLockRotation;
    private boolean mForceSWDecoding;
    private boolean mHideSubtitles = false;
    private String mSubsFavoriteLanguage;
    private boolean mStopped;
    private boolean mHdmiPlugged = false;
    private boolean mLudoHmdiPlugged = false;
    private int mNotificationMode;
    private MenuItem mInfoMenuItem;
    private MenuItem mBookmarkMenuItem;
    private MenuItem mBrightnessMenuItem;
    private boolean mSeekingWithJoystickStarted = false;

    // Specific player settings used for demo mode
    private boolean mForceExitOnTouch;

    private int mSubtitleSizeDefault;
    private int mSubtitleVPosDefault;
    private int mSubtitleColorDefault;
    private boolean mSubtitleOutlineDefault;
    private boolean mAudioSubtitleNeedUpdate = false;
    private int mNewSubtitleTrack = -1;
    private int mNewAudioTrack = -1;
    private VideoDbInfo mVideoInfo;
    private IndexHelper mIndexHelper = null;

    private boolean mNetworkStateReceiverRegistered = false;
    private boolean mCling = false;

    private TVMenu mSubtitleTVMenu;
    private TVCardView mSubtitleTVCardView;
    private TVCardView mAudioTracksTVCardView;
    private TVMenu mAudioTracksTVMenu;
    private boolean isTVMode;
    private TorrentObserverService mTorrent;
    private int mTorrentFilePosition = -1;
    private Runnable r = null;
    private int mSavedMode;
    private AlertDialog ad=null;
    private long mWillSleepAt; //for timer, stop player activity
    private ServiceConnection mPlayerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            if(mIsReadytoStart)
                postOnPlayerServiceBind();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };


    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "onReceive: " + intent);
            String action = intent.getAction();
            if (isFinishing())
                return;
            if (action.equals(Intent.ACTION_SHUTDOWN)) {
                finish();
            } else if (action.equals(ACTION_HDMI_PLUGGED)) {
                boolean plugged = intent.getBooleanExtra(EXTRA_HDMI_PLUGGED_STATE, false);
                int w = 0, h = 0;
                mHdmiPlugged = plugged;
                mLudoHmdiPlugged = false;
                if (plugged) {
                    int size[] = readHdmiSize();
                    if (size != null) {
                        w = size[0];
                        h = size[1];
                        Log.d(TAG,"intent received ludo hdmi");
                        mSurfaceController.setHdmiPlugged(plugged, w, h);
                        mLudoHmdiPlugged = plugged;
                    }
                }
            }
            else if(action.equals(PlayerService.PLAYER_SERVICE_STARTED)){
                if(mIsReadytoStart)
                    postOnPlayerServiceBind();
            }
        }
    };
    private boolean mWasInPictureInPicture;


    public boolean isPluggedOnTv() {
        return (TVUtils.isTV(this) || mHdmiPlugged);
    }

    public static int[] readHdmiSize() {
        final String filename = "/sys/devices/omapdss/display1/timings";

        FileReader reader = null;
        try {
            reader = new FileReader(filename);
            char[] buf = new char[512];
            int n = reader.read(buf);
            if (n > 1) {
                int w, h, endIdx = 0, startIdx = 0;
                String string = new String(buf, 0, n-1);
                // 148500,1920/88/148/44,1080/4/36/5

                startIdx = string.indexOf(',', 0);
                if (startIdx == -1)
                    return null;
                startIdx++;
                endIdx = string.indexOf('/', startIdx);
                if (endIdx == -1)
                    return null;
                w = Integer.parseInt(string.substring(startIdx, endIdx));
                startIdx = string.indexOf(',', endIdx);
                if (startIdx == -1)
                    return null;
                startIdx++;
                endIdx = string.indexOf('/', startIdx);
                if (endIdx == -1)
                    return null;
                h = Integer.parseInt(string.substring(startIdx, endIdx));

                int ret[] = new int[2];
                ret[0] = w;
                ret[1] = h;
                return ret;
            } else {
                return null;
            }
        } catch (IOException ex) {
            Log.d(TAG, "couldn't read hdmi state from " + filename + ": " + ex);
            return null;
        } catch (NumberFormatException ex) {
            Log.d(TAG, "couldn't read hdmi state from " + filename + ": " + ex);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    public void setUIExternalSurface(Surface uiSurface) {
        mSubtitleManager.setUIExternalSurface(uiSurface);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        if (DBG) Log.d(TAG, "onCreate");

        super.onCreate(icicle);
        mIndexHelper = new IndexHelper(this, getLoaderManager(), LOADER_INDEX);

        /*
        if (android.os.Build.VERSION.SDK_INT > 18){
            startActivity(new Intent(this, SDKNotSupportedDialogActivity.class));
            finish();
            return;
        }
         */
        mPermissionChecker = new PermissionChecker();
        mPermissionChecker.setListener(this);
        VideoEffect.resetForcedMode();
        VideoEffect.setStereoForced(MainActivity.mStereoForced);

        mResources = getResources();

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        // Android 4.4 translucent
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().addFlags(0x08000000 /* FLAG_TRANSLUCENT_NAVIGATION  */);
            getWindow().addFlags(0x04000000 /* FLAG_TRANSLUCENT_STATUS */);
        }

        /*
         * transparent background for archos devices
         * (hide black bars on TVOUT)
         */
        getWindow().setBackgroundDrawable(new ColorDrawable(PlayerConfig.hasArchosEnhancement() ? 0x00000000 : 0xFF000000));

        setContentView(R.layout.player);
        mRootView = findViewById(R.id.root);

        mRootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, final int top, int right, final int bottom, int oldLeft, final int oldTop, int oldRight, final int oldBottom) {
                if(oldBottom!=bottom||oldTop!=top) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                                updateSizes();
                        }
                    });
                }
            }
        });
        // We use the ActionBar for the top-right menu only
        // our PlayerController puts the Title in there
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setBackgroundDrawable(null);

        if (PlayerConfig.hasArchosEnhancement()) {
            Method setShowHideAnimationEnabled;
            try {
                setShowHideAnimationEnabled = ActionBar.class.getMethod("setShowHideAnimationEnabled", boolean.class);
                setShowHideAnimationEnabled.invoke(actionBar, false);
            } catch (Exception e) {
                //Method doesn't exist, no big deal
            }
        }

        mPaused = false;
        mPlayerControllerPlaceholder = findViewById(R.id.player_controller_placeholder);
        mPlayerControllerPlaceholder.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
            }
        });

        mSubtitleSizeDefault = getResources().getInteger(R.integer.player_pref_subtitle_size_default);
        mSubtitleVPosDefault = getResources().getInteger(R.integer.player_pref_subtitle_vpos_default);
        mSubtitleColorDefault = Color.parseColor(getResources().getString(R.string.subtitle_color_default));
        mSubtitleOutlineDefault = false;
        mSurfaceController = new SurfaceController(mRootView);


        mSurfaceController.setListener(mSurfaceListener);

        View menuAnchor = mRootView.findViewById(R.id.menu_anchor);
        mProgressView = mRootView.findViewById(R.id.progress_indicator);
        mBufferView = (TextView) mRootView.findViewById(R.id.buffer_percentage);

        mPlayerController = new PlayerController(this, getWindow(), (ViewGroup)mRootView, mSurfaceController, this, actionBar);
        mPlayerController.setVideoTitleEnabled(true);
        mPlayerController.setOnShowHideListener(mOnShowHideListener);

        mAudioInfoController = new TrackInfoController(this, getLayoutInflater(), menuAnchor, actionBar);
        mAudioInfoController.setListener(this);
        mSubtitleManager = new SubtitleManager(this, (ViewGroup)mRootView, getWindow().getWindowManager(),false);
        mSubtitleInfoController = new TrackInfoController(this, getLayoutInflater(), menuAnchor, actionBar);
        mSubtitleInfoController.setListener(this);
        mSubtitleInfoController.setAlwayDisplay(true);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mResumeFromLast = false;

        // Set the specific player behaviour if playing the demo video
        Intent intent = getIntent();
        mContext = this;



        
        mPlayer = new Player(this, getWindow(), mSurfaceController, false);
        // Clock (for leanback devices only)
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            registerReceiver(mClockReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        }

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N_MR1){ //detect any kind of rotation, even from 270 to 90°
            DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
                public int mOldOrientation;

                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    int orientation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                    if(mOldOrientation!=orientation &&(orientation==Surface.ROTATION_270&&mOldOrientation == Surface.ROTATION_90||orientation==Surface.ROTATION_90&&mOldOrientation == Surface.ROTATION_270))
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateSizes();
                            }
                        });
                    mOldOrientation = orientation;                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            };
            DisplayManager displayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
            displayManager.registerDisplayListener(mDisplayListener, mHandler);

        }

    }

    public boolean isInfoActivityDisplayed(){
        return mIsInfoActivityDisplayed;
    }
    final BroadcastReceiver mClockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    private void updateClock() {
        mPlayerController.updateClock();
    }

    private void setEffectForced (int mode) {
        setEffectForced(mode, true);
    }

    private void setEffectForced (int mode, boolean save) {
        VideoEffect.setForcedMode(mode);
        setEffect(mode, save);
    }

    private void setEffect (int mode) {
        setEffect(mPlayer.getEffectType(), mode, true);
    }

    private void setEffect (int mode, boolean save) {
        setEffect(mPlayer.getEffectType(), mode, save);
    }

    private void setEffect(int type, int mode) {
        setEffect(type, mode, true);
    }

    private void setEffect(int type, int mode, boolean needSave) {
        if (needSave) mSavedMode=mode;
        mPlayer.setEffect(type, mode);
        mPlayerController.setUIMode(mPlayer.getUIMode());
        if(mSubtitleManager!=null)
            mSubtitleManager.setUIMode(mPlayer.getUIMode());
        if(type!=VideoEffect.EFFECT_NONE){
            setLockRotation(true);
        }
        else{
            setLockRotation(mLockRotation);
        }
    }

    private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NetworkState ns = NetworkState.instance(context);
            ns.updateFrom(context);
            if (!ns.hasLocalConnection()) {
                if (DBG) Log.d(TAG, "lost network: finish");
                finish();
            }
        }
    };


    private void registerNetworkReceiver() {
        if (!mNetworkStateReceiverRegistered) {
            if (DBG) Log.d(TAG, "registerNetworkReceiver");
            registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            mNetworkStateReceiverRegistered = true;
        }
    }

    private void unregisterNetworkReceiver() {
        if (mNetworkStateReceiverRegistered) {
            if (DBG) Log.d(TAG, "unregisterNetworkReceiver");
            unregisterReceiver(mNetworkStateReceiver);
            mNetworkStateReceiverRegistered = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        mStopped = false;

        unregisterNetworkReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        intentFilter.addAction(PlayerService.PLAYER_SERVICE_STARTED);
        intentFilter.addAction(ACTION_HDMI_PLUGGED);
        registerReceiver(mReceiver, intentFilter);
        isTVMode = TVUtils.isTV(mContext);
        mLockRotation = mPreferences.getBoolean(KEY_LOCK_ROTATION, false);
        mHideSubtitles = mPreferences.getBoolean(KEY_HIDE_SUBTITLES, false);
        mNetworkBookmarksEnabled = mPreferences.getBoolean(KEY_NETWORK_BOOKMARKS, true);
        mSubsFavoriteLanguage = mPreferences.getString(KEY_SUBTITLES_FAVORITE_LANGUAGE, Locale.getDefault().getISO3Language());
        mForceSWDecoding = mPreferences.getBoolean(KEY_FORCE_SW, false);
        setLockRotation(mLockRotation);
        updateSizes();
        mSurfaceController.setVideoFormat(Integer.parseInt(mPreferences.getString(KEY_PLAYER_FORMAT, "-1")),
                Integer.parseInt(mPreferences.getString(KEY_PLAYER_AUTO_FORMAT, "-1")));
        if (LibAvos.isAvailable()) {
            VideoPreferencesFragment.resetPassthroughPref(mPreferences);
            LibAvos.setPassthrough(Integer.valueOf(mPreferences.getString("force_audio_passthrough_multiple","0")));
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) // Android is recent enough not to require downmix on phones/tablets
                LibAvos.setDownmix(0);
            else if(ArchosFeatures.isAndroidTV(this)&&!"AFTM".equals(Build.MODEL))  // no downmix on AndroidTV except if on the firestick
                LibAvos.setDownmix(0);
            else
                LibAvos.setDownmix(1);
        }
        //if not started from floating player, we have to stop our video
        if (mForceSWDecoding)
            Toast.makeText(
                mContext,
                R.string.warning_swdec,
                Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, mPlayerServiceConnection, BIND_AUTO_CREATE);
        if(PlayerService.sPlayerService!=null)
            postOnPlayerServiceBind();
        else
            mIsReadytoStart = true;
        getIntent().putExtra(LAUNCH_FROM_FLOATING_PLAYER, false);

    }

    private void postOnPlayerServiceBind() {
        if (!mResumeFromLast && getSharedPreferences("player", 0).getInt("lastintent", 0) == getIntent().hashCode()) {
            /* resume video if last intent == current intent
             * (when resumed from history for example)
             */
            mResumeFromLast = true;
        }
        final String action = getIntent().getAction();
        if (mResumeFromLast || (action != null && action.equals(ArchosIntents.ARCHOS_RESUME_VIDEOPLAYER))) {
            mResume = RESUME_FROM_LAST_POS;
            getIntent().putExtra(RESUME, mResume);
        } else {
            mResume = getIntent().getIntExtra(RESUME, RESUME_NO);
        }

        mIsReadytoStart = false;
        Log.d(TAG, "postOnPlayerServiceBind() ");
        Intent intent = new Intent();
        intent.putExtras(getIntent());
        intent.setData(getIntent().getData());

        PlayerService.sPlayerService.switchPlayerFrontend(mPlayerListener);
        Player.sPlayer = mPlayer;
        PlayerService.sPlayerService.setPlayer();
        if(mPermissionChecker.hasExternalPermission(this)) {
            Log.d(TAG, "hasExternalPermission ");
            PlayerService.sPlayerService.onStart(intent);
            PlayerService.sPlayerService.setIndexHelper(mIndexHelper);
            start();
        }

    }
    @Override
    public void onPictureInPictureModeChanged(boolean isInPict){
        super.onPictureInPictureModeChanged(isInPict);
        if(isInPict)
            mWasInPictureInPicture = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DBG) Log.d(TAG, "onResume");
        PlayerBrightnessManager.getInstance().restoreBrightness(this);
        if(!mWasInPictureInPicture){
            mPermissionChecker.checkAndRequestPermission(this);
            if(mHasAskedFloatingPermission&&Settings.canDrawOverlays(this)){ //permission has been granted
                startService(new Intent(this, FloatingPlayerService.class));

            }
            mHasAskedFloatingPermission = false;
            TorrentObserverService.resumed(PlayerActivity.this);
            if (mPaused) {
                mPaused = false;
                mPlayer.checkSubtitles();
            }

            
            mIsInfoActivityDisplayed = false;
        }


        // Restore the previous notifications mode
        mNotificationMode = mPreferences.getInt(KEY_NOTIFICATIONS_MODE, NOTIFICATION_MODE_ALL);
        if (mNotificationMode != NOTIFICATION_MODE_ALL) {
            // Notifications must be disabled, at least partially
            applyNotificationsMode(mNotificationMode);
        }

        mWasInPictureInPicture = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (DBG) Log.d(TAG, "onPause");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && ArchosFeatures.isAndroidTV(this)) {
            if (mPlayer.isPlaying()) {
                if (!requestVisibleBehind(true)&&!mLaunchFloatingPlayer) {
                    // Try to play behind launcher, but if it fails, so paused.
                    TorrentObserverService.paused(this);
                }
            } else {
                requestVisibleBehind(false);
                TorrentObserverService.paused(this);
            }
        } else {
            TorrentObserverService.paused(this);
        }
        mPaused = true;

        
    }

    @Override
    public void onVisibleBehindCanceled() {
        mPaused = true;
        TorrentObserverService.paused(this);
        super.onVisibleBehindCanceled();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (DBG) Log.d(TAG, "onStop");
        if (mStopped)
            return;
        if(mTorrent!=null){

            Log.d(TAG, "unbinding torrentObserver");
        }

        /*
         * register a receiver that finish activity in case
         * network state is changed since a resume is not possible anymore.
         */
        if (!mPlayer.isLocalVideo())
            registerNetworkReceiver();

        if (mShowingDialogId != DIALOG_NO) {
            removeDialog(mShowingDialogId);
            mShowingDialogId = DIALOG_NO;
        }

        mPlayerController.hide();

        if (mLastPosition != LAST_POSITION_END)
            mLastPosition = getBookmarkPosition();
        stop();
        if(PlayerService.sPlayerService !=null)
            PlayerService.sPlayerService.removePlayerFrontend(mPlayerListener, mLaunchFloatingPlayer);

        if(FloatingPlayerService.sFloatingPlayerService!=null&&!mLaunchFloatingPlayer)
            FloatingPlayerService.sFloatingPlayerService.stopSelf();
        mLaunchFloatingPlayer = false;
        mResumeFromLast = true;

        Editor editor = getSharedPreferences("player", 0).edit();
        editor.putInt("lastintent", getIntent().hashCode());
        editor.apply();
        unregisterReceiver(mReceiver);
        unbindService(mPlayerServiceConnection);
    }

    @Override
    protected void onDestroy() {

        if (DBG) Log.d(TAG, "onDestroy");
        unregisterNetworkReceiver();

        VideoEffect.resetForcedMode();
        setEffect(VideoEffect.getDefaultMode());

        

        // Clock
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            unregisterReceiver(mClockReceiver);
        }

        super.onDestroy();
    }

    @SuppressLint("NewApi") // XXX
    private void updateSizes() {
        boolean isInPictureInPictureMode = Build.VERSION.SDK_INT>=Build.VERSION_CODES.N&&isInPictureInPictureMode();
        boolean isInMultiWindowMode = Build.VERSION.SDK_INT>=Build.VERSION_CODES.N&&isInMultiWindowMode();
        Display display = getWindowManager().getDefaultDisplay();
        int width, height, layoutWidth, layoutHeight, displayWidth, displayHeight;
        Point point = new Point();
        display.getRealSize(point);
        displayWidth = point.x;
        displayHeight = point.y;
        display.getSize(point);
        layoutWidth = point.x;
        layoutHeight = point.y;
        boolean isChromebook = getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
        if(isChromebook){
            View content = findViewById(android.R.id.content);
            displayWidth = content.getWidth();
            displayHeight = content.getHeight();

        }
        Log.d(TAG, "isInMultiWindowMode() :");

        if (PlayerConfig.hasFullScreen()&&!isInPictureInPictureMode&&!isInMultiWindowMode) {
            width = displayWidth;
            height = displayHeight;
        } else {
            width = layoutWidth;
            height = layoutHeight;
        }

        if (DBG) Log.d(TAG, "trueFullscreen: " + PlayerConfig.hasFullScreen() + ", size: " + width+"x"+height);
        if(!isChromebook) { //keeping things as it was on other devices
            ViewGroup.LayoutParams lp = mRootView.getLayoutParams();
            lp.width = width;
            lp.height = height;
            mRootView.setLayoutParams(lp);
        }
        mSurfaceController.setScreenSize(width, height);
        mSubtitleManager.setScreenSize(width, height);
        if(!isInPictureInPictureMode) {
            mPlayerController.setSizes(layoutWidth, layoutHeight, displayWidth, displayHeight);
            // Close the menus if needed
            mAudioInfoController.resetPopup();
            mSubtitleInfoController.resetPopup();
        }
        int size = mPreferences.getInt(KEY_SUBTITLE_SIZE, mSubtitleSizeDefault);
        int vpos = mPreferences.getInt(KEY_SUBTITLE_VPOS, mSubtitleVPosDefault);
        if(isInPictureInPictureMode) { //proportional size
            size = (int) ((layoutWidth / (float)(displayHeight<displayWidth?displayWidth:displayHeight)) * size);
            vpos = (int) ((layoutHeight / (float)(displayHeight<displayWidth?displayHeight:displayWidth)) * vpos);
        }

        mSubtitleManager.setSize(size);
        mSubtitleManager.setVerticalPosition(vpos);


    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateSizes();
            }
        });

        invalidateOptionsMenu();
    }

    private void setLockRotation(boolean avpLock) {
        Display display = getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        if (DBG) Log.d(TAG, "rotation status: " + rotation);

        boolean systemLock;
        try {
            systemLock = 1 != Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
        } catch (SettingNotFoundException e) {
            systemLock = false;
        }

        if (DBG) Log.d(TAG, "avpLock: " + avpLock + " systemLock: " + systemLock);
        if (avpLock || systemLock) {
            int tmpOrientation = getResources().getConfiguration().orientation;
            int wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

            if (tmpOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                else
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                setRequestedOrientation(wantedOrientation);
            }
            else if (tmpOrientation == Configuration.ORIENTATION_PORTRAIT || tmpOrientation == Configuration.ORIENTATION_UNDEFINED) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                else
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                setRequestedOrientation(wantedOrientation);
            }
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DBG) Log.d(TAG, "onNewIntent: " + intent);
        setIntent(intent);
        if(mWasInPictureInPicture) {
            if (PlayerService.sPlayerService != null) {
                PlayerService.sPlayerService.stopAndSaveVideoState();
                postOnPlayerServiceBind();

            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        boolean handled = false;
        if (!mStopped && (mPlayerController!=null)) {
            // Send key event to PlayerController if it (its place-holder actually) has the focus
            // Only keep keys used for focus navigation (because this is not handled by PlayerController)
            if (!handled && mPlayerControllerPlaceholder.hasFocus() && !isKeyUsedForFocusNavigation(keyCode)) {

                handled = mPlayerController.onKey(keyCode, event);
            }
            // Send key event to PlayerController even if it doesn't have the focus, in order to handled special media keys (play, pause, seek, volume, etc.)
            // Only keep keys used for navigation in the ActionBar
            if (!handled && !mPlayerControllerPlaceholder.hasFocus() && (!isKeyUsedForActionBarNavigation(keyCode)||mPlayerController.isSeekPressed())) {
                handled = mPlayerController.onKey(keyCode, event);
            }
        }
        return handled ? true : super.onKeyUp(keyCode, event);
    }

    // to handle touch event before on ui change
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {

        boolean handle = false;
        if(mPlayerController!=null)
            handle = mPlayerController.onTouch(event);

        //Be careful not to override the return unless necessary
        //return false;
        return handle?true:super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = false;
        // When using DPad, show all the OSD so that focus can move freely
        // from/to ActionBar to/from PlayerController
        switch (keyCode) {

            case KeyEvent.KEYCODE_DPAD_UP:
                if(mPlayerController!=null){
                    return mPlayerController.onKey(keyCode, event); 
                }
                break;
            case KeyEvent.KEYCODE_I:
                showVideoInfos();
                handled = true;
                break;
        }

        if (!mStopped && (mPlayerController!=null) &&mPlayerControllerPlaceholder!=null&&!handled) {
            // Send key event to PlayerController if it (its place-holder actually) has the focus
            // Only keep keys used for focus navigation (because this is not handled by PlayerController)
            handled = mPlayerController.onKey(keyCode, event);
        }
        return handled ? true : super.onKeyDown(keyCode, event);
    }

    /**
     * Some keys can't be given to the player controller window because they may
     * be used to move the focus in or out of the controller window.
     * In our current UI it's UP and DOWN because the controller is at the
     * bottom of the screen, while the only other focusable area is the
     * ActionBar at the top of the screen
     * @param keyCode
     * @return
     */
    private static boolean isKeyUsedForFocusNavigation(int keyCode) {
        if ((keyCode==KeyEvent.KEYCODE_DPAD_UP)||(keyCode==KeyEvent.KEYCODE_DPAD_DOWN)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Some keys can't be given to the player controller window because they may
     * be used to move the focus among the items of the ActionBar.
     * @param keyCode
     * @return
     */
    private static boolean isKeyUsedForActionBarNavigation(int keyCode) {
        if ((keyCode==KeyEvent.KEYCODE_DPAD_LEFT)||(keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)||(keyCode==KeyEvent.KEYCODE_ENTER)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // If we pass here it means that the user clicked out of the mPlayerController window,
        // and not in any view of the activity (this)
        // In that case we send the event to the mPlayerController anyway to have it handle the show/hide of the OSD
        if (!mStopped && mPlayerController != null)
            return mPlayerController.onTouch(null, event);
        else
            return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Only handle joystick events
        if(mPlayerController!=null && !mPlayerController.isTVMenuDisplayed())
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int joystickZone = MediaUtils.getJoystickZone(event);
                if (DBG) Log.d(TAG, "onGenericMotionEvent : event=ACTION_MOVE");

                if (!mSeekingWithJoystickStarted && joystickZone != MediaUtils.JOYSTICK_ZONE_CENTER) {
                    // Starting to seek => make the control bar visible
                    mSeekingWithJoystickStarted = true;
                    mPlayerController.showControlBar();
                }

                mPlayerController.handleJoystickEvent(joystickZone);

                if (mSeekingWithJoystickStarted && joystickZone == MediaUtils.JOYSTICK_ZONE_CENTER) {
                    // Seeking done
                    mSeekingWithJoystickStarted = false;
                }
            }

        return super.onGenericMotionEvent(event);
    }

    boolean isAudioFilterEnabled() {
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

    // TV Menu

    private void createTVTimerDialog(){
        View dialogMainView = (View)LayoutInflater.from(mContext)
                .inflate(R.layout.card_dialog_layout, null);
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).setText((String) getText(R.string.sleep_timer_title));

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        final TVMenu tvmenu = mPlayerController.getTVMenuAdapter().createTVMenu();

        // adding tv picker
        final TimerDelayTVPicker tvPicker = (TimerDelayTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.timer_tv_picker, null);
        tvPicker.setHourFormat(true);
        tvPicker.setStep(60000);
        tvPicker.setMin(0);
        if(mWillSleepAt-System.currentTimeMillis()>0)
            tvPicker.init((int)(mWillSleepAt-System.currentTimeMillis()), null);
        else
            tvPicker.init(0, null);
        tvPicker.setMax(24*60*60*1000); //24h
        tvmenu.createAndAddTVMenuItem(getString(R.string.sleep_timer_description), false, false);
        tvmenu.addTVMenuItem(tvPicker);
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).addOtherView(tvmenu);
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {
            @Override
            public void onResult(int code) {
                mHandler.removeMessages(MSG_SLEEP);
                if(tvPicker.getDelay()>0) {
                    mWillSleepAt = System.currentTimeMillis() + tvPicker.getDelay();
                    mHandler.sendEmptyMessageDelayed(MSG_SLEEP, tvPicker.getDelay());
                }
                mPlayerController.getTVMenuAdapter().setDiscrete(false);
            }
        });
        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        mPlayerController.addToMenuContainer(dialogMainView);
        tvPicker.requestFocus();
    }

    private void createTVSubtitleDialog() {
        View dialogMainView = LayoutInflater.from(mContext)
                .inflate(R.layout.card_dialog_layout, null);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dialogMainView.findViewById(R.id.card_view).getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).setText((String) getText(R.string.player_pref_subtitle_delay_title));

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        final TVMenu tvmenu = mPlayerController.getTVMenuAdapter().createTVMenu();

        // adding tv picker
        SubtitleDelayTVPicker tvPicker = (SubtitleDelayTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.subtitle_delay_tv_picker, null);

        tvPicker.setStep(1);
        if(mPlayer.getDuration()>0) {
            tvPicker.setMax(mPlayer.getDuration());
            tvPicker.setMin(-mPlayer.getDuration());
        }
        tvPicker.setHourFormat(true);
        tvmenu.addTVMenuItem(tvPicker);

        View separator = LayoutInflater.from(mContext).inflate(R.layout.menu_separator_layout, null);
        tvmenu.addTVMenuItem(separator);

        tvmenu.createAndAddTVMenuItem(getText(R.string.subtitle_delay_speed).toString(), false);
        tvmenu.setItems(R.array.subtitle_delay_ratio_array, mVideoInfo.subtitleRatio, true);
        tvmenu.setOnItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                if (v instanceof TVMenuItem) {
                    tvmenu.unCheckAll();
                    ((TVMenuItem) v).setChecked(true);
                    PlayerActivity.this.onDelayChange(null, mVideoInfo.subtitleDelay, tvmenu.getItemPostion(v) - 3);
                }
            }
        });
        tvPicker.init(mVideoInfo.subtitleDelay, new SubtitleDelayPickerAbstract.OnDelayChangedListener() {
            @Override
            public void onDelayChanged(SubtitleDelayPickerAbstract view, int delay) {
                PlayerActivity.this.onDelayChange(null, delay, mVideoInfo.subtitleRatio);
            }
        });
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).addOtherView(tvmenu);
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {     
            @Override
            public void onResult(int code) {
                mPlayerController.getTVMenuAdapter().setDiscrete(false);
            }
        });
        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        mPlayerController.addToMenuContainer(dialogMainView);
        tvPicker.requestFocus();
    }

    private void createTVSubtitleSettingsDialog() {
        float density = getApplicationContext().getResources().getDisplayMetrics().density;
        float pickerWidth= (float)100 * density;

        View dialogMainView =   LayoutInflater.from(mContext)
                .inflate(R.layout.card_dialog_layout, null);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dialogMainView.findViewById(R.id.card_view).getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).setText((String) getText(R.string.menu_player_settings));

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        final TVMenu tvmenu = mPlayerController.getTVMenuAdapter().createTVMenu();

        tvmenu.createAndAddTVMenuItem(getText(R.string.subtitle_style_text).toString(), false);
        final SubtitleDelayTVPicker tvPicker = (SubtitleDelayTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.subtitle_delay_tv_picker, null);
        tvPicker.setStep(1);
        tvPicker.setMin(10 * 100);
        tvPicker.setMax(100 * 100);
        // tvPicker.setHourFormat(true);
        tvmenu.addTVMenuItem(tvPicker);
        tvPicker.setTextViewWidth((int) pickerWidth);
        mSubtitleManager.setShowSubtitlePositionHint(true);
        tvPicker.setText(getTVSizeText(mSubtitleManager.getSize()));
        tvPicker.setTextSize(mSubtitleManager.getSize());
        tvPicker.setUpdateText(false);
        tvPicker.setTextColor(mSubtitleManager.getColor());
        tvPicker.init(mSubtitleManager.getSize() * 100, new SubtitleDelayPickerAbstract.OnDelayChangedListener() {
            @Override
            public void onDelayChanged(SubtitleDelayPickerAbstract view, int delay) {
                if (r != null)
                    tvPicker.removeCallbacks(r);
                mSubtitleManager.setSize(delay / 100);
                tvPicker.setText(getTVSizeText(delay / 100));
                tvPicker.setTextSize(mSubtitleManager.getSize());
            }
        });

        final SubtitleColorPicker colorPicker = new SubtitleColorPicker(this);

        colorPicker.setColorPickListener(new SubtitleColorPicker.ColorPickListener() {
            @Override
            public void onColorPicked(int color) {
                tvPicker.setTextColor(color);
                mSubtitleManager.setColor(color);

            }
        });
        tvmenu.addTVMenuItem(colorPicker);

        tvmenu.createAndAddTVMenuItem(getText(R.string.subtitle_vert_text).toString(), false);

        // adding tv picker
        final SubtitleDelayTVPicker tvPicker2 = (SubtitleDelayTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.subtitle_delay_tv_picker, null);

        tvPicker2.setStep(10);
        tvPicker2.setMin(0);
        tvPicker2.setMax(255*100);
        // tvPicker.setHourFormat(true);
        tvmenu.addTVMenuItem(tvPicker2);
        tvPicker2.setTextViewWidth((int) pickerWidth);
        mSubtitleManager.setShowSubtitlePositionHint(true);
        tvPicker2.init(mSubtitleManager.getVerticalPosition()*100, new SubtitleDelayPickerAbstract.OnDelayChangedListener() {
            @Override
            public void onDelayChanged(SubtitleDelayPickerAbstract view, int delay) {
                if (r != null)
                    tvPicker2.removeCallbacks(r);
                mSubtitleManager.fadeSubtitlePositionHint(true);

                mSubtitleManager.setVerticalPosition(delay/100);
                r = new Runnable() {
                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        mSubtitleManager.fadeSubtitlePositionHint(false);
                    }
                };
                tvPicker2.postDelayed(r, 200);
            }
        });

        tvmenu.createAndAddSeparator();
        final TVMenuItem tvm = tvmenu.createAndAddTVSwitchableMenuItem(getResources().getString(R.string.subtitle_outline), mSubtitleManager.getOutlineState());
        tvm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean outline = mSubtitleManager.getOutlineState();
                tvm.setChecked(! outline);
                mSubtitleManager.setOutlineState(! outline);
            }
        });

        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).addOtherView(tvmenu);
        ((TVCardDialog)dialogMainView.findViewById(R.id.card_view)).setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {     
            @Override
            public void onResult(int code) {
               	mPreferences.edit().putInt(PlayerActivity.KEY_SUBTITLE_SIZE, mSubtitleManager.getSize()).apply();
            	mPreferences.edit().putInt( PlayerActivity.KEY_SUBTITLE_VPOS, mSubtitleManager.getVerticalPosition()).apply();
                mPreferences.edit().putInt( PlayerActivity.KEY_SUBTITLE_COLOR, mSubtitleManager.getColor()).apply();
                mPreferences.edit().putBoolean(PlayerActivity.KEY_SUBTITLE_OUTLINE, mSubtitleManager.getOutlineState()).apply();
                mPlayerController.getTVMenuAdapter().setDiscrete(false);
                mSubtitleManager.fadeSubtitlePositionHint(false);
            }
        });
        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        mPlayerController.addToMenuContainer(dialogMainView);
        tvPicker.requestFocus();
    }

    private void createTVAudioDelayDialog() {
        View dialogContainer = (View)LayoutInflater.from(mContext).inflate(R.layout.card_dialog_layout, null);
        View dialogView = dialogContainer.findViewById(R.id.card_view);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dialogView.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        dialogView.setLayoutParams(params);
        ((TVCardDialog) dialogView).setText((String) getText(R.string.player_pref_audio_delay_title));

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        final TVMenu tvmenu = mPlayerController.getTVMenuAdapter().createTVMenu();

        // adding tv picker
        AudioDelayTVPicker tvPicker = (AudioDelayTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.audio_delay_tv_picker, null);

        tvPicker.setStep(20);
        if (mPlayer.getDuration() > 0) {
            tvPicker.setMax(mPlayer.getDuration());
            tvPicker.setMin(-mPlayer.getDuration());
        }
        tvPicker.setHourFormat(true);
        tvmenu.addTVMenuItem(tvPicker);
        final TVMenuItem saveSettingCB = tvmenu.createAndAddTVSwitchableMenuItem(getString(R.string.keep_setting), mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0) != 0);
        saveSettingCB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettingCB.toggle();
            }
        });
        tvPicker.init(PlayerService.sPlayerService.getAudioDelay(), new AudioDelayPickerAbstract.OnAudioDelayChangedListener() {
            @Override
            public void onAudioDelayChanged(AudioDelayPickerAbstract view, int delay) {
                PlayerActivity.this.onAudioDelayChange(null, delay);


            }
        });
        ((TVCardDialog)dialogView).addOtherView(tvmenu);
        ((TVCardDialog)dialogView).setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {
            @Override
            public void onResult(int code) {
                mPlayerController.getTVMenuAdapter().setDiscrete(false);
                if(saveSettingCB.isChecked()){
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), PlayerService.sPlayerService.getAudioDelay()).apply();
                    ;
                }
                else {
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), 0).apply();
                }
            }
        });

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        mPlayerController.addToMenuContainer(dialogContainer);
        tvPicker.requestFocus();
    }

    private String getTVSizeText(int size) {
        if (size<20)
            return "ABC";
        else if (size<50)
            return "AB";
        else 
            return "A";
    }

    private void refreshSubtitleTVMenu() {
        if (mSubtitleTVMenu != null) {
            mSubtitleTVMenu.clean();

            mPlayerController.getTVMenuAdapter().setCardViewVisibility(View.VISIBLE, mSubtitleTVCardView);

            if(mSubtitleInfoController.getTrackCount()>0) {
                for (int i = 0; i < mSubtitleInfoController.getTrackCount(); i++) {
                    mSubtitleTVMenu.createAndAddTVMenuItem(mSubtitleInfoController.getTrackNameAt(i).toString(), true, mSubtitleInfoController.getTrack() == i);
                }
                mSubtitleTVMenu.createAndAddSeparator();
                mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.player_pref_subtitle_delay_title).toString(), false, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        createTVSubtitleDialog();
                    }
                });
                mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.menu_player_settings).toString(), false, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        createTVSubtitleSettingsDialog();
                    }
                });
            }
            mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.get_subtitles_online).toString(), false, false).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadSubtitles();
                    mPlayerController.showTVMenu(false);
                }
            });

            Uri uri = VideoUtils.getFileUriFromMediaLibPath(mUri.toString());

            if (uri.getScheme().equals("file") || uri.getScheme().equals("smb")) {
                mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.get_subtitles_on_drive).toString(), false, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chooseSubtitles();
                        mPlayerController.showTVMenu(false);
                    }
                });
            }
        }
    }

    private void refreshAudioTracksTVMenu() {
        if(mAudioTracksTVMenu!=null){
            mAudioTracksTVMenu.clean();
            if (mAudioInfoController.getTrackCount() > 0) {
                mPlayerController.getTVMenuAdapter().setCardViewVisibility(View.VISIBLE, mAudioTracksTVCardView);
		    
		for (int i =0; i<mAudioInfoController.getTrackCount(); i++) {
                    mAudioTracksTVMenu.createAndAddTVMenuItem(mAudioInfoController.getTrackNameAt(i).toString(), true, mAudioInfoController.getTrack()==i);
                }

                if (isAudioFilterEnabled()) {
                    mAudioTracksTVMenu.createAndAddSeparator();
        
                    final TVMenuItem tvmi = mAudioTracksTVMenu.createAndAddTVSwitchableMenuItem(getResources().getString(R.string.pref_audio_filt_title),  PlayerService.sPlayerService.mAudioFilt > 0);
                    tvmi.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // TODO Auto-generated method stub
                            PlayerService.sPlayerService.setAudioFilt(PlayerService.sPlayerService.mAudioFilt > 0 ? 0 : 3);
                            tvmi.setChecked( PlayerService.sPlayerService.mAudioFilt>0);
                        }
                    });
        
                    final TVMenuItem tvmi2 = mAudioTracksTVMenu.createAndAddTVSwitchableMenuItem(getResources().getString(R.string.pref_audio_filt_night_mode),  PlayerService.sPlayerService.mNightModeOn);
                    tvmi2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // TODO Auto-generated method stub
                            PlayerService.sPlayerService.setNightMode(! PlayerService.sPlayerService.mNightModeOn);
                            tvmi2.setChecked( PlayerService.sPlayerService.mNightModeOn);
                        }
                    });
        
                    mAudioTracksTVMenu.createAndAddSeparator();
        
                    final TVMenuItem tvmi3 = mAudioTracksTVMenu.createAndAddTVMenuItem(getText(R.string.player_pref_subtitle_delay_title).toString(), false, false);
                    tvmi3.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            createTVAudioDelayDialog();
                        }
                    });
                }
            } else {
                mPlayerController.getTVMenuAdapter().setCardViewVisibility(View.GONE, mAudioTracksTVCardView);
            }
        }
    }

    public void createPlayerTVMenu() {
        if (mPlayerController != null && mPlayerController.getTVMenuAdapter() != null) {
            TVMenuAdapter tma = mPlayerController.getTVMenuAdapter();

            //[audiotrack]
            mAudioTracksTVCardView = tma.createAndAddView(null, getResources().getDrawable(R.drawable.tv_languages),
                                                          getResources().getString(R.string.menu_audio));
            mAudioTracksTVMenu = tma.createTVMenu();
            mAudioTracksTVMenu.setOnItemClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    int pos = mAudioTracksTVMenu.getItemPostion(v);
                    if (pos != -1) {
                        if (onTrackSelected(mAudioInfoController, pos, "", "")) {
                            if (v instanceof Checkable) {
                                mAudioTracksTVMenu.unCheckAll();
                                ((Checkable) v).setChecked(true);
                            }
                        }
                    }
                }
            });
            mAudioTracksTVCardView.addOtherView(mAudioTracksTVMenu);
            refreshAudioTracksTVMenu();
            //[/audiotrack]

            //[subtitles]
            mSubtitleTVCardView = tma.createAndAddView(null, getResources().getDrawable(R.drawable.tv_subtitles),
                                                       getResources().getString(R.string.menu_subtitles));
            mSubtitleTVMenu = tma.createTVMenu();
            mSubtitleTVMenu.setOnItemClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    int pos = mSubtitleTVMenu.getItemPostion(v);
                    if (pos != -1) {
                        if (onTrackSelected(mSubtitleInfoController, pos, "", "")) {
                            if (v instanceof Checkable) {
                                mSubtitleTVMenu.unCheckAll();
                                ((Checkable) v).setChecked(true);
                            }
                        }
                    }
                }
            });
            mSubtitleTVCardView.addOtherView(mSubtitleTVMenu);
            refreshSubtitleTVMenu();
            //[/subtitles]

            if (isStereoEffectOn()) {
                TVCardView cv = tma.createAndAddView(getResources().getDrawable(R.drawable.tv_3d), null,
                                                      getResources().getString(R.string.pref_s3d_mode_title));
                final TVMenu tvm3d = tma.createTVMenu();
                int dialogInitItem = (mPlayer.getEffectMode() != 0) ? Integer.numberOfTrailingZeros(mPlayer.getEffectMode()) : 0;
                tvm3d.setItems(R.array.pref_s3d_mode_entries_tv, dialogInitItem, true);
                tvm3d.setOnItemClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        if (v instanceof TVMenuItem) {
                            int which = tvm3d.getItemPostion(v);
                            set3DMode(which);
                            tvm3d.unCheckAll();

                            ((TVMenuItem)v).setChecked(true);
                        }
                    }
                });
                cv.addOtherView(tvm3d);
            }

            //[infomenu]
            TVCardView tcv = tma.createAndAddView(null, getResources().getDrawable(R.drawable.tv_info),
                                                  getResources().getString(R.string.menu_info));
            String decoder = VideoInfoCommonClass.getShortDecoder(mPlayer.getVideoMetadata(), getResources(), mPlayer.getType());
            
            if (decoder != null)
                tcv.setText2(decoder);
            
            tcv.setOnSwitchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showVideoInfos();
                    v.postDelayed(new Runnable() { // delayed otherwise another onKeyDown pauses the player (will inspect that issue)
                        @Override
                        public void run() {
                            mPlayerController.showTVMenu(false);
                        }
                    }, 20);
                }
            });
		
            final TVMenu tm = tma.createTVMenu();

            tcv.addOtherView(tm);
            //[/infomenu]

            // Scale (format) type
            tcv = tma.createAndAddView(getResources().getDrawable(R.drawable.tv_format), null,
                                       getResources().getString( R.string.pref_format_mode_title));
            final TVMenu tvmFormat = tma.createTVMenu();

            tvmFormat.setItems(R.array.pref_format_mode_entries, mSurfaceController.getCurrentFormat(), true);


            if (mSurfaceController.getMax() < 3) {
                tvmFormat.getItem(2).setVisibility(View.GONE);
                if (tvmFormat.getSlaveView() != null)
                    ((TVMenu)tvmFormat.getSlaveView()).getItem(2).setVisibility(View.GONE);
            }
            if (mSurfaceController.getMax() < 2) {
                tvmFormat.getItem(1).setVisibility(View.GONE);
                if (tvmFormat.getSlaveView() != null)
                    ((TVMenu)tvmFormat.getSlaveView()).getItem(1).setVisibility(View.GONE);
            }
            final View vPicInPic;
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N&& ArchosFeatures.isAndroidTV(this)) {
                tvmFormat.createAndAddSeparator();
                 vPicInPic = tvmFormat.createAndAddTVMenuItem(getString(R.string.picture_in_picture), false, false);
            }
            else vPicInPic = null;
            tvmFormat.setOnItemClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    if(v == vPicInPic){
                        enterPictureInPictureMode();
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mPlayerController.showTVMenu(false);
                            }
                        });
                    }
                    if (v instanceof TVMenuItem) {
                        int which = tvmFormat.getItemPostion(v);
                        mSurfaceController.setVideoFormat(tvmFormat.getItemPostion(v));
                        tvmFormat.unCheckAll();
                        ((TVMenuItem)v).setChecked(true);
                    }
                }
            });

            tcv.addOtherView(tvmFormat);

            //[playmode]
            tcv = tma.createAndAddView(null, getResources().getDrawable(R.drawable.tv_playmode),
                                       getResources().getString(R.string.pref_play_mode_title));
            final TVMenu tvmPlayMode = tma.createTVMenu();
            tvmPlayMode.setOnItemClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v instanceof TVMenuItem) {
                        tvmPlayMode.unCheckAll();
                        ((TVMenuItem) v).toggle();
                        int which = tvmPlayMode.getItemPostion(v);
                        if (which > -1) {
                            PlayerService.sPlayerService.menuChangePlayMode(which);
                        }
                    }
                }
            });

            tvmPlayMode.setItems(R.array.pref_play_mode_entries, PlayerService.sPlayerService.mPlayMode, true);
            tcv.addOtherView(tvmPlayMode);
            //[/playmode]
            /*
            //[sleep timer]
            TVCardView timerTVCardView = tma.createAndAddView(null, getResources().getDrawable(R.drawable.ic_menu_delay),
                    getResources().getString(R.string.sleep_timer_title));
            timerTVCardView.setOnSwitchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createTVTimerDialog();
                }
            });
            //[/sleep timer]
            */

            // Do not display notification menu on Android TV devices because they have no actual "notifications"
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                TVCardView cv = tma.createAndAddView(null, getResources().getDrawable(R.drawable.tv_notifications),
                                                     getResources().getString(R.string.notification_mode));
                final TVMenu tvm2 = tma.createTVMenu();
                tvm2.setOnItemClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (v instanceof TVMenuItem) {
                            int newNotificationMode = tvm2.getItemPostion(v); // Caution here, NotificationMode values must be [0,n[
                            setNewNotificationMode(newNotificationMode);
                            tvm2.unCheckAll();
                            ((TVMenuItem) v).toggle();
                        }
                    }
                });
                tvm2.setItems(R.array.pref_notification_mode_entries, mNotificationMode, true);
                cv.addOtherView(tvm2);
            }

            final Activity mActivity = this;
            tcv = tma.createAndAddView(null, getResources().getDrawable(R.drawable.tv_settings),
                                       getResources().getString(R.string.preferences));
            tcv.setOnSwitchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    Intent p = new Intent(Intent.ACTION_MAIN);
                    p.setComponent(new ComponentName(mActivity, VideoPreferencesActivity.class));
                    startActivity(p);
                }
            });
        }
    }

    // Common methods for TV and normal menu
    private void set3DMode(int which) {
        if (which > -1) {
            int mode = 1 << which;
            switch (which) {
                case 0:
                    mode = VideoEffect.getDefaultMode();
                    break;
                default:
                    break;
            }
            setEffectForced(mode);
        }
    }



    private void setNewNotificationMode(int newNotificationMode){
        if (newNotificationMode != mNotificationMode) {
            // The user selected a new mode
            mNotificationMode = newNotificationMode;
            // Apply the selected mode
            applyNotificationsMode(mNotificationMode);
            // Store immediately the new mode in case the video player crashes before the end...
            mPreferences.edit()
            .putInt(KEY_NOTIFICATIONS_MODE, newNotificationMode)
            .apply(); // commit is blocking.. avoid!
        }
    }

    public void bookmark() {
        if (mVideoInfo != null) {
            mVideoInfo.bookmark = getBookmarkPosition();
            mIndexHelper.writeVideoInfo(mVideoInfo, mNetworkBookmarksEnabled);
            mPlayerController.updateBookmarkToast(mPlayer.getCurrentPosition());
        }
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();

        if (!isTVMode) {
            MenuItem menuItem;

            //------------------------------------------------------------------
            // Add first the items related to the current video
            //------------------------------------------------------------------
            mInfoMenuItem = menu.add(MENU_FILE_ACTIONS_GROUP, MENU_INFO_ID, Menu.NONE, R.string.menu_info);
            if (mInfoMenuItem != null) {
                mInfoMenuItem.setIcon(R.drawable.ic_menu_info)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            mBookmarkMenuItem = menu.add(MENU_FILE_ACTIONS_GROUP, MENU_BOOKMARK_ID, Menu.NONE, R.string.menu_bookmark);
            if (mBookmarkMenuItem != null) {
                mBookmarkMenuItem.setIcon(R.drawable.ic_menu_bookmark)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            mAudioInfoController.attachMenu(menu, R.drawable.ic_menu_languages);
            mSubtitleInfoController.attachMenu(menu, R.drawable.ic_menu_subtitles);
            //------------------------------------------------------------------
            // Then add the global items related to the video player
            //------------------------------------------------------------------

            menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_LOCK_ID, Menu.NONE, R.string.menu_lock_player);
            if (!ArchosFeatures.hasNoTouchScreen() & !isPluggedOnTv()) {

                mBrightnessMenuItem = menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_BRIGHTNESS_ID, Menu.NONE, R.string.menu_brightness_settings);
                if (mBrightnessMenuItem != null) {
                    mBrightnessMenuItem.setIcon(R.drawable.ic_menu_brightness)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
                if (mPlayer!=null&&mPlayer.getEffectType()==VideoEffect.EFFECT_NONE) {
                    menuItem = menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_LOCK_ROTATION_ID,
                            Menu.NONE,R.string.rotation_unlock);
                    if (menuItem != null) {
                        menuItem.setIcon(mLockRotation ? R.drawable.ic_menu_locked : R.drawable.ic_menu_unlocked)
                        //.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                        menuItem.setCheckable(true);
                        menuItem.setChecked(!mLockRotation);
                    }
                }
            }

            menuItem = menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_NOTIFICATION_MANAGEMENT_ID,
                    Menu.NONE, R.string.notification_mode);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_notifications);
                menuItem.setShowAsAction(!isPluggedOnTv()?MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            //------------------------------------------------------------------
            // Finally add the other items (which will be available in the menu)
            //------------------------------------------------------------------
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_PLAYMODE_ID, Menu.NONE, R.string.pref_play_mode_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_playmode);
                menuItem.setShowAsAction(!isPluggedOnTv()?MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            if (isAudioFilterEnabled()) {
                menuItem = menu.add(MENU_OTHER_GROUP, MENU_AUDIO_FILTER_ID, Menu.NONE, R.string.pref_audio_parameters_title);
                if (menuItem != null) {
                    menuItem.setIcon(R.drawable.ic_menu_audioboost);
                    menuItem.setShowAsAction(!isPluggedOnTv()?MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
            }
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_AUDIO_DELAY_ID, Menu.NONE, R.string.player_pref_audio_delay_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_delay);
                menuItem.setShowAsAction(!isPluggedOnTv() ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_S3D_ID, Menu.NONE, R.string.pref_s3d_mode_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_3d);
                menuItem.setShowAsAction(!isPluggedOnTv()?MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            // Check if the brightness item can be enabled
            /*if (mBrightnessMenuItem != null) {
                int brightnessMode = 0;
                if (ArchosFeatures.hasLightSensor()) {
                    try {
                         brightnessMode = Settings.System.getInt(getContentResolver(),
                                         Settings.System.SCREEN_BRIGHTNESS_MODE);
                    } catch (SettingNotFoundException e) {
                    }
                }
                mBrightnessMenuItem.setVisible(brightnessMode == 0);
            }*/
            menu.add(MENU_OTHER_GROUP, MENU_WINDOW_MODE, Menu.NONE, R.string.player_window_mode);
            // Always add a link to the general application preferences
            menu.add(MENU_OTHER_GROUP, MENU_PREFERENCES, Menu.NONE, R.string.preferences)
            .setIcon(R.drawable.ic_menu_settings)
            .setShowAsAction(!isPluggedOnTv()?MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
        if (mPlayer==null||mPlayer.isBusy()) {
            return false;
        }

        // The first time onPrepareOptionsMenu() is called, Video info can be
        // not available. However this does not matter because this item will
        // be updated anyway after loading the video
        if (mInfoMenuItem != null) {
            mInfoMenuItem.setVisible(mStreamingUri != null);
        }

        // The first time onPrepareOptionsMenu() is called we don't know yet if
        // the "set bookmark" item can be enabled or not. However this does not
        // matter because this item will be updated anyway after loading the video
        if (mBookmarkMenuItem != null)
            mBookmarkMenuItem.setVisible(canSetBookmark());
        if (menu.findItem(MENU_S3D_ID) != null)
            menu.findItem(MENU_S3D_ID).setVisible(isStereoEffectOn());
        /*if(menu.findItem(MENU_WINDOW_MODE)!=null)
            menu.findItem(MENU_WINDOW_MODE).setVisible(mPreferences.getBoolean(KEY_ADVANCED_VIDEO_ENABLED, false));*/
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_LOCK_ID:
                mPlayerController.lock();
                return true;
            case MENU_WINDOW_MODE:
                mLaunchFloatingPlayer = true;

                if(Build.VERSION.SDK_INT>=  Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this)){
                    displayFloatingWindowPermissionDialog();
                }
                else
                    startService(new Intent(this, FloatingPlayerService.class));
                //finish();
                return true;
            case MENU_INFO_ID:
                showVideoInfos();
                return true;
            case MENU_BOOKMARK_ID:
                if (mVideoInfo != null) {
                    mVideoInfo.bookmark = getBookmarkPosition();
                    mIndexHelper.writeVideoInfo(mVideoInfo, mNetworkBookmarksEnabled);
                    mPlayerController.updateBookmarkToast(mPlayer.getCurrentPosition());
                }
                return true;
            case MENU_BRIGHTNESS_ID:
                showDialog(DIALOG_BRIGHTNESS);
                return true;
            case MENU_LOCK_ROTATION_ID:
                mLockRotation = !mLockRotation;
                setLockRotation(mLockRotation);
                mPreferences.edit().putBoolean(KEY_LOCK_ROTATION, mLockRotation).apply();
                //item.setTitle(mLockRotation ? R.string.rotation_unlock : R.string.rotation_lock);
                item.setChecked(!mLockRotation);
                item.setIcon(mLockRotation ? R.drawable.ic_menu_locked : R.drawable.ic_menu_unlocked);
                Toast.makeText(
                        mContext,
                        mLockRotation ? R.string.rotation_locked : R.string.rotation_unlocked,
                                Toast.LENGTH_SHORT).show();
                return true;
            case MENU_NOTIFICATION_MANAGEMENT_ID: {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setTitle(R.string.notification_mode);
                adb.setSingleChoiceItems(R.array.pref_notification_mode_entries, mNotificationMode, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        setNewNotificationMode(which);
                        dialog.dismiss();
                    }
                });
                adb.create().show();
                return true;
            }
            case MENU_PLAYMODE_ID: {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setTitle(R.string.pref_play_mode_title);
                adb.setSingleChoiceItems(R.array.pref_play_mode_entries, PlayerService.sPlayerService.mPlayMode, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        PlayerService.sPlayerService.menuChangePlayMode(which);
                        dialog.dismiss();
                    }
                });
                adb.create().show();

                return true;
            }
            case MENU_AUDIO_FILTER_ID: {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setTitle(R.string.pref_audio_parameters_title);
                final ArrayList<RadioButton> rbs = new  ArrayList<RadioButton>();
                adb.setAdapter(new ArrayAdapter<View>(mContext, R.layout.menu_item_layout) {
                    @Override
                    public View getView(final int position, View convertView, ViewGroup parent) {
                        if (position > 0) {
                            Switch tb = new Switch(mContext);
                            tb.setText(R.string.pref_audio_filt_title);
                            tb.setPadding(20,20, 20, 20);
                            tb.setChecked( PlayerService.sPlayerService.mAudioFilt>0);
                            tb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                    PlayerService.sPlayerService.setAudioFilt(isChecked ? 3 : 0);
                                }
                            });
                            return tb;
                        }
                        else {
                            Switch tb = new Switch(mContext);
                            tb.setText(R.string.pref_audio_filt_night_mode);
                            tb.setPadding(20,20, 20, 20);
                            tb.setChecked(PlayerService.sPlayerService.mNightModeOn);
                            tb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                    PlayerService.sPlayerService.setNightMode(isChecked);
                                }
                            });
                            return tb;
                        }
                    }
                    @Override
                    public int getCount() {
                        return 2;
                    }
                }
                , new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

                ad = adb.create();
                ad.show();
                return true;
            }
            case MENU_AUDIO_DELAY_ID: {
                showDialog(DIALOG_AUDIO_DELAY);
                return true;
            }
            case MENU_S3D_ID: {
                int menuId = R.array.pref_s3d_mode_entries;
                if (!isStereoEffectOn()) return true;

                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                Switch tb = new Switch(mContext);
                tb.setText(R.string.pref_s3d_mode_title);
                tb.setTextSize(25);
                tb.setPadding(20,20, 20, 20);

                final int dialogInitItem = (mPlayer.getEffectMode()!=0)?Integer.numberOfTrailingZeros(mPlayer.getEffectMode()):0;
                adb.setCustomTitle(tb);

                if (!isPluggedOnTv()) {
                    menuId = R.array.pref_s3d_mode_entries_dive;
                }
                final CharSequence[] t = mContext.getResources().getTextArray(menuId);          
                final ArrayList<RadioButton>rbs = new  ArrayList<RadioButton>();
                final int menuId2= menuId;
                adb.setAdapter(new ArrayAdapter<View>(mContext, R.layout.menu_item_layout){
                    @Override
                    public View getView(final int position, View convertView, ViewGroup parent) {

                        RadioButton rb= new RadioButton(mContext);
                        rb.setText(t[position]);
                        rb.setEnabled(dialogInitItem!=VideoEffect.NORMAL_2D_MODE);
                        rbs.add(rb);
                        rb.setPadding(20,20, 20, 20);
                        rb.setChecked((dialogInitItem>0?dialogInitItem-1:(Integer.numberOfTrailingZeros(mSavedMode)>0?Integer.numberOfTrailingZeros(mSavedMode)-1:0))==position);
                        rb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                // TODO Auto-generated method stub
                                if (isChecked) {
                                    set3DMode(position+1);
                                    for (int i =0; i< rbs.size(); i++) {
                                        if (buttonView!=rbs.get(i))
                                            rbs.get(i).setChecked(false);
                                    }
                                    if (ad != null) {
                                        ad.dismiss();
                                        ad = null;
                                    }
                                }
                            }
                        });
                        return rb;
                    }
                    @Override
                    public int getCount() {
                        return mContext.getResources().getTextArray(menuId2).length;
                    }
                }
                , new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });              
                ad = adb.create();
                ad.show();

                if (dialogInitItem == VideoEffect.NORMAL_2D_MODE) {
                    ad.getListView().setEnabled(false);
                }
                tb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // TODO Auto-generated method stub
                        if (isChecked) {
                            setEffectForced(mSavedMode);
                            ad.getListView().setEnabled(true);
                            for(RadioButton rb : rbs)
                                rb.setEnabled(true);
                        }
                        else {
                            setEffectForced(VideoEffect.NORMAL_2D_MODE, false);
                            ad.getListView().setEnabled(false);
                            for (RadioButton rb : rbs)
                                rb.setEnabled(false);
                        }
                    }
                });
                tb.setChecked(mPlayer.getEffectMode()!=VideoEffect.NORMAL_2D_MODE);
                return true;
            }
            case MENU_PREFERENCES: {
                Intent p = new Intent(Intent.ACTION_MAIN);
                p.setComponent(new ComponentName(this, VideoPreferencesActivity.class));
                startActivity(p);
                return true;
            }
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void displayFloatingWindowPermissionDialog() {


        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(R.string.error_permission_display_over_apps).setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mHasAskedFloatingPermission = true;
                Intent in = new Intent();
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                in.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                in.putExtra("android.intent.extra.PACKAGE_NAME", getPackageName());
                startActivity(in);
            }
        }).setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                mHasAskedFloatingPermission = false;
            }
        })
                .setNegativeButton(android.R.string.cancel, null).show();


    }

    private void showVideoInfos() {
        mPlayerController.hide();

        Class infoActivity = null;

        try {
            // When on an actual leanback device (Android TV, etc.) we give no choice -> Leanback!
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                infoActivity = getClassLoader().loadClass("com.archos.mediacenter.video.leanback.details.VideoDetailsOverlayActivity");
            }
            else {
                // string definitions are in preference_video.xml and in @array/ui_mode_leanback_entryvalues
                final String uiMode = PreferenceManager.getDefaultSharedPreferences(this)
                        .getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");

                if (uiMode.equals(UiChoiceDialog.UI_CHOICE_LEANBACK_TV_VALUE)) {
                    // User explicitly choose TV mode
                    infoActivity = getClassLoader().loadClass("com.archos.mediacenter.video.leanback.details.VideoDetailsOverlayActivity");
                } else { // user did not choose or user explicitly choose tablet mode
                    infoActivity = VideoInfoActivity.class;
                }
            }
        } catch (ClassNotFoundException ex) {
            infoActivity = VideoInfoActivity.class; // fallback to dialog
        }

        Intent intent = new Intent(this, infoActivity);

        if (mPlayer.getType() == IMediaPlayer.TYPE_AVOS && fileExists()) {
            intent.setData(Uri.fromFile(mPlayer.getVideoMetadata().getFile()));
        } else {
            intent.setData(mUri);
        }

        if (mPlayer.getType() == IMediaPlayer.TYPE_AVOS) {
            intent.putExtra(VideoInfoActivity.EXTRA_USE_VIDEO_METADATA, mPlayer.getVideoMetadata());
        }
        intent.putExtra(VideoInfoActivity.EXTRA_PLAYER_TYPE, mPlayer.getType());
        intent.putExtra(VideoInfoActivity.EXTRA_VIDEO_ID, mVideoId);
        intent.putExtra(VideoInfoActivity.EXTRA_LAUNCHED_FROM_PLAYER, true);
        startActivity(intent);
        mIsInfoActivityDisplayed = true;
    }

    private void applyNotificationsMode(int mode) {
        switch (mode) {
            case NOTIFICATION_MODE_ALL:
                mPlayerController.enableAllNotifications();
                break;

            case NOTIFICATION_MODE_ALERTS:
                mPlayerController.enableNotificationAlerts();
                break;

            case NOTIFICATION_MODE_NONE:
                mPlayerController.disableNotifications();
                break;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        switch(id) {
            case DIALOG_SUBTITLE_DELAY:
                SubtitleTrack track = mPlayer.getVideoMetadata().getSubtitleTrack(mVideoInfo.subtitleTrack);
                if (track == null)
                    return null;
                boolean hasRatio = track.isExternal;
                dialog = new SubtitleDelayPickerDialog(this, this, mVideoInfo.subtitleDelay, mVideoInfo.subtitleRatio, hasRatio);
                break;
            case DIALOG_SUBTITLE_SETTINGS:
                dialog = new SubtitleSettingsDialog(this, mSubtitleManager);
                break;
            case DIALOG_AUDIO_DELAY:
                if(PlayerService.sPlayerService!=null)
                    dialog = new AudioDelayPickerDialog(this, this, PlayerService.sPlayerService.getAudioDelay());
                else
                    dialog = new AudioDelayPickerDialog(this, this, mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0));
                break;
            case DIALOG_NOT_ENOUGHT_SPACE:
                dialog= new AlertDialog.Builder(this)
                    .setTitle(R.string.player_err_cantplayvideo)
                    .setMessage(R.string.error_downloading_not_enough_space)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    finish();
                                }
                            })
                    .setCancelable(false)
                    .create();
                break;
            case DIALOG_ERROR:
                if (mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_ALLOWED) {
                    dialog = new AlertDialog.Builder(this)
                            .setTitle(R.string.player_err_cantplayvideo)
                            .setMessage(buildErrorMessage(mErrorCode, mErrorQualCode, 0, mErrorMsg))
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    finish();
                                }
                            })
                            .setCancelable(false)
                            .create();
                }
                break;
            case DIALOG_BRIGHTNESS:
                dialog = new BrightnessDialog(this);
                break;
            case DIALOG_CODEC_NOT_SUPPORTED:
                String msg;
                int storeStringId;
                int notSupportedId;

                // First check if there are already codecs needing an update
                dialog = getPluginNeedUpdateDialog();
                // If no update needed display the regular codec upselling dialog
                if (dialog == null) {
                    notSupportedId = mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED ?
                            R.string.videocodec_not_supported :
                                R.string.audiocodec_not_supported;

                    msg = mResources.getString(notSupportedId, mErrorMsg) + "\n";
                    msg += mResources.getString(R.string.player_plugin_purchase_google_play_msg);
                    storeStringId = R.string.player_plugin_purchase_google_play_button;
                    final OnClickListener onCancelButtonClick = new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED)
                                finish();
                            else
                                dialog.cancel();
                        }
                    };
                    final int storeStringIdFinal = storeStringId;
                    final OnClickListener onPositiveButtonClick = new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=archos+video+plugins")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (ActivityNotFoundException e) {}
                        }
                    };
                    dialog = new AlertDialog.Builder(this)
                    .setTitle(mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED ?
                            R.string.player_err_cantplayvideo : R.string.player_err_cantplaysound)
                            .setMessage(msg)
                            .setNegativeButton(android.R.string.cancel, onCancelButtonClick)
                            .setPositiveButton(storeStringId, onPositiveButtonClick)
                            .setNeutralButton(R.string.learn_more, new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    new AlertDialog.Builder(PlayerActivity.this)
                                            .setTitle(R.string.learn_more)
                                            .setMessage(R.string.learn_more_about_codecs)
                                            .setNegativeButton(android.R.string.cancel, onCancelButtonClick)
                                            .setPositiveButton(storeStringIdFinal, onPositiveButtonClick)
                                    .show();

                                }
                            })
                            .setCancelable(true)
                            .create();
                }
                break;
            case DIALOG_WRONG_DEVICE_KINDLE:
                dialog = new AlertDialog.Builder(this)
                .setTitle("Incompatible device")
                .setMessage("This application runs only on Amazon Kindle")
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                })
                .setCancelable(false)
                .create();
                break;
            default:
                dialog = null;
                break;
        }
        if (dialog != null) {
            dialog.setOnDismissListener(this);
        }
        return dialog;
    }

    /**
     * @return null if there is no update needed
     */
    private Dialog getPluginNeedUpdateDialog() {
        if (LibAvos.pluginNeedUpdate(this)) {
            Log.d(TAG, "pluginNeedUpdate returns true");
            return new AlertDialog.Builder(this)
            .setTitle(R.string.plugin_update_required_title)
            .setMessage(R.string.plugin_update_required_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.player_plugin_purchase_google_play_button,  new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=archos+video+plugins")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        finish(); // probably safer to "quit" AVP now
                    } catch (ActivityNotFoundException e) {}
                }
            })
            .setCancelable(true)
            .create();
        }
        else {
            return null;
        }
    }

    public void onDismiss(DialogInterface dialog) {
        mShowingDialogId = DIALOG_NO;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog masterDialog) {
        mShowingDialogId = id;
        switch (id) {
            case DIALOG_SUBTITLE_DELAY:
                mPlayerController.hide();
                ((SubtitleDelayPickerDialog)masterDialog).updateDelay(mVideoInfo.subtitleDelay);
                break;
            case DIALOG_SUBTITLE_SETTINGS:
                mPlayerController.hide();
                break;
            case DIALOG_AUDIO_DELAY:
                mPlayerController.hide();
                AudioDelayPickerDialog dialog = (AudioDelayPickerDialog)masterDialog;
                dialog.setStep(20);
                if (mPlayer.getDuration() > 0) {
                    dialog.setMax(mPlayer.getDuration());
                    dialog.setMin(-mPlayer.getDuration());
                }
                if(PlayerService.sPlayerService!=null)
                    dialog.updateDelay(PlayerService.sPlayerService.getAudioDelay());
                else
                    dialog.updateDelay(mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0));

                break;
            case DIALOG_BRIGHTNESS:
                mPlayerController.hide();
                break;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        mPlayerController.onWindowFocusChanged(hasFocus);
        // this is called when systembar gets implicitly visible
        // when top menu gets visible
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    


    @Override
    public void onPermissionGranted() {
        if(PlayerService.sPlayerService!=null)
            postOnPlayerServiceBind();
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

    private final static String SHOW_FORMAT = "%s  -  S%02dE%02d  -  %s";

    public void setVideoInfo(VideoDbInfo info){
        mVideoInfo = info;
        int viewMode = VideoEffect.getDefaultMode();
        int viewType = VideoEffect.getDefaultType();
        if (mVideoInfo != null) {
            // one of them is already known, don't care and overwrite both

            mVideoId = mVideoInfo.id;
            mUri = mVideoInfo.uri;
            if (DBG) Log.d(TAG, "mVideoId: " + mVideoId);


            // get resume position only if video was played
            mLastPosition = getLastPosition(mVideoInfo, mResume);
            if (!mCling) {
                mTitle = mVideoInfo.title;
            }
            mMovieOrShowName = null;
            mEpisode = null;
            mPosterPath = null;
            mPoster = false;

            if (mVideoInfo.isScraped) {
                mMovieOrShowName = mVideoInfo.scraperTitle;
                if (mMovieOrShowName != null) {
                    if (mVideoInfo.isShow) {
                        mTitle = String.format(SHOW_FORMAT, mMovieOrShowName, mVideoInfo.scraperSeasonNr, mVideoInfo.scraperEpisodeNr, mVideoInfo.scraperEpisodeName);
                    } else {
                        mTitle = mMovieOrShowName;
                    }
                }
                if (!TextUtils.isEmpty(mVideoInfo.scraperCover)) {
                    mPosterPath = mVideoInfo.scraperCover;
                }
            }

            switch (mVideoInfo.videoStereo) {
                case 4: // Anaglyph mode
                    viewMode = VideoEffect.ANAGLYPH_MODE;
                    break;
                case 3: // Top bottom mode
                    viewMode = VideoEffect.TB_MODE;
                    break;
                case 2: //SBS Mode
                case 1: //3D Mode
                    viewMode = VideoEffect.SBS_MODE;
                    break;
                case 0:
                default: //Normal mode
                    viewMode = VideoEffect.getDefaultMode();
                    break;
            }
        }

        if (!isPluggedOnTv() || MainActivity.mStereoForced) {
            viewType = VideoEffect.EFFECT_STEREO_SPLIT;
        } else {
            if (mLudoHmdiPlugged)
                viewType = VideoEffect.EFFECT_STEREO_MERGE_ARCHOS;
            else
                viewType = VideoEffect.EFFECT_STEREO_MERGE;
        }

        if (viewMode == VideoEffect.NORMAL_2D_MODE)
            viewType = VideoEffect.EFFECT_NONE;

        setEffect(viewType, viewMode);

        /*
         * check if kindle apk (without lvl) run on amazon device
         * we need to com.archos.mediacenter.videoki in 2 strings because of
         * the mighty sed that s/com.archos.mediacenter.video/com.archos.mediacenter.videoki/
         */
        if (getPackageName().equals("com.archos.mediacenter"+"."+"video"+"ki")) {
            Log.d(TAG, "amazon?");
            if (Build.BRAND == null || !Build.BRAND.equalsIgnoreCase("a"+"m"+"a"+"z"+"o"+"n")) {
                showDialog(DIALOG_WRONG_DEVICE_KINDLE);
                return;
            }
        }

        mPlayerController.setVideoTitle(mTitle);

        postVideoInfoAndPrepared();
    }

    /**
     * set start state = removing progress + enabling controllers
     */
    private void  postVideoInfoAndPrepared() {
        if (DBG) Log.d(TAG, "postVideoInfoAndPrepared "+String.valueOf((PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PREPARED||PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PLAYING) && mVideoInfo != null));
        if (DBG) Log.d(TAG, "postVideoInfoAndPrepared "+String.valueOf((PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PREPARED||PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PLAYING)));
        if (DBG) Log.d(TAG, "postVideoInfoAndPrepared "+String.valueOf( mVideoInfo != null));

        // ex onStreamingUriOK
        if ((PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PREPARED||PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PLAYING) && mVideoInfo != null) {
            if (DBG) Log.d(TAG, "postVideoInfoAndPrepared");
            if (mThumbnail != null) {
                mThumbnail.recycle();
                mThumbnail = null;
            }
            mThumbnailDone = 0;
            mHandler.removeMessages(MSG_PROGRESS_VISIBLE);
            mProgressView.setVisibility(View.GONE);
            PlayerService.sPlayerService.setAudioFilt();
            mPlayerController.start();
            // Now that the video is loaded, Video info should be avalaible
            if (mInfoMenuItem != null) {
                mInfoMenuItem.setVisible(mStreamingUri != null);
            }
            // Now that the video is loaded we know if the "set bookmark" item can be enabled or not
            if(mBookmarkMenuItem!=null) {
                mBookmarkMenuItem.setVisible(canSetBookmark());
            }
                mPlayerListener.onAudioMetadataUpdated(mPlayer.getVideoMetadata(), mNewAudioTrack);
                mPlayerListener.onSubtitleMetadataUpdated(mPlayer.getVideoMetadata(), mNewSubtitleTrack);


        }

    }

    private boolean isStereoEffectOn() {
        return VideoEffect.isStereoEffectOn(mPlayer.getEffectType());
    }

    @Override
    public void onVideoDb(final VideoDbInfo localVideoInfo, final VideoDbInfo remoteVideoInfo) {

    }

    public void showTraktResumeDialog(final int localTraktPosition, VideoDbInfo localVideoInfo) {
    	mVideoInfo = localVideoInfo;
        if(PlayerService.sPlayerService!=null){
            PlayerService.sPlayerService.setVideoInfo(mVideoInfo);
            PlayerService.sPlayerService.requestIndexAndScrap();
        }
    	// if we want to display a dialog for trakt resume, uncomment this
    	/* Log.d(TAG, "onVideoDb: trakt: into dialog");
    	if(localTraktPosition>0&&
        		(localTraktPosition<localVideoInfo.resume-60000||localTraktPosition>localVideoInfo.resume+60000)){
    		Log.d(TAG, "onVideoDb: trakt: showing dialog");
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.use_trakt_resume)
            .setCancelable(false)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    mVideoInfo.resume = localTraktPosition;
                    postStart();
                }
            })
            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            
                            postStart();
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    	else */
        setVideoInfo(mVideoInfo);
    }

    @Override
    public void onScraped(ScrapeDetailResult result) {
        PlayerService.sPlayerService.onScraped(result);
    }

    /**
     * send progress visible
     * request DataUri
     *
     */

    private void start() {
        Intent intent = getIntent();
        mPlayer.setSurfaceController(mSurfaceController);
        mPlayer.setWindow(getWindow());
        if (DBG) Log.d(TAG, "start: " + intent);
        if (mBufferView != null)
            mBufferView.setText("");
        mHandler.removeMessages(MSG_PROGRESS_VISIBLE);
        if(!Player.sPlayer.isPlaying())
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PROGRESS_VISIBLE), PROGRESS_VISIBLE_DELAY);

        if (mUri == null) {
            showDialog(DIALOG_ERROR);
            return;
        }

        mLastPosition = intent.getIntExtra("position", -1);
        mShowingDialogId = DIALOG_NO;
        if (mForceAudioTrack != -1) {
            mVideoInfo.audioTrack = mForceAudioTrack;
            mForceAudioTrack = -1;
        }

        mPlayerController.setMediaPlayer(mPlayer);

        mPlayerController.setVideoTitle(mTitle);
        //mVideoId = getIntent().getIntExtra("id", -1);

        mCling = intent.getBooleanExtra("cling", false);
        final String clingName = mCling ? intent.getStringExtra("title") : null;

        if (clingName != null) {
            mExtraMap = new HashMap<String, String>();
            mExtraMap.put("extra_name", clingName);
        }

    }

    private void stop() {

        if (DBG) Log.d(TAG, "stop");


        mPlayer.pause();

        mHandler.removeMessages(MSG_PROGRESS_VISIBLE);
        mProgressView.setVisibility(View.GONE);

        mSubtitleInfoController.clear();
        mAudioInfoController.clear();
        mSubtitleManager.stop();
        mPlayerController.stop();
        Intent intent = new Intent(STOPPED_VIDEO_INTENT);
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        sendBroadcast(intent);

    }


    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults){
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private boolean canSetBookmark() {
        return false;  //06/2015: no more bookmark feature!
        //return mPlayer.canSeekBackward() && mPlayer.canSeekForward();
    }

    private boolean fileExists() {
        if (mPlayer.getVideoMetadata() == null || mPlayer.getVideoMetadata().getFile() == null)
            return false;
        return mPlayer.getVideoMetadata().getFile().exists();
    }

    private int getBookmarkPosition() {
        if (mPlayer.getDuration() != 0) {
            /* resume a little before */
            int position = mPlayer.getCurrentPosition();
            return position > 3000 ? position - 3000 : 0;
        } else {
            return mPlayer.getRelativePosition();
        }
    }

    private void appendCodec(StringBuilder stringBuilder, String csq) {
        if (csq.equals("s_none"))
            stringBuilder.append(" ");
        else
            stringBuilder.append(" \"").append(csq).append("\" ");
    }

    private String buildErrorMessage(int errorCode, int errorQualCode, int plugin, String msg) {
        Resources r = mResources;
        StringBuilder msgBuilder = new StringBuilder();
        final VideoMetadata vMetadata = mPlayer.getVideoMetadata();

        if (mUri == null) {
            /* File doesn't exist */
            msgBuilder.append(r.getText(R.string.player_err_file));
        } else if (errorCode < IMediaPlayer.MEDIA_ERROR_VE_NO_ERROR || vMetadata == null) {
            /* Android errors */
            msgBuilder.append(r.getText(R.string.player_err_critical));
        } else {
            /* Avos stream errors */
            VideoMetadata.VideoTrack video = vMetadata.getVideoTrack();
            VideoMetadata.AudioTrack audio = null;
            if (mVideoInfo != null &&
                    mVideoInfo.audioTrack >= 0 &&
                    mVideoInfo.audioTrack < vMetadata.getAudioTrackNb()) {
                audio = vMetadata.getAudioTrack(mVideoInfo.audioTrack);
            } else if (vMetadata.getAudioTrackNb() > 0) {
                audio = vMetadata.getAudioTrack(0);
            }
            int width = vMetadata.getVideoWidth();
            int height = vMetadata.getVideoHeight();
            String errorDesc = msg != null ? msg : mPlayer.getErrorDesc();

            switch (errorCode) {
                case IMediaPlayer.MEDIA_ERROR_VE_TOO_BIG_FOR_STREAM:
                    if (video != null) {
                        msgBuilder.append(r.getText(R.string.player_err_sizetoobig)).append("\n(")
                        .append(width).append("x").append(height).append(")\n");
                    }
                    break;
                case IMediaPlayer.MEDIA_ERROR_VE_TOO_BIG_FOR_CODEC:
                    if (video != null) {
                        msgBuilder.append(r.getText(R.string.player_err_sizetoobig)).append(" ")
                        .append(r.getText(R.string.player_err_forcodec))
                        .append(" \"").append(video.format).append("\"");
                        if (width > 0 && height > 0) {
                            msgBuilder.append(" (")
                            .append(width).append("x").append(height).append(")");
                        }
                    } else {
                        msgBuilder.append(r.getText(R.string.player_err_unknown));
                    }
                    break;
                case IMediaPlayer.MEDIA_ERROR_VE_NOT_INTERLEAVED:
                    msgBuilder.append(r.getText(R.string.player_err_notinterleaved));
                    break;
                case IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_ALLOWED: {
                    int audioIdx = 0;
                    boolean hasVideo = video != null;
                    boolean hasAudio = false;

                    for (audioIdx = 0; audioIdx < vMetadata.getAudioTrackNb(); ++audioIdx) {
                            hasAudio = true;
                            audio = vMetadata.getAudioTrack(audioIdx);
                            break;
                    }
                    if (hasVideo && hasAudio) {
                        msgBuilder.append(getString(R.string.player_plugin_video_audio_msg, video.format, audio.format))
                        .append("\n");
                    } else if (hasVideo) {
                        msgBuilder.append(getString(R.string.player_plugin_video_msg, video.format))
                        .append("\n");
                    } else if (hasAudio) {
                        msgBuilder.append(getString(R.string.player_plugin_audio_msg, audio.format))
                        .append("\n");
                    }
                    break;
                }
                case IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED:
                    switch (errorQualCode) {
                        case IMediaPlayer.MEDIA_ERROR_VEQ_SEE_DESCRIPTION:
                            if (errorDesc != null && !errorDesc.equals("null")) {
                                msgBuilder.append(errorDesc);
                            } else {
                                msgBuilder.append(r.getText(R.string.player_err_unknown));
                            }
                            break;
                        default:
                            if (video != null) {
                                msgBuilder.append(r.getText(R.string.player_err_codec));
                                appendCodec(msgBuilder, video.format);
                                msgBuilder.append(r.getText(R.string.player_err_isnotsupported));
                            } else {
                                msgBuilder.append(r.getText(R.string.player_err_unknown));
                            }
                            break;
                    }
                    break;

                case IMediaPlayer.MEDIA_ERROR_VE_AUDIO_NOT_ALLOWED:
                case IMediaPlayer.MEDIA_ERROR_VE_AUDIO_NOT_SUPPORTED:
                    if (audio != null) {
                        msgBuilder.append(r.getText(R.string.player_err_codec));
                        appendCodec(msgBuilder, audio.format);
                        msgBuilder.append(r.getText(R.string.player_err_isnotsupported));
                    } else {
                        msgBuilder.append(r.getText(R.string.player_err_unknown));
                    }
                    break;

                case IMediaPlayer.MEDIA_ERROR_VE_CRYPTED:
                    msgBuilder.append(r.getText(R.string.player_err_fileNoLicense));
                    break;

                case IMediaPlayer.MEDIA_ERROR_VE_FILE_ERROR:
                    if(Player.sPlayer.checkCurrentFileExists()==0)
                        msgBuilder.append(r.getText(R.string.player_err_file));
                    else
                        msgBuilder.append(r.getText(R.string.player_err_fileerror));
                    break;

                case IMediaPlayer.MEDIA_ERROR_VE_CONNECTION_ERROR:
                    msgBuilder.append(r.getText(R.string.player_err_connection_failed));
                    break;
                case IMediaPlayer.MEDIA_ERROR_VE_ERROR:
                default:
                    switch (errorQualCode) {
                        case IMediaPlayer.MEDIA_ERROR_VEQ_SEE_DESCRIPTION:
                            if (errorDesc != null && !errorDesc.equals("null")) {
                                msgBuilder.append(r.getText(R.string.player_err_video_decoder_error))
                                .append(" \"").append(errorDesc).append("\"");
                            } else {
                                msgBuilder.append(r.getText(R.string.player_err_unknown));
                            }
                            break;
                        case IMediaPlayer.MEDIA_ERROR_VEQ_MPG4_UNSUPPORTED:
                            msgBuilder.append(r.getText(R.string.player_err_qpel_and_gmc)).append(" ")
                            .append(r.getText(R.string.player_err_isnotsupported));
                            break;
                        case IMediaPlayer.MEDIA_ERROR_VEQ_INTERLACED_NOT_SUPPORTED:
                            msgBuilder.append(r.getText(R.string.player_err_interlaced)).append(" ")
                            .append(r.getText(R.string.player_err_isnotsupported));
                            break;
                        case IMediaPlayer.MEDIA_ERROR_VEQ_PROFILE_AND_LEVEL_UNSUPPORTED:
                            if (video != null) {
                                msgBuilder.append(r.getText(R.string.player_err_codec))
                                .append(" \"").append(video.format).append("\" ")
                                .append(r.getText(R.string.player_err_profile_and_level)).append(" ");

                                String profileName = VideoMetadata.getH264ProfileName(video.profile);
                                if (profileName != null) {
                                    msgBuilder.append("(")
                                    .append(video.profile);
                                    if (video.level != 0) {
                                        msgBuilder.append(" ").append(video.level / (double) 10);
                                    }
                                    msgBuilder.append(") ");
                                }
                                msgBuilder.append(r.getText(R.string.player_err_isnotsupported));
                            } else {
                                msgBuilder.append(r.getText(R.string.player_err_unknown));
                            }
                            break;
                        case IMediaPlayer.MEDIA_ERROR_VEQ_AUDIO_PROFILE_AND_LEVEL_UNSUPPORTED:
                            if (audio != null) {
                                msgBuilder.append(r.getText(R.string.player_err_codec))
                                .append(" \"").append(audio.format).append("\" ")
                                .append(r.getText(R.string.player_err_profile_and_level)).append(" ")
                                .append(r.getText(R.string.player_err_isnotsupported));
                            } else {
                                msgBuilder.append(r.getText(R.string.player_err_unknown));
                            }
                            break;
                        default:
                            msgBuilder.append(r.getText(R.string.player_err_unknown));
                            break;
                    }
                    break;
            }
        }
        return msgBuilder.toString();
    }

    /* SubtitleDelayPickerDialog.OnDelayChangeListener */
    public void onDelayChange(SubtitleDelayPickerAbstract view, int delay, int ratio) {
        boolean delayChanged = delay != mVideoInfo.subtitleDelay;
        boolean ratioChanged = ratio != mVideoInfo.subtitleRatio;
        mVideoInfo.subtitleDelay = delay;
        mVideoInfo.subtitleRatio = ratio;
        if (delayChanged || ratioChanged) {
            mSubtitleManager.clear();
            mPlayer.setSubtitleDelay(mVideoInfo.subtitleDelay);
            mPlayer.setSubtitleRatio(mVideoInfo.subtitleRatio);
        }
    }

    /* AudioDelayPickerDialog.OnAudioDelayChangeListener */
    public void onAudioDelayChange(AudioDelayPickerAbstract delayPicker, int delay) {
       PlayerService.sPlayerService.setAudioDelay(delay,false);
    }



    private void sendVideoStateChanged() {

        // mThumbnailDone is the state of the thread: 0: not started yet, 1: started, 2: done
        if (mThumbnailDone == 0) {
            Log.d("XXT", "starting new Thread");
            mThumbnailDone = 1;
            final ContentResolver cr = getContentResolver();
            final String posterPath = mPosterPath;
            final long videoId = mVideoId;
            final Uri videoUri = mUri;
            new Thread() {
                @Override
                public void run() {
                    Bitmap result = null;
                    boolean foundPoster = false;
                    if (posterPath != null) {
                        Bitmap bm = BitmapFactory.decodeFile(posterPath);
                        if (bm != null) {
                            float scaleFactor = (float)100 / (float)bm.getWidth();
                            result = Bitmap.createScaledBitmap(bm, (int)(scaleFactor * (float)bm.getWidth()), (int)(scaleFactor * (float)bm.getHeight()), true);
                            // Free the original bitmap ASAP if we made a copy (Caution!!! createScaledBitmap() doesn't always make a copy)
                            if (result != bm) {
                                bm.recycle();
                            }
                            foundPoster = true;
                        }
                    }
                    if (!foundPoster && videoId >= 0) {
                        Bitmap bm = VideoStore.Video.Thumbnails.getThumbnail(cr, videoId, VideoStore.Video.Thumbnails.MINI_KIND, null);
                        if (bm != null) {
                            float scaleFactor = (float)160 / (float)bm.getWidth();
                            result = Bitmap.createScaledBitmap(bm, (int)(scaleFactor * (float)bm.getWidth()), (int)(scaleFactor * (float)bm.getHeight()), true);
                            // Free the original bitmap ASAP if we made a copy (Caution!!! createScaledBitmap() doesn't always make a copy)
                            if (result != bm) {
                                bm.recycle();
                            }
                        }
                    }
                    final Bitmap thumb = result;
                    final boolean isPoster = foundPoster;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // check that it's still the same video
                            if (videoUri == mUri) {
                                mThumbnailDone = 2;
                                mThumbnail = thumb;
                                mPoster = isPoster;
                                sendVideoStateChanged();
                            }
                        }
                    });
                }
            }.start();
        }

        if (mThumbnailDone != 2) {
            return;
        }

        if (mPlayer != null) {

            Intent intent = new Intent(STARTED_VIDEO_INTENT);
            intent.putExtra("title", mMovieOrShowName != null ? mMovieOrShowName : mTitle);
            if (mEpisode != null) {
                intent.putExtra("episode", mEpisode);
            }
            intent.putExtra("duration", mPlayer.getDuration());
            intent.putExtra("playing", mPlayer.isPlaying());
            if (mThumbnail != null) {
                intent.putExtra("poster", mPoster);
                intent.putExtra("thumbnail", mThumbnail);
            }
            sendBroadcast(intent);
        }
    }

    /* PlayerController.Settings */
    public void switchSubtitleTrack() {
        if (mSubtitleInfoController.getTrackCount() > 1) {
            int newSubtitleTrack = (mVideoInfo.subtitleTrack + 1) % mSubtitleInfoController.getTrackCount();
            if (mPlayer.setSubtitleTrack(newSubtitleTrack)) {
                mVideoInfo.subtitleTrack = newSubtitleTrack;
                mSubtitleManager.clear();
                mSubtitleInfoController.setTrack(mVideoInfo.subtitleTrack);

                mPlayerController.updateToast(getResources().getText(R.string.player_subtitle_track_toast) + " " +
                        mSubtitleInfoController.getTrackNameAt(mVideoInfo.subtitleTrack));
            }
        }
        Log.d(TAG, "switchSubtitleTrack: " + mVideoInfo.subtitleTrack);
    }

    public void switchAudioTrack() {
        if (mAudioInfoController.getTrackCount() > 1) {
            int newAudioTrack = (mVideoInfo.audioTrack + 1) % mAudioInfoController.getTrackCount();
            if (setPlayerAudioTrack(newAudioTrack)) {
                mVideoInfo.audioTrack = newAudioTrack;
                mAudioInfoController.setTrack(mVideoInfo.audioTrack);
                mPlayerController.updateToast(getResources().getText(R.string.player_audio_track_toast) + " " +
                        mAudioInfoController.getTrackNameAt(mVideoInfo.audioTrack));
            }
        }
    }

    public void setSubtitleDelay(int delay) {
        if (mSubtitleInfoController.getTrackCount() > 1) {
            mVideoInfo.subtitleDelay += delay;
            mPlayer.setSubtitleDelay(mVideoInfo.subtitleDelay);
            mPlayerController.updateToast("Subtitle delay: " + mVideoInfo.subtitleDelay + "ms");
        }
    }

    /* TrackInfoAdapter.OnTrackInfoListener */
    public boolean onTrackSelected(TrackInfoController trackInfoController, int position, CharSequence name,
            CharSequence summary) {
        boolean ret = false;
        if (mPlayer.isBusy())
            return false;
        Log.d(TAG, "onTrackSelected(" + position + "): " + name);
        if (trackInfoController == mAudioInfoController) {
            AudioTrack at = mPlayer.getVideoMetadata().getAudioTrack(position);
            if (at.supported) {
                ret = setPlayerAudioTrack(position);
                if (ret)
                    mVideoInfo.audioTrack = position;
            } else if (!at.supported){
                mErrorMsg = at.format;
                showDialog(DIALOG_CODEC_NOT_SUPPORTED);
            }
        } else if (trackInfoController == mSubtitleInfoController) {
            if (position != mVideoInfo.subtitleTrack) {
                ret = mPlayer.setSubtitleTrack(position);
                if (ret) {
                    mSubtitleManager.clear();
                    mVideoInfo.subtitleTrack = position;
                }
                if (mVideoInfo.subtitleTrack >= 0) {
                    String trackName = mSubtitleInfoController.getTrackNameAt(mVideoInfo.subtitleTrack).toString();
                    mSubtitleInfoController.enableSettings(SUBTITLE_MENU_DELAY, !trackName.equals(getText(R.string.s_none)), true);
                }
            }
        }
        return ret;
    }

    public boolean onSettingsSelected(TrackInfoController trackInfoController, int key, CharSequence name) {
        Log.d(TAG, "onSettingsSelected: " + key);
        if (mPlayer.isBusy())
            return false;
        if (trackInfoController == mSubtitleInfoController) {
            switch (key) {
                case SUBTITLE_MENU_DELAY:
                    showDialog(DIALOG_SUBTITLE_DELAY);
                    break;
                case SUBTITLE_MENU_SETTINGS:
                    showDialog(DIALOG_SUBTITLE_SETTINGS);
                    break;
                case SUBTITLE_MENU_DOWNLOAD:
                    downloadSubtitles();
                    break;
            }
        }
        return true;
    }

    private void downloadSubtitles() {
        Intent subIntent = new Intent(Intent.ACTION_MAIN);
        subIntent.setClass(mContext, SubtitlesDownloaderActivity.class);
        subIntent.putExtra(SubtitlesDownloaderActivity.FILE_URL, PlayerService.sPlayerService.getStreamingUri().toString());
        startActivityForResult(subIntent, SUBTITLE_REQUEST);
    }

    private void chooseSubtitles() {
        Intent subIntent = new Intent(Intent.ACTION_MAIN);
        Uri uri = VideoUtils.getFileUriFromMediaLibPath(mUri.toString());

        subIntent.setClass(mContext, SubtitlesWizardActivity.class);
        subIntent.setData(uri);
        startActivityForResult(subIntent, SUBTITLE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == SUBTITLE_REQUEST){
            Log.d(TAG, "Get result from SubtitlesDownloaderActivity/SubtitlesWizardActivity");
            mPlayer.checkSubtitles();
        }
    }
    protected boolean forceExitOnTouch() {
        return mForceExitOnTouch;
    }

    PlayerController.OnShowHideListener mOnShowHideListener = new PlayerController.OnShowHideListener() {
        public void onShow() {
        }

        public void onHide() {
        }

        public void onBottomBarHeightChange(int height) {
            mSubtitleManager.setBottomBarHeight(height);
        };
    };

    private boolean setPlayerAudioTrack(int audioTrack) {
        if (mPlayer.getType() == IMediaPlayer.TYPE_ANDROID) {
            /*
             * On android, AudioTrack can only be changed on Prepared State
             */
            mForceAudioTrack = mVideoInfo.audioTrack = audioTrack;
            stop();
            start();
            return true;
        } else {
            return mPlayer.setAudioTrack(audioTrack);
        }
    }

    private PlayerListener mPlayerListener = new PlayerListener();

    /*
        TODO : not implement Player.Listener anymore

     */
    public class PlayerListener implements PlayerService.PlayerFrontend {

        public void onPrepared() {
            if (DBG) Log.d(TAG, "onPrepared");
            mNetworkFailed = false;

            postVideoInfoAndPrepared();
        }

        public void onCompletion() {
            if (DBG) Log.d(TAG,  "onCompletion");

            PlayerService.sPlayerService.setAudioFilt();
        }

        public boolean onError(int errorCode, int errorQualCode, String msg) {
            if (isFinishing())
                return true;
            Log.d(TAG, "onError: " + errorCode + ", " + errorQualCode);

            if (errorCode == IMediaPlayer.MEDIA_ERROR_VE_FILE_ERROR
                    && !mPlayer.isLocalVideo()
                    && !mNetworkFailed) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
                if (ni != null && ni.getState() == NetworkInfo.State.CONNECTED && mVideoInfo != null) {
                    /* If we get a corrupted file error, if the file is from the network,
                     * and if we are still connected, try to reopen the video one time.
                     */
                    mVideoInfo.resume = mLastPosition = getBookmarkPosition();
                    mVideoInfo.duration = mPlayer.getDuration();
                    if (mLastPosition != 0) {
                        mNetworkFailed = true;
                        stop();
                        mResumeFromLast = true;
                        start();
                        return true;
                    }
                }
            }
            mLastPosition = LAST_POSITION_UNKNOWN;
            stop();
            mErrorCode = errorCode;
            mErrorQualCode = errorQualCode;
            mErrorMsg = msg;

            if (mShowingDialogId != DIALOG_NO) {
                removeDialog(mShowingDialogId);
                mShowingDialogId = DIALOG_NO;
            }

            if (mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED) {
                VideoTrack vt = mPlayer.getVideoMetadata().getVideoTrack();
                mErrorMsg = vt != null ? vt.format : "unknown";
                showDialog(DIALOG_CODEC_NOT_SUPPORTED);
            } else {
                showDialog(DIALOG_ERROR);
            }

            return true;
        }

        public void onSeekStart(int pos) {
            if (mSubtitleManager != null)
                mSubtitleManager.onSeekStart(pos);
        }

        public void onSeekComplete() {
            if (mPlayerController != null)
                mPlayerController.onSeekComplete();
        }

        public void onAllSeekComplete() {
            if (mPlayerController != null)
                mPlayerController.onAllSeekComplete();
        }

        public void onPlay() {
            if (mSubtitleManager != null)
                mSubtitleManager.onPlay();
            sendVideoStateChanged();
            //mPlayerController.hide();

        }
        public void onFirstPlay() {
            mPlayerController.hide();
        }
        public void onPause() {

            if (mSubtitleManager != null)
                mSubtitleManager.onPause();
            sendVideoStateChanged();
        }

        public void onOSDUpdate() {
            if (mPlayerController != null)
                mPlayerController.setEnabled(true);
        }

        public void onVideoMetadataUpdated(VideoMetadata vMetadata) {
            if (isStereoEffectOn()) {
                int mode = vMetadata.getVideoTrack().s3dMode;
                if (mode != 0) setEffect (mode);
            }
        }

        public void onAudioMetadataUpdated(VideoMetadata vMetadata, int newAudioTrack) {
            if (mVideoInfo == null) {
                mNewAudioTrack = newAudioTrack;
                mAudioSubtitleNeedUpdate = true;
                return;
            }

            boolean firstTimeUpdated = mAudioInfoController.getTrackCount() == 0;
            int nbTrack = vMetadata.getAudioTrackNb();

            Log.d(TAG, "onAudioMetadataUpdated: newAudio: " + newAudioTrack
                    + "  mVideoInfo.audioTrack: " + mVideoInfo.audioTrack
                    + "  firstTimeUpdated: " + firstTimeUpdated
                    + "  nbTrack: " + nbTrack);

            mAudioInfoController.clear();
            for (int i = 0; i < nbTrack; ++i) {
            	VideoMetadata.AudioTrack audio = vMetadata.getAudioTrack(i);
            	CharSequence name = VideoUtils.getLanguageString(PlayerActivity.this, audio.name);
            	CharSequence summary = VideoUtils.getLanguageString(PlayerActivity.this, audio.format);
            	mAudioInfoController.addTrack(name, summary);
            }

            mAudioInfoController.setTrack(mVideoInfo.audioTrack);

        }

        public void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int newSubtitleTrack) {
            if (mVideoInfo == null) {
                mNewSubtitleTrack = newSubtitleTrack;
                mAudioSubtitleNeedUpdate = true;
                return;
            }
            int nbTrack = vMetadata.getSubtitleTrackNb();

            final boolean firstTimeCalled = mSubtitleInfoController.getTrackCount() == 0;

            Log.d(TAG, "onSubtitleMetadataUpdated: newSubtitle: " + newSubtitleTrack + ", mVideoInfo.subtitleTrack: " + mVideoInfo.subtitleTrack + ", firstTimeCalled: " + firstTimeCalled);

            mSubtitleInfoController.clear();
            int noneTrack = nbTrack+1;
            if (nbTrack != 0) {
                mVideoInfo.nbSubtitles = nbTrack;

                for (int i = 0; i < nbTrack; ++i) {
                    mSubtitleInfoController.addTrack(VideoUtils.getLanguageString(PlayerActivity.this, vMetadata.getSubtitleTrack(i).name));
                }

                // none track

                mSubtitleInfoController.addTrack(getText(R.string.s_none));
                nbTrack++;
                mSubtitleInfoController.addSeparator();

                mSubtitleInfoController.addSettings(getText(R.string.player_pref_subtitle_delay_title), R.drawable.ic_menu_delay, SUBTITLE_MENU_DELAY);
                mSubtitleInfoController.addSettings(getText(R.string.menu_player_settings), R.drawable.ic_menu_settings, SUBTITLE_MENU_SETTINGS);
            }
            mSubtitleInfoController.addSettings(getText(R.string.get_subtitles_online), R.drawable.ic_menu_subtitles, SUBTITLE_MENU_DOWNLOAD);
            if (nbTrack != 0) {
                mSubtitleManager.start();

                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(PlayerActivity.this);
                int size = preferences.getInt(KEY_SUBTITLE_SIZE, mSubtitleSizeDefault);
                int vpos = preferences.getInt(KEY_SUBTITLE_VPOS, mSubtitleVPosDefault);
                int color = preferences.getInt(KEY_SUBTITLE_COLOR, mSubtitleColorDefault);
                boolean outline = preferences.getBoolean(KEY_SUBTITLE_OUTLINE, mSubtitleOutlineDefault);
                mSubtitleManager.setSize(size);
                mSubtitleManager.setColor(color);
                mSubtitleManager.setVerticalPosition(vpos);
                mSubtitleManager.setOutlineState(outline);

                // If no language set for subs, set the user favorite. Or system language if none.
                if (!mHideSubtitles && mVideoInfo.subtitleTrack == -1) {
                    Locale locale = new Locale(mSubsFavoriteLanguage);
                    for (int i = 0; i < nbTrack; ++i) {
                        if (mSubtitleInfoController.getTrackNameAt(i).toString().equalsIgnoreCase(locale.getDisplayLanguage())){
                            mVideoInfo.subtitleTrack = i;
                            break;
                        }
                    }
                }

                if (mVideoInfo.subtitleTrack >= 0 && mVideoInfo.subtitleTrack < nbTrack) {
                    //mVideoInfo.subtitleTrack has been changed by playerservice
                    mSubtitleInfoController.setTrack(mVideoInfo.subtitleTrack);
                }

                if (mSubtitleInfoController.getTrack() == noneTrack) {
                    mSubtitleInfoController.enableSettings(SUBTITLE_MENU_DELAY, false, false);
                }
                refreshSubtitleTVMenu();
            }
        }

        public void onBufferingUpdate(int percent) {
            if (!mPlayer.isInPlaybackState()) {
                mBufferView.setText(" "+percent+"%");
            }
        }

        public void onSubtitle(Subtitle subtitle) {
            if (mSubtitleManager != null)
                mSubtitleManager.addSubtitle(subtitle);
        }

        @Override
        public void onAudioError(boolean isNotSupported,String msg) {
            mErrorMsg = msg;
            if(isNotSupported)
                showDialog(DIALOG_CODEC_NOT_SUPPORTED);
        }

        @Override
        public void onVideoDb(final VideoDbInfo localVideoInfo, final VideoDbInfo remoteVideoInfo) {
            Log.d(TAG, "onVideoDb: videoInfo: " + localVideoInfo);
            Log.d(TAG, "onVideoDb: trakt: " + localVideoInfo.traktResume+ " local "+ localVideoInfo.resume);
            if (localVideoInfo != null) {
                final int localTraktPosition = Math.abs(localVideoInfo.duration>0 ? (int)(localVideoInfo.traktResume * (double) localVideoInfo.duration / 100) : 0);
                Log.d(TAG, "onVideoDb: trakt calc: "+ localTraktPosition+ " local "+ localVideoInfo.resume);

                if (localVideoInfo != null && remoteVideoInfo != null && mResume != RESUME_NO&& mResume !=  RESUME_FROM_LOCAL_POS) {
                    Log.d(TAG, "hasRemoteVideoInfo");
                    int localLastPosition = getLastPosition(localVideoInfo, mResume);
                    int remoteLastPosition = getLastPosition(remoteVideoInfo, mResume);

                    if (localLastPosition != remoteLastPosition && remoteLastPosition > 0) {
                        //do not display dialog if remote position is the only available
                        if (localLastPosition <= 0) {
                            Log.d(TAG, "use remoteVideoInfo");
                            showTraktResumeDialog(localTraktPosition,remoteVideoInfo);

                        } else {
                            if(mResume ==  RESUME_FROM_REMOTE_POS){ //use only remote
                                mVideoInfo = remoteVideoInfo;
                                if(PlayerService.sPlayerService!=null){
                                    PlayerService.sPlayerService.setVideoInfo(mVideoInfo);
                                    PlayerService.sPlayerService.requestIndexAndScrap();
                                }
                                setVideoInfo(mVideoInfo);
                            }
                            else {
                                AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
                                builder.setMessage(R.string.use_remote_resume)
                                        .setCancelable(false)
                                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                mVideoInfo = remoteVideoInfo;
                                                if(PlayerService.sPlayerService!=null){
                                                    PlayerService.sPlayerService.setVideoInfo(mVideoInfo);
                                                    PlayerService.sPlayerService.requestIndexAndScrap();
                                                }
                                                setVideoInfo(mVideoInfo);
                                            }
                                        })
                                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                showTraktResumeDialog(localTraktPosition, localVideoInfo);
                                            }
                                        });
                                AlertDialog alert = builder.create();
                                alert.show();
                            }
                        }
                        return;
                    }
                }
                //	showTraktResumeDialog(localTraktPosition,localVideoInfo);
                //return ;
            }

            mVideoInfo = localVideoInfo;
            if(PlayerService.sPlayerService!=null){
                PlayerService.sPlayerService.setVideoInfo(mVideoInfo);
                PlayerService.sPlayerService.requestIndexAndScrap();
            }
            setVideoInfo(mVideoInfo);
        }

        @Override
        public void setUri(Uri uri, Uri streamingUri){
            mUri = uri;
            mStreamingUri = streamingUri;
            final String scheme = mUri.getScheme();
            if (getIntent().getStringExtra("title") != null)
                mTitle = getIntent().getStringExtra("title");
            else if (scheme == null || !scheme.equals("content"))
                mTitle = mUri.getLastPathSegment();
            invalidateOptionsMenu();
        }

        @Override
        public void setVideoInfo(VideoDbInfo mVideoInfo) {
            if (DBG) Log.d(TAG, "setVideoInfo " + String.valueOf(mVideoInfo != null));
            PlayerActivity.this.setVideoInfo(mVideoInfo);
        }

        @Override
        public void onEnd() {
            finish();
        }

        @Override
        public void onTorrentUpdate(String daemonString) {

            mHandler.sendMessage(mHandler.obtainMessage(MSG_TORRENT_UPDATE , daemonString));

        }

        @Override
        public void onTorrentNotEnoughSpace() {
            mHandler.sendEmptyMessage(MSG_TORRENT_NOT_ENOUGH_SPACE);
        }

        @Override
        public void onFrontendDetached() {
            if(mLaunchFloatingPlayer)
                finish();
        }

    };



    private SurfaceController.Listener mSurfaceListener = new SurfaceController.Listener() {
        public void onSwitchVideoFormat(int fmt, int autoFmt) {
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putString(KEY_PLAYER_FORMAT, String.valueOf(fmt));
            editor.putString(KEY_PLAYER_AUTO_FORMAT, String.valueOf(autoFmt));
            editor.apply(); // commit is blocking .. avoid
        }
    };


    public void switchMode(boolean tv) {
        // TODO Auto-generated method stub
        if (tv != isTVMode) {
            isTVMode = tv;
            invalidateOptionsMenu();
        }
    }

}
