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

package com.archos.mediacenter.video.player;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.BaseColumns;

import androidx.loader.content.CursorLoader;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.AuthenticationException;
import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.RawListerFactoryWithUpnp;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.NextEpisodeLoader;
import com.archos.mediaprovider.video.ListTables;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by alexandre on 24/04/15.
 */
public class UpdateNextTask extends AsyncTask<Boolean, Integer, UpdateNextTask.Result> {

    private static final Logger log = LoggerFactory.getLogger(UpdateNextTask.class);

    private final ArrayList<String> mRemoteUrlsList;
    private final int mRemotePosition;
    private final ContentResolver mResolver;
    private final long mPlaylistId;
    private final Video mVideo;
    private Uri mUri;
    private Listener mListener;
    protected static class Result {
        public final Uri uri;
        public final long id;
        public Result(Uri uri, long id) {
            this.uri = uri;
            this.id = id;
        }
    }

    public static interface Listener {
        void onResult(Uri uri, long id);
    }

    public UpdateNextTask(ContentResolver resolver, Video video, Uri uri, ArrayList<String> remoteUrlsList, int remotePosition, long playlistId) {
        log.debug("UpdateNextTask: what is the next video after " + uri);
        mResolver = resolver;
        mUri = uri;
        mRemoteUrlsList = remoteUrlsList;
        mRemotePosition = remotePosition;
        mListener = null;
        mPlaylistId = playlistId;
        mVideo = video;
    }

    public void setListener(Listener listener) {
        log.debug("setListener");
        mListener = listener;
    }

    // SELECT _id,_data
    private final static String[] QUERY_COLUMNS_ID_DATA = { BaseColumns._ID, VideoStore.MediaColumns.DATA };
    // FROM video
    private final static Uri QUERY_VIDEO_TABLE = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
    // WHERE bucket_id=? AND _data > ?
    private final static String QUERY_WHERE_NEXT_VIDEO_IN_FOLDER = VideoStore.Video.VideoColumns.BUCKET_ID + "=? AND " +
            VideoStore.MediaColumns.DATA + ">?";
    // WHERE bucket_id=?
    private final static String QUERY_WHERE_FIRST_VIDEO_IN_FOLDER = VideoStore.Video.VideoColumns.BUCKET_ID + "=?";
    // ORDER BY data LIMIT 1;  // XXX Limit added to Order since we only need 1 entry and query() does not have a place for Limit
    private final static String QUERY_ORDER_BY_DATA_LIMIT1 = VideoStore.MediaColumns.DATA + " LIMIT 1";

    private static Cursor getNextInBucket(ContentResolver cr, int bucketId, String fullPathOfVideo) {
        log.debug("getNextInBucket: bucketId " + bucketId + " for video " + fullPathOfVideo);
        return cr.query(
                QUERY_VIDEO_TABLE,
                QUERY_COLUMNS_ID_DATA,
                QUERY_WHERE_NEXT_VIDEO_IN_FOLDER,
                new String[] { String.valueOf(bucketId), fullPathOfVideo },
                QUERY_ORDER_BY_DATA_LIMIT1);
    }
    private static Cursor getFirstInBucket(ContentResolver cr, int bucketId) {
        log.debug("getFirstInBucket: " + bucketId);
        return cr.query(
                QUERY_VIDEO_TABLE,
                QUERY_COLUMNS_ID_DATA,
                QUERY_WHERE_FIRST_VIDEO_IN_FOLDER,
                new String[] { String.valueOf(bucketId) },
                QUERY_ORDER_BY_DATA_LIMIT1);
    }
    private Result findEpisode(int episode, int season, long show){
        log.debug("findEpisode: look for episode " + episode);
        String [] projection2 = new String[]{VideoStore.MediaColumns.DATA,VideoStore.Files.FileColumns._ID,VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE};
        String []  args2;
        if(episode>=0)
            args2= new String[]{String.valueOf(episode),String.valueOf(show), String.valueOf(season)};
        else
            args2= new String[]{String.valueOf(show), String.valueOf(season)};
        String selection2 = (episode>=0?VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE + ">=? AND ":"")+VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID+" = ? AND "+VideoStore.Video.VideoColumns.SCRAPER_E_SEASON+" = ?";
        Cursor cursor2 = mResolver.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, projection2, selection2, args2, VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
        if(cursor2!=null) {
            if (cursor2.getCount() > 0) {
                cursor2.moveToFirst();
                int id = cursor2.getColumnIndex(VideoStore.Files.FileColumns._ID);
                int uri = cursor2.getColumnIndex(VideoStore.MediaColumns.DATA);
                Uri uri2 = Uri.parse(cursor2.getString(uri));
                int id2 = cursor2.getInt(id);
                log.debug("findEpisode: found new episode " + (cursor2.getInt(cursor2.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE))) + " " + cursor2.getString(uri));
                cursor2.close();
                return new Result(uri2, id2);
            }
            cursor2.close();
        }
        return null;
    }

    public UpdateNextTask.Result run(boolean repeatFolder, boolean binge) {
        log.debug("UpdateNextTask.Result: repeatFolder " + repeatFolder + ", binge " + binge);
        // reset to nothing
        Uri nextUri = null;
        long nextId = -1;

        if (mRemoteUrlsList != null) {
            log.debug("UpdateNextTask.Result: mRemoteUrlsList not null");
            int nextPos = mRemotePosition + 1;
            if ((nextPos == mRemoteUrlsList.size()) && !repeatFolder) {
                log.debug("UpdateNextTask.Result: updateNextVideo(" + repeatFolder + ") - using remote list, no next since last in list.");
                return null;
            }
            nextUri = Uri.parse(mRemoteUrlsList.get(nextPos % mRemoteUrlsList.size()));
            log.debug("UpdateNextTask.Result: updateNextVideo(" + repeatFolder + ") - using remote list, next is " + nextUri);
            return new Result(nextUri, nextId);
        } else {
            log.debug("UpdateNextTask.Result: mRemoteUrlsList null, mPlaylistId " + mPlaylistId + ", mVideo " + ((mVideo == null) ? "null" : mVideo.getFilePath()));
            if (mPlaylistId != -1 && mVideo != null) { //next in playlist
                BaseTags tags = mVideo.getFullScraperTags(ArchosUtils.getGlobalContext());
                long currentEpisodeId = tags instanceof EpisodeTags ? tags.getOnlineId() : -1;
                long currentMovieId = tags instanceof MovieTags ? tags.getOnlineId() : -1;

                // binge watch mode to find next episode
                if (binge && currentEpisodeId != -1) {
                    CursorLoader loader = new NextEpisodeLoader(ArchosUtils.getGlobalContext(), (EpisodeTags)tags);
                    Cursor c = loader.loadInBackground();
                    if (c.getCount()>0) {
                        c.moveToFirst();
                        nextUri = Uri.parse(c.getString(c.getColumnIndex(VideoStore.MediaColumns.DATA)));
                    }
                    c.close();
                    log.debug("UpdateNextTask.Result binge mode " + nextUri);
                    if (nextUri != null) return new Result(nextUri, nextId);
                    else return null;
                }

                boolean useNextVideo = false;
                String selection = "Select \n" +
                        "   v._id as _id,\n" +
                        "   v." + VideoStore.MediaColumns.DATA + " as " + VideoStore.MediaColumns.DATA + ", \n" +
                        "   vl." + VideoStore.VideoList.Columns.M_ONLINE_ID + " as " + VideoStore.VideoList.Columns.M_ONLINE_ID + ", \n" +
                        "   vl." + VideoStore.VideoList.Columns.E_ONLINE_ID + " as " + VideoStore.VideoList.Columns.E_ONLINE_ID +
                        "   from \n" +
                        "   video v,\n" +
                        "   " + ListTables.VIDEO_LIST_TABLE + " vl \n" +
                        "WHERE\n" +
                        "        (" +
                        "(" +
                        "v." + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + " = vl." + VideoStore.VideoList.Columns.M_ONLINE_ID + " " +
                        " AND v." + VideoStore.Video.VideoColumns.SCRAPER_M_ONLINE_ID + " NOT NULL" +
                        " OR v." + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + " = vl." + VideoStore.VideoList.Columns.E_ONLINE_ID + " " +
                        " AND v." + VideoStore.Video.VideoColumns.SCRAPER_E_ONLINE_ID + " NOT NULL) " +
                        " AND " + LoaderUtils.HIDE_USER_HIDDEN_FILTER +
                        " AND vl." + VideoStore.List.Columns.SYNC_STATUS + " != " + VideoStore.List.SyncStatus.STATUS_DELETED +
                        " AND vl." + VideoStore.VideoList.Columns.LIST_ID + " = ?)";
                Cursor cursor = mResolver.query(VideoStore.RAW_QUERY, null, selection, new String[]{mPlaylistId + ""}, null);
                if (cursor != null && cursor.getCount() > 0) {
                    log.debug("UpdateNextTask.Result: cursor not null");
                    int movieIdColumn = cursor.getColumnIndex(VideoStore.VideoList.Columns.M_ONLINE_ID);
                    int episodeIdColumn = cursor.getColumnIndex(VideoStore.VideoList.Columns.E_ONLINE_ID);
                    while (cursor.moveToNext()) {
                        long episodeId = cursor.getLong(episodeIdColumn);
                        long movieId = cursor.getLong(movieIdColumn);
                        log.debug("UpdateNextTask.Result: episodeId " + episodeId + ", movieId " + movieId);
                        if (currentEpisodeId != -1 && currentEpisodeId == episodeId) {
                            useNextVideo = true;
                        } else if (currentMovieId != -1 && currentMovieId == movieId) {
                            useNextVideo = true;
                        } else if (useNextVideo) { //previous was our video, this one is different, use Uri
                            nextUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
                            return new Result(nextUri, nextId);
                        }
                    }
                } else {
                    log.debug("UpdateNextTask.Result: cursor null");
                }
                log.debug("UpdateNextTask.Result: found nothing");
                return null;
            }
        }
        if (!UriUtils.isImplementedByFileCore(mUri)) {// when we can't list folder
            log.debug("UpdateNextTask.Result: scheme for " + mUri + " not supported -> cannot list folder thus no next...");
            return null;
        }
        /*
            If we don't have a list of files set by arguments, we need to create one
         */
        if (mUri != null) {
            log.debug("UpdateNextTask.Result: trying to find next for mUri " + mUri);
            String[] projection1 = new String[]{VideoStore.MediaColumns.DATA, VideoStore.Files.FileColumns._ID, VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE, VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, VideoStore.Video.VideoColumns.SCRAPER_E_SEASON};
            String[] args1 = new String[]{mUri.toString()};
            String selection1 = VideoStore.MediaColumns.DATA + "=?";
            //retrieve episode number
            Cursor cursor1 = mResolver.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, projection1, selection1, args1, VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
            if (cursor1 != null && cursor1.getCount() > 0) {
                log.debug("UpdateNextTask.Result: found next episodes");

                int episodeColumn = cursor1.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
                int seasonColumn = cursor1.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
                int showColumn = cursor1.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID);

                cursor1.moveToFirst();
                int episode = cursor1.getInt(episodeColumn);
                int season = cursor1.getInt(seasonColumn);
                long show = cursor1.getLong(showColumn);
                if (cursor1 != null) cursor1.close();
                if (show > 0 && season >= 0 && episode >= 0) {
                    log.debug("current episode : " + episode + " " + mUri);
                    Result result = findEpisode(episode + 1, season, show);
                    if (result != null)
                        return result;
                    else {
                        if (binge) {
                            result = findEpisode(-1, season + 1, show);
                            if (result != null)
                                return result;
                        } else if (repeatFolder) { //when no next episode, look for the first one
                            result = findEpisode(-1, season, show);
                            if (result != null)
                                return result;
                        }
                    }

                }
            }
            if (cursor1 != null) cursor1.close();
            if (! binge) {
                int bucketId = FileUtils.getBucketId(mUri);
                log.debug("UpdateNextTask.Result: trying to find for bucketId " + bucketId);
                Cursor cursor;
                // 1. Try to find the next video in the database
                cursor = getNextInBucket(mResolver, bucketId, mUri.toString());
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            nextUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.Files.FileColumns.DATA)));
                            nextId = cursor.getInt(cursor.getColumnIndex(VideoStore.Files.FileColumns._ID));
                            log.debug("updateNextVideo(" + repeatFolder + ") - next via getNextInBucket(DB):" + nextUri);
                            return new Result(nextUri, nextId);
                        } else log.debug("updateNextVideo(" + repeatFolder + ") - getNextInBucket empty cursor!?");
                    } finally {
                        cursor.close();
                    }
                } else log.debug("updateNextVideo(" + repeatFolder + ") - getNextInBucket null cursor!?");

                if (repeatFolder) {
                    // 2. Try to find the first video in that folder in the database
                    cursor = getFirstInBucket(mResolver, bucketId);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                nextUri = Uri.parse(cursor.getString(cursor.getColumnIndex(VideoStore.Files.FileColumns.DATA)));
                                nextId = cursor.getInt(cursor.getColumnIndex(VideoStore.Files.FileColumns._ID));
                                log.debug("updateNextVideo(" + repeatFolder + ") - next via getFirstInBucket(DB):" + nextUri);
                                return new Result(nextUri, nextId);
                            } else log.debug("updateNextVideo(" + repeatFolder + ") - getNextInBucket empty cursor!?");
                        } finally {
                            cursor.close();
                        }
                    } else log.debug("updateNextVideo(" + repeatFolder + ") - getFirstInBucket null cursor!?");
                }
                // 3. try to find the next file within the filesystem
                if (mUri.getScheme() == null)
                    mUri = Uri.parse("file://" + mUri.toString());
                Uri parentUri = FileUtils.getParentUrl(mUri);
                if (parentUri != null) {
                    RawLister lister = RawListerFactoryWithUpnp.getRawListerForUrl(parentUri);

                    try {
                        List<MetaFile2> files = lister.getFileList();
                        if (files != null && files.size() > 0) {
                            List<MetaFile2> filteredList = new ArrayList<MetaFile2>();
                            //filter folder
                            for (int i = 0; i < files.size(); i++) {
                                if (files.get(i).isFile()) {
                                    String mimeType = files.get(i).getMimeType();
                                    if (mimeType != null && mimeType.startsWith("video/"))
                                        filteredList.add(files.get(i));
                                }
                            }
                            final Comparator<? super MetaFile2> comparator = new FileComparator().selectFileComparator(ListingEngine.SortOrder.SORT_BY_NAME_ASC);
                            Collections.sort(filteredList, comparator);
                            int total = filteredList.size();
                            int current = -1;
                            // try to find the current video in the list
                            for (int i = 0; i < filteredList.size(); i++) {
                                if (mUri.equals(filteredList.get(i).getUri())) {
                                    current = i;
                                    break;
                                }
                            }
                            // if it is found
                            if (current != -1) {
                                int next = current + 1;
                                // when repeat folder, just modulo
                                if (repeatFolder) {
                                    nextUri = filteredList.get(next % total).getUri();
                                    log.debug("updateNextVideo(" + repeatFolder + ") - next via filesystem:" + nextUri);
                                    return new Result(nextUri, nextId);
                                } else if (next < total) {
                                    // in no repeat more, check that next still in list
                                    nextUri = filteredList.get(next).getUri();
                                    log.debug("updateNextVideo(" + repeatFolder + ") - next via filesystem:" + nextUri);
                                    return new Result(nextUri, nextId);
                                } else log.debug("updateNextVideo(" + repeatFolder + ") - no next in filesystem, it's the last:" + mUri);
                            } else log.debug("updateNextVideo(" + repeatFolder + ") - could not find video in list:" + mUri);
                        } else log.debug("updateNextVideo(" + repeatFolder + ") - could not list files / empty dir:" + mUri);
                    } catch (IOException e) {
                        log.error("UpdateNextTask.Result: caught IOException ", e);
                    } catch (AuthenticationException e) {
                        log.error("UpdateNextTask.Result: caught AuthenticationException ", e);
                    } catch (SftpException e) {
                        log.error("UpdateNextTask.Result: caught SftpException ", e);
                    } catch (JSchException e) {
                        log.error("UpdateNextTask.Result: caught JSchException ", e);
                    }
                } else log.debug("updateNextVideo(" + repeatFolder + ") - no parent file:" + mUri);
            }
        } else log.debug("updateNextVideo(" + repeatFolder + ") - not a local file:" + mUri);

        log.debug("updateNextVideo(" + repeatFolder + ") - No next found");
        return null;
    }

    @Override
    protected UpdateNextTask.Result doInBackground(Boolean... params) {
        if (params.length < 1)
            return null;
        return run(params[0], params[1]);
    }

    @Override
    protected void onPostExecute(Result result) {
        if (mListener != null) {
            if (result != null)
                mListener.onResult(result.uri, result.id);
            else
                mListener.onResult(null, -1);
        }
    }
}
