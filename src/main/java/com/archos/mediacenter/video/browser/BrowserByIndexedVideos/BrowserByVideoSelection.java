
package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import static com.archos.mediacenter.video.browser.MainActivity.MENU_START_AUTO_SCRAPER_ACTIVITY;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuItemCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediacenter.video.utils.CustomTypefaceSpan;
import com.archos.mediacenter.video.utils.SortOrder;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.MediaColumns;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;

public class BrowserByVideoSelection extends CursorBrowserByVideo {

    private static final boolean DBG = false;
    private static final String TAG = "BrowserByVideoSelection";

	public static final String SELECTION_ALL_MOVIES = VideoStore.Video.VideoColumns.SCRAPER_MOVIE_ID + " IS NOT NULL";

	public static final String DEFAULT_SORT = "name COLLATE LOCALIZED";

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
        mHideWatched = mPreferences.getBoolean(VideoPreferencesCommon.KEY_HIDE_WATCHED,false);
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

	private void attachCustomTooltip(View anchorView, String message) {
		anchorView.setOnLongClickListener(v -> {
			Context context = v.getContext();
			Toast toast = new Toast(context);

			TextView textView = new TextView(context);
			textView.setText(message);
			textView.setTextColor(Color.WHITE);
			textView.setBackgroundResource(R.drawable.menu_bg);
			textView.setPadding(24, 16, 24, 16);
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
			textView.setTypeface(ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd));
			textView.setGravity(Gravity.CENTER);

			// Measure the textView to get width
			textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
			int tooltipWidth = textView.getMeasuredWidth();

			toast.setView(textView);

			// Get location of the anchor view
			int[] location = new int[2];
			v.getLocationOnScreen(location);
			int anchorX = location[0];
			int anchorY = location[1];

			int viewWidth = v.getWidth();
			int centerX = anchorX + viewWidth / 2;

			// Position toast so it's centered horizontally below the anchor
			int xOffset = centerX - tooltipWidth / 2;
			int yOffset = anchorY + v.getHeight() + 16; // distance below the view

			toast.setGravity(Gravity.TOP | Gravity.START, xOffset, yOffset);
			toast.setDuration(Toast.LENGTH_SHORT);
			toast.show();

			return true;
		});
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		// Manage sort mode menu item visibility according to search info online (scrape button) visibility in portrait mode
		MenuItem sortMenuItem = menu.findItem(Browser.MENU_SORT_MODE);
		MenuItem scraperItem = menu.findItem(MENU_START_AUTO_SCRAPER_ACTIVITY);
		boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
		if (scraperItem != null && scraperItem.isVisible() && isPortrait) {
			if (sortMenuItem != null) sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		} else {
			if (sortMenuItem != null) sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		}
	}

	@SuppressLint("RestrictedApi")
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty() && mSortModeSubmenu!=null) {
			// Add the "sort mode" item
			MenuItem sortMenuItem = menu.add(Browser.MENU_VIEW_MODE_GROUP, Browser.MENU_SORT_MODE, Menu.NONE, applyCustomFont(R.string.sort_mode));
			sortMenuItem.setIcon(R.drawable.ic_menu_sort);
			sortMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			mSortModeSubmenu.attachMenuItem(sortMenuItem);

			Toolbar toolbar = requireActivity().findViewById(R.id.main_toolbar);
			String sortTitle = getString(R.string.sort_mode);

			ViewTreeObserver observer = toolbar.getViewTreeObserver();
			observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					if (!isAdded()) return;

					boolean sortTooltipSet = false;

					for (int i = 0; i < toolbar.getChildCount(); i++) {
						View child = toolbar.getChildAt(i);
						if (child instanceof ActionMenuView) {
							ActionMenuView menuView = (ActionMenuView) child;
							for (int j = 0; j < menuView.getChildCount(); j++) {
								View itemView = menuView.getChildAt(j);
								if (itemView instanceof ActionMenuItemView) {
									CharSequence title = ((ActionMenuItemView) itemView).getItemData().getTitle();
									if (title != null) {
										String titleStr = title.toString();
										if (!sortTooltipSet && titleStr.equalsIgnoreCase(sortTitle)) {
											attachCustomTooltip(itemView, sortTitle);
											sortTooltipSet = true;
										}
									}
								}
							}
						}
					}

					// Once both are set, remove listener
					if (sortTooltipSet) {
						toolbar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					}
				}
			});

			mSortModeSubmenu.clear();
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_name_asc),       MENU_ITEM_SORT + MENU_ITEM_NAME     + MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_name_desc),      MENU_ITEM_SORT + MENU_ITEM_NAME     + MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_year_asc),       MENU_ITEM_SORT + MENU_ITEM_YEAR     + MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_year_desc),      MENU_ITEM_SORT + MENU_ITEM_YEAR     + MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_duration_asc),   MENU_ITEM_SORT + MENU_ITEM_DURATION + MENU_ITEM_ASC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_duration_desc),  MENU_ITEM_SORT + MENU_ITEM_DURATION + MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_rating_asc),     MENU_ITEM_SORT + MENU_ITEM_RATING   + MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_date_added_desc),MENU_ITEM_SORT + MENU_ITEM_ADDED    + MENU_ITEM_DESC);
			mSortModeSubmenu.addSubmenuItem(0, applyCustomFont(R.string.sort_by_date_added_asc), MENU_ITEM_SORT + MENU_ITEM_ADDED    + MENU_ITEM_ASC);

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

	private SpannableString applyCustomFont(@StringRes int resId) {
		String family ="";
		Typeface typeface = ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd);
		int color = ContextCompat.getColor(mContext, android.R.color.white);
		float textSize = 18f; // in SP
		String text = mContext.getString(resId);
		SpannableString spannable = new SpannableString(text);
		spannable.setSpan(new CustomTypefaceSpan(family, typeface, textSize, color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		return spannable;
	}

	@Override
	public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
		if (submenu==mSortModeSubmenu) {
			if ((itemId & MENU_ITEM_SORT_MASK)==MENU_ITEM_SORT) {
				mSortOrder = itemid2sortorder((int)itemId);
				// It's not enough to call notifyDataSetChanged() here to have the sort mode changed, must reset at Loader level. 
				LoaderManager.getInstance(this).restartLoader(0, null, this);
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
			sortOrder = "name COLLATE LOCALIZED";
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

		if (DBG) Log.d(TAG, "itemid2sortorder: sortOrder="+sortOrder);
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
				return new VideosSelectionLoader(getContext(), listOfMoviesIds, mSortOrder).getV4CursorLoader(true, mPreferences.getBoolean(VideoPreferencesCommon.KEY_HIDE_WATCHED, false));
		}
		return null;
	}
}
