package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;

import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by alexandre on 16/05/17.
 */

public class ListsLoader extends CursorLoader {
    public ListsLoader(Context context) {
        super(context, VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.COLUMNS, null, null, null);
    }
}
