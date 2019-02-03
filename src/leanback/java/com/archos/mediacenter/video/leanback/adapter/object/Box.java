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

package com.archos.mediacenter.video.leanback.adapter.object;

import android.graphics.Bitmap;

/**
 * Created by vapillon on 10/04/15.
 */
public class Box {

    public enum ID {
        ALL_MOVIES,
        MOVIES_BY_GENRE,
        MOVIES_BY_YEAR,
        VIDEOS_BY_LISTS,
        FOLDERS,
        USB,
        SDCARD,
        OTHER,
        NETWORK,
        FTP,
        INDEXED_FOLDERS_REFRESH,
        NON_SCRAPED_VIDEOS,
        ALL_TVSHOWS,
        TVSHOWS_BY_ALPHA,
        TVSHOWS_BY_GENRE
    }

    final private ID mBoxId;
    final private String mName;
    final private int mIconResId;
    final private String mPath;
    final private Bitmap mBitmap;

    public Box(ID boxId, String name, int iconResId, String path) {
        mBoxId = boxId;
        mName = name;
        mIconResId = iconResId;
        mPath = path;
        mBitmap = null;
    }

    public Box(ID boxId, String name, int iconResId) {
        this(boxId, name, iconResId, null);
    }

    public Box(ID boxId, String name, Bitmap bitmap) {
        mBoxId = boxId;
        mName = name;
        mIconResId = -1;
        mPath = null;
        mBitmap = bitmap;
    }

    public ID getBoxId() {
        return mBoxId;
    }

    public String getName() {
        return mName;
    }

    /**
     * @return -1 if there is no resource id. In that case you must call getBitmap() instead
     */
    public int getIconResId() {
        return mIconResId;
    }

    public String getPath() {
        return mPath;
    }

    /**
     * @return null if there is no bitmap. In that case you must call getIconResId() instead
     */
    public Bitmap getBitmap() {
        return mBitmap;
    }
}
