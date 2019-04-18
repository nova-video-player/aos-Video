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

import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserByQuery;
import com.archos.mediacenter.video.info.VideoInfoActivity;


public class QueryBrowserActivityVideo extends AppCompatActivity {
    private final static String TAG = "QueryBrowserActivityVideo";
    
    private BrowserByQuery mFragment;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onCreate : intent action=" + action);

        if (Intent.ACTION_VIEW.equals(action)) {
            // this is something we got from the search bar
            Uri uri = intent.getData();
            resumeVideo(uri);
            finish();
            return;
        }

        String filterString = intent.getStringExtra(SearchManager.QUERY);
        Log.d(TAG, "onCreate : filtering string = " + filterString);

        FragmentManager fm = getSupportFragmentManager();
        mFragment = (BrowserByQuery) fm.findFragmentById(android.R.id.content);
        if (mFragment == null) {
            mFragment = new BrowserByQuery();
            Bundle args = getIntent().getExtras();
            if (args == null) {
                args = new Bundle();
            }
            if (getIntent() != null && getIntent().getData() != null) {
                Uri uri = getIntent().getData();
                if ("smb".equalsIgnoreCase(uri.getScheme())) {
                    args.putString("path", getIntent().getData().toString()); //full path for smb files
                }
                else {
                    args.putString("path", getIntent().getData().getPath());
                }
            }
            args.putString("filter_string", filterString);
            mFragment.setArguments(args);
            fm.beginTransaction().add(android.R.id.content, mFragment).commit();
        } else {
            fm.beginTransaction().attach(mFragment).commit();
        }
    }

    private void resumeVideo(Uri uri) {

        VideoInfoActivity.startInstance(this, null, uri, new Long(-1));

    }
}
