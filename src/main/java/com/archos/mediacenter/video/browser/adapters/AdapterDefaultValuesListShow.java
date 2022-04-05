package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;
import android.content.res.Resources;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.ListingAdapter;


public enum AdapterDefaultValuesListShow implements AdapterDefaultValues {

    INSTANCE;

    public int getDefaultDirectoryThumbnail() {
        return R.drawable.filetype_video_folder;
    }

    public int getDefaultShortcutThumbnail() {
        return R.drawable.filetype_video_folder_indexed;
    }

    public int getDefaultVideoThumbnail() {
        return R.drawable.filetype_video;
    }

    public int getMediaSyncIcon(int state) {
        switch (state) {
            case 0:
                return R.drawable.label_video_disabled;
            case 1:
                return R.drawable.label_video;
            case 2:
                return R.drawable.label_sync_animated;
            default:
                return R.drawable.label_video_disabled;
        }
    }

    public int getLayoutId() {
        return R.layout.browser_item_list_series;
    }

    public int getLayoutId(int itemType) {
        int layoutId;

        switch (itemType) {
            case ItemViewType.ITEM_VIEW_TYPE_SERVER:
                layoutId = R.layout.browser_item_list_server;
                break;

            case ItemViewType.ITEM_VIEW_TYPE_SHORTCUT:
                layoutId = R.layout.browser_item_list_shortcut;
                break;

            case ItemViewType.ITEM_VIEW_TYPE_TITLE:
                layoutId = R.layout.browser_item_list_title;
                break;

            case ItemViewType.ITEM_VIEW_TYPE_TEXT:
                layoutId = R.layout.browser_item_list_text;
                break;

            case ItemViewType.ITEM_VIEW_TYPE_LONG_TEXT:
                layoutId = R.layout.browser_item_list_long_text;
                break;

            case ItemViewType.ITEM_VIEW_TYPE_HEADER_SHOW:
                layoutId = R.layout.browser_item_header_show;
                break;
            case ItemViewType.ITEM_VIEW_TYPE_SHOW:
                layoutId = R.layout.browser_item_list_show;
                break;
            case ListingAdapter.ITEM_VIEW_TYPE_FILE:
            default:
                layoutId = R.layout.browser_item_list_series;
                break;
        }

        return layoutId;
    }

    public int[] getThumnailHeightWidth(Context context) {
        Resources res = context.getResources();
        return new int[] {
                res.getDimensionPixelSize(R.dimen.video_list_thumbnail_height),
                res.getDimensionPixelSize(R.dimen.video_list_thumbnail_width)
        };
    }

    public int getExpandedZone() {
        return R.id.expanded;
    }
}
