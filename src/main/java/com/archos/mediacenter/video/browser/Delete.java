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

import static com.archos.filecorelibrary.FileUtils.isLocal;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.filecorelibrary.localstorage.ExternalSDFileWriter;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by vapillon on 21/05/15.
 */
public class Delete {

    private static final Logger log = LoggerFactory.getLogger(Delete.class);

    private static final int MAX_DEPTH = 3;//for folder delete : do not delete
    private static final int MIN_FILE_SIZE = 300000000; //do not delete parent folder if currently deleted file is inferior to min file size
    private static final int MAX_FOLDER_SIZE = 30000000; //do not delete parent folder if this folder is bigger than that

    private final Handler mHandler;
    private final DeleteListener mListener;
    private final Context mContext;

    private MetaFile2 currentVideoFileToDelete;
    private long currentVideoFileToDeleteSize;

    private Integer counter = 0; // only for video files not for associated files

    public void deleteOK(List<Uri> fileUris) { // flush backlog
        log.debug("deleteOK: counter " + counter + ", fileUris " + fileUris);
        for (Uri uri : fileUris)
            deleteOK(uri);
    }

    public void deleteOK(Uri fileUri) {
        counter--;
        log.debug("deleteOK: "+ fileUri + " counter " + counter);
        if (counter <= 0) {
            // sometimes we will want to delete parent folder, when empty or only filled with little files like subtitles or nfo
            // then, ask the user
            if (mListener != null) {

                if (isLocal(fileUri)) { // record if this is a directory being deleted for later
                    log.debug("deleteOK: locale file/folder trying to delete if directory");
                    LocalStorageFileEditor editor = new LocalStorageFileEditor(fileUri, mContext);
                    editor.deleteDir(fileUri);
                }

                if (isLocal(fileUri) &&
                        !LocalStorageFileEditor.checkIfShouldNotTouchFolder(FileUtils.getParentUrl(fileUri))) {
                    long shouldIDelete = getFolderSizeAndStopOnMax(FileUtils.getParentUrl(fileUri), MAX_FOLDER_SIZE, 0, 0);
                    if ((currentVideoFileToDeleteSize > MIN_FILE_SIZE || shouldIDelete == 0) && MAX_FOLDER_SIZE > shouldIDelete && shouldIDelete >= 0) {
                        mHandler.post(() -> {
                            log.debug("deleteOK onVideoFileRemoved ask for folder removal " + fileUri);
                            mListener.onVideoFileRemoved(fileUri, true, FileUtils.getParentUrl(fileUri));
                        });
                    } else {
                        mHandler.post(() -> {
                            log.debug("deleteOK onVideoFileRemoved " + fileUri);
                            mListener.onVideoFileRemoved(fileUri, false, null);
                        });
                    }
                } else {
                    mHandler.post(() -> {
                        log.debug("deleteOK onVideoFileRemoved " + fileUri);
                        mListener.onVideoFileRemoved(fileUri, false, null);
                    });
                }
                mHandler.post(() -> {
                    log.debug("deleteOK onDeleteSuccess " + fileUri);
                    mListener.onDeleteSuccess();
                });
            }
        }
    }

    public void deleteNOK(List<Uri> fileUris) { // flush backlog
        for (Uri uri : fileUris)
            deleteNOK(uri);
    }

    public void deleteNOK(Uri fileUri) {
        counter--;
        log.debug("deleteNOK: " + fileUri + " counter " + counter);
        if (counter == 0) {
            if (mListener != null)
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        log.debug("deleteNOK onDeleteVideoFailed " + fileUri);
                        mListener.onDeleteVideoFailed(fileUri);
                    }
                });
        }
    }

    public void deleteFolderOK(Uri folderUri) {
        log.debug("deleteFolderOK: " + folderUri + " counter " + counter);
        if (mListener != null)
            mHandler.post(() -> {
                log.debug("deleteFolder onFolderRemoved " + folderUri);
                mListener.onFolderRemoved(folderUri);
            });
    }

    public void startMultipleDeleteProcess(final List<Uri> toDelete) { // Uri are only video files
        log.debug("startMultipleDeleteProcess: " + ((toDelete != null) ? Arrays.toString(toDelete.toArray()) : null));
        new Thread(){
            public void run() {
                if (toDelete != null) {
                    counter += toDelete.size(); // this is not the number of files but block of files to process
                    log.debug("startMultipleDeleteProcess: counter " + counter);
                    Boolean allUrisLocal = true;
                    List<Uri> toDeleteLocal = new ArrayList<>();
                    for (Uri toDeleteUri : toDelete) {
                        final Uri fileUri = toDeleteUri;
                        if (!isLocal(fileUri)) {
                            allUrisLocal = false;
                            NetworkScanner.removeVideos(mContext, fileUri);
                            // delete file
                            deleteFileAndAssociatedFiles(mContext, fileUri);
                        } else {
                            // sending intent to unindex the file
                            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(VideoUtils.getMediaLibCompatibleFilepathFromUri(fileUri)));
                            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                            mContext.sendBroadcast(intent);
                            toDeleteLocal.add(fileUri);
                        }
                    }
                    log.debug("startMultipleDeleteProcess: all uris are local " + allUrisLocal);
                    // delete local files in a batch
                    deleteLocalFilesAndAssociatedFiles(mContext, toDeleteLocal);
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
        log.debug("deleteAssociatedNfoFiles: " + fileUri);
        new Thread(){
            public void run(){
                if(!UriUtils.isImplementedByFileCore(fileUri)||"upnp".equals(fileUri.getScheme())) //we can't delete files on upnp
                    return;
                List<Uri> toDelete = getAssociatedFiles(fileUri);
                if(toDelete!=null){
                    log.debug("deleteAssociatedNfoFiles: counter " + counter);
                    for(Uri uri : toDelete){
                        try {
                            FileEditorFactory.getFileEditorForUrl(uri,mContext).delete();
                        } catch (Exception e) {
                            log.error("deleteAssociatedNfoFiles: caught Exception", e);
                        }
                    }
                }
            }
        }.start();
    }

    public void startDeleteProcess(final Uri fileUri){
        log.debug("startDeleteProcess: " + fileUri);
        counter = 1; // one fileUri
        new Thread(){
            public void run(){
                currentVideoFileToDeleteSize = 0;
                //retieve video size
                MetaFile2 currentVideoFileToDelete = null;
                try {
                    currentVideoFileToDelete = MetaFileFactoryWithUpnp.getMetaFileForUrl(fileUri);
                } catch (Exception e) { }
                //sending intent to unindex the file
                if (isLocal(fileUri)) {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,Uri.parse(VideoUtils.getMediaLibCompatibleFilepathFromUri(fileUri)));
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    mContext.sendBroadcast(intent);
                } else
                    NetworkScanner.removeVideos(mContext, fileUri);
                if (currentVideoFileToDelete==null) {
                    deleteNOK(fileUri);
                    return;
                }
                currentVideoFileToDeleteSize = currentVideoFileToDelete.length();
                // async now
                deleteFileAndAssociatedFiles(mContext, fileUri);
            }
        }.start();
    }

    public void deleteFileAndAssociatedFiles(Context context, Uri fileUri) {
        log.debug("deleteFileAndAssociatedFiles: " + fileUri);
        new Thread() {
            public void run() {
                Boolean isDeleteOK;
                // TODO if directory do not get associate files....
                // Get list of all files (video and associated)
                List<Uri> associatedFiles = getAssociatedFiles(fileUri);
                // Do not forget to add the video file!
                List<Uri> allFiles = new ArrayList<>(associatedFiles.size() + 1);
                allFiles.add(fileUri);
                allFiles.addAll(associatedFiles);
                log.debug("deleteFileAndAssociatedFiles: counter " + counter);
                // Delete found associated files
                for (Uri uri : allFiles) {
                    FileEditor editor = FileEditorFactory.getFileEditorForUrl(uri, context);
                    // delete feedback provided through boolean or exception handling
                    Boolean isLocaleFile = editor instanceof LocalStorageFileEditor;
                    try {
                        if (isLocaleFile) //delete from database
                            isDeleteOK = ((LocalStorageFileEditor) editor).deleteFileAndDatabase(context);
                        else {
                            NetworkScanner.removeVideos(context, uri);
                            isDeleteOK = editor.delete();
                            if (isDeleteOK == null) isDeleteOK = true; // smb and other editors are returning null otherwise throws exception
                        }
                        // ok only on main video file and if we have feedback already
                        if (isDeleteOK != null && uri == fileUri) deleteOK(fileUri);
                        log.debug("deleteFileAndAssociatedFiles: delete achieved " + uri + ", counter " + counter);
                    } catch (Exception e) {
                        log.error("deleteFileAndAssociatedFiles: failed to delete file " + uri + ", counter " + counter, e);
                        if (uri == fileUri) { // if failure is on main file
                            deleteNOK(fileUri);
                            log.error("deleteFileAndAssociatedFiles: failed to delete main file " + uri + ", counter " + counter, e);
                            return;
                        }
                    }
                    if (! isLocaleFile) { // no deferred feedback
                        log.debug("deleteFileAndAssociatedFiles: no localeFile counter " + counter);
                    }
                }
                //delete subs
                if (!FileUtils.isSlowRemote(fileUri)) {
                    SubtitleManager.deleteAssociatedSubs(fileUri, context);
                    XmlDb.deleteAssociatedResumeDatabase(fileUri);
                }
            }
        }.start();
    }

    public void deleteLocalFilesAndAssociatedFiles(Context context, List<Uri> fileUris) {
        log.debug("deleteLocalFilesAndAssociatedFiles: " + fileUris);
        new Thread() {
            public void run() {
                Boolean isDeleteOK;
                // TODO if directory do not get associate files....
                // Get list of all files (video and associated)
                List<Uri> associatedFiles = new ArrayList<>();
                for (Uri uri : fileUris) {
                    associatedFiles.addAll(getAssociatedFiles(uri));
                }
                log.debug("deleteLocalFilesAndAssociatedFiles: counter " + counter);
                // Delete found associated files
                for (Uri uri : associatedFiles) {
                    LocalStorageFileEditor editor = new LocalStorageFileEditor(uri, context);
                    // delete feedback provided through boolean or exception handling
                    try {
                        isDeleteOK = editor.deleteFileAndDatabase(context);
                        // ok only on main video file and if we have feedback already
                        log.debug("deleteLocalFilesAndAssociatedFiles: delete achieved " + uri + ", counter " + counter);
                    } catch (Exception e) {
                        log.error("deleteLocalFilesAndAssociatedFiles: failed to delete file " + uri + ", counter " + counter, e);
                    }
                }

                List<Uri> contentUrisToDelete = new ArrayList<>();
                Uri contentUri;
                for (Uri uri : fileUris) { // handle the video files separately
                    // start with deleting all the files from dataBase
                    LocalStorageFileEditor editor = new LocalStorageFileEditor(uri, context);
                    editor.deleteFromDatabase(uri);
                    contentUri = FileUtilsQ.getContentUri(uri);
                    log.debug("deleteLocalFilesAndAssociatedFiles: video to be batch processed: " + uri + " -> contentUri " + contentUri);
                    // if contentUri is null file has already been deleted before...
                    if (contentUri != null) contentUrisToDelete.add(contentUri);
                    else deleteOK(uri);
                    // delete subs too
                    if (!FileUtils.isSlowRemote(uri)) {
                        SubtitleManager.deleteAssociatedSubs(uri, context);
                        XmlDb.deleteAssociatedResumeDatabase(uri);
                    }
                }
                log.debug("deleteLocalFilesAndAssociatedFiles: contentUrisToDelete " + contentUrisToDelete);
                if (! contentUrisToDelete.isEmpty()) {
                    if (Build.VERSION.SDK_INT > 29) {
                        log.debug("deleteFile: delete failed -> going the Q way");
                        // isDeleteOK can be null since UI involved in Android Q+
                        isDeleteOK = FileUtilsQ.deleteAll(FileUtilsQ.getDeleteLauncher(), contentUrisToDelete);
                        if (isDeleteOK != null && !isDeleteOK)
                            deleteNOK(contentUrisToDelete);
                        else deleteOK(contentUrisToDelete);
                    } else {
                        log.debug("deleteFile: delete failed -> going the traditional way");
                        for (Uri uri : fileUris) { // handle the video files separately
                            File fileToDelete = new File(uri.getPath());
                            ExternalSDFileWriter external = new ExternalSDFileWriter(mContext.getContentResolver(), fileToDelete);
                            try {
                                if (!external.delete()) deleteNOK(uri);
                                else deleteOK(uri);
                            } catch (IOException ioe) {
                                deleteNOK(uri);
                            }
                        }
                    }
                }
            }
        }.start();
    }

    public void deleteFolder(final Uri uri){
        log.debug("deleteFolder: " + uri);
        new Thread(){
            public void run() {
                try {
                    FileEditorFactory.getFileEditorForUrl(uri, mContext).delete();
                } catch (Exception e) { }
                deleteFolderOK(uri);
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
        // TODO if fileUri is a folder do not provide associatedFiles?
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
            // relocate uri for local files to writeable location to comply with API30
            Uri uri = FileUtils.relocateNfoJpgAppPublicDir(Uri.parse(parentUri.toString() + filenameWithoutExtension + extension));
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
                if (tags != null) {
                    EpisodesLoader episodesLoader = new EpisodesLoader(ArchosUtils.getGlobalContext(), tags.getShowId(), tags.getSeason(), false);
                    boolean shouldDeleteSeasonExportedFiles = true;
                    Cursor cursor2 = episodesLoader.loadInBackground();
                    if (cursor2 != null && cursor2.getCount() > 0) {
                        cursor2.moveToFirst();
                        VideoCursorMapper videoCursorMapper2 = new VideoCursorMapper();
                        videoCursorMapper2.bindColumns(cursor2);
                        do {
                            Episode episode2 = (Episode) videoCursorMapper2.bind(cursor2);
                            if (!Uri.parse(episode2.getFilePath()).equals(fileUri)) {
                                shouldDeleteSeasonExportedFiles = false;
                                break;
                            }
                        } while (cursor2.moveToNext());
                        cursor2.close();
                    }
                    if (shouldDeleteSeasonExportedFiles) {
                        //add nfo/jpg files to delete

                        SeasonsLoader seasonLoader = new SeasonsLoader(ArchosUtils.getGlobalContext(), tags.getShowId());
                        boolean shouldDeleteShowExportedFiles = true;
                        Cursor cursor3 = seasonLoader.loadInBackground();

                        if (cursor3 != null && cursor3.getCount() > 0) {

                            SeasonCursorMapper seasonCursorMapper2 = new SeasonCursorMapper();
                            seasonCursorMapper2.bindColumns(cursor3);
                            cursor3.moveToFirst();
                            do {
                                Season season = (Season) seasonCursorMapper2.bind(cursor3);
                                if (season.getSeasonNumber() != tags.getSeason()) {
                                    shouldDeleteShowExportedFiles = false;
                                    break;
                                }
                            } while (cursor3.moveToNext());
                            cursor3.close();
                        }
                        //add season poster file
                        String formatedName = parentUri + NfoParser.getCustomSeasonPosterName(tags.getShowTitle(), tags.getSeason());
                        if (formatedName != null)
                            result.add(Uri.parse(formatedName));

                        if (shouldDeleteShowExportedFiles) {

                            //add show files
                            formatedName = NfoParser.getCustomShowNfoName(tags.getShowTitle());
                            if (formatedName != null)
                                result.add(Uri.parse(parentUri + formatedName));
                            formatedName = NfoParser.getCustomShowPosterName(tags.getShowTitle());
                            if (formatedName != null)
                                result.add(Uri.parse(parentUri + formatedName));
                            formatedName = NfoParser.getCustomShowBackdropName(tags.getShowTitle());
                            if (formatedName != null)
                                result.add(Uri.parse(parentUri + formatedName));
                            formatedName = NfoParser.getCustomShowNetworkLogoName(tags.getShowTitle());
                            if (formatedName != null)
                                result.add(Uri.parse(parentUri + formatedName));
                            formatedName = NfoParser.getCustomShowActorPhotoName(tags.getShowTitle());
                            if (formatedName != null)
                                result.add(Uri.parse(parentUri + formatedName));
                        }
                    }
                }
            }
        }

        if (cursor != null) cursor.close();

        return result;
    }
}
