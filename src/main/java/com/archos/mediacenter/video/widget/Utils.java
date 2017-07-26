// Copyright 2017 Archos SA
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

package com.archos.mediacenter.video.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;

import com.archos.mediaprovider.video.VideoStore;

import java.io.IOException;
import java.io.InputStream;


public class Utils {
    private final static String TAG = "Utils";
    private final static boolean DBG = false;

    /**
    * Scale the original image and add a shadow around it so that the shadow fills exactly the
    * display area.
    */
    public static Bitmap scaleThumbnailCenterCrop(Context context, Bitmap src, int dstWidth, int dstHeight) {
        if (DBG) Log.d(TAG, "scaleThumbnailCenterCrop");

        // Create the destination bitmap with the requested size
        if (DBG) Log.d(TAG, "   dst size=" + dstWidth + "x" + dstHeight);
        Bitmap dst = Bitmap.createBitmap(dstWidth, dstHeight, Config.ARGB_8888);        
        dst.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(dst);

        // Get the size and aspect ratio of the source bitmap
        final int srcWidth = src.getWidth();
        final int srcHeight = src.getHeight();
        final float srcAspectRatio = (float) srcWidth / srcHeight;
        if (DBG) Log.d(TAG, "   src size =" + srcWidth + "x" + srcHeight + " => srcAspectRatio=" + srcAspectRatio);

        // Compute the size and aspect ratio of the thumbnail area (destination bitmap)
        float thumbAspectRatio = (float)dstWidth / (float)dstHeight;
        if (DBG) Log.d(TAG, "   thumb size=" + dstWidth + "x" + dstHeight + " => thumbAspectRatio=" + thumbAspectRatio);        

        // The source bitmap and the thumbnail area may have different aspect ratios => we want now
        // to resize the source bitmap so that it is at least as big as the thumbnail area and
        // crop what is outside the thumbnail area.

        // Check if the source bitmap is wider or higher than the expected aspect ratio
        int resizedWidth;
        int resizedHeight;
        if (srcAspectRatio < thumbAspectRatio) {
            // The source bitmap is higher than the display area => make the widths match
            // (the top and bottom parts of the source bitmap will be cropped)
            resizedWidth = srcWidth;
            resizedHeight = (int)(srcWidth / thumbAspectRatio);
        }
        else {
            // The source bitmap is wider than the display area => make the heights match
            // (the left and right parts of the source bitmap ill be cropped)
            resizedWidth = (int)(srcHeight * thumbAspectRatio);
            resizedHeight = srcHeight;
        }
        if (DBG) Log.d(TAG, "   resized src size=" + resizedWidth + "x" + resizedHeight);

        // Compute the offset to apply to the resized source bitmap so that the part of the bitmap
        // which will be drawn is centered inside the bitmap
        int xOffset = 0;
        int yOffset = 0;
        if (resizedWidth < srcWidth) {
            xOffset = (int) ((srcWidth - resizedWidth) / 2);
        }
        if (resizedHeight < srcHeight) {
            yOffset = (int) ((srcHeight - resizedHeight) / 2);
        }
        if (DBG) Log.d(TAG, "   resized src offset : xOffset=" + xOffset + " yOffset=" + yOffset);

        // Draw the source bitmap in the destination bitmap and let the system
        // rescale and crop automatically the bitmap with the computed parameters
        Rect srcRect = new Rect(xOffset, yOffset, srcWidth - xOffset, srcHeight - yOffset);
        Rect thumbRect = new Rect(0, 0, dstWidth, dstHeight);
        canvas.drawBitmap(src, srcRect, thumbRect, new Paint());

        return dst;
    }
} 
