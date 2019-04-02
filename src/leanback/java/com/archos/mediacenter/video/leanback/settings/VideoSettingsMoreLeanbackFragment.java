package com.archos.mediacenter.video.leanback.settings;

import android.os.Bundle;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.WebUtils;

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