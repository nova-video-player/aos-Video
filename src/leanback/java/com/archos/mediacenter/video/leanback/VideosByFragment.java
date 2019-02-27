package com.archos.mediacenter.video.leanback;

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.database.CursorMapper;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.CursorObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.SearchOrbView;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediacenter.video.leanback.CompatibleCursorMapperConverter;
import com.archos.mediacenter.video.leanback.VideoViewClickedListener;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.loader.MoviesByLoader;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.browser.loader.MoviesSelectionLoader;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.PosterImageCardPresenter;
import com.archos.mediacenter.video.player.PrivateMode;

import java.util.ArrayList;


public abstract class VideosByFragment extends BrowseFragment  implements  LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "MoviesByFragment";

    private ArrayObjectAdapter mRowsAdapter;
    private Overlay mOverlay;
    private SharedPreferences mPrefs;
    protected TextView mEmptyView;

    private int mSortOrderItem;
    private String mSortOrder;

    /**
     * We can have a single instance of presenter and mapper used for all the subset rows created
     */
    private Presenter mVideoPresenter;
    private CursorMapper mVideoMapper;

    /**
     * keep a reference of the cursor containing the categories to check if there is actually an update when we get a new one
     */
    private Cursor mCurrentCategoriesCursor;

    /**
     * Map to update the adapter when we get the onLoadFinished() callback
     */
    SparseArray<CursorObjectAdapter> mAdaptersMap = new SparseArray<>();

    BackgroundManager bgMngr = null;

    abstract protected Loader<Cursor> getSubsetLoader(Context context);

    abstract protected CharSequence[] getSortOrderEntries();
    abstract protected String item2SortOrder(int item);
    abstract protected int sortOrder2Item(String sortOrder);
    abstract protected String getSortOrderParamKey();

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mOverlay = new Overlay(this);

        SearchOrbView searchOrbView = (SearchOrbView) getView().findViewById(R.id.title_orb);
        if (searchOrbView != null) {
            searchOrbView.setOrbIcon(getResources().getDrawable(R.drawable.orb_sort));
        } else {
            throw new IllegalArgumentException("Did not find R.id.title_orb in BrowseFragment! Need to update the orbview hack!");
        }

        ViewGroup container = (ViewGroup) getView().findViewById(R.id.browse_frame);
        if (container != null) {
            LayoutInflater.from(getActivity()).inflate(R.layout.leanback_empty_view, container, true);
            mEmptyView = (TextView) container.findViewById(R.id.empty_view);
            mEmptyView.setText(R.string.you_have_no_movies);
        } else {
            throw new IllegalArgumentException("Did not find R.id.browse_frame in BrowseFragment! Need to update the emptyview hack!");
        }
    }

    @Override
    public void onDestroyView() {
        mOverlay.destroy();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        // Save the sort mode
        mPrefs.edit().putString(getSortOrderParamKey(), mSortOrder).commit();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        mOverlay.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mSortOrder = mPrefs.getString(getSortOrderParamKey(), MoviesLoader.DEFAULT_SORT);

        Resources r = getResources();
        updateBackground();

        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(r.getColor(R.color.leanback_side));

        // set search icon color
        setSearchAffordanceColor(r.getColor(R.color.lightblueA200));

        setupEventListeners();

        RowPresenter rowPresenter = new ListRowPresenter();
        rowPresenter.setHeaderPresenter(new RowHeaderPresenter());
        mRowsAdapter = new ArrayObjectAdapter(rowPresenter);
        setAdapter(mRowsAdapter);

        mVideoPresenter = new PosterImageCardPresenter(getActivity());
        mVideoMapper = new CompatibleCursorMapperConverter(new VideoCursorMapper());

        getLoaderManager().initLoader(-1, null, this);
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {
            public void onClick(View view) {
                mSortOrderItem = sortOrder2Item(mSortOrder);
                new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(getSortOrderEntries(), mSortOrderItem, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mSortOrderItem != which) {
                                    mSortOrderItem = which;
                                    mSortOrder = item2SortOrder(mSortOrderItem);
                                    loadCategoriesRows(mCurrentCategoriesCursor);
                                }
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
        setOnItemViewClickedListener(new VideoViewClickedListener(getActivity()));
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == -1) {
            // List of categories
            return getSubsetLoader(getActivity());
        } else {
            // One of the row
            return new MoviesSelectionLoader(getActivity(), args.getString("ids"), args.getString("sort"));
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor c) {
        // List of categories
        if (cursorLoader.getId() == -1) {

            // Empty view visibility
            mEmptyView.setVisibility(c.getCount() > 0 ? View.GONE : View.VISIBLE);

            if (mCurrentCategoriesCursor != null) {
                if (!isCategoriesListModified(mCurrentCategoriesCursor, c)) {
                    // no actual modification, no need to rebuild all the rows
                    mCurrentCategoriesCursor = c; // keep the reference to the new cursor because the old one won't be valid anymore
                    return;
                }
            }
            mCurrentCategoriesCursor = c;
            loadCategoriesRows(c);
        }
        // One of the row
        else {
            CursorObjectAdapter adapter = mAdaptersMap.get(cursorLoader.getId());
            if (adapter != null) {
                adapter.changeCursor(c);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
    }


    private boolean isCategoriesListModified(Cursor oldCursor, Cursor newCursor) {

        // Modified for sure if has different length
        if (oldCursor.getCount() != newCursor.getCount()) {
            Log.d(TAG, "Difference found in the category list (size changed)");
            return true;
        }

        // these two column index are the same but it looks nicer like this :-)
        final int oldSubsetNameColumn = oldCursor.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_NAME);
        final int newSubsetNameColumn = newCursor.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_NAME);

        // Check all names
        oldCursor.moveToFirst();
        newCursor.moveToFirst();
        while (!oldCursor.isAfterLast() && !newCursor.isAfterLast()) {
            final String oldName = oldCursor.getString(oldSubsetNameColumn);
            final String newName = newCursor.getString(newSubsetNameColumn);
            if (oldName != null && !oldName.equals(newName)) {
                // difference found
                Log.d(TAG, "Difference found in the category list (" + oldName + " vs " + newName + ")");
                return true;
            }
            oldCursor.moveToNext();
            newCursor.moveToNext();
        }
        // no difference found
        Log.d(TAG, "No difference found in the category list");
        return false;
    }

    private void loadCategoriesRows(Cursor c) {
        int subsetIdColumn = c.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_ID);
        int subsetNameColumn = c.getColumnIndex(MoviesByLoader.COLUMN_SUBSET_NAME);
        int listOfMovieIdsColumn = c.getColumnIndex(MoviesByLoader.COLUMN_LIST_OF_MOVIE_IDS);

        mRowsAdapter.clear();
        mAdaptersMap.clear();

        // NOTE: A first version was using a CursorObjectAdapter for the rows.
        // The problem was that when any DB update occurred (resume point...) I found no way
        // to not update all the rows. Hence the selection position on the current row was lost.
        // I tried to not update but the older cursor was closed by the LoaderManager (I think), leading to crashes.
        // Solution implemented here is to "convert" the cursor into an array. No performance issue since the
        // number of categories is always quite limited (~100 max)

        // Build the array of categories from the cursor
        ArrayList<ListRow> rows = new ArrayList<>(c.getCount());
        c.moveToFirst();
        while(!c.isAfterLast())
        {
            int subsetId = (int) c.getLong(subsetIdColumn);
            String subsetName = c.getString(subsetNameColumn);
            String listOfMovieIds = c.getString(listOfMovieIdsColumn);

            // Build the row
            CursorObjectAdapter subsetAdapter = new CursorObjectAdapter(mVideoPresenter);
            subsetAdapter.setMapper(mVideoMapper);
            rows.add(new ListRow(subsetId, new HeaderItem(subsetName), subsetAdapter));
            mAdaptersMap.append(subsetId, subsetAdapter);

            // Start the loader manager for this row
            Bundle args = new Bundle();
            args.putString("ids", listOfMovieIds);
            args.putString("sort", mSortOrder);
            getLoaderManager().restartLoader(subsetId, args, this);

            c.moveToNext();
        }

        mRowsAdapter.addAll(0,rows);
    }

    private void updateBackground() {
        Resources r = getResources();

        bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached())
            bgMngr.attach(getActivity().getWindow());

        if (PrivateMode.isActive()) {
            bgMngr.setColor(r.getColor(R.color.private_mode));
            bgMngr.setDrawable(r.getDrawable(R.drawable.private_background));
        } else {
            bgMngr.setColor(r.getColor(R.color.leanback_background));
            bgMngr.setDrawable(new ColorDrawable(r.getColor(R.color.leanback_background)));
        }
    }
    
    public ArrayObjectAdapter getRowsAdapter() {
        return mRowsAdapter;
    }
}
