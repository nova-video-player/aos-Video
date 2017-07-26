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
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.loader.AllVideosLoader;
import com.archos.mediacenter.video.utils.SortOrder;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.AutoScrapeService;

public class BrowserAllVideos extends CursorBrowserByVideo {

    private static final String SELECTION = VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0";

	private static final String DEFAULT_SORT = "name COLLATE NOCASE ASC,"
            + VideoColumns.SCRAPER_E_SEASON + " ASC ,"
            + VideoColumns.SCRAPER_E_EPISODE + " ASC";

	static final String SORT_PARAM_KEY = BrowserAllVideos.class.getName()+"_SORT";

	private String mSortOrder = DEFAULT_SORT;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState!=null) {
			mSortOrder = savedInstanceState.getString(SORT_PARAM_KEY);
		}else if (getArguments()!=null && getArguments().getString(SORT_PARAM_KEY, null)!=null){
		    mSortOrder = getArguments().getString(SORT_PARAM_KEY, null);
		}
		else {
			mSortOrder = mPreferences.getString(SORT_PARAM_KEY, DEFAULT_SORT);
		}
	}

	@Override
	public void onDestroy() {
		// Save the sort mode
		mPreferences.edit()
		.putString(SORT_PARAM_KEY, mSortOrder)
		.commit();

		super.onDestroy();
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		state.putString(SORT_PARAM_KEY, mSortOrder);
	}




    @Override
    protected String getActionBarTitle() {
        return getString(R.string.all_videos);
    }

	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty() && mSortModeSubmenu!=null) {
			// Add the "sort mode" item
			MenuItem sortMenuItem = menu.add(Browser.MENU_VIEW_MODE_GROUP, Browser.MENU_VIEW_MODE, Menu.NONE, R.string.sort_mode);
			sortMenuItem.setIcon(R.drawable.ic_menu_sort);
			sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
			mSortModeSubmenu.attachMenuItem(sortMenuItem);

			mSortModeSubmenu.clear();
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_asc,      MENU_ITEM_SORT+MENU_ITEM_NAME    +MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_desc,     MENU_ITEM_SORT+MENU_ITEM_NAME    +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_asc,      MENU_ITEM_SORT+MENU_ITEM_YEAR    +MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_desc,     MENU_ITEM_SORT+MENU_ITEM_YEAR    +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_duration_asc,  MENU_ITEM_SORT+MENU_ITEM_DURATION+MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_duration_desc, MENU_ITEM_SORT+MENU_ITEM_DURATION+MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_rating_asc,    MENU_ITEM_SORT+MENU_ITEM_RATING  +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_added_desc,MENU_ITEM_SORT+MENU_ITEM_ADDED+MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_added_asc, MENU_ITEM_SORT+MENU_ITEM_ADDED+MENU_ITEM_ASC);

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
			menu.add(0,R.string.rescrap_not_found,0, R.string.rescrap_not_found).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		}
	}


	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.string.rescrap_not_found){
			Intent intent = new Intent(getActivity(),AutoScrapeService.class);
			intent.putExtra(AutoScrapeService.RESCAN_EVERYTHING, true);
			intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, true);
			getActivity().startService(intent);
			Toast.makeText(getActivity(), R.string.rescrap_in_progress, Toast.LENGTH_SHORT).show();
			return true;
		} else
			return super.onOptionsItemSelected(item);
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
	protected static final String COALESCE = "COALESCE(";
	private static String itemid2sortorder(int itemid) {

		String sortOrder = DEFAULT_SORT;
		boolean parseOrderAfterType = true;
		boolean isDesc = (itemid & MENU_ITEM_SORT_ORDER_MASK) == MENU_ITEM_DESC;

		switch (itemid & MENU_ITEM_SORT_TYPE_MASK) {
		// What is sorted
		case MENU_ITEM_NAME:
			String sSort = " ASC";
			if ((itemid & MENU_ITEM_SORT_ORDER_MASK) == MENU_ITEM_DESC) {
				sSort = " DESC";
			}
			sortOrder = "name COLLATE NOCASE" + sSort + ","
	            + VideoColumns.SCRAPER_E_SEASON + sSort + ","
	            + VideoColumns.SCRAPER_E_EPISODE + sSort;
			parseOrderAfterType = false; // sort order is already integrated in sortOrder this stage
			break;
		case MENU_ITEM_YEAR:
			sortOrder = COALESCE + "datetime("+													// Sort by either...
					VideoColumns.SCRAPER_E_AIRED+" / 1000, 'unixepoch'), " +					// episode aired time...
					"strftime('%Y-%m-%d', ("+VideoColumns.SCRAPER_M_YEAR+"||'-01-01')), " +     // or movie year...
					"datetime(" + MediaColumns.DATE_ADDED + ", 'unixepoch')" +					// or date file is added to the library
					")";
			break;
		case MENU_ITEM_DURATION:
		    sortOrder = SortOrder.DURATION.get(isDesc);
		    parseOrderAfterType = false;
			break;
		case MENU_ITEM_RATING:
		    sortOrder = SortOrder.SCRAPER_RATING.get(isDesc);
		    parseOrderAfterType = false;
			break;
		case MENU_ITEM_ADDED:
			sortOrder = MediaColumns.DATE_ADDED;
			break;
		}

		// Order of the sort
		if (parseOrderAfterType) { 
			switch (itemid & MENU_ITEM_SORT_ORDER_MASK) {
			case MENU_ITEM_ASC:
				sortOrder += " ASC";
				break;
			case MENU_ITEM_DESC:
				sortOrder += " DESC";
				break;
			}
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

		if (sortOrder.contains("name")) {
			itemId |= MENU_ITEM_NAME;
		}
		else if (sortOrder.contains(VideoColumns.SCRAPER_E_AIRED) && sortOrder.contains(VideoColumns.SCRAPER_M_YEAR)) {
			itemId |= MENU_ITEM_YEAR;
		}
		else if (sortOrder.contains(VideoColumns.DURATION)) {
			itemId |= MENU_ITEM_DURATION;
		}
		else if (sortOrder.contains(VideoColumns.SCRAPER_RATING)) {
			itemId |= MENU_ITEM_RATING;
		}
		else if (sortOrder.contains(MediaColumns.DATE_ADDED)) {
			itemId |= MENU_ITEM_ADDED;
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
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		return new AllVideosLoader(getContext(), mSortOrder).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
	}
}
