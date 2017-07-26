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

package com.archos.mediacenter.video.utils.credentialsmanager;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.mediacenter.video.R;

public class CredentialsEditorDialog extends DialogFragment {

    private AlertDialog dialog;
    private NetworkCredentialsDatabase.Credential mCredential;
    public static final String CREDENTIAL = "credential";
    private DialogInterface.OnDismissListener mOnDismissListener;
    private OnModifyListener mOnModifyListener;

    public interface OnModifyListener{
        public void onModify();
    }
    public CredentialsEditorDialog(){
    }
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = getArguments();
        if(arg.getSerializable(CREDENTIAL )!=null){
            mCredential = (NetworkCredentialsDatabase.Credential)arg.getSerializable(CREDENTIAL);
        }
        else
            throw new IllegalArgumentException(this.getClass().getCanonicalName()+" needs a "+NetworkCredentialsDatabase.Credential.class.getName()+" as argument");

        LayoutInflater factory = LayoutInflater.from(getActivity());
        final View textEntryView = factory.inflate(com.archos.filecorelibrary.R.layout.samba_password_request, null);
        final EditText usernameET = (EditText) textEntryView.findViewById(com.archos.filecorelibrary.R.id.username_edit);
        usernameET.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        final EditText passwordET = (EditText) textEntryView.findViewById(com.archos.filecorelibrary.R.id.password_edit);
        if (mCredential != null) {
            usernameET.setText(Uri.decode(mCredential.getUsername()));
            passwordET.setText(Uri.decode(mCredential.getPassword()));
        }
        dialog = new AlertDialog.Builder(getActivity())
        .setTitle(R.string.samba_password_request_title)
        .setView(textEntryView)
        .setPositiveButton(getText(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (usernameET.length() > 0) {
                    mCredential.setPassword(passwordET.getText().toString());
                    mCredential.setUsername(usernameET.getText().toString());
                }
                NetworkCredentialsDatabase.getInstance().addCredential(mCredential);
                if(!mCredential.isTemporary())
                    NetworkCredentialsDatabase.getInstance().saveCredential(mCredential);
                mOnModifyListener.onModify();
            }
        })
        .setNeutralButton(R.string.delete, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                NetworkCredentialsDatabase.getInstance().deleteCredential(mCredential.getUriString());
                mOnModifyListener.onModify();
            }
        })
        .setNegativeButton(getText(android.R.string.cancel), null)
        .create();
        dialog.setOnDismissListener(mOnDismissListener);
        return dialog;
    }
    public void setOnModifyListener(OnModifyListener onModifyListener){
        mOnModifyListener = onModifyListener;
    }
}
