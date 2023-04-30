// Copyright 2023 Courville Software
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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupWindow;

import com.archos.mediacenter.utils.ActionItem;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.utils.QuickAction;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser.BrowserBySFTP;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.browser.filebrowsing.network.SmbBrowser.BrowserBySmb;
import com.archos.mediacenter.video.browser.filebrowsing.network.UpnpBrowser.BrowserByUpnp;
import com.archos.mediacenter.video.leanback.network.NetworkServerCredentialsDialog;
import com.archos.mediaprovider.NetworkScanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShortcutRootFragment extends NewRootFragment implements View.OnClickListener, ShortcutAdapter.OnShortcutAddListener {

    private static final Logger log = LoggerFactory.getLogger(ShortcutRootFragment.class);

    private String mSelectedName;
    private Uri mSelectedUri;

    public ShortcutRootFragment(){
        super();
    }

    @Override
    public void onViewCreated (View v, Bundle saved){}

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if(v.getTag() instanceof ShortcutAdapter.ShortcutViewHolder){
            mSelectedUri =((ShortcutAdapter.ShortcutViewHolder) v.getTag()).getUri();
            mSelectedName =((ShortcutAdapter.ShortcutViewHolder) v.getTag()).getName();
            menu.add(0, R.string.remove_from_shortcuts, 0, R.string.remove_from_shortcuts);
            menu.add(0, R.string.open_indexed_folder, 0, R.string.open_indexed_folder);
            if(ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), mSelectedUri.toString())<0)
                menu.add(0, R.string.add_to_library, 0, R.string.add_to_library);
        }
        else super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        switch (itemId) {
            case R.string.add_to_library:
                addToIndexed(mSelectedUri, mSelectedName);
                return true;
            case R.string.remove_from_shortcuts:
                ShortcutDb.STATIC.removeShortcut(getContext(), mSelectedUri);
                ((ShortcutAdapter)mAdapter).updateShortcuts(ShortcutDb.STATIC.getAllShortcuts(getActivity()));
                loadIndexedShortcuts();
                return true;
        }

        return super.onContextItemSelected(item);
    }
    public RootFragmentAdapter getAdapter(){
        mAdapter  = new ShortcutAdapter(getActivity());
        ((ShortcutAdapter)mAdapter).setOnBrowseClickListener(this);
        ((ShortcutAdapter)mAdapter).setOnShortcutAddListener(this);
        return mAdapter;
    }

    @Override
    protected void rescanAvailableShortcuts() {}

    @Override
    protected void loadIndexedShortcuts() {
        Cursor cursor = ShortcutDbAdapter.VIDEO.getAllShortcuts(getActivity(), null, null); // get all shortcuts
        ((ShortcutAdapter)mAdapter).updateShortcuts(ShortcutDb.STATIC.getAllShortcuts(getActivity()));
        mAdapter.updateIndexedShortcuts(cursor);
        if (cursor != null) {
            cursor.close();
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View view) {
        if(getParentFragmentManager().findFragmentByTag(NetworkServerCredentialsDialog.class.getCanonicalName())==null){
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
                            uriToBuild = "sshj";
                            break;
                        case 4:
                            uriToBuild = "smb";
                            break;
                        case 5:
                            uriToBuild = "smbj";
                            break;
                        case 6:
                            uriToBuild = "webdav";
                            break;
                        case 7:
                            uriToBuild = "webdavs";
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid network protocol type " + type);
                    }
                    //path needs to start with "/"
                    if (path.isEmpty() || !path.startsWith("/"))
                        path = "/" + path;
                    uriToBuild += "://" + (!remote.isEmpty() ? remote + (port != -1 ? ":" + port : "") : "") + path;
                    final Uri uri = Uri.parse(uriToBuild);

                    Bundle args = new Bundle();
                    Fragment f;
                    if (uri.getScheme().equals("smb")) {
                        f = new BrowserBySmb();
                        args.putParcelable(BrowserByNetwork.CURRENT_DIRECTORY, uri);
                        args.putString(BrowserByNetwork.TITLE
                                , uri.getLastPathSegment());
                        args.putString(BrowserByNetwork.SHARE_NAME, uri.getLastPathSegment());
                    } else if (uri.getScheme().equals("upnp")) {
                        f = new BrowserByUpnp();
                    } else {
                        f = new BrowserBySFTP();
                        args.putParcelable(BrowserBySFTP.CURRENT_DIRECTORY, uri);
                    }
                    f.setArguments(args);
                    BrowserCategory category = (BrowserCategory) getActivity().getSupportFragmentManager().findFragmentById(R.id.category);
                    category.startContent(f);
                }
            });
            dialog.setOnCancelClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                }
            });
            dialog.show(getParentFragmentManager(),NetworkServerCredentialsDialog.class.getCanonicalName());
        }
    }

    public void addToIndexed(Uri uri, String name){
        ShortcutDb.STATIC.removeShortcut(getActivity(), uri);
        if (ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), uri.toString()) < 0) {
            //if not a shortcut, add as shortcut
            ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(name, uri.toString()));
            NetworkScanner.scanVideos(getActivity(), uri);
        }
        loadIndexedShortcuts();
    }

    @Override
    public void onShortcutAdd(View v, final Uri uri, final String name) {
        mQuickAction = new QuickAction(v);
        ActionItem rescanAction = new ActionItem();
        rescanAction.setTitle(getString(R.string.add_to_library));
        rescanAction.setIcon(ContextCompat.getDrawable(getActivity(), R.drawable.ic_menu_refresh));
        rescanAction.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                addToIndexed(uri, name);
                mQuickAction.dismiss();
            }
        });
        mQuickAction.addActionItem(rescanAction);
        mQuickAction.setAnimStyle(QuickAction.ANIM_REFLECT);
        mQuickAction.show();
        final View fv = v;
        mQuickAction.setOnDismissListener(new PopupWindow.OnDismissListener() {
            public void onDismiss() {
                fv.invalidate();
                mQuickAction.onClose();
            }
        });
    }
}
