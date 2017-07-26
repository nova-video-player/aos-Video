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

package com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.archos.filecorelibrary.samba.Share;
import com.archos.filecorelibrary.samba.Workgroup;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.WorkgroupShortcutAndServerAdapter;

import java.util.List;


public class SmbWorkgroupShortcutAndServerAdapter extends WorkgroupShortcutAndServerAdapter {
    private boolean mDisplayWorkgroupSeparator;
    protected static final int TYPE_WORKGROUP_SEPARATOR = 0;

    public SmbWorkgroupShortcutAndServerAdapter(Context ct) {
        super(ct);
    }

    public void updateWorkgroups(List<Workgroup> workgroups) {
        // No need to display workgroup if there is only one
        mDisplayWorkgroupSeparator = workgroups.size() > 1;
        mShares.clear();
        mAvailableShares.clear();

        for (Workgroup w : workgroups) {
            // Add the actual shares
            for (Share share : w.getShares()) {
                GenericShare s = new GenericShare(share.getDisplayName(), share.getWorkgroup(), share.toUri().toString());
                mShares.add(s);
                mAvailableShares.add(s.getName().toLowerCase());
                mAvailableShares.add(Uri.parse(share.getAddress()).getHost()); // retrieve ip address from smb://ip/
            }
        }

        resetData();
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("mDisplayWorkgroupSeparator", mDisplayWorkgroupSeparator);
    }

    /**
     * Restore the state that has been saved in a bundle
     * @param inState
     */
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Bundle inState) {
        mDisplayWorkgroupSeparator = inState.getBoolean("mDisplayWorkgroupSeparator");
        super.onRestoreInstanceState(inState);

    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        if(viewHolder.getItemViewType() == TYPE_WORKGROUP_SEPARATOR) {
            SeparatorViewHolder wsViewHolder = (SeparatorViewHolder)viewHolder;
            wsViewHolder.setProgressVisible(false);
            wsViewHolder.getNameTextView().setText((String)mData.get(position));
        }
        else
          super.onBindViewHolder(viewHolder, position);

    }
    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        if (viewType == TYPE_WORKGROUP_SEPARATOR) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_workgroup_separator, viewGroup, false);
            return new SeparatorViewHolder(v);
        }
        else return super.onCreateViewHolder(viewGroup,viewType);
    }
    public void resetData() {
        mData.clear();
        mTypes.clear();
        String lastWorgroup = null;
        mData.add(Integer.valueOf(R.string.network_shared_folders));

        mTypes.add(TYPE_TITLE);
        for (GenericShare s : mShares) {
            if (mDisplayWorkgroupSeparator && s.getWorkgroup()!=null && !s.getWorkgroup().equals(lastWorgroup)) {
                mData.add(s.getWorkgroup());
                mTypes.add(TYPE_WORKGROUP_SEPARATOR);
            }
            mData.add(s);
            mTypes.add(TYPE_SHARE);
            lastWorgroup = s.getWorkgroup();
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
