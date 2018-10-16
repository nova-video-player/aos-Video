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

package com.archos.mediacenter.video.cover;

import com.archos.mediacenter.cover.ArtworkFactory;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.VideoStore.Video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;

/**
 * This class is used for Videos that have no ScraperDB representation
 */
public class VideoCover extends BaseVideoCover {

    final static String TAG = "VideoCover";
    final static boolean DBG = false;

    // Layout stuff is done only once for all Cover instances
    //VIDEO
    private static View sDescriptionViewVideo = null;
    private static TextView sVideoFilename = null;
    private static TextView sVideoDuration = null;
    
    private static View sOverlayDescriptionView = null;
	private static TextView sOverlayDescriptionText = null; // in case of overlay description with handle a single line of text

    private final String mTitle;

    public VideoCover(long videoId, String filepath, long durationMs, String title) {
        super(videoId, filepath, durationMs);
        mTitle = title;
    }

	@Override
	public String getCoverID() {
		return computeCoverID(mObjectLibraryId);
	}

	static public String computeCoverID(long libraryId) {
        return "VFI"+libraryId; //Video FIle
    }

	/**
	 * Destroy all cached layout, bitmaps, etc.
	 * Called when the screen size is changed, for example.
	 */
	public static void resetCachedGraphicStuff() {
	    sDescriptionViewVideo = null;
	    sVideoFilename = null;
	    sVideoDuration = null;
	    
	    sOverlayDescriptionView = null;
		sOverlayDescriptionText = null;
	}

    @Override
    public Bitmap getArtwork(ArtworkFactory factory, boolean descriptionOnCover) {
        if (DBG) Log.d(TAG, "getArtwork for " + mFilepath);
        if (factory == null) {
            return null;
        }

        try {
            Bitmap coverBitmap = null;

            coverBitmap = Video.Thumbnails.getThumbnail(factory.getContentResolver(), mObjectLibraryId, Video.Thumbnails.MINI_KIND, factory.getBitmapOptions());
            // Regular thumbnail must not be too large
            final float scaleFactor = THUMBNAIL_SHRINK_FACTOR;

            if (coverBitmap == null) {
            	if(DBG) Log.d(TAG, "Failed to get the video bitmap");
                return null;
            }

            Rect crop = new Rect(0,0,coverBitmap.getWidth(), coverBitmap.getHeight());
            // In case of a landscape art, crop a 4:3 area inside
            if (crop.width() > crop.height()) {
                final float fTargetRatio = 4.0f/3.0f;
                final float fCurrentRatio = (float)crop.width() / (float)crop.height();
                if (fCurrentRatio < fTargetRatio) { // square artwork, need to cut top and bottom
                	if(DBG) Log.d(TAG, "Square-like artwork, need to cut top and bottom");
                    final float newHeight = crop.height() * fCurrentRatio / fTargetRatio;
                    final int halfDiff = (int)((crop.height() - newHeight)/2f); 
                    crop.top += halfDiff;
                    crop.bottom -= halfDiff;
                } else { // very wide artwork, need to cut left and right
                	if(DBG) Log.d(TAG, "Too-wide artwork, need to cut left and right");
                    final int newWidth = (int)(crop.width() * fTargetRatio / fCurrentRatio);
                    final int halfDiff = (int)((crop.width() - newWidth)/2f);
                    crop.left += halfDiff;
                    crop.right -= halfDiff;            		
                }
            }

	        // Description view
	        View descriptionView = null;
	        if (descriptionOnCover) {
	    		if (sOverlayDescriptionView==null) {
	    			inflateOverlayDescriptionLayout(factory);
	    		}
	    		sOverlayDescriptionText.setText(factory.removeFilenameExtension((new File(mFilepath)).getName()));
	        	descriptionView = sOverlayDescriptionView;
	        }
	        
            // Add the shadow effect
            Bitmap shadowedCover = factory.addShadowAndDescription(coverBitmap, descriptionView, crop, scaleFactor, null);
            coverBitmap.recycle();

            return shadowedCover;
        }
        catch (Exception e) {
            Log.e(TAG, "getArtwork: Exception", e);
        } 
        return null;
    }

    static public Bitmap getDefaultArtwork(ArtworkFactory factory) {
        if(DBG) Log.d(TAG, "getDefaultArtwork");
        if (factory == null) {
            return null;
        }

        // Get default bitmap
        final int bitmapid = R.drawable.default_cover_art_video;
        Bitmap coverBitmap = BitmapFactory.decodeResource(factory.getContext().getResources(), bitmapid);;
        Bitmap result = factory.addShadow(coverBitmap, null, 1f, null);
        coverBitmap.recycle();
        return result;
    }

    @Override
    public int getDescriptionWidth() {
        return DESCRIPTION_TEXTURE_WIDTH;
    }

    @Override
    public int getDescriptionHeight() {
        return DESCRIPTION_TEXTURE_HEIGHT;
    }

    @Override
    public Bitmap getDescription( ArtworkFactory factory ) {
        if (factory == null) {
            return null;
        }

        // Inflate the layout for regular video file
        if (sDescriptionViewVideo==null) {
            inflateDescriptionLayoutVideo(factory);
        }
        View view = sDescriptionViewVideo;
        sVideoFilename.setText(mTitle);
        sVideoDuration.setText(MediaUtils.formatTime(mDurationMs));

        // Update the layout setup to take care of the updated text views
        view.measure(View.MeasureSpec.makeMeasureSpec(DESCRIPTION_TEXTURE_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(DESCRIPTION_TEXTURE_HEIGHT, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, DESCRIPTION_TEXTURE_WIDTH, DESCRIPTION_TEXTURE_HEIGHT);

        return factory.createViewBitmap(view, DESCRIPTION_TEXTURE_WIDTH, DESCRIPTION_TEXTURE_HEIGHT);
    }

    /**
     * Inflate the (static) layout used for the Video description texture 
     */
    private static void inflateDescriptionLayoutVideo( ArtworkFactory factory ) {
        if (factory != null) {
            sDescriptionViewVideo = factory.getLayoutInflater().inflate(R.layout.cover_floating_description_video, null);
            sVideoFilename = (TextView)sDescriptionViewVideo.findViewById(R.id.filename);
            sVideoDuration = (TextView)sDescriptionViewVideo.findViewById(R.id.duration);

            sDescriptionViewVideo.setLayoutParams( new FrameLayout.LayoutParams(DESCRIPTION_TEXTURE_WIDTH,DESCRIPTION_TEXTURE_HEIGHT) );
        }
    }
    
	/**
	 * Inflate the (static) layout used for the overlay description texture 
	 */
	private static void inflateOverlayDescriptionLayout( ArtworkFactory factory ) {
        if (factory != null) {
            sOverlayDescriptionView = factory.getLayoutInflater().inflate(R.layout.cover_overlay_description_video, null);
            sOverlayDescriptionText = (TextView)sOverlayDescriptionView.findViewById(R.id.main);
        }
	}
}