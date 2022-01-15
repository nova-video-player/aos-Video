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


import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.picasso.SmbRequestHandler;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.medialib.LibAvos;
import com.archos.mediaprovider.video.NetworkAutoRefresh;
import com.archos.mediaprovider.video.RemoteStateService;
import com.archos.mediaprovider.video.VideoStoreImportReceiver;
import com.archos.mediascraper.ScraperImage;
import com.squareup.picasso.Picasso;


import httpimage.FileSystemPersistence;
import httpimage.HttpImageManager;

import org.acra.*;
import org.acra.annotation.*;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.HttpSenderConfigurationBuilder;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomApplication extends Application {

    private static Logger log = null;

    private NetworkState networkState = null;
    private static boolean isNetworkStateRegistered = false;
    private static boolean isAppStateListenerAdded = false;
    private static boolean isVideStoreImportReceiverRegistered = false;
    private static boolean isNetworkStateListenerAdded = false;

    private static int [] novaVersionArray;
    private static int [] novaPreviousVersionArray;
    private static String novaLongVersion;
    private static int novaVersionCode = -1;
    private static String novaVersionName;
    private static boolean novaUpdated = false;

    public static int[] getNovaVersionArray() { return novaVersionArray; }
    public static String getNovaLongVersion() { return novaLongVersion; }
    public static int getNovaVersionCode() { return novaVersionCode; }
    public static String getNovaVersionName() { return novaVersionName; }
    public static boolean isNovaUpdated() { return novaUpdated; }
    public static void clearUpdatedFlag(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean("app_updated", false).commit();
        novaUpdated = false;
    }

    private static SambaDiscovery mSambaDiscovery = null;

    private PropertyChangeListener propertyChangeListener = null;

    private static VideoStoreImportReceiver videoStoreImportReceiver = new VideoStoreImportReceiver();
    final static IntentFilter intentFilter = new IntentFilter();

    private JcifsUtils jcifsUtils = null;
    private FileUtilsQ fileUtilsQ = null;

    private static Context mContext = null;

    public static Context getAppContext() {
        return CustomApplication.mContext;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // The following line triggers the initialization of ACRA
        CoreConfigurationBuilder builder = new CoreConfigurationBuilder(this);
        builder.withBuildConfigClass(BuildConfig.class).withReportFormat(StringFormat.JSON);
        builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder.class)
                .withUri("https://bug.courville.org/report")
                .withBasicAuthLogin("1HrXuNtb1JAtflJu")
                .withBasicAuthPassword("tdCgove1nfdEVTY6")
                .withHttpMethod(HttpSender.Method.POST)
                .withEnabled(true);
        ACRA.init(this, builder);
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

        // must be done before sambaDiscovery otherwise no context for jcifs
        new Thread(() -> {
            // create instance of jcifsUtils in order to pass context and initial preference
            if (mContext == null) log.warn("onCreate: mContext null!!!");
            if (jcifsUtils == null) jcifsUtils = JcifsUtils.getInstance(mContext);
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

        // programmatically register android scanner finished, lifecycle is handled in handleForeGround
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addDataScheme("file");

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

        updateVersionState();
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

    // link networkState register/unregister networkCallback linked to app foreground/background lifecycle
    private final AppState.OnForeGroundListener sForeGroundListener = (applicationContext, foreground) -> {
        handleForeGround(foreground);
    };

    protected void handleForeGround(boolean foreground) {
        log.debug("handleForeGround: is app foreground " + foreground);
        if (networkState == null ) networkState = NetworkState.instance(mContext);
        if (foreground) {
            if (!isVideStoreImportReceiverRegistered) {
                log.debug("handleForeGround: app now in ForeGround registerReceiver for videoStoreImportReceiver");
                registerReceiver(videoStoreImportReceiver, intentFilter);
                isVideStoreImportReceiverRegistered = true;
            }
            if (!isNetworkStateRegistered) {
                log.debug("handleForeGround: app now in ForeGround NetworkState.registerNetworkCallback");
                networkState.registerNetworkCallback();
                isNetworkStateRegistered = true;
            }
            addNetworkListener();
            launchSambaDiscovery();
        } else {
            if (isVideStoreImportReceiverRegistered) {
                log.debug("handleForeGround: app now in ForeGround registerReceiver for videoStoreImportReceiver");
                unregisterReceiver(videoStoreImportReceiver);
                isVideStoreImportReceiverRegistered = false;
            }
            if (isNetworkStateRegistered) {
                log.debug("handleForeGround: app now in BackGround NetworkState.unRegisterNetworkCallback");
                networkState.unRegisterNetworkCallback();
                isNetworkStateRegistered = false;
            }
            removeNetworkListener();
        }
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

    private void updateVersionState() {
        try {
            //this code gets current version-code (after upgrade it will show new versionCode)
            PackageInfo info = this.getPackageManager().getPackageInfo(this.getPackageName(), 0);
            novaVersionCode = info.versionCode;
            novaVersionName = info.versionName;
            try {
                novaVersionArray = splitVersion(novaVersionName);
                novaLongVersion = "Nova v" + novaVersionArray[0] + "." + novaVersionArray[1] + "." + novaVersionArray[2] +
                        " (" + novaVersionArray[3] + novaVersionArray[4] + novaVersionArray[5] +
                        "." + novaVersionArray[6] + novaVersionArray[7] + ")";
            } catch (IllegalArgumentException ie) {
                log.error("updateVersionState: cannot split application version "+ novaVersionName);
                novaLongVersion = "Nova v" + novaVersionName;
            }
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            int previousVersion = sharedPreferences.getInt("current_versionCode", -1);
            sharedPreferences.edit().putString("nova_version", novaLongVersion).commit();
            String previousVersionName = sharedPreferences.getString("current_versionName", "0.0.0");
            try {
                novaPreviousVersionArray = splitVersion(previousVersionName);
            } catch (IllegalArgumentException ie) {
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
        if (novaPreviousVersionArray[0] == 5 && novaVersionArray[0] == 6)
            return context.getResources().getString(R.string.v5_v6_upgrade_info);
        else return null;
    }

    public static void showChangelogDialog(String changelog, final Activity activity) {
        if (changelog == null) return;
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.upgrade_info)
            .setMessage(changelog)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    clearUpdatedFlag(activity);
                    dialog.cancel();
                }
            })
            .show();
    }
}
