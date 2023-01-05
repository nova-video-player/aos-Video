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

import static com.archos.mediacenter.video.leanback.LoaderIds.CollectionLoaderId;

import android.app.Activity;
import android.app.ActivityOptions;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.loader.app.LoaderManager;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.loader.content.Loader;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsFragmentWithLessTopOffset;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.CursorObjectAdapter;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.FullWidthDetailsOverviewSharedElementHelper;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import android.transition.Slide;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.loader.content.CursorLoader;

import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.Delete;
import com.archos.mediacenter.video.browser.adapters.MovieCollectionAdapter;
import com.archos.mediacenter.video.browser.adapters.mappers.CollectionCursorMapper;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.loader.AllCollectionsLoader;
import com.archos.mediacenter.video.browser.loader.CollectionLoader;
import com.archos.mediacenter.video.browser.loader.MovieCollectionLoader;
import com.archos.mediacenter.video.collections.CollectionsSortOrderEntries;
import com.archos.mediacenter.video.info.VideoInfoCommonClass;
import com.archos.mediacenter.video.leanback.BackdropTask;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.leanback.details.ArchosDetailsOverviewRowPresenter;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.leanback.presenter.PresenterUtils;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.utils.DbUtils;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.squareup.picasso.Picasso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class CollectionFragment extends DetailsFragmentWithLessTopOffset implements LoaderManager.LoaderCallbacks<Cursor>, Delete.DeleteListener {

    private static final Logger log = LoggerFactory.getLogger(CollectionFragment.class);

    public static final String EXTRA_COLLECTION = "COLLECTION";
    public static final String EXTRA_COLLECTION_ID = "collection_id";
    public static final String SHARED_ELEMENT_NAME = "hero";

    public static final int COLLECTION_LOADER_ID = CollectionLoaderId;

    public static final int REQUEST_CODE_VIDEO = 8576;
    public static final int REQUEST_CODE_MARK_WATCHED = 8577;

    private static final int INDEX_DETAILS = 0;

    /** The collection we're displaying */
    private Collection mCollection;
    private long mCollectionId;

    private DetailsOverviewRow mDetailsOverviewRow;
    private ArrayObjectAdapter mRowsAdapter;
    private MovieCollectionAdapter mMovieCollectionAdapter;

    private AsyncTask mBackdropTask;
    private AsyncTask mDetailRowBuilderTask;
    private AsyncTask mRefreshCollectionBitmapTask;

    private ArchosDetailsOverviewRowPresenter mOverviewRowPresenter;
    private CollectionDetailsDescriptionPresenter mDescriptionPresenter;

    private Overlay mOverlay;
    private int mColor;
    private Handler mHandler;
    private int oldPos = 0;
    private int oldSelectedSubPosition = 0;
    private boolean mHasDetailRow;

    private boolean mShouldDisplayConfirmDelete = false;

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
        log.debug("onCreate");
        super.onCreate(savedInstanceState);
        // pass the right deleteLauncher linked to activity
        FileUtilsQ.setDeleteLauncher(deleteLauncher);
        Object transition = TransitionHelper.getEnterTransition(getActivity().getWindow());
        if(transition!=null) {
            TransitionHelper.addTransitionListener(transition, new TransitionListener() {
                @Override
                public void onTransitionStart(Object transition) {
                    mOverlay.hide();
                }

                @Override
                public void onTransitionEnd(Object transition) {
                    mOverlay.show();
                }
            });
        }

        setTopOffsetRatio(0.6f);

        Intent intent = getActivity().getIntent();
        mCollection = (Collection) intent.getSerializableExtra(EXTRA_COLLECTION);
        if (mCollection != null) mCollectionId = mCollection.getCollectionId();
        else mCollectionId = intent.getLongExtra(EXTRA_COLLECTION_ID, -1);

        refreshCollection();

        log.debug("onCreate: " + mCollection.getName());

        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
        mHandler = new Handler();
        mDescriptionPresenter = new CollectionDetailsDescriptionPresenter();
        mOverviewRowPresenter = new ArchosDetailsOverviewRowPresenter(mDescriptionPresenter);
        //be aware of a hack to avoid fullscreen overview : cf onSetRowStatus
        FullWidthDetailsOverviewSharedElementHelper helper = new FullWidthDetailsOverviewSharedElementHelper();
        helper.setSharedElementEnterTransition(getActivity(), SHARED_ELEMENT_NAME, 1000);
        mOverviewRowPresenter.setListener(helper);
        mOverviewRowPresenter.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background));
        mOverviewRowPresenter.setActionsBackgroundColor(getDarkerColor(ContextCompat.getColor(getActivity(), R.color.leanback_details_background)));
        mOverviewRowPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == CollectionActionAdapter.ACTION_PLAY) {
                    playMovie();
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_WATCHED) {
                    log.debug("mOverviewRowPresenter.setOnActionClickedListener: action watched, collection watched ? " + mCollection.isWatched());
                    if (!mCollection.isWatched()) {
                        DbUtils.markAsRead(getActivity(), mCollection);
                        refreshCollection();
                        if (mRefreshCollectionBitmapTask != null) mRefreshCollectionBitmapTask.cancel(true);
                        mRefreshCollectionBitmapTask = new RefreshCollectionBitmapTask().execute(mCollection);
                        refreshActivity();
                    }
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_MARK_COLLECTION_AS_NOT_WATCHED) {
                    log.debug("mOverviewRowPresenter.setOnActionClickedListener: action not watched, collection watched ? " + mCollection.isWatched());
                    if (mCollection.isWatched()) {
                        DbUtils.markAsNotRead(getActivity(), mCollection);
                        refreshCollection();
                        if (mRefreshCollectionBitmapTask != null) mRefreshCollectionBitmapTask.cancel(true);
                        mRefreshCollectionBitmapTask = new RefreshCollectionBitmapTask().execute(mCollection);
                        refreshActivity();
                    }
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_DELETE) {
                    mShouldDisplayConfirmDelete = true;
                    ((CollectionActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(mCollection, mShouldDisplayConfirmDelete);
                }
                else if (action.getId() == CollectionActionAdapter.ACTION_CONFIRM_DELETE) {
                    ArrayList<Uri> uris = new ArrayList<Uri>();
                    for(String filePath : DbUtils.getFilePaths(getActivity(), mCollection)) {
                        Uri uri = VideoUtils.getFileUriFromMediaLibPath(filePath);
                        uris.add(uri);
                    }
                    delete = new Delete(CollectionFragment.this, getActivity());
                    deleteUrisList = uris;
                    if (uris.size() == 1)
                        delete.startDeleteProcess(uris.get(0));
                    else if (uris.size() > 1)
                        delete.startMultipleDeleteProcess(uris);
                    mShouldDisplayConfirmDelete = false;
                    refreshActivity();
                }
            }
        });

        ClassPresenterSelector ps = new ClassPresenterSelector();
        ps.addClassPresenter(DetailsOverviewRow.class, mOverviewRowPresenter);
        ps.addClassPresenter(ListRow.class, new ListRowPresenter());

        mRowsAdapter = new ArrayObjectAdapter(ps);
        mHasDetailRow = false;

        // WORKAROUND: at least one instance of BackdropTask must be created soon in the process (onCreate ?)
        // else it does not work later.
        // --> This instance of BackdropTask() will not be used but it must be created here!
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor));

        setOnItemViewClickedListener(new OnItemViewClickedListener() {
            @Override
            public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
                if (item instanceof Video) {
                    //animate only if episode picture isn't displayed
                    boolean animate =!((item instanceof Video)&&((Video)item).getPosterUri()!=null);
                    VideoViewClickedListener.showVideoDetails(getActivity(), (Video) item, itemViewHolder, animate, false, false, -1, CollectionFragment.this, REQUEST_CODE_VIDEO);
                }
            }
        });
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
                                delete = new Delete(CollectionFragment.this, activity);
                                deleteUrisList = Collections.singletonList(folder);
                                delete.deleteFolder(folder);
                            }
                        });
                b.setOnCancelListener(dialogInterface -> sendDeleteResult(videoFile));
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

    private void playMovie() {
        log.debug("playMovie");
        if (mMovieCollectionAdapter != null) {
            Movie resumeMovie = null;
            Movie firstMovie = null;
            int i = 0;
            while(i < mMovieCollectionAdapter.getCount() && resumeMovie == null) {
                Movie movie = (Movie)mMovieCollectionAdapter.getItem(i);
                if (movie.getResumeMs() != PlayerActivity.LAST_POSITION_END && resumeMovie == null) {
                    resumeMovie = movie;
                }
                if (firstMovie == null)
                    firstMovie = movie;
                i++;
            }
            if (resumeMovie != null)
                PlayUtils.startVideo(getActivity(), (Video)resumeMovie, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
            else if (firstMovie != null)
                PlayUtils.startVideo(getActivity(), (Video)firstMovie, PlayerActivity.RESUME_FROM_LAST_POS, false, -1, null, -1);
        }
    }

    private int getDarkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f;
        return Color.HSVToColor(hsv);
    }

    //hack to avoid fullscreen overview
    @Override
    protected void onSetRowStatus(RowPresenter presenter, RowPresenter.ViewHolder viewHolder, int
            adapterPosition, int selectedPosition, int selectedSubPosition) {
        super.onSetRowStatus(presenter, viewHolder, adapterPosition, selectedPosition, selectedSubPosition);
        if(selectedPosition == 0 && selectedSubPosition != 0) {
            if (oldPos == 0 && oldSelectedSubPosition == 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setSelectedPosition(1);
                    }
                });
            } else if (oldPos == 1) {
                setSelectedPosition(1);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setSelectedPosition(0);
                    }
                });
            }
        }
        oldPos = selectedPosition;
        oldSelectedSubPosition = selectedSubPosition;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        log.debug("onViewCreated");
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);
    }

    @Override
    public void onDestroyView() {
        log.debug("onDestroyView");
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onStop() {
        log.debug("onStop");
        mBackdropTask.cancel(true);
        if (mDetailRowBuilderTask!=null) {
            mDetailRowBuilderTask.cancel(true);
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        log.debug("onResume");
        super.onResume();
        mOverlay.resume();

        // Load the details view
        if (mDetailRowBuilderTask != null) {
            mDetailRowBuilderTask.cancel(true);
        }
        mDetailRowBuilderTask = new DetailRowBuilderTask().execute(mCollection);

        // Launch backdrop task in BaseTags-as-arguments mode
        if (mBackdropTask!=null) {
            mBackdropTask.cancel(true);
        }
        // what we need here is the backdrop i.e. the image generated
        mBackdropTask = new BackdropTask(getActivity(), VideoInfoCommonClass.getDarkerColor(mColor)).execute(mCollection);

        // Start loading the list of seasons
        LoaderManager.getInstance(CollectionFragment.this).restartLoader(COLLECTION_LOADER_ID, null, CollectionFragment.this);
    }

    @Override
    public void onPause() {
        log.debug("onPause");
        super.onPause();
        mOverlay.pause();
    }

    /**
     * Getting RESULT_OK from REQUEST_CODE_MORE_DETAILS means that the poster and/or the backdrop has been changed
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        log.debug("onActivityResult requestCode " + requestCode);
        if ((requestCode == REQUEST_CODE_MARK_WATCHED || requestCode == REQUEST_CODE_VIDEO) && resultCode == Activity.RESULT_OK) {
            log.debug("onActivityResult processing requestCode, first refreshCollection");
            refreshCollection();
            refreshActivity();
        } else {
            log.debug("onActivityResult NOT processing requestCode");
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle bundle) {
        log.debug("onCreateLoader");
        return new MovieCollectionLoader(getActivity(), mCollection.getCollectionId());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (getActivity() == null) return;
        log.debug("onLoadFinished: mRowsAdapter size " + mRowsAdapter.size());

        CursorObjectAdapter movieCollectionAdapter = new CursorObjectAdapter(new PosterImageCardPresenter(getActivity(), PosterImageCardPresenter.EpisodeDisplayMode.FOR_SEASON_LIST));
        movieCollectionAdapter.setMapper(new CompatibleCursorMapperConverter(new VideoCursorMapper()));
        ListRow row = new ListRow(1,
                new HeaderItem(1, getString(R.string.movies)),
                movieCollectionAdapter);

        // replace row if exists
        if (mRowsAdapter.size() <2 ) mRowsAdapter.add(row);
        else mRowsAdapter.replace(1, row);

        if (movieCollectionAdapter != null) movieCollectionAdapter.changeCursor(cursor);

        mMovieCollectionAdapter = new MovieCollectionAdapter(getContext(), cursor);
        log.debug("onLoadFinished: movie collection cursor size " + cursor.getCount());
        if (cursor.getCount() == 0) // no more movies in collection
            getActivity().finish();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }

    private class DetailRowBuilderTask extends AsyncTask<Collection, Void, Pair<Collection, Bitmap>> {

        @Override
        protected Pair<Collection, Bitmap> doInBackground(Collection... collections) {
            log.debug("DetailRowBuilderTask.doInBackground collectionS length " + collections.length);
            Collection collection = collections[0];
            log.debug("DetailRowBuilderTask.doInBackground collection " + collection.getName());
            Bitmap bitmap = generateCollectionBitmap(collection.getPosterUri(), collection.isWatched());
            return new Pair<>(collection, bitmap);
        }

        @Override
        protected void onPostExecute(Pair<Collection, Bitmap> result) {
            Collection collection = result.first;
            Bitmap bitmap = result.second;

            // Buttons
            if (mDetailsOverviewRow == null) {
                mDetailsOverviewRow = new DetailsOverviewRow(collection);
                mDetailsOverviewRow.setActionsAdapter(new CollectionActionAdapter(getActivity(), collection, mShouldDisplayConfirmDelete));
            }
            else {
                mDetailsOverviewRow.setItem(collection);
            }

            if (bitmap!=null) {
                mOverviewRowPresenter.updateBackgroundColor(mColor);
                mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));
                mDetailsOverviewRow.setImageBitmap(getActivity(), bitmap);
                mDetailsOverviewRow.setImageScaleUpAllowed(true);
            }
            else {
                mDetailsOverviewRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_video));
                mDetailsOverviewRow.setImageScaleUpAllowed(false);
            }

            log.debug("mHasDetailRow = " + mHasDetailRow);
            if (!mHasDetailRow) {
                log.debug("mHasDetailRow is false adding detailOverviewRow");
                BackgroundManager.getInstance(getActivity()).setDrawable(new ColorDrawable(VideoInfoCommonClass.getDarkerColor(mColor)));
                mRowsAdapter.add(INDEX_DETAILS, mDetailsOverviewRow);
                setAdapter(mRowsAdapter);
                mHasDetailRow = true;
            } else {
                mRowsAdapter.replace(INDEX_DETAILS, mDetailsOverviewRow);
                setAdapter(mRowsAdapter);
            }
        }
    }

    public void onKeyDown(int keyCode) {
        int direction = -1;

        switch (keyCode) {
            case KeyEvent.KEYCODE_MENU:
                setSelectedPosition(0);
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                playMovie();
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                direction = Gravity.RIGHT;
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                direction = Gravity.LEFT;
                break;
        }

        if (direction != -1) {
            CursorLoader loader = null;
            if (mCollection != null) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String sortOrder = prefs.getString(AllCollectionsGridFragment.SORT_PARAM_KEY, CollectionsSortOrderEntries.DEFAULT_SORT);
                boolean showWatched = prefs.getBoolean(AllCollectionsGridFragment.COLLECTION_WATCHED_KEY, true);
                loader = new AllCollectionsLoader(getActivity(), sortOrder, showWatched);
            }
            if (loader != null) {
                // Using a CursorLoader but outside of the LoaderManager : need to make sure the Looper is ready
                if (Looper.myLooper()==null) Looper.prepare();
                Cursor c = loader.loadInBackground();
                Collection collection = null;
                for (int i = 0; i < c.getCount(); i++) {
                    c.moveToPosition(i);
                    Collection mc = (Collection)new CompatibleCursorMapperConverter(new CollectionCursorMapper()).convert(c);
                    if (mc.getCollectionId() == mCollection.getCollectionId()) {
                        if (direction == Gravity.LEFT) {
                            if (i - 1 >= 0)
                                c.moveToPosition(i - 1);
                            else
                                c.moveToPosition(c.getCount() - 1);
                        }
                        else if (direction == Gravity.RIGHT) {
                            if (i + 1 <= c.getCount() - 1)
                                c.moveToPosition(i + 1);
                            else
                                c.moveToPosition(0);
                        }
                        Collection nc = (Collection)new CompatibleCursorMapperConverter(new CollectionCursorMapper()).convert(c);
                        if (nc.getCollectionId() != mc.getCollectionId())
                            collection = nc;
                        break;
                    }
                }
                c.close();
                if (collection != null) {
                    if (direction == Gravity.LEFT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.RIGHT));
                    else if (direction == Gravity.RIGHT)
                        getActivity().getWindow().setExitTransition(new Slide(Gravity.LEFT));
                    final Intent intent = new Intent(getActivity(), CollectionActivity.class);
                    intent.putExtra(CollectionFragment.EXTRA_COLLECTION, collection);
                    intent.putExtra(CollectionActivity.SLIDE_TRANSITION_EXTRA, true);
                    intent.putExtra(CollectionActivity.SLIDE_DIRECTION_EXTRA, direction);
                    // Launch next activity with slide animation
                    // Starting from lollipop we need to give an empty "SceneTransitionAnimation" for this to work
                    mOverlay.hide(); // hide the top-right overlay else it slides across the screen!
                    startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(getActivity()).toBundle());
                    // Delay the finish the "old" activity, else it breaks the animation
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            Activity activity = getActivity();
                            if (activity != null) activity.finish(); // better safe than sorry
                        }
                    }, 1000);
                }
            }
        }
    }

    private void refreshCollection() {
        // TODO MARC
        if (mCollectionId != -1) {
            // CollectionLoader is a CursorLoader
            CollectionLoader collectionLoader = new CollectionLoader(getActivity(), mCollectionId);
            Cursor cursor = collectionLoader.loadInBackground();
            if(cursor != null && cursor.getCount()>0) {
                log.debug("refreshCollection DatabaseUtils.dumpCursorToString(cursor)");
                cursor.moveToFirst();
                CollectionCursorMapper collectionCursorMapper = new CollectionCursorMapper();
                collectionCursorMapper.bindColumns(cursor);
                mCollection = (Collection) collectionCursorMapper.bind(cursor);
                cursor.close();
            } else {
                mCollection = null;
            }
        }
    }

    private void refreshActivity() {
        if (mCollection != null) {
            log.debug("refreshActivity: collection is not empty " + mCollection.getMovieCollectionCount());
            ((CollectionActionAdapter)mDetailsOverviewRow.getActionsAdapter()).update(mCollection, mShouldDisplayConfirmDelete);
            mDetailsOverviewRow.setItem(mCollection);
        } else {
            log.debug("refreshActivity: collection is null exit!");
            getActivity().finish();
        }
    }

    private Bitmap generateCollectionBitmap(Uri posterUri, boolean isWatched) {
        Bitmap bitmap = null;
            try {
                if (posterUri != null) {
                    bitmap = Picasso.get()
                            .load(posterUri)
                            .noFade() // no fade since we are using activity transition anyway
                            .resize(getResources().getDimensionPixelSize(R.dimen.poster_width), getResources().getDimensionPixelSize(R.dimen.poster_height))
                            .centerCrop()
                            .get();
                    log.debug("generateCollectionBitmap: "+bitmap.getWidth()+"x"+bitmap.getHeight()+" "+posterUri);
                }
            } catch (IOException e) {
                log.error("generateCollectionBitmap Picasso load exception", e);
            } catch (NullPointerException e) { // getDefaultPoster() may return null (seen once at least)
                log.error("generateCollectionBitmap doInBackground exception", e);
            } finally {
                if (bitmap!=null) {
                    Palette palette = Palette.from(bitmap).generate();
                    if (palette.getDarkVibrantSwatch() != null)
                        mColor = palette.getDarkVibrantSwatch().getRgb();
                    else if (palette.getDarkMutedSwatch() != null)
                        mColor = palette.getDarkMutedSwatch().getRgb();
                    else
                        mColor = ContextCompat.getColor(getActivity(), R.color.leanback_details_background);
                    if (isWatched)
                        bitmap = PresenterUtils.addWatchedMark(bitmap, getContext());
                }
            }
        return bitmap;
    }

    private class RefreshCollectionBitmapTask extends AsyncTask<Collection, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(Collection... collections) {
            Collection collection = collections[0];
            log.debug("RefreshCollectionBitmapTask.doInBackground collection " + collection.getName());
            Bitmap bitmap = generateCollectionBitmap(collection.getPosterUri(), collection.isWatched());
            return bitmap;
        }
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap!=null) {
                mOverviewRowPresenter.updateBackgroundColor(mColor);
                mOverviewRowPresenter.updateActionsBackgroundColor(getDarkerColor(mColor));
                mDetailsOverviewRow.setImageBitmap(getActivity(), bitmap);
                mDetailsOverviewRow.setImageScaleUpAllowed(true);
                if (mHasDetailRow) {
                    mRowsAdapter.replace(INDEX_DETAILS, mDetailsOverviewRow);
                    setAdapter(mRowsAdapter);
                }
            }
        }
    }
}
