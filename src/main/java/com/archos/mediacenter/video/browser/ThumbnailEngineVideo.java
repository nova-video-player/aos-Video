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

package com.archos.mediacenter.video.browser;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.BitmapUtils;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.utils.ThumbnailRequest;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.Video;
import com.archos.mediascraper.LocalImages;

import java.io.File;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class ThumbnailEngineVideo extends ThumbnailEngine {
	private final static String TAG = "ThumbnailEngineVideo";
	private final static boolean DBG = false;

	/**
	 * Type that the mediaDb will be representing:
	 * a file: a video, a movie or a TV show episode.
	 */
	public final static int TYPE_FILE = 1;

	/**
	 * Type that the mediaDb will be representing:
	 * a tv show (i.e. not a file)
	 */
	public final static int TYPE_TV_SHOW = 2;

	/**
	 * Type that the mediaDb will be representing:
	 * a tv show season (i.e. not a file) // not used (yet?)
	 */
	public final static int TYPE_TV_SHOW_SEASON = 3;

    /**
     * Type that the mediaDb will be representing:
     * a movie genre
     */
    public final static int TYPE_MOVIE_GENRE = 4;

    /**
     * Type that the mediaDb will be representing:
     * a movie genre
     */
    public final static int TYPE_MOVIE_YEAR = 5;

	/**
	 * Thumbnail engine video is a singleton
	 */
	private static ThumbnailEngineVideo sThumbnailEngineVideo = null;

	/**
	 * The type of the thumbnails currently in the pool.
	 * values among TYPE_FILE, TYPE_TV_SHOW, TYPE_TV_SHOW_SEASON, TYPE_MOVIE_GENRE
	 */
	private int mThumbnailsType;

	/**
	 * A collection of layout objects that are used for multi-poster compositions
	 * Goal of this instance is to not allocate these stuff at each poster computation
	 */
	private PosterCompositionStuff mPosterCompositionStuff;

	/**
	 * Get the thumbnail manager instance (it's a singleton)
	 */
	public static synchronized ThumbnailEngineVideo getInstance(Context context) {
		if (sThumbnailEngineVideo == null) {
			sThumbnailEngineVideo = new ThumbnailEngineVideo(context.getApplicationContext());
			sThumbnailEngineVideo.mThumbnailsType = TYPE_FILE; // default, and most common type
		}

		return sThumbnailEngineVideo;
	}

	/**
	 * The type of the thumbnails to compute.
	 * values among TYPE_FILE, TYPE_TV_SHOW, TYPE_TV_SHOW_SEASON, TYPE_MOVIE_YEAR
	 * @param type
	 */
    public void setThumbnailType(int type) {
        if(DBG) Log.d(TAG, "setThumbnailType : " + type);
        if (type != mThumbnailsType) {
            clearThumbnailCache();
        }
        mThumbnailsType = type;
    }

    @Override
    public void setThumbnailSize(int thumbnailWidth, int thumbnailHeight) {
        if(DBG) Log.d(TAG, "setThumbnailSize : " + thumbnailWidth + " "+ thumbnailHeight);
        if (mPosterCompositionStuff==null || thumbnailWidth != mThumbnailWidth || thumbnailHeight != mThumbnailHeight) {
            LayoutInflater li = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mPosterCompositionStuff = new PosterCompositionStuff( li, thumbnailWidth, thumbnailHeight);
        }
        super.setThumbnailSize(thumbnailWidth, thumbnailHeight);
    }

	/**
	 * The result object returned by the ThumbnailEngineVideo
	 * Extends the ThumbnailEngine result by adding the scraper-based description
	 */
	public static class VideoResult extends ThumbnailEngine.Result {

	    /**
	     * The scraper cover for which this result has been computed
	     */
	    private final String mPosterPath;

		public VideoResult(Bitmap thumb, ThumbnailRequestVideo videoRequest) {
			super(thumb);
			mPosterPath = videoRequest.getPosterPath();
		}

		/**
		 * Check if this result is up to date compared to the details of the request
		 * It is not the case if the scraper-related info have been updated
		 */
		@Override
		public boolean needRefresh(ThumbnailRequest request) {
		    // request should always be a video one but better safe than sorry
		    if (request instanceof ThumbnailRequestVideo) {
		        final ThumbnailRequestVideo videoRequest = (ThumbnailRequestVideo)request;
		        // Check if the scraper info have been updated.
		        // If yes this VideoResult is not up to date compared to the request argument.
		        if (!isEquals(mPosterPath, videoRequest.getPosterPath())) {
		            return true;
		        }
		    }
		    return false;
		}

        /** null safe comparison of Objects, where null is equals to null */
        private static boolean isEquals(Object a, Object b) {
            return a == null ? b == null : a.equals(b);
      }
	}

	/**
	 * Private constructor (it's a singleton)
	 */
	private ThumbnailEngineVideo(Context context) {
		super(context);
	}

	/**
	 * The actual processing of the thumbnail
	 * @param dbId: the Media database ID of the item
	 * @return
	 */
	@Override
    protected Result computeThumbnail(ThumbnailRequest request) {
		if(DBG) Log.d(TAG, "computeThumbnail for " + request);

		// Should always be a ThumbnailRequestVideo
		if (request instanceof ThumbnailRequestVideo == false) {
			throw new IllegalArgumentException("computeThumbnail: request should always be a ThumbnailRequestVideo!");
		}

		final ThumbnailRequestVideo videoRequest = (ThumbnailRequestVideo)request;

		// Don't compute anything if mediaDB ID and file are invalid
		final long dbId = videoRequest.getMediaDbId();
		final Uri videoFile = videoRequest.getVideoFile();

		if (dbId < 0 && videoFile == null) {
			if (DBG) Log.d(TAG, "computeThumbnail: dbId & video file invalid! returning null");
			return null;
		}

		String posterPath = videoRequest.getPosterPath();

		// --- Multi-poster case ---
		ArrayList<String> postersPaths = videoRequest.getPostersPaths();
		if (postersPaths!=null) {
		    Bitmap bitmap=null;
		    if (postersPaths.size()>=4) {
		        bitmap = mPosterCompositionStuff.getCompositionFour(
		                BitmapFactory.decodeFile(postersPaths.get(0)),
		                BitmapFactory.decodeFile(postersPaths.get(1)),
		                BitmapFactory.decodeFile(postersPaths.get(2)),
		                BitmapFactory.decodeFile(postersPaths.get(3)));
		        return new VideoResult(bitmap, videoRequest);
		    }
		    else if (postersPaths.size()==3) {
		        bitmap = mPosterCompositionStuff.getCompositionThree(
                        BitmapFactory.decodeFile(postersPaths.get(0)),
                        BitmapFactory.decodeFile(postersPaths.get(1)),
                        BitmapFactory.decodeFile(postersPaths.get(2)));
		        return new VideoResult(bitmap, videoRequest);
            }
		    else if (postersPaths.size()==2) {
		        bitmap = mPosterCompositionStuff.getCompositionTwo(
                        BitmapFactory.decodeFile(postersPaths.get(0)),
                        BitmapFactory.decodeFile(postersPaths.get(1)));
		        return new VideoResult(bitmap, videoRequest);
            }
		    else if (postersPaths.size()==1) {
		        posterPath = postersPaths.get(0);
		        // no return, let's proceed further
		    }
		}

		// 1. try to get the poster from poster path, path may be null
		Bitmap scaledImage = decodeCover(posterPath);

		// 2. check for locally stored images if we know the video file
		if (scaledImage == null && videoFile != null) {
		    if (DBG) Log.d(TAG, "computeThumbnail from local file: " + videoFile);
		    File poster = LocalImages.generateLocalPoster(videoFile, mContext);
		    if (poster != null)
		        scaledImage = decodeCover(poster.getPath());
		}

		// 3. Fallback on the regular thumbnail if there is no cover
        if (scaledImage == null && dbId >= 0) {
            Bitmap thumbnail = VideoStore.Video.Thumbnails.getThumbnail(mContentResolver, dbId, Video.Thumbnails.MINI_KIND, null);

            if (thumbnail != null) {
                // There is a valid MINI_KIND thumbnail in the database
                if(DBG) Log.d(TAG, "Bitmap source : MINI_KIND thumbnail");
                if(DBG) Log.d(TAG, "Destination size=" + mThumbnailHeight + "x" + mThumbnailWidth);

                if (mThumbnailWidth > mThumbnailHeight) {
                    // Horizontal display => resize the thumbnail to best match the display size
                    // so that it fills completely the area (no empty areas)
                	scaledImage = BitmapUtils.scaleThumbnailCenterCrop(thumbnail, mThumbnailWidth, mThumbnailHeight);
                }
                else {
                    // Vertical display => resize the thumbnail to match the display width
                    // and fill the remaining areas above and below with black padding
                	scaledImage = BitmapUtils.scaleThumbnailCenterInside(thumbnail, mThumbnailWidth, mThumbnailHeight);
                }
            }
            else {
                // Failed to get the thumbnail from the database => set this item as empty in the cache
                if (DBG) Log.d(TAG, "Bitmap source : none");
                scaledImage = null;
            }
        }

        return new VideoResult(scaledImage, videoRequest);
	}

	private Bitmap decodeCover(String poster) {
		Bitmap scaledBitmap = null;
		if (poster != null)
			if (poster.length() != 0) {
				if (DBG)
					Log.d(TAG, "decodeCover before for " + poster + " of length " + poster.length());
				Bitmap coverBitmap = BitmapFactory.decodeFile(poster);
				if (DBG) Log.d(TAG, "decodeCover after");
				if (coverBitmap != null) {
					// There is a valid poster
					if (DBG)
						Log.d(TAG, "Destination size=" + mThumbnailWidth + "x" + mThumbnailHeight);
					scaledBitmap = BitmapUtils.scaleThumbnailCenterCrop(coverBitmap, mThumbnailWidth, mThumbnailHeight);
				}
			}
		return scaledBitmap;
	}

	/**
	 * Goal of this class is to not allocate these stuff at each poster computation
	 */
	class PosterCompositionStuff {
	    private final Canvas mCanvas;
	    private final int mWidth;
	    private final int mHeight;

	    private View mCompositionFour;
	    private ImageView mCompositionFour_topLeft;
	    private ImageView mCompositionFour_topRight;
	    private ImageView mCompositionFour_bottomLeft;
	    private ImageView mCompositionFour_bottomRight;

	    private View mCompositionThree;
	    private ImageView mCompositionThree_topLeft;
	    private ImageView mCompositionThree_topRight;
	    private ImageView mCompositionThree_bottom;

	    private View mCompositionTwo;
	    private ImageView mCompositionTwo_left;
	    private ImageView mCompositionTwo_right;

	    public PosterCompositionStuff(LayoutInflater li, int width, int height) {
	        if (DBG) Log.d(TAG, "PosterCompositionStuff "+width+" "+height);

	        mCanvas = new Canvas();
	        mWidth = width;
	        mHeight = height;

	        mCompositionFour  = li.inflate(R.layout.poster_composition_four, null);
	        mCompositionFour_topLeft     = (ImageView)mCompositionFour.findViewById(R.id.top_left);
	        mCompositionFour_topRight    = (ImageView)mCompositionFour.findViewById(R.id.top_right);
	        mCompositionFour_bottomLeft  = (ImageView)mCompositionFour.findViewById(R.id.bottom_left);
	        mCompositionFour_bottomRight = (ImageView)mCompositionFour.findViewById(R.id.bottom_right);
	        mCompositionFour.setLayoutParams( new FrameLayout.LayoutParams(mWidth,mHeight));

	        mCompositionThree = li.inflate(R.layout.poster_composition_three, null);
	        mCompositionThree_topLeft  = (ImageView)mCompositionThree.findViewById(R.id.top_left);
	        mCompositionThree_topRight = (ImageView)mCompositionThree.findViewById(R.id.top_right);
	        mCompositionThree_bottom   = (ImageView)mCompositionThree.findViewById(R.id.bottom);
	        mCompositionThree.setLayoutParams( new FrameLayout.LayoutParams(mWidth,mHeight));

	        mCompositionTwo   = li.inflate(R.layout.poster_composition_two, null);
	        mCompositionTwo_left  = (ImageView)mCompositionTwo.findViewById(R.id.left);
	        mCompositionTwo_right = (ImageView)mCompositionTwo.findViewById(R.id.right);
	        mCompositionTwo.setLayoutParams( new FrameLayout.LayoutParams(mWidth,mHeight));
	    }

	    public Bitmap getCompositionFour(Bitmap topLeft, Bitmap topRight, Bitmap bottomLeft, Bitmap bottomRight) {
	        mCompositionFour_topLeft.setImageBitmap(topLeft);
	        mCompositionFour_topRight.setImageBitmap(topRight);
	        mCompositionFour_bottomLeft.setImageBitmap(bottomLeft);
	        mCompositionFour_bottomRight.setImageBitmap(bottomRight);

	        measureAndLayout(mCompositionFour);
	        return makeBitmap(mCompositionFour);
	    }

	    public Bitmap getCompositionThree(Bitmap topLeft, Bitmap topRight, Bitmap bottom) {
	        mCompositionThree_topLeft.setImageBitmap(topLeft);
	        mCompositionThree_topRight.setImageBitmap(topRight);
	        mCompositionThree_bottom.setImageBitmap(bottom);

	        measureAndLayout(mCompositionThree);
            return makeBitmap(mCompositionThree);
	    }
	    public Bitmap getCompositionTwo(Bitmap left, Bitmap right) {
	        mCompositionTwo_left.setImageBitmap(left);
	        mCompositionTwo_right.setImageBitmap(right);

	        measureAndLayout(mCompositionTwo);
            return makeBitmap(mCompositionTwo);
	    }

	    private void measureAndLayout(View v) {
	        v.measure(View.MeasureSpec.makeMeasureSpec(mWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(mHeight, View.MeasureSpec.EXACTLY));
            v.layout(0, 0, mWidth, mHeight);
	    }

	    private Bitmap makeBitmap(View v) {
	        Bitmap bitmap = Bitmap.createBitmap( mWidth, mHeight, Bitmap.Config.ARGB_8888 );
            mCanvas.setBitmap(bitmap);
            v.draw(mCanvas);
            return bitmap;
	    }
	}
}
