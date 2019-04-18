package com.archos.mediacenter.video.leanback.settings;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceDialogFragment;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;

import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

public class VideoSettingsFragment extends LeanbackSettingsFragment {

    private PrefsFragment mPrefsFragment;

    @Override
    public void onPreferenceStartInitialScreen() {
        mPrefsFragment = new PrefsFragment();
        startPreferenceFragment(mPrefsFragment);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
        mPrefsFragment = new PrefsFragment();
        final Bundle args = new Bundle(1);
        args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
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

    public static class PrefsFragment extends LeanbackPreferenceFragment {

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