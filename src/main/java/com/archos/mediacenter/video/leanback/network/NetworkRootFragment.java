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
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;

import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ClassPresenterSelector;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.app.ActivityOptionsCompat;
import android.view.View;

import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.filecorelibrary.samba.Share;
import com.archos.filecorelibrary.samba.Workgroup;
import com.archos.mediacenter.filecoreextension.upnp2.UpnpServiceManager;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.leanback.adapter.GenericNetworkShortcutMapper;
import com.archos.mediacenter.video.leanback.adapter.NetworkShortcutMapper;
import com.archos.mediacenter.video.leanback.adapter.object.Box;
import com.archos.mediacenter.video.leanback.adapter.object.GenericNetworkShortcut;
import com.archos.mediacenter.video.leanback.adapter.object.NetworkBrowse;
import com.archos.mediacenter.video.leanback.adapter.object.NetworkShortcut;
import com.archos.mediacenter.video.leanback.adapter.object.NetworkSource;
import com.archos.mediacenter.video.leanback.adapter.object.SmbShare;
import com.archos.mediacenter.video.leanback.adapter.object.UpnpServer;
import com.archos.mediacenter.video.leanback.filebrowsing.ListingActivity;
import com.archos.mediacenter.video.leanback.network.ftp.FtpShortcutDetailsActivity;
import com.archos.mediacenter.video.leanback.network.ftp.FtpShortcutDetailsFragment;
import com.archos.mediacenter.video.leanback.network.rescan.RescanActivity;
import com.archos.mediacenter.video.leanback.overlay.Overlay;
import com.archos.mediacenter.video.leanback.presenter.NetworkShortcutPresenter;
import com.archos.mediacenter.video.leanback.presenter.RescanBoxItemPresenter;
import com.archos.mediacenter.video.leanback.presenter.SmbSharePresenter;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediaprovider.video.NetworkScannerReceiver;

import org.fourthline.cling.model.meta.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Fragment displaying 3 rows : one for the shortcuts a.k.a. indexed folders ; one for the SMB discovered servers ; one for the UPnP discovered servers
 * Created by vapillon on 20/04/15.
 */
public class NetworkRootFragment extends BrowseSupportFragment {

    private static final Logger log = LoggerFactory.getLogger(NetworkRootFragment.class);

    public static final int DISCOVERY_REPEAT_DELAY_MS = 2000;

    public static final int REQUEST_CODE_DETAILS = 100;
    public static final int REQUEST_CODE_BROWSING = 101;
    public static final int RESULT_CODE_SHORTCUTS_MODIFIED = 1001;

    private ArrayObjectAdapter mRowsAdapter;
    private ArrayObjectAdapter mIndexedFoldersAdapter;
    private ArrayObjectAdapter mSmbDiscoveryAdapter;
    private ArrayObjectAdapter mUpnpDiscoveryAdapter;

    private ListRow mIndexedFoldersListRow;

    private ShortcutsLoaderTask mShortcutsLoaderTask;

    SambaDiscovery mSambaDiscovery;

    private Handler mDiscoveryRepeatHandler = new Handler();

    private Overlay mOverlay;
    private ArrayObjectAdapter mNetworkShortcutsAdapter;
    private NetworkShortcutsLoaderTask mNetworkShortcutsLoaderTask;

    // temp debug flag (to remove once re-scan feature is published)
    static boolean sDisplayRescanItem = false;

    BackgroundManager bgMngr = null;

    // temp debug method (to remove once re-scan feature is published)
    void displayRescanItem() {
        sDisplayRescanItem = true;
        mShortcutsLoaderTask = new ShortcutsLoaderTask();
        mShortcutsLoaderTask.execute();
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
        log.debug("onResume");
        mOverlay.resume();
        updateBackground();
        setTitle(getString(R.string.network_storage));
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(false);

        loadRows();

        // Launch the shortcuts loading async task
        mShortcutsLoaderTask = new ShortcutsLoaderTask();
        mShortcutsLoaderTask.execute();
        mNetworkShortcutsLoaderTask = new NetworkShortcutsLoaderTask(); //not in the same task because this isn't using the same cursor
        mNetworkShortcutsLoaderTask.execute();
    }

