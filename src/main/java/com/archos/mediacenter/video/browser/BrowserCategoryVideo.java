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


package com.archos.mediacenter.video.browser;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserAllMovies;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserAllTvShows;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserAllVideos;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserLastAdded;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserLastPlayed;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserMoviesByGenre2;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserMoviesByYear;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserNeverPlayed;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserPlaylists;
import com.archos.mediacenter.video.browser.filebrowsing.BrowserByExtStorage;
import com.archos.mediacenter.video.browser.filebrowsing.BrowserByVideoFolder;
import com.archos.mediacenter.video.browser.filebrowsing.network.ShortcutRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser.SmbRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.UpnpBrowser.UpnpRootFragment;

import java.util.ArrayList;

public class BrowserCategoryVideo extends BrowserCategory implements androidx.appcompat.app.ActionBar.OnNavigationListener {
    static final String TAG = "BrowserCategoryVideo";

    static final String KEY_ACTIONBAR_NAVIGATION_MODE = "KEY_ACTIONBAR_NAVIGATION_MODE";
    static final String KEY_ACTIONBAR_NAVIGATION_POSITION = BrowserCategoryVideo.class.getName()+"_ACTIONBAR_NAVIGATION_POSITION";
    static final int KEY_ACTIONBAR_NAVIGATION_POSITION_DEFAULT = 0; // first on is "All movies"

    static final int MOVIE_CATEGORIES_NAMES_ID[] = {
            R.string.all_movies,
            R.string.movies_by_year,
            R.string.movies_by_genre,
    };

    static final Class<? extends Fragment> MOVIE_CATEGORIES_CLASSES[] = new Class[]{
            BrowserAllMovies.class,
            BrowserMoviesByYear.class,
            BrowserMoviesByGenre2.class,
    };

    /**
     * Used to disable the action bar navigation listener when initializing the action bar navigation while the fragment is already created
     */
    private boolean mNavigationItemListenerActive = true;
    private static final int ITEM_ID_VIDEO_FOLDER = ITEM_ID_OFFSET + 0;
    private static final int ITEM_ID_MOVIES = ITEM_ID_OFFSET + 1;
    private static final int ITEM_ID_TV_SHOWS = ITEM_ID_OFFSET + 2;
    private static final int ITEM_ID_ALL_VIDEOS = ITEM_ID_OFFSET + 3;
    public static final int ITEM_ID_RECENTLY_ADDED = ITEM_ID_OFFSET + 4;
    private static final int ITEM_ID_RECENTLY_PLAYED = ITEM_ID_OFFSET +5;
    private static final int ITEM_ID_LISTS = ITEM_ID_OFFSET +6;

