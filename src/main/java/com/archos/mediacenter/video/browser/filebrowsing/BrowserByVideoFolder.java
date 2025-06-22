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


package com.archos.mediacenter.video.browser.filebrowsing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuItemCompat;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.MainActivity;
import com.archos.mediacenter.video.utils.CustomTypefaceSpan;
import com.archos.mediacenter.video.utils.FolderPicker;
import com.archos.mediacenter.video.utils.VideoPreferencesActivity;

import java.io.File;

public class BrowserByVideoFolder extends BrowserByLocalFolder {

    private static final String TAG = "BrowserByVideoFolder";
    private static final boolean DBG = false;

    private static final int FOLDER_PICKER_REQUEST_CODE = 2011;

    @Override
    protected Uri getDefaultDirectory() {
        // Check if there is one specified in the preferences
        String defaultDirectoryPath = PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getString(VideoPreferencesActivity.FOLDER_BROWSING_DEFAULT_FOLDER, null);
        if (defaultDirectoryPath!=null) {
            return Uri.parse(defaultDirectoryPath);
        } else {
            return Uri.fromFile(Environment.getExternalStorageDirectory());
        }
    }

    private void attachCustomTooltip(View anchorView, String message) {
        anchorView.setOnLongClickListener(v -> {
            Context context = v.getContext();
            Toast toast = new Toast(context);

            TextView textView = new TextView(context);
            textView.setText(message);
            textView.setTextColor(Color.WHITE);
            textView.setBackgroundResource(R.drawable.menu_bg);
            textView.setPadding(24, 16, 24, 16);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            textView.setTypeface(ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd));
            textView.setGravity(Gravity.CENTER);

            // Measure the textView to get width
            textView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int tooltipWidth = textView.getMeasuredWidth();

            toast.setView(textView);

            // Get location of the anchor view
            int[] location = new int[2];
            v.getLocationOnScreen(location);
            int anchorX = location[0];
            int anchorY = location[1];

            int viewWidth = v.getWidth();
            int centerX = anchorX + viewWidth / 2;

            // Position toast so it's centered horizontally below the anchor
            int xOffset = centerX - tooltipWidth / 2;
            int yOffset = anchorY + v.getHeight() + 16; // distance below the view

            toast.setGravity(Gravity.TOP | Gravity.START, xOffset, yOffset);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.show();

            return true;
        });
    }

    @Override
    @SuppressLint("RestrictedApi")
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MainActivity.MENU_CHANGE_FOLDER, Menu.NONE, applyCustomFont(R.string.menu_change_folder)).setIcon(R.drawable.ic_menu_folder).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        Toolbar toolbar = requireActivity().findViewById(R.id.main_toolbar);
        String changeFolder = getString(R.string.menu_change_folder);

        ViewTreeObserver observer = toolbar.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!isAdded()) return;

                boolean changeFolderSet = false;

                for (int i = 0; i < toolbar.getChildCount(); i++) {
                    View child = toolbar.getChildAt(i);
                    if (child instanceof ActionMenuView) {
                        ActionMenuView menuView = (ActionMenuView) child;
                        for (int j = 0; j < menuView.getChildCount(); j++) {
                            View itemView = menuView.getChildAt(j);
                            if (itemView instanceof ActionMenuItemView) {
                                CharSequence title = ((ActionMenuItemView) itemView).getItemData().getTitle();
                                if (title != null) {
                                    String titleStr = title.toString();
                                    if (!changeFolderSet && titleStr.equalsIgnoreCase(changeFolder)) {
                                        attachCustomTooltip(itemView, changeFolder);
                                        changeFolderSet = true;
                                    }
                                }
                            }
                        }
                    }
                }

                // Once both are set, remove listener
                if (changeFolderSet) {
                    toolbar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    private SpannableString applyCustomFont(@StringRes int resId) {
        String family ="";
        Typeface typeface = ResourcesCompat.getFont(mContext, R.font.nhaasgroteskdspro_75bd);
        int color = ContextCompat.getColor(mContext, android.R.color.white);
        float textSize = 18f; // in SP
        String text = mContext.getString(resId);
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new CustomTypefaceSpan(family, typeface, textSize, color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret;
        switch (item.getItemId()) {
            case MainActivity.MENU_CHANGE_FOLDER:
                Intent i = new Intent(mContext, FolderPicker.class);
                Bundle b = new Bundle();
                i.putExtra(FolderPicker.EXTRA_CURRENT_SELECTION, getDefaultDirectory().getPath());
                i.putExtra(FolderPicker.EXTRA_DIALOG_TITLE, getResources().getString(R.string.menu_change_folder_details));
                startActivityForResult(i, FOLDER_PICKER_REQUEST_CODE);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(DBG) Log.d(TAG, "onActivityResult "+requestCode+" "+resultCode);
        if (requestCode == FOLDER_PICKER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String newPath = data.getStringExtra(FolderPicker.EXTRA_SELECTED_FOLDER);
                if(DBG) Log.d(TAG, "FolderPicker returns "+newPath);
                if (newPath!=null) { //better safe than sorry
                    File f = new File(newPath);
                    if ((f!=null) && f.isDirectory() && f.exists()) { //better safe than sorry x3
                        Editor ed = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();
                        ed.putString(VideoPreferencesActivity.FOLDER_BROWSING_DEFAULT_FOLDER, f.getPath());
                        ed.commit();
                        // Only the activity is able to correctly update the root folder browser view
                        // (because user may be deep in a folder hierarchy already)
                        MainActivity bav = (MainActivity)getActivity();
                        bav.reloadBrowserByVideoFolder();
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


}
