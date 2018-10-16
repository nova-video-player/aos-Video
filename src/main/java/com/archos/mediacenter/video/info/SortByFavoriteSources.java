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

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.video.VideoStore;

import java.util.Comparator;
import java.util.List;

/**
 * Created by alexandre on 08/12/15.
 */
public class SortByFavoriteSources  implements Comparator<Video> {
    private final List<Video> mOldVideoList;

    public SortByFavoriteSources(List<Video> oldVideoList) {
        mOldVideoList = oldVideoList;
    }

    @Override
        public int compare(Video video1, Video video2) {
        int pos1=0;
        int pos2=0;
        boolean found1 = false;
        boolean found2 = false;
        for(Video oldVideoInList : mOldVideoList){
            if(oldVideoInList.getUri().equals(video1.getUri())){
                found1 = true;
                if(found2)
                    break;
            }
            if(oldVideoInList.getUri().equals(video2.getUri())){
                found2 = true;
                if(found1)
                    break;
            }
            if(!found1)
                pos1++;
            if(!found2)
                 pos2++;
        }
        if(found1&&found2){
            if(pos1<pos2)
                return -1;
            if(pos1>pos2)
                return 1;
        }
            if(video1.is3D()&&!video2.is3D())
                return -1;
            if(!video1.is3D()&&video2.is3D())
                return 1 ;
            if(video1.getNormalizedDefinition()!=video2.getNormalizedDefinition()){ // return better resolution

                if(video1.getNormalizedDefinition() == VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K)
                    return -1;
                if(video2.getNormalizedDefinition() == VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K)
                    return 1;
                if(video1.getNormalizedDefinition() == VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P)
                    return -1;
                if(video2.getNormalizedDefinition() == VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P)
                    return 1;
                if(video1.getNormalizedDefinition() == VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P)
                    return -1;
                if(video2.getNormalizedDefinition() == VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P)
                    return 1;

            }

            if(FileUtils.isLocal(video1.getFileUri()))
                return -1;
            if (FileUtils.isLocal(video2.getFileUri()))
                return 1;
            if(!FileUtils.isSlowRemote(video1.getFileUri()))
                return -1;
            if(!FileUtils.isSlowRemote(video2.getFileUri()))
                return 1;


            return -1;

    }
}
