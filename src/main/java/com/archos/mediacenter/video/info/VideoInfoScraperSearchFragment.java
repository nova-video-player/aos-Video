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

package com.archos.mediacenter.video.info;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.archos.environment.ArchosSettings;
import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.ScraperResultsAdapter;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class VideoInfoScraperSearchFragment extends Fragment implements  Handler.Callback {

    private static final String TAG = VideoInfoScraperSearchFragment.class.getSimpleName();
    private static final boolean DBG = false;
    
    public static final int SELECTION_DIALOG_MAX_ITEMS = 20;
    
    // Actions to perform when the selection thread is interrupted (powers of 2)
    private static final int ACTION_NONE = 0;
    private static final int ACTION_STOP_SERVICE = (1 << 0);
    private static final int ACTION_VALIDATE_LAST_ITEM = (1 << 1);

    private boolean mSetup = false; // state
    protected String mTitle;

    private boolean mDisableOnlineUpdate; // intent may require no-edit mode

    private TextView mHeaderMessage;
    private TextView mMessage;
    private Button mSearchButton;
    private View mProgressGroup;
    private View mResultsGroup;
    private ListView mList;
    private Button mCancelButton;
    private View mCustomSearchContainer;
    private EditText mCustomSearchEditText;

    private Scraper mScraper;

    private final Handler mHandler = new Handler (this);
    
    // Search thread
    private Thread mResThread;
    private List<SearchResult> mResults;
    private List<BaseTags> mSelectionTags = new ArrayList<BaseTags>();
    private ScraperResultsAdapter mScraperResultsAdapter;
    private int mSelectionItemsProcessed;
    
    // Selection thread
    private ScraperSelectionThread mSelectionThread;
    private int mResIndex;
    
    // Save state on screen rotation
    private SavedState mSavedState;
    private BaseTags mNfoTag;
    private View mView;
    private Uri mUri;

    private static class SavedState {
        int mHeaderMessageVisibility;
        int mMessageVisibility;
        int mCustomSearchContainerVisibility;
        int mProgressGroupVisibility;
        int mResultsVisibility;
    }
    private boolean mHasSaved;

    private SearchInfo mSearchInfo;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mScraper = new Scraper(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mScraper = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	if(DBG) Log.d(TAG, "onCreate this="+this+"  savedInstanceState="+savedInstanceState);
        setInfo((Video) getActivity().getIntent().getExtras().get(VideoInfoScraperActivity.EXTRA_VIDEO));
        setRetainInstance(false); // keep fragment instance when rotating screen
    }

    @Override
    public void onDestroyView() {
        // Device rotation : if the selection thread is running prevent it
        // from updating the activity data until the activity is re-created
        if (mSelectionThread != null && mSelectionThread.isAlive()) {
            mSelectionThread.pause();
        }
        
    	mSavedState = new SavedState();
    	mSavedState.mHeaderMessageVisibility = mHeaderMessage.getVisibility();
    	mSavedState.mMessageVisibility = mMessage.getVisibility();
    	mSavedState.mCustomSearchContainerVisibility = mCustomSearchContainer.getVisibility();
    	mSavedState.mProgressGroupVisibility = mProgressGroup.getVisibility();
    	mSavedState.mResultsVisibility = mResultsGroup.getVisibility();
    	
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
    	super.onActivityCreated(savedInstanceState);
    	// The activity was destroyed while the selection thread was running => now that
        // the data are restored  we can tell the thread that the activity has changed
    	if (mSelectionThread != null) {
            if(DBG) Log.d(TAG, "onActivityCreated: unpause mSelectionThread");
    		mSelectionThread.unpause();
    	}
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.video_info_scraper_search, null);
        return mView;
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

    	mResultsGroup = mView.findViewById(R.id.search_results_group);
    	mList = (ListView)mView.findViewById(R.id.list);

    	// No cancel button in this case
    	mCancelButton = (Button)mView.findViewById(R.id.cancel);
    	mCancelButton.setVisibility(View.GONE);

    	mHeaderMessage = (TextView) mView.findViewById(R.id.header_message);
    	mMessage = (TextView)mView.findViewById(R.id.message);
    	mSearchButton = (Button)mView.findViewById(R.id.search);
    	mProgressGroup = mView.findViewById(R.id.progress_group);
    	
    	mCustomSearchContainer = mView.findViewById(R.id.custom_search_container);
    	mCustomSearchEditText = (EditText) mView.findViewById(R.id.custom_search_edittext);
    	mCustomSearchEditText.setHint(R.string.video_info_custom_search_file_hint);
    	mSearchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                // Close the virtual keyboard if visible
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                // Start searching
				search();
			}
		});
        mCustomSearchEditText.setOnEditorActionListener(mEditorActionListener);

    	// update UI visibility
        if (mSavedState!=null && !mHasSaved) {
        	mHeaderMessage.setVisibility(mSavedState.mHeaderMessageVisibility);
    		mMessage.setVisibility(mSavedState.mMessageVisibility);
        	mCustomSearchContainer.setVisibility(mSavedState.mCustomSearchContainerVisibility);
        	mProgressGroup.setVisibility(mSavedState.mProgressGroupVisibility);
        	mResultsGroup.setVisibility(mSavedState.mResultsVisibility);
        	
        	// Reload list adapter
        	mList.setAdapter(mScraperResultsAdapter);
    	}
    	else {
    		initVisibilities();
            mHasSaved = false;
    	}
    	
    	// search result list click listener
    	mList.setOnItemClickListener(new OnItemClickListener() {
    		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(DBG) Log.d(TAG, "onClick : select item " + position + " (items already processed=" + mSelectionItemsProcessed + ")");

                if (mSelectionThread != null && mSelectionThread.isAlive()) {
                    //----------------------------------------------------------------
                    // The items of the selection dialog are still being processed
                    // => we can stop processing the other items
                    //----------------------------------------------------------------
                    if (position < mSelectionItemsProcessed) {
                        // The user selected an item which is already processed
                        // => the infos for this item are already available so we only
                        // need to stop the thread and the scraper service
                        mSelectionThread.interrupt(ACTION_STOP_SERVICE);

                        // Validate the selected item
                        BaseTags itemTags = mSelectionTags.get(position);
                        mHandler.obtainMessage(MESSAGE_WRITE_TAGS_TO_DB, itemTags).sendToTarget();
                    }
                    else if (position > mSelectionItemsProcessed) {
                        // The user selected an item which has not been processed yet
                        // => stop the processing thread but keep connected to the scraper service
                        // because we must still get the infos for the selected item
                        mSelectionThread.interrupt(ACTION_NONE);

                        // Start the thread which will retrieve the infos for the selected item
                        mResIndex = position;
                        new ScraperDetailsThread().start();
                    }
                    else {
                        // The user selected the item which is currently processed
                        // => stop the processing thread which will finish after
                        // processing the current item so we just need to wait until
                        // it finishes to retrieve the item data
                        mSelectionThread.interrupt(ACTION_STOP_SERVICE | ACTION_VALIDATE_LAST_ITEM);
                    }
                }
                else {
                    //----------------------------------------------------------------
                    // All the items of the selection dialog are already processed
                    // and the service is stopped => just validate the selected item
                    //----------------------------------------------------------------
                    if (position < mSelectionTags.size()) {
                        BaseTags itemTags = mSelectionTags.get(position);
                        mHandler.obtainMessage(MESSAGE_WRITE_TAGS_TO_DB, itemTags).sendToTarget();
                    }
                }
    		}
		});

    	// Check if everything is ready to setup the fragment
    	setupIfReady();
    }
    
    private void initVisibilities() {
    	mHeaderMessage.setVisibility(View.VISIBLE);
		mMessage.setVisibility(View.GONE);
		mCustomSearchContainer.setVisibility( mDisableOnlineUpdate ? View.GONE : View.VISIBLE);
		mProgressGroup.setVisibility(View.GONE);
		mResultsGroup.setVisibility(View.GONE);
    }




    public void setInfo(Video video) {
        mUri = Uri.parse(video.getFilePath());
        if(DBG) Log.d(TAG, "video Uri"+mUri);
        mTitle = video.getName();

        // Check if everything is ready to setup the fragment
        setupIfReady();
    }


    /**
     * setup the UI and start the threads if ready
     */
    void setupIfReady() {
        if ((!mSetup) &&  // not already setup
            (mUri!=null) &&       // file to show is setup
            (mView!=null) &&       // View has been created
            (getActivity()!=null)) // is Attached to an activity
        {
        	if(DBG) Log.d(TAG, "setupIfReady: READY!");

            mSearchInfo = SearchPreprocessor.instance().parseFileBased(mUri, mTitle!=null&&!mTitle.isEmpty()?Uri.parse("/"+mTitle):mUri);
            String searchText = mSearchInfo.getSearchSuggestion();
            mCustomSearchEditText.setText(searchText);
            mCustomSearchEditText.setSelection(searchText.length());

            // Check if the client allows online updates of the info or not
            mDisableOnlineUpdate = false;
            mCustomSearchContainer.setVisibility(mDisableOnlineUpdate ? View.GONE : View.VISIBLE);
        }
        else {
        	if(DBG) Log.d(TAG, "setupIfReady: not ready");
        }
    }

    private void search() {
    	// Make sure we are connected to a network
    	Context context = getActivity();
    	if (!ArchosSettings.isDemoModeActive(context) && !ArchosUtils.isNetworkConnected(context)) {
    		// No connection => show an error dialog
    		String message = context.getResources().getString(R.string.scrap_no_network);
    		message += " " + context.getResources().getString(R.string.scrap_enable_network_first);

    		AlertDialog.Builder builder = new AlertDialog.Builder(context);
    		builder.setIcon(android.R.drawable.ic_dialog_alert)
    		.setTitle(R.string.mediacenterlabel)
    		.setMessage(message)
    		.setCancelable(false)
    		.setPositiveButton(android.R.string.ok, null);   // just let the dialog be closed by the system when clicking on the button
    		AlertDialog alert = builder.create();
    		alert.show();
    		return;
    	}

    	if(DBG) Log.d(TAG, "search: start a new search");

    	// update UI visibility
    	mMessage.setVisibility(View.GONE);
    	mProgressGroup.setVisibility(View.VISIBLE);
    	mResultsGroup.setVisibility(View.GONE);

        mResThread = new ScraperMatchesThread();
        mResThread.start();

    }


    private static final int MESSAGE_SERV_RES = 1;
    private static final int MESSAGE_UPDATE_SELECTION_DIALOG = 2;
    private static final int MESSAGE_WRITE_TAGS_TO_DB = 3;
    private static final int MESSAGE_RESET_SEARCH_UI = 4;

    public boolean handleMessage(Message msg) {

        switch(msg.what) {
            case MESSAGE_SERV_RES:
                if (getActivity() != null) { // make sure we didn't quit
                    handleResults();
                }
            	break;

            // Update the details in the list view items
            case MESSAGE_UPDATE_SELECTION_DIALOG:
                if (getActivity() != null) { // make sure we didn't quit
                    mList.invalidateViews(); // NOTE : how could we only update one given item? => http://stackoverflow.com/questions/257514/android-access-child-views-from-a-listview
                }
            	break;
            	
            // Write the choose tags to the Scraper DB (threaded)
            case MESSAGE_WRITE_TAGS_TO_DB:
            	// Display the progress wheel
            	mMessage.setVisibility(View.GONE);
        		mProgressGroup.setVisibility(View.VISIBLE);
        		mResultsGroup.setVisibility(View.GONE);
            	// Start the threaded ScraperDB write
        		new ScraperWriteDBThread((BaseTags)msg.obj).start();
            	break;

            // Update UI visibility back to square one for if back to search later
            case MESSAGE_RESET_SEARCH_UI:
                initVisibilities();
                // ...Be sure to reset the message
                mMessage.setText(R.string.scrap_no_info);
                getActivity().finish();
                break;
        }
        return true;
    }
    
    /**
     * Function called when the results of the online search are received
     */
    private void handleResults() {
        if ((mResults == null || mResults.isEmpty())&&mNfoTag==null) {
            //---------------------------------------------------------------
            // No match found for this file
            //---------------------------------------------------------------
            if (ArchosUtils.isNetworkConnected(getActivity())) {
                // The network connection is still active
            	mMessage.setText(R.string.scrap_failed);
            } else {
                // The network connection was lost since we started searching
            	mMessage.setText(R.string.scrap_no_network);
            }

        	// update UI visibility
        	mMessage.setVisibility(View.VISIBLE);
        	mCustomSearchContainer.setVisibility(mDisableOnlineUpdate ? View.GONE : View.VISIBLE);
        	mProgressGroup.setVisibility(View.GONE);
        	mResultsGroup.setVisibility(View.GONE);
        }
        else if (mResults.size() == 1&&mNfoTag==null||mNfoTag!=null&&mResults.size() == 0) {
            //----------------------------------------------------------------
            // A single match was found for this file => apply it immediately
            //----------------------------------------------------------------
        	mResIndex = 0;
            Thread scraperDetails = new ScraperDetailsThread();
        	scraperDetails.start();
        }
        else {
            //-------------------------------------------------------------------------------
            // We found several matches for this file => ask the user to select the best one
            //-------------------------------------------------------------------------------
            // Build a list with the name of all matches
            mScraperResultsAdapter = new ScraperResultsAdapter(getActivity(),mNfoTag, mResults);
            mScraperResultsAdapter.setResultList(mNfoTag,mResults);
            mList.setAdapter(mScraperResultsAdapter);
            
            // update UI visibility
            mMessage.setVisibility(View.GONE);
        	mProgressGroup.setVisibility(View.GONE);
        	mResultsGroup.setVisibility(View.VISIBLE);

            // Get info from the online database for all the items of the list not
            // processed yet in order to update the dialog display (poster, year, actors, ...)
            mSelectionThread = new ScraperSelectionThread();
            mSelectionThread.start();
        }
    }

    //*************************************************************************************
    // Scraper threads
    //*************************************************************************************
    
    /**
     * This thread retrieves the possible matches from the online database for the selected video
     */
    private final class ScraperMatchesThread extends Thread {

        @Override
        public void run() {
            try {
                mNfoTag = null;
                if (NfoParser.isNetworkNfoParseEnabled(getActivity())) {
                    if(DBG) Log.d(TAG, "NFO enabled "+mUri);
                    mNfoTag = NfoParser.getTagForFile(mUri, getActivity());
                    if(DBG) Log.d(TAG, "NFO tag is null ? "+String.valueOf(mNfoTag==null));
                }




                String search = mCustomSearchEditText.getText().toString();
                SearchInfo searchInfo = mSearchInfo;
                if (searchInfo == null) {
                    searchInfo = SearchPreprocessor.instance().parseFileBased(mUri, mTitle!=null&&!mTitle.isEmpty()?Uri.parse("/"+mTitle):mUri);
                }
                searchInfo.setUserInput(search);
                mResults = mScraper.getBestMatches(searchInfo, SELECTION_DIALOG_MAX_ITEMS).results;

                // reset the results
                if (mSelectionTags != null) {
                    mSelectionTags.clear();
                }
                if (DBG) {
                    int resultsSize = (mResults != null) ? mResults.size() : 0;
                    Log.d(TAG, "ScraperMatchesThread: getBestMatches returns " + resultsSize + " results");
                }
            } finally {
                if (!isInterrupted()) {
                    mHandler.sendEmptyMessage(MESSAGE_SERV_RES);
                }
            }
        }
    }

    /**
     * This thread retrieves the poster and the description from the online database
     * for all the possible matches
     */
    private class ScraperSelectionThread extends Thread {
        private boolean mStopService;
        private boolean mSaveLastItem;
        private int mFirstItem = 0;
        private boolean mPaused;

        @Override
        public void run() {
            int itemsCount = mResults.size();
            int offset = 0;
            mSelectionItemsProcessed = mFirstItem;
            mPaused = false;

            if (DBG) Log.d(TAG, "ScraperSelectionThread : start processing items from " + mFirstItem + " to " + (itemsCount - 1));



            // Get the details for this match

            // Wait until the thread is unpaused because the activity data may not be available
            // (happens when the activity is destroyed/re-created while rotating the device)
            while (mPaused) {
                try {
                    sleep(200);
                }
                catch (InterruptedException e) {
                }
            }

            if(mNfoTag!=null) {
                if (DBG) Log.d(TAG, "Found NFO tag");
                offset=1;
                // Update the dialog adapter data
                if (mNfoTag instanceof MovieTags) {
                    mScraperResultsAdapter.updateItemData(0, (MovieTags) mNfoTag);
                } else if (mNfoTag instanceof EpisodeTags) {
                    mScraperResultsAdapter.updateItemData(0, (EpisodeTags) mNfoTag);
                }

                // Add the available data to the tags list
                mSelectionTags.add(mNfoTag);

                // Done processing the current item
                mSelectionItemsProcessed = 1;
                mScraperResultsAdapter.setItemsUpdated(1);

                // Update the display of the selection dialog
                // (we must move the spinbar to the next item even if there are no infos available)
                mHandler.sendEmptyMessage(MESSAGE_UPDATE_SELECTION_DIALOG);

                // Exit the loop if the activity wants to abort the thread
                if (isInterrupted()) {
                    if (DBG) Log.d(TAG, "ScraperSelectionThread interrupted");

                    if (mSaveLastItem) {
                        // Validate the last item processed and force a redraw of the info dialog
                        // which is needed in case the device was rotated in the meantime
                        mHandler.obtainMessage(MESSAGE_WRITE_TAGS_TO_DB, mNfoTag).sendToTarget();
                    }
                    return;
                }


            }
            int position;
            for (position = mFirstItem; position < itemsCount; position++) {
                {
                    BaseTags tags = null;
                    boolean searchMovies = true;
                    if (DBG) Log.d(TAG, "ScraperSelectionThread : processing item " + position);

                    // Get the details for this match
                    ScrapeDetailResult detail = mScraper.getDetails(mResults.get(position), null);

                    // Wait until the thread is unpaused because the activity data may not be available
                    // (happens when the activity is destroyed/re-created while rotating the device)
                    while (mPaused) {
                        try {
                            sleep(200);
                        }
                        catch (InterruptedException e) {
                        }
                    }

                    tags = detail.tag;
                    searchMovies = detail.isMovie;

                    if (tags == null) {
                        // No tags were found online for this movie/show but we know at least its title
                        // => build an empty tags structure containing only the title
                        SearchResult res = mResults.get(position);
                        if (searchMovies) {
                            MovieTags movieTags = buildNewMovieTags(res.getTitle());
                        	tags = (BaseTags)movieTags;
                        }
                        else {
                        	EpisodeTags episodeTags = buildNewEpisodeTags(res.getTitle());
                        	tags = (BaseTags)episodeTags;
                        }
                    }

                    // Update the dialog adapter data
                    if (tags instanceof MovieTags) {
                        mScraperResultsAdapter.updateItemData(position+ offset, (MovieTags)tags);
                    }
                    else if (tags instanceof EpisodeTags) {
                        mScraperResultsAdapter.updateItemData(position+ offset, (EpisodeTags)tags);
                    }

                    // Add the available data to the tags list
                    mSelectionTags.add(tags);

                    // Done processing the current item
                    mSelectionItemsProcessed = position + 1+ offset;
                    mScraperResultsAdapter.setItemsUpdated(position+ offset + 1);

                    // Update the display of the selection dialog
                    // (we must move the spinbar to the next item even if there are no infos available)
                    mHandler.sendEmptyMessage(MESSAGE_UPDATE_SELECTION_DIALOG);

                    // Exit the loop if the activity wants to abort the thread
                    if (isInterrupted()) {
                        if (DBG) Log.d(TAG, "ScraperSelectionThread interrupted");

                        if (mSaveLastItem) {
                            // Validate the last item processed and force a redraw of the info dialog
                            // which is needed in case the device was rotated in the meantime
                            mHandler.obtainMessage(MESSAGE_WRITE_TAGS_TO_DB, tags).sendToTarget();
                        }
                        return;
                    }
                }
            }
        }

        public void pause() {
            if (DBG) Log.d(TAG, "ScraperSelectionThread paused");
            mPaused = true;
        }

        public void unpause() {
            if (DBG) Log.d(TAG, "ScraperSelectionThread unpaused");
            mPaused = false;
        }

        public void interrupt(int actions) {
            // Check the actions to perform when the thread will finish
        	mSaveLastItem = ((actions & ACTION_VALIDATE_LAST_ITEM) != 0);
            interrupt();
        }
    }

    /**
     * This thread retrieves the poster and the description from the online database for the selected match
     */
    private final class ScraperDetailsThread extends Thread {
        @Override
        public void run() {
            Log.d(TAG, "ScraperDetailsThread");
                BaseTags tags = null;
                boolean searchMovies = true;
                if(mNfoTag==null) {
                    ScrapeDetailResult detail = mScraper.getDetails(mResults.get(mResIndex), null);
                    tags = detail.tag;
                    searchMovies = detail.isMovie;
                }
                else{
                    tags = mNfoTag;
                    searchMovies = tags instanceof MovieTags;
                }
                if (tags == null) {
                    // No tags were found online for this movie/show but we know at least its title
                    // => build an empty tags structure containing only the title
                    SearchResult res = mResults.get(mResIndex);
                    if (searchMovies) {
                        MovieTags movieTags = buildNewMovieTags(res.getTitle());
                        tags = (BaseTags)movieTags;
                    }
                    else {
                        EpisodeTags episodeTags = buildNewEpisodeTags(res.getTitle());
                        tags = (BaseTags)episodeTags;
                    }
                }

                if (tags!=null) {
                    mHandler.obtainMessage(MESSAGE_WRITE_TAGS_TO_DB, tags).sendToTarget();
                }
        }
    }
    
    private static MovieTags buildNewMovieTags(String movieTitle) {
        MovieTags movieTags = new MovieTags();
        movieTags.setTitle(movieTitle);
        return movieTags;
    }

    private static EpisodeTags buildNewEpisodeTags(String episodeTitle) {
        EpisodeTags episodeTags = new EpisodeTags();
        ShowTags showTags = new ShowTags();
        showTags.setTitle(episodeTitle);
        episodeTags.setShowTags(showTags);
        return episodeTags;
    }

    /**
     * This thread writes the choosen description to the DB.
     * (Takes more than 1 sec, hence threaded). 
     */
    private final class ScraperWriteDBThread extends Thread {

        private final BaseTags mTags;

        public ScraperWriteDBThread(BaseTags tags) {
            mTags = tags;
        }

        @Override
        public void run() {
            validateTags(mTags);
        }
        /**
         * Update MediaDB and ScraperDB when a description is choosen
         * @param tags
         */
        private void validateTags(BaseTags tags) {
            Context context = getActivity();

            // this can happen after being detached from the context..
            if (context == null) return;

            // since poster can be deleted again we refresh it here
            tags.downloadPoster(context);
            // saving will trigger database change notification and reloading in
            // VideoInfoActivity
            tags.save(context, mUri);

            // TODO make this nicer.
            if (NfoWriter.isNfoAutoExportEnabled(context)) {
                // also auto-export all the data
                if (mUri != null) {
                    try {
                        NfoWriter.export(mUri, tags, null);
                    } catch (IOException e) {
                        Log.w(TAG, e);
                    }
                }
            }
            // store the fact that we just saved the tag so we can return
            // to initial state when we get visible again.
            mHasSaved = true;

            // update UI visibility
            // fine tuning: delay a bit so that this UI reset is done after the result fragment is displayed: looks nicer
            mHandler.sendEmptyMessageDelayed(MESSAGE_RESET_SEARCH_UI, 300);
            TraktService.onNewVideo(context);
        }
    }
    
    /** catches onClick, hides the Keyboard and forwards the event */
    private static class ClickInterceptor implements View.OnClickListener {
        private View.OnClickListener mListener = null;
        private final InputMethodManager mImm;
        private final View mView;

        public ClickInterceptor(InputMethodManager imm, View v) {
            mImm = imm;
            mView = v;
        }

        public void setOtherListener(View.OnClickListener listener) {
            mListener = listener;
        }

        public void onClick(View v) {
            mImm.hideSoftInputFromWindow(mView.getWindowToken(), 0);
            if (mListener != null)
                mListener.onClick(v);
        }
    }

    private TextView.OnEditorActionListener mEditorActionListener = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            // Forward keyboard ok -> button
            mSearchButton.callOnClick();
            return true;
        }
    };
}
