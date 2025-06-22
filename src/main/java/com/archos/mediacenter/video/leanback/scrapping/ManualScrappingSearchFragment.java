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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.SearchSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.ShadowLessRowPresenter;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Created by vapillon on 11/06/15.
 */
public abstract class ManualScrappingSearchFragment extends SearchSupportFragment implements SearchSupportFragment.SearchResultProvider {

    private static final Logger log = LoggerFactory.getLogger(ManualScrappingSearchFragment.class);

    protected static final int SEARCH_RESULT_MAX_ITEMS = 10;

    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mResultsAdapter;

    Scraper mScraper;
    private List<SearchResult> mSearchResults;

    private BaseTags mNfoTags;
    private int mOffset;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Runnable mSearchRunnable;
    private Runnable mDetailsRunnable;
    private volatile boolean mSearchCancelled = false;
    private volatile boolean mDetailsCancelled = false;
    private volatile boolean mNfoCancelled = false;
    private ActivityResultLauncher<Intent> mSearchResultLauncher;

    private static final int SEARCH_REQUEST_CODE = 1;

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

        mSearchResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() == Activity.RESULT_OK && data != null) {
                        setSearchQuery(data, true);
                    }
                }
        );

        mScraper = new Scraper(getActivity());
        mSearchExecutor = new SerialExecutor();

        ClassPresenterSelector rowsPresenterSelector = new ClassPresenterSelector();
        rowsPresenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        rowsPresenterSelector.addClassPresenter(ShadowLessListRow.class, new ShadowLessRowPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowsPresenterSelector);

        setBadgeDrawable(ContextCompat.getDrawable(getActivity(), R.mipmap.nova));
        setSearchResultProvider(this);
        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof BaseTags) {
                    saveTagsAndFinish((BaseTags) item);
                }
            }
        });
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Resources r = getResources();
        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        bgMngr.attach(getActivity().getWindow());
        bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.leanback_background));
        performNfoTask();
    }

    @Override
    public ObjectAdapter getResultsAdapter() {
        log.debug("getResultsAdapter()");
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
        log.debug("onQueryText() "+text);

        // Makes no sens to search for one character
        if(text.length()==0){
            mSearchResults = null;
            updateRow();
        }
        if (text.length()<2) {
            return false;
        }

        cancelSearchTask();
        cancelDetailsTask();

        performSearchTask(text);
        return true;
    }

    public void onStop(){
        super.onStop();
        mNfoCancelled = true;
    }

    protected abstract BaseTags getNfoTags();

    private void performNfoTask() {
        mNfoCancelled = false;
        mSearchExecutor.execute(() -> {
            mNfoTags = getNfoTags();
            mMainHandler.post(() -> {
                if (isDetached() || mNfoCancelled) return;
                updateRow();
            });
        });
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
            // Do not add all the show search results to check if season poster exists
            /*
            if(mSearchResults!=null)
                mResultsAdapter.addAll(mOffset, mSearchResults);
             */
            ListRow listRow = new ListRow(new HeaderItem(getResultsHeaderText()), mResultsAdapter);

            mRowsAdapter.clear();
            mRowsAdapter.add(listRow);
            if(mSearchResults!=null) {
                // Launch the details task to get posters, etc.
                performDetailsTask(mSearchResults);
            }
        }
    }

    protected abstract String getEmptyText();

    /**
     * This task retrieves the possible matches from the online database for the selected video
     */
    private void performSearchTask(final String text) {
        mSearchCancelled = false;
        mSearchRunnable = () -> {
            if (mSearchCancelled) return;
            final ScrapeSearchResult result = performSearch(text);
            mMainHandler.post(() -> {
                if (mSearchCancelled) return;
                mSearchResults = result.results;
                if (isDetached() || !isAdded()) return;
                updateRow();
            });
        };
        mSearchExecutor.execute(mSearchRunnable);
    }

    private void cancelSearchTask() {
        mSearchCancelled = true;
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
    private void performDetailsTask(final List<SearchResult> searchResults) {
        mDetailsCancelled = false;
        mDetailsRunnable = () -> {
            int n = mOffset;
            for (SearchResult result : searchResults) {
                if (mDetailsCancelled) break;
                final BaseTags details = getTagFromSearchResult(result);
                final int position = n++;
                mMainHandler.post(() -> {
                    if (details != null && details.getDefaultPoster() != null) {
                        mResultsAdapter.add(mResultsAdapter.size(), details);
                    }
                });
            }
        };
        mSearchExecutor.execute(mDetailsRunnable);
    }

    private void cancelDetailsTask() {
        mDetailsCancelled = true;
    }
}
