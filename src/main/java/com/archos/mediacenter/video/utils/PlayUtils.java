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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import android.widget.Toast;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.upnp2.StreamUriFinder;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.player.TorrentLoaderActivity;
import com.archos.mediascraper.ScrapeDetailResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Created by vapillon on 15/04/15.
 */
public class PlayUtils implements IndexHelper.Listener {
    private static final Logger log = LoggerFactory.getLogger(PlayUtils.class);

    private IndexHelper mIndexHelper;
    private VideoDbInfo mVideoDbInfo;
    private int mResume;
    private String mMimeType;
    private boolean mLegacyPlayer;
    private ExternalPlayerWithResultStarter mExternalPlayerWithResultStarter;
    private int mResumePosition;
    private Context mContext;
    private long mPlaylistId;
    private Video mVideo;

    @Override
    public void onScraped(ScrapeDetailResult result) {

    }

    public interface SubtitleDownloadListener{
        public void onDownloadStart(SubtitleManager engine);
        public void onDownloadEnd();
    }
    private static PlayUtils sPlayUtils = null;

    static public void startTorrent(Context context, MetaFile2 torrentFile, int resume) {
        startTorrent(context, torrentFile.getUri(), torrentFile.getMimeType(),resume);
    }
    static public void startTorrent(Context context, Uri torrentFileUri, String mimeType, int resume) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(context, TorrentLoaderActivity.class);
        intent.setDataAndType(torrentFileUri, mimeType);
        intent.putExtra(PlayerActivity.RESUME, resume);
        context.startActivity(intent);
    }

    /**
     * Common method to start a video
     * @param context
     * @param resume
     * @param externalPlayerWithResultStarter
     * @param playlistId
     */
    static public void startVideo(final Context context,
                                  final Video video,
                                  final int resume,
                                  final boolean legacyPlayer,
                                  final int resumePosition, //in case we already have resume position. Will only be used by external players
                                  final ExternalPlayerWithResultStarter externalPlayerWithResultStarter, //mxplayer will call onResult after playing video
                                  final long playlistId)
                                    {

        if(sPlayUtils==null)
            sPlayUtils = new PlayUtils();
        if (video == null) {
            log.warn("startVideo: video is null!");
            Toast.makeText(context, "Error video is null", Toast.LENGTH_SHORT).show();
            return;
        } else {
            log.debug("startVideo from resume=" + resume + ", streamingUri " + (video.getStreamingUri() == null ? "null" : video.getStreamingUri()));
        }
        // try to find extension when none has been set
        String mimeType = video.getMimeType();
        if(mimeType==null&&video.getFileUri()!=null) {
            String extension = FileUtils.getExtension(video.getFileUri().getLastPathSegment());
            if (extension!=null) {
                mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
            }
        }
        if(mimeType==null&&video.getStreamingUri()!=null) {
            String extension = FileUtils.getExtension(video.getStreamingUri().getLastPathSegment());
            if (extension!=null) {
                mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
            }
        }

        final String finalMimetype = mimeType;
        if("application/x-bittorrent".equals(mimeType)){
            startTorrent(context, video.getFileUri(), mimeType, resume);
        }
        else if(video.getFileUri() != null && "upnp".equals(video.getFileUri().getScheme()) &&
                        (video.getStreamingUri()==null || "upnp".equals(video.getStreamingUri().getScheme()))){ // retrieve streaming uri for external player
            StreamUriFinder uriFinder = new StreamUriFinder(video.getFileUri(), context);
            uriFinder.setListener(new StreamUriFinder.Listener() {
                @Override
                public void onUriFound(Uri uri) {
                    video.setStreamingUri(uri);
                    sPlayUtils.startPlayer(context, video, finalMimetype, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, playlistId);
                }

                @Override
                public void onError() {
                    sPlayUtils.startPlayer(context, video, finalMimetype, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, playlistId);
                }
            });
            uriFinder.start();
        }
        else
            sPlayUtils.startPlayer(context, video, finalMimetype, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, playlistId);
    }

    private void startPlayer(Context context,
                             Video video,
                             final String mimeType,
                             int resume,
                             boolean legacyPlayer,
                             int resumePosition,
                             ExternalPlayerWithResultStarter externalPlayerWithResultStarter,
                             long playlistId) {
        reset();
        mContext = context;
        mResume = resume;
        mVideo = video;
        mMimeType = mimeType;
        mLegacyPlayer = legacyPlayer;
        mExternalPlayerWithResultStarter = externalPlayerWithResultStarter;
        mResumePosition = resumePosition;
        mPlaylistId = playlistId;
        if (allow3rdPartyPlayer(context)&&resume!=PlayerService.RESUME_NO&&resumePosition==-1) {
            if(mIndexHelper==null)
                mIndexHelper = new IndexHelper(context, null, 0);
            mIndexHelper.requestVideoDb(video.getUri(), -1,null, this, false, true);
        }else {
            if (resume == PlayerService.RESUME_NO)
                resumePosition = 0;
            onResumeReady(context, mVideo, mimeType, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, playlistId);
        }
    }

    @Override
    public void onVideoDb(VideoDbInfo info, VideoDbInfo remoteInfo) {
        int resumePos = -1;
        if(info!=null||remoteInfo!=null) {

            if (mResume != PlayerService.RESUME_NO) {
                if(remoteInfo!=null&&info==null)
                    resumePos = remoteInfo.resume;
                else if(info!=null&&remoteInfo==null)
                    resumePos = info.resume;
                else if (mResume == PlayerService.RESUME_FROM_LAST_POS) {
                    resumePos = info.resume > remoteInfo.resume ? info.resume : remoteInfo.resume;
                } else if (mResume == PlayerService.RESUME_FROM_REMOTE_POS) {
                    resumePos = remoteInfo.resume;
                } else if (mResume == PlayerService.RESUME_FROM_LOCAL_POS) {
                    resumePos = info.resume;
                }
            }
        }
        onResumeReady(mContext, mVideo, mMimeType,
                mResume, mLegacyPlayer, resumePos, mExternalPlayerWithResultStarter, mPlaylistId);
    }

    private void reset() {
        if(mIndexHelper!=null)
            mIndexHelper.abort();
        mVideoDbInfo = null;
        mContext = null;
        mResume = PlayerService.RESUME_NO;
        mVideo = null;
        mMimeType = null;
        mLegacyPlayer = false;
        mExternalPlayerWithResultStarter = null;
        mResumePosition = -1;
        mPlaylistId = -1;
    }

    private void onResumeReady(Context context, Video video, final String mimeType, int resume, boolean legacyPlayer, int resumePosition, ExternalPlayerWithResultStarter externalPlayerWithResultStarter, long playlistId){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri dataUri = video.getUri();
        Uri fileUri = null;
        if (!allow3rdPartyPlayer(context)) {
            intent.putExtra(PlayerService.VIDEO, video);
            intent.setClass(context, PlayerActivity.class);
            intent.setDataAndType(dataUri, mimeType);
        }
        else {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // do not check Uri below because it can be a MediaDB Uri starting with content:
            if (!FileUtils.isLocal(video.getFileUri())) {
                if (!"upnp".equals(video.getFileUri().getScheme())) {
                    // Http proxy to allow 3rd party players to play remote files
                    try {
                        StreamOverHttp stream = new StreamOverHttp(video.getFileUri(), mimeType);
                        dataUri = stream.getUri(video.getFileUri().getLastPathSegment());
                    } catch (IOException e) {
                        log.error("onResumeReady: failed to start " + video.getFileUri() + e);
                    }
                } else if (video.getStreamingUri() != null && !"upnp".equals(video.getStreamingUri().getScheme())) { //when upnp, try to open streamingUri
                    dataUri = video.getStreamingUri();
                }
                intent.setDataAndType(dataUri, mimeType);

            }
            else {
                // in case of a local file, need to rely on FileProvider since API24+ to avoid android.os.FileUriExposedException
                File localFile = new File(video.getFileUri().getPath());
                fileUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", localFile);
                intent.setDataAndType(fileUri, mimeType);
                // TODO: add support for 3rd party player subs here /!\ will only work for local files
                // for vlc https://wiki.videolan.org/Android_Player_Intents/ subtitles_location path
                // for mxplayer http://mx.j2inter.com/api subs android.net.Uri[], subs.name String[], subs.filename String[]
                // mxplayer is clever enough for local files to find local subs, thus no need to implement it
                final VideoMetadata videoMetadata = video.getMetadata();
                if (videoMetadata!=null) {
                    Boolean subFound = false;
                    VideoMetadata.SubtitleTrack sub;
                    int n = 0;
                    // find first external subtitle file and pass it to vlc
                    while (n < videoMetadata.getSubtitleTrackNb() && ! subFound) {
                        sub = videoMetadata.getSubtitleTrack(n);
                        if (sub.isExternal) {
                            subFound = true;
                            log.debug("onResumeReady: adding external subtitle name=" + sub.name + ", path=" + sub.path);
                            // vlc
                            intent.putExtra("subtitles_location", sub.path);
                        }
                        n++;
                    }
                }
            }
        }
        //this differs from the file uri for upnp
        intent.putExtra(PlayerActivity.KEY_STREAMING_URI, video.getStreamingUri());
        intent.putExtra(PlayerActivity.RESUME, resume);
        intent.putExtra(PlayerActivity.VIDEO_PLAYER_LEGACY_EXTRA, legacyPlayer);
        intent.putExtra(PlayerService.PLAYLIST_ID, playlistId);

        ExternalPlayerResultListener.ExternalPositionExtra.setAllPositionExtras(intent,resumePosition );
        try {
            if(externalPlayerWithResultStarter==null||!allow3rdPartyPlayer(context))
                context.startActivity(intent);
            else {
                ExternalPlayerResultListener.getInstance().init(context, video.getUri(), dataUri, mVideoDbInfo);
                externalPlayerWithResultStarter.startActivityWithResultListener(intent);
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_to_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Try to launch another app to open a file that we do not directly support
     */
    static public void openAnyFile(MetaFile2 file, Context context) {
        // Play/view the file
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String mimeType = file.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "*/" + file.getExtension();
        }
        Uri uri = file.getUri();
        Uri fileUri = null;
        if(!FileUtils.isLocal(uri)){
            try {
                StreamOverHttp streamOverHttp = new StreamOverHttp(file,mimeType);
                uri = streamOverHttp.getUri(file.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
            intent.setDataAndType(uri, mimeType);
        }
        else {
            // in case of a local file, need to rely on FileProvider since API24+ to avoid android.os.FileUriExposedException
            File localFile = new File(uri.getPath());
            fileUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", localFile);
            intent.setDataAndType(fileUri, mimeType);
        }
        log.debug("openAnyFile: data=" + uri);
        log.debug("openAnyFile: type=" + mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // If the file is a local file, we can try to open it with another application, with
            // data type set to *, because Android does not handle well Mime-type in some cases
            if (!file.isRemote()) {
                Intent intent2 = new Intent(Intent.ACTION_VIEW);
                intent2.setDataAndType(fileUri, "*/*");
                intent2.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    context.startActivity(intent2);
                } catch (ActivityNotFoundException e2) {
                    Toast.makeText(context, R.string.no_application_to_open_file, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    static private boolean allow3rdPartyPlayer(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(VideoPreferencesActivity.ALLOW_3RD_PARTY_PLAYER, VideoPreferencesActivity.ALLOW_3RD_PARTY_PLAYER_DEFAULT);
    }
}
