package com.archos.mediacenter.video.leanback.settings;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

public class VideoSettingsFragment extends LeanbackSettingsFragmentCompat {

    private PrefsFragment mPrefsFragment;

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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mPrefsFragment != null)
            mPrefsFragment.onActivityResult(requestCode, resultCode, data);
    }

    public static class PrefsFragment extends LeanbackPreferenceFragmentCompat {

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
    }
}