 
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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;


/*
 * Implementation of RemoteViewsService common to all the video services
 */
abstract class RemoteViewsFactoryBase implements RemoteViewsService.RemoteViewsFactory {
    private final static String TAG = "RemoteViewsFactoryBase";
    private final static boolean DBG = false;

    private static final int MAX_ITEM_COUNT = 1000; // hard-limit for now, keep it safe with app widgets

    private static final int RELOAD_ACTION_DELAY = 2000;
    private static final int UPDATE_ACTION_DELAY = 1000;

    public static final int RESIZED_POSTER_WIDTH = 160;
    public static final int RESIZED_POSTER_HEIGHT = 240;

    private Context mContext;
    private int mAppWidgetId;
    protected Cursor mCursor;
    private CursorContentObserver mContentObserver;
    private AlarmManager mAlarmManager;
    private boolean mCursorFailed = false;
    private Intent mLastContentChangedIntent = null;
    private long mLastShowSpinbarTime = 0;

    public static final Uri MEDIA_DB_CONTENT_URI = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;

    protected static final String[] VIDEO_FILES_COLUMNS = new String[] {
        VideoStore.Video.Media._ID,
        VideoStore.Video.Media.TITLE,
        VideoLoader.NAME,
        VideoColumns.SCRAPER_COVER,
        VideoColumns.DURATION,
        VideoColumns.BOOKMARK,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
        VideoColumns.SCRAPER_E_SEASON,
        VideoColumns.SCRAPER_E_EPISODE};

    protected static final String[] TVSHOWS_COLUMNS = new String[] {
        VideoStore.Video.Media._ID,
        VideoStore.Video.Media.TITLE,
        VideoLoader.NAME,
        VideoColumns.SCRAPER_S_COVER + " AS " + VideoColumns.SCRAPER_COVER,
        VideoColumns.SCRAPER_SHOW_ID,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
        VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE };

    protected final String WHERE_NOT_HIDDEN = VideoColumns.ARCHOS_HIDE_FILE + "=0";


    /*****************************************************************
    ** Mandatory RemoteViewsFactory methods which must be implemented
    *****************************************************************/

    public RemoteViewsFactoryBase(Context context, Intent intent) {
        mContext = context;

        // Retrieve the id of the widget which uses this service
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    public void onCreate() {
        // In onCreate() you setup any connections / cursors to your data source. Heavy lifting,
        // for example downloading or creating content etc, should be deferred to onDataSetChanged()
        // or getViewAt(). Taking more than 20 seconds in this call will result in an ANR.
        if (DBG) Log.d(TAG, "onCreate : widget id=" + mAppWidgetId);

        mAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);

        mContentObserver = new CursorContentObserver();
    }

    public void onDestroy() {
        // In onDestroy() you should tear down anything that was setup for your data source,
        // eg. cursors, connections, etc.
        if (DBG) Log.d(TAG, "onDestroy : widget id=" + mAppWidgetId);
        if (mCursor != null) {
        	mCursor.unregisterContentObserver(mContentObserver);
        	mCursor.close();
        }
    }

    public int getCount() {
        if(DBG) Log.d(TAG, "getDataSize() returns "+mCursor.getCount());
        if (mCursor==null) {
            return 0;
        } else {
            return mCursor.getCount();
        }
    }

    public RemoteViews getViewAt(int position) {
    	if(DBG) Log.d(TAG, "getData("+position+")");
        // position will always range from 0 to getCount() - 1.

        // We construct a remote views item based on our widget item xml file, and set the
        // data based on the position.
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_item);
        
		mCursor.moveToPosition(position);
		final long videoId =  mCursor.getLong(mCursor.getColumnIndexOrThrow(VideoStore.Video.Media._ID));
		final String name =  mCursor.getString(mCursor.getColumnIndexOrThrow(VideoStore.Video.Media.TITLE));
		final int scraperId = mCursor.getInt(mCursor.getColumnIndexOrThrow(VideoColumns.ARCHOS_MEDIA_SCRAPER_ID));
		final int scraperType = mCursor.getInt(mCursor.getColumnIndexOrThrow(VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
		final String scraperCover = mCursor.getString(mCursor.getColumnIndexOrThrow(VideoColumns.SCRAPER_COVER));

		long showId = -1;
		try {
		    showId = mCursor.getLong(mCursor.getColumnIndexOrThrow(VideoColumns.SCRAPER_SHOW_ID));
        } catch(IllegalArgumentException e) {
            // happens in case this is not show
        }

        // Poster/thumbnail
		boolean isPoster = false;
        Bitmap bitmap = BitmapFactory.decodeFile(scraperCover);
        if (bitmap != null) {
            // A valid poster is available => resize it to the expected size, without black areas
        	bitmap = Utils.scaleThumbnailCenterCrop(mContext, bitmap, RESIZED_POSTER_WIDTH, RESIZED_POSTER_HEIGHT);
        	isPoster = (bitmap!=null);
        }
        else if (videoId >= 0) {
            // Retrieve the video thumbnail (this will create it if it does not exist yet)
            // NOTE : we must ask for a MINI_KIND thumbnail (512x384) because we need a rectangular display but
            // it is much bigger than what we need so we can subsample its dimensions by 2 to save memory.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            bitmap = VideoStore.Video.Thumbnails.getThumbnail(mContext.getContentResolver(), videoId, VideoStore.Video.Thumbnails.MINI_KIND, options);
            if (bitmap != null) {
                // A valid thumbnail is available => resize it to the expected size, without black areas
            	bitmap = Utils.scaleThumbnailCenterCrop(mContext, bitmap, RESIZED_POSTER_WIDTH, RESIZED_POSTER_HEIGHT);
            }
        }

        if (bitmap!=null) {
            rv.setImageViewBitmap(R.id.item_image, bitmap);
        } else {
        	rv.setImageViewResource(R.id.item_image, R.drawable.widget_default_video);
        }

        // Display some text if needed
        int textVisibility = View.GONE;
        if (scraperType == com.archos.mediascraper.BaseTags.TV_SHOW) {
            int season,episode;
            try {
                showId = mCursor.getLong(mCursor.getColumnIndexOrThrow(VideoColumns.SCRAPER_SHOW_ID));
        	    season = mCursor.getInt(mCursor.getColumnIndexOrThrow(VideoColumns.SCRAPER_E_SEASON));
    		    episode = mCursor.getInt(mCursor.getColumnIndexOrThrow(VideoColumns.SCRAPER_E_EPISODE));
    		    // This item is a TV show episode => the poster allows to identify the TV show
    		    // but it is useful to display the season and episode numbers
    		    rv.setTextViewText(R.id.single_line, "S"+season+"E"+episode);
    		    textVisibility = View.VISIBLE;
        	} catch(IllegalArgumentException e) {
                // happens in case this is not show
            }
        }
        else if (!isPoster) {
            // No poster available for this item => display the full filename
            rv.setTextViewText(R.id.single_line, name);
            textVisibility = View.VISIBLE;
        }

       	rv.setViewVisibility(R.id.single_line, textVisibility);

        // Next, we set a fill-intent which will be used to fill-in the pending intent template
        // which is set on the collection view in WidgetProviderVideo.
        if (DBG) Log.d(TAG, "getViewAt " + position + " for id=" + mAppWidgetId + " => videoId=" + videoId + " name=" + name);
        Bundle extras = new Bundle();
        extras.putInt(WidgetProviderVideo.EXTRA_POSITION, position);
        extras.putLong(WidgetProviderVideo.EXTRA_VIDEO_ID, videoId);
        if (showId >= -1) {
            extras.putLong(WidgetProviderVideo.EXTRA_SHOW_ID, showId);
        }

        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(extras);
        rv.setOnClickFillInIntent(R.id.item_image, fillInIntent);

        // Return the remote views object.
        return rv;
    }

    public RemoteViews getLoadingView() {
        RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_item);
        rv.setImageViewResource(R.id.item_image, R.drawable.widget_default_video);
        return rv;
    }

    public int getViewTypeCount() {
        return 1;
    }

    public long getItemId(int position) {
        return position;
    }

    public boolean hasStableIds() {
        return true;
    }

    public void onDataSetChanged() {
        // This is triggered when you call AppWidgetManager notifyAppWidgetViewDataChanged
        // on the collection view corresponding to this factory. You can do heaving lifting in
        // here, synchronously. For example, if you need to process an image, fetch something
        // from the network, etc., it is ok to do it here, synchronously. The widget will remain
        // in its current state while work is being done here, so you don't need to worry about
        // locking up the widget.
        long currentElapsedRealTime = SystemClock.elapsedRealtime();

        boolean dataAvailable = isMedialibAvailable();

        if (dataAvailable) {
            if (mCursor != null) {
            	mCursor.unregisterContentObserver(mContentObserver);
            	mCursor.close();
            }
            // Try to get the data corresponding to this service 
            // (thumbnails are not built yet, they will be requested only when we need to display the items)
            boolean success = loadData(mContext, MAX_ITEM_COUNT);
            mCursor.registerContentObserver(mContentObserver);

            if (success) {
                // Video data are available
                int numItems = getCount();
                if (DBG) Log.d(TAG, "onDataSetChanged : " + numItems + " items loaded for id=" + mAppWidgetId);

                if (numItems == 0) {
                    // No data
                    if (DBG) Log.d(TAG, "No files found => set EMPTY_DATA_ACTION alarm at" + currentElapsedRealTime);
                    setDelayedAlarm(WidgetProviderVideo.EMPTY_DATA_ACTION, UPDATE_ACTION_DELAY, false);
                }
                else if (mCursorFailed) {
                    // Data are now loaded after one or more failed attempts => send a request to update the widget
                    mCursorFailed = false;
                    
                    if (DBG) Log.d(TAG, "Data now available => set UPDATE_ACTION alarm at" + currentElapsedRealTime);
                    setDelayedAlarm(WidgetProviderVideo.UPDATE_ACTION, UPDATE_ACTION_DELAY, true);                
                }
                else {
                    // This case is needed to display the widget content for the first time when it is created
                    if (DBG) Log.d(TAG, "Data ok => set UPDATE_ACTION alarm at " + currentElapsedRealTime);
                    setDelayedAlarm(WidgetProviderVideo.UPDATE_ACTION, UPDATE_ACTION_DELAY, true);
                }
            }
            else {
                // Could not get the video data => this is weird because the medialib is supposed to be available...
                dataAvailable = false;
            }
        }

        if (!dataAvailable) {
            // The video data are not available (yet) => retry loading them a bit later
            mCursorFailed = true;

            if (DBG) Log.d(TAG, "Data not available yet => set RELOAD_ACTION alarm at " + SystemClock.elapsedRealtime());
            setDelayedAlarm(WidgetProviderVideo.RELOAD_ACTION, RELOAD_ACTION_DELAY, false);
        }
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        if (DBG) Log.d(TAG, "registerDataSetObserver");
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (DBG) Log.d(TAG, "unregisterDataSetObserver");
    }


