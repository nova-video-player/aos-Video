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

package com.archos.mediacenter.video.leanback.scrapping;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.SearchFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.ShadowLessRowPresenter;
import android.support.v17.leanback.widget.SpeechRecognitionCallback;
import android.util.Log;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.ShadowLessListRow;
import com.archos.mediacenter.video.leanback.adapter.object.EmptyView;
import com.archos.mediacenter.video.leanback.presenter.EmptyViewPresenter;
import com.archos.mediacenter.video.leanback.presenter.ScraperBaseTagsPresenter;
import com.archos.mediacenter.video.leanback.presenter.ScraperSearchResultPresenter;
import com.archos.mediacenter.video.utils.SerialExecutor;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.SearchResult;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Created by vapillon on 11/06/15.
 */
public abstract class ManualScrappingSearchFragment extends SearchFragment implements SearchFragment.SearchResultProvider {

    private static final String TAG = "ManualScrappingSF";

    protected static final int SEARCH_RESULT_MAX_ITEMS = 10;

    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mResultsAdapter;

    Scraper mScraper;
    private List<SearchResult> mSearchResults;

    AsyncTask mSearchTask;
    AsyncTask mDetailsTask;
    private BaseTags mNfoTags;
    private int mOffset;
    private NfoTask mNfoTask;

    private static final int SEARCH_REQUEST_CODE = 1;
    private SpeechRecognitionCallback mSpeechRecognitionCallback;

    /*
        Execute in our own serial executor :
        avoid search task be stuck because of another asynctask (for example file info async task)
        but still don't run two searchtask in parallel
     */
    private Executor mSearchExecutor;


    /**
     * Do the actual search
     * @param text to search for
     * @return
     */
    abstract protected ScrapeSearchResult performSearch(String text);

    /**
     * Get full scraper tags for a given search result
     * @param result
     * @return
     */
    abstract protected BaseTags getTagFromSearchResult(SearchResult result);

    /**
     * Update MediaDB and ScraperDB when a description is chosen and finish the activity
     * NOTE: the finish() must be handled in this method because the save maybe long and asynchronous (hence it can't be done by the caller)
     * @param tags
     */
    abstract protected void saveTagsAndFinish(BaseTags tags);

    /**
     * @return the message to display above the results
     */
    abstract protected String getResultsHeaderText();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mScraper = new Scraper(getActivity());
        mSearchExecutor = new SerialExecutor();

        ClassPresenterSelector rowsPresenterSelector = new ClassPresenterSelector();
        rowsPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        rowsPresenterSelector.addClassPresenter(ShadowLessListRow.class, new ShadowLessRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowsPresenterSelector);

        setBadgeDrawable(getResources().getDrawable(R.mipmap.video2));
        setSearchResultProvider(this);
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof BaseTags) {
                    saveTagsAndFinish((BaseTags) item);
                }
            }
        });
        mSpeechRecognitionCallback = new SpeechRecognitionCallback() {

            @Override
            public void recognizeSpeech() {

                // ACTION_RECOGNIZE_SPEECH
                try{
                    startActivityForResult(getRecognizerIntent(), SEARCH_REQUEST_CODE);
                }catch (ActivityNotFoundException e){/*non google device*/}
            }
        };
        setSpeechRecognitionCallback(mSpeechRecognitionCallback);
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
        mNfoTask = new NfoTask();
        mNfoTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        Log.d(TAG, "getResultsAdapter()");
        return mRowsAdapter;
    }
    @Override
    public boolean onQueryTextChange(String newQuery) {
        return onQueryText(newQuery);
    }
    @Override
    public boolean onQueryTextSubmit(String query) {
        return onQueryText(query);
    }

    private boolean onQueryText(String text) {
        Log.d(TAG, "onQueryText() "+text);

        // Makes no sens to search for one character
        if(text.length()==0){
            mSearchResults = null;
            updateRow();
        }
        if (text.length()<2) {
            return false;
        }

        if (mSearchTask!=null) {
            mSearchTask.cancel(true);
        }
        if (mDetailsTask!=null) {
            mDetailsTask.cancel(true);
        }

        mSearchTask = new ScraperSearchTask().executeOnExecutor(mSearchExecutor,text);
        return true;
    }

    public void onStop(){
        super.onStop();
        mNfoTask.cancel(true);
    }

    protected abstract BaseTags getNfoTags();


    private final class NfoTask extends AsyncTask<String, Void, Void> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected Void doInBackground(String... strings) {
            mNfoTags = getNfoTags();
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if(isDetached()||isCancelled())
                return;
            updateRow();
        }
    }


    private void updateRow(){
        if ((mSearchResults==null||mSearchResults.isEmpty())&&mNfoTags==null) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(new EmptyViewPresenter());
            listRowAdapter.add(new EmptyView(getEmptyText()));
            mRowsAdapter.clear();
            mRowsAdapter.add(new ShadowLessListRow(new HeaderItem(getString(R.string.search_results)), listRowAdapter));
        }
        else {
            // Prepare the result list widget
            ClassPresenterSelector classPresenter = new ClassPresenterSelector();
            classPresenter.addClassPresenter(SearchResult.class, new ScraperSearchResultPresenter()); // for initial search result
            classPresenter.addClassPresenter(BaseTags.class, new ScraperBaseTagsPresenter()); // for detailed result info


            mResultsAdapter = new ArrayObjectAdapter(classPresenter);
            if(mNfoTags!=null) {
                mResultsAdapter.add(0, mNfoTags);
                mOffset=1;
            }
            if(mSearchResults!=null)
                mResultsAdapter.addAll(mOffset, mSearchResults);
            ListRow listRow = new ListRow(new HeaderItem(getResultsHeaderText()), mResultsAdapter);

            mRowsAdapter.clear();
            mRowsAdapter.add(listRow);
            if(mSearchResults!=null) {
                // Launch the details task to get posters, etc.
                SearchResult[] array = mSearchResults.toArray(new SearchResult[mSearchResults.size()]);
                mDetailsTask = new ScraperDetailsFetchTask().executeOnExecutor(mSearchExecutor,array);
            }
        }
    }

    protected abstract String getEmptyText();

    /**
     * This task retrieves the possible matches from the online database for the selected video
     */
    private final class ScraperSearchTask extends AsyncTask<String, Void, ScrapeSearchResult> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected ScrapeSearchResult doInBackground(String... strings) {
            String search = strings[0];
            return performSearch(search);
        }

        @Override
        protected void onPostExecute(ScrapeSearchResult scrapeSearchResult) {
            mSearchResults = scrapeSearchResult.results;
            if(isDetached()||isCancelled()||!isAdded())
                return;
            updateRow();
        }
    }

    static class SearchResultDetails {
        final int mPosition;
        final BaseTags mDetails;

        SearchResultDetails(int position, BaseTags details) {
            mPosition = position;
            mDetails = details;
        }
    }

    /**
     * This task get the details (poster) for the search results from ScraperSearchTask
     */
    private final class ScraperDetailsFetchTask extends AsyncTask<SearchResult, SearchResultDetails, Void> {
        @Override
        protected Void doInBackground(SearchResult... searchResults) {
            int n=mOffset;
            for (SearchResult result : searchResults) {
                if (isCancelled()) {
                    break;
                }
                publishProgress(new SearchResultDetails(n, getTagFromSearchResult(result)));
                n++;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(SearchResultDetails... values) {
            if(values[0].mPosition<mResultsAdapter.size())
                mResultsAdapter.replace(values[0].mPosition, values[0].mDetails);
            else
                mResultsAdapter.add(values[0].mPosition, values[0].mDetails);

        }
    }
}
