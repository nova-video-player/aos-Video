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

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public abstract class WorkgroupShortcutAndServerAdapter extends RootFragmentAdapter {

    private static final String TAG = "WorkgroupAndServerAdapter";

    protected static final int TYPE_SHARE = 1;
    protected ArrayList<GenericShare> mShares;
    private OnShareOpenListener mOnShareOpenListener;

    public List<String> getShares() {
        return mAvailableShares;
    }



    public interface OnShareOpenListener {
        public void onShareOpen(GenericShare share);
    }


    public void setOnShareOpenListener(OnShareOpenListener listener) {
        mOnShareOpenListener = listener;
    }
    public void setIsLoadingWorkgroups(boolean isLoadingWorkground){
        mIsLoadingShares = isLoadingWorkground;
        notifyItemChanged(0);
    }
    public static class GenericShare implements Serializable {

        private final String mshareName;
        private final String mWorkgroup;
        private final String mUri;
        public String getName() {
            return mshareName;
        }

        public String getUri() {
            return mUri;
        }

        public String getWorkgroup() {
            return mWorkgroup;
        }

        public GenericShare(String shareName, String workgroup, String uri){
            mshareName = shareName;
            mWorkgroup = workgroup;
            mUri = uri;
        }
    }
    public class ShareViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final View mRoot;
        private GenericShare mGenericShare;

        public ShareViewHolder(View v) {
            super(v);
            mRoot = v;
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                   mOnShareOpenListener.onShareOpen(mGenericShare);
                }
            });
            mIcon = (ImageView) v.findViewById(R.id.icon);
            mMainTv = (TextView) v.findViewById(R.id.name);
            mSecondaryTv = (TextView) v.findViewById(R.id.info);
        }
        public void setGenericShare(GenericShare share){
            mGenericShare = share;
        }
        public View getRoot(){
            return mRoot;
        }
        public ImageView getIcon() {
            return mIcon;
        }
        public TextView getMainTextView() {
            return mMainTv;
        }
        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }
    }


    public WorkgroupShortcutAndServerAdapter(Context ct) {
        super(ct);
        setHasStableIds(false);
        mShares = new ArrayList<GenericShare>();
        mAvailableShares = new ArrayList<String>();
    }


    /**
     * Store the list (and possibly other things) in a bundle
     * @param outState
     */
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mShares", mShares);
        super.onSaveInstanceState(outState);
    }

    /**
     * Restore the state that has been saved in a bundle
     * @param inState
     */
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Bundle inState) {
        mShares = (ArrayList<GenericShare>) inState.getSerializable("mShares");
        for (GenericShare s : mShares) {
            if(s!=null)
                mAvailableShares.add(s.getName());
        }
        super.onRestoreInstanceState(inState);
        resetData();

    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view.
        if (viewType == TYPE_SHARE) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.browser_smb_worgroup_item, viewGroup, false);
            return new ShareViewHolder(v);
        }
        else return super.onCreateViewHolder(viewGroup, viewType);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        if (viewHolder.getItemViewType() == TYPE_SHARE) {
            final GenericShare share = (GenericShare) mData.get(position); // no separator offset to handle here! see "trick" comment in the source code above for explanation
            ShareViewHolder shareViewHolder = (ShareViewHolder)viewHolder;
            shareViewHolder.getIcon().setImageResource(R.drawable.filetype_video_server);
            shareViewHolder.getMainTextView().setText(share.getName());
            shareViewHolder.getSecondaryTextView().setVisibility(View.GONE);
            shareViewHolder.setGenericShare(share);
        }
        else
            super.onBindViewHolder(viewHolder, position);
    }

}
