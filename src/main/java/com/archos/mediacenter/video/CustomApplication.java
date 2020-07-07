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
import android.os.Build;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.utils.AppState;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.browser.BootupRecommandationService;
import com.archos.mediacenter.video.picasso.SmbRequestHandler;
import com.archos.mediacenter.video.picasso.ThumbnailRequestHandler;
import com.archos.medialib.LibAvos;
import com.archos.mediaprovider.video.NetworkAutoRefresh;
import com.archos.mediaprovider.video.VideoStoreImportReceiver;
import com.archos.mediascraper.ScraperImage;
import com.squareup.picasso.Picasso;


import httpimage.FileSystemPersistence;
import httpimage.HttpImageManager;

import org.acra.*;
import org.acra.annotation.*;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

@AcraCore(reportFormat = StringFormat.JSON)
@AcraHttpSender(uri = "https://home.courville.org/acrarium/report",
        basicAuthLogin = "VAwdfjf9p9IhfYAl",
        basicAuthPassword = "Dr65wv2sy94hAaGH",
        httpMethod = HttpSender.Method.POST)

public class CustomApplication extends Application {

    private static final String TAG = "CustomApplication";
    private static final boolean DBG = false;

    private NetworkState networkState = null;
    private static boolean isNetworkStateRegistered = false;
    private static boolean isAppStateListenerAdded = false;
    private static boolean isVideStoreImportReceiverRegistered = false;

    private static VideoStoreImportReceiver videoStoreImportReceiver = new VideoStoreImportReceiver();
    final static IntentFilter intentFilter = new IntentFilter();

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // The following line triggers the initialization of ACRA
        //ACRA.init(this);
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

    @Override
    public void onCreate() {
        /* if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 16) {
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
        } */

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
        Trakt.initApiKeys(this);
        new Thread() {
            public void run() {
                this.setPriority(Thread.MIN_PRIORITY);
                LibAvos.initAsync(getApplicationContext());
            };
        }.start();

        // Initialize picasso thumbnail extension
        Picasso.setSingletonInstance(
                new Picasso.Builder(getApplicationContext())
                        .addRequestHandler(new ThumbnailRequestHandler(getApplicationContext()))
                        .addRequestHandler(new SmbRequestHandler(getApplicationContext()))
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
        if (DBG) Log.d(TAG, "onCreate: registerActivityLifecycleCallbacks AppState");
        registerActivityLifecycleCallbacks(AppState.sCallbackHandler);

        // NetworkState.(un)registerNetworkCallback following AppState
        if (!isAppStateListenerAdded) {
            if (DBG) Log.d(TAG, "addListener: AppState.addOnForeGroundListener");
            AppState.addOnForeGroundListener(sForeGroundListener);
            isAppStateListenerAdded = true;
        }
        handleForeGround(AppState.isForeGround());

        // init HttpImageManager manager.
        mHttpImageManager = new HttpImageManager(HttpImageManager.createDefaultMemoryCache(), 
                new FileSystemPersistence(BASEDIR));

        // Note: we do not init UPnP here, we wait for the user to enter the network view
        if (DBG) Log.d(TAG, "onCreate: TraktService.init");
        TraktService.init();

        NetworkAutoRefresh.init();
        //init credentials db
        NetworkCredentialsDatabase.getInstance().loadCredentials(this);
        ArchosUtils.setGlobalContext(this.getApplicationContext());
        // only launch BootupRecommandation if on AndroidTV and before Android O otherwise target TV channels
        if(ArchosFeatures.isAndroidTV(this) && Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            BootupRecommandationService.init();
    }

    // link networkState register/unregister networkCallback linked to app foreground/background lifecycle
    private final AppState.OnForeGroundListener sForeGroundListener = (applicationContext, foreground) -> {
        handleForeGround(foreground);
    };

    protected void handleForeGround(boolean foreground) {
        if (DBG) Log.d(TAG, "handleForeGround: is app foreground " + foreground);
        if (networkState == null ) networkState = NetworkState.instance(getApplicationContext());
        if (foreground) {
            if (!isVideStoreImportReceiverRegistered) {
                if (DBG) Log.d(TAG, "handleForeGround: app now in ForeGround registerReceiver for videoStoreImportReceiver");
                registerReceiver(videoStoreImportReceiver, intentFilter);
            }
            if (!isNetworkStateRegistered) {
                if (DBG) Log.d(TAG, "handleForeGround: app now in ForeGround NetworkState.registerNetworkCallback");
                networkState.registerNetworkCallback();
                isNetworkStateRegistered = true;
            }
        } else {
            if (isVideStoreImportReceiverRegistered) {
                if (DBG) Log.d(TAG, "handleForeGround: app now in ForeGround registerReceiver for videoStoreImportReceiver");
                unregisterReceiver(videoStoreImportReceiver);
            }
            if (isNetworkStateRegistered) {
                if (DBG) Log.d(TAG, "handleForeGround: app now in BackGround NetworkState.unRegisterNetworkCallback");
                networkState.unRegisterNetworkCallback();
                isNetworkStateRegistered = false;
            }
        }
    }

    public HttpImageManager getHttpImageManager() {
        return mHttpImageManager;
    }
}
