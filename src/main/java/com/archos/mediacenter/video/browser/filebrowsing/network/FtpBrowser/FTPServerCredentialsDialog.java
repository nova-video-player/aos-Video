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

        // get default port if it's wrong
        switch(type){
            case 0: if (port == -1)  port=21; break;
            case 1: if (port == -1)  port=22; break;
            case 2: if (port == -1)  port=21; break;
            default:
                throw new IllegalArgumentException("Invalid FTP type "+type);
        }
        String uriToBuild = "";
        switch(type){
            case 0: uriToBuild = "ftp"; break;
            case 1: uriToBuild = "sftp"; break;
            case 2: uriToBuild = "ftps"; break;
            default:
                throw new IllegalArgumentException("Invalid FTP type "+type);
        }
        //path needs to start by a "/"
        if(path.isEmpty()||!path.startsWith("/"))
            path = "/"+path;
        uriToBuild +="://"+(!address.isEmpty()?address+(port!=-1?":"+port:""):"")+path;

        return uriToBuild;

    }



}
