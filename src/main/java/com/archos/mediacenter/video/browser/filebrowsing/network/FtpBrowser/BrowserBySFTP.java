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


package com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.core.widget.TextViewCompat;
import androidx.loader.app.LoaderManager;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.filebrowsing.network.AdapterByNetwork;
import com.archos.mediacenter.video.browser.filebrowsing.network.BrowserByNetwork;

import java.util.ArrayList;
import java.util.List;

public class BrowserBySFTP extends BrowserByNetwork implements ListingEngine.Listener,LoaderManager.LoaderCallbacks<Cursor> {
    public static final long LONG_CONNECTION_DELAY = 8000; //delay before proposing to reset connection

    private static final int LONG_CONNECTION = 9;
    private static final String SSH_SHORTCUT_HELP_OVERLAY_KEY = "ssh_shortcut_help_overlay";
    // was protected before...
    public static final String CURRENT_DIRECTORY = "currentDirectory";

    protected final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case LONG_CONNECTION:
                    // Hide content, show message
                    mHandler.removeMessages(LONG_CONNECTION);
                    if(mArchosGridView!=null && mRootView!=null){
                        mArchosGridView.setVisibility(View.GONE);
                        View emptyView = mRootView.findViewById(R.id.empty_view);
                        if (emptyView instanceof ViewStub) {
                            final ViewStub stub = (ViewStub) emptyView;
                            emptyView = stub.inflate();
                        }
                        if (emptyView != null) {
                            emptyView.setVisibility(View.VISIBLE);
                            // Update the text of the empty view
                            TextView emptyViewText = (TextView)emptyView.findViewById(R.id.empty_view_text);
                            TextViewCompat.setTextAppearance(emptyViewText, android.R.style.TextAppearance_Medium);
                            emptyViewText.setText(R.string.connection_abnormally_long);
                            // Check if a button is needed in the empty view
                            Button emptyViewButton = (Button)emptyView.findViewById(R.id.empty_view_button);
                            if (emptyViewButton != null) {
                                emptyViewButton.setVisibility(View.VISIBLE);
                                emptyViewButton.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        SFTPSession.getInstance().removeSession(mCurrentDirectory);
                                        Session.getInstance().removeFTPClient(mCurrentDirectory);
                                        if(mListingEngine!=null)
                                            mListingEngine.abort();
                                        listFiles(false);
                                    }
                                });
                            }
                            emptyViewButton.setText(R.string.connection_reset);
                            View loading = mRootView.findViewById(R.id.loading);
                            if (loading != null){
                                loading.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                    break;
            }
        }
    };

    private SearchView mSearchView;
    private List<ShortcutDb.Shortcut> mShortcuts;
    private List<Object> mUnfilteredItemList;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        Bundle arg = getArguments();
        mShortcuts = ShortcutDb.STATIC.getAllShortcuts(getActivity());
    }

    public void onPause(){
        super.onPause();
        if(mHandler != null){
            mHandler.removeMessages(LONG_CONNECTION);
        }
    }

    @Override
    protected void refresh() {
        listFiles(true);
    }

    private boolean helpOverlayAlreadyActivated() {
        return mPreferences.getBoolean(SSH_SHORTCUT_HELP_OVERLAY_KEY, false);
    }

    @Override
    public void onListingStart() {
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2> files) {
        mHandler.removeMessages(LONG_CONNECTION);
        super.onListingUpdate(files);
    }

    @Override
    public void onListingEnd() {
        mHandler.removeMessages(LONG_CONNECTION);
    }

    @Override
    public void onListingTimeOut() {
        if (getActivity() == null)
            return; //too late
        mHandler.removeMessages(LONG_CONNECTION);
        super.onListingTimeOut();
    }

    @Override
    protected void displayFailPage(){
        super.displayFailPage();
        View emptyView = mRootView.findViewById(R.id.empty_view);
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);

            Button emptyViewButton = (Button)emptyView.findViewById(R.id.empty_view_button);
            emptyViewButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    listFiles(true);
                }
            });
        }
    }

    @Override
    public void onCredentialRequired(Exception e) {
        if (getActivity() == null)
            return; //too late
        askForCredentials();
    }

    @Override
    public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
        if (getActivity() == null)
            return; //too late
        mHandler.removeMessages(LONG_CONNECTION);
        super.onListingFatalError(e, errorCode);
        if(errorCode== ListingEngine.ErrorEnum.ERROR_NO_PERMISSION)
            askForCredentials();
    }

    private void askForCredentials(){
        if(getParentFragmentManager().findFragmentByTag(FTPServerCredentialsDialog.class.getCanonicalName())==null){
            FTPServerCredentialsDialog dialog = new FTPServerCredentialsDialog();
            Bundle args = new Bundle();
            if(mCurrentDirectory!=null){
                NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mCurrentDirectory.toString());
                if(cred!=null) {
                    args.putString(FTPServerCredentialsDialog.USERNAME, cred.getUsername());
                    args.putString(FTPServerCredentialsDialog.PASSWORD, cred.getPassword());
                }
                args.putParcelable(FTPServerCredentialsDialog.URI, mCurrentDirectory);
                dialog.setArguments(args);
            }
            dialog.setOnConnectClickListener( new FTPServerCredentialsDialog.onConnectClickListener() {
                @Override
                public void onConnectClick(String username, Uri uri, String password) {
                    mCurrentDirectory = uri;
                    listFiles(true);
                }
            });
            dialog.setOnCancelClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                }
            });
            dialog.show(getParentFragmentManager(),FTPServerCredentialsDialog.class.getCanonicalName());
        }
    }

    protected void updateAdapterIfReady() {
        super.updateAdapterIfReady();
        mUnfilteredItemList = new ArrayList<>(mItemList);
    }

    public void filter(String filter){
        if(mUnfilteredItemList!=null){
            mItemList = new ArrayList<>();
            mFirstFileIndex = -1;
             for(Object obj : mUnfilteredItemList){
                 String name ;
                 if(obj instanceof MetaFile2)
                     name = ((MetaFile2)obj).getName();
                 else
                     name = ((Video)obj).getName();
                 if(name.toLowerCase().contains(filter.toLowerCase())||filter.isEmpty()) {
                     if(obj instanceof Video && mFirstFileIndex == -1)
                         mFirstFileIndex = mItemList.size();
                     mItemList.add(obj);
                 }
             }
             if(mBrowserAdapter!=null)
                 ((AdapterByNetwork)mBrowserAdapter).setCurrentItemList(mItemList);
        }
    }

    protected void listFiles(boolean discrete) {
        mHandler.removeMessages(LONG_CONNECTION);
        if(!discrete)
            mHandler.sendEmptyMessageDelayed(LONG_CONNECTION, LONG_CONNECTION_DELAY);
       super.listFiles(discrete);
    }

}