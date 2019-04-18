package com.archos.mediacenter.video.leanback.wizard;

import android.os.Bundle;
import androidx.leanback.app.GuidedStepSupportFragment;

import com.archos.mediacenter.video.leanback.LeanbackActivity;

public class SubtitlesWizardActivity extends LeanbackActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GuidedStepSupportFragment fragment = new SubtitlesWizardFragment();
        GuidedStepSupportFragment.addAsRoot(this, fragment, android.R.id.content);
    }
}