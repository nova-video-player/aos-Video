// Copyright 2026 Archos SA
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

package com.archos.mediacenter.video.picasso;

import android.graphics.Bitmap;
import com.squareup.picasso.Transformation;

/**
 * High-fidelity scaling transformation to eliminate aliasing artifacts on MediaTek hardware.
 * Performs multi-step downscaling and enables GPU mipmapping.
 */
public class FidelityTransformation implements Transformation {
    private final int mTargetWidth;
    private final int mTargetHeight;

    public FidelityTransformation(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Target dimensions must be positive: " + width + "x" + height);
        }
        this.mTargetWidth = width;
        this.mTargetHeight = height;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        if (source == null) return null;

        // 1. Handle Aspect Ratio (Center Crop)
        float sourceRatio = (float) source.getWidth() / source.getHeight();
        float targetRatio = (float) mTargetWidth / mTargetHeight;
        
        Bitmap current;
        if (Math.abs(sourceRatio - targetRatio) > 0.001f) {
            int newWidth, newHeight, x, y;
            if (sourceRatio > targetRatio) {
                newWidth = boundedCropDimension(source.getHeight() * targetRatio, source.getWidth());
                newHeight = source.getHeight();
                x = (source.getWidth() - newWidth) / 2;
                y = 0;
            } else {
                newWidth = source.getWidth();
                newHeight = boundedCropDimension(source.getWidth() / targetRatio, source.getHeight());
                x = 0;
                y = (source.getHeight() - newHeight) / 2;
            }

            current = Bitmap.createBitmap(source, x, y, newWidth, newHeight);
            if (current != source) {
                source.recycle();
            }
        } else {
            current = source;
        }
        
        // 2. Adaptive Downscaling
        // Path A: Premium Multi-step SSAA Path (for legacy w780 sources)
        // Path B: Fast Single-pass Path (for optimized w500 sources)
        // We scale by exactly 50% steps if the ratio is large to preserve high-frequency detail.
        while ((long) current.getWidth() > (long) mTargetWidth * 2) {
            int nextW = current.getWidth() / 2;
            int nextH = current.getHeight() / 2;
            if (nextW == 0 || nextH == 0) break;
            
            Bitmap next = Bitmap.createScaledBitmap(current, nextW, nextH, true);
            current.recycle();
            current = next;
        }

        // 3. Final scale to exact target dimensions
        Bitmap finalBitmap;
        if (current.getWidth() != mTargetWidth || current.getHeight() != mTargetHeight) {
            finalBitmap = Bitmap.createScaledBitmap(current, mTargetWidth, mTargetHeight, true);
            current.recycle();
        } else {
            finalBitmap = current;
        }

        // Enable MipMapping so the GPU can perform high-quality filtering in the grid.
        // This is the "secret sauce" for smooth 4K TV scaling.
        finalBitmap.setHasMipMap(true);
        
        return finalBitmap;
    }

    static int boundedCropDimension(float idealDimension, int sourceDimension) {
        return Math.max(1, Math.min(sourceDimension, Math.round(idealDimension)));
    }

    @Override
    public String key() {
        return "fidelity_" + mTargetWidth + "x" + mTargetHeight;
    }
}
