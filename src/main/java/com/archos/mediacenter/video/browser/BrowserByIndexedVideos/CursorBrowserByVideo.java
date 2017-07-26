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
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;

import com.archos.filecorelibrary.MetaFile.FileType;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BrowserAdapterCommon;
import com.archos.mediacenter.video.browser.BrowserByVideoObjects;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.ThumbnailRequesterVideo;
import com.archos.mediacenter.video.browser.adapters.CursorAdapterByVideo;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterByCursor;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterInterface;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;

import java.io.File;

abstract public class CursorBrowserByVideo extends BrowserByVideoObjects implements
         LoaderManager.LoaderCallbacks<Cursor> {




    // Sort constants
	protected static final int MENU_ITEM_SORT = 0x1000;
	protected static final int MENU_ITEM_SORT_MASK = 0xF000;

	protected static final int MENU_ITEM_NAME = 0x10;
	protected static final int MENU_ITEM_YEAR = 0x20;
	protected static final int MENU_ITEM_DURATION = 0x30;
	protected static final int MENU_ITEM_RATING = 0x40;
	protected static final int MENU_ITEM_ADDED = 0x50;
	protected static final int MENU_ITEM_GENRE = 0x60;
	protected static final int MENU_ITEM_SORT_TYPE_MASK = 0xF0;

	protected static final int MENU_ITEM_ASC = 0x00;
	protected static final int MENU_ITEM_DESC = 0x01;
	protected static final int MENU_ITEM_SORT_ORDER_MASK = 0x0F;

    static final public String SUBCATEGORY_NAME = "subcategoryName";

    protected Cursor mCursor;
    protected String mTitle = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Try to retrieve the title to display in the action bar if there is
        // one.
        Bundle args = getArguments();
        if (args != null) {
            mTitle = args.getString(SUBCATEGORY_NAME);
        }
        mHideOption = true;
        mHideWatched = mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false);
    }



    static String concatArray(String[] a) {
        if (a==null) {
            return "null array";
        }
        String out="";
        for (String s : a) {
            out = out + s + " | ";
        }
        return out;
    }



    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if(loader.getId()==0) { //if was called with this fragment, not a child
            if (mCursor == null) {
                mCursor = cursor;
                bindAdapter();
            } else {
                boolean forceReloadUI = (mCursor.getCount() == 0 || cursor.getCount() == 0);
                mCursor = cursor;
                ((CursorAdapter) mBrowserAdapter).changeCursor(cursor);
                if (forceReloadUI) {
                    postBindAdapter();
                }
            }
        }
    }

    public void onLoaderReset(Loader<Cursor> loader) {
        ((CursorAdapter) mBrowserAdapter).swapCursor(null);
        mCursor = null;
    }
    protected void refresh(){
        mBrowserAdapter.notifyDataSetChanged();
    }
    @Override
    public void onResume() {
        super.onResume();
        if (mCursor != null) {
	        bindAdapter();
        }

        getLoaderManager().restartLoader(0, null, this);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(getActionBarTitle());
    }



    /**
     * Get the type that the mediaDb ID will be representing, i.e. the type of the thumbnails
     * @return
     */
    public int getThumbnailsType() {
        // Most BrowserById subclasses represents files, so here is the default implementation, but some subclasses will do something else
        return ThumbnailEngineVideo.TYPE_FILE;
    }

    @Override
    protected void setupThumbnail() {
        mThumbnailEngine.setThumbnailType(getThumbnailsType());
        mThumbnailRequester = new ThumbnailRequesterVideo(mThumbnailEngine, (PresenterAdapterByCursor) mBrowserAdapter);
    }

    @Override
    protected void setupAdapter(boolean createNewAdapter) {
        if (createNewAdapter || mBrowserAdapter == null) {
            mBrowserAdapter = new CursorAdapterByVideo(mContext, mCursor);
            BrowserByVideoObjects.setPresenters(getActivity(), this, (PresenterAdapterInterface) mBrowserAdapter, mViewMode);
        } else {
            PresenterAdapterByCursor adapter = (PresenterAdapterByCursor)mBrowserAdapter;
            adapter.setData(mCursor);
        }
    }


    @Override
    public void onItemClick(AdapterView parent, View v, final int position, long id) {
        if (!isItemClickable(position)||mMultiplePositionEnabled) {
            if(mActionModeManager!=null)
                mActionModeManager.invalidateActionBar();
            return;
        }

        super.onItemClick(parent, v, position, id);

    }
    @Override
    public boolean shouldDownloadRemoteSubtitle(int position){
        return ((PresenterAdapterByCursor)mBrowserAdapter).hasRemoteSubtitles(position);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mBrowserAdapter != null && (!mBrowserAdapter.isEmpty()||mHideWatched)) {
            if (Trakt.isTraktV2Enabled(mContext, PreferenceManager.getDefaultSharedPreferences(mContext))) {
                MenuItem hideMarkedSeen = menu.add(MENU_HIDE_WATCHED_GROUP, MENU_VIEW_HIDE_SEEN, Menu.NONE, mHideWatched ? R.string.hide_seen : R.string.show_all);
                hideMarkedSeen.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_VIEW_HIDE_SEEN){
            mHideWatched = !mHideWatched;
            item.setTitle(mHideWatched ? R.string.hide_seen : R.string.show_all);
            mPreferences.edit().putBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, mHideWatched).apply();
            getLoaderManager().restartLoader(0, null, this);
            return true;
        } else
            return super.onOptionsItemSelected(item);
    }

    @Override
    public FileType getFileType(int position) {
        // There are no directories and shortcuts in the view by id
        return FileType.File;
    }

    @Override
    public int getFileAndFolderSize() {
        return mBrowserAdapter.getCount();
    }

    @Override
    public int getFirstFilePosition() {
        return 0;
    }

    @Override
    public File getFile(int position) {
        String path = ((BrowserAdapterCommon) mBrowserAdapter).getPath(position);

        return new File(path);
    }

    abstract protected String getActionBarTitle();



}
