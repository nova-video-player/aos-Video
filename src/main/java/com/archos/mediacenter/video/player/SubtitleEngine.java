package com.archos.mediacenter.video.player;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
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

    // 3D Hybrid Render Variables
    private Bitmap mSoftBitmap = null;
    private int mUiMode = 0;
    private int mLast2DWidth = 1920;
    private int mLast2DHeight = 1080;

    public SubtitleEngine() {
        // Initialize the native C engine and store its memory pointer
        mNativeEngineHandle = nativeCreate();
    }

    public void release() {
        if (mNativeEngineHandle != 0) {
            nativeDestroy(mNativeEngineHandle);
            mNativeEngineHandle = 0;
        }
        if (mSoftBitmap != null) {
            mSoftBitmap.recycle();
            mSoftBitmap = null;
        }
    }

    // --- TextureView.SurfaceTextureListener Implementation ---

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        log.debug("SubtitleEngine: onSurfaceTextureAvailable {}x{}", width, height);
        mCurrentSurface = new Surface(surfaceTexture);

        // Only attach hardware EGL if we are NOT in 3D mode
        if (mNativeEngineHandle != 0 && !is3DMode()) {
            nativeSurfaceCreated(mNativeEngineHandle, mCurrentSurface);
            nativeSurfaceChanged(mNativeEngineHandle, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        mLast2DWidth = width;
        mLast2DHeight = height;
        if (mNativeEngineHandle != 0 && !is3DMode()) {
            nativeSurfaceChanged(mNativeEngineHandle, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        log.debug("SubtitleEngine: onSurfaceTextureDestroyed");
        if (mNativeEngineHandle != 0 && !is3DMode()) {
            nativeSurfaceDestroyed(mNativeEngineHandle);
        }
        if (mCurrentSurface != null) {
            mCurrentSurface.release();
            mCurrentSurface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        // Ignored. The native OpenGL thread owns the render loop in 2D mode.
    }

    public boolean is3DMode() {
        return (mUiMode & VideoEffect.SBS_MODE) != 0 || (mUiMode & VideoEffect.TB_MODE) != 0;
    }

    // ====================================================================
    // 3D UI MODE & HYBRID RENDERER
    // ====================================================================

    public void setUIMode(int uiMode) {
        boolean was3D = is3DMode();
        mUiMode = uiMode;
        boolean isNow3D = is3DMode();

        int mappedMode = 0;
        if ((uiMode & VideoEffect.SBS_MODE) != 0) mappedMode = 1;
        else if ((uiMode & VideoEffect.TB_MODE) != 0) mappedMode = 2;

        if (mNativeEngineHandle != 0) {
            nativeSetUIMode(mNativeEngineHandle, mappedMode);

            if (isNow3D && !was3D) {
                // Shut down 2D hardware EGL
                nativeSurfaceDestroyed(mNativeEngineHandle);
            } else if (!isNow3D && was3D) {
                // Restore 2D hardware EGL and 2D dimensions
                if (mCurrentSurface != null) nativeSurfaceCreated(mNativeEngineHandle, mCurrentSurface);
                nativeSurfaceChanged(mNativeEngineHandle, mLast2DWidth, mLast2DHeight);
            }
        }
    }

    /**
     * Called synchronously by VideoEffectRenderer.onFrameAvailable()
     * This guarantees subtitle rendering is flawlessly locked to the video frame clock.
     */
     public void draw3DSubtitles(Surface uiSurface, int viewWidth, int viewHeight) {
        if (mNativeEngineHandle == 0 || uiSurface == null || !uiSurface.isValid()) return;
        if (viewWidth <= 0 || viewHeight <= 0) return;

        // Give Libass the FULL physical screen size. No 3D halving!
        if (mSoftBitmap == null || mSoftBitmap.getWidth() != viewWidth || mSoftBitmap.getHeight() != viewHeight) {
            if (mSoftBitmap != null) mSoftBitmap.recycle();
            mSoftBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
            nativeSurfaceChanged(mNativeEngineHandle, viewWidth, viewHeight);
        }

        boolean hasSubs = nativeFillBitmap(mNativeEngineHandle, mSoftBitmap);

        try {
            Canvas c = uiSurface.lockCanvas(null);
            if (c != null) {
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                if (hasSubs) {
                    // Draw the full 1920x1080 image ONCE. The 3D Shader duplicates it!
                    c.drawBitmap(mSoftBitmap, 0, 0, null);
                }
                uiSurface.unlockCanvasAndPost(c);
            }
        } catch (Exception e) {
            log.error("Failed to draw 3D subtitles to Canvas", e);
        }
    }

    public void setFontSize(float pt) { if (mNativeEngineHandle != 0) nativeSetFontSize(mNativeEngineHandle, pt); }
    public void setFontScale(float scale) { if (mNativeEngineHandle != 0) nativeSetFontScale(mNativeEngineHandle, scale); }
    public void setFontFamily(String familyName) { if (mNativeEngineHandle != 0) nativeSetFontFamily(mNativeEngineHandle, familyName); }
    public void setBold(boolean bold) { if (mNativeEngineHandle != 0) nativeSetBold(mNativeEngineHandle, bold); }
    public void setTextColor(int color) { if (mNativeEngineHandle != 0) nativeSetTextColor(mNativeEngineHandle, color); }
    public void setOutlineColor(int color) { if (mNativeEngineHandle != 0) nativeSetOutlineColor(mNativeEngineHandle, color); }
    public void setOutlineWidth(float px) { if (mNativeEngineHandle != 0) nativeSetOutlineWidth(mNativeEngineHandle, px); }
    public void setShadowWidth(float px) { if (mNativeEngineHandle != 0) nativeSetShadowWidth(mNativeEngineHandle, px); }
    public void setShadowColor(int color) { if (mNativeEngineHandle != 0) nativeSetShadowColor(mNativeEngineHandle, color); }
    public void setBackgroundMode(int mode) { if (mNativeEngineHandle != 0) nativeSetBackgroundMode(mNativeEngineHandle, mode); }
    public void setBackgroundColor(int color) { if (mNativeEngineHandle != 0) nativeSetBackgroundColor(mNativeEngineHandle, color); }
    public void setBackgroundOpacity(float opacity) { if (mNativeEngineHandle != 0) nativeSetBackgroundOpacity(mNativeEngineHandle, opacity); }
    public void setVerticalOffset(float pixels) { if (mNativeEngineHandle != 0) nativeSetVerticalOffset(mNativeEngineHandle, pixels); }
    public void setOverrideMode(int mode) { if (mNativeEngineHandle != 0) nativeSetOverrideMode(mNativeEngineHandle, mode); }

    // ====================================================================
    // JNI Bindings (implemented in jni_sub_engine.c)
    // ====================================================================

    /* ── Lifecycle & Surface ── */
    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native void nativeSurfaceCreated(long handle, Surface surface);
    private native void nativeSurfaceChanged(long handle, int width, int height);
    private native void nativeSurfaceDestroyed(long handle);

    // 3D Bridge Hook
    private native boolean nativeFillBitmap(long handle, Bitmap bitmap);
    private native void nativeSetUIMode(long handle, int mode);

    /* ── Typography & Master Control ── */
    private native void nativeSetFontSize(long handle, float pt);
    private native void nativeSetFontScale(long handle, float scale);
    private native void nativeSetFontFamily(long handle, String familyName);
    private native void nativeSetBold(long handle, boolean bold);
    private native void nativeSetTextColor(long handle, int color);

     /* ── Borders, Shadows, and Backgrounds ── */
    private native void nativeSetOutlineColor(long handle, int color);
    private native void nativeSetOutlineWidth(long handle, float px);
    private native void nativeSetShadowWidth(long handle, float px);
    private native void nativeSetShadowColor(long handle, int color);
    private native void nativeSetBackgroundMode(long handle, int mode);
    private native void nativeSetBackgroundColor(long handle, int color);
    private native void nativeSetBackgroundOpacity(long handle, float opacity);

    /* ── Positioning & Overrides ── */
    private native void nativeSetVerticalOffset(long handle, float fraction);
    private native void nativeSetOverrideMode(long handle, int mode);
}