    @Override
    public void onPause() {
        super.onPause();
        mOverlay.pause();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.debug("onCreate");
        updateBackground();

        setTitle(getString(R.string.network_storage));
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(false);

        loadRows();

        // Launch the shortcuts loading async task
        mShortcutsLoaderTask = new ShortcutsLoaderTask();
        mShortcutsLoaderTask.execute();
        mNetworkShortcutsLoaderTask = new NetworkShortcutsLoaderTask(); //not in the same task because this isn't using the same cursor
        mNetworkShortcutsLoaderTask.execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mShortcutsLoaderTask.cancel(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Start SMB
        mDiscoveryRepeatHandler.post(mSmbDiscoveryRepeat);

        // Start UPnP
        UpnpServiceManager
            .startServiceIfNeeded(getActivity())
            .addListener(mUpnpListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        mDiscoveryRepeatHandler.removeCallbacks(mSmbDiscoveryRepeat);

        if (mSambaDiscovery != null) {
            mSambaDiscovery.abort();
            mSambaDiscovery.removeListener(mSambaListener);
            mSambaDiscovery = null;
        }
    }

    private void loadRows() {
        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mRowsAdapter);

        mRowsAdapter.clear();
        ClassPresenterSelector classPresenter = new ClassPresenterSelector();
        classPresenter.addClassPresenter(NetworkShortcut.class, new NetworkShortcutPresenter());
        classPresenter.addClassPresenter(Box.class, new RescanBoxItemPresenter()); // for the rescan item
        mIndexedFoldersAdapter = new ArrayObjectAdapter(classPresenter);
        mIndexedFoldersListRow = new ListRow(
                new HeaderItem(getString(R.string.indexed_folders)),
                mIndexedFoldersAdapter);
        mRowsAdapter.add(mIndexedFoldersListRow);
        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());

        mSmbDiscoveryAdapter = new ArrayObjectAdapter(new SmbSharePresenter());
        mRowsAdapter.add(new ListRow(
                new HeaderItem(getString(R.string.network_shared_folders)),
                mSmbDiscoveryAdapter));
        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());
        mUpnpDiscoveryAdapter = new ArrayObjectAdapter(new SmbSharePresenter());
        mRowsAdapter.add(new ListRow(
                new HeaderItem(getString(R.string.network_media_servers)),
                mUpnpDiscoveryAdapter));
            mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());

        mNetworkShortcutsAdapter = new ArrayObjectAdapter(new NetworkShortcutPresenter());
        mRowsAdapter.add(new ListRow(
                new HeaderItem(getString(R.string.network_shortcuts)),
                mNetworkShortcutsAdapter));
        mRowsAdapter.notifyArrayItemRangeChanged(0, mRowsAdapter.size());

        setOnItemViewClickedListener(mClickListener);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode==REQUEST_CODE_DETAILS && resultCode==RESULT_CODE_SHORTCUTS_MODIFIED) {
            mShortcutsLoaderTask = new ShortcutsLoaderTask();
            mShortcutsLoaderTask.execute();
            mNetworkShortcutsLoaderTask = new NetworkShortcutsLoaderTask();
            mNetworkShortcutsLoaderTask.execute();
        }
        else if (requestCode==REQUEST_CODE_BROWSING && resultCode==RESULT_CODE_SHORTCUTS_MODIFIED) {
            mShortcutsLoaderTask = new ShortcutsLoaderTask();
            mShortcutsLoaderTask.execute();
        }
    }

    OnItemViewClickedListener mClickListener = new OnItemViewClickedListener() {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item, RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof GenericNetworkShortcut) { // network shortcuts are for now only *ftp*
                log.debug("onItemClicked: GenericNetworkShortcut");
                Intent intent = new Intent(getActivity(), FtpShortcutDetailsActivity.class);
                intent.putExtra(FtpShortcutDetailsFragment.EXTRA_SHORTCUT, (Serializable) item);
                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                        ((NetworkShortcutPresenter.NetworkShortcutViewHolder) itemViewHolder).getImageView(),
                        FtpShortcutDetailsFragment.SHARED_ELEMENT_NAME).toBundle();
                startActivityForResult(intent, REQUEST_CODE_DETAILS, bundle);
            }
            else if (item instanceof NetworkBrowse) { // browse network
                log.debug("onItemClicked: NetworkBrowse");
                if (getParentFragmentManager().findFragmentByTag(NetworkServerCredentialsDialog.class.getCanonicalName()) == null) {
                    NetworkServerCredentialsDialog dialog = new NetworkServerCredentialsDialog();
                    dialog.setOnConnectClickListener(new NetworkServerCredentialsDialog.onConnectClickListener() {
                        @Override
                        public void onConnectClick(String username, String path, String password, int port, int type, String remote, String domain) {
                            String uriToBuild = "";
                            switch (type) {
                                case 0:
                                    uriToBuild = "ftp";
                                    break;
                                case 1:
                                    uriToBuild = "sftp";
                                    break;
                                case 2:
                                    uriToBuild = "ftps";
                                    break;
                                case 3:
                                    uriToBuild = "smb";
                                    break;
                                default:
                                    throw new IllegalArgumentException("Invalid network protocol type " + type);
                            }
                            //path needs to start with "/"
                            if (path.isEmpty() || !path.startsWith("/"))
                                path = "/" + path;
                            uriToBuild += "://" + (!remote.isEmpty() ? remote + (port != -1 ? ":" + port : "") : "") + path;
                            final Uri uri = Uri.parse(uriToBuild);

                            Intent intent = new Intent(getActivity(), ListingActivity.getActivityForUri(uri));
                            log.debug("onItemClicked: NetworkBrowse ListingActivity root uri=" + uri + ", root name=" + uri.getHost());
                            intent.putExtra(ListingActivity.EXTRA_ROOT_URI, uri);
                            String shareName = uri.getLastPathSegment();
                            intent.putExtra(ListingActivity.EXTRA_ROOT_NAME, (shareName==null || shareName.isEmpty())?uri.getHost():shareName);
                            startActivityForResult(intent, REQUEST_CODE_BROWSING);
                        }
                    });
                    dialog.setOnCancelClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                        }
                    });
                    dialog.show(getParentFragmentManager(), NetworkServerCredentialsDialog.class.getCanonicalName());
                }
            }
            else if (item instanceof NetworkShortcut) { // indexed folders
                log.debug("onItemClicked: NetworkShortcut");
                Intent intent = new Intent(getActivity(), NetworkShortcutDetailsActivity.class);
                intent.putExtra(NetworkShortcutDetailsFragment.EXTRA_SHORTCUT, (Serializable)item);
                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                        ((NetworkShortcutPresenter.NetworkShortcutViewHolder) itemViewHolder).getImageView(),
                        NetworkShortcutDetailsFragment.SHARED_ELEMENT_NAME).toBundle();
                startActivityForResult(intent, REQUEST_CODE_DETAILS, bundle);
            }
            else if (item instanceof Box) {
                log.debug("onItemClicked: Box");
                Box box = (Box)item;
                if (box.getBoxId()==Box.ID.INDEXED_FOLDERS_REFRESH) {
                    startActivity(new Intent(getActivity(), RescanActivity.class));
                }
            }
            else if (item instanceof SmbShare) {
                log.debug("onItemClicked: SmbShare");
                SmbShare share = (SmbShare)item;
                final Uri uri = share.getFileCoreShare().toUri();

                log.debug("onItemClicked: SmbShare ListingActivity root uri=" + uri + ", root name=" + uri.getHost());
                Intent intent = new Intent(getActivity(), ListingActivity.getActivityForUri(uri));
                intent.putExtra(ListingActivity.EXTRA_ROOT_URI, uri);
                intent.putExtra(ListingActivity.EXTRA_ROOT_NAME, share.getName());
                startActivityForResult(intent, REQUEST_CODE_BROWSING);
            }
            else if (item instanceof UpnpServer) {
                log.debug("onItemClicked: UpnpServer");
                UpnpServer server = (UpnpServer)item;

                // Build our own special Upnp Uri
                final Uri uri = UpnpServiceManager.getDeviceUri(server.getClingDevice());

                log.debug("onItemClicked: UpnpServer ListingActivity root uri=" + uri + ", root name=" + uri.getHost());
                Intent intent = new Intent(getActivity(), ListingActivity.getActivityForUri(uri));
                intent.putExtra(ListingActivity.EXTRA_ROOT_URI, uri);
                intent.putExtra(ListingActivity.EXTRA_ROOT_NAME, server.getName());
                startActivityForResult(intent, REQUEST_CODE_BROWSING);
            }
        }
    };

    private class ShortcutsLoaderTask extends AsyncTask<Void, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Void... args) {
            return ShortcutDbAdapter.VIDEO.queryAllShortcuts(getActivity());
        }
        @Override
        protected void onPostExecute(Cursor cursor) {
            if (isAdded()) {
                if (cursor.getCount() == 0) {
                    // remove shortcuts row if empty
                    mRowsAdapter.remove(mIndexedFoldersListRow);
                } else {
                    // Add it back in first row if it is not
                    if (mRowsAdapter.indexOf(mIndexedFoldersListRow) == -1) {
                        mRowsAdapter.add(0, mIndexedFoldersListRow);
                    }

                    // update content
                    mIndexedFoldersAdapter.clear();
                    // First item is not an actual shortcut, it opens the re-scan settings
                    if (cursor.getCount() > 0) {
                        Box rescanBox = new Box(Box.ID.INDEXED_FOLDERS_REFRESH, getString(R.string.rescan), R.drawable.filetype_new_rescan);
                        log.debug("ShortcutsLoaderTask NetworkScannerReceiver.isScannerWorking()=" + NetworkScannerReceiver.isScannerWorking());
                        mIndexedFoldersAdapter.add(rescanBox);
                    }

                    // Convert from cursor to array (only because we need to add the "refresh" Box in the list)
                    cursor.moveToFirst();
                    NetworkShortcutMapper mapper = new NetworkShortcutMapper();
                    mapper.bind(cursor);
                    while (!cursor.isAfterLast()) {
                        mIndexedFoldersAdapter.add(mapper.convert(cursor));
                        cursor.moveToNext();
                    }
                }
            }
            if (cursor != null) cursor.close();
        }
    }

    // need a second task because they are not using the same cursor
    private class NetworkShortcutsLoaderTask extends AsyncTask<Void, Void, Cursor> {
        @Override
        protected Cursor doInBackground(Void... args) {
            return ShortcutDb.STATIC.getCursorAllShortcuts(getActivity());
        }
        @Override
        protected void onPostExecute(Cursor cursor) {
            if (isAdded()) {
                mNetworkShortcutsAdapter.clear();
                mNetworkShortcutsAdapter.add(new NetworkBrowse(getString(R.string.browse_net_server)));
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    do {
                        GenericNetworkShortcutMapper shortcutMapper = new GenericNetworkShortcutMapper();
                        shortcutMapper.bindColumns(cursor);
                        mNetworkShortcutsAdapter.add(shortcutMapper.bind(cursor));
                    } while (cursor.moveToNext());
                }
            }
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Flag already present shares as old
     * @param adapter
     */
    static private void flagObjectsAsOld(ArrayObjectAdapter adapter) {
        for (Object o : adapter.unmodifiableList()) {
            NetworkSource source = (NetworkSource)o;
            source.setOld(true);
        }
    }

    /**
     * remove the objects still flagged as old (it means they are not discovered anymore)
     * @param adapter
     */
    static private void removeOldObjects(ArrayObjectAdapter adapter) {
        // Remove disappeared shares (two steps to avoid ConcurrentModificationException and UnsupportedOperationException...)
        List<NetworkSource> toRemove = new LinkedList<NetworkSource>();
        for (Object obj : adapter.unmodifiableList()) {
            NetworkSource source = (NetworkSource)obj;
            if (source.isOld()) {
                toRemove.add(source);
            }
        }
        for (NetworkSource source : toRemove) {
            log.debug("Removing "+source.getName()+" ("+source.getClass()+")");
            adapter.remove(source);
        }
    }

    //------------------------------
    // Implementation of SambaDiscovery.Listener
    // ------------------------------

    SambaDiscovery.Listener mSambaListener = new SambaDiscovery.Listener() {

        @Override
        public void onDiscoveryStart() {
            log.trace("SambaDiscovery onDiscoveryStart");
            flagObjectsAsOld(mSmbDiscoveryAdapter);
        }

        @Override
        public void onDiscoveryEnd() {
            log.trace("SambaDiscovery onDiscoveryEnd");
            removeOldObjects(mSmbDiscoveryAdapter);

            // Schedule a discovery refresh
            mDiscoveryRepeatHandler.removeCallbacks(mSmbDiscoveryRepeat); // better safe than sorry, don't want to have several same runnable posted
            mDiscoveryRepeatHandler.postDelayed(mSmbDiscoveryRepeat, DISCOVERY_REPEAT_DELAY_MS);
        }

        @Override
        public void onDiscoveryUpdate(List<Workgroup> workgroups) {
            log.trace("SambaDiscovery onDiscoveryUpdate");

            for (Workgroup workgroup : workgroups) {
                for (Share share : workgroup.getShares()) {
                    SmbShare newInstance = new SmbShare(share);
                    // Check if this share is already in the adapter.
                    // We just check the IP because name and workgroup may change (TCP discovery returns IP only,
                    // need to update it with name once we get result from UDP discovery)
                    for (int i=0; i<mSmbDiscoveryAdapter.size(); i++) {
                        Object obj = mSmbDiscoveryAdapter.get(i);
                        if (obj instanceof NetworkSource) {
                            NetworkSource newSource = (NetworkSource)obj;
                        }
                    }
                    int index = mSmbDiscoveryAdapter.indexOf(newInstance);
                    if (index==-1) {
                        log.debug("smb Adding "+newInstance.getName());
                        addInAdapterInAlphabeticalOrder(newInstance, mSmbDiscoveryAdapter);
                    }
                    else {
                        log.debug("smb "+newInstance.getName() + " already present");
                        SmbShare existingItem = (SmbShare) mSmbDiscoveryAdapter.get(index);
                        // seen again, flag as not old
                        existingItem.setOld(false);
                        // check if it must be replaced
                        if (existingItem.getName().isEmpty() && !newInstance.getName().isEmpty()) {
                            mSmbDiscoveryAdapter.replace(index, newInstance);
                        }
                    }
                }
            }
        }

        @Override
        public void onDiscoveryFatalError() {
            //TODO
        }
    };

    //------------------------------
    // Implementation of UpnpDiscovery.Listener
    // ------------------------------

    UpnpServiceManager.Listener mUpnpListener = new UpnpServiceManager.Listener() {

        @Override
        public void onDeviceListUpdate(List<Device> devices) {
            log.trace("UpnpDiscovery onDiscoveryUpdate");

            // NOTE: for UPnP, onDiscoveryUpdate() is called each time a server is added or removed

            flagObjectsAsOld(mUpnpDiscoveryAdapter);

            for (Device d : devices) {
                UpnpServer newInstance = new UpnpServer(d, UpnpServiceManager.getDeviceFriendlyName(d));
                // Check if this server is already in the adapter (even if it is not the same instance it is OK due to the equals() implementation)
                int index = mUpnpDiscoveryAdapter.indexOf(newInstance);
                if (index==-1) {
                    log.trace("upnp Adding "+newInstance.getName());
                    addInAdapterInAlphabeticalOrder(newInstance, mUpnpDiscoveryAdapter);
                }
                else {
                    log.trace("upnp "+newInstance.getName() + " already present, flag as not old");
                    ((NetworkSource)mUpnpDiscoveryAdapter.get(index)).setOld(false);
                }
            }

            removeOldObjects(mUpnpDiscoveryAdapter);
        }
    };

    /** Runnable used to relaunch the SMB discovery after it ends */
    Runnable mSmbDiscoveryRepeat = new Runnable() {
        @Override
        public void run() {

            // samba discovery should not be running at this stage, but better safe than sorry
            if (mSambaDiscovery != null) {
                mSambaDiscovery.abort();
                mSambaDiscovery.removeListener(mSambaListener);
            }

            mSambaDiscovery = new SambaDiscovery(getActivity());
            mSambaDiscovery.setMinimumUpdatePeriodInMs(100);
            mSambaDiscovery.addListener(mSambaListener);
            mSambaDiscovery.start();
        }
    };


    /**
     * Bloody ArrayObjectAdapter does not support sort()! (ArrayAdapter does...)
     * Need this f*ck*ng piece of code to do it
     * Non performance issue here since we're never handling a LOT of items
     * @param newObject a NetworkSource instance to add to the adapter
     * @param adapter an adapter containing some NetworkSource objects
     */

    private void addInAdapterInAlphabeticalOrder(NetworkSource newObject, ArrayObjectAdapter adapter) {

        for (int i=0; i<adapter.size(); i++) {
            NetworkSource obj = (NetworkSource)adapter.get(i);
            if (newObject.getName().compareToIgnoreCase(obj.getName()) < 0) {
                adapter.add(i, newObject); // insert before this item
                return;
            }
        }

        // Default is to add at the end (either because of the names or because this is the first item)
        adapter.add(newObject);
    }

    private void updateBackground() {
        Resources r = getResources();

        bgMngr = BackgroundManager.getInstance(getActivity());
        if(!bgMngr.isAttached())
            bgMngr.attach(getActivity().getWindow());

        if (PrivateMode.isActive()) {
            bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.private_mode));
            bgMngr.setDrawable(ContextCompat.getDrawable(getActivity(), R.drawable.private_background));
        } else {
            bgMngr.setColor(ContextCompat.getColor(getActivity(), R.color.leanback_background));
            bgMngr.setDrawable(new ColorDrawable(ContextCompat.getColor(getActivity(), R.color.leanback_background)));
        }
    }
}
