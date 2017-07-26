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

import android.net.Uri;
import android.widget.Toast;

import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.BrowserByNetwork;
import com.archos.mediaprovider.NetworkScanner;

/**
 * Created by alexandre on 29/10/15.
 */
public class BrowserByUpnp extends BrowserByNetwork {
    @Override
    protected boolean isIndexable(Uri folder) {
        // allows only indexing for shares as in upnp://[user:pass@]server/share/
        String path = folder != null ? folder.toString() : null;
        if (path == null || !path.startsWith("upnp://"))
            return false;
        // valid paths contain at least 4x'/' e.g. "upnp://server/share/"
        int len = path.length();
        int slashCount = 0;
        for (int i = 0; i < len; i++) {
            if (path.charAt(i) == '/') {
                slashCount++;
            }
        }
        return slashCount >= 4;
    }
    @Override
    protected void createShortcut(String shortcutPath, String shortcutName) {
        String friendlyUri="upnp://";
        String friendlyName = UpnpServiceManager.getSingleton(getActivity()).getDeviceFriendlyName(Uri.parse(shortcutPath).getHost());
        if(friendlyName!=null){
            friendlyUri+=friendlyName;
        }
        else{
            friendlyUri+=Uri.parse(shortcutPath).getHost();
        }
        friendlyUri+="/"+shortcutName;
        boolean result = ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(shortcutName, shortcutPath,friendlyUri));

        if (result) {
            Toast.makeText(getActivity(), getString(R.string.indexed_folder_added, shortcutName), Toast.LENGTH_SHORT).show();
            // Send a scan request to MediaScanner
            NetworkScanner.scanVideos(getActivity(), shortcutPath);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        // Update the menu items
        getActivity().invalidateOptionsMenu();
    }

}
