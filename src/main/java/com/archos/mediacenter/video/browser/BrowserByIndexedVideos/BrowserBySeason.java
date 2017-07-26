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
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterByCursor;
import com.archos.mediacenter.video.browser.adapters.SeasonsAdapter;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.browser.presenter.SeasonGridPresenter;
import com.archos.mediacenter.video.browser.presenter.SeasonGridShortPresenter;
import com.archos.mediacenter.video.browser.presenter.SeasonListPresenter;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;

public class BrowserBySeason extends BrowserWithShowHeader  {


    static final private String BROWSER_SHOW = BrowserByShow.class.getName();
    public static final String EXTRA_SHOW_ITEM = "show_item";
    private final static int SUBMENU_ITEM_LIST_INDEX = 0;
    private final static int SUBMENU_ITEM_GRID_INDEX = 1;


    public BrowserBySeason() {
        Log.d(Browser.TAG, "BrowserBySeason()");
    }



    @Override
    public int getThumbnailsType() {
        return ThumbnailEngineVideo.TYPE_TV_SHOW;
    }

    @Override
    public int getDefaultViewMode() {
        return VideoUtils.VIEW_MODE_GRID;
    }


    @Override
    protected String getActionBarTitle() {
        return (mTitle != null ? mTitle : getString(R.string.all_tv_shows));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        super.onCreateOptionsMenu(menu, inflater);
        if (mBrowserAdapter != null
                && !mBrowserAdapter.isEmpty()) {
            mDisplayModeSubmenu.clear();
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_list_mode2, R.string.view_mode_list, 0);
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_grid_mode, R.string.view_mode_grid, 0);
            mDisplayModeSubmenu.selectSubmenuItem(mViewMode == VideoUtils.VIEW_MODE_GRID
                    ? SUBMENU_ITEM_GRID_INDEX : SUBMENU_ITEM_LIST_INDEX);
        }

    }

    public boolean shouldEnableMultiSelection(){
        return false;
    }
    
    @Override
    public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
        switch (position) {
            case SUBMENU_ITEM_LIST_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_LIST) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_LIST);
                }
                break;

            case SUBMENU_ITEM_GRID_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_GRID) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_GRID);
                }
                break;
        }
    }

    @Override
    protected void setSeason(TextView seasonView) {
        seasonView.setVisibility(View.GONE);
    }

    @Override
    protected void setColor(int color) {

    }

    @Override
    protected void onPosterClick() {
        selectSeriesPoster();
    }

    @Override
    protected void setupAdapter(boolean createNewAdapter) {

        if (createNewAdapter || mBrowserAdapter == null) {
            mBrowserAdapter = new SeasonsAdapter(mContext, mCursor);
            if(mViewMode== VideoUtils.VIEW_MODE_GRID || mViewMode== VideoUtils.VIEW_MODE_GRID_SHORT) {
                ((PresenterAdapterByCursor)mBrowserAdapter).setPresenter(Season.class, new SeasonGridPresenter(getActivity(), this));
            }
            else  if(mViewMode == VideoUtils.VIEW_MODE_GRID_SHORT){
                ((PresenterAdapterByCursor) mBrowserAdapter).setPresenter(Episode.class, new SeasonGridShortPresenter(getContext(),this));
            }
            else {
                ((PresenterAdapterByCursor)mBrowserAdapter).setPresenter(Season.class, new SeasonListPresenter(getActivity(), this));
            }
        } else {
            PresenterAdapterByCursor adapter = (PresenterAdapterByCursor)mBrowserAdapter;
            adapter.setData(mCursor);
        }
    }


    public Uri getRealPathUriFromPosition(int position){
        mCursor.moveToPosition(position);
        return Uri.parse(mCursor.getString(mCursor.getColumnIndex(VideoStore.MediaColumns.DATA)));

    }

    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        //setPosition(position);
        position = correctedPosition(position);
        if(position ==-1)
            return;

        Season season = (Season) mBrowserAdapter.getItem(position);
        Bundle args = new Bundle(3);
        args.putLong(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, season.getShowId());
        args.putInt(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON,
                season.getSeasonNumber());

        args.putString(SUBCATEGORY_NAME, season.getName());
        Fragment f = Fragment.instantiate(mContext, BROWSER_SHOW, args);
        BrowserCategory category = (BrowserCategory) getFragmentManager().findFragmentById(
                R.id.category);
        category.startContent(f);

        mSelectedPosition=position;
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

    }

    @Override
    protected Uri getPosterUri() {
        Uri posterUri;
        if (mShow.getShowTags()!=null) {
            posterUri = Uri.parse("file://"+mShow.getShowTags().getDefaultPoster().getLargeFile());
        } else {
            posterUri = mShow.getPosterUri(); // fallback
        }
        return posterUri;
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(id==0) {
            return new SeasonsLoader(getContext(), mShowId).getV4CursorLoader(true, false);
        }

        return super.onCreateLoader(id, args);
    }



}
