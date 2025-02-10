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

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.ftp.Session;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser.BrowserBySFTP;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediacenter.video.leanback.network.NetworkListingFragment;

import java.util.List;

/**
 * Created by vapillon on 17/04/15.
 */

public class FtpListingFragment extends NetworkListingFragment {

    private static final String TAG = "FtpListingFragment";
    private static final int LONG_CONNECTION = 1;
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
    protected void startListing(Uri uri){
        mHandler.removeMessages(LONG_CONNECTION);
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        mHandler.sendEmptyMessageDelayed(LONG_CONNECTION, BrowserBySFTP.LONG_CONNECTION_DELAY);
        super.startListing(uri);
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

    @Override
    protected int getListingTimeout(){
        return 30000;
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
        if(getParentFragmentManager().findFragmentByTag(FtpServerCredentialsDialog.class.getCanonicalName())==null) {
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
            dialog.show(getParentFragmentManager(), FtpServerCredentialsDialog.class.getCanonicalName());
        }
    }

    @Override
    public void onCredentialRequired(Exception e){
        mLongConnectionMessage.setVisibility(View.GONE);
        mActionButton.setVisibility(View.GONE);
        mHandler.removeMessages(LONG_CONNECTION);
        askForCredentials();
    }
}
