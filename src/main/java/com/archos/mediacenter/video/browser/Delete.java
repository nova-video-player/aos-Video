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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.filecorelibrary.localstorage.LocalStorageFileEditor;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.MetaFileFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.RawListerFactoryWithUpnp;
import com.archos.mediacenter.utils.videodb.XmlDb;
import com.archos.mediacenter.video.browser.adapters.mappers.SeasonCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.EpisodesLoader;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.info.SingleVideoLoader;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.NetworkScanner;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.NfoParser;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by vapillon on 21/05/15.
 */
public class Delete {

    private static final String TAG = "Delete";
    private static final int MAX_DEPTH = 3;//for folder delete : do not delete
    private static final int MIN_FILE_SIZE = 300000000; //do not delete parent folder if currently deleted file is inferior to min file size
    private static final int MAX_FOLDER_SIZE = 30000000; //do not delete parent folder if this folder is bigger than that

    private final Handler mHandler;
    private final DeleteListener mListener;
    private final Context mContext;

    public void startMultipleDeleteProcess(final List<Uri> toDelete) {
        new Thread(){
            public void run(){
                for(Uri toDeleteUri : toDelete) {
                    final Uri fileUri = toDeleteUri;
                    //sending intent to unindex the file
                    boolean deleteResult = deleteFileAndAssociatedFiles(mContext, fileUri);
                    if (FileUtils.isLocal(fileUri)) {
                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(VideoUtils.getMediaLibCompatibleFilepathFromUri(fileUri)));
                        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                        mContext.sendBroadcast(intent);
                    }
                    else
                        NetworkScanner.removeVideos(mContext, fileUri);

                    if (!deleteResult) {
                        if (mListener != null)
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onDeleteVideoFailed(fileUri);
                                }
                            });
                        return;
                    } else if (mListener != null) {

                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onVideoFileRemoved(fileUri, false, null);
                            }
                        });
                    }
                    if (mListener != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onDeleteSuccess();
                            }
                        });
                    }
                }
            }
        }.start();
    }

    public interface DeleteListener{
        void onVideoFileRemoved(Uri videoFile, boolean askForFolderRemoval, Uri folderToDelete);
        void onDeleteVideoFailed(Uri videoFile);
        void onFolderRemoved(Uri folder);
        /**
         * when full delete process has finished -> called immediately after
         * onVideoFileRemoved() on single file delete or at the end of list
         * delete when multiple files to delete
         *
         */
        void onDeleteSuccess();
    }

    public Delete(DeleteListener listener, Context context){
        mHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
        mContext = context;
    }
    public void deleteAssociatedNfoFiles(final Uri fileUri){ //when deleting a description, also delete Nfo
        new Thread(){
            public void run(){
                if(!UriUtils.isImplementedByFileCore(fileUri)||"upnp".equals(fileUri.getScheme())) //we can"t delete files on upnp
                    return;
                List<Uri> toDelete =  getAssociatedFiles(fileUri);
                if(toDelete!=null){

                    for(Uri uri : toDelete){
                        try {
                            FileEditorFactory.getFileEditorForUrl(uri,mContext).delete();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
        }.start();

    }
    public void startDeleteProcess(final Uri fileUri){
        new Thread(){
            public void run(){
                long currentFileSize = 0;
                //retieve video size
                MetaFile2 file = null;
                try {
                    file = MetaFileFactoryWithUpnp.getMetaFileForUrl(fileUri);
                } catch (Exception e) {

                }

                //sending intent to unindex the file
                if(FileUtils.isLocal(fileUri)) {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.parse(VideoUtils.getMediaLibCompatibleFilepathFromUri(fileUri)));
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    mContext.sendBroadcast(intent);
                }
                else
                    NetworkScanner.removeVideos(mContext, fileUri);

                if(file==null) {
                    if(mListener!=null)
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onDeleteVideoFailed(fileUri);
                            }
                        });
                    return;
                }

                currentFileSize =file.length();
                if(!deleteFileAndAssociatedFiles(mContext, fileUri)){
                    if(mListener!=null)
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onDeleteVideoFailed(fileUri);
                            }
                        });
                    return;
                }

                // sometimes we will want to delete parent folder, when empty or only filled with little files like subtitles or nfo
                // then, ask the user
                if(mListener!=null) {
                    if(FileUtils.isLocal(fileUri)&&
                            !LocalStorageFileEditor.checkIfShouldNotTouchFolder(FileUtils.getParentUrl(fileUri)))
                    {
                        long shouldIDelete = getFolderSizeAndStopOnMax(FileUtils.getParentUrl(fileUri), MAX_FOLDER_SIZE, 0, 0);
                        if ((currentFileSize > MIN_FILE_SIZE || shouldIDelete == 0) && MAX_FOLDER_SIZE > shouldIDelete && shouldIDelete >= 0) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onVideoFileRemoved(fileUri, true, FileUtils.getParentUrl(fileUri));
                                }
                            });
                        } else {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onVideoFileRemoved(fileUri, false, null);
                                }
                            });
                        }
                    }
                    else{
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.onVideoFileRemoved(fileUri, false, null);
                            }
                        });
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onDeleteSuccess();
                        }
                    });
                }
            }
        }.start();


    }
    public static boolean deleteFileAndAssociatedFiles(Context context, Uri fileUri) {
        // Get list of all files (video and associated)
        List<Uri> associatedFiles = getAssociatedFiles(fileUri);
        // Do not forget to add the video file!
        List<Uri> allFiles = new ArrayList<>(associatedFiles.size()+1);
        allFiles.add(fileUri);
        allFiles.addAll(associatedFiles);
        // Delete found associated files
        for (Uri uri : allFiles) {

            FileEditor editor = FileEditorFactory.getFileEditorForUrl(uri,context);
            try {
                if(editor instanceof LocalStorageFileEditor) //delete from database
                    ((LocalStorageFileEditor)editor).deleteFileAndDatabase(context);
                else {
                    NetworkScanner.removeVideos(context, uri);
                    editor.delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete file " + uri, e);
                if(uri == fileUri) // if failure is on main file
                    return false;
            }
        }
        //delete subs
        if(!FileUtils.isSlowRemote(fileUri)) {
            SubtitleManager.deleteAssociatedSubs(fileUri,context);
            XmlDb.deleteAssociatedResumeDatabase(fileUri);
        }
        return true;
    }


    public void deleteFolder(final Uri uri){
        new Thread(){
            public void run() {
                try {
                    FileEditorFactory.getFileEditorForUrl(uri, mContext).delete();
                } catch (Exception e) {

                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onFolderRemoved(uri);
                    }
                });
            }

        }.start();

    }
    private static long getFolderSizeAndStopOnMax(Uri toList, long maxSize, long currentSize, int currentDepth) {
        if(currentDepth>=MAX_DEPTH)
            return -1;
        if(currentSize<maxSize&&toList!=null){
            RawLister lister = RawListerFactoryWithUpnp.getRawListerForUrl(toList);
            try {
                List<MetaFile2> metaFiles = lister.getFileList();
                if(metaFiles!=null) {
                    for (MetaFile2 metaFile : metaFiles) {
                        long foundSize = 0;
                        if (metaFile.isDirectory())
                            foundSize = getFolderSizeAndStopOnMax(metaFile.getUri(), maxSize, currentSize, currentDepth++);
                        else
                            foundSize = metaFile.length();
                        if (foundSize == -1)// if foundSize==-1 (error) return error -> an error occured in size retrieval
                            return -1;
                        currentSize += foundSize;
                    }
                }
                else
                    return -1;
                if(currentSize>=maxSize)
                    return currentSize;
            } catch (IOException e) {
                currentSize = -1;
            }
            catch (SftpException e) {
                currentSize=-1;
            } catch (JSchException e) {
                currentSize=-1;
            } catch (AuthenticationException e) {
                currentSize=-1;
            }
        }

        return currentSize;
    }

    /**
     * Get all the files that are in the same directory and starting with the same name
     * @param fileUri
     * @return
     */
    private static List<Uri> getAssociatedFiles(Uri fileUri) {
        List<Uri> result = new LinkedList<>();

        final Uri parentUri = FileUtils.getParentUrl(fileUri);
        if (parentUri==null) {
            return result;
        }

        final String filenameWithoutExtension = FileUtils.getFileNameWithoutExtension(fileUri);
        if (filenameWithoutExtension==null || filenameWithoutExtension.isEmpty()) {
            return result;
        }
        final String[] extensionsToClean = new String[] {
                "-fanart.archos.jpg",
                "-poster.archos.jpg",
                ".archos.nfo"
        };

        for (String extension : extensionsToClean) {
            Uri uri = Uri.parse(parentUri.toString() + filenameWithoutExtension + extension);
            if (uri!=null) { // is it possible?
                result.add(uri);
            }
        }
        if (Looper.myLooper() == null)
            Looper.prepare();

        //check if episode of a TV SHOW
        if("file".equals(fileUri.getScheme()))
            fileUri = Uri.parse(fileUri.getPath());
        SingleVideoLoader videoLoader = new SingleVideoLoader(ArchosUtils.getGlobalContext(), fileUri.toString());
        Cursor cursor = videoLoader.loadInBackground();

        if(cursor!=null&&cursor.getCount()>0){
            cursor.moveToFirst();
            VideoCursorMapper videoCursorMapper = new VideoCursorMapper();
            videoCursorMapper.bindColumns(cursor);
            Video video = (Video) videoCursorMapper.bind(cursor);

            if(video!=null && video instanceof Episode){

                EpisodeTags tags = (EpisodeTags) video.getFullScraperTags(ArchosUtils.getGlobalContext());
                EpisodesLoader episodesLoader = new EpisodesLoader(ArchosUtils.getGlobalContext(),tags.getShowId(), tags.getSeason(),false);
                boolean shouldDeleteSeasonExportedFiles = true;
                Cursor cursor2 = episodesLoader.loadInBackground();
                if(cursor2!=null&&cursor2.getCount()>0){
                    cursor2.moveToFirst();
                    VideoCursorMapper videoCursorMapper2 = new VideoCursorMapper();
                    videoCursorMapper2.bindColumns(cursor2);
                    do{
                        Episode episode2 = (Episode) videoCursorMapper2.bind(cursor2);
                        if(!Uri.parse(episode2.getFilePath()).equals(fileUri)){
                            shouldDeleteSeasonExportedFiles = false;
                            break;
                        }
                    }while (cursor2.moveToNext());
                }
                if(shouldDeleteSeasonExportedFiles){
                    //add nfo/jpg files to delete

                    SeasonsLoader seasonLoader = new SeasonsLoader(ArchosUtils.getGlobalContext(),tags.getShowId());
                    boolean shouldDeleteShowExportedFiles = true;
                    Cursor cursor3 = seasonLoader.loadInBackground();

                    if(cursor3!=null&&cursor3.getCount()>0){

                        SeasonCursorMapper seasonCursorMapper2 = new SeasonCursorMapper();
                        seasonCursorMapper2.bindColumns(cursor3);
                        cursor3.moveToFirst();
                        do{
                            Season season = (Season) seasonCursorMapper2.bind(cursor3);
                            if(season.getSeasonNumber()!=tags.getSeason()){
                                shouldDeleteShowExportedFiles = false;
                                break;
                            }
                        }while (cursor3.moveToNext());
                    }
                    //add season poster file
                    String formatedName = parentUri+NfoParser.getCustomSeasonPosterName(tags.getShowTitle(), tags.getSeason());
                    if(formatedName!=null)
                        result.add(Uri.parse(formatedName));

                    if(shouldDeleteShowExportedFiles){

                        //add show files
                        formatedName=NfoParser.getCustomShowNfoName(tags.getShowTitle());
                        if(formatedName!=null)
                            result.add(Uri.parse(parentUri+formatedName));
                        formatedName=NfoParser.getCustomShowPosterName(tags.getShowTitle());
                        if(formatedName!=null)
                            result.add(Uri.parse(parentUri+formatedName));
                        formatedName=NfoParser.getCustomShowBackdropName(tags.getShowTitle());
                        if(formatedName!=null)
                            result.add(Uri.parse(parentUri+formatedName));
                    }
                }
            }
        }
        return result;
    }
}
