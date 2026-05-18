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


package com.archos.mediacenter.video.browser.filebrowsing;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.MenuProvider;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.utils.FolderPicker;
import com.archos.mediacenter.video.utils.VideoPreferencesActivity;

import java.io.File;

public class BrowserByVideoFolder extends BrowserByLocalFolder {

    private static final String TAG = "BrowserByVideoFolder";
    private static final boolean DBG = false;

    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String newPath = result.getData().getStringExtra(FolderPicker.EXTRA_SELECTED_FOLDER);
                    if (DBG) Log.d(TAG, "FolderPicker returns " + newPath);
                    if (newPath != null) {
                        File f = new File(newPath);
                        if (f.isDirectory() && f.exists()) {
                            Editor ed = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
                            ed.putString(VideoPreferencesActivity.FOLDER_BROWSING_DEFAULT_FOLDER, f.getPath());
                            ed.commit();
                            MainActivity bav = (MainActivity) getActivity();
                            bav.reloadBrowserByVideoFolder();
                        }
                    }
                }
            });

    @Override
    protected Uri getDefaultDirectory() {
        // Check if there is one specified in the preferences
        String defaultDirectoryPath = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(VideoPreferencesActivity.FOLDER_BROWSING_DEFAULT_FOLDER, null);
        if (defaultDirectoryPath!=null) {
            return Uri.parse(defaultDirectoryPath);
        } else {
            return Uri.fromFile(Environment.getExternalStorageDirectory());
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(Menu menu, MenuInflater menuInflater) {
                menu.add(0, MainActivity.MENU_CHANGE_FOLDER, Menu.NONE, R.string.menu_change_folder)
                        .setIcon(R.drawable.ic_menu_folder)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            }
            @Override
            public boolean onMenuItemSelected(MenuItem item) {
                if (item.getItemId() == MainActivity.MENU_CHANGE_FOLDER) {
                    Intent i = new Intent(mContext, FolderPicker.class);
                    i.putExtra(FolderPicker.EXTRA_CURRENT_SELECTION, getDefaultDirectory().getPath());
                    i.putExtra(FolderPicker.EXTRA_DIALOG_TITLE, getResources().getString(R.string.menu_change_folder_details));
                    folderPickerLauncher.launch(i);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner());
    }


}
