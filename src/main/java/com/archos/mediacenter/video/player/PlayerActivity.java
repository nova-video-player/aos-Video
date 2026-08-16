package com.archos.mediacenter.video.player;

import android.app.Activity;
import android.app.Dialog;
import android.app.PictureInPictureParams;
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
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.loader.app.LoaderManager;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosIntents;
import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.introdb.IntroSegments;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.PermissionChecker;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.settings.VideoSettingsActivity;
import com.archos.mediacenter.video.leanback.wizard.SubtitlesWizardActivity;
import com.archos.mediacenter.video.player.TrackInfoController.TrackInfoListener;
import com.archos.mediacenter.video.player.tvmenu.AudioDelayTVPicker;
import com.archos.mediacenter.video.player.tvmenu.AudioSpeedTVPicker;
import com.archos.mediacenter.video.player.tvmenu.SubtitleDelayTVPicker;
import com.archos.mediacenter.video.player.tvmenu.TVCardDialog;
import com.archos.mediacenter.video.player.tvmenu.TVCardView;
import com.archos.mediacenter.video.player.tvmenu.TVMenu;
import com.archos.mediacenter.video.player.tvmenu.TVMenuAdapter;
import com.archos.mediacenter.video.player.tvmenu.TVMenuItem;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;
import com.archos.mediacenter.video.player.tvmenu.TimerDelayTVPicker;
import com.archos.mediacenter.video.utils.AdditionalServiceSingleton;
import com.archos.mediacenter.video.utils.CodecDiscovery;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity2;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoMetadata.AudioTrack;
import com.archos.mediacenter.video.utils.VideoMetadata.SubtitleTrack;
import com.archos.mediacenter.video.utils.VideoMetadata.VideoTrack;
import com.archos.mediacenter.video.utils.VideoPreferencesActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.LibAvos;
import com.archos.medialib.Subtitle;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.ScrapeDetailResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.archos.environment.ArchosFeatures.isChromeOS;
import static com.archos.filecorelibrary.FileUtils.hasManageExternalStoragePermission;
import static com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.generateTrackName;
import static com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager.getSubLanguageFromSubPathAndVideoPath;
import static com.archos.mediacenter.video.utils.MiscUtils.isEmulator;

import com.archos.mediacenter.utils.ISO639codes;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.DEFAULT_MAX_IFRAME_SIZE;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.DEFAULT_STREAM_BUFFER_SIZE;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_PARSER_SYNC_MODE;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_PLAYBACK_SPEED;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_STREAM_BUFFER_SIZE;
import static com.archos.mediacenter.video.utils.VideoPreferencesCommon.KEY_STREAM_MAX_IFRAME_SIZE;

public class PlayerActivity extends AppCompatActivity implements PlayerController.Settings,
        SubtitleDelayPickerDialog.OnDelayChangeListener, AudioDelayPickerDialog.OnAudioDelayChangeListener,
        AudioSpeedPickerDialog.OnAudioSpeedChangeListener,
        DialogInterface.OnDismissListener, TrackInfoListener,
        IndexHelper.Listener, PermissionChecker.PermissionListener, MiscUtils.CutoutMetricsSetter {

    private static final Logger log = LoggerFactory.getLogger(PlayerActivity.class);

    public static final int RESUME_NO = 0;
    public static final int RESUME_FROM_LAST_POS = 1;
    public static final int RESUME_FROM_BOOKMARK = 2;
    public static final int RESUME_FROM_REMOTE_POS = 3;
    public static final int RESUME_FROM_LOCAL_POS = 4;
    public static final String RESUME = "resume";
    // Kept as API aliases for browser/database code; runtime position is owned by PlayerService.
    public static final int LAST_POSITION_UNKNOWN = PlayerService.LAST_POSITION_UNKNOWN;
    public static final int LAST_POSITION_END = PlayerService.LAST_POSITION_END;

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
    private static final int DIALOG_AUDIO_SPEED = 10;

    // accessed from SubtitleSettingsDialog
    public static final String KEY_SUBTITLE_BACKGROUND = "subtitle_background";
    public static final String KEY_SUBTITLE_BG_OPACITY = "subtitle_bg_opacity";
    /* package */ public static final String KEY_SUBTITLE_SIZE = "pref_play_subtitle_size_key";
    /* package */ public static final String KEY_SUBTITLE_VPOS = "pref_play_subtitle_vpos_key";
    public static final String KEY_SUBTITLE_OUTLINE = "pref_play_subtitle_outline_key";
    public static final String KEY_SUBTITLE_COLOR = "pref_play_subtitle_color_key";
    private static final String KEY_PLAYER_FORMAT = "player_pref_format_key";
    private static final String KEY_PLAYER_AUTO_FORMAT = "player_pref_auto_format_key";
    private static final String KEY_PLAYER_PROJECTOR_MODE = "player_projector_mode_key";
    private static final String KEY_AUDIO_FILT = "pref_audio_filt_int_key"; // used to be "pref_audio_filt_key", containing a string
    private static final String KEY_AUDIO_FILT_NIGHT = "pref_audio_filt_night_int_key";
    private static final String KEY_SPATIALIZATION_ENABLED = "player_spatialization_enabled";
    private static final String KEY_NOTIFICATIONS_MODE = "notifications_mode";
    private static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";
    private static final String KEY_LOCK_ROTATION = "pref_lock_rotation";
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
    private static final int MENU_AUDIO_SPEED_ID = 307;
    private static final int MENU_SPATIALIZATION_ID = 308;

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
    private static final String[] GENERIC_TEXT_SUBTITLE_FORMATS = {"srt", "vtt"};

    private final ActivityResultLauncher<Intent> subtitleLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> { if (result.getResultCode() == Activity.RESULT_OK) onSubtitleResult(); });

    private boolean mHasAskedFloatingPermission;
    private boolean mIsInfoActivityDisplayed;
    private boolean mLaunchFloatingPlayer;
    private boolean mIsReadytoStart;
    private PermissionChecker mPermissionChecker;
    private static int mScreenWidth, mScreenHeight;
    private static int mCurrentRotation;
    // screen cutouts
    public static int mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom;
    private static boolean mFullScreenWithCutout = true;

    private NetworkState networkState = null;
    private PropertyChangeListener propertyChangeListener = null;
    private DisplayManager mDisplayManager = null;
    private DisplayManager.DisplayListener mDisplayListener = null;

    @Override
    public void setCutoutMetrics(int left, int top, int right, int bottom) {
        mCutoutLeft = left;
        mCutoutTop = top;
        mCutoutRight = right;
        mCutoutBottom = bottom;
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
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
                    myShowDialog(DIALOG_ERROR);
                    break;
                case MSG_TORRENT_NOT_ENOUGH_SPACE:
                    myShowDialog(DIALOG_NOT_ENOUGHT_SPACE);
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
						Log.w("AVP", "Display update", e);
                    } catch(java.lang.ArrayIndexOutOfBoundsException e) {
                        Log.w("AVP", "Display update, out of bound", e);
                    }
                    break;
                case MSG_SLEEP:
                    finishWithResult();
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

    private boolean             fileHasAlreadyPlayed = false;
    private int                 mResume;
    private long                mVideoId;
    private int                 mErrorCode = 0;
    private int                 mErrorQualCode = 0;
    private String              mErrorMsg = null;
    private int                 mShowingDialogId;
    private Dialog              mDialog; // assume there is only one dialog shown
    private boolean             mNetworkFailed = false;
    private boolean             mPaused;
    private boolean             mUserPausedVideo = false;  // Track if user explicitly paused
    private Resources           mResources;
    private SharedPreferences   mPreferences;
    private TrackInfoController mAudioInfoController;
    private TrackInfoController mSubtitleInfoController;

    // State maintained for proper onPause/OnResume behaviour.
    private boolean mResumeFromLast;
    private boolean mNetworkBookmarksEnabled;

    // External player result reporting
    private boolean mIsExternalPlayer = false;
    private boolean mVideoFinished = false;
    private boolean mResultSent = false;
    private String mCallingPackage = null;
    private int mForceAudioTrack = -1;
    private static boolean mLockRotation;
    private static boolean mIsRotationLocked;
    private static int mLockedRotation;
    private boolean mForceSWDecoding;
    private boolean mStopped;
    private boolean mHdmiPlugged = false;
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

    private boolean mNetworkStateListenerAdded = false;
    private boolean mCling = false;

    private TVMenu mSubtitleTVMenu;
    private TVMenuItem mSubtitleSettingsMenuItem;
    private TVMenuItem mSubtitleDelayMenuItem;
    private TVCardView mSubtitleTVCardView;
    private TVCardView mAudioTracksTVCardView;
    private TVMenu mAudioTracksTVMenu;
    private TVMenu mPlayModeTVMenu;
    private TVMenuItem mIntroSummaryMenuItem;
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
            if (log.isDebugEnabled()) log.debug("Service connected");
            if(mIsReadytoStart)
                postOnPlayerServiceBind();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    public static Boolean isRotationLocked() {
        return mIsRotationLocked;
    }
    public static int getLockedRotation() {
        return mLockedRotation;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (log.isDebugEnabled()) log.debug("onReceive: {}", intent);
            String action = intent.getAction();
            if (isFinishing())
                return;
            if (action.equals(Intent.ACTION_SHUTDOWN)) {
                finishWithResult();
            } else if (action.equals(ACTION_HDMI_PLUGGED)) {
                if (log.isDebugEnabled()) log.debug("intent received hdmi");
                boolean plugged = intent.getBooleanExtra(EXTRA_HDMI_PLUGGED_STATE, false);
                int w = 0, h = 0;
                mHdmiPlugged = plugged;
                if (log.isDebugEnabled()) log.debug("intent received hdmi plugged={}", plugged);
                if (plugged) {
                    // update HDMI screen size
                    int[] size = readHdmiSize(mContext);
                    if (size == null) {
                        // Some USB-C mirror outputs are not reported as FLAG_PRESENTATION displays.
                        size = readFallbackDisplaySize();
                        log.warn("HDMI plugged but no presentation display found, using fallback size=({},{})", size[0], size[1]);
                    }
                    w = size[0];
                    h = size[1];
                }
                if (mSurfaceController != null) {
                    mSurfaceController.setHdmiPlugged(plugged, w, h);
                }
                invalidateOptionsMenu();
                if (isTVMode) {
                    refreshAudioTracksTVMenu();
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

    private boolean isSpatializationSupportedByPlatform() {
        int capabilities = CustomApplication.getSpatializerCapabilities();
        return Build.VERSION.SDK_INT >= 32
                && (capabilities & CodecDiscovery.SPATIALIZER_CAP_SUPPORTED) != 0
                && (capabilities & CodecDiscovery.SPATIALIZER_CAP_AVAILABLE) != 0
                && (capabilities & (CodecDiscovery.SPATIALIZER_CAP_CAN_SPATIALIZE_5_1
                | CodecDiscovery.SPATIALIZER_CAP_CAN_SPATIALIZE_7_1)) != 0;
    }

    private boolean isSpatializationToggleAvailable() {
        return isSpatializationSupportedByPlatform()
                && Integer.parseInt(mPreferences.getString("force_audio_passthrough_multiple", "0")) == 0;
    }

    private boolean isSpatializationPreferenceEnabled() {
        return mPreferences.getBoolean(KEY_SPATIALIZATION_ENABLED, true);
    }

    private boolean isSpatializationEnabledForPlayback() {
        return isSpatializationToggleAvailable() && isSpatializationPreferenceEnabled();
    }

    private void applySpatializationPreferenceToAvos() {
        if (LibAvos.isAvailable()) {
            LibAvos.setSpatializerEnabled(isSpatializationEnabledForPlayback());
        }
    }

    private void applyDownmixPreferenceToAvos() {
        if (!LibAvos.isAvailable()) {
            return;
        }
        int passthroughMode = Integer.parseInt(mPreferences.getString("force_audio_passthrough_multiple", "0"));
        if (passthroughMode > 0) {
            LibAvos.setDownmix(0);
            return;
        }
        if (isSpatializationEnabledForPlayback()) {
            LibAvos.setDownmix(0);
            return;
        }
        if (ArchosFeatures.isAndroidTV(this)) {
            LibAvos.setDownmix(mPreferences.getBoolean("enable_downmix_androidtv", false) ? 1 : 0);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && mPreferences.getBoolean("disable_downmix", false)) {
            LibAvos.setDownmix(0);
        } else {
            LibAvos.setDownmix(1);
        }
    }

    private boolean isPassthroughAudioDelayLimited() {
        return CustomApplication.isPassthroughSupported()
                && Integer.parseInt(mPreferences.getString("force_audio_passthrough_multiple", "0")) > 0;
    }

    private int clampAudioDelayForPassthrough(int delay) {
        return isPassthroughAudioDelayLimited() && delay > 0 ? 0 : delay;
    }

    private void resetUnsupportedPassthroughAudioDelayPreset() {
        if (!isPassthroughAudioDelayLimited()) {
            return;
        }
        int delay = mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0);
        if (delay <= 0) {
            return;
        }
        if (log.isDebugEnabled()) log.debug("resetUnsupportedPassthroughAudioDelayPreset: reset unsupported delay {}", delay);
        mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), 0).apply();
        if (PlayerService.sPlayerService != null) {
            PlayerService.sPlayerService.setAudioDelay(0, false);
        }
    }

    private void setSpatializationPreferenceEnabled(boolean enabled) {
        mPreferences.edit().putBoolean(KEY_SPATIALIZATION_ENABLED, enabled).apply();
        applySpatializationPreferenceToAvos();
        applyDownmixPreferenceToAvos();
        if (mPlayer != null && mPlayer.isInPlaybackState()) {
            mPlayer.refreshAudioOutput();
        }
        invalidateOptionsMenu();
        if (isTVMode) {
            refreshAudioTracksTVMenu();
        }
    }

    private void toggleSpatializationPreference() {
        setSpatializationPreferenceEnabled(!isSpatializationPreferenceEnabled());
    }

    public static int[] readHdmiSize(Context context) {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        for (Display display : displays) {
            if ((display.getFlags() & Display.FLAG_PRESENTATION) != 0) {
                Display.Mode mode = display.getMode();
                int width = mode.getPhysicalWidth();
                int height = mode.getPhysicalHeight();
                if (log.isDebugEnabled()) log.debug("readHdmiSize: size=({},{})", width,height);
                int[] ret = new int[2];
                ret[0] = width;
                ret[1] = height;
                return ret;
            }
        }
        if (log.isDebugEnabled()) log.debug("readHdmiSize: no external HDMI display found.");
        return null;
    }

    @SuppressWarnings("deprecation") // FLAG_TRANSLUCENT_NAVIGATION: no edge-to-edge alternative in PlayerActivity
    private void addTranslucentNavigationFlag() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
    }

    @SuppressWarnings("deprecation") // getRealMetrics: API 30+ uses getCurrentWindowMetrics
    private int[] readFallbackDisplaySize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            return new int[] { bounds.width(), bounds.height() };
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            return new int[] { metrics.widthPixels, metrics.heightPixels };
        }
    }

    public void setUIExternalSurface(Surface uiSurface) {
        mSubtitleManager.setUIExternalSurface(uiSurface);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        if (log.isDebugEnabled()) log.debug("onCreate");

        AdditionalServiceSingleton.getInstance().bindToService(
                getApplicationContext(),
                () -> {
                    LibAvos.setAudioTransformer(new LibAvos.AudioTransformer() {
                        @Override
                        public float[] transformAudio(float[] samples) {
                            var svc = AdditionalServiceSingleton.getService();
                            if (svc == null) return samples;
                            try {
                                samples = svc.transformAudio(samples);
                            } catch(RemoteException e) {
                                log.error("transformAudio failed", e);
                            }
                            return samples;
                        }
                        });
                },
                () -> {
                    LibAvos.setAudioTransformer(null);
                }
        );
        mContext = this;

        // Detect if we're being used as an external player
        detectExternalPlayerMode();

        super.onCreate(icicle);

        // Predictive back (mandatory on targetSdk 36) no longer dispatches KEYCODE_BACK to
        // onKeyDown/onKeyUp, which is how the TV menu / TVCardDialog overlays used to intercept
        // BACK to close themselves instead of finishing the player. Close them explicitly first.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                log.info("Back navigation: OnBackPressedDispatcher callback, dialogId={}",
                        mShowingDialogId);
                if (mPlayerController != null && mPlayerController.handleBackPressed()) {
                    // The player controller dismisses a nested TV card before the main TV menu.
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        mIndexHelper = new IndexHelper(mContext, LoaderManager.getInstance(this), LOADER_INDEX);

        mPermissionChecker = new PermissionChecker(hasManageExternalStoragePermission(getApplicationContext()));
        mPermissionChecker.setListener(this);
        VideoEffect.resetForcedMode();
        VideoEffect.setStereoForced(MainActivity.mStereoForced);

        mResources = getResources();

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);

        WindowManager.LayoutParams attributes = getWindow().getAttributes();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        
        // cutout mode: display below cutout
        boolean cutBothSidesX = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mFullScreenWithCutout = mPreferences.getBoolean("enable_cutout_mode_short_edges", true);
            cutBothSidesX = mPreferences.getBoolean("enable_cutout_both_sidesx", false);
            if (log.isDebugEnabled()) log.debug("onCreate cutout: mFullScreenWithCutout={}, cutBothSidesX={}",  mFullScreenWithCutout, cutBothSidesX);
            //Always using the long one, does this matter!?
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        mPlayerController.pauseTimeout = (mPreferences.getBoolean("hide_controls_on_pause", false)) ? 5000 : 0;

        addTranslucentNavigationFlag();
        getWindow().setAttributes(attributes);
        /*
         * transparent background for archos devices
         * (hide black bars on TVOUT)
         */
        // needed on Bravia for HDR content to avoid grey bars cf. issue #270
        if (isEmulator() || isChromeOS(mContext)) // avoid emulator UI glitch
            getWindow().setBackgroundDrawable(new ColorDrawable(0xFF000000));
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setContentView(R.layout.player);
        mRootView = findViewById(R.id.root);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRootView.setOnApplyWindowInsetsListener( new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                    //NOTE do not do updateSizes() here otherwise player controller is not displayed
                    MiscUtils.setCutoutMetrics(insets, mRootView, PlayerActivity.this);
                    mSurfaceController.setCutoutMetrics(mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom);
                    if (log.isDebugEnabled()) log.debug("CONFIG onApplyWindowInsets: cutout=({},{},{},{})", mCutoutLeft, mCutoutTop, mCutoutRight, mCutoutBottom);
                    getWindow().getDecorView().setOnApplyWindowInsetsListener(null);
                    // needed on Bravia for HDR content to avoid grey bars cf. issue #270
                    // avoid emulator UI glitch
                    if (!(isEmulator() || isChromeOS(mContext)))
                        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    return view.onApplyWindowInsets(insets);
                }
            });
        }

        // needed otherwise the playerController does not appear
        mRootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, final int top, int right, final int bottom, int oldLeft, final int oldTop, int oldRight, final int oldBottom) {
                if (log.isDebugEnabled()) log.debug("CONFIG addOnLayoutChangeListener: left={}, top={}, right={}, bottom={}, oldLeft={}, oldTop={}, oldRight={}, oldBottom={}", left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom);
                if(oldBottom!=bottom||oldTop!=top) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (log.isDebugEnabled()) log.debug("CONFIG addOnLayoutChangeListener, do updateSizes()");
                            // without this video is stretched fullscreen
                            updateSizes();
                        }
                    });
                }
            }
        });

        // We use the ActionBar for the top-right menu only
        // our PlayerController puts the Title in there
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setBackgroundDrawable(null);

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
        mSurfaceController.mFullScreenWithCutout = mFullScreenWithCutout;
        mSurfaceController.mCutBothSidesX = cutBothSidesX;

        mSurfaceController.setListener(mSurfaceListener);

        View menuAnchor = mRootView.findViewById(R.id.menu_anchor);
        mProgressView = mRootView.findViewById(R.id.progress_indicator);
        mBufferView = (TextView) mRootView.findViewById(R.id.buffer_percentage);

        mPlayerController = new PlayerController(mContext, getWindow(), (ViewGroup)mRootView, mSurfaceController, this, actionBar);
        mPlayerController.setVideoTitleEnabled(true);

        mAudioInfoController = new TrackInfoController(mContext, getLayoutInflater(), menuAnchor, actionBar);
        mAudioInfoController.setListener(this);
        mSubtitleManager = new SubtitleManager(mContext, (ViewGroup)mRootView, getWindow().getWindowManager(),false);
        mSubtitleInfoController = new TrackInfoController(mContext, getLayoutInflater(), menuAnchor, actionBar);
        mSubtitleInfoController.setListener(this);
        mSubtitleInfoController.setAlwayDisplay(true);
        mResumeFromLast = false;

        mPlayer = new Player(mContext, getWindow(), mSurfaceController, false);

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N_MR1){ //detect any kind of rotation, even from 270 to 90°
            mDisplayListener = new DisplayManager.DisplayListener() {
                int orientation;
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                @SuppressWarnings("deprecation") // getDefaultDisplay: API 30+ uses context.getDisplay()
                public void onDisplayChanged(int displayId) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        orientation = mContext.getDisplay().getRotation();
                    } else {
                        orientation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                    }
                    if(mCurrentRotation != orientation) {
                        mCurrentRotation = orientation;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (log.isDebugEnabled()) log.debug("CONFIG onDisplayChanged do updateSizes() rotation={}({})->{}({})",
                                        getHumanReadableRotation(mCurrentRotation), mCurrentRotation,
                                        getHumanReadableRotation(orientation), orientation);
                                // needed to update dimensions when unchecking autorot
                                updateSizes();
                            }
                        });
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }
            };
            mDisplayManager = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);

            networkState = NetworkState.instance(getApplicationContext());
            if (propertyChangeListener == null)
                propertyChangeListener = new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        if (evt.getOldValue() != evt.getNewValue()) {
                            if (log.isDebugEnabled()) log.debug("NetworkState for {} changed:{} -> {}", evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
                            if (!networkState.hasLocalConnection() && !mPlayer.isLocalVideo()) { // should not finish if playing local file
                                if (log.isDebugEnabled()) log.debug("lost network: finish");
                                finishWithResult();
                            }
                        }
                    }
                };
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
        if (log.isDebugEnabled()) log.debug("setEffectForced: type {}, mode {}, needSave {}", type, mode, needSave);
        if (needSave) mSavedMode=mode;
        mPlayer.setEffect(type, mode);
        mPlayerController.setUIMode(mPlayer.getUIMode());
        if(mSubtitleManager!=null)
            mSubtitleManager.setUIMode(mPlayer.getUIMode());
        if(type!=VideoEffect.EFFECT_NONE){
            if (log.isDebugEnabled()) log.debug("setEffect: setLockRotation true");
            setLockRotation(true);
        }
        else{
            if (log.isDebugEnabled()) log.debug("setEffect: setLockRotation {}", mLockRotation);
            setLockRotation(mLockRotation);
        }
    }

    private void addNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(mContext);
        if (!mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (log.isDebugEnabled()) log.debug("addNetworkListener: networkState.addPropertyChangeListener");
            networkState.addPropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = true;
        }
    }

    private void removeNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(mContext);
        if (mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (log.isDebugEnabled()) log.debug("removeListener: networkState.removePropertyChangeListener");
            networkState.removePropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (log.isDebugEnabled()) log.debug("onStart()");
        mStopped = false;
        removeNetworkListener();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        intentFilter.addAction(PlayerService.PLAYER_SERVICE_STARTED);
        intentFilter.addAction(ACTION_HDMI_PLUGGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mReceiver, intentFilter);
        }
        isTVMode = TVUtils.isTV(mContext);
        mLockRotation = mPreferences.getBoolean(KEY_LOCK_ROTATION, false);
        mNetworkBookmarksEnabled = mPreferences.getBoolean(KEY_NETWORK_BOOKMARKS, true);
        mForceSWDecoding = mPreferences.getBoolean(KEY_FORCE_SW, false);
        if (log.isDebugEnabled()) log.debug("onStart: setLockRotation {}", mLockRotation);
        setLockRotation(mLockRotation);
        mSurfaceController.setVideoFormat(Integer.parseInt(mPreferences.getString(KEY_PLAYER_FORMAT, "-1")),
                Integer.parseInt(mPreferences.getString(KEY_PLAYER_AUTO_FORMAT, "-1")));
        
        //Set up projector mode if we need it, otherwise dont even call.
        if (mPreferences.getBoolean(KEY_PLAYER_PROJECTOR_MODE, false)) mSurfaceController.setProjectorMode(true);
        
        if (log.isDebugEnabled()) log.debug("onStart: Setting audio transformer");
        if (LibAvos.isAvailable()) {
            VideoPreferencesCommon.resetPassthroughPref(mPreferences); // note this resets the audio_speed if in passthrough to 1.0f in prefs
            // enable passthrough only if HDMI is connected and enabled in options
            // Use effective max PCM channels
            int maxPcmChannels = CustomApplication.getEffectiveMaxPcmChannels();
            log.info("onStart: PCM diagnostic - maxPcmChannels={} hasHdmi={} isIecCapable={} isDirectPcmCapable={} maxAudioChannelCount={}",
                    maxPcmChannels,
                    CustomApplication.isHdmiConnected(),
                    CustomApplication.isIecEncapsulationCapable(),
                    CustomApplication.isDirectPcmMultichannelCapable(),
                    CustomApplication.getMaxAudioChannelCount());
            LibAvos.setMaxPcmChannels(maxPcmChannels);
            log.info("onStart: Set max PCM channels to {}", maxPcmChannels);
            LibAvos.setPcmChannelMasks(CustomApplication.getHdmiChannelMasks());
            int passthroughMode = CustomApplication.isPassthroughSupported() ? Integer.parseInt(mPreferences.getString("force_audio_passthrough_multiple","0") ) : 0;
            LibAvos.setPassthrough(passthroughMode);
            resetUnsupportedPassthroughAudioDelayPreset();
            if (mPreferences.getBoolean(VideoPreferencesCommon.KEY_FORCE_AUDIO_PASSTHROUGH, false)) {
                long forcedFlags = CustomApplication.allHdmiAudioCodecs;
                if (!CustomApplication.isIecEncapsulationCapable()) {
                    // Don't fake IEC support on devices that never advertised it; otherwise
                    // mode 3 fallback gets forced back into IEC and fails on IEC-less eARC.
                    forcedFlags &= ~(1L << 13); // clear AVOS_ENCODING_IEC61937
                }
                LibAvos.setHdmiSupportedAudioCodecs(forcedFlags);
            } else {
                LibAvos.setHdmiSupportedAudioCodecs(CustomApplication.getNativeAudioCodecsFlag());
            }
            LibAvos.setMediaCodecAudioCapabilities(CustomApplication.getMediaCodecAudioCapabilitiesFlag());
            LibAvos.setSpatializerCapabilities(CustomApplication.getSpatializerCapabilities());
            mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            applySpatializationPreferenceToAvos();
            // note enable_downmix_androidtv and disable_downmix are the opposite same settings but only one applies to androidTV
            // this is done on purpose to respect logic of presentation and default value
            float audioSpeed;
            if (passthroughMode == 0) {
                audioSpeed = mPreferences.getFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f);
                if (log.isDebugEnabled()) log.debug("onStart: {}", audioSpeed);
            } else {
                if (log.isDebugEnabled()) log.debug("onStart: {}", 1.0f);
                audioSpeed = 1.0f;
            }
            String size = mPreferences.getString(KEY_STREAM_BUFFER_SIZE, String.valueOf(DEFAULT_STREAM_BUFFER_SIZE));
            int finalSize;
            try {
                finalSize = Integer.parseInt(size);
            } catch(NumberFormatException | NullPointerException e) {
                finalSize = DEFAULT_STREAM_BUFFER_SIZE;
            }
            LibAvos.setStreamBufferSize(finalSize);
            size = mPreferences.getString(KEY_STREAM_MAX_IFRAME_SIZE, String.valueOf(DEFAULT_MAX_IFRAME_SIZE));
            try {
                finalSize = Integer.parseInt(size);
            } catch(NumberFormatException | NullPointerException e) {
                finalSize = DEFAULT_MAX_IFRAME_SIZE;
            }
            LibAvos.setStreamMaxIframeSize(finalSize);
            LibAvos.enableAudioSpeed(VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences));
            LibAvos.disableAtempoFilter(mPreferences.getBoolean(VideoPreferencesCommon.KEY_AUDIO_SPEED_AUDIOTRACK, false));
            LibAvos.setAudioSpeed(audioSpeed); // set audio speed playback (does nothing if audio speed not enabled)
            LibAvos.setDynamicAudioDelay(mPreferences.getBoolean(VideoPreferencesCommon.KEY_ENABLE_DYNAMIC_AUDIO_DELAY, true)); // AVOS applies it only when the active sink path can use dynamic delay.
            LibAvos.parserSyncMode(Integer.parseInt(mPreferences.getString(KEY_PARSER_SYNC_MODE,"0"))); // set lavc parser sync mode (0: PTS, 1 samples)
            applyDownmixPreferenceToAvos();
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

    @SuppressWarnings("deprecation") // Bundle.get(key) is required for the untyped external header bundle.
    private void postOnPlayerServiceBind() {
        if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: START, mResumeFromLast={}", mResumeFromLast);
        if (!mResumeFromLast && getSharedPreferences("player", 0).getInt("lastintent", 0) == getIntent().hashCode()) {
            /* resume video if last intent == current intent
             * (when resumed from history for example)
             * NOTE: Skip this check when used as external player with position extras to respect caller's position
             */
            boolean hasPositionExtras = ExternalResumeIntent.hasPositionExtra(getIntent());
            if (!(mIsExternalPlayer && hasPositionExtras)) {
                mResumeFromLast = true;
                if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: Set mResumeFromLast=true due to lastintent match");
            } else {
                if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: Skipping mResumeFromLast - external player with position extras detected");
            }
        }
        final String action = getIntent().getAction();
        if (mResumeFromLast || (action != null && action.equals(ArchosIntents.ARCHOS_RESUME_VIDEOPLAYER))) {
            mResume = RESUME_FROM_LAST_POS;
            getIntent().putExtra(RESUME, mResume);
            if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: Set mResume=RESUME_FROM_LAST_POS due to mResumeFromLast or ARCHOS_RESUME action");
        } else {
            mResume = getIntent().getIntExtra(RESUME, RESUME_NO);
            if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: Retrieved mResume from intent, value={}", mResume);
            // Check for external resume position if no other resume mode is set
            if (mResume == RESUME_NO) {
                // Presence alone is not sufficient: position=0 and malformed values historically
                // mean "start from the beginning", not "select the remote bookmark".
                if (ExternalResumeIntent.hasValidLaunchPosition(getIntent())) {
                    mResume = RESUME_FROM_REMOTE_POS;
                    getIntent().putExtra(RESUME, mResume);
                    if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: explicit position is owned by PlayerService");
                }
            }
        }

        mIsReadytoStart = false;
        if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind() ");

        // Read HTTP headers passed by external apps (MX Player API / Stremio)
        Bundle headersBundle = getIntent().getBundleExtra("headers");
        if (headersBundle != null && !headersBundle.isEmpty()) {
            mExtraMap = new java.util.HashMap<>();
            for (String key : headersBundle.keySet()) {
                Object value = headersBundle.get(key);
                if (value instanceof String) {
                    mExtraMap.put(key, (String) value);
                }
            }
            if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: found {} HTTP headers from external intent: {}", mExtraMap.size(), mExtraMap.keySet());
        } else {
            if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: no HTTP headers in external intent");
        }
        // Header values may contain bearer tokens; log only their names.
        if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: uri={} headerNames={}",
                getIntent().getData(), mExtraMap != null ? mExtraMap.keySet() : null);

        Intent intent = new Intent();
        intent.putExtras(getIntent());
        intent.setData(getIntent().getData());
        // Internal marker lets the service distinguish a new external ACTION_VIEW command from
        // a frontend handoff. It is paired with SESSION_POSITION for lifecycle reattachment.
        intent.putExtra(ExternalResumeIntent.EXTERNAL_PLAYER_LAUNCH, mIsExternalPlayer);

        PlayerService.sPlayerService.switchPlayerFrontend(mPlayerListener);
        Player.sPlayer = mPlayer;
        PlayerService.sPlayerService.setPlayer();
        if(mPermissionChecker.hasExternalPermission(this)) {
            if (log.isDebugEnabled()) log.debug("postOnPlayerServiceBind: hasExternalPermission");
            PlayerService.sPlayerService.onStart(intent);
            mUserPausedVideo = !PlayerService.sPlayerService.isPlayOnResume();
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
        if (log.isDebugEnabled()) log.debug("onResume");
        // Clock (for leanback devices only)
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK) || isChromeOS(mContext)) {
            registerReceiver(mClockReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        }
        PlayerBrightnessManager.getInstance().restoreBrightness(this);
        if(!mWasInPictureInPicture){
            mPermissionChecker.checkAndRequestPermission(this);
            if (!isFinishing() && !isDestroyed()) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mHasAskedFloatingPermission&&Settings.canDrawOverlays(this)){ //permission has been granted
                    startService(new Intent(this, FloatingPlayerService.class));
                }
                mHasAskedFloatingPermission = false;
                TorrentObserverService.resumed(PlayerActivity.this);
            }
            addNetworkListener();
            if (mPaused) {
                mPaused = false;
                mPlayer.checkSubtitles();
                // Ensure mPlayOnResume stays false if player is paused
                if (mPlayer.isPaused() && PlayerService.sPlayerService != null) {
                    if (log.isDebugEnabled()) log.debug("onResume: player is paused, ensuring mPlayOnResume = false and mUserPausedVideo = true");
                    mUserPausedVideo = true;
                    PlayerService.sPlayerService.setPlayOnResume(false);
                }
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

    @SuppressWarnings("deprecation") // requestVisibleBehind: deprecated API 26, no replacement for Android TV
    @Override
    protected void onPause() {
        super.onPause();
        if (log.isDebugEnabled()) log.debug("onPause");

        // Clock (for leanback devices only)
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK) || isChromeOS(mContext)) {
            unregisterReceiver(mClockReceiver);
        }
        if (ArchosFeatures.isAndroidTV(this)) {
            if (mPlayer.isPlaying()) {
                if (!requestVisibleBehind(true)&&!mLaunchFloatingPlayer) {
                    // Try to play behind launcher, but if it fails, so paused.
                    TorrentObserverService.paused(this);
                    removeNetworkListener();
                }
            } else {
                requestVisibleBehind(false);
                TorrentObserverService.paused(this);
                removeNetworkListener();
            }
        } else {
            TorrentObserverService.paused(this);
            removeNetworkListener();
        }
        mPaused = true;

        if (PlayerService.sPlayerService != null) {
            PlayerService.sPlayerService.checkpointPlaybackIntent(getIntent());
        }

        // If player is paused when activity pauses (screen off), preserve pause state
        if (mPlayer != null && mPlayer.isPaused() && PlayerService.sPlayerService != null) {
            if (log.isDebugEnabled()) log.debug("onPause (activity): player is paused, setting mPlayOnResume = false");
            PlayerService.sPlayerService.setPlayOnResume(false);
        }

    }

    @SuppressWarnings("deprecation") // onVisibleBehindCanceled: deprecated API 26, no replacement for Android TV
    @Override
    public void onVisibleBehindCanceled() {
        mPaused = true;
        TorrentObserverService.paused(this);
        super.onVisibleBehindCanceled();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (log.isDebugEnabled()) log.debug("onStop");

        // Refresh the service-owned handoff position in case playback continued behind Home.
        if (PlayerService.sPlayerService != null) {
            PlayerService.sPlayerService.checkpointPlaybackIntent(getIntent());
        }

        // Home and screensavers also call onStop(). They are session checkpoints, not an
        // external-player completion, so do not publish a stale result here. Every deliberate
        // exit goes through finish(), which reports the latest service-owned snapshot.
        if (isFinishing()) sendExternalPlayerResult();

        if (mStopped)
            return;
        if(mTorrent!=null){
            if (log.isDebugEnabled()) log.debug("onStop, unbinding torrentObserver");
        }

        /*
         * register a receiver that finish activity in case
         * network state is changed since a resume is not possible anymore.
         */
        if (!mPlayer.isLocalVideo())
            addNetworkListener();

        stopDialog();

        mPlayerController.hide();

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
        removeNetworkListener();
    }

    @Override
    protected void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy");

        // System-driven destruction after Home/screensaver is not an external-player exit.
        // finish() normally sends first; this guarded fallback covers framework finish paths.
        if (isFinishing()) sendExternalPlayerResult();

        stopDialog();
        removeNetworkListener();

        // System/screensaver recreation must retain a user pause. A deliberate finish ends it.
        if (isFinishing()) {
            mPreferences.edit()
                    .putBoolean(PlayerService.PREFERENCE_USER_PAUSED_VIDEO, false)
                    .remove(PlayerService.PREFERENCE_USER_PAUSED_URI)
                    .apply();
            if (log.isDebugEnabled()) log.debug("onDestroy: cleared finished paused session");
        }

        // Unregister DisplayListener to prevent memory leak
        if (mDisplayManager != null && mDisplayListener != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
            mDisplayListener = null;
            mDisplayManager = null;
        }

        VideoEffect.resetForcedMode();
        if (log.isDebugEnabled()) log.debug("onDestroy: setEffect");
        setEffect(VideoEffect.getDefaultMode());
        super.onDestroy();
    }

    @SuppressWarnings("deprecation") // getRealSize/getSize: API 30+ uses getCurrentWindowMetrics
    private void updateSizes() {
        boolean isInPictureInPictureMode = Build.VERSION.SDK_INT>=Build.VERSION_CODES.N&&isInPictureInPictureMode();
        boolean isInMultiWindowMode = Build.VERSION.SDK_INT>=Build.VERSION_CODES.N&&isInMultiWindowMode();
        int width, height, layoutWidth, layoutHeight, displayWidth, displayHeight;
        // returns the real screen dimension
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect realBounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            displayWidth = realBounds.width();
            displayHeight = realBounds.height();
        } else {
            Point realPoint = new Point();
            getWindowManager().getDefaultDisplay().getRealSize(realPoint);
            displayWidth = realPoint.x;
            displayHeight = realPoint.y;
        }
        // returns the available dimension (real screen size minus decors): this is needed on phones, cannot only matchParent
        Point point = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // In API 30+ use currentWindowMetrics bounds minus insets for the usable area
            Rect bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
            point.set(bounds.width(), bounds.height());
        } else {
            getWindowManager().getDefaultDisplay().getSize(point);
        }
        // note on chromeos pixelbook point.y when fullscreen only reports a wrong layoutHeight (2400x1400 instead of 2400x1600) as if there are hidden decors
        // status bar | action bar | navigation bar, system bar = status bar + navigation bar
        layoutWidth = point.x;
        layoutHeight = point.y;

        boolean isPortrait = ((1.0f*layoutHeight/layoutWidth)>1.0);
        boolean isSeenPortrait = ((1.0f*displayHeight/displayWidth)>1.0);

        //Find the screen density.
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        
        //Set the Floating Player Size, 45% of the smallest side, or 2 inches on Tablet etc.
        int smallestSide = (isPortrait ? displayWidth : displayHeight);
        int smallestSideLayout = (isPortrait ? layoutWidth : layoutHeight);
        mPlayerController.floatingPlayerSize =  (int) ( smallestSide / metrics.densityDpi < 3 ?
                (int) (smallestSideLayout * 0.45):
                metrics.densityDpi * 2);
                
        //Update the Strecth X /Y Icon
        mPlayerController.setStretchXYIcon();

        //Update the Strecth X /Y Icon
        mPlayerController.setFullscreenWithCutoutButtonIcon(mFullScreenWithCutout);

        // hack to fix fullscreen height on chromeos pixelbook (and more?) since it reports 2400x1440 instead of 2400x1600 but ok in multiWindow
        if(isChromeOS(mContext)&&(layoutWidth == displayWidth)&&(layoutHeight != displayHeight)) {
            log.warn("CONFIG updateSizes: hack correcting on chromeOS layoutHeight from {} to {}", layoutHeight, displayHeight);
            layoutHeight = displayHeight;
        }

        if (log.isDebugEnabled()) log.debug("CONFIG updateSizes isPortrait={}, isSeenPortrait={}, isInMultiWindowMode()={}, isInPictureInPictureMode()={}, layout=({},{}), display=({},{})",
                isPortrait, isSeenPortrait, isInMultiWindowMode, isInPictureInPictureMode,
                layoutWidth, layoutHeight, displayWidth, displayHeight);

        // if rotation is locked reverse w/h but only if we have a difference of portrait/landscape perception between layout and screen dimension
        if (isRotationLocked()&&(isPortrait != isSeenPortrait)) {
            int swapTemp = displayWidth;
            displayWidth = displayHeight;
            displayHeight = swapTemp;
            if (log.isDebugEnabled()) log.debug("CONFIG updateSizes RotationLocked overriding display ({},{})", displayWidth, displayHeight);
        }

        if (!isInPictureInPictureMode&&!isInMultiWindowMode) {
            width = displayWidth;
            height = displayHeight;
        } else {
            width = layoutWidth;
            height = layoutHeight;
        }

        // Note: mRootView contains player.xml layout and must cover full screen including cutout
        if (log.isDebugEnabled()) log.debug("CONFIG updateSizes: trueFullscreen size WxH={}x{}", width, height);
        if(!isChromeOS(mContext)) { //keeping things as it was on other devices
            ViewGroup.LayoutParams lp = mRootView.getLayoutParams();
            lp.width = width;
            lp.height = height;
            mRootView.setLayoutParams(lp);
        }
        mScreenHeight = height;
        mScreenWidth = width;
        mSurfaceController.setScreenSize(width, height);
        mSubtitleManager.setScreenSize(width, height);
        if(!isInPictureInPictureMode) {
            if (log.isDebugEnabled()) log.debug("CONFIG updateSizes: not PIP mPlayerController.setSizes layout=({},{}), display=({},{})", layoutWidth, layoutHeight, displayWidth, displayHeight);
            mPlayerController.setSizes(displayWidth, displayHeight, layoutWidth, layoutHeight);
            // Close the menus if needed
            mAudioInfoController.resetPopup();
            mSubtitleInfoController.resetPopup();
        }
        int size = mPreferences.getInt(KEY_SUBTITLE_SIZE, mSubtitleSizeDefault);
        int vpos = mPreferences.getInt(KEY_SUBTITLE_VPOS, mSubtitleVPosDefault);
        if(isInPictureInPictureMode||isInMultiWindowMode) { //proportional size
            size = (int) ((layoutWidth / (float)(displayHeight<displayWidth?displayWidth:displayHeight)) * size);
            // note that in multiwindow mode chromeos returns correct height but not in full screen thus it works here
            vpos = (int) ((layoutHeight / (float)(displayHeight<displayWidth?displayHeight:displayWidth)) * vpos);
        }
        if (log.isDebugEnabled()) log.debug("CONFIG updateSizes: mSubtitleManager.setSize({}), vpos={}", size, vpos);
        mSubtitleManager.setSize(size);
        setSubtitleVpos(vpos, "updateSizes");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (log.isDebugEnabled()) log.debug("CONFIG onConfigurationChanged: do updateSizes()");
                updateSizes();
            }
        });

        invalidateOptionsMenu();
    }

    public static String getHumanReadableRotation(int rotation) {
        return switch (rotation) {
            case Surface.ROTATION_0 -> "0°";
            case Surface.ROTATION_90 -> "90°";
            case Surface.ROTATION_180 -> "180°";
            case Surface.ROTATION_270 -> "270°";
            default -> "";
        };
    }

    private static String getHumanReadableActivityOrientation(int orientation) {
        return switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "landscape";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "reverse landscape";
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "portrait";
            case ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "unspecified";
            default -> "";
        };
    }

    @SuppressWarnings("deprecation") // getDefaultDisplay: API 30+ uses getDisplay()
    private void setLockRotation(boolean avpLock) {
        int rotation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rotation = getDisplay().getRotation();
        } else {
            rotation = getWindowManager().getDefaultDisplay().getRotation();
        }
        if (log.isDebugEnabled()) log.debug("CONFIG setLockRotation, rotation status: {}, i.e. {}", rotation, getHumanReadableRotation(rotation));

        boolean systemLock;
        try {
            systemLock = 1 != Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
        } catch (SettingNotFoundException e) {
            systemLock = false;
        }
        mIsRotationLocked = (avpLock || systemLock);
        if (log.isDebugEnabled()) log.debug("avpLock: {} systemLock: {}", avpLock, systemLock);
        if (mIsRotationLocked) {
            int tmpOrientation = getResources().getConfiguration().orientation;
            if (log.isDebugEnabled()) log.debug("CONFIG setLockRotation: current orientation is {}", getHumanReadableActivityOrientation(tmpOrientation));
            int wantedOrientation;

            if (tmpOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    mLockedRotation = Surface.ROTATION_90;
                } else {
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    mLockedRotation = Surface.ROTATION_270;
                }
                if (log.isDebugEnabled()) log.debug("CONFIG setLockRotation: wanted orientation is {}", getHumanReadableActivityOrientation(wantedOrientation));
                setRequestedOrientation(wantedOrientation);
            }
            else if (tmpOrientation == Configuration.ORIENTATION_PORTRAIT || tmpOrientation == Configuration.ORIENTATION_UNDEFINED) {
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90) {
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    mLockedRotation = Surface.ROTATION_90;
                } else {
                    wantedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    mLockedRotation = Surface.ROTATION_270;
                }
                if (log.isDebugEnabled()) log.debug("CONFIG setLockRotation: wanted orientation is {}", getHumanReadableActivityOrientation(wantedOrientation));
                setRequestedOrientation(wantedOrientation);
            }
        } else {
            if (log.isDebugEnabled()) log.debug("CONFIG setLockRotation: wanted orientation is unspecified");
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (log.isDebugEnabled()) log.debug("onNewIntent: {}", intent);
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
                if (log.isDebugEnabled()) log.debug("onGenericMotionEvent : event=ACTION_MOVE");

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
                setSubtitleVpos(delay/100, "onDelayChanged");
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

        final TVMenuItem tvmBg = tvmenu.createAndAddTVSwitchableMenuItem(getResources().getString(R.string.subtitle_background_text), mSubtitleManager.getBackgroundState()); // Make sure to add string resource or use literal "Subtitle Background"
        tvmBg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean bg = mSubtitleManager.getBackgroundState();
                tvmBg.setChecked(!bg);
                mSubtitleManager.setBackgroundState(!bg);
            }
        });

        tvmenu.createAndAddTVMenuItem(getResources().getString(R.string.subtitle_bg_opacity_text), false);

        final SubtitleDelayTVPicker tvPickerOpacity = (SubtitleDelayTVPicker) LayoutInflater.from(mContext)
                .inflate(R.layout.subtitle_delay_tv_picker, null);
        tvPickerOpacity.setStep(1); 
        tvPickerOpacity.setMin(0);
        tvPickerOpacity.setMax(255 * 100); 
        tvmenu.addTVMenuItem(tvPickerOpacity);
        tvPickerOpacity.setTextViewWidth((int) pickerWidth);
        tvPickerOpacity.setUpdateText(false);
        tvPickerOpacity.setText(String.valueOf(mSubtitleManager.getBackgroundOpacity()));
        tvPickerOpacity.init(mSubtitleManager.getBackgroundOpacity() * 100, new SubtitleDelayPickerAbstract.OnDelayChangedListener() {
            @Override
            public void onDelayChanged(SubtitleDelayPickerAbstract view, int delay) {
                int opacity = delay / 100;
                if (opacity < 0) opacity = 0;
                if (opacity > 255) opacity = 255;
                mSubtitleManager.setBackgroundOpacity(opacity);
                tvPickerOpacity.setText(String.valueOf(opacity));
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
                mPreferences.edit().putBoolean(PlayerActivity.KEY_SUBTITLE_BACKGROUND, mSubtitleManager.getBackgroundState()).apply();
                mPreferences.edit().putInt(PlayerActivity.KEY_SUBTITLE_BG_OPACITY, mSubtitleManager.getBackgroundOpacity()).apply();
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

        tvPicker.setStep(10);
        if (mPlayer.getDuration() > 0) {
            tvPicker.setMax(mPlayer.getDuration());
            tvPicker.setMin(-mPlayer.getDuration());
        }
        if (isPassthroughAudioDelayLimited()) {
            tvPicker.setMax(0);
        }
        tvPicker.setHourFormat(true);
        tvmenu.addTVMenuItem(tvPicker);
        final TVMenuItem saveSettingCB = tvmenu.createAndAddTVSwitchableMenuItem(getString(R.string.keep_setting), mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0) != 0);
        saveSettingCB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettingCB.toggle();
                if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog:onClick saveSettingCB.isChecked()={}", saveSettingCB.isChecked());
                if (saveSettingCB.isChecked()) {
                    int delay = clampAudioDelayForPassthrough(PlayerService.sPlayerService.getAudioDelay());
                    if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog: keep setting toggled ON, save current audio delay={} in prefs", delay);
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), delay).apply();
                } else {
                    if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog: keep setting toggled OFF, save 0 in prefs");
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), 0).apply();
                }
            }
        });
        // View hierarchy is
        // TVCardDialog
        //└── LinearLayout
        //    └── TVScrollView
        //        └── TVMenu
        //            ├── AudioDelayTVPicker
        //            └── TVMenuItem (saveSettingCB)
        tvPicker.init(clampAudioDelayForPassthrough(PlayerService.sPlayerService.getAudioDelay()), new AudioDelayPickerAbstract.OnAudioDelayChangedListener() {
            @Override
            public void onAudioDelayChanged(AudioDelayPickerAbstract view, int delay) {
                delay = clampAudioDelayForPassthrough(delay);
                if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog:onAudioDelayChanged delay={}", delay);
                PlayerActivity.this.onAudioDelayChange(null, delay);
                if (saveSettingCB.isChecked()) {
                    if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog: audio delay changed to {} with keep setting ON, save in prefs", delay);
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), delay).apply();
                }
            }
        });
        ((TVCardDialog)dialogView).addOtherView(tvmenu);
        ((TVCardDialog)dialogView).setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {
            @Override
            public void onResult(int code) {
                mPlayerController.getTVMenuAdapter().setDiscrete(false);
                if(saveSettingCB.isChecked()){
                    int delay = clampAudioDelayForPassthrough(PlayerService.sPlayerService.getAudioDelay());
                    if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog:onResult save audio delay={} in prefs", delay);
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), delay).apply();
                }
                else {
                    if (log.isDebugEnabled()) log.debug("createTVAudioDelayDialog:onResult do not save audio delay and carve 0 in prefs");
                    mPreferences.edit().putInt(getString(R.string.save_delay_setting_pref_key), 0).apply();
                }
            }
        });

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        mPlayerController.addToMenuContainer(dialogContainer);
        tvPicker.requestFocus();
    }

    private void createTVAudioSpeedDialog() {
        View dialogContainer = (View)LayoutInflater.from(mContext).inflate(R.layout.card_dialog_layout, null);
        View dialogView = dialogContainer.findViewById(R.id.card_view);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dialogView.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;
        dialogView.setLayoutParams(params);
        ((TVCardDialog) dialogView).setText((String) getText(R.string.player_pref_audio_speed_title));

        mPlayerController.getTVMenuAdapter().setDiscrete(true);
        final TVMenu tvmenu = mPlayerController.getTVMenuAdapter().createTVMenu();

        // adding tv picker
        AudioSpeedTVPicker tvPicker = (AudioSpeedTVPicker)LayoutInflater.from(mContext)
                .inflate(R.layout.audio_speed_tv_picker, null);
        tvmenu.addTVMenuItem(tvPicker);
        final TVMenuItem saveSettingCB = tvmenu.createAndAddTVSwitchableMenuItem(getString(R.string.keep_setting), PlayerService.sPlayerService.getAudioSpeedFromPreferences() != 1.0f);
        saveSettingCB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettingCB.toggle();
                // Save preference immediately when toggled
                if(saveSettingCB.isChecked()){
                    if (log.isDebugEnabled()) log.debug("createTVAudioSpeedDialog: keep setting toggled ON, save current audio speed={} in prefs", PlayerService.sPlayerService.getAudioSpeed());
                    mPreferences.edit().putFloat(getString(R.string.save_audio_speed_setting_pref_key), PlayerService.sPlayerService.getAudioSpeed()).apply();
                } else {
                    if (log.isDebugEnabled()) log.debug("createTVAudioSpeedDialog: keep setting toggled OFF, save 1.0f in prefs");
                    mPreferences.edit().putFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f).apply();
                }
            }
        });
        tvPicker.init(PlayerService.sPlayerService.getAudioSpeed(), new AudioSpeedPickerAbstract.OnAudioSpeedChangedListener() {
            @Override
            public void onAudioSpeedChanged(AudioSpeedPickerAbstract view, float speed) {
                PlayerActivity.this.onAudioSpeedChange(null, speed);
                // Save preference immediately when speed changes if keep setting is enabled
                if(saveSettingCB.isChecked()) {
                    if (log.isDebugEnabled()) log.debug("createTVAudioSpeedDialog: audio speed changed to {} with keep setting ON, save in prefs", speed);
                    mPreferences.edit().putFloat(getString(R.string.save_audio_speed_setting_pref_key), speed).apply();
                }
            }
        });
        ((TVCardDialog)dialogView).addOtherView(tvmenu);
        ((TVCardDialog)dialogView).setOnDialogResultListener(new TVCardDialog.OnDialogResultListener() {
            @Override
            public void onResult(int code) {
                if (log.isDebugEnabled()) log.debug("createTVAudioSpeedDialog:onResult CALLED with code={}", code);
                mPlayerController.getTVMenuAdapter().setDiscrete(false);
                if(saveSettingCB.isChecked()){
                    if (log.isDebugEnabled()) log.debug("createTVAudioSpeedDialog:onResult save audio speed={} in prefs", PlayerService.sPlayerService.getAudioSpeed());
                    mPreferences.edit().putFloat(getString(R.string.save_audio_speed_setting_pref_key), PlayerService.sPlayerService.getAudioSpeed()).apply();
                }
                else {
                    if (log.isDebugEnabled()) log.debug("createTVAudioSpeedDialog:onResult do not save audio speed and carve 1.0f in prefs");
                    mPreferences.edit().putFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f).apply();
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

            if (log.isDebugEnabled()) log.debug("refreshSubtitleTVMenu: mSubtitleInfoController.getTrackCount()={}", mSubtitleInfoController.getTrackCount());

            mPlayerController.getTVMenuAdapter().setCardViewVisibility(View.VISIBLE, mSubtitleTVCardView);

            if(mSubtitleInfoController.getTrackCount()>0) {
                for (int i = 0; i < mSubtitleInfoController.getTrackCount(); i++) {
                    mSubtitleTVMenu.createAndAddTVMenuItem(mSubtitleInfoController.getTrackNameAt(i).toString(), true, mSubtitleInfoController.getTrack() == i);
                }
                mSubtitleTVMenu.createAndAddSeparator();
                mSubtitleDelayMenuItem = mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.player_pref_subtitle_delay_title).toString(), false, false);
                mSubtitleDelayMenuItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        createTVSubtitleDialog();
                    }
                });

                if (log.isDebugEnabled()) log.debug("refreshSubtitleTVMenu: isCurrentSubtrackNone={}", isCurrentSubtrackNone());
                disableSubtitleDelayTVMenuItem(isCurrentSubtrackNone());

                mSubtitleSettingsMenuItem = mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.menu_player_settings).toString(), false, false);
                mSubtitleSettingsMenuItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        createTVSubtitleSettingsDialog();
                    }
                });

                if (log.isDebugEnabled()) log.debug("refreshSubtitleTVMenu: isCurrentSubtrackGfx={}", isCurrentSubtrackGfx());
                disableSubtitleSettingsMenuItem(isCurrentSubtrackGfx() || isCurrentSubtrackNone());
            }
            mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.get_subtitles_online).toString(), false, false).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadSubtitles();
                }
            });

            Uri uri = VideoUtils.getFileUriFromMediaLibPath(mUri.toString());

            if (uri.getScheme().equals("file") || uri.getScheme().startsWith("smb") || uri.getScheme().equals("sshj") || uri.getScheme().equals("sftp")) {
                mSubtitleTVMenu.createAndAddTVMenuItem(getText(R.string.get_subtitles_on_drive).toString(), false, false).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        chooseSubtitles();
                    }
                });
            }
        }
    }

    private void refreshAudioTracksTVMenu() {
        if (mAudioTracksTVMenu != null) {
            mAudioTracksTVMenu.clean();
            if (mAudioInfoController.getTrackCount() > 0) {
                mPlayerController.getTVMenuAdapter().setCardViewVisibility(View.VISIBLE, mAudioTracksTVCardView);

                for (int i = 0; i < mAudioInfoController.getTrackCount(); i++) {
                    mAudioTracksTVMenu.createAndAddTVMenuItem(mAudioInfoController.getTrackNameAt(i).toString(), true, mAudioInfoController.getTrack() == i);
                }

                mAudioTracksTVMenu.createAndAddSeparator();

                // Get passthrough mode to disable audio boost and night mode for passthrough modes 1 and 2 only
                // Modes 0 (disabled) and 3 (recoding) support audio filters
                int passthroughMode = Integer.parseInt(mPreferences.getString("force_audio_passthrough_multiple", "0"));
                boolean isPassthroughActive = passthroughMode == 1 || passthroughMode == 2;

                final TVMenuItem tvmi = mAudioTracksTVMenu.createAndAddTVSwitchableMenuItem(getResources().getString(R.string.pref_audio_filt_title), PlayerService.sPlayerService.mAudioFilt > 0);
                tvmi.setDisabled(isPassthroughActive);
                tvmi.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        PlayerService.sPlayerService.setAudioFilt(PlayerService.sPlayerService.mAudioFilt > 0 ? 0 : 3);
                        tvmi.setChecked(PlayerService.sPlayerService.mAudioFilt > 0);
                    }
                });

                final TVMenuItem tvmi2 = mAudioTracksTVMenu.createAndAddTVSwitchableMenuItem(getResources().getString(R.string.pref_audio_filt_night_mode), PlayerService.sPlayerService.mNightModeOn);
                tvmi2.setDisabled(isPassthroughActive);
                tvmi2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // TODO Auto-generated method stub
                        PlayerService.sPlayerService.setNightMode(!PlayerService.sPlayerService.mNightModeOn);
                        tvmi2.setChecked(PlayerService.sPlayerService.mNightModeOn);
                    }
                });

                final TVMenuItem tvmiSpatialization = mAudioTracksTVMenu.createAndAddTVSwitchableMenuItem(
                        getResources().getString(R.string.spatialization_capabilities),
                        isSpatializationEnabledForPlayback());
                tvmiSpatialization.setDisabled(!isSpatializationToggleAvailable());
                tvmiSpatialization.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleSpatializationPreference();
                        tvmiSpatialization.setChecked(isSpatializationEnabledForPlayback());
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

                // disable playback speed if audio speed is disabled (passthrough, MediaCodec audio decoder, or API < 23)
                if (VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences)) {
                    final TVMenuItem tvmi4 = mAudioTracksTVMenu.createAndAddTVMenuItem(getText(R.string.player_pref_audio_speed_title).toString(), false, false);
                    tvmi4.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            createTVAudioSpeedDialog();
                        }
                    });
                }
            } else {
                mPlayerController.getTVMenuAdapter().setCardViewVisibility(View.GONE, mAudioTracksTVCardView);
            }
        }
    }

    // The intro/outro timings are fetched asynchronously and may not be known when the Play
    // mode tile is first built. This adds (or updates) the summary line inside the Play mode
    // menu once the segments become available; safe to call repeatedly (e.g. on each menu show).
    public void refreshPlayModeIntroSummary() {
        if (mPlayModeTVMenu == null) return;
        IntroSegments segments = (PlayerService.sPlayerService != null) ? PlayerService.sPlayerService.getIntroSegments() : null;
        String summary = (segments != null) ? segments.toSummaryString(PlayerService.introLabels(this), getString(R.string.introdb_segment_end)) : null;
        if (summary == null) return;
        if (mIntroSummaryMenuItem == null) {
            mPlayModeTVMenu.createAndAddSeparator();
            mIntroSummaryMenuItem = mPlayModeTVMenu.createAndAddTVMenuItem(summary, false);
            ViewGroup.LayoutParams lp = mIntroSummaryMenuItem.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mIntroSummaryMenuItem.setLayoutParams(lp);
            }
        } else {
            mIntroSummaryMenuItem.setText(summary);
        }
        TextView summaryText = (TextView) mIntroSummaryMenuItem.findViewById(R.id.info_text);
        if (summaryText != null) {
            summaryText.setSingleLine(false);
            summaryText.setMaxLines(6);
            summaryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        }
    }

    public void createPlayerTVMenu() {
        if (mPlayerController != null && mPlayerController.getTVMenuAdapter() != null) {
            TVMenuAdapter tma = mPlayerController.getTVMenuAdapter();

            //[subtitles]
            mSubtitleTVCardView = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(), R.drawable.tv_subtitles, null),
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

            //[audiotrack]
            mAudioTracksTVCardView = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(), R.drawable.tv_languages, null),
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

            if (isStereoEffectOn()) {
                TVCardView cv = tma.createAndAddView(ResourcesCompat.getDrawable(getResources(), R.drawable.tv_3d, null), null,
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
            TVCardView tcv = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(),R.drawable.tv_info, null),
                    getResources().getString(R.string.menu_info));
            String decoder = VideoInfoCommonClass.getShortDecoder(mPlayer.getVideoMetadata(), getResources(), mPlayer.getType());

            tcv.setText(decoder);
            tcv.setText2(CodecDiscovery.getTechnicalInfo(mContext));

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
            tcv = tma.createAndAddView(ResourcesCompat.getDrawable(getResources(), R.drawable.tv_format, null), null,
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
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.N && TVUtils.isTV(this)) {
                tvmFormat.createAndAddSeparator();
                vPicInPic = tvmFormat.createAndAddTVMenuItem(getString(R.string.picture_in_picture), false, false);
            }
            else vPicInPic = null;
            tvmFormat.setOnItemClickListener(new View.OnClickListener() {
                @SuppressWarnings("deprecation") // enterPictureInPictureMode(): API 26+ uses PictureInPictureParams
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    if(v == vPicInPic){
                        if (Build.VERSION.SDK_INT>=26)
                            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
                        else
                            if (Build.VERSION.SDK_INT>=24)
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
            tcv = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(), R.drawable.tv_playmode, null),
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

            tvmPlayMode.createAndAddSeparator();
            final TVMenuItem tvmAutoSkip = tvmPlayMode.createAndAddTVSwitchableMenuItem(
                    getResources().getString(R.string.pref_introdb_autoskip_title),
                    mPreferences.getBoolean(PlayerService.KEY_INTRODB_ENABLED, PlayerService.DEFAULT_INTRODB_ENABLED));
            tvmAutoSkip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean enabled = !mPreferences.getBoolean(PlayerService.KEY_INTRODB_ENABLED, PlayerService.DEFAULT_INTRODB_ENABLED);
                    mPreferences.edit().putBoolean(PlayerService.KEY_INTRODB_ENABLED, enabled).apply();
                    tvmAutoSkip.setChecked(enabled);
                }
            });

            mPlayModeTVMenu = tvmPlayMode;
            mIntroSummaryMenuItem = null;
            tcv.addOtherView(tvmPlayMode);
            refreshPlayModeIntroSummary();
            //[/playmode]
            /*
            //[sleep timer]
            TVCardView timerTVCardView = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_menu_delay, null),
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
                TVCardView cv = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(), R.drawable.tv_notifications, null),
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

            final AppCompatActivity mActivity = this;
            tcv = tma.createAndAddView(null, ResourcesCompat.getDrawable(getResources(), R.drawable.tv_settings, null),
                                       getResources().getString(R.string.preferences));
            tcv.setOnSwitchClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO Auto-generated method stub
                    Intent p = new Intent(Intent.ACTION_MAIN);
                    p.setComponent(new ComponentName(mActivity, VideoSettingsActivity.class));
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
            if (log.isDebugEnabled()) log.debug("set3DMode: setEffect");
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
            persistVideoInfo();
            mPlayerController.updateBookmarkToast(mPlayer.getCurrentPosition());
        }
    }

    private void persistVideoInfo() {
        if (PlayerService.sPlayerService != null) {
            PlayerService.sPlayerService.persistVideoInfoFromFrontend(mVideoInfo);
        } else if (mIndexHelper != null && mVideoInfo != null) {
            mIndexHelper.writeVideoInfo(mVideoInfo, mNetworkBookmarksEnabled);
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
                mInfoMenuItem.setIcon(R.drawable.ic_menu_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }

            mBookmarkMenuItem = menu.add(MENU_FILE_ACTIONS_GROUP, MENU_BOOKMARK_ID, Menu.NONE, R.string.menu_bookmark);
            if (mBookmarkMenuItem != null) {
                mBookmarkMenuItem.setIcon(R.drawable.ic_menu_bookmark).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            mAudioInfoController.attachMenu(menu, R.drawable.ic_menu_languages);
            mSubtitleInfoController.attachMenu(menu, R.drawable.ic_menu_subtitles);
            //------------------------------------------------------------------
            // Then add the global items related to the video player
            //------------------------------------------------------------------

            menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_LOCK_ID, Menu.NONE, R.string.menu_lock_player);
            if (!isPluggedOnTv()) {

                mBrightnessMenuItem = menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_BRIGHTNESS_ID, Menu.NONE, R.string.menu_brightness_settings);
                if (mBrightnessMenuItem != null) {
                    mBrightnessMenuItem.setIcon(R.drawable.ic_menu_brightness).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }
                if (mPlayer!=null&&mPlayer.getEffectType()==VideoEffect.EFFECT_NONE) {
                    menuItem = menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_LOCK_ROTATION_ID,
                            Menu.NONE,R.string.rotation_unlock);
                    if (menuItem != null) {
                        menuItem.setIcon(mLockRotation ? R.drawable.ic_menu_locked : R.drawable.ic_menu_unlocked).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                        menuItem.setCheckable(true);
                        menuItem.setChecked(!mLockRotation);
                    }
                }
            }

            menuItem = menu.add(MENU_GLOBAL_ACTIONS_GROUP, MENU_NOTIFICATION_MANAGEMENT_ID,
                    Menu.NONE, R.string.notification_mode);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_notifications);
                menuItem.setShowAsAction(!isPluggedOnTv()? MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            //------------------------------------------------------------------
            // Finally add the other items (which will be available in the menu)
            //------------------------------------------------------------------
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_PLAYMODE_ID, Menu.NONE, R.string.pref_play_mode_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_playmode);
                menuItem.setShowAsAction(!isPluggedOnTv()? MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_AUDIO_FILTER_ID, Menu.NONE, R.string.pref_audio_parameters_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_audioboost);
                menuItem.setShowAsAction(!isPluggedOnTv()? MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_AUDIO_DELAY_ID, Menu.NONE, R.string.player_pref_audio_delay_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_delay);
                menuItem.setShowAsAction(!isPluggedOnTv() ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_AUDIO_SPEED_ID, Menu.NONE, R.string.player_pref_audio_speed_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_baseline_speed_24);
                menuItem.setShowAsAction(!isPluggedOnTv() ? MenuItem.SHOW_AS_ACTION_NEVER : MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            // disable playback speed if audio speed is disabled (passthrough, MediaCodec audio decoder, or API < 23)
            menuItem.setVisible(VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences));
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_SPATIALIZATION_ID, Menu.NONE, R.string.spatialization_capabilities);
            if (menuItem != null) {
                menuItem.setCheckable(true);
                menuItem.setChecked(isSpatializationEnabledForPlayback());
                menuItem.setEnabled(isSpatializationToggleAvailable());
                menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
            menuItem = menu.add(MENU_OTHER_GROUP, MENU_S3D_ID, Menu.NONE, R.string.pref_s3d_mode_title);
            if (menuItem != null) {
                menuItem.setIcon(R.drawable.ic_menu_3d);
                menuItem.setShowAsAction(!isPluggedOnTv()? MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
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
                    .setIcon(R.drawable.ic_menu_settings).setShowAsAction(!isPluggedOnTv()? MenuItem.SHOW_AS_ACTION_NEVER:MenuItem.SHOW_AS_ACTION_ALWAYS);
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
        if (menu.findItem(MENU_SPATIALIZATION_ID) != null) {
            menu.findItem(MENU_SPATIALIZATION_ID).setChecked(isSpatializationEnabledForPlayback());
            menu.findItem(MENU_SPATIALIZATION_ID).setEnabled(isSpatializationToggleAvailable());
        }
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
                if(Build.VERSION.SDK_INT>=  Build.VERSION_CODES.M&&!Settings.canDrawOverlays(this))
                    displayFloatingWindowPermissionDialog();
                else {
                    Intent floatingIntent = new Intent(this, FloatingPlayerService.class);
                    // Pass a service-owned snapshot to the new frontend.
                    if (PlayerService.sPlayerService != null) {
                        int currentPos = PlayerService.sPlayerService.getPlaybackSnapshot().getPositionMs();
                        floatingIntent.putExtra(ExternalResumeIntent.FLOATING_POSITION, currentPos);
                        floatingIntent.putExtra("floating_player_size", mPlayerController.floatingPlayerSize);            //FLOATING PLAYER SIZE
                    }
                    startService(floatingIntent);
                }
                //finish();
                return true;
            case MENU_INFO_ID:
                showVideoInfos();
                return true;
            case MENU_BOOKMARK_ID:
                if (mVideoInfo != null) {
                    mVideoInfo.bookmark = getBookmarkPosition();
                    persistVideoInfo();
                    mPlayerController.updateBookmarkToast(mPlayer.getCurrentPosition());
                }
                return true;
            case MENU_BRIGHTNESS_ID:
                myShowDialog(DIALOG_BRIGHTNESS);
                return true;
            case MENU_LOCK_ROTATION_ID:
                mLockRotation = !mLockRotation;
                if (log.isDebugEnabled()) log.debug("onStart: setLockRotation {}", mLockRotation);
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
                final CharSequence[] playModeEntries = mContext.getResources().getTextArray(R.array.pref_play_mode_entries);
                final ArrayList<RadioButton> playModeRbs = new ArrayList<RadioButton>();

                LinearLayout content = new LinearLayout(mContext);
                content.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                content.setPadding(pad, pad / 2, pad, pad / 2);

                for (int i = 0; i < playModeEntries.length; i++) {
                    final int position2 = i;
                    RadioButton rb = new RadioButton(mContext);
                    rb.setText(playModeEntries[i]);
                    rb.setPadding(pad, pad, pad, pad);
                    rb.setChecked(PlayerService.sPlayerService.mPlayMode == i);
                    playModeRbs.add(rb);
                    rb.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            PlayerService.sPlayerService.menuChangePlayMode(position2);
                            for (RadioButton other : playModeRbs)
                                other.setChecked(other == v);
                        }
                    });
                    content.addView(rb);
                }

                Switch tb = new Switch(mContext);
                tb.setText(R.string.pref_introdb_autoskip_title);
                tb.setPadding(pad, pad, pad, pad);
                tb.setChecked(mPreferences.getBoolean(PlayerService.KEY_INTRODB_ENABLED, PlayerService.DEFAULT_INTRODB_ENABLED));
                tb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        mPreferences.edit().putBoolean(PlayerService.KEY_INTRODB_ENABLED, isChecked).apply();
                    }
                });
                content.addView(tb);

                IntroSegments introSegmentsPhone = (PlayerService.sPlayerService != null) ? PlayerService.sPlayerService.getIntroSegments() : null;
                String introSummaryPhone = (introSegmentsPhone != null) ? introSegmentsPhone.toSummaryString(PlayerService.introLabels(this), getString(R.string.introdb_segment_end)) : null;
                if (introSummaryPhone != null) {
                    TextView footer = new TextView(mContext);
                    footer.setText(introSummaryPhone);
                    footer.setEnabled(false);
                    footer.setPadding(pad, pad / 2, pad, pad / 2);
                    content.addView(footer);
                }

                ScrollView scroll = new ScrollView(mContext);
                scroll.addView(content);
                adb.setView(scroll);
                adb.create().show();

                return true;
            }
            case MENU_AUDIO_FILTER_ID: {
                AlertDialog.Builder adb = new AlertDialog.Builder(this);
                adb.setTitle(R.string.pref_audio_parameters_title);
                final ArrayList<RadioButton> rbs = new  ArrayList<RadioButton>();
                // Get passthrough mode to disable audio boost and night mode for passthrough modes 1 and 2 only
                // Modes 0 (disabled) and 3 (recoding) support audio filters
                final int passthroughMode = Integer.parseInt(mPreferences.getString("force_audio_passthrough_multiple", "0"));
                final boolean isPassthroughActive = passthroughMode == 1 || passthroughMode == 2;
                adb.setAdapter(new ArrayAdapter<View>(mContext, R.layout.menu_item_layout) {
                    @Override
                    public View getView(final int position, View convertView, ViewGroup parent) {
                        if (position == 2) {
				SeekBar sb = new SeekBar(mContext);
				sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					@Override
					public void onStartTrackingTouch(SeekBar sb) {}
					@Override
					public void onStopTrackingTouch(SeekBar sb) {}
					@Override
					public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
						float control = (progress - 50) / 50.0f;
						android.util.Log.d("PHH", "progress changed " + progress);
						var svc = AdditionalServiceSingleton.getService();
						if (svc != null) {
							try {
								svc.setControl(control);
							} catch(RemoteException e) {
						android.util.Log.d("PHH", "failed changing progress", e);
							}
						}
					}
				});
				return sb;
			} else if (position == 1) {
                            Switch tb = new Switch(mContext);
                            tb.setText(R.string.pref_audio_filt_title);
                            tb.setPadding(20,20, 20, 20);
                            tb.setChecked( PlayerService.sPlayerService.mAudioFilt>0);
                            tb.setEnabled(!isPassthroughActive);
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
                            tb.setEnabled(!isPassthroughActive);
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
			// Display the seekbar only if SuperNOVA is available
			if (AdditionalServiceSingleton.getService() != null)
				return 3;
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
                myShowDialog(DIALOG_AUDIO_DELAY);
                return true;
            }
            case MENU_AUDIO_SPEED_ID: {
                myShowDialog(DIALOG_AUDIO_SPEED);
                return true;
            }
            case MENU_SPATIALIZATION_ID: {
                toggleSpatializationPreference();
                item.setChecked(isSpatializationEnabledForPlayback());
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
                            if (log.isDebugEnabled()) log.debug("onOptionsItemSelected: setEffect");
                            setEffectForced(mSavedMode);
                            ad.getListView().setEnabled(true);
                            for(RadioButton rb : rbs)
                                rb.setEnabled(true);
                        }
                        else {
                            if (log.isDebugEnabled()) log.debug("onOptionsItemSelected: setEffect");
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
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showVideoInfos() {
        mPlayerController.hide();

        Class infoActivity = null;

        try {
            // When on an actual leanback device (Android TV, etc.) we give no choice -> Leanback!
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK) || isChromeOS(mContext)) {
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
        // Forward HTTP headers so the info activity can probe the stream with the same headers
        if (mExtraMap != null && !mExtraMap.isEmpty()) {
            Bundle headersBundle = new Bundle();
            for (java.util.Map.Entry<String, String> entry : mExtraMap.entrySet()) {
                headersBundle.putString(entry.getKey(), entry.getValue());
            }
            intent.putExtra("headers", headersBundle);
            if (log.isDebugEnabled()) log.debug("showVideoInfos: forwarding {} headers to info activity", mExtraMap.size());
        }
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

    protected void stopDialog() {
        if (mShowingDialogId != DIALOG_NO && mDialog != null) {
            // assume only one dialog (before there was a call to removeDialog(mShowingDialogId);
            mDialog.dismiss();
            mShowingDialogId = DIALOG_NO;
            mDialog = null;
        }
    }

    // assume there is only one dialog which is by design the case
    protected void myShowDialog(int id) {
        if (mDialog != null) {
            mDialog.dismiss();
            mShowingDialogId = DIALOG_NO;
            mDialog = null;
        }
        switch(id) {
            case DIALOG_SUBTITLE_DELAY:
                SubtitleTrack track = mPlayer.getVideoMetadata().getSubtitleTrack(mVideoInfo.subtitleTrack);
                if (track == null)
                    return;
                boolean hasRatio = track.isExternal;
                mDialog = new SubtitleDelayPickerDialog(this, this, mVideoInfo.subtitleDelay, mVideoInfo.subtitleRatio, hasRatio);
                mPlayerController.hide();
                SubtitleDelayPickerDialog subtitleDelayPickerDialog = (SubtitleDelayPickerDialog) mDialog;
                subtitleDelayPickerDialog.updateDelay(mVideoInfo.subtitleDelay);
                break;
            case DIALOG_SUBTITLE_SETTINGS:
                mDialog = new SubtitleSettingsDialog(this, mSubtitleManager);
                mPlayerController.hide();
                break;
            case DIALOG_AUDIO_DELAY:
                if(PlayerService.sPlayerService!=null)
                    mDialog = new AudioDelayPickerDialog(this, this, clampAudioDelayForPassthrough(PlayerService.sPlayerService.getAudioDelay()), isPassthroughAudioDelayLimited());
                else
                    mDialog = new AudioDelayPickerDialog(this, this, clampAudioDelayForPassthrough(mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0)), isPassthroughAudioDelayLimited());
                AudioDelayPickerDialog audioPickerDialog = (AudioDelayPickerDialog) mDialog;
                audioPickerDialog.setStep(20);
                if (mPlayer.getDuration() > 0) {
                    audioPickerDialog.setMax(mPlayer.getDuration());
                    audioPickerDialog.setMin(-mPlayer.getDuration());
                }
                if(PlayerService.sPlayerService!=null)
                    audioPickerDialog.updateDelay(clampAudioDelayForPassthrough(PlayerService.sPlayerService.getAudioDelay()));
                else
                    audioPickerDialog.updateDelay(clampAudioDelayForPassthrough(mPreferences.getInt(getString(R.string.save_delay_setting_pref_key), 0)));
                mPlayerController.hide();
                break;
            case DIALOG_AUDIO_SPEED:
                if(PlayerService.sPlayerService!=null)
                    mDialog = new AudioSpeedPickerDialog(this, this, PlayerService.sPlayerService.getAudioSpeed());
                else
                    mDialog = new AudioSpeedPickerDialog(this, this, mPreferences.getFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f));
                AudioSpeedPickerDialog audioSpeedPickerDialog = (AudioSpeedPickerDialog) mDialog;
                if(PlayerService.sPlayerService!=null)
                    audioSpeedPickerDialog.updateSpeed(PlayerService.sPlayerService.getAudioSpeed());
                else
                    audioSpeedPickerDialog.updateSpeed(mPreferences.getFloat(getString(R.string.save_audio_speed_setting_pref_key), 1.0f));
                mPlayerController.hide();
                break;
            case DIALOG_NOT_ENOUGHT_SPACE:
                mDialog = new AlertDialog.Builder(this)
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
                    mDialog = new AlertDialog.Builder(this)
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
                mDialog = new BrightnessDialog(this);
                mPlayerController.hide();
                break;
            case DIALOG_CODEC_NOT_SUPPORTED:
                // Show toast instead of plugin download dialog
                int toastStringId = mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED ?
                        R.string.player_err_cantplayvideo : R.string.player_err_cantplaysound;
                Toast.makeText(this, toastStringId, Toast.LENGTH_SHORT).show();
                if (mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED) {
                    finish();
                }
                break;
            case DIALOG_WRONG_DEVICE_KINDLE:
                mDialog = new AlertDialog.Builder(this)
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
                mDialog = null;
                break;
        }
        if (mDialog != null) {
            mDialog.setOnDismissListener(this);
            mShowingDialogId = id;
            mDialog.show();
        }
    }

    /**
     * @return null if there is no update needed
     */
    private Dialog getPluginNeedUpdateDialog() {
        if (LibAvos.pluginNeedUpdate(this)) {
            log.info("pluginNeedUpdate returns true");
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

    private final static String SHOW_FORMAT = "%s  -  S%02dE%02d  -  %s";

    public void setVideoInfo(VideoDbInfo info){
        mVideoInfo = info;
        int viewMode = VideoEffect.getDefaultMode();
        int viewType = VideoEffect.getDefaultType();
        if (mVideoInfo != null) {

            if (log.isDebugEnabled()) log.debug("setVideoInfo: mVideoInfo.subtitleTrack {}", mVideoInfo.subtitleTrack);

            // one of them is already known, don't care and overwrite both

            mVideoId = mVideoInfo.id;
            mUri = mVideoInfo.uri;
            if (log.isDebugEnabled()) log.debug("setVideoInfo mVideoId: {}", mVideoId);

            if (!mCling && !TextUtils.isEmpty(mVideoInfo.title)) {
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
            viewType = VideoEffect.EFFECT_STEREO_MERGE;
        }

        if (viewMode == VideoEffect.NORMAL_2D_MODE)
            viewType = VideoEffect.EFFECT_NONE;

        if (log.isDebugEnabled()) log.debug("setVideoInfo: setEffect");
        setEffect(viewType, viewMode);

        /*
         * check if kindle apk (without lvl) run on amazon device
         * we need to com.archos.mediacenter.videoki in 2 strings because of
         * the mighty sed that s/com.archos.mediacenter.video/com.archos.mediacenter.videoki/
         */
        if (getPackageName().equals("com.archos.mediacenter"+"."+"video"+"ki")) {
            log.info("amazon?");
            if (Build.BRAND == null || !Build.BRAND.equalsIgnoreCase("a"+"m"+"a"+"z"+"o"+"n")) {
                myShowDialog(DIALOG_WRONG_DEVICE_KINDLE);
                return;
            }
        }

        mPlayerController.setVideoTitle(mTitle);
        if (log.isDebugEnabled()) log.debug("setVideoInfo: mTitle {}, call postVideoInfoAndPrepared", mTitle);
        postVideoInfoAndPrepared();
    }

    /**
     * set start state = removing progress + enabling controllers
     */
    private void  postVideoInfoAndPrepared() {
        if (log.isDebugEnabled()) log.debug("postVideoInfoAndPrepared mVideoInfo!= null && (PlayerState PREPARED || PLAYING)={}", String.valueOf((PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PREPARED||PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PLAYING) && mVideoInfo != null));
        // ex onStreamingUriOK
        if ((PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PREPARED||PlayerService.sPlayerService.mPlayerState == PlayerService.PlayerState.PLAYING) && mVideoInfo != null) {
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
        }
    }

    private boolean isStereoEffectOn() {
        return VideoEffect.isStereoEffectOn(mPlayer.getEffectType());
    }

    @Override
    public void onVideoDb(final VideoDbInfo localVideoInfo, final VideoDbInfo remoteVideoInfo) {
    }

    private void selectVideoInfo(VideoDbInfo videoInfo, PlayerService.ResumeSource resumeSource) {
        mVideoInfo = videoInfo;
        if (PlayerService.sPlayerService != null) {
            PlayerService.sPlayerService.setVideoInfo(mVideoInfo, resumeSource);
            PlayerService.sPlayerService.requestIndexAndScrap();
        }
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
        if (log.isDebugEnabled()) log.debug("start: {}", intent);
        if (mBufferView != null)
            mBufferView.setText("");
        mHandler.removeMessages(MSG_PROGRESS_VISIBLE);
        if(!Player.sPlayer.isPlaying())
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PROGRESS_VISIBLE), PROGRESS_VISIBLE_DELAY);

        if (mUri == null) {
            myShowDialog(DIALOG_ERROR);
            return;
        }

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
        if (log.isDebugEnabled()) log.debug("stop");

        mPlayer.pause(PlayerController.STATE_OTHER);

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
        VideoMetadata metadata = mPlayer.getVideoMetadata();
        if (metadata == null) return false;

        File file = metadata.getFile();
        return file != null && file.exists();
    }

    private int getBookmarkPosition() {
        if (mPlayer.getDuration() != 0) {
            /* resume a little before */
            int position = mPlayer.getCurrentPosition();
            return position > 3000 ? position - 1000 : 0;
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
            // Save the subtitle delay and ratio to the database for persistence across resume
            persistVideoInfo();
            if (log.isDebugEnabled()) log.debug("onDelayChange: saved subtitleDelay={} subtitleRatio={} to database", mVideoInfo.subtitleDelay, mVideoInfo.subtitleRatio);
        }
    }

    /* AudioDelayPickerDialog.OnAudioDelayChangeListener */
    public void onAudioDelayChange(AudioDelayPickerAbstract delayPicker, int delay) {
       PlayerService.sPlayerService.setAudioDelay(clampAudioDelayForPassthrough(delay), false);
    }

    /* AudioSpeedPickerDialog.OnAudioSpeedChangeListener */
    public void onAudioSpeedChange(AudioSpeedPickerAbstract speedPicker, float speed) {
        if (VideoPreferencesCommon.isAudioSpeedEnabled(mPreferences)) {
            if (log.isDebugEnabled()) log.debug("onAudioSpeedChange: setAudioSpeed {}", speed);
             PlayerService.sPlayerService.setAudioSpeed(speed, false);
        } else {
            if (log.isDebugEnabled()) log.debug("onAudioSpeedChange: DO NOT setAudioSpeed coz audio speed disabled");
        }
    }

    private void sendVideoStateChanged() {

        // mThumbnailDone is the state of the thread: 0: not started yet, 1: started, 2: done
        if (mThumbnailDone == 0) {
            if (log.isDebugEnabled()) log.debug("XXT", "starting new Thread");
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

    int positionToSubtitleTrack(int position, int nbTracks) { // nbTracks does not count none track
        // subtitleTracks are between 0<=track<=nbTrack and none track is nbTrack
        if (nbTracks <= 0) return 0; // to avoid division by 0
        if (position <= 0) return nbTracks; // position 0 is none track thus return nbTracks
        else return (position - 1) % (nbTracks + 1);
    }

    int positionToPlayerSubtitleTrack(int position, int nbTracks) { // nbTracks does not count none track
        if (nbTracks == 0) return -1; // to avoid division by 0
        if (position == 0) return -1; // position 0 is none track thus return -1 for the player
        else return (position - 1) % nbTracks;
    }

    int subtitleTrackToPosition(int subtitleTrack, int nbTracks) { // nbTracks does not count none track
        if (nbTracks == 0) return 0;
        // position is between 0<=position<=nbTrack with 0 is none track
        if (nbTracks >= 0) return (subtitleTrack + 1) % (nbTracks + 1);
        else return 0;
    }

    int nextSubtitleTrack(int subtitleTrack, int nbTracks) { // nbTracks does not count none track
        // subtitleTracks are between 0<=track<=nbTrack-1 and none track is -1
        // none track is covered in nextSubtitleTrack
        if (subtitleTrack == nbTracks) return -1; // none track
        else return (subtitleTrack + 1) % nbTracks;
    }

    int nextVideoInfoSubtitleTrack(int subtitleTrack, int nbTracks) { // nbTracks does not count none track
        if (nbTracks == 0) return 0; // to avoid division by 0
        // subtitleTracks are between 0<=track<=nbTrack and none track is nbTrack
        // none track is covered in nextSubtitleTrack
        return (subtitleTrack == nbTracks - 1) ? nbTracks : (subtitleTrack + 1) % (nbTracks + 1);
    }

    int nextPosition(int subtitleTrack, int nbTracks) { // nbTracks does not count none track
        // position is between 0<=position<=nbTrack with 0 is none track
        return ((subtitleTrack + 1) % (nbTracks + 1));
    }

    // 0<=subtitleTrack<=nbTracks and noneTrack=nbTracks for mVideoInfo with nvTracks out of reach
    // 0<=subtitlepostion<=nbTracks and noneTrack=0 for mSubtitleInfoController
    // warning: when addressing mPlayer.setSubtitleTrack none track is -1

    /* PlayerController.Settings */
    public void switchSubtitleTrack() { // switch to next subtitle track avoiding none
        if (mSubtitleInfoController.getTrackCount() > 1) {
            int newSubtitleTrack = nextVideoInfoSubtitleTrack(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles);
            int newSubtitlePosition = subtitleTrackToPosition(newSubtitleTrack, mVideoInfo.nbSubtitles);
            int playerPosition = positionToPlayerSubtitleTrack(newSubtitlePosition, mVideoInfo.nbSubtitles);
            if (log.isDebugEnabled()) log.debug("switchSubtitleTrack: {}/{} -> (track,position)=({},{}), playerPosition={}", mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles,
                    newSubtitleTrack, newSubtitlePosition, playerPosition);
            if (mPlayer.setSubtitleTrack(playerPosition)) {
                mVideoInfo.subtitleTrack = newSubtitleTrack;
                mSubtitleManager.clear();
                mSubtitleInfoController.setTrack(subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles)); // +1 since none track is at position 0, for UI only
                if (mSubtitleInfoController.getTrack() == 0) { // 0 is nonePosition
                    if (log.isDebugEnabled()) log.debug("switchSubtitleTrack: disableSubtitleDelayTVMenuItem(true) because nonePosition");
                    disableSubtitleDelayTVMenuItem(true);
                    disableSubtitleSettingsMenuItem(true);
                }
                refreshSubtitleTVMenu();
                CharSequence subTrackName = mSubtitleInfoController.getTrackNameAt(subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles));
                if (log.isDebugEnabled()) log.debug("switchSubtitleTrack: changed track={} -> {}", mVideoInfo.subtitleTrack, subTrackName);
                setSubtitleVpos("switchSubtitleTrack");
                mPlayerController.updateToast(getResources().getText(R.string.player_subtitle_track_toast) + " " + subTrackName);
            }
        }
        log.info("switchSubtitleTrack: " + mVideoInfo.subtitleTrack);
    }

    public void switchAudioTrack() {
        if (mAudioInfoController.getTrackCount() > 1) {
            int newAudioTrack = (mVideoInfo.audioTrack + 1) % mAudioInfoController.getTrackCount();
            if (log.isDebugEnabled()) log.debug("switchAudioTrack: circular increment from {} to  {}", mVideoInfo.audioTrack, newAudioTrack);
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

    public boolean isCurrentSubtrackGfx() {
        if (mPlayer == null || mPlayer.getVideoMetadata() == null || mVideoInfo == null ||
                mVideoInfo.subtitleTrack == -1 || mVideoInfo.subtitleTrack >= mVideoInfo.nbSubtitles) {
            return false;
        }
        VideoMetadata.SubtitleTrack sub = mPlayer.getVideoMetadata().getSubtitleTrack(mVideoInfo.subtitleTrack);
        return sub != null && sub.isGfx;
    }

    public boolean isCurrentSubtrackNone() {
        if (mPlayer == null || mPlayer.getVideoMetadata() == null || mVideoInfo == null ||
                mVideoInfo.subtitleTrack == -1) {
            return false;
        }
        return mVideoInfo.subtitleTrack >= mVideoInfo.nbSubtitles;
    }

    private void setSubtitleVpos(String caller) {
        setSubtitleVpos(PreferenceManager.getDefaultSharedPreferences(PlayerActivity.this).getInt(KEY_SUBTITLE_VPOS, mSubtitleVPosDefault), caller);
    }

    private void setSubtitleVpos(int vpos, String caller) {
        if (mVideoInfo == null || mVideoInfo.subtitleTrack == -1 || mVideoInfo.subtitleTrack >= mVideoInfo.nbSubtitles) return;
        VideoMetadata.SubtitleTrack subtitleTrack = mPlayer.getVideoMetadata().getSubtitleTrack(mVideoInfo.subtitleTrack);
        if (subtitleTrack != null && subtitleTrack.isGfx) {
            if (log.isDebugEnabled()) log.debug("{}: set vpos to 0, mVideoInfo={}", caller, ((mVideoInfo == null) ? "null" : "noNull" + ", subtitleTrack=" + ((mVideoInfo == null) ? "null" : mVideoInfo.subtitleTrack)));
            mSubtitleManager.setVerticalPosition(0);
            disableSubtitleSettingsMenuItem(true);
        } else {
            if (log.isDebugEnabled()) log.debug("{}: set vpos to {}, subtitleTrack={}", caller, vpos, mVideoInfo.subtitleTrack);
            mSubtitleManager.setVerticalPosition(vpos);
            disableSubtitleSettingsMenuItem(false);
        }
    }

    private void disableSubtitleDelayTVMenuItem(boolean disable) {
        if (log.isDebugEnabled()) log.debug("disableSubtitleDelayTVMenuItem: {}", disable);
        mSubtitleInfoController.enableSettings(SUBTITLE_MENU_DELAY, !disable, disable);
        if (mSubtitleDelayMenuItem != null) {
            mSubtitleDelayMenuItem.setDisabled(disable);
        }
    }

    private void disableSubtitleSettingsMenuItem(boolean disable) {
        if (log.isDebugEnabled()) log.debug("disableSubtitleSettingsMenuItem: {}", disable);
        mSubtitleInfoController.enableSettings(SUBTITLE_MENU_SETTINGS, !disable, disable);
        if (mSubtitleSettingsMenuItem != null) {
            mSubtitleSettingsMenuItem.setDisabled(disable);
        }
    }

    /* TrackInfoAdapter.OnTrackInfoListener */
    public boolean onTrackSelected(TrackInfoController trackInfoController, int position, CharSequence name,
            CharSequence summary) {
        boolean ret = false;
        if (mPlayer.isBusy())
            return false;
        log.info("onTrackSelected(" + position + "): " + name);
        if (Objects.equals(trackInfoController, mAudioInfoController)) {
            if (log.isDebugEnabled()) log.debug("onTrackSelected: position={}, mVideoInfo.audioTrack={}", position, mVideoInfo.audioTrack);
            AudioTrack at = mPlayer.getVideoMetadata().getAudioTrack(position);
            if (at != null && at.supported) {
                ret = setPlayerAudioTrack(position);
                if (ret) {
                    mVideoInfo.audioTrack = position;
                    // Save the audio track selection to the database for persistence across resume
                    persistVideoInfo();
                    if (log.isDebugEnabled()) log.debug("onTrackSelected: saved audioTrack {} to database", mVideoInfo.audioTrack);
                }
            } else if (at == null || !at.supported){
                mErrorMsg = (at != null) ? at.format : "";
                myShowDialog(DIALOG_CODEC_NOT_SUPPORTED);
            }
        } else if (Objects.equals(trackInfoController, mSubtitleInfoController)) {
            if (log.isDebugEnabled()) log.debug("onTrackSelected: position={}, mVideoInfo.subtitleTrack={}, mVideoInfo.nbSubtitles={}, subtitleTrackToPosition={}", position, mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles, subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles));
            if (position != subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles)) {
                if (log.isDebugEnabled()) log.debug("onTrackSelected: position={}, old mVideoInfo.subtitleTrack={}", position, mVideoInfo.subtitleTrack);
                ret = mPlayer.setSubtitleTrack(positionToPlayerSubtitleTrack(position, mVideoInfo.nbSubtitles));
                if (ret) {
                    mSubtitleManager.clear();
                    mVideoInfo.subtitleTrack = positionToSubtitleTrack(position, mVideoInfo.nbSubtitles);
                    if (log.isDebugEnabled()) log.debug("onTrackSelected: -> mVideoInfo.subtitleTrack={}", mVideoInfo.subtitleTrack);
                    // Extract and save the subtitle language for track validation on re-enumeration
                    if (mVideoInfo.subtitleTrack >= 0) {
                        SubtitleTrack track = mPlayer.getVideoMetadata().getSubtitleTrack(mVideoInfo.subtitleTrack);
                        if (track != null) {
                            String language = null;
                            if (track.isExternal) {
                                language = getSubLanguageFromSubPathAndVideoPath(getApplicationContext(), track.path, mUri.toString());
                            } else {
                                language = ISO639codes.getLanguageNameForLetterCode(track.language);
                            }
                            mVideoInfo.subtitleLanguage = extractLanguageCode(language).toLowerCase();
                            if (log.isDebugEnabled()) log.debug("onTrackSelected: saved subtitleLanguage={} for track {}", mVideoInfo.subtitleLanguage, mVideoInfo.subtitleTrack);
                        }
                    } else {
                        mVideoInfo.subtitleLanguage = null;
                    }
                    setSubtitleVpos("onTrackSelected");
                    // Save the subtitle track selection to the database for persistence across resume
                    persistVideoInfo();
                    if (log.isDebugEnabled()) log.debug("onTrackSelected: saved subtitleTrack {} to database", mVideoInfo.subtitleTrack);
                } else {
                    if (log.isDebugEnabled()) log.debug("onTrackSelected: player failed to get to subtitletrack {}", positionToSubtitleTrack(position, mVideoInfo.nbSubtitles));
                }
                if (mVideoInfo.subtitleTrack >= 0) {
                    String trackName = mSubtitleInfoController.getTrackNameAt(subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles)).toString();
                    disableSubtitleDelayTVMenuItem(position == 0);
                    disableSubtitleSettingsMenuItem(position == 0 || isCurrentSubtrackGfx());
                    if (log.isDebugEnabled()) log.debug("onTrackSelected: position={}, mSubtitleInfoController.getTrackNameAt({}) mVideoInfo.subtitleTrack={}", position, trackName, mVideoInfo.subtitleTrack);
                } else {
                    if (log.isDebugEnabled()) log.debug("onTrackSelected: position={}, None mVideoInfo.subtitleTrack={}", position, mVideoInfo.subtitleTrack);
                }
            }
        }
        return ret;
    }

    public boolean onSettingsSelected(TrackInfoController trackInfoController, int key, CharSequence name) {
        log.info("onSettingsSelected: " + key);
        if (mPlayer.isBusy())
            return false;
        if (trackInfoController == mSubtitleInfoController) {
            switch (key) {
                case SUBTITLE_MENU_DELAY:
                    myShowDialog(DIALOG_SUBTITLE_DELAY);
                    break;
                case SUBTITLE_MENU_SETTINGS:
                    myShowDialog(DIALOG_SUBTITLE_SETTINGS);
                    break;
                case SUBTITLE_MENU_DOWNLOAD:
                    downloadSubtitles();
                    break;
            }
        }
        return true;
    }

    private void onSubtitleResult() {
        if (log.isDebugEnabled()) log.debug("Get result from SubtitlesDownloaderActivity/SubtitlesWizardActivity");
        mPlayer.checkSubtitles();
    }

    private void downloadSubtitles() {
        Intent subIntent = new Intent(Intent.ACTION_MAIN);
        subIntent.setClass(mContext, SubtitlesDownloaderActivity2.class);
        subIntent.putExtra(SubtitlesDownloaderActivity2.FILE_URL, PlayerService.sPlayerService.getStreamingUri().toString());
        subtitleLauncher.launch(subIntent);
    }

    private void chooseSubtitles() {
        Intent subIntent = new Intent(Intent.ACTION_MAIN);
        Uri uri = VideoUtils.getFileUriFromMediaLibPath(mUri.toString());

        subIntent.setClass(mContext, SubtitlesWizardActivity.class);
        subIntent.setData(uri);
        subtitleLauncher.launch(subIntent);
    }

    private static boolean isGenericTextSubtitleFormat(String lang) {
        if (lang == null) return false;
        for (String format : GENERIC_TEXT_SUBTITLE_FORMATS) {
            if (format.equalsIgnoreCase(lang)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract 2-letter ISO 639-1 language code from a language name or code string.
     * Uses ISO639codes utility to handle conversions from full names or different code formats.
     * Returns lowercase 2-letter code or empty string if unable to extract.
     */
    private static String extractLanguageCode(String languageStr) {
        if (languageStr == null || languageStr.isEmpty()) {
            return "";
        }
        // Handle special cases for known subtitle formats (e.g. SRT, VTT)
        if (isGenericTextSubtitleFormat(languageStr)) {
            return languageStr.toLowerCase();
        }
        // Use ISO639codes utility to convert any format (2-letter, 3-letter, or full name) to 2-letter code
        String code = com.archos.mediacenter.utils.ISO639codes.getISO6391ForLetterCode(languageStr);
        if (code != null && !code.isEmpty()) {
            return code.toLowerCase();
        }
        // Fallback: if it's already a 2-letter code, use it
        if (languageStr.length() == 2 && !languageStr.contains(" ")) {
            return languageStr.toLowerCase();
        }
        // Last resort: return first 2 chars
        return languageStr.substring(0, Math.min(2, languageStr.length())).toLowerCase();
    }

    protected boolean forceExitOnTouch() {
        return mForceExitOnTouch;
    }

    private boolean setPlayerAudioTrack(int audioTrack) {
        if (log.isDebugEnabled()) log.debug("setPlayerAudioTrack: {}", audioTrack);
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

    // External player result reporting methods
    private void detectExternalPlayerMode() {
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            boolean returnResult = intent.getBooleanExtra("return_result", false);
            if (log.isDebugEnabled()) log.debug("detectExternalPlayerMode: action={}, return_result={}", action, returnResult);

            // Check if launched via ACTION_VIEW (typical for external player usage)
            if (Intent.ACTION_VIEW.equals(action)) {
                // Try to get calling package from multiple sources
                mCallingPackage = getCallingPackage();

                // API 22+: Try getReferrer() as fallback (more reliable than getCallingPackage)
                if (mCallingPackage == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Uri referrer = getReferrer();
                    if (referrer != null && "android-app".equals(referrer.getScheme())) {
                        mCallingPackage = referrer.getHost();
                        if (log.isDebugEnabled()) log.debug("detectExternalPlayerMode: Got calling package from referrer: {}", mCallingPackage);
                    }
                }

                // Check if we have external player indicators
                boolean hasExternalIndicators = returnResult
                        || ExternalResumeIntent.hasPositionExtra(intent);

                // Detect external player mode if:
                // 1. We have a calling package that's different from Nova, OR
                // 2. We have external player indicators (position extras, return_result, etc.)
                if ((mCallingPackage != null && !mCallingPackage.equals(getPackageName())) || hasExternalIndicators) {
                    mIsExternalPlayer = true;
                    if (log.isDebugEnabled()) log.debug("detectExternalPlayerMode: Nova launched as external player by {} (hasExternalIndicators={})", mCallingPackage, hasExternalIndicators);
                }
            }
        }
    }

    private void sendExternalPlayerResult() {
        if (log.isDebugEnabled()) log.debug("sendExternalPlayerResult called - mIsExternalPlayer={}, mResultSent={}", mIsExternalPlayer, mResultSent);
        if (!mIsExternalPlayer || mResultSent) {
            return;
        }
        mResultSent = true;

        Intent resultIntent = new Intent();
        // Set MX Player action - Stremio specifically recognizes this format
        resultIntent.setAction("com.mxtech.intent.result.VIEW");

        // Get current position - use last known position or current player position
        PlayerService.PlaybackSnapshot snapshot = PlayerService.sPlayerService != null
                ? PlayerService.sPlayerService.getPlaybackSnapshot()
                : null;
        int currentPosition;
        if (snapshot != null) {
            currentPosition = snapshot.getPositionMs();
        } else if (mPlayer != null && mPlayer.isInPlaybackState()) {
            currentPosition = mPlayer.getCurrentPosition();
        } else {
            currentPosition = 0;
        }

        // Get duration
        int duration = -1;
        if (snapshot != null && snapshot.getDurationMs() > 0) {
            duration = snapshot.getDurationMs();
        } else if (mVideoInfo != null && mVideoInfo.duration > 0) {
            duration = mVideoInfo.duration;
        } else if (mPlayer != null && mPlayer.isInPlaybackState()) {
            duration = mPlayer.getDuration();
        }

        if (duration <= 0) {
            duration = Math.max(currentPosition + 1000, 1000); // At least 1 second
        }

        // Add result extras in multiple formats for compatibility
        // Standard format (MX Player, Just Player compatible)
        resultIntent.putExtra("position", currentPosition);
        resultIntent.putExtra("duration", duration);

        // VLC format
        resultIntent.putExtra("extra_position", (long) currentPosition);
        resultIntent.putExtra("extra_duration", (long) duration);

        // Completion status
        if (mVideoFinished) {
            resultIntent.putExtra("end_by", "playback_completion");
            // Set position to duration for completed videos
            resultIntent.putExtra("position", duration);
            resultIntent.putExtra("extra_position", (long) duration);
        }

        if (log.isDebugEnabled()) log.debug("sendExternalPlayerResult: position={}, duration={}, finished={}, callingPackage={}, action={}", currentPosition, duration, mVideoFinished, mCallingPackage, resultIntent.getAction());


        setResult(Activity.RESULT_OK, resultIntent);

        // Also try sending a broadcast to the calling package as backup
        if (mCallingPackage != null) {
            try {
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction("com.external.player.result");
                broadcastIntent.setPackage(mCallingPackage);
                broadcastIntent.putExtras(resultIntent);
                sendBroadcast(broadcastIntent);
                if (log.isDebugEnabled()) log.debug("sendExternalPlayerResult: broadcast sent to {}", mCallingPackage);
            } catch (Exception e) {
                if (log.isDebugEnabled()) log.debug("sendExternalPlayerResult: broadcast failed: {}", e.getMessage());
            }
        }
    }

    private void finishWithResult() {
        sendExternalPlayerResult();
        finish();
    }

    @Override
    public void finish() {
        // Send result before finishing if we haven't already
        if (mIsExternalPlayer && !mResultSent) {
            if (log.isDebugEnabled()) log.debug("finish() called - sending result before finish");
            sendExternalPlayerResult();
        }
        super.finish();
    }

    /*
        TODO : not implement Player.Listener anymore

     */
    public class PlayerListener implements PlayerService.PlayerFrontend {

        public void onPrepared() {
            if (log.isDebugEnabled()) log.debug("onPrepared");
            mNetworkFailed = false;
            if (log.isDebugEnabled()) log.debug("onPrepared: call postVideoInfoAndPrepared");
            postVideoInfoAndPrepared();
        }

        public void onCompletion() {
            if (log.isDebugEnabled()) log.debug("onCompletion");

            PlayerService.sPlayerService.setAudioFilt();
        }

        public boolean onError(int errorCode, int errorQualCode, String msg) {
            if (isFinishing())
                return true;
            log.warn("onError: {}, {}", errorCode, errorQualCode);

            if (errorCode == IMediaPlayer.MEDIA_ERROR_VE_FILE_ERROR
                    && !mPlayer.isLocalVideo()
                    && !mNetworkFailed) {
                if (NetworkState.isNetworkConnected(mContext) && mVideoInfo != null) {
                    /* If we get a corrupted file error, if the file is from the network,
                     * and if we are still connected, try to reopen the video one time.
                     */
                    int retryPosition = PlayerService.sPlayerService != null
                            ? PlayerService.sPlayerService.prepareRetryFromCurrentPosition()
                            : getBookmarkPosition();
                    if (PlayerService.sPlayerService == null) {
                        mVideoInfo.resume = retryPosition;
                        mVideoInfo.duration = mPlayer.getDuration();
                    }
                    if (retryPosition != 0) {
                        mNetworkFailed = true;
                        stop();
                        mResumeFromLast = true;
                        start();
                        return true;
                    }
                }
            }
            stop();
            mErrorCode = errorCode;
            mErrorQualCode = errorQualCode;
            mErrorMsg = msg;

            stopDialog();
            if (mErrorCode == IMediaPlayer.MEDIA_ERROR_VE_VIDEO_NOT_SUPPORTED) {
                VideoTrack vt = mPlayer.getVideoMetadata().getVideoTrack();
                mErrorMsg = vt != null ? vt.format : "unknown";
                myShowDialog(DIALOG_CODEC_NOT_SUPPORTED);
            } else {
                myShowDialog(DIALOG_ERROR);
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

        public void onPlay(int state) {
            if (mSubtitleManager != null)
                mSubtitleManager.onPlay();
            sendVideoStateChanged();
            //mPlayerController.hide();

            // Set mPlayOnResume to true for user-initiated play (STATE_NORMAL)
            if (state == PlayerController.STATE_NORMAL && PlayerService.sPlayerService != null) {
                if (log.isDebugEnabled()) log.debug("onPlay: user initiated play, setting mPlayOnResume = true and mUserPausedVideo = false");
                mUserPausedVideo = false;
                PlayerService.sPlayerService.setPlayOnResume(true);
                // Clear the pause state preference
                mPreferences.edit()
                        .putBoolean(PlayerService.PREFERENCE_USER_PAUSED_VIDEO, false)
                        .remove(PlayerService.PREFERENCE_USER_PAUSED_URI)
                        .apply();
            }
        }
        public void onFirstPlay() {
            mPlayerController.hide();
        }
        public void onIntroDbReady() {
            refreshPlayModeIntroSummary();
        }
        public void onPause(int state) {

            if (mSubtitleManager != null)
                mSubtitleManager.onPause();
            sendVideoStateChanged();

            // Set mPlayOnResume to false for user-initiated pause (STATE_NORMAL)
            // so that video doesn't auto-play when screen turns back on
            if (state == PlayerController.STATE_NORMAL && PlayerService.sPlayerService != null) {
                if (log.isDebugEnabled()) log.debug("onPause: user paused, setting mPlayOnResume = false and mUserPausedVideo = true");
                mUserPausedVideo = true;
                PlayerService.sPlayerService.setPlayOnResume(false);
                // Save pause state to preferences to survive activity recreation
                mPreferences.edit()
                        .putBoolean(PlayerService.PREFERENCE_USER_PAUSED_VIDEO, true)
                        .putString(PlayerService.PREFERENCE_USER_PAUSED_URI,
                                mUri != null ? mUri.toString() : null)
                        .apply();
            }
        }

        public void onOSDUpdate() {
            if (mPlayerController != null)
                mPlayerController.setEnabled(true);
        }

        public void onVideoMetadataUpdated(VideoMetadata vMetadata) {
            if (isStereoEffectOn()) {
                int mode = vMetadata.getVideoTrack().s3dMode;
                if (mode != 0) {
                    if (log.isDebugEnabled()) log.debug("onVideoMetadataUpdated: setEffect");
                    setEffect (mode);
                }
            }
        }

        public void onAudioMetadataUpdated(VideoMetadata vMetadata, int newAudioTrack) {
            if (mVideoInfo == null) {
                mNewAudioTrack = newAudioTrack;
                mAudioSubtitleNeedUpdate = true;
                return;
            }

            // /!\ IMPORTANT: this is only for the UI part, setting the audio track is done in PlayerService thus the two must be in sync
            // thus DO NOT modify mVideoInfo.audioTrack here, only in PlayerService

            boolean firstTimeUpdated = mAudioInfoController.getTrackCount() == 0;
            int nbTrack = vMetadata.getAudioTrackNb();

            if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: newAudio: {}  mVideoInfo.audioTrack: {}  firstTimeUpdated: {}  nbTrack: {}", newAudioTrack, mVideoInfo.audioTrack, firstTimeUpdated, nbTrack);

            mAudioInfoController.clear();
            String trackName = null;
            for (int i = 0; i < nbTrack; ++i) {
                VideoMetadata.AudioTrack audio = vMetadata.getAudioTrack(i);
                if (audio == null)
                    continue;
                if (log.isDebugEnabled()) log.debug("onAudioMetadataUpdated: name={}, language={}, format={}, disposition={}", audio.name, audio.language, audio.format, audio.disposition);
                trackName = generateTrackName(mContext, audio.name, audio.language, audio.format, audio.disposition, true);
                CharSequence name = trackName;
                // when no name use track number instead of R.string.unknown_track_name th
                if (trackName.isEmpty())
                    name = getText(R.string.player_track) + " " + (i + 1);
                CharSequence summary = audio.format;
                mAudioInfoController.addTrack(name, summary, false);
            }
            mAudioInfoController.setTrack(mVideoInfo.audioTrack);
        }

        public void onSubtitleMetadataUpdated(VideoMetadata vMetadata, int newSubtitleTrack) {
            if (mVideoInfo == null) {
                mNewSubtitleTrack = newSubtitleTrack;
                mAudioSubtitleNeedUpdate = true;
                return;
            }

            // /!\ IMPORTANT: this is only for the UI part, setting the subtitle track is done in PlayerService thus the two must be in sync
            // thus DO NOT modify mVideoInfo.subtitleTrack here, only in PlayerService

            int nbTrack = vMetadata.getSubtitleTrackNb(); // it does not include none track

            final boolean firstTimeCalled = mSubtitleInfoController.getTrackCount() == 0;

            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: newSubtitle: {}, mVideoInfo.subtitleTrack: {}, firstTimeCalled: {}, nbTrack: {}", newSubtitleTrack, mVideoInfo.subtitleTrack, firstTimeCalled, nbTrack);

            mSubtitleInfoController.clear();

            int noneTrack = nbTrack; // none track is at position nbTrack even if displayed in menu as track 0
            int nonePosition = 0;

            if (nbTrack != 0) {
                mSubtitleInfoController.addTrack(getText(R.string.s_none), false); // first track displayed is none
                mVideoInfo.nbSubtitles = nbTrack; // nbSubtitles does not capture none track
                String lang = null;
                for (int i = 0; i < nbTrack; ++i) {
                    SubtitleTrack track = vMetadata.getSubtitleTrack(i);
                    if (track == null)
                        continue;
                    // name comes from IMediaPlayer (avos) and if not internal it says SRT/VTT generic, infer the name from path
                    // infer language from path if path is provided
                    if (track.isExternal) {
                        // external subtitle get name from file
                        lang = getSubLanguageFromSubPathAndVideoPath(mContext, track.path, vMetadata.getFile().getPath());
                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: extsub name={}, path={}, videoPath={}, isExternal={}, langFromPath={}", track.name, track.path, vMetadata.getFile().getPath(), track.isExternal, lang);
                        if (lang != null) {
                            if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: extsub name might not be null add track name with lang={}", lang);
                            mSubtitleInfoController.addTrack(lang, true);
                        } else { // this should never happen
                            log.warn("onSubtitleMetadataUpdated: extsub name and lang are null, add track name to unknown");
                            mSubtitleInfoController.addTrack(getText(R.string.unknown_track_name), true);
                        }
                    } else {
                        // internal subtitle get name from name
                        if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: intsub add track name with name={} replacing language code in {}, disposition={}", track.name, track.language, track.disposition);
                        String format = VideoUtils.getSubtitleFormatLabel(mContext, track.format);
                        mSubtitleInfoController.addTrack(generateTrackName(mContext, track.name, track.language, format, track.disposition, false), false);
                    }
                }
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
                setSubtitleVpos(vpos, "onSubtitleMetadataUpdated");
                mSubtitleManager.setOutlineState(outline);
                boolean background = preferences.getBoolean(KEY_SUBTITLE_BACKGROUND, false);
                int bgOpacity = preferences.getInt(KEY_SUBTITLE_BG_OPACITY, 128);
                mSubtitleManager.setBackgroundState(background);
                mSubtitleManager.setBackgroundOpacity(bgOpacity);
                // mVideoInfo.subtitleTrack is the track number with the none track 0<=mVideoInfo.subtitleTrack<=nbTrack, nbTrack for none track
                // but mSubtitleInfoController is the track number with the none track (i.e. nbTrack + 1) at position 0
                // at this point mVideoInfo.subtitleTrack is the track number to be used
                if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: set mSubtitleInfoController.setTrack: {}", subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles));
                mSubtitleInfoController.setTrack(subtitleTrackToPosition(mVideoInfo.subtitleTrack, mVideoInfo.nbSubtitles)); // +1 since none track is at position 0, for UI only
                if (mSubtitleInfoController.getTrack() == nonePosition) {
                    if (log.isDebugEnabled()) log.debug("onSubtitleMetadataUpdated: disableSubtitleDelayTVMenuItem(true) because nonePosition");
                    disableSubtitleDelayTVMenuItem(true);
                    disableSubtitleSettingsMenuItem(true);
                }
            }

            refreshSubtitleTVMenu();

            if (mPlayerController.isTVMenuDisplayed())
                mPlayerController.showTVMenu(true);
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
                myShowDialog(DIALOG_CODEC_NOT_SUPPORTED);
        }

        @Override
        public void onVideoDb(final VideoDbInfo localVideoInfo, final VideoDbInfo remoteVideoInfo) {
            if (log.isDebugEnabled()) log.debug("onVideoDb: localVideoInfo.subtitleTrack={}, remoteVideoInfo.subtitleTrack={}", ((localVideoInfo != null) ? localVideoInfo.subtitleTrack : "none"), ((remoteVideoInfo != null) ? remoteVideoInfo.subtitleTrack : "none"));
            if (log.isDebugEnabled()) log.debug("onVideoDb: trakt: {} local {}", localVideoInfo.traktResume, localVideoInfo.resume);
            if (log.isDebugEnabled()) log.debug("onVideoDb: localVideoInfo.lastTimePlayed: {}, remoteVideoInfo.lastTimePlayed: {}", ((localVideoInfo != null) ? localVideoInfo.lastTimePlayed : "none"), ((remoteVideoInfo != null) ? remoteVideoInfo.lastTimePlayed : "none"));
            if (localVideoInfo != null) {
                if (remoteVideoInfo != null) {
                    if (localVideoInfo.lastTimePlayed == 0 && remoteVideoInfo.audioTrack == -1) {
                        if (log.isDebugEnabled()) log.debug("onVideoDb: first play");
                        fileHasAlreadyPlayed = false;
                    } else fileHasAlreadyPlayed = true;
                } else {
                    if (localVideoInfo.lastTimePlayed == 0) {
                        if (log.isDebugEnabled()) log.debug("onVideoDb: first play");
                        fileHasAlreadyPlayed = false;
                    } else fileHasAlreadyPlayed = true;
                }
            } else fileHasAlreadyPlayed = false;
            if (localVideoInfo != null && PlayerService.sPlayerService != null) {
                int explicitPosition = PlayerService.sPlayerService.getResumeCandidate(PlayerService.ResumeSource.EXPLICIT);
                if (explicitPosition > 0) {
                    selectVideoInfo(localVideoInfo, PlayerService.ResumeSource.EXPLICIT);
                    return;
                }

                if (remoteVideoInfo != null && mResume != RESUME_NO && mResume != RESUME_FROM_LOCAL_POS) {
                    if (log.isDebugEnabled()) log.debug("hasRemoteVideoInfo");
                    // Don't show resume dialog if user explicitly paused the video
                    // Use mUserPausedVideo flag which is set by onPause listener and cleared by onPlay
                    if (log.isDebugEnabled()) log.debug("onVideoDb: mUserPausedVideo={}", mUserPausedVideo);

                    if (!mUserPausedVideo) {
                        int localLastPosition = PlayerService.sPlayerService.getResumeCandidate(PlayerService.ResumeSource.LOCAL);
                        int remoteLastPosition = PlayerService.sPlayerService.getResumeCandidate(PlayerService.ResumeSource.NETWORK);

                        if (localLastPosition != remoteLastPosition && remoteLastPosition > 0) {
                            //do not display dialog if remote position is the only available
                            if (localLastPosition <= 0) {
                                if (log.isDebugEnabled()) log.debug("use remoteVideoInfo");
                                selectVideoInfo(remoteVideoInfo, PlayerService.ResumeSource.NETWORK);

                            } else {
                                if(mResume ==  RESUME_FROM_REMOTE_POS){ //use only remote
                                    selectVideoInfo(remoteVideoInfo, PlayerService.ResumeSource.NETWORK);
                                }
                                else {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
                                    builder.setMessage(R.string.use_remote_resume)
                                            .setCancelable(false)
                                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    selectVideoInfo(remoteVideoInfo, PlayerService.ResumeSource.NETWORK);
                                                }
                                            })
                                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    selectVideoInfo(localVideoInfo, PlayerService.ResumeSource.LOCAL);
                                                }
                                    });
                                    AlertDialog alert = builder.create();
                                    alert.show();
                                    alert.getButton(DialogInterface.BUTTON_POSITIVE).requestFocus();
                                }
                            }
                            return;
                        }
                    } else {
                        if (log.isDebugEnabled()) log.debug("onVideoDb: player is paused, skipping resume dialog");
                    }
                }
            }

            // this provides the video info to the player based on localVideoInfo (keeping subtrack etc...)
            if (log.isDebugEnabled()) log.debug("onVideoDb: call setVideoInfo for playerActivity and playerService");
            selectVideoInfo(localVideoInfo, PlayerService.ResumeSource.NONE);
        }

        @Override
        public void setUri(Uri uri, Uri streamingUri){
            mUri = uri;
            mStreamingUri = streamingUri;
            final String scheme = mUri.getScheme();
            if (getIntent().getStringExtra("title") != null)
                mTitle = getIntent().getStringExtra("title");
            else if (scheme == null || !scheme.equals("content"))
                mTitle = FileUtils.getName(mUri);
            invalidateOptionsMenu();
        }

        @Override
        public void setVideoInfo(VideoDbInfo mVideoInfo) {
            if (log.isDebugEnabled()) log.debug("setVideoInfo {}", String.valueOf(mVideoInfo != null));
            PlayerActivity.this.setVideoInfo(mVideoInfo);
        }

        @Override
        public void onEnd() {
            mVideoFinished = true;
            finishWithResult();
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
            if (log.isDebugEnabled()) log.debug("CONFIG onSwitchVideoFormat: fmt={}, autoFmt={}", fmt, autoFmt);
            SharedPreferences.Editor editor = mPreferences.edit();
            editor.putString(KEY_PLAYER_FORMAT, String.valueOf(fmt));
            editor.putString(KEY_PLAYER_AUTO_FORMAT, String.valueOf(autoFmt));
            editor.apply(); // commit is blocking .. avoid
            // Update the subtitle layout when the video format changes
            mSubtitleManager.updateSubtitleLayout();
        }
    };

    public void switchMode(boolean tv) {
        // TODO Auto-generated method stub
        if (tv != isTVMode) {
            isTVMode = tv;
            invalidateOptionsMenu();
        }
    }

    public static int getScreenWidth() { return mScreenWidth; }
    public static int getScreenHeight() { return mScreenHeight; }

}
