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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.archos.mediacenter.utils.ActionBarSubmenu;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.ThumbnailRequesterVideo;
import com.archos.mediacenter.video.browser.adapters.AdapterByShow;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValuesList;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.EpisodesLoader;
import com.archos.mediacenter.video.browser.presenter.EpisodeListDetailedPresenter;
import com.archos.mediacenter.video.browser.presenter.EpisodePresenter;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.video.VideoStore;


public class BrowserByShow extends BrowserWithShowHeader {

    private static final String TAG = "BrowserByShow";

    private static final String SELECTION = VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID
            + " = ? AND " + VideoStore.Video.VideoColumns.SCRAPER_E_SEASON + " = ? ";
    private static final String SORT_ORDER = VideoStore.Video.VideoColumns.SCRAPER_E_EPISODE;

    private final static int SUBMENU_ITEM_LIST_INDEX = 0;
    private final static int SUBMENU_ITEM_DETAILS_INDEX = 1;

    private long mId = 0;
    private int mSeason = 0;


    private EpisodePresenter mEpisodePresenter;
    private int mLastColor;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHideOption = false;
        mLastColor = Color.TRANSPARENT;

    }

    public void onViewCreated(View view, Bundle save ){
        super.onViewCreated(view, save);
        ((MainActivity)getActivity()).getSupportActionBar().setBackgroundDrawable(null);
        ((ListView)mArchosGridView).setDivider(new ColorDrawable(ContextCompat.getColor(getContext(), R.color.transparent_white_list_divider)));
        ((ListView)mArchosGridView).setDividerHeight(3);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ((ListView)mArchosGridView).setDivider(null); //unset otherwise, crash in listview
        ((ListView)mArchosGridView).setDividerHeight(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mApplicationFrameLayout.setBackground(null);
        else
            mApplicationFrameLayout.setBackgroundDrawable(null);
        ((MainActivity)getActivity()).getSupportActionBar().setBackgroundDrawable(ContextCompat.getDrawable(getContext(), R.color.leanback_background_transparent));
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

    }

    public String[] getCursorSelectionArgs() {
        Bundle args = getArguments();
        if (args != null) {
            mId = args.getLong(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, 0);
            mSeason = args.getInt(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON, 0);
        }
        String ret[] = {
                String.valueOf(mId), String.valueOf(mSeason)
        };
        return ret;
    }



    @Override
    public int getDefaultViewMode() {
        return VideoUtils.VIEW_MODE_LIST;
    }

    @Override
    public int getEmptyMessage() {
        return R.string.scraper_no_episode_found;
    }

    @Override
    protected String getActionBarTitle() {
        return (mTitle != null ? mTitle : getString(R.string.all_tv_shows));
    }


    @Override
    public boolean isItemClickable(int position) {
        return mBrowserAdapter.isEnabled(position);
    }

    @Override
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        //setPosition(position);
        position = correctedPosition(position);
        if (position == -1)
            return;
        super.onItemClick(parent,v, position, id);

    }



    protected void postBindAdapter() {
    	super.postBindAdapter();
    	
    	
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id != SHOW_LOADER_ID) {
            Bundle args2 = getArguments();
            if (args2 != null) {
                mId = args2.getLong(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, 0);
                mSeason = args2.getInt(VideoStore.Video.VideoColumns.SCRAPER_E_SEASON, 0);
            }
            return new EpisodesLoader(getContext(), mId, mSeason, true).getV4CursorLoader(true, false);//mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
        }
        else return super.onCreateLoader(id, args);
    }



    @Override
    public void setupThumbnail() {
        mThumbnailRequester = new ThumbnailRequesterVideo(mThumbnailEngine, (AdapterByShow) mBrowserAdapter);
    }

    @Override
    protected void setupAdapter(boolean createNewAdapter) {
        if (createNewAdapter || mBrowserAdapter == null) {
            mBrowserAdapter = new AdapterByShow(mContext, mCursor);

            mEpisodePresenter = null;
            if(mViewMode == VideoUtils.VIEW_MODE_DETAILS) {
                mEpisodePresenter =  new EpisodeListDetailedPresenter(getContext(),this);
            }

            else {
                mEpisodePresenter = new EpisodePresenter(getContext(),AdapterDefaultValuesList.INSTANCE ,this);
            }
            mEpisodePresenter.setTransparent(true);
            ((AdapterByShow) mBrowserAdapter).setPresenter(Episode.class, mEpisodePresenter );

        } else {
            AdapterByShow adapter = (AdapterByShow) mBrowserAdapter;
            adapter.setData(mCursor);
        }
    }
    @Override
    public void onResume(){
        super.onResume();
        if(mArchosGridView!=null)
            mArchosGridView.clearChoices();
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mBrowserAdapter != null && !mBrowserAdapter.isEmpty()) {
            mDisplayModeSubmenu.clear();
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_list_mode2, R.string.view_mode_list, 0);
            mDisplayModeSubmenu.addSubmenuItem(R.drawable.ic_menu_details_mode2, R.string.view_mode_details, 0);
            mDisplayModeSubmenu.selectSubmenuItem(mViewMode == VideoUtils.VIEW_MODE_DETAILS
                                                ? SUBMENU_ITEM_DETAILS_INDEX : SUBMENU_ITEM_LIST_INDEX);
        }
    }

    @Override
    public void onSubmenuItemSelected(ActionBarSubmenu submenu, int position, long itemId) {
        switch (position) {
            case SUBMENU_ITEM_LIST_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_LIST) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_LIST);
                }
                break;

            case SUBMENU_ITEM_DETAILS_INDEX:
                if (mViewMode != VideoUtils.VIEW_MODE_DETAILS) {
                    applySelectedViewMode(VideoUtils.VIEW_MODE_DETAILS);
                }
                break;
        }
    }

    @Override
    protected void setColor(int color) {

        int darkColor = VideoInfoCommonClass.getDarkerColor(color);
        ColorDrawable[] colord = {new ColorDrawable(mLastColor), new ColorDrawable(darkColor)};
        TransitionDrawable trans = new TransitionDrawable(colord);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mApplicationFrameLayout.setBackground(trans);
        else
            mApplicationFrameLayout.setBackgroundDrawable(trans);
        trans.startTransition(200);
        mLastColor = darkColor;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getActivity().getWindow().setStatusBarColor(VideoInfoCommonClass.getAlphaColor(darkColor, 160));
        }

    }

    @Override
    protected void onPosterClick() {

    }

    @Override
    protected Uri getPosterUri() {
        if(mBrowserAdapter.getCount()>0)
            return ((Video)mBrowserAdapter.getItem(0)).getPosterUri();
        return null;
    }

    public int getFileSize(){
        return getFileAndFolderSize();
    }

    @Override
    public int getFileAndFolderSize() {

        return mBrowserAdapter.getCount() ;
    }

    @Override
    protected void setSeason(TextView seasonView) {
        seasonView.setText(getResources().getString(R.string.episode_season) + " " + mSeason);
    }


    @Override
    public int getFirstFilePosition() {
        return 0;
    }

}
