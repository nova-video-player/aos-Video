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
import android.content.Context;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.archos.mediacenter.utils.ActionItem;
import com.archos.mediacenter.video.browser.BrowserCategory;
import com.archos.mediacenter.utils.QuickAction;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.filebrowsing.network.NewRootFragment;
import com.archos.mediacenter.video.browser.filebrowsing.network.RootFragmentAdapter;
import com.archos.mediacenter.video.browser.ShortcutDb;
import com.archos.mediacenter.video.browser.filebrowsing.network.ShortcutAdapter;
import com.archos.mediaprovider.NetworkScanner;
import com.skydoves.powermenu.MenuAnimation;
import com.skydoves.powermenu.MenuBaseAdapter;
import com.skydoves.powermenu.PowerMenu;
import com.skydoves.powermenu.PowerMenuItem;

import java.util.ArrayList;
import java.util.List;

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

        if(v.getTag() instanceof FtpShortcutAdapter.FtpShortcutViewHolder){
            mSelectedUri =((FtpShortcutAdapter.FtpShortcutViewHolder) v.getTag()).getUri();
            mSelectedName =((FtpShortcutAdapter.FtpShortcutViewHolder) v.getTag()).getName();
            //menu.add(0, R.string.remove_from_shortcuts, 0, R.string.remove_from_shortcuts);
            //menu.add(0, R.string.open_indexed_folder, 0, R.string.open_indexed_folder);
            //if(ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), mSelectedUri.toString())<0)
            //menu.add(0, R.string.add_to_library, 0, R.string.add_to_library);

            mTouchX = ((Float) v.getTag(R.id.touch_x)).intValue();
            mTouchY = ((Float) v.getTag(R.id.touch_y)).intValue();
            // Trigger haptic feedback manually on long click anchor
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showPowerMenu(v);
        }
        else super.onCreateContextMenu(menu, v, menuInfo);

    }

    private void showPowerMenu(View anchor) {
        List<PowerMenuItem> menuItems = new ArrayList<>();

        menuItems.add(new PowerMenuItem(getContext().getString(R.string.remove_from_shortcuts)));
        menuItems.add(new PowerMenuItem(getContext().getString(R.string.open_indexed_folder)));
        if(ShortcutDbAdapter.VIDEO.isShortcut(getActivity(), mSelectedUri.toString())<0)
            menuItems.add(new PowerMenuItem(getContext().getString(R.string.add_to_library)));

        Context themedContext = new ContextThemeWrapper(getContext(), R.style.PowerMenuTheme);
        View decorView = ((Activity) anchor.getContext()).getWindow().getDecorView();
        int[] decorLocation = new int[2];
        decorView.getLocationOnScreen(decorLocation);
        int xOffset = mTouchX - decorLocation[0];
        int yOffset = mTouchY - decorLocation[1];

        Typeface typeface = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_75bd);
        if (typeface == null) typeface = Typeface.DEFAULT;
        float textSizeSp = 16f;
        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        Paint paint = new Paint();
        paint.setTypeface(typeface);
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, metrics));
        // Find the widest item
        float maxTextWidth = 0;
        for (PowerMenuItem item : menuItems) {
            float width = paint.measureText(item.title.toString());
            if (width > maxTextWidth) maxTextWidth = width;
        }
        // Calculate total width: max text + padding + margin
        int internalPaddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, metrics); // start + end padding
        int externalMarginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics); // optional
        int menuWidth = (int) (maxTextWidth + internalPaddingPx + externalMarginPx);

        PowerMenu powerMenu = new PowerMenu.Builder(themedContext)
                .addItemList(menuItems)
                .setMenuRadius(16f)
                .setMenuShadow(8f)
                .setAnimation(MenuAnimation.DROP_DOWN)
                .setAutoDismiss(true)
                .setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent))
                .setWidth(menuWidth) // ⬅ set width here
                .build();

        // set adapter
        ListView listView = powerMenu.getMenuListView();
        CustomPowerMenuAdapter adapter = new CustomPowerMenuAdapter(listView);
        adapter.addItemList(menuItems);
        listView.setAdapter(adapter);

        // set background
        View menuListView = powerMenu.getMenuListView();
        if (menuListView != null) {
            View parent = (View) menuListView.getParent();
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(getContext(), R.color.tranparent_deep_blue));
            float radiusPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12f, getContext().getResources().getDisplayMetrics());
            int strokeWidthPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1f, getContext().getResources().getDisplayMetrics());
            bg.setCornerRadius(radiusPx);
            bg.setStroke(strokeWidthPx, ContextCompat.getColor(getContext(), R.color.black));
            parent.setBackground(bg);
        }

        powerMenu.setOnMenuItemClickListener((positionClicked, item) -> {
            String titleClicked = ((PowerMenuItem) item).title.toString();
            handlePowerMenuClick(titleClicked);
        });
        powerMenu.showAtLocation(decorView, Gravity.NO_GRAVITY, xOffset, yOffset);
    }

    protected void handlePowerMenuClick(String title) {
        if (title.equals(getContext().getString(R.string.remove_from_shortcuts))) {
            ShortcutDb.STATIC.removeShortcut(getContext(), mSelectedUri);
            
            // Filter shortcuts to only include FTP ones
            List<ShortcutDb.Shortcut> allShortcuts = ShortcutDb.STATIC.getAllShortcuts(getActivity());
            List<ShortcutDb.Shortcut> ftpShortcuts = new ArrayList<>();
            for (ShortcutDb.Shortcut shortcut : allShortcuts) {
                if (shortcut.uri != null && shortcut.uri.toLowerCase().contains("ftp://")) {
                    ftpShortcuts.add(shortcut);
                }
            }
            
            ((FtpShortcutAdapter)mAdapter).updateShortcuts(ftpShortcuts);
        } else if (title.equals(getContext().getString(R.string.open_indexed_folder))){
            onShortcutTap(mSelectedUri);
        } else if (title.equals(getContext().getString(R.string.add_to_library))){
            addToIndexed(mSelectedUri, mSelectedName);
        } else {
            super.handlePowerMenuClick(title);  // fallback to base logic
        }
    }

    public class CustomPowerMenuAdapter extends MenuBaseAdapter<PowerMenuItem> {
        public CustomPowerMenuAdapter(ListView listView) {
            super(listView);
        }
        @Override
        public View getView(int index, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                view = inflater.inflate(R.layout.item_power_menu, parent, false);
            }
            PowerMenuItem item = (PowerMenuItem) getItem(index);
            TextView title = view.findViewById(R.id.menu_item_title);
            title.setText(((PowerMenuItem) item).title.toString());
            return view;
        }
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
                
                // Filter shortcuts to only include FTP ones
                List<ShortcutDb.Shortcut> allShortcuts = ShortcutDb.STATIC.getAllShortcuts(getActivity());
                List<ShortcutDb.Shortcut> ftpShortcuts = new ArrayList<>();
                for (ShortcutDb.Shortcut shortcut : allShortcuts) {
                    if (shortcut.uri != null && shortcut.uri.toLowerCase().contains("ftp://")) {
                        ftpShortcuts.add(shortcut);
                    }
                }
                
                ((FtpShortcutAdapter)mAdapter).updateShortcuts(ftpShortcuts);
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
        
        // Filter shortcuts to only include FTP ones
        List<ShortcutDb.Shortcut> allShortcuts = ShortcutDb.STATIC.getAllShortcuts(getActivity());
        List<ShortcutDb.Shortcut> ftpShortcuts = new ArrayList<>();
        for (ShortcutDb.Shortcut shortcut : allShortcuts) {
            if (shortcut.uri != null && shortcut.uri.toLowerCase().contains("ftp://")) {
                ftpShortcuts.add(shortcut);
            }
        }
        
        ((FtpShortcutAdapter)mAdapter).updateShortcuts(ftpShortcuts);
        mAdapter.updateIndexedShortcuts(cursor);
        if (cursor != null) {
            cursor.close();
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.ftp_shortcuts);
    }


    @Override
    public void onClick(View view) {
        if(getParentFragmentManager().findFragmentByTag(FTPServerCredentialsDialog.class.getCanonicalName())==null){
            FTPServerCredentialsDialog dialog = new FTPServerCredentialsDialog();
            dialog.setOnConnectClickListener( new FTPServerCredentialsDialog.onConnectClickListener() {
                @Override
                public void onConnectClick(String username, Uri uri, String password) {
                    Bundle args = new Bundle();
                    args.putParcelable(BrowserBySFTP.CURRENT_DIRECTORY, uri);
                    Fragment f = new BrowserBySFTP();
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
            dialog.show(getParentFragmentManager(),FTPServerCredentialsDialog.class.getCanonicalName());
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
    public void onFtpShortcutAdd(View v, final Uri uri, final String name) {


        mQuickAction = new QuickAction(v);

        ActionItem rescanAction = new ActionItem();
        rescanAction.setTitle(getString(R.string.add_to_library));
        rescanAction.setIcon(ContextCompat.getDrawable(getActivity(), R.drawable.ic_plus));
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
