// Copyright 2020 Courville Software
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

package com.archos.mediacenter.video.leanback.collections;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

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

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.adapters.mappers.CollectionCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.loader.CollectionLoader;
import com.archos.mediacenter.video.browser.loader.MovieCollectionLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.adapter.PlaceholderCursorObjectAdapter;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.MovieCollectionPresenter;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.util.ArrayList;

public class MovieCollectionFragment extends BrowseSupportFragment implements LoaderManager.LoaderCallbacks<Cursor>, Delete.DeleteListener {

    private static final String TAG = "MovieCollectionFragment";
    private static final boolean DBG = true;

    public static final String EXTRA_ACTION_ID = "ACTION_ID";
    public static final String EXTRA_COLLECTION_ID = "COLLECTION_ID";
    public static final String EXTRA_COLLECTION_NAME = "COLLECTION_NAME";
    public static final String EXTRA_COLLECTION_POSTER = "COLLECTION_POSTER";

    private long mActionId;
    /** The show we're displaying */
    private long mCollectionId;
    private String mCollectionName;
    private Uri mCollectionPosterUri;

    Collection mCollection;

    private ArrayObjectAdapter mRowsAdapter;
    private MovieCollectionPresenter mMovieCollectionPresenter;
    private PlaceholderCursorObjectAdapter mMovieCollectionAdapter;
    private ListRow mMovieCollectionListRow;

    private Overlay mOverlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mActionId = getActivity().getIntent().getLongExtra(EXTRA_ACTION_ID, -1);
        mCollectionId = getActivity().getIntent().getLongExtra(EXTRA_COLLECTION_ID, -1);
        mCollectionName = getActivity().getIntent().getStringExtra(EXTRA_COLLECTION_NAME);
        String collectionPoster = getActivity().getIntent().getStringExtra(EXTRA_COLLECTION_POSTER);
        mCollectionPosterUri = collectionPoster != null ? Uri.parse(collectionPoster) : null;

        // TODO MARC could be passed from CollectionFragment in the intent!!!!! since collection already there --> TODO
        // find back the collection from mCollectionId
        CollectionLoader collectionLoader = new CollectionLoader(getActivity(), mCollectionId);
        Cursor cursor = collectionLoader.loadInBackground();
        if(cursor != null && cursor.getCount()>0) {
            cursor.moveToFirst();
            CollectionCursorMapper collectionCursorMapper = new CollectionCursorMapper();
            collectionCursorMapper.bindColumns(cursor);
            mCollection = (Collection) collectionCursorMapper.bind(cursor);
        }

        // Just need to attach the background manager to keep the background of the parent activity
        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        bgMngr.attach(getActivity().getWindow());

        setTitle(mCollectionName);
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(false);

        loadRows();
        
        mOnItemViewClickedListener = new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {

                // TODO MARC: warning if in background the loading might be null!!!
                if (mCollection == null) {
                    if (DBG) Log.d(TAG, "onCreate: mCollection null!!!)");
                    return;
                }

                if (item == null) {
                    /*
                    if (mActionId == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_WATCHED) {
                        boolean collectionWatched = true;
                        for (int i = 1; i < mMovieCollectionAdapter.size(); i++) {
                            Movie movie = (Movie)mMovieCollectionAdapter.get(i);
                            if (!movie.isWatched()) {
                                collectionWatched = false;
                                break;
                            }
                        }
                        if (collectionWatched) {
                            for (int i = 1; i < mMovieCollectionAdapter.size(); i++) {
                                Movie movie = (Movie)mMovieCollectionAdapter.get(i);
                                DbUtils.markAsNotRead(getActivity(), movie);
                            }
                        } else {
                            for (int i = 1; i < mMovieCollectionAdapter.size(); i++) {
                                Movie movie = (Movie)mMovieCollectionAdapter.get(i);
                                DbUtils.markAsRead(getActivity(), movie);
                            }
                        }
                        getActivity().setResult(Activity.RESULT_OK);
                    }
                    else if (mActionId == CollectionActionAdapter.ACTION_UNINDEX) {
                        for (int i = 1; i < mMovieCollectionAdapter.size(); i++) {
                            Movie movie = (Movie)mMovieCollectionAdapter.get(i);
                            DbUtils.markAsHiddenByUser(getActivity(), movie);
                        }
                    } else if (mActionId == CollectionActionAdapter.ACTION_DELETE) {
                        MovieCollectionPresenter.VideoViewHolder vh = (MovieCollectionPresenter.VideoViewHolder) itemViewHolder;
                        if (!vh.getConfirmDelete()) {
                            vh.enableConfirmDelete();
                        } else {
                            ArrayList<Uri> uris = new ArrayList<Uri>();
                            for (int i = 1; i < mMovieCollectionAdapter.size(); i++) {
                                Movie movie = (Movie) mMovieCollectionAdapter.get(i);
                                uris.add(VideoUtils.getFileUriFromMediaLibPath(movie.getFilePath()));
                            }
                            Delete delete = new Delete(MovieCollectionFragment.this, getActivity());
                            if (uris.size() == 1)
                                delete.startDeleteProcess(uris.get(0));
                            else if (uris.size() > 1)
                                delete.startMultipleDeleteProcess(uris);
                        }
                    }
                     */

                    // TODO MARC: warning if in background the loading might be null!!!
                    if (mCollection == null) {
                        Log.w(TAG, "onCreate: mCollection null!!!)");
                        return;
                    }

                    if (mActionId == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_WATCHED) {
                        boolean collectionWatched = true;
                        if (!mCollection.isWatched()) {
                            collectionWatched = false;
                            break;
                        }
                        if (collectionWatched) DbUtils.markAsNotRead(getActivity(), mCollection);
                        else DbUtils.markAsRead(getActivity(), mCollection);
                        getActivity().setResult(Activity.RESULT_OK);
                    } else if (mActionId == CollectionActionAdapter.ACTION_UNINDEX) {
                        DbUtils.markAsHiddenByUser(getActivity(), mCollection);
                    } else if (mActionId == CollectionActionAdapter.ACTION_DELETE) {
                        MovieCollectionPresenter.VideoViewHolder vh = (MovieCollectionPresenter.VideoViewHolder)itemViewHolder;
                        if (!vh.getConfirmDelete()) {
                            vh.enableConfirmDelete();
                        } else {
                            ArrayList<Uri> uris = new ArrayList<Uri>();
                            for(String filePath : DbUtils.getFilePaths(getActivity(), mCollection)) {
                                Uri uri = VideoUtils.getFileUriFromMediaLibPath(filePath);
                                uris.add(uri);
                            }
                            Delete delete = new Delete(MovieCollectionFragment.this, getActivity());
                            if (uris.size() == 1)
                                delete.startDeleteProcess(uris.get(0));
                            else if (uris.size() > 1)
                                delete.startMultipleDeleteProcess(uris);
                        }
                    }
                    return;
                }

                Movie movie = (Movie)item;
                
                if (mActionId == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_WATCHED) {
                    if (movie.isWatched()) {
                        DbUtils.markAsNotRead(getActivity(), movie);
                    }
                    else {
                        DbUtils.markAsRead(getActivity(), movie);
                    }

                    getActivity().setResult(Activity.RESULT_OK);
                }
                else if (mActionId == CollectionActionAdapter.ACTION_UNINDEX) {
                    DbUtils.markAsHiddenByUser(getActivity(), movie);
                }
                else if (mActionId == CollectionActionAdapter.ACTION_DELETE) {
                    MovieCollectionPresenter.VideoViewHolder vh = (MovieCollectionPresenter.VideoViewHolder)itemViewHolder;

                    if (!vh.getConfirmDelete()) {
                        vh.enableConfirmDelete();
                    }
                    else {
                        ArrayList<Uri> uris = new ArrayList<Uri>();
                        uris.add(VideoUtils.getFileUriFromMediaLibPath(movie.getFilePath()));
                        Delete delete = new Delete(MovieCollectionFragment.this, getActivity());
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

        mMovieCollectionPresenter = new MovieCollectionPresenter(getActivity(), mActionId);
        mMovieCollectionAdapter = new PlaceholderCursorObjectAdapter(mMovieCollectionPresenter);
        mMovieCollectionAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        String desc = "";
        // TODO MARC collection has no multiple seasons!!! rework this part and delete strings
        if (mActionId == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_WATCHED)
            desc = getString(R.string.how_to_mark_collection_watched);
        else if (mActionId == CollectionActionAdapter.ACTION_UNINDEX)
            desc = getString(R.string.how_to_unindex_collection);
        else if (mActionId == CollectionActionAdapter.ACTION_DELETE)
            desc = getString(R.string.how_to_delete_collection);
        mMovieCollectionListRow = new ListRow(new HeaderItem(desc), mMovieCollectionAdapter);
        mRowsAdapter.add(mMovieCollectionListRow);

        setAdapter(mRowsAdapter);
    }

    //--------------------------------------------

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        return new MovieCollectionLoader(getActivity(), mCollectionId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mMovieCollectionPresenter.setCollection(null);
        mMovieCollectionAdapter.changeCursor(cursor);

        int movieCollectionTotalCount = mCollection.getMovieCollectionCount();
        int movieCollectionWatchedCount = mCollection.getMovieCollectionWatchedCount();

        /*
        int movieCollectionTotalCount = 0;
        int movieCollectionWatchedCount = 0;
        for (int i = 1; i < mMovieCollectionAdapter.size(); i++) {
            Movie movie = (Movie)mMovieCollectionAdapter.get(i);
            movieCollectionTotalCount += 1;
            if (movie.isWatched()) movieCollectionWatchedCount += 1;
        }
         */

        // TODO MARC: check that this is ok
        //Season allSeasons = new Season(mCollectionId, mCollectionName, mCollectionPosterUri, -1, episodeTotalCount, episodeWatchedCount);

        mMovieCollectionPresenter.setCollection(mCollection);
        mMovieCollectionAdapter.onCursorChanged();
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
                            Delete delete = new Delete(MovieCollectionFragment.this, getActivity());
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
