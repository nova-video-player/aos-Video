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

package com.archos.mediacenter.video.player.cast;

import android.content.Context;
import android.content.DialogInterface;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.MediaRouteButton;
import android.util.AttributeSet;

import com.archos.mediacenter.video.R;

/**
 * Created by alexandre on 08/11/16.
 */

public class ArchosMediaRouteButton extends MediaRouteButton {
    private boolean mAttachedToWindow;
    private static final String DO_NOT_DISPLAY_WARNING = "do_not_display_chromecast_warning";

    public ArchosMediaRouteButton(Context context) {
        super(context);
    }

    public ArchosMediaRouteButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ArchosMediaRouteButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean showDialog() {
        if (!mAttachedToWindow) return false;
        else if(PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(DO_NOT_DISPLAY_WARNING,false))
           return super.showDialog();
        else {
            showWarningDialog();
            return true;
        }
    }

    private void showWarningDialog() {
        new AlertDialog.Builder(getContext()).setTitle(R.string.cast_warning_title).setMessage(R.string.cast_warning).setPositiveButton(R.string.cast, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PreferenceManager.getDefaultSharedPreferences(getContext()).edit().putBoolean(DO_NOT_DISPLAY_WARNING, true).apply();
                ArchosMediaRouteButton.super.showDialog();
            }
        }).setNegativeButton(android.R.string.cancel,null).show();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
    }

    @Override
    public void onDetachedFromWindow() {
        mAttachedToWindow = false;
        super.onDetachedFromWindow();
    }
}