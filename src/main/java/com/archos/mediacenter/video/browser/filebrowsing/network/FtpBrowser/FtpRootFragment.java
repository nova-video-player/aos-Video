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

package com.archos.mediacenter.video.browser.filebrowsing.network.FtpBrowser;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupWindow;

import com.archos.mediacenter.utils.ActionItem;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.utils.QuickAction;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.NewRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.RootFragmentAdapter;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediaprovider.NetworkScanner;

/**
 * Created by alexandre on 28/05/15.
 */
public class FtpRootFragment extends NewRootFragment implements View.OnClickListener, FtpShortcutAdapter.OnFtpShortcutAddListener {
    private static final String TAG = "FtpRootFragment";
    private String mSelectedName;
    private Uri mSelectedUri;

    public FtpRootFragment(){
        super();
    }
    @Override
    public void onViewCreated (View v, Bundle saved){

    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

        if(v.getTag() instanceof FtpShortcutAdapter.FtpShortcutViewHolder){
            mSelectedUri =((FtpShortcutAdapter.FtpShortcutViewHolder) v.getTag()).getUri();
            mSelectedName =((FtpShortcutAdapter.FtpShortcutViewHolder) v.getTag()).getName();
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
                ShortcutDb.STATIC.removeShortcut(mSelectedUri);
                ((FtpShortcutAdapter)mAdapter).updateShortcuts(ShortcutDb.STATIC.getAllShortcuts(getActivity()));
                return true;
        }

        return super.onContextItemSelected(item);
    }
    public RootFragmentAdapter getAdapter(){
        mAdapter  = new FtpShortcutAdapter(getActivity());
        ((FtpShortcutAdapter)mAdapter).setOnBrowseClickListener(this);
        ((FtpShortcutAdapter)mAdapter).setOnFtpShortcutAddListener(this);
        return mAdapter;
    }

    @Override
    protected void rescanAvailableShortcuts() {
    }


    @Override
    protected void loadIndexedShortcuts() {
        Cursor cursor = ShortcutDbAdapter.VIDEO.getAllShortcuts(getActivity(), ShortcutDbAdapter.KEY_PATH+" LIKE ?",new String[]{"%ftp%://%"});
        ((FtpShortcutAdapter)mAdapter).updateShortcuts(ShortcutDb.STATIC.getAllShortcuts(getActivity()));
        mAdapter.updateIndexedShortcuts(cursor);
        if (cursor != null) {
            cursor.close();
        }
        mAdapter.notifyDataSetChanged();
    }


    @Override
    public void onClick(View view) {
        if(getFragmentManager().findFragmentByTag(FTPServerCredentialsDialog.class.getCanonicalName())==null){
            FTPServerCredentialsDialog dialog = new FTPServerCredentialsDialog();
            dialog.setOnConnectClickListener( new FTPServerCredentialsDialog.onConnectClickListener() {
                @Override
                public void onConnectClick(String username, Uri uri, String password) {
                    Bundle args = new Bundle();
                    args.putParcelable(BrowserBySFTP.CURRENT_DIRECTORY, uri);
                    Fragment f = Fragment.instantiate(getActivity(), BrowserBySFTP.class.getCanonicalName(), args);
                    BrowserCategory category = (BrowserCategory) getActivity().getSupportFragmentManager().findFragmentById(R.id.category);
                    category.startContent(f);
                }
            });
            dialog.setOnCancelClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                }
            });
            dialog.show(getFragmentManager(),FTPServerCredentialsDialog.class.getCanonicalName());
        }
    }
    public void addToIndexed(Uri uri, String name){
        NetworkScanner.scanVideos(getActivity(), uri);
        if (ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), uri.toString()) < 0) {
            //if not a shortcut, add as shortcut
            ShortcutDbAdapter.VIDEO.addShortcut(getActivity(), new ShortcutDbAdapter.Shortcut(name, uri.toString()));
            loadIndexedShortcuts();
        }
    }
    @Override
    public void onFtpShortcutAdd(View v, final Uri uri, final String name) {


        mQuickAction = new QuickAction(v);

        ActionItem rescanAction = new ActionItem();
        rescanAction.setTitle(getString(R.string.add_to_library));
        rescanAction.setIcon(getResources().getDrawable(R.drawable.ic_menu_refresh));
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
