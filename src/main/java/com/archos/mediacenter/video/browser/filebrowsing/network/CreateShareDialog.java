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
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.filecoreextension.UriUtils;
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

        View customTitleView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom_title, null);
        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * customTitleView.getContext().getResources().getDisplayMetrics().density);
        customTitleView.setPadding(dpToPx.apply(12),dpToPx.apply(10),dpToPx.apply(16),dpToPx.apply(4));
        TextView titleText = customTitleView.findViewById(R.id.dialog_title);
        titleText.setText(R.string.manually_create_share);
        Typeface customFont = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
        titleText.setTypeface(customFont);
        titleText.setTextSize(24);
        ImageView iconView = customTitleView.findViewById(R.id.dialog_icon);
        iconView.setVisibility(View.GONE);

        Dialog dialog = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme).setCustomTitle(customTitleView)
                .setView(v)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.yes, this).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                AlertDialog ad = (AlertDialog)dialog;
                Typeface typeface = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
                Button positiveButton = ad.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positiveButton != null) {
                    positiveButton.setTypeface(typeface);
                    Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
                    positiveButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                    positiveButton.setBackground(ripple);
                    positiveButton.setClipToOutline(true);
                }
                Button negativeButton = ad.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (negativeButton != null) {
                    negativeButton.setTypeface(typeface);
                    Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
                    negativeButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                    negativeButton.setBackground(ripple);
                    negativeButton.setClipToOutline(true);
                }
            }
        });

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
        if (UriUtils.isValidStringUri(path)) {
            createShortcut(path);
            if (mOnShortcutCreatedListener != null)
                mOnShortcutCreatedListener.onShortcutCreated(path);
        } else {
            Toast.makeText(getActivity(), R.string.ssh_remote_address_error, Toast.LENGTH_SHORT).show();
        }
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
