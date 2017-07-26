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

package com.archos.mediacenter.video.browser.filebrowsing.network.UpnpBrowser;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.browser.filebrowsing.network.UpnpSmbCommonRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.WorkgroupShortcutAndServerAdapter;
import com.archos.mediaprovider.NetworkScanner;

import org.fourthline.cling.model.meta.Device;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexandre on 28/05/15.
 */
public class UpnpRootFragment extends UpnpSmbCommonRootFragment implements UpnpServiceManager.Listener {
    private static final String TAG = "SmbRootFragment";

    public UpnpRootFragment(){
        super();
    }
    @Override
    public void onViewCreated (View v, Bundle saved){

        // First initialization, start the discovery (if there is connectivity)
        if (ArchosUtils.isNetworkConnected(getActivity())) {
            startDiscovery();
        }
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Remember if the discovery is still running in order to restart it when restoring the fragment
    }
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

// Start UPnP
        UpnpServiceManager
                .startServiceIfNeeded(activity);
        Log.d(TAG, "onAttach this=" + this);
        Log.d(TAG, "onAttach mSambaDiscovery=");

    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        UpnpServiceManager
                .startServiceIfNeeded(getActivity())
                .addListener(this);
        if(UpnpServiceManager
                .startServiceIfNeeded(getActivity()).getDevices()!=null) {
            onDeviceListUpdate(new ArrayList<Device>(UpnpServiceManager
                    .startServiceIfNeeded(getActivity()).getDevices()));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        UpnpServiceManager
                .startServiceIfNeeded(getActivity()).removeListener(this);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView");
    }
    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach");
    }
    /**
     * Start or restart the discovery.
     * Not needed at initialization since the fragment will start it by itself (if there is connectivity)
     */
    public void startDiscovery() {
        UpnpServiceManager
                .startServiceIfNeeded(getActivity()).start();
        onDiscoveryStart();
    }


    public void onDiscoveryStart() {
        ((WorkgroupShortcutAndServerAdapter)mAdapter).setIsLoadingWorkgroups(true);
    }
    public WorkgroupShortcutAndServerAdapter getAdapter(){
        return new UpnpShortcutAndServerAdapter(getActivity());
    }
    @Override
    public void onDeviceListUpdate(List<Device> devices) {
        List<WorkgroupShortcutAndServerAdapter.GenericShare> shares = new ArrayList<>();
        for(Device device : devices){
            shares.add(new WorkgroupShortcutAndServerAdapter.GenericShare(UpnpServiceManager
                    .startServiceIfNeeded(getActivity()).getDeviceFriendlyName(device), "", UpnpServiceManager
                    .startServiceIfNeeded(getActivity()).getDeviceUri(device).toString()));
        }
        ((UpnpShortcutAndServerAdapter)mAdapter).updateShare(shares);
        mAdapter.notifyDataSetChanged();
    }
    @Override
    protected void loadIndexedShortcuts() {
        Cursor cursor = ShortcutDbAdapter.VIDEO.getAllShortcuts(getActivity(), ShortcutDbAdapter.KEY_PATH+" LIKE ?",new String[]{"upnp%"});
        mAdapter.updateIndexedShortcuts(cursor);
        if (cursor != null) {
            cursor.close();
        }
        mAdapter.notifyDataSetChanged();
    }
    @Override
    protected void rescanAvailableShortcuts() {
        Cursor cursor = ShortcutDbAdapter.VIDEO.getAllShortcuts(getActivity(), ShortcutDbAdapter.KEY_PATH+" LIKE ?",new String[]{"upnp%"});
        if (cursor == null) return;
        int uriIndex = cursor.getColumnIndex(ShortcutDbAdapter.KEY_PATH);
        if(cursor.getCount()>0) {
            cursor.moveToFirst();
            do {
                String path = cursor.getString(uriIndex);
                if (((WorkgroupShortcutAndServerAdapter) mAdapter).getShares().contains(Uri.parse(path).getHost().toLowerCase())) {
                    NetworkScanner.scanVideos(getActivity(), path);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
    }



}
