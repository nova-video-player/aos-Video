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

package com.archos.mediacenter.video.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NovaWebView extends WebView {

    private static final Logger log = LoggerFactory.getLogger(NovaWebView.class);

    static boolean doItOnce = false;

    public void resetDoItOnce() {
        if (log.isDebugEnabled()) log.debug("resetDoItOnce");
        doItOnce = false;
    }

    public boolean requestingFocus = false;  // requestFocus() is executing at the moment

    public NovaWebView(@NonNull Context context) {
        super(context);
    }

    public NovaWebView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NovaWebView(@NonNull Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // only process dpad events cf. #675
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 &&
                (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ||
                        event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN ||
                        event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT ||
                        event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (log.isDebugEnabled()) log.debug("dpad hasFocus()={}", hasFocus());
            // TOFIX: hack to avoid "scroll focus"
            if (!doItOnce) {
                clearFocus();
                requestFocus();
                doItOnce = true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

}
