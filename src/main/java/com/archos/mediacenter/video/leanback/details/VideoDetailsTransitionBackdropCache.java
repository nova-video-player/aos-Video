// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.leanback.details;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transfers the currently displayed backdrop artwork and file identity to the destination details
 * activity without parceling a bitmap through the Intent. Single-use and weakly referenced.
 */
public final class VideoDetailsTransitionBackdropCache {
    private static final AtomicReference<Entry> sEntry = new AtomicReference<>();

    private VideoDetailsTransitionBackdropCache() {
    }

    public static void put(long launchUptimeMs, Drawable drawable, File file) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) {
                sEntry.set(new Entry(launchUptimeMs, bitmap, file));
            }
        }
    }

    public static Entry takeEntry(long launchUptimeMs) {
        Entry entry = sEntry.getAndSet(null);
        return entry != null && entry.launchUptimeMs == launchUptimeMs ? entry : null;
    }

    public static final class Entry {
        public final long launchUptimeMs;
        public final WeakReference<Bitmap> bitmap;
        public final File file;

        Entry(long launchUptimeMs, Bitmap bitmap, File file) {
            this.launchUptimeMs = launchUptimeMs;
            this.bitmap = new WeakReference<>(bitmap);
            this.file = file;
        }

        public Bitmap getBitmap() {
            return bitmap.get();
        }
    }
}
