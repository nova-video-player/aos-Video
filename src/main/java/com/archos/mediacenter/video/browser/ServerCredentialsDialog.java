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

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.video.R;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public abstract class ServerCredentialsDialog extends DialogFragment {

    private static final Logger log = LoggerFactory.getLogger(ServerCredentialsDialog.class);

    private AlertDialog mDialog;
    protected SharedPreferences mPreferences;
    private String mUsername="";
    private String mPassword="";
    private String mDomain="";
    private int mPort=-1;
    private int mType=-1;
    private String mRemote="";
    private onConnectClickListener mOnConnectClick;
    final private static String FTP_LATEST_USERNAME = "FTP_LATEST_USERNAME";
    final private static String FTP_LATEST_URI = "FTP_LATEST_URI";
    final private static String FTP_LATEST_DOMAIN = "FTP_LATEST_DOMAIN";

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
    protected EditText mDomainEt;

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
        if(mUsername.isEmpty()&&mPassword.isEmpty()&&mUri==null&&mDomain.isEmpty()){
            mUsername = mPreferences.getString(FTP_LATEST_USERNAME, "");
            String lastUri = mPreferences.getString(FTP_LATEST_URI, "");
            mDomain = mPreferences.getString(FTP_LATEST_DOMAIN, "");
            if(!lastUri.isEmpty()){
                mUri = Uri.parse(lastUri);
            }
        }

        if(mUri!=null){
            mPort = mUri.getPort();
            mType = UriUtils.getUriType(mUri);
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

        mDomainEt = (EditText)v.findViewById(R.id.domain);

        mTypeSp = (Spinner)v.findViewById(R.id.ssh_spinner);

        // Set custom spinner
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
        mTypeSp.setAdapter(adapter);

        // Calculate widest item width
        int maxWidth = 0;
        View itemView = null;
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < adapter.getCount(); i++) {
            itemView = adapter.getDropDownView(i, null, mTypeSp);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);
            maxWidth = Math.max(maxWidth, itemView.getMeasuredWidth());
        }
        // Set dropdown position and width after layout
        int finalMaxWidth = maxWidth;
        mTypeSp.post(new Runnable() {
            @Override
            public void run() {
                mTypeSp.setDropDownVerticalOffset(mTypeSp.getHeight());
                mTypeSp.setDropDownHorizontalOffset(0);
                mTypeSp.setDropDownWidth(finalMaxWidth); // Add some padding
            }
        });

        // Disable internal spinner item ripple (MOP)
        mTypeSp.setOnTouchListener(new View.OnTouchListener() {
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
                            mTypeSp.postDelayed(() -> {
                                try {
                                    Field popupField = AppCompatSpinner.class.getDeclaredField("mPopup");
                                    popupField.setAccessible(true);
                                    Object popupWindow = popupField.get(mTypeSp);

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

        mTypeSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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
        if (UriUtils.isValidUriType(type)) { // better safe than sorry
            mTypeSp.setSelection(type);
        }
        mAddressEt.setText(mRemote);
        mPathEt.setText(mPath);
        int portInt =  mPort;
        String portString = (portInt!=-1) ? Integer.toString(portInt) : "";
        mPortEt.setText(portString);
        mUsernameEt.setText(mUsername);
        mPasswordEt.setText(mPassword);
        if (UriUtils.requiresDomain(type)) mDomainEt.setText(mDomain);

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
                String username = mUsernameEt.getText().toString();
                final String password = mPasswordEt.getText().toString();
                final String domain = mDomainEt.getText().toString();

                final int type = mTypeSp.getSelectedItemPosition();
                final String address = mAddressEt.getText().toString();
                String path = mPathEt.getText().toString();

                int port = -1;
                if (! mPortEt.getText().toString().isEmpty()) {
                    try {
                        port = Integer.parseInt(mPortEt.getText().toString());
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

                    String uriToBuild = createUri();
                    onConnectClick(username, Uri.parse(uriToBuild), password);
                    if(mSavePassword.isChecked())
                        NetworkCredentialsDatabase.getInstance().saveCredential(new Credential(username, password, uriToBuild, domain,true));
                    else
                        NetworkCredentialsDatabase.getInstance().addCredential(new Credential(username, password, uriToBuild, domain,true));
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

    public abstract void onConnectClick(String username, Uri uri, String password);
    public abstract String createUri();
    public void setOnConnectClickListener(onConnectClickListener onConnectClick) {
        mOnConnectClick = onConnectClick;
    }
    public void setOnCancelClickListener(OnClickListener onClickListener) {
        mOnCancelClickListener = onClickListener;
    }

}
