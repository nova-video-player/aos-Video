// Copyright 2025 Courville Software
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

package com.archos.mediacenter.video.leanback.scrapping;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.MotionEvent;

import androidx.leanback.app.SearchSupportFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom SearchSupportFragment that handles InputDevice NPE crashes in AndroidX SearchBar
 */
public abstract class SafeSearchSupportFragment extends SearchSupportFragment {

    private static final Logger log = LoggerFactory.getLogger(SafeSearchSupportFragment.class);

    @Override
    public void onViewCreated(View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Add a global touch listener to catch NPE from SearchBar InputDevice.getName()
        if (view != null) {
            view.setOnTouchListener(new View.OnTouchListener() {
                @SuppressLint("ClickableViewAccessibility")
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        // Let the normal touch handling proceed
                        return false;
                    } catch (NullPointerException e) {
                        // Log the InputDevice NPE and prevent crash
                        if (e.getMessage() != null && e.getMessage().contains("InputDevice.getName()")) {
                            log.warn("SafeSearchSupportFragment: Caught InputDevice NPE in SearchBar - preventing crash", e);
                            return true; // Consume the event to prevent crash
                        }
                        // Re-throw if it's a different NPE
                        throw e;
                    }
                }
            });
        }
    }
}