package com.archos.mediacenter.video.leanback.channels;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.media.tv.Channel;
import android.support.media.tv.ChannelLogoUtils;
import android.support.media.tv.PreviewProgram;
import android.support.media.tv.TvContractCompat;
import android.support.v4.content.CursorLoader;
import android.support.v7.preference.PreferenceManager;
import android.util.ArrayMap;
import android.util.Log;

import com.archos.environment.ArchosFeatures;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.browser.loader.LastAddedLoader;
import com.archos.mediacenter.video.browser.loader.LastPlayedLoader;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.WatchingUpNextLoader;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediacenter.video.browser.loader.VideosByListLoader;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediacenter.video.leanback.details.VideoDetailsActivity;
import com.archos.mediacenter.video.leanback.details.VideoDetailsFragment;
import com.archos.mediacenter.video.leanback.tvshow.TvshowActivity;
import com.archos.mediacenter.video.leanback.tvshow.TvshowFragment;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

public class ChannelManager {

    private static final String TAG = "ChannelManager";
    private static final int MAX_PROGRAM_COUNT = 20;

    private static ChannelManager mInstance;
    
    private final Context mContext;
    private final String mWatchingUpNext;
    private final String mRecentlyAdded;
    private final String mRecentlyPlayed;
    private final String mAllMovies;
    private final String mAllTvShows;
    private LinkedHashMap<String, ChannelData> mChannels;
    private PrepareEmptyPosterTask mPrepareEmptyPosterTask;
    private PrepareChannelsTask mPrepareChannelsTask;
    private ArrayMap<String, CreateChannelTask> mCreateChannelTasks = new ArrayMap<>();
    private ArrayMap<String, RefreshChannelTask> mRefreshChannelTasks = new ArrayMap<>();

    public static void refreshChannels(Context context) {
        if(!ArchosFeatures.isAndroidTV(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;
        
        if (mInstance == null) {
            mInstance = new ChannelManager(context);
            mInstance.prepareEmptyPoster(); 
        }

        mInstance.prepareChannels();
    }

    public ChannelManager(Context context) {
        mContext = context;
        mWatchingUpNext = mContext.getString(R.string.watching_up_next);
        mRecentlyAdded = mContext.getString(R.string.recently_added);
        mRecentlyPlayed = mContext.getString(R.string.recently_played);
        mAllMovies = mContext.getString(R.string.all_movies);
        mAllTvShows = mContext.getString(R.string.all_tvshows);
    }
    
    private void prepareEmptyPoster() {
        if (mPrepareEmptyPosterTask != null)
            mPrepareEmptyPosterTask.cancel(true);

        mPrepareEmptyPosterTask = new PrepareEmptyPosterTask();

        mPrepareEmptyPosterTask.execute();
    }

    private void prepareChannels() {
        if (mPrepareChannelsTask != null)
            mPrepareChannelsTask.cancel(true);

        mPrepareChannelsTask = new PrepareChannelsTask();

        mPrepareChannelsTask.execute();
    }

    private void onChannelsPrepared() {
        mInstance.createChannels();
        mInstance.refreshChannels();
    }

    private void createChannels() {
        for(ChannelData channel : mChannels.values()) {
            if (channel.getId() == -1)
                createChannel(channel);
        }
    }

    private void createChannel(ChannelData channel) {
        CreateChannelTask oldTask = mCreateChannelTasks.get(channel.getName());

        if (oldTask != null)
            oldTask.cancel(true);

        CreateChannelTask newTask = new CreateChannelTask();

        mCreateChannelTasks.put(channel.getName(), newTask);
        newTask.execute(channel);
    }

    private void refreshChannels() {
        for(ChannelData channel : mChannels.values()) {
            if (channel.getId() != -1)
                refreshChannel(channel);
        }
    }

    private void refreshChannel(ChannelData channel) {
        RefreshChannelTask oldTask = mRefreshChannelTasks.get(channel.getName());

        if (oldTask != null)
            oldTask.cancel(true);
        
        RefreshChannelTask newTask = new RefreshChannelTask();

        mRefreshChannelTasks.put(channel.getName(), newTask);
        newTask.execute(channel);
    }

    private class PrepareEmptyPosterTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            String path = new File(mContext.getExternalCacheDir(), "empty_poster.png").getAbsolutePath();

            createEmptyPosterFile(path);
            createEmptyPosterRow(path);

            return null;
        }

        private void createEmptyPosterFile(String path) {
            File dest = new File(path);

            if (!dest.exists()) {
                try {
                    dest.createNewFile();

                    BitmapDrawable drawable = (BitmapDrawable)mContext.getResources().getDrawable(R.drawable.empty_poster);
                    Bitmap bitmap = drawable.getBitmap();
                    OutputStream stream = null;

                    try {
                        stream = new FileOutputStream(dest);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); //use the compression format of your need
                    }
                    catch (FileNotFoundException e) {
                        Log.e(TAG, "createEmptyPosterFile", e);
                    }
                }
                catch (IOException e) {
                    Log.e(TAG, "createEmptyPosterFile", e);
                }
            }
        }

        private void createEmptyPosterRow(String path) {
            Cursor cursor = mContext.getContentResolver().query(Uri.parse(VideoStore.Video.Thumbnails.EXTERNAL_CONTENT_URI.toString() + "/0"), new String[] { VideoStore.Video.Thumbnails._ID }, null, null, null);
            
            if (cursor == null || cursor.getCount() == 0) {
                ContentValues values = new ContentValues(2);
                values.put(VideoStore.Video.Thumbnails._ID, "0");
                values.put(VideoStore.Video.Thumbnails.DATA, path);

                mContext.getContentResolver().insert(VideoStore.Video.Thumbnails.EXTERNAL_CONTENT_URI, values);
            }

            if (cursor != null)
                cursor.close();
        }
    }

    private class PrepareChannelsTask extends AsyncTask<Void, Void, Void> {

        private VideosByListLoader mListLoader;

        @Override
        protected void onPreExecute() {
            mListLoader = new VideosByListLoader(mContext);
        }

        @Override
        protected Void doInBackground(Void... params) {
            addInternalChannels();
            deleteNotExistingChannels();

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            initInternalChannels();
            onChannelsPrepared();
        }

        private void addInternalChannels() {
            LinkedHashMap<String, ChannelData> newChannels = new LinkedHashMap<>();
            
            addInternalChannel(newChannels, mWatchingUpNext);
            addInternalChannel(newChannels, mRecentlyAdded);
            addInternalChannel(newChannels, mRecentlyPlayed);
            addInternalChannel(newChannels, mAllMovies);
            addInternalChannel(newChannels, mAllTvShows);

            Cursor listCursor = mListLoader.loadInBackground();

            int subsetIdColumn = listCursor.getColumnIndex(VideosByListLoader.COLUMN_SUBSET_ID);
            int subsetNameColumn = listCursor.getColumnIndex(VideosByListLoader.COLUMN_SUBSET_NAME);
            int videoIdsColumn = listCursor.getColumnIndex(VideosByListLoader.COLUMN_LIST_OF_VIDEO_IDS);

            try {
                while (listCursor.moveToNext())
                    addInternalChannel(newChannels, listCursor.getString(subsetNameColumn), listCursor.getLong(subsetIdColumn), listCursor.getString(videoIdsColumn));
            }
            finally {
                listCursor.close();
            }
            
            mChannels = newChannels;
        }

        private void addInternalChannel(LinkedHashMap<String, ChannelData> newChannels, String name) {
            addInternalChannel(newChannels, name, -1, null);
        }

        private void addInternalChannel(LinkedHashMap<String, ChannelData> newChannels, String name, long listId, String listVideoIds) {
            if (newChannels.containsKey(name))
                return;
            
            ChannelData channel;
            
            if (mChannels != null && mChannels.containsKey(name))
                channel = mChannels.get(name);
            else
                channel = new ChannelData(name);

            channel.setOrder(newChannels.size() + 1);
            channel.setListId(listId);
            channel.setListVideoIds(listVideoIds);
            newChannels.put(name, channel);
        }

        private void deleteNotExistingChannels() {
            Cursor cursor = mContext.getContentResolver().query(TvContractCompat.Channels.CONTENT_URI, new String[] { TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_DISPLAY_NAME }, null, null, null);

            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        Channel channel = Channel.fromCursor(cursor);
    
                        if (!mChannels.containsKey(channel.getDisplayName()))
                            mContext.getContentResolver().delete(TvContractCompat.buildChannelUri(channel.getId()), null, null);
                    }
                }
                finally {
                    cursor.close();
                }
            }
        }

        private void initInternalChannels() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
            String allMoviesSortOrder = prefs.getString(VideoPreferencesCommon.KEY_MOVIE_SORT_ORDER, MoviesLoader.DEFAULT_SORT);
            String allTvShowsSortOrder = prefs.getString(VideoPreferencesCommon.KEY_TV_SHOW_SORT_ORDER, TvshowSortOrderEntries.DEFAULT_SORT);

            mChannels.get(mWatchingUpNext).setLoader(new WatchingUpNextLoader(mContext));
            mChannels.get(mRecentlyAdded).setLoader(new LastAddedLoader(mContext));
            mChannels.get(mRecentlyPlayed).setLoader(new LastPlayedLoader(mContext));
            mChannels.get(mAllMovies).setLoader(new MoviesLoader(mContext, allMoviesSortOrder, true, true));
            mChannels.get(mAllTvShows).setLoader(new AllTvshowsLoader(mContext, allTvShowsSortOrder, true));

            for(ChannelData channel : mChannels.values()) {
                if (channel.getListVideoIds() != null)
                    channel.setLoader(new VideosSelectionLoader(mContext, channel.getListVideoIds(), VideoLoader.DEFAULT_SORT));
            }
        }
    }

    private class CreateChannelTask extends AsyncTask<ChannelData, Void, Void> {

        @Override
        protected Void doInBackground(ChannelData... params) {
            ChannelData channel = params[0];

            long channelId = getChannelId(channel);

            if (channelId == -1) {
                channelId = createChannel(channel);
                channel.setId(channelId);
            }
            else {
                channel.setId(channelId);
                updateChannel(channel);
            }
            
            return null;
        }

        private long getChannelId(ChannelData internalChannel) {
            Cursor cursor = mContext.getContentResolver().query(TvContractCompat.Channels.CONTENT_URI, new String[] { TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_DISPLAY_NAME }, null, null, null);

            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        Channel channel = Channel.fromCursor(cursor);
    
                        if (channel.getDisplayName().equals(internalChannel.getName())) {
                            cursor.close();

                            return channel.getId();
                        }
                    }
                }
                finally {
                    cursor.close();
                }
            }

            return -1;
        }

        private long createChannel(ChannelData channel) {
            Uri uri = mContext.getContentResolver().insert(TvContractCompat.Channels.CONTENT_URI, buildChannel(channel).toContentValues());
            long id = ContentUris.parseId(uri);
            
            ChannelLogoUtils.storeChannelLogo(mContext, id, BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.video2_full));
            
            return id;
        }

        private void updateChannel(ChannelData channel) {
            mContext.getContentResolver().update(TvContractCompat.buildChannelUri(channel.getId()), buildChannel(channel).toContentValues(), null, null);
        }

        private Channel buildChannel(ChannelData channel) {
            Channel.Builder builder = new Channel.Builder();
            builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
                    .setDisplayName(channel.getName())
                    .setConfigurationDisplayOrder(channel.getOrder())
                    .setAppLinkIntentUri(Uri.parse(mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName()).toUri(Intent.URI_INTENT_SCHEME)));

            return builder.build();
        }
    }

    private class RefreshChannelTask extends AsyncTask<ChannelData, Void, Void> {

        @Override
        protected Void doInBackground(ChannelData... params) {
            ChannelData channel = params[0];

            Log.d(TAG, "Refreshing " + channel.getName());

            boolean isVisible = isChannelVisible(channel);

            if (!isVisible) {
                deletePrograms(channel);

                return null;
            }

            Cursor cursor = channel.getLoader().loadInBackground();

            try {
                ArrayList<Long> oldVideoOrTvShowIds = getVideoOrTvShowIds(channel);
                ArrayList<Long> newVideoOrTvShowIds = getVideoOrTvShowIds(cursor);
                ArrayList<Long> oldPosterIds = getPosterIds(channel);
                ArrayList<Long> newPosterIds = getPosterIds(cursor);

                if (newVideoOrTvShowIds.equals(oldVideoOrTvShowIds) && newPosterIds.equals(oldPosterIds)) {
                    cursor.close();

                    return null;
                }

                deletePrograms(channel);

                boolean isVideo = cursor.getColumnIndex(VideoLoader.COLUMN_NAME) != -1;

                int typeColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE) : -1;
                int onlineIdColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_ONLINE_ID) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_ONLINE_ID);
                int posterIdColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_POSTER_ID) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_POSTER_ID);
                int titleColumn = isVideo ? cursor.getColumnIndex(VideoLoader.COLUMN_NAME) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
                int videoOrTvShowIdColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns._ID);
                int dateColumn = isVideo ? cursor.getColumnIndex(VideoLoader.COLUMN_DATE) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_PREMIERED);
                int seasonNumberColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON) : -1;
                int episodeNumberColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE) : -1;
                int durationColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.DURATION) : -1;
                int ratingColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_RATING) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_RATING);
                int episodeTitleColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_NAME) : -1;
                int plotColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_PLOT) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_PLOT);

                int count = 0;

                while (cursor.moveToNext() && count < MAX_PROGRAM_COUNT) {
                    // type
                    int type = isVideo ? cursor.getInt(typeColumn) : -1;
                    boolean isEpisode = type == BaseTags.TV_SHOW;

                    // poster id
                    long onlineId = cursor.getLong(onlineIdColumn);
                    long posterId = cursor.getLong(posterIdColumn);
                    Uri posterUri = null;

                    if (posterId != 0) {
                        if (isEpisode || !isVideo)
                            posterUri = Uri.parse(ScraperStore.ShowPosters.URI.BASE.toString() + "/" + onlineId + "/" + posterId);
                        else
                            posterUri = Uri.parse(ScraperStore.MoviePosters.URI.BASE.toString() + "/" + onlineId + "/" + posterId);
                    }
                    else {
                        posterUri = Uri.parse(VideoStore.Video.Thumbnails.EXTERNAL_CONTENT_URI.toString() + "/0");
                    }

                    // title
                    String title = cursor.getString(titleColumn);

                    // intent
                    long videoOrTvShowId = cursor.getLong(videoOrTvShowIdColumn);
                    Intent intent = null;

                    if (isVideo) {
                        intent = new Intent(mContext, VideoDetailsActivity.class);
                        intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO_ID, videoOrTvShowId);

                        if (channel.getListId() != -1)
                            intent.putExtra(VideoDetailsFragment.EXTRA_LIST_ID, channel.getListId());
                    }
                    else {
                        intent = new Intent(mContext, TvshowActivity.class);
                        intent.putExtra(TvshowFragment.EXTRA_TV_SHOW_ID, videoOrTvShowId);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    
                    PreviewProgram.Builder builder = new PreviewProgram.Builder();
                    builder.setChannelId(channel.getId())
                            .setType(isVideo ? (isEpisode ? TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE : TvContractCompat.PreviewPrograms.TYPE_MOVIE) : TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
                            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER)
                            .setPosterArtUri(posterUri)
                            .setTitle(title)
                            .setIntent(intent);

                    // date
                    String date = null;

                    if (isVideo) {
                        if (isEpisode) {
                            long aired = cursor.getLong(dateColumn);

                            if (aired > 0)
                                date = new SimpleDateFormat("yyyy-MM-dd").format(new Date(aired));
                        }
                        else {
                            int year = cursor.getInt(dateColumn);

                            if (year > 0)
                                date = String.valueOf(year);
                        }
                    }
                    else {
                        long premiered = cursor.getLong(dateColumn);

                        if (premiered > 0)
                            date = new SimpleDateFormat("yyyy").format(new Date(premiered));
                    }

                    if (date != null && !date.isEmpty())
                        builder.setReleaseDate(date);

                    // season number
                    int seasonNumber = isEpisode ? cursor.getInt(seasonNumberColumn) : -1;

                    if (seasonNumber != -1)
                        builder.setSeasonNumber(seasonNumber);

                    // episode number
                    int episodeNumber = isEpisode ? cursor.getInt(episodeNumberColumn) : -1;

                    if (episodeNumber != -1)
                        builder.setEpisodeNumber(episodeNumber);

                    // duration
                    int duration = isVideo ? cursor.getInt(durationColumn) : -1;

                    if (duration > 0)
                        builder.setDurationMillis(duration);

                    // rating
                    float rating = cursor.getFloat(ratingColumn);

                    if (rating > 0) {
                        builder.setReviewRatingStyle(TvContractCompat.PreviewPrograms.REVIEW_RATING_STYLE_PERCENTAGE)
                                .setReviewRating(String.valueOf(rating * 10));
                    }

                    // episode title
                    String episodeTitle = isEpisode ? cursor.getString(episodeTitleColumn) : null;

                    if (episodeTitle != null && !episodeTitle.isEmpty())
                        builder.setEpisodeTitle(episodeTitle);

                    // plot
                    String plot = cursor.getString(plotColumn);

                    if (plot != null && !plot.isEmpty())
                        builder.setDescription(plot);
                    
                    Uri uri = mContext.getContentResolver().insert(TvContractCompat.PreviewPrograms.CONTENT_URI, builder.build().toContentValues());
                    
                    Log.d(TAG, "Adding " + title);

                    count++;
                }
            }
            finally {
                cursor.close();
            }

            return null;
        }

        private boolean isChannelVisible(ChannelData internalChannel) {
            Cursor cursor = mContext.getContentResolver().query(TvContractCompat.Channels.CONTENT_URI, new String[] { TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_BROWSABLE }, null, null, null);

            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        Channel channel = Channel.fromCursor(cursor);
    
                        if (channel.getId() == internalChannel.getId()) {
                            cursor.close();

                            return channel.isBrowsable();
                        } 
                    }
                }
                finally {
                    cursor.close();
                }
            }

            return false;
        }

        private void deletePrograms(ChannelData channel) {
            Cursor cursor = mContext.getContentResolver().query(TvContractCompat.PreviewPrograms.CONTENT_URI, new String[] { TvContractCompat.Programs._ID, TvContractCompat.Programs.COLUMN_CHANNEL_ID }, null, null, null);
            
            if (cursor != null) {
                try {
                    while(cursor.moveToNext()) {
                        PreviewProgram program = PreviewProgram.fromCursor(cursor);
    
                        if (program.getChannelId() == channel.getId())
                            mContext.getContentResolver().delete(TvContractCompat.buildPreviewProgramUri(program.getId()), null, null);
                    }
                }
                finally {
                    cursor.close();
                }
            }
        }

        private ArrayList<Long> getVideoOrTvShowIds(ChannelData channel) {
            ArrayList<Long> videoOrTvShowIds = new ArrayList<>();
            Cursor cursor = mContext.getContentResolver().query(TvContractCompat.PreviewPrograms.CONTENT_URI, new String[] { TvContractCompat.Programs._ID, TvContractCompat.Programs.COLUMN_CHANNEL_ID, TvContractCompat.PreviewPrograms.COLUMN_INTENT_URI }, null, null, null);

            if (cursor != null) {
                try {
                    int count = 0;

                    while (cursor.moveToNext() && count < MAX_PROGRAM_COUNT) {
                        PreviewProgram program = PreviewProgram.fromCursor(cursor);

                        if (program.getChannelId() == channel.getId()) {
                            Intent intent = null;

                            try {
                                intent = program.getIntent();
                            }
                            catch (URISyntaxException e) {}

                            long videoOrTvShowId = -1;

                            if (intent != null) {
                                if (intent.hasExtra(VideoDetailsFragment.EXTRA_VIDEO_ID))
                                    videoOrTvShowId = intent.getLongExtra(VideoDetailsFragment.EXTRA_VIDEO_ID, -1);
                                else if (intent.hasExtra(TvshowFragment.EXTRA_TV_SHOW_ID))
                                    videoOrTvShowId = intent.getLongExtra(TvshowFragment.EXTRA_TV_SHOW_ID, -1);
                            }

                            videoOrTvShowIds.add(videoOrTvShowId);

                            count++;
                        }
                    }
                }
                finally {
                    cursor.close();
                }
            }

            return videoOrTvShowIds;
        }

        private ArrayList<Long> getVideoOrTvShowIds(Cursor cursor) {
            ArrayList<Long> videoOrTvShowIds = new ArrayList<>();
            int videoOrTvShowIdColumn = cursor.getColumnIndex(VideoStore.Video.VideoColumns._ID);
            int count = 0;

            while (cursor.moveToNext() && count < MAX_PROGRAM_COUNT) {
                long videoOrTvShowId = cursor.getLong(videoOrTvShowIdColumn);

                videoOrTvShowIds.add(videoOrTvShowId);
            
                count++;
            }

            cursor.moveToPosition(-1);

            return videoOrTvShowIds;
        }

        private ArrayList<Long> getPosterIds(ChannelData channel) {
            ArrayList<Long> posterIds = new ArrayList<>();
            Cursor cursor = mContext.getContentResolver().query(TvContractCompat.PreviewPrograms.CONTENT_URI, new String[] { TvContractCompat.Programs._ID, TvContractCompat.Programs.COLUMN_CHANNEL_ID, TvContractCompat.PreviewPrograms.COLUMN_POSTER_ART_URI }, null, null, null);

            if (cursor != null) {
                try {
                    int count = 0;

                    while (cursor.moveToNext() && count < MAX_PROGRAM_COUNT) {
                        PreviewProgram program = PreviewProgram.fromCursor(cursor);

                        if (program.getChannelId() == channel.getId()) {
                            Uri posterUri = program.getPosterArtUri();

                            posterIds.add(Long.parseLong(posterUri.getLastPathSegment()));

                            count++;
                        }
                    }
                }
                finally {
                    cursor.close();
                }
            }

            return posterIds;
        }

        private ArrayList<Long> getPosterIds(Cursor cursor) {
            ArrayList<Long> posterIds = new ArrayList<>();
            boolean isVideo = cursor.getColumnIndex(VideoLoader.COLUMN_NAME) != -1;
            int posterIdColumn = isVideo ? cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_POSTER_ID) : cursor.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_S_POSTER_ID);
            int count = 0;

            while (cursor.moveToNext() && count < MAX_PROGRAM_COUNT) {
                long posterId = cursor.getLong(posterIdColumn);
                
                posterIds.add(posterId);
            
                count++;
            }

            cursor.moveToPosition(-1);

            return posterIds;
        }
    }

    private class ChannelData {
        private String mName;
        private int mOrder;
        private long mId = -1;

        private long mListId;
        private String mListVideoIds;
        private CursorLoader mLoader;

        public String getName() {
            return mName;
        }

        public void setOrder(int order) {
            mOrder = order;
        }

        public int getOrder() {
            return mOrder;
        }

        public void setId(long id) {
            mId = id;
        }

        public long getId() {
            return mId;
        }

        public void setListId(long listId) {
            mListId = listId;
        }

        public long getListId() {
            return mListId;
        }

        public void setListVideoIds(String listVideoIds) {
            mListVideoIds = listVideoIds;
        }

        public String getListVideoIds() {
            return mListVideoIds;
        }

        public void setLoader(CursorLoader loader) {
            mLoader = loader;
        }

        public CursorLoader getLoader() {
            return mLoader;
        }

        public ChannelData(String name) {
            mName = name;
        }
    }
}