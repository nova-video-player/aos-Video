package com.archos.mediacenter.video.browser.loader;

import android.content.Context;

import com.archos.mediaprovider.video.VideoStore;

public class SearchEpisodeLoader extends VideoLoader {

    // search based on scraper title and file title
    private static final String SELECTION = VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID + " IS NOT NULL AND ("
                                          + VideoStore.Video.VideoColumns.SCRAPER_TITLE + " LIKE ? OR "
                                          + VideoStore.Video.VideoColumns.SCRAPER_E_NAME + " LIKE ? OR "
                                          + VideoStore.MediaColumns.TITLE + " LIKE ?)";

    private static final String DEFAULT_QUERY = "";
    private static final String DEFAULT_SORT = "name COLLATE NOCASE,"
            + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + " ASC,"
            + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + " ASC";

    private String mSortOrder = DEFAULT_SORT;
    private String mQuery = DEFAULT_QUERY;

    public SearchEpisodeLoader(Context context) {
        super(context);
        init();
    }

    public void setQuery(String query) {
        mQuery = query;
    }

    @Override
    public String getSortOrder() {
        return mSortOrder;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        if (sb.length()>0) { sb.append(" AND "); }
        sb.append(SELECTION);

        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return new String[] { "%" + mQuery + "%", "%" + mQuery + "%", "%" + mQuery + "%" };
    }

}