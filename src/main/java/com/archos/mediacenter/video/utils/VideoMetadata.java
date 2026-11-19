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

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.LibAvos;
import com.archos.medialib.MediaFactory;
import com.archos.medialib.MediaMetadata;
import com.archos.mediaprovider.video.VideoStore;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

public class VideoMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(VideoMetadata.class);

    private File mFile;
    private String mRemotePath;
    private String mSubtitleFingerprint;
    private boolean mHasSubtitleLanguageEdit;
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
            colorTrc = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_COLOR_TRC);
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
            colorTrc = 0;
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
        public final int colorTrc; // AVCOL_TRC_* (e.g. 16=SMPTE2084/PQ, 18=HLG)
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
            language = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_LANGUAGE);
            disposition = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_DISPOSITION);
            if (log.isDebugEnabled()) log.debug("AudioTrack name={}, format={}, language={}, disposition={}", name, format, language, disposition);
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
            language = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_LANGUAGE);
            disposition = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_AUDIO_TRACK_DISPOSITION);
            if (log.isDebugEnabled()) log.debug("AudioTrack name={}, format={}, disposition={}", name, format, disposition);
        }

        public final String  name;
        public final String  format;
        public final int     bitRate;
        public final int     sampleRate;
        public final String  channels;
        public final boolean vbr;
        public final boolean supported;
        public final String  language;
        public final int     disposition;
    }

    public static class SubtitleTrack implements Serializable {
        private static final long serialVersionUID = 1L;

        SubtitleTrack(MediaMetadata data, int idx) {
            index = idx;
            int gapKey = IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_MAX * idx;
            name = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_NAME);
            path = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_PATH);
            isExternal = (path != null) && (!path.isEmpty()); // it is "" for internal subs
            kind = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_KIND);
            format = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_FORMAT);
            language = getMetadataString(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE);
            disposition = getMetadataInt(data, gapKey + IMediaPlayer.METADATA_KEY_SUBTITLE_TRACK_DISPOSITION);
            languageSource = getMetadataInt(data, IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE + idx);
            languageConfidence = getMetadataInt(data, IMediaPlayer.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE + idx);
        }
        
        SubtitleTrack(IMediaMetadataRetriever retriever, int idx) {
            index = idx;
            // note no bool method here
            int gapKey = IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_MAX * idx;
            name = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_NAME);
            path = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_PATH);
            isExternal = (path != null) && (!path.isEmpty());
            kind = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_KIND);
            format = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_FORMAT);
            language = getMetadataRetrieverString(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE);
            disposition = getMetadataRetrieverInt(retriever, gapKey + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_DISPOSITION);
            languageSource = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE + idx);
            languageConfidence = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE + idx);
        }

        private SubtitleTrack(SubtitleTrack original, String inferred, int score) {
            name=original.name; path=original.path; isExternal=original.isExternal;
            kind=original.kind; format=original.format; disposition=original.disposition; index=original.index;
            language=inferred; languageSource=com.archos.medialib.SubtitleLanguage.FASTTEXT; languageConfidence=score;
        }

        public final String name;
        public final String path;
        public final boolean isExternal;
        /**
         * Raw SUB_KIND exactly as native sent it (0 if absent). Deliberately not interpreted
         * here: SubtitleManager.kindFromNative() is the one place that gives it meaning.
         */
        public final int kind;
        public final int format;
        public final String language;
        public final int disposition;
        public final int languageSource;
        public final int languageConfidence;
        public final int index;
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
            mFile = new File("file".equals(Uri.parse(path).getScheme()) ? Uri.parse(path).getPath() : path);
    }

    public VideoMetadata(VideoMetadata other) {
        mFile = other.mFile;
        mRemotePath = other.mRemotePath;
        mSubtitleFingerprint = other.mSubtitleFingerprint;
        mHasSubtitleLanguageEdit = other.mHasSubtitleLanguageEdit;
        mVideoTrack = other.mVideoTrack;
        mAudioTrackList = other.mAudioTrackList != null ? Arrays.copyOf(other.mAudioTrackList, other.mAudioTrackList.length) : null;
        mSubtitleTrackList = other.mSubtitleTrackList != null ? Arrays.copyOf(other.mSubtitleTrackList, other.mSubtitleTrackList.length) : null;
        mDuration = other.mDuration;
        mVideoWidth = other.mVideoWidth;
        mVideoHeight = other.mVideoHeight;
        mFileSize = other.mFileSize;
    }

    private void reset() {
        mSubtitleFingerprint = null;
        mHasSubtitleLanguageEdit = false;
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
        mSubtitleFingerprint = getMetadataString(data, IMediaPlayer.METADATA_KEY_SUBTITLE_FINGERPRINT);
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
        fillFromRetriever(ctx, null);
    }

    public void fillFromRetriever(Context ctx, Map<String, String> headers) {
        fillFromRetriever(ctx, headers, false, null);
    }

    public boolean hasUnknownSubtitleLanguages() {
        if (mSubtitleTrackList == null) return false;
        for (SubtitleTrack track : mSubtitleTrackList) {
            if (!track.isExternal && track.languageSource == com.archos.medialib.SubtitleLanguage.UNKNOWN
                    && (track.language == null || track.language.isEmpty() || "und".equals(track.language))
                    && (track.kind == com.archos.mediacenter.video.player.SubtitleManager.KIND_PLAIN_TEXT
                    || track.kind == com.archos.mediacenter.video.player.SubtitleManager.KIND_SSA)) return true;
        }
        return false;
    }

    /** Run on a metadata worker, never the UI thread. Cancellation does not wait for I/O. */
    public void fillFromRetriever(Context ctx, Map<String, String> headers, boolean analyzeLanguages,
            android.os.CancellationSignal cancellation) {
        if (mFile == null && mRemotePath == null)
            return;
        reset();

        int nbTrack;
        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(ctx);

        try {
            if (cancellation != null) {
                cancellation.setOnCancelListener(retriever::cancel);
                cancellation.throwIfCanceled();
            }
            if (mFile != null) {
                if (log.isDebugEnabled()) log.debug("fillFromRetriever: local file={}", mFile.getPath());
                retriever.setDataSource(mFile.getPath());
            } else if (UriUtils.isContentUri(Uri.parse(mRemotePath))) {
                retriever.setDataSource(ctx, Uri.parse(mRemotePath));
            } else {
                if (headers != null && !headers.isEmpty()) {
                    if (log.isDebugEnabled()) log.debug("fillFromRetriever: remote url={} with {} headers: {}", mRemotePath, headers.size(), headers.keySet());
                    retriever.setDataSource(mRemotePath, headers);
                } else {
                    if (log.isDebugEnabled()) log.debug("fillFromRetriever: remote url={} (no headers)", mRemotePath);
                    retriever.setDataSource(mRemotePath);
                }
            }
            if (analyzeLanguages) throw new IllegalStateException("Use the explicit analysis service");
            mSubtitleFingerprint = retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_FINGERPRINT);
            if (cancellation != null) cancellation.throwIfCanceled();
            mFileSize = getMetadataRetrieverLong(retriever, IMediaMetadataRetriever.METADATA_KEY_FILE_SIZE);
            nbTrack = getMetadataRetrieverInt(retriever, IMediaMetadataRetriever.METADATA_KEY_NB_VIDEO_TRACK);
            if (log.isDebugEnabled()) log.debug("fillFromRetriever: fileSize={} nbVideoTrack={}", mFileSize, nbTrack);
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
            applyStoredSubtitleLanguages(ctx, mRemotePath != null ? mRemotePath : Uri.fromFile(mFile).toString());
            try {
                org.json.JSONObject journal = com.archos.medialib.SubtitleLanguageStore.journal(ctx,
                        mRemotePath != null ? mRemotePath : Uri.fromFile(mFile).toString());
                mHasSubtitleLanguageEdit = journal != null && !"UNDONE".equals(journal.optString("state"));
            } catch (Exception e) { android.util.Log.w("SubtitleLanguages", "Cannot read recovery journal", e); }

        } catch (Exception ex) {
            log.error("fillFromRetriever: failed for url={}, ex={}", mRemotePath != null ? mRemotePath : (mFile != null ? mFile.getPath() : "null"), ex.getMessage());
        } finally {
            if (cancellation != null) cancellation.setOnCancelListener(null);
            try { retriever.release(); }
            catch (IOException e) { log.error("fillFromRetriever: release failed", e); }
        }
    }

    public boolean hasSubtitleLanguageEdit() { return mHasSubtitleLanguageEdit; }

    public String getSubtitleFingerprint() { return mSubtitleFingerprint; }

    public boolean hasInferredSubtitleLanguages() {
        if (mSubtitleTrackList != null) for (SubtitleTrack track : mSubtitleTrackList)
            if (track.languageSource == com.archos.medialib.SubtitleLanguage.FASTTEXT) return true;
        return false;
    }

    public void applyStoredSubtitleLanguages(Context context, String path) {
        if (path == null || mSubtitleTrackList == null) return;
        Uri uri = Uri.parse(path);
        if (uri.getScheme() == null) uri = Uri.fromFile(new File(path));
        org.json.JSONObject saved = com.archos.medialib.SubtitleLanguageStore.load(context, uri.toString(), mSubtitleFingerprint);
        if (saved == null) return;
        try {
            org.json.JSONArray tracks = saved.getJSONArray("tracks");
            if (tracks.length() != mSubtitleTrackList.length) return;
            for (int i=0; i<tracks.length(); i++) {
                org.json.JSONObject result = tracks.getJSONObject(i);
                SubtitleTrack original = mSubtitleTrackList[i];
                if (!original.isExternal && original.languageSource == com.archos.medialib.SubtitleLanguage.UNKNOWN
                        && result.optInt("source") == com.archos.medialib.SubtitleLanguage.FASTTEXT
                        && result.optInt("score") >= 85 && result.optString("language").matches("[a-z]{2,3}"))
                    mSubtitleTrackList[i] = new SubtitleTrack(original, result.getString("language"), result.getInt("score"));
            }
        } catch (org.json.JSONException ignored) { }
    }

    public VideoTrack getVideoTrack() {
        return mVideoTrack;
    }
    public AudioTrack getAudioTrack(int idx) {
        return mAudioTrackList != null && idx < mAudioTrackList.length ? mAudioTrackList[idx] : null;
    }

    public SubtitleTrack getSubtitleTrack(int idx) {
        return mSubtitleTrackList != null && idx < mSubtitleTrackList.length && idx >= 0 ? mSubtitleTrackList[idx] : null;
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
            log.warn("getMetadataRetrieverInt: key ({}) is null", key);
        }
        return 0;
    }

    private static long getMetadataRetrieverLong(IMediaMetadataRetriever retriever, int key) {
        try {
            return Long.parseLong(retriever.extractMetadata(key));
        } catch (NumberFormatException e) {
            log.warn("getMetadataRetrieverLong: key ({}) is null", key);
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
