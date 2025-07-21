package com.archos.mediacenter.video.info;

import android.view.View;

import androidx.annotation.Nullable;

public class MediaFlag {
    public final String assetPath; // like "videocodec/h264.png"
    public final View.OnClickListener clickListener;

    public MediaFlag(String assetPath, @Nullable View.OnClickListener clickListener) {
        this.assetPath = assetPath;
        this.clickListener = clickListener;
    }
}
