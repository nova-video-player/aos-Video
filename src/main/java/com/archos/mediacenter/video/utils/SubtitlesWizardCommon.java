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

package com.archos.mediacenter.video.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.StrictMode;
import android.text.format.Formatter;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SubtitlesWizardCommon {

    private final static String TAG = "SubtitlesWizardCommon";
    private final static boolean DBG = false;

    // Subtitles files will be renamed as : video_name + SUBTITLES_FILES_SUFFIX + file_counter + extension
    // (for instance "." => myvideo.srt, myvideo.1.srt, myvideo.2.srt, ...)
    private final static String SUBTITLES_FILES_SUFFIX = ".";

    private String mVideoPath;
    private Uri mVideoUri;

    private List<String> mCurrentFiles;
    private int mCurrentFilesCount;

    private List<String> mAvailableFiles;
    private int mAvailableFilesCount;

    public Uri getVideoUri() {
        return mVideoUri;
    }

    public String getCurrentFile(int index) {
        return mCurrentFiles.get(index);
    }

    public int getCurrentFilesCount() {
        return mCurrentFilesCount;
    }

    public String getAvailableFile(int index) {
        return mAvailableFiles.get(index);
    }

    public int getAvailableFilesCount() {
        return mAvailableFilesCount;
    }

    private FragmentActivity mWizardActivity;

    public SubtitlesWizardCommon(FragmentActivity wizardActivity) {
        mWizardActivity = wizardActivity;
    }

    private Activity getActivity() {
        return mWizardActivity;
    }

    private Intent getIntent() {
        return mWizardActivity.getIntent();
    }

    private String getString(int arg0) {
        return mWizardActivity.getString(arg0);
    }

    private void sendBroadcast(Intent arg0) {
        mWizardActivity.sendBroadcast(arg0);
    }

    public void onCreate() {
        // Extract the path of the video to handle from the intent
        Uri videoUri = getIntent().getData();
        if (videoUri != null) {
            mVideoUri = videoUri;
            mVideoPath = videoUri.toString();
            if (DBG) Log.d(TAG, "onCreate : video to process = " + mVideoPath);

            if (mVideoPath != null) {
                StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

                StrictMode.setThreadPolicy(policy);

                // Retrieve the list of subtitles files already associated with the video
                mCurrentFilesCount = buildCurrentSubtitlesFilesList(mVideoPath);
                if (DBG) Log.d(TAG, "onCreate : mCurrentFilesCount = " + mCurrentFilesCount);

                // Get the list of subtitles files available in the current folder
                mAvailableFilesCount = buildAvailableSubtitlesFilesList(mVideoPath);
                if (DBG) Log.d(TAG, "onCreate : mAvailableFilesCount = " + mAvailableFilesCount);
            }
        }
        else {
            // Bad intent
            Log.e(TAG, "onCreate error : no folder provided");
            mVideoUri = null;
        }
    }

    public int buildCurrentSubtitlesFilesList(String videoPath) {
        if (DBG) Log.d(TAG, "buildCurrentSubtitlesFilesList : get current subtitles files");

        mCurrentFiles = new ArrayList<String>();

        // Get subtitles files from SubtitleManager
        SubtitleManager lister = new SubtitleManager(getActivity(), null);

        try {
            Uri videoUri = Uri.parse(videoPath);
            List<SubtitleManager.SubtitleFile> list = lister.listLocalAndRemotesSubtitles(videoUri, false, true, false);

            // Retrieve the path of each file found and add it to the available subtitles list
            for(SubtitleManager.SubtitleFile sub : list) {
                String path = sub.mFile.getUri().toString();

                mCurrentFiles.add(path);
            }
        } catch (Exception ex) {
            Log.e(TAG, "buildCurrentSubtitlesFilesList error : failed to get data from SubtitleManager");
        }
        
        return mCurrentFiles.size();
    }

    public int buildAvailableSubtitlesFilesList(String videoPath) {
        if (DBG) Log.d(TAG, "buildAvailableSubtitlesFilesList : get available subtitles files");

        mAvailableFiles = new ArrayList<String>();

        // Get subtitles files from SubtitleManager
        SubtitleManager lister = new SubtitleManager(getActivity(), null);

        try {
            Uri videoUri = Uri.parse(videoPath);
            List<SubtitleManager.SubtitleFile> list = lister.listLocalAndRemotesSubtitles(videoUri, true, true, false);

            // Retrieve the path of each file found and add it to the available subtitles list
            // if it is not already associated to the selected video
            for(SubtitleManager.SubtitleFile sub : list) {
                String path = sub.mFile.getUri().toString();

                if (!mCurrentFiles.contains(path))
                    mAvailableFiles.add(path);
            }
        } catch (Exception ex) {
            Log.e(TAG, "buildAvailableSubtitlesFilesList error : failed to get data from SubtitleManager");
        }
        
        return mAvailableFiles.size();
    }

    /*
     * Builds a new name for a subtitles file when it is associated to the selected video
     */
    public String buildSubtitlesFilename(String subtitlesPath) {
        int i;
        int count;
        int maxCount = 0;
        boolean sameNameFound = false;

        // Split the path of the provided subtitles file
        String subtitlesExtension = FileUtils.getExtension(subtitlesPath);
        // String subtitlesName;
        // if (subtitlesExtension != null) {
        //     subtitlesName = subtitlesPath.substring(0, subtitlesPath.length() - subtitlesExtension.length() - 1);
        // }
        // else {
        //     subtitlesName = subtitlesPath;
        // }

        // Split the path of the selected video
        String videoExtension = FileUtils.getExtension(mVideoPath);
        String videoName;
        if (videoExtension != null) {
            videoName = mVideoPath.substring(0, mVideoPath.length() - videoExtension.length() - 1);
        }
        else {
            videoName = mVideoPath;
        }
        int videoNameLength = videoName.length();

        // Compare the name of the selected video to the name of all current subtitle files
        // in order to check if there is a suffix after the name
        for (i = 0; i < mCurrentFilesCount; i++) {
            // Remove the extension
            String path = mCurrentFiles.get(i);
            String extension = FileUtils.getExtension(path);
            if (extension != null) {
                String name = path.substring(0, path.length() - extension.length() - 1);
                int nameLength = name.length();
                if (nameLength >= videoNameLength && name.startsWith(videoName) && extension.equals(subtitlesExtension)) {
                    sameNameFound = true;
                    String suffix = name.substring(videoNameLength);
                    if (suffix.startsWith(SUBTITLES_FILES_SUFFIX)) {
                        // cs should correspond to a number (for instance path="Big Buck Bunny.17.srt" => cs="17"
                        String cs = suffix.substring(SUBTITLES_FILES_SUFFIX.length());
                        try   {
                            // valid index, remember the highest value
                            count = Integer.parseInt(cs);
                            if (count > maxCount) {
                                maxCount = count;
                            }
                        }
                        catch (NumberFormatException e) {
                            // Invalid format  => ignore this file
                        }
                    }
                }
            }
        }

        // Build a name for the provided subtitles file starting with the name of the video
        if (sameNameFound) {
            return (videoName + SUBTITLES_FILES_SUFFIX + String.valueOf(maxCount + 1) + "." + subtitlesExtension);
        }
        else {
            return (videoName + "." + subtitlesExtension);
        }
    }

    public boolean renameFile(String path, int index) {
        boolean fileRenamed = false;

        // Find a name for this file
        String oldFilePath = path;
        String newFilePath = buildSubtitlesFilename(oldFilePath);

        Uri oldUri = Uri.parse(oldFilePath);
        FileEditor oldFile = FileEditorFactory.getFileEditorForUrl(oldUri, getActivity());
        Uri newUri = Uri.parse(newFilePath);
        String newName = FileUtils.getName(newUri);
        // Rename the file
        try {
            fileRenamed = oldFile.rename(newName);
            if (DBG) Log.d(TAG, "onItemClick : selected file renamed as " + newFilePath);
        }
        catch (Exception e) {
            Log.d(TAG, "renameFile : can not rename file as " + newFilePath);
        }

        String cacheDirPath = Uri.fromFile(MediaUtils.getSubsDir(getActivity())).toString();
        String cacheOldFilePath = cacheDirPath + "/" + FileUtils.getName(oldUri);

        if (!cacheOldFilePath.equals(oldFilePath)) {
            String cacheNewFilePath = cacheDirPath + "/" + FileUtils.getName(newUri);

            File cacheOldFile = new File(cacheOldFilePath);
            File cacheNewFile = new File(cacheNewFilePath);
            if (cacheOldFile.exists()) {
                try {
                    cacheOldFile.renameTo(cacheNewFile);
                    if (DBG) Log.d(TAG, "onItemClick : selected file renamed as " + cacheNewFilePath);
                }
                catch (Exception e) {
                    Log.d(TAG, "renameFile : can not rename file as " + cacheNewFilePath);
                }
            }
        }

        if (fileRenamed) {
            // Update the medialib
            Intent intent1 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, oldUri);
            intent1.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent1);
            Intent intent2 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, newUri);
            intent2.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent2);

            if (DBG) Log.d(TAG, "rescanning Video: " + mVideoUri.toString());
            Intent intent3 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mVideoUri);
            intent3.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent3);

            // Update the local data : add the file to current subtitles list
            // and remove it from the available subtitles list
            mCurrentFiles.add(newFilePath);
            mCurrentFilesCount++;
            mAvailableFiles.remove(index);
            mAvailableFilesCount--;
        }

        return fileRenamed;
    }

    public boolean deleteFile(String path, int index, boolean current) {
        boolean fileDeleted = false;
        
        Uri uri = Uri.parse(path);
        FileEditor file = FileEditorFactory.getFileEditorForUrl(uri, getActivity());
        try {
            file.delete();
            if (DBG) Log.d(TAG, "deleteFile : file " + path + " deleted");
        }
        catch (Exception e) {
            Log.d(TAG, "deleteFile : can not delete file " + path);
        }

        fileDeleted = !file.exists();

        String cacheDirPath = Uri.fromFile(MediaUtils.getSubsDir(getActivity())).toString();
        String cacheFilePath = cacheDirPath + "/" + FileUtils.getName(uri);

        if (!cacheFilePath.equals(path)) {
            File cacheFile = new File(cacheFilePath);
            if (cacheFile.exists()) {
                try {
                    cacheFile.delete();
                    if (DBG) Log.d(TAG, "deleteFile : file " + cacheFilePath + " deleted");
                }
                catch (Exception e) {
                    Log.d(TAG, "deleteFile : can not delete file " + cacheFilePath);
                }
            }
        }

        if (fileDeleted) {
            // Update the medialib
            Intent intent1 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri);
            intent1.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent1);

            if (DBG) Log.d(TAG, "rescanning Video: " + mVideoUri.toString());
            Intent intent2 = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mVideoUri);
            intent2.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent2);
            // Update the local data : remove the file from the list it belongs
            if (current) {
                mCurrentFiles.remove(index);
                mCurrentFilesCount--;
            }
            else {
                mAvailableFiles.remove(index);
                mAvailableFilesCount--;
            }
        }

        return fileDeleted;
    }

    public String getHelpMessage() {
        String name = FileUtils.getFileNameWithoutExtension(mVideoUri);

        String helpMessage;
        if (mAvailableFilesCount == 0 && mCurrentFilesCount == 0) {
            helpMessage = getString(R.string.subtitles_wizard_empty_list_help).replace("%s", name);
        }
        else {
            helpMessage = getString(R.string.subtitles_wizard_help).replace("%s", name);
        }

        return helpMessage;
    }

    public String getFileName(String path) {
        Uri uri = Uri.parse(path);
        String name = FileUtils.getName(uri);

        return name;
    }

    public String getFileSize(String path) {
        String size = "";

        Uri uri = Uri.parse(path);
        MetaFile2 file = null;
        try {
            file = MetaFile2Factory.getMetaFileForUrl(uri);
        }
        catch (Exception e) {
            Log.e(TAG, "getFileSize error : can not get file");
        }
        if (file != null) {
            size = Formatter.formatFileSize(getActivity(), file.length());
        }

        return size;
    }

    public boolean isCacheFile(String path) {
        String cacheDirPath = Uri.fromFile(MediaUtils.getSubsDir(getActivity())).toString();
        boolean cache = path.startsWith(cacheDirPath + "/");

        return cache;
    }
}