    public void setNavigationMode(int navigationMode){
        ((MainActivity)getActivity()).setNavigationMode(navigationMode);
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIONBAR_NAVIGATION_MODE, ((MainActivity) getActivity()).getNavigationMode());
        // No need to save the position in the navigation drop-down here, it is saved in the Preferences already.
    }

    @Override
    public void onViewCreated(View v, Bundle save){
        super.onViewCreated(v, save);
        if (save!=null) {
            int navigationMode = save.getInt(KEY_ACTIONBAR_NAVIGATION_MODE, ActionBar.NAVIGATION_MODE_STANDARD);
            if (navigationMode==ActionBar.NAVIGATION_MODE_LIST) {
                setupMovieActionBarNavigation(false); // false because the corresponding fragment is already re-created by the framework after rotation
            } else {
                setNavigationMode(navigationMode);
            }
        }
        // Ensure ActionBar is initialized before calling setFragment
        if (save == null) //restore only when starting from scratch
            v.post(() -> setFragment(null));
    }


    protected int getDefaultId(){return ITEM_ID_RECENTLY_ADDED;}


    @Override
    public void setLibraryList(ArrayList<Object> categoryList) {
        ItemData itemData;

        itemData = new ItemData();
        itemData.icon = R.drawable.category_video_movie;
        itemData.text = R.string.movies;
        itemData.id = ITEM_ID_MOVIES;
        categoryList.add(itemData);

        itemData = new ItemData();
        itemData.icon = R.drawable.category_video_tvshow;
        itemData.text = R.string.all_tv_shows;
        itemData.id = ITEM_ID_TV_SHOWS;
        categoryList.add(itemData);

        itemData = new ItemData();
        itemData.icon = R.drawable.category_video_all;
        itemData.text = R.string.all_videos;
        itemData.id = ITEM_ID_ALL_VIDEOS;
        categoryList.add(itemData);

        itemData = new ItemData();
        itemData.icon = R.drawable.category_video_added;
        itemData.text = R.string.recently_added_videos;
        itemData.id = ITEM_ID_RECENTLY_ADDED;
        categoryList.add(itemData);

        itemData = new ItemData();
        itemData.icon = R.drawable.category_video_played;
        itemData.text = R.string.recently_played_videos;
        itemData.id = ITEM_ID_RECENTLY_PLAYED;
        categoryList.add(itemData);

        itemData = new ItemData();
        itemData.icon = R.drawable.category_video_played;
        itemData.text = R.string.video_lists;
        itemData.id = ITEM_ID_LISTS;
        categoryList.add(itemData);

        /*itemData = new ItemData();
        itemData.icon = R.drawable.category_video_not_played;
        itemData.text = R.string.not_played_yet_videos;
        categoryList.add(itemData);*/

        itemData = new ItemData();
        itemData.icon = R.drawable.category_common_folder;
        itemData.text = R.string.video_folder;
        itemData.id = ITEM_ID_VIDEO_FOLDER;
        categoryList.add(itemData);
    }


    /**
     * Override the base class because we need a special treatment for the Movies category,
     * that is using the ActionBar list navigation to switch between various fragments
     */
    @Override
    public void setFragment(String path){
        if(mSelectedItemId == ITEM_ID_PROVIDER){
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra("requestCode", FILE_CHOOSER_ACTIVITY_REQUEST_CODE);
            getFileChooserLauncher().launch(Intent.createChooser(intent, "Choose file"));
            //restore browser
            mSelectedItemId = mOldSelectedItemId;
            return ;
        }
        else if (mSelectedItemId == ITEM_ID_MOVIES) {
            setupMovieActionBarNavigation(true);
            // refresh the category list

        }
        else {
            //default case is no navigation in action bar
            ((MainActivity)getActivity()).hideSeachView();
            setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            // and it is handled by the parent class
            super.setFragment(path);
        }

    }

    /**
     *
     * @param setupTheFragmentAsWell: if true, the fragment corresponding to the selected drop-down item will also be created
     */
    private void setupMovieActionBarNavigation(boolean setupTheFragmentAsWell) {
         androidx.appcompat.app.ActionBar ab = ((AppCompatActivity)getActivity()).getSupportActionBar();
        // no title in that case
        ab.setTitle("");
        // navigation drop-down instead
        setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        // build the localized string list
        String[] movieCategoriesNames = new String[MOVIE_CATEGORIES_NAMES_ID.length];
        for (int i=0; i<MOVIE_CATEGORIES_NAMES_ID.length; i++) {
            movieCategoriesNames[i] = getResources().getString(MOVIE_CATEGORIES_NAMES_ID[i]);
        }
        ab.setListNavigationCallbacks( new ArrayAdapter(getActivity(), android.R.layout.simple_spinner_dropdown_item, movieCategoriesNames), this);

        // Set default value
        int defaultListPosition = PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt(KEY_ACTIONBAR_NAVIGATION_POSITION, KEY_ACTIONBAR_NAVIGATION_POSITION_DEFAULT);
        mNavigationItemListenerActive = setupTheFragmentAsWell; // we want the listener to be called only if the fragment is not created yet
        ab.setSelectedNavigationItem(defaultListPosition);
    }

    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        Log.d(TAG, "onNavigationItemSelected "+itemPosition);
        if (!mNavigationItemListenerActive) {
            Log.d(TAG, "onNavigationItemSelected: listener is inactive, returning");
            mNavigationItemListenerActive = true; // regular state is active, to get user feedback
            return true;
        }
        BrowserCategory category = (BrowserCategory) getParentFragmentManager().findFragmentById(R.id.category);
        try {
            Fragment f = MOVIE_CATEGORIES_CLASSES[itemPosition].getConstructor().newInstance();
            category.loadFragmentAfterStackReset(f);
        } catch (Exception e) {
            Log.w(TAG, "onNavigationItemSelected: caught exception", e);
        }
        // Save the current position to the preferences
        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
        .putInt(KEY_ACTIONBAR_NAVIGATION_POSITION, itemPosition)
        .commit();
        return true;
    }

    @Override
    public FragmentTitleStruc getContentFragmentAndTitle(int id) {
        FragmentTitleStruc struc = new FragmentTitleStruc();
        Class<? extends Fragment> fragmentClass = null;
        switch (id) {
            case ITEM_ID_VIDEO_FOLDER:
                fragmentClass = BrowserByVideoFolder.class;
                struc.title = R.string.video_folder;
                break;
            case ITEM_ID_SMB:
                fragmentClass = SmbRootFragment.class;
                struc.title = R.string.network_shared_folders;
                break;
            case ITEM_ID_NETWORK:
                fragmentClass = ShortcutRootFragment.class;
                struc.title = R.string.network_shortcuts;
                break;
            case ITEM_ID_UPNP:
                fragmentClass = UpnpRootFragment.class;
                struc.title = R.string.network_media_servers;
                break;
            case ITEM_ID_RECENTLY_ADDED:
                fragmentClass = BrowserLastAdded.class;
                struc.title = R.string.recently_added_videos;
                break;
            case ITEM_ID_RECENTLY_PLAYED:
                fragmentClass = BrowserLastPlayed.class;
                struc.title = R.string.recently_played_videos;
                break;
            case ITEM_ID_LISTS:
                fragmentClass = BrowserPlaylists.class;
                struc.title = R.string.video_lists;
                break;
            case R.string.not_played_yet_videos:
                fragmentClass = BrowserNeverPlayed.class;
                struc.title = R.string.not_played_yet_videos;
                break;
            case ITEM_ID_MOVIES:
                fragmentClass = BrowserAllMovies.class;
                struc.title = R.string.movies;
                break;
            case ITEM_ID_TV_SHOWS:
                fragmentClass = BrowserAllTvShows.class;
                struc.title = R.string.all_tv_shows;
                break;
            case ITEM_ID_ALL_VIDEOS:
                fragmentClass = BrowserAllVideos.class;
                struc.title = R.string.all_videos;
                break;
            case ITEM_ID_BROWSER:
                fragmentClass = BrowserByExtStorage.class;
                struc.title = R.string.other_storage; // will be replaced by fragment
                break;
            default:
                fragmentClass = BrowserLastPlayed.class;
                struc.title = R.string.video_folder;
                break;
        }
        try {
            struc.fragment = fragmentClass.getConstructor().newInstance();
        } catch (Exception e) {
            Log.w(TAG, "onNavigationItemSelected: caught exception", e);
        }
        return struc;
    }

    public void goToRecentlyAdded() {
        if(mSelectedItemId != ITEM_ID_RECENTLY_ADDED) {
            mSelectedItemId = ITEM_ID_RECENTLY_ADDED;
            setFragment(null);
        }
    }

    public void goToRecentlyPlayed() {
        if(checkAvailability(ITEM_ID_RECENTLY_PLAYED, null)) {
            mSelectedItemId = ITEM_ID_RECENTLY_PLAYED;
            setFragment(null);
        }
    }
}
