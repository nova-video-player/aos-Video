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

package com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.archos.mediacenter.video.browser.ServerCredentialsDialog;

public class SMBServerCredentialsDialog extends ServerCredentialsDialog {
    private Dialog mDialog;
    final private static String SMB_LATEST_USERNAME = "SMB_LATEST_USERNAME";


    public SMBServerCredentialsDialog(){ }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mDialog = super.onCreateDialog(savedInstanceState);
        mTypeSp.setVisibility(View.GONE);
        mAddressEt.setVisibility(View.GONE);
        mPortEt.setVisibility(View.GONE);
        mPathEt.setVisibility(View.GONE);
        if(mUsernameEt.getText().toString().isEmpty()){
            mUsernameEt.setText(mPreferences.getString(SMB_LATEST_USERNAME,""));
        }
        return mDialog;

    }

    @Override
    public void onConnectClick(String username, Uri uri, String password) {
        // Store new values to preferences
        mPreferences.edit()
                .putString(SMB_LATEST_USERNAME, username)
                .apply();
    }

    @Override
    public String createUri() {
        return mUri.toString();
    }


}
