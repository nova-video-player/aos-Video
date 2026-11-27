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
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import android.content.ContentUris;
import com.archos.mediacenter.utils.simkl.Simkl;
import com.archos.mediascraper.BaseTags;
import java.util.List;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;

/**
 * Created by vapillon on 13/05/15.
 */
public class DbUtils {
    private static final String TAG = "DbUtils";
    private static final Boolean DBG = false;

    private static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";

    static public void markAsRead(final Context context, final Video video) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsRead: " + video.getFilePath());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean networkBookmarksEnabled = prefs.getBoolean(KEY_NETWORK_BOOKMARKS, true);  //TODO networkBookmarks

        if (!video.isIndexed()) {
            return;
        }

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);
        final boolean simklSync = Simkl.isSimklEnabled(context, prefs);

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
        if (simklSync) {
            syncSimkl(context, video, true);
        }
    }

    static public  void markAsNotRead(final Context context, final Video video) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsNotRead: " + video.getFilePath());
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
        values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, 0);

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values,
                VideoStore.Video.VideoColumns._ID + " =?",
                new String[]{Long.toString(video.getId())});

        if (traktSync) {
            syncTrakt(context, video);
        }
        // SIMKL /sync/history/remove also removes items from the Watchlist; keep ordinary unmark local.
    }

    static private void syncTrakt(Context context, Base object) {
        int flags = TraktService.FLAG_SYNC_TO_TRAKT_WATCHED|TraktService.FLAG_SYNC_NOW;

        if (object instanceof Episode || object instanceof Season || object instanceof Tvshow || object instanceof Collection)
            flags |= TraktService.FLAG_SYNC_SHOWS;
        else if (object instanceof Movie || object instanceof Collection) // collections are movies
            flags |= TraktService.FLAG_SYNC_MOVIES;
        else
            return;

        new TraktService.Client(context, null, false).sync(flags);
        Toast.makeText(context, R.string.trakt_toast_syncing, Toast.LENGTH_SHORT).show();
    }

    static public void syncSimkl(final Context context, final Base object, final boolean watched) {
        if (!watched) {
            // SIMKL /sync/history/remove also removes items from the Watchlist; ordinary unmark stays local.
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!Simkl.isSimklEnabled(context, prefs)) {
            return;
        }

        if (object instanceof Movie) {
            long tmdbId = ((Movie) object).getOnlineId();
            if (tmdbId > 0) {
                Simkl.markMovieAsWatched(tmdbId, watched, prefs);
                            } else {
                syncSimklForGenericVideo(context, ((Movie) object).getId(), watched, prefs);
            }
        } else if (object instanceof Episode) {
            Episode ep = (Episode) object;
            long showTmdbId = getShowOnlineIdForVideo(context, ep.getId());
            if (showTmdbId > 0) {
                Simkl.markEpisodeAsWatched(showTmdbId, ep.getSeasonNumber(), ep.getEpisodeNumber(), watched, prefs);
                            }
        } else if (object instanceof Season) {
            Season season = (Season) object;
            long showTmdbId = getShowOnlineId(context, season.getShowId());
            if (showTmdbId > 0) {
                List<Integer> indexedEpisodes = getIndexedEpisodesForSeason(context, season.getShowId(), season.getSeasonNumber());
                if (!indexedEpisodes.isEmpty()) {
                    Simkl.markSeasonEpisodesAsWatched(showTmdbId, season.getSeasonNumber(), indexedEpisodes, watched, prefs);
                }
            }
        } else if (object instanceof Tvshow) {
            Tvshow tvshow = (Tvshow) object;
            long showTmdbId = getShowOnlineId(context, tvshow.getTvshowId());
            if (showTmdbId > 0) {
                Simkl.markShowAsWatched(showTmdbId, watched, prefs);
                            }
        } else if (object instanceof Collection) {
            Collection coll = (Collection) object;
            List<Long> movieTmdbIds = getMovieOnlineIdsForCollection(context, coll.getCollectionId());
            if (!movieTmdbIds.isEmpty()) {
                Simkl.markMoviesAsWatched(movieTmdbIds, watched, prefs);
                            }
        } else if (object instanceof Video) {
            syncSimklForGenericVideo(context, ((Video) object).getId(), watched, prefs);
        }
    }

    private static List<Integer> getIndexedEpisodesForSeason(Context context, long showId, int seasonNumber) {
        List<Integer> list = new ArrayList<>();
        final String where = "_id IN (SELECT video_id FROM episode e JOIN show s ON e.show_episode=s._id WHERE s._id=? AND e.season_episode=?)";
        final String[] selectionArgs = new String[]{Long.toString(showId), Integer.toString(seasonNumber)};
        try (Cursor c = context.getContentResolver().query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{ VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE },
                where, selectionArgs, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    int ep = c.getInt(0);
                    if (ep >= 0 && !list.contains(ep)) {
                        list.add(ep);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getIndexedEpisodesForSeason error", e);
        }
        return list;
    }

    private static long getShowOnlineId(Context context, long showId) {
        long onlineId = -1;
        try (Cursor c = context.getContentResolver().query(
                ContentUris.withAppendedId(ScraperStore.Show.URI.ID, showId),
                new String[]{ ScraperStore.Show.ONLINE_ID }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                onlineId = c.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "getShowOnlineId error", e);
        }
        return onlineId;
    }

    private static long getShowOnlineIdForVideo(Context context, long videoId) {
        long showOnlineId = -1;
        try (Cursor c = context.getContentResolver().query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{ VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID },
                VideoStore.Video.VideoColumns._ID + " = ?",
                new String[]{ Long.toString(videoId) },
                null)) {
            if (c != null && c.moveToFirst()) {
                showOnlineId = c.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "getShowOnlineIdForVideo error", e);
        }
        return showOnlineId;
    }

    private static List<Long> getMovieOnlineIdsForCollection(Context context, long collectionId) {
        List<Long> list = new ArrayList<>();
        try (Cursor c = context.getContentResolver().query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{ VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID },
                "_id IN (SELECT video_id FROM movie m WHERE m.m_coll_id=?)",
                new String[]{ Long.toString(collectionId) },
                null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    if (id > 0) {
                        list.add(id);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMovieOnlineIdsForCollection error", e);
        }
        return list;
    }

    private static void syncSimklForGenericVideo(Context context, long videoId, boolean watched, SharedPreferences prefs) {
        try (Cursor c = context.getContentResolver().query(
                VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                new String[]{
                        VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE,
                        VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID,
                        VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID,
                        VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
                        VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE
                },
                VideoStore.Video.VideoColumns._ID + " = ?",
                new String[]{ Long.toString(videoId) },
                null)) {
            if (c != null && c.moveToFirst()) {
                int scraperType = c.getInt(0);
                if (scraperType == BaseTags.MOVIE) {
                    long tmdbId = c.getLong(1);
                    if (tmdbId > 0) {
                        Simkl.markMovieAsWatched(tmdbId, watched, prefs);
                                            }
                } else if (scraperType == BaseTags.TV_SHOW) {
                    long showTmdbId = c.getLong(2);
                    int season = c.getInt(3);
                    int episode = c.getInt(4);
                    if (showTmdbId > 0 && season >= 0 && episode >= 0) {
                        Simkl.markEpisodeAsWatched(showTmdbId, season, episode, watched, prefs);
                                            }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "syncSimklForGenericVideo error", e);
        }
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
        // delete nfo files and posters: no need in theory to do the API30 gazplant
        // TODO check and provide option to disable Q way here
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
        if (DBG) Log.d(TAG, "markHiddenValue: "+videoIds+" "+value);
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
        if (DBG) Log.d(TAG, "markAsRead: " + season.getName()+" S"+season.getSeasonNumber());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);
        final boolean simklSync = Simkl.isSimklEnabled(context, prefs);

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
        if (simklSync) {
            syncSimkl(context, season, true);
        }
    }

    /**
     * Marke all the episodes of a season as not read
     * @param context
     * @param season
     */
    static public void markAsNotRead(final Context context, final Season season) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsNotRead: " + season.getName()+" S"+season.getSeasonNumber());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_UNKNOWN);
        // In the TRAKT_DB_UNMARK case we must write it ourselves to the DB (unlike for TRAKT_DB_MARKED)
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, 0);

        final String where = "_id IN (SELECT video_id FROM episode e JOIN show s ON e.show_episode=s._id WHERE s._id=? AND e.season_episode=?)";
        final String[] selectionArgs = new String[]{Long.toString(season.getShowId()), Integer.toString(season.getSeasonNumber())};

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);

        if (traktSync) {
            syncTrakt(context, season);
        }
        // SIMKL /sync/history/remove also removes items from the Watchlist; keep ordinary unmark local.
    }

    /**
     * Marke all the movies of a collection as Watched
     * @param context
     * @param collection
     */
    static public void markAsRead(final Context context, final Collection collection) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsRead: " + collection.getName());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);
        final boolean simklSync = Simkl.isSimklEnabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_END);

        // TRAKT_DB_MARKED must not be marked here or TraktService would think it is already synchronized
        // But if there uis not trakt sync we want to have the flag in the UI as well, hence we write it here ourselves!
        if (!traktSync) {
            values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_MARKED);
        }

        final String where = "_id IN (SELECT video_id FROM movie m WHERE m.m_coll_id=?)";
        final String[] selectionArgs = new String[]{Long.toString(collection.getCollectionId())};

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);

        if (traktSync) {
            syncTrakt(context, collection);
        }
        if (simklSync) {
            syncSimkl(context, collection, true);
        }
    }

    /**
     * Marke all the movies of a collection as not read
     * @param context
     * @param collection
     */
    static public void markAsNotRead(final Context context, final Collection collection) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsNotRead: " + collection.getName());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_UNKNOWN);
        // In the TRAKT_DB_UNMARK case we must write it ourselves to the DB (unlike for TRAKT_DB_MARKED)
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, 0);

        final String where = "_id IN (SELECT video_id FROM movie m WHERE m.m_coll_id=?)";

        final String[] selectionArgs = new String[]{Long.toString(collection.getCollectionId())};

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);

        if (traktSync) {
            syncTrakt(context, collection);
        }
        // SIMKL /sync/history/remove also removes items from the Watchlist; keep ordinary unmark local.
    }
    
    public static void markAsHiddenByUser(final Context context, final Season season) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markHiddenValue: " + season.getName()+" S"+season.getSeasonNumber());
        
        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER, 1);

        final String where = "_id IN (SELECT video_id FROM episode e JOIN show s ON e.show_episode=s._id WHERE s._id=? AND e.season_episode=?)";
        final String[] selectionArgs = new String[]{Long.toString(season.getShowId()), Integer.toString(season.getSeasonNumber())};
        
        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, where, selectionArgs);
    }

    public static void markAsHiddenByUser(final Context context, final Collection collection) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markHiddenValue: " + collection.getName());

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER, 1);

        final String where = "_id IN (SELECT video_id FROM movie m WHERE m.m_coll_id=?)";

        final String[] selectionArgs = new String[]{Long.toString(collection.getCollectionId())};

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

    public static ArrayList<String> getFilePaths(final Context context, final Collection collection) {
        ArrayList<String> filePaths = new ArrayList<String>();
        final Uri uri = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
        final String[] projection = new String[] { VideoStore.MediaColumns.DATA };
        final String selection = VideoStore.Video.VideoColumns.SCRAPER_C_ID + "=?";
        final String[] selectionArgs = new String[] { String.valueOf(collection.getCollectionId()) };
        final String sortOrder = VideoStore.Video.VideoColumns.SCRAPER_M_NAME;
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

    public static void markAsNotRead(final Context context) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsNotRead");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final boolean traktSync = Trakt.isTraktV2Enabled(context, prefs);
        final boolean simklSync = Simkl.isSimklEnabled(context, prefs);

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.BOOKMARK, PlayerActivity.LAST_POSITION_UNKNOWN);
        // In the TRAKT_DB_UNMARK case we must write it ourselves to the DB (unlike for TRAKT_DB_MARKED)
        values.put(VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN, Trakt.TRAKT_DB_UNMARK);
        values.put(VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED, 0);

        cr.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, values, null, null);

        if (traktSync) {
            syncTrakt(context);
        }
    }

    static private void syncTrakt(Context context) {
        int flags = TraktService.FLAG_SYNC_TO_TRAKT_WATCHED|TraktService.FLAG_SYNC_NOW|TraktService.FLAG_SYNC_SHOWS|TraktService.FLAG_SYNC_MOVIES;

        new TraktService.Client(context, null, false).sync(flags);
        Toast.makeText(context, R.string.trakt_toast_syncing, Toast.LENGTH_SHORT).show();
    }

    static public void markAsPinned(final Context context, final Base base) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsPinned: " + base.getName());

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.NOVA_PINNED, (long)(System.currentTimeMillis() / 1000));

        if (base instanceof Movie) {
            cr.update(ScraperStore.Movie.URI.BASE, values,
                    VideoStore.Video.VideoColumns._ID + " =?",
                    new String[]{Long.toString(((Movie)base).getMovieId())});
        }
        else if (base instanceof Tvshow) {
            cr.update(ScraperStore.Show.URI.BASE, values,
                    VideoStore.Video.VideoColumns._ID + " =?",
                    new String[]{Long.toString(((Tvshow)base).getTvshowId())});
        }
    }

    static public  void markAsNotPinned(final Context context, final Base base) {
        final ContentResolver cr = context.getContentResolver();
        if (DBG) Log.d(TAG, "markAsNotPinned: " + base.getName());

        final ContentValues values = new ContentValues();
        values.put(VideoStore.Video.VideoColumns.NOVA_PINNED, 0);

        if (base instanceof Movie) {
            cr.update(ScraperStore.Movie.URI.BASE, values,
                    VideoStore.Video.VideoColumns._ID + " =?",
                    new String[]{Long.toString(((Movie)base).getMovieId())});
        }
        else if (base instanceof Tvshow) {
            cr.update(ScraperStore.Show.URI.BASE, values,
                    VideoStore.Video.VideoColumns._ID + " =?",
                    new String[]{Long.toString(((Tvshow)base).getTvshowId())});
        }
    }
}
