package com.archos.mediacenter.video.leanback;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

public class VideosByListActivity extends SingleFragmentActivity {
    @Override
    public Fragment getFragmentInstance() {
        return new VideosByListFragment();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }
}