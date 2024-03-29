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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by alexandre on 29/10/15.
 */
public class BrowserByUpnp extends BrowserByNetwork {

    private static final Logger log = LoggerFactory.getLogger(BrowserByUpnp.class);

    @Override
    protected void createShortcut(String shortcutPath, String shortcutName) {
        mShortcutPath = shortcutPath;
        mShortcutName = shortcutName;
        String friendlyUri = getFriendlyUri();
        log.debug("createShortcut: adding shortcutName=" + shortcutName + ", shortcutPath=" + shortcutPath + ", friendlyUri=" + friendlyUri);
        boolean result = ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(shortcutName, shortcutPath, friendlyUri));
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

    protected String getFriendlyUri() {
        String friendlyUri = "upnp://";
        String friendlyName = UpnpServiceManager.getSingleton(getActivity()).getDeviceFriendlyName(Uri.parse(mShortcutPath).getHost());
        if(friendlyName != null) friendlyUri += friendlyName;
        else friendlyUri += Uri.parse(mShortcutPath).getHost();
        friendlyUri += "/" + mShortcutName;
        log.debug("getFriendlyUri: mShortcutPath=" + mShortcutPath + ", mShortcutName=" + mShortcutName + " -> friendlyName=" + friendlyName + ", friendlyUri=" + friendlyUri);
        return friendlyUri;
    }

}
