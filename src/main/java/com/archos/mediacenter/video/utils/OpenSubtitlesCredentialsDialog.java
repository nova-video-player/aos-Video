// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.archos.mediacenter.video.R;

public class OpenSubtitlesCredentialsDialog extends DialogFragment {

    final public static String OPENSUBTITLES_USERNAME = "OPENSUBTITLES_USERNAME";
    final public static String OPENSUBTITLES_PASSWORD = "OPENSUBTITLES_PASSWORD";

    final public static String USERNAME = "username";
    final public static String PASSWORD = "password";

    private AlertDialog mDialog;
    private SharedPreferences mPreferences;
    private String mUsername = "";
    private String mPassword = "";
    private OnClickListener mOnCancelClickListener;

    public OpenSubtitlesCredentialsDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            mUsername = args.getString(USERNAME, "");
            mPassword = args.getString(PASSWORD, "");
        }

        mPreferences = getContext().getApplicationContext().getSharedPreferences("opensubtitles_credentials", Context.MODE_PRIVATE);

        if(mUsername.isEmpty()) // get previous username if it exists
            mUsername = mPreferences.getString(OPENSUBTITLES_USERNAME, "");

        final View v = getActivity().getLayoutInflater().inflate(R.layout.credential_layout, null);
        final EditText usernameEt = v.findViewById(R.id.username);
        final EditText passwordEt = v.findViewById(R.id.password);
        final CheckBox showPassword = v.findViewById(R.id.show_password_checkbox);
        showPassword.setOnCheckedChangeListener((compoundButton, b) -> {
            if (!b)
                passwordEt.setTransformationMethod(PasswordTransformationMethod.getInstance());
            else
                passwordEt.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        });
        usernameEt.setText(mUsername);
        passwordEt.setText(mPassword);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.dialog_subloader_credentials)
                .setView(v)
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
                    if (mOnCancelClickListener != null)
                        mOnCancelClickListener.onClick(null);
                })
                .setPositiveButton(android.R.string.ok, (dialog, id) -> {
                    if (!usernameEt.getText().toString().isEmpty()) {
                        final String username = usernameEt.getText().toString();
                        final String password = passwordEt.getText().toString();
                        // Store new values to preferences
                        mPreferences.edit()
                                .putString(OPENSUBTITLES_USERNAME, username)
                                .apply();
                        mPreferences.edit()
                                .putString(OPENSUBTITLES_PASSWORD, password)
                                .apply();
                    }
                    if (usernameEt.getText().toString().isEmpty() || passwordEt.getText().toString().isEmpty())
                        Toast.makeText(getActivity(), getString(R.string.dialog_subloader_credentials_empty), Toast.LENGTH_SHORT).show();
                });
        mDialog = builder.create();
        return mDialog;
    }
}
