package com.archos.mediacenter.video.leanback.channels;

import android.app.Notification;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.v4.content.CursorLoader;

import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.media.tv.Channel;
import android.support.media.tv.ChannelLogoUtils;
import android.support.media.tv.PreviewProgram;
import android.support.media.tv.TvContractCompat;
import android.util.Log;

import com.archos.environment.ArchosFeatures;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.UpdateRecommendationsService;
import com.archos.mediacenter.video.browser.loader.LastAddedLoader;
import com.archos.mediacenter.video.leanback.details.VideoDetailsActivity;
import com.archos.mediacenter.video.leanback.details.VideoDetailsFragment;
import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ChannelManager {

    private static final String TAG = "ChannelManager";
    private final Context mContext;
    private long mRecentlyAddedChannelId;
    private int mNameColumn;
    private int mIDColumns;
    private int mProgressColumns;
    private int mDurationColumns;

    public ChannelManager(Context context){
        mContext = context;
    }

    private static final String RECENTLY_ADDED_KEY = "recently-added-channel-id";

    public static void refreshChannels(Context context) {
        if(!ArchosFeatures.isAndroidTV(context))
            return;
        ChannelManager channelManager = new ChannelManager(context);
        channelManager.prepareEmptyPoster();
        channelManager.createChannels();
        channelManager.refreshChannels();
    }

    private void prepareEmptyPoster() {
        File dest = new File(mContext.getExternalCacheDir(), "poster.png");
        if(dest.exists())
            return;
        try {
            dest.createNewFile();
            Drawable drawable = mContext.getResources().getDrawable(R.drawable.filetype_new_video_poster);
            BitmapDrawable bitmapDrawable = ((BitmapDrawable) drawable);
            Bitmap bitmap = bitmapDrawable.getBitmap();
            OutputStream stream = null;
            try {
                stream = new FileOutputStream(dest);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); //use the compression format of your need

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void refreshChannels() {

        Channel.Builder builder = new Channel.Builder();
        builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(mContext.getString(R.string.recently_added))
                .setAppLinkIntentUri(Uri.parse(mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName()).toUri(Intent.URI_INTENT_SCHEME)));
        mContext.getContentResolver().update(TvContractCompat.buildChannelUri(mRecentlyAddedChannelId),
                builder.build().toContentValues(), null, null);

        Cursor c = null;

        try {
            CursorLoader cursorloader = new LastAddedLoader(mContext);
            c = cursorloader.loadInBackground();
            mNameColumn = c.getColumnIndex(UpdateRecommendationsService.Columns.NAME);
            mIDColumns = c.getColumnIndex(UpdateRecommendationsService.Columns.ID);
            mProgressColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.BOOKMARK);
            mDurationColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.DURATION);
            int episodeNameColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_NAME);
            int seasonColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
            int episodeColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
            int count = 0;
            int total = c.getCount();
            List<Integer> addedCards = new ArrayList<>();
            Log.d(TAG, "Updating");

            Cursor progCur = mContext.getContentResolver().query(TvContractCompat.PreviewPrograms.CONTENT_URI, new String[]{"_id", TvContractCompat.Programs.COLUMN_TITLE},null, null, null);
            while(progCur.moveToNext()) {
                mContext.getContentResolver().delete(TvContractCompat.buildPreviewProgramUri(progCur.getLong(progCur.getColumnIndex("_id"))), null, null);
            }
            while(c.moveToNext()){
                if(count >10) break;
                try {
                    boolean isTVShow = false;
                    int season = c.getInt(seasonColumns);
                    int episode = c.getInt(episodeColumns);
                    if (season != 0 && episode != 0)
                        isTVShow = true;
                    final String scraperCover = c.getString(c.getColumnIndexOrThrow(UpdateRecommendationsService.Columns.COVER_PATH));
                    String posterPath = "";
                    if(!scraperCover.isEmpty()) {
                        posterPath = "content://"+ArchosMediaCommon.AUTHORITY_SCRAPER+"/tags/"+(isTVShow?"showposters/":"movieposters/")+(c.getLong(c.getColumnIndex("poster_id")));
                    } else {
                        posterPath = "file:/"+new File(mContext.getExternalCacheDir(), "poster.png").getAbsolutePath();
                    }

                    PreviewProgram.Builder progBuilder = new PreviewProgram.Builder();
                    progBuilder.setChannelId(mRecentlyAddedChannelId)
                            .setType(isTVShow?TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE:TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                            .setTitle(c.getString(mNameColumn));
                    if(isTVShow){
                        progBuilder.setEpisodeNumber(episode)
                                .setSeasonNumber(season)
                                .setEpisodeTitle(c.getString(episodeNameColumns));
                    }
                    Intent intent = new Intent(mContext, VideoDetailsActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(VideoDetailsFragment.EXTRA_VIDEO_ID, c.getLong(mIDColumns));
                    progBuilder.setIntentUri(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
                    progBuilder.setPosterArtAspectRatio(5)
                            .setPosterArtUri(Uri.parse(posterPath));
                    Uri programUri = mContext.getContentResolver().insert(TvContractCompat.PreviewPrograms.CONTENT_URI,
                            progBuilder.build().toContentValues());
                    Log.d(TAG, "adding "+c.getString(mNameColumn));

                    count++;
                } catch (Exception e) {
                    Log.e(TAG, "Unable to add program", e);
                }
            }

        } catch(SQLiteException ignored){
        } finally {
            if (c != null)
                c.close();
        }
    }

    public void createChannels(){
        mRecentlyAddedChannelId = PreferenceManager.getDefaultSharedPreferences(mContext).getLong(RECENTLY_ADDED_KEY,-1);

        if(mRecentlyAddedChannelId == -1) {
            Channel.Builder builder = new Channel.Builder();
            builder.setType(TvContractCompat.Channels.TYPE_PREVIEW)
                    .setDisplayName(mContext.getString(R.string.recently_added))
                    .setAppLinkIntentUri(Uri.parse(mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName()).toUri(Intent.URI_INTENT_SCHEME)));

            Uri channelUri = mContext.getContentResolver().insert(
                    TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues());
            mRecentlyAddedChannelId = ContentUris.parseId(channelUri);
            ChannelLogoUtils.storeChannelLogo(mContext, mRecentlyAddedChannelId, BitmapFactory.decodeResource(mContext.getResources(), R.mipmap.ic_launcher_foreground));
            PreferenceManager.getDefaultSharedPreferences(mContext).edit().putLong(RECENTLY_ADDED_KEY, mRecentlyAddedChannelId).commit();
        }
    }

}
