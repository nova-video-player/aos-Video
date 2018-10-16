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

package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SectionIndexer;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserMoviesBy;
import com.archos.mediacenter.video.browser.ThumbnailAdapterVideo;
import com.archos.mediacenter.video.browser.ThumbnailRequestVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.presenter.MovieByPresenter;
import com.archos.mediacenter.video.utils.VideoUtils;

/**
 * Created by alexandre on 27/10/15.
 */
public class GroupOfMovieAdapter extends CursorAdapter implements SectionIndexer,AdapterByVideoObjectsInterface, ThumbnailAdapterVideo {

    private int mNameColumnIdx;
    private static final String TAG  = "GroupOfMovieAdapter";
    private int mListOfMovieIdsColumnIdx;
    private int mNumberOfMovieIdsColumnIdx;
    private final LayoutInflater mInflater;
    //private final int mDefaultIconsColor;
    private final SparseArray<String> mIndexer;
    private int mViewMode;
    private String[] mSections;
    private ThumbnailEngine mThumbnailEngine;
    private MovieByPresenter mPresenter;

    @Override
    public Video getVideoItem(int position) {
        return null;
    }



    public GroupOfMovieAdapter(Context context, ThumbnailEngine thumbnailEngine, Cursor cursor, int viewMode) {
        super(context, cursor, 0);
        mViewMode = viewMode;
        mThumbnailEngine = thumbnailEngine;
        mInflater = LayoutInflater.from(context);
        if (mViewMode == VideoUtils.VIEW_MODE_LIST) {
            mPresenter = new MovieByPresenter(context,AdapterDefaultValuesList.INSTANCE,null);
        } else if (mViewMode == VideoUtils.VIEW_MODE_GRID) {
            mPresenter = new MovieByPresenter(context,AdapterDefaultValuesGrid.INSTANCE,null);
        } else {
            // should not happen, default to grid
            mPresenter = new MovieByPresenter(context,AdapterDefaultValuesGrid.INSTANCE,null);
            Log.d(TAG, "invalid view mode " + mViewMode + "defaulting to VIEW_MODE_GRID");
        }
        //mDefaultIconsColor = mContext.getResources().getColor(R.color.default_icons_color_filter);
        mIndexer = new SparseArray<String>();
        setData(cursor, viewMode);
    }

    public void setData(Cursor cursor, int viewMode) {
        mNameColumnIdx = cursor.getColumnIndex(BrowserMoviesBy.COLUMN_NAME);
        mListOfMovieIdsColumnIdx = cursor.getColumnIndex(BrowserMoviesBy.COLUMN_LIST_OF_MOVIE_IDS);
        mNumberOfMovieIdsColumnIdx = cursor.getColumnIndex(BrowserMoviesBy.COLUMN_NUMBER_OF_MOVIES);
        setSections();

    }

    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        int layoutId = -1;
        return mPresenter.getView(parent, null, null);
    }

    public void bindView(View view, Context context, Cursor cursor) {

        int movieCount = cursor.getInt(mNumberOfMovieIdsColumnIdx);
        String format = context.getResources().getQuantityText(R.plurals.Nmovies, movieCount).toString();
        mPresenter.bindView(view, new Pair(cursor.getString(mNameColumnIdx),String.format(format, movieCount)),mThumbnailEngine.getResultFromPool(getItemId(cursor.getPosition())),cursor.getPosition());


    }

    private void setSections() {
        String previousLetter = "";
        mIndexer.clear();
        if (getCount() > 0) {
            getCursor().moveToFirst();
            do {
                String letter = getCursor().getString(mNameColumnIdx).substring(0, 1);
                if (!letter.equals(previousLetter)) {
                    previousLetter = letter;
                    mIndexer.append(getCursor().getPosition(), letter);
                }
            } while (getCursor().moveToNext());
        }

        int size = mIndexer.size();
        mSections = new String[size];
        for (int i = 0; i < size; i++)
            mSections[i] = mIndexer.valueAt(i);
    }

    public Object[] getSections() {
        return mSections;
    }

    public int getPositionForSection(int sectionIndex) {
        return MediaUtils.getPositionForSection(sectionIndex, mIndexer, getCount(),
                mSections);
    }

    public int getSectionForPosition(int position) {
        return MediaUtils.getSectionForPosition(position, mIndexer, getCount());
    }

    public String getName(int position) {
        String name = null;
        if (getCount() > 0 && position < getCount()) {
            getCursor().moveToPosition(position);
            name = getCursor().getString(mNameColumnIdx);
        }
        return name;
    }

    public String getListOfMoviesIds(int position) {
        String listOfMovieIds = null;
        if (getCount() > 0 && position < getCount()) {
            getCursor().moveToPosition(position);
            listOfMovieIds = getCursor().getString(mListOfMovieIdsColumnIdx);
        }
        return listOfMovieIds;
    }

    @Override
    public boolean doesItemNeedAThumbnail(int position) {
        return true;
    }

    @Override
    public ThumbnailRequestVideo getThumbnailRequest(int position) {
        return BrowserMoviesBy.getMultiposterThumbnailRequest(getCursor(), position, getItemId(position));
    }
}
