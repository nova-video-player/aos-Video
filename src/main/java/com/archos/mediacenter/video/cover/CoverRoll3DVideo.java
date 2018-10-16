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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.SurfaceHolder;
import android.widget.Toast;

import com.archos.environment.ArchosIntents;
import com.archos.environment.ArchosSettings;
import com.archos.filecorelibrary.MetaFile;
import com.archos.mediacenter.cover.ArtworkFactory;
import com.archos.mediacenter.cover.Cover;
import com.archos.mediacenter.cover.CoverProvider;
import com.archos.mediacenter.cover.CoverRoll3D;
import com.archos.mediacenter.cover.CoverRollLayout;
import com.archos.mediacenter.cover.LibraryUtils;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.dialogs.DeleteDialog;
import com.archos.mediacenter.video.info.VideoInfoActivity;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.SubtitlesDownloaderActivity;
import com.archos.mediacenter.video.utils.SubtitlesWizardActivity;

import java.util.ArrayList;


public class CoverRoll3DVideo extends CoverRoll3D {

	static final String TAG = "CoverRoll3DVideo";
	static final boolean DBG = false;

	public final static String CONTENT_ALL_VIDEOS = "all_videos";
	public final static String CONTENT_RECENTLY_ADDED = "recently_added";
	public final static String CONTENT_ALL_MOVIES = "movies";
	public final static String CONTENT_ALL_TV_SHOWS = "all_tvshows";
	
	final static private CoverRollVideoContent[] ROLL_CONTENT = new CoverRollVideoContent[] {
			new CoverRollVideoContent(CONTENT_ALL_VIDEOS, R.string.all_videos),
			new CoverRollVideoContent(CONTENT_RECENTLY_ADDED, R.string.recently_added_videos),
			new CoverRollVideoContent(CONTENT_ALL_MOVIES, R.string.movies),
			new CoverRollVideoContent(CONTENT_ALL_TV_SHOWS, R.string.all_tv_shows)
	};

	private static final int DEFAULT_CONTENT = 0;	//all_videos

	private int mInitialContent = 0; // the init value for CoverRollVideoContent.mId

	private CoverRollVideoContent mCurrentContent;

	/**
	 * Construct object, initializing with any attributes we understand from a
	 * layout file. These attributes are defined in
	 * SDK/assets/res/any/classes.xml.
	 *
	 * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
	 */
	public CoverRoll3DVideo(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	/**
	 * Set the content to be displayed
	 * @param content_id: CONTENT_ALL_ALBUMS, CONTENT_RECENTLY_ADDED, CONTENT_RECENTLY_PLAYED or CONTENT_FAVORITES
	 */
	public void setContentId(String content_id) {
		if (content_id==null) {
			return;
		}

		for (int i=0; i<ROLL_CONTENT.length; i++) {
			if (content_id.equals(ROLL_CONTENT[i].mId)) {
				mInitialContent = i;
			}
		}
	}

	/**
	 * surfaceChanged is called when the surface size is known
	 * (may be overridden by child classes)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// Reset the cached layout, bitmaps, etc. when the screen size is changed
		VideoCover.resetCachedGraphicStuff();
		MovieCover.resetCachedGraphicStuff();
		EpisodeCover.resetCachedGraphicStuff();
		// Set the fine tuning of the layout
		CoverRollLayout layout = (CoverRollLayout)mLayout; // layout shall always be a CoverRollLayout in that case;
		layout.setFineTuningValues(CoverRollLayout.FINE_TUNE_FOR_VIDEO);
		float densityIndependantWidth = w/getResources().getDisplayMetrics().density;
		layout.displayDescriptions(densityIndependantWidth>550); // MAGICAL

		// Now that we know the view size we can decide on the eye distance
		mRenderer.setEyeDistance(getEyeDistance(w,h) * 0.9f); // MAGICAL x0.9 factor for Video Covers!!!

		// Super class probably has stuff to do as well
		super.surfaceChanged(holder, format, w, h);
	}

	/**
	 * Build the single default cover bitmap
	 */
	protected Bitmap getDefaultArtwork(ArtworkFactory factory) {
		int labelId;
		if (mCurrentContent!=null) {
			labelId = mCurrentContent.mLabelId;
		} else {
			labelId = ROLL_CONTENT[DEFAULT_CONTENT].mLabelId;
		}

		if(DBG) Log.d(TAG, "getDefaultArtwork, labelId="+labelId);

		switch (labelId) {
			case R.string.movies:
			case R.string.all_tv_shows:
				return MovieCover.getDefaultArtwork(factory);
			case R.string.all_videos:
			case R.string.recently_added_videos:
			default:
				return VideoCover.getDefaultArtwork(factory);
		}
	}

	/**
	 * Load the initial content of the Cover view
	 */
	protected void loadInitialContent() {
		changeContent(mInitialContent);
	}

	/**
	 * Save what has to be saved to restore the Cover view next time
	 */
	protected void saveCoverProviderContext() {
		// Save the current choice for the type of content in the CoverRoll
		if (mCurrentContent!=null) {
			LibraryUtils.setStringPref(getContext(), MediaUtils.PREFS_SETTINGS_COVER_ROLL_3D_VIDEO_CONTENT_KEY, mCurrentContent.mId);
		}
	}

	/**
	 * When user swipe to change the content
	 *
	 */
	public void changeContent( int directionChange) {

		if (mCurrentContent == null) {
			String coverRollInitContentId = null;

			// Check in settings for last value used
			coverRollInitContentId = LibraryUtils.getStringPref( getContext(), MediaUtils.PREFS_SETTINGS_COVER_ROLL_3D_VIDEO_CONTENT_KEY, ROLL_CONTENT[DEFAULT_CONTENT].mId);
			if(DBG) Log.d(TAG,"reading "+MediaUtils.PREFS_SETTINGS_COVER_ROLL_3D_VIDEO_CONTENT_KEY+"="+coverRollInitContentId);

			for (int i=0 ; i<ROLL_CONTENT.length ; i++) {
				if (ROLL_CONTENT[i].mId.equals(coverRollInitContentId)) {
					mCurrentContent = ROLL_CONTENT[i];
					continue;
				}
			}
			// Set to default value if not found
			if (mCurrentContent == null) {
				mCurrentContent = ROLL_CONTENT[DEFAULT_CONTENT];
			}
		}
		else if (directionChange==0) {  // reload the current one
			//nothing here
		}
		else if (directionChange==-1) {    // Change to next content type
			mCurrentContent = mCurrentContent.getNextContentType(ROLL_CONTENT);
		}
		else { // Change to previous content type
			mCurrentContent = mCurrentContent.getPreviousContentType(ROLL_CONTENT);
		}
		// Stop the cover provider updates before changing to the new one
		if (mCoverProvider!=null) {
			mCoverProvider.stop();
		}
		// Get content provider for this new type of content
		mCoverProvider = mCurrentContent.getCoverProvider(getContext());
		mCoverProvider.start(mLoaderManager, this);
		// Set the 3D label of the view
        String label = getResources().getString( mCurrentContent.mLabelId);
		setGeneralLabel(label);

		//TODO: SHOULD I EMPTY THE ROLL HERE BEFORE GETTING THE ASYNC REPLY???
	}

	/**
	 *  Private Inner class to describe the 4 choices of content for the Music Cover Roll
	 */
	private static class CoverRollVideoContent {
		public String mId;
		public int mLabelId;
		public CoverRollVideoContent(String id, int labelId) {
			mId = id;
			mLabelId = labelId;
		}
		public CoverProvider getCoverProvider(Context context) {
			switch (mLabelId) {
			case R.string.all_videos: return new AllVideosProvider(context);
			case R.string.recently_added_videos: return new RecentlyAddedVideosProvider(context);
			case R.string.movies: return new AllMoviesProvider(context);
			case R.string.all_tv_shows: return new AllTVShowsProvider(context);
			default: return null; // should not happen
			}
		}
		// weird (but convenient) functions using the final array ROLL_CONTENT as argument...
		final public CoverRollVideoContent getNextContentType(CoverRollVideoContent[] roll_content) {
			for (int i=0; i<(roll_content.length-1); i++) {
				if (roll_content[i] == this)
					return roll_content[i+1];
			}
			return roll_content[0];
		}
		final public CoverRollVideoContent getPreviousContentType(CoverRollVideoContent[] roll_content) {
			for (int i=roll_content.length-1; i>0; i--) {
				if (roll_content[i] == this)
					return roll_content[i-1];
			}
			return roll_content[roll_content.length-1];
		}
	}


	@Override
	public void createContextMenu(final Activity activity, ContextMenu menu) {
	    if(DBG) Log.d(TAG,"createContextMenu");

	    final Cover c = mLayout.getFrontCover();
	    if (c==null) {
	        return;
	    }

		// Some stuff to do in the parent class
		super.createContextMenu(activity,menu);

	    // Special case: TvShow covers have only info in context menu.
	    if (c instanceof TvShowCover) {
            menu.add(R.string.info);
	        return;
	    }

		menu.add(0, R.string.play_selection, 0, R.string.play_selection).setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				mLayout.getFrontCover().play(getContext());
				return true;
			}
		});

		if (!(c instanceof BaseVideoCover)) {
			throw new IllegalStateException("CoverRoll3DVideo must only contain instances of BaseVideoCover!");
		}

		// Then add video specific stuff

		// Check for resume and bookmark in the database
		// Not very nice to do it here synchronously, but I have no nice way to handle it in the CoverProvider with value being updated
		// (I could get it in cursor update, but I would then recompute the cover texture at the same time, not nice...)
		int[] resumeAndBookmark = new int[2];
		LibraryUtils.getVideoResumeAndBookmark(getContext(), c.getMediaLibraryId(), resumeAndBookmark);

		//Resume
		final int resume = resumeAndBookmark[0];
		if (resume > 0) {
		    menu.findItem(R.string.play_selection).setTitle(R.string.play_from_beginning);

            String resumeString = activity.getString(R.string.resume) + " (" + MediaUtils.formatTime(resume) + ")";
			menu.add(resumeString).setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					BaseVideoCover bvc = (BaseVideoCover)c;
					bvc.getOpenAction(getContext(), PlayerActivity.RESUME_FROM_LAST_POS).run(); // here we lose the actual resume position, Player will query it again...
					return true;
				}});
		}
		//Bookmark
		final int bookmark = resumeAndBookmark[1];
		if (bookmark > 0) {
            String bookmarkString = activity.getString(R.string.bookmark) + " (" + MediaUtils.formatTime(bookmark) + ")";
			menu.add(bookmarkString).setOnMenuItemClickListener(new OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					BaseVideoCover bvc = (BaseVideoCover)c;
					bvc.getOpenAction(getContext(), PlayerActivity.RESUME_FROM_BOOKMARK).run(); // here we lose the actual bookmark position, Player will query it again...
					return true;
				}});
		}

		//Delete
		menu.add(R.string.delete).setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
                // Forbid deleting in DemoMode
                if (ArchosSettings.isDemoModeActive(mActivity)) {
                    mActivity.startService(new Intent(ArchosIntents.ACTION_DEMO_MODE_FEATURE_DISABLED));
                } else {
					AlertDialog.Builder b = new AlertDialog.Builder(activity).setTitle("");
					b.setIcon(R.drawable.filetype_new_video);
					b.setMessage(R.string.confirm_delete);
					b.setNegativeButton(R.string.no, null)
							.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialogInterface, int i) {
									final DeleteDialog deleteDialog = new DeleteDialog();
									deleteDialog.show(((FragmentActivity) mActivity).getSupportFragmentManager(), null);
									Delete delete = new Delete(new Delete.DeleteListener() {
										@Override
										public void onVideoFileRemoved(Uri videoFile, boolean b, Uri u ) {}
										@Override
										public void onDeleteVideoFailed(Uri videoFile) {
											deleteDialog.dismiss();
											Toast.makeText(getContext(), R.string.delete_error, Toast.LENGTH_LONG).show();
										}
										@Override
										public void onDeleteSuccess() {
											deleteDialog.dismiss();
											Toast.makeText(getContext(), R.string.delete_done, Toast.LENGTH_LONG).show();
										}
										@Override
										public void onFolderRemoved(Uri folder) {}
									}, activity);
									delete.startDeleteProcess(Uri.parse(((BaseVideoCover) c).getFilePath()));
								}
							}).show();
                }
				return true;
			}
		});

        //Subtitles wizard
        menu.add(R.string.get_subtitles_on_drive).setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                String videoPath = ((BaseVideoCover)c).getFilePath();
                if (videoPath != null && videoPath.length() > 0) {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClass(mActivity, SubtitlesWizardActivity.class);
                    intent.setData(MetaFile.pathToUri(videoPath));
                    getContext().startActivity(intent);
                }
                return true;
            }});
        //Subloader
        final String videoPath = ((BaseVideoCover)c).getFilePath();
        menu.add(R.string.get_subtitles_online).setOnMenuItemClickListener(
                new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.setClass(getContext(), SubtitlesDownloaderActivity.class);
                        intent.putExtra("fileUrl", videoPath);
                        mActivity.startActivity(intent);
                        return true;
                    }

                });
        //Info
		menu.add(R.string.info).setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				openInfoActivity(mLayout.getFrontCoverIndex());
				return true;
			}});

	}

	public void openInfoActivity(int position){
		// Give all the file paths of the current view
		ArrayList<Uri> urlList = new ArrayList<Uri>(mCovers.size());

		int j =0;
		int pos = 0;
		for (int i=
			 mLayout.getFrontCoverIndex()- VideoInfoActivity.MAX_VIDEO/2<0?0:position-VideoInfoActivity.MAX_VIDEO/2;i<mCovers.size();i++, j++) {
			urlList.add(j,Uri.parse(((BaseVideoCover)mCovers.get(i)).getFilePath()));
			if(i == position)
				pos = j;
			if(j>VideoInfoActivity.MAX_VIDEO)
				break;
		}
		// Give current position
		VideoInfoActivity.startInstance(getContext(), null,null, pos, urlList,-1, false, -1);
	}
	@Override
	protected Runnable getOpenAction(final Integer cid) {
		if(mCovers.get(cid) instanceof BaseVideoCover)
			return new Runnable() {
				@Override
				public void run() {
						openInfoActivity(cid);
				}
			};
		else
			return mCovers.get(cid).getOpenAction(getContext());
	}


}
