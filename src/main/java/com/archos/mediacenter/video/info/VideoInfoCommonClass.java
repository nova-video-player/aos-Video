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

package com.archos.mediacenter.video.info;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.mediacenter.filecoreextension.upnp2.FileEditorFactoryWithUpnp;
import com.archos.mediacenter.filecoreextension.upnp2.StreamUriFinder;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.medialib.IMediaPlayer;
import com.archos.medialib.LibAvos;
import com.archos.mediaprovider.video.VideoStore;

import org.fourthline.cling.model.meta.Device;

import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Created by alexandre on 15/01/16.
 */
public class VideoInfoCommonClass {
    final static String SEP = "  ";


    public static int getDarkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.4f; // value component
        color = Color.HSVToColor(hsv);
        return color;
    }

    public static int getClearerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 1.4f; // value component
        color = Color.HSVToColor(hsv);
        return color;
    }

    public static int getAlphaColor(int color, int alpha) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        color = Color.HSVToColor(alpha,hsv);
        return color;
    }

    public static VideoMetadata retrieveMetadata(Video video, Context context){
        String startingPath= video.getFilePath();
        String streamingPath = startingPath;
//first, look for upnp streaming uri
        if(streamingPath.startsWith("upnp")&&video!=null){
            StreamUriFinder streamUriFinder = new StreamUriFinder(video.getFileUri(),context);
            Uri uri= streamUriFinder.start_blocking();
            Device device = UpnpServiceManager.getSingleton(context).getDeviceByKey_blocking(Integer.parseInt(video.getFileUri().getHost()),1000);
            if(device!=null) {
                String friendlyName = UpnpServiceManager.getDeviceFriendlyName(device);
                if(friendlyName!=null){
                    video.setFriendlyPath("upnp://" + friendlyName + "/" + video.getFileUri().getLastPathSegment());
                }
            }
            if(uri != null){
                video.setStreamingUri(uri);
                streamingPath = uri.toString();
            }
            else {
                // failed to get streaming uri
                return new VideoMetadata(); //return empty metadata
            }
        }

        // Special SMB case: Checking if the file exist.
        // We do not call fillFromRetriever() when the file does not exist because we see it block forever on Android TV
        // if called two times with unexisting files (looks like a problem in SmbProxy.java, maybe due to
        // lower layers because we do not see it on some devices)
        if (streamingPath.startsWith("smb")) {
            FileEditor editor = FileEditorFactoryWithUpnp.getFileEditorForUrl(Uri.parse(streamingPath), null);
            if (!editor.exists()) {
                return new VideoMetadata(); //return empty metadata
            }
        }
        // Get metadata from file
        VideoMetadata videoMetaData = new VideoMetadata(streamingPath);
        videoMetaData.fillFromRetriever(context);
        return videoMetaData;

    }
    
    public static String getShortDecoder(VideoMetadata videoMetadata, Resources resources, int playerType){
        VideoMetadata.VideoTrack video = videoMetadata.getVideoTrack();
        int videoDecoderStringResId = -1;
        if (playerType == IMediaPlayer.TYPE_AVOS  && video != null) {
            switch (video.decoder) {
                case LibAvos.MP_DECODER_SW:            videoDecoderStringResId = R.string.dec_sw; break;
                case LibAvos.MP_DECODER_HW_OMX:
                case LibAvos.MP_DECODER_HW_OMXPLUS:
                case LibAvos.MP_DECODER_HW_OMXCODEC:
                case LibAvos.MP_DECODER_HW_MEDIACODEC: videoDecoderStringResId = R.string.dec_hw; break;
                default: break;
            }
        }
        else if (playerType == IMediaPlayer.TYPE_ANDROID) {
            videoDecoderStringResId = R.string.dec_unknown;
        }
        if(videoDecoderStringResId==-1)
            return null;
        return resources.getString(videoDecoderStringResId);
    }

    public static String getDecoder(VideoMetadata videoMetadata, Resources resources, int playerType){
        // Video Decoder (only when launched from player)
        VideoMetadata.VideoTrack video = videoMetadata.getVideoTrack();
        int videoDecoderStringResId = -1;
        if (playerType == IMediaPlayer.TYPE_AVOS  && video != null) {
            switch (video.decoder) {
                case LibAvos.MP_DECODER_SW:            videoDecoderStringResId = R.string.dec_sw; break;
                case LibAvos.MP_DECODER_HW_OMX:        videoDecoderStringResId = R.string.dec_hw_omx; break;
                case LibAvos.MP_DECODER_HW_OMXPLUS:    videoDecoderStringResId = R.string.dec_hw_omxplus; break;
                case LibAvos.MP_DECODER_HW_OMXCODEC:   videoDecoderStringResId = R.string.dec_hw_omxcodec; break;
                case LibAvos.MP_DECODER_HW_MEDIACODEC: videoDecoderStringResId = R.string.dec_hw_mediacodec; break;
                default: break;
            }
        }
        else if (playerType == IMediaPlayer.TYPE_ANDROID) {
            videoDecoderStringResId = R.string.dec_android;
        }
        if(videoDecoderStringResId==-1)
            return null;
        return resources.getString(videoDecoderStringResId);
    }
    public static String getAudioTrackString(VideoMetadata videoMetadata, Resources resources, Context c ) {
        // Audio track(s)
        int audioTrackNb = videoMetadata.getAudioTrackNb();
        if (audioTrackNb > 0) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < audioTrackNb; i++) {
                if (i > 0) {
                    sb.append("\n");
                }
                VideoMetadata.AudioTrack audio = videoMetadata.getAudioTrack(i);

                if (audioTrackNb > 1) {  // number and name of the track only if there are more than one track
                    sb.append(Integer.toString(i + 1)).append('.').append(SEP).append(VideoUtils.getLanguageString(c, audio.name)).append(SEP);
                }
                sb.append(audio.format).append(SEP);
                sb.append(audio.channels).append(SEP);
                if (audio.bitRate > 0) {
                    sb.append(audio.bitRate).append("kb/s").append(SEP);
                }
                sb.append(audio.sampleRate).append("Hz").append(SEP);
                if (audio.vbr) {
                    sb.append(resources.getText(R.string.info_audio_vbr)).append(SEP);
                }
            }
            return sb.toString();
        } else {
            return null;
        }

    }
    public static String getVideoTrackString(VideoMetadata videoMetadata, Resources resources ){

        VideoMetadata.VideoTrack video = videoMetadata.getVideoTrack();
        StringBuilder sb = new StringBuilder();
        sb.append(video.format).append(SEP);

        String profileName = VideoMetadata.getH264ProfileName(video.profile);
        if (profileName != null) {
            if (video.level != 0) {
                sb.append('(').append(profileName).append(' ').append(video.level/(double)10).append(')').append(SEP);
            } else {
                sb.append('(').append(profileName).append(')').append(SEP);
            }
        }
        sb.append(videoMetadata.getVideoWidth()).append('x').append(videoMetadata.getVideoHeight()).append(SEP);

        if (video.bitRate != 0)
            sb.append(video.bitRate).append("kb/s").append(SEP);
        if (video.fpsRate > 0 && video.fpsScale >0) {
            NumberFormat format = new DecimalFormat("#0.###");
            double dFps = (double) video.fpsRate / (double) video.fpsScale;
            String sFps = format.format(dFps);
            sb.append(sFps).append("fps").append(SEP);
        } else if (video.fps > 0)
            sb.append(video.fps).append("fps").append(SEP);

        switch (video.s3dMode) {
            case VideoStore.Video.VideoColumns.ARCHOS_STEREO_3D_TB:       sb.append("3D-TB"); break;
            case VideoStore.Video.VideoColumns.ARCHOS_STEREO_3D_SBS:      sb.append("3D-SBS"); break;
            case VideoStore.Video.VideoColumns.ARCHOS_STEREO_3D_ANAGLYPH: sb.append("3D-Anaglyph"); break;
            case VideoStore.Video.VideoColumns.ARCHOS_STEREO_3D_UNKNOWN:  sb.append("3D"); break;
            default: break;
        }

        return sb.toString();

    }
}
