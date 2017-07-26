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

import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser.BrowserBySmb;
import com.archos.mediacenter.video.browser.filebrowsing.network.UpnpBrowser.BrowserByUpnp;

/**
 * Created by alexandre on 08/06/15.
 */
public abstract class UpnpSmbCommonRootFragment extends NewRootFragment implements WorkgroupShortcutAndServerAdapter.OnShareOpenListener{
    public UpnpSmbCommonRootFragment(){
        super();

    }
    @Override
    public void onAttach(Activity activity){
        super.onAttach(activity);
        ((WorkgroupShortcutAndServerAdapter)mAdapter).setOnShareOpenListener(this);
    }
    @Override
    public void onShareOpen(WorkgroupShortcutAndServerAdapter.GenericShare share) {
        final Uri uri = Uri.parse(share.getUri());
        Bundle args = new Bundle();
        args.putParcelable(BrowserByNetwork.CURRENT_DIRECTORY, uri);
        args.putString(BrowserByNetwork.TITLE, share.getName());
        args.putString(BrowserByNetwork.SHARE_NAME, uri.getLastPathSegment());
        Fragment f;
        if (uri.getScheme().equals("smb")) {
            f = Fragment.instantiate(getActivity(), BrowserBySmb.class.getCanonicalName(), args);
        } else {
            f = Fragment.instantiate(getActivity(), BrowserByUpnp.class.getCanonicalName(), args);
        }
        BrowserCategory category = (BrowserCategory) getActivity().getSupportFragmentManager().findFragmentById(R.id.category);
        category.startContent(f);
    }

}
