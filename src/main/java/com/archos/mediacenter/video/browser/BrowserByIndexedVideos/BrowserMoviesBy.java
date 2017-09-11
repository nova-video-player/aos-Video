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
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.ThumbnailRequestVideo;
import com.archos.mediacenter.video.browser.ThumbnailRequesterVideo;
import com.archos.mediacenter.video.browser.adapters.GroupOfMovieAdapter;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.util.ArrayList;

public abstract class BrowserMoviesBy extends CursorBrowserByVideo implements LoaderManager.LoaderCallbacks<Cursor> {

    /**
     * The column in which we copy the name to display. Can be the name of the genre or the "name" for the year
     */
    public static final String COLUMN_NAME = "name";

    public static final String COLUMN_LIST_OF_MOVIE_IDS = "list";
    public static final String COLUMN_NUMBER_OF_MOVIES = "number";
    public static final String COLUMN_LIST_OF_POSTER_FILES = "po_file_list";

	protected String mSortOrder = getDefaultSortOrder();

	protected String getSortOrderParamKey() {
	    return getClass().getName()+"_SORT";
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState!=null) {
			mSortOrder = savedInstanceState.getString(getSortOrderParamKey());
		}
		else {
			mSortOrder = mPreferences.getString(getSortOrderParamKey(), getDefaultSortOrder());
		}
	}

	@Override
	public void onResume() {
		((MainActivity)getActivity()).setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		super.onResume();
	}
	
	@Override
	public void onDestroy() {
		// Save the sort mode
		mPreferences.edit()
		.putString(getSortOrderParamKey(), mSortOrder)
		.commit();

		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(getSortOrderParamKey(), mSortOrder);
	}

    @Override
    public int getDefaultViewMode() {
        return VideoUtils.VIEW_MODE_GRID;
    }



    abstract protected String getDefaultSortOrder();

    @Override
    public String getActionBarTitle() {
        return ""; // no title because there is the NAVIGATION_MODE_LIST list at this place instead
    }

	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty() && mSortModeSubmenu!=null) {
            // Add the "view mode" item
            MenuItem viewModeMenuItem = menu.add(Browser.MENU_VIEW_MODE_GROUP, Browser.MENU_VIEW_MODE, Menu.NONE, R.string.view_mode);
            viewModeMenuItem.setIcon(R.drawable.ic_menu_view_mode);
            viewModeMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            mDisplayModeSubmenu.attachMenuItem(viewModeMenuItem);

            mDisplayModeSubmenu.clear();
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_list_mode2, R.string.view_mode_list, 0);
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_poster_mode, R.string.view_mode_grid, 0);
            // no Details view mode here
            mDisplayModeSubmenu.selectSubmenuItem(getSubmenuItemIndex(mViewMode));

			// Add the "sort mode" item
			MenuItem sortMenuItem = menu.add(Browser.MENU_VIEW_MODE_GROUP, Browser.MENU_VIEW_MODE, Menu.NONE, R.string.sort_mode);
			sortMenuItem.setIcon(R.drawable.ic_menu_sort);
			sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			mSortModeSubmenu.attachMenuItem(sortMenuItem);

			
			mSortModeSubmenu.clear();
			addSortOptionsSubmenus(mSortModeSubmenu);

			// Init with the current value
			int initId = sortorder2itemid(mSortOrder);
			if (initId==-1) { // not found
				mSortModeSubmenu.selectSubmenuItem(0);
			}
			else {
				int position = mSortModeSubmenu.getPosition(initId);
				if (position<0) { // not found
				    position=0;
				}
				mSortModeSubmenu.selectSubmenuItem(position);
			}
		}
	}
	
	abstract public void addSortOptionsSubmenus(ActionBarSubmenu submenu);

	abstract protected Uri getCursorUri();


    public void setupThumbnail() {
        mThumbnailEngine.setThumbnailType(getThumbnailsType());
        mThumbnailRequester = new ThumbnailRequesterVideo(mThumbnailEngine,
                (GroupOfMovieAdapter) mBrowserAdapter);
        mThumbnailEngine.setThumbnailSize(
                getResources().getDimensionPixelSize(R.dimen.video_grid_poster_width),
                getResources().getDimensionPixelSize(R.dimen.video_grid_poster_height));
    }

    @Override
    protected void setupAdapter(boolean createNewAdapter) {
        if (createNewAdapter || mBrowserAdapter == null) {
            mBrowserAdapter = new GroupOfMovieAdapter(getActivity().getApplicationContext(), mThumbnailEngine, mCursor, mViewMode);
        } else {
            GroupOfMovieAdapter adapter = (GroupOfMovieAdapter)mBrowserAdapter;
            adapter.setData(mCursor, mViewMode);
        }
    }

	@Override
	public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
		if (submenu==mSortModeSubmenu) {
			if ((itemId & MENU_ITEM_SORT_MASK)==MENU_ITEM_SORT) {
				mSortOrder = itemid2sortorder((int)itemId);
				// It's not enough to call notifyDataSetChanged() here to have the sort mode changed, must reset at Loader level.
				getLoaderManager().restartLoader(0, null, this);
			}
		}
		else {
			super.onSubmenuItemSelected(submenu, position, itemId);
		}
	}

	private String itemid2sortorder(int itemid) {

		String sortOrder = getDefaultSortOrder();

		switch (itemid & MENU_ITEM_SORT_TYPE_MASK) {
		// What is sorted
		case MENU_ITEM_NAME:
            sortOrder = COLUMN_NAME + " COLLATE NOCASE";
            break;
		}

		// Order of the sort
		switch (itemid & MENU_ITEM_SORT_ORDER_MASK) {
			case MENU_ITEM_ASC:
				sortOrder += " ASC";
				break;
			case MENU_ITEM_DESC:
				sortOrder += " DESC";
				break;
		}

		Log.d(Browser.TAG, "itemid2sortorder: sortOrder="+sortOrder);
		return sortOrder;
	}

	/**
	 * Returns -1 if given sortOrder can't be found in the menuid list
	 * @param sortOrder
	 * @return
	 */
	private static int sortorder2itemid(String sortOrder) {
		int itemId = MENU_ITEM_SORT;

		if (sortOrder.contains(COLUMN_NAME)) {
			itemId |= MENU_ITEM_NAME;
		}
		else {
			return -1; // better return an error in case we don't manage to find what is the current settings (it may be not supported anymore)
		}

		if (sortOrder.contains("ASC")) {
			itemId |= MENU_ITEM_ASC;
		} else if (sortOrder.contains("DESC")) {
			itemId |= MENU_ITEM_DESC;
		} else {
			return -1; // better return an error in case we don't manage to find what is the current settings (it may be not supported anymore)
		}

		return itemId;
	}

	@Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
	    // Prepare the list of movies and the title, to be given to the opened fragment
        Bundle args = new Bundle(2);
        args.putString(BrowserByVideoSelection.LIST_OF_IDS, ((GroupOfMovieAdapter)mBrowserAdapter).getListOfMoviesIds(position));
        Log.d(Browser.TAG, "onItemClick: Selection: "+((GroupOfMovieAdapter)mBrowserAdapter).getListOfMoviesIds(position));
        args.putString(CursorBrowserByVideo.SUBCATEGORY_NAME, ((GroupOfMovieAdapter)mBrowserAdapter).getName(position));
		completeNewFragmentBundle(args, position);
        //Load fragment
        BrowserCategory category = (BrowserCategory) getFragmentManager().findFragmentById(R.id.category);
        Fragment newfragment = Fragment.instantiate(getActivity().getApplicationContext(), getBrowserNameToInstantiate(), args);
        category.startContent(newfragment);
        
        // Remove the navigation drop down from the actionbar when opening a child fragment
		((MainActivity)getActivity()).setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    }

	protected void completeNewFragmentBundle(Bundle args, int pos) {

	}

	protected String getBrowserNameToInstantiate(){
		return BrowserAllMovies.class.getName();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
	    //none
	    return;
	}

    @Override
    public int getEmptyMessage() {
        return R.string.scraper_no_movie_text;
    }

    @Override
    public int getEmptyViewButtonLabel() {
        return R.string.scraper_no_movie_button_label;
    }

    public boolean showEmptyViewButton() {
        return true;
    }

    public static ThumbnailRequestVideo getMultiposterThumbnailRequest(Cursor c, int position, long id) {
		if(c==null)
			return null;
        if (c.moveToPosition(position)) {
            String posterFileListString = c.getString(c.getColumnIndexOrThrow(COLUMN_LIST_OF_POSTER_FILES));
            if (posterFileListString==null) {
                return null;
            }

            ArrayList<String> posterFileSelection;
            String[] posterFileList = posterFileListString.split(",", 20); // be reasonnable, limit to a selection among the 20 first
            // 4 posters or less, just keep them all
            if (posterFileList.length<=4) {
                posterFileSelection = new ArrayList<String>(posterFileList.length);
                for (String s : posterFileList) {
                    posterFileSelection.add(s);
                }
            }
            // more than 4: don't take the first ones, else you will get the same posters for several categories
            else {
                posterFileSelection = new ArrayList<String>(4);
                posterFileSelection.add(posterFileList[(int)(posterFileList.length*1/5f)]);
                posterFileSelection.add(posterFileList[(int)(posterFileList.length*2/5f)]);
                posterFileSelection.add(posterFileList[(int)(posterFileList.length*3/5f)]);
                posterFileSelection.add(posterFileList[(int)(posterFileList.length*4/5f)]);
            }

            ThumbnailRequestVideo rv = new ThumbnailRequestVideo( position, id, posterFileSelection);
            return rv;
        }
        return null;
    }
}
