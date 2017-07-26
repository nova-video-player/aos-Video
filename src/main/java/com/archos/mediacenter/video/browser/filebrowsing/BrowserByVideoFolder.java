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
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.archos.mediacenter.video.utils.FolderPicker;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesActivity;

import java.io.File;

public class BrowserByVideoFolder extends BrowserByLocalFolder {

    private static final String TAG = "BrowserByVideoFolder";
    private static final boolean DBG = false;

    private static final int FOLDER_PICKER_REQUEST_CODE = 2011;

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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MainActivity.MENU_CHANGE_FOLDER, Menu.NONE, R.string.menu_change_folder)
                .setIcon(R.drawable.ic_menu_folder)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret;
        switch (item.getItemId()) {
            case MainActivity.MENU_CHANGE_FOLDER:
                Intent i = new Intent(mContext, FolderPicker.class);
                Bundle b = new Bundle();
                i.putExtra(FolderPicker.EXTRA_CURRENT_SELECTION, getDefaultDirectory().getPath());
                i.putExtra(FolderPicker.EXTRA_DIALOG_TITLE, getResources().getString(R.string.menu_change_folder_details));
                startActivityForResult(i, FOLDER_PICKER_REQUEST_CODE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(DBG) Log.d(TAG, "onActivityResult "+requestCode+" "+resultCode);
        if (requestCode == FOLDER_PICKER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String newPath = data.getStringExtra(FolderPicker.EXTRA_SELECTED_FOLDER);
                if(DBG) Log.d(TAG, "FolderPicker returns "+newPath);
                if (newPath!=null) { //better safe than sorry
                    File f = new File(newPath);
                    if ((f!=null) && f.isDirectory() && f.exists()) { //better safe than sorry x3
                        Editor ed = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
                        ed.putString(VideoPreferencesActivity.FOLDER_BROWSING_DEFAULT_FOLDER, f.getPath());
                        ed.commit();
                        // Only the activity is able to correctly update the root folder browser view
                        // (because user may be deep in a folder hierarchy already)
                        MainActivity bav = (MainActivity)getActivity();
                        bav.reloadBrowserByVideoFolder();
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}
