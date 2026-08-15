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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import com.archos.mediacenter.video.utils.TorrentPathDialogPreference;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StatFs;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TorrentObserverService extends Service implements DefaultLifecycleObserver {

    private static final Logger log = LoggerFactory.getLogger(TorrentObserverService.class);
    private static volatile TorrentObserverService sInstance;

    private static final String DEFAULT_TORRENT_PATH = "/sdcard/";
    public static final String BLOCKLIST = "blocklist";
    private Context mContext;
    private  String mTorrent;
    ArrayList<String> files ;
    TorrentThreadObserver mObserver;
    private static boolean isDaemonRunning;
    private static Process sProcess;
    private IBinder binder ;
    private Thread mTorrentThread;
    private boolean hasToStop=false;
    private Integer mPort;
    private boolean mHasSetFiles = false;
    private int mSelectedFile =-1;
    private BufferedReader mReader;
    private String mBlockList;

    public class TorrentServiceBinder extends Binder{
        public TorrentObserverService getService(){
            return TorrentObserverService.this;
        }

    }
    public TorrentObserverService(){
        super();
        isDaemonRunning=false;
        binder = new TorrentServiceBinder();
        sProcess =null;
        mObserver = null;
        mBlockList="";
    }

    public void setParameters( String torrent, int selectedFile){
        mContext = getBaseContext();
        mTorrent = torrent;
        mBlockList = getFilesDir()+"/"+BLOCKLIST;
    }

    public static interface TorrentThreadObserver{
        public void setFilesList(ArrayList<String>files);
        /**
         * daemon port changes everytime it is launched
         */
        public void setPort(int valueOf);
        /**
         * is called as soon as a file has been selected
         */
        public void notifyDaemonStreaming();
        /**
         * Called when daemon is dead
         * 
         */
        public void onEndOfTorrentProcess();
        /**
         * send the output to the observer
         * 
         */
        
        public void notifyObserver(String daemonString);

        public void warnOnNotEnoughSpace();

    }

    public final static String intentPaused = "activity.paused";
    public final static String intentResumed = "activity.resumed";

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        // Register as a lifecycle observer
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if(i==null)
            return START_STICKY;
        if (log.isDebugEnabled()) log.debug("Got intent {}", i.getAction());
        if(intentPaused.equals(i.getAction())) {
            _paused();
        } else if(intentResumed.equals(i.getAction())) {
            _resumed();
        }
        return START_STICKY;
    }

    static public void paused(Context ctxt) {
        if (log.isDebugEnabled()) log.debug("Sending paused intent");
        TorrentObserverService service = sInstance;
        if (service != null) {
            service._paused();
        }
    }

    static public void resumed(Context ctxt) {
        if (log.isDebugEnabled()) log.debug("Sending resumed intent");
        TorrentObserverService service = sInstance;
        if (service == null) {
            return;
        }
        service._resumed();

        // Keep the bound torrent service alive across the loader/player binding handoff. Do not
        // include the resume action because it was delivered directly above.
        Intent i = new Intent(ctxt.getApplicationContext(), TorrentObserverService.class);
        try {
            ctxt.getApplicationContext().startService(i);
        } catch (IllegalStateException e) {
            // An activity can reach onResume before Android marks the app as foreground. The
            // existing bound service has already received the resume signal, so this is harmless.
            log.warn("Unable to keep torrent observer started while app is considered background: {}", e.getMessage());
        }
    }

    public void setObserver(TorrentThreadObserver observer){
        this.mObserver = observer;
        if(mPort!=null&&mObserver!= null&&mPort>0){
            mObserver.setPort(mPort);
        }
        if(mObserver!= null&&files!=null&&files.size()>0){
            mObserver.setFilesList(files);
        }
    }
    public void selectFile(int filePostion){
        mSelectedFile = filePostion;
        if(sProcess !=null&&!mHasSetFiles){
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sProcess.getOutputStream()));
                writer.write(String.valueOf(filePostion)+"\n");
                writer.flush();
                mHasSetFiles=true;
                if(mObserver!=null)
                    mObserver.notifyDaemonStreaming();
                //observeStdout();
            } catch (IOException e) {
                log.error("selectFile: caught IOException, error writing", e);
            }
        }
    }
    public void start(){
        mTorrentThread = new Thread(){
            public void run(){
                //If we start a new torrent, but we have a pending quit/kill
                //quit/kill now, and drops them from the queue
                if(mHandler.hasMessages(MSG_QUIT) || mHandler.hasMessages(MSG_KILL)) {
                    exitProcess();
                    killProcess();
                    mHandler.removeMessages(MSG_QUIT);
                    mHandler.removeMessages(MSG_KILL);
                }
                
                if(!isDaemonRunning&&mContext!=null){
                    try{
                        hasToStop=false;
                        mHasSetFiles = false;
                        killProcess();
                        files = new ArrayList<String>();

                        //Use app's internal files directory as working directory (Android 11+ compatibility)
                        //This allows native library execution while keeping video files in user's preferred location
                        File torrentWorkingDir = new File(mContext.getFilesDir(), "torrents");
                        if (!torrentWorkingDir.exists()) {
                            torrentWorkingDir.mkdirs();
                        }

                        String torrentDownloadPath = TorrentPathDialogPreference.getDefaultDirectory(
                                PreferenceManager.getDefaultSharedPreferences(mContext)).getAbsolutePath();

                        // Use native library directory (now that extractNativeLibs="true")
                        String nativeLibDir = mContext.getApplicationInfo().nativeLibraryDir;
                        String torrentBinary = nativeLibDir + "/libtorrentd.so";

                        if (log.isDebugEnabled()) log.debug("Using torrent binary: {}", torrentBinary);
                        if (log.isDebugEnabled()) log.debug("Native library dir: {}", nativeLibDir);
                        if (log.isDebugEnabled()) log.debug("Binary exists: {}", new File(torrentBinary).exists());
                        if (log.isDebugEnabled()) log.debug("Binary executable: {}", new File(torrentBinary).canExecute());

                        String [] cmdArray = new String[4];
                        cmdArray[0] = torrentBinary;
                        cmdArray[1] =  mTorrent.replace("%20", " ");
                        cmdArray[2] = mBlockList;
                        cmdArray[3] = torrentDownloadPath; //Pass download path as argument to torrent daemon

                        if (log.isDebugEnabled()) log.debug("starting url {} with download path {} working dir {}", mTorrent, torrentDownloadPath, torrentWorkingDir.getAbsolutePath());

                        sProcess = Runtime.getRuntime().exec(cmdArray,null, torrentWorkingDir);
                        isDaemonRunning = true;
                        String line;
                        mReader = new BufferedReader (new InputStreamReader(sProcess.getInputStream()));

                        if((line = mReader.readLine ())!=null){
                            mPort = Integer.valueOf(line);
                            if(mObserver!=null){
                                mObserver.setPort(mPort);
                            }
                        }
                        final BufferedReader readerError = new BufferedReader (new InputStreamReader(sProcess.getErrorStream()));
                        new Thread(){
                            public void run(){
                                String line = "";
                                try {
                                    while (readerError!=null&&(line = readerError.readLine ()) != null&&!hasToStop && !Thread.currentThread().isInterrupted()) {
                                        if (log.isDebugEnabled()) log.debug("Stderr: {}", line);
                                    }
                                } catch (IOException e) {
                                    log.error("Error reading stderr", e);
                                }
                                if (log.isDebugEnabled()) log.debug("end of error lines");
                            }
                        }.start();

                        observeStdout();

                        if(sProcess!=null)
                            sProcess.waitFor();

                        if (log.isDebugEnabled()) log.debug("daemon has finished");
                        isDaemonRunning=false;
                        mHasSetFiles  =false;
                    } catch(IOException io){
                        log.warn("IOException ", io);
                        isDaemonRunning=false;
                        mHasSetFiles  =false;
                    } catch(InterruptedException io){
                        log.warn("InterruptedException", io);
                        isDaemonRunning = false;
                        mHasSetFiles = false;
                    }
                    if(mObserver!=null)
                        mObserver.onEndOfTorrentProcess();
                } 
                else
                    if(mContext!=null&&mObserver!=null)
                        mObserver.notifyDaemonStreaming();
            }

           

        };
        mTorrentThread.start();
    }

    private void observeStdout() {
        String line;

        try {
            if (log.isDebugEnabled()) log.debug("observeStdout: Starting to read stdout");
            while ((line = mReader.readLine ()) != null&&!hasToStop) {
                if (log.isDebugEnabled()) log.debug("observeStdout: Read line: '{}'", line);
                if(line.isEmpty()) {
                    if (log.isDebugEnabled()) log.debug("observeStdout: Empty line received, setting files list (count: {})", files.size());
                    mObserver.setFilesList(files);
                    break;
                }
                files.add(line);
                if (log.isDebugEnabled()) log.debug("observeStdout: Added file: {} (total files: {})", line, files.size());
            }

            if(line == null) {
                if (log.isDebugEnabled()) log.debug("observeStdout: Reached end of stdout (line == null)");
            }
            if(mSelectedFile >= 0)
                selectFile(mSelectedFile);

            while ((line = mReader.readLine ()) != null&&!hasToStop) {
                // check size
                String[] parsed = line.split(";");
                long downloadingSize = Long.parseLong(parsed[5]) - Long.parseLong(parsed[4]); //total - remaining
                long size = -1;
                try {
                    final StatFs stat = new StatFs(TorrentPathDialogPreference.getDefaultDirectory(
                            PreferenceManager.getDefaultSharedPreferences(mContext)).getAbsolutePath());
                    final long blockSize = stat.getBlockSizeLong();
                    final long availableBlocks = stat.getAvailableBlocksLong();
                    size = availableBlocks * blockSize;
                }
                catch (IllegalArgumentException e) {
                }
                if(size<downloadingSize&&size>=0) {
                    exitProcess();
                    killProcess();
                    if(mObserver != null)
                        mObserver.warnOnNotEnoughSpace();
                    return;
                }
                if(mObserver != null)
                    mObserver.notifyObserver(line);
                if (log.isDebugEnabled()) log.debug("Stdout: {}{}", line, String.valueOf(mHasSetFiles));
            }
        } catch (InterruptedIOException e) {
            if (log.isDebugEnabled()) log.debug("observeStdout: read interrupted by close() on another thread (normal cleanup)");
            Thread.currentThread().interrupt(); // Restore the interrupted status
        } catch (IOException e) {
            // TODO Auto-generated catch block
            log.error("Error reading stdout", e);
        } finally {
            if (mReader != null) {
                try {
                    mReader.close();
                } catch (IOException e) {
                    log.error("Error closing reader", e);
                }
            }
        }
    }

    public void exitProcess(){
        if (log.isDebugEnabled()) log.debug("exitProcess");
        hasToStop=true;
        try {
            Runtime.getRuntime().exec("killall -2 libtorrentd.so").waitFor();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            if(sProcess != null)
                sProcess.destroy();
        }
        try {
            if(sProcess != null) {
                sProcess.getInputStream().close();
                sProcess.getOutputStream().close();
            }
        } catch(IOException e) {
            log.warn("exitProcess.close", e);
        }
        files = null;
        mHasSetFiles = false;
        mSelectedFile = -1;
        isDaemonRunning = false;
    }

    public static void staticExitProcess(){
        if (log.isDebugEnabled()) log.debug("staticExitProcess");
        try {
            if (isDaemonRunning) Runtime.getRuntime().exec("killall -2 libtorrentd.so").waitFor();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            if(sProcess != null)
                sProcess.destroy();
        }
        try {
            if(sProcess != null) {
                sProcess.getInputStream().close();
                sProcess.getOutputStream().close();
            }
        } catch(IOException e) {
            log.warn("exitProcess.close", e);
        }
        sProcess = null;
    }

    public static void killProcess(){
        if (log.isDebugEnabled()) log.debug("killProcess");

        try {
            if (isDaemonRunning) Runtime.getRuntime().exec("killall -9 libtorrentd.so").waitFor();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            log.error("killProcess: caught Exception", e);
        }
       
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return binder;
    }
    public void removeObserver(TorrentThreadObserver observer) {
        // TODO Auto-generated method stub
        if(mObserver == observer)
            mObserver=null;
    }

    private android.os.Looper newLooper() {
        HandlerThread thread = new HandlerThread(this.getClass().getName(),
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        return thread.getLooper();
    }

    private final static int MSG_QUIT = 1;
    private final static int MSG_KILL = 2;
    private Handler mHandler = new Handler(newLooper()) {
        @Override
        public void handleMessage(android.os.Message msg) {
            switch(msg.what) {
                case MSG_QUIT:
                    if (log.isDebugEnabled()) log.debug("Quitting");
                    exitProcess();
                    //Give .5s to save state
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_KILL), 500);
                    break;
                case MSG_KILL:
                    if (log.isDebugEnabled()) log.debug("Killing");
                    killProcess();
                    break;
                default:
                    break;
            }
        }
    };

    private int nResume = 0;
    private int nPause = 0;

    private void _paused() {
        nPause++;
        if (log.isDebugEnabled()) log.debug("_paused = {}, nResume = {}", nPause, nResume );
        if(nPause >= nResume) {
            //Give 2s grace period
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_QUIT), 2000);
        }
    }

    private void _resumed() {
        nResume++;
        if (log.isDebugEnabled()) log.debug("_resumed = {}, nPause = {}", nResume, nPause);
        if(nResume > nPause) {
            mHandler.removeMessages(MSG_QUIT);
        }
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        // App in background
        if (log.isDebugEnabled()) log.debug("onStop: LifecycleOwner app in background, stopSelf");
        cleanup();
        stopSelf();
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        // App in foreground
        if (log.isDebugEnabled()) log.debug("onStart: LifecycleOwner app in foreground");
    }

    @Override
    public void onDestroy() {
        if (log.isDebugEnabled()) log.debug("onDestroy()");
        if (sInstance == this) {
            sInstance = null;
        }
        ProcessLifecycleOwner.get().getLifecycle().removeObserver(this);
        cleanup(); // Call cleanup here
        super.onDestroy();
    }

    private void cleanup() {
        if (log.isDebugEnabled()) log.debug("cleanup");
        // Stop the torrent thread if it's running
        if (mTorrentThread != null) {
            mTorrentThread.interrupt();
            mTorrentThread = null;
        }
        // Exit and kill the torrent process
        exitProcess();
        killProcess();
        // Remove any pending messages from the handler
        mHandler.removeCallbacksAndMessages(null);
    }
}
