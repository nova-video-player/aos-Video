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

package com.archos.mediacenter.video.leanback.filebrowsing;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.archos.customizedleanback.widget.MyTitleView;
import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.BlacklistedDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.Blacklist;
import com.archos.mediaprovider.video.VideoStoreImportService;

import io.sentry.SentryLevel;

/**
 * Created by vapillon on 17/04/15.
 */
public class LocalListingFragment extends ListingFragment {

    private static final String TAG = "LocalListingFragment";

    private long mBlacklistedId;
    private boolean mAnAncestorIsBlacklisted;

    @Override
    protected  ListingFragment instantiateNewFragment() {
        return new LocalListingFragment();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateBlacklistedState();
    }

    private final View.OnClickListener mOrbClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (isBlacklisted()) {
                deleteBlacklisted();
            } else {
                createBlacklisted();
            }
        }
    };

    /**
     * Check if the current folder is blacklisted or if one of his ancestor is.
     * Update the available actions (orbs) accordingly
     */
    protected void updateBlacklistedState() {
        final String currentUri = mUri.toString();

        Cursor c = BlacklistedDbAdapter.VIDEO.queryAllBlacklisteds(getActivity());
        final int pathColumn = c.getColumnIndexOrThrow(BlacklistedDbAdapter.KEY_PATH);
        final int idColumn = c.getColumnIndexOrThrow(BlacklistedDbAdapter.KEY_ROWID);

        mAnAncestorIsBlacklisted = false; // reset value
        mBlacklistedId = -1; // reset value

        c.moveToFirst();
        while (!c.isAfterLast()) {
            String blacklistedPath = c.getString(pathColumn);
            if (blacklistedPath!=null && blacklistedPath.equals(mUri.toString())) {
                mBlacklistedId = c.getLong(idColumn);
            }
            // Check if this blacklisted Uri is ancestor of the current Uri
            if (currentUri.startsWith(blacklistedPath)) {
                mAnAncestorIsBlacklisted = true;
                //Log.d(TAG, "mAnAncestorIsBlacklisted="+mAnAncestorIsBlacklisted);
                break;
            }
            c.moveToNext();
        }
        c.close();

        updateOrbIcon();
    }

    private boolean isBlacklisted() {
        return mBlacklistedId>=0;
    }

    private void updateOrbIcon() {
        final MyTitleView titleView = getTitleView();
        if (isBlacklisted()) {
            titleView.setOrb3IconResId(R.drawable.orb_plus);
            titleView.setOnOrb3ClickedListener(mOrbClickListener);
            titleView.setOnOrb3Description(getString(R.string.add_to_indexed_folders));
        }
        else {
            if (!canBeUnindexed()) {
                titleView.setOnOrb3ClickedListener(null); // set null listener to hide the orb
            } else {
                titleView.setOrb3IconResId(R.drawable.orb_minus);
                titleView.setOnOrb3ClickedListener(mOrbClickListener);
                titleView.setOnOrb3Description(getString(R.string.remove_from_indexed_folders));
            }
        }
    }

    /**
     * Returns true if this folder can be indexed
     * @return
     */
    protected boolean canBeUnindexed() {
        return !mAnAncestorIsBlacklisted;
    }

    /** Add current Uri to the blacklisted list */
    protected void createBlacklisted() {

        String blacklistedPath = mUri.toString();
        String blacklistedName = getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment();
        boolean result = BlacklistedDbAdapter.VIDEO.addBlacklisted(getActivity(), new BlacklistedDbAdapter.Blacklisted(blacklistedPath));

        if (result) {
            Toast.makeText(getActivity(), getString(R.string.indexed_folder_removed, blacklistedName), Toast.LENGTH_SHORT).show();
            // Send a scan request to MediaScanner
            Intent serviceIntent = new Intent(getActivity(), VideoStoreImportService.class);
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "LocalListingFragment.createBlacklisted", "scan request VideoStoreImportService intent action ACTION_VIDEO_SCANNER_METADATA_UPDATE");
            serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE);
            serviceIntent.setData(mUri);
            getActivity().startService(serviceIntent);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        // update internal static blacklist
        Blacklist.updateBlacklisteds();
        updateBlacklistedState();
    }

    /** Remove current Uri from the blacklisted list */
    private void deleteBlacklisted() {
        String blacklistedPath = mUri.toString();
        String blacklistedName = getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment();

        boolean result = BlacklistedDbAdapter.VIDEO.deleteBlacklisted(getActivity(), mUri.toString());
        if (result) {
            Toast.makeText(getActivity(), getString(R.string.indexed_folder_added, blacklistedName), Toast.LENGTH_SHORT).show();
            ArchosUtils.addBreadcrumb(SentryLevel.INFO, "LocalListingFragment.deleteBlacklisted", "remove video from this directoty VideoStoreImportService intent action ACTION_VIDEO_SCANNER_METADATA_UPDATE");
            // Tell MediaScanner to remove the videos from this directory
            Intent serviceIntent = new Intent(getActivity(), VideoStoreImportService.class);
            serviceIntent.setAction(ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE);
            serviceIntent.setData(mUri);
            getActivity().startService(serviceIntent);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        // update internal static blacklist
        Blacklist.updateBlacklisteds();
        updateBlacklistedState();
    }
}