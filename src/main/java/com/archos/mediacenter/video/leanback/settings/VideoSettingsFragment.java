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
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.utils.ThemeManager;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

public class VideoSettingsFragment extends LeanbackSettingsFragmentCompat {

    private PrefsFragment mPrefsFragment;
    private SharedPreferences.OnSharedPreferenceChangeListener mThemeChangeListener;

    @Override
    public void onPreferenceStartInitialScreen() {
        mPrefsFragment = new PrefsFragment();
        startPreferenceFragment(mPrefsFragment);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        mPrefsFragment = new PrefsFragment();
        final Bundle args = new Bundle(1);
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        mPrefsFragment.setArguments(args);
        startPreferenceFragment(mPrefsFragment);
        return true;
    }

    @Override
    public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Note: Don't apply window theme for leanback preferences
        // The activity uses MyLeanbackTheme.Preferences which has translucent window
        // Applying a background would break the overlay appearance

        // Set up theme change listener - MainActivityLeanback will handle the window theme update
        mThemeChangeListener = (sharedPreferences, key) -> {
            if (key.equals(VideoPreferencesCommon.KEY_APP_THEME)) {
                // Signal to parent that theme changed - it will update its window
                getActivity().setResult(VideoPreferencesCommon.ACTIVITY_RESULT_THEME_CHANGED);
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

        private VideoPreferencesCommon mPreferencesCommon = new VideoPreferencesCommon(this);

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            mPreferencesCommon.onCreatePreferences(savedInstanceState, rootKey);
        }

        @Override
        public void onViewCreated(android.view.View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            // Apply theme background color to the preference list (RecyclerView)
            // This colors only the right-side content area, not the entire window
            final RecyclerView list = getListView();
            if (list != null) {
                list.setBackgroundColor(ThemeManager.getInstance(requireContext()).getLeanbackBackgroundColor());
            }
            // Note: Header color is now handled by the theme (MyLeanbackTheme.Preferences.Black)

            // Fix for #1898: on phones (touch input), tapping a row never gives it Android
            // view focus, so the leanback GridLayoutManager has no focused child to anchor
            // its scroll position to and snaps back to the top on the next layout pass
            // (e.g. after a CheckBoxPreference toggle). On Android TV this does not happen
            // since dpad navigation always keeps a row focused. Requesting focus on the
            // touched row restores that anchor without affecting dpad navigation.
            if (list != null) {
                list.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
                    @Override
                    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            View child = recyclerView.findChildViewUnder(event.getX(), event.getY());
                            if (child != null && !child.hasFocus()) child.requestFocus();
                        }
                        return false;
                    }

                    @Override
                    public void onTouchEvent(RecyclerView recyclerView, MotionEvent event) {}

                    @Override
                    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
                });
            }
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

    }
}
