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

import android.net.Uri;
import android.os.Bundle;

import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.preprocess.TvShowSearchInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

public class ManualVideoScrappingSearchFragment extends ManualScrappingSearchFragment {

    private static final Logger log = LoggerFactory.getLogger(ManualVideoScrappingSearchFragment.class);

    private Video mVideo;
    private SearchInfo mSearchInfo;
    HashMap<BaseTags, SearchResult> mTagsToSearchResultMap = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get input file and init the SearchInfo ASAP
        mVideo = (Video) getActivity().getIntent().getSerializableExtra(ManualVideoScrappingActivity.EXTRA_VIDEO);
        mSearchInfo = SearchPreprocessor.instance().parseFileBased(mVideo.getUri(), mVideo.getName()!=null&&!mVideo.getName().isEmpty()? Uri.parse("/"+mVideo.getName()):mVideo.getUri());

        // Start a search using the search suggestion. It makes it easy for the user to edit it for typo if needed
        // Allow often the second or third suggestion is the right one
        setSearchQuery(mSearchInfo.getSearchSuggestion(), true);

        setTitle(getString(R.string.leanback_video_info_custom_search_file_hint));
    }

    @Override
    protected BaseTags getNfoTags() {
        if (NfoParser.isNetworkNfoParseEnabled(getActivity())) {
            return NfoParser.getTagForFile(mVideo.getFileUri(), getActivity());
        }
        return null;
    }

    @Override
    protected String getEmptyText() {
        return getString(R.string.no_results_found)+" "+getString(R.string.no_results_found_show_helper);
    }

    @Override
    protected String getResultsHeaderText() {
        return getString(R.string.leanback_scrap_choose_the_description_for, mVideo.getFilenameNonCryptic());
    }

    /**
     * Do the actual search
     * @param text to search for
     * @return
     */
    @Override
    protected ScrapeSearchResult performSearch(String text) {
        mTagsToSearchResultMap.clear();
        mSearchInfo.setUserInput(text);
        return mScraper.getBestMatches(mSearchInfo, SEARCH_RESULT_MAX_ITEMS);
    }

    protected BaseTags getTagFromSearchResult(SearchResult result) {
        // Get the details for this match
        Bundle b = new Bundle();
        b.putBoolean(Scraper.ITEM_REQUEST_BASIC_VIDEO, true);

        if (result.isTvShow()) {
            b.putBoolean(Scraper.ITEM_REQUEST_BASIC_VIDEO, true);
            b.putInt(Scraper.ITEM_REQUEST_SEASON, result.getOriginSearchSeason());
            // this is required to get the season poster (episode does not have this information on tmdb)
            //b.putInt(Scraper.ITEM_REQUEST_EPISODE, result.getOriginSearchEpisode());
        }
        ScrapeDetailResult detail = mScraper.getDetails(result, b);
        BaseTags tags = detail.tag;

        if (tags instanceof EpisodeTags) {
            if (((EpisodeTags)tags).getShowTags() != null) ((EpisodeTags)tags).getShowTags().setTitle(result.getTitle());
        }

        if (tags == null) {
            // No tags were found online for this movie/show but we know at least its title
            // => build an empty tags structure containing only the title
            if (detail.isMovie) {
                tags = buildNewMovieTags(result.getTitle());
            }
            else {
                tags = buildNewEpisodeTags(result.getTitle());
            }
        }

        log.debug("put in mTagsToSearchResultMap: "+tags);
        mTagsToSearchResultMap.put(tags, result);

        return tags;
    }

    /**
     * Update MediaDB and ScraperDB when a description is choosen
     * @param tags
     */
    @Override
    protected void saveTagsAndFinish(final BaseTags fTags) {
        final NovaProgressDialog progressDialog = new NovaProgressDialog(getActivity());
        progressDialog.setTitle(R.string.scrap_get_title);
        progressDialog.setMessage(getString(R.string.scrap_change_initializing));
        progressDialog.setProgressStyle(NovaProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);
        progressDialog.setMax(1);
        progressDialog.show();

        new Thread() {
            @Override
            public void run() {
                BaseTags tags = fTags;
                Bundle bundle = new Bundle();
                if (tags instanceof EpisodeTags) {
                    bundle.putInt(Scraper.ITEM_REQUEST_SEASON, ((EpisodeTags)tags).getSeason());
                    // this is required to get the season poster (episode does not have this information on tmdb)
                    //bundle.putInt(Scraper.ITEM_REQUEST_EPISODE, ((EpisodeTags)tags).getEpisode());
                    SearchResult sr = mTagsToSearchResultMap.get(tags); // Get the searchResult from the map we built for it
                    ScrapeDetailResult detail = Scraper.getDetails(sr, bundle);
                    if (detail.isOkay())
                        tags = detail.tag;
                }
        
                // since poster can be deleted again we refresh it here
                tags.downloadPoster(getActivity());

                // Nfo export does network access => thread
                if (NfoWriter.isNfoAutoExportEnabled(getActivity())) {
                    // also auto-export all the data
                    try {
                        NfoWriter.export(mVideo.getFileUri(), tags, null);
                    } catch (IOException e) {
                        log.error("IOException ", e);
                    }
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(1);
                        progressDialog.setMessage(getString(R.string.scrap_change_finalizing));
                    }
                });

                // saving will trigger database change notification and reloading in
                // VideoInfoActivity
                tags.save(getActivity(), mVideo.getId());
        
                // Update Trakt
                TraktService.onNewVideo(getActivity());
                
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        getActivity().finish();
                    }
                });
            }
        }.start();
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
}
