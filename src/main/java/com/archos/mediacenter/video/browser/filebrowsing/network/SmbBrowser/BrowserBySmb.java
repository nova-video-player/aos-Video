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

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.BrowserByNetwork;

/**
 * Created by alexandre on 29/10/15.
 */
public class BrowserBySmb extends BrowserByNetwork {
    private TextView mButton;
    private String mUser;

    @Override
    protected Uri getDefaultDirectory() {
        return null;
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getConnectionUser();
        mButton = (TextView) mRootView.findViewById(R.id.connection_button);
        mButton.setBackgroundResource(R.drawable.big_button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askForCredentials();
            }
        });
        mButton.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
        mButton.setVisibility(View.VISIBLE);
        displayConnectionDescription();


    }

    @Override
    public void onCredentialRequired(Exception e) {
        super.onCredentialRequired(e);
        // We are in a protected folder => ask the user to enter the login and password
        Log.w("jcifs2", "listing error - no permission");
        askForCredentials();
    }

    private void askForCredentials() {
        String tag = SMBServerCredentialsDialog.class.getCanonicalName();
        SMBServerCredentialsDialog dialog = (SMBServerCredentialsDialog)getFragmentManager().findFragmentByTag(tag);
        if (dialog == null) {
            dialog = new SMBServerCredentialsDialog();
            Bundle args = new Bundle();
            if (mCurrentDirectory != null) {
                NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mCurrentDirectory.toString());
                if (cred != null) {
                    args.putString(SMBServerCredentialsDialog.USERNAME, cred.getUsername());
                    args.putString(SMBServerCredentialsDialog.PASSWORD, cred.getPassword());
                }
                args.putParcelable(SMBServerCredentialsDialog.URI, mCurrentDirectory);
                dialog.setArguments(args);
            }
            dialog.show(getFragmentManager(), tag);
        }

        dialog.setOnConnectClickListener(new SMBServerCredentialsDialog.onConnectClickListener() {
            @Override
            public void onConnectClick(String username, Uri path, String password) {
                mCurrentDirectory = path;
                getConnectionUser();
                displayConnectionDescription();
                listFiles(true);
            }
        });
    }

    private void getConnectionUser() {
        mUser = getString(R.string.network_guest);
        if (mCurrentDirectory != null) {
            NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mCurrentDirectory.toString());
            if (cred != null) {
                String userName = cred.getUsername();
                if (userName != null && !userName.isEmpty()) {
                    mUser = userName;
                }
            }
        }
    }

    private void displayConnectionDescription() {
        final String description = getString(R.string.network_connected_as, mUser);
        final int userStart = description.indexOf(mUser);
        final int userEnd = userStart + mUser.length();
        final SpannableStringBuilder sb = new SpannableStringBuilder(description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            sb.setSpan(new TypefaceSpan("sans-serif-light"), 0, description.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(new TypefaceSpan("sans-serif"), userStart, userEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        sb.setSpan(new StyleSpan(Typeface.BOLD), userStart, userEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        mButton.setText(sb);
    }


}
