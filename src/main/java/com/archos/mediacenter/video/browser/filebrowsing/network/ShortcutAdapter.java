// Copyright 2023 Courville Software
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
import android.net.Uri;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.util.List;

public class ShortcutAdapter extends RootFragmentAdapter {
    List<ShortcutDb.Shortcut> mShorcuts;
    protected static final int TYPE_SHORTCUT = 1;
    protected static final int TYPE_BROWSE = 5;
    private View.OnClickListener mOnBrowseClickListener;


    private OnShortcutAddListener mOnShortcutAddListener;
    public interface OnShortcutAddListener{
        public void onShortcutAdd(View v,Uri uri, String name);
    }
    public ShortcutAdapter(Context ct) {
        super(ct);
    }


    public void updateShortcuts(List<ShortcutDb.Shortcut> shorcuts) {
        mShorcuts = shorcuts;

    }
    public void setOnBrowseClickListener(View.OnClickListener listener){
        mOnBrowseClickListener = listener;
    }
    public void setOnShortcutAddListener(OnShortcutAddListener listener){
        mOnShortcutAddListener = listener;
    }
    public void resetData() {
        mData.clear();
        mTypes.clear();
        mData.add(Integer.valueOf(R.string.network_shortcuts));
        mTypes.add(TYPE_TITLE);
        if(mShorcuts!=null&&!mShorcuts.isEmpty())
            for (ShortcutDb.Shortcut s : mShorcuts) {

                mData.add(s);
                mTypes.add(TYPE_SHORTCUT);
            }
        else{
            mData.add(mContext.getString(R.string.shortcut_list_empty,mContext.getString(R.string.add_ssh_shortcut)));
            mTypes.add(TYPE_TEXT);
        }
        mData.add(Integer.valueOf(R.string.indexed_folders));
        mTypes.add(TYPE_TITLE);
        if (mIndexedShortcuts != null&&mIndexedShortcuts.size()>0) {
            for (ShortcutDbAdapter.Shortcut uri : mIndexedShortcuts) {
                mData.add(uri);
                mTypes.add(TYPE_INDEXED_SHORTCUT);
            }
        }else{
            mData.add(mContext.getString(R.string.indexed_folders_list_empty_network));
            mTypes.add(TYPE_TEXT);
        }
        mTypes.add(TYPE_BROWSE);
        mData.add(R.string.browse_net_server);
    }

    public class BrowseViewHolder extends RecyclerView.ViewHolder {
        private final Button mText;

        public BrowseViewHolder(View v) {
            super(v);
            mText = (Button)v.findViewById(R.id.button);
            mText.setOnClickListener(mOnBrowseClickListener);
        }
        public Button getNameTextView() {
            return mText;
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        if(viewType == TYPE_SHORTCUT){
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.browser_indexed_item, viewGroup, false);
            return new ShortcutViewHolder(v);
        }
        else if (viewType == TYPE_BROWSE) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.browser_network_share_button, viewGroup, false);
            return new BrowseViewHolder(v);
        }
        else return super.onCreateViewHolder(viewGroup,viewType);
    }

    public class ShortcutViewHolder extends RecyclerView.ViewHolder {
        private final View mRoot;
        private final ImageView mIcon;
        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final ImageView mRefresh;
        private Uri mUri;
        private boolean mAvailable;
        private String mName;

        public ShortcutViewHolder(View v) {
            super(v);
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                        mOnShortcutTapListener.onShortcutTap(mUri);

                }
            });
            v.setOnCreateContextMenuListener(mOnCreateContextMenuListener);
            mRoot = v;
            mRoot.setTag(this);
            mIcon = (ImageView) v.findViewById(R.id.icon);
            mRefresh = (ImageView) v.findViewById(R.id.refresh);
            mRefresh.setImageResource(R.drawable.label_plus);
            mRefresh.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mOnShortcutAddListener.onShortcutAdd(mRefresh,mUri, mName);
                }
            });
            mMainTv = (TextView) v.findViewById(R.id.name);
            mSecondaryTv = (TextView) v.findViewById(R.id.second);

        }
        public View getRoot() {
            return mRoot;
        }
        public ImageView getIcon() {
            return mIcon;
        }
        public void setUri(Uri uri) {
            mUri = uri;
            mIcon.setImageResource(VideoUtils.getShortcutImage(mUri));
        }
        public void setName(String name) {
            mName  = name;
            mMainTv.setText(name);
        }

        public Uri getUri() {
            return mUri;
        }


        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }

        public String getName() {
            return mName;
        }
    }
    
    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        if (viewHolder.getItemViewType() == TYPE_SHORTCUT) {
            ShortcutViewHolder sViewHolder = (ShortcutViewHolder) viewHolder;
            ShortcutDb.Shortcut shortcut = (ShortcutDb.Shortcut) mData.get(position);
            sViewHolder.setName(shortcut.name);
            sViewHolder.getSecondaryTextView().setText(shortcut.uri);
            sViewHolder.setUri(Uri.parse(shortcut.uri));
        }
        else if (viewHolder.getItemViewType() == TYPE_BROWSE) {
            ((BrowseViewHolder)viewHolder).getNameTextView().setText(R.string.browse_net_server);
        }
        else
            super.onBindViewHolder(viewHolder,position);
    }
}
