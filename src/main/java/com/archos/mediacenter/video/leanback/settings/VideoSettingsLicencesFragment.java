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

package com.archos.mediacenter.video.leanback.settings;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.ThemeManager;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediacenter.video.utils.WebUtils;

public class VideoSettingsLicencesFragment extends LeanbackSettingsFragmentCompat {

    private SharedPreferences.OnSharedPreferenceChangeListener mThemeChangeListener;

    @Override
    public void onPreferenceStartInitialScreen() {
        startPreferenceFragment(new PrefsFragment());
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        final PrefsFragment f = new PrefsFragment();
        final Bundle args = new Bundle(1);
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        f.setArguments(args);
        startPreferenceFragment(f);
        return true;
    }

    @Override
    public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Note: Don't apply window theme for leanback preferences
        // The activity uses MyLeanbackTheme.Preferences which has translucent window
        // Applying a background would break the overlay appearance

        // Set up theme change listener to recreate activity when theme changes
        mThemeChangeListener = (sharedPreferences, key) -> {
            if (key.equals(VideoPreferencesCommon.KEY_APP_THEME)) {
                requireActivity().recreate();
            }
        };
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(mThemeChangeListener);
    }

    @Override
    public void onDestroyView() {
        // Unregister theme change listener
        if (mThemeChangeListener != null) {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .unregisterOnSharedPreferenceChangeListener(mThemeChangeListener);
        }
        super.onDestroyView();
    }

    public static class PrefsFragment extends LeanbackPreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences_licences);
        }

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // Apply theme background color to the preference list (RecyclerView)
            // This colors only the right-side content area, not the entire window
            if (getListView() != null) {
                getListView().setBackgroundColor(ThemeManager.getInstance(requireContext()).getLeanbackBackgroundColor());
            }
            // Note: Header color is now handled by the theme (MyLeanbackTheme.Preferences.Black)
        }

        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            try {
                Uri uri = preference.getIntent().getData();
                if (uri.getScheme().startsWith("http")) {
                    WebUtils.openWebLink(getActivity(), uri.toString());
                    return true;
                }
            }
            catch (NullPointerException npe) {}
    
            return super.onPreferenceTreeClick(preference);
        }
    }
}