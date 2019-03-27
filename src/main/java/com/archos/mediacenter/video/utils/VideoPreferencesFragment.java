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

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

public class VideoPreferencesFragment extends PreferenceFragmentCompat {

    private VideoPreferencesCommon mPreferencesCommon = new VideoPreferencesCommon(this);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mPreferencesCommon.onCreatePreferences(savedInstanceState, rootKey);
    }

    @Override
    public void onDestroy() {
        mPreferencesCommon.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mPreferencesCommon.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPreferencesCommon.onActivityResult(requestCode, resultCode, data);
    }
}
