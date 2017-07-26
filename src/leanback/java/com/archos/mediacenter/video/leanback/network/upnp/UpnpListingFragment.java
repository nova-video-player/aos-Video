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

package com.archos.mediacenter.video.leanback.network.upnp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediacenter.video.leanback.network.NetworkListingFragment;
import com.archos.mediacenter.video.leanback.network.NetworkRootFragment;
import com.archos.mediaprovider.NetworkScanner;

/**
 * Created by vapillon on 10/06/15.
 */
public class UpnpListingFragment extends NetworkListingFragment {

    private static final String TAG = "UpnpListingFragment";

    @Override
    protected  ListingFragment instantiateNewFragment() {
        return new UpnpListingFragment();
    }


    /**
     * For UPnP we display a warning dialog before indexing a folder
     */
    @Override
    protected void createShortcut() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.upnp_indexing_warning_title)
                .setMessage(R.string.upnp_indexing_warning_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add_to_indexed_folders, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // really create the shortcut
                        String shortcutPath = mUri.toString();
                        String shortcutName = getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment(); //to avoid name like "33" in upnp
                        String friendlyUri="upnp://";
                        String friendlyName = UpnpServiceManager.getSingleton(getActivity()).getDeviceFriendlyName(mUri.getHost());
                        if(friendlyName!=null){
                            friendlyUri+=friendlyName;
                        }
                        else{
                            friendlyUri+=mUri.getHost();
                        }
                        friendlyUri+="/"+shortcutName;
                        boolean result = ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(shortcutName, shortcutPath,friendlyUri));

                        if (result) {
                            Toast.makeText(getActivity(), getString(R.string.indexed_folder_added, shortcutName), Toast.LENGTH_SHORT).show();
                            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
                            // Send a scan request to MediaScanner
                            NetworkScanner.scanVideos(getActivity(), shortcutPath);
                        }
                        else {
                            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
                        }
                        updateShortcutState();
                    }
                })
                .create()
                .show();

    }

    /**
     * We do not allow to create a shortcut at the top level of a UPnP server
     * @return
     */
    @Override
    protected boolean canBeIndexed() {
        if (super.canBeIndexed() == false) {
            return false;
        }

        // there is only one path segment at root level ("0"). If there are more it is OK.
        return mUri.getPathSegments().size() > 1;
    }
}
