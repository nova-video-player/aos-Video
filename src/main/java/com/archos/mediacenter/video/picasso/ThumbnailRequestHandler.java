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

package com.archos.mediacenter.video.picasso;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.archos.mediaprovider.video.VideoStore;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Request;
import com.squareup.picasso.RequestHandler;

import java.io.IOException;

/**
 * Integration of our MediaLib thumbnail stuff into com.squareup.picasso
 * Created by vapillon on 23/04/15.
 */
public class ThumbnailRequestHandler extends RequestHandler {

    private static final String TAG = "ThumbnailRequestHandler";

    public final static String THUMBNAIL_SCHEME = "thumbnail";

    private final Context mContext;

    public ThumbnailRequestHandler(Context context) {
        mContext = context;
    }

    public static final Uri buildUri(long videoId) {
        return Uri.parse(THUMBNAIL_SCHEME+"://"+Long.toString(videoId));
    }
    public static final Uri buildUriNoThumbCreation(long videoId) {
        return buildUri(videoId).buildUpon().appendQueryParameter("nothumbcreation", "1").build();
    }
    @Override
    public boolean canHandleRequest(Request request) {
        return THUMBNAIL_SCHEME.equals(request.uri.getScheme());
    }

    @Override
    public Result load(Request request, int networkPolicy) throws IOException {
        final long videoId = Long.parseLong(request.uri.getHost());
        if (videoId<0) {
            return null;
        }
        Bitmap thumbnail = VideoStore.Video.Thumbnails.getThumbnail(mContext.getContentResolver(), videoId, VideoStore.Video.Thumbnails.MINI_KIND, null, !"1".equals(request.uri.getQueryParameter("nothumbcreation")));
        if (thumbnail==null) {
            return null;
        }
        return new RequestHandler.Result(thumbnail, Picasso.LoadedFrom.DISK);
    }

    private static Bitmap decodeResource(Resources resources, int id, Request data) {
        final BitmapFactory.Options options = createBitmapOptions(data);
        if (requiresInSampleSize(options)) {
            BitmapFactory.decodeResource(resources, id, options);
            calculateInSampleSize(data.targetWidth, data.targetHeight, options, data);
        }
        return BitmapFactory.decodeResource(resources, id, options);
    }

    /**
     * Copy/Paste of com.squareup.picasso.RequestHandler.requiresInSampleSize()...
     */
    static boolean requiresInSampleSize(BitmapFactory.Options options) {
        return options != null && options.inJustDecodeBounds;
    }

    /**
     * Copy/Paste of com.squareup.picasso.RequestHandler.createBitmapOptions()...
     */
    static BitmapFactory.Options createBitmapOptions(Request data) {
        final boolean justBounds = data.hasSize();
        final boolean hasConfig = data.config != null;
        BitmapFactory.Options options = null;
        if (justBounds || hasConfig) {
            options = new BitmapFactory.Options();
            options.inJustDecodeBounds = justBounds;
            if (hasConfig) {
                options.inPreferredConfig = data.config;
            }
        }
        return options;
    }

    /**
     * Copy/Paste of com.squareup.picasso.RequestHandler.calculateInSampleSize()...
     */
    static void calculateInSampleSize(int reqWidth, int reqHeight, BitmapFactory.Options options,
                                      Request request) {
        calculateInSampleSize(reqWidth, reqHeight, options.outWidth, options.outHeight, options,
                request);
    }

    /**
     * Copy/Paste of com.squareup.picasso.RequestHandler.calculateInSampleSize()...
     */
    static void calculateInSampleSize(int reqWidth, int reqHeight, int width, int height,
                                      BitmapFactory.Options options, Request request) {
        int sampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int heightRatio;
            final int widthRatio;
            if (reqHeight == 0) {
                sampleSize = (int) Math.floor((float) width / (float) reqWidth);
            } else if (reqWidth == 0) {
                sampleSize = (int) Math.floor((float) height / (float) reqHeight);
            } else {
                heightRatio = (int) Math.floor((float) height / (float) reqHeight);
                widthRatio = (int) Math.floor((float) width / (float) reqWidth);
                sampleSize = request.centerInside
                        ? Math.max(heightRatio, widthRatio)
                        : Math.min(heightRatio, widthRatio);
            }
        }
        options.inSampleSize = sampleSize;
        options.inJustDecodeBounds = false;
    }
}
