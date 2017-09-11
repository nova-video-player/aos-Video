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
import com.archos.mediacenter.cover.Cover;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserListOfSeasons;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.browser.BrowserActivity;
import com.archos.mediacenter.utils.InfoDialog;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Locale;

/**
 * This class is used TV Show
 * It represents a MediaStore "Show" entry
 */
public class TvShowCover extends Cover {

    final static String TAG = "TvShowCover";
    final static boolean DBG = false;

    protected final static int DESCRIPTION_TEXTURE_WIDTH = 256;
    protected final static int DESCRIPTION_TEXTURE_HEIGHT = 128;

    // Info from MediaDB
    private final long mScraperId; // Show ID in the ScraperStore
    private String mName;          //Name of the Show
    private int mNumberOfEpisodes; // Number of episodes for this show
    private String mPosterPath;    //URI of the Poster
    private int mNumberOfSeasons; // Number of seasonss for this show
    private int mNumberOfSingleSeason; // Number of single season for this show
    private String mAnyEpisodeFilePath;

    // Layout stuff is done only once for all Cover instances
    //TVSHOW
    private static View sDescriptionViewTVShow = null;
    private static TextView sTVShowName = null;
    private static TextView sNumberOfSeasonsOrEpisodes = null;
    /** Formatting optimization to avoid creating many temporary objects. */
    private static StringBuilder sFormatBuilder;
    /** Formatting optimization to avoid creating many temporary objects. */
    private static Formatter sFormatter;

    public TvShowCover(long id, String name, int numberOfEpisodes, String poster, int numberOfSeasons, int numberOfSingleSeason, String anyEpisodeFilePath) {
        super();
        if (DBG) Log.d(TAG, "TvShowCover(" + id +"|"+name+"|"+poster+")");
        mScraperId = id; // We don't set mObjectLibraryId because this Cover doesn't represent an item of the MediaDb
        mName = name;
        mNumberOfEpisodes = numberOfEpisodes;
        mPosterPath = poster;
        mNumberOfSeasons = numberOfSeasons;
        mNumberOfSingleSeason = numberOfSingleSeason;
        mAnyEpisodeFilePath = anyEpisodeFilePath;
    }

    @Override
    public String getCoverID() {
        return computeCoverIDwithNumberOfEpisode(mScraperId, mNumberOfEpisodes);
    }

    static public String computeCoverID(long libraryId) {
        return "TVS"+libraryId; //TV Show
    }

    /**
     * Special version for TvShowCover, also containing the number of episodes
     */
    static public String computeCoverIDwithNumberOfEpisode(long libraryId, int NumberOfEpisodes) {
        return "TVS"+libraryId+"_"+NumberOfEpisodes; //TV Show
    }

    /**
     * Destroy all cached layout, bitmaps, etc.
     * Called when the screen size is changed, for example.
     */
    public static void resetCachedGraphicStuff() {
        sDescriptionViewTVShow = null;
        sTVShowName = null;
        sNumberOfSeasonsOrEpisodes = null;
        sFormatBuilder = null;
        sFormatter = null;
    }

