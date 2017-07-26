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

package com.archos.mediacenter.video.browser.filebrowsing.network;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;

import com.archos.mediacenter.utils.ActionItem;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.utils.QuickAction;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser.BrowserBySFTP;
import com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser.BrowserBySmb;
import com.archos.mediacenter.video.browser.filebrowsing.network.UpnpBrowser.BrowserByUpnp;
import com.archos.mediaprovider.NetworkScanner;
import com.archos.mediaprovider.video.NetworkScannerServiceVideo;

public abstract class NewRootFragment extends Fragment implements  WorkgroupShortcutAndServerAdapter.OnShortcutTapListener,  WorkgroupShortcutAndServerAdapter.OnRefreshClickListener, NetworkScannerServiceVideo.ScannerListener {

    private static final String TAG = "NewRootFragment";

    private RecyclerView mDiscoveryList;
    private RecyclerView.LayoutManager mLayoutManager;
    protected RootFragmentAdapter mAdapter;
    private Toast mToast;
    protected QuickAction mQuickAction;
    private ShortcutDbAdapter.Shortcut mSelectedShortcut;


    @Override
    public void onShortcutTap(Uri uri) {
        // Build root Uri from shortcut Uri
        String rootUriString = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() != -1) {
            rootUriString += ":" + uri.getPort();
        }
        rootUriString += "/";// important to end with "/"
        Uri rootUri = Uri.parse(rootUriString);
        Bundle args = new Bundle();
        args.putParcelable(BrowserByNetwork.CURRENT_DIRECTORY, uri);
        args.putString(BrowserByNetwork.TITLE
                , uri.getLastPathSegment());
        args.putString(BrowserByNetwork.SHARE_NAME, uri.getLastPathSegment());

