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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.mediacenter.video.BuildConfig;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.ThemeManager;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.WebUtils;
import com.archos.environment.NetworkState;
import com.archos.mediaprovider.video.NetworkAutoRefresh;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;

abstract public class BrowserCategory extends ListFragment {

    private static final String SELECTED_ID = "selectedId";
    private static final String SELECTED_TOP = "selectedTop";
    public static final String MOUNT_POINT = "mount_point";
    private static final String TAG = "BrowserCategory";
    private static final boolean DBG = false;
    private static final boolean DBG_LISTENER = false;

    private static final int[] mExternalIDs = {
            R.string.sd_card_storage, R.string.usb_host_storage, R.string.other_storage, R.string.network_shared_folders,R.string.network_shortcuts,
            R.string.network_media_servers, R.string.network_jcifs, R.string.network_cling,R.string.preferences
    };
    private static final String PREFERENCE_LAST_FRAGMENT = "preference_last_selected_fragment";
    private static final String PREFERENCE_LAST_PATH = "preference_last_selected_path";
    protected static final int ITEM_ID_BROWSER = 1;
    protected static final int ITEM_ID_VIDEO_FOLDER = 7;
    protected static final int ITEM_ID_OFFSET = 7;
    protected static final int ITEM_ID_SMB = 2;
    protected static final int ITEM_ID_UPNP = 3;
    protected static final int ITEM_ID_FTP = 4;
    protected static final int ITEM_ID_PROVIDER = 6;
    protected static final int ITEM_ID_NETWORK = 5;
    final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getData() != null) {
                    VideoInfoActivity.startInstance(getActivity(), null, result.getData().getData(), -1L);
                }
            });

    private int mLibrarySize;
    protected int mSelectedItemId;
    private int mSelectedItemTop;
    protected ArrayList<Object> mCategoryList;
    private CategoryAdapter mCategoryAdapter;
    private LayoutCallback mLayoutCallback;
    protected SharedPreferences mPreferences;
    protected int mOldSelectedItemId;

    private NetworkState networkState = null;
    private PropertyChangeListener propertyChangeListener = null;
    private boolean mNetworkStateListenerAdded = false;
    private boolean mEnableSponsor = false;
    private SharedPreferences.OnSharedPreferenceChangeListener mThemeChangeListener;

    /**
     * This object is used to store basic info for the category list item.
     */
    public static class ItemData {
        public int icon=-1;
        public int text;
        public String path;
        public int id;
    };

    public static class FragmentTitleStruc{
        public Fragment fragment;
        public int title;
    }

    private class LayoutCallback implements BrowserLayout.Callback {
        // The fragment to start when the callback is called.
        Fragment fragment;

        public void onLayoutChanged() {
            // Always update the category content when the application layout has changed
            // (i.e. after switching between the cover roll and the category content)
            if (fragment != null) {
                updateCategoryContent(fragment, fragment.getTag());
                fragment = null;
            }
        }
        public void onGoHome() {
            ((BrowserActivity) getActivity()).goHome();
        }
    }

    /**
     * Update (un)mount sdcard/usb host/samba/upnp.
     */
    private final BroadcastReceiver mExternalStorageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "mExternalStorageReceiver: intent " + intent);
            String action = intent.getAction();
            if (action.equals(ExtStorageReceiver.ACTION_MEDIA_MOUNTED)){
                String path = null;
                if(intent.getDataString().startsWith("file"))
                    path = intent.getDataString().substring("file".length());
                else if (intent.getDataString().startsWith(ExtStorageReceiver.ARCHOS_FILE_SCHEME))
                    path = intent.getDataString().substring(ExtStorageReceiver.ARCHOS_FILE_SCHEME.length());
                if (path == null || path.isEmpty())
                    return;
                mSelectedItemId = 0;
                updateExternalStorage();
            } else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_CHANGED)){
                updateExternalStorage();
            } else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED)){
                final String path = intent.getDataString();
                if (path == null || path.isEmpty())
                    return;
                mSelectedItemId = 0;
                updateExternalStorage();
            }
        }
    };

    public void onSaveInstanceState(Bundle outState) {
        if (mSelectedItemId != 0) {
            outState.putInt(SELECTED_ID, mSelectedItemId);
            outState.putInt(SELECTED_TOP, mSelectedItemTop);
        }
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ListView categoryView = (ListView) inflater.inflate(R.layout.browser_category, container, false);
        categoryView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        // Check if we're in phone mode with black theme for special selector
        ThemeManager themeManager = ThemeManager.getInstance(getContext());
        if (themeManager.isBlackTheme() && themeManager.isPhoneMode(getContext())) {
            // Phone + Black theme: Use rounded grey selector
            categoryView.setSelector(R.drawable.phone_category_selector);
        } else {
            // TV or Blue theme: Use standard selector
            android.graphics.drawable.StateListDrawable selector = new android.graphics.drawable.StateListDrawable();
            int selectorColor = themeManager.getCategorySelectorColor();
            // Activated state (selected item)
            selector.addState(new int[]{android.R.attr.state_activated}, new android.graphics.drawable.ColorDrawable(selectorColor));
            // Focused state (keyboard navigation)
            selector.addState(new int[]{android.R.attr.state_focused}, new android.graphics.drawable.ColorDrawable(selectorColor));
            // Default state (transparent)
            selector.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            categoryView.setSelector(selector);
        }
        return categoryView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String lastPath = null;
        if (savedInstanceState != null) {
            mSelectedItemId = savedInstanceState.getInt(SELECTED_ID);
            mSelectedItemTop = savedInstanceState.getInt(SELECTED_TOP);
        }else{
            mSelectedItemId = mPreferences.getInt(PREFERENCE_LAST_FRAGMENT, getDefaultId());
            lastPath = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PREFERENCE_LAST_PATH, null);
            if(!checkAvailability(mSelectedItemId, lastPath))
                mSelectedItemId = getDefaultId();

        }
        mLayoutCallback = new LayoutCallback();

        updateLibrary();
        mCategoryAdapter = new CategoryAdapter(getActivity().getApplicationContext());
        setListAdapter(mCategoryAdapter);

        // in some release mSelectedItemId and path can be in a weird state
        // meaning that mSelectedItemId is BrowserExt and path is null
        // avoiding this special case
        // I know this is ugly
        if(mSelectedItemId == ITEM_ID_BROWSER && lastPath == null)
            mSelectedItemId = BrowserCategoryVideo.ITEM_ID_RECENTLY_ADDED;

        if(savedInstanceState==null) //restore only when starting from scratch
            setFragment(lastPath);

        if(getActivity() instanceof BrowserActivity)
            ((MainActivity)getActivity()).updateHomeIcon(getParentFragmentManager().getBackStackEntryCount()>1);

        // handles NetworkState changes
        networkState = NetworkState.instance(getContext());
        if (propertyChangeListener == null)
            propertyChangeListener = evt -> {
                if (evt.getOldValue() != evt.getNewValue()) {
                    if (DBG) Log.d(TAG, "onViewCreated: updateUI NetworkState for " + evt.getPropertyName() + " changed:" + evt.getOldValue() + " -> " + evt.getNewValue());
                    updateUI();
                }
            };
        updateUI(); // get initialization right

        // Register theme change listener to update category selector color
        mThemeChangeListener = (sharedPreferences, key) -> {
            if (ThemeManager.KEY_APP_THEME.equals(key)) {
                ListView listView = getListView();
                if (listView != null) {
                    android.graphics.drawable.StateListDrawable selector = new android.graphics.drawable.StateListDrawable();
                    int selectorColor = ThemeManager.getInstance(getContext()).getCategorySelectorColor();
                    // Activated state (selected item)
                    selector.addState(new int[]{android.R.attr.state_activated}, new android.graphics.drawable.ColorDrawable(selectorColor));
                    // Focused state (keyboard navigation)
                    selector.addState(new int[]{android.R.attr.state_focused}, new android.graphics.drawable.ColorDrawable(selectorColor));
                    // Default state (transparent)
                    selector.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                    listView.setSelector(selector);
                }
                // Refresh adapter to update item backgrounds
                if (mCategoryAdapter != null) {
                    mCategoryAdapter.notifyDataSetChanged();
                }
            }
        };
        ThemeManager.getInstance(getContext()).registerThemeChangeListener(mThemeChangeListener);
    }

    private void addNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(getContext());
        if (!mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (DBG_LISTENER) Log.d(TAG, "addNetworkListener: networkState.addPropertyChangeListener");
            networkState.addPropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = true;
        }
    }

    private void removeNetworkListener() {
        if (networkState == null) networkState = NetworkState.instance(getContext());
        if (mNetworkStateListenerAdded && propertyChangeListener != null) {
            if (DBG_LISTENER) Log.d(TAG, "removeListener: networkState.removePropertyChangeListener");
            networkState.removePropertyChangeListener(propertyChangeListener);
            mNetworkStateListenerAdded = false;
        }
    }

    protected abstract int getDefaultId();

    protected boolean checkAvailability(int selectedItemId, String lastPath){
        if(lastPath!=null&&(selectedItemId == ITEM_ID_BROWSER)){
            return new File(lastPath).exists();
        }
        return true;
    }

    private void updateUI() {
        if (getActivity() != null)
            getActivity().runOnUiThread(() -> {
                if (DBG) Log.d(TAG, "updateUI");
                // run this on UI thread
                updateExternalStorage();
            });
    }

    public void onResume() {
        if (DBG) Log.d(TAG, "onResume");
        super.onResume();
        // Listen (un)mount sdcard/usb host/samba/upnp events.
        IntentFilter intentFilter = new IntentFilter(ExtStorageReceiver.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_CHANGED);
        intentFilter.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);//new android nougat send UriExposureException when scheme = file
        intentFilter.addDataScheme("file");
        if (Build.VERSION.SDK_INT >= 33) {
            getActivity().registerReceiver(mExternalStorageReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(mExternalStorageReceiver, intentFilter);
        }
        addNetworkListener();
        // Remove non constant category items.
        for (int index = mCategoryList.size() - 1; index >= mLibrarySize; index--)
            mCategoryList.remove(index);
        updateExternalStorage();
    }

    public void onPause() {
        if (DBG) Log.d(TAG, "onPause");
        getActivity().unregisterReceiver(mExternalStorageReceiver);
        removeNetworkListener();
        // Unregister theme change listener
        if (mThemeChangeListener != null) {
            ThemeManager.getInstance(getContext()).unregisterThemeChangeListener(mThemeChangeListener);
        }
        super.onPause();
    }

    public void setFragment(String path){
        FragmentTitleStruc struc = getContentFragmentAndTitle(mSelectedItemId);
        Fragment f = struc.fragment;
        if (path != null && !path.isEmpty()) {
            Bundle b = new Bundle();
            b.putString(BrowserCategory.MOUNT_POINT, path);
            f.setArguments(b);
        }
        loadFragmentAfterStackReset(f);
        if (getActivity() != null && ((AppCompatActivity)getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(struc.title);
        }
    }

    /**
     * Called when the user has clicked on a category item
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Object object = mCategoryList.get(position);
        if (object instanceof ItemData) {
            ItemData item = (ItemData) object;
            if(item.text == R.string.preferences){
                if(getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).startPreference();
                setSelection(mSelectedItemId); //restore selection
            } else if (item.text == R.string.rescan){
                NetworkAutoRefresh.forceRescan(getActivity());
                setSelection(mSelectedItemId); //restore selection
            } else if (item.text == R.string.help_faq){
                WebUtils.openWebLink(getActivity(),getString(R.string.faq_url));
            } else if (item.text == R.string.sponsor){
                WebUtils.openWebLink(getActivity(),getString(R.string.sponsor_url));
            } else if(item.text  == R.string.activate_private_mode || item.text  == R.string.deactivate_private_mode){
                if (!PrivateMode.isActive() && PrivateMode.canShowDialog(getActivity())) {
                    PrivateMode.showDialog(getActivity());
                }
                PrivateMode.toggle();
                setSelection(mSelectedItemId); //restore selection
                ((MainActivity) getActivity()).setBackground();
                updateExternalStorage();
            } else {

                updateListSelection(v, item);
                setFragment(item.path);
                if(item.id!=ITEM_ID_PROVIDER) { //don't save when provider to avoid restarting with android browser view
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putInt(PREFERENCE_LAST_FRAGMENT, mSelectedItemId).apply();
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putString(PREFERENCE_LAST_PATH, item.path).apply();
                }
                if (getActivity() instanceof MainActivity)
                    ((MainActivity) getActivity()).closeDrawer();
            }

        }
    }

    protected void updateListSelection(View v, ItemData item) {
        mOldSelectedItemId = mSelectedItemId;
        mSelectedItemId = item.id;
        if(v!=null)
            mSelectedItemTop = v.getTop();
    }

    public void loadFragmentAfterStackReset(Fragment f) {
        FragmentManager fm = getParentFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            // Clear the back stack as a new category is started.
            fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        // Update the content of the fragment corresponding to the selected category
        // and make it visible if needed
        startContent(f);
    }

    /**
     * Returns the fragment corresponding to the category name
     */
    abstract public FragmentTitleStruc getContentFragmentAndTitle(int id);

    public void startContent(Fragment fragment) {
        startContent(fragment, null);
    }

    public void startContent(Fragment fragment, String tag) {
         updateCategoryContent(fragment, tag);
    }

    private void showCategoryContent(Fragment fragment) {
        // Make the fragment corresponding to the current category visible
        mLayoutCallback.fragment = fragment;
    }

    private void updateCategoryContent(Fragment fragment, String tag) {
        // Update the content of the fragment corresponding to the current category
        FragmentManager fm = getParentFragmentManager();
        if (fm != null) {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            ft.setCustomAnimations(R.anim.browser_content_enter,
                R.anim.browser_content_exit, R.anim.browser_content_pop_enter,
                R.anim.browser_content_pop_exit);
            ft.replace(R.id.content, fragment, tag);
            ft.addToBackStack(null);
            ft.commitAllowingStateLoss();
            if(getActivity() instanceof BrowserActivity)
                ((MainActivity)getActivity()).updateHomeIcon(getParentFragmentManager().getBackStackEntryCount()>0);
        }
    }

    /**
     * Update the library category's items
     */
    private void updateLibrary() {
        if (mCategoryList == null)
            mCategoryList = new ArrayList<Object>();
        else
            mCategoryList.clear();

        mCategoryList.add(getText(R.string.goto_start));
        setLibraryList(mCategoryList);
        mLibrarySize = mCategoryList.size();
    }

    /**
     * Add the library category items.
     */
    abstract public void setLibraryList(ArrayList<Object> categoryList);


    /**
     * Update the view for network, sd card and usb host.
     */
    private void updateExternalStorage() {
        if (DBG) Log.d(TAG, "updateExternalStorage");
        // First, remove old external items.
       boolean oneAgain = true;
        // Remove non constant category items.
        for (int index = mCategoryList.size() - 1; index >= mLibrarySize; index--) {
            mCategoryList.remove(index);
        }
       /*  while (oneAgain) {
            int lastIndex = mCategoryList.size() - 1;
            Object item = mCategoryList.get(lastIndex);
            if (item instanceof CharSequence) {
                mCategoryList.remove(lastIndex);
                oneAgain = false;
            } else {
                boolean contains = false;
                int size = mExternalIDs.length;
                int id = ((ItemData) item).text;
                for (int i = 0; i < size && !contains; i++) {
                    if (id == mExternalIDs[i])
                        contains = true;
                }
                if (contains)
                    mCategoryList.remove(lastIndex);
                else
                    oneAgain = false;
            }
        }
        */
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        final boolean hasExternal = storageManager.hasExtStorage();
        final boolean isConnected = isConnected();
        if (hasExternal|| isConnected || NetworkState.isNetworkConnected(getActivity())) {
            mCategoryList.add(getText(R.string.leanback_browsing));

            {
                ItemData itemData2 = new ItemData();
                itemData2.icon = R.drawable.category_common_folder;
                itemData2.text = R.string.root_storage;
                itemData2.id = ITEM_ID_VIDEO_FOLDER;
                mCategoryList.add(itemData2);
            }

            if (hasExternal) {
                for(String s : storageManager.getExtSdcards()) {
                    ItemData itemData = new ItemData();
                    itemData.icon = R.drawable.category_common_sdcard;
                    itemData.text = R.string.sd_card_storage;
                    itemData.path = s;
                    itemData.id = ITEM_ID_BROWSER;
                    mCategoryList.add(itemData);
                }
                for(String s : storageManager.getExtUsbStorages()) {
                    ItemData itemData = new ItemData();
                    itemData.icon = R.drawable.category_common_usb;
                    itemData.text = R.string.usb_host_storage;
                    itemData.path = s;
                    itemData.id = ITEM_ID_BROWSER;
                    mCategoryList.add(itemData);
                }
                for(String s : storageManager.getExtOtherStorages()) {
                    ItemData itemData = new ItemData();
                    itemData.icon = R.drawable.category_common_folder;
                    itemData.text = R.string.other_storage;
                    itemData.path = s;
                    itemData.id = ITEM_ID_BROWSER;
                    mCategoryList.add(itemData);
                }
            }

            if (isConnected){
                ItemData itemData = new ItemData();
                itemData.icon = R.drawable.category_common_network;
                itemData.text = R.string.network_shared_folders;
                itemData.id = ITEM_ID_SMB;
                mCategoryList.add(itemData);
            }

            if (isConnected){
                ItemData itemData = new ItemData();
                itemData.icon = R.drawable.category_common_network;
                itemData.text = R.string.network_media_servers;
                itemData.id = ITEM_ID_UPNP;
                mCategoryList.add(itemData);
            }
            if ( NetworkState.isNetworkConnected(getActivity())){
                ItemData itemData = new ItemData();
                itemData.icon = R.drawable.category_network_shortcut;
                itemData.text = R.string.network_shortcuts;
                itemData.id = ITEM_ID_NETWORK;
                mCategoryList.add(itemData);
            }
        }
        // one could argue that "cloud" should be made available only if connected
        // but offline capability is present in drive and provider is more generic
        // than cloud in reality. Perhaps think of better name
        ItemData itemData = new ItemData();
        itemData.icon = R.drawable.cloud_24px;
        itemData.text = R.string.provider_folders;
        itemData.id = ITEM_ID_PROVIDER;
        mCategoryList.add(itemData);
        addLastItems();
        mCategoryAdapter.notifyDataSetChanged();
        // Set the selection when rotating.
        if (mSelectedItemId != 0) {
            int index = 0;
            oneAgain = true;
            while (index < mCategoryList.size() && oneAgain) {
                Object o = mCategoryList.get(index);
                if (o instanceof ItemData && ((ItemData) o).id == mSelectedItemId) {
                    oneAgain = false;
                    ListView lv = getListView();
                    lv.setItemChecked(index, true);
                    lv.setSelectionFromTop(index, mSelectedItemTop);
                }
                index++;
            }
        }
    }

    private void addLastItems() {
        mCategoryList.add("Nova" + " v" + getText(R.string.VERSION_NAME));
        ItemData itemData = new ItemData();
        itemData.icon = R.drawable.android29_ic_settings;
        itemData.text = R.string.preferences;
        mCategoryList.add(itemData);
        itemData = new ItemData();
        itemData.icon = R.drawable.android29_ic_rescan;
        itemData.text = R.string.rescan;
        mCategoryList.add(itemData);
        itemData = new ItemData();
        itemData.icon = R.drawable.android29_ic_menu_help;
        itemData.text = R.string.help_faq;
        mCategoryList.add(itemData);
        // Google Play is allergic to piggies... no donation button
        if (BuildConfig.ENABLE_SPONSOR) mEnableSponsor = mPreferences.getBoolean(VideoPreferencesCommon.KEY_ENABLE_SPONSOR, VideoPreferencesCommon.ENABLE_SPONSOR_DEFAULT);
        if (((! ArchosUtils.isInstalledfromPlayStore(getActivity().getApplicationContext())) || mEnableSponsor) && BuildConfig.ENABLE_SPONSOR) {
            itemData = new ItemData();
            itemData.icon = R.drawable.piggy_bank;
            itemData.text = R.string.sponsor;
            mCategoryList.add(itemData);
        }
    }

    public void clearCheckedItem() {
        ListView lv = getListView();
        int checkPosition = lv.getCheckedItemPosition();
        if (checkPosition != ListView.INVALID_POSITION) {
            lv.setItemChecked(checkPosition,false);
        }
        mSelectedItemId = 0;
    }

    private boolean isConnected(){
        if (mPreferences.getBoolean(getString(R.string.preferences_network_mobile_vpn_key), false))
            return NetworkState.isNetworkConnected(getActivity());
        else
            return NetworkState.isLocalNetworkConnected(getActivity());
    }

    /**
     * Adapter class for displaying category items.
     */
    protected class CategoryAdapter extends BaseAdapter {
        static final private int ITEM_VIEW_TYPE_CATEGORY = 0;
        static final private int ITEM_VIEW_TYPE_SEPARATOR = 1;
        static final private int ITEM_VIEW_TYPE_COUNT = 2;
        private final LayoutInflater inflater;

        public CategoryAdapter(Context context) {
            super();
            inflater = LayoutInflater.from(context);
        }

        public boolean areAllItemsEnabled() {
            return false;
        }
        public int getCount() {
            return mCategoryList.size();
        }

        public Object getItem(int position) {
            return mCategoryList.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public int getViewTypeCount() {
            return ITEM_VIEW_TYPE_COUNT;
        }

        public int getItemViewType(int position) {
            int type;
            Object v = mCategoryList.get(position);
            if (v instanceof CharSequence){
                type = ITEM_VIEW_TYPE_SEPARATOR;
            }
            else {
                type = ITEM_VIEW_TYPE_CATEGORY;
            }
            return type;
        }

        public boolean isEnabled(int position) {
            // A separator cannot be clicked !
            return getItemViewType(position) == ITEM_VIEW_TYPE_CATEGORY;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final int type = getItemViewType(position);
            if (convertView == null) {
                if (type == ITEM_VIEW_TYPE_CATEGORY) {
                    convertView = inflater.inflate(R.layout.browser_category_item_shortcut, parent, false);
                } else {
                    convertView = inflater.inflate(R.layout.browser_category_item_separator, parent, false);
                }
            }

            final TextView tv = (TextView) convertView;
            if (type == ITEM_VIEW_TYPE_CATEGORY) {
                // Create theme-aware background selector
                android.graphics.drawable.StateListDrawable bgSelector = new android.graphics.drawable.StateListDrawable();
                int selectorColor = ThemeManager.getInstance(getContext()).getCategorySelectorColor();
                // Pressed state
                bgSelector.addState(new int[]{android.R.attr.state_pressed}, new android.graphics.drawable.ColorDrawable(selectorColor));
                // Activated/selected state
                bgSelector.addState(new int[]{android.R.attr.state_activated}, new android.graphics.drawable.ColorDrawable(selectorColor));
                // Default state (transparent)
                bgSelector.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                convertView.setBackground(bgSelector);
                // Set the category name
                ItemData item = (ItemData) mCategoryList.get(position);
                tv.setText(item.text);
                if(item.icon!=-1)
                    tv.setCompoundDrawablesWithIntrinsicBounds(item.icon, 0 , 0 , 0);
                else tv.setCompoundDrawablesWithIntrinsicBounds(0, 0 , 0 , 0);
            }
            else {
                tv.setText((CharSequence) mCategoryList.get(position));
            }

            return convertView;
        }

        /*private int getSelectedItemIndex() {
            // Look for the index of the item whose id is equal to
            // mSelectedItemId 
            int size = mCategoryList.size();
            int index = 0;
            while (index < size) {
                Object o = mCategoryList.get(index);
                if (o instanceof ItemData && ((ItemData) o).text == mSelectedItemId) {
                    return index;
                }
                index++;
            }
            return -1;
        }*/
    }
}
