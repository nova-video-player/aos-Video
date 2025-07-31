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
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.Spinner;
import android.os.Handler;

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
import com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser.FtpRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.ShortcutRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser.SmbRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.UpnpBrowser.UpnpRootFragment;

import java.util.ArrayList;
import androidx.fragment.app.FragmentManager;
import android.content.res.Configuration;

public class BrowserCategoryVideo extends BrowserCategory implements androidx.appcompat.app.ActionBar.OnNavigationListener {
    static final String TAG = "BrowserCategoryVideo";

    static final String KEY_ACTIONBAR_NAVIGATION_MODE = "KEY_ACTIONBAR_NAVIGATION_MODE";
    static final String KEY_ACTIONBAR_NAVIGATION_POSITION = BrowserCategoryVideo.class.getName()+"_ACTIONBAR_NAVIGATION_POSITION";
    static final int KEY_ACTIONBAR_NAVIGATION_POSITION_DEFAULT = 0; // first on is "All movies"

    static final int MOVIE_CATEGORIES_NAMES_ID[] = {
            R.string.movies,
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

    // Add this field to track the previous back stack count
    private int mPreviousBackStackCount = -1;

    // Handler and Runnable for debouncing spinner restoration
    private final Handler mHandler = new Handler();
    private Runnable mRestoreSpinnerRunnable;

    public void setNavigationMode(int navigationMode){
        ((MainActivity)getActivity()).setNavigationMode(navigationMode);
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ACTIONBAR_NAVIGATION_MODE, ((MainActivity) getActivity()).getNavigationMode());
        // No need to save the position in the navigation drop-down here, it is saved in the Preferences already.
    }

    // Remove the back stack listener completely and replace with a simpler approach
    @Override
    public void onResume() {
        super.onResume();
        // Only restore spinner if we're in the Movies category and at root level
        if (mSelectedItemId == ITEM_ID_MOVIES) {
            FragmentManager fm = getParentFragmentManager();
            int backStackCount = fm.getBackStackEntryCount();
            boolean isAtRootLevel = backStackCount <= 1;

            if (isAtRootLevel) {
                // We're at root level, ensure spinner is visible
                androidx.appcompat.app.ActionBar ab = ((AppCompatActivity)getActivity()).getSupportActionBar();
                if (ab.getNavigationMode() != ActionBar.NAVIGATION_MODE_LIST) {
                    setupMovieActionBarNavigation(false, true);
                }
            } else {
                // We're in a subfolder, ensure spinner is hidden
                setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);
        if (bundle!=null) {
            int navigationMode = bundle.getInt(KEY_ACTIONBAR_NAVIGATION_MODE, ActionBar.NAVIGATION_MODE_STANDARD);
            if (navigationMode==ActionBar.NAVIGATION_MODE_LIST) {
                setupMovieActionBarNavigation(false); // false because the corresponding fragment is already re-created by the framework after rotation
            } else {
                setNavigationMode(navigationMode);
            }
        }
        // Initialize the back stack count
        mPreviousBackStackCount = getParentFragmentManager().getBackStackEntryCount();
        getParentFragmentManager().addOnBackStackChangedListener(backStackChangedListener);
    }

    private final FragmentManager.OnBackStackChangedListener backStackChangedListener = new FragmentManager.OnBackStackChangedListener() {
        @Override
        public void onBackStackChanged() {
            if (mSelectedItemId == ITEM_ID_MOVIES || mSelectedItemId == 999 || mSelectedItemId == 1000 || mSelectedItemId == 1001) {  // 999 = preferences | 1000 = help_faq | 1001 = sponsor
                FragmentManager fm = getParentFragmentManager();
                int currentBackStackCount = fm.getBackStackEntryCount();
                boolean isAtRootLevel = currentBackStackCount <= 1;

                // Remove any pending spinner restoration
                if (mRestoreSpinnerRunnable != null) {
                    mHandler.removeCallbacks(mRestoreSpinnerRunnable);
                }

                if (isAtRootLevel) {
                    mRestoreSpinnerRunnable = () -> {
                        Log.d(TAG, "Restoring spinner at root level (debounced)");
                        setupMovieActionBarNavigation(false, true);

                        // Apply spinner translation after spinner is restored
                        AppCompatActivity activity = (AppCompatActivity)getActivity();
                        if (activity != null) {
                            activity.getWindow().getDecorView().post(() -> {
                                ViewGroup toolbar = (ViewGroup) activity.findViewById(R.id.main_toolbar);
                                if (toolbar != null) {
                                    Spinner foundSpinner = null;
                                    for (int i = 0; i < toolbar.getChildCount(); i++) {
                                        View child = toolbar.getChildAt(i);
                                        if (child instanceof Spinner) {
                                            foundSpinner = (Spinner) child;
                                            break;
                                        } else if (child instanceof ViewGroup) {
                                            ViewGroup group = (ViewGroup) child;
                                            for (int j = 0; j < group.getChildCount(); j++) {
                                                View subChild = group.getChildAt(j);
                                                if (subChild instanceof Spinner) {
                                                    foundSpinner = (Spinner) subChild;
                                                    break;
                                                }
                                            }
                                        }
                                        if (foundSpinner != null) break;
                                    }
                                    if (foundSpinner != null) {
                                        foundSpinner.setPadding(0, 0, 0, 9);
                                        boolean mIsPortraitMode = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
                                        if (mIsPortraitMode){
                                            foundSpinner.setTranslationX(-50);
                                        } else {
                                            foundSpinner.setTranslationX(0);
                                        }
                                    }
                                }
                            });
                        }
                    };
                    // Post with a small delay to allow the back stack to settle
                    mHandler.postDelayed(mRestoreSpinnerRunnable, 100);
                } else {
                    // Hide spinner, show standard navigation
                    setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                }
                mPreviousBackStackCount = currentBackStackCount;
            }
        }
    };

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getParentFragmentManager().removeOnBackStackChangedListener(backStackChangedListener);
        if (mRestoreSpinnerRunnable != null) {
            mHandler.removeCallbacks(mRestoreSpinnerRunnable);
        }
    }

