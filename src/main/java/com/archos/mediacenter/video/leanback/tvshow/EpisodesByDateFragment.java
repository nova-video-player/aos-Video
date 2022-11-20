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

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.SearchOrbView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.loader.EpisodesByDateLoader;
import com.archos.mediacenter.video.browser.loader.EpisodesNoAnimeByDateLoader;
import com.archos.mediacenter.video.browser.loader.EpisodesSelectionLoader;
import com.archos.mediacenter.video.leanback.VideosByFragment;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediaprovider.video.VideoStore;


public class EpisodesByDateFragment extends VideosByFragment {

    private static final String SORT_PARAM_KEY = EpisodesByDateFragment.class.getName() + "_SORT";
    private static final String VIEW_PARAM_KEY = EpisodesByDateFragment.class.getName() + "_VIEW";

    private SharedPreferences mPrefs;
    private int mDateView;
    private boolean mSeparateAnimeFromShowMovie;

    public EpisodesByDateFragment() {
        super(VideoStore.Video.VideoColumns.SCRAPER_E_AIRED);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDateView = mPrefs.getInt(VIEW_PARAM_KEY, 0);
        mSeparateAnimeFromShowMovie = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(VideoPreferencesCommon.KEY_SEPARATE_ANIME_MOVIE_SHOW, VideoPreferencesCommon.SEPARATE_ANIME_MOVIE_SHOW_DEFAULT);

        super.onActivityCreated(savedInstanceState);

        setTitle(getString(R.string.episodes_by_date));
        SearchOrbView searchOrbView = (SearchOrbView) getView().findViewById(R.id.title_orb);
        searchOrbView.setOrbIcon(ContextCompat.getDrawable(getActivity(), R.drawable.orb_list));
        setOnSearchClickedListener(new View.OnClickListener() {
            public void onClick(View view) {
                String[] names = { getString(R.string.date_view_week), getString(R.string.date_view_month), getString(R.string.date_view_year) };

                new AlertDialog.Builder(getActivity())
                        .setSingleChoiceItems(names, mDateView, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (mDateView != which) {
                                    mDateView = which;
                                    // Save the view mode
                                    mPrefs.edit().putInt(VIEW_PARAM_KEY, mDateView).commit();
                                    LoaderManager.getInstance(EpisodesByDateFragment.this).restartLoader(-1, null, EpisodesByDateFragment.this);
                                }
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == -1) {
            // List of categories
            return getSubsetLoader(getActivity());
        } else {
            // One of the row
            return new EpisodesSelectionLoader(getActivity(), args.getString("ids"), args.getString("sort"));
        }
    }

    @Override
    protected Loader<Cursor> getSubsetLoader(Context context) {
        if (mSeparateAnimeFromShowMovie) return new EpisodesNoAnimeByDateLoader(context, EpisodesNoAnimeByDateLoader.DateView.values()[mDateView]);
        else return new EpisodesByDateLoader(context, EpisodesByDateLoader.DateView.values()[mDateView]);
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

}
