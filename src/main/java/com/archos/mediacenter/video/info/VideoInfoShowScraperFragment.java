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
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.utils.ScraperResultsAdapter;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.DebugTimer;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.TagsFactory;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.saxhandler.ShowAllDetailsHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoInfoShowScraperFragment extends Fragment implements
        OnItemClickListener, OnClickListener, OnEditorActionListener,
        Handler.Callback {

    protected static final boolean DBG = false;
    protected static final String TAG = VideoInfoShowScraperFragment.class.getSimpleName();
    public static final String SHOW_ID = "show_id";

    protected Scraper mScraper;

    private SearchTask mCurrentSearchTask;
    private ShowTags mShowTag;
    private final List<ProgressItem> mResultsList;

    private ListView mListView;
    private ScraperResultsAdapter mAdapter;

    private View mSearchContainer;
    private View mProgressContainer;
    private View mResultContainer;
    private TextView mMessage;

    private View mSearchButton;
    private EditText mSearchEdTxt;

    private Handler mHandler;

    private DisplayState mDisplayState;
    private View mView;
    private SearchInfo mSearchInfo;

    public VideoInfoShowScraperFragment() {
        if (DBG) Log.d(TAG, "CTOR");
        mResultsList = new ArrayList<ProgressItem>();
    }

    // ---------------------- FRAGMENT LIFECYCLE ---------------------------- //

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mScraper = new Scraper(activity);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DBG) Log.d(TAG, "onCreate savedInstanceState=" + savedInstanceState);
        super.onCreate(savedInstanceState);

        // we'd like to keep this instance when rotating
        setRetainInstance(false);
        mSearchInfo = SearchPreprocessor.instance().parseFileBased(Uri.parse("/foo.avi"), Uri.parse("/foo.avi"));
        mHandler = new Handler(this);

        mDisplayState = DisplayState.SEARCH_INITIAL;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.video_info_scraper_search, null);
        return mView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (DBG) Log.d(TAG, "onViewCreated");
        mListView = (ListView) mView.findViewById(R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setAdapter(mAdapter);

        mSearchContainer = mView.findViewById(R.id.custom_search_container);
        mProgressContainer = mView.findViewById(R.id.progress_group);
        mResultContainer = mView.findViewById(R.id.search_results_group);
        mMessage = (TextView) mView.findViewById(R.id.message);

        mSearchButton = mView.findViewById(R.id.search);
        mSearchButton.setOnClickListener(this);

        mView.findViewById(R.id.cancel).setOnClickListener(this);

        mSearchEdTxt = (EditText) mView.findViewById(R.id.custom_search_edittext);
        mSearchEdTxt.setHint(R.string.video_info_custom_search_show_hint);
        mSearchEdTxt.setOnEditorActionListener(this);

        // limit the way the window is adjusted when softkeyboard is opened
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);




        setInfoItem((Base)getActivity().getIntent().getSerializableExtra(VideoInfoScraperActivity.EXTRA_SHOW));

        // restore display state
        setDisplayState(mDisplayState);
    }

    // onActivityCreated

    @Override
    public void onStart() {
        if (DBG) Log.d(TAG, "onStart");
        resumeCurrentTask();
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        // limit the way the window is adjusted when softkeyboard is opened
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        hideSoftKbd();
    }
    // -- running

    @Override
    public void onPause() {
        super.onPause();
        hideSoftKbd();
    }

    @Override
    public void onStop() {
        if (DBG) Log.d(TAG, "onStop");
        pauseCurrentTask();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (DBG) Log.d(TAG, "onDestroyView");
        super.onDestroyView();
        // null references to views
        mSearchContainer = null;
        mProgressContainer = null;
        mResultContainer = null;
        mSearchEdTxt = null;
        mListView = null;
        mMessage = null;
        mSearchButton = null;
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        cancelCurrentTask();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        if (DBG) Log.d(TAG, "onDetach");
        super.onDetach();
        mScraper = null;
    }



    public void setInfoItem(Base item) {
        if (DBG) Log.d(TAG, "setInfoItem");

        ShowTags newTag = (ShowTags) item.getFullScraperTags(getActivity());
        long oldId = mShowTag != null ? mShowTag.getId() : 0;
        long newId = newTag != null ? newTag.getId() : 0;
        if (DBG) Log.d(TAG, "old:" + oldId + " new:" + newId);
        if (oldId != newId) {
            mShowTag = newTag;
        }
        if (mShowTag != null && mSearchEdTxt != null) {
            String txt = mShowTag.getTitle();
            mSearchEdTxt.setText(txt);
            mSearchEdTxt.setSelection(txt != null ? txt.length() : 0);
            mSearchEdTxt.setSelected(false);

        }

    }



    // ---------------------- IMPLEMENTS Handler.Callback ------------------- //
    public boolean handleMessage(Message msg) {
        // the only message we ever get is send once saving is complete
        if (isVisible()) {
            Intent intent = new Intent();
            intent.putExtra(SHOW_ID, msg.arg1);
            getActivity().setResult(Activity.RESULT_OK, intent);
            getActivity().finish();
        }
        return true;
    }

    // ---------------------- IMPLEMENTS OnItemClickListener ---------------- //
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (DBG) Log.d(TAG, "onItemClick");
        HashMap<String,EpisodeTags> map = position < mResultsList.size() ? mResultsList.get(position).epMap : null;
        if (map != null) {
            SaveItem item = new SaveItem();
            item.source = map;
            item.target = mShowTag;
            item.sendOnSuccess = mHandler.obtainMessage();
            cancelCurrentTask();
            EpSaveTask.createAndRun(getActivity(), item);
        } else if (mCurrentSearchTask != null) {
            mCurrentSearchTask.requestSave(position, mShowTag, getActivity(), mHandler.obtainMessage());
        } else {
            Log.e(TAG, "Failed to save, no task & no result available");
        }
        setDisplayState(DisplayState.APPLY_RESULT);
    }

    // ---------------------- IMPLEMENTS OnClickListener -------------------- //
    public void onClick(View v) {
        if (DBG) Log.d(TAG, "onClick");

        switch (v.getId()) {
            case R.id.cancel:
                hideSoftKbd();
                cancelCurrentTask();
                getActivity().finish();
                break;
            case R.id.search:
                hideSoftKbd();
                startSearch();
                break;
            default:
                Log.e(TAG, "Click on " + v + " not supported.");
                break;
        }
    }

    // ---------------------- IMPLEMENTS OnEditorActionListener ------------- //
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (DBG) Log.d(TAG, "onEditorAction");
        mSearchButton.callOnClick();
        return true;
    }

    // ---------------------- DISPLAY STATE HANDLING ------------------------ //
    private enum DisplayState {
        SEARCH_INITIAL,
        SEARCH_SEARCHING,
        SEARCH_NORESULT,
        SEARCH_RESULT,
        APPLY_RESULT
    }

    private void setDisplayState(DisplayState state) {
        if (DBG) Log.d(TAG, "setDisplayState:" + state.name());
        mDisplayState = state;
        switch (state) {
            case SEARCH_INITIAL:
                setVisibility(mSearchContainer, true);
                setVisibility(mMessage, true);
                setText(mMessage, getActivity(), 0);
                setVisibility(mProgressContainer, false);
                setVisibility(mResultContainer, false);
                break;
            case SEARCH_SEARCHING:
                setVisibility(mSearchContainer, true);
                setVisibility(mMessage, false);
                setVisibility(mProgressContainer, true);
                setVisibility(mResultContainer, false);
                break;
            case SEARCH_NORESULT:
                setVisibility(mSearchContainer, true);
                setVisibility(mMessage, true);
                setText(mMessage, getActivity(), R.string.scrap_show_no_result);
                setVisibility(mProgressContainer, false);
                setVisibility(mResultContainer, false);
                break;
            case SEARCH_RESULT:
                setVisibility(mSearchContainer, true);
                setVisibility(mMessage, false);
                setVisibility(mProgressContainer, false);
                setVisibility(mResultContainer, true);
                break;
            case APPLY_RESULT:
                setVisibility(mSearchContainer, false);
                setVisibility(mMessage, false);
                setVisibility(mProgressContainer, true);
                setVisibility(mResultContainer, false);
                break;
            default:
                // state == null !?
                break;
        }
    }

    private static void setVisibility(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void setText(TextView view, Context context, int resId) {
        if (view != null && context != null) {
            view.setText(resId > 0 ? context.getString(resId) : null);
        }
    }

    // ---------------------- PRIVATE IMPLEMENATION ------------------------- //
    private void hideSoftKbd() {
        if (mSearchEdTxt != null) {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mSearchEdTxt.getWindowToken(), 0);
        }
    }

    private void pauseCurrentTask() {
        if (DBG) Log.d(TAG, "pauseCurrentTask");
        if (mCurrentSearchTask != null) {
            mCurrentSearchTask.pause();
        }
    }

    private void resumeCurrentTask() {
        if (DBG) Log.d(TAG, "resumeCurrentTask");
        if (mCurrentSearchTask != null) {
            mCurrentSearchTask.resume();
        }
    }

    private void cancelCurrentTask() {
        if (DBG) Log.d(TAG, "cancelCurrentTask");
        if (mCurrentSearchTask != null) {
            // cancel before resume so it exists after waiting
            mCurrentSearchTask.cancel(false);
            mCurrentSearchTask.resume();
            mCurrentSearchTask = null;
        }
        setDisplayState(DisplayState.SEARCH_INITIAL);
    }

    private void startSearch() {
        if (DBG) Log.d(TAG, "startSearch");
        // make sure there is no old task
        cancelCurrentTask();
        // start searching
        mCurrentSearchTask = new SearchTask();
        mCurrentSearchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mSearchEdTxt.getText().toString());
        setDisplayState(DisplayState.SEARCH_SEARCHING);
    }

    protected void onUpdateProgress(ProgressItem item) {
        if (DBG) Log.d(TAG, "onUpdateProgress:" + item);
        if (item.position < 0) {
            mResultsList.clear();
            mAdapter = new ScraperResultsAdapter(getActivity(),null, item.list);
            mListView.setAdapter(mAdapter);
            if (item.list != null && item.list.size() > 0) {
                setDisplayState(DisplayState.SEARCH_RESULT);
                //mAdapter.setResultList(item.list);
                mAdapter.notifyDataSetChanged();
            } else {
                setDisplayState(DisplayState.SEARCH_NORESULT);
            }
        } else {
            BaseTags tag = item.tag;
            if (tag instanceof EpisodeTags) {
                mResultsList.add(item);
                EpisodeTags epTag = (EpisodeTags) tag;

                mAdapter.updateItemData(item.position, epTag);
                mAdapter.setItemsUpdated(item.position + 1);
                int first = mListView.getFirstVisiblePosition();
                int last = mListView.getLastVisiblePosition();
                int our = item.position;
                if (first <= our && last >= our)
                    mAdapter.notifyDataSetChanged();
            }
        }
    }

    // ---------------------- SEARCH VIA SCRAPER SERVICE -------------------- //
    protected void onSearchFinished() {
        if (DBG) Log.d(TAG, "onSearchFinished");
        mCurrentSearchTask = null;
    }

    static class ProgressItem {

        public final List<SearchResult> list;
        public final BaseTags tag;
        public final HashMap<String, EpisodeTags> epMap;
        public final int position;

        public ProgressItem(List<SearchResult> list) {
            this.list = list;
            position = -1;
            tag = null;
            epMap = null;
        }

        public ProgressItem(BaseTags tag, HashMap<String, EpisodeTags> epMap, int position) {
            list = null;
            this.position = position;
            this.tag = tag;
            this.epMap = epMap;
        }
    }

    private class SearchTask extends AsyncTask<String, ProgressItem, Void> {
        private volatile boolean mPause;
        private Object mWaitObject = new Object();

        private volatile boolean mSaveRequested;
        private volatile int mSaveRequestId;
        private volatile Context mSaveContext;
        private volatile ShowTags mSaveTarget;
        private volatile Message mSaveMessage;

        public SearchTask() { /* empty */ }

        public void requestSave(int resultId, ShowTags saveTarget, Context saveContext, Message message) {
            if (DBG) Log.d(TAG, "requestSave " + resultId);
            mSaveRequestId = resultId;
            mSaveContext = saveContext.getApplicationContext();
            mSaveTarget = saveTarget;
            mSaveMessage = message;
            // set this true last so above is initialized
            mSaveRequested = true;
        }

        public void pause() {
            synchronized (mWaitObject) {
                mPause = true;
            }
        }

        public void resume() {
            synchronized (mWaitObject) {
                mPause = false;
                mWaitObject.notifyAll();
            }
        }

        private void checkPause() {
            if (!mPause) return;
            synchronized (mWaitObject) {
                while (mPause) {
                    Log.d(TAG, "checkPause - Paused");
                    try {
                        mWaitObject.wait();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "checkPause - InterruptedException");
                        // expected
                    }
                }
            }
        }

        @Override
        protected Void doInBackground(String... params) {
            if (params != null && params.length > 0 && mScraper != null) {
                    checkPause();
                    if (isCancelled())
                        return null;
                    // search for param + " S1E1" so we get show results only, filename is ignored but has to be != null
                    mSearchInfo.setUserInput(params[0] + " S1E1");
                    List<SearchResult> matches = mScraper.getAllMatches(mSearchInfo).results;
                    publishProgressSafe(new ProgressItem(matches));
                    int count = matches != null ? matches.size() : 0;
                    int current = 0;
                    while (!isCancelled() && current < count) {
                        Bundle b = new Bundle();
                        b.putBoolean(Scraper.ITEM_REQUEST_ALL_EPISODES, true);
                        checkPause();
                        if (isCancelled())
                             return null;
                        BaseTags tag = null;
                        HashMap<String, EpisodeTags> epMap = null;
                        if (mSaveRequested) {
                            current = mSaveRequestId;
                            if (DBG) Log.d(TAG, "fetching / saving item: " + current);
                            ScrapeDetailResult detail = Scraper.getDetails(matches.get(current), b);
                            if (detail.isOkay()) {
                                tag = detail.tag;
                                Bundle episodeList = detail.extras;
                                epMap = toMap(episodeList);
                                SaveItem saveItem = new SaveItem();
                                saveItem.source = epMap;
                                saveItem.target = mSaveTarget;
                                saveItem.sendOnSuccess = mSaveMessage;
                                EpSaveTask.createAndRun(mSaveContext, saveItem);
                                mSaveContext = null;
                                mSaveTarget = null;
                                mSaveMessage = null;
                            }
                            return null;
                        }
                        Log.d(TAG, "mScraperService.getDetailsSpecial - " + current);
                        ScrapeDetailResult detail = Scraper.getDetails(matches.get(current), b);

                        if (detail.isOkay()) {
                            tag = detail.tag;
                            Bundle episodeList = detail.extras;
                            epMap = toMap(episodeList);
                        }
                        publishProgressSafe(new ProgressItem(tag, epMap, current));
                        current++;
                    }
            }
            return null;
        }

        private void publishProgressSafe(ProgressItem... items) {
            synchronized (mWaitObject) {
                checkPause();
                if (isCancelled())
                    return;
                publishProgress(items);
            }
        }

        private HashMap<String, EpisodeTags> toMap(Bundle b) {
            int size = b != null ? b.size() : 0;
            HashMap<String, EpisodeTags> result = new HashMap<String, EpisodeTags>(size);
            if (b != null) {
                b.setClassLoader(BaseTags.class.getClassLoader());
                for (String key : b.keySet()) {
                    result.put(key, b.<EpisodeTags>getParcelable(key));
                }
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(ProgressItem... values) {
            if (isCancelled())
                return;

            if (values != null && values.length > 0) {
                Log.d(TAG, "onProgressUpdate got " + values.length + " items");
                onUpdateProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            onSearchFinished();
        }
    }

    static class SaveItem {
        ShowTags target;
        Map<String, EpisodeTags> source;
        Message sendOnSuccess;
    }

    private static class EpSaveTask extends AsyncTask<SaveItem, Void, Void> {
        private final Context mContext;

        public static void createAndRun(Context context, SaveItem item) {
            // might be better on a single thread instead of a pool but the default one is bad too
            new EpSaveTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, item);
        }

        public EpSaveTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(SaveItem... params) {
            if (params != null && params.length > 0) {
                // step 1: find all episodes in database that belong to this show
                List<EpisodeTags> episodeList = getEpisodeList(params[0].target);
                // step 2: save new show / episode info for those
                long showID = handleSave(params[0], episodeList);
                Message m = params[0].sendOnSuccess;
                m.arg1 = (int) showID;
                if (DBG) Log.d(TAG, "save finished, sending message");
                if (m != null)
                    m.sendToTarget();
            }
            return null;
        }

        private List<EpisodeTags> getEpisodeList(ShowTags sTag) {
            ArrayList<EpisodeTags> result = new ArrayList<EpisodeTags>();
            if (sTag != null) {
                // get EpisodeTags by ShowId
                long sId = sTag.getId();
                ContentResolver cr = mContext.getContentResolver();
                Uri uri = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
                String selection = VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + "=?";
                String[] selectionArgs = new String[] {
                        String.valueOf(sId)
                };
                String sortOrder = null;
                Cursor c = cr.query(uri, TagsFactory.VIDEO_COLUMNS, selection, selectionArgs, sortOrder);
                List<BaseTags> tagsList = TagsFactory.buildTagsFromVideoCursor(c);
                if (c != null)
                    c.close();
                // add every EpisodeTags (should be all) to result list
                if (tagsList != null) {
                    result.ensureCapacity(tagsList.size());
                    for (BaseTags bTag : tagsList) {
                        if (bTag instanceof EpisodeTags) {
                            EpisodeTags epTag = (EpisodeTags) bTag;
                            result.add(epTag);
                        }
                    }
                }
            }
            return result;
        }

        private long handleSave(SaveItem item, List<EpisodeTags> targetList) {
            // TODO make this nicer
            NfoWriter.ExportContext exportContext = null;
            if (NfoWriter.isNfoAutoExportEnabled(mContext)) {
                exportContext = new NfoWriter.ExportContext();
            }

            DebugTimer t = new DebugTimer();
            if (item != null &&
                    item.source != null && item.source.size() > 0 &&
                    targetList != null && targetList.size() > 0) {

                ShowTags targetShow = item.source.values().iterator().next().getShowTags();
                long targetShowId = targetShow.save(mContext, 0);
                int size = targetList.size();
                int i = 1;
                ArrayList<ContentProviderOperation> opList = new ArrayList<ContentProviderOperation>();
                Map<String, Long> poster2IdMap = createPosterIdMap(mContext, targetShowId);
                for (EpisodeTags epTag : targetList) {
                    Log.d(TAG, "Saving " + (i++) + " of " + size + " episodes.");
                    EpisodeTags targetEpTag = getEpisode(item.source, epTag.getEpisode(), epTag.getSeason(), targetShow);
                    targetEpTag.setVideoId(epTag.getVideoId());
                    targetEpTag.setShowId(targetShowId);
                    targetEpTag.setFile(epTag.getFile());
                    targetEpTag.addSaveOperation(opList, poster2IdMap);
                    targetEpTag.downloadPoster(mContext);
                    if (exportContext != null) {
                        Uri file = targetEpTag.getFile();
                        if (file != null) {
                            try {
                                NfoWriter.export(file, targetEpTag, exportContext);
                            } catch (IOException e) {
                                // ignored, probably not writable smb share
                            }
                        }
                    }
                }
                Log.d(TAG, "preparations took:" + t.step());
                if (opList.size() > 0) {
                    try {
                        mContext.getContentResolver().applyBatch(ScraperStore.AUTHORITY, opList);
                    } catch (RemoteException e) {
                        Log.e(TAG, "handleSave failed", e);
                    } catch (OperationApplicationException e) {
                        Log.e(TAG, "handleSave failed", e);
                    }
                }
                TraktService.onNewVideo(mContext);
                Log.d(TAG, "saving in the end:" + t.step() + " thats:" + t.total());
                return targetShowId;
            }
            return -1;
        }

        private static final String[] POSTER_ID_PROJ = {
                ScraperStore.ShowPosters.ID,        // 0
                ScraperStore.ShowPosters.LARGE_FILE // 1
        };
        private static Map<String, Long> createPosterIdMap(Context context, long showId) {
            HashMap<String, Long> result = new HashMap<String, Long>();
            ContentResolver cr = context.getContentResolver();
            Uri uri = ContentUris.withAppendedId(ScraperStore.ShowPosters.URI.BY_SHOW_ID, showId);
            Cursor c = cr.query(uri, POSTER_ID_PROJ, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    Long id = Long.valueOf(c.getLong(0));
                    String path = c.getString(1);
                    result.put(path, id);
                }
                c.close();
            }
            return result;
        }

        private EpisodeTags getEpisode(Map<String, EpisodeTags> map, int episode, int season, ShowTags show) {
            EpisodeTags newEpTag = map.get(ShowAllDetailsHandler.getKey(season, episode));
            if (newEpTag == null) {
                newEpTag = new EpisodeTags();
                // assume episode / season of request
                newEpTag.setSeason(season);
                newEpTag.setEpisode(episode);
                newEpTag.setShowTags(show);
                // also check if there is a poster
                List<ScraperImage> posters = show.getPosters();
                if (posters != null) {
                    for (ScraperImage image : posters) {
                        if (image.getSeason() == season) {
                            newEpTag.setPosters(image.asList());
                            newEpTag.downloadPoster(mContext);
                            newEpTag.downloadPicture(mContext);
                            break;
                        }
                    }
                }
            }
            return newEpTag;
        }
    }

}