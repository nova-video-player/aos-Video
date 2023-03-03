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

package com.archos.mediacenter.video.browser.filebrowsing.network;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.utils.HelpOverlayActivity;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.filebrowsing.BrowserByFolder;
import com.archos.mediacenter.video.browser.filebrowsing.ListingAdapter;
import com.archos.mediaprovider.NetworkScanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by alexandre on 29/10/15.
 */
public class BrowserByNetwork extends BrowserByFolder {

    private static final Logger log = LoggerFactory.getLogger(BrowserByNetwork.class);

    protected static final String SHARE_NAME = "shareName";
    private static final String TAG = BrowserByNetwork.class.getCanonicalName();
    public static final String KEY_NETWORK_BOOKMARKS = "network_bookmarks";
    private Menu mMenu;
    private ViewGroup mIndexFolderActionView;
    private int mHelpOverlayHorizontalOffset;
    private int mHelpOverlayVerticalOffset;
    private static final String SAMBA_INDEXING_HELP_OVERLAY_KEY = "samba_indexing_help_overlay";
    private HelpOverlayHandler mHelpOverlayHandler;
    private static final int MSG_START_HELP_OVERLAY = 1;

    boolean isHimselfIndexedFolder = false;
    boolean isCurrentDirectoryShortcut = false;
    boolean isCurrentDirectoryIndexed = false;

    protected String mShortcutPath;
    protected String mShortcutName;

    private void checkIfIsShortcut() {
        String uriStringWithoutCred = mCurrentDirectory.toString();
        isCurrentDirectoryIndexed = ShortcutDbAdapter.VIDEO.isHimselfOrAncestorShortcut(getActivity(), uriStringWithoutCred);
        isHimselfIndexedFolder = ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), uriStringWithoutCred) > 0;
        isCurrentDirectoryShortcut = (ShortcutDb.STATIC.isShortcut(getContext(), uriStringWithoutCred) != -1);
        log.debug("checkIfIsShortcut: isCurrentDirectoryIndexed=" + isCurrentDirectoryIndexed + ", isHimselfIndexedFolder=" + isHimselfIndexedFolder + ", isCurrentDirectoryShortcut=" + isCurrentDirectoryShortcut);
    }

    @Override
    protected Uri getDefaultDirectory() {
        return null;
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2>  list){
        super.onListingUpdate(list);
        if(mHelpOverlayHandler==null){
            mHelpOverlayHandler = new HelpOverlayHandler();
        }
        // Check if we need to display the help overlay
        if (UriUtils.isIndexable(mCurrentDirectory) &&
                !helpOverlayAlreadyActivated() && !mHelpOverlayHandler.hasMessages(MSG_START_HELP_OVERLAY)) {
            // Help overlay is suitable for the current folder and has never been requested yet => show help overlay
            mHelpOverlayHandler.sendEmptyMessageDelayed(MSG_START_HELP_OVERLAY, 200);
        }
    }

    protected void createShortcut(String shortcutPath, String shortcutName) {
        // Add the shortcut to the list (shortcut means indexed)
        ShortcutDbAdapter.Shortcut shortcut = new ShortcutDbAdapter.Shortcut(shortcutName,shortcutPath);
        ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), shortcut);

        String text = getString(R.string.indexed_folder_added, shortcutName);
        Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();

        // Send a scan request to MediaScanner
        NetworkScanner.scanVideos(mContext, shortcutPath);
        // Update the menu items
        getActivity().invalidateOptionsMenu();
    }

    public void addIndexedFolder(Uri currentDirectory, String name) {
        ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(name, currentDirectory.toString(), getFriendlyUri()));
    }

    @Override
    protected void setupAdapter(boolean createNewAdapter) {
        if (createNewAdapter || mBrowserAdapter == null) {
            mFilesAdapter = new AdapterByNetwork(getActivity().getApplicationContext(),
                    mItemList, mFullFileList) ;
            setPresenters(getActivity(), this, mFilesAdapter, mViewMode);
            mBrowserAdapter = mFilesAdapter;
        }
    }

    private void removeShortcut(String shortcutPath) {
        // Remove the shortcut from the list
        ShortcutDbAdapter.VIDEO.deleteShortcut(getActivity(), shortcutPath);
        String text = getString(R.string.indexed_folder_removed, shortcutPath);
        Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
        // Send a delete request to MediaScanner
        NetworkScanner.removeVideos(mContext, shortcutPath);
        // Update the menu items
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        // Check if the current file requires a context menu
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            log.error("onCreateContextMenu: bad menuInfo", e);
            return;
        }
        // This can be null sometimes, don't crash...
        if (info == null) {
            log.error("onCreateContextMenu: bad menuInfo");
            return;
        }

        int position = info.position;
        if (!isItemClickable(position))
            return;

        // Build the common entries
        super.onCreateContextMenu(menu, v, menuInfo);

        // Add the Samba specific entries
        ListingAdapter adapterCommon = (ListingAdapter) mBrowserAdapter;
        Object item = mFilesAdapter.getItem(info.position);
        Video video = null;
        MetaFile2 metaFile2 =null;
        if(item instanceof Video) {
            video = (Video) item;
            menu.setHeaderTitle(video.getName());
        }
        else if(item instanceof MetaFile2) {
            metaFile2 = (MetaFile2) item;
            menu.setHeaderTitle(metaFile2.getName());
        }
        if (metaFile2!=null&&metaFile2.isDirectory() && UriUtils.isIndexable(metaFile2.getUri())) {
            // Contextual menu for folders which do not correspond to a workgroup
            long id = ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), metaFile2.getUri().toString());

            if (id>=0) {
                // There is already a shortcut for this folder => suggest to remove it
                menu.add(0, R.string.remove_from_indexed_folders, 0, R.string.remove_from_indexed_folders);
            } else {
                // There is no shortcut for this folder yet => suggest to add one
                menu.add(0, R.string.add_to_indexed_folders, 0, R.string.add_to_indexed_folders);
            }
            // TODO unhide
            // menu.add(0, R.string.nfo_export_folder, 0, R.string.nfo_export_folder);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) menuItem.getMenuInfo();
        Video video = null;
        MetaFile2 metaFile2 =null;
        Object item = mFilesAdapter.getItem(menuInfo.position);
        if(item instanceof Video) {
            video = (Video) item;
        }
        else if(item instanceof MetaFile2) {
            metaFile2 = (MetaFile2) item;
        }
        switch (itemId) {
            case R.string.add_to_indexed_folders:
            case R.string.remove_from_indexed_folders:
                mShortcutPath = metaFile2.getUri().toString();
                mShortcutName = metaFile2.getName();
                log.debug("onContextItemSelected: mShortcutPath=" + mShortcutPath + ", mShortcutName=" + mShortcutName);
                if (itemId == R.string.add_to_indexed_folders) {
                    createShortcut(mShortcutPath, mShortcutName);
                } else {
                    removeShortcut(mShortcutPath);
                }
                // The indexed status of the selected folder has changed => redraw the list
                // in order to update the indexed symbol
                mArchosGridView.invalidateViews();
                return true;
        }
        return super.onContextItemSelected(menuItem);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret;
        switch (item.getItemId()) {
            case R.string.add_to_indexed_folders:
                // Handle this item when it is in the options menu
                mShortcutPath = mCurrentDirectory.toString();
                mShortcutName = FileUtils.getName(mCurrentDirectory);
                log.debug("onOptionsItemSelected: index folder mShortcutPath=" + mShortcutPath + ", mShortcutName=" + mShortcutName);
                createShortcut(mShortcutPath, mShortcutName);
                ret = true;
                break;
            case R.string.remove_from_indexed_folders:
            case R.string.remove_from_shortcuts:
                removeShortcut(mCurrentDirectory.toString());
                ret = true;
                break;
            case R.string.add_ssh_shortcut:
                mShortcutPath = mCurrentDirectory.toString();
                mShortcutName = getActionBarTitle();
                log.debug("onOptionsItemSelected: add as shortcut mShortcutPath=" + mShortcutPath + ", mShortcutName=" + mShortcutName);
                // have a dialog where shortcut name can be specified and add to library option too
                final View v = getActivity().getLayoutInflater().inflate(R.layout.ssh_shortcut_dialog_layout, null);
                ((EditText)v.findViewById(R.id.shortcut_name)).setText(Uri.parse(getFriendlyUri()).getLastPathSegment());
                boolean isCurrentDirectoryIndexed = ShortcutDbAdapter.VIDEO.isHimselfOrAncestorShortcut(getActivity(), mCurrentDirectory.toString());
                // if current folder is already indexed do not propose to index it in the dialog
                if (isCurrentDirectoryIndexed) {
                    v.findViewById(R.id.checkBox).setVisibility(View.INVISIBLE);
                } else {
                    ((CheckBox) v.findViewById(R.id.checkBox)).setChecked(false);
                    v.findViewById(R.id.checkBox).setVisibility(View.VISIBLE);
                }
                new AlertDialog.Builder(getActivity())
                        .setCancelable(false)
                        .setView(v)
                        .setTitle(R.string.ssh_shortcut_name)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.dismiss();
                                if (((CheckBox) v.findViewById(R.id.checkBox)).isChecked()) {
                                    NetworkScanner.scanVideos(getActivity(), mCurrentDirectory);
                                    addIndexedFolder(mCurrentDirectory, ((EditText) v.findViewById(R.id.shortcut_name)).getText().toString());
                                } else {
                                    ShortcutDb.STATIC.insertShortcut(getContext(), mCurrentDirectory, ((EditText) v.findViewById(R.id.shortcut_name)).getText().toString(), getFriendlyUri());
                                }
                                getActivity().invalidateOptionsMenu();
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                            }
                        })
                        .setNegativeButton(android.R.string.cancel,new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.cancel();
                            }
                        }).create().show();
                ret = true;
                break;
            case R.string.manually_create_share:
                CreateShareDialog shareDialog = new CreateShareDialog();
                shareDialog.setRetainInstance(true); // the dialog is dismissed at screen rotation, that's better than a crash...
                shareDialog.show(getParentFragmentManager(), "CreateShareDialog");
            case R.string.rescan:
                NetworkScanner.scanVideos(mContext, mCurrentDirectory);
                return true;
            default:
                ret = super.onOptionsItemSelected(item);
                break;
        }
        return ret;
    }

    protected String getFriendlyUri() {
        log.debug("getFriendlyUri=" + mCurrentDirectory.toString());
        return mCurrentDirectory.toString();
    }

    private View.OnClickListener mIndexFolderActionClickListener = new View.OnClickListener() {
        // Handle clicks on the "indexed folder" item when it is in displayed the action bar
        public void onClick(View v) {
            mShortcutPath = mCurrentDirectory.toString();
            mShortcutName = getActionBarTitle();
            log.debug("mIndexFolderActionClickListener: mShortcutPath=" + mShortcutPath + ", mShortcutName=" + mShortcutName);
            createShortcut(mShortcutPath, mShortcutName);
        }
    };
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mIndexFolderActionView = (ViewGroup)getActivity().getLayoutInflater().inflate(R.layout.action_bar_menu_item_index_folder, null, false);
        if (mIndexFolderActionView != null) {
            mIndexFolderActionView.setOnClickListener(mIndexFolderActionClickListener);
        }

        mHelpOverlayHorizontalOffset = getActivity().getResources().getDimensionPixelSize(R.dimen.help_overlay_horizontal_offset);
        mHelpOverlayVerticalOffset = getActivity().getResources().getDimensionPixelSize(R.dimen.help_overlay_vertical_offset);
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mMenu = menu;
        if (getFileAndFolderSize() == 0)
            mMenu.setGroupVisible(MENU_SUBLOADER_GROUP, false);

        // Enable/Disable the items related to folder indexing
        MenuItem addFolderMenuItem = menu.findItem(R.string.add_to_indexed_folders);
        MenuItem addShortcutMenuItem = menu.findItem(R.string.add_ssh_shortcut);
        MenuItem removeFolderMenuItem = menu.findItem(R.string.remove_from_indexed_folders);
        MenuItem removeShortcutMenuItem = menu.findItem(R.string.remove_from_shortcuts);
        MenuItem rescanFolderMenuItem = menu.findItem(R.string.rescan);
        if(removeFolderMenuItem!=null&&addFolderMenuItem!=null) {
            if (!UriUtils.isIndexable(mCurrentDirectory)) {
                // No possible actions at the root and workgroup levels
                addFolderMenuItem.setVisible(false);
                removeFolderMenuItem.setVisible(false);
                rescanFolderMenuItem.setVisible(false);
                addShortcutMenuItem.setVisible(false);
                removeShortcutMenuItem.setVisible(false);
            } else {
                // Check if the current folder or one of its ancestor is indexed
                checkIfIsShortcut();
                log.debug("onPrepareOptionsMenu: isCurrentDirectoryIndexed=" + isCurrentDirectoryIndexed + ", isHimselfIndexedFolder=" + isHimselfIndexedFolder + ", isCurrentDirectoryShortcut=" + isCurrentDirectoryShortcut);
                // If the current folder is indexed => show the "unindex folder" item and do not show "remove the shortcut" item
                // If the current folder is not indexed and none of its ancestors is indexed => show the "add as shortcut" item
                // If the current folder is not indexed but one of its ancestors is indexed => show "add as shortcut" item and do not propose to index folder in add shortcut dialog
                // If the current folder is indexed => show the "rescan" item
                // If the current folder is a shortcut but not indexed => show the "index folder"
                addFolderMenuItem.setVisible(isCurrentDirectoryShortcut && (! isCurrentDirectoryIndexed));
                rescanFolderMenuItem.setVisible(isHimselfIndexedFolder);
                removeFolderMenuItem.setVisible(isHimselfIndexedFolder);
                addShortcutMenuItem.setVisible((!isCurrentDirectoryShortcut) && (! isCurrentDirectoryIndexed));
                removeShortcutMenuItem.setVisible(isCurrentDirectoryShortcut && (! isHimselfIndexedFolder));
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem IndexFolderMenuItem = menu.add(0, R.string.add_to_indexed_folders, Menu.NONE, R.string.add_to_indexed_folders);
        IndexFolderMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT | MenuItem.SHOW_AS_ACTION_IF_ROOM);
        IndexFolderMenuItem.setActionView(mIndexFolderActionView);
        menu.add(0,R.string.add_ssh_shortcut, 0,R.string.add_ssh_shortcut);
        menu.add(0, R.string.remove_from_indexed_folders, Menu.NONE, R.string.remove_from_indexed_folders).setIcon(R.drawable.ic_menu_video_unindex).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_WITH_TEXT | MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, R.string.rescan, Menu.NONE, R.string.rescan);
        menu.add(0,R.string.remove_from_shortcuts, 0,R.string.remove_from_shortcuts).setShowAsAction(
                MenuItem.SHOW_AS_ACTION_WITH_TEXT | MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }

    private boolean helpOverlayAlreadyActivated() {
        return mPreferences.getBoolean(SAMBA_INDEXING_HELP_OVERLAY_KEY, false);
    }

    private class HelpOverlayHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_START_HELP_OVERLAY && mIndexFolderActionView != null) {
                // Get the size of the action bar item
                int itemWidth = mIndexFolderActionView.getWidth();
                int itemHeight = mIndexFolderActionView.getHeight();

                // Make sure the item is currently displayed in the action bar
                // (the size is 0x0 if the item is in the options menu)
                if (itemWidth > 0 && itemHeight > 0) {
                    // Get the position of the action bar item
                    int[] location = new int[2];
                    mIndexFolderActionView.getLocationOnScreen(location);

                    // Get the size of the window which will provide the height of the statusbar
                    // if it is displayed at the top of the screen
                    Rect windowFrame = new Rect();
                    mIndexFolderActionView.getWindowVisibleDisplayFrame(windowFrame);
                    int windowWidth = windowFrame.right - windowFrame.left;
                    int windowHeight = windowFrame.bottom - windowFrame.top;
                    int statusbarHeight = windowFrame.top;

                    // Compute a target area a bit bigger than the item itself
                    int left = location[0] - mHelpOverlayHorizontalOffset;
                    int top = location[1] - mHelpOverlayVerticalOffset - statusbarHeight;
                    int right = location[0] + itemWidth + mHelpOverlayHorizontalOffset;
                    int bottom = location[1] + itemHeight + mHelpOverlayVerticalOffset - statusbarHeight;

                    // Check the target area bounds
                    if (right > windowWidth) {
                        // When the item is aligned with the right edge of the window there is no room for adding
                        // an horizontal offset to the right so don't add it to the left side either for symetrical reason
                        right = windowWidth;
                        left = location[0];
                    }
                    if (left < 0) {
                        left = 0;
                    }
                    if (top < 0) {
                        top = 0;
                    }

                    // Start the help overlay activity with the selected target area
                    Intent hov = new Intent(Intent.ACTION_MAIN);
                    hov.setComponent(new ComponentName(getActivity(), HelpOverlayActivity.class));
                    hov.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_LEFT, left);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_TOP, top);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_RIGHT, right);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_BOTTOM, bottom);
                    hov.putExtra(HelpOverlayActivity.EXTRA_POPUP_CONTENT_LAYOUT_ID, R.layout.help_overlay_network_indexing);
                    getActivity().startActivity(hov);

                    // Remember that the help overlay has been activated so that it won't be shown again in the future
                    SharedPreferences.Editor ed = mPreferences.edit();
                    ed.putBoolean(SAMBA_INDEXING_HELP_OVERLAY_KEY, true);
                    ed.commit();
                }
            }
        }
    }

}
