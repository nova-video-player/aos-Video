package com.archos.mediacenter.video.leanback.settings;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;

import com.archos.mediacenter.video.R;

public class VideoSettingsMoreLeanbackFragment extends LeanbackSettingsFragmentCompat {

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

    public static class PrefsFragment extends LeanbackPreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences_more_leanback);
        }
    }
}