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

package com.archos.mediacenter.video.leanback.network.ftp;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Toast;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser.BrowserBySFTP;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediacenter.video.leanback.network.NetworkRootFragment;
import com.archos.mediaprovider.NetworkScanner;

import java.util.List;

/**
 * Created by vapillon on 17/04/15.
 */
public class FtpListingFragment extends ListingFragment {

    private static final String TAG = "NetworkListingFragment";
    private static final int LONG_CONNECTION = 1;
    private long mShorcutId;
    private boolean isCredentialsDialogDisplayed;
    protected final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case LONG_CONNECTION:
                    mHandler.removeMessages(LONG_CONNECTION);
                    mLongConnectionMessage.setText(R.string.connection_abnormally_long);
                    mLongConnectionMessage.setVisibility(View.VISIBLE);
                    mActionButton.setVisibility(View.VISIBLE);
                    mActionButton.setText(R.string.connection_reset);
                    mActionButton.setEnabled(true);
                    mActionButton.setClickable(true);
                    mActionButton.requestFocus();
                    mActionButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            SFTPSession.getInstance().removeSession(mUri);
                            Session.getInstance().removeFTPClient(mUri);
                            startListing(mUri);
                        }
                    });
                    break;


            }
        }
    };
    @Override
    protected  ListingFragment instantiateNewFragment() {
        return new FtpListingFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Second orb is for indexing/unindexing
        checkIfIsShortcut();
        updateOrbIcon();
        getTitleView().setOnOrb3ClickedListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isShortcut()) {
                    deleteShortcut();
                } else {
                    createShortcut();
                    askForIndexing();
                }
            }
        });
    }

    @Override
    protected void startListing(Uri uri){
        mHandler.removeMessages(LONG_CONNECTION);
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        mHandler.sendEmptyMessageDelayed(LONG_CONNECTION, BrowserBySFTP.LONG_CONNECTION_DELAY);
        super.startListing(uri);
    }


    @Override
    public void onListingStart() {
        super.onListingStart();
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2> files) {
        mHandler.removeMessages(LONG_CONNECTION);
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        super.onListingUpdate(files);
    }

    @Override
    public void onListingEnd() {
        mHandler.removeMessages(LONG_CONNECTION);
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        super.onListingEnd();
    }

    @Override
    public void onListingTimeOut() {
        mHandler.removeMessages(LONG_CONNECTION);
        super.onListingTimeOut();
    }


    private void askForIndexing() {
        new AlertDialog.Builder(getActivity()).setMessage(R.string.add_all_items_to_library).setTitle(mUri.getLastPathSegment()).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(FileUtils.getName(mUri), mUri.toString()));
                NetworkScanner.scanVideos(getActivity(), mUri);
            }
        }).setNegativeButton(R.string.no, null).show();
    }

    @Override
    protected int getListingTimeout(){
        return 30000;
    }
    private void checkIfIsShortcut() {
        mShorcutId = ShortcutDb.STATIC.isShortcut(getActivity(), mUri.toString());
        //Log.d(TAG, "shorcutId = " + mShorcutId + "\t (" + mUri.toString() + ")");
    }

    private void updateOrbIcon() {
        getTitleView().setOrb3IconResId(isShortcut() ? R.drawable.orb_minus : R.drawable.orb_plus);
    }
    @Override
    public void onListingFatalError( Exception e, ListingEngine.ErrorEnum errorCode){
        mHandler.removeMessages(LONG_CONNECTION);
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        if(errorCode== ListingEngine.ErrorEnum.ERROR_NO_PERMISSION)
            askForCredentials();
    }

    private void askForCredentials() {
        if(getFragmentManager().findFragmentByTag(FtpServerCredentialsDialog.class.getCanonicalName())==null) {
            FtpServerCredentialsDialog dialog = new FtpServerCredentialsDialog();
            Bundle args = new Bundle();
            if (mUri != null) {
                NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
                if (cred != null) {
                    args.putString(FtpServerCredentialsDialog.USERNAME, cred.getUsername());
                    args.putString(FtpServerCredentialsDialog.PASSWORD, cred.getPassword());
                }

                args.putString(FtpServerCredentialsDialog.PATH, mUri.getPath());
                args.putInt(FtpServerCredentialsDialog.PORT, mUri.getPort());
                args.putString(FtpServerCredentialsDialog.REMOTE, mUri.getHost());
                int type = 0;
                if("sftp".equals(mUri.getScheme())) {
                    type = 1;
                }if("ftps".equals(mUri.getScheme())) {
                    type = 2;
                }
                args.putInt(FtpServerCredentialsDialog.TYPE, type);
                dialog.setArguments(args);
            }
            dialog.setOnConnectClickListener(new FtpServerCredentialsDialog.onConnectClickListener() {
                @Override
                public void onConnectClick(String username, String path, String password, int port, int type, String remote) {
                    isCredentialsDialogDisplayed = false;
                    String uriToBuild = "";
                    switch (type) {
                        case 0:
                            uriToBuild = "ftp";
                            break;
                        case 1:
                            uriToBuild = "sftp";
                            break;
                        case 2:
                            uriToBuild = "ftps";
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid FTP type " + type);
                    }
                    //path needs to start by a "/"
                    if (path.isEmpty() || !path.startsWith("/"))
                        path = "/" + path;
                    uriToBuild += "://" + (!remote.isEmpty() ? remote + (port != -1 ? ":" + port : "") : "") + path;
                    mUri = Uri.parse(uriToBuild);
                    startListing(mUri);
                }

            });
            dialog.setOnCancelClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                }
            });
            dialog.show(getFragmentManager(), FtpServerCredentialsDialog.class.getCanonicalName());
        }
    }

    @Override
    public void onCredentialRequired(Exception e){
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        mHandler.removeMessages(LONG_CONNECTION);
        askForCredentials();
    }

    private boolean isShortcut() {
        return mShorcutId>=0;
    }

    /** Add current Uri to the shortcut list */
    private void createShortcut() {

        String shortcutPath = mUri.toString();
        boolean result = ShortcutDb.STATIC.insertShortcut(mUri, mUri.getLastPathSegment());

        if (result) {
            Toast.makeText(getActivity(), getString(R.string.indexed_folder_added, shortcutPath), Toast.LENGTH_SHORT).show();
            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
            // Send a scan request to MediaScanner
           // NetworkScanner.scanVideos(getActivity(), shortcutPath);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        checkIfIsShortcut();
        updateOrbIcon();
    }

    /** Remove current Uri from the shortcut list */
    private void deleteShortcut() {
        String shortcutPath = mUri.toString();

        boolean result = ShortcutDb.STATIC.removeShortcut(mUri)>0;
        ShortcutDbAdapter.VIDEO.deleteShortcut(getActivity(), mUri.toString());
        if (result) {
            Toast.makeText(getActivity(), getString(R.string.shortcut_removed, shortcutPath), Toast.LENGTH_SHORT).show();
            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        checkIfIsShortcut();
        updateOrbIcon();
    }
}
