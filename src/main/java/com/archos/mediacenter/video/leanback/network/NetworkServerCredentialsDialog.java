// Copyright 2022 Courville Software
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

package com.archos.mediacenter.video.leanback.network;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.mediacenter.video.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkServerCredentialsDialog extends DialogFragment {

    private static final Logger log = LoggerFactory.getLogger(NetworkServerCredentialsDialog.class);

    private AlertDialog mDialog;
    private SharedPreferences mPreferences;
    private String mUsername="";
    private String mPassword="";
    private int mPort=-1;
    private int mType=-1;
    private String mRemote="";
    private onConnectClickListener mOnConnectClick;
    final private static String NET_LATEST_TYPE = "NET_LATEST_TYPE";
    final private static String NET_LATEST_ADDRESS = "NET_LATEST_ADDRESS";
    final private static String NET_LATEST_PORT = "NET_LATEST_PORT";
    final private static String NET_LATEST_USERNAME = "NET_LATEST_USERNAME";
    final private static String NET_LATEST_DOMAIN = "NET_LATEST_DOMAIN";

    final public static String USERNAME = "username";
    final public static String REMOTE = "remote_address";
    final public static String PORT = "port";
    final public static String PASSWORD = "password";
    final public static String TYPE = "type";
    final public static String PATH = "path";
    final public static String DOMAIN = "domain";
    private OnClickListener mOnCancelClickListener;
    private String mPath = "";
    private String mDomain = "";
    private EditText portEt;
    private EditText addressEt;
    private EditText domainEt;

    public interface onConnectClickListener {
        public void onConnectClick(String username, String path, String password, int port, int type, String remote, String domain);
    }
    public NetworkServerCredentialsDialog(){ }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args  = getArguments();
        if(args != null){
            mUsername = args.getString(USERNAME, "");
            mPassword = args.getString(PASSWORD, "");
            mPort = args.getInt(PORT, -1);
            mType = args.getInt(TYPE, 0);
            mPath = args.getString(PATH, "");
            mRemote = args.getString(REMOTE, "");
            mDomain = args.getString(DOMAIN, "");
        }
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Get latest values from preference
        if(mUsername.isEmpty()&&mPassword.isEmpty()&&mPort==-1&&mType==-1&&mRemote.isEmpty()){
            mRemote = mPreferences.getString(NET_LATEST_ADDRESS, "");
            mUsername = mPreferences.getString(NET_LATEST_USERNAME, "");
            mType = mPreferences.getInt(NET_LATEST_TYPE, 0);
            mPort = mPreferences.getInt(NET_LATEST_PORT, -1);
        }
        if(mPassword.isEmpty()&&!mRemote.isEmpty()){
            NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
            String uriToBuild = "";
            switch(mType){
                case 0: uriToBuild = "ftp"; break;
                case 1: uriToBuild = "sftp"; break;
                case 2: uriToBuild = "ftps"; break;
                case 3: uriToBuild = "smb"; break;
                default:
                    throw new IllegalArgumentException("Invalid FTP type "+mType);
            }
            uriToBuild +="://"+mRemote+":"+mPort+"/";
            log.debug("onCreateDialog: uriToBuild=" + uriToBuild);

            Credential cred = database.getCredential(uriToBuild);
            if(cred!=null){
                mPassword= cred.getPassword();
                mDomain = cred.getDomain();
            }
        }
        final View v = getActivity().getLayoutInflater().inflate(R.layout.network_credential_layout, null);
        final Spinner typeSp = (Spinner)v.findViewById(R.id.ssh_spinner);
        addressEt = (EditText)v.findViewById(R.id.remote);
        portEt = (EditText)v.findViewById(R.id.port);
        final EditText usernameEt = (EditText)v.findViewById(R.id.username);
        final EditText passwordEt = (EditText)v.findViewById(R.id.password);
        final EditText pathEt = (EditText)v.findViewById(R.id.path);
        final EditText domainEt = (EditText)v.findViewById(R.id.domain);
        final CheckBox savePassword = (CheckBox)v.findViewById(R.id.save_password);
        final CheckBox showPassword = (CheckBox)v.findViewById(R.id.show_password_checkbox);
        v.findViewById(R.id.domain).setVisibility(View.GONE);

        typeSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (position == 3) v.findViewById(R.id.domain).setVisibility(View.VISIBLE);
                else {
                    ((EditText) v.findViewById(R.id.domain)).setText("");
                    v.findViewById(R.id.domain).setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });

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
        if (type > 0 && type < 4) { // better safe than sorry
            typeSp.setSelection(type);
        }
        addressEt.setText(mRemote);
        pathEt.setText(mPath);
        int portInt =  mPort;
        String portString = (portInt!=-1) ? Integer.toString(portInt) : "";
        portEt.setText(portString);
        log.debug("onCreateDialog: username=" + mUsername + ", domain=" + mDomain + ", port=" + mPort + ", remote=" + mRemote + ", path=" + mPath + "; type=" + mType);
        usernameEt.setText(mUsername);
        passwordEt.setText(mPassword);
        if (type == 3) domainEt.setText(mDomain);

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
                // username can be empty with samba guest shares
                if(!usernameEt.getText().toString().isEmpty() || type == 3){
                    final int type = typeSp.getSelectedItemPosition();
                    final String address = addressEt.getText().toString();
                    String path = pathEt.getText().toString();
                    int port = -1;
                    try{
                        port = Integer.parseInt(portEt.getText().toString());
                    } catch(NumberFormatException e){ }

                    // get default port if it's wrong
                    switch(type){
                        case 0:
                        case 2:
                            if (port == -1) port = 21; break;
                        case 1: if (port == -1) port = 22; break;
                        case 3: if (port == -1) port = 445; break;
                        default:
                            throw new IllegalArgumentException("Invalid protocol type "+type);
                    }

                    final String username = usernameEt.getText().toString();
                    final String password = passwordEt.getText().toString();
                    final String domain = domainEt.getText().toString();

                    // Store new values to preferences
                    mPreferences.edit()
                            .putInt(NET_LATEST_TYPE, type)
                            .putString(NET_LATEST_ADDRESS, address)
                            .putInt(NET_LATEST_PORT, port)
                            .putString(NET_LATEST_USERNAME, username)
                            .putString(NET_LATEST_DOMAIN, domain)
                            .apply();
                    String uriToBuild = "";
                    switch(type){
                        case 0: uriToBuild = "ftp"; break;
                        case 1: uriToBuild = "sftp"; break;
                        case 2: uriToBuild = "ftps"; break;
                        case 3: uriToBuild = "smb"; break;
                        default:
                            throw new IllegalArgumentException("Invalid protocol type "+type);
                    }
                    // path needs to start by a "/"
                    if(path.isEmpty()||!path.startsWith("/"))
                        path = "/"+path;
                    uriToBuild +="://"+(!address.isEmpty()?address+(port!=-1?":"+port:""):"")+path;
                    log.debug("onCreateDialog: username=" + mUsername + ", domain=" + mDomain + ", port=" + mPort + ", remote=" + mRemote + ", path=" + mPath + "; type=" + mType);
                    if(savePassword.isChecked())
                        NetworkCredentialsDatabase.getInstance().saveCredential(new Credential(username, password, uriToBuild, domain, true));
                    else
                        NetworkCredentialsDatabase.getInstance().addCredential(new Credential(username, password, uriToBuild, domain, true));
                    if(mOnConnectClick!=null){
                        mOnConnectClick.onConnectClick(username, path, password, port, type, address, domain);
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
