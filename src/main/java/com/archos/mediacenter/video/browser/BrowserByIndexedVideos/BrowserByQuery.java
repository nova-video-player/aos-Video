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

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.SearchVideoLoader;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediacenter.video.utils.VideoUtils;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class BrowserByQuery extends CursorBrowserByVideo {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        // Enable the type filter window (display the filtering string on top of the browser)
        String filterString = "";
        Bundle args = getArguments();
        if (args != null) {
            filterString = args.getString("filter_string", "");
        }
        mArchosGridView.setTextFilterEnabled(true);
        mArchosGridView.setFilterText(filterString);

        return v;
    }


    @Override
    public int getDefaultViewMode() {
        return VideoUtils.VIEW_MODE_LIST;
    }

    @Override
    public int getEmptyMessage() {
        return R.string.you_have_no_video;
    }

    @Override
    public int getEmptyViewButtonLabel() {
        return 0;
    }

    public boolean showEmptyViewButton() {
        return false;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.video);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args2) {
        String filterString = "";
        Bundle args = getArguments();
        if (args != null) {
            filterString = args.getString("filter_string", "");
        }
        SearchVideoLoader loader = new SearchVideoLoader(getContext());
        loader.setQuery(filterString);
        return loader.getV4CursorLoader(true, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
    }
}
