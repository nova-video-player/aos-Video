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

package com.archos.mediacenter.video.leanback.tvshow;

import android.app.Fragment;

import com.archos.mediacenter.video.leanback.SingleFragmentActivity;

/**
 * Created by vapillon on 16/06/15.
 */
public class SeasonActivity extends SingleFragmentActivity {
    @Override
    public Fragment getFragmentInstance() {
        return new SeasonFragment();
    }
}
