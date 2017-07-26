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

package com.archos.mediacenter.video.browser.adapters.object;

import android.content.ContentUris;
import android.net.Uri;

import com.archos.filecorelibrary.MimeUtils;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;

import java.io.Serializable;

/**
 * A video file that is in our DB.
 * CAUTION: for non-indexed videos one MUST use the NonIndexedVideo class instead
 * Created by vapillon on 10/04/15.
 */
public class Video extends Base implements Serializable {

    final long mId;
    final String mFilePath;
    private final boolean mIsTraktLibrary;
    private final long mLastTimePlayed;
    private final int mCalculatedWidth;
    private final int  mCalculatedHeight;
    private final String mAudioFormat;
    private final String mVideoFormat;
    private final int mCalculatedBestAudiotrack;
    private final String mGuessedVideoFormat;
    private final String mGuessedAudioFormat;
    private final int mOccurencies;
    private final long mSize;

    private boolean mHasSubs;
    String mFriendlyPath; // sometimes we will want a more beautiful uri (for example in upnp, with real server name)
    final String mFilenameWithExtension;
    /** Duration in milliseconds. May equals PlayerActivity.LAST_POSITION_UNKNOWN or PlayerActivity.LAST_POSITION_END */
    int mDurationMs;
    int mResumeMs;
    int mRemoteResume =-1;

    final int mVideo3dMode; // one of ARCHOS_STEREO_2D, ARCHOS_STEREO_3D_UNKNOWN, ARCHOS_STEREO_3D_SBS, ARCHOS_STEREO_3D_TB, ARCHOS_STEREO_3D_ANAGLYPH
    final int mGuessedDefinition; // one of ARCHOS_DEFINITION_UNKNOWN, ARCHOS_DEFINITION_720P, ARCHOS_DEFINITION_1080P
    final boolean mIsTraktSeen;
    final boolean mIsUserHidden;

    /** Optional metadata obtained by actually reading/decoding the file */
    private VideoMetadata mMetaData;
    private String mStreamingUri;

    public Video(long id, String filePath, String name, Uri posterUri, int durationMs, int resumeMs,
                 int video3dMode, int guessedDefinition, boolean traktSeen, boolean isTraktLibrary, boolean hasSubs, boolean isUserHidden, long lastTimePlayed, long size) {
        this(id, filePath, name, posterUri, durationMs, resumeMs,
        video3dMode, guessedDefinition, traktSeen, isTraktLibrary, hasSubs, isUserHidden, lastTimePlayed,-1, -1, null, null, null, null, -1,1,size);
    }



    public Video(long id, String filePath, String name, Uri posterUri, int durationMs, int resumeMs,
                 int video3dMode, int guessedDefinition, boolean traktSeen, boolean isTraktLibrary,
                 boolean hasSubs,
                 boolean isUserHidden,
                 long lastTimePlayed, int calculatedWidth, int calculatedHeight, String audioFormat, String videoFormat, String guessedAudioFormat, String guessedVideoFormat,  int calculatedBestAudiotrack, int occurencies, long size) {
        super(name, posterUri);
        if (id<0 && !(this instanceof NonIndexedVideo)) {
            throw new IllegalArgumentException("id MUST be a valid MediaDB id (you must use NonIndexedVideo for non-indexed video files");
        }
        mId = id;
        mSize=size;
        mIsTraktLibrary = isTraktLibrary;
        mFilePath = filePath;
        mFilenameWithExtension = buildFileNameWithExtension(filePath);
        mDurationMs = durationMs;
        mResumeMs = resumeMs;
        mVideo3dMode = video3dMode;
        mGuessedDefinition = guessedDefinition;
        mIsTraktSeen = traktSeen;
        mIsUserHidden = isUserHidden;
        mHasSubs = hasSubs;
        mLastTimePlayed = lastTimePlayed;
        mCalculatedWidth =  calculatedWidth;
        mCalculatedHeight= calculatedHeight;
        mAudioFormat = audioFormat;
        mVideoFormat = videoFormat;
        mGuessedAudioFormat = guessedAudioFormat;
        mGuessedVideoFormat = guessedVideoFormat;
        mCalculatedBestAudiotrack= calculatedBestAudiotrack;
        mOccurencies = occurencies;
    }

    static protected String buildFileNameWithExtension(String filePath) {
        int lastSlash = filePath.lastIndexOf('/');
        if (lastSlash>=0 && filePath.length()>lastSlash+1) {
            return filePath.substring(lastSlash+1);
        } else {
            return filePath;
        }
    }

    public void setMetadata(VideoMetadata metaData) {
        mMetaData = metaData;
    }

    /**
     * @return metadata obtained by actually reading/decoding the file. May be null if it has not been set
     */
    public VideoMetadata getMetadata() {
        return mMetaData;
    }

    public boolean isIndexed() {
        return (mId>=0);
    }

    /** returns true if this file has a description, a poster, a backdrop, etc. (i.e. if it is a movie or an episode) */
    public boolean hasScraperData() {
        return ((this instanceof Movie) || (this instanceof Episode));
    }

