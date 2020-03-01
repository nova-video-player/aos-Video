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

package com.archos.mediacenter.video.cover;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.cover.Cover;
import com.archos.mediacenter.utils.InfoDialog;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.VideoStore;

import java.io.File;

/**
 * This class is used for Movies
 * It represents a ScraperStore "Movie" entry
 */
abstract public class BaseVideoCover extends Cover {

    final static String TAG = "BaseVideoCover";
    final static boolean DBG = false;

    protected final static int DESCRIPTION_TEXTURE_WIDTH = 256;
    protected final static int DESCRIPTION_TEXTURE_HEIGHT = 128;

    protected final static float THUMBNAIL_SHRINK_FACTOR = 0.75f; // The video thumbs are rendered smaller than the video covers (in order to fit together in the roll

    protected String mFilepath;
    protected long mDurationMs;

    public BaseVideoCover(long videoId, String filepath, long durationMs) {
        mObjectLibraryType = VideoStore.Video.Media.CONTENT_TYPE;
        mObjectLibraryId = videoId;

        mFilepath = filepath;
        mDurationMs = durationMs;
    }

    @Override
    public Runnable getOpenAction(Context context) {
    	return getOpenAction(context, PlayerActivity.RESUME_FROM_LAST_POS);
    }

    public Runnable getOpenAction(final Context context, final int resume) {
    	return new Runnable() {
    		public void run() {
                VideoInfoActivity.startInstance(context, null, FileUtils.getRealUriFromVideoURI(context, getUri()), new Long(-1));
               // PlayUtils.startVideo(context, getUri(), FileUtils.getRealUriFromVideoURI(context, getUri()), null, null, resume, false, null, true);
    		}
    	};
    }

    @Override
    public void play(Context context) {
    	getOpenAction(context, 0).run();
    }

    public void prepareInfoDialog(Context context, InfoDialog infoDialog) {
        // Not used for Video covers
    }

    @Override
    public Uri getUri() {
        final Uri uri = ContentUris.withAppendedId( VideoStore.Video.Media.EXTERNAL_CONTENT_URI, getMediaLibraryId() );
        Log.d(TAG,"getUri: "+uri);
        return uri;
    }

    public String getFilePath() {
        return mFilepath;
    }

    @Override
    public String getDescriptionName() {
        return Uri.fromFile(new File(mFilepath)).getLastPathSegment();
    }

    @Override
    public String getDebugName() {
        return mFilepath;
    }

    @Override
    public int getDescriptionWidth() {
        return DESCRIPTION_TEXTURE_WIDTH;
    }

    @Override
    public int getDescriptionHeight() {
        return DESCRIPTION_TEXTURE_HEIGHT;
    }
}