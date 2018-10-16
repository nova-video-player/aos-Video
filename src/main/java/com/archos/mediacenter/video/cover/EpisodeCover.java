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
import com.archos.mediacenter.video.utils.EpisodeInfo;
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
 * This class is used TV Show Episodes
 * It represents a MediaStore "Episode" entry, that at the end may be represented by a ScraperStore cover, if found
 */
public class EpisodeCover extends BaseVideoCover {

	final static String TAG = "EpisodeCover";
	final static boolean DBG = false;

	// Info from MediaDB
	private final long mScraperId;
	//private EpisodeTags mEpisodeTags;
	EpisodeInfo mEpisodeInfo;
	private boolean mScraperInfoHasBeenChecked = false;

	// Layout stuff is done only once for all Cover instances
    //TVSHOW
    private static View sDescriptionViewTVShow = null;
    private static TextView sTVShowName = null;
    private static TextView sTVShowSeasonAndEpisode = null;
    private static TextView sTVShowEpisodeName = null;
    private static TextView sTVShowDuration = null;

	private static View sOverlayDescriptionView = null;
	private static TextView sOverlayDescriptionText = null; // in case of overlay description with handle a single line of text

	public EpisodeCover(long videoId, String filepath, long durationMs, long scraperId) {
		super(videoId, filepath, durationMs);
		if (DBG) Log.d(TAG, "EpisodeCover(" + videoId +"|"+filepath+"|"+durationMs+"|"+scraperId);
        mScraperId = scraperId;
    }

	@Override
	public String getCoverID() {
		return computeCoverID(mObjectLibraryId);
	}

	static public String computeCoverID(long libraryId) {
		return "VEP"+libraryId; //Video EPisode
	}

	/**
	 * Destroy all cached layout, bitmaps, etc.
	 * Called when the screen size is changed, for example.
	 */
	public static void resetCachedGraphicStuff() {
	    sDescriptionViewTVShow = null;
	    sTVShowName = null;
	    sTVShowSeasonAndEpisode = null;
	    sTVShowEpisodeName = null;
	    sTVShowDuration = null;

	    sOverlayDescriptionView = null;
	    sOverlayDescriptionText = null;
	}

	/**
	 * Get more info from the Scraper database, if needed
	 * @return true if data is found
	 */
	private boolean checkScraperInfo( ArtworkFactory factory ) {
		if (! mScraperInfoHasBeenChecked) {
			if (DBG) Log.d(TAG, "checkScraperInfo for " + mScraperId + " / " + mFilepath);
			mEpisodeInfo = new EpisodeInfo( factory.getContentResolver(), mScraperId);
			mScraperInfoHasBeenChecked = true;
		}
		return mEpisodeInfo.isValid();
	}

	@Override
	public Bitmap getArtwork(ArtworkFactory factory, boolean descriptionOnCover) {
		if (DBG) Log.d(TAG, "getArtwork for " + mFilepath);
		try {
			Bitmap coverBitmap = null;
			float scaleFactor = 1.0f;

			if (checkScraperInfo(factory)) {
			    // First try to get the DVD-like art from the Scraper database
			    final File coverFile = mEpisodeInfo.getShowCover();
			    if (coverFile!=null) {
			        final String coverPath = coverFile.getPath();
			        if (DBG) Log.d(TAG, "try to decode coverPath=" + coverPath);
			        coverBitmap = BitmapFactory.decodeFile(coverPath);
			    }
			}

			// Fall-back on the regular video thumbs
			if (coverBitmap == null) {
				if (DBG) Log.d(TAG, "Fall-back on the regular video thumb");
				coverBitmap = Video.Thumbnails.getThumbnail(factory.getContentResolver(), mObjectLibraryId, Video.Thumbnails.MINI_KIND, factory.getBitmapOptions());
				if (DBG) Log.d(TAG, "coverBitmap="+coverBitmap);
				scaleFactor = THUMBNAIL_SHRINK_FACTOR;
			}

			if (coverBitmap == null) {
				Log.d(TAG, "Failed to get the video bitmap");
				return null;
			}

			Rect crop = new Rect(0,0,coverBitmap.getWidth(), coverBitmap.getHeight());
			// In case of a landscape art, crop a 4:3 area inside
			if (crop.width() > crop.height()) {
				final float fTargetRatio = 4.0f/3.0f;
				final float fCurrentRatio = (float)crop.width() / (float)crop.height();
				if (fCurrentRatio < fTargetRatio) { // square artwork, need to cut top and bottom
					Log.d(TAG, "Square-like artwork, need to cut top and bottom");
					final float newHeight = crop.height() * fCurrentRatio / fTargetRatio;
					final int halfDiff = (int)((crop.height() - newHeight)/2f);
					crop.top += halfDiff;
					crop.bottom -= halfDiff;
				} else { // very wide artwork, need to cut left and right
					Log.d(TAG, "Too-wide artwork, need to cut left and right");
					final int newWidth = (int)(crop.width() * fTargetRatio / fCurrentRatio);
					final int halfDiff = (int)((crop.width() - newWidth)/2f);
					crop.left += halfDiff;
					crop.right -= halfDiff;
				}
			}

	        // Description view
	        View descriptionView = null;
	        if (descriptionOnCover && (mEpisodeInfo.isValid())) {
	    		if (sOverlayDescriptionView==null) {
	    			inflateOverlayDescriptionLayout(factory);
	    		}
	    		sOverlayDescriptionText.setText(mEpisodeInfo.getSXEY());
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
		// Get default bitmap
		final int bitmapid = R.drawable.default_cover_art_video_tall;
		Bitmap coverBitmap = BitmapFactory.decodeResource(factory.getContext().getResources(), bitmapid);;
		Bitmap result = factory.addShadow(coverBitmap, null, 1f, null);
		coverBitmap.recycle();
		return result;
	}

	@Override
	public Bitmap getDescription( ArtworkFactory factory ) {
		View view;

        // Inflate the layout in case it has not been inflated yet for this Cover class
        if (sDescriptionViewTVShow==null) {
            inflateDescriptionLayoutTVShow(factory);
        }
        view = sDescriptionViewTVShow;

        if (checkScraperInfo(factory)) {
	        sTVShowName.setText(mEpisodeInfo.getShowTitle());
            sTVShowSeasonAndEpisode.setText(EpisodeInfo.getEpisodeIdentificationString(factory.getContext().getResources(),
                                                                                 mEpisodeInfo.getSeasonNumber(),
                                                                                 mEpisodeInfo.getEpisodeNumber()));
            String episodeNameFormat = factory.getContext().getString(R.string.quotation_format);
	        sTVShowEpisodeName.setText(String.format(episodeNameFormat, mEpisodeInfo.getEpisodeTitle()));
        }
		else {
			// Scraper info not available or not valid => fall-back on filename (this is not a common expected use-case...)
			sTVShowName.setText(factory.removeFilenameExtension((new File(mFilepath)).getName()));
			sTVShowSeasonAndEpisode.setText("-");
			sTVShowEpisodeName.setText("-");
		}

        sTVShowDuration.setText(MediaUtils.formatTime(mDurationMs));

		// Update the layout setup to take care of the updated text views
		view.measure(View.MeasureSpec.makeMeasureSpec(DESCRIPTION_TEXTURE_WIDTH, View.MeasureSpec.EXACTLY),
					 View.MeasureSpec.makeMeasureSpec(DESCRIPTION_TEXTURE_HEIGHT, View.MeasureSpec.AT_MOST));
		view.layout(0, 0, DESCRIPTION_TEXTURE_WIDTH, DESCRIPTION_TEXTURE_HEIGHT);

		return factory.createViewBitmap(view, DESCRIPTION_TEXTURE_WIDTH, DESCRIPTION_TEXTURE_HEIGHT);
	}

    /**
     * Inflate the (static) layout used for the TVShow description texture
     */
    private static void inflateDescriptionLayoutTVShow( ArtworkFactory factory ) {
        sDescriptionViewTVShow = factory.getLayoutInflater().inflate(R.layout.cover_floating_description_episode, null);
        sTVShowName = (TextView)sDescriptionViewTVShow.findViewById(R.id.show_name);
        sTVShowSeasonAndEpisode = (TextView)sDescriptionViewTVShow.findViewById(R.id.season_and_episode);
        sTVShowEpisodeName = (TextView)sDescriptionViewTVShow.findViewById(R.id.episode_name);
        sTVShowDuration = (TextView)sDescriptionViewTVShow.findViewById(R.id.duration);

        sDescriptionViewTVShow.setLayoutParams( new FrameLayout.LayoutParams(DESCRIPTION_TEXTURE_WIDTH,DESCRIPTION_TEXTURE_HEIGHT) );
    }

	/**
	 * Inflate the (static) layout used for the overlay description texture
	 */
	private static void inflateOverlayDescriptionLayout( ArtworkFactory factory ) {
		sOverlayDescriptionView = factory.getLayoutInflater().inflate(R.layout.cover_overlay_description_episode, null);
		sOverlayDescriptionText = (TextView)sOverlayDescriptionView.findViewById(R.id.main);
	}
}
