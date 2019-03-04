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

import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.SearchFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;

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
import android.support.v17.leanback.widget.ShadowLessRowPresenter;
import android.support.v17.leanback.widget.SpeechRecognitionCallback;


public class VideoSearchFragment extends SearchFragment implements SearchFragment.SearchResultProvider {
    public static final int ROW_ID = 2000;

    private static final int SEARCH_DELAY_MS = 300;

    private ArrayObjectAdapter mRowsAdapter;
    private Handler mHandler = new Handler();
    private SearchRunnable mDelayedLoad;
    private VideoLoader mSearchLoader;
    private SpeechRecognitionCallback mSpeechRecognitionCallback;
    private String mLastQuery;
    private static final int SEARCH_REQUEST_CODE = 1;

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

        ClassPresenterSelector rowsPresenterSelector = new ClassPresenterSelector();
        rowsPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        rowsPresenterSelector.addClassPresenter(ShadowLessListRow.class, new ShadowLessRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowsPresenterSelector);

        mSpeechRecognitionCallback = new SpeechRecognitionCallback() {

            @Override
            public void recognizeSpeech() {

                // ACTION_RECOGNIZE_SPEECH
                try {
                    startActivityForResult(getRecognizerIntent(), SEARCH_REQUEST_CODE);
                }catch (android.content.ActivityNotFoundException e){
                    //no google device
                }
            }
        };
        setSpeechRecognitionCallback(mSpeechRecognitionCallback);

        setSearchResultProvider(this);
        setOnItemViewClickedListener(new VideoViewClickedListener(getActivity()));
        setBadgeDrawable(getResources().getDrawable(R.mipmap.video2));
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
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode==SEARCH_REQUEST_CODE&&data!=null){
                setSearchQuery(data, true);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Resources r = getResources();
        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        bgMngr.attach(getActivity().getWindow());
        bgMngr.setColor(r.getColor(R.color.leanback_background));
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
        new AsyncTask<String, Void, ListRow>() {
            @Override
            protected void onPreExecute() {
                mRowsAdapter.clear();
            }

            @Override
            protected ListRow doInBackground(String... params) {
                ContentResolver cr = getActivity().getContentResolver();
                if (mSearchLoader instanceof SearchMovieLoader) {
                    ((SearchMovieLoader)mSearchLoader).setQuery(query);
                } else if (mSearchLoader instanceof SearchEpisodeLoader) {
                    ((SearchEpisodeLoader)mSearchLoader).setQuery(query);
                } else if (mSearchLoader instanceof SearchNonScrapedVideoLoader) {
                        ((SearchNonScrapedVideoLoader)mSearchLoader).setQuery(query);
                } else {
                    ((SearchVideoLoader)mSearchLoader).setQuery(query);
                }
                Cursor cursor = cr.query(mSearchLoader.getUri(), mSearchLoader.getProjection(), mSearchLoader.getSelection(), mSearchLoader.getSelectionArgs(), mSearchLoader.getSortOrder());
                if (cursor.getCount() > 0) {
                    CursorObjectAdapter listRowAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity()));
                    listRowAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
                    listRowAdapter.changeCursor(cursor);
                    return new ListRow(ROW_ID, new HeaderItem(getString(R.string.search_results)), listRowAdapter);
                } else {
                    ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new EmptyViewPresenter());
                    listRowAdapter.add(new EmptyView(getString(R.string.no_results_found)));
                    return new ShadowLessListRow(new HeaderItem(getString(R.string.search_results)), listRowAdapter);
                }
            }

            @Override
            protected void onPostExecute(ListRow listRow) {
                mRowsAdapter.add(listRow);
            }
        }.execute();
    }

}
