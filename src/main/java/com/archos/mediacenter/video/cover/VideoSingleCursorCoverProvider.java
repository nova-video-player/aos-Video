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

package com.archos.mediacenter.video.cover;

import com.archos.mediacenter.cover.*;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediaprovider.video.VideoStore;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public abstract class VideoSingleCursorCoverProvider extends SingleCursorCoverProvider {

	private final static String TAG = "VideoSingleCursorCoverProvider";
	private final static boolean DBG = false;

	protected final static int MAXIMUM_NB_OF_VIDEOS = 500; // play it safe for now... see #5919

    protected int mErrorMessageId = R.string.you_have_no_video; // default value

	public VideoSingleCursorCoverProvider( Context context ) {
		super (context);
	}

	protected Collection<Cover> convertCursorToCovers(Cursor c, boolean update) {
		if(DBG) Log.d(TAG, "convertCursorToCovers()");
		final int mDurationIdx = c.getColumnIndexOrThrow(VideoStore.Video.Media.DURATION);
		final int mVideoIDidx = c.getColumnIndexOrThrow(VideoStore.Video.Media._ID);
		final int mFilePathIdx = c.getColumnIndexOrThrow(VideoStore.Video.Media.DATA);
		final int mScraperTypeIdx = c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE);
		final int mScraperIdIdx = c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID);
		final int mTitleIdx = c.getColumnIndexOrThrow(VideoLoader.COLUMN_NAME);

		//keep a reference to the previous Cover Map, to keep track of covers that are not in the new cursor anymore, to recycle them
		HashMap<String, Cover> newCoverIdMap = new HashMap<String, Cover>();

		// Convert Cursor into Cover array
		mCoverArray = new ArrayList<Cover>(c.getCount());
		c.moveToFirst();

		while (!c.isAfterLast()) {
			final long videoID = c.getLong(mVideoIDidx);
			final long scraperID = c.getLong(mScraperIdIdx);
			final long scraperType = c.getLong(mScraperTypeIdx);
			Cover cover = null;

			// Update case
			if (update) {
				final String coverID;
				if (scraperID > 0) {
					if (scraperType == com.archos.mediascraper.BaseTags.MOVIE) {
						coverID = MovieCover.computeCoverID(videoID);
					} else if (scraperType == com.archos.mediascraper.BaseTags.TV_SHOW) {
						coverID = EpisodeCover.computeCoverID(videoID);
					} else {
						throw new IllegalArgumentException("Unexpected scraper type!: "+scraperType);
					}
				} else {
					coverID = VideoCover.computeCoverID(videoID);
				}
				// Is it in the previous cover collection already?
				cover = mCoverIdMap.get(coverID);
				if (cover!=null) {
					if(DBG) Log.d(TAG, "Found old cover for "+coverID);
					// remove in the old cover map. Will allow to keep track of the non-recycled covers remaining at the end
					mCoverIdMap.remove(coverID);
				}
			}
			if (cover==null) {
				if (scraperID > 0) {
					if (scraperType == com.archos.mediascraper.BaseTags.MOVIE) {
						if(DBG) Log.d(TAG, "Create new cover for "+MovieCover.computeCoverID(videoID));
						// Create a new one
						cover = new MovieCover(
								videoID,
								c.getString(mFilePathIdx),
								c.getLong(mDurationIdx),
								c.getLong(mScraperIdIdx));
					} else if (scraperType == com.archos.mediascraper.BaseTags.TV_SHOW) {
						if(DBG) Log.d(TAG, "Create new cover for "+EpisodeCover.computeCoverID(videoID));
						// Create a new one
						cover = new EpisodeCover(
								videoID,
								c.getString(mFilePathIdx),
								c.getLong(mDurationIdx),
								c.getLong(mScraperIdIdx));
					} else {
						throw new IllegalArgumentException("Unexpected scraper type!: "+scraperType);
					}

				}
				else {
					if(DBG) Log.d(TAG, "Create new cover for "+VideoCover.computeCoverID(videoID));
					// Create a new one
					cover = new VideoCover(
							videoID,
							c.getString(mFilePathIdx),
							c.getLong(mDurationIdx),
							c.getString(mTitleIdx));
				}
			}
			mCoverArray.add(cover);
			newCoverIdMap.put(cover.getCoverID(), cover);
			c.moveToNext();
		}

		if(DBG) Log.d(TAG,"Cursor count = "+c.getCount());

		// Must not close the cursor here. It is handled at CoverProvider level.

		// Return the collection of covers to recycle
		Collection<Cover> coversToRecycle = null;
		if (update) {
			coversToRecycle = mCoverIdMap.values();
		}

		// Update the CoverMap: old = new, now
		mCoverIdMap = newCoverIdMap;

		// Sanity check
		if (mCoverArray.size() == 0) {
			setErrorMessage();
			Log.e(TAG,"Error: " + mErrorMessage);
		}
		return coversToRecycle;
	}

	abstract void setErrorMessage();
}
