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

package com.archos.mediacenter.video.leanback.network.smb;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.mediacenter.video.R;

public class SmbServerCredentialsDialog extends DialogFragment {

    final private static String SMB_LATEST_USERNAME = "SMB_LATEST_USERNAME";

    final public static String USERNAME = "username";
    final public static String PASSWORD = "password";
    final public static String URI = "uri";

    private AlertDialog mDialog;
    private SharedPreferences mPreferences;
    private String mUsername="";
    private String mPassword="";
    private  Uri mUri = null;
    private onConnectClickListener mOnConnectClick;
    private OnClickListener mOnCancelClickListener;


    public interface onConnectClickListener{
        public void onConnectClick(String username, Uri path, String password);
    }

    public SmbServerCredentialsDialog() {}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args  = getArguments();
        if(args != null){
            mUsername = args.getString(USERNAME,"");
            mPassword = args.getString(PASSWORD,"");
            mUri = args.getParcelable(URI);
        }
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Get latest values from preference
        if(mUsername.isEmpty()&&mPassword.isEmpty()){
            mUsername = mPreferences.getString(SMB_LATEST_USERNAME, "");
        }
        if(mPassword.isEmpty()&&mUri!=null){
            NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
            Credential cred = database.getCredential(mUri.toString());
            if(cred!=null){
                mPassword= cred.getPassword();
            }
        }
        final View v = getActivity().getLayoutInflater().inflate(R.layout.ssh_credential_layout, null);
        v.findViewById(R.id.ssh_spinner).setVisibility(View.GONE);
        v.findViewById(R.id.remote).setVisibility(View.GONE);
        v.findViewById(R.id.port).setVisibility(View.GONE);
        final EditText usernameEt = (EditText)v.findViewById(R.id.username);
        final EditText passwordEt = (EditText)v.findViewById(R.id.password);
        v.findViewById(R.id.path).setVisibility(View.GONE);
        final CheckBox savePassword = (CheckBox)v.findViewById(R.id.save_password);
        final CheckBox showPassword = (CheckBox)v.findViewById(R.id.show_password_checkbox);
        showPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(!b)
                    passwordEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
                else
                    passwordEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }
        });
        usernameEt.setText(mUsername);
        passwordEt.setText(mPassword);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
        .setTitle(R.string.browse_ftp_server)
        .setView(v)
        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(mOnCancelClickListener!=null)
                    mOnCancelClickListener.onClick(null);
            }
        })
        .setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int id) {
                if(!usernameEt.getText().toString().isEmpty()){


                    final String username = usernameEt.getText().toString();
                    final String password = passwordEt.getText().toString();

                    // Store new values to preferences
                    mPreferences.edit()

                    .putString(SMB_LATEST_USERNAME, username)
                    .apply();

                    if(savePassword.isChecked())
                        NetworkCredentialsDatabase.getInstance().saveCredential(new Credential(username, password, mUri.toString(),true));
                    else
                        NetworkCredentialsDatabase.getInstance().addCredential(new Credential(username, password, mUri.toString(),true));
                    if(mOnConnectClick!=null){
                        mOnConnectClick.onConnectClick(username, mUri, password);
                    }

                }
                else
                    Toast.makeText(getActivity(), getString(R.string.ssh_remote_address_error), Toast.LENGTH_SHORT).show();
            }});
        mDialog = builder.create();

        return mDialog;
    }

    public void setOnConnectClickListener(onConnectClickListener onConnectClick) {
        mOnConnectClick = onConnectClick;
    }

    public void setOnCancelClickListener(OnClickListener onClickListener) {
        mOnCancelClickListener = onClickListener;
    }

}
