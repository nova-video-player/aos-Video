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

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.loader.MoviesByGenreLoader;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediaprovider.video.VideoStore;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.Loader;

public class BrowserMoviesByGenre2 extends BrowserMoviesBy {

    @Override
    public int getThumbnailsType() {
        return ThumbnailEngineVideo.TYPE_MOVIE_GENRE;
    }

    @Override
    protected Uri getCursorUri() {
        return VideoStore.RAW_QUERY;
    }



    public void addSortOptionsSubmenus(ActionBarSubmenu submenu) {
        // MENU_ITEM_NAME is not a typo here, because the genre name will be copied to the name column
        submenu.addSubmenuItem(0, R.string.sort_by_genre_asc,  MENU_ITEM_SORT+MENU_ITEM_NAME+MENU_ITEM_ASC);
        submenu.addSubmenuItem(0, R.string.sort_by_genre_desc, MENU_ITEM_SORT+MENU_ITEM_NAME+MENU_ITEM_DESC);
    }

    @Override
    protected String getDefaultSortOrder() {
        return COLUMN_NAME+" COLLATE NOCASE ASC";
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new MoviesByGenreLoader(getContext(), mSortOrder).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
    }
}
