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

import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediacenter.video.leanback.network.NetworkListingFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by vapillon on 10/06/15.
 */
public class UpnpListingFragment extends NetworkListingFragment {

    private static final Logger log = LoggerFactory.getLogger(UpnpListingFragment.class);

    @Override
    protected  ListingFragment instantiateNewFragment() {
        return new UpnpListingFragment();
    }

    /**
     * For UPnP we display a warning dialog before indexing a folder
     */
    protected String getTitleForAskForIndexing() {
        return getActivity().getString(R.string.upnp_indexing_warning_title);
    }

    protected Integer getMessageForAskForIndexing() {
        return R.string.upnp_indexing_warning_message;
    }

    protected Integer getPositiveForAskForIndexing() {
        return R.string.add_to_indexed_folders;
    }

    protected Integer getNegativeForAskForIndexing() {
        return R.string.add_ssh_shortcut;
    }

    protected String getShortcutName() {  //to avoid name like "33" in upnp indexed folders
        return getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment();
    }

    protected String getFriendlyUri() {
        String shortcutName = getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment(); //to avoid name like "33" in upnp
        String friendlyUri = "upnp://";
        String friendlyName = UpnpServiceManager.getSingleton(getActivity()).getDeviceFriendlyName(mUri.getHost());
        if(friendlyName!=null) friendlyUri += friendlyName;
        else friendlyUri += mUri.getHost();
        friendlyUri += "/" + shortcutName;
        log.debug("getFriendlyUri=" + friendlyUri);
        return friendlyUri;
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
