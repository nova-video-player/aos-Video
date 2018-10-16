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
import com.archos.mediacenter.video.utils.MovieInfo;
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
 * This class is used for Movies
 * It represents a MediaStore "Video" entry, that at the end may be represented by a ScreperDb cover, if found
 */
public class MovieCover extends BaseVideoCover {

	final static String TAG = "MovieCover";
	final static boolean DBG = false;

	// Info from MediaDB & ScraperDB
	private final long mScraperId;
	//private MovieTags mMovieTags;
	private MovieInfo mMovieInfo;
	private boolean mScraperInfoHasBeenChecked = false;

	// Layout stuff is done only once for all Cover instances
	//MOVIE
	private static View sDescriptionViewMovie = null;
	private static TextView sMovieTitle = null;
	private static TextView sMovieDirector = null;
	private static TextView sMovieDuration = null;

	public MovieCover(long videoId, String filepath, long durationMs, long scraperId) {
    	super(videoId, filepath, durationMs);
		if (DBG) Log.d(TAG, "MovieCover(" + videoId +"|"+filepath+"|"+durationMs+"|"+scraperId);
        mScraperId = scraperId;
    }

	@Override
	public String getCoverID() {
		return computeCoverID(mObjectLibraryId);
	}

	static public String computeCoverID(long libraryId) {
		return "VMO"+libraryId; //Video MOvie
	}

	/**
	 * Destroy all cached layout, bitmaps, etc.
	 * Called when the screen size is changed, for example.
	 */
	public static void resetCachedGraphicStuff() {
		sDescriptionViewMovie = null;
		sMovieTitle = null;
		sMovieDirector = null;
		sMovieDuration = null;
	}

	/**
	 * Get more info from the Scraper database, if needed
	 * @return true if data is found
	 */
	private boolean checkScraperInfo( ArtworkFactory factory ) {
	    if (! mScraperInfoHasBeenChecked) {
	        if (DBG) Log.d(TAG, "checkScraperInfo for " + mScraperId + " / " + mFilepath);
	        mMovieInfo = new MovieInfo( factory.getContentResolver(), mScraperId);
	        if (DBG) Log.d(TAG, "mMovieInfo="+mMovieInfo);
	        mScraperInfoHasBeenChecked = true;
	    }
	    return mMovieInfo.isValid();
	}

	@Override
	public Bitmap getArtwork(ArtworkFactory factory, boolean descriptionOnCover) {
		if (DBG) Log.d(TAG, "getArtwork for " + mFilepath);
		try {
			Bitmap coverBitmap = null;
			float scaleFactor = 1.0f;

			if (checkScraperInfo(factory)) {
			    // First try to get the DVD-like art from the Scraper database
			    final File coverFile = mMovieInfo.getCover();
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

			// Add the shadow effect
			Bitmap shadowedCover = factory.addShadow(coverBitmap, crop, scaleFactor, null);
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
		if (sDescriptionViewMovie==null) {
			inflateDescriptionLayoutMovie(factory);
		}
		view = sDescriptionViewMovie;

		if (checkScraperInfo(factory)) {
			sMovieTitle.setText(mMovieInfo.getTitle());

			if ((mMovieInfo.getDirectors()!=null) && mMovieInfo.getDirectors().length()>0) {
				sMovieDirector.setText(mMovieInfo.getDirectors());
			}
			else { //fall-back on year if there is no director info
				sMovieDirector.setText(Integer.toString(mMovieInfo.getYear()));
			}
		}
		else {
			// Scraper info not available or not valid => fall-back on filename (this is not a common expected use-case...)
			sMovieTitle.setText(factory.removeFilenameExtension((new File(mFilepath)).getName()));
			sMovieDirector.setText("-");
		}

		if (mDurationMs!=0) {
			sMovieDuration.setText(MediaUtils.formatTime(mDurationMs));
		} else {
			sMovieDuration.setText("");
		}

		// Update the layout setup to take care of the updated text views
		view.measure(View.MeasureSpec.makeMeasureSpec(DESCRIPTION_TEXTURE_WIDTH, View.MeasureSpec.EXACTLY),
					 View.MeasureSpec.makeMeasureSpec(DESCRIPTION_TEXTURE_HEIGHT, View.MeasureSpec.AT_MOST));
		view.layout(0, 0, DESCRIPTION_TEXTURE_WIDTH, DESCRIPTION_TEXTURE_HEIGHT);

		return factory.createViewBitmap(view, DESCRIPTION_TEXTURE_WIDTH, DESCRIPTION_TEXTURE_HEIGHT);
	}

	/**
	 * Inflate the (static) layout used for the Movie description texture
	 */
	private static void inflateDescriptionLayoutMovie( ArtworkFactory factory ) {
		sDescriptionViewMovie = factory.getLayoutInflater().inflate(R.layout.cover_floating_description_movie, null);
		sMovieTitle = (TextView)sDescriptionViewMovie.findViewById(R.id.movie_title);
		sMovieDirector = (TextView)sDescriptionViewMovie.findViewById(R.id.director);
		sMovieDuration = (TextView)sDescriptionViewMovie.findViewById(R.id.duration);

		sDescriptionViewMovie.setLayoutParams( new FrameLayout.LayoutParams(DESCRIPTION_TEXTURE_WIDTH,DESCRIPTION_TEXTURE_HEIGHT) );
	}
}
