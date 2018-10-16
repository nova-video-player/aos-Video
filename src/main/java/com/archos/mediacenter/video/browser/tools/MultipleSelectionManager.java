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

package com.archos.mediacenter.video.browser.tools;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Browser;
import com.archos.mediacenter.video.browser.HeaderGridView;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.adapters.object.Tvshow;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.filebrowsing.network.BrowserByNetwork;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexandre on 21/10/15.
 */
public class MultipleSelectionManager implements ActionMode.Callback {

    private final Browser mBrowser;
    private final BaseAdapter mBrowserAdapter;
    private final AbsListView mArchosGridView;
    private final SharedPreferences mPreferences;
    private ActionMode mActionBar;

    public MultipleSelectionManager(Browser browser, AbsListView listView, BaseAdapter browserAdapter){
        mBrowser = browser;
        mArchosGridView = listView;
        mBrowserAdapter = browserAdapter;
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mBrowser.getActivity());
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        menu.add(0, R.string.delete, 0,R.string.delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0,R.string.copy_on_device_multi, 0,R.string.copy_on_device_multi).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, R.string.menu_subloader_allfolder, Menu.NONE, R.string.menu_subloader_allfolder).setIcon(R.drawable.ic_menu_subtitles).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.video_browser_unindex_file, 0, R.string.video_browser_unindex_file).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.video_browser_index_file, 0, R.string.video_browser_index_file).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.mark_as_watched, 0, R.string.mark_as_watched).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.mark_as_not_watched, 0, R.string.mark_as_not_watched).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        onPrepareActionMode(mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu.findItem(R.string.delete).setVisible(mArchosGridView.getCheckedItemCount() > 0);
        boolean areNotLocal =true;
        boolean areFiles = true;
        boolean areAllIndexed = true;

        boolean markAsTrakt = Trakt.isTraktV2Enabled(mBrowser.getActivity(), mPreferences); //= (isShowOrSeason || isMovieOrEpisode) && Trakt.isTraktV2Enabled(mContext, mPreferences);
        boolean markAsWatched = false;
        boolean markAsUnWatched = false;

        int firstPosition = 0;
        if(mArchosGridView instanceof HeaderGridView){
            firstPosition += ((HeaderGridView) mArchosGridView).getOffset();
        }
        else if(mArchosGridView instanceof ListView){
            firstPosition += ((ListView) mArchosGridView).getHeaderViewsCount()   ;
        }
        if(mArchosGridView.getCheckedItemCount()>0) {
            for (int i = firstPosition; i < mArchosGridView.getCount(); i++) {

                if (mArchosGridView.getCheckedItemPositions().get(i)) {
                    Object obj = mBrowserAdapter.getItem(i-firstPosition);
                    final boolean isShowOrSeason = obj instanceof Season || obj instanceof Tvshow;

                    final boolean isMovieOrEpisode = obj instanceof Movie || obj instanceof Episode;
                    Uri uri = null;
                    if(obj instanceof Video){
                        uri = ((Video)obj).getFileUri();
                    }
                    else if(obj instanceof MetaFile2){
                        uri = ((MetaFile2)obj).getUri();
                    }
                    if (FileUtils.isLocal(uri)) {
                        areNotLocal = false;
                    }
                    if(obj instanceof MetaFile2 || obj instanceof NonIndexedVideo) {
                        areAllIndexed = false;
                        if(obj instanceof MetaFile2 && ((MetaFile2)obj).isDirectory()){
                            areFiles = false;
                        }

                    }
                    if(obj instanceof Video) {
                        Video video = (Video) obj;
                        if (markAsTrakt && (isShowOrSeason || isMovieOrEpisode)) {
                            if (!video.isWatched())
                                markAsWatched = true;
                            else
                                markAsUnWatched = true;
                        } else if (areFiles) {
                            if (video.getResumeMs() != PlayerActivity.LAST_POSITION_END) {
                                markAsWatched = true;
                            } else {
                                markAsUnWatched = true;
                            }
                        } else {
                            markAsWatched = false;
                            markAsUnWatched = false;
                        }
                    }
                    if(!areFiles && !areNotLocal)
                        break;

                }

            }
        }
        else {
            areNotLocal = false;
            areFiles = false;
        }


        menu.findItem(R.string.video_browser_unindex_file).setVisible(areAllIndexed&&mArchosGridView.getCheckedItemCount() > 0);
        menu.findItem(R.string.video_browser_index_file).setVisible(!areAllIndexed && areFiles && areNotLocal);
        menu.findItem(R.string.copy_on_device_multi).setVisible(areNotLocal);
        menu.findItem(R.string.menu_subloader_allfolder).setVisible(areFiles);
        menu.findItem(R.string.mark_as_not_watched).setVisible(markAsUnWatched);
        menu.findItem(R.string.mark_as_watched).setVisible(markAsWatched);
        return true;
    }
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        int firstPosition = 0;
        if(mArchosGridView instanceof HeaderGridView){
            firstPosition += ((HeaderGridView) mArchosGridView).getOffset();
        }
        else if(mArchosGridView instanceof ListView){
            firstPosition += ((ListView) mArchosGridView).getHeaderViewsCount()   ;
        }
        switch (item.getItemId()){
            case R.string.delete:
                List<Uri> toDelete = new ArrayList<>();
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i)) {
                        toDelete.add(mBrowser.getRealPathUriFromPosition(i-firstPosition));
                    }
                }
                mBrowser.showConfirmDeleteDialog(false, toDelete);
                return true;

            case R.string.copy_on_device_multi:
                List<Uri> toCopy = new ArrayList<>();
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i)) {
                        toCopy.add(mBrowser.getRealPathUriFromPosition(i-firstPosition));
                    }
                }
                mBrowser.startDownloadingVideo(toCopy);
                return true;

            case R.string.menu_subloader_allfolder:
                ArrayList<String> videoPaths = new ArrayList<String>();
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i)) {
                        videoPaths.add(mBrowser.getRealPathUriFromPosition(i-firstPosition).toString());

                    }
                }
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClass(mBrowser.getContext(), SubtitlesDownloaderActivity.class);
                intent.putExtra(SubtitlesDownloaderActivity.FILE_URLS, videoPaths);
                mBrowser.startActivity(intent);
                mActionBar.finish();
                return true;



            case R.string.video_browser_unindex_file:
                ArrayList<Long> toUnindex = new ArrayList<>();
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i)) {
                        Object obj = mBrowserAdapter.getItem(i-firstPosition);
                        if(obj instanceof Video){
                            toUnindex.add(((Video)obj).getId());
                        }
                    }
                }

                DbUtils.markHiddenValue(mBrowser.getActivity(),toUnindex.toArray(new Long[1]),1);

                mActionBar.finish();
                return true;
            case R.string.video_browser_index_file:
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i))
                        VideoStore.requestIndexing(mBrowser.getRealPathUriFromPosition(i-firstPosition), mBrowser.getActivity());
                }

                return true;
            case R.string.mark_as_watched:
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i)){
                        Object obj = mBrowserAdapter.getItem(i-firstPosition);
                        if(obj instanceof Video){
                            final boolean isShowOrSeason = obj instanceof Season || obj instanceof Tvshow;
                            final boolean isMovieOrEpisode = obj instanceof Movie || obj instanceof Episode;
                            if((isShowOrSeason||isMovieOrEpisode)&&!((Video)obj).isWatched() || ((Video)obj).getResumeMs() != PlayerActivity.LAST_POSITION_END) {
                                mBrowser.markAsRead(i-firstPosition, true, mPreferences.getBoolean(BrowserByNetwork.KEY_NETWORK_BOOKMARKS, true));
                            }
                        }



                    }
                }

                return true;
            case R.string.mark_as_not_watched:
                for (int i = 0; i < mArchosGridView.getCount(); i++) {
                    if (mArchosGridView.getCheckedItemPositions().get(i)) {
                        Object obj = mBrowserAdapter.getItem(i-firstPosition);
                        Uri uri = null;
                        if(obj instanceof Video){
                            final boolean isShowOrSeason = obj instanceof Season || obj instanceof Tvshow;
                            final boolean isMovieOrEpisode = obj instanceof Movie || obj instanceof Episode;
                            if((isShowOrSeason||isMovieOrEpisode)&&((Video)obj).isWatched() || ((Video)obj).getResumeMs() == PlayerActivity.LAST_POSITION_END) {
                                mBrowser.markAsNotRead(i-firstPosition, true, mPreferences.getBoolean(BrowserByNetwork.KEY_NETWORK_BOOKMARKS, true));
                            }
                        }


                    }
                }
                return true;
        }
        return false;
    }
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mBrowser.disableMultiple();
        mActionBar =null;
    }
    public void destroyActionBar(){
        if(mActionBar!=null)
            mActionBar.finish();
    }
    public void setActionBar(ActionMode actionBar) {
        mActionBar = actionBar;
    }

    public void invalidateActionBar() {
        mActionBar.invalidate();
    }
}
