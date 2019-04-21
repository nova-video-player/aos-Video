package com.archos.mediacenter.video.leanback.details;

import android.os.Bundle;
import android.view.KeyEvent;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.LeanbackActivity;

public class ImagePreviewActivity extends LeanbackActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.leanback_image_preview_activity);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            ImagePreviewFragment fragment = (ImagePreviewFragment)getSupportFragmentManager().findFragmentById(R.id.main_browse_fragment);

            return fragment.goLeft();
        }
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            ImagePreviewFragment fragment = (ImagePreviewFragment)getSupportFragmentManager().findFragmentById(R.id.main_browse_fragment);

            return fragment.goRight();
        }
        
        return super.onKeyDown(keyCode, event);
    }
}