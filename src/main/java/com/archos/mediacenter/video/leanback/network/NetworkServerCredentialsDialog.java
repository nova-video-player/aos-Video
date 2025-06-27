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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class NetworkServerCredentialsDialog extends DialogFragment {

    // Note: this is both used in the leanback and the mobile interface in network shortcuts

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
    final private static String NET_LATEST_PATH = "NET_LATEST_PATH";

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
            mPath = mPreferences.getString(NET_LATEST_PATH, "");
            mDomain = mPreferences.getString(NET_LATEST_DOMAIN, "");
        }
        if(mPassword.isEmpty()&&!mRemote.isEmpty()){
            NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
            String uriToBuild = "";
            uriToBuild = UriUtils.getTypeUri(mType);
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

        String[] spinnerItems = getResources().getStringArray(R.array.protocol_types); // replace with actual array
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                getContext(), R.layout.custom_spinner_item, spinnerItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTypeface(ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_65md));
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTypeface(ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_65md));
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown_item);
        typeSp.setAdapter(adapter);

        // Calculate widest item width
        int maxWidth = 0;
        View itemView = null;
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < adapter.getCount(); i++) {
            itemView = adapter.getDropDownView(i, null, typeSp);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            maxWidth = Math.max(maxWidth, itemView.getMeasuredWidth());
        }
        // Set dropdown position and width after layout
        int finalMaxWidth = maxWidth;
        typeSp.post(new Runnable() {
            @Override
            public void run() {
                typeSp.setDropDownVerticalOffset(typeSp.getHeight());
                typeSp.setDropDownHorizontalOffset(0);
                typeSp.setDropDownWidth(finalMaxWidth); // Add some padding
            }
        });

        // Disable internal spinner item ripple (MOP)
        typeSp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Let the ripple animation begin naturally
                        v.setPressed(true);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Let ripple finish
                        v.setPressed(false);

                        // Trigger click manually to preserve accessibility
                        v.performClick();

                        // Delay dropdown ripple removal to avoid interfering with ripple
                        for (int i = 0; i < 5; i++) {
                            final int attempt = i;
                            typeSp.postDelayed(() -> {
                                try {
                                    Field popupField = AppCompatSpinner.class.getDeclaredField("mPopup");
                                    popupField.setAccessible(true);
                                    Object popupWindow = popupField.get(typeSp);

                                    if (popupWindow != null &&
                                            popupWindow.getClass().getName().equals("androidx.appcompat.widget.AppCompatSpinner$DropdownPopup")) {

                                        Field dropDownListField = popupWindow.getClass().getSuperclass().getDeclaredField("mDropDownList");
                                        dropDownListField.setAccessible(true);
                                        ListView listView = (ListView) dropDownListField.get(popupWindow);

                                        if (listView != null) {
                                            listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
                                            Log.d("SpinnerHack", "Dropdown ripple removed (attempt " + attempt + ")");
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e("SpinnerHack", "Reflection error: " + e.getMessage());
                                }
                            }, 100 + i * 50); // Start late to avoid clashing
                        }
                        return true; // We handled the touch completely
                }
                return false;
            }
        });

        typeSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                if (UriUtils.requiresDomain(position)) v.findViewById(R.id.domain).setVisibility(View.VISIBLE);
                else {
                    ((EditText) v.findViewById(R.id.domain)).setText("");
                    v.findViewById(R.id.domain).setVisibility(View.GONE);
                }
                ((EditText) v.findViewById(R.id.port)).setText("");
            }
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                if (UriUtils.requiresDomain(mType))
                    v.findViewById(R.id.domain).setVisibility(View.VISIBLE);
                else v.findViewById(R.id.domain).setVisibility(View.GONE);
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
        typeSp.setSelection(type);
        addressEt.setText(mRemote);
        pathEt.setText(mPath);
        int portInt =  mPort;
        String portString = (portInt!=-1) ? Integer.toString(portInt) : "";
        portEt.setText(portString);
        log.debug("onCreateDialog: username=" + mUsername + ", domain=" + mDomain + ", port=" + mPort + ", remote=" + mRemote + ", path=" + mPath + "; type=" + mType);
        usernameEt.setText(mUsername);
        passwordEt.setText(mPassword);
        if (UriUtils.requiresDomain(type)) domainEt.setText(mDomain);

        View customTitleView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom_title, null);
        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * customTitleView.getContext().getResources().getDisplayMetrics().density);
        customTitleView.setPadding(dpToPx.apply(12),dpToPx.apply(10),dpToPx.apply(16),dpToPx.apply(4));
        TextView titleText = customTitleView.findViewById(R.id.dialog_title);
        titleText.setText(R.string.browse_ftp_server);
        Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
        titleText.setTypeface(customFont);
        titleText.setTextSize(24);
        ImageView iconView = customTitleView.findViewById(R.id.dialog_icon);
        iconView.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme)
        .setCustomTitle(customTitleView)
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

                String username = usernameEt.getText().toString();
                final String password = passwordEt.getText().toString();
                final String domain = domainEt.getText().toString();

                final int type = typeSp.getSelectedItemPosition();
                final String address = addressEt.getText().toString();
                String path = pathEt.getText().toString();

                int port = -1;
                if (! portEt.getText().toString().isEmpty()) {
                    try {
                        port = Integer.parseInt(portEt.getText().toString());
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), getString(R.string.invalid_port), Toast.LENGTH_SHORT).show();
                    }
                }

                String scheme = "";
                scheme = UriUtils.getTypeUri(type);

                // webdav(s) empty user means anonymous
                if (username.equals("") && UriUtils.emptyCredentialMeansAnonymous(scheme))
                    username = "anonymous";

                boolean validUri = true;

                // username can be empty with samba guest shares (UriUtils.requiresDomain(type))
                if (username.equals("") && ! UriUtils.requiresDomain(type)) {
                    log.debug("onClick: invalid credential, username empty and not smb protocol");
                    validUri = false;
                }
                // path needs to start by a "/"
                if(path.isEmpty()||!path.startsWith("/"))
                    path = "/"+path;

                if (! UriUtils.isValidHost(address)) {
                    Toast.makeText(getActivity(), getString(R.string.invalid_host), Toast.LENGTH_SHORT).show();
                    log.warn("onClick: invalid host: " + address);
                    validUri = false;
                } else if (! UriUtils.isValidPort(port)) {
                    Toast.makeText(getActivity(), getString(R.string.invalid_port), Toast.LENGTH_SHORT).show();
                    log.warn("onClick: invalid port: " + port);
                    validUri = false;
                } else if (! UriUtils.isValidPath(path)) {
                    Toast.makeText(getActivity(), getString(R.string.invalid_path), Toast.LENGTH_SHORT).show();
                    log.warn("onClick: invalid path: " + path);
                    validUri = false;
                }

                log.debug("onClick: scheme=" + scheme + ", username=" + username + ", domain=" + domain + ", port=" + port + ", remote=" + address + ", path=" + path + "; type=" + type + ", validUri=" + validUri);

                if(validUri){

                    if (port == -1) {
                        port = MetaFile2Factory.defaultPortForProtocol(scheme);
                    }

                    // Store new values to preferences
                    mPreferences.edit()
                            .putInt(NET_LATEST_TYPE, type)
                            .putString(NET_LATEST_ADDRESS, address)
                            .putInt(NET_LATEST_PORT, port)
                            .putString(NET_LATEST_USERNAME, username)
                            .putString(NET_LATEST_DOMAIN, domain)
                            .putString(NET_LATEST_PATH, path)
                            .apply();

                    String uriToBuild = scheme;

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

    @Override
    public void onStart() {
        super.onStart();

        if (mDialog != null) {
            Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
            Button positiveButton = mDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTypeface(customFont);
                Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
                positiveButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                positiveButton.setBackground(ripple);
                positiveButton.setClipToOutline(true);
            }
            Button negativeButton = mDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTypeface(customFont);
                Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
                negativeButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                negativeButton.setBackground(ripple);
                negativeButton.setClipToOutline(true);
            }
        }
    }

    public void setOnConnectClickListener(onConnectClickListener onConnectClick) {
        mOnConnectClick = onConnectClick;
    }
    public void setOnCancelClickListener(OnClickListener onClickListener) {
        mOnCancelClickListener = onClickListener;
    }

}
