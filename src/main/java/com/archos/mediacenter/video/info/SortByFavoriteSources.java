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

import android.net.Uri;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.video.VideoStore;

import java.util.Comparator;
import java.util.List;

/**
 * Created by alexandre on 08/12/15.
 */
public class SortByFavoriteSources implements Comparator<Video> {
    private final List<Video> mOldVideoList;

    public SortByFavoriteSources(List<Video> oldVideoList) {
        mOldVideoList = oldVideoList;
    }

    @Override
    public int compare(Video video1, Video video2) {
        if (video1 == video2) {
            return 0;
        }
        if (video1 == null) {
            return 1;
        }
        if (video2 == null) {
            return -1;
        }

        if (mOldVideoList != null && !mOldVideoList.isEmpty()) {
            int pos1 = -1;
            int pos2 = -1;
            int index = 0;
            for (Video oldVideoInList : mOldVideoList) {
                if (oldVideoInList != null && oldVideoInList.getUri() != null) {
                    if (pos1 == -1 && oldVideoInList.getUri().equals(video1.getUri())) {
                        pos1 = index;
                    }
                    if (pos2 == -1 && oldVideoInList.getUri().equals(video2.getUri())) {
                        pos2 = index;
                    }
                    if (pos1 != -1 && pos2 != -1) {
                        break;
                    }
                }
                index++;
            }
            if (pos1 != -1 && pos2 != -1) {
                if (pos1 != pos2) {
                    return Integer.compare(pos1, pos2);
                }
            } else if (pos1 != -1) {
                return -1;
            } else if (pos2 != -1) {
                return 1;
            }
        }

        if (video1.is3D() != video2.is3D()) {
            return video1.is3D() ? -1 : 1;
        }

        int def1 = getDefinitionRank(video1.getNormalizedDefinition());
        int def2 = getDefinitionRank(video2.getNormalizedDefinition());
        if (def1 != def2) {
            return Integer.compare(def2, def1);
        }

        int loc1 = getLocationRank(video1.getFileUri());
        int loc2 = getLocationRank(video2.getFileUri());
        if (loc1 != loc2) {
            return Integer.compare(loc2, loc1);
        }

        if (video1.getFileUri() != null && video2.getFileUri() != null) {
            return video1.getFileUri().toString().compareTo(video2.getFileUri().toString());
        }
        return 0;
    }

    private static int getDefinitionRank(int definition) {
        switch (definition) {
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K:
                return 4;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P:
                return 3;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P:
                return 2;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_SD:
                return 1;
            case VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_UNKNOWN:
            default:
                return 0;
        }
    }

    private static int getLocationRank(Uri uri) {
        if (uri == null) {
            return 0;
        }
        if (FileUtils.isLocal(uri)) {
            return 2;
        }
        if (!FileUtils.isSlowRemote(uri)) {
            return 1;
        }
        return 0;
    }
}
