
package com.archos.mediacenter.video.browser.BrowserByIndexedVideos;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.NonIndexedVideo;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.MoviesSelectionLoader;
import com.archos.mediacenter.video.browser.loader.VideosSelectionInPlaylistLoader;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.SubtitlesWizardActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesFragment;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BrowserVideosInPlaylist extends BrowserByVideoSelection {

	public static final String EXTRA_PLAYLIST_ID = "extra_playlist_id";
	public static final String EXTRA_MAP_MOVIES = "extra_map_movies";
	public static final String EXTRA_MAP_EPISODES = "extra_map_episodes";
	private Map<String, List<String>> mMoviesMap = new HashMap<>();
	private Map<String, List<String>> mEpisodesMap = new HashMap<>();

	protected long getPlaylistId() {
		return getArguments().getLong(EXTRA_PLAYLIST_ID);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//parsing maps
		String moviesMap = getArguments().getString(EXTRA_MAP_MOVIES);
		if(moviesMap != null) {
			for (String movie : moviesMap.split(",")) {
				String id = movie.split(":")[0];
				if (mMoviesMap.get(id) == null) {
					mMoviesMap.put(id, new ArrayList<String>());
				}
				mMoviesMap.get(id).add(movie.split(":")[1]);
			}
		}

		String episodeMap = getArguments().getString(EXTRA_MAP_EPISODES);
		if(episodeMap != null) {
			for (String episode : episodeMap.split(",")) {
				String id = episode.split(":")[0];
				if (mEpisodesMap.get(id) == null) {
					mEpisodesMap.put(id, new ArrayList<String>());
				}
				mEpisodesMap.get(id).add(episode.split(":")[1]);
			}
		}
	}
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args2) {
		if (getArguments() != null) {
			String listOfMoviesIds = getArguments().getString(BrowserByVideoSelection.LIST_OF_IDS);
			if (listOfMoviesIds != null)
				return new VideosSelectionInPlaylistLoader(getContext(), listOfMoviesIds).getV4CursorLoader(true, mPreferences.getBoolean(VideoPreferencesFragment.KEY_HIDE_WATCHED, false));
		}
		return null;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		} catch (ClassCastException e) {
			Log.e(TAG, "bad menuInfo", e);
			return;
		}
		// This can be null sometimes, don't crash...
		if (info == null) {
			Log.e(TAG, "bad menuInfo");
			return;
		}

		final int position = info.position;

		menu.add(0, R.string.remove_from_list, 0, R.string.remove_from_list);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if(!super.onContextItemSelected(item)){
			int index = item.getItemId();
			if(index == R.string.remove_from_list){
				AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
				Video video = mAdapterByVideoObjects.getVideoItem(info.position);
				BaseTags metadata = video.getFullScraperTags(getContext());
				boolean isEpisode = metadata instanceof EpisodeTags;
				VideoStore.VideoList.VideoItem videoItem  = new VideoStore.VideoList.VideoItem(-1,!isEpisode?(int)metadata.getOnlineId():-1, isEpisode?(int)metadata.getOnlineId():-1, VideoStore.List.SyncStatus.STATUS_DELETED);
				getContext().getContentResolver().update(VideoStore.List.getListUri(getArguments().getLong(EXTRA_PLAYLIST_ID)), videoItem.toContentValues(),  videoItem.getDBWhereString(), videoItem.getDBWhereArgs());
				//manually remove id
				String ids = getArguments().getString(BrowserByVideoSelection.LIST_OF_IDS);
				if(isEpisode){
					for(String itemId : mEpisodesMap.get(""+metadata.getOnlineId())){
						//remove all indexes
						ids = ids.replace(","+itemId,"");
						ids = ids.replace(itemId+",","");
						ids = ids.replace(itemId,"");
					}
				} else{
					for(String itemId : mMoviesMap.get(""+metadata.getOnlineId())){
						//remove all indexes
						ids = ids.replace(","+itemId,"");
						ids = ids.replace(itemId+",","");
						ids = ids.replace(itemId,"");
					}
				}
				getArguments().putString(BrowserByVideoSelection.LIST_OF_IDS, ids);
				getLoaderManager().restartLoader(0, null, this);
				TraktService.sync(ArchosUtils.getGlobalContext(), TraktService.FLAG_SYNC_AUTO);
				return true;
			}
			else{
				return false;
			}
		}else return true;
	}
}
