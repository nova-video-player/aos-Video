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

import android.net.Uri;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.archos.filecorelibrary.MetaFile.FileType;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediascraper.NfoExportService;

public abstract class BrowserByLocalFolder extends BrowserByFolder {

    private static final String TAG = "BrowserByLocalFolder";
    private static final boolean DBG = false;


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterContextMenuInfo info = null;
        try {
            info = (AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
        }

        if (info != null && getFileType(info.position) == FileType.Directory) {
            menu.add(0, R.string.start_auto_scraper_activity, 0,
                    R.string.start_auto_scraper_activity);
            // TODO unhide
            // menu.add(0, R.string.nfo_export_folder, 0, R.string.nfo_export_folder);
        }
    }





    @Override
    public boolean onContextItemSelected(MenuItem item) {
        boolean ret = true;
        int index = item.getItemId();

        switch (index) {
            case R.string.start_auto_scraper_activity: {
                // Search all the videos located in the selected folder or its
                // sub-folders
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                Object obj = mFilesAdapter.getItem(info.position);
                String path;
                if(obj instanceof MetaFile2)
                    path = ((MetaFile2)obj).getUri().toString();
                else
                    path  = ((Video)obj).getFileUri().toString();
                if (path != null) {
                    startOnlineSearchForFolder(path);
                }
                break;
            }

            case R.string.nfo_export_folder: {
                AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
                Object obj = mFilesAdapter.getItem(info.position);
                if (obj instanceof MetaFile2) {
                    NfoExportService.exportDirectory(getActivity(), ((MetaFile2)obj).getUri());
                }
                break;
            }
            default:
                ret = super.onContextItemSelected(item);
                break;
        }

        return ret;
    }

    @Override
    public Uri getUriFromPosition(int position) {
        return Uri.parse(mFilesAdapter.getPath(position));
    }

}
