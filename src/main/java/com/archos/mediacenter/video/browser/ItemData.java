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

import com.archos.filecorelibrary.SmbItemData;
import com.archos.filecorelibrary.samba.SambaConfiguration;
import com.archos.mediacenter.utils.UpnpItemData;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;

public class ItemData implements Comparable<ItemData> {

    // Add here all the available item types (each item type can have its own layout)
    public static final int ITEM_VIEW_TYPE_FILE = 0;        // File or folder
    private VideoProperties mVp = null;
    private String mInfo = null;


    public ItemData(){}

    public String getName() {
        if (mVp != null)
            return mVp.getName();
        else
            return getFileName();
    }

    public String getFileName() {
        return null;
    }

    public String getPath() {
        return "";
    }

    public void setInfo(String info) {
        mInfo = info;
    }

    public String getInfo() {
        return mInfo;
    }

    public boolean isDirectory() {
        return false;
    }

    @Override
    public int compareTo(ItemData another) {
        if (isDirectory() != another.isDirectory())
            return another.isDirectory() ? 1 : -1;
        return getFileName().compareToIgnoreCase(another.getFileName());
    }
}