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

package com.archos.mediacenter.video.utils.credentialsmanager;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.video.R;

import java.util.List;

/**
 * Created by alexandre on 14/04/15.
 */
public class CredentialsManagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<NetworkCredentialsDatabase.Credential> mCredentialList;
    private final Context mContext;
    private final OnItemClickListener mOnItemClickListener;

    public void setCredentials(List<NetworkCredentialsDatabase.Credential> credentials) {
        mCredentialList = credentials;
    }

    public interface OnItemClickListener{
        public void onItemClick(NetworkCredentialsDatabase.Credential credential);
    }
    public interface OnItemLongClickListener{
        public boolean onItemLongClick(NetworkCredentialsDatabase.Credential credential);
    }
    public class CredentialViewHolder extends RecyclerView.ViewHolder {

        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final View mRoot;

        public CredentialViewHolder(View v) {
            super(v);
            mRoot = v;
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mOnItemClickListener.onItemClick(mCredentialList.get(getAdapterPosition()));
                }
            });
            mMainTv = (TextView) v.findViewById(R.id.main);
            mSecondaryTv = (TextView) v.findViewById(R.id.secondary);
        }
        public View getRoot() {
            return mRoot;
        }
        public TextView getMainTextView() {
            return mMainTv;
        }
        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }

    }
    public CredentialsManagerAdapter(List<NetworkCredentialsDatabase.Credential> credentialList, Context context, OnItemClickListener onItemClickListener){
        mCredentialList = credentialList;
        mContext = context;
        mOnItemClickListener = onItemClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_credential, viewGroup, false);
        return new CredentialViewHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        ((CredentialViewHolder)viewHolder).getMainTextView().setText(mCredentialList.get(i).getUriString());
        ((CredentialViewHolder)viewHolder).getSecondaryTextView().setText(mCredentialList.get(i).getUsername());
    }

    @Override
    public int getItemCount() {
        return mCredentialList.size();
    }
}
