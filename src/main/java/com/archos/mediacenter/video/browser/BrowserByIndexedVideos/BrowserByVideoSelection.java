
package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediacenter.video.utils.SortOrder;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

public class BrowserByVideoSelection extends CursorBrowserByVideo {

	public static final String SELECTION_ALL_MOVIES = VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL";

	public static final String DEFAULT_SORT = "name COLLATE NOCASE";

	static final String SORT_PARAM_KEY = BrowserByVideoSelection.class.getName()+"_SORT";

	// To be put in args to select only a subset of the movies
	static final public String LIST_OF_IDS = "ListOfMovieIds";

	protected String mSortOrder = DEFAULT_SORT;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState!=null) {
			mSortOrder = savedInstanceState.getString(SORT_PARAM_KEY);
		}
        else if (getArguments()!=null && getArguments().getString(SORT_PARAM_KEY, null)!=null){
            mSortOrder = getArguments().getString(SORT_PARAM_KEY, null);
        }
		else {
			mSortOrder = mPreferences.getString(SORT_PARAM_KEY, DEFAULT_SORT);
		}

		mTitle = null; // no default title because there may be the NAVIGATION_MODE_LIST list at this place instead
		Bundle args = getArguments();
		if (args != null) {
		    mTitle = args.getString(CursorBrowserByVideo.SUBCATEGORY_NAME);
		}
		mHideOption = true;
        mHideWatched = mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED,false);
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
	public int getDefaultViewMode() {
		return VideoUtils.VIEW_MODE_GRID;
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

	@Override
	protected String getActionBarTitle() {
	    return mTitle;
	}

	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty() && mSortModeSubmenu!=null) {
			// Add the "sort mode" item
			MenuItem sortMenuItem = menu.add(Browser.MENU_VIEW_MODE_GROUP, Browser.MENU_VIEW_MODE, Menu.NONE, R.string.sort_mode);
			sortMenuItem.setIcon(R.drawable.ic_menu_sort);
			sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			mSortModeSubmenu.attachMenuItem(sortMenuItem);

			mSortModeSubmenu.clear();
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_asc,      MENU_ITEM_SORT+MENU_ITEM_NAME    +MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_desc,     MENU_ITEM_SORT+MENU_ITEM_NAME    +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_year_asc,      MENU_ITEM_SORT+MENU_ITEM_YEAR    +MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_year_desc,     MENU_ITEM_SORT+MENU_ITEM_YEAR    +MENU_ITEM_DESC);
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

	private static String itemid2sortorder(int itemid) {

		String sortOrder = DEFAULT_SORT;
        boolean parseOrderAfterType = true;
        boolean isDesc = (itemid & MENU_ITEM_SORT_ORDER_MASK) == MENU_ITEM_DESC;

		switch (itemid & MENU_ITEM_SORT_TYPE_MASK) {
		// What is sorted
		case MENU_ITEM_NAME:
			sortOrder = "name COLLATE NOCASE";
			break;
		case MENU_ITEM_YEAR:
			sortOrder = VideoColumns.SCRAPER_M_YEAR;
			break;
		case MENU_ITEM_DURATION:
		    sortOrder = SortOrder.DURATION.get(isDesc);
		    parseOrderAfterType = false;
			break;
		case MENU_ITEM_RATING:
		    sortOrder = SortOrder.SCRAPER_M_RATING.get(isDesc);
		    parseOrderAfterType = false;
			break;
		case MENU_ITEM_ADDED:
			sortOrder = MediaColumns.DATE_ADDED;
			break;
		}

		if (parseOrderAfterType) {
		    // Order of the sort
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
		else if (sortOrder.contains(VideoColumns.SCRAPER_M_YEAR)) {
			itemId |= MENU_ITEM_YEAR;
		}
		else if (sortOrder.contains(VideoColumns.DURATION)) {
			itemId |= MENU_ITEM_DURATION;
		}
		else if (sortOrder.contains(VideoColumns.SCRAPER_M_RATING)) {
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
	public Loader<Cursor> onCreateLoader(int id, Bundle args2) {
		if(getArguments()!=null){
			String listOfMoviesIds = getArguments().getString(BrowserByVideoSelection.LIST_OF_IDS);
			if (listOfMoviesIds != null)
				return new VideosSelectionLoader(getContext(), listOfMoviesIds, mSortOrder).getV4CursorLoader(true, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
		}
		return null;
	}
}
