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

package com.archos.mediacenter.video.leanback.network;

import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.view.View;
import android.widget.Toast;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.adapter.object.Shortcut;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.presenter.ShortcutDetailsPresenter;
import com.archos.mediaprovider.NetworkScanner;


public class NetworkShortcutDetailsFragment extends DetailsFragment implements OnActionClickedListener {

    private static final String TAG = "NetworkShortcutDetailsFragment";

    public static final String SHARED_ELEMENT_NAME = "hero";

    public static final String EXTRA_SHORTCUT = "SHORTCUT";

    private static final int ACTION_REINDEX = 0;
    protected static final int ACTION_OPEN = 1;
    protected static final int ACTION_REMOVE = 2;

    protected Shortcut mShortcut;

    private FullWidthDetailsOverviewRowPresenter mDetailsRowPresenter;

    private Overlay mOverlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        bgMngr.attach(getActivity().getWindow());
        bgMngr.setColor(getResources().getColor(R.color.leanback_background));

        mShortcut = (Shortcut)getActivity().getIntent().getSerializableExtra(EXTRA_SHORTCUT);

        DetailsOverviewRow detailRow = new DetailsOverviewRow(mShortcut);
        detailRow.setImageScaleUpAllowed(false);
        addActions(detailRow);
        mDetailsRowPresenter = new FullWidthDetailsOverviewRowPresenter(new ShortcutDetailsPresenter());

        mDetailsRowPresenter.setBackgroundColor(getResources().getColor(R.color.lightblue900));
        mDetailsRowPresenter.setOnActionClickedListener(this);

        ArrayObjectAdapter adapter = new ArrayObjectAdapter(mDetailsRowPresenter);
        adapter.add(detailRow);
        setAdapter(adapter);
    }
    public void addActions(DetailsOverviewRow detailRow){
        detailRow.addAction(new Action(ACTION_OPEN, getResources().getString(R.string.open_indexed_folder)));
        detailRow.addAction(new Action(ACTION_REINDEX, getResources().getString(R.string.network_reindex)));
        detailRow.addAction(new Action(ACTION_REMOVE, getResources().getString(R.string.remove_from_indexed_folders)));
        detailRow.setImageDrawable(getResources().getDrawable(R.drawable.filetype_new_folder_indexed));
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
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // If the shortcut has been modified (removed) from the NetworkListingActivity launched by ACTION_OPEN,
        // we must forward the info to the root fragment
        if (requestCode==NetworkRootFragment.REQUEST_CODE_BROWSING && resultCode==NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED) {
            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
        }
        getActivity().finish();
    }

    protected void slightlyDelayedFinish() {
        getView().postDelayed(new Runnable() {
            @Override
            public void run() {
                getActivity().finish();
            }
        }, 200);
    }

    @Override
    public void onActionClicked(Action action) {

        if (action.getId() == ACTION_OPEN) {
            Intent intent = new Intent(getActivity(), ListingActivity.getActivityForUri(mShortcut.getUri()));
            intent.putExtra(ListingActivity.EXTRA_ROOT_URI, mShortcut.getUri());
            intent.putExtra(ListingActivity.EXTRA_ROOT_NAME, mShortcut.getName());
            startActivityForResult(intent, NetworkRootFragment.REQUEST_CODE_BROWSING);
        }
        else if (action.getId() == ACTION_REINDEX) {
            NetworkScanner.scanVideos(getActivity(), mShortcut.getUri());
            slightlyDelayedFinish();
        }
        else if (action.getId() == ACTION_REMOVE) {
            boolean result = ShortcutDbAdapter.VIDEO.deleteShortcut(getActivity(), mShortcut.getId());
            if (result) {
                Toast.makeText(getActivity(), getString(R.string.indexed_folder_removed, mShortcut.getName()), Toast.LENGTH_SHORT).show();
                // Send a delete request to MediaScanner
                NetworkScanner.removeVideos(getActivity(), mShortcut.getUri());
                // set caller result
                getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
            }
            else {
                Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
            }

            slightlyDelayedFinish();
        }

    }
}
