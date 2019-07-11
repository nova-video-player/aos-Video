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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.DebugTimer;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
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

public class ManualShowScrappingSearchFragment extends ManualScrappingSearchFragment {

    public static final String TAG = "ManualShowScrappingSF";
    public static final boolean DBG = false;

    private long mShowId;
    private String mShowName;

    /** We keep a map to be able to get back the SearchResult for a given BaseTags result when saving at the end */
    HashMap<BaseTags, SearchResult> mTagsToSearchResultMap = new HashMap<>();
    private AsyncTask<ShowTags, Integer, ShowTags> mEpisodeSaveTask;
    private SearchInfo mSearchInfo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get input show and init the SearchInfo ASAP
        mShowId = getActivity().getIntent().getLongExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_ID, -1);
        mShowName = getActivity().getIntent().getStringExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_NAME);
        mSearchInfo = SearchPreprocessor.instance().parseFileBased(Uri.parse("/foo.avi"), Uri.parse("/foo.avi"));

        // Start a search using the search suggestion. It makes it easy for the user to edit it for typo if needed
        // Allow often the second or third suggestion is the right one
        setSearchQuery(mShowName, true);

        setTitle(getString(R.string.leanback_scrap_searching_tvshow_hint));
    }

    @Override
    protected BaseTags getNfoTags() {
        return null;
    }

    @Override
    protected String getEmptyText() {
        return getString(R.string.no_results_found);
    }

    @Override
    protected String getResultsHeaderText() {
        return getString(R.string.leanback_scrap_choose_the_description);
    }

    /**
     * Do the actual search
     * @param text to search for
     * @return
     */
    @Override
    protected ScrapeSearchResult performSearch(String text) {
        mTagsToSearchResultMap.clear();
        mSearchInfo.setUserInput(text+ " S1E1");
        // search for param + " S1E1" so we get show results only, filename is ignored but has to be != null
        return mScraper.getAllMatches(mSearchInfo);
    }

    @Override
    protected BaseTags getTagFromSearchResult(SearchResult result) {
        // Get the details for this match
        ScrapeDetailResult detail = mScraper.getDetails(result, null);
        BaseTags tags = detail.tag;

        // In theory we should get a ShowTags here but in practice we did a search for Episodes.
        // Hence we need to get the ShowTags from the EpisodeTags
        if (tags instanceof  EpisodeTags) {
            tags = ((EpisodeTags)tags).getShowTags();
        }

        if (tags == null) {
            // 2015: I didn't test this case...
            buildNewShowTags(result.getTitle());
        }

        if(DBG) Log.d(TAG, "put in mTagsToSearchResultMap: "+tags);
        mTagsToSearchResultMap.put(tags, result);

        return tags;
    }

    private static ShowTags buildNewShowTags(String showTitle) {
        ShowTags showTags = new ShowTags();
        showTags.setTitle(showTitle);
        return showTags;
    }

    @Override
    protected void saveTagsAndFinish(BaseTags newTags) {
        // In theory we should get a ShowTags here but in practice we did a search for Episodes.
        // Hence we need to get the ShowTags from the EpisodeTags
        if (newTags instanceof ShowTags) {
            final ShowTags newShowTags = (ShowTags)newTags;
            String confirmationMessage = getString(R.string.scrap_change_confirmation, mShowName, newShowTags.getTitle());
            new AlertDialog.Builder(getActivity())
                    .setMessage(confirmationMessage)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            new EpSaveTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, newShowTags);
                        }
                    })
                    .show();
        }
        else {
            if(DBG) Log.d(TAG, "saveTags error should not get a " + newTags.getClass().getCanonicalName());
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
    }
    @Override
    public void onStop(){
        super.onStop();
        if(mEpisodeSaveTask!=null)
            mEpisodeSaveTask.cancel(true);
    }

    private class EpSaveTask extends AsyncTask<ShowTags, Integer, ShowTags> {

        final int PROGRESS_ID_FINALIZING = -2;

        final Context mContext;
        ProgressDialog mProgressDialog;

        public EpSaveTask() {
            mContext = getActivity();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle(R.string.scrap_change_title);
            mProgressDialog.setMessage(getString(R.string.scrap_change_initializing));
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }

        @Override
        protected ShowTags doInBackground(ShowTags... params) {
            // step 1: find all episodes in database that belong to the old show
            List<EpisodeTags> episodeList = getEpisodeList(mShowId);
            if (DBG) {
                Log.d(TAG, "--------------------\nAll episodes in database that belong to the old show:");
                for (EpisodeTags et : episodeList) {
                    Log.d(TAG, "      S"+et.getSeason()+" E"+et.getEpisode()+" "+et.getTitle());
                }
                Log.d(TAG, "--------------------");

            }
            // step 2: save new show / episode info for those
            ShowTags newShow = handleSave(params[0], episodeList);
            Log.d(TAG, "save finished");
            return newShow;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values[0]==PROGRESS_ID_FINALIZING) {
                mProgressDialog.setMessage(getString(R.string.scrap_change_finalizing));
            }
            else {
                mProgressDialog.setMax(values[1]);
                mProgressDialog.setProgress(values[0]);
                int season = values[2];
                int episode = values[3];
                mProgressDialog.setMessage(getString(R.string.episode_identification, season, episode));
            }
        }

        @Override
        protected void onPostExecute(ShowTags newShow) {
            mProgressDialog.dismiss();
            Intent resultIntent = new Intent();
            resultIntent.putExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_ID, newShow.getId());
            resultIntent.putExtra(ManualShowScrappingActivity.EXTRA_TVSHOW_NAME, newShow.getTitle());
            getActivity().setResult(Activity.RESULT_OK, resultIntent);
            getActivity().finish();
        }

        /**
         * Get the list of episodes in the DB for the given show
         * @param showId
         * @return
         */
        private List<EpisodeTags> getEpisodeList(long showId) {
            ArrayList<EpisodeTags> result = new ArrayList<EpisodeTags>();
            // get EpisodeTags by ShowId
            final Uri uri = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
            final String selection = VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID + "=?";
            final String[] selectionArgs = new String[] { String.valueOf(showId) };
            final String sortOrder = VideoStore.Video.VideoColumns.SCRAPER_E_SEASON+", "+VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE;
            Cursor c = mContext.getContentResolver().query(uri, TagsFactory.VIDEO_COLUMNS, selection, selectionArgs, sortOrder);
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
            return result;
        }

        /**
         *
         * @param newShow the new show to save
         * @param targetEpisodesList the list of episode that are in the DB and must be changed from oldShow to newShow
         * @return the new show with its ID set up
         */
        private ShowTags handleSave(ShowTags newShow, List<EpisodeTags> targetEpisodesList) {
            // TODO make this nicer
            NfoWriter.ExportContext exportContext = null;
            if (NfoWriter.isNfoAutoExportEnabled(mContext)) {
                exportContext = new NfoWriter.ExportContext();
            }

            DebugTimer t = new DebugTimer();

            // Get all episodes for the new show
            SearchResult sr = mTagsToSearchResultMap.get(newShow); // Get the searchResult from the map we built for it
            Bundle b = new Bundle();
            b.putBoolean(Scraper.ITEM_REQUEST_ALL_EPISODES, true);
            ScrapeDetailResult detail = Scraper.getDetails(sr, b);
            HashMap<String, EpisodeTags> epMap = null;
            if (detail.isOkay()) {
                Bundle episodeList = detail.extras;
                epMap = toMap(episodeList);
            }
            if (DBG) {
                Log.d(TAG, "--------------------\nAll episodes for the new show:");
                for (String key : epMap.keySet()) {
                    Log.d(TAG, "epMap "+key+" -> "+epMap.get(key).getShowTitle()+" "+epMap.get(key).getSeason()+ " "+epMap.get(key).getEpisode());
                }
                Log.d(TAG, "--------------------");
            }

            // Update all episodes
            final long newShowId = newShow.save(mContext, 0); // second argument is not used in case of ShowTags
            newShow.setId(newShowId);
            int size = targetEpisodesList.size();
            int i = 1;
            ArrayList<ContentProviderOperation> opList = new ArrayList<ContentProviderOperation>();
            Map<String, Long> poster2IdMap = createPosterIdMap(mContext, newShowId);
            for (EpisodeTags oldEpisodeTag : targetEpisodesList) {
                Log.d(TAG, "Saving " + (i++) + " of " + size + " episodes.");
                EpisodeTags newEpisodeTag = getEpisode(epMap, oldEpisodeTag.getEpisode(), oldEpisodeTag.getSeason(), newShow);
                newEpisodeTag.setVideoId(oldEpisodeTag.getVideoId());
                newEpisodeTag.setShowId(newShowId);
                newEpisodeTag.setFile(oldEpisodeTag.getFile());
                newEpisodeTag.downloadPicture(mContext); //download before saving
                newEpisodeTag.addSaveOperation(opList, poster2IdMap);
                newEpisodeTag.downloadPoster(mContext);

                if (exportContext != null) {
                    Uri file = newEpisodeTag.getFile();
                    if (file != null) {
                        try {
                            NfoWriter.export(file, newEpisodeTag, exportContext);
                        } catch (IOException e) {
                            // ignored, probably not writable smb share
                        }
                    }
                }
                publishProgress(i, size, newEpisodeTag.getSeason(), newEpisodeTag.getEpisode());
            }
            publishProgress(PROGRESS_ID_FINALIZING);
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
            return newShow;
        }

        private final String[] POSTER_ID_PROJ = {
                ScraperStore.ShowPosters.ID,        // 0
                ScraperStore.ShowPosters.LARGE_FILE // 1
        };

        private Map<String, Long> createPosterIdMap(Context context, long showId) {
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
                            break;
                        }
                    }
                }
            }
            return newEpTag;
        }

        private HashMap<String, EpisodeTags> toMap(Bundle b) {
            int size = (b!=null) ? b.size() : 0;
            HashMap<String, EpisodeTags> result = new HashMap<String, EpisodeTags>(size);
            if (b != null) {
                b.setClassLoader(BaseTags.class.getClassLoader());
                for (String key : b.keySet()) {
                    result.put(key, b.<EpisodeTags>getParcelable(key));
                }
            }
            return result;
        }
    }
}
