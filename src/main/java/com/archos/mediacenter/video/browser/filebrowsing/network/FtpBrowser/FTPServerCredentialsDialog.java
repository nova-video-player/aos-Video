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

import android.net.Uri;

import com.archos.mediacenter.video.browser.ServerCredentialsDialog;
import com.archos.filecorelibrary.MetaFile2Factory;

public class FTPServerCredentialsDialog extends ServerCredentialsDialog {


    final private static String FTP_LATEST_USERNAME = "FTP_LATEST_USERNAME";
    final private static String FTP_LATEST_URI = "FTP_LATEST_URI";
    final public static String USERNAME = "username";
    final public static String PASSWORD = "password";


    public FTPServerCredentialsDialog(){ }

    @Override
    public void onConnectClick(String username, Uri uri, String password) {
        // Store new values to preferences
        mPreferences.edit()
                .putString(FTP_LATEST_URI, uri.toString())
                .putString(FTP_LATEST_USERNAME, username)
                .apply();
    }

    @Override
    public String createUri() {
        final int type = mTypeSp.getSelectedItemPosition();
        final String address = mAddressEt.getText().toString();
        String path = mPathEt.getText().toString();
        int port = -1;
        try{
            port = Integer.parseInt(mPortEt.getText().toString());
        } catch(NumberFormatException e){ }

        var uriB = new Uri.Builder();

        String scheme = "";
        switch(type){
            case 0: scheme = "ftp"; break;
            case 1: scheme = "sftp"; break;
            case 2: scheme = "ftps"; break;
            default:
                throw new IllegalArgumentException("Invalid FTP type "+type);
        }

        uriB.scheme(scheme);

        //TODO: Do we need a default port or not...? URI could not have a port included
        if(port == -1) {
            port = MetaFile2Factory.defaultPortForProtocol(scheme);
        }

        if (!address.isEmpty()) {
            if(port == -1)
                uriB.authority(address);
            else
                uriB.authority(address + ":" + port);
        }

        uriB.path(path);

        return uriB.toString();
    }
}
