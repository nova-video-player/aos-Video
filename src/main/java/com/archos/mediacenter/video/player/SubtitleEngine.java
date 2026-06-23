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

    // ====================================================================
    // USER STYLE SETTERS (Routes UI Preferences to the C-Engine)
    // ====================================================================

    public void setFontSize(float pt) { if (mNativeEngineHandle != 0) nativeSetFontSize(mNativeEngineHandle, pt); }
    public void setFontScale(float scale) { if (mNativeEngineHandle != 0) nativeSetFontScale(mNativeEngineHandle, scale); }
    public void setFontFamily(String familyName) { if (mNativeEngineHandle != 0) nativeSetFontFamily(mNativeEngineHandle, familyName); }
    public void setBold(boolean bold) { if (mNativeEngineHandle != 0) nativeSetBold(mNativeEngineHandle, bold); }
    public void setItalic(boolean italic) { if (mNativeEngineHandle != 0) nativeSetItalic(mNativeEngineHandle, italic); }
    public void setTextColor(int color) { if (mNativeEngineHandle != 0) nativeSetTextColor(mNativeEngineHandle, color); }
    public void setOutlineColor(int color) { if (mNativeEngineHandle != 0) nativeSetOutlineColor(mNativeEngineHandle, color); }
    public void setOutlineWidth(float px) { if (mNativeEngineHandle != 0) nativeSetOutlineWidth(mNativeEngineHandle, px); }
    public void setBackgroundEnabled(boolean enabled) { if (mNativeEngineHandle != 0) nativeSetBackgroundEnabled(mNativeEngineHandle, enabled); }
    public void setBackgroundColor(int color) { if (mNativeEngineHandle != 0) nativeSetBackgroundColor(mNativeEngineHandle, color); }
    public void setBackgroundOpacity(float opacity) { if (mNativeEngineHandle != 0) nativeSetBackgroundOpacity(mNativeEngineHandle, opacity); }
    public void setVerticalOffset(float pixels) { if (mNativeEngineHandle != 0) nativeSetVerticalOffset(mNativeEngineHandle, pixels); }
    public void setForceOverride(boolean force) { if (mNativeEngineHandle != 0) nativeSetForceOverride(mNativeEngineHandle, force); }

// --- JNI Bindings (implemented in jni_sub_engine.c) ---
    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native void nativeSurfaceCreated(long handle, Surface surface);
    private native void nativeSurfaceChanged(long handle, int width, int height);
    private native void nativeSurfaceDestroyed(long handle);

    // --- NEW: Style Native Declarations ---
    private native void nativeSetFontSize(long handle, float pt);
    private native void nativeSetFontScale(long handle, float scale);
    private native void nativeSetFontFamily(long handle, String familyName);
    private native void nativeSetBold(long handle, boolean bold);
    private native void nativeSetItalic(long handle, boolean italic);
    private native void nativeSetTextColor(long handle, int color);
    private native void nativeSetOutlineColor(long handle, int color);
    private native void nativeSetOutlineWidth(long handle, float px);
    private native void nativeSetBackgroundEnabled(long handle, boolean enabled);
    private native void nativeSetBackgroundColor(long handle, int color);
    private native void nativeSetBackgroundOpacity(long handle, float opacity);
    private native void nativeSetVerticalOffset(long handle, float fraction);
    private native void nativeSetForceOverride(long handle, boolean force);

}
