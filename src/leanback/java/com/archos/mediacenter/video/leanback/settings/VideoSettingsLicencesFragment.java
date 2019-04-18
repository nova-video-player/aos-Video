package com.archos.mediacenter.video.leanback.settings;

import android.net.Uri;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceDialogFragment;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.WebUtils;

public class VideoSettingsLicencesFragment extends LeanbackSettingsFragment {

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
            addPreferencesFromResource(R.xml.preferences_licences);
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