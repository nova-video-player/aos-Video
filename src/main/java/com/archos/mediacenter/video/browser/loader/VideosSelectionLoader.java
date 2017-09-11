package com.archos.mediacenter.video.browser.loader;

import android.content.Context;

import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by vapillon on 10/04/15.
 */
public class VideosSelectionLoader extends MoviesLoader {

    protected final String mListOfIds;
    protected String mSortOrder;

    public VideosSelectionLoader(Context context, String listOfMoviesIds) {
        this(context, listOfMoviesIds, DEFAULT_SORT);
    }

    public VideosSelectionLoader(Context context, String listOfIds, String SortOrder) {
        super(context, true);
        mListOfIds = listOfIds;
        mSortOrder = SortOrder;
        init();
    }

    @Override
    public String getSortOrder() {
        return null;
    }

    @Override
    public String getSelection() {
        return VideoStore.Video.VideoColumns._ID + " IN (" + mListOfIds + ")";
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }

}
