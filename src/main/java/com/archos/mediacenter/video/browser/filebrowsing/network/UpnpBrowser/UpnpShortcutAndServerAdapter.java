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

import android.content.Context;
import android.net.Uri;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.WorkgroupShortcutAndServerAdapter;

import java.util.List;


public class UpnpShortcutAndServerAdapter extends WorkgroupShortcutAndServerAdapter {
    private boolean mDisplayWorkgroupSeparator;

    public UpnpShortcutAndServerAdapter(Context ct) {
        super(ct);
    }

    public void updateShare(List<GenericShare> devices) {
        // No need to display workgroup if there is only one
        mShares.clear();
        mAvailableShares.clear();
        int i = 0;
        mShares.addAll(devices);
        for (GenericShare s : mShares) {
            if(s!=null)
                mAvailableShares.add(Uri.parse(s.getUri()).getHost().toLowerCase());
        }
        resetData();

    }
    public void resetData() {
        mData.clear();
        mTypes.clear();
        mData.add(Integer.valueOf(R.string.network_media_servers));
        mTypes.add(TYPE_TITLE);
        for (GenericShare s : mShares) {

            mData.add(s);
            mTypes.add(TYPE_SHARE);
        }
        mData.add(Integer.valueOf(R.string.indexed_folders));
        mTypes.add(TYPE_TITLE);
        if (mIndexedShortcuts != null&&mIndexedShortcuts.size()>0) {
            for (ShortcutDbAdapter.Shortcut uri : mIndexedShortcuts) {
                mData.add(uri);
                mTypes.add(TYPE_INDEXED_SHORTCUT);
            }
        }else{
            mData.add(mContext.getString(R.string.indexed_folders_list_empty, mContext.getString(R.string.add_to_indexed_folders)));
            mTypes.add(TYPE_TEXT);
        }
    }
}
