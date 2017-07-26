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

package com.archos.mediacenter.video.leanback.network.rescan;

import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;

import com.archos.mediacenter.video.leanback.LeanbackActivity;

/**
 * Created by vapillon on 22/06/15.
 */
public class RescanActivity extends LeanbackActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState==null) {
            // One hour lost to find out the fucking weirdo way how to do this, i hate you leanback tream!
            GuidedStepFragment firstFragment = new RescanFragment();
            GuidedStepFragment.addAsRoot(this, firstFragment,android.R.id.content);
        }
    }
}
