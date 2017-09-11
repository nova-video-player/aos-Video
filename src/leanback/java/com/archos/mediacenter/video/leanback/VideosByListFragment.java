package com.archos.mediacenter.video.leanback;

import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v17.leanback.widget.SearchOrbView;
import android.util.SparseArray;
import android.view.View;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.VideosByListLoader;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediacenter.video.leanback.movies.MoviesSortOrderEntry;


public class VideosByListFragment extends VideosByFragment {

    private static final String SORT_PARAM_KEY = VideosByListFragment.class.getName() + "_SORT";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(getString(R.string.video_lists));
        SearchOrbView searchOrbView = (SearchOrbView) getView().findViewById(R.id.title_orb);
        searchOrbView.setVisibility(View.INVISIBLE);
        setOnSearchClickedListener(null);
    }

    @Override
    protected Loader<Cursor> getSubsetLoader(Context context) {
        return new VideosByListLoader(context);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if(mEmptyView!=null)
            mEmptyView.setText(R.string.no_list_detected);
    }

    @Override
    protected CharSequence[] getSortOrderEntries() {
        return null;
    }

    @Override
    protected String item2SortOrder(int item) {
        return "";
    }

    @Override
    protected int sortOrder2Item(String sortOrder) {
        return 0;
    }

    @Override
    protected String getSortOrderParamKey() {
        return SORT_PARAM_KEY;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == -1) {
            // List of categories
            return getSubsetLoader(getActivity());
        } else {
            // One of the row
            return new VideosSelectionLoader(getActivity(), args.getString("ids"), args.getString("sort"));
        }
    }

}
