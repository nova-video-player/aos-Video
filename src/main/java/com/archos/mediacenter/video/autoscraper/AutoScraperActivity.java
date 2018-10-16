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

package com.archos.mediacenter.video.autoscraper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.support.v4.app.NotificationCompat;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager.LayoutParams;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.environment.ArchosSettings;
import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.MetaFile;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.imageview.ChainProcessor;
import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.ImageViewSetter;
import com.archos.mediacenter.utils.imageview.ImageViewSetterConfiguration;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.player.tvmenu.TVUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class AutoScraperActivity extends Activity implements AbsListView.OnScrollListener,
                                                             View.OnKeyListener, View.OnClickListener {
    private final static String TAG = "AutoScraperActivity";
    private final static boolean DBG = false;

    /** showname S01E04 */
    private static final String EPISODE_FORMAT = "%s S%02dE%02d";

    private final static int ITEM_STATUS_INITIAL = 0;
    private final static int ITEM_STATUS_BUSY = 1;
    private final static int ITEM_STATUS_SUCCESS = 2;
    private final static int ITEM_STATUS_FAILED = 3;
    private final static int ITEM_STATUS_REJECTED = 4;

    private final static int MY_SCROLL_STATE_IDLE = 100;
    private final static int MY_SCROLL_STATE_USER = 101;
    private final static int MY_SCROLL_STATE_AUTO = 102;

    private static final String mNotifChannelId = "AutoScraperActivity_id";
    private static final String mNotifChannelName = "AutoScraperActivity";
    private static final String mNotifChannelDescr = "AutoScraperActivity";
    private static final int NOTIFICATION_ID = 2; // MediaPlaybackService is using the default ID 1, see #94

    // The contents of this cursor is modified each time scraper info are
    // found for a file => to be used only when creating the activity
    private final static String[] SCRAPER_ACTIVITY_COLS = {
        // Columns needed by the activity
        BaseColumns._ID,
        MediaColumns.DATA,
        VideoStore.Video.VideoColumns.DURATION,
        VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
        MediaColumns.TITLE
    };
    protected Cursor mActivityFileCursor;

    // The contents of this cursor should never be modified => to be used only
    // in the adapter to avoid processed files from beeing removed from the list
    private final static String[] SCRAPER_ADAPTER_COLS = {
        // Columns needed by the adapter
        BaseColumns._ID,
        MediaColumns.DATA,
        MediaColumns.TITLE
    };
    private MatrixCursor mAdapterFileCursor;

    private int mIdIndex;
    protected int mDataIndex;
    private int mDurationIndex;
    private int mScraperTypeIndex;
    private int mTitleIndex;

    protected List<String> mFileList;
    HashMap<String, FileProperties> mFileProperties;
    protected ScraperResultTask mResultTask;
    private NotificationManager mNotificationManager;
    protected Notification mNotification = null;
    private NotificationCompat.Builder mNotificationBuilder = null;

    protected Scraper mScraper;
    private String mContextMenuPath;
    protected int mFileCount;
    protected int mFilesProcessed = 0;
    protected boolean mInBackground = false;
    private boolean mFolderMode;
    private String mFolderPath;
    protected int mPreviousScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    protected int mMyScrollState = MY_SCROLL_STATE_IDLE;
    private boolean mScrollingWithKeys = false;

    protected ListView mListView;
    private AutoScraperAdapter mAdapter;
    private View mMainView;
    private Button mAbortButton;
    private Button mExitButton;
    private View mEmptyView;

    private boolean mIsLargeScreen;
    private PowerManager.WakeLock mWakeLock;

    protected final static class FileProperties {
        int id;
        int status;
        int duration;
        int scraperType;
        String title;

        String posterPath = null;
        // below values are used to display so set them to empty string
        String rating = "";
        String date = "";
        String genre = "";

        public FileProperties(int id, int status, int duration, int scraperType, String title) {
            this.id = id;
            this.status = status;
            this.duration = duration;
            this.scraperType = scraperType;
            this.title = title;
        }
    }

    //*****************************************************************************
    // Activity lifecycle functions
    //*****************************************************************************

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        getWindow().setFlags(LayoutParams.FLAG_NOT_TOUCH_MODAL, LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().setFlags(LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

        // Notify the application that the activity has started
        CustomApplication app = (CustomApplication)getApplication();
        app.setAutoScraperActive(true);
        mScraper = new Scraper(this);

        // Check if the intent which created this activity contains a folder path
        Uri folderUri = getIntent().getData();
        if (folderUri != null) {
            mFolderMode = true;
            // FIXME: this is broken for smb:// files
            mFolderPath = folderUri.getPath();
            if (DBG) Log.d(TAG, "onCreate : search in folder " + mFolderPath);
        }
        else {
            mFolderMode = false;
            mFolderPath = null;
            if (DBG) Log.d(TAG, "onCreate : search in the full database");
        }

        setContentView(R.layout.auto_scraper_main);

        mMainView = findViewById(R.id.main_view);
        mAbortButton = (Button)findViewById(R.id.abort_button);
        mAbortButton.setOnClickListener(this);
        mExitButton = (Button)findViewById(R.id.exit_button);
        mExitButton.setOnClickListener(this);

        mListView = (ListView) findViewById(R.id.list_items);
        mListView.setTextFilterEnabled(true);
        mListView.setCacheColorHint(0);
        mListView.setSelector(R.drawable.list_selector_no_background);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setOnScrollListener(this);
        mListView.setOnKeyListener(this);

        mActivityFileCursor = getFileListCursor();
        getColumnIndices(mActivityFileCursor);
        buildFileProperties(mActivityFileCursor);
        mAdapterFileCursor = buildAdapterCursor(mActivityFileCursor);

        mAdapter = new AutoScraperAdapter(getApplication(), this,
                                          R.layout.auto_scraper_item,
                                          mAdapterFileCursor);
        mListView.setAdapter(mAdapter);
/*
        if (!mListView.isInTouchMode()) {
            // The application is remotely controlled => set the focus by default
            // on the Cancel button so that the user can abort the task with a 
            // single click instead of navigating the full ListView
            mAbortButton.requestFocus();
        }
*/
        mEmptyView = buildEmptyView();
        mListView.setEmptyView(mEmptyView);
        updateControlButtons(false);
        if (mFileCount > 0) {
            mMainView.setVisibility(View.VISIBLE);
        }
        else {
            mMainView.setVisibility(View.GONE);
        }

        mIsLargeScreen = getResources().getConfiguration().isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)|| TVUtils.isTV(this);

        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "AutoScraperActivity");
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        startScraperTask();
    }
    @Override
    public void onStart() {
        super.onStart();

        // Allow the screen to be dimmed while the videos are processed in the foreground
        mWakeLock.acquire();

        // Remove the notification when switching the activity to the foreground
        if (mNotification != null) {
            removeStatusbarNotification();
        }

        mInBackground = false;
    }

    @Override
    public void onStop() {
        // Make sure to restore the standard screen on/off behaviour when the activity is not visible
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        if (isResultTaskActive()) {
            // The user pressed HOME and the processing task is still running
            // => show a notification in the statusbar while the activity keeps on running the background
            startStatusbarNotification();
        }
        else if (!isFinishing()) {
            // The user pressed HOME and the processing task is done
            // => finish the activity as if the BACK key was pressed
            finish();
        }

        mInBackground = true;

        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");

        if (mFilesProcessed > 0)
            TraktService.onNewVideo(this);

        if (isResultTaskActive()) {
            mResultTask.cancel(true);
        }

        mScraper = null;

        if (mAdapter != null) {
            mAdapter.stopAndCleanup();
        }

        if (mActivityFileCursor != null) {
            mActivityFileCursor.close();
        }

        if (mAdapterFileCursor != null) {
            mAdapterFileCursor.close();
        }

        // Make sure the notification is removed when the activity is destroyed
        if (mNotification != null) {
            removeStatusbarNotification();
        }

        // Notify the application that the activity is destroyed
        CustomApplication app = (CustomApplication)getApplication();
        app.setAutoScraperActive(false);

        super.onDestroy();
    }


    //*****************************************************************************
    // Activity events management
    //*****************************************************************************

    @Override
    public void onBackPressed() {
        if (isResultTaskActive()) {
            // We want this activity to keep on running in the background and go back
            // to the previous activity => bring the previous activity back to front
            startBrowserActivity();
        }
        else {
            // Keep the standard BACK behaviour
            super.onBackPressed();
        }
    }

    private void startBrowserActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      // If we've received a touch notification that the user has touched
      // outside the app, Stop the activity.
      if (MotionEvent.ACTION_OUTSIDE == event.getAction()) {
        startBrowserActivity();
        return true;
      }

      // Delegate everything else to Activity.
      return super.onTouchEvent(event);
    }

    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && event.getAction() == KeyEvent.ACTION_DOWN) {
            View focusedItem = mListView.getSelectedView();
            if (focusedItem != null) {
                // The user is using the Archos remote to control this activity and he pressed the OK key
                // while an item of the listview was highlighted => make as if he pressed the reject button
                int position = mListView.getPositionForView(focusedItem);
                rejectScraperInfos(position);
                return true;
            }
        }
        else if ((keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) && event.getAction() == KeyEvent.ACTION_DOWN) {
            mScrollingWithKeys = true;
        }
        return false;
    }

    public void onClick(View view) {
        if (view == mAbortButton) {
            // Ask the user whether he really wants to abort the scraper task or not
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle(R.string.scraper_notification_title)
                    .setMessage(R.string.scraper_abort_request)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            if (mResultTask!=null) { // How can this be null ? I don't know, but we got a crash report on GooglePlay console...
                                // Abort the scraper task and exit the activity
                                mResultTask.cancel(true);
                            }
                            finish();
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // Keep on searching => do nothing
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
        else if (view == mExitButton) {
            // Processing is done, just close the current activity
            finish();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        // Show the name of the file in the header
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo)menuInfo;
        int position = adapterMenuInfo.position;
        mActivityFileCursor.moveToPosition(position);
        String path = mActivityFileCursor.getString(mDataIndex);
        MetaFile file = MetaFile.from(path);
        menu.setHeaderTitle(file.getName());

        // Add the context menu items
        menu.add(0, R.string.info, 0, R.string.info);

        // Save the path, we will need it when an entry of the menu is selected
        mContextMenuPath = path;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        boolean success = false;

        // Make sure that the user selected the "details" entry of the menu
        int menuId = item.getItemId();
        if (menuId == R.string.info) {
            // Ask to display the info dialog with the "online update" feature disabled
            MetaFile file = MetaFile.from(mContextMenuPath);
            Intent intent = new Intent(this, VideoInfoActivity.class);
            intent.setData(file.getUri());
            intent.putExtra(VideoInfoActivity.EXTRA_NO_ONLINE_UPDATE, true);
            try {
                startActivity(intent);
                success = true;
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.cannot_open_video, Toast.LENGTH_SHORT).show();
            }
        }

        return success;
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == SCROLL_STATE_FLING) {
            // User fling is always preceded by a touch scroll
            if (mPreviousScrollState == SCROLL_STATE_TOUCH_SCROLL) {
                mMyScrollState = MY_SCROLL_STATE_USER;
            }
            else {
                // Fling state is due to a call to smoothScroll()
                mMyScrollState = MY_SCROLL_STATE_AUTO;
            }
        }
        else if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
            // Touch scroll is always done by the user
            mMyScrollState = MY_SCROLL_STATE_USER;
        }
        else {
            mMyScrollState = MY_SCROLL_STATE_IDLE;
        }

        mPreviousScrollState = scrollState;
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        // nothing
    }


    //*****************************************************************************
    // Activity notification management
    //*****************************************************************************

    private void startStatusbarNotification() {
        if (DBG) Log.d(TAG, "startStatusbarNotification");

        Context context = AutoScraperActivity.this;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mNotifChannel = new NotificationChannel(mNotifChannelId, mNotifChannelName,
                    mNotificationManager.IMPORTANCE_LOW);
            mNotifChannel.setDescription(mNotifChannelDescr);
            if (mNotificationManager != null)
                mNotificationManager.createNotificationChannel(mNotifChannel);
        }

        // Set the title and icon
        int icon = R.drawable.stat_notify_scraper;
        CharSequence title = context.getResources().getText(R.string.scraper_notification_title);
        long when = System.currentTimeMillis();

        Intent notificationIntent = new Intent(context, AutoScraperActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

        // Create a new notification builder
        mNotificationBuilder = new NotificationCompat.Builder(context, mNotifChannelId)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true).setTicker(null).setOnlyAlertOnce(true).setContentIntent(contentIntent).setOngoing(true);

        // Set the info to display in the notification panel and attach the notification to the notification manager
        updateStatusbarNotification();
    }

    protected void updateStatusbarNotification() {
        if (DBG) Log.d(TAG, "updateStatusbarNotification");

        if (mNotificationBuilder != null) {
            Context context = AutoScraperActivity.this;

            // Display the number of files already processed as notification text
            String formatString = context.getResources().getString(R.string.scraper_notification_progress);
            String formattedString = String.format(Locale.getDefault(), formatString, Integer.valueOf(mFilesProcessed), Integer.valueOf(mFileCount));

            // Update the notification text
            mNotificationBuilder.setContentText(formattedString);
            // Tell the notification manager about the changes
            mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
        }
    }

    protected void removeStatusbarNotification() {
        if (DBG) Log.d(TAG, "removeStatusbarNotification");

        mNotificationManager.cancel(NOTIFICATION_ID);
        mNotification = null;
    }


    //*****************************************************************************
    // Activity local functions
    //*****************************************************************************

    private static final String WHERE_FOLDER_MODE = VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID +
            "=? AND " + VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0 AND " + MediaColumns.DATA + " LIKE ?";
    private static final String WHERE_ALL_MODE = VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID +
            "=? AND " + VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0 AND " + MediaColumns.DATA + " NOT LIKE ?";
    private Cursor getFileListCursor() {
        if (mFolderMode) {
            // Look for all the videos not yet processed which are located in the requested folder
            Uri uri = VideoStore.Video.Media.getContentUriForPath(mFolderPath);
            String[] selectionArgs = new String[] {
                    "0",
                    mFolderPath + "/%"
            };
            return getContentResolver().query(uri, SCRAPER_ACTIVITY_COLS, WHERE_FOLDER_MODE, selectionArgs, null);
        }
        else {
            // Look for all the videos not yet processed and not located in the Camera folder
            final String cameraPath =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
            // Uri uri = MediaStore.Video.Media.getContentUriForPath(cameraPath);
            String[] selectionArgs = new String[]{
                    "0",
                    cameraPath + "/%"
            };
            return getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, WHERE_ALL_MODE, selectionArgs, null);
        }
    }

    private Cursor getAllMatchingVideosCursor() {
        if (mFolderMode) {
            // Look for all the processed videos located in the requested folder for which no info has been found
            Uri uri = VideoStore.Video.Media.getContentUriForPath(mFolderPath);
            String[] selectionArgs = new String[] {
                    "-1",
                    mFolderPath + "/%"
            };
            return getContentResolver().query(uri, SCRAPER_ACTIVITY_COLS, WHERE_FOLDER_MODE, selectionArgs, null);
        }
        else {
            // Look for all the processed videos not located in the Camera folder for which no info has been found
            final String cameraPath =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath() + "/Camera";
            String[] selectionArgs = new String[]{
                    "-1",
                    cameraPath + "/%"
            };
            return getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, SCRAPER_ACTIVITY_COLS, WHERE_ALL_MODE, selectionArgs, null);
        }
    }

    private void buildFileProperties(Cursor cursor) {
        mFileProperties = new HashMap<String, FileProperties>();
        mFileList = new ArrayList<String>();

        mFileCount = (cursor != null) ? cursor.getCount() : 0;
        if (cursor == null) return;
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(mIdIndex);
                int duration = cursor.getInt(mDurationIndex);
                int scraperType = cursor.getInt(mScraperTypeIndex);
                String path = cursor.getString(mDataIndex);
                String title = cursor.getString(mTitleIndex);
                mFileProperties.put(path, new FileProperties(id, ITEM_STATUS_INITIAL, duration, scraperType, title));
                mFileList.add(path);
            } while (cursor.moveToNext());
        }
    }

    private MatrixCursor buildAdapterCursor(Cursor cursor) {
        // Create a clone of the provided cursor but keep only the needed columns
        MatrixCursor newCursor = new MatrixCursor(SCRAPER_ADAPTER_COLS);

        if (mFileCount > 0) {
            int columns = newCursor.getColumnCount();
            cursor.moveToFirst();
            do {
                // Build an array with the data corresponding to the current row
                ArrayList<String> rowData = new ArrayList<String>(columns);
                rowData.add(cursor.getString(mIdIndex));
                rowData.add(cursor.getString(mDataIndex));
                rowData.add(cursor.getString(mTitleIndex));

                // Add the row to the cloned cursor
                newCursor.addRow(rowData);
            } while (cursor.moveToNext());
        }

        return newCursor;
    }

    private void getColumnIndices(Cursor cursor) {
        if (cursor != null) {
            mIdIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            mDataIndex = cursor.getColumnIndexOrThrow(MediaColumns.DATA);
            mDurationIndex = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.DURATION);
            mScraperTypeIndex = cursor.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE);
            mTitleIndex = cursor.getColumnIndexOrThrow(MediaColumns.TITLE);
        }
    }

    private static final String WHERE_PATH = MediaColumns.DATA + "=?";
    protected void updateScraperInfoInMediaLib(String path, int scraperId, int typeId) {
        ContentValues cv = new ContentValues(2);
        cv.put(VideoColumns.ARCHOS_MEDIA_SCRAPER_ID, String.valueOf(scraperId));
        cv.put(VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, String.valueOf(typeId));
        getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, WHERE_PATH, new String[]{ path });
    }

    protected void invalidateItem(int position) {
        int first = mListView.getFirstVisiblePosition();
        int last = mListView.getLastVisiblePosition();
        if (position >= first && position <= last) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private boolean isResultTaskActive() {
        if (mResultTask != null && mResultTask.getStatus() !=
            AsyncTask.Status.FINISHED && !mResultTask.isCancelled()) {
            return true;
        }
        return false;
    }

    private View buildEmptyView() {
        String text = null;

        View emptyView = findViewById(R.id.empty_view);

        TextView emptyViewText = (TextView) findViewById(R.id.empty_view_text);

        // Are there videos not yet processed?
        if (mFileCount == 0) {
            // All the requested videos are already processed but we can at least allow
            // the user to try processing again those for which no info was found so far
            int matchingVideosCount = 0;

            // Get the number of videos in the requested set which still do not have info
            Cursor cursor = getAllMatchingVideosCursor();
            if (cursor != null) {
                matchingVideosCount = cursor.getCount();
                cursor.close();
            }

            final Button emptyViewYesButton = (Button) findViewById(R.id.empty_view_yes_button);
            final Button emptyViewNoButton = (Button) findViewById(R.id.empty_view_no_button);

            if (matchingVideosCount > 0) {
                // There are still one or more videos without info
                // => ask the user whether he wants to process them again

                if (mFolderMode) {
                    text = getString(com.archos.mediacenter.video.R.string.scraper_no_new_files_in_folder);
                }
                else {
                    text = getString(com.archos.mediacenter.video.R.string.scraper_no_new_files);
                }

                if (matchingVideosCount > 1) {
                    text += ".\n\n" + getString(com.archos.mediacenter.video.R.string.scraper_folder_no_files, matchingVideosCount);
                }
                else {
                    text += ".\n\n" + getString(com.archos.mediacenter.video.R.string.scraper_folder_no_file);
                }
                text += " " + getString(com.archos.mediacenter.video.R.string.scraper_folder_try_again);

                emptyViewYesButton.setVisibility(View.VISIBLE);
                emptyViewYesButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // The user wants to try searching again
                        trySearchingAgain();
                    }
                });

                emptyViewNoButton.setVisibility(View.VISIBLE);
                emptyViewNoButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // The user doesn't want to search again => close the activity
                        finish();
                    }
                });
            }
            else {
                // All the requested videos already have infos => just tell the user that there is nothing more to do
                text = getString(com.archos.mediacenter.video.R.string.scraper_no_files);
                // We only need the OK button here
                emptyViewNoButton.setVisibility(View.GONE);
                emptyViewYesButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        // nothing to do => close the activity
                        finish();
                    }
                });
            }
        }

        emptyViewText.setText(text);

        return emptyView;
    }

    protected void trySearchingAgain() {
        // First make sure the previous cursors are closed
        if (mActivityFileCursor != null) {
            mActivityFileCursor.close();
        }
        if (mAdapterFileCursor != null) {
            mAdapterFileCursor.close();
        }

        // Now process all the videos located in the folder for which no info was found formerly
        mActivityFileCursor = getAllMatchingVideosCursor();
        getColumnIndices(mActivityFileCursor);
        buildFileProperties(mActivityFileCursor);

        // Build a new adapter for the new list of video
        mAdapterFileCursor = buildAdapterCursor(mActivityFileCursor);
        mAdapter = new AutoScraperAdapter(getApplication(), AutoScraperActivity.this,
                                                                              R.layout.auto_scraper_item,
                                                                              mAdapterFileCursor);
        // Bind the new adapter to the list
        mListView.setAdapter(mAdapter);

        // Hide the empty view and switch to the main view
        mEmptyView.setVisibility(View.GONE);
        mMainView.setVisibility(View.VISIBLE);

        // Force the focus on the list so that it can be controlled with the Archos remote if needed
        mListView.requestFocus();

        // Start the scraper task which will search infos online
        startScraperTask();
    }

    private void startScraperTask() {
        if (mFileCount > 0) {
            // Make sure we are connected to a network
            if (!ArchosSettings.isDemoModeActive(this) && !ArchosUtils.isNetworkConnected(this)) {
                // No connection => show an error dialog
                String message = getResources().getString(com.archos.mediacenter.video.R.string.scrap_no_network);
                message += " " + getResources().getString(com.archos.mediacenter.video.R.string.scrap_enable_network_first);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setIcon(android.R.drawable.ic_dialog_alert)
                       .setTitle(R.string.mediacenterlabel)
                       .setMessage(message)
                       .setCancelable(true)
                       .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Exit the activity when the user closes the dialog
                                finish();
                            }
                    });
                AlertDialog alert = builder.create();
                alert.show();
                return;
            }

            // Start scraping
            mResultTask = new ScraperResultTask(AutoScraperActivity.this);
            mResultTask.execute();
        }
    }

    private void rejectScraperInfos(int position) {
        if (position >= 0) {
            // Get the path corresponding to the item
            Cursor cursor = mActivityFileCursor;
            cursor.moveToPosition(position);
            String path = cursor.getString(mDataIndex);

            // Makie sure this item is processed and that scraper infos have been found
            FileProperties itemProperties = mFileProperties.get(path);
            if (itemProperties.status == ITEM_STATUS_SUCCESS) {
                // Set this item as rejected
                itemProperties.status = ITEM_STATUS_REJECTED;
                Log.d(TAG, "onClick : reject infos for " + path);

                // Reset the scraper fields for this item in the medialib (set them to -1 so
                // that this file will skipped when launching the automated process again)
                // this also removes data from the scraper database
                updateScraperInfoInMediaLib(path, -1, -1);

                // Update the display
                invalidateItem(position);
            }
        }
    }

    private void updateControlButtons(boolean processingDone) {
        if (processingDone) {
            // Only show the exit button when processing is done
            mAbortButton.setVisibility(View.GONE);
            mExitButton.setVisibility(View.VISIBLE);

            // When the application is remotely controlled set the focus on the exit button
            // so that the user can exit the activity with a single click
            if (!mListView.isInTouchMode()) {
                mExitButton.requestFocus();
            }
        }
        else {
            // Only show the abort button as long as videos are beeing processed
            mAbortButton.setVisibility(View.VISIBLE);
            mExitButton.setVisibility(View.GONE);
        }
    }


    //*****************************************************************************
    // Adapter
    //*****************************************************************************

    class AutoScraperAdapter extends CursorAdapter implements OnClickListener {
        private final AutoScraperActivity mActivity;

        private final ImageViewSetter mSetter;
        private final ImageProcessor mPosterProcessor;
        private final ChainProcessor mChainProcessor;

        private final LayoutInflater mInflater;
        private final int mLayoutId;

        private int mAdapterIdIndex;
        private int mAdapterDataIndex;
        private int mAdapterTitleIndex;

        private int mThumbnailWidth;
        public int mThumbnailHeight;
        private int mPosterWidth;
        private int mPosterHeight;

        class ViewHolder {
            RelativeLayout initial_item_container;
            ImageView      initial_thumbnail;
            TextView       initial_name;
            TextView       initial_duration;
            TextView       initial_status;
            ProgressBar    initial_spinbar;

            RelativeLayout processed_item_container;
            ImageView      processed_poster;
            TextView       processed_name;
            TextView       processed_duration;
            TextView       processed_genre;
            TextView       processed_date;
            TextView       processed_rating;
            Button         processed_reject_btn;
        }


        AutoScraperAdapter(Context context, AutoScraperActivity currentActivity,
                           int layout, Cursor cursor) {
            super(context, cursor, 0);

            mActivity = currentActivity;

            if (cursor != null) {
                mAdapterIdIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID);
                mAdapterDataIndex = cursor.getColumnIndexOrThrow(MediaColumns.DATA);
                mAdapterTitleIndex = cursor.getColumnIndexOrThrow(MediaColumns.TITLE);
            }

            mInflater = LayoutInflater.from(context);
            mLayoutId = layout;

            mThumbnailWidth = getResources().getDimensionPixelSize(R.dimen.auto_scraper_thumbnail_width);
            mThumbnailHeight = getResources().getDimensionPixelSize(R.dimen.auto_scraper_thumbnail_height);

            mPosterWidth = getResources().getDimensionPixelSize(R.dimen.auto_scraper_poster_width);
            mPosterHeight = getResources().getDimensionPixelSize(R.dimen.auto_scraper_poster_height);

            //int defaultIconColor = getResources().getColor(R.color.default_icons_color_filter);
            ImageViewSetterConfiguration config = ImageViewSetterConfiguration.Builder
                    .createNew()
                    .setDrawableWhileLoading(context.getResources().getDrawable(R.drawable.filetype_video))
                    .build();
            mSetter = new ImageViewSetter(context, config);
            mPosterProcessor = new PosterProcessor(mPosterWidth, mPosterHeight/*, defaultIconColor*/);// , mSetter);
            mChainProcessor = new ChainProcessor(mSetter);
        }

        public void stopAndCleanup() {
            mSetter.stopLoadingAll();
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = mInflater.inflate(mLayoutId, null);
            ViewHolder vh = new ViewHolder();

            // Retrieve all views of the item layout (we don't know yet which part will be visible)
            vh.initial_item_container = (RelativeLayout) v.findViewById(R.id.initial_item_container);
            vh.initial_thumbnail = (ImageView) v.findViewById(R.id.initial_thumbnail);
            vh.initial_name = (TextView) v.findViewById(R.id.initial_name);
            vh.initial_duration = (TextView) v.findViewById(R.id.initial_duration);
            vh.initial_status = (TextView) v.findViewById(R.id.initial_status);
            vh.initial_spinbar = (ProgressBar) v.findViewById(R.id.initial_spinbar);

            vh.processed_item_container = (RelativeLayout) v.findViewById(R.id.processed_item_container);
            vh.processed_poster = (ImageView) v.findViewById(R.id.processed_poster);
            vh.processed_name = (TextView) v.findViewById(R.id.processed_name);
            vh.processed_duration = (TextView) v.findViewById(R.id.processed_duration);
            vh.processed_genre = (TextView) v.findViewById(R.id.processed_genre);
            vh.processed_date = (TextView) v.findViewById(R.id.processed_date);
            vh.processed_rating = (TextView) v.findViewById(R.id.processed_rating);
            vh.processed_reject_btn = (Button) v.findViewById(R.id.processed_reject_btn);

            if (!mIsLargeScreen) {
                vh.processed_reject_btn.setText(R.string.scrap_remove_short);
                vh.processed_date.setVisibility(View.GONE);
                vh.processed_rating.setVisibility(View.GONE);
            }

            // Allow the user to click on the reject button
            vh.processed_reject_btn.setOnClickListener(this);

            // For some reason I do not manage to have the accent color defined in the theme applied to this progress bar.
            // Doing it by hand then...
            if (Build.VERSION.SDK_INT >= 21 /*Build.VERSION_CODES.LOLLIPOP*/) {
                int accentColor = getResources().getColor(R.color.lightblue400);
                vh.initial_spinbar.getIndeterminateDrawable().setColorFilter(accentColor, android.graphics.PorterDuff.Mode.MULTIPLY);
            }

            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            // Default values
            int duration = 0;
            int status = ITEM_STATUS_INITIAL;
            int statusStringId = R.string.scrap_status_unknown;
            String genre = "";
            String date = "";
            String rating = "";

            ViewHolder vh = (ViewHolder) view.getTag();

            // Get the path of the item
            String path = cursor.getString(mAdapterDataIndex);
            String name = cursor.getString(mAdapterTitleIndex);

            // Get the properties corresponding to this item
            FileProperties itemProperties = mActivity.mFileProperties.get(path);
            if (itemProperties != null) {
                duration = itemProperties.duration;
                status = itemProperties.status;
                switch (status) {
                    case ITEM_STATUS_BUSY:
                        statusStringId = R.string.scrap_status_busy;
                        break;
                    case ITEM_STATUS_SUCCESS:
                        statusStringId = R.string.scrap_status_success;
                        genre = itemProperties.genre;
                        date = itemProperties.date;
                        rating = itemProperties.rating;
                        name = itemProperties.title;
                        break;
                    case ITEM_STATUS_FAILED:
                        statusStringId = R.string.scrap_status_failed;
                        break;
                    case ITEM_STATUS_REJECTED:
                        statusStringId = R.string.scrap_status_rejected;
                        break;
                    default:
                        statusStringId = R.string.scrap_status_initial;
                        break;
                }
            }

            // Display the item info depending on its status
            if (status == ITEM_STATUS_SUCCESS) {
                //---------------------------------------
                // Apply the processed items layout
                //---------------------------------------

                mSetter.set(vh.processed_poster, mChainProcessor,
                            ChainProcessor.newChain(mPosterProcessor, itemProperties.posterPath));

                vh.processed_name.setText(name);
                vh.processed_duration.setText((duration > 0) ? MediaUtils.formatTime(duration) : "");
                vh.processed_genre.setText(getResources().getString(R.string.scrap_genre) + ": " + genre);

                vh.processed_rating.setText(getResources().getString(R.string.scrap_rating) + ": " + rating);
                if (itemProperties != null && itemProperties.scraperType == BaseTags.MOVIE) {
                    vh.processed_date.setText(getResources().getString(R.string.scrap_year) + ": " + date);
                }
                else {
                    vh.processed_date.setText(getResources().getString(R.string.scrap_aired) + ": " + date);
                }

                // Show the layout of the processed items
                vh.initial_item_container.setVisibility(View.GONE);
                vh.processed_item_container.setVisibility(View.VISIBLE);

                // Allow the contextual menu for this item
                vh.processed_item_container.setClickable(true);
                vh.processed_item_container.setLongClickable(true);

                // Make sure the poster will have the right size
                ViewGroup.LayoutParams lp = vh.processed_poster.getLayoutParams();
                lp.width = mPosterWidth;
                lp.height = mPosterHeight;

            }
            else {
                //---------------------------------------
                // Apply the initial items layout
                //---------------------------------------
                vh.initial_thumbnail.setImageResource(R.drawable.filetype_video);
                vh.initial_name.setText(name);
                vh.initial_duration.setText((duration > 0) ? MediaUtils.formatTime(duration) : "");
                vh.initial_status.setText(statusStringId);

                // Show the initial layout of the items
                vh.initial_item_container.setVisibility(View.VISIBLE);
                vh.processed_item_container.setVisibility(View.GONE);

                // Make sure the thumbnail will have the right size
                ViewGroup.LayoutParams lp = vh.initial_thumbnail.getLayoutParams();
                lp.width = mThumbnailWidth;
                lp.height = mThumbnailHeight;
            }

            // Show or hide the search spinbar
            vh.initial_spinbar.setVisibility((status == ITEM_STATUS_BUSY) ? View.VISIBLE : View.GONE);
        }

        /**
        * Handle clicks on the revert buttons
        */
        public void onClick(View view) {
            int position = mActivity.mListView.getPositionForView(view);
            if (DBG) Log.d(TAG, "onClick : position=" + position);
            rejectScraperInfos(position);
        }
    }


    //*****************************************************************************
    // Scraper task
    //*****************************************************************************

    private class ScraperResultTask extends AsyncTask<Void, Integer, Integer> {
        AutoScraperActivity mActivity;

        public ScraperResultTask(AutoScraperActivity currentActivity) {
            super();
            mActivity = currentActivity;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            NfoWriter.ExportContext exportContext;
            if (NfoWriter.isNfoAutoExportEnabled(AutoScraperActivity.this)) {
                exportContext = new NfoWriter.ExportContext();
            } else {
                exportContext = null;
            }

            int fileIndex;
            if (DBG) Log.d(TAG, "ScraperResultTask : starting scraper automation process");
            for (fileIndex = 0; fileIndex < mFileCount; fileIndex++) {
                String path = mFileList.get(fileIndex);
                if (DBG) Log.d(TAG, "processing file " + fileIndex + " = " + path);

                // Display the status of the file to process as "busy"
                FileProperties itemProperties = mFileProperties.get(path);
                itemProperties.status = ITEM_STATUS_BUSY;

                Uri file = Uri.parse(path);
                SearchInfo searchInfo = null;
                if (file != null) {
                    searchInfo = SearchPreprocessor.instance().parseFileBased(file, null);
                    itemProperties.title = searchInfo.getSearchSuggestion();
                } else {
                    continue;
                }

                publishProgress(Integer.valueOf(fileIndex), Integer.valueOf(itemProperties.status));

                // Try to retrieve the poster and the tags for this file
                BaseTags tags = null;
                ScrapeDetailResult details = mScraper.getAutoDetails(searchInfo);
                tags = details.tag;

                int typeId = -1;

                if (tags != null) {
                    // Use the retrieved tags to update the item properties
                    if (tags instanceof MovieTags) {
                        // The retrieved tags correspond to a movie
                        typeId = BaseTags.MOVIE;
                        MovieTags movieTags = (MovieTags)tags;

                        // Update the FileProperties of the item
                        itemProperties.scraperType = typeId;
                        mFileProperties.put(path, itemProperties);

                        String ttl = movieTags.getTitle();
                        if (ttl != null && !ttl.isEmpty()) {
                            itemProperties.title = ttl;
                        }
                        if (movieTags.getCover() != null) {
                            itemProperties.posterPath = movieTags.getCover().getPath();
                        }
                        if (movieTags.getRating() != 0.0f) {
                            itemProperties.rating = String.valueOf(movieTags.getRating());
                        }
                        itemProperties.date = String.valueOf(movieTags.getYear());
                        if (movieTags.getGenres().size() > 0) {
                            // Append all the genres as a single string
                            itemProperties.genre = TextUtils.join(", ", movieTags.getGenres());
                        }
                    }
                    else if (tags instanceof EpisodeTags) {
                        EpisodeTags epTags = (EpisodeTags) tags;
                        // The retrieved tags correspond to a TV show
                        typeId = BaseTags.TV_SHOW;
                        ShowTags showTags = epTags.getShowTags();

                        // Update the FileProperties of the item
                        itemProperties.scraperType = typeId;
                        mFileProperties.put(path, itemProperties);

                        String ttl = showTags.getTitle();
                        int epSeason = epTags.getSeason();
                        int epEpisode = epTags.getEpisode();
                        if (ttl != null && !ttl.isEmpty()) {
                            if (epSeason > 0 && epEpisode > 0) {
                                ttl = String.format(EPISODE_FORMAT, ttl, epSeason, epEpisode);
                            }
                            itemProperties.title = ttl;
                        }
                        if (tags.getCover() != null) {
                            itemProperties.posterPath = tags.getCover().getPath();
                        } else if (showTags.getCover() != null) {
                            itemProperties.posterPath = showTags.getCover().getPath();
                        }
                        if (showTags.getRating() != 0.0f) {
                            itemProperties.rating = String.valueOf(showTags.getRating());
                        }

                        EpisodeTags episodeTags = (EpisodeTags)tags;
                        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
                        if (episodeTags.getAired() != null && episodeTags.getAired().getTime() > 0) {
                            // Display the aired date if available and valid
                            itemProperties.date = df.format(episodeTags.getAired());
                        }
                        else if (showTags.getPremiered() != null && showTags.getPremiered().getTime() > 0) {
                            // Aired date not available => try at least the premiered date
                            itemProperties.date = df.format(showTags.getPremiered());
                        }

                        if (showTags.getGenres().size() > 0) {
                            // Append all the genres as a single string
                            itemProperties.genre = TextUtils.join(", ", showTags.getGenres());
                        }
                    }

                    if (typeId != -1) {
                        // Valid tags => update the scraper database
                        // Note: This will do the updateScraperInfoInMediaLib stuff for us
                        tags.save(AutoScraperActivity.this, itemProperties.id);

                        // TODO make this nicer.
                        if (exportContext != null) {
                            // also auto-export all the data
                            Uri videoFile = Uri.parse(path);
                            if (videoFile != null) {
                                try {
                                    NfoWriter.export(videoFile, tags, exportContext);
                                } catch (IOException e) {
                                    Log.w(TAG, e);
                                }
                            }
                        }
                    }
                }

                // Update the medialib depending on the results
                if (typeId != -1) {
                    itemProperties.status = ITEM_STATUS_SUCCESS;
                }
                else {
                    // Failed => set the scraper fields to -1 so that we will be able
                    // to skip this file when launching the automated process again
                    if (DBG) Log.d(TAG, "failed => update medialib with scraperId=-1");
                    itemProperties.status = ITEM_STATUS_FAILED;
                    mActivity.updateScraperInfoInMediaLib(path, -1, -1);
                }

                // Display the updated info and status of the processed file
                publishProgress(Integer.valueOf(fileIndex), Integer.valueOf(itemProperties.status));

                if (isCancelled()) {
                    // Exit the task
                    if (DBG) Log.d(TAG, "ScraperResultTask : task aborted");
                    return Integer.valueOf(0);
                }
            }

            if (DBG) Log.d(TAG, "ScraperResultTask : all files processed");
            return Integer.valueOf(1);
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            int fileIndex = progress[0].intValue();
            int status = progress[1].intValue();
            if (DBG) Log.d(TAG, "onProgressUpdate : updating item " + fileIndex);

            // Update the display with the retrieved poster and infos
            mActivity.invalidateItem(fileIndex);

            // NOTE: for display reasons the list is scrolled:
            // - when starting to process an item in focus mode (remote control or keyboard)
            // - after processing an item in touch mode
            if (status == ITEM_STATUS_BUSY) {
                //----------------------------------------------------------------------------------------
                // Smart automatic scrolling of the list in focus mode:
                //
                // Try to scroll the list so that the item beeing processed is displayed at the bottom of the list
                // and stop scrolling as soon as the user starts to navigate with the UP or DOWN keys
                //----------------------------------------------------------------------------------------
                if (!mScrollingWithKeys && !mActivity.mListView.isInTouchMode()) {
                    int firstVisibleItemPosition = mActivity.mListView.getFirstVisiblePosition();
                    int lastVisibleItemPosition = mActivity.mListView.getLastVisiblePosition();

                    if (firstVisibleItemPosition == 0 && mFilesProcessed <= lastVisibleItemPosition) {
                        // The list has not been scrolled yet and the item beeing processed is still visible
                        // => no need to scroll yet, so keep the first item selected and position it at the top of the list
                        mActivity.mListView.setSelectionFromTop(0, 0);
                    }
                    else {
                        // The list has started to scroll or the next item to process is not visible
                        // => select the next item to process and position it at the bottom of the list
                        int listViewHeight = mActivity.mListView.getHeight();
                        int initialItemHeight = mActivity.mAdapter.mThumbnailHeight;
                        mActivity.mListView.setSelectionFromTop(mFilesProcessed, listViewHeight - initialItemHeight);
                    }
                }
            }
            else {
                // A file has been processed => update the number of processed files
                mFilesProcessed = fileIndex + 1;
                boolean taskDone = (mFilesProcessed >= mFileCount || isCancelled());

                if (!taskDone) {
                    int firstVisibleItemPosition = mActivity.mListView.getFirstVisiblePosition();
                    int lastVisibleItemPosition = mActivity.mListView.getLastVisiblePosition();

                    //--------------------------------------------------------------------------
                    // Smart automatic scrolling of the list in touch mode:
                    //
                    // Make the next item to process visible if the current one is visible
                    // and the user is not currently scrolling the list.
                    //--------------------------------------------------------------------------
                    if (mActivity.mListView.isInTouchMode()) {
                        // Check if the item which was just processed is visible
                        boolean isPreviousItemVisible = (fileIndex >= firstVisibleItemPosition && fileIndex <= lastVisibleItemPosition);

                        // Check if the user is already scrolling the list
                        boolean isUserScrolling = (mActivity.mMyScrollState == MY_SCROLL_STATE_USER);
                        boolean isAutoScrolling = (mActivity.mMyScrollState == MY_SCROLL_STATE_AUTO);

                        // NOTE: smoothScrollToPosition() takes some time so when video has been processed very quickly
                        // we may start processing the next one before scrolling is stopped. In that case 
                        // firstVisibleItemPosition and lastVisibleItemPosition are not updated yet => check  
                        // isAutoScrolling to make sure to keep on scrolling anyway if needed
                        if ((isPreviousItemVisible && !isUserScrolling) || isAutoScrolling) {
                            // Scroll the list if needed to make the current item visible
                            mActivity.mListView.smoothScrollToPosition(mFilesProcessed);

                            // Assume we are now in auto scroll mode even if no scrolling is applied
                            // and onScrollStateChanged() is not called
                            mActivity.mMyScrollState = MY_SCROLL_STATE_AUTO;
                        }
                    }
                }
                else if (!mActivity.mListView.isInTouchMode()) {
                    // All done in focus mode => select the last item to make sure it is fully visible
                    // in case a poster was found (otherwise only the upper part would be visible)
                    mActivity.mListView.setSelection(mFilesProcessed - 1);
                }

                if (mNotification != null) {
                    // Handle the statusbar notification
                    if (taskDone) {
                        // Search done or cancelled => remove the notification
                        removeStatusbarNotification();
                    } else {
                        // Search in progress => update the display of the number of files processed
                        updateStatusbarNotification();
                    }
                }
            }
        }

        /**
        * This method is only called when doInBackground() finishes normally
        */
        @Override
        protected void onPostExecute(Integer result) {
            // All files have been processed
            if (mInBackground) {
                // The activity is running in the background => just exit it
                finish();
            }
            else {
                // The activity is visible => update the control buttons
                updateControlButtons(true);

                // Processing is done, no need to keep the screen dimmed anymore
                // in case the user doesn't close the activity immediately
                mWakeLock.release();
            }
        }
    }
}
