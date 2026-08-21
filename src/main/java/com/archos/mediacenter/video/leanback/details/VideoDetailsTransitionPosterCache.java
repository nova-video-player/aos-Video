// Copyright 2026 Courville Software
package com.archos.mediacenter.video.leanback.details;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transfers the currently displayed card artwork to the details activity without parceling a
 * bitmap through the Intent.  It is deliberately single-use and weakly referenced: the source
 * card/Picasso cache remains the owner, and a process recreation simply falls back to normal
 * poster loading.
 */
public final class VideoDetailsTransitionPosterCache {
    private static final AtomicReference<Entry> sEntry = new AtomicReference<>();

    private VideoDetailsTransitionPosterCache() {
    }

    public static void put(long launchUptimeMs, Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) sEntry.set(new Entry(launchUptimeMs, bitmap));
        }
    }

    public static Bitmap take(long launchUptimeMs) {
        Entry entry = sEntry.getAndSet(null);
        return entry != null && entry.launchUptimeMs == launchUptimeMs ? entry.bitmap.get() : null;
    }

    private static final class Entry {
        final long launchUptimeMs;
        final WeakReference<Bitmap> bitmap;

        Entry(long launchUptimeMs, Bitmap bitmap) {
            this.launchUptimeMs = launchUptimeMs;
            this.bitmap = new WeakReference<>(bitmap);
        }
    }
}
