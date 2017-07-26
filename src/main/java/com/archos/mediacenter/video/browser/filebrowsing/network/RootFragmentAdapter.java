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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.NetworkScannerServiceVideo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public abstract class RootFragmentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "WorkgroupAndServerAdapter";

    protected static final int TYPE_INDEXED_SHORTCUT = 2;
    protected static final int TYPE_TITLE = 3; //separator for shares and shortcuts
    protected static final int TYPE_TEXT = 4;
    protected final Context mContext;
    private final ArrayList<String> mForcedEnabledShortcut;
    protected View.OnCreateContextMenuListener mOnCreateContextMenuListener;
    protected ArrayList<Object> mData;
    protected ArrayList<Integer> mTypes;

    private Cursor mShortcutsCursor;
    private int uriColumnIndex;
    protected ArrayList<ShortcutDbAdapter.Shortcut> mIndexedShortcuts;
    //keep a list of share names, faster when binding view
    protected List<String> mAvailableShares;



    protected boolean mIsLoadingShares;
    private int nameColumnIndex;
    private int friendlyUriColumnIndex;

    public void setOnCreateContextMenuListener(View.OnCreateContextMenuListener onCreateContextMenuListener) {
        mOnCreateContextMenuListener = onCreateContextMenuListener;
    }

    public List<String>  getAvailableShares() {
        return mAvailableShares;
    }

    public List<String> getForcedEnabledShortcuts() {
        return mForcedEnabledShortcut;
    }


    public interface OnRefreshClickListener{
        public void onRefreshClickListener(View v, Uri uri);
    }

    public interface OnShortcutTapListener {
        public void onShortcutTap(Uri uri);
        public void onUnavailableShortcutTap(Uri uri);
    }
    protected OnShortcutTapListener mOnShortcutTapListener;
    protected OnRefreshClickListener mOnRefreshClickListener;

    public void setOnShortcutTapListener(OnShortcutTapListener listener) {
        mOnShortcutTapListener = listener;
    }

    public void setOnRefreshClickListener(OnRefreshClickListener listener){
        mOnRefreshClickListener = listener;
    }

    public abstract void resetData();

    public List<ShortcutDbAdapter.Shortcut> getShortcuts(){
        return mIndexedShortcuts;
    }

    public void updateIndexedShortcuts(Cursor cursor) {
        mShortcutsCursor = cursor;
        uriColumnIndex = mShortcutsCursor.getColumnIndex(ShortcutDbAdapter.KEY_PATH);
        nameColumnIndex = mShortcutsCursor.getColumnIndex(ShortcutDbAdapter.KEY_NAME);
        friendlyUriColumnIndex = mShortcutsCursor.getColumnIndex(ShortcutDbAdapter.KEY_FRIENDLY_URI);
        mIndexedShortcuts = new ArrayList<ShortcutDbAdapter.Shortcut>();
        if(mShortcutsCursor.getCount()>0){
            mShortcutsCursor.moveToFirst();
            do{
                String name = mShortcutsCursor.getString(nameColumnIndex);
                Uri uri = Uri.parse(mShortcutsCursor.getString(uriColumnIndex));
                String friendlyUri = mShortcutsCursor.getString(friendlyUriColumnIndex);
                if(name==null||name.isEmpty()) {
                    name = uri.getLastPathSegment();
                }
                mIndexedShortcuts.add(new ShortcutDbAdapter.Shortcut(name, uri.toString(),friendlyUri));
            }while(mShortcutsCursor.moveToNext());
        }
        resetData();
    }

    public class ShortcutViewHolder extends RecyclerView.ViewHolder {
        private final View mRoot;
        private final ImageView mIcon;
        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final ImageView mRefresh;
        private ShortcutDbAdapter.Shortcut mShortcut;
        private boolean mAvailable;

        public ShortcutViewHolder(View v) {
            super(v);
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mAvailable) {
                        mOnShortcutTapListener.onShortcutTap(Uri.parse(mShortcut.getUri()));
                    } else {
                        mOnShortcutTapListener.onUnavailableShortcutTap(Uri.parse(mShortcut.getUri()));
                    }
                }
            });
            v.setOnCreateContextMenuListener(mOnCreateContextMenuListener);
            mRoot = v;
            mRoot.setTag(this);
            mIcon = (ImageView) v.findViewById(R.id.icon);
            mRefresh = (ImageView) v.findViewById(R.id.refresh);
            mRefresh.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    mOnRefreshClickListener.onRefreshClickListener(view, Uri.parse(mShortcut.getUri()));
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
        public TextView getMainTextView() {
            return mMainTv;
        }
        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }
        public void setShortcut(ShortcutDbAdapter.Shortcut shortcut) {
            mShortcut = shortcut;
        }
        public void setAvailable(boolean available) {
            mRefresh.setClickable(available);
            mAvailable = available;
            // Do not change the root alpha because it is also modified by the RecyclerView
            final float alpha = available ? 1.0f : 0.3f;
            mIcon.setAlpha(alpha);
            mMainTv.setAlpha(alpha);
            mSecondaryTv.setAlpha(alpha);
        }

        public Uri getUri() {
            return Uri.parse(mShortcut.getUri());
        }

        public void setRefreshable(boolean b) {
            mRefresh.setVisibility(b?View.VISIBLE:View.INVISIBLE);
        }

        public ShortcutDbAdapter.Shortcut getShortcut() {
            return  mShortcut;
        }
    }

    public static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        private final TextView mName;
        private final View mProgress;

        public SeparatorViewHolder(View v) {
            super(v);
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Element " + getAdapterPosition() + " clicked.");
                }
            });
            mName = (TextView) v.findViewById(R.id.name);
            mProgress = v.findViewById(R.id.progressBar);
        }
        public TextView getNameTextView() {
            return mName;
        }

        public void setProgressVisible(boolean b) {
            mProgress.setVisibility(b?View.VISIBLE:View.GONE);
        }
    }

    public static class TextViewHolder extends RecyclerView.ViewHolder {
        private final TextView mText;

        public TextViewHolder(View v) {
            super(v);

            mText = (TextView)v;
        }
        public TextView getNameTextView() {
            return mText;
        }


    }
    public RootFragmentAdapter(Context ct) {
        mContext = ct;
        setHasStableIds(false);
        mData = new ArrayList<Object>();
        mTypes = new ArrayList<>();
        mForcedEnabledShortcut =  new ArrayList<>();
    }

    public void forceShortcutDisplay(String uriString){
        mForcedEnabledShortcut.add(uriString);
    }
    /**
     * Store the list (and possibly other things) in a bundle
     * @param outState
     */
    public void onSaveInstanceState(Bundle outState) {
        if(mAvailableShares!=null)
            outState.putSerializable("mAvailableShares", (Serializable) mAvailableShares);
        outState.putSerializable("mShortcuts", mIndexedShortcuts);
    }

    /**
     * Restore the state that has been saved in a bundle
     * @param inState
     */
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Bundle inState) {
        mAvailableShares = (List<String>) inState.getSerializable("mAvailableShares");
        mIndexedShortcuts = (ArrayList<ShortcutDbAdapter.Shortcut>) inState.getSerializable("mIndexedShortcuts");
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view.

        if (viewType == TYPE_TITLE) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_workgroup_separator, viewGroup, false);
            return new SeparatorViewHolder(v);
        }
        else if(viewType == TYPE_INDEXED_SHORTCUT){
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.browser_smb_shortcut_item, viewGroup, false);
            return new ShortcutViewHolder(v);
        }
        else if(viewType == TYPE_TEXT){
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.browser_item_list_long_text, viewGroup, false);
            return new TextViewHolder(v);
        }
        else {
            throw new IllegalArgumentException("invalid viewType "+viewType);   
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
       if(viewHolder.getItemViewType() == TYPE_INDEXED_SHORTCUT){
            ShortcutViewHolder sViewHolder = (ShortcutViewHolder)viewHolder;
            ShortcutDbAdapter.Shortcut shortcut = (ShortcutDbAdapter.Shortcut) mData.get(position);
            sViewHolder.getMainTextView().setText(shortcut.getName());
            sViewHolder.getSecondaryTextView().setText(shortcut.getFriendlyUri());
            sViewHolder.setShortcut(shortcut);

            // Set shortcut availability

            boolean available = (mAvailableShares==null||mAvailableShares.contains(Uri.parse(shortcut.getUri()).getHost().toLowerCase())||mForcedEnabledShortcut.contains(shortcut.getUri()));
            sViewHolder.setAvailable(available);
            sViewHolder.setRefreshable(available && !NetworkScannerServiceVideo.isScannerAlive());
        }
        else if(viewHolder.getItemViewType() == TYPE_TITLE){
            SeparatorViewHolder wsViewHolder = (SeparatorViewHolder)viewHolder;
            wsViewHolder.getNameTextView().setText((Integer) mData.get(position));
            wsViewHolder.setProgressVisible(position == 0 && mIsLoadingShares);
        }
        else if(viewHolder.getItemViewType() == TYPE_TEXT){
            TextViewHolder wsViewHolder = (TextViewHolder)viewHolder;
            Context ct  = wsViewHolder.getNameTextView().getContext();
            wsViewHolder.getNameTextView().setText((String)mData.get(position));
        }
        else {
            throw new IllegalArgumentException("invalid viewType "+viewHolder.getItemViewType());   
        }
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    @Override
    public int getItemViewType(int position) {
        if(mTypes.size()>position){
            return mTypes.get(position);
        }
        return -1;
    }
}
