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

package com.archos.mediacenter.video.utils;

import java.util.ArrayList;
import java.util.List;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.ListingEngineFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.R;


import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.IntentFilter;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class FolderPicker extends FragmentActivity {

    private static final String TAG = "FolderPicker";
    private static final boolean DBG = false;

    private static final Uri INTERNAL_STORAGE = Uri.parse(Environment.getExternalStorageDirectory().getAbsolutePath());
    // Our virtual root where we list all the available storages: internal, sdcard, otg, etc.
    private static final Uri VIRTUAL_ROOT_POINT = Uri.parse("/");

    // the default starting point
    private static final Uri DEFAULT_STARTING_POINT = INTERNAL_STORAGE;
    //private static final Uri DEFAULT_STARTING_POINT = Uri.parse(Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+Environment.DIRECTORY_MOVIES);

    /**
     * Extra to be put in the calling intent to specify the currently selected folder.
     * The listed folder will be the parent of this selected folder.
     * This extra is a String.
     */
    public static final String EXTRA_DIALOG_TITLE = "EXTRA_DIALOG_TITLE";

    /**
     * Extra to be put in the calling intent to specify the currently selected folder.
     * The listed folder will be the parent of this selected folder.
     * This extra is a String (the full path).
     */
    public static final String EXTRA_CURRENT_SELECTION = "EXTRA_CURRENT_SELECTION";

    /**
     * Extra to be put in the result intent to specify the resulting selected folder
     * This extra is a String (the full path).
     */
    public static final String EXTRA_SELECTED_FOLDER = "EXTRA_SELECTED_FOLDER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            FolderPickerDialogFragment df = new FolderPickerDialogFragment();

            // get the dialog title
            String customTitle = getIntent().getStringExtra(EXTRA_DIALOG_TITLE);
            if (customTitle != null) {
                df.setDialogTitle(customTitle);
            }

            // init the current selection
            String extraCurrentSelection = getIntent().getStringExtra(EXTRA_CURRENT_SELECTION);
            if (extraCurrentSelection != null) {
                df.setSelectedFolder(Uri.parse(extraCurrentSelection));
            }

            df.show(getSupportFragmentManager(), FolderPickerDialogFragment.FRAGMENT_TAG);
        }
    }

    static private boolean isOneOfTheRootStorageItems(Uri uri) {
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        boolean ret = INTERNAL_STORAGE.getPath().equals(uri.getPath())
                      || storageManager.getExtSdcards().contains(uri.getPath())
                      || storageManager.getExtUsbStorages().contains(uri.getPath());

        if(DBG) Log.d(TAG, "isOneOfTheRootStorageItems " + uri + " returns " + ret);
        return ret;
    }

    static private Uri getParent(Uri uri) {
        if (isOneOfTheRootStorageItems(uri)) {
			return VIRTUAL_ROOT_POINT;
		} else {
            Uri parentUri = FileUtils.getParentUrl(uri);
            if (parentUri != null) {
                String parent = parentUri.toString();
                if (parent != null && !parent.isEmpty() && parent.endsWith("/")) {
                    int index = parent.lastIndexOf("/");
                    if (index > 0) {
                        parent = parent.substring(0, index);
                    }
                }
                return Uri.parse(parent);
            }
        }
        return VIRTUAL_ROOT_POINT;
    }

    static private boolean isAvailable(String mountedStatus) {
        if (Environment.MEDIA_MOUNTED.equals(mountedStatus) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(mountedStatus)) {
            return true;
        } else {
            return false;
        }
    }

    static public class FolderPickerDialogFragment extends DialogFragment implements OnItemClickListener, ListingEngine.Listener {

        public static final String FRAGMENT_TAG = "FolderPickerDialogFragment";

        private static ListingEngine mListingEngine = null;
        private static LayoutInflater mLayoutInflater = null;

        private class Item {
            public Object mHolder;
            public String mName;
            public Uri mUri;
            public boolean mEnabled;

            public final String getName() {
                if (mName != null) {
                    return mName;
                }
                return FileUtils.getName(mUri);
			}
		}

        /**
         * The list of items (folders for now) displayed in the list view, i.e. the content of mCurrentFolderDisplayed
         */
        private ArrayList<Item> mListItems;

        /**
         * Optimization to avoid making too many string comparisons in the adapter
         */
        private boolean mCurrentlyInVirtualRoot = false;

        /**
         * The folder is which the user is currenlty located, i.e. which content is displayed
         */
        private Uri mSelectedFolder = null;

        /**
         * Custom dialog title
         * May be null, in that case use a default title
         */
        private String mDialogTitle;

        /**
         * UI elements
         */
        private TextView mCurrentSelectionTv;
        private ListView mListView;
        private ImageButton mBackButton;
        private Button mOkButton;

        /**
         * Used by the activity to init the selected folder value
         * @param selectedFolder
         */
        public void setSelectedFolder(Uri selectedFolder) {
            mSelectedFolder = selectedFolder;
        }

        /**
         * Used by the activity to set a custom dialog folder
         * @param dialogTitle title
         */
        public void setDialogTitle(String dialogTitle) {
            mDialogTitle = dialogTitle;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (mSelectedFolder == null) {
                if (savedInstanceState != null) {
                    mSelectedFolder = Uri.parse(savedInstanceState.getString("mSelectedFolder"));
                } else {
                    mSelectedFolder = DEFAULT_STARTING_POINT;
                }
            }

            if (mLayoutInflater == null) {
                mLayoutInflater = LayoutInflater.from(getActivity().getApplicationContext());
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (mSelectedFolder != null) {
                outState.putString("mSelectedFolder", mSelectedFolder.toString());
            }
            super.onSaveInstanceState(outState);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mListingEngine != null) {
                mListingEngine.abort();
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            // Listen (un)mount sdcard/usb host/samba/upnp events.
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            intentFilter.addAction(Intent.ACTION_MEDIA_SHARED);
            intentFilter.addDataScheme("file");
            intentFilter.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);//new android nougat send UriExposureException when scheme = file
            getActivity().registerReceiver(mExternalStorageReceiver, intentFilter);
        }

        @Override
        public void onPause() {
            getActivity().unregisterReceiver(mExternalStorageReceiver);
            super.onPause();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Let's inflate the custom view first
            View customView = mLayoutInflater.inflate(R.layout.folder_picker_dialog, null);
            mListView = (ListView)customView.findViewById(R.id.listview);
            mListView.setOnItemClickListener(this);
            mListView.setEmptyView(customView.findViewById(R.id.emptyview));

            mBackButton = (ImageButton)customView.findViewById(R.id.back_button);
            mBackButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });

            mCurrentSelectionTv = (TextView)customView.findViewById(R.id.current_selection);
            if (mSelectedFolder != null) {
                mCurrentSelectionTv.setText(mSelectedFolder.getPath());
            } else {
                mCurrentSelectionTv.setText(R.string.no_selection);
            }

            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (mDialogTitle != null) {
                builder.setTitle(mDialogTitle);
            } else {
                builder.setTitle(R.string.choose_a_folder);
            }
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    getActivity().setResult(RESULT_OK, new Intent().putExtra(EXTRA_SELECTED_FOLDER, mSelectedFolder.getPath()));
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // User cancelled the dialog
                    getActivity().setResult(RESULT_CANCELED);
                }
            });
            builder.setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    // We handle the BACK key in the activity
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                        onBackPressed();
                        return true;
                    }
                    return false;
                }
            })
            .setCancelable(false)
            .setView(customView);

            loadFolder(mSelectedFolder);

            AlertDialog ad = builder.create();

            // Need to setup this listener to get the ok button once it is actually allocated...
            ad.setOnShowListener(new OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    AlertDialog ad = (AlertDialog)dialog;
                    mOkButton = ad.getButton(DialogInterface.BUTTON_POSITIVE);
                    setOkButtonState(); // init its state once it is valid
                }
            });

            return ad;
        }

        /**
         * Close the host invisible activity when the dialog is closed
         */
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (getActivity() != null) { // better safe than sorry with fragments life-cycle...
                getActivity().finish();
            }
            super.onDismiss(dialog);
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (!view.isEnabled()) {
                return;
            }
            mSelectedFolder = mListItems.get(position).mUri;
            loadFolder(mSelectedFolder);
        }

        public void onBackPressed() {
            if (mSelectedFolder.equals(VIRTUAL_ROOT_POINT)) {
                // BACK key when already at root closes the activity
                getActivity().finish();
            } else {
                mSelectedFolder = getParent(mSelectedFolder);
                loadFolder(mSelectedFolder);
            }
        }

        private void loadFolder(Uri uri) {
            if(DBG) Log.d(TAG, "loadFolder " + uri);
            if (uri.equals(VIRTUAL_ROOT_POINT)) {
                loadVirtualRoot();
                // update the current selection
                mCurrentSelectionTv.setText(R.string.no_selection); // virtual root is not a valid location
                // disable the back button at root level
                mBackButton.setEnabled(false);
            } else {
                mCurrentlyInVirtualRoot = false;

                if (mListingEngine != null) {
                    mListingEngine.abort();
                }
                mListingEngine = ListingEngineFactory.getListingEngineForUrl(getActivity(), uri);
                mListingEngine.setListener(this);
                mListingEngine.start();

                // update the current selection
                mCurrentSelectionTv.setText(uri.getPath());
                // enable the back button at non-root level
                mBackButton.setEnabled(true);
            }
            // update the ok button
            setOkButtonState();
        }

        private void loadVirtualRoot() {
            final Resources res = getResources();
            mListItems = new ArrayList<Item>(3);

            Item internal = new Item();
            internal.mUri = INTERNAL_STORAGE;
            internal.mName = res.getString(R.string.external_storage_fake_name);
            internal.mHolder = res.getDrawable(R.drawable.folder);
            internal.mEnabled = true; // internal storage always available (almost...)
            mListItems.add(internal);

            ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
            final boolean hasExternal = storageManager.hasExtStorage();
            if (hasExternal) {
                for (String s : storageManager.getExtSdcards()) {
                    Item sdcard = new Item();
                    sdcard.mUri = Uri.parse(s);
                    sdcard.mName = res.getString(R.string.sdcard_fake_name);
                    sdcard.mHolder = res.getDrawable(R.drawable.sdcard);
                    sdcard.mEnabled = true;
                    mListItems.add(sdcard);
                }
                for (String s : storageManager.getExtUsbStorages()) {
                    Item usbHost = new Item();
                    usbHost.mUri = Uri.parse(s);
                    usbHost.mName = res.getString(R.string.usb_host_fake_name);
                    usbHost.mHolder = res.getDrawable(R.drawable.usb);
                    usbHost.mEnabled = true;
                    mListItems.add(usbHost);
                }
            }

//          Item sdcard = new Item();
//          sdcard.mUri = SDCARD;
//          sdcard.mName = res.getString(R.string.sdcard_fake_name);
//          sdcard.mHolder = res.getDrawable(R.drawable.sdcard);
//          sdcard.mEnabled = isAvailable(ArchosUtils.getExternalStorageSDCardState());
//          mListItems.add(sdcard);
//
//          Item usbHost = new Item();
//          usbHost.mUri = USB_HOST;
//          usbHost.mName = res.getString(R.string.usb_host_fake_name);
//          usbHost.mHolder = res.getDrawable(R.drawable.usb);
//          usbHost.mEnabled = isAvailable(ArchosUtils.getExternalStorageUsbHostState());
//          mListItems.add(usbHost);

            // Optimization to avoid making too many string comparisons in the adapter
            mCurrentlyInVirtualRoot = true;

            ListAdapter la = new FolderArrayAdapter(getActivity(), android.R.layout.simple_list_item_1);
            mListView.setAdapter(la);
        }

        private void setOkButtonState() {
            if (mOkButton != null) { // does not exist yet at first dialog init
                mOkButton.setEnabled(!mCurrentlyInVirtualRoot);
            }
        }

        /**
         * Update (un)mount sdcard/usb host/samba/upnp.
         */
        private final BroadcastReceiver mExternalStorageReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                final String path = intent.getDataString().substring(7);
                if(DBG) Log.d(TAG, "mExternalStorageReceiver " + intent.getDataString());
                ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
                if (storageManager.getExtSdcards().contains(path) || storageManager.getExtUsbStorages().contains(path)) {
                    if(DBG) Log.d(TAG, "mExternalStorageReceiver onReceive: need to update");
                    if (mCurrentlyInVirtualRoot) {
                        loadVirtualRoot();
                    }
                }
            }
        };

        /**
         * The adapter
         * @author vapillon
         */
        public class FolderArrayAdapter extends ArrayAdapter<Uri> {

            private class ViewTag {
                public ViewTag(ImageView icon, TextView name, TextView info) {
                    mIcon = icon;
                    mName = name;
                    mInfo = info;
                }
                public ImageView mIcon;
                public TextView mName;
                public TextView mInfo;
            }


            public FolderArrayAdapter(Context context, int textViewResourceId) {
                super(context, textViewResourceId);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v;
                if (convertView == null) {
                    v = mLayoutInflater.inflate(R.layout.folder_picker_item, null);
                } else {
                    v = convertView;
                }
                if (v.getTag() == null) {
                    v.setTag(new ViewTag(
							(ImageView)v.findViewById(R.id.icon),
                            (TextView)v.findViewById(R.id.name),
                            (TextView)v.findViewById(R.id.info)));
                }
                ViewTag tag = (ViewTag)v.getTag();
                final Item item = mListItems.get(position);
                tag.mName.setText(item.getName());
                tag.mInfo.setText(item.mUri.getPath());
                if (item.mHolder == null) {
                    tag.mIcon.setImageDrawable(getResources().getDrawable(R.drawable.folder));
                } else {
                    tag.mIcon.setImageDrawable((Drawable)item.mHolder);
                }

                // Is item enabled ?
                v.setEnabled(item.mEnabled);
                v.setAlpha(item.mEnabled ? 1.0f : 0.2f);

                return v;
            }

            @Override
            public int getCount() {
                return mListItems.size();
            }

            @Override
            public boolean areAllItemsEnabled() {
                return mCurrentlyInVirtualRoot;
            }

            @Override
            public boolean isEnabled(int position) {
                return mListItems.get(position).mEnabled;
            }
        }

        @Override
        public void onListingStart() {
        }

        @Override
        public void onListingUpdate(List<? extends MetaFile2> files) {

            Resources res = getResources();
            // Get the folders only
            mListItems = new ArrayList<Item>(files.size());
            for (MetaFile2 file : files) {
                Item item = new Item();
                item.mUri = file.getUri();
                if (file.isDirectory()) {
                    item.mHolder = res.getDrawable(R.drawable.filetype_music_folder);
                    item.mEnabled = true; // Only the folders are enabled
                } else {
                    item.mHolder = res.getDrawable(R.drawable.filetype_generic2);
                    item.mEnabled = false; // Only the folders are enabled
                }
                mListItems.add(item);
            }

            ListAdapter la = new FolderArrayAdapter(getActivity(), android.R.layout.simple_list_item_1);
            mListView.setAdapter(la);
        }

        @Override
        public void onListingEnd() {
        }

        @Override
        public void onListingTimeOut() {
        }

        @Override
        public void onCredentialRequired(Exception e) {
        }

        @Override
        public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
        }

        @Override
        public void onListingFileInfoUpdate(Uri uri, MetaFile2 metaFile2) {
        }
    }

}
