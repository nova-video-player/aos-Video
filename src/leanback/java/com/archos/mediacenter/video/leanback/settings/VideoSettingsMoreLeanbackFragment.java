package com.archos.mediacenter.video.leanback.settings;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragment;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;

import com.archos.mediacenter.video.R;

public class VideoSettingsMoreLeanbackFragment extends LeanbackSettingsFragment {

    @Override
    public void onPreferenceStartInitialScreen() {
        startPreferenceFragment(new PrefsFragment());
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
        final PrefsFragment f = new PrefsFragment();
        final Bundle args = new Bundle(1);
        args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
        f.setArguments(args);
        startPreferenceFragment(f);
        return true;
    }

    public static class PrefsFragment extends LeanbackPreferenceFragment {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.preferences_more_leanback);
        }
    }
}