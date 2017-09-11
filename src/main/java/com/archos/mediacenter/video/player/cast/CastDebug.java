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

package com.archos.mediacenter.video.player.cast;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.Pair;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.Utils;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.AllVideosLoader;
import com.archos.mediacenter.video.debug.Debug;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.sql.Timestamp;

/**
 * Created by alexandre on 09/12/15.
 */
public class CastDebug implements ArchosVideoCastManager.ArchosCastManagerListener {
    private final static String TAG = "CastDebug";


    public final static String TVDB_SHOW_ONLINE_ID = "tvdb_show_online_id";
    public static final boolean DBG = true;
    private static final String PATH_RESULT = Debug.ARCHOS_DEBUG_FOLDER_PATH+"castdebug";
    private static final String PATH_RESULT_FAILED = Debug.ARCHOS_DEBUG_FOLDER_PATH+"castdebugfailed";
    public static boolean is_media_loading;
    private static CastDebug sCastDebug;
    private final Context mContext;
    private final Handler mHandler;
    private final VideoCursorMapper mVideoMapper;
    private VideoInfoTask mVideoInfoTask;
    private Cursor mCursor = null;
    private int currentPosition=-1;
    private String mVideoInfo;
    private String mAudioInfo;
    private Video mCurrentVideo;
    private int mFailed = 0;
    private String mFileResultPath;
    private String mFileResultFailedPath;
    private boolean mHasStarted;

    public CastDebug(Context context){
        super();

        sCastDebug = this;
        mContext = context;
        mHandler = new android.os.Handler(){
            public void handleMessage(Message msg) {
                try {
                    if((ArchosVideoCastManager.getInstance().isRemoteMediaPlaying()||
                            ArchosVideoCastManager.getInstance().getPlaybackStatus()== MediaStatus.PLAYER_STATE_IDLE)&&is_media_loading){
                        goToNextVideo();
                    }
                } catch (TransientNetworkDisconnectionException e) {
                    e.printStackTrace();
                } catch (NoConnectionException e) {
                    e.printStackTrace();
                }
            }


        };
        mVideoMapper = new VideoCursorMapper();
        ArchosVideoCastManager.getInstance().addArchosCastManagerListener(this);

    }

    public void start(){
        if(!mHasStarted){
            mHasStarted = true;
            goToNextVideo();
        }
    }
    public void goToNextVideo() {
        if(!DBG||!mHasStarted)
            return;
        is_media_loading = false;
        if(mCursor==null){
            AllVideosLoader allVideosLoader = new AllVideosLoader(mContext);
            mCursor = allVideosLoader.loadInBackground();
            currentPosition = 0;
            mVideoMapper.bindColumns(mCursor);
        }
        else currentPosition ++;
        if(currentPosition>=mCursor.getCount()){
            mHasStarted = false;
            log("all videos have been played: "+mFailed+" failed for "+mCursor.getCount()+" videos");
            return;
        }
        log("--------------------------------------------------------------");
        mCursor.moveToPosition(currentPosition);
        Video video = (Video) mVideoMapper.bind(mCursor);
        log("startVideoInfoTask for "+video.getFilePath());
        if(!video.getFilePath().startsWith("smb://QUATRO2/sda1/video/nas/"))
        startVideoInfoTask(video);
        else goToNextVideo();
    }


    public void run(){

    }

    private void startVideoInfoTask(Video video){
        //first get all file info
        if(mVideoInfoTask!=null){
            mVideoInfoTask.cancel(true);
        }
        mVideoInfoTask = new VideoInfoTask();
        mVideoInfoTask.execute(video);

    }
    public  void logFailure(String s) {
        mFailed++;
        if(mCurrentVideo!=null){
            log("--------------------------", getPathResultFailed());
            log(mCurrentVideo.getFilePath(), getPathResultFailed());
            log(mVideoInfo, getPathResultFailed());
            log(mAudioInfo, getPathResultFailed());
        }

    }
    public static void log(String s) {
        if(getInstance(ArchosUtils.getGlobalContext())!=null)
            getInstance(null).notStaticLog(s);
    }

    private void notStaticLog(String s) {
        log(s, getPathResult());
    }

    private String getPathResult() {
        Timestamp stamp = new Timestamp(System.currentTimeMillis());
        Date date = new Date(stamp.getTime());
        if(mFileResultPath == null)
            mFileResultPath = PATH_RESULT+ date;
        return mFileResultPath;
    }
    private String getPathResultFailed() {
        if(mFileResultFailedPath == null)
            mFileResultFailedPath = PATH_RESULT_FAILED+System.currentTimeMillis();
        return mFileResultFailedPath;
    }
    public void log(String s, String path) {
        if(s==null||s.isEmpty())
            return;
        if(!DBG)
            return;
        File file= new File (path);
        FileWriter fw = null;

        if (!file.exists()) {
            try {

                file.getParentFile().mkdirs();
                file.createNewFile();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            fw = new FileWriter(path,true);
            fw.append(s+"\n");
            fw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, s);


    }

    @Override
    public void updateUI() {
        mHandler.removeMessages(0);
        mHandler.sendEmptyMessageDelayed(0, 10000);

    }

    @Override
    public void switchCastMode() {

    }

    public static CastDebug getInstance(Context context) {
        if(!DBG||context==null&&sCastDebug==null)
            return null;
        if(sCastDebug==null)
            sCastDebug = new CastDebug(context);
        return sCastDebug;
    }

    //retrieve info on file such as codecs, etc
    private class VideoInfoTask extends AsyncTask<Video, Integer, Pair<Video,VideoMetadata>> {

        @Override
        protected Pair<Video,VideoMetadata> doInBackground(Video... videos) {

            Video video = videos[0];


            // Get metadata from file
            VideoMetadata videoMetaData = VideoInfoCommonClass.retrieveMetadata(video, mContext);

            return new Pair<Video,VideoMetadata>(video,videoMetaData);

        }

        protected void onPostExecute(Pair<Video,VideoMetadata> videoInfo) {

            if(isCancelled())
                return;
            if(videoInfo==null) {
                log("null video info");
                return;
            }
            mCurrentVideo = videoInfo.first;
            mVideoInfo = VideoInfoCommonClass.getVideoTrackString(videoInfo.second, mContext.getResources());
            mAudioInfo = VideoInfoCommonClass.getAudioTrackString(videoInfo.second, mContext.getResources(), mContext);
            log(mVideoInfo);
            log(mAudioInfo);
            startVideo(videoInfo.first);
        }
    }

    private void startVideo(Video video) {
        log("starting video "+video.getFilePath());
        PlayUtils.startVideo(mContext,
                video,
                PlayerService.RESUME_NO,
                true,-1, null, false, -1);
    }

}
