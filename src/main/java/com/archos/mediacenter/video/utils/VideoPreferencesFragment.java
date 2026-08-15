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
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;

import java.util.IdentityHashMap;
import java.util.Locale;

public class VideoPreferencesFragment extends PreferenceFragmentCompat {

    private final IdentityHashMap<Preference, Boolean> mOriginalVisibility = new IdentityHashMap<>();
    private boolean mVisibilitySnapshotted = false;
    private VideoPreferencesCommon mPreferencesCommon = new VideoPreferencesCommon(this);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mPreferencesCommon.onCreatePreferences(savedInstanceState, rootKey);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Adjust padding for edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets systemBarsInsets = insets.getInsets(WindowInsets.Type.systemBars());
                applySearchBoxInsets(view, systemBarsInsets.top);
                v.setPadding(systemBarsInsets.left, 0, systemBarsInsets.right, systemBarsInsets.bottom);
                return insets;
            });
        } else {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                applySearchBoxInsets(view, insets.getSystemWindowInsetTop());
                v.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        0,
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );
                return insets;
            });
        }
        // The search box lives in the Activity layout (sibling of this fragment),
        // so it can only be reached once the view is attached to the window.
        view.post(() -> {
            if (getActivity() == null) return;
            EditText searchEdit = getActivity().findViewById(R.id.preferences_search);
            if (searchEdit != null) {
                searchEdit.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        filterPreferences(s == null ? "" : s.toString());
                    }
                });
            }
        });
    }

    private void applySearchBoxInsets(View fragmentView, int statusBarTop) {
        View root = fragmentView.getRootView();
        if (root == null) return;
        EditText search = root.findViewById(R.id.preferences_search);
        if (search != null) {
            float density = search.getResources().getDisplayMetrics().density;
            int extraV = (int) (8 * density);
            search.setPadding(
                    (int) (12 * density),
                    statusBarTop + extraV,
                    (int) (12 * density),
                    extraV
            );
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mPreferencesCommon.onResume();
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

    private void filterPreferences(String query) {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        if (!mVisibilitySnapshotted) {
            snapshotVisibility(screen);
            mVisibilitySnapshotted = true;
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            restoreVisibility(screen);
            return;
        }
        filterGroup(screen, q);
    }

    private void snapshotVisibility(PreferenceGroup group) {
        mOriginalVisibility.put(group, group.isVisible());
        for (int j = 0; j < group.getPreferenceCount(); j++) {
            Preference child = group.getPreference(j);
            mOriginalVisibility.put(child, child.isVisible());
            if (child instanceof PreferenceGroup) {
                snapshotVisibility((PreferenceGroup) child);
            }
        }
    }

    private void restoreVisibility(PreferenceGroup group) {
        Boolean groupVisible = mOriginalVisibility.get(group);
        if (groupVisible != null) group.setVisible(groupVisible);
        for (int j = 0; j < group.getPreferenceCount(); j++) {
            Preference child = group.getPreference(j);
            Boolean childVisible = mOriginalVisibility.get(child);
            if (childVisible != null) child.setVisible(childVisible);
            if (child instanceof PreferenceGroup) {
                restoreVisibility((PreferenceGroup) child);
            }
        }
    }

    private void filterGroup(PreferenceGroup group, String q) {
        if (matchesQuery(group, q)) {
            group.setVisible(true);
            for (int j = 0; j < group.getPreferenceCount(); j++) {
                Preference child = group.getPreference(j);
                if (child instanceof PreferenceGroup) {
                    restoreVisibility((PreferenceGroup) child);
                } else {
                    Boolean childVisible = mOriginalVisibility.get(child);
                    child.setVisible(childVisible == null || childVisible);
                }
            }
            return;
        }
        boolean anyVisible = false;
        for (int j = 0; j < group.getPreferenceCount(); j++) {
            Preference child = group.getPreference(j);
            if (child instanceof PreferenceGroup) {
                filterGroup((PreferenceGroup) child, q);
                anyVisible |= child.isVisible();
            } else {
                boolean childVisible = matchesQuery(child, q);
                child.setVisible(childVisible);
                anyVisible |= childVisible;
            }
        }
        group.setVisible(anyVisible);
    }

    private boolean matchesQuery(Preference preference, String q) {
        return titleMatches(preference, q) || summaryMatches(preference, q);
    }

    private boolean titleMatches(Preference preference, String q) {
        CharSequence title = preference.getTitle();
        return title != null && title.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    private boolean summaryMatches(Preference preference, String q) {
        CharSequence summary = preference.getSummary();
        return summary != null && summary.toString().toLowerCase(Locale.ROOT).contains(q);
    }
}