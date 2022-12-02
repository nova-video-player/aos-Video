// Copyright 2017 Archos SA
// Copyright 2020 Courville Software
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

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.CopyCutEngine;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.OperationEngineListener;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.browser.TorrentObserverService.TorrentServiceBinder;
import com.archos.mediacenter.video.browser.TorrentObserverService.TorrentThreadObserver;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediacenter.video.utils.TorrentPathDialogPreference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

public class TorrentLoaderActivity extends AppCompatActivity implements TorrentThreadObserver{

    private static final Logger log = LoggerFactory.getLogger(TorrentLoaderActivity.class);

    private static int LOADING_FINISHED =0;
    private static int ERROR_DIALOG =1;
    private static int TORRENT_DAEMON_PORT = 19992;
    private int mPort;
    private NovaProgressDialog mProgress;
    private boolean hasLaunchedPlayer = false;
    private String mTorrentURL = "";
    private boolean isDialogDisplayed = false;
    private HashMap<String, Integer> mFiles = null;
    private boolean isClosingService;
    private ServiceConnection mTorrentObserverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            mTorrent  =  ((TorrentServiceBinder) binder).getService();
            mTorrent. setParameters( mTorrentURL,-1);
            mTorrent.setObserver(TorrentLoaderActivity.this);
            mTorrent.start();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mTorrent = null;
        }
    };

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {                    
            if(msg.what == LOADING_FINISHED){
                if(mFiles!=null&& !mFiles.isEmpty()&&!isDialogDisplayed)   {
                    mProgress.dismiss();
                    if(mFiles.size()>1){
                        isDialogDisplayed = true;
                        new AlertDialog.Builder(TorrentLoaderActivity.this).setTitle(R.string.torrent_file_to_play)
                        .setOnCancelListener(new OnCancelListener() {

                            @Override
                            public void onCancel(DialogInterface dialog) {
                                TorrentLoaderActivity.this.finish();
                                isDialogDisplayed =false;
                            }
                        })
                        .setItems(mFiles.keySet().toArray(new CharSequence[mFiles.size()]), new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                startPlayerActivity(mFiles.keySet().toArray(new String[mFiles.size()])[which]);
                                isDialogDisplayed = false;

                            }
                        })
                        .setNegativeButton(android.R.string.no, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TorrentLoaderActivity.this.finish();
                            }
                        })
                        .create().show();
                    }
                    else{
                        startPlayerActivity(mFiles.keySet().toArray(new String[mFiles.size()])[0]);
                    }
                }
                else if(mFiles!=null&&mFiles.isEmpty()){
                    mProgress.dismiss();
                    showErrorDialog(getString(R.string.error_no_video_file));
                }
            }
            else if(msg.what == ERROR_DIALOG){
                mProgress.dismiss();
                if(!isClosingService)
                    showErrorDialog(getString(R.string.error_loading_torrent));
            }
        }
    };

    private String mOriginalTorrentUri;

    private void startPlayerActivity(String name){
        int toLaunch = mFiles.get(name);
        mTorrent.selectFile(toLaunch);
        Uri toPlay = Uri.parse("http://localhost:"+mPort+"/"+name);
        Intent intent = new Intent();
        String mimeType = "video/*";
        String extension = getExtension(name);
        if (extension!=null) {
            mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
        }
        intent.setDataAndType(toPlay
                ,mimeType);
        intent.putExtra(PlayerActivity.KEY_STREAMING_URI, toPlay);
        intent.putExtra(PlayerActivity.KEY_TORRENT, toLaunch);
        intent.putExtra(PlayerActivity.KEY_TORRENT_URL, mTorrentURL);
        intent.putExtra(PlayerActivity.RESUME, getIntent().getIntExtra(PlayerActivity.RESUME, PlayerActivity.RESUME_NO));
        intent.putExtra(PlayerService.KEY_ORIGINAL_TORRENT_URL, mOriginalTorrentUri);
        intent.setClass(TorrentLoaderActivity.this, PlayerActivity.class);
        startActivity(intent);
        hasLaunchedPlayer = true;
        this.finish();
    }

    protected String getExtension(String filename) {
        if (filename == null)
            return null;
        int dotPos = filename.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filename.length()) {
            return filename.substring(dotPos + 1).toLowerCase();
        }
        return null;
    }

    private TorrentObserverService mTorrent;
    private Uri mTorrentToLaunch;

    public void onCreate(Bundle d){
        super.onCreate(d);    
        Intent intent = getIntent();
        boolean isTorrentReady = true;
        isClosingService = false;
        mTorrentURL = intent.getDataString();
        if(mTorrentURL.toLowerCase().startsWith("file://"))
            mTorrentURL= intent.getData().getPath();
        mOriginalTorrentUri = mTorrentURL; //keep original uri, mTorrentUrl will be replaced after torrent file download
        mProgress = NovaProgressDialog.show(this, "", getString(R.string.loading_torrent), true, true, dialog -> TorrentLoaderActivity.this.finish());
        preloadTorrent();
    }

    private void preloadTorrent(){
        if(!FileUtils.isLocal(Uri.parse(mTorrentURL))&& UriUtils.isImplementedByFileCore(Uri.parse(mTorrentURL))){
            //first we download the torrent file
            Uri mTorrentUri = Uri.parse(mTorrentURL);
            ArrayList<Uri> source = new ArrayList<Uri>();
            source.add(mTorrentUri);
            File targetFile = TorrentPathDialogPreference.getDefaultDirectory(PreferenceManager.getDefaultSharedPreferences(this));
            Uri target = Uri.parse("file://" + targetFile.getAbsolutePath());
            //mtorrenttolaunch shouldn't have "file://"
            mTorrentToLaunch = Uri.withAppendedPath( Uri.parse(targetFile.getAbsolutePath()), mTorrentUri.getLastPathSegment());
            CopyCutEngine engine = new CopyCutEngine(getBaseContext());
            engine.setListener(new OperationEngineListener() {
                @Override
                public void onStart() { }
                @Override
                public void onProgress(int currentFile, long currentFileProgress,int currentRootFile, long currentRootFileProgress, long totalProgress, double currentSpeed) { }
                @Override
                public void onSuccess(Uri file) { }
                @Override
                public void onFilesListUpdate(List<MetaFile2> copyingMetaFiles,List<MetaFile2> rootFiles) { }
                @Override
                public void onEnd() {
                    mTorrentURL = mTorrentToLaunch.toString();
                    loadTorrent();
                }
                @Override
                public void onFatalError(Exception e) {
                    showErrorDialog(getString(R.string.error_loading_torrent));
                    mProgress.dismiss();
                }
                @Override
                public void onCanceled() {
                    mProgress.dismiss();
                }
            });
            engine.copyUri(source, target, true);
            mProgress.show();
        }
        else {
            loadTorrent();
        }
    }

    private void loadTorrent() {
        if(mTorrentURL.toLowerCase().startsWith("/")){
            InputStream is = null;
            FileOutputStream output = null;
            // mTorrentURL =  mTorrentURL.substring("file://".length());
            try  {
                is = new GZIPInputStream(new FileInputStream(mTorrentURL));
                output = new FileOutputStream(mTorrentURL+"tmp");
                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                int len = 0;
                while ((len = is.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                try{
                    output.close();
                    is.close();
                    is = new FileInputStream(mTorrentURL+"tmp");
                    output = new FileOutputStream(mTorrentURL); 
                    buffer = new byte[bufferSize];
                    len = 0;
                    while ((len = is.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                    output.close();
                    is.close();
                }catch(IOException i){
                    log.error("caught IOException");
                }
            } catch (ZipException z) {
                log.error("caught ZipException: reverting to filestream");
            } catch (FileNotFoundException e) {
                log.error("caught FileNotFoundException", e);
            } catch (IOException e) {
                log.error("caught IoException", e);
            }
            try {
                if (is != null) is.close();
                if (output != null) output.close();
            } catch(IOException e) {
                log.error("caught IoException", e);
            }
        }
        mFiles = new HashMap<String, Integer>();
        //bind to our service by first creating a new connectionIntent
        Intent connectionIntent = new Intent(this, TorrentObserverService.class);
        bindService(connectionIntent, mTorrentObserverServiceConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop(){
        super.onStop();
        if(isChangingConfigurations())
            return;
        isClosingService=true;
        if(mTorrent == null)
            return;
        mTorrent.removeObserver(this);
        if(!hasLaunchedPlayer){
            mTorrent.exitProcess();

            TorrentObserverService.staticExitProcess();
        }
        try{
            unbindService(mTorrentObserverServiceConnection);
        } catch(IllegalArgumentException e){
        }
    }

    @Override
    public void setFilesList(ArrayList<String> files) {
        if(mFiles.size()==0){
            int i = 0;
            for(String file : files){
                String mimeType = "video/*";
                String extension = getExtension(file);
                if (extension!=null) {
                    String tmp  = MimeUtils.guessMimeTypeFromExtension(extension);
                    mimeType = tmp!=null?tmp:mimeType;
                }
                if(mimeType.toLowerCase().startsWith("video")){
                    File f = new File(file);
                    mFiles.put(f.getName(), i);
                }
                i++;
            }
            mHandler.sendEmptyMessage(LOADING_FINISHED);
        }
    }

    private void showErrorDialog(String message){
        new AlertDialog.Builder(TorrentLoaderActivity.this)
        .setTitle(R.string.error_listing)
        .setMessage(message)
        .setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                TorrentLoaderActivity.this.finish();
            }
        })
        .create().show();
    }

    @Override
    public void setPort(int port) {
        mPort = port;
    }
    @Override
    public void notifyDaemonStreaming() {
    }
    @Override
    public void onEndOfTorrentProcess() {
        mHandler.sendEmptyMessage(ERROR_DIALOG);

    }
    @Override
    public void notifyObserver(String daemonString) {
        // TODO Auto-generated method stub
    }
    @Override
    public void warnOnNotEnoughSpace() {
    }

}
