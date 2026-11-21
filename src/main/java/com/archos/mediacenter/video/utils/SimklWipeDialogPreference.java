// Copyright 2026 Nova Video Player Project
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

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.simkl.Simkl;

public class SimklWipeDialogPreference extends Preference {

    public SimklWipeDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimklWipeDialogPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onClick() {
        Simkl.wipe(PreferenceManager.getDefaultSharedPreferences(getContext()));
        notifyChanged();
    }
}
