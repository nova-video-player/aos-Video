package com.archos.mediacenter.video.leanback.wizard;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.leanback.app.GuidedStepSupportFragment;

import com.archos.mediacenter.video.leanback.LeanbackActivity;

public class SubtitlesWizardActivity extends LeanbackActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GuidedStepSupportFragment fragment = new SubtitlesWizardFragment();
        GuidedStepSupportFragment.addAsRoot(this, fragment, android.R.id.content);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Fragment current = getSupportFragmentManager()
                        .findFragmentById(android.R.id.content);
                if (current instanceof GuidedStepSupportFragment
                        && ((GuidedStepSupportFragment) current).isSubActionsExpanded()) {
                    ((GuidedStepSupportFragment) current).collapseSubActions();
                    return;
                }

                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }
}
