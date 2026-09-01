// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.leanback.wizard;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.GuidedStepSupportFragment;

import com.archos.mediacenter.video.leanback.LeanbackActivity;

public class SubtitlesWizardActivity extends LeanbackActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GuidedStepSupportFragment fragment = new SubtitlesWizardFragment();
        GuidedStepSupportFragment.addAsRoot(this, fragment, android.R.id.content);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment current = getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                if (current instanceof GuidedStepSupportFragment
                        && ((GuidedStepSupportFragment) current).isSubActionsExpanded()) {
                    ((GuidedStepSupportFragment) current).collapseSubActions();
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }
}