    @Override
    public Bitmap getArtwork(ArtworkFactory factory, boolean descriptionOnCover) {
        if (DBG) Log.d(TAG, "getArtwork for " + mScraperId);
        try {
            Bitmap coverBitmap = null;
            float scaleFactor = 1.0f;

            // get the DVD-like art from the Scraper database
            if (mPosterPath!=null) {
                if (DBG) Log.d(TAG, "try to decode mPosterPath=" + mPosterPath);
                coverBitmap = BitmapFactory.decodeFile(mPosterPath);
            }

            if (coverBitmap == null) {
                Log.d(TAG, "Failed to get the poster bitmap");
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
            Bitmap shadowedCover = factory.addShadowAndDescription(coverBitmap, null, crop, scaleFactor, null);
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

        sTVShowName.setText(mName);

        int numberOfSeasons = 0;
        String[] seasonProjection = new String[] {ScraperStore.Seasons.SHOW_ID};
        Uri seasonUri = ContentUris.withAppendedId(ScraperStore.Seasons.URI.ALL, mScraperId);
        Cursor seasonCursor = factory.getContentResolver().query(seasonUri, seasonProjection, null, null, null);
        if (seasonCursor != null) {
            numberOfSeasons = seasonCursor.getCount();
            if (numberOfSeasons > 1) {
                String f = factory.getResources().getQuantityText(R.plurals.Nseasons, numberOfSeasons).toString();
                sFormatBuilder.setLength(0);
                sFormatter.format(f, Integer.valueOf(numberOfSeasons));
                sNumberOfSeasonsOrEpisodes.setText(sFormatBuilder);
            }
            seasonCursor.close();
        }

        if (numberOfSeasons <= 1) {
            String[] episodeProjection = new String[] {ScraperStore.Episode.ID};
            Uri episodeUri = ContentUris.withAppendedId(ScraperStore.Episode.URI.SHOW, mScraperId);
            Cursor episodeCursor = factory.getContentResolver().query(episodeUri, episodeProjection, null, null, null);
            if (episodeCursor != null) {
                int numberOfEpisodes = episodeCursor.getCount();
                if (numberOfEpisodes > 0) {
                    String f = factory.getResources().getQuantityText(R.plurals.Nepisodes, numberOfEpisodes).toString();
                    sFormatBuilder.setLength(0);
                    sFormatter.format(f, Integer.valueOf(numberOfEpisodes));
                    sNumberOfSeasonsOrEpisodes.setText(sFormatBuilder);
                } else {
                    sNumberOfSeasonsOrEpisodes.setText("");
                }
                episodeCursor.close();
            } else {
                sNumberOfSeasonsOrEpisodes.setText("");
            }
        }

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
        // Here we "re-use" the basic video description (one line title plus one line duration that we use here for number of episodes)
        sDescriptionViewTVShow = factory.getLayoutInflater().inflate(R.layout.cover_floating_description_video, null);
        sTVShowName = (TextView)sDescriptionViewTVShow.findViewById(R.id.filename);
        sNumberOfSeasonsOrEpisodes = (TextView)sDescriptionViewTVShow.findViewById(R.id.duration);

        sDescriptionViewTVShow.setLayoutParams( new FrameLayout.LayoutParams(DESCRIPTION_TEXTURE_WIDTH,DESCRIPTION_TEXTURE_HEIGHT) );

        sFormatBuilder = new StringBuilder();
        sFormatter = new Formatter( sFormatBuilder, Locale.getDefault() );
    }

    @Override
    public Runnable getOpenAction(Context context) {
        return getOpenAction(context, PlayerActivity.RESUME_FROM_LAST_POS);
    }

    public Runnable getOpenAction(final Context context, final int resume) {
        return new Runnable() {
            public void run() {
                Bundle args;
                Intent i = new Intent(LAUNCH_CONTENT_BROWSER_INTENT);
                i.putExtra(BrowserActivity.FRAGMENT_NAME, BrowserListOfSeasons.class.getName());
                args = new Bundle(2);
                args.putLong(VideoStore.Video.VideoColumns.SCRAPER_SHOW_ID, mScraperId);
                args.putString("subcategoryName", mName);
                i.putExtra(BrowserActivity.FRAGMENT_ARGS, args);
                context.sendBroadcast(i);
            }
        };
    }

    @Override
    public void play(Context context) {
        getOpenAction(context).run();
    }

    public void prepareInfoDialog(Context context, InfoDialog infoDialog) {
        // Not used for Video covers
    }

    @Override
    public Uri getUri() {
        final Uri uri = ContentUris.withAppendedId( ScraperStore.Show.URI.ALL, mScraperId );
        Log.d(TAG,"getUri: "+uri);
        return uri;
    }

    public String getFilePath() {
        return mAnyEpisodeFilePath; // give file path of any episode
    }

    @Override
    public String getDescriptionName() {
        return mName;
    }

    @Override
    public String getDebugName() {
        return "TvShow"+mScraperId;
    }

    @Override
    public int getDescriptionWidth() {
        return DESCRIPTION_TEXTURE_WIDTH;
    }

    @Override
    public int getDescriptionHeight() {
        return DESCRIPTION_TEXTURE_HEIGHT;
    }
}
