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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;

import android.graphics.Typeface;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class VideoPreferencesFragment extends PreferenceFragmentCompat {

    private static final Set<String> CATEGORY_TITLES = new HashSet<>(Arrays.asList(
        "About", "Video", "User Interface", "Subtitles", "Trakt", "Shared folders (SMB)", "Posters & info", "Storage", "Torrent"
        // Add more category titles as needed
    ));

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
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (getParentFragmentManager().findFragmentByTag("androidx.preference.PreferenceFragment.DIALOG") != null) {
            return; // Avoid multiple dialogs
        }

        DialogFragment dialogFragment = null;

        if (preference instanceof ListPreference) {
            dialogFragment = CustomListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        } else if (preference instanceof MultiSelectListPreference) {
            dialogFragment = CustomMultiSelectListPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        }

        if (dialogFragment != null) {
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG");
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Typeface categoryTypeface = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_95blk);
        Typeface titleTypeface = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_75bd);
        Typeface descTypeface = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_55rg);

        RecyclerView recyclerView = (RecyclerView) view.findViewById(androidx.preference.R.id.recycler_view);
        recyclerView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View child) {
                setCustomFonts(child, categoryTypeface, titleTypeface, descTypeface);
            }
            @Override
            public void onChildViewDetachedFromWindow(View view) {}
        });
    }

    private void setCustomFonts(View view, Typeface categoryTypeface, Typeface titleTypeface, Typeface descTypeface) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setCustomFonts(group.getChildAt(i), categoryTypeface, titleTypeface, descTypeface);
            }
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int id = tv.getId();
            if (id == android.R.id.title) {
                if (CATEGORY_TITLES.contains(tv.getText().toString())) {
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.green_accent));
                    tv.setTextSize(20);
                    tv.setTypeface(categoryTypeface);
                }else{
                    tv.setTypeface(titleTypeface);
                    tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                }
                View parent = (View) tv.getParent();
                View grandparent = parent != null ? (View) parent.getParent() : null;
                android.util.Log.d("PrefFont", "Title: '" + tv.getText() + "' Parent: " + (parent != null ? parent.getClass().getName() : "null") + ", Grandparent: " + (grandparent != null ? grandparent.getClass().getName() : "null"));
            } else if (id == android.R.id.summary) {
                tv.setTypeface(descTypeface);
            }
        }
    }
}
