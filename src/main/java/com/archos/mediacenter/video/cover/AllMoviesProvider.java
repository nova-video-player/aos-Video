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

package com.archos.mediacenter.video.cover;


import android.content.Context;
import android.content.CursorLoader;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;

public class AllMoviesProvider extends VideoSingleCursorCoverProvider {

	private final static String TAG = "AllMoviesProvider";
	private final static boolean DBG = false;

	public AllMoviesProvider( Context context ) {
		super (context);
	}

	protected int getLoaderManagerId() {
	    return VIDEO_ALL_MOVIES_LOADER_ID;
	}

    /**
     *  Get the cursor loader for this provider
     */
    protected CursorLoader getCursorLoader() {
		return new MoviesLoader(mContext, false);
        //return LibraryUtils.getAllMoviesCursorLoader(mContext, MAXIMUM_NB_OF_VIDEOS);
    }

	@Override
	void setErrorMessage() {
        mErrorMessage = mContext.getResources().getString(R.string.you_have_no_movies);
        mErrorMessage += "\n\n" + mContext.getResources().getString(R.string.scraper_suggest_message);
    }
}
