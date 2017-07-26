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


package com.archos.mediacenter.video.browser.adapters;

import com.archos.mediacenter.video.R;

import android.content.Context;
import android.content.res.Resources;

public enum AdapterDefaultValuesGrid implements AdapterDefaultValues {

    INSTANCE;

    public int getDefaultDirectoryThumbnail() {
        return R.drawable.filetype_video_folder_vertical;
    }

    public int getDefaultShortcutThumbnail() {
        return R.drawable.filetype_video_folder_indexed_vertical;
    }

    public int getDefaultVideoThumbnail() {
        return R.drawable.filetype_video_large;
    }

    public int getMediaSyncIcon(int state) {
        return R.drawable.label_video_disabled; // not required here
    }

    public int getLayoutId() {
        return R.layout.browser_item_grid;
    }

    public int getLayoutId(int itemType) {
        // This method is only needed for compatibility,
        // there is only one possible layout anyway
        return R.layout.browser_item_grid;
    }

    public int[] getThumnailHeightWidth(Context context) {
        Resources res = context.getResources();
        return new int[] {
                res.getDimensionPixelSize(R.dimen.video_grid_poster_height),
                res.getDimensionPixelSize(R.dimen.video_grid_poster_width)
        };
    }

    public int getExpandedZone() {
        return R.id.expanded;
    }
}
