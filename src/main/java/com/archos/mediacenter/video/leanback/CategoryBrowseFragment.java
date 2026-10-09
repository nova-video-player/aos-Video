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

package com.archos.mediacenter.video.leanback;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.leanback.app.BrowseSupportFragment;

/** Browse rows without Leanback's asynchronous headers back-stack entry. */
public abstract class CategoryBrowseFragment extends BrowseSupportFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // BrowseSupportFragment reads this in its onCreate. With its back-stack support enabled,
        // a header transition posted to pre-draw may use an index after Back has popped the entry.
        setHeadersTransitionOnBackEnabled(false);
        super.onCreate(savedInstanceState);
        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isInHeadersTransition()) return;
                if (!isShowingHeaders()) {
                    startHeadersTransition(true);
                    return;
                }
                setEnabled(false);
                try {
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } finally {
                    setEnabled(true);
                }
            }
        });
    }
}
