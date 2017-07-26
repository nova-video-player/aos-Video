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

package com.archos.mediacenter.video.info;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;

import com.archos.mediacenter.video.R;

public class VideoInfoScraperActivity extends FragmentActivity {
    public static final String EXTRA_VIDEO = "video";
    public static final String EXTRA_SHOW = "show";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_info_posterandbackdrop);
        Fragment frag;
        if(getIntent().getSerializableExtra(EXTRA_VIDEO)!=null)
            frag = new VideoInfoScraperSearchFragment();
        else
            frag = new VideoInfoShowScraperFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.root,frag)
                .addToBackStack(null).commit();
    }

    @Override
    public void onBackPressed(){
        finish();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

}
