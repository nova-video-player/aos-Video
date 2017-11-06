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
import android.util.Log;

import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.NfoParser;
import com.archos.mediascraper.NfoWriter;
import com.archos.mediascraper.ScrapeDetailResult;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;

import java.io.IOException;

public class ManualVideoScrappingSearchFragment extends ManualScrappingSearchFragment {

    public static final String TAG = "ManualVideoScrappingSF";


    private Video mVideo;
    private SearchInfo mSearchInfo;


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
        mSearchInfo.setUserInput(text);
        return mScraper.getBestMatches(mSearchInfo, SEARCH_RESULT_MAX_ITEMS);
    }

    protected BaseTags getTagFromSearchResult(SearchResult result) {
        // Get the details for this match
        ScrapeDetailResult detail = mScraper.getDetails(result, null);
        BaseTags tags = detail.tag;

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
        return tags;
    }

    /**
     * Update MediaDB and ScraperDB when a description is choosen
     * @param tags
     */
    @Override
    protected void saveTagsAndFinish(final BaseTags tags) {
        // since poster can be deleted again we refresh it here
        tags.downloadPoster(getActivity());

        // saving will trigger database change notification and reloading in
        // VideoInfoActivity
        tags.save(getActivity(), mVideo.getId());

        // Update Trakt
        TraktService.onNewVideo(getActivity());

        // Nfo export does network access => thread
        if (NfoWriter.isNfoAutoExportEnabled(getActivity())) {
            new Thread() {
                @Override
                public void run() {
                    // also auto-export all the data
                    try {
                        NfoWriter.export(mVideo.getFileUri(), tags, null);
                    } catch (IOException e) {
                        Log.w(TAG, e);
                    }
                }
            }.start();
        }

        getActivity().finish();
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
