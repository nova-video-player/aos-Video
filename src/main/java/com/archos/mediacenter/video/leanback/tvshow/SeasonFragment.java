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

package com.archos.mediacenter.video.leanback.tvshow;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.adapters.mappers.SeasonCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.adapter.PlaceholderCursorObjectAdapter;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.SeasonPresenter;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.VideoUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by vapillon on 16/06/15.
 */
public class SeasonFragment extends BrowseSupportFragment implements LoaderManager.LoaderCallbacks<Cursor>, Delete.DeleteListener {

    private static final Logger log = LoggerFactory.getLogger(SeasonFragment.class);

    public static final String EXTRA_ACTION_ID = "ACTION_ID";
    public static final String EXTRA_TVSHOW_ID = "TVSHOW_ID";
    public static final String EXTRA_TVSHOW_NAME = "TVSHOW_NAME";
    public static final String EXTRA_TVSHOW_POSTER = "TVSHOW_POSTER";

    private long mActionId;
    /** The show we're displaying */
    private long mTvshowId;
    private String mTvshowName;
    private Uri mTvshowPosterUri;

    private ArrayObjectAdapter mRowsAdapter;
    private SeasonPresenter mSeasonPresenter;
    private PlaceholderCursorObjectAdapter mSeasonsAdapter;
    private ListRow mSeasonsListRow;

    private Overlay mOverlay;

    // need to be static otherwise ActivityResultLauncher find them null
    private static Delete delete;
    private static List<Uri> deleteUrisList;

    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> { // result can be RESULT_OK, RESULT_CANCELED
                Context context = getActivity();
                log.debug("ActivityResultLauncher deleteLauncher: result " + result.toString());
                if (result.getResultCode() == Activity.RESULT_OK) {
                    log.debug("ActivityResultLauncher deleteLauncher: OK, deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
                    if (delete != null && deleteUrisList != null && deleteUrisList.size() >= 1) {
                        log.debug("ActivityResultLauncher deleteLauncher: calling delete.deleteOK on " + deleteUrisList.get(0));
                        delete.deleteOK(deleteUrisList.get(0));
                    }
                } else {
                    log.debug("ActivityResultLauncher deleteLauncher: NO, deleteUris " + ((deleteUrisList != null) ? Arrays.toString(deleteUrisList.toArray()) : null));
                    if (delete != null && deleteUrisList != null && deleteUrisList.size() > 1)
                        delete.deleteNOK(deleteUrisList.get(0));
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // pass the right deleteLauncher linked to activity
        FileUtilsQ.setDeleteLauncher(deleteLauncher);

        mActionId = getActivity().getIntent().getLongExtra(EXTRA_ACTION_ID, -1);
        mTvshowId = getActivity().getIntent().getLongExtra(EXTRA_TVSHOW_ID, -1);
        mTvshowName = getActivity().getIntent().getStringExtra(EXTRA_TVSHOW_NAME);
        String tvshowPoster = getActivity().getIntent().getStringExtra(EXTRA_TVSHOW_POSTER);
        mTvshowPosterUri = tvshowPoster != null ? Uri.parse(tvshowPoster) : null;

        // Just need to attach the background manager to keep the background of the parent activity
        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        bgMngr.attach(getActivity().getWindow());

        setTitle(mTvshowName);
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(false);

        loadRows();
        
        mOnItemViewClickedListener = new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item == null) {
                    if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED || mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_NOT_WATCHED) {
                        boolean allEpisodesWatched = true;
    
                        for (int i = 1; i < mSeasonsAdapter.size(); i++) {
                            Season season = (Season)mSeasonsAdapter.get(i);
        
                            if (!season.allEpisodesWatched()) {
                                allEpisodesWatched = false;
    
                                break;
                            }
                        }
    
                        if (allEpisodesWatched) {
                            for (int i = 1; i < mSeasonsAdapter.size(); i++) {
                                Season season = (Season)mSeasonsAdapter.get(i);
            
                                DbUtils.markAsNotRead(getActivity(), season);
                            }
                        }
                        else {
                            for (int i = 1; i < mSeasonsAdapter.size(); i++) {
                                Season season = (Season)mSeasonsAdapter.get(i);
            
                                DbUtils.markAsRead(getActivity(), season);
                            }
                        }
    
                        getActivity().setResult(Activity.RESULT_OK);
                    }
                    else if (mActionId == TvshowActionAdapter.ACTION_UNINDEX) {
                        for (int i = 1; i < mSeasonsAdapter.size(); i++) {
                            Season season = (Season)mSeasonsAdapter.get(i);
        
                            DbUtils.markAsHiddenByUser(getActivity(), season);
                        }
                    }
                    else if (mActionId == TvshowActionAdapter.ACTION_DELETE) {
                        SeasonPresenter.VideoViewHolder vh = (SeasonPresenter.VideoViewHolder)itemViewHolder;

                        if (!vh.getConfirmDelete()) {
                            vh.enableConfirmDelete();
                        }
                        else {
                            ArrayList<Uri> uris = new ArrayList<Uri>();
    
                            for (int i = 1; i < mSeasonsAdapter.size(); i++) {
                                Season season = (Season)mSeasonsAdapter.get(i);
            
                                for(String filePath : DbUtils.getFilePaths(getActivity(), season)) {
                                    Uri uri = VideoUtils.getFileUriFromMediaLibPath(filePath);
                                    
                                    uris.add(uri);
                                }
                            }
    
                            delete = new Delete(SeasonFragment.this, getActivity());
                            deleteUrisList = uris;
                            if (uris.size() == 1)
                                delete.startDeleteProcess(uris.get(0));
                            else if (uris.size() > 1)
                                delete.startMultipleDeleteProcess(uris);
                        }
                    }

                    return;
                }

                Season season = (Season)item;
                
                if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED || mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_NOT_WATCHED) {
                    if (season.allEpisodesWatched()) {
                        DbUtils.markAsNotRead(getActivity(), season);
                    }
                    else {
                        DbUtils.markAsRead(getActivity(), season);
                    }

                    getActivity().setResult(Activity.RESULT_OK);
                }
                else if (mActionId == TvshowActionAdapter.ACTION_UNINDEX) {
                    DbUtils.markAsHiddenByUser(getActivity(), season);
                }
                else if (mActionId == TvshowActionAdapter.ACTION_DELETE) {
                    SeasonPresenter.VideoViewHolder vh = (SeasonPresenter.VideoViewHolder)itemViewHolder;

                    if (!vh.getConfirmDelete()) {
                        vh.enableConfirmDelete();
                    }
                    else {
                        ArrayList<Uri> uris = new ArrayList<Uri>();

                        for(String filePath : DbUtils.getFilePaths(getActivity(), season)) {
                            Uri uri = VideoUtils.getFileUriFromMediaLibPath(filePath);
                            
                            uris.add(uri);
                        }

                        delete = new Delete(SeasonFragment.this, getActivity());
                        deleteUrisList = uris;
                        if (uris.size() == 1)
                            delete.startDeleteProcess(uris.get(0));
                        else if (uris.size() > 1)
                            delete.startMultipleDeleteProcess(uris);
                    }
                }
            }
        };

        setOnItemViewClickedListener(mOnItemViewClickedListener);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();

        // Start loading the list of seasons
        LoaderManager.getInstance(this).restartLoader(1, null, this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    private void loadRows() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        mSeasonPresenter = new SeasonPresenter(getActivity(), mActionId);
        mSeasonsAdapter = new PlaceholderCursorObjectAdapter(mSeasonPresenter);
        mSeasonsAdapter.setMapper(new CompatibleCursorMapperConverter(new SeasonCursorMapper()));
        String desc = "";
        if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED || mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_NOT_WATCHED)
            desc = getString(R.string.how_to_mark_season_watched);
        else if (mActionId == TvshowActionAdapter.ACTION_UNINDEX)
            desc = getString(R.string.how_to_unindex_season);
        else if (mActionId == TvshowActionAdapter.ACTION_DELETE)
            desc = getString(R.string.how_to_delete_season);
        mSeasonsListRow = new ListRow(new HeaderItem(desc), mSeasonsAdapter);
        mRowsAdapter.add(mSeasonsListRow);

        setAdapter(mRowsAdapter);
    }

    //--------------------------------------------

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        return new SeasonsLoader(getActivity(), mTvshowId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mSeasonPresenter.setAllSeasons(null);
        mSeasonsAdapter.changeCursor(cursor);

        int episodeTotalCount = 0;
        int episodeWatchedCount = 0;
    
        for (int i = 1; i < mSeasonsAdapter.size(); i++) {
            Season season = (Season)mSeasonsAdapter.get(i);

            episodeTotalCount += season.getEpisodeTotalCount();
            episodeWatchedCount += season.getEpisodeWatchedCount();
        }

        Season allSeasons = new Season(mTvshowId, mTvshowName, mTvshowPosterUri, -1, episodeTotalCount, episodeWatchedCount);

        mSeasonPresenter.setAllSeasons(allSeasons);
        mSeasonsAdapter.onCursorChanged();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }
    
    @Override
    public void onVideoFileRemoved(final Uri videoFile,boolean askForFolderRemoval, final Uri folder) {
        Activity activity = getActivity();
        if (activity != null) Toast.makeText(activity, R.string.delete_done, Toast.LENGTH_SHORT).show();
        if (askForFolderRemoval) {
            if (activity != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(activity).setTitle("");
                b.setIcon(R.drawable.filetype_new_folder);
                b.setMessage(R.string.confirm_delete_parent_folder);
                b.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        sendDeleteResult(videoFile);
                    }
                })
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                delete = new Delete(SeasonFragment.this, activity);
                                deleteUrisList = Collections.singletonList(folder);
                                delete.deleteFolder(folder);
                            }
                        });
                b.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        sendDeleteResult(videoFile);
                    }
                });
                b.create().show();
            }
        } else {
            sendDeleteResult(videoFile);
        }
    }

    private void sendDeleteResult(Uri file){
        Intent intent = new Intent();
        intent.setData(file);
        Activity activity = getActivity();
        if (activity != null) activity.setResult(ListingActivity.RESULT_FILE_DELETED, intent);
    }

    @Override
    public void onDeleteVideoFailed(Uri videoFile) {
        Activity activity = getActivity();
        if (activity != null) Toast.makeText(activity,R.string.delete_error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFolderRemoved(Uri folder) {
        Activity activity = getActivity();
        if (activity != null) Toast.makeText(activity, R.string.delete_done, Toast.LENGTH_SHORT).show();
        sendDeleteResult(folder);
    }

    @Override
    public void onDeleteSuccess() {
    }

    //--------------------------------------------

    private OnItemViewClickedListener mOnItemViewClickedListener;
}