    /*****************************************************************
    ** Addditional API
    *****************************************************************/

    /*
     * Called when the contents of the cursor(s) used to get the data for this service changes
     */
    public void onContentChanged() {
        // We want the provider to handle the event and take care of reloading the data but we don't want the widget
        // to be redrawn too often if the ContentObserver triggers a lot of events => schedule a delayed intent
        // to the provider but make sure to cancel the previous intent which was scheduled so that the provider will only
        // receive one intent after the last event is triggered.

        long currentElapsedRealTime = SystemClock.elapsedRealtime();
        if (DBG) Log.d(TAG, "onContentChanged : elapsed time since the last SHOW_UPDATE_SPINBAR_ACTION was sent = " + (currentElapsedRealTime - mLastShowSpinbarTime));

        if (currentElapsedRealTime - mLastShowSpinbarTime > 1000) {
            // Tell the provider to hide the RollView and show the spinbar
            // NOTE : it should be enough to send this intent once (for instance when onContentChanged() is called
            // for the first time after a while) but it is safer to send it periodically in case another event
            // causes the widget to be redrawn
            setDelayedAlarm(WidgetProviderVideo.SHOW_UPDATE_SPINBAR_ACTION, UPDATE_ACTION_DELAY, false);
            mLastShowSpinbarTime = currentElapsedRealTime;
        }

        if (DBG) Log.d(TAG, "onContentChanged : replace previous RELOAD_ACTION at " + currentElapsedRealTime);
        replacePreviousDelayedAlarm(WidgetProviderVideo.RELOAD_ACTION, RELOAD_ACTION_DELAY);
    }


    /*****************************************************************
    ** Local methods
    *****************************************************************/

    private boolean isMedialibAvailable() {
        ContentResolver resolver = mContext.getContentResolver();

        // Try a dummy request to the medialib
        String[] column = new String[] { VideoStore.Video.Media._ID };
        String sortOrder = VideoStore.Video.Media.DEFAULT_SORT_ORDER + " LIMIT " + 1;
        String whereClause = (VideoColumns.ALBUM + " != ''");
        Cursor cursor = resolver.query(MEDIA_DB_CONTENT_URI, column, whereClause , null, sortOrder);

        if (cursor != null) {
            cursor.close();
            return true;
        }
        return false;
    }

    private void setDelayedAlarm(String action, int delay, boolean notifyContentChanged) {
        Intent intent = new Intent(mContext, WidgetProviderVideo.class);
        intent.setAction(action);
        intent.setData(Uri.parse(String.valueOf(SystemClock.elapsedRealtime())));    // Fill data with a dummy value to avoid the "extra beeing ignored" optimization of the PendingIntent
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        if (notifyContentChanged) {
            intent.putExtra(WidgetProviderVideo.EXTRA_CONTENT_CHANGED, true);
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pendingIntent);
    }

    private void replacePreviousDelayedAlarm(String action, int delay) {
        PendingIntent pendingIntent;

        // Cancel any previous alarm set for a content changed event
        if (mLastContentChangedIntent != null) {
            pendingIntent = PendingIntent.getBroadcast(mContext, 0, mLastContentChangedIntent, 0);
            mAlarmManager.cancel(pendingIntent);
        }

        // Set a new delayed alarm and save a copy of the intent to send in case we want to cancel it later
        Intent intent = new Intent(mContext, WidgetProviderVideo.class);
        intent.setAction(action);
        intent.setData(Uri.parse(String.valueOf(SystemClock.elapsedRealtime())));   // Fill data with a dummy value to avoid the "extra beeing ignored" optimization of the PendingIntent
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        mLastContentChangedIntent = intent.cloneFilter();                           // This is enough to identify the intent
        pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pendingIntent);
    }

    // To be implemented by each sub-class
    abstract protected boolean loadData(Context context, int itemCount);


    /****************************************************************************************
    * Content observer 
    ****************************************************************************************/
    private class CursorContentObserver extends ContentObserver {
        public CursorContentObserver() {
            super(new Handler());
        }

        public boolean deliverSelfNotifications() {
            return true;
        }

        public void onChange(boolean selfChange) {
            // The content of a cursor has changed => make the service reload
            // the data if needed
            onContentChanged();
        }
    }
}