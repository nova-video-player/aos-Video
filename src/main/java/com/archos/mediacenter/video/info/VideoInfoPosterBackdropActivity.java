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
import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.MiscUtils;

public class VideoInfoPosterBackdropActivity extends FragmentActivity {
    public static final  String EXTRA_VIDEO = "extra_video";
    public static final String EXTRA_CHOOSE_BACKDROP = "choose_backdrop";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_info_posterandbackdrop);
        applySystemInsets(findViewById(R.id.root));
        Fragment frag = null;
        if(getIntent().getBooleanExtra(EXTRA_CHOOSE_BACKDROP, false))
            frag = new VideoInfoBackdropChooserFragment();
        else
            frag = new VideoInfoPosterChooserFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.root,frag)
                .commit();
    }

    private void applySystemInsets(View root) {
        MiscUtils.applySystemWindowInsets(root, true, (v, left, top, right, bottom) ->
                v.setPadding(left, top, right, bottom));
    }
}
