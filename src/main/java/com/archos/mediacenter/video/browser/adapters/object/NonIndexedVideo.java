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

import android.net.Uri;

import com.archos.filecorelibrary.contentstorage.DocumentUriBuilder;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.io.Serializable;

/**
 * Created by vapillon on 10/04/15.
 */
public class NonIndexedVideo extends Video implements Serializable {

    /**
     * Build a non indexed Video object from an uri and a name
     * name can be null, in that case the filename is used (taken from the Uri)
     * @param streamingUri
     * @param indexableUri is the uri that can be indexed by file scanner ("upnp://" for upnp instead of "http://")
     * @param name basically used for UPnP only, because in that case the Uri may be cryptic and not related to the name
     * @param thumbnailUri uri of a thumbnail or poster file
     */
    public NonIndexedVideo(Uri streamingUri,Uri indexableUri, String name, Uri thumbnailUri) {
        super(-1L, VideoUtils.getMediaLibCompatibleFilepathFromUri(indexableUri),
                buildName(name, indexableUri),
                thumbnailUri,
                PlayerActivity.LAST_POSITION_UNKNOWN, -1, -1, -1,  false, false, false, false, 0,0);

        setStreamingUri(streamingUri);
    }

    private static String buildName(String name, Uri uri){
        if(name!=null)
            return name;
        if(UriUtils.isContentUri(uri)){
            //try to find name in content provider
            name = DocumentUriBuilder.getNameFromContentProvider(uri);
            if(name!=null)
                return name;
        }
        return buildFileNameWithExtension(uri.toString());
    }
    /**
     * Build a non indexed Video object from a path and a name
     * name can be null, in that case the filename is used (taken from the path)
     * @param filepath
     */
    public NonIndexedVideo(String filepath) {
        super(-1L, filepath,
                buildName(null, Uri.parse(filepath)),
                null,
                PlayerActivity.LAST_POSITION_UNKNOWN, -1,
                -1, -1, false, false,false, false,0,0);
    }

    /**
     * There is no DB Uri for non indexed Videos, so we return the File Uri instead
     * @return
     */
    @Override
    public Uri getUri() {
        return getFileUri();
    }

    @Override
    public Uri getDbUri() {
        return null;
    }
}
