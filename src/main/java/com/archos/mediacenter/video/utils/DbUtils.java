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

package com.archos.mediacenter.video.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;

/**
 * Created by vapillon on 13/05/15.
 */
public class DbUtils {
    private static final String TAG = "DbUtils";

    private static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";

    static public void markAsRead(final Context context, final Video video) {
        final ContentResolver cr = context.getContentResolver();
        Log.d(TAG, "markAsRead " + video.getFilePath());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean networkBookmarksEnabled = prefs.getBoolean(KEY_NETWORK_BOOKMARKS, true);  //TODO networkBookmarks

        if (!video.isIndexed()) {
            return;
        }

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_END);

        // TRAKT_DB_MARKED must not be marked here or TraktService would think it is already synchronized
        // But if there uis not trakt sync we want to have the flag in the UI as well, hence we write it here ourselves!
        if (!traktSync) {
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_MARKED);
        }

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                VideoStore.Video.VideoColumns._ID + " =?",
                new String[]{Long.toString(video.getId())});

        if (traktSync) {
            syncTrakt(context, video);
        }
    }

    static public  void markAsNotRead(final Context context, final Video video) {
        final ContentResolver cr = context.getContentResolver();
        Log.d(TAG, "markAsNotRead " + video.getFilePath());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean networkBookmarksEnabled = prefs.getBoolean(KEY_NETWORK_BOOKMARKS, true);  //TODO networkBookmarks

        if (!video.isIndexed()) {
            return;
        }

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_UNKNOWN);
        // In the TRAKT_DB_UNMARK case we must write it ourselves to the DB (unlike for TRAKT_DB_MARKED)
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK);

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                VideoStore.Video.VideoColumns._ID + " =?",
                new String[]{Long.toString(video.getId())});

        if (traktSync) {
            syncTrakt(context, video);
        }
    }

    static private void syncTrakt(Context context, Base object) {
        int flags = TraktService.FLAG_SYNC_TO_TRAKT_WATCHED|TraktService.FLAG_SYNC_NOW;

        if (object instanceof Episode || object instanceof Season || object instanceof Tvshow)
            flags |= TraktService.FLAG_SYNC_SHOWS;
        else if (object instanceof Movie)
            flags |= TraktService.FLAG_SYNC_MOVIES;
        else
            return;

        new TraktService.Client(context, null, false).sync(flags);
        Toast.makeText(context, R.string.trakt_toast_syncing, Toast.LENGTH_SHORT).show();
    }

    public static void deleteScraperInfo(Context context,Video video) {
        // Reset the scraper fields for this item in the medialib
        // (set them to -1 because there is no need to search it again when running the automated task)
        // this also deletes the scraper data
        ContentValues values = new ContentValues(2);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID, "-1");
        values.put(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE, "-1");
        final String selection = VideoStore.MediaColumns._ID + "=?";
        final String[] selectionArgs =new String[]{Long.toString(video.getId())};
        context.getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);
        /*delete nfo files and posters*/
        Delete delete = new Delete(null,context);
        delete.deleteAssociatedNfoFiles(video.getFileUri());
    }


    public static void markAsHiddenByUser(Context context, Video video) {
        markHiddenValue(context, new Long[]{video.getId()}, 1);
    }
    public static void markAsNotHiddenByUser(Context context, Video video) {
        markHiddenValue(context, new Long[]{video.getId()}, 0);
    }

    public static void markHiddenValue(Context context, Long[] videoIds, int value) {
        Log.d(TAG, "markHiddenValue "+videoIds+" "+value);
        final ContentResolver cr = context.getContentResolver();
        final ContentValues values = new ContentValues(1);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER, value);
        String whereString ="";
        String [] whereArg = new String[videoIds.length];
        int i = 0;
        for (long id : videoIds) {
            if(!whereString.isEmpty())
                whereString+= " OR ";
            whereString += VideoStore.Video.VideoColumns._ID+ " = ?";
            whereArg[i] = Long.toString(id);
            i++;
        }
        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                whereString,
                whereArg);
    }

    /**
     * Marke all the episodes of a season as Watched
     * @param context
     * @param season
     */
    static public void markAsRead(final Context context, final Season season) {
        final ContentResolver cr = context.getContentResolver();
        Log.d(TAG, "markAsRead " + season.getName()+" S"+season.getSeasonNumber());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_END);

        // TRAKT_DB_MARKED must not be marked here or TraktService would think it is already synchronized
        // But if there uis not trakt sync we want to have the flag in the UI as well, hence we write it here ourselves!
        if (!traktSync) {
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_MARKED);
        }

        final String where = "_id IN (SELECT video_id FROM episode e JOIN show s ON e.show_episode=s._id WHERE s._id=? AND e.season_episode=?)";
        final String[] selectionArgs = new String[]{Long.toString(season.getShowId()), Integer.toString(season.getSeasonNumber())};

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);

        if (traktSync) {
            syncTrakt(context, season);
        }
    }

    /**
     * Marke all the episodes of a season as not read
     * @param context
     * @param season
     */
    static public void markAsNotRead(final Context context, final Season season) {
        final ContentResolver cr = context.getContentResolver();
        Log.d(TAG, "markAsNotRead " + season.getName()+" S"+season.getSeasonNumber());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_UNKNOWN);
        // In the TRAKT_DB_UNMARK case we must write it ourselves to the DB (unlike for TRAKT_DB_MARKED)
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK);

        final String where = "_id IN (SELECT video_id FROM episode e JOIN show s ON e.show_episode=s._id WHERE s._id=? AND e.season_episode=?)";
        final String[] selectionArgs = new String[]{Long.toString(season.getShowId()), Integer.toString(season.getSeasonNumber())};

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);

        if (traktSync) {
            syncTrakt(context, season);
        }
    }
    
    public static void markAsHiddenByUser(final Context context, final Season season) {
        final ContentResolver cr = context.getContentResolver();
        Log.d(TAG, "markHiddenValue " + season.getName()+" S"+season.getSeasonNumber());
        
        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER, 1);
        
        final String where = "_id IN (SELECT video_id FROM episode e JOIN show s ON e.show_episode=s._id WHERE s._id=? AND e.season_episode=?)";
        final String[] selectionArgs = new String[]{Long.toString(season.getShowId()), Integer.toString(season.getSeasonNumber())};
        
        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);
    }
    
    public static ArrayList<String> getFilePaths(final Context context, final Season season) {
        ArrayList<String> filePaths = new ArrayList<String>();
        final Uri uri = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
        final String[] projection = new String[] { VideoStore.MediaColumns.DATA };
        final String selection = VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + "=? AND " + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + "=?";
        final String[] selectionArgs = new String[] { String.valueOf(season.getShowId()), String.valueOf(season.getSeasonNumber()) };
        final String sortOrder = VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + ", " + VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE;
        Cursor c = context.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        final int filePathColumn = c.getColumnIndex(VideoStore.MediaColumns.DATA);
        
        c.moveToFirst();
        
        while (!c.isAfterLast()) {
            String filePath = c.getString(filePathColumn);
            
            filePaths.add(filePath);
            c.moveToNext();
        }
        
        c.close();
        
        return filePaths;
    }

    public static void markAllAsUnplayed(final Context context) {
        final ContentResolver cr = context.getContentResolver();
        
        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, 0);
        
        final String where = VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + "<>0";
        
        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, null);
    }
}
