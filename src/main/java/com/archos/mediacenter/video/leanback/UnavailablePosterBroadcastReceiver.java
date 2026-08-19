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
import android.os.Build;

import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by alexandre on 21/12/15.
 */
public class UnavailablePosterBroadcastReceiver extends BroadcastReceiver{

    private static final Logger log = LoggerFactory.getLogger(UnavailablePosterBroadcastReceiver.class);

    private static UnavailablePosterBroadcastReceiver sReceiver;
    private static String ACTION_CHECK_POSTER = "ACTION_CHECK_POSTER";
    public static final String COLUMN_COVER_PATH = "cover";

    //delete poster from DB when can't be loaded

    public static void registerReceiver(Context context){
        if(sReceiver == null)
            sReceiver = new UnavailablePosterBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CHECK_POSTER);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(sReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(sReceiver, filter);
        }
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
        intent.setPackage(context.getPackageName());
        intent.putExtra("VIDEO_ID", videoId);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (log.isDebugEnabled()) log.debug("onReceive");
        if(ACTION_CHECK_POSTER.equals(intent.getAction())&&intent.getLongExtra("VIDEO_ID",-1)!=-1){
            if (log.isDebugEnabled()) log.debug("onReceive2");
            StringBuilder sb = new StringBuilder();
            if (LoaderUtils.mustHideUserHiddenObjects()) {
                sb.append(LoaderUtils.HIDE_USER_HIDDEN_FILTER);
                sb.append(" AND ");
            }
            sb.append(VideoStore.Video.VideoColumns._ID + " = ? ");

            String[] arg = new String[]{Long.toString(intent.getLongExtra("VIDEO_ID",-1))};
            String where = sb.toString();
            Cursor c = context.getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, new String[]{COLUMN_COVER_PATH,VideoStore.Video.VideoColumns.TITLE, VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID, VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID,VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE}, where, arg, null);
            if(c!=null&&c.getCount()>0){
                c.moveToFirst();
                int coverColumn = c.getColumnIndex(COLUMN_COVER_PATH);
                int titleColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.TITLE);
                int idMovieColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID);
                int idEpisodeColumn = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_EPISODE_ID);
                final int scraperType = c.getInt(c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                if(c.getString(coverColumn)!=null){
                    String path = c.getString(coverColumn);
                    Uri posterUri = Uri.parse(path);
                    boolean shouldClear = false;
                    String reason = "";

                    try {
                        if(!FileEditorFactory.getFileEditorForUrl(posterUri, null).exists()){
                            shouldClear = true;
                            reason = "file does not exist";
                        } else {
                            // Check if file exists but is empty (corrupted/incomplete download)
                            long fileSize = FileEditorFactory.getFileEditorForUrl(posterUri, null).length();
                            if (fileSize == 0) {
                                shouldClear = true;
                                reason = "file is empty (0 bytes)";
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error checking poster file: {}", path, e);
                        shouldClear = true;
                        reason = "error accessing file: " + e.getMessage();
                    }

                    if (shouldClear) {
                        ContentValues cv = new ContentValues();
                        Uri uri;
                        long scraperId;
                        if (scraperType == BaseTags.TV_SHOW) {
                            scraperId = c.getLong(idEpisodeColumn);
                            uri = ContentUris.withAppendedId(ScraperStore.Episode.URI.ID, scraperId);
                            cv.put(ScraperStore.Episode.POSTER_ID, -1);
                            cv.putNull(ScraperStore.Episode.COVER);
                            log.warn("Cached episode poster missing or corrupted: path={}, title={}, videoId={}, episodeId={}, reason={} - clearing and triggering re-scrape",
                                    path, c.getString(titleColumn), intent.getLongExtra("VIDEO_ID",-1), scraperId, reason);
                        }
                        else {
                            scraperId = c.getLong(idMovieColumn);
                            uri = ContentUris.withAppendedId(ScraperStore.Movie.URI.ID, scraperId);
                            cv.put(ScraperStore.Movie.POSTER_ID, -1);
                            cv.putNull(ScraperStore.Movie.COVER);
                            log.warn("Cached movie poster missing or corrupted: path={}, title={}, videoId={}, movieId={}, reason={}  - clearing and triggering re-scrape",
                                    path, c.getString(titleColumn), intent.getLongExtra("VIDEO_ID",-1), scraperId, reason);
                        }
                        int n = context.getContentResolver().update(uri,cv,null,null);
                        if (log.isDebugEnabled()) log.debug("{} DB records updated for {}, poster cleared to trigger re-download", n, (scraperType == BaseTags.TV_SHOW ? "episode" : "movie"));

                    }
                }
                else {
                    if (log.isDebugEnabled()) log.debug("Video has no poster path in DB: title={}, videoId={}", c.getString(titleColumn), intent.getLongExtra("VIDEO_ID",-1));
                }
            }
            if (c!=null)
                c.close();
        }
    }
}
