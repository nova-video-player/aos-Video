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
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.video.browser.adapters.AllTvShowsAdapter;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterByCursor;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.loader.AllTvshowsLoader;
import com.archos.mediacenter.video.browser.presenter.TvshowDetailedPresenter;
import com.archos.mediacenter.video.browser.presenter.TvshowGridPresenter;
import com.archos.mediacenter.video.browser.presenter.TvshowGridShortPresenter;
import com.archos.mediacenter.video.browser.presenter.TvshowListPresenter;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

import java.util.ArrayList;

public class BrowserAllTvShows extends CursorBrowserByVideo {

    static final public String SELECTION = VideoColumns.SCRAPER_SHOW_ID + " > '0') GROUP BY (" +VideoColumns.SCRAPER_SHOW_ID;
    static final public String SELECTION_HIDE_WATCHED = VideoColumns.SCRAPER_SHOW_ID + " > '0') AND "+VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " != "+Trakt.TRAKT_DB_MARKED+" GROUP BY (" +VideoColumns.SCRAPER_SHOW_ID;
	
	static final public String DEFAULT_SORT = VideoColumns.SCRAPER_TITLE;

	static final String SORT_PARAM_KEY = BrowserAllTvShows.class.getName()+"_SORT";

	private String mSortOrder = DEFAULT_SORT;

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
		mHideOption = mPreferences.getBoolean(VideoPreferencesFragment.KEY_TRAKT_SYNC_COLLECTION, false) 
		        || mPreferences.getBoolean(VideoPreferencesFragment.KEY_TRAKT_LIVE_SCROBBLING, false);
        if (!mHideOption){
            mHideWatched = false;
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

	public boolean shouldEnableMultiSelection(){
		return false;
	}

    @Override
    public int getDefaultViewMode() {
        return VideoUtils.VIEW_MODE_GRID;
    }

    @Override
    public String getActionBarTitle() {
        return getString(R.string.all_tv_shows);
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
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_asc,              MENU_ITEM_SORT+MENU_ITEM_NAME    +MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_name_desc,             MENU_ITEM_SORT+MENU_ITEM_NAME    +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_premiered_desc,   MENU_ITEM_SORT+MENU_ITEM_YEAR    +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_date_premiered_asc,    MENU_ITEM_SORT+MENU_ITEM_YEAR    +MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_rating_asc,            MENU_ITEM_SORT+MENU_ITEM_RATING  +MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, R.string.sort_by_recently_added_episode_desc, MENU_ITEM_SORT+MENU_ITEM_ADDED+MENU_ITEM_DESC);

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


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		//menu.add(0, R.string.delete, 0, R.string.delete);
		menu.add(0, R.string.info, 0, R.string.info);
		// Subloader
		//menu.add(0, R.string.get_subtitles_online, 0, R.string.get_subtitles_online);
		//menu.add(0, R.string.video_browser_unindex_file, 0, R.string.video_browser_unindex_file);


		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		Tvshow tvshow = (Tvshow) mBrowserAdapter.getItem(info.position);
		Cursor cursor2 = getEpisodeForShowCursor(tvshow.getTvshowId());
		boolean distant = false;
		if(cursor2!=null) {
			if (cursor2.getCount() > 0) {
				cursor2.moveToFirst();
				int uri = cursor2.getColumnIndex(VideoStore.MediaColumns.DATA);
				do {
					if (!FileUtils.isLocal(Uri.parse(cursor2.getString(uri)))) {
						distant = true;
					}
				} while (cursor2.moveToNext() && !distant);

			}
			cursor2.close();
		}

		if(distant)
			menu.add(0, R.string.copy_on_device_multi, 0, R.string.copy_on_device_multi);
	}
	private Cursor getEpisodeForShowCursor(long showId){
		String [] projection2 = new String[]{VideoStore.MediaColumns.DATA,VideoStore.Files.FileColumns._ID};
		String []  args2 = new String[]{String.valueOf(showId)};
		String selection2 = VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID+" = ? AND "+ LoaderUtils.HIDE_USER_HIDDEN_FILTER;
		return getContext().getContentResolver().query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, projection2, selection2, args2, VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE);

	}
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if(item.getItemId() ==  R.string.copy_on_device_multi){
			AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
			Tvshow tvshow = (Tvshow) mBrowserAdapter.getItem(info.position);
			ArrayList<Uri> list = new ArrayList<>();
			Cursor cursor2 = getEpisodeForShowCursor(tvshow.getTvshowId());
			if(cursor2!=null) {
				if (cursor2.getCount() > 0) {
					cursor2.moveToFirst();
					int uriCol = cursor2.getColumnIndex(VideoStore.MediaColumns.DATA);
					do {
						Uri uri = Uri.parse(cursor2.getString(uriCol));
						if (!FileUtils.isLocal(uri))
							list.add(uri);
					} while (cursor2.moveToNext());
					startDownloadingVideo(list);

				}
			}
			cursor2.close();
			return  true;
		}
		else return super.onContextItemSelected(item);

	}

	public Uri getRealPathUriFromPosition(int position){

		mCursor.moveToPosition(position);
		return Uri.parse(mCursor.getString(mCursor.getColumnIndex(VideoStore.MediaColumns.DATA)));
	}
	private static String itemid2sortorder(int itemid) {

		String sortOrder = DEFAULT_SORT;

		switch (itemid & MENU_ITEM_SORT_TYPE_MASK) {
		// What is sorted
		case MENU_ITEM_NAME:
			sortOrder = VideoColumns.SCRAPER_TITLE;
			break;
		case MENU_ITEM_YEAR:
			sortOrder = VideoColumns.SCRAPER_S_PREMIERED;
			break;
		case MENU_ITEM_RATING:
			sortOrder = "IFNULL("+VideoColumns.SCRAPER_S_RATING+", 0)";	// assign rating zero to the non-scraped files
			break;
		case MENU_ITEM_ADDED:
			sortOrder = AllTvshowsLoader.SORT_COUMN_LAST_ADDED;
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

		if (sortOrder.contains(VideoColumns.SCRAPER_TITLE)) {
			itemId |= MENU_ITEM_NAME;
		}
		else if (sortOrder.contains(VideoColumns.SCRAPER_S_PREMIERED)) {
			itemId |= MENU_ITEM_YEAR;
		}
		else if (sortOrder.contains(VideoColumns.SCRAPER_S_RATING)) {
			itemId |= MENU_ITEM_RATING;
		}
		else if (sortOrder.contains(AllTvshowsLoader.SORT_COUMN_LAST_ADDED)) {
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
	protected void setupAdapter(boolean createNewAdapter) {
		if (createNewAdapter || mBrowserAdapter == null) {
			mBrowserAdapter = new AllTvShowsAdapter(mContext, mCursor);
			if(mViewMode== VideoUtils.VIEW_MODE_GRID || mViewMode== VideoUtils.VIEW_MODE_GRID_SHORT) {
				((PresenterAdapterByCursor)mBrowserAdapter).setPresenter(Tvshow.class, new TvshowGridPresenter(getActivity(), this));
			}
			else if(mViewMode== VideoUtils.VIEW_MODE_GRID_SHORT) {
				((PresenterAdapterByCursor)mBrowserAdapter).setPresenter(Tvshow.class, new TvshowGridShortPresenter(getActivity(), this));
			}
			else if(mViewMode== VideoUtils.VIEW_MODE_DETAILS) {
				((PresenterAdapterByCursor)mBrowserAdapter).setPresenter(Tvshow.class, new TvshowDetailedPresenter(getActivity(), this));
			}
			else {
				((PresenterAdapterByCursor)mBrowserAdapter).setPresenter(Tvshow.class, new TvshowListPresenter(getActivity(), this));
			}
		} else {
			PresenterAdapterByCursor adapter = (PresenterAdapterByCursor)mBrowserAdapter;
			adapter.setData(mCursor);
		}
	}


    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
    	if(mMultiplePositionEnabled){
    		super.onItemClick(parent, v, position, id);
    		return;
    	}
		Tvshow tvshow = (Tvshow) mBrowserAdapter.getItem(position);
        Bundle args;
		args = new Bundle(3);
		int seasonColumn = mCursor
				.getColumnIndex(VideoColumns.SCRAPER_E_SEASON);
		args.putInt(VideoColumns.SCRAPER_E_SEASON,
				mCursor.getInt(seasonColumn));
		args.putString(SUBCATEGORY_NAME, tvshow.getName());
		args.putSerializable(BrowserListOfSeasons.EXTRA_SHOW_ITEM, tvshow);
        args.putLong(VideoColumns.SCRAPER_SHOW_ID,
				tvshow.getTvshowId());

        Fragment f = null;
		if(tvshow.getSeasonCount()>1)
			f = Fragment.instantiate(mContext, BrowserListOfSeasons.class.getName(), args);
		else
			f = Fragment.instantiate(mContext, BrowserListOfEpisodes.class.getName(), args);
        BrowserCategory category = (BrowserCategory) getFragmentManager().findFragmentById(
                R.id.category);
        category.startContent(f);

        mSelectedPosition=position;
    }

    @Override
    public int getEmptyMessage() {
        return R.string.scraper_no_tv_show_text;
    }

    @Override
    public int getEmptyViewButtonLabel() {
        return R.string.scraper_no_tv_show_button_label;
    }

    public boolean showEmptyViewButton() {
        return true;
    }

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new AllTvshowsLoader(getContext(), mSortOrder).getV4CursorLoader(false, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
	}



}
