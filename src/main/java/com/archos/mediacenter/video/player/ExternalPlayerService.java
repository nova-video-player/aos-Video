// Copyright 2026 Courville Software
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.video.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalPlayerService extends Service {

    private static final String ACTION_STOP_SERVICE = "StopExternalPlayerService";

    private static final String NOTIFICATION_CHANNEL_ID = "ExternalPlayerService";
    private static final int NOTIFICATION_ID = 13;

    private static final Logger log = LoggerFactory.getLogger(ExternalPlayerService.class);

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onCreate() {
        super.onCreate();
        if (log.isDebugEnabled()) log.debug("onCreate()");

        IntentFilter intentFilter = new IntentFilter(ACTION_STOP_SERVICE);
        registerReceiver(stopServiceBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);

        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (log.isDebugEnabled()) log.debug("onStartCommand({})", intent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (log.isDebugEnabled()) log.debug("onDestroy()");
        unregisterReceiver(stopServiceBroadcastReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel(notificationManager);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.nova_notification)
                .setContentTitle(getString(R.string.external_player_service_title))
                .setContentText(getString(R.string.external_player_service_description))
                .setWhen(0)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, getString(R.string.stop), createStopServicePendingIntent())
                .build();
    }

    private void createNotificationChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel notificationChannel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.external_player_service_title),
                NotificationManager.IMPORTANCE_LOW
        );

        notificationChannel.enableVibration(false);
        notificationChannel.enableLights(false);
        notificationChannel.setShowBadge(false);

        notificationManager.createNotificationChannel(notificationChannel);
    }

    private PendingIntent createStopServicePendingIntent() {
        Intent intent = new Intent(ACTION_STOP_SERVICE);
        intent.setPackage(getPackageName());

        return PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static Intent createServiceIntent(Context context) {
        Context ctx = context != null ? context : ArchosUtils.getGlobalContext();
        if (ctx == null) return null;
        return new Intent(ctx, ExternalPlayerService.class);
    }

    public static void startService(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context ctx = context != null ? context : ArchosUtils.getGlobalContext();
            if (ctx == null) return;
            final Intent intent = createServiceIntent(ctx);
            if (intent != null) {
                try {
                    ctx.startForegroundService(intent);
                } catch (Exception e) {
                    log.error("startService: failed to start foreground service", e);
                }
            }
        }
    }

    public static void stopService(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Context ctx = context != null ? context : ArchosUtils.getGlobalContext();
            if (ctx == null) return;
            final Intent intent = createServiceIntent(ctx);
            if (intent != null) {
                try {
                    ctx.stopService(intent);
                } catch (Exception e) {
                    log.error("stopService: failed to stop service", e);
                }
            }
        }
    }

    private final BroadcastReceiver stopServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                stopSelf();
            }
        }

    };

}
