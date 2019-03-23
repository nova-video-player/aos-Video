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
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import com.archos.mediacenter.video.utils.TorrentPathDialogPreference;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.util.Log;

public class TorrentObserverService extends Service{
    private static final String TAG = "TorrentObserverService";
    private static final String DEFAULT_TORRENT_PATH = "/sdcard/";
    public static final String BLOCKLIST = "blocklist";
    private Context mContext;
    private  String mTorrent;
    ArrayList<String> files ;
    TorrentThreadObserver mObserver;
    private boolean isDaemonRunning;
    private static Process sProcess;
    private IBinder binder ;
    private boolean DBG = false;
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
    public int onStartCommand(Intent i, int flags, int id) {
        if(i==null)
            return START_STICKY;
        Log.d("AVP", "Got intent " + i.getAction());
        if(i.getAction().equals(intentPaused)) {
            _paused();
        } else if(i.getAction().equals(intentResumed)) {
            _resumed();
        }
        return START_STICKY;
    }

    static public void paused(Context ctxt) {
        Log.d("AVP", "Sending paused intent");
        Intent i = new Intent(intentPaused, Uri.EMPTY, ctxt.getApplicationContext(), TorrentObserverService.class);
        ctxt.getApplicationContext().startService(i);
    }

    static public void resumed(Context ctxt) {
        Log.d("AVP", "Sending resumed intent");
        Intent i = new Intent(intentResumed, Uri.EMPTY, ctxt.getApplicationContext(), TorrentObserverService.class);
        ctxt.getApplicationContext().startService(i);
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
                e.printStackTrace();
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
                        String [] cmdArray = new String[3];
                        cmdArray[0] = mContext.getApplicationInfo().nativeLibraryDir +"/libtorrentd.so";
                        cmdArray[1] =  mTorrent.replace("%20", " ");  
                        cmdArray[2] = mBlockList;
                        
                        Log.d(TAG,"starting url "+mTorrent);
                        //path to save torrent
                        
                        String torrentDownloadPath = TorrentPathDialogPreference.getDefaultDirectory(
                                PreferenceManager.getDefaultSharedPreferences(mContext)).getAbsolutePath();
                        sProcess = Runtime.getRuntime().exec(cmdArray,null, new File(torrentDownloadPath));
                        isDaemonRunning = true;
                        String line;
                        mReader = new BufferedReader (new InputStreamReader(sProcess.getInputStream()));

                        if((line = mReader.readLine ())!=null){
                            mPort = Integer.valueOf(line);
                            if(mObserver!=null){
                                mObserver.setPort(mPort);
                            }
                        }
                        final BufferedReader readererror = new BufferedReader (new InputStreamReader(sProcess.getErrorStream()));
                        new Thread(){
                            public void run(){
                                String line = "";
                                try {
                                    while (readererror!=null&&(line = readererror.readLine ()) != null&&!hasToStop) {
                                        if(DBG)
                                            Log.d(TAG,"Stderr: " + line);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Log.d(TAG,"end of error lines");
                            }
                        }.start();
                        
                        
                        observeStdout();
                        
                       
                        if(sProcess!=null)
                        sProcess.waitFor();
                           
                        if(DBG)
                            Log.d(TAG,"daemon has finished");
                        isDaemonRunning=false;
                        mHasSetFiles  =false;
                    } catch(IOException io){
                        Log.d(TAG,"IOException ", io);
                        isDaemonRunning=false;
                        mHasSetFiles  =false;

                    } catch(InterruptedException io){
                        Log.d(TAG, "InterruptedException", io);
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
            while ((line = mReader.readLine ()) != null&&!hasToStop) {
                if(line.isEmpty()) {
                    mObserver.setFilesList(files);
                    break;
                }
                files.add(line);
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
                    final long blockSize = stat.getBlockSize();
                    final long availableBlocks = stat.getAvailableBlocks();
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
                if(DBG)
                    Log.d(TAG,"Stdout: " + line+String.valueOf(mHasSetFiles));
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void exitProcess(){
        Log.d(TAG,"calling exit");
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
            Log.d("AVP", "exitProcess.close", e);
        }
        files = null;
        mHasSetFiles = false;
        mSelectedFile = -1;
        isDaemonRunning = false;
    }


    public static void staticExitProcess(){
        Log.d(TAG,"calling exit");
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
            Log.d("AVP", "exitProcess.close", e);
        }
        sProcess = null;
    }

    public static void killProcess(){
        Log.d(TAG,"calling kill");

        try {
            Runtime.getRuntime().exec("killall -9 libtorrentd.so").waitFor();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
                    Log.d("AVP", "Quitting");
                    exitProcess();
                    //Give .5s to save state
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_KILL), 500);
                    break;
                case MSG_KILL:
                    Log.d("AVP", "Killing");
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
        Log.d("AVP", "_paused = " + nPause + ", nResume = " + nResume );
        if(nPause >= nResume) {
            //Give 2s grace period
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_QUIT), 2000);
        }
    }

    private void _resumed() {
        nResume++;
        Log.d("AVP", "_resumed = " + nResume + ", nPause = " + nPause);
        if(nResume > nPause) {
            mHandler.removeMessages(MSG_QUIT);
        }
    }
}
