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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import androidx.core.content.ContextCompat;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.util.ArrayList;

/**
 * Created by vapillon on 11/05/15.
 */
public class PresenterUtils {

    static public int getIconResIdFor(MetaFile2 file) {
        if (file.isDirectory()) {
            return R.drawable.filetype_new_folder;
        }

        final String extension = file.getExtension();
        final String mimeType = file.getMimeType();
        if (extension==null || mimeType==null) {
            return R.drawable.filetype_new_generic;
        }
        else if (mimeType.startsWith("video/")) {
            return R.drawable.filetype_new_video;
        }
        // Special case for subtitles (not based on MimeType because these are text files)
        else if (VideoUtils.getSubtitleExtensions().contains(extension)) {
            return R.drawable.filetype_new_subtitles;
        }
        else if (extension.equals("torrent")) {
            return R.drawable.filetype_new_torrent;
        }
        else if (mimeType.startsWith("image/")) {
            return R.drawable.filetype_new_image;
        }
        else if (mimeType.startsWith("audio/")) {
            return R.drawable.filetype_new_audio;
        }
        else if (extension.equals("pdf")) {
            return R.drawable.filetype_new_pdf;
        }
        else if (mimeType.equals("text/html")) {
            return R.drawable.filetype_new_html;
        }
        // TODO special icon for .nfo?
        // TODO special icon for readme?
        else {
            return R.drawable.filetype_new_generic;
        }
    }

    public static Bitmap addWatchedMark(Bitmap bitmap, Context context) {
        Drawable posterDrawable = new BitmapDrawable(context.getResources(), bitmap);
        ArrayList<Drawable> layer = new ArrayList<>();
        layer.add(posterDrawable);
        BitmapDrawable icon = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.watched_icon_corner);
        icon.setGravity(Gravity.TOP | Gravity.RIGHT);
        layer.add(icon);
        return drawableToBitmap(new LayerDrawable(layer.toArray(new Drawable[layer.size()])));
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }
}
