// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.browser.adapters.object;

import android.content.Context;
import android.net.Uri;

import com.archos.mediacenter.video.R;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.TagsFactory;

import java.io.Serializable;

public class Collection extends Base implements Serializable {

    private final boolean mIsTraktSeen;
    private final boolean mIsTraktLibrary;

    public String getPlot() {
        return mCollDescription;
    }

    private final String mCollDescription;
    private long mCollId;
    private int mCollMovieCount;
    private int mCollMovieWatchedCount;

    private long mPinned;

    // TODO MARC: stored in movietags but perhaps collectiontags?
    /**
     * Not computed by this class but only a place to store it.
     * Need to be set with setShowTags()
     */
    private ShowTags mShowTags;

    public Collection(long collId, String collName, Uri posterUri, int collMovieCount, int collMovieWatchedCount) {
        this(collId, collName, posterUri, collMovieCount, collMovieWatchedCount, false, false, null, 0);
    }

    public Collection(long collId,
                  String collName,
                  Uri posterUri,
                  int collMovieCount,
                  int collMovieWatchedCount,
                  boolean traktSeen,
                  boolean traktLibrary,
                  String collDescription,
                  long pinned) {
        super(collName, posterUri);
        mCollId = collId;
        mCollMovieCount = collMovieCount;
        mCollMovieWatchedCount = collMovieWatchedCount;
        mIsTraktSeen = traktSeen;
        mIsTraktLibrary = traktLibrary;
        mCollDescription = collDescription;
        mPinned = pinned;
    }

    public String getCountString(Context context) {
        return String.format(context.getResources().getQuantityText(R.plurals.Nseasons, mCollMovieCount).toString(), mCollMovieCount);
    }

    public int getCollectionMovieCount() {
        return mCollMovieCount;
    }

    public int getCollectionMovieWatchedCount() {
        return mCollMovieWatchedCount;
    }

    public boolean isWatched() {
        return mCollMovieWatchedCount>=mCollMovieCount;
    }

    public boolean isPinned() {
        return mPinned > 0;
    }

    public long getCollectionId() {
        return mCollId;
    }

    // TODO MARC: buildCollectionTags and rework scraping
    @Override
    public BaseTags getFullScraperTags(Context context) {
        return TagsFactory.buildShowTags(context, mCollId);
    }

    public void setShowTags(ShowTags showTags) {
        mShowTags = showTags;
    }

    /**
     *
     * @return null if you did not set the show tags with @link:setShowTags before
     */
    public ShowTags getShowTags() {
        return mShowTags;
    }

    public boolean isTraktSeen() {
        return mIsTraktSeen;
    }

    public boolean isTraktLibrary() {
        return mIsTraktLibrary;
    }

}
