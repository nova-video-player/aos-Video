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

package com.archos.mediacenter.video.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.archos.mediacenter.video.R;

import java.io.File;

public class TorrentPathDialogPreference extends Preference{

	
    private View mView;
	public TorrentPathDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);        
    }
	@Override
    public View onCreateView(ViewGroup parent) {
         mView = super.onCreateView(parent);
         refresh();
         return mView;
    }
    public TorrentPathDialogPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle); 
    }
 
    @Override
    public void onClick() {
        if(getOnPreferenceClickListener()==null){
          
            Intent i = new Intent(getContext(), FolderPicker.class);
            Bundle b = new Bundle();
            i.putExtra(FolderPicker.EXTRA_CURRENT_SELECTION, getDefaultDirectory(getSharedPreferences()).getPath());
            i.putExtra(FolderPicker.EXTRA_DIALOG_TITLE, getContext().getString(R.string.torrent_path));
            ((Activity)getContext()).startActivityForResult(i, VideoPreferencesActivity.FOLDER_PICKER_REQUEST_CODE);
        }
    }
    public static File getDefaultDirectory(SharedPreferences pref) {
        // Check if there is one specified in the preferences
        String defaultDirectoryPath = pref.getString(VideoPreferencesFragment.KEY_TORRENT_PATH,null);
        if (defaultDirectoryPath!=null) {
            return new File(defaultDirectoryPath);
        } else {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
    }
	public void refresh() {
		setSummary(getDefaultDirectory(getSharedPreferences()).getAbsolutePath());
	}
   
}
