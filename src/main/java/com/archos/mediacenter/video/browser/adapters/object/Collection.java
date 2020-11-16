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
import android.util.Log;

import com.archos.mediacenter.video.R;

import java.io.Serializable;

public class Collection extends Base implements Serializable {

    private static final String TAG = "Collection";
    private static final boolean DBG = true;

    private final boolean mIsTraktSeen;
    private final boolean mIsTraktLibrary;

    public String getPlot() {
        return mCollDescription;
    }

    private final String mCollDescription;
    private long mCollId;
    private int mCollCount;
    private int mCollMovieCount;
    private int mCollMovieWatchedCount;
    private String mPosterUri;
    private String mBackdropUri;

    private long mPinned;

    public Collection(long collId, String collName, Uri posterUri, Uri backdropUri, int collCount, int collMovieCount, int collMovieWatchedCount) {
        this(collId, collName, posterUri, backdropUri, collCount, collMovieCount, collMovieWatchedCount, false, false, null, 0);
    }

    public Collection(long collId,
                  String collName,
                  Uri posterUri,
                  Uri backdropUri,
                  int collCount,
                  int collMovieCount,
                  int collMovieWatchedCount,
                  boolean traktSeen,
                  boolean traktLibrary,
                  String collDescription,
                  long pinned) {
        super(collName, posterUri);
        mCollId = collId;
        mCollCount = collCount;
        mCollMovieCount = collMovieCount;
        mCollMovieWatchedCount = collMovieWatchedCount;
        mIsTraktSeen = traktSeen;
        mIsTraktLibrary = traktLibrary;
        mCollDescription = collDescription;
        mPinned = pinned;
        mPosterUri = posterUri.toString();
        mBackdropUri = backdropUri.toString();
        if (DBG) Log.d(TAG, collId + " " + collName + " count " + collCount + "/" + collMovieCount + "/" + collMovieWatchedCount);
    }

    public String getCountString(Context context) {
        return String.format(context.getResources().getQuantityText(R.plurals.Nmovies, mCollMovieCount).toString(), mCollMovieCount);
    }

    public int getCollectionCount() {
        return mCollCount;
    }

    public int getMovieCollectionCount() {
        return mCollMovieCount;
    }

    public int getMovieCollectionWatchedCount() {
        return mCollMovieWatchedCount;
    }

    public boolean allCollectionWatched() {
        return mCollMovieWatchedCount>=mCollMovieCount;
    }

    public boolean isPinned() {
        return mPinned > 0;
    }

    public long getCollectionId() {
        return mCollId;
    }

    public long getTitle() {
        return mCollId;
    }

    public Uri getPosterUri() {
        if(mPosterUri!=null)
            return Uri.parse(mPosterUri);
        else
            return null;
    }

    public Uri getBackdropUri() {
        if(mBackdropUri!=null)
            return Uri.parse(mBackdropUri);
        else
            return null;
    }

    public boolean isWatched() {
        return mCollMovieWatchedCount>=mCollMovieCount;
    }

    public boolean isTraktSeen() {
        return mIsTraktSeen;
    }

    public boolean isTraktLibrary() {
        return mIsTraktLibrary;
    }

}
