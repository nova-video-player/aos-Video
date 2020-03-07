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
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.DetailsSupportFragment;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.DetailsOverviewRow;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import android.view.View;
import android.widget.Toast;

import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.adapter.object.Shortcut;
import com.archos.mediacenter.video.leanback.details.ArchosDetailsOverviewRowPresenter;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.presenter.ShortcutDetailsPresenter;
import com.archos.mediaprovider.NetworkScanner;


public class NetworkShortcutDetailsFragment extends DetailsSupportFragment implements OnActionClickedListener {

    private static final String TAG = "NetworkShortcutDetailsFragment";

    public static final String SHARED_ELEMENT_NAME = "hero";

    public static final String EXTRA_SHORTCUT = "SHORTCUT";

    private static final int ACTION_REINDEX = 0;
    protected static final int ACTION_OPEN = 1;
    protected static final int ACTION_REMOVE = 2;

    protected Shortcut mShortcut;

    private ArchosDetailsOverviewRowPresenter mDetailsRowPresenter;

    private Overlay mOverlay;
    private Handler mHandler;
    private int oldPos = 0;
    private int oldSelectedSubPosition = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BackgroundManager bgMngr = BackgroundManager.getInstance(getActivity());
        bgMngr.attach(getActivity().getWindow());
        bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.leanback_background));

        mShortcut = (Shortcut)getActivity().getIntent().getSerializableExtra(EXTRA_SHORTCUT);

        DetailsOverviewRow detailRow = new DetailsOverviewRow(mShortcut);
        detailRow.setImageScaleUpAllowed(false);
        addActions(detailRow);
        mHandler = new Handler();
        mDetailsRowPresenter = new ArchosDetailsOverviewRowPresenter(new ShortcutDetailsPresenter());
        //be aware of a hack to avoid fullscreen overview : cf onSetRowStatus

        mDetailsRowPresenter.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.lightblue900));
        mDetailsRowPresenter.setActionsBackgroundColor(getDarkerColor(ContextCompat.getColor(getActivity(), R.color.lightblue900)));
        mDetailsRowPresenter.setOnActionClickedListener(this);

        ArrayObjectAdapter adapter = new ArrayObjectAdapter(mDetailsRowPresenter);
        adapter.add(detailRow);
        setAdapter(adapter);
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

    public void addActions(DetailsOverviewRow detailRow){
        detailRow.setActionsAdapter(new SparseArrayObjectAdapter() {
            @Override
            public int size() { return 3; }
            @Override
            public Object get(int position) {
                switch(position) {
                    case 0: return new Action(ACTION_OPEN, getResources().getString(R.string.open_indexed_folder));
                    case 1: return new Action(ACTION_REINDEX, getResources().getString(R.string.network_reindex));
                    case 2: return new Action(ACTION_REMOVE, getResources().getString(R.string.remove_from_indexed_folders));
                    default: return null;
                }
            }
        });
        detailRow.setImageDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.filetype_new_folder_indexed));
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
