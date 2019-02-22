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

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.LibAvos;
import com.archos.medialib.MediaFactory;
import com.archos.medialib.MediaMetadata;
import com.archos.mediaprovider.video.VideoStore;

import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;

public class VideoMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String TAG = "VideoMetadata";

    private File mFile;
    private String mRemotePath;
    private VideoTrack mVideoTrack;
    private AudioTrack[] mAudioTrackList;
    private SubtitleTrack[] mSubtitleTrackList;
    private int mDuration;
    private int mVideoWidth;
    private int mVideoHeight;
    private long mFileSize;

    public static String getH264ProfileName(int profile) {
        switch (profile) {
            case 66: return "Baseline Profile";
            case 77: return "Main Profile";
            case 88: return "Extended Profile";
            case 100:
            case 110:
            case 122:
            case 144: return "High Profile";
            default:
                return null;
        }
    }

    public static class VideoTrack implements Serializable {
        private static final long serialVersionUID = 1L;

        VideoTrack(MediaMetadata data) {
            int gapKey = IMediaPlayer.METADATA_KEY_VIDEO_TRACK;
            
            format = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_FORMAT);
            profile = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_PROFILE);
            level = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_LEVEL);
            bitRate = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_BIT_RATE);
            fps = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_FPS);
            fpsRate = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_FPS_RATE);
            fpsScale = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_FPS_SCALE);
            s3dMode = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_S3D);
            decoder = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_DECODER);
        }
        
        VideoTrack(IMediaMetadataRetriever retriever) {
            int gapKey = IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK;

            format = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_FORMAT);
            profile = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_PROFILE);
            level = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_LEVEL);
            bitRate = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_BIT_RATE);
            fps = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_FPS);
            fpsRate = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_FPS_RATE);
            fpsScale = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_FPS_SCALE);
            s3dMode = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_VIDEO_TRACK_S3D_MODE);
            decoder = LibAvos.MP_DECODER_ANY;
        }

        public final String format;
        public final int profile;
        public final int level;

        public final int bitRate;
        public final int fps;
	public final int fpsRate;
	public final int fpsScale;
        public final int s3dMode;
        public final int decoder;
    }

    public static class AudioTrack implements Serializable {
        private static final long serialVersionUID = 1L;

        AudioTrack(MediaMetadata data, int idx) {
            int gapKey = IMediaPlayer.METADATA_KEY_AUDIO_TRACK + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_MAX * idx;
            
            name = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_NAME);
            format = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_FORMAT);
            bitRate = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_BIT_RATE);
            sampleRate = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_SAMPLE_RATE);
            channels = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_CHANNELS);
            vbr = getMetadataBool(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_VBR);
            supported = getMetadataBool(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_SUPPORTED);
        }
        
        AudioTrack(IMediaMetadataRetriever retriever, int idx) {
            int gapKey = IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_MAX * idx;

            name = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_NAME);
            format = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_FORMAT);
            bitRate = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_BIT_RATE);
            sampleRate = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_SAMPLE_RATE);
            channels = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_CHANNELS);
            vbr = getMetadataRetrieverBool(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_VBR);
            supported = getMetadataRetrieverBool(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_SUPPORTED);
        }

        public final String  name;
        public final String  format;
        public final int     bitRate;
        public final int     sampleRate;
        public final String  channels;
        public final boolean vbr;
        public final boolean supported;
    }

    public static class SubtitleTrack implements Serializable {
        private static final long serialVersionUID = 1L;

        SubtitleTrack(MediaMetadata data, int idx) {
            int gapKey = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX * idx;

            name = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_NAME);
            String path = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_PATH);
            isExternal = (path != null) && (path.length() > 0); // it is "" for internal subs
        }
        
        SubtitleTrack(IMediaMetadataRetriever retriever, int idx) {
            int gapKey = IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_MAX * idx;
            
            name = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_NAME);
            String path = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_PATH);
            isExternal = (path != null) && (path.length() > 0);
        }

        public final String name;
        public final boolean isExternal;
    }

    public VideoMetadata() {
        reset();
        mFile = null;
        mRemotePath = null;
    }

    public VideoMetadata(String path) {
        reset();
        mFile = null;
        mRemotePath = null;
        if (!FileUtils.isLocal(Uri.parse(path))|| UriUtils.isContentUri(Uri.parse(path)))
            mRemotePath = path;
        else
            mFile = new File(path);
    }

    private void reset() {
        mVideoTrack = null;
        mAudioTrackList = null;
        mSubtitleTrackList = null;
        mDuration = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        mFileSize = 0;
    }

    public void setFile(String path) {
        mFile = new File(path);
    }

    public File getFile() {
        return mFile;
    }

    public void setData(MediaMetadata data) {
        reset();
        int nbTrack;
        if (data.has(IMediaPlayer.METADATA_KEY_FILE_SIZE))
            mFileSize = data.getLong(IMediaPlayer.METADATA_KEY_FILE_SIZE);
	if (data.has(IMediaPlayer.METADATA_KEY_DURATION))
            mDuration = data.getInt(IMediaPlayer.METADATA_KEY_DURATION);
        if (data.has(IMediaPlayer.METADATA_KEY_VIDEO_WIDTH))
            mVideoWidth = data.getInt(IMediaPlayer.METADATA_KEY_VIDEO_WIDTH);
        if (data.has(IMediaPlayer.METADATA_KEY_VIDEO_HEIGHT))
            mVideoHeight = data.getInt(IMediaPlayer.METADATA_KEY_VIDEO_HEIGHT);
        if (data.has(IMediaPlayer.METADATA_KEY_NB_VIDEO_TRACK) && data.getInt(IMediaPlayer.METADATA_KEY_NB_VIDEO_TRACK) > 0)
            mVideoTrack = new VideoTrack(data);
        if (data.has(IMediaPlayer.METADATA_KEY_NB_AUDIO_TRACK) && (nbTrack = data.getInt(IMediaPlayer.METADATA_KEY_NB_AUDIO_TRACK)) > 0) {
            mAudioTrackList = new AudioTrack[nbTrack];
            for (int i = 0; i < nbTrack; ++i)
                mAudioTrackList[i] = new AudioTrack(data, i);
        }
        if (data.has(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK) && (nbTrack = data.getInt(IMediaPlayer.METADATA_KEY_NB_SUBTITLE_TRACK)) > 0) {
            mSubtitleTrackList = new SubtitleTrack[nbTrack];
            for (int i = 0; i < nbTrack; ++i)
                mSubtitleTrackList[i] = new SubtitleTrack(data, i);
        }
    }
    
    public void fillFromRetriever(Context ctx) {
        if (mFile == null && mRemotePath == null)
            return;
        reset();

        int nbTrack;
        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(ctx);

        try {
            if (mFile != null)
                retriever.setDataSource(mFile.getPath());
            else
                retriever.setDataSource(mRemotePath);
            mFileSize = getMetadataRetrieverLong(retriever, IMediaMetadataRetriever.METADATA_KEY_FILE_SIZE);
            nbTrack = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_NB_VIDEO_TRACK);
            Log.d(TAG, "nbTrack: " + nbTrack);
            if (nbTrack > 0)
                mVideoTrack = new VideoTrack(retriever);
            nbTrack = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_NB_AUDIO_TRACK);
            if (nbTrack > 0) {
                mAudioTrackList = new AudioTrack[nbTrack];
                for (int i = 0; i < nbTrack; ++i)
                    mAudioTrackList[i] = new AudioTrack(retriever, i);
            }
            nbTrack = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK);
            if (nbTrack > 0) {
                mSubtitleTrackList = new SubtitleTrack[nbTrack];
                for (int i = 0; i < nbTrack; ++i)
                    mSubtitleTrackList[i] = new SubtitleTrack(retriever, i);
            }
            mVideoWidth = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            mVideoHeight = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            mDuration = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_DURATION);
        } catch (Exception ex) {
        }
        retriever.release();
    }

    public VideoTrack getVideoTrack() {
        return mVideoTrack;
    }
    public AudioTrack getAudioTrack(int idx) {
        return mAudioTrackList != null && idx < mAudioTrackList.length ? mAudioTrackList[idx] : null;
    }

    public SubtitleTrack getSubtitleTrack(int idx) {
        return mSubtitleTrackList != null && idx < mSubtitleTrackList.length ? mSubtitleTrackList[idx] : null;
    }
    public int getAudioTrackNb() {
        return mAudioTrackList != null ? mAudioTrackList.length : 0;
    }
    public int getSubtitleTrackNb() {
        return mSubtitleTrackList != null ? mSubtitleTrackList.length : 0;
    }
    public int getVideoWidth() {
        return mVideoWidth;
    }
    public int getVideoHeight() {
        return mVideoHeight;
    }
    public void setDuration(int duration) {
        mDuration = duration;
    }
    public int getDuration() {
        return mDuration;
    }
    public long getFileSize() {
        return mFileSize;
    }

    private static int getMetadataInt(MediaMetadata data, int key) {
        return data.has(key) ? data.getInt(key) : 0;
    }
    private static boolean getMetadataBool(MediaMetadata data, int key) {
        return data.has(key) ? data.getBoolean(key) : false;
    }
    private static String getMetadataString(MediaMetadata data, int key) {
        return data.has(key) ? data.getString(key) : "";
    }

    public ContentValues toContentValues(){
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Video.VideoColumns.WIDTH,String.valueOf(mVideoWidth));
        cv.put(MediaStore.Video.VideoColumns.HEIGHT ,String.valueOf(mVideoHeight));
        if(mVideoTrack!=null) {
            cv.put(VideoStore.Video.VideoColumns.ARCHOS_VIDEO_BITRATE, mVideoTrack.bitRate);
            cv.put(VideoStore.Video.VideoColumns.ARCHOS_FRAMES_PER_THOUSAND_SECONDS, String.valueOf(mVideoTrack.fpsRate * 100));
            cv.put(VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_VIDEO_FORMAT, mVideoTrack.format);
        }
        String json = "{ audiotracks:[ ";

        for (int n=0; n<getAudioTrackNb(); n++) {

                json += "{format : "+ JSONObject.quote(getAudioTrack(n).format);
                json += ",";
                json += "channels : "+ JSONObject.quote(getAudioTrack(n).channels)+"}";

                cv.put(VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT,getAudioTrack(n).format);

               if(n<getAudioTrackNb()-1)
                   json += ",";

        }
        json+="]}";
        cv.put(VideoStore.Video.VideoColumns.ARCHOS_CALCULATED_BEST_AUDIOTRACK_FORMAT,json);

        return cv;

    }

    public void save(Context context, String path){
        ContentValues values = toContentValues();
        context.getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                values, VideoStore.MediaColumns.DATA+" = ?",new String[]{path});
    }

    private static int getMetadataRetrieverInt(IMediaMetadataRetriever retriever, int key) {
        try {
            return Integer.parseInt(retriever.extractMetadata(key));
        } catch (NumberFormatException e) {
            Log.d(TAG, "key (" +key+ ") is null");
        }
        return 0;
    }

    private static long getMetadataRetrieverLong(IMediaMetadataRetriever retriever, int key) {
        try {
            return Long.parseLong(retriever.extractMetadata(key));
        } catch (NumberFormatException e) {
            Log.d(TAG, "key (" +key+ ") is null");
        }
        return 0;
    }

    private static boolean getMetadataRetrieverBool(IMediaMetadataRetriever retriever, int key) {
        return Boolean.parseBoolean(retriever.extractMetadata(key));
    }
    private static String getMetadataRetrieverString(IMediaMetadataRetriever retriever, int key) {
        return retriever.extractMetadata(key);
    }
}
