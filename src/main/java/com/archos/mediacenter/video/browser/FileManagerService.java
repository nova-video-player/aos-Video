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

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
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
    private static final boolean DBG = false;

    private ArrayList<MetaFile2> mProcessedFiles = null;
    private IBinder localBinder;
    private static String OPEN_AT_THE_END_KEY= "open_at_the_end_key";
    public static FileManagerService fileManagerService = null;

    private static final int PASTE_NOTIFICATION_ID = 9;
    private static final int OPEN_NOTIFICATION_ID = 10;
    private NotificationManager nm;
    private NotificationCompat.Builder nb;
    private static final String notifChannelId = "FileManagerService_id";
    private static final String notifChannelName = "FileManagerService";
    private static final String notifChannelDescr = "FileManagerService";

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
        if (DBG) Log.d(TAG, "FileManagerService: setting fileManagerService not to null");
        fileManagerService = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DBG) Log.d(TAG, "onCreate: creating notification channel first");

        // need to do that early to avoid ANR on Android 26+
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(notifChannelId, notifChannelName,
                    nm.IMPORTANCE_LOW);
            nc.setDescription(notifChannelDescr);
            if (nm != null)
                nm.createNotificationChannel(nc);
        }
        nb = new NotificationCompat.Builder(this, notifChannelId)
                .setSmallIcon(R.mipmap.nova)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setTicker(null).setOnlyAlertOnce(true).setOngoing(true).setAutoCancel(true);
        startForeground(PASTE_NOTIFICATION_ID, nb.build());

        mLastStatus = ActionStatusEnum.NONE;
        localBinder = new FileManagerServiceBinder();
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
        if (DBG) Log.d(TAG, "onStartCommand");
        startForeground(PASTE_NOTIFICATION_ID, nb.build());
        return START_NOT_STICKY;
    }

    private void openLastFile() {
        if(mTarget!=null&& mProcessedFiles !=null&& mProcessedFiles.size()==1&& mProcessedFiles.get(0).isFile()){

            nm.cancel(OPEN_NOTIFICATION_ID);
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
        if (DBG) Log.d(TAG, "onDestroy: removing Foreground notif and stopping self");
        setCanceledStatus();
    	super.onDestroy();
    	unregisterReceiver(receiver);
        stopService();
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
        return PendingIntent.getBroadcast(this, 0, intent,
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT: PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private Intent getOpenIntent() {
        Intent intent = new Intent("OPEN");
        return intent;
    }

    private void updateStatusbarNotification(String text) {
        if (text != null) {
            // Update the notification text
            nb.setContentText(text);
        }
        // Tell the notification manager about the changes
        nm.notify(PASTE_NOTIFICATION_ID, nb.build());
    }

    protected void removeStatusbarNotification() {
        if (nb != null) {
            nm.cancel(PASTE_NOTIFICATION_ID);
            //nb = null;
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
        if (DBG) Log.d(TAG, "stopPasting");
        if(mIsActionRunning){
             if(mCopyCutEngine!=null)
                  mCopyCutEngine.stop();
        }
    }

    public void stopService() {
        if (DBG) Log.d(TAG, "stopService");
        stopForeground(true);
    }

    public static void startService(Context context) {
        ContextCompat.startForegroundService(context, new Intent(context, FileManagerService.class));
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
        if (DBG) Log.d(TAG, "acquireWakeLock");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nova:FileManagerWakeLock");
        mWakeLock.acquire();
    }
    private void releaseWakeLock(){
        if (DBG) Log.d(TAG, "releaseWakeLock");
        if(mWakeLock!=null&&mWakeLock.isHeld())
            mWakeLock.release();
    }
    @Override
    public void onEnd() {
        if (DBG) Log.d(TAG, "onEnd: releasing wakelock, removing Foreground notif and stopping self");
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
        stopService();
    }

    @Override
    public void onFatalError(Exception e) {
        if (DBG) Log.d(TAG, "onFatalError: releasing wakelock, removing Foreground notif and stopping self");
        releaseWakeLock();
        Toast.makeText(this, com.archos.filecorelibrary.R.string.copy_file_failed_one, Toast.LENGTH_LONG).show();
        mLastStatus = ActionStatusEnum.ERROR;
        mIsActionRunning = false;
        removeStatusbarNotification();
        for (ServiceListener lis : mListeners){
            lis.onActionError();
        }
        stopService();
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
        if (DBG) Log.d(TAG, "onFilesListUpdate");
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

    public void startStatusbarNotification() {
        if (DBG) Log.d(TAG, "startStatusbarNotification: stopping OPEN_NOTIFICATION_ID notif and doing PASTE_NOTIFICATION_ID");
        nm.cancel(OPEN_NOTIFICATION_ID);

        // Build the intent to send when the user clicks on the notification in the notification panel
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(MainActivity.LAUNCH_DIALOG);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT: PendingIntent.FLAG_UPDATE_CURRENT));

        nb.setContentTitle(getText(R.string.copying))
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis());
        // Set the info to display in the notification panel and attach the notification to the notification manager
        updateStatusbarNotification(null);
        nm.notify(PASTE_NOTIFICATION_ID, nb.build());
    }

    private void displayOpenFileNotification() {
        if (DBG) Log.d(TAG, "displayOpenFileNotification");
        nb.setContentTitle(getText(R.string.open_file))
                .setContentText(mProcessedFiles.get(0).getName())
                .setWhen(System.currentTimeMillis())
                .setContentIntent(PendingIntent.getBroadcast(this, 0, getOpenIntent(),
                        ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT: PendingIntent.FLAG_UPDATE_CURRENT)));
        nm.notify(OPEN_NOTIFICATION_ID, nb.build());
    }

    private void updateStatusbarNotification(long currentSize, long totalSize, int currentFiles, int totalFiles) {
        if (DBG) Log.d(TAG, "updateStatusbarNotification");
        if (nb != null) {
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
            nb.setProgress((int)totalSize, (int)currentSize, false);
            updateStatusbarNotification(formattedString);
        }
    }
    private void setCanceledStatus(){
        if (DBG) Log.d(TAG, "setCanceledStatus");
        mLastStatus = ActionStatusEnum.CANCELED;
        mIsActionRunning = false;
        removeStatusbarNotification();
        for (ServiceListener lis : mListeners){
            lis.onActionCanceled();
        }
        removeStatusbarNotification();
        nm.cancel(OPEN_NOTIFICATION_ID);
    }
    @Override
    public void onCanceled() {
        if (DBG) Log.d(TAG, "onCanceled");
        releaseWakeLock();
        Toast.makeText(this, com.archos.filecorelibrary.R.string.copy_file_failed_one, Toast.LENGTH_LONG).show();
        setCanceledStatus();
        stopService();
    }

    @Override
    public void onSuccess(Uri target) {}


}
