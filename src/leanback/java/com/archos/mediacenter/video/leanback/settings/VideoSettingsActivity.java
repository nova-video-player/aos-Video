package com.archos.mediacenter.video.leanback.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.archos.mediacenter.video.R;

public class VideoSettingsActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_settings);
        overridePendingTransition(R.anim.slide_in_from_right, 0);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // FIXME
        overridePendingTransition(0, R.anim.slide_out_to_right);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(getFragmentManager().findFragmentById(R.id.settingsFragment)!=null){
            ((VideoSettingsFragment)getFragmentManager().findFragmentById(R.id.settingsFragment)).onActivityResult(requestCode, resultCode, data);
        }
    }
}