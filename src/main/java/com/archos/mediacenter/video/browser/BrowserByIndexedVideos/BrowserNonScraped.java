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

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import androidx.core.view.MenuProvider;
import androidx.loader.content.Loader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.NonScrapedVideosLoader;
import com.archos.mediascraper.AutoScrapeService;


public class BrowserNonScraped extends CursorBrowserByVideo {
    //private static final boolean DBG = false;
    //private static final String TAG = "BrowserNonScraped";

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(Menu menu, MenuInflater menuInflater) {
                if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty()) {
                    menu.add(0, R.string.rescrap_not_found, 0, R.string.rescrap_not_found).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                }
            }
            @Override
            public boolean onMenuItemSelected(MenuItem item) {
                if (item.getItemId() == R.string.rescrap_not_found) {
                    Intent intent = new Intent(getActivity(), AutoScrapeService.class);
                    intent.putExtra(AutoScrapeService.RESCAN_EVERYTHING, true);
                    intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, true);
                    getActivity().startService(intent);
                    Toast.makeText(getActivity(), R.string.rescrap_in_progress, Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner());
    }

    @Override
    public int getEmptyMessage() {
        return R.string.you_have_no_non_scraped_videos;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.non_scraped_videos);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new NonScrapedVideosLoader(getContext()).getV4CursorLoader(false, false);
    }
}
