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

package com.archos.mediacenter.video.browser.filebrowsing.network;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.NetworkScanner;

/**
 * Created by alexandre on 08/06/15.
 */
@SuppressLint("ValidFragment") // XXX
public class CreateShareDialog extends DialogFragment implements DialogInterface.OnClickListener {
    String path;
    EditText  pathEdit;
    Button validationButton;
    protected static final String DEFAULT_PATH = "smb://";
    private OnShortcutCreatedListener mOnShortcutCreatedListener;


    public interface OnShortcutCreatedListener{
        public void onShortcutCreated(String path);
    }



    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View v = getActivity().getLayoutInflater().inflate(R.layout.create_share_dialog, null);
        pathEdit = (EditText) v.findViewById(R.id.edit_sharepath);
        pathEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        Dialog dialog = new AlertDialog.Builder(getActivity()).setTitle(R.string.manually_create_share)
                .setView(v)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.yes, this).create();

        // Put the cursor at the end of "smb://"
        // This must be done after the dialog is created, else it does not work
        pathEdit.setSelection(pathEdit.getText().length());

        return dialog;
    }


        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
        path = pathEdit.getText().toString().trim();
        if (path == null || path.isEmpty() || path.equalsIgnoreCase(DEFAULT_PATH)){
            Toast.makeText(getActivity(), R.string.share_infos_incomplete, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!path.endsWith("/"))
            path = path+"/";
        createShortcut(path);
        if(mOnShortcutCreatedListener!=null)
            mOnShortcutCreatedListener.onShortcutCreated(path);
        dismiss();
    }


    public void setOnShortcutCreatedListener(OnShortcutCreatedListener listener){
        mOnShortcutCreatedListener = listener;
    }
    private void createShortcut(String shortcutPath) {
        // Add the shortcut to the list
        ShortcutDbAdapter.Shortcut shortcut = new ShortcutDbAdapter.Shortcut(FileUtils.getName(Uri.parse(shortcutPath)),shortcutPath);
        ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), shortcut);

        String text = getString(R.string.indexed_folder_added, shortcutPath);
        Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();

        // Send a scan request to MediaScanner
        NetworkScanner.scanVideos(getActivity(), shortcutPath);

        // Update the menu items
        getActivity().invalidateOptionsMenu();
    }

}
