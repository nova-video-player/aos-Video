package com.archos.mediacenter.video.leanback.settings;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.LeanbackActivity;
import com.archos.mediacenter.video.utils.ThemeManager;

public class VideoSettingsActivity extends LeanbackActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Apply black theme variant for leanback preferences when in black theme
        if (ThemeManager.getInstance(this).isBlackTheme()) {
            setTheme(R.style.MyLeanbackTheme_Preferences_Black);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_settings);
        overridePendingTransition(R.anim.slide_in_from_right, 0);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment fragment = getSupportFragmentManager()
                        .findFragmentById(R.id.settingsFragment);
                if (fragment instanceof LeanbackSettingsFragmentCompat
                        && fragment.getChildFragmentManager().popBackStackImmediate()) {
                    return;
                }
                finish();
                overridePendingTransition(0, R.anim.slide_out_to_right);
            }
        });
    }

    private int getResultCode() {
        // This is a workaround to get the current result code
        // since there's no public API for it
        return 0; // We can't easily get this, so we'll log differently
    }

    @Override
    public void finish() {
        super.finish();
    }

}
