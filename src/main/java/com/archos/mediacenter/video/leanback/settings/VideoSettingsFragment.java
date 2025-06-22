package com.archos.mediacenter.video.leanback.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;

import com.archos.mediacenter.video.utils.VideoPreferencesActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;

public class VideoSettingsFragment extends LeanbackSettingsFragmentCompat {

    private PrefsFragment mPrefsFragment;
    private ActivityResultLauncher<Intent> mActivityResultLauncher;

    @Override
    public void onPreferenceStartInitialScreen() {
        mActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (mPrefsFragment != null && mPrefsFragment.mPreferencesCommon != null) {
                        Intent data = result.getData();
                        int resultCode = result.getResultCode();
                        int requestCode = VideoPreferencesActivity.FOLDER_PICKER_REQUEST_CODE;
                        mPrefsFragment.mPreferencesCommon.onActivityResult(requestCode, resultCode, data);
                    }
                }
        );
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

    public void launchFolderPicker(Intent intent) {
        mActivityResultLauncher.launch(intent);
    }

    public static class PrefsFragment extends LeanbackPreferenceFragmentCompat {

        VideoPreferencesCommon mPreferencesCommon = new VideoPreferencesCommon(this);

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

    }
}