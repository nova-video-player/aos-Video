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
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.filecorelibrary.Utils;
import com.archos.mediacenter.filecoreextension.upnp2.StreamUriFinder;
import com.archos.mediacenter.utils.videodb.IndexHelper;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.subtitlesmanager.SubtitleManager;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.PlayerService;
import com.archos.mediacenter.video.player.TorrentLoaderActivity;
import com.archos.mediacenter.video.player.cast.ArchosVideoCastManager;
import com.archos.mediacenter.video.player.cast.CastService;
import com.archos.mediascraper.ScrapeDetailResult;

import java.io.IOException;

/**
 * Created by vapillon on 15/04/15.
 */
public class PlayUtils implements IndexHelper.Listener {
    private final static String TAG = "PlayUtils";
    private IndexHelper mIndexHelper;
    private VideoDbInfo mVideoDbInfo;
    private int mResume;
    private Uri mContentUri;
    private Uri mFileUri;
    private Uri mStreamingUri;
    private String mMimeType;
    private boolean mLegacyPlayer;
    private ExternalPlayerWithResultStarter mExternalPlayerWithResultStarter;
    private int mResumePosition;
    private Context mContext;
    private boolean mDisablePassthrough;


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
     * @param upnpUriRetrievingListener listener for http uri retriever
     * @param context
     * @param contentUri uri in content://
     * @param videoUri file uri when available
     * @param streamingUri http:// uri when available for upnp
     * @param mimeType
     * @param resume
     * @param externalPlayerWithResultStarter
     */
    static public void startVideo(final Context context,
                                  final Uri contentUri,
                                  final Uri videoUri,
                                  final Uri streamingUri,
                                  String mimeType,
                                  final int resume,
                                  final boolean legacyPlayer,
                                  final int resumePosition, //in case we already have resume position. Will only be used by external players
                                  final ExternalPlayerWithResultStarter externalPlayerWithResultStarter,
                                  final boolean disablePassthrough) //mxplayer will call onResult after playing video
                                    {
        Log.d(TAG, "passthrough disabled ? "+disablePassthrough);
        if(sPlayUtils==null)
            sPlayUtils = new PlayUtils();
        Log.d(TAG, "startVideo " + resume);
        Log.d(TAG, "streamingUri " + (streamingUri == null ? "null" : streamingUri));
        // try to find extension when none has been set
        if(mimeType==null&&videoUri!=null) {
            String extension = Utils.getExtension(videoUri.getLastPathSegment());
            if (extension!=null) {
                mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
            }
        }
        if(mimeType==null&&streamingUri!=null) {
            String extension = Utils.getExtension(streamingUri.getLastPathSegment());
            if (extension!=null) {
                mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
            }
        }

        final String finalMimetype = mimeType;
        if("application/x-bittorrent".equals(mimeType)){
            startTorrent(context, videoUri, mimeType, resume);
        }

        else if("upnp".equals(videoUri.getScheme())&&(streamingUri==null||"upnp".equals(streamingUri.getScheme()))){ // retrieve streaming uri for external player
            StreamUriFinder uriFinder = new StreamUriFinder(videoUri, context);
            uriFinder.setListener(new StreamUriFinder.Listener() {
                @Override
                public void onUriFound(Uri uri) {
                    sPlayUtils.startPlayer(context, contentUri, videoUri, uri, finalMimetype, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, disablePassthrough);
                }

                @Override
                public void onError() {
                    sPlayUtils.startPlayer(context, contentUri, videoUri, streamingUri, finalMimetype, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, disablePassthrough);
                }
            });
            uriFinder.start();
        }
        else
            sPlayUtils.startPlayer(context, contentUri, videoUri, streamingUri, finalMimetype, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter, disablePassthrough);
    }

    static public void startVideo(final Context context,
                                  final Uri contentUri,
                                  final Uri videoUri,
                                  final Uri streamingUri,
                                  String mimeType,
                                  final int resume,
                                  final boolean legacyPlayer,
                                  final int resumePosition, //in case we already have resume position. Will only be used by external players
                                  final ExternalPlayerWithResultStarter externalPlayerWithResultStarter) //mxplayer will call onResult after playing video
    {
        startVideo(context,contentUri, videoUri,streamingUri,mimeType,resume,legacyPlayer,resumePosition,externalPlayerWithResultStarter,false);
    }


    private void startPlayer(Context context,
                             Uri contentUri,
                             Uri fileUri,
                             Uri streamingUri,
                             final String mimeType,
                             int resume,
                             boolean legacyPlayer,
                             int resumePosition,
                             ExternalPlayerWithResultStarter externalPlayerWithResultStarter,
                             boolean disablePassthrough) {
        reset();
        mContext = context;
        mResume = resume;
        mContentUri = contentUri;
        mFileUri = fileUri;
        mStreamingUri = streamingUri;
        mMimeType = mimeType;
        mLegacyPlayer = legacyPlayer;
        mExternalPlayerWithResultStarter = externalPlayerWithResultStarter;
        mResumePosition = resumePosition;
        mDisablePassthrough = disablePassthrough;
        if (allow3rdPartyPlayer(context)&&resume!=PlayerService.RESUME_NO&&resumePosition==-1) {
            if(mIndexHelper==null)
                mIndexHelper = new IndexHelper(context, null, 0);
            mIndexHelper.requestVideoDb(contentUri,-1,null, this, false, true);
        }else {
            if (resume == PlayerService.RESUME_NO)
                resumePosition = 0;
            onResumeReady(context, contentUri, fileUri, streamingUri, mimeType, resume, legacyPlayer, resumePosition, externalPlayerWithResultStarter);
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
        onResumeReady(mContext, mContentUri, mFileUri, mStreamingUri, mMimeType,
                mResume, mLegacyPlayer, resumePos, mExternalPlayerWithResultStarter);
    }

    private void reset() {
        if(mIndexHelper!=null)
            mIndexHelper.abort();
        mVideoDbInfo = null;
        mContext = null;
        mResume = PlayerService.RESUME_NO;
        mContentUri = null;
        mFileUri = null;
        mStreamingUri = null;
        mMimeType = null;
        mLegacyPlayer = false;
        mExternalPlayerWithResultStarter = null;
        mResumePosition = -1;
        mDisablePassthrough = false;
    }


    private void onResumeReady(Context context, Uri contentUri, Uri fileUri, Uri streamingUri, final String mimeType, int resume, boolean legacyPlayer, int resumePosition, ExternalPlayerWithResultStarter externalPlayerWithResultStarter){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri dataUri = contentUri;
        if(ArchosVideoCastManager.getInstance().isConnected())
            intent.setClass(context, CastService.class);
        else if (!allow3rdPartyPlayer(context)) {
            intent.setClass(context, PlayerActivity.class);
        }
        else {
            // do not check Uri below because it can be a MediaDB Uri starting with content:
            if (!Utils.isLocal(fileUri)) {
                if (!"upnp".equals(fileUri.getScheme())) {
                    // Http proxy to allow 3rd party players to play remote files
                    try {
                        StreamOverHttp stream = new StreamOverHttp(fileUri, mimeType);
                        dataUri = stream.getUri(fileUri.getLastPathSegment());
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to start " + fileUri + e);
                    }
                } else if (streamingUri != null && !"upnp".equals(streamingUri.getScheme())) { //when upnp, try to open streamingUri
                    dataUri = streamingUri;
                }
            }
        }


        intent.setDataAndType(dataUri, mimeType);
        //this differs from the file uri for upnp
        intent.putExtra(PlayerActivity.KEY_STREAMING_URI, streamingUri);
        intent.putExtra(PlayerActivity.RESUME, resume);
        intent.putExtra(PlayerActivity.VIDEO_PLAYER_LEGACY_EXTRA, legacyPlayer);
        intent.putExtra(PlayerService.DISABLE_PASSTHROUGH, mDisablePassthrough);

        ExternalPlayerResultListener.ExternalPositionExtra.setAllPositionExtras(intent,resumePosition );
        try {
            if(ArchosVideoCastManager.getInstance().isConnected())
                context.startService(intent);
            else if(externalPlayerWithResultStarter==null||!allow3rdPartyPlayer(context))
                context.startActivity(intent);
            else {
                ExternalPlayerResultListener.getInstance().init(context,contentUri,dataUri, mVideoDbInfo);
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
        if(!Utils.isLocal(uri)){
            try {
                StreamOverHttp streamOverHttp = new StreamOverHttp(file,mimeType);
                uri = streamOverHttp.getUri(file.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "data=" + uri);
        Log.d(TAG, "type=" + mimeType);
        intent.setDataAndType(uri, mimeType);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // If the file is a local file, we can try to open it with another application, with
            // data type set to *, because Android does not handle well Mime-type in some cases
            if (!file.isRemote()) {
                Intent intent2 = new Intent(Intent.ACTION_VIEW);
                intent2.setDataAndType(uri, "*/*");
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
