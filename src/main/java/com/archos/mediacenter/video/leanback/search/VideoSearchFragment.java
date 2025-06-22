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

package com.archos.mediacenter.video.leanback.search;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.ShadowLessListRow;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.adapter.object.EmptyView;
import com.archos.mediacenter.video.browser.loader.SearchEpisodeLoader;
import com.archos.mediacenter.video.browser.loader.SearchMovieLoader;
import com.archos.mediacenter.video.browser.loader.SearchNonScrapedVideoLoader;
import com.archos.mediacenter.video.browser.loader.SearchVideoLoader;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediacenter.video.leanback.presenter.EmptyViewPresenter;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import androidx.leanback.widget.ShadowLessRowPresenter;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class VideoSearchFragment extends SearchSupportFragment implements SearchSupportFragment.SearchResultProvider {

    static final String TAG = "VideoSearchFragment";
    public static final int ROW_ID = 2000;
    private static final int SEARCH_DELAY_MS = 300;

    private ArrayObjectAdapter mRowsAdapter;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private SearchRunnable mDelayedLoad;
    private VideoLoader mSearchLoader;
    private String mLastQuery;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<Intent> mSearchResultLauncher;

    private class SearchRunnable implements Runnable {
        private String mQuery;
        public void setSearchQuery(String query) {
            mQuery = query;
        }
        @Override
        public void run() {
            loadRows(mQuery);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSearchResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                        setSearchQuery(data, true);
                    }
                }
        );

        ClassPresenterSelector rowsPresenterSelector = new ClassPresenterSelector();
        rowsPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        rowsPresenterSelector.addClassPresenter(ShadowLessListRow.class, new ShadowLessRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowsPresenterSelector);

        setSearchResultProvider(this);
        setOnItemViewClickedListener(new VideoViewClickedListener(getActivity()));
        setBadgeDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.nova));
        mDelayedLoad = new SearchRunnable();

        int searchMode = getArguments() != null ? getArguments().getInt(VideoSearchActivity.EXTRA_SEARCH_MODE, VideoSearchActivity.SEARCH_MODE_ALL) : VideoSearchActivity.SEARCH_MODE_ALL;
        if (searchMode == VideoSearchActivity.SEARCH_MODE_MOVIE) {
            setTitle(getString(R.string.movies));
            mSearchLoader = new SearchMovieLoader(getActivity());
        } else if (searchMode == VideoSearchActivity.SEARCH_MODE_EPISODE) {
            setTitle(getString(R.string.all_tv_shows));
            mSearchLoader = new SearchEpisodeLoader(getActivity());
        } else if (searchMode == VideoSearchActivity.SEARCH_MODE_NON_SCRAPED) {
            setTitle(getString(R.string.non_scraped_videos));
            mSearchLoader = new SearchNonScrapedVideoLoader(getActivity());
        } else {
            setTitle(getString(R.string.videos));
            mSearchLoader = new SearchVideoLoader(getActivity());
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Resources r = getResources();
        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        try {
            bgMngr.attach(getActivity().getWindow());
        } catch (IllegalStateException e) {
            // BackgroundManager.attach() can throw IllegalStateException if the window is not ready yet
            Log.w(TAG, "BackgroundManager.attach() failed, retrying later", e);
            // We will set the background color later in onActivityCreated()
        }
        bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.leanback_background));
    }


    @Override
    public ObjectAdapter getResultsAdapter() {
        return mRowsAdapter;
    }

    @Override
    public boolean onQueryTextChange(String query) {
        loadQuery(query, query.length() > 1);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        loadQuery(query, !query.isEmpty());
        return true;
    }

    private void loadQuery(String query, boolean valid) {
        if (!query.equals(mLastQuery)) {
            mLastQuery = query;

            mHandler.removeCallbacks(mDelayedLoad);
            if (valid) {
                mDelayedLoad.setSearchQuery(query);
                mHandler.postDelayed(mDelayedLoad, SEARCH_DELAY_MS);
            }
        }
    }

    private void loadRows(final String query) {
        mRowsAdapter.clear();
        mExecutor.execute(() -> {
            ListRow listRow = null;
            if (getActivity() != null && !getActivity().isFinishing()) {
                ContentResolver cr = getActivity().getContentResolver();
                if (mSearchLoader instanceof SearchMovieLoader) {
                    ((SearchMovieLoader) mSearchLoader).setQuery(query);
                } else if (mSearchLoader instanceof SearchEpisodeLoader) {
                    ((SearchEpisodeLoader) mSearchLoader).setQuery(query);
                } else if (mSearchLoader instanceof SearchNonScrapedVideoLoader) {
                    ((SearchNonScrapedVideoLoader) mSearchLoader).setQuery(query);
                } else {
                    ((SearchVideoLoader) mSearchLoader).setQuery(query);
                }
                Cursor cursor = cr.query(mSearchLoader.getUri(), mSearchLoader.getProjection(), mSearchLoader.getSelection(), mSearchLoader.getSelectionArgs(), mSearchLoader.getSortOrder());
                if (cursor != null && cursor.getCount() > 0) {
                    CursorObjectAdapter listRowAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
                    listRowAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
                    listRowAdapter.changeCursor(cursor);
                    listRow = new ListRow(ROW_ID, new HeaderItem(getString(R.string.search_results)), listRowAdapter);
                } else {
                    ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new EmptyViewPresenter());
                    listRowAdapter.add(new EmptyView(getString(R.string.no_results_found)));
                    listRow = new ShadowLessListRow(new HeaderItem(getString(R.string.search_results)), listRowAdapter);
                }
                if (cursor != null) cursor.close();
            } else {
                Log.e(TAG, "loadRows: no more activity, aborting search");
            }
            final ListRow finalListRow = listRow;
            mMainHandler.post(() -> {
                if (finalListRow != null) mRowsAdapter.add(finalListRow);
            });
        });
    }

}
