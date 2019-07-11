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

package com.archos.mediacenter.video.leanback;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;

/**
 * Created by alexandre on 21/12/15.
 */
public class UnavailablePosterBroadcastReceiver extends BroadcastReceiver{
    private static UnavailablePosterBroadcastReceiver sReceiver;
    private static String ACTION_CHECK_POSTER = "ACTION_CHECK_POSTER";
    private static String TAG = "UnavailablePosterBroadcastReceiver";
    public static final String COLUMN_COVER_PATH = "cover";


    //delete poster from DB when can't be loaded

    public static void registerReceiver(Context context){
        if(sReceiver == null)
            sReceiver = new UnavailablePosterBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CHECK_POSTER);
        context.registerReceiver(sReceiver,filter);
    }
    public static void unregisterReceiver(Context context){
        if(sReceiver != null) {
            try {
                context.unregisterReceiver(sReceiver);
            }
            catch (java.lang.IllegalArgumentException e){}
        }
    }
    public static void sendBroadcast(Context context, long videoId){
        Intent intent = new Intent(ACTION_CHECK_POSTER);
        intent.putExtra("VIDEO_ID", videoId);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        if(ACTION_CHECK_POSTER.equals(intent.getAction())&&intent.getLongExtra("VIDEO_ID",-1)!=-1){
            Log.d(TAG, "onReceive2");
            StringBuilder sb = new StringBuilder();
            if (LoaderUtils.mustHideUserHiddenObjects()) {
                sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);
                sb.append(" AND ");
            }
            sb.append(VideoStore.Video.VideoColumns._ID + " = ? ");

            String[] arg = new String[]{Long.toString(intent.getLongExtra("VIDEO_ID",-1))};
            String where = sb.toString();
            Cursor c = context.getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, new String[]{COLUMN_COVER_PATH,VideoStore.Video.VideoColumns.TITLE, VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID,VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE}, where, arg, null);
            if(c!=null&&c.getCount()>0){
                c.moveToFirst();
                int coverColumn = c.getColumnIndex(COLUMN_COVER_PATH);
                int titleColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.TITLE);
                int idMovieColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID);
                int idShowColumn= c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID);
                final int scraperType = c.getInt(c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                if(c.getString(coverColumn)!=null){
                    String path = c.getString(coverColumn);
                    if(!FileEditorFactory.getFileEditorForUrl(Uri.parse(path), null).exists()){
                        //remove
                        Log.d(TAG, path + " does not exists : removing for "+c.getString(titleColumn));
                        ContentValues cv = new ContentValues();
                        Uri uri;
                        if (scraperType == BaseTags.TV_SHOW) {
                            uri  = ContentUris.withAppendedId(ScraperStore.Episode.URI.ID, c.getLong(idShowColumn));
                            cv.put(ScraperStore.Episode.POSTER_ID, -1);
                           cv.putNull(ScraperStore.Episode.COVER);

                        }
                        else {
                            uri  = ContentUris.withAppendedId(ScraperStore.Movie.URI.ID, c.getLong(idMovieColumn));
                            cv.put(ScraperStore.Movie.POSTER_ID, -1);
                            cv.putNull(ScraperStore.Movie.COVER);
                        }
                        int n = context.getContentResolver().update(uri,cv,null,null);
                        Log.d(TAG,n+  "updated");

                    }
                }
            }
            if (c!=null)
                c.close();
        }
    }





}
