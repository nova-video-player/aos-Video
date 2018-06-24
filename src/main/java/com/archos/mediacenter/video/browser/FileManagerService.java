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

package com.archos.mediacenter.video.browser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import com.archos.filecorelibrary.CopyCutEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.OperationEngineListener;
import com.archos.mediacenter.video.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class FileManagerService extends Service implements OperationEngineListener{

    private static final String TAG = "FileManagerService";
    private ArrayList<MetaFile2> mProcessedFiles = null;
    private IBinder localBinder;
    private static final int PASTE_NOTIFICATION_ID = 1;
    private static final int OPEN_NOTIFICATION_ID = 2;
    private static String OPEN_AT_THE_END_KEY= "open_at_the_end_key";
    public static FileManagerService fileManagerService = null;

    private NotificationManager mNotificationManager;
    private Builder mNotificationBuilder;
    private long mPasteTotalSize = 0;
    private int mCurrentFile = 0;
    private BroadcastReceiver receiver;
    private long mLastUpdate = 0;
    private long mLastStatusBarUpdate =0;
    private boolean mOpenAtTheEnd;
    private boolean mHasOpenAtTheEndBeenSet;
    private ActionStatusEnum mLastStatus;
    private HashMap<MetaFile2, Long> mProgress;
    private long mPasteTotalProgress;
    private CopyCutEngine mCopyCutEngine;
    private ArrayList<ServiceListener> mListeners;
    private boolean mIsActionRunning;
    private Uri mTarget;
    private PowerManager.WakeLock mWakeLock;


    public enum FileActionEnum {
        NONE, COPY, CUT, DELETE, COMPRESSION, EXTRACTION
    };

    public enum ActionStatusEnum {
        PROGRESS, START, STOP, CANCELED, ERROR, NONE
    };

    public interface  ServiceListener {
        void onActionStart();
        void onActionStop();
        void onActionError();
        void onActionCanceled();
        void onProgressUpdate();
    }


    public FileManagerService() {
        super();
        fileManagerService = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mLastStatus = ActionStatusEnum.NONE;
        localBinder = new FileManagerServiceBinder();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mOpenAtTheEnd = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(OPEN_AT_THE_END_KEY, true);
        mHasOpenAtTheEndBeenSet = false;
        mListeners = new ArrayList<>();
        mProcessedFiles = new ArrayList<>();
        mProgress = new HashMap<>();
        mCopyCutEngine = new CopyCutEngine(this);
        mCopyCutEngine.setListener(this);
        mIsActionRunning = false;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction()!=null&&intent.getAction().equals("CANCEL"))
                    stopPasting();
                else if(intent.getAction()!=null&&intent.getAction().equals("OPEN"))
                    openLastFile();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("CANCEL");
        filter.addAction("OPEN");
        registerReceiver(receiver, filter);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    private void openLastFile() {
        if(mTarget!=null&& mProcessedFiles !=null&& mProcessedFiles.size()==1&& mProcessedFiles.get(0).isFile()){

            mNotificationManager.cancel(OPEN_NOTIFICATION_ID);
            Uri uri = Uri.withAppendedPath(mTarget, mProcessedFiles.get(0).getName());
            String extension = "*";
            if(uri.getLastPathSegment().contains(".")&&!uri.getLastPathSegment().endsWith(".")){
                extension = uri.getLastPathSegment().substring(uri.getLastPathSegment().lastIndexOf(".")+1);
            }

            String mimeType = mProcessedFiles.get(0).getMimeType() !=null && !mProcessedFiles.get(0).getMimeType().isEmpty() ? mProcessedFiles.get(0).getMimeType() : "*/" + extension;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (uri != null) {
                intent.setDataAndType(uri, mimeType);
            } else {
                Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            }
        }

    }

    /**
     * progress hashmap wasn't in the right order when iterating, this one represents the real copy order
     * list of the files being operated by copy/delete/cut
     * @return
     */
    public List<MetaFile2> getFilesToPaste() {
        return mProcessedFiles;
    }

    /**
     * return how many files are currently being operated
     * @return
     */
    public int getPasteTotalFiles() {
        return mProcessedFiles.size();
    }

    /**
     * give the index of current file in list getFilesToPaste
     * @return
     */
    public int getCurrentFile() {
        return mCurrentFile;
    }

    /**
     * give the total size (useful when copying multiple files). Size in B
     * @return
     */
    public long getPasteTotalSize() {
        return mPasteTotalSize;
    }

    /**
     * give the total progress (useful when copying multiple files). Size in B
     * @return
     */
    public long getPasteTotalProgress() {
        return mPasteTotalProgress;
    }

    /**
     * Get progress by file. Size in B
     * @return
     */
    public  HashMap<MetaFile2, Long> getFilesProgress() {
        return mProgress;
    }


    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(receiver);
    }


    public void copy(List<MetaFile2> FilesToPaste, Uri target) {
        if(!mIsActionRunning){
            mIsActionRunning = true;
            mTarget = target;
            mHasOpenAtTheEndBeenSet = false;
            mProcessedFiles = new ArrayList<MetaFile2>();
            mProcessedFiles.addAll(FilesToPaste);
            mProgress.clear();
            mPasteTotalSize = (long) 0;
            for (MetaFile2 mf : mProcessedFiles) {
                mProgress.put(mf,(long) 0);
                mPasteTotalSize += mf.length();
            }
            ArrayList<MetaFile2> sources = new ArrayList<MetaFile2>(mProcessedFiles.size());
            sources.addAll(mProcessedFiles);
            mCopyCutEngine.copy(sources, target, false);
            startStatusbarNotification();
        }
    }

    public void copyUri(List<Uri> FilesToPaste, Uri target) {
        if(!mIsActionRunning){
            mIsActionRunning = true;
            mTarget = target;
            mHasOpenAtTheEndBeenSet = false;
            mProgress.clear();
            mPasteTotalSize = (long) 0;
            ArrayList<Uri> sources = new ArrayList<Uri>(FilesToPaste.size());
            sources.addAll(FilesToPaste);
            mCopyCutEngine.copyUri(sources, target, false);
            startStatusbarNotification();
        }
    }



    /*
     * is currently pasting files
     * 
     */
    public boolean isPastingInProgress() {
        return mIsActionRunning;
    }


    public void addListener(ServiceListener listener) {
        if(!mListeners.contains(listener))
            mListeners.add(listener);
    }

    private PendingIntent getCancelIntent() {
        Intent intent = new Intent("CANCEL");
        return PendingIntent.getBroadcast(this, 0, intent, 0);
    }

    private Intent getOpenIntent() {
        Intent intent = new Intent("OPEN");
        return intent;
    }

    private void updateStatusbarNotification(String text) {
        if (text != null) {
            // Update the notification text
            mNotificationBuilder.setContentText(text);
        }

        // Tell the notification manager about the changes
        mNotificationManager.notify(PASTE_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    protected void removeStatusbarNotification() {
        if (mNotificationBuilder != null) {
            mNotificationManager.cancel(PASTE_NOTIFICATION_ID);
            mNotificationBuilder = null;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return localBinder;
    }

    public class FileManagerServiceBinder extends Binder {
        public FileManagerService getService() {
            return FileManagerService.this;
        }
    }

    public void stopPasting() {
        if(mIsActionRunning){
             if(mCopyCutEngine!=null)
                  mCopyCutEngine.stop();

        }
    }

    public void deleteObserver(ServiceListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void onStart() {
        acquireWakeLock();
        mLastStatus = ActionStatusEnum.START;
        mIsActionRunning = true;
        startStatusbarNotification();
        long totalSize = 0;
        for (MetaFile2 mf : mProcessedFiles) {
            totalSize  += mf.length();
        }
        updateStatusbarNotification(0, totalSize, 0, mProcessedFiles.size());
        for(ServiceListener fl :mListeners){
            fl.onActionStart();
        }
    }

    private void acquireWakeLock() {

        releaseWakeLock();
        Log.d(TAG, "acquireWakeLock");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FileManagerWakeLock");
        mWakeLock.acquire();
    }
    private void releaseWakeLock(){
        Log.d(TAG, "releaseWakeLock");
        if(mWakeLock!=null&&mWakeLock.isHeld())
            mWakeLock.release();
    }
    @Override
    public void onEnd() {
        releaseWakeLock();
        mLastStatus = ActionStatusEnum.STOP;
        mIsActionRunning = false;
        removeStatusbarNotification();
        if (mHasOpenAtTheEndBeenSet) {
            if (mOpenAtTheEnd) {
                openLastFile();
            } else {
                displayOpenFileNotification();
            }
        }
            // Copy => don't reset the paste mode when done so that the current
            // selection can be pasted again somewhere else if needed
            String message;
            // Copy successful
            if (mProcessedFiles.size() == 1 && mProcessedFiles.get(0).isDirectory())
                message = getResources().getString(R.string.copy_directory_success_one);
            else
                message = getResources().getString(R.string.copy_file_success_one);

            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        for(ServiceListener fl :mListeners){
            fl.onActionStop();
        }
    }

    @Override
    public void onFatalError(Exception e) {
        releaseWakeLock();
        Toast.makeText(this, com.archos.filecorelibrary.R.string.copy_file_failed_one, Toast.LENGTH_LONG).show();
        mLastStatus = ActionStatusEnum.ERROR;
        mIsActionRunning = false;
        removeStatusbarNotification();
        for (ServiceListener lis : mListeners){
            lis.onActionError();
        }
    }


    @Override
    public void onProgress(int currentFile, long currentFileProgress,int currentRootFile, long currentRootFileProgress, long totalProgress, double currentSpeed) {
        mLastStatus = ActionStatusEnum.PROGRESS;
        if(currentFile< mProcessedFiles.size()){
            mProgress.put(mProcessedFiles.get(currentFile), currentFileProgress);
        }
        mCurrentFile = currentFile;
        mPasteTotalProgress = totalProgress;
        if(System.currentTimeMillis()-mLastUpdate>200){
            mLastUpdate = System.currentTimeMillis();
            notifyListeners();
        }
        if(System.currentTimeMillis()-mLastStatusBarUpdate>1000){ //updating too often would prevent user from touching the cancel button
            mLastStatusBarUpdate = System.currentTimeMillis();
            updateStatusbarNotification(totalProgress, getPasteTotalSize(), currentFile + 1, mProcessedFiles.size());
        }
    }

    private void notifyListeners() {
        for (ServiceListener lis : mListeners){
            lis.onProgressUpdate();
        }
    }

    @Override
    public void onFilesListUpdate(List<MetaFile2> copyingMetaFiles,List<MetaFile2> rootFiles) {
        mProcessedFiles.clear();
        mProcessedFiles.addAll(copyingMetaFiles);
        mProgress.clear();
        mPasteTotalSize = (long) 0;
        mPasteTotalProgress = 0;
        for(MetaFile2 mf : mProcessedFiles){
            mProgress.put(mf, (long) 0);
            mPasteTotalSize+=mf.length();
        }
        long totalSize = 0;
        for(MetaFile2 mf : mProcessedFiles){
            totalSize  += mf.length();
        }
        updateStatusbarNotification(0, totalSize, 0, mProcessedFiles.size());
    }

    /* Notification */

    private static final String notifChannelId = "FileManagerService_id";
    private static final String notifChannelName = "FileManagerService";
    private static final String notifChannelDescr = "FileManagerService";
    public void startStatusbarNotification() {
        mNotificationManager.cancel(OPEN_NOTIFICATION_ID);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int message =  R.string.copying;
        CharSequence title = getResources().getText(message);
        long when = System.currentTimeMillis();

        // Build the intent to send when the user clicks on the notification in the notification panel
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(MainActivity.LAUNCH_DIALOG);

        // Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel mNotifChannel = new NotificationChannel(notifChannelId, notifChannelName,
                    mNotificationManager.IMPORTANCE_LOW);
            mNotifChannel.setDescription(notifChannelDescr);
            if (mNotificationManager != null)
                mNotificationManager.createNotificationChannel(mNotifChannel);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        // Create a new notification builder
        mNotificationBuilder = new NotificationCompat.Builder(this, notifChannelId);
        int icon = R.mipmap.video2;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon = R.drawable.video2; //special white icon for lollipop
        }
        mNotificationBuilder.setSmallIcon(icon);
        mNotificationBuilder.setTicker(null);

        mNotificationBuilder.setOnlyAlertOnce(true);
        mNotificationBuilder.setContentTitle(title);
        mNotificationBuilder.setContentIntent(contentIntent);
        mNotificationBuilder.setWhen(when);
        mNotificationBuilder.setOngoing(true);
        mNotificationBuilder.setDefaults(0); // no sound, no light, no vibrate

        // Set the info to display in the notification panel and attach the notification to the notification manager
        updateStatusbarNotification(null);
        mNotificationManager.notify(PASTE_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void displayOpenFileNotification() {
        Intent notificationIntent = getOpenIntent();
        int icon =  R.mipmap.video2;
        CharSequence title = getResources().getText(R.string.open_file);
        long when = System.currentTimeMillis();
        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);
        Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setSmallIcon(icon);
        notificationBuilder.setTicker(null);
        notificationBuilder.setOnlyAlertOnce(true);
        notificationBuilder.setContentTitle(title);
        notificationBuilder.setContentText(mProcessedFiles.get(0).getName());
        notificationBuilder.setContentIntent(contentIntent);
        notificationBuilder.setWhen(when);
        notificationBuilder.setDefaults(0); // no sound, no light, no vibrate
        mNotificationManager.notify(OPEN_NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateStatusbarNotification(long currentSize, long totalSize, int currentFiles, int totalFiles) {

        if (mNotificationBuilder != null) {
            String formattedCurrentSize = Formatter.formatShortFileSize(this, currentSize);
            String formattedTotalSize = Formatter.formatShortFileSize(this, totalSize);
            int textId;
            String formattedString;
            if (totalFiles <= 0) {
                textId = R.string.pasting_copy_one;
                // Display the progress in bytes only
                formattedString = getResources().getString(textId, formattedCurrentSize, formattedTotalSize);
            }
            else {
                textId = R.string.pasting_copy_many;
                // Display the progress in number of files and bytes
                formattedString = getResources().getString(textId, currentFiles, totalFiles,
                        formattedCurrentSize, formattedTotalSize);
            }
            mNotificationBuilder.setProgress((int)totalSize, (int)currentSize, false);
            updateStatusbarNotification(formattedString);
        }
    }
    private void setCanceledStatus(){
        mLastStatus = ActionStatusEnum.CANCELED;
        mIsActionRunning = false;
        removeStatusbarNotification();
        for (ServiceListener lis : mListeners){
            lis.onActionCanceled();
        }
        mNotificationManager.cancel(OPEN_NOTIFICATION_ID);
    }
    @Override
    public void onCanceled() {
        releaseWakeLock();
        Toast.makeText(this, com.archos.filecorelibrary.R.string.copy_file_failed_one, Toast.LENGTH_LONG).show();
        setCanceledStatus();
    }

    @Override
    public void onSuccess(Uri target) {}


}
