package com.archos.mediacenter.video.player;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubtitleEngine implements TextureView.SurfaceTextureListener {

    private static final Logger log = LoggerFactory.getLogger(SubtitleEngine.class);

    // Holds the memory address of the C sub_engine struct
    private long mNativeEngineHandle = 0;
    private Surface mCurrentSurface = null;

    public SubtitleEngine() {
        // Initialize the native C engine and store its memory pointer
        mNativeEngineHandle = nativeCreate();
    }

    public void release() {
        if (mNativeEngineHandle != 0) {
            nativeDestroy(mNativeEngineHandle);
            mNativeEngineHandle = 0;
        }
    }

    // --- TextureView.SurfaceTextureListener Implementation ---

@Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        log.debug("SubtitleEngine: onSurfaceTextureAvailable {}x{}", width, height);
        mCurrentSurface = new Surface(surfaceTexture);

        if (mNativeEngineHandle != 0) {
            // STRICTLY filesv2: Attach first, then immediately size it
            nativeSurfaceCreated(mNativeEngineHandle, mCurrentSurface);
            nativeSurfaceChanged(mNativeEngineHandle, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        log.debug("SubtitleEngine: onSurfaceTextureSizeChanged {}x{}", width, height);
        if (mNativeEngineHandle != 0) {
            nativeSurfaceChanged(mNativeEngineHandle, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        log.debug("SubtitleEngine: onSurfaceTextureDestroyed");
        if (mNativeEngineHandle != 0) {
            nativeSurfaceDestroyed(mNativeEngineHandle);
        }
        if (mCurrentSurface != null) {
            mCurrentSurface.release();
            mCurrentSurface = null;
        }
        return true; // Return true to let the SurfaceTexture be released
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        // Ignored. The native OpenGL thread owns the render loop, not Java.
    }

    // --- JNI Bindings (to be implemented in jni_sub_engine.c) ---
    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native void nativeSurfaceCreated(long handle, Surface surface);
    private native void nativeSurfaceChanged(long handle, int width, int height);
    private native void nativeSurfaceDestroyed(long handle);
}
