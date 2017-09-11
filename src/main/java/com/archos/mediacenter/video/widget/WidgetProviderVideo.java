/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediacenter.video.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.info.VideoInfoActivity;

public class WidgetProviderVideo extends AppWidgetProvider {
    private final static String TAG = "WidgetProviderVideo";
    private final static boolean DBG = false;

    public static final String TAP_ACTION = "com.archos.mediacenter.video.widget.TAP_ACTION";
    public static final String RELOAD_ACTION = "com.archos.mediacenter.video.widget.RELOAD_ACTION";
    public static final String INITIAL_UPDATE_ACTION = "com.archos.mediacenter.video.widget.INITIAL_UPDATE_ACTION";
    public static final String UPDATE_ACTION = "com.archos.mediacenter.video.widget.UPDATE_ACTION";
    public static final String EMPTY_DATA_ACTION = "com.archos.mediacenter.video.widget.EMPTY_DATA_ACTION";
    public static final String SHOW_UPDATE_SPINBAR_ACTION = "com.archos.mediacenter.video.widget.SHOW_UPDATE_SPINBAR";

    public static final String EXTRA_POSITION = "com.archos.mediacenter.video.widget.EXTRA_POSITION";
    public static final String EXTRA_VIDEO_ID = "com.archos.mediacenter.video.widget.EXTRA_ID";
    public static final String EXTRA_SHOW_ID = "com.archos.mediacenter.video.widget.EXTRA_SHOW_ID";
    public static final String EXTRA_CONTENT_CHANGED = "com.archos.mediacenter.video.widget.EXTRA_CONTENT_CHANGED";

    private static String SHARED_PREFERENCES_KEY_MODE = "video_widget_mode";

    public static final int MODE_UNKNOWN = -1;
    public static final int MODE_MOVIES = 0;
    public static final int MODE_TVSHOWS = 1;
    public static final int MODE_ALL_VIDEOS = 2;
    public static final int MODE_RECENTLY_ADDED = 3;
    public static final int MODE_RECENTLY_PLAYED = 4;

    private class WidgetConfiguration {
        public int mode;

        public WidgetConfiguration(int mode) {
            this.mode = mode;
        }
    }


    /*****************************************************************
    ** Mandatory AppWidgetProvider methods which must be implemented
    *****************************************************************/

    @Override
    public void onEnabled(Context context) {
        if (DBG) Log.d(TAG, "onEnabled");
        super.onEnabled(context);
    }

    @Override
    public void onDisabled(Context context) {
        if (DBG) Log.d(TAG, "onDisabled");
        super.onDisabled(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        if (DBG) Log.d(TAG, "onDeleted");
        super.onDeleted(context, appWidgetIds);

        // Delete the configuration settings corresponding to the provided widget ids
        deleteConfiguration(context, appWidgetIds);
    }

    /*
     * Handle intents sent to the video widget provider
     *  - if a widget id is provided => apply the action to this widget only.
     *  - if no id is provided => apply the action to all the existing video widgets.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DBG) Log.d(TAG, "onReceive intent=" + intent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        // Check if a specific widget id is provided by the intent
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // Build the list of widget ids to process
        int[] appWidgetIds;
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            // A specific id is provided => build a dummy list with only this id
            appWidgetIds = new int[] { appWidgetId };
            if (DBG) Log.d(TAG, "onReceive : process a single widget id=" + appWidgetId);
        }
        else {
            // No id provided => retrieve the list of all the widget ids handled by the AppWidgetManager
            ComponentName componentName = new ComponentName(context, WidgetProviderVideo.class);
            appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            if (DBG) {
                int count = appWidgetIds.length;
                String[] s = new String[count];
                int i;
                for (i = 0; i < count; i++) {
                    s[i] = String.valueOf(appWidgetIds[i]);
                }
                Log.d(TAG, "onReceive : process all video widget ids = " + TextUtils.join(", ", s));
            }
        }

        if (intent.getAction().equals(TAP_ACTION)) {
            //---------------------------------------------------
            // Handle a click on an item
            //---------------------------------------------------
            // Retrieve the position of the item on which the user tapped
            int position = intent.getIntExtra(EXTRA_POSITION, 0);
            long videoId = intent.getLongExtra(EXTRA_VIDEO_ID, -1);
            long showId = intent.getLongExtra(EXTRA_SHOW_ID, -1);

            // case 1: Open a TVShow in the browser
            if (showId >= 0) {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse("show:///"+showId));
                context.startActivity(i);
            }
            // case 2: launch the video
            else if (videoId >= 0) {
                VideoInfoActivity.startInstance(context, null, null, videoId);
            }
            else {
                if (DBG) Log.d(TAG, "Can not play item " + position);
                String toastMsg = context.getString(R.string.cant_play_video);
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
            }
        }
        else if (intent.getAction().equals(RELOAD_ACTION)) {
            //---------------------------------------------------
            // Handle a request to reload the widget data
            //---------------------------------------------------
            if (DBG) Log.d(TAG, "received RELOAD_ACTION at " + SystemClock.elapsedRealtime());

            // Force the widget service to reload its data (onDataSetChanged will be called)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.gridview);
        }
        else if (intent.getAction().equals(INITIAL_UPDATE_ACTION)) {
            //------------------------------------------------------------------
            // Handle a request to update a widget after it has been configured
            //------------------------------------------------------------------
            if (DBG) Log.d(TAG, "received INITIAL_UPDATE_ACTION at " + SystemClock.elapsedRealtime());

            // We only need to force the service to be created and bound
            update(context, appWidgetManager, appWidgetIds, true, false, false);
        }
        else if (intent.getAction().equals(UPDATE_ACTION)) {
            //---------------------------------------------------
            // Handle a request to update the widget(s)
            //---------------------------------------------------
            if (DBG) Log.d(TAG, "received UPDATE_ACTION at " + SystemClock.elapsedRealtime());

            // Hide the loading data spinbar and restore the normal view
            boolean contentChanged = intent.getBooleanExtra(EXTRA_CONTENT_CHANGED, false);
            update(context, appWidgetManager, appWidgetIds, contentChanged, false, false);
        }
        else if (intent.getAction().equals(EMPTY_DATA_ACTION)) {
            //------------------------------------------------------------------
            // Handle a request to update a widget when no data are available
            //------------------------------------------------------------------
            if (DBG) Log.d(TAG, "received EMPTY_DATA_ACTION at " + SystemClock.elapsedRealtime());

            // Update the empty view, hide the loading data spinbar and restore the normal view
            update(context, appWidgetManager, appWidgetIds, false, true, false);
        }
        else if (intent.getAction().equals(SHOW_UPDATE_SPINBAR_ACTION)) {
            //--------------------------------------------------------------------
            // Handle a request to show the loading spinbar
            //--------------------------------------------------------------------
            if (DBG) Log.d(TAG, "received SHOW_UPDATE_SPINBAR_ACTION at " + SystemClock.elapsedRealtime());

            // Show the loading data spinbar and hide the normal view
            update(context, appWidgetManager, appWidgetIds, false, false, true);
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (DBG) Log.d(TAG, "onUpdate : " + appWidgetIds.length + " widgets to update");
        super.onUpdate(context, appWidgetManager, appWidgetIds);

        // Normal update of the widget
        update(context, appWidgetManager, appWidgetIds, true, false, false);
    }


    /*****************************************************************
    ** Additional API
    *****************************************************************/

    public static void configure(Context context, int appWidgetId, int contentType) {
        saveConfiguration(context, appWidgetId, contentType);
    }


    /*****************************************************************
    ** Local methods
    *****************************************************************/

    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean updateRemoteAdapter, boolean showEmptyViewText, boolean showDataLoadingSpinBar) {
        if (DBG) Log.d(TAG, "update : updateRemoteAdapter=" + updateRemoteAdapter + " showEmptyViewText=" + showEmptyViewText + " showDataLoadingSpinBar=" + showDataLoadingSpinBar);

       // update each of the widgets with the remote adapter
        for (int i = 0; i < appWidgetIds.length; ++i) {
            WidgetConfiguration config = loadConfiguration(context, appWidgetIds[i]);

            if (config.mode != MODE_UNKNOWN) {
                // Here we setup the intent which points to the ViewService which will
                // provide the views for this collection.
                Intent intent = getServiceIntent(context, config.mode);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);

                RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

                // Select the label to display accordingly to the content type
                rv.setTextViewText(R.id.label, getLabel(context, config.mode));

                // Pending intent on the icon and title
                Intent appIntent = new Intent(context, MainActivity.class);
                PendingIntent appPendingIntent = PendingIntent.getActivity(context, 0 /* no requestCode */, appIntent, 0 /* no flags */);
                rv.setOnClickPendingIntent(R.id.app_icon, appPendingIntent);
                rv.setOnClickPendingIntent(R.id.label, appPendingIntent);

                if (updateRemoteAdapter) {
                    // When intents are compared, the extras are ignored, so we need to embed the extras
                    // into the data so that the extras will not be ignored.
                    intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                    rv.setRemoteAdapter(appWidgetIds[i], R.id.gridview, intent);
                }

                // The empty view is displayed when the collection has no items. It should be a sibling
                // of the collection view.
                rv.setEmptyView(R.id.gridview, R.id.empty_view);

                // Set the visibility of the different components of the widget
                rv.setViewVisibility(R.id.empty_view_spinbar, showEmptyViewText ? View.GONE : View.VISIBLE);
                rv.setViewVisibility(R.id.empty_view_text, showEmptyViewText ? View.VISIBLE : View.GONE);
                rv.setViewVisibility(R.id.data_update_spinbar, showDataLoadingSpinBar ? View.VISIBLE : View.GONE);
                rv.setViewVisibility(R.id.gridview, showDataLoadingSpinBar ? View.GONE : View.VISIBLE);

                // Here we setup the a pending intent template. Individuals items of a collection
                // cannot setup their own pending intents, instead, the collection as a whole can
                // setup a pending intent template, and the individual items can set a fillInIntent
                // to create unique before on an item to item basis.
                Intent tapIntent = new Intent(context, WidgetProviderVideo.class);
                tapIntent.setAction(WidgetProviderVideo.TAP_ACTION);
                tapIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
                intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                PendingIntent tapPendingIntent = PendingIntent.getBroadcast(context, 0, tapIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                rv.setPendingIntentTemplate(R.id.gridview, tapPendingIntent);

                // Apply all changes to the widget
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    private Intent getServiceIntent(Context context, int mode) {
        Intent intent;

        switch (mode) {
            case MODE_MOVIES:
                intent = new Intent(context, RemoteViewsServiceMovies.class);
                break;
            case MODE_TVSHOWS:
                intent = new Intent(context, RemoteViewsServiceTVShows.class);
                break;
            case MODE_RECENTLY_ADDED:
                intent = new Intent(context, RemoteViewsServiceRecentlyAdded.class);
                break;
            case MODE_RECENTLY_PLAYED:
                intent = new Intent(context, RemoteViewsServiceRecentlyPlayed.class);
                break;
            case MODE_ALL_VIDEOS:
            default:
                intent = new Intent(context, RemoteViewsServiceAllVideos.class);
                break;
        }

        return intent;
    }

    private String getLabel(Context context, int mode) {
        String[] contentType = context.getResources().getStringArray(R.array.video_content_array);
        return contentType[mode];
    }
    
    private static String getSharedPreferencesKeyMode(int appWidgetId) {
        return SHARED_PREFERENCES_KEY_MODE + "_" + String.valueOf(appWidgetId);
    }

    private static void saveConfiguration(Context context, int appWidgetId, int mode) {
        if (DBG) Log.d(TAG, "saveConfiguration for id=" + appWidgetId + " mode=" + mode);
        Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();

        // Save a specific configuration for this widget id
        ed.putInt(getSharedPreferencesKeyMode(appWidgetId), mode);

        // Apply() is asynchronous, thus faster than commit() which may be blocking
        ed.apply();
    }

    private WidgetConfiguration loadConfiguration(Context context, int appWidgetId) {

        // Load the specific configuration for this widget id
        int mode = PreferenceManager.getDefaultSharedPreferences(context).getInt(getSharedPreferencesKeyMode(appWidgetId), MODE_UNKNOWN);
        if (DBG) Log.d(TAG, "loadConfiguration for id=" + appWidgetId + " mode=" + mode);

        return new WidgetConfiguration(mode);
    }

    private void deleteConfiguration(Context context, int[] appWidgetIds) {
    	Editor ed = PreferenceManager.getDefaultSharedPreferences(context).edit();

        // Delete the settings corresponding to the provided widget id(s)
        for (int appWidgetId:appWidgetIds) {
            if (DBG) Log.d(TAG, "deleteConfiguration for id=" + appWidgetId);
            ed.remove(getSharedPreferencesKeyMode(appWidgetId));
        }

        // Apply() is asynchronous, thus faster than commit() which may be blocking
        ed.apply();
    }
} 
