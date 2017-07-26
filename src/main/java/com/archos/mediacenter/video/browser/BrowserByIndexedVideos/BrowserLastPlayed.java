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
import com.archos.mediacenter.video.browser.loader.LastPlayedLoader;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediaprovider.video.VideoStore;


public class BrowserLastPlayed extends CursorBrowserByVideo {

    private static final String SELECT_LAST_PLAYED = VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED
            + "!=0";
    private static final String SORT_LAST_PLAYED = VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED
            + " DESC LIMIT 17";



    @Override
    public int getEmptyMessage() {
        return R.string.you_have_no_recently_played_video;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.recently_played_videos);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new LastPlayedLoader(getContext()).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
    }
}
