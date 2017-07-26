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

public enum AdapterDefaultValuesDetails implements AdapterDefaultValues {

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
        return R.layout.browser_item_details;
    }

    public int getLayoutId(int itemType) {
        int layoutId;

        switch (itemType) {
            case ItemViewType.ITEM_VIEW_TYPE_HEADER_SHOW:
                layoutId = R.layout.browser_item_header_show;
                break;
            case ItemViewType.ITEM_VIEW_TYPE_SHOW:
                layoutId = R.layout.browser_item_details_show;
                break;
            case ItemViewType.ITEM_VIEW_TYPE_FILE:
            default:
                layoutId = R.layout.browser_item_details;
                break;
        }

        return layoutId;
    }

    public int[] getThumnailHeightWidth(Context context) {
        Resources res = context.getResources();
        return new int[] {
                res.getDimensionPixelSize(R.dimen.video_details_poster_height),
                res.getDimensionPixelSize(R.dimen.video_details_poster_width)
        };
    }

    public int getExpandedZone() {
        return R.id.expanded;
    }
}
