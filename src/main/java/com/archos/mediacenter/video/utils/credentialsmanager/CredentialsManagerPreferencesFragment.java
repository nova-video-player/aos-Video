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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.video.R;

import java.util.List;

public class CredentialsManagerPreferencesFragment extends Fragment implements CredentialsManagerAdapter.OnItemClickListener {

    private RecyclerView mList;
    private View mEmptyView;
    private TextView mEmptyTextView;
    private LinearLayoutManager mLayoutManager;
    private List<NetworkCredentialsDatabase.Credential> mCredentials;
    private CredentialsManagerAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }
    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(R.layout.credentials_manager_fragment, container, false);
        mEmptyView = v.findViewById(R.id.empty_view);
        mEmptyTextView = (TextView)v.findViewById(R.id.empty_textview);
        mList = (RecyclerView)v.findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mList.setLayoutManager(mLayoutManager);
        mList.setHasFixedSize(false);
        mList.setItemAnimator(new DefaultItemAnimator());
        mCredentials = NetworkCredentialsDatabase.getInstance().getAllPersistentCredentials();
        mAdapter = new CredentialsManagerAdapter(mCredentials,getActivity(), this);
        mList.setAdapter(mAdapter);
        refreshCredentialsList();
        return v;
    }
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }



    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void refreshCredentialsList(){
        mCredentials = NetworkCredentialsDatabase.getInstance().getAllPersistentCredentials();
        if(mCredentials.size()>0) {
            mList.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            mAdapter.setCredentials(mCredentials);
            mAdapter.notifyDataSetChanged();
        }
        else{
            mEmptyView.setVisibility(View.VISIBLE);
            mList.setVisibility(View.GONE);

        }


    }

    @Override
    public void onItemClick(NetworkCredentialsDatabase.Credential credential) {
        CredentialsEditorDialog dialog = new CredentialsEditorDialog();
        Bundle args = new Bundle();
        args.putSerializable(CredentialsEditorDialog.CREDENTIAL,credential);
        dialog.setArguments(args);
        dialog.show(getActivity().getSupportFragmentManager(), CredentialsEditorDialog.class.getCanonicalName());
        dialog.setOnModifyListener(new CredentialsEditorDialog.OnModifyListener() {
            @Override
            public void onModify() {
                refreshCredentialsList();
            }
        });
    }
}
