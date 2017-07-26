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

package com.archos.mediacenter.video.leanback.network.ftp;

import android.content.Intent;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.widget.Toast;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.leanback.network.NetworkRootFragment;
import com.archos.mediacenter.video.leanback.network.NetworkShortcutDetailsFragment;
import com.archos.mediaprovider.NetworkScanner;


public class FtpShortcutDetailsFragment extends NetworkShortcutDetailsFragment {

    private static final String TAG = "FtpShortcutDetailsFragment";



    private static final int ACTION_REINDEX = 0;
    private static final int ACTION_ADD_INDEX = 4;
    
    @Override
    public void addActions(DetailsOverviewRow detailRow){
        detailRow.addAction(new Action(ACTION_OPEN, getResources().getString(R.string.open_indexed_folder)));
        if(ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), mShortcut.getUri().toString())<0) {
            detailRow.addAction(new Action(ACTION_ADD_INDEX, getResources().getString(R.string.add_to_indexed_folders)));
        }
        else
            detailRow.addAction(new Action(ACTION_REINDEX, getResources().getString(R.string.network_reindex)));
        detailRow.addAction(new Action(ACTION_REMOVE, getResources().getString(R.string.remove_from_shortcuts)));
        detailRow.setImageDrawable(getResources().getDrawable(R.drawable.filetype_new_server));
    }

    @Override
    public void onActionClicked(Action action) {
        if (action.getId() == ACTION_ADD_INDEX) {
            if(ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(mShortcut.getName(), mShortcut.getUri().toString()))) {
                Toast.makeText(getActivity(), getString(R.string.indexed_folder_added, mShortcut.getName()), Toast.LENGTH_SHORT).show();
                NetworkScanner.scanVideos(getActivity(), mShortcut.getUri());
            }
            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
            slightlyDelayedFinish();
        }
        else if (action.getId() == ACTION_REMOVE) {
            boolean result = ShortcutDb.STATIC.removeShortcut(mShortcut.getUri())>0;
            if (result) {
                Toast.makeText(getActivity(), getString(R.string.shortcut_removed, mShortcut.getName()), Toast.LENGTH_SHORT).show();
                // Send a delete request to MediaScanner
                NetworkScanner.removeVideos(getActivity(), mShortcut.getUri());
                // set caller result
                getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
            }
            else {
                Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
            }

            slightlyDelayedFinish();
        }
        else super.onActionClicked(action);
    }

}
