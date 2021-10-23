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


import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;

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
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


@AcraCore(reportFormat = StringFormat.JSON)
@AcraHttpSender(uri = "https://bug.courville.org/report",
        basicAuthLogin = "1HrXuNtb1JAtflJu",
        basicAuthPassword = "tdCgove1nfdEVTY6",
        httpMethod = HttpSender.Method.POST)

public class CustomApplication extends Application {

    private static Logger log = null;

    private NetworkState networkState = null;
    private static boolean isNetworkStateRegistered = false;
    private static boolean isAppStateListenerAdded = false;
    private static boolean isVideStoreImportReceiverRegistered = false;
    private static boolean isNetworkStateListenerAdded = false;

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
        ACRA.init(this);
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
}