        Fragment f;
        if (uri.getScheme().equals("smb")) {
            f = Fragment.instantiate(getActivity(), BrowserBySmb.class.getCanonicalName(), args);
        } else if (uri.getScheme().equals("upnp")) {
            f = Fragment.instantiate(getActivity(), BrowserByUpnp.class.getCanonicalName(), args);
        } else {
            f = Fragment.instantiate(getActivity(), BrowserBySFTP.class.getCanonicalName(), args);
        }
        BrowserCategory category = (BrowserCategory) getActivity().getSupportFragmentManager().findFragmentById(R.id.category);
        category.startContent(f);
    }

    @Override
    public void onUnavailableShortcutTap(Uri uri) {
        if (mToast != null) {
            mToast.cancel(); // if we don't do that we have a very long toast in case user press on several shortcuts in row
        }
        mToast = Toast.makeText(getActivity(), getString(R.string.server_not_available_2, uri.getHost()), Toast.LENGTH_SHORT);
        mToast.show();
    }

    @Override
    public void onRefreshClickListener(View v, final Uri uri) {
        // Network shortcut

        mQuickAction = new QuickAction(v);

        ActionItem rescanAction = new ActionItem();
        rescanAction.setTitle(getString(R.string.network_reindex));
        rescanAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_refresh));
        rescanAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Rescan the contents of the folder
                NetworkScanner.scanVideos(getActivity(), uri);
                if(ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), uri.toString())<0){
                    //if not a shortcut, add as shortcut
                    ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(uri.getLastPathSegment(), uri.toString()));
                    loadIndexedShortcuts();
                }
                // Close the popup
                mQuickAction.dismiss();
            }
        });
        mQuickAction.addActionItem(rescanAction);
        mQuickAction.setAnimStyle(QuickAction.ANIM_REFLECT);
        mQuickAction.show();
        final View fv = v;
        mQuickAction.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                fv.invalidate();
                mQuickAction.onClose();
            }
        });
    }

    public NewRootFragment() {
        Log.d(TAG, "SambaDiscoveryFragment() constructor " + this);
        setRetainInstance(false);

    }
    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        // Adapter need to be instantiate ASAP because setOnShareOpenListener() may be called before onCreateView()
        mAdapter = getAdapter();
        mAdapter.setOnRefreshClickListener(this);
        mAdapter.setOnCreateContextMenuListener(this);
        mAdapter.setOnShortcutTapListener(this);

        //refresh when scan state changes to show or hide "refresh indexing" arrow
        NetworkScannerServiceVideo.addListener(this);
    }


    @Override
    public void onScannerStateChanged() {
        mAdapter.notifyDataSetChanged();
    }


    protected abstract RootFragmentAdapter getAdapter();


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        super.onCreateOptionsMenu(menu, inflater);
        menu.add(0, R.string.rescan_indexed_folders, Menu.NONE, R.string.rescan_indexed_folders)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0,  R.string.manually_create_share, Menu.NONE, R.string.manually_create_share)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if (item.getItemId() == R.string.rescan_indexed_folders) {
            rescanAvailableShortcuts();
            return true;
        }
        else if(item.getItemId() == R.string.manually_create_share){
            CreateShareDialog shareDialog = new CreateShareDialog();
            shareDialog.setRetainInstance(true); // the dialog is dismissed at screen rotation, that's better than a crash...
            shareDialog.show(getFragmentManager(), "CreateShareDialog");
            shareDialog.setOnShortcutCreatedListener(new CreateShareDialog.OnShortcutCreatedListener() {
                @Override
                public void onShortcutCreated(String path) {
                    loadIndexedShortcuts();
                }
            });
         return true;
        }
        return false;
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        menu.add(0, R.string.remove_from_indexed_folders, 0, R.string.remove_from_indexed_folders);
        menu.add(0, R.string.open_indexed_folder, 0, R.string.open_indexed_folder);
        menu.add(0, R.string.network_reindex, 0, R.string.network_reindex);
        mSelectedShortcut = ((WorkgroupShortcutAndServerAdapter.ShortcutViewHolder) v.getTag()).getShortcut();
    }
    protected abstract void rescanAvailableShortcuts();
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        switch (itemId) {
            case R.string.remove_from_indexed_folders:
                removeShortcut(mSelectedShortcut);
                return true;

            case R.string.open_indexed_folder:
                onShortcutTap(Uri.parse(mSelectedShortcut.getUri()));
                return true;

            case R.string.network_reindex:
                // Make as if the user had clicked on the refresh icon and validated the "re-scan content" item
                NetworkScanner.scanVideos(getActivity(), Uri.parse(mSelectedShortcut.getUri()));
                return true;
        }

        return super.onContextItemSelected(item);
    }
    private void removeShortcut(ShortcutDbAdapter.Shortcut shortcut) {
        // Remove the shortcut from the list
        ShortcutDbAdapter.VIDEO.deleteShortcut(getActivity(),shortcut.getUri().toString());
        loadIndexedShortcuts();
        String text = getString(R.string.indexed_folder_removed, shortcut.getName());
        Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
        // Send a delete request to MediaScanner
        NetworkScanner.removeVideos(getActivity(), shortcut.getUri());

        // Update the menu items
        getActivity().invalidateOptionsMenu();
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setHasOptionsMenu(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach");
        NetworkScannerServiceVideo.removeListener(this);

    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mLayoutManager!=null)
            outState.putParcelable("mLayoutManager", mLayoutManager.onSaveInstanceState()); // Save the layout manager state (that's cool we don't even know what it is doing inside!)
        mAdapter.onSaveInstanceState(outState);        // Save the adapter "saved instance" parameters
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View v = inflater.inflate(R.layout.samba_discovery_fragment, container, false);

        mDiscoveryList = (RecyclerView)v.findViewById(R.id.discovery_list);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mDiscoveryList.setLayoutManager(mLayoutManager);
        mDiscoveryList.setHasFixedSize(false); // there are separators
        mDiscoveryList.setAdapter(mAdapter);
        mDiscoveryList.setFocusable(false);
        if (savedInstanceState!=null) {
            mAdapter.onRestoreInstanceState(savedInstanceState); // Restore the adapter "saved instance" parameters
            mLayoutManager.onRestoreInstanceState(savedInstanceState.getParcelable("mLayoutManager")); // Restore the layout manager state
        }

        loadIndexedShortcuts();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        loadIndexedShortcuts();
    }

    protected abstract void loadIndexedShortcuts();

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

}
