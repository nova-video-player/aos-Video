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
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

public class FtpServerCredentialsDialog extends DialogFragment {
    private AlertDialog mDialog;
    private SharedPreferences mPreferences;
    private String mUsername="";
    private String mPassword="";
    private int mPort=-1;
    private int mType=-1;
    private String mRemote="";
    private onConnectClickListener mOnConnectClick;
    final private static String FTP_LATEST_TYPE = "FTP_LATEST_TYPE";
    final private static String FTP_LATEST_ADDRESS = "FTP_LATEST_ADDRESS";
    final private static String FTP_LATEST_PORT = "FTP_LATEST_PORT";
    final private static String FTP_LATEST_USERNAME = "FTP_LATEST_USERNAME";

    final public static String USERNAME = "username";
    final public static String REMOTE = "remote_address";
    final public static String PORT = "port";
    final public static String PASSWORD = "password";
    final public static String TYPE = "type";
    final public static String PATH = "path";
    private OnClickListener mOnCancelClickListener;
    private String mPath;
    private EditText portEt;
    private EditText addressEt;


    public interface onConnectClickListener{
        public void onConnectClick(String username, String path, String password, int port, int type, String remote);
    }
    public FtpServerCredentialsDialog(){ }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args  = getArguments();
        if(args != null){
            mUsername = args.getString(USERNAME,"");
            mPassword = args.getString(PASSWORD,"");
            mPort = args.getInt(PORT, -1);
            mType = args.getInt(TYPE, 0);
            mPath = args.getString(PATH, "");
            mRemote = args.getString(REMOTE,"");
        }
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Get latest values from preference
        if(mUsername.isEmpty()&&mPassword.isEmpty()&&mPort==-1&&mType==-1&&mRemote.isEmpty()){
            mRemote = mPreferences.getString(FTP_LATEST_ADDRESS, "");
            mUsername = mPreferences.getString(FTP_LATEST_USERNAME, "");
            mType = mPreferences.getInt(FTP_LATEST_TYPE, 0);
            mPort = mPreferences.getInt(FTP_LATEST_PORT, -1);
        }
        if(mPassword.isEmpty()&&!mRemote.isEmpty()){
            NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
            String uriToBuild = "";
            switch(mType){
                case 0: uriToBuild = "ftp"; break;
                case 1: uriToBuild = "sftp"; break;
                case 2: uriToBuild = "ftps"; break;
                default:
                    throw new IllegalArgumentException("Invalid FTP type "+mType);
            }
            uriToBuild +="://"+mRemote+":"+mPort+"/";
            Credential cred = database.getCredential(uriToBuild);
            if(cred!=null){
                mPassword= cred.getPassword();
            }
        }
        final View v = getActivity().getLayoutInflater().inflate(R.layout.ssh_credential_layout, null);
        final Spinner typeSp = (Spinner)v.findViewById(R.id.ssh_spinner);
        addressEt = (EditText)v.findViewById(R.id.remote);
        portEt = (EditText)v.findViewById(R.id.port);
        final EditText usernameEt = (EditText)v.findViewById(R.id.username);
        final EditText passwordEt = (EditText)v.findViewById(R.id.password);
        final EditText pathEt = (EditText)v.findViewById(R.id.path);
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
        int type = mType;
        if (type==0 || type==1 || type==2) { // better safe than sorry
            typeSp.setSelection(type);
        }
        addressEt.setText(mRemote);
        pathEt.setText(mPath);
        int portInt =  mPort;
        String portString = (portInt!=-1) ? Integer.toString(portInt) : "";
        portEt.setText(portString);
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
                    final int type = typeSp.getSelectedItemPosition();
                    final String address = addressEt.getText().toString();
                    String path = pathEt.getText().toString();
                    int port = -1;
                    try{
                        port = Integer.parseInt(portEt.getText().toString());
                    } catch(NumberFormatException e){ }

                    // get default port if it's wrong
                    switch(type){
                        case 0: if (port == -1)  port=21; break;
                        case 1: if (port == -1)  port=22; break;
                        case 2: if (port == -1)  port=21; break;
                        default:
                            throw new IllegalArgumentException("Invalid FTP type "+type);
                    }

                    final String username = usernameEt.getText().toString();
                    final String password = passwordEt.getText().toString();

                    // Store new values to preferences
                    mPreferences.edit()
                    .putInt(FTP_LATEST_TYPE, type)
                    .putString(FTP_LATEST_ADDRESS, address)
                    .putInt(FTP_LATEST_PORT, port)
                    .putString(FTP_LATEST_USERNAME, username)
                    .apply();
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
                    if(savePassword.isChecked())
                        NetworkCredentialsDatabase.getInstance().saveCredential(new Credential(username, password, uriToBuild,true));
                    else
                        NetworkCredentialsDatabase.getInstance().addCredential(new Credential(username, password, uriToBuild,true));
                    if(mOnConnectClick!=null){
                        mOnConnectClick.onConnectClick(username, path, password, port, type, address);
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
