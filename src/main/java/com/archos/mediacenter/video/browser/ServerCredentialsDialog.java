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

package com.archos.mediacenter.video.browser;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.mediacenter.video.R;

public abstract class ServerCredentialsDialog extends DialogFragment {
    private AlertDialog mDialog;
    protected SharedPreferences mPreferences;
    private String mUsername="";
    private String mPassword="";
    private int mPort=-1;
    private int mType=-1;
    private String mRemote="";
    private onConnectClickListener mOnConnectClick;
    final private static String FTP_LATEST_USERNAME = "FTP_LATEST_USERNAME";
    final private static String FTP_LATEST_URI = "FTP_LATEST_URI";

    final public static String USERNAME = "username";
    final public static String PASSWORD = "password";
    final public static String URI = "uri";
    private OnClickListener mOnCancelClickListener;
    private String mPath;
    private CheckBox mShowPassword;
    private CheckBox mSavePassword;
    protected EditText mPathEt;
    protected EditText mPasswordEt;
    protected EditText mUsernameEt;
    protected Uri mUri;
    protected Spinner mTypeSp;
    protected EditText mAddressEt;
    protected EditText mPortEt;


    public interface onConnectClickListener{
        public void onConnectClick(String username, Uri uri, String password);
    }
    public ServerCredentialsDialog(){ }
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
        if(mUsername.isEmpty()&&mPassword.isEmpty()&&mUri==null){
            mUsername = mPreferences.getString(FTP_LATEST_USERNAME, "");
            String lastUri = mPreferences.getString(FTP_LATEST_URI, "");
            if(!lastUri.isEmpty()){
                mUri = Uri.parse(lastUri);
            }
        }

        if(mUri!=null){
            mPort = mUri.getPort();
            mType = "ftp".equals(mUri.getScheme())?0:"sftp".equals(mUri.getScheme())?1:2;
            mPath = mUri.getPath();
            mRemote = mUri.getHost();
        }
        else{
            mPort=-1;
            mType = 0;
            mPath = "";
            mRemote = "";
            mPassword = "";
        }
        if(mUri!=null){
            NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
            Credential cred = database.getCredential(mUri.toString());
            if(cred!=null){
                mPassword= cred.getPassword();
            }
        }
        final View v = getActivity().getLayoutInflater().inflate(R.layout.ssh_credential_layout, null);
        mTypeSp = (Spinner)v.findViewById(R.id.ssh_spinner);
        mAddressEt = (EditText)v.findViewById(R.id.remote);
        mPortEt = (EditText)v.findViewById(R.id.port);
        mUsernameEt = (EditText)v.findViewById(R.id.username);
        mPasswordEt = (EditText)v.findViewById(R.id.password);
        mPathEt = (EditText)v.findViewById(R.id.path);
        mSavePassword = (CheckBox)v.findViewById(R.id.save_password);
        mShowPassword = (CheckBox)v.findViewById(R.id.show_password_checkbox);
        mShowPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(!b)
                    mPasswordEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
                else
                    mPasswordEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            }
        });
        int type = mType;
        if (type==0 || type==1 || type==2) { // better safe than sorry
            mTypeSp.setSelection(type);
        }
        mAddressEt.setText(mRemote);
        mPathEt.setText(mPath);
        int portInt =  mPort;
        String portString = (portInt!=-1) ? Integer.toString(portInt) : "";
        mPortEt.setText(portString);
        mUsernameEt.setText(mUsername);
        mPasswordEt.setText(mPassword);
        
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
                if(!mUsernameEt.getText().toString().isEmpty()){
                    final String username = mUsernameEt.getText().toString();
                    final String password = mPasswordEt.getText().toString();

                    String uriToBuild = createUri();
                    onConnectClick(username, Uri.parse(uriToBuild), password);
                    if(mSavePassword.isChecked())
                        NetworkCredentialsDatabase.getInstance().saveCredential(new Credential(username, password, uriToBuild,true));
                    else
                        NetworkCredentialsDatabase.getInstance().addCredential(new Credential(username, password, uriToBuild,true));
                    if(mOnConnectClick!=null){
                        mOnConnectClick.onConnectClick(username, Uri.parse(uriToBuild), password);
                    }

                }
                else
                    Toast.makeText(getActivity(), getString(R.string.ssh_remote_address_error), Toast.LENGTH_SHORT).show();
            }});
        mDialog = builder.create();

        return mDialog;

    }
    public abstract void onConnectClick(String username, Uri uri, String password);
    public abstract String createUri();
    public void setOnConnectClickListener(onConnectClickListener onConnectClick) {
        mOnConnectClick = onConnectClick;
    }
    public void setOnCancelClickListener(OnClickListener onClickListener) {
        mOnCancelClickListener = onClickListener;
    }

}