    public long getId() {
        return mId;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public int getDurationMs() { return mDurationMs; }

    public int getResumeMs() { return mResumeMs; }

    public long getSize(){return mSize;}

    public void setResumeMs(int resumeMs) { mResumeMs = resumeMs; }
    public void setRemoteResumeMs(int resumeMs) { mRemoteResume = resumeMs; }
    public boolean is3D() {
        return (mVideo3dMode > VideoStore.Video.VideoColumns.ARCHOS_STEREO_2D);
    }

    public int getCalculatedBestAudiotrack(){
        return mCalculatedBestAudiotrack;
    }

    /**
     * @return the guessed definition according to the file name, i.e. one of ARCHOS_DEFINITION_SD, ARCHOS_DEFINITION_720P, ARCHOS_DEFINITION_1080P
     */
    public int getGuessedDefinition() {
        return mGuessedDefinition;
    }



    /**
     * turns calculated width and height DB or metadata information into one of ARCHOS_DEFINITION_SD, ARCHOS_DEFINITION_720P, ARCHOS_DEFINITION_1080P
     * ore returns getGuessedDefinition when not available
     * @return
     */

    public int getNormalizedDefinition(){
        int w = mCalculatedWidth;
        int h = mCalculatedHeight;
        if(getMetadata()!=null){
            w = getMetadata().getVideoWidth();
            h = getMetadata().getVideoHeight();
        }
        if(w>0&&h>0) {
            // Resolution badge

            if (w >= 3840 || h >= 2160) {
                return  VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K;
            }
            // normally you would expect w>=1920 || h>=1080 but based on collection empirical observation we need to include a margin to detect fhd video resolution
            else if (w >= 1728 || h >= 1040) {
                return  VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P;
            }
            // normally you would expect w>=1280 || h>=720 but based on collection empirical observation we need to include a margin to detect hd video resolution
            else if (w >= 1200 || h >= 720) {
                return  VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P;
            } else if (w > 0 && h > 0) {
                return  VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_SD;
            }
        }
        return getGuessedDefinition();

    }

    public boolean isWatched() {
        return mIsTraktSeen;
    }

    public boolean isUserHidden() {
        return mIsUserHidden;
    }

    /** Default implementation returns empty string */
    public String getDescriptionBody() {
        return "";
    }

    /**
     * Default for indexed videos is to return the DB Uri.
     * CAUTION: it is overriden for NonIndexedVideo
     **/
    public Uri getUri() {
        return getDbUri();
    }

    public Uri getDbUri() {
        return ContentUris.withAppendedId(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, mId);
    }
    // this point to the file
    public Uri getFileUri() {
        return VideoUtils.getFileUriFromMediaLibPath(mFilePath);
    }

    // this point to the stream file, For UPNP this will be a http:// uri Most of the time this will be the same as getFileUri
    public Uri getStreamingUri() {
        return mStreamingUri ==null?getFileUri():Uri.parse(mStreamingUri);
    }

    public boolean isLocalFile() {
        return getFileUri().getScheme().equals("file");
    }

    public String getFileNameWithExtension() {
        return mFilenameWithExtension;
    }

    /**
     * @return the filename if it is not cryptic (see {@link boolean filenameMayBeCryptic()}), else the (nice) name
     */
    public String getFilenameNonCryptic() {
        if (filenameMayBeCryptic()) {
            return getName();
        }
        else {
            return getFileNameWithExtension();
        }
    }

    /** based on the file extension. May return null */
    public String getMimeType() {
        return MimeUtils.guessMimeTypeFromExtension(MimeUtils.getExtension(mFilePath));
    }

    /**
     * Return true if the video location supports deleting files.
     * Basically it returns false for UPnP only.
     * CAUTION: it does not check write permission, i.e. it will return true for
     * all SMB and/or FTP, even if the delete fails after due to missing write permission
     * @return
     */
    public boolean locationSupportsDelete() {
        if ("upnp".equals(getFileUri().getScheme())) {
            return false;
        }
        return true;
    }

    /**
     * Return true if the filename taken from the Uri is not relevant to be displayed to the end-user
     * This method is needed because in some case we want to display the full filename (with extension) to the
     * end-user and we extract it from the url ; If the url is "cryptic" we know we must not do that.
     * Basically this is used for UPnP only.
     *
     * @return
     */
    public boolean filenameMayBeCryptic() {
        if ("upnp".equals(getFileUri().getScheme()) || "http".equals(getFileUri().getScheme())) {
            return true;
        }
        return false;
    }

    public void setStreamingUri(Uri uri) {
        mStreamingUri = uri.toString();
    }

    public void setFriendlyPath(String friendlyUri){
        mFriendlyPath = friendlyUri;
    }
    public String getFriendlyPath(){
        if(mFriendlyPath !=null)
            return mFriendlyPath;
        else return getFilePath();
    }

    public void setDuration(int duration) {
        mDurationMs = duration;
    }

    public int getRemoteResumeMs() {
        return mRemoteResume;
    }

    public boolean isTraktLibrary() {
        return mIsTraktLibrary;
    }

    public boolean hasSubs() {
        return mHasSubs;
    }

    public void setHasSubs(boolean hasSubs) {
        mHasSubs = hasSubs;
    }

    public long getLastPlayed() {
        return mLastTimePlayed;
    }

    public String getVideoFormat() {
        return mVideoFormat;
    }

    public String getAudioFormat() {
        return mAudioFormat;
    }

    public String getGuessedVideoFormat() {
        return mGuessedVideoFormat;
    }

    public String getGuessedAudioFormat() {
        return mGuessedAudioFormat;
    }

    public String getCalculatedVideoFormat() {
        return mVideoFormat;
    }

    public String getCalculatedBestAudioFormat() {
        return mAudioFormat;
    }

    public int getOccurencies() {
        return mOccurencies;
    }
}
