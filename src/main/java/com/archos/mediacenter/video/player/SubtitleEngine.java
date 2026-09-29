package com.archos.mediacenter.video.player;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import androidx.annotation.Keep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class SubtitleEngine implements TextureView.SurfaceTextureListener, SurfaceController.VideoBoxListener {

    private static final Logger log = LoggerFactory.getLogger(SubtitleEngine.class);

    // Holds the memory address of the C sub_engine struct
    private long mNativeEngineHandle = 0;
    private Surface mCurrentSurface = null;

    // 3D Hybrid Render Variables
    private Bitmap mSoftBitmap = null;
    private int mUiMode = 0;
    private int mLast2DWidth = 1920;
    private int mLast2DHeight = 1080;

    // Last video box reported by SurfaceController (canvas pixels, relative to the canvas's own
    // top-left). Cached here because the native engine deliberately INVALIDATES its own copy
    // on every surface attach/detach (a box measured against a surface that's gone can't be
    // trusted for the next one -- see sub_engine_attach_surface()), while nothing on the Java
    // side re-runs SurfaceController.updateSurface() just because a TextureView was recreated
    // or the 3D->2D switch re-attached the surface. Without this cache the native box would sit
    // at its "video fills canvas" fallback after those events until the next unrelated layout
    // change. If the new surface really does have different geometry, SurfaceController
    // recomputes and overwrites this through onVideoBoxChanged() as usual.
    // mBoxLock also serializes the native call itself, so a replay racing a fresh
    // setVideoBox() can't deliver the older box last.
    private final Object mBoxLock = new Object();
    private boolean mHasVideoBox = false;
    private int mBoxX, mBoxY, mBoxW, mBoxH;

    // Cached target for the "redraw right now" path used by style setters while paused in
    // 3D mode (see redraw3DIfNeeded() below). Kept in sync every time draw3DSubtitles() runs
    // -- either from the video-frame-driven path (VideoEffectRenderer.onFrameAvailable) or
    // from primeThreeDSurface() (called once up front so a style change made before the
    // very first video frame arrives still has somewhere to redraw onto). volatile because
    // these are written from the SurfaceTexture callback thread and read from the UI thread.
    private volatile Surface mLast3DSurface = null;
    private volatile int mLast3DWidth = 0;
    private volatile int mLast3DHeight = 0;

    // Guards draw3DSubtitles(): it can now be invoked both from the SurfaceTexture callback
    // thread (real video frames) and the UI thread (style setters while paused), and both
    // paths lockCanvas() the same Surface and touch the same mSoftBitmap.
    //
    // release() also takes this lock (see below), which is what makes it safe to tear down
    // the native engine and mSoftBitmap concurrently with either draw path: whichever side
    // gets the lock first either finishes its native call/bitmap use before teardown can
    // start, or (if release() gets there first) observes mReleased and bails out before
    // touching a freed handle or a recycled bitmap.
    private final Object m3DDrawLock = new Object();

    // Set (under m3DDrawLock) at the start of release(), before the native handle is
    // destroyed or mSoftBitmap is recycled. Checked (also under m3DDrawLock) at the top of
    // draw3DSubtitlesInternal() so a draw call that was already blocked waiting for the lock
    // -- or arrives afterwards -- safely no-ops instead of calling into freed native memory
    // or drawing into a recycled Bitmap. volatile so the plain is3DMode()/no-lock early-outs
    // elsewhere (e.g. redraw3DIfNeeded()'s is3DMode() check) still see a fresh value.
    private volatile boolean mReleased = false;

    // What is currently on the 3D overlay Surface, as a PAIR: (which Surface, which subtitle
    // generation). Both are updated together, only after a successful unlockCanvasAndPost(),
    // and the generation is the one nativeFillBitmap() reported for the exact pixels it copied
    // -- never a separate read taken afterwards. -1 is not a valid generation, so it always
    // forces the next pull to produce a real answer. Only ever touched inside m3DDrawLock.
    private long mLastPostedGeneration = -1;
    private Surface mLastPostedSurface = null;

    // --- native -> Java "subtitle content changed" push (3D path, while paused) ---
    // The 3D overlay is only PULLED on video frames and style changes. While paused, a change that
    // originates natively -- a subtitle track switch/close, or a new track's first cue arriving --
    // would never be pulled and the overlay would go stale. The native render thread announces
    // those (see sub_render_change_cb in sub_render_gl.h; it never announces while playing) by
    // calling onNativeSubtitleContentChanged() below, which coalesces them into ONE pending
    // main-thread pull. The pull itself is the ordinary passive one: it asks native what is on
    // screen and answers UNCHANGED / CLEAR / FRAME by generation, so a spurious or duplicate
    // announce costs one cheap call and can never draw anything stale.
    private final Handler mPullHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mPullQueued = new AtomicBoolean(false);
    private final Runnable mPullRunnable = new Runnable() {
        @Override public void run() {
            // Clear BEFORE pulling: an announce that lands during the pull queues a new one, and
            // the pull reads native's newest state anyway -- worst case one redundant UNCHANGED.
            mPullQueued.set(false);
            pull3DSubtitles();
        }
    };

    // Reused out-param for nativeFillBitmap()'s generation (element 0). Only used inside
    // m3DDrawLock, so sharing one array is safe and avoids allocating per video frame.
    private final long[] mFillGeneration = new long[1];

    // nativeFillBitmap() results. MUST match SUB_FILL_RESULT in sub_engine.h.
    private static final int FILL_ERROR     = -1; // nothing decided -- record nothing, retry next time
    private static final int FILL_UNCHANGED = 0;  // what was last posted is still right -- do nothing
    private static final int FILL_CLEAR     = 1;  // nothing to show -- clear the Surface (bitmap untouched)
    private static final int FILL_FRAME     = 2;  // mSoftBitmap holds the frame -- draw it

    public SubtitleEngine() {
        // Initialize the native C engine and store its memory pointer
        mNativeEngineHandle = nativeCreate();
    }

    /**
     * Tears down the native engine and the 3D soft-render bitmap.
     *
     * MUST be called only after the video-frame callback that drives the 3D draw path
     * (VideoEffectRenderer's SurfaceTexture.OnFrameAvailableListener) has been stopped and
     * joined -- see Player.releasePlayer(), which now calls mEffectRenderer.stop() (not just
     * pause()) before this. That alone closes the onFrameAvailable() side of the race.
     *
     * The remaining side is the UI-thread style-setter path (redraw3DIfNeeded()), which isn't
     * a background thread and can't be "joined" the same way. m3DDrawLock below handles that:
     * taking it here means release() cannot run while draw3DSubtitlesInternal() is mid-flight
     * (e.g. inside nativeFillBitmap()/nativeSyncFillBitmap() or drawing to the Canvas), and
     * mReleased, checked first thing inside that same lock on the draw side, stops any call
     * that was queued up behind us -- or arrives after -- from proceeding at all.
     */
    public void release() {
        synchronized (m3DDrawLock) {
            mReleased = true;
            mPullHandler.removeCallbacks(mPullRunnable);
            if (mNativeEngineHandle != 0) {
                nativeDestroy(mNativeEngineHandle);
                mNativeEngineHandle = 0;
            }
            if (mSoftBitmap != null) {
                mSoftBitmap.recycle();
                mSoftBitmap = null;
            }
        }
    }

    // --- TextureView.SurfaceTextureListener Implementation ---

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        log.debug("SubtitleEngine: onSurfaceTextureAvailable {}x{}", width, height);
        mCurrentSurface = new Surface(surfaceTexture);
        mLast2DWidth = width;
        mLast2DHeight = height;

        // Only attach hardware EGL if we are NOT in 3D mode
        if (mNativeEngineHandle != 0 && !is3DMode()) {
            nativeSurfaceCreated(mNativeEngineHandle, mCurrentSurface);
            resendVideoBox(); // attach just invalidated the native box -- restore it
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

    /**
     * Re-pushes the last known 2D surface size down to the native engine. Safe to call
     * unconditionally (e.g. from onResume()): it's a no-op if nothing is attached yet, and
     * simply re-applies the same size if nothing changed. See callers for why this exists.
     */
    public void resyncSurfaceSize() {
        if (mNativeEngineHandle == 0 || is3DMode()) return;
        if (mCurrentSurface == null || !mCurrentSurface.isValid()) return;
        if (mLast2DWidth <= 0 || mLast2DHeight <= 0) return;
        resendVideoBox(); // re-push the whole known geometry, not just the size
        nativeSurfaceChanged(mNativeEngineHandle, mLast2DWidth, mLast2DHeight);
    }

    /**
     * Reports where the video's own on-screen box sits within the subtitle canvas --
     * needed so PGS/VobSub bitmaps (decoded in the video's own pixel space) land correctly
     * whenever the canvas's aspect ratio or size diverges from the video's (letterbox,
     * pillarbox, zoom/crop, stretch). x/y/w/h are in canvas pixels, relative to the
     * canvas's own top-left.
     */
    public void setVideoBox(int x, int y, int w, int h) {
        synchronized (mBoxLock) {
            mBoxX = x; mBoxY = y; mBoxW = w; mBoxH = h;
            mHasVideoBox = true;
            sendVideoBoxLocked();
        }
        // Outside mBoxLock: redraw3DIfNeeded() can take m3DDrawLock, and the two must never nest.
        if (mNativeEngineHandle != 0) redraw3DIfNeeded();
    }

    /**
     * Forwards the cached box to native. Caller holds mBoxLock. This is the ONE place the box
     * is translated between coordinate spaces, so setVideoBox() and resendVideoBox() can't
     * disagree.
     *
     * SurfaceController reports the box relative to the 2D subtitle canvas (mSubtitleView),
     * which -- when use-margins extends it into the letterbox bars -- is taller than the
     * video's own view, so the box's y is (dh - dch)/2 + mMarginTop. In 3D there is no such
     * canvas: the native canvas is the effect overlay bitmap, sized to VideoEffectRenderer's
     * view, which IS the video's own view (dcw x dch, never bar-extended, and sized by
     * SurfaceController to the per-eye aspect). The video therefore fills that canvas
     * exactly, and passing the 2D y through pushes the box below the canvas: GFX (PGS/VobSub)
     * bitmaps get shifted down by the bar height and clipped, and libass only avoids the same
     * fate through its "box doesn't fit -> fill canvas" fallback.
     *
     * So in 3D we send an all-zero box, native's existing "video fills canvas" contract,
     * which sub_engine_poll_frame() resolves against the canvas size current at apply time.
     * That is deliberately NOT (0, 0, mBoxW, mBoxH): the box arrives synchronously from
     * updateSurface() while the canvas follows the overlay bitmap, which is only resized on
     * the next video frame after the view changes (never, while paused), so a numeric box
     * would disagree with the canvas for that whole gap. Zero tracks the canvas by definition.
     */
    private void sendVideoBoxLocked() {
        if (!mHasVideoBox || mNativeEngineHandle == 0) return;
        if (is3DMode()) {
            nativeSetVideoBox(mNativeEngineHandle, 0, 0, 0, 0);
        } else {
            nativeSetVideoBox(mNativeEngineHandle, mBoxX, mBoxY, mBoxW, mBoxH);
        }
    }

    /**
     * Replays the last known video box to the native engine. Call right after anything that
     * attaches a surface (nativeSurfaceCreated) -- attach zeroes the native box, and nothing
     * else would restore it. No-op if SurfaceController hasn't reported a box yet (native is
     * already at its zero fallback in that case).
     */
    private void resendVideoBox() {
        synchronized (mBoxLock) {
            sendVideoBoxLocked();
        }
    }

    // SurfaceController.VideoBoxListener -- see setSubtitleTextureCallback()'s sibling
    // registration of `this` in Player.java for the TextureView.SurfaceTextureListener side.
    @Override
    public void onVideoBoxChanged(int x, int y, int w, int h) {
        setVideoBox(x, y, w, h);
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
                // detach zeroed AND dirtied the native box; the 3D path still needs one, so
                // replay it (translated to the 3D overlay's origin by sendVideoBoxLocked())
                // before the next poll_frame() -- the two coalesce into one apply.
                resendVideoBox();
            } else if (!isNow3D && was3D) {
                // Restore 2D hardware EGL and 2D dimensions
                if (mCurrentSurface != null) {
                    nativeSurfaceCreated(mNativeEngineHandle, mCurrentSurface);
                }
                // Unconditional (not just when a surface exists): the box's coordinate space
                // just changed back from the 3D overlay's to the 2D canvas's, and a cached
                // 2D box was sent as 3D-origin while 3D was active.
                resendVideoBox();
                nativeSurfaceChanged(mNativeEngineHandle, mLast2DWidth, mLast2DHeight);
            }
        }
    }

    /**
     * Called synchronously by VideoEffectRenderer.onFrameAvailable() for every real video
     * frame. This guarantees subtitle rendering is flawlessly locked to the video frame
     * clock. Uses the cheap passive pull (nativeFillBitmap) -- called 30-60x/sec during
     * playback, the render thread has already been ticking on its own independent of us.
     */
     public void draw3DSubtitles(Surface uiSurface, int viewWidth, int viewHeight) {
        draw3DSubtitlesInternal(uiSurface, viewWidth, viewHeight, /*forceSync=*/false);
    }

    /**
     * Registers the Surface/size VideoEffectRenderer will later drive via draw3DSubtitles(),
     * before the first real video frame has necessarily arrived. Lets redraw3DIfNeeded()
     * (called by style setters) work even for a video opened already paused, where
     * onFrameAvailable() may not have fired yet. Superseded by the next real
     * draw3DSubtitles() call as usual. Safe to call from initGLComponents() before playback
     * starts.
     */
    public void primeThreeDSurface(Surface uiSurface, int viewWidth, int viewHeight) {
        if (uiSurface == null || !uiSurface.isValid() || viewWidth <= 0 || viewHeight <= 0) return;
        mLast3DSurface = uiSurface;
        mLast3DWidth = viewWidth;
        mLast3DHeight = viewHeight;
    }

    /**
     * Forces one immediate 3D subtitle redraw using the last Surface/size we know about
     * (see mLast3DSurface above). Style setters call this so a change made while the video
     * is PAUSED -- and therefore not ticking VideoEffectRenderer.onFrameAvailable, the only
     * other caller of draw3DSubtitles() -- still shows up right away instead of waiting for
     * the next real video frame. See the class-level push-vs-pull note on draw3DSubtitles()
     * for the full story. No-ops in 2D mode (the native EGL thread already pushes those
     * instantly) and before any Surface/size is known yet.
     *
     * Queuing a fresh buffer (draw3DSubtitlesInternal, below) is necessary but NOT
     * sufficient on its own: nothing consumes that buffer -- updateTexImage() + the GL
     * composite + the actual screen swap -- while paused, since VideoEffectRenderer's draw
     * loop is normally driven entirely by real video frames. wakeDrawLoop() is the other
     * half: it unblocks that consumer for one pass. See its doc comment for the full story.
     */
    /**
     * Called by the native render thread (via JNI; see jni_sub_engine.c) when subtitle content
     * changed while the player is paused in 3D mode. Runs on that native thread, so it must be
     * quick and MUST NOT take m3DDrawLock: release() holds that lock while nativeDestroy() joins
     * this very thread, so blocking on it here would deadlock. It only flips a flag and posts.
     *
     * @Keep: looked up by name from native code with no Java reference to it, so the shrinker
     * would otherwise strip or rename it. (If it ever is, native logs a warning and the push is
     * simply disabled -- the 3D path falls back to pulling on video frames and style changes.)
     */
    @Keep
    private void onNativeSubtitleContentChanged() {
        if (mReleased) return;
        if (mPullQueued.compareAndSet(false, true)) {
            mPullHandler.post(mPullRunnable);
        }
    }

    // Main-thread side of the push: an ordinary passive pull against the last registered 3D
    // Surface -- exactly what onFrameAvailable() does per video frame, minus the video frame.
    private void pull3DSubtitles() {
        Surface surface = mLast3DSurface;
        if (!is3DMode() || surface == null) return;
        draw3DSubtitlesInternal(surface, mLast3DWidth, mLast3DHeight, /*forceSync=*/false);
    }

    private void redraw3DIfNeeded() {
        if (!is3DMode() || mLast3DSurface == null) return;
        draw3DSubtitlesInternal(mLast3DSurface, mLast3DWidth, mLast3DHeight, /*forceSync=*/true);
        if (Player.sPlayer != null && Player.sPlayer.getEffectRenderer() != null) {
            Player.sPlayer.getEffectRenderer().wakeDrawLoop();
        }
    }

    private void draw3DSubtitlesInternal(Surface uiSurface, int viewWidth, int viewHeight, boolean forceSync) {
        if (mNativeEngineHandle == 0 || uiSurface == null || !uiSurface.isValid()) return;
        if (viewWidth <= 0 || viewHeight <= 0) return;

        mLast3DSurface = uiSurface;
        mLast3DWidth = viewWidth;
        mLast3DHeight = viewHeight;

        synchronized (m3DDrawLock) {
            // Must be re-checked here, under the lock: release() takes this same lock before
            // destroying mNativeEngineHandle and recycling mSoftBitmap, so if release() won
            // the race to acquire it first, mReleased is now true and neither of those is
            // safe to touch any more. The mNativeEngineHandle==0 check above is not enough
            // on its own -- it happens before this lock is acquired, so a release() that
            // starts (and finishes) in between would slip past it undetected.
            if (mReleased) return;

            // Give Libass the FULL physical screen size. No 3D halving!
            if (mSoftBitmap == null || mSoftBitmap.getWidth() != viewWidth || mSoftBitmap.getHeight() != viewHeight) {
                if (mSoftBitmap != null) mSoftBitmap.recycle();
                mSoftBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
                nativeSurfaceChanged(mNativeEngineHandle, viewWidth, viewHeight);
                // The new bitmap is blank, so nothing valid has been posted from it yet.
                // Invalidating here (rather than passing a one-shot flag to this call) also
                // covers the case where the pull or the post below fails: the next call still
                // knows it owes a real draw instead of trusting a generation that no longer
                // describes the bitmap.
                mLastPostedGeneration = -1;
            }

            // A different Surface holds none of our content, whatever generation we last
            // posted to the previous one.
            if (uiSurface != mLastPostedSurface) {
                mLastPostedGeneration = -1;
            }

            // One native call decides everything, atomically with the pixel copy: whether the
            // frame we last posted is still current (UNCHANGED), whether nothing should be
            // showing (CLEAR), or a new frame (FRAME) -- and which generation that answer is.
            //
            // forceSync=true is the style-change path: it forces a fresh render and blocks
            // briefly for the render thread to apply it (closes the race where the setter's
            // wake hasn't been picked up yet), and never answers UNCHANGED because the pixels
            // legitimately differ (new font/color/size) even if the frame's identity doesn't.
            // forceSync=false is the per-video-frame path: no waiting, UNCHANGED is the norm.
            int result = nativeFillBitmap(mNativeEngineHandle, mSoftBitmap,
                                          mLastPostedGeneration, forceSync, mFillGeneration);
            if (result == FILL_UNCHANGED) return;
            if (result != FILL_CLEAR && result != FILL_FRAME) {
                // FILL_ERROR (or an unknown code): no decision was made, so record nothing --
                // in particular do NOT paint an empty overlay, which would look like "clear".
                log.warn("nativeFillBitmap failed ({}); keeping previous overlay", result);
                return;
            }

            try {
                Canvas c = uiSurface.lockCanvas(null);
                if (c != null) {
                    c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                    if (result == FILL_FRAME) {
                        // Draw the full 1920x1080 image ONCE. The 3D Shader duplicates it!
                        int cw = c.getWidth(), ch = c.getHeight();
                        if (cw == mSoftBitmap.getWidth() && ch == mSoftBitmap.getHeight()) {
                            c.drawBitmap(mSoftBitmap, 0, 0, null);
                        } else {
                            // The overlay buffer hasn't caught up with the view size yet (a
                            // resize applies to the next dequeued buffer, so one frame can
                            // still be the old size). Drawing 1:1 would clip or leave the
                            // bitmap in a corner, and the composite stretches the whole buffer
                            // over the view anyway -- so scale the bitmap to the buffer and
                            // the on-screen result keeps the right proportions.
                            c.drawBitmap(mSoftBitmap, null, new Rect(0, 0, cw, ch), null);
                        }
                    }
                    uiSurface.unlockCanvasAndPost(c);
                    // Record the generation native reported for the pixels we just copied --
                    // not a fresh read, which could already describe a newer frame.
                    mLastPostedGeneration = mFillGeneration[0];
                    mLastPostedSurface = uiSurface;
                }
            } catch (Exception e) {
                log.error("Failed to draw 3D subtitles to Canvas", e);
            }
        }
    }

    // Each of these calls redraw3DIfNeeded() after the native setter so a change made while
    // paused in 3D mode shows up immediately instead of waiting for the next real video
    // frame (see redraw3DIfNeeded()'s doc comment). It's a no-op in 2D mode or while playing,
    // so this costs nothing outside the specific paused+3D case it's fixing.
    private native void nativeSetVideoBox(long handle, int x, int y, int w, int h);
    public void setFontSize(float pt) { if (mNativeEngineHandle != 0) { nativeSetFontSize(mNativeEngineHandle, pt); redraw3DIfNeeded(); } }
    public void setFontScale(float scale) { if (mNativeEngineHandle != 0) { nativeSetFontScale(mNativeEngineHandle, scale); redraw3DIfNeeded(); } }
    public void setFontFamily(String familyName) { if (mNativeEngineHandle != 0) { nativeSetFontFamily(mNativeEngineHandle, familyName); redraw3DIfNeeded(); } }
    public void setBold(boolean bold) { if (mNativeEngineHandle != 0) { nativeSetBold(mNativeEngineHandle, bold); redraw3DIfNeeded(); } }
    public void setTextColor(int color) { if (mNativeEngineHandle != 0) { nativeSetTextColor(mNativeEngineHandle, color); redraw3DIfNeeded(); } }
    public void setOutlineColor(int color) { if (mNativeEngineHandle != 0) { nativeSetOutlineColor(mNativeEngineHandle, color); redraw3DIfNeeded(); } }
    public void setOutlineWidth(float px) { if (mNativeEngineHandle != 0) { nativeSetOutlineWidth(mNativeEngineHandle, px); redraw3DIfNeeded(); } }
    public void setShadowWidth(float px) { if (mNativeEngineHandle != 0) { nativeSetShadowWidth(mNativeEngineHandle, px); redraw3DIfNeeded(); } }
    public void setShadowColor(int color) { if (mNativeEngineHandle != 0) { nativeSetShadowColor(mNativeEngineHandle, color); redraw3DIfNeeded(); } }
    public void setBackgroundMode(int mode) { if (mNativeEngineHandle != 0) { nativeSetBackgroundMode(mNativeEngineHandle, mode); redraw3DIfNeeded(); } }
    public void setBackgroundColor(int color) { if (mNativeEngineHandle != 0) { nativeSetBackgroundColor(mNativeEngineHandle, color); redraw3DIfNeeded(); } }
    public void setBackgroundOpacity(float opacity) { if (mNativeEngineHandle != 0) { nativeSetBackgroundOpacity(mNativeEngineHandle, opacity); redraw3DIfNeeded(); } }
    public void setVerticalOffset(float pixels) { if (mNativeEngineHandle != 0) { nativeSetVerticalOffset(mNativeEngineHandle, pixels); redraw3DIfNeeded(); } }
    public void setOverrideMode(int mode) { if (mNativeEngineHandle != 0) { nativeSetOverrideMode(mNativeEngineHandle, mode); redraw3DIfNeeded(); } }

    /**
     * Points the native engine at a custom fonts folder (MX Player / mpv-android style
     * "third fonts folder"): every .ttf/.otf/.ttc file in {@code dirPath} is registered with
     * libass, checked BEFORE the system fontconfig database when resolving a font by name.
     * Takes effect starting with the next track opened (nativeCreate()d tracks already
     * playing are unaffected until the next open_track()) -- call this before starting
     * playback, or force a re-open (e.g. seek/track switch) for it to take effect immediately.
     * Pass null or empty to clear/disable.
     */
    public void setFontsFolder(String dirPath) {
        if (mNativeEngineHandle != 0) nativeSetFontsFolder(mNativeEngineHandle, dirPath);
    }

    /**
     * Sets the fallback family name libass uses when nothing else names a font -- most
     * importantly, this is what SRT/plain-text subtitles render with, since they carry no
     * font information of their own. Pass a family name that resolves against whatever
     * folder was last set via {@link #setFontsFolder(String)} (typically one of the files
     * in that folder), or null/empty to fall back to the generic "sans-serif" fontconfig alias.
     */
    public void setDefaultFontName(String familyName) {
        if (mNativeEngineHandle != 0) nativeSetDefaultFontName(mNativeEngineHandle, familyName);
    }

    // ====================================================================
    // JNI Bindings (implemented in jni_sub_engine.c)
    // ====================================================================

    /* ── Lifecycle & Surface ── */
    private native long nativeCreate();
    private native void nativeDestroy(long handle);
    private native void nativeSurfaceCreated(long handle, Surface surface);
    private native void nativeSurfaceChanged(long handle, int width, int height);
    private native void nativeSurfaceDestroyed(long handle);


    // 3D Bridge Hook -- the single pull for the CPU-blend path. Returns FILL_* and reports,
    // in outGeneration[0], the generation of exactly what that result describes. lastGeneration
    // is what the caller last posted (-1 = nothing valid). sync=true is the forced,
    // wait-for-render variant used only by redraw3DIfNeeded()'s style-change path. See
    // jni_sub_engine.c for the full contract.
    private native int nativeFillBitmap(long handle, Bitmap bitmap, long lastGeneration,
                                        boolean sync, long[] outGeneration);
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

    /* ── Custom Fonts Folder (third-party fonts dir, MX Player / mpv-android style) ── */
    private native void nativeSetFontsFolder(long handle, String dirPath);
    private native void nativeSetDefaultFontName(long handle, String familyName);
}
