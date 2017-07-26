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

package com.archos.mediacenter.video.browser;

import android.net.Uri;
import android.util.Log;

import com.archos.mediacenter.utils.UpnpItemData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class ItemDataList extends ArrayList<ItemData>{
    private static final long serialVersionUID = 1L;

    public ItemDataList() {
        super();
    }

    public ItemDataList(int fileCount) {
        super(fileCount);
    }

    public void reset(int count) {
        clear();
        ensureCapacity(count);
    }

    public UpnpItemData getUpnpItemData(int position) {
        final ItemData itemData = get(position);
        return itemData != null ? itemData.getUpnpItemData() : null;
    }

    public void fillVideoProperties(HashMap<String, VideoProperties> videoDB) {
        if (size() == 0 || videoDB == null)
            return;

        for (ItemData itemData : this) {
            final String path = itemData.getIndexablePath();
            if (path != null) {
                VideoProperties vp = videoDB.get(path);
                itemData.setVideoProperties(vp);
            }
        }
        Collections.sort(this);
    }

    public void fillInfos(HashMap<String, String> filesInfo) {
        if (size() == 0)
            return;

        for (ItemData itemData : this) {
            final String path = itemData.getPath();
            if (path != null) {
                final String info = filesInfo.get(path);
                if (info != null)
                    itemData.setInfo(info);
            }
        }
    }
    //fill with different resume points
    public void fillVideoResumePoints(HashMap<String, Integer> networkResumePoints) {

        if (size() == 0 || networkResumePoints == null || networkResumePoints.size() == 0)
            return;

        for (ItemData itemData : this) {
            final String path = itemData.getIndexablePath();
            if (path != null) {
                Integer resume = networkResumePoints.get(path);
                if (resume!=null&&resume > 0) {

                    itemData.setRemoteResumePosition(resume);

                }
            }
        }
    }
}
