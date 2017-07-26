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

package com.archos.mediacenter.video.leanback;

import android.app.Fragment;
import android.os.Bundle;

import com.archos.mediacenter.video.R;

/**
 * An activity host a single fullscreen fragment
 * Created by vapillon on 09/06/15.
 */
public abstract class SingleFragmentActivity extends LeanbackActivity {

    public abstract Fragment getFragmentInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.leanback_single_fragment_activity);
    }

    @Override
    public void onResume() {
        super.onResume();

        // The fragment is added only in onResume because we first need to check if the fragment is not already in:
        // In some cases (after a crash for example) the activity is recreated with the fragment already in it (because the
        // fragment state was saved in a Bundle) and we must not add a new fragment in it.

        Fragment existingFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (existingFragment==null) {
            getFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragment_container, getFragmentInstance(), "fragment_"+getFragmentManager().getBackStackEntryCount())//tag is useful when we want to iterate on fragments
                    .commit();
        }
    }
}
