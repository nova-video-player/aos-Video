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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.AdapterByNetwork;
import com.archos.mediacenter.video.browser.filebrowsing.network.BrowserByNetwork;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.NetworkScanner;

import java.util.ArrayList;
import java.util.List;


public class BrowserBySFTP extends BrowserByNetwork implements ListingEngine.Listener,LoaderManager.LoaderCallbacks<Cursor> {
    public static final long LONG_CONNECTION_DELAY = 8000; //delay before proposing to reset connection

    private static final int LONG_CONNECTION = 9;
    private static final String SSH_SHORTCUT_HELP_OVERLAY_KEY = "ssh_shortcut_help_overlay";
    protected static final String CURRENT_DIRECTORY = "currentDirectory";

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
                            emptyViewText.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        //super.onCreateOptionsMenu(menu, inflater);

        menu.add(0, R.string.add_ssh_server, 0, R.string.add_ssh_server);
        if(mCurrentDirectory!=null){
           MenuItem mi;
            if(ShortcutDb.STATIC.isShortcut(getActivity(), mCurrentDirectory.toString())<=0)
                mi= menu.add(0,R.string.add_ssh_shortcut, 0,R.string.add_ssh_shortcut);
            else
                mi= menu.add(0,R.string.remove_from_shortcuts, 0,R.string.remove_from_shortcuts);
           mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        if(getActivity() instanceof MainActivity) {
            mSearchView = ((MainActivity) getActivity()).getSearchView();
            mSearchView.setSearchableInfo(null);
            mSearchView.setQuery("", true);//resetting query
            mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                boolean isFirstEntryTextChange = true;


                @Override
                public boolean onQueryTextChange(String newText) {
                    if (!isFirstEntryTextChange)
                        filter(newText);
                    isFirstEntryTextChange = false;
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String query) {

                    return true;
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        
        
        
        if(item.getItemId()==R.string.add_ssh_server){
            askForCredentials();
            return true;
         
        }
        else if (item.getItemId()==R.string.remove_from_shortcuts){
            ShortcutDb.STATIC.removeShortcut(mCurrentDirectory);
            NetworkScanner.removeVideos(getActivity(), mCurrentDirectory);
            getActivity().invalidateOptionsMenu();
        }
        else if(item.getItemId()==R.string.add_ssh_shortcut){
            
                final View v = getActivity().getLayoutInflater().inflate(R.layout.ssh_shortcut_dialog_layout, null);
                ((EditText)v.findViewById(R.id.shortcut_name)).setText(mCurrentDirectory.getLastPathSegment());

                new AlertDialog.Builder(getActivity())
                .setCancelable(false)
                .setView(v)
                .setTitle(R.string.ssh_shortcut_name)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                        if (((CheckBox) v.findViewById(R.id.checkBox)).isChecked()) {
                            NetworkScanner.scanVideos(getActivity(), mCurrentDirectory);
                            addIndexedFolder(mCurrentDirectory, ((EditText) v.findViewById(R.id.shortcut_name)).getText().toString());
                        }
                        ShortcutDb.STATIC.insertShortcut(mCurrentDirectory, ((EditText) v.findViewById(R.id.shortcut_name)).getText().toString());
                        getActivity().invalidateOptionsMenu();
                    }
                })
                .setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                    }
                })
                .setNegativeButton(android.R.string.cancel,new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        dialog.cancel();
                    }
                }).create().show();
            }
        return super.onOptionsItemSelected(item);
        
    }

    protected void createShortcut(String shortcutPath, String shortcutName) {
        super.createShortcut(shortcutPath,shortcutName);
        ShortcutDb.STATIC.insertShortcut(mCurrentDirectory, shortcutName);
    }

    private void addIndexedFolder(Uri currentDirectory, String name) {
        ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(name, currentDirectory.toString()));
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
        if(getFragmentManager().findFragmentByTag(FTPServerCredentialsDialog.class.getCanonicalName())==null){
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
            dialog.show(getFragmentManager(),FTPServerCredentialsDialog.class.getCanonicalName());
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