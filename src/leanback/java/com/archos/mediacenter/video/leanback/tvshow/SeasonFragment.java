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

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.SeasonCursorMapper;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediacenter.video.browser.adapters.object.Season;
import com.archos.mediacenter.video.browser.loader.SeasonsLoader;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.SeasonPresenter;

import java.util.ArrayList;

/**
 * Created by vapillon on 16/06/15.
 */
public class SeasonFragment extends BrowseFragment implements  LoaderManager.LoaderCallbacks<Cursor>, Delete.DeleteListener {

    private static final String TAG = "SeasonFragment";

    public static final String EXTRA_ACTION_ID = "ACTION_ID";
    public static final String EXTRA_TVSHOW_ID = "TVSHOW_ID";
    public static final String EXTRA_TVSHOW_NAME = "TVSHOW_NAME";

    private long mActionId;
    /** The show we're displaying */
    private long mTvshowId;
    private String mTvshowName;

    private ArrayObjectAdapter mRowsAdapter;
    private CursorObjectAdapter mSeasonsAdapter;
    private ListRow mSeasonsListRow;

    private Overlay mOverlay;
    
    private Button mActionButton;
    private boolean mConfirmDelete = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActionId = getActivity().getIntent().getLongExtra(EXTRA_ACTION_ID, -1);
        mTvshowId = getActivity().getIntent().getLongExtra(EXTRA_TVSHOW_ID, -1);
        mTvshowName = getActivity().getIntent().getStringExtra(EXTRA_TVSHOW_NAME);

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
                Season season = (Season)item;
                
                if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED) {
                    if (season.allEpisodesWatched()) {
                        DbUtils.markAsNotRead(getActivity(), season);
                    }
                    else {
                        DbUtils.markAsRead(getActivity(), season);
                    }
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

                        Delete delete = new Delete(SeasonFragment.this, getActivity());

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
        
        ViewGroup titleView = (ViewGroup)getTitleView();
        mActionButton = (Button)LayoutInflater.from(getContext()).inflate(R.layout.leanback_season_action_button, titleView, false);
        String text = "";
        
        if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED)
            text = getString(R.string.mark_show_watched);
        else if (mActionId == TvshowActionAdapter.ACTION_UNINDEX)
            text = getString(R.string.unindex_show);
        else if (mActionId == TvshowActionAdapter.ACTION_DELETE)
            text = getString(R.string.delete_show);

        mActionButton.setText(text);
        mActionButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED) {
                    boolean allEpisodesWatched = true;

                    for (int i = 0; i < mSeasonsAdapter.size(); i++) {
                        Season season = (Season)mSeasonsAdapter.get(i);
    
                        if (!season.allEpisodesWatched()) {
                            allEpisodesWatched = false;

                            break;
                        }
                    }

                    if (allEpisodesWatched) {
                        for (int i = 0; i < mSeasonsAdapter.size(); i++) {
                            Season season = (Season)mSeasonsAdapter.get(i);
        
                            DbUtils.markAsNotRead(getActivity(), season);
                        }
                    }
                    else {
                        for (int i = 0; i < mSeasonsAdapter.size(); i++) {
                            Season season = (Season)mSeasonsAdapter.get(i);
        
                            DbUtils.markAsRead(getActivity(), season);
                        }
                    }
                }
                else if (mActionId == TvshowActionAdapter.ACTION_UNINDEX) {
                    for (int i = 0; i < mSeasonsAdapter.size(); i++) {
                        Season season = (Season)mSeasonsAdapter.get(i);
    
                        DbUtils.markAsHiddenByUser(getActivity(), season);
                    }
                }
                else if (mActionId == TvshowActionAdapter.ACTION_DELETE) {
                    if (!mConfirmDelete) {
                        mConfirmDelete = true;

                        mActionButton.setText(getString(R.string.confirm_delete_short));
                    }
                    else {
                        ArrayList<Uri> uris = new ArrayList<Uri>();

                        for (int i = 0; i < mSeasonsAdapter.size(); i++) {
                            Season season = (Season)mSeasonsAdapter.get(i);
        
                            for(String filePath : DbUtils.getFilePaths(getActivity(), season)) {
                                Uri uri = VideoUtils.getFileUriFromMediaLibPath(filePath);
                                
                                uris.add(uri);
                            }
                        }

                        Delete delete = new Delete(SeasonFragment.this, getActivity());

                        if (uris.size() == 1)
                            delete.startDeleteProcess(uris.get(0));
                        else if (uris.size() > 1)
                            delete.startMultipleDeleteProcess(uris);
                    }
                }
            }
         });
        titleView.addView(mActionButton);
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
        getLoaderManager().restartLoader(1, null, this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    private void loadRows() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

        mSeasonsAdapter = new CursorObjectAdapter(new SeasonPresenter(getActivity(), mActionId));
        mSeasonsAdapter.setMapper(new CompatibleCursorMapperConverter(new SeasonCursorMapper()));
        String desc = "";
        if (mActionId == TvshowActionAdapter.ACTION_MARK_SHOW_AS_WATCHED)
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
        mSeasonsAdapter.changeCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }
    
    @Override
    public void onVideoFileRemoved(final Uri videoFile,boolean askForFolderRemoval, final Uri folder) {
        Toast.makeText(getActivity(),R.string.delete_done, Toast.LENGTH_SHORT).show();
        if(askForFolderRemoval) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity()).setTitle("");
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
                            Delete delete = new Delete(SeasonFragment.this, getActivity());
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
        else {
            sendDeleteResult(videoFile);
        }

    }

    private void sendDeleteResult(Uri file){
        Intent intent = new Intent();
        intent.setData(file);
        getActivity().setResult(ListingActivity.RESULT_FILE_DELETED, intent);
    }

    @Override
    public void onDeleteVideoFailed(Uri videoFile) {
        Toast.makeText(getActivity(),R.string.delete_error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFolderRemoved(Uri folder) {
        Toast.makeText(getActivity(), R.string.delete_done, Toast.LENGTH_SHORT).show();
        sendDeleteResult(folder);
    }

    @Override
    public void onDeleteSuccess() {
    }

    //--------------------------------------------

    private OnItemViewClickedListener mOnItemViewClickedListener;
}
