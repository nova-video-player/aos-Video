// Copyright 2026 Courville Software
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
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.adapters.GroupOfMovieAdapter;
import com.archos.mediacenter.video.browser.loader.VideosByListLoader;
import com.archos.mediacenter.video.utils.TraktSigninDialogPreference;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediaprovider.video.VideoStore;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.loader.content.Loader;
import androidx.appcompat.app.ActionBar;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

public class BrowserPlaylists extends BrowserMoviesBy {

    private boolean mHasLaunchedTrakt = false;

    private final ActivityResultLauncher<Intent> traktAuthLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) postBindAdapter();
                mHasLaunchedTrakt = false;
            });

    @Override
    public int getThumbnailsType() {
        return ThumbnailEngineVideo.TYPE_MOVIE_YEAR;
    }

    @Override
    protected Uri getCursorUri() {
        return VideoStore.RAW_QUERY;
    }

    @Override
    public int getEmptyMessage() {
        return R.string.no_list_detected;
    }

    @Override
    public int getEmptyViewButtonLabel() {
        return R.string.trakt_list_signin;
    }

    public boolean showEmptyViewButton() {
        return !Trakt.isTraktV2Enabled(null, PreferenceManager.getDefaultSharedPreferences(getActivity())) ;
    }

    protected boolean onEmptyviewButtonClick(){
        if(mHasLaunchedTrakt)
            return true;
        TraktSigninDialogPreference dialogPreference = new TraktSigninDialogPreference(getContext(), null);
        dialogPreference.setLauncher(traktAuthLauncher);
        dialogPreference.showDialog(true);
        mHasLaunchedTrakt = true;
        return true;
    }
    public void onResume(){
        super.onResume();
        ((MainActivity)getActivity()).setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }
    public void addSortOptionsSubmenus(ActionBarSubmenu submenu) {
	    // MENU_ITEM_NAME is not a typo here, because the year will be copied to the name column
	    submenu.addSubmenuItem(0, R.string.sort_by_date_desc, MENU_ITEM_SORT+MENU_ITEM_NAME+MENU_ITEM_DESC);
	    submenu.addSubmenuItem(0, R.string.sort_by_date_asc,  MENU_ITEM_SORT+MENU_ITEM_NAME+MENU_ITEM_ASC);
    }

    @Override
    protected String getDefaultSortOrder() {
        return COLUMN_NAME+" COLLATE LOCALIZED DESC";
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new VideosByListLoader(getContext(), mSortOrder).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesCommon.KEY_HIDE_WATCHED, false));
    }

    protected void completeNewFragmentBundle(Bundle args, int pos){
        Cursor cursor = ((GroupOfMovieAdapter)mBrowserAdapter).getCursor();
        if (cursor.getCount() > 0 && pos < cursor.getCount()) {
            cursor.moveToPosition(pos);
            args.putString(BrowserVideosInPlaylist.EXTRA_MAP_MOVIES, cursor.getString(cursor.getColumnIndex(VideosByListLoader.COLUMN_MAP_MOVIE_ID)));
            args.putString(BrowserVideosInPlaylist.EXTRA_MAP_EPISODES, cursor.getString(cursor.getColumnIndex(VideosByListLoader.COLUMN_MAP_EPISODE_ID)));
        }
        args.putLong(BrowserVideosInPlaylist.EXTRA_PLAYLIST_ID, mBrowserAdapter.getItemId(pos));
    }

    protected String getBrowserNameToInstantiate(){
        return BrowserVideosInPlaylist.class.getName();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        //rename will come later
        menu.add(0, R.string.delete, 0, R.string.delete);
        return;
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int index = item.getItemId();
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        long listId = mBrowserAdapter.getItemId(info.position);
        if (index == R.string.delete) {
            getActivity().getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.ID +" = ?", new String[]{listId+""});
        }
        return true;
    }
}