    @Override
    public void onViewCreated(View v, Bundle save){
        super.onViewCreated(v, save);
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
            startActivityForResult(intent.createChooser(intent, "Choose file"), FILE_CHOOSER_ACTIVITY_REQUEST_CODE);
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
        setupMovieActionBarNavigation(setupTheFragmentAsWell, true);
    }

    private void setupMovieActionBarNavigation(boolean setupTheFragmentAsWell, boolean setTitle) {
        androidx.appcompat.app.ActionBar ab = ((AppCompatActivity)getActivity()).getSupportActionBar();

        if (setTitle) {
            // We're at root level, show the spinner
            ab.setTitle("");
            // navigation drop-down instead
            setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            // build the localized string list
            String[] movieCategoriesNames = new String[MOVIE_CATEGORIES_NAMES_ID.length];
            for (int i=0; i<MOVIE_CATEGORIES_NAMES_ID.length; i++) {
                movieCategoriesNames[i] = getResources().getString(MOVIE_CATEGORIES_NAMES_ID[i]);
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    getActivity(),
                    R.layout.movie_category_selected_item,        // selected item layout
                    R.id.text1,
                    movieCategoriesNames
            ) {
                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    // Inflate dropdown item layout for dropdown
                    LayoutInflater inflater = LayoutInflater.from(getContext());
                    View view = inflater.inflate(R.layout.movie_category_dropdown_item, parent, false);

                    CheckedTextView textView = view.findViewById(android.R.id.text1);
                    textView.setText(getItem(position));

                    return view;
                }
            };
            ab.setListNavigationCallbacks(adapter, this);
            // Set default value
            int defaultListPosition = PreferenceManager.getDefaultSharedPreferences(getActivity()).getInt(KEY_ACTIONBAR_NAVIGATION_POSITION, KEY_ACTIONBAR_NAVIGATION_POSITION_DEFAULT);
            mNavigationItemListenerActive = setupTheFragmentAsWell; // we want the listener to be called only if the fragment is not created yet
            ab.setSelectedNavigationItem(defaultListPosition);
        } else {
            // We're in a subfolder, don't show the spinner at all
            // Just ensure we're in standard navigation mode so the child fragment can set its own title
            setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        }
    }

    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        Log.d(TAG, "onNavigationItemSelected "+itemPosition);
        if (!mNavigationItemListenerActive) {
            Log.d(TAG, "onNavigationItemSelected: listener is inactive, returning");
            mNavigationItemListenerActive = true; // regular state is active, to get user feedback
            return true;
        }

        // Save the position in the preferences
        PreferenceManager.getDefaultSharedPreferences(getActivity())
                .edit()
                .putInt(KEY_ACTIONBAR_NAVIGATION_POSITION, itemPosition)
                .apply();

        // Get the spinner and update its width
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            ViewGroup toolbar = (ViewGroup) activity.findViewById(R.id.main_toolbar);
            // Recursively search for Spinner inside the Toolbar
            Spinner foundSpinner = null;
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof Spinner) {
                    foundSpinner = (Spinner) child;
                    break;
                } else if (child instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) child;
                    for (int j = 0; j < group.getChildCount(); j++) {
                        View subChild = group.getChildAt(j);
                        if (subChild instanceof Spinner) {
                            foundSpinner = (Spinner) subChild;
                            break;
                        }
                    }
                }
                if (foundSpinner != null) break;
            }

            if (foundSpinner != null) {
                foundSpinner.setSelection(itemPosition);
                activity.updateSpinnerWidth((AppCompatSpinner) foundSpinner, itemPosition);
            }
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
            case ITEM_ID_FTP:
                fragmentClass = FtpRootFragment.class;
                struc.title = R.string.ftp_shortcuts;
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
