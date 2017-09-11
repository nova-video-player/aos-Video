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


package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.MoviesSelectionLoader;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediaprovider.video.VideoStore;

public class BrowserAllMovies extends BrowserByVideoSelection {
	public static final String SELECTION_ALL_MOVIES = VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL";

	@Override
	public int getEmptyMessage() {
		return R.string.scraper_no_movie_text;
	}

	@Override
	public int getEmptyViewButtonLabel() {
		return R.string.scraper_no_movie_button_label;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args2) {
		if(getArguments()!=null){
			String listOfMoviesIds = getArguments().getString(BrowserAllMovies.LIST_OF_IDS);
			if (listOfMoviesIds != null)
				return new MoviesSelectionLoader(getContext(), listOfMoviesIds, mSortOrder).getV4CursorLoader(true, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
		}
		return new MoviesLoader(getContext(), mSortOrder, true).getV4CursorLoader(true, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
	}
}
