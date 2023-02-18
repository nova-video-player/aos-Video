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

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.archos.customizedleanback.widget.MyTitleView;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingFragment;
import com.archos.mediaprovider.NetworkScanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by vapillon on 17/04/15.
 */
public class NetworkListingFragment extends ListingFragment {

    private static final Logger log = LoggerFactory.getLogger(NetworkListingFragment.class);

    private long mShortcutId;
    private boolean mAnAncestorIsShortcut;
    private boolean mUserHasNoShortcutAtAll = true;

    private boolean isCurrentDirectoryIndexed = false;
    private boolean isHimselfIndexedFolder = false;
    private boolean isCurrentDirectoryShortcut = false;

    @Override
    protected  ListingFragment instantiateNewFragment() {
        return new NetworkListingFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updateShortcutState();
    }

    private final View.OnClickListener mOrbClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            checkIfIsShortcut();
            if (isShortcut()) {
                deleteShortcut();
            } else {
                createShortcut();
                // asks if it is to be added immediately (i.e. index folder and not only add shortcut) if an ancestor is not indexed
                if (! mAnAncestorIsShortcut) askForIndexing();
            }
        }
    };

    private void checkIfIsShortcut() {
        final String currentUri = mUri.toString();
        mUserHasNoShortcutAtAll = (ShortcutDb.STATIC.numberOfShortcuts(getActivity()) == 0) && (ShortcutDbAdapter.VIDEO.numberOfShortcuts(getActivity()) == 0);
        mShortcutId = ShortcutDb.STATIC.isShortcut(getActivity(), currentUri);
        mAnAncestorIsShortcut = ShortcutDbAdapter.VIDEO.isHimselfOrAncestorShortcut(getActivity(), currentUri);
        isHimselfIndexedFolder = ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), currentUri) > 0;
        isCurrentDirectoryShortcut = (ShortcutDb.STATIC.isShortcut(getContext(), currentUri) != -1);
        log.debug("updateShortcutState: mUri=" + currentUri + " -> mShortcutId=" + mShortcutId + ", isCurrentDirectoryIndexed=" + isCurrentDirectoryIndexed + ", mAnAncestorIsShortcut=" + mAnAncestorIsShortcut + ", isCurrentDirectoryShortcut=" + isCurrentDirectoryShortcut);
    }

    /**
     * Check if the current folder is a shortcut or if one of his ancestor is.
     * Update the available actions (orbs) accordingly
     */
    protected void updateShortcutState() {
        checkIfIsShortcut();
        updateOrbIcon();
    }

    private boolean isShortcut() {
        return mShortcutId>=0;
    }

    private void updateOrbIcon() {
        final MyTitleView titleView = getTitleView();
        if (isShortcut()) { // shortcut -> remove shortcut (but also add to lib?)
            titleView.setOrb3IconResId(R.drawable.orb_minus);
            titleView.setOnOrb3ClickedListener(mOrbClickListener);
            titleView.setOnOrb3Description(getString(R.string.remove_from_shortcuts));
            titleView.hideHintMessage();
        } else { // not a shortcut
            if (isHimselfIndexedFolder) { // indexed folder -> remove from lib
                titleView.setOrb3IconResId(R.drawable.orb_minus);
                titleView.setOnOrb3ClickedListener(mOrbClickListener);
                titleView.setOnOrb3Description(getString(R.string.remove_from_indexed_folders));
                titleView.hideHintMessage();
            } else {
                if (!canBeIndexed()) {
                    titleView.setOnOrb3ClickedListener(null); // set null listener to hide the orb
                    titleView.hideHintMessage();
                } else { // not a shortcut, not indexed -> add shortcut but do not propose index if mAnAncestorIsShortcut
                    titleView.setOrb3IconResId(R.drawable.orb_plus);
                    titleView.setOnOrb3ClickedListener(mOrbClickListener);
                    titleView.setOnOrb3Description(getString(R.string.add_ssh_shortcut));
                    if (mUserHasNoShortcutAtAll) {
                        titleView.setAndShowHintMessage(getString(R.string.help_overlay_network_shortcut_text1_lb));
                    }
                }
            }
        }
    }

    /**
     * Returns true if this folder can be indexed
     * Is overridden by some child classes (UPnP)
     * @return
     */
    protected boolean canBeIndexed() {
        return (!isEmpty() && !mAnAncestorIsShortcut);
    }

    @Override
    public void onListingEnd() {
        super.onListingEnd();
        // Need to update the orb visibility once we know if the list is empty or not
        updateOrbIcon();
    }

    /** Add current Uri to the shortcut list */
    protected void createShortcut() {
        log.debug("createShortcut: ARG_TITLE=" + ARG_TITLE + ", argument ARG_TITLE=" + getArguments().getString(ARG_TITLE));
        String shortcutPath = mUri.toString();
        String shortcutName = getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment(); //to avoid name like "33" in upnp
        log.debug("createShortcut: shorcutName=" + shortcutName + ", shortcutPath=" + shortcutPath + ", lastPathSegment=" + mUri.getLastPathSegment());
        boolean result = ShortcutDb.STATIC.insertShortcut(getContext(), mUri, shortcutName);
        if (result) {
            Toast.makeText(getActivity(), getString(R.string.shortcut_folder_added, shortcutName), Toast.LENGTH_SHORT).show();
            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
            // Send a scan request to MediaScanner -> not for now because it needs to be confirmed first
            //NetworkScanner.scanVideos(getActivity(), shortcutPath);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        updateShortcutState();
    }

    private void askForIndexing() {
        new AlertDialog.Builder(getActivity()).setMessage(R.string.add_all_items_to_library).setTitle(mUri.getLastPathSegment()).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(FileUtils.getName(mUri), mUri.toString()));
                NetworkScanner.scanVideos(getActivity(), mUri);
            }
        }).setNegativeButton(R.string.no, null).show();
    }

    /** Remove current Uri from the shortcut list */
    private void deleteShortcut() {
        String shortcutPath = mUri.toString();
        String shortcutName = getArguments().getString(ARG_TITLE)!=null?getArguments().getString(ARG_TITLE):mUri.getLastPathSegment(); //to avoid name like "33" in upnp

        boolean result = ShortcutDbAdapter.VIDEO.deleteShortcut(getActivity(), mUri.toString());
        if (result) {
            Toast.makeText(getActivity(), getString(R.string.indexed_folder_removed, shortcutName), Toast.LENGTH_SHORT).show();
            getActivity().setResult(NetworkRootFragment.RESULT_CODE_SHORTCUTS_MODIFIED);
            // Tell MediaScanner to remove the videos from this directory
            NetworkScanner.removeVideos(getActivity(), mUri);
        }
        else {
            Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
        }
        updateShortcutState();
    }
}