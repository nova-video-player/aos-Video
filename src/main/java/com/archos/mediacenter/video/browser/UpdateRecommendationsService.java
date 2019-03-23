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

package com.archos.mediacenter.video.browser;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class  UpdateRecommendationsService extends IntentService {
	private static final String TAG = "UpdateRecommendationsService";
	public static class Columns {
		public static final String ID = BaseColumns._ID;
		public static final String NAME = "name";
		public static final String NAME_LIST = "name_list";
		public static final String NAME_GRID = "name_grid";
		public static final String RESUME = VideoStore.Video.VideoColumns.BOOKMARK;
		public static final String BOOKMARK = VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK;
		public static final String PATH = VideoStore.MediaColumns.DATA;
		public static final String RATING = "rating";
		public static final String COVER_PATH = "cover";
		public static final String DATE = "date";
		public static final String TRAKT_SEEN = VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN;

	}
	private static final int MAX_RECOMMENDATIONS = 3;
	private static final String SELECT_LAST_PLAYED = VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED
			+ "!=0";
	protected static final String HIDE_WATCHED_FILTER = " AND ("+VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN+" IS NULL OR "+VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " != "+Trakt.TRAKT_DB_MARKED
			+") AND ("+VideoStore.Video.VideoColumns.BOOKMARK+" IS NULL OR "+VideoStore.Video.VideoColumns.BOOKMARK+" != "+PlayerActivity.LAST_POSITION_END+")";

	private static final String SORT_LAST_PLAYED = VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED
			+ " DESC LIMIT 0,"+MAX_RECOMMENDATIONS;
	protected static final String COALESCE = "COALESCE(";
	protected static final String EPISODE = "|| 'E' || ";
	protected static final String SEASON = " || ' S' || ";
	private static final boolean DBG = false;
	protected  final String COVER = COALESCE
			+ VideoStore.Video.VideoColumns.SCRAPER_COVER + ",'') AS " + Columns.COVER_PATH;
	public  final String NAME = COALESCE + VideoStore.Video.VideoColumns.SCRAPER_TITLE
			+ "," + VideoStore.MediaColumns.TITLE + ") AS " + Columns.NAME;
	private String[] mProjection = {
			VideoStore.Video.VideoColumns._ID, VideoStore.Video.VideoColumns.DATA,
			VideoStore.Video.VideoColumns.BOOKMARK,
			VideoColumns.DURATION,
			VideoStore.Video.VideoColumns.ARCHOS_BOOKMARK,
			VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
			VideoStore.Video.VideoColumns.SCRAPER_E_NAME,
			VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
			VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE,
			COVER,  NAME,
			null, null
	};
	private int mNameColumn;
	private NotificationManager mNotificationManager;
	private int mIDColumns;
	private IBinder binder = new RecommendationServiceBinder();
	private static int mLastCount;
	private int mProgressColumns;
	private int mDurationColumns;
	private static List<Integer> sLastCard = new ArrayList<>();
	public UpdateRecommendationsService() {
		super("RecommendationService");

	}



	public class RecommendationServiceBinder extends Binder{
		public UpdateRecommendationsService getService(){
			return UpdateRecommendationsService.this;
		}

	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return binder;
	}
	public void update(){
		Cursor c = null;

		try {
			CursorLoader cursorloader = new CursorLoader(this,VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
					mProjection, SELECT_LAST_PLAYED+HIDE_WATCHED_FILTER+" AND "+ LoaderUtils.HIDE_USER_HIDDEN_FILTER, null,
					SORT_LAST_PLAYED);
			c = cursorloader.loadInBackground();
			mNameColumn = c.getColumnIndex(Columns.NAME);
			mIDColumns = c.getColumnIndex(Columns.ID);
			mProgressColumns = c.getColumnIndex(VideoColumns.BOOKMARK);
			mDurationColumns = c.getColumnIndex(VideoColumns.DURATION);
			int episodeNameColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_NAME);
			int seasonColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
			int episodeColumns = c.getColumnIndex(VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);
			int count = 0;
			int total = c.getCount();
			//mNotificationManager.cancelAll();
			List<Integer> addedCards = new ArrayList<>();
			if(DBG) Log.d(TAG, "Updating");
			while(c.moveToNext()){
				try {
					RecommendationBuilder builder = new RecommendationBuilder()
							.setContext(getApplicationContext())
							.setSmallIcon(R.mipmap.video2);
					final String scraperCover = c.getString(c.getColumnIndexOrThrow(Columns.COVER_PATH));
					Bitmap bitmap = BitmapFactory.decodeFile(scraperCover);
					if (bitmap == null&&c.getLong(mIDColumns) >= 0) {
						BitmapFactory.Options options = new BitmapFactory.Options();
						options.inSampleSize = 2;
						bitmap = VideoStore.Video.Thumbnails.getThumbnail(getContentResolver(), c.getLong(mIDColumns), VideoStore.Video.Thumbnails.MINI_KIND, options);
					}
					if (bitmap == null){
						bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.widget_default_video);
					}
					int season = c.getInt(seasonColumns);
					int episode = c.getInt(episodeColumns);
					if (season != 0 && episode != 0)
						builder.setDescription(String.format("S%02dE%02d  -  %s", season, episode, c.getString(episodeNameColumns)));
					Notification notification = builder.setTitle(c.getString(mNameColumn))
							.setImage(bitmap)
							.setMax(c.getInt(mDurationColumns))
							.setProgress(c.getInt(mProgressColumns))
							.setPriority(total - count)
							.setIntent(buildPendingIntent(ContentUris.withAppendedId(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(mIDColumns))))
							.build();
					addedCards.add((int)c.getLong(mIDColumns));
					sLastCard.add((int)c.getLong(mIDColumns));
					mNotificationManager.notify((int)c.getLong(mIDColumns), notification);
					count++;
				} catch (IOException e) {
					Log.e(TAG, "Unable to update recommendation", e);
				}
			}
			for(int i : sLastCard){
				if(!addedCards.contains(i))
					mNotificationManager.cancel(i);
			}
			mLastCount = total;
		} catch(SQLiteException ignored){
		} finally {
			if (c != null)
				c.close();
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		mNotificationManager =(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		update();
}
	public class RecommendationBuilder {
		private Bitmap mImageUri;
		private String mDescription;
		private String mTitle;
		private int mSmallIcon;
		private PendingIntent mIntent;
		private Uri mUri;
		private Context ct;
		private int mPriority;
		private int mMax = -1;
		private int mProgress = -1;

		public RecommendationBuilder setTitle(String title) {
			mTitle = title;
			return this;
		}

		public RecommendationBuilder setContext(Context applicationContext) {
			ct = applicationContext;
			return this;
		}

		public RecommendationBuilder setDescription(String description) {
			mDescription = description;
			return this;
		}
		public RecommendationBuilder setVideoUri(Uri video){
			mUri = video;
			return this;
		}
		public RecommendationBuilder setImage(Bitmap uri) {
			mImageUri = uri;
			return this;
		}
		public RecommendationBuilder setSmallIcon(int uri) {
			mSmallIcon = uri;
			return this;
		}
		public RecommendationBuilder setIntent(PendingIntent intent){
			mIntent = intent;
			return this;

		}
		public RecommendationBuilder setPriority(int priority){
			mPriority = priority;
			return this;
		}

		private static final String notifChannelId = "UpdateRecommendationsService_id";
		private static final String notifChannelName = "UpdateRecommendationsService";
		private static final String notifChannelDescr = "UpdateRecommendationsService";
		public Notification build() throws IOException {
			// Create the NotificationChannel, but only on API 26+ because the NotificationChannel class is new and not in the support library
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				NotificationChannel mNotifChannel = new NotificationChannel(notifChannelId, notifChannelName,
						mNotificationManager.IMPORTANCE_LOW);
				mNotifChannel.setDescription(notifChannelDescr);
				if (mNotificationManager != null)
					mNotificationManager.createNotificationChannel(mNotifChannel);
			}
			NotificationCompat.Builder b = new NotificationCompat.Builder(ct, notifChannelId)
					.setContentTitle(mTitle)
					.setContentText(mDescription)
					.setLocalOnly(true)
					.setOngoing(true)
					.setPriority(mPriority)
					.setOnlyAlertOnce(true)
					.setColor(UpdateRecommendationsService.this.getResources().getColor(R.color.lightblue800))
					.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
					.setLargeIcon(mImageUri)
					.setSmallIcon(mSmallIcon)
					//.setWhen(System.currentTimeMillis())
					.setContentIntent(mIntent)
					.setExtras(null);
			if(mProgress!=-1&&mMax!=-1)
				b.setProgress(mMax, mProgress,false);
			Notification notification = new NotificationCompat.BigPictureStyle(b)
			.build();
			return notification;
		}

		public RecommendationBuilder setMax(int max) {
			mMax= max;
			return this;
		}

		public RecommendationBuilder setProgress(int progress) {
			mProgress = progress;
			return this;
		}
	}
	private PendingIntent buildPendingIntent(Uri video) {
		Intent detailsIntent = new Intent(Intent.ACTION_VIEW);
		detailsIntent.setData(video);
		detailsIntent.putExtra(PlayerActivity.RESUME, PlayerActivity.RESUME_FROM_LAST_POS);
		detailsIntent.setClass(this, PlayerActivity.class);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(PlayerActivity.class);
		stackBuilder.addNextIntent(detailsIntent);
		// Ensure a unique PendingIntents, otherwise all
		// recommendations end up with the same PendingIntent
		PendingIntent intent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		return intent;
	}
}
