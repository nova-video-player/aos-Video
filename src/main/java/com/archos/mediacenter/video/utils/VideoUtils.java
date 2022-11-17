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

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.Log;

import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;

import java.io.File;
import java.util.Arrays;
import java.util.List;


public class VideoUtils {
    private final static String TAG = "VideoUtils";
    private final static boolean DBG = false;

    // Available view modes (values must be powers of 2)
    public static final int VIEW_MODE_LIST    = 1;
    public static final int VIEW_MODE_GRID    = 1 << 1;
    public static final int VIEW_MODE_DETAILS = 1 << 2;
    public static final int VIEW_MODE_GRID_SHORT = 1 << 4;

    // Available categories of view modes for a given activity
    public static final int ALLOWED_VIEW_MODE_LIST       = VIEW_MODE_LIST;
    public static final int ALLOWED_VIEW_MODES_LIST_GRID = VIEW_MODE_LIST | VIEW_MODE_GRID;
    public static final int ALLOWED_VIEW_MODES_ALL       = VIEW_MODE_LIST | VIEW_MODE_GRID | VIEW_MODE_DETAILS;

    // View modes to select by default for each category
    public static final int VIEW_MODES_LIST_GRID_DEFAULT = VIEW_MODE_GRID;
    public static final int VIEW_MODES_ALL_DEFAULT = VIEW_MODE_GRID;

    public static final String ALLOWED_VIEW_MODES_EXTRA = "allowed_view_modes";

    private static final String[] VIDEO_MIME_TYPES_ARRAY = {"video/", "application/x-bittorrent"};

    // keep in sync with com.archos.mediaprovider.ArchosMediaFile
    private static final String[] SUBTITLES_ARRAY = { "srt", "smi", "ssa", "ass", "srr", "idx", "sub", "mpl", "txt"};
    private static final List<String> SUBTITLES_ARRAYLIST = Arrays.asList(SUBTITLES_ARRAY);

    // Preferences
    public final static String RECENTLY_ADDED_PERIOD_IN_DAYS_KEY = "VideoRecentlyAddedPeriodInDays";
    public final static int RECENTLY_ADDED_PERIOD_IN_DAYS_DEFAULT_VALUE = 7;

    // Set the device ID obtained by viewing the logcat output after creating a new ad to make your device gets test ads.
    // e.g. Use AdRequest.Builder.addTestDevice("92ABC6BFCB32F831DBEC0A35283BD46E") to get test ads on this device.
    public final static String TEST_ADS_DEVICE_ID = "92ABC6BFCB32F831DBEC0A35283BD46E";

 /**
  * Delete subtitles associated with a video file
  */
    public static void deleteAssociatedSubtitles(File videoFile) {
    	if(DBG) Log.d(TAG, "deleteAssociatedSubtitles " + videoFile.getPath());
    	if (videoFile.isDirectory()) {
            return;
    	}

        String name = videoFile.getName();
        // Remove the extension from name
        int dotPos = name.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < name.length()) {
            name = name.substring(0, dotPos);
        }

        // Get files from the same directory
    	File directory = videoFile.getParentFile();
    	if (!directory.isDirectory()) {
            if(DBG) Log.e(TAG, "Can't get the directory containing videoFile, may have been erased from storage but not from library");
            return;
    	}
    	File[] files = directory.listFiles();

        if (files != null) {
            // Look for subtitle files with same name in the same directory
            for (File f : files) {
                String fullName = f.getName();
                //if(DBG) Log.d(TAG, "deleteAssociatedSubtitles: checking file " + fullName + " ("+name+")");
                if (f.isFile() && fullName.startsWith(name)) {
                    //if(DBG) Log.d(TAG, "deleteAssociatedSubtitles: Match for " + fullName);
                    dotPos = fullName.lastIndexOf('.');
                    if (dotPos >= 0 && dotPos < fullName.length()) {
                        String ext = fullName.substring(dotPos + 1).toLowerCase();
                        if (getSubtitleExtensions().contains(ext)) {
                            if(DBG) Log.d(TAG, "deleteAssociatedSubtitles: deleting file " + f.getPath());
                            f.delete();
                        }
                    }
                }
            }
        }
    }

    public static String[] getVideoFilterMimeTypes() {
        return VIDEO_MIME_TYPES_ARRAY;
    }

    public static String[] getSubtitleFilterExtensions() {
        return SUBTITLES_ARRAY;
    }

    public static List<String> getSubtitleExtensions() {
    	return SUBTITLES_ARRAYLIST;
    }

    static public CharSequence getLanguageString(Context context, CharSequence name) {
        final Resources resources = context.getResources();
        CharSequence lang;

        if(name==null)
            return resources.getText(R.string.unknown_track_name);
        int resId = resources.getIdentifier((String) name, "string", context.getPackageName());
        try {
            lang = resources.getText(resId);
        } catch (Resources.NotFoundException e) {
            lang = name;
        }
        return lang;
    }

    /**
     * Build a filepath compatible with MediaLib from Uri
     * Not compatible with Uris like "content://" (will return null)
     * @param uri
     * @return path
     */
    public static String getMediaLibCompatibleFilepathFromUri(Uri uri) {
        if (uri == null) return null;
        String filePath;
        if ("content".equals(uri.getScheme())) {
            filePath = uri.toString();
        }
        else if ("file".equals(uri.getScheme())) {
            // in the local file case we want no leading "file" scheme
            filePath = uri.getPath();
        }
        else {
            // In other cases (smb, etc.) we need the full Uri, scheme included
            filePath = uri.toString();
        }
        return filePath;
    }

    /**
     * Build an Uri from the path stored in the MediaLib
     * @param path
     * @return
     */
    public static Uri getFileUriFromMediaLibPath(String path) {
        if (path == null) return null;
        if (path.startsWith("/")) {
            // local file, need to add the scheme (file scheme not stored in MediaDb)
            return Uri.parse("file://"+path);
        }
        else {
            // for smb (and others?) the scheme is stored in the path
            return Uri.parse(path);
        }
    }

    public static int convertDpToPixel(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    // provide path for file like content://com.archos.media/external/video/media/51
    public static String getFileUriStringFromContentUri(Context context, String path) {
        if (path == null) return null;
        Uri mPath = Uri.parse(path);
        if (mPath.getScheme() != null && mPath.getScheme().equals("content")) {
            int id = 0;
            try {
                id = Integer.parseInt(mPath.getLastPathSegment());
                ContentResolver cr = context.getContentResolver();
                VideoDbInfo videoDbInfo = VideoDbInfo.fromId(cr, id);
                if (DBG) Log.d(TAG, "getFileUriFromContentUri content translated from " + mPath + " to " + ((videoDbInfo != null) ? videoDbInfo.uri : null));
                if (videoDbInfo !=null && videoDbInfo.uri != null)
                    return videoDbInfo.uri.toString();
                else {
                    Log.w(TAG, "getFileUriFromContentUri: videoDbInfo is null for " + path);
                    return null;
                }
            } catch (NumberFormatException e) {
                id = -1;
                return null;
            }
        } else {
            return null;
        }
    }

}
