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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.mediacenter.upnp.UpnpAvailability;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.WebUtils;

import java.io.File;
import java.util.ArrayList;

abstract public class BrowserCategory extends ListFragment {

    private static final String SELECTED_ID = "selectedId";
    private static final String SELECTED_TOP = "selectedTop";
    public static final String MOUNT_POINT = "mount_point";
    private static final String TAG = "BrowserCategory";

    private static final int[] mExternalIDs = {
            R.string.sd_card_storage, R.string.usb_host_storage, R.string.other_storage, R.string.network_shared_folders,R.string.sftp_folders,
            R.string.network_media_servers, R.string.network_jcifs, R.string.network_cling,R.string.preferences
    };
    private static final String PREFERENCE_LAST_FRAGMENT = "preference_last_selected_fragment";
    private static final String PREFERENCE_LAST_PATH = "preference_last_selected_path";
    protected static final int ITEM_ID_BROWSER = 1;
    protected static final int ITEM_ID_OFFSET = 7;
    protected static final int ITEM_ID_SMB = 2;
    protected static final int ITEM_ID_UPNP = 3;
    protected static final int ITEM_ID_FTP = 4;
    protected static final int ITEM_ID_PROVIDER = 6;
    protected static final int FILE_CHOOSER_ACTIVITY_REQUEST_CODE = 788;


    private int mLibrarySize;
    protected int mSelectedItemId;
    private int mSelectedItemTop;
    protected ArrayList<Object> mCategoryList;
    private CategoryAdapter mCategoryAdapter;
    private LayoutCallback mLayoutCallback;
    protected SharedPreferences mPreferences;
    protected int mOldSelectedItemId;

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
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                final NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null
                        && (networkInfo.getState() == State.DISCONNECTED || networkInfo.getState() == State.CONNECTED)) {
                    updateExternalStorage();
                }
            } else if (action.equals(ExtStorageReceiver.ACTION_MEDIA_MOUNTED)){
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


        return categoryView;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode, data);
        if(requestCode == FILE_CHOOSER_ACTIVITY_REQUEST_CODE&&data!=null){
            //PlayUtils.startVideo(getActivity(), data.getData(), data.getData(), null, null, PlayerActivity.RESUME_FROM_LAST_POS, true,-1, null);
            VideoInfoActivity.startInstance(getActivity(), null, data.getData(),new Long(-1));
        }
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

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
            ((MainActivity)getActivity()).updateHomeIcon(getFragmentManager().getBackStackEntryCount()>1);

    }

    protected abstract int getDefaultId();

    protected boolean checkAvailability(int selectedItemId, String lastPath){

        if(lastPath!=null&&(selectedItemId == ITEM_ID_BROWSER)){
            return new File(lastPath).exists();
        }
        return true;
    }

    public void onResume() {
        super.onResume();

        // Listen (un)mount sdcard/usb host/samba/upnp events.
        IntentFilter networkIntent = new IntentFilter();
        networkIntent.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(mExternalStorageReceiver, networkIntent);
        IntentFilter intentFilter = new IntentFilter(ExtStorageReceiver.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addAction(ExtStorageReceiver.ACTION_MEDIA_CHANGED);
        intentFilter.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);//new android nougat send UriExposureException when scheme = file
        intentFilter.addDataScheme("file");
        getActivity().registerReceiver(mExternalStorageReceiver, intentFilter);

        // Remove non constant category items.
        for (int index = mCategoryList.size() - 1; index >= mLibrarySize; index--) {
            mCategoryList.remove(index);
        }
        updateExternalStorage();
    }

    public void onPause() {
        getActivity().unregisterReceiver(mExternalStorageReceiver);

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
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(struc.title);

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
            }
            else if (item.text == R.string.help_faq){
                WebUtils.openWebLink(getActivity(),"https://home.courville.org/nova_video_player-faq/index.html");
            }
            else if(item.text  == R.string.activate_private_mode || item.text  == R.string.deactivate_private_mode){
                if (!PrivateMode.isActive() && PrivateMode.canShowDialog(getActivity())) {
                    PrivateMode.showDialog(getActivity());
                }
                PrivateMode.toggle();
                setSelection(mSelectedItemId); //restore selection
                ((MainActivity) getActivity()).setBackground();
                updateExternalStorage();
            }
            else {

                updateListSelection(v, item);
                setFragment(item.path);
                if(item.id!=ITEM_ID_PROVIDER) { //don't save when provider to avoid restarting with android browser view
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putInt(PREFERENCE_LAST_FRAGMENT, mSelectedItemId).commit();
                    PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putString(PREFERENCE_LAST_PATH, item.path).commit();
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
        FragmentManager fm = getFragmentManager();
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
        FragmentManager fm = getFragmentManager();
        if (fm != null) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            if(ArchosUtils.shouldAnimate())
                ft.setCustomAnimations(R.anim.browser_content_enter,
                    R.anim.browser_content_exit, R.anim.browser_content_pop_enter,
                    R.anim.browser_content_pop_exit);
            ft.replace(R.id.content, fragment, tag);
            ft.addToBackStack(null);
            ft.commitAllowingStateLoss();
            if(getActivity() instanceof BrowserActivity)
                ((MainActivity)getActivity()).updateHomeIcon(getFragmentManager().getBackStackEntryCount()>0);
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
        if (hasExternal|| isConnected || ArchosUtils.isNetworkConnected(getActivity())) {
            mCategoryList.add(getText(R.string.external_storage));

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

            if (isConnected && UpnpAvailability.isUpnpAvaialbe()){
                ItemData itemData = new ItemData();
                itemData.icon = R.drawable.category_common_network;
                itemData.text = R.string.network_media_servers;
                itemData.id = ITEM_ID_UPNP;
                mCategoryList.add(itemData);
            }
            if ( ArchosUtils.isNetworkConnected(getActivity())){
                ItemData itemData = new ItemData();
                itemData.icon = R.drawable.category_common_network;
                itemData.text = R.string.sftp_folders;
                itemData.id = ITEM_ID_FTP;
                mCategoryList.add(itemData);
            }
        }
        ItemData itemData = new ItemData();
        itemData.icon = R.drawable.category_common_network;
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

        mCategoryList.add("");
        ItemData itemData = new ItemData();
        itemData.icon = R.drawable.category_settings;
        itemData.text = R.string.preferences;
        mCategoryList.add(itemData);
        itemData = new ItemData();
        itemData.icon = R.drawable.category_help;
        itemData.text = R.string.help_faq;
        mCategoryList.add(itemData);
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
            return ArchosUtils.isNetworkConnected(getActivity());
        else
            return ArchosUtils.isLocalNetworkConnected(getActivity());
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
                convertView.setBackgroundResource(R.drawable.category_item_background_normal);
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
