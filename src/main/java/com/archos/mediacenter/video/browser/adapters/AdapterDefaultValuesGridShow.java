package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;
import android.content.res.Resources;

import com.archos.mediacenter.video.R;

public enum AdapterDefaultValuesGridShow implements AdapterDefaultValues {
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
        return R.layout.browser_item_grid_show;
    }

    public int getLayoutId(int itemType) {
        // This method is only needed for compatibility,
        // there is only one possible layout anyway
        return R.layout.browser_item_grid_show;
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

