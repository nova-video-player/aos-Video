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

package com.archos.mediacenter.video;


import static com.archos.filecorelibrary.FileUtils.getPermissions;
import static com.archos.filecorelibrary.FileUtils.hasPermission;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.archos.filecorelibrary.webdav.WebdavUtils;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.picasso.SmbRequestHandler;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.mediacenter.video.utils.OpenSubtitlesApiHelper;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.medialib.LibAvos;
import com.archos.mediaprovider.video.NetworkAutoRefresh;
import com.archos.mediaprovider.video.VideoStoreImportReceiver;
import com.archos.mediascraper.ScraperImage;
import com.squareup.picasso.Picasso;

import httpimage.FileSystemPersistence;
import httpimage.HttpImageManager;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroid;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomApplication extends Application {

    private static Logger log = null;

    private NetworkState networkState = null;
    private static boolean isNetworkStateRegistered = false;
    private static boolean isAppStateListenerAdded = false;
    private static boolean isVideStoreImportReceiverRegistered = false;
    private static boolean isNetworkStateListenerAdded = false;
    private static boolean isHDMIPlugReceiverRegistered = false;
    private static long hdmiAudioEncodingFlag = 0;
    private static long maxAudioChannelCount = 0;
    private static boolean hasHdmi = false;
    private static boolean isAudioPlugged = false;
    public static final long allHdmiAudioCodecs = 0b11111111111111111111111111111;
    private static boolean hasManageExternalStoragePermissionInManifest = false;
    public static boolean isManageExternalStoragePermissionInManifest() { return hasManageExternalStoragePermissionInManifest; }

    private static int [] novaVersionArray;
    private static int [] novaPreviousVersionArray;
    private static String novaLongVersion;
    private static String novaShortVersion;
    private static int novaVersionCode = -1;
    private static String novaVersionName;
    private static boolean novaUpdated = false;

    public static int[] getNovaVersionArray() { return novaVersionArray; }
    public static String getNovaLongVersion() { return novaLongVersion; }
    public static String getNovaShortVersion() { return novaShortVersion; }
    public static int getNovaVersionCode() { return novaVersionCode; }
    public static String getNovaVersionName() { return novaVersionName; }
    public static boolean isNovaUpdated() { return novaUpdated; }
    public static void clearUpdatedFlag(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean("app_updated", false).commit();
        novaUpdated = false;
    }

    // make the array android constant independent not to main sync with avos
    private final int AVOS_ENCODING_INVALID = 0;
    private final int AVOS_ENCODING_DEFAULT = 1;
    private final int AVOS_ENCODING_PCM_16BIT = 2;
    private final int AVOS_ENCODING_PCM_8BIT = 3;
    private final int AVOS_ENCODING_PCM_FLOAT = 4;
    private final int AVOS_ENCODING_AC3 = 5;
    private final int AVOS_ENCODING_E_AC3 = 6;
    private final int AVOS_ENCODING_DTS = 7;
    private final int AVOS_ENCODING_DTS_HD = 8;
    private final int AVOS_ENCODING_MP3 = 9;
    private final int AVOS_ENCODING_AAC_LC = 10;
    private final int AVOS_ENCODING_AAC_HE_V1= 11;
    private final int AVOS_ENCODING_AAC_HE_V2= 12;
    private final int AVOS_ENCODING_IEC61937 = 13;
    private final int AVOS_ENCODING_DOLBY_TRUEHD = 14;
    private final int AVOS_ENCODING_AAC_ELD = 15;
    private final int AVOS_ENCODING_AAC_XHE = 16;
    private final int AVOS_ENCODING_AC4 = 17;
    private final int AVOS_ENCODING_E_AC3_JOC = 18;
    private final int AVOS_ENCODING_DOLBY_MAT = 19;
    private final int AVOS_ENCODING_OPUS = 20;
    private final int AVOS_ENCODING_PCM_24BIT_PACKED = 21;
    private final int AVOS_ENCODING_PCM_32BIT = 22;
    private final int AVOS_ENCODING_MPEGH_BL_L3 = 23;
    private final int AVOS_ENCODING_MPEGH_BL_L4 = 24;
    private final int AVOS_ENCODING_MPEGH_LC_L3 = 25;
    private final int AVOS_ENCODING_MPEGH_LC_L4 = 26;
    private final int AVOS_ENCODING_DTS_UHD = 27;
    private final int AVOS_ENCODING_DRA = 28;

    public static long getHdmiAudioCodecsFlag() {
        return hdmiAudioEncodingFlag;
    }

    private static SambaDiscovery mSambaDiscovery = null;

    private PropertyChangeListener propertyChangeListener = null;

    private static VideoStoreImportReceiver videoStoreImportReceiver = new VideoStoreImportReceiver();

    private static JcifsUtils jcifsUtils = null;
    private static WebdavUtils webdavUtils = null;
    private static SmbjUtils smbjUtils = null;
    private static SshjUtils sshjUtils = null;
    private static FileUtilsQ fileUtilsQ = null;

    private static OpenSubtitlesApiHelper openSubtitlesApiHelper = null;

    private static Context mContext = null;

    public static Context getAppContext() {
        return CustomApplication.mContext;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (BuildConfig.ENABLE_BUG_REPORT) {
            SentryAndroid.init(this, options -> {
                options.setDsn(BuildConfig.SENTRY_DSN);
                options.setSampleRate(null);
                options.setDebug(false);
                });
        }
    }

    public static String BASEDIR;
    private boolean mAutoScraperActive;
    private HttpImageManager mHttpImageManager;

    public CustomApplication() {
        super();
        mAutoScraperActive = false;
    }

    public void setAutoScraperActive(boolean active) {
        mAutoScraperActive = active;
    }

    public boolean isAutoScraperActive() {
        return mAutoScraperActive;
    }

    // store latest video played on a global level
    private static long mLastVideoPlayerId = -42;
    private static Uri mLastVideoPlayedUri = null;
    public static void setLastVideoPlayedId(long videoId) { mLastVideoPlayerId = videoId; }
    public static long getLastVideoPlayedId() { return mLastVideoPlayerId; }
    public static void setLastVideoPlayedUri(Uri videoUri) { mLastVideoPlayedUri = videoUri; }
    public static Uri getLastVideoPlayedUri() { return mLastVideoPlayedUri; }
    public static void resetLastVideoPlayed() {
        setLastVideoPlayedUri(null);
        setLastVideoPlayedId(-42);
    }

    @Override
    public void onCreate() {
        /*
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectAll()
                .penaltyLog()
                .penaltyFlashScreen()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                //.penaltyDeath()
                .build());
        */

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build());
        }

        super.onCreate();
        // init application context to make it available to all static methods
        mContext = getApplicationContext();
        // must be done after context is available
        log = LoggerFactory.getLogger(CustomApplication.class);

        setupBouncyCastle();

        // must be done before sambaDiscovery otherwise no context for jcifs
        new Thread(() -> {
            // create instance of jcifsUtils in order to pass context and initial preference
            if (mContext == null) log.warn("onCreate: mContext null!!!");
            if (jcifsUtils == null) jcifsUtils = JcifsUtils.getInstance(mContext);
            if (webdavUtils == null) webdavUtils = WebdavUtils.getInstance(mContext);
            if (smbjUtils == null) smbjUtils = smbjUtils.getInstance(mContext);
            if (sshjUtils == null) sshjUtils = sshjUtils.getInstance(mContext);
            if (fileUtilsQ == null) fileUtilsQ = FileUtilsQ.getInstance(mContext);
        }).start();

        Trakt.initApiKeys(this);
        new Thread() {
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                LibAvos.initAsync(mContext);
            };
        }.start();

        // Initialize picasso thumbnail extension
        Picasso.setSingletonInstance(
                new Picasso.Builder(mContext)
                        .addRequestHandler(new ThumbnailRequestHandler(mContext))
                        .addRequestHandler(new SmbRequestHandler(mContext))
                        .build()
        );

        // Set the dimension of the posters to save
        ScraperImage.setGeneralPosterSize(
                getResources().getDimensionPixelSize(R.dimen.details_poster_width),
                getResources().getDimensionPixelSize(R.dimen.details_poster_height));

        BASEDIR = Environment.getExternalStorageDirectory().getPath()+"Android/data/"+getPackageName();

        // Class that keeps track of activities so we can tell is we are foreground
        log.debug("onCreate: registerActivityLifecycleCallbacks AppState");
        registerActivityLifecycleCallbacks(AppState.sCallbackHandler);

        // NetworkState.(un)registerNetworkCallback following AppState
        if (!isAppStateListenerAdded) {
            log.debug("addListener: AppState.addOnForeGroundListener");
            AppState.addOnForeGroundListener(sForeGroundListener);
            isAppStateListenerAdded = true;
        }
        handleForeGround(AppState.isForeGround());

        // handles NetworkState changes
        networkState = NetworkState.instance(mContext);
        if (propertyChangeListener == null)
            propertyChangeListener = evt -> {
                if (evt.getOldValue() != evt.getNewValue()) {
                    log.trace("NetworkState for " + evt.getPropertyName() + " changed:" + evt.getOldValue() + " -> " + evt.getNewValue());
                    launchSambaDiscovery();
                }
            };

        launchSambaDiscovery();

        // init HttpImageManager manager.
        mHttpImageManager = new HttpImageManager(HttpImageManager.createDefaultMemoryCache(), 
                new FileSystemPersistence(BASEDIR));

        // Note: we do not init UPnP here, we wait for the user to enter the network view
        log.debug("onCreate: TraktService.init");
        TraktService.init();

        NetworkAutoRefresh.init();
        //init credentials db
        NetworkCredentialsDatabase.getInstance().loadCredentials(this);
        ArchosUtils.setGlobalContext(this.getApplicationContext());
        // only launch BootupRecommandation if on AndroidTV and before Android O otherwise target TV channels
        if(ArchosFeatures.isAndroidTV(this) && Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            BootupRecommandationService.init();

        log.trace("onCreate: manifest permissions " + Arrays.toString(getPermissions(mContext)));
        hasManageExternalStoragePermissionInManifest = hasPermission("android.permission.MANAGE_EXTERNAL_STORAGE", mContext);
        log.trace("onCreate: has permission android.permission.MANAGE_EXTERNAL_STORAGE " + hasManageExternalStoragePermissionInManifest);

        updateVersionState(this);
        if (openSubtitlesApiHelper == null) openSubtitlesApiHelper = OpenSubtitlesApiHelper.getInstance();
        //makeUseOpenSubtitlesRestApi(PreferenceManager.getDefaultSharedPreferences(this).getBoolean(VideoPreferencesCommon.KEY_OPENSUBTITILES_REST_API, true));
    }

    private void launchSambaDiscovery() {
        if (networkState.hasLocalConnection()) {
            log.debug("launchSambaDiscovery: local connection, launching samba discovery");
            // samba discovery should not be running at this stage, but better safe than sorry
            if (mSambaDiscovery != null) {
                mSambaDiscovery.abort();
            }
            SambaDiscovery.flushShareNameResolver();
            mSambaDiscovery = new SambaDiscovery(mContext);
            mSambaDiscovery.setMinimumUpdatePeriodInMs(0);
            mSambaDiscovery.start();
        } else
            log.debug("launchSambaDiscovery: no local connection, doing nothing");
    }

    public static SambaDiscovery getSambaDiscovery() {
        return mSambaDiscovery;
    }

    // link networkState register/unregister networkCallback linked to app foreground/background lifecycle
    private final AppState.OnForeGroundListener sForeGroundListener = (applicationContext, foreground) -> {
        handleForeGround(foreground);
    };

    protected void handleForeGround(boolean foreground) {
        log.debug("handleForeGround: is app foreground " + foreground);
        if (networkState == null ) networkState = NetworkState.instance(mContext);
        if (foreground) {
            registerHdmiAudioPlugReceiver();
            if (!isVideStoreImportReceiverRegistered) {
                log.debug("handleForeGround: app now in ForeGround registerReceiver for videoStoreImportReceiver");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "CustomApplication.handleForeGround", "app now in ForeGround registerReceiver for videoStoreImportReceiver");
                // programmatically register android scanner finished, lifecycle is handled in handleForeGround
                final IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
                intentFilter.addDataScheme("file");
                registerReceiver(videoStoreImportReceiver, intentFilter);
                isVideStoreImportReceiverRegistered = true;
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "CustomApplication.handleForeGround", "app now in ForeGround register videoStoreImportReceiver");
            } else {
                log.debug("handleForeGround: app now in ForeGround registerReceiver videoStoreImportReceiver already registered");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "CustomApplication.handleForeGround", "app now in ForeGround videoStoreImportReceiver already registered");
            }
            if (!isNetworkStateRegistered) {
                log.debug("handleForeGround: app now in ForeGround NetworkState.registerNetworkCallback");
                networkState.registerNetworkCallback();
                isNetworkStateRegistered = true;
            }
            addNetworkListener();
            launchSambaDiscovery();
        } else {
            unRegisterHdmiAudioPlugReceiver();
            if (isVideStoreImportReceiverRegistered) {
                log.debug("handleForeGround: app now in BackGround unregisterReceiver for videoStoreImportReceiver");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "CustomApplication.handleForeGround", "app now in Background unregister videoStoreImportReceiver");
                unregisterReceiver(videoStoreImportReceiver);
                isVideStoreImportReceiverRegistered = false;
            } else {
                log.debug("handleForeGround: app now in BackGround, videoStoreImportReceiver already unregistered");
                ArchosUtils.addBreadcrumb(SentryLevel.INFO, "CustomApplication.handleForeGround", "app now in Background videoStoreImportReceiver already unregistered");
            }
            if (isNetworkStateRegistered) {
                log.debug("handleForeGround: app now in BackGround NetworkState.unRegisterNetworkCallback");
                networkState.unRegisterNetworkCallback();
                isNetworkStateRegistered = false;
            }
            removeNetworkListener();
        }
    }

    private void registerHdmiAudioPlugReceiver() {
        final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        registerReceiver(mHdmiAudioPlugReceiver, intentFilter);
        isHDMIPlugReceiverRegistered = true;
    }

    private void unRegisterHdmiAudioPlugReceiver() {
        if (isHDMIPlugReceiverRegistered) unregisterReceiver(mHdmiAudioPlugReceiver);
        isHDMIPlugReceiverRegistered = false;
    }

    private final BroadcastReceiver mHdmiAudioPlugReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;
            if (action.equalsIgnoreCase(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                hasHdmi = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1;
                hdmiAudioEncodingFlag = !hasHdmi ? 0 : getEncodingFlags(intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS));
                final Integer isAudioPlugged = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0);
                if (isAudioPlugged != null) {
                    // maxAudioChannelCount not exploited for now
                    if (isAudioPlugged == 1) {
                        maxAudioChannelCount = intent.getIntExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, 2);
                    }
                }
                log.debug("mHdmiAudioPlugReceiver: received ACTION_HDMI_AUDIO_PLUG, isAudioPlugged=" + isAudioPlugged + ", hasHdmi=" + hasHdmi + ", maxAudioChannelCount=" + maxAudioChannelCount + ", hdmiAudioEncodingFlag=" + hdmiAudioEncodingFlag);
            }
        }
    };

    public static String[] audioEncodings = new String[] {"INVALID", "DEFAULT", "PCM_16BIT", "PCM_8BIT", "PCM_FLOAT",
            "AC3", "E_AC3", "DTS", "DTS_HD",
            "MP3", "AAC_LC", "AAC_HE_V1", "AAC_HE_V2",
            "IEC61937", "DOLBY_TRUEHD", "AAC_ELD", "AAC_XHE",
            "AC4", "E_AC3_JOC", "DOLBY_MAT", "OPUS",
            "PCM_24BIT_PACKED", "PCM_32BIT", "MPEGH_BL_L3", "MPEGH_BL_L4",
            "MPEGH_LC_L3", "MPEGH_LC_L4", "DTS_UHD", "DRA"
    };

    // provides correspondence between avos codec index and android one and yields to android independent reference and code sync inssues with native part
    public static List<Integer> convertAudioEncodings = new ArrayList<Integer>(Arrays.asList(
            AudioFormat.ENCODING_INVALID, AudioFormat.ENCODING_DEFAULT, AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_AC3,
            AudioFormat.ENCODING_E_AC3, AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD,
            AudioFormat.ENCODING_MP3, AudioFormat.ENCODING_AAC_LC, AudioFormat.ENCODING_AAC_HE_V1,
            AudioFormat.ENCODING_AAC_HE_V2, AudioFormat.ENCODING_IEC61937, AudioFormat.ENCODING_DOLBY_TRUEHD,
            AudioFormat.ENCODING_AAC_ELD, AudioFormat.ENCODING_AAC_XHE, AudioFormat.ENCODING_AC4,
            AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.ENCODING_DOLBY_MAT, AudioFormat.ENCODING_OPUS,
            AudioFormat.ENCODING_PCM_24BIT_PACKED, AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_MPEGH_BL_L3,
            AudioFormat.ENCODING_MPEGH_BL_L4, AudioFormat.ENCODING_MPEGH_LC_L3, AudioFormat.ENCODING_MPEGH_LC_L4,
            AudioFormat.ENCODING_DTS_UHD, AudioFormat.ENCODING_DRA));

    private boolean isEncoded(int encoding) {
        switch (encoding) {
            case AVOS_ENCODING_PCM_16BIT:        // 2
            case AVOS_ENCODING_PCM_8BIT:         // 3
            case AVOS_ENCODING_PCM_FLOAT:        // 4
            case AVOS_ENCODING_AC3:              // 5
            case AVOS_ENCODING_E_AC3:            // 6
            case AVOS_ENCODING_DTS:              // 7
            case AVOS_ENCODING_DTS_HD:           // 8
            case AVOS_ENCODING_MP3:              // 9
            case AVOS_ENCODING_AAC_LC:           // 10
            case AVOS_ENCODING_AAC_HE_V1:        // 11
            case AVOS_ENCODING_AAC_HE_V2:        // 12
            case AVOS_ENCODING_IEC61937:         // 13
            case AVOS_ENCODING_DOLBY_TRUEHD:     // 14
            case AVOS_ENCODING_AAC_ELD:          // 15
            case AVOS_ENCODING_AAC_XHE:          // 16
            case AVOS_ENCODING_AC4:              // 17
            case AVOS_ENCODING_E_AC3_JOC:        // 18
            case AVOS_ENCODING_DOLBY_MAT:        // 19
            case AVOS_ENCODING_OPUS:             // 20
            case AVOS_ENCODING_PCM_24BIT_PACKED: // 21
            case AVOS_ENCODING_PCM_32BIT:        // 22
            case AVOS_ENCODING_MPEGH_BL_L3:      // 23
            case AVOS_ENCODING_MPEGH_BL_L4:      // 24
            case AVOS_ENCODING_MPEGH_LC_L3:      // 25
            case AVOS_ENCODING_MPEGH_LC_L4:      // 26
            case AVOS_ENCODING_DTS_UHD:          // 27
            case AVOS_ENCODING_DRA:              // 28
                log.debug("isEncoded: hdmi RX supports " + audioEncodings[encoding]);
                return true;
            default:
                log.warn("isEncoded: not identified audio encoding " + encoding + "!!!");
                return false;
        }
    }

    private long getEncodingFlags(int encodings[]) {
        if (encodings == null)
            return 0;
        long encodingFlags = 0;
        for (int encoding : encodings) {
            // convert android codec index to android avos independent one (should be the same though)
            int avosEncoding = convertAudioEncodings.indexOf(encoding);
            log.debug("getEncodingFlags: android domain " + encoding + ", avos domain " + avosEncoding);
            if (isEncoded(avosEncoding))
                encodingFlags |= 1 << avosEncoding;
        }
        log.debug("getEncodingFlags: encodings=" + Arrays.toString(encodings) + ", convertAudioEncodings=" + Arrays.toString(convertAudioEncodings.toArray()) + ", encodingFlags=" + encodingFlags + ", allHdmiAudioCodecs=" + allHdmiAudioCodecs);
        return encodingFlags;
    }

    private void addNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(mContext);
        if (!isNetworkStateListenerAdded && propertyChangeListener != null) {
            log.trace("addNetworkListener: networkState.addPropertyChangeListener");
            networkState.addPropertyChangeListener(propertyChangeListener);
            isNetworkStateListenerAdded = true;
        }
    }

    private void removeNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(mContext);
        if (isNetworkStateListenerAdded && propertyChangeListener != null) {
            log.trace("removeListener: networkState.removePropertyChangeListener");
            networkState.removePropertyChangeListener(propertyChangeListener);
            isNetworkStateListenerAdded = false;
        }
    }

    public HttpImageManager getHttpImageManager() {
        return mHttpImageManager;
    }

    private static void updateVersionState(Context context) {
        try {
            //this code gets current version-code (after upgrade it will show new versionCode)
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            novaVersionCode = info.versionCode;
            novaVersionName = info.versionName;
            try {
                novaVersionArray = splitVersion(novaVersionName);
                novaLongVersion = "Nova v" + novaVersionArray[0] + "." + novaVersionArray[1] + "." + novaVersionArray[2] +
                        " (" + novaVersionArray[3] + String.format("%02d", novaVersionArray[4]) + String.format("%02d", novaVersionArray[5]) +
                        "." + String.format("%02d", novaVersionArray[6]) + String.format("%02d", novaVersionArray[7]) + ")";
                novaShortVersion = "v" + novaVersionArray[0] + "." + novaVersionArray[1] + "." + novaVersionArray[2];
            } catch (IllegalArgumentException ie) {
                novaVersionArray = new int[] { 0, 0, 0, 0, 0, 0, 0, 0};
                log.error("updateVersionState: cannot split application version "+ novaVersionName);
                novaLongVersion = "Nova v" + novaVersionName;
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            int previousVersion = sharedPreferences.getInt("current_versionCode", -1);
            sharedPreferences.edit().putString("nova_version", novaLongVersion).apply();
            String previousVersionName = sharedPreferences.getString("current_versionName", "0.0.0");
            try {
                novaPreviousVersionArray = splitVersion(previousVersionName);
            } catch (IllegalArgumentException ie) {
                novaPreviousVersionArray = new int[] { 0, 0, 0, 0, 0, 0, 0, 0};
                log.error("updateVersionState: cannot split application previous version "+ previousVersionName);
            }
            if (previousVersion > 0) {
                if (previousVersion != novaVersionCode) {
                    // got upgraded, save version in current_versionCode and remember former version in previous_versionCode
                    // and indicated that we got updated in app_updated until used and reset
                    sharedPreferences.edit().putInt("current_versionCode", novaVersionCode).commit();
                    sharedPreferences.edit().putInt("previous_versionCode", previousVersion).commit();
                    novaUpdated = true;
                    sharedPreferences.edit().putBoolean("app_updated", true).commit();
                    sharedPreferences.edit().putString("current_versionName", novaVersionName).commit();
                    sharedPreferences.edit().putString("previous_versionName", previousVersionName).commit();
                    log.debug("updateVersionState: update from " + previousVersionName + "(" + previousVersion + ") to "
                            + novaVersionName + "(" + novaVersionCode + ")");
                }
            } else {
                // save first app version
                log.debug("updateVersionState: save first version " + novaVersionCode);
                sharedPreferences.edit().putInt("current_versionCode", novaVersionCode).commit();
                sharedPreferences.edit().putInt("previous_versionCode", -1).commit();
                sharedPreferences.edit().putString("current_versionName", novaVersionName).commit();
                sharedPreferences.edit().putString("previous_versionName", "0.0.0").commit();
            }
        } catch (PackageManager.NameNotFoundException e) {
            log.error("updateVersionState: caught NameNotFoundException", e);
        }
    }

    // takes version major.minor.revision-YYYYMMDD.HHMMSS and convert it into an integer array
    static int[] splitVersion(String version) throws IllegalArgumentException {
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)-(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)\\.(\\d\\d)(\\d\\d)?").matcher(version);
        if (!m.matches())
            throw new IllegalArgumentException("Malformed application version");
        return new int[] {
                Integer.parseInt(m.group(1)), // major
                Integer.parseInt(m.group(2)), // minor
                Integer.parseInt(m.group(3)), // version
                Integer.parseInt(m.group(4)), // year
                Integer.parseInt(m.group(5)), // month
                Integer.parseInt(m.group(6)), // day
                Integer.parseInt(m.group(7)), // hour
                Integer.parseInt(m.group(8))  // minute
        };
    }

    public static String getChangelog(Context context) {
        log.debug("getChangelog: " + novaPreviousVersionArray[0] + "->" + novaVersionArray[0]);
        if (novaPreviousVersionArray[0] > 0 && novaPreviousVersionArray[0] <= 5 && novaVersionArray[0] > 5)
            return context.getResources().getString(R.string.v5_v6_upgrade_info);
        else return null;
    }

    public static void showChangelogDialog(String changelog, final Activity activity) {
        if (changelog == null) {
            clearUpdatedFlag(activity);
            return;
        } else {
            log.debug("showChangelogDialog: changelog is null, nothing to do.");
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.upgrade_info)
            .setMessage(changelog)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    clearUpdatedFlag(activity);
                    dialog.cancel();
                    updateVersionState(activity); // be sure not to display twice
                }
            })
            .show();
    }

    private void setupBouncyCastle() {
        final Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            return;
        }
        if (provider.getClass().equals(BouncyCastleProvider.class)) {
            // BC with same package name, shouldn't happen in real life.
            return;
        }
        // Android registers its own BC provider. As it might be outdated and might not include
        // all needed ciphers, we substitute it with a known BC bundled in the app.
        // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
        // of that it's possible to have another BC implementation loaded in VM.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }
}
