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


import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.cover.Cover;
import com.archos.mediacenter.cover.LibraryUtils;
import com.archos.mediacenter.cover.SingleCursorCoverProvider;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class AllTVShowsProvider extends SingleCursorCoverProvider {

	private final static String TAG = "AllTVShowsProvider";
	private final static boolean DBG = false;

	public AllTVShowsProvider( Context context ) {
		super (context);
	}

    /**
     *  Get the cursor loader for this provider
     */
    protected CursorLoader getCursorLoader() {
        return new AllTvshowsLoader(mContext);
    }

    protected int getLoaderManagerId() {
        return VIDEO_ALL_TV_SHOWS_LOADER_ID;
    }

    protected Collection<Cover> convertCursorToCovers(Cursor c, boolean update) {
        if(DBG) Log.d(TAG, "convertCursorToCovers()");
        final int mIdidx = c.getColumnIndexOrThrow(BaseColumns._ID);
        final int mNameIdx = c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_TITLE);
        final int mPosterIdx = c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_S_COVER);
        final int seasonNumberIdx = c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON);
        final int anyEpisodeFilePathIdx = c.getColumnIndexOrThrow(VideoStore.Video.VideoColumns.DATA);
        final int epCountIdx = c.getColumnIndexOrThrow(AllTvshowsLoader.COLUMN_EPISODE_COUNT);
        final int seasCountIdx = c.getColumnIndexOrThrow(AllTvshowsLoader.COLUMN_SEASON_COUNT);

        //keep a reference to the previous Cover Map, to keep track of covers that are not in the new cursor anymore, to recycle them
        HashMap<String, Cover> newCoverIdMap = new HashMap<String, Cover>();

        // Allocate new array (we don't have to keep the previous one, update is handled using the old CoverMap instead
        mCoverArray = new ArrayList<Cover>(c.getCount());
        c.moveToPosition(-1); // make sure position is before first
        while (c.moveToNext()) {
            final long id = c.getLong(mIdidx);
            final int numberOfEpisodes = c.getInt(epCountIdx);
            final String name = c.getString(mNameIdx);
            final String poster = c.getString(mPosterIdx);
            final int seasonCount = c.getInt(seasCountIdx);
            final int seasonNumber = c.getInt(seasonNumberIdx);
            final String anyEpisodeFilePath = c.getString(anyEpisodeFilePathIdx);
            Cover cover = null;
            // Update case
            if (update) {
                // For the TVShow covers there is a special method to compute the CoverID. It integrates the number of episodes,
                // so that the cover is updated in case it is changed (only the description texture should be updated tough...)
                final String coverID = TvShowCover.computeCoverIDwithNumberOfEpisode(id, numberOfEpisodes);
                if (DBG) Log.d(TAG, "convertCursorToCovers update: coverID="+coverID);
                // Is it in the previous cover collection already?
                cover = mCoverIdMap.get(coverID);
                if (cover!=null) {
                    if (DBG) Log.d(TAG, "convertCursorToCovers update: found "+coverID+" in previous covers");
                    // remove in the old cover map. Will allow to keep track of the non-recycled covers remaining at the end
                    mCoverIdMap.remove(coverID);
                } else {
                    if (DBG) Log.d(TAG, "convertCursorToCovers update: "+coverID+" not found in previous covers");
                }
            }
            if (cover==null) {
                // Create a new one
                cover = new TvShowCover(id, name, numberOfEpisodes, poster, seasonCount, seasonNumber, anyEpisodeFilePath);
            }
            mCoverArray.add(cover);
            if (DBG) Log.d(TAG, "convertCursorToCovers: put coverID="+cover.getCoverID());
            newCoverIdMap.put(cover.getCoverID(), cover);
        }

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
            mErrorMessage = mContext.getResources().getString(R.string.you_have_no_tv_shows);
            mErrorMessage += "\n\n" + mContext.getResources().getString(R.string.scraper_suggest_message);
            Log.e(TAG,"Error: " + mErrorMessage);
        }
        return coversToRecycle;
    }
}
