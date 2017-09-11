package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.content.CursorLoader;
import android.util.Log;

import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserMoviesBy;
import com.archos.mediaprovider.video.ListTables;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

/**
 * Created by alexandre on 15/05/17.
 */

public class VideosByListLoader extends CursorLoader implements CompatAndSDKCursorLoaderFactory{
    public static final String COLUMN_COUNT = "count";
        public static final String COLUMN_SUBSET_ID = "_id";
    public static final String COLUMN_LIST_OF_POSTER_FILES = "po_file_list";
    public static final String COLUMN_SUBSET_NAME = "name";
    public static final String COLUMN_LIST_OF_VIDEO_IDS = "list";
    public static final String COLUMN_MAP_MOVIE_ID = "map_movie";
    public static final String COLUMN_MAP_EPISODE_ID = "map_episode";
    public static final String COLUMN_NUMBER_OF_MOVIES = "number";
    protected static String COUNT = "COUNT(*) as "+COLUMN_COUNT;

    protected String mSortOrder;
    private boolean mForceHideVideos;

    private static final String DEFAULT_SORT = COLUMN_SUBSET_NAME+" COLLATE NOCASE DESC";

    public VideosByListLoader(Context context) {
        super(context);
        mSortOrder = DEFAULT_SORT;
        setUri(VideoStore.RAW_QUERY);

        setSelection(getSelection(context));
    }

    public VideosByListLoader(Context context, String sortOrder) {
        super(context);
        mSortOrder = sortOrder;
        setUri(VideoStore.RAW_QUERY);
        setSelection(getSelection(context));
    }



    public String getSelection(Context context) {
        String cmd =  "Select \n" +
                "   l._id as _id,\n" +
                "   count(v._id) as "+ BrowserMoviesBy.COLUMN_NUMBER_OF_MOVIES+", \n"+
                "   group_concat( v.po_large_file) AS "+COLUMN_LIST_OF_POSTER_FILES+", \n"+
                "   group_concat(v._id) AS "+ COLUMN_LIST_OF_VIDEO_IDS +", -- movie id list\n" +
                "   group_concat(v."+VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID+" || ':' || v._id) AS "+ COLUMN_MAP_MOVIE_ID +",\n" +
                "   group_concat(v."+VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID+" || ':' || v._id) AS "+ COLUMN_MAP_EPISODE_ID +",\n" +
                "   l."+VideoStore.List.Columns.TITLE+" as name\n" +
                "   from \n" +
                "   video v,\n" +
                "   "+ ListTables.LIST_TABLE+" l,\n" +
                "   "+ ListTables.VIDEO_LIST_TABLE+" vl \n" +
                "WHERE\n" +
                "        (" +
                "(" +
                    "v."+ VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID+" = vl."+VideoStore.VideoList.Columns.M_ONLINE_ID+" " +
                    " AND v."+ VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID+" NOT NULL" +
                    " OR v."+ VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID+" = vl."+VideoStore.VideoList.Columns.E_ONLINE_ID+" " +
                    " AND v."+ VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID+" NOT NULL) " +
                " AND l."+VideoStore.List.Columns.ID+" = vl."+VideoStore.VideoList.Columns.LIST_ID+
                " AND l."+VideoStore.List.Columns.SYNC_STATUS+" != "+VideoStore.List.SyncStatus.STATUS_DELETED+
                " AND vl."+VideoStore.List.Columns.SYNC_STATUS+" != "+VideoStore.List.SyncStatus.STATUS_DELETED+
                " AND "+ LoaderUtils.HIDE_USER_HIDDEN_FILTER+")"+
                " GROUP BY name\n";
        return cmd;
    }

    public android.support.v4.content.Loader getV4CursorLoader(boolean detailed, boolean hideWatchedVideos){
        mForceHideVideos = hideWatchedVideos;
        return  new android.support.v4.content.CursorLoader(getContext(),
                getUri(), getProjection(), getSelection(), getSelectionArgs(),
                getSortOrder());
    }}

