package com.archos.mediacenter.video.leanback;

import android.support.v4.app.Fragment;

public class VideosByListActivity extends SingleFragmentActivity {
    @Override
    public Fragment getFragmentInstance() {
        return new VideosByListFragment();
    }

    public void onBackPressed(){
        finish();
    }
}