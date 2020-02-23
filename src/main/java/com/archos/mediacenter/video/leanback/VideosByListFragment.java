package com.archos.mediacenter.video.leanback;

import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.SearchOrbView;
import androidx.loader.content.Loader;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.VideoLoader;
import com.archos.mediacenter.video.browser.loader.VideosByListLoader;
import com.archos.mediacenter.video.browser.loader.VideosSelectionLoader;
import com.archos.mediaprovider.video.VideoStore;

import java.util.ArrayList;

public class VideosByListFragment extends VideosByFragment {

    private static final String SORT_PARAM_KEY = VideosByListFragment.class.getName() + "_SORT";

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setTitle(getString(R.string.video_lists));
        SearchOrbView searchOrbView = (SearchOrbView) getView().findViewById(R.id.title_orb);
        searchOrbView.setOrbIcon(ContextCompat.getDrawable(getActivity(), R.drawable.orb_minus));
        setOnSearchClickedListener(new View.OnClickListener() {
            public void onClick(View view) {
                ArrayList<String> names = new ArrayList<String>();

                for (int i = 0; i < getRowsAdapter().size(); i++) {
                    Row row = (Row)getRowsAdapter().get(i);

                    names.add(row.getHeaderItem().getName());
                }
                if (names.size() > 0) {
                    new AlertDialog.Builder(getActivity())
                            .setItems(names.toArray(new String[names.size()]), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Row row = (Row)getRowsAdapter().get(which);
                                    String id = String.valueOf(row.getId());

                                    getActivity().getContentResolver().delete(VideoStore.List.LIST_CONTENT_URI, VideoStore.List.Columns.ID + " = ?", new String[] {id});
                                    dialog.dismiss();
                                }
                            })
                            .create().show();
                }
            }
        });
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
            return new VideosSelectionLoader(getActivity(), args.getString("ids"), VideoLoader.DEFAULT_SORT);
        }
    }

}
