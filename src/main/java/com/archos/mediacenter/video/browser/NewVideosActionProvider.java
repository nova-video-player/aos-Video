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


package com.archos.mediacenter.video.browser;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.view.ActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.HelpOverlayActivity;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.autoscraper.AutoScraperActivity;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.AutoScrapeService;

public class NewVideosActionProvider extends ActionProvider implements
        LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {

    protected static final String TAG = NewVideosActionProvider.class.getSimpleName();
    private static final boolean DBG = false;

    private static final Uri URI = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
    private static final String[] PROJECTION = {
        "count(*)"
    };
    private static final String SELECTION = VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID + " = 0 AND " +
            VideoStore.Video.VideoColumns.ARCHOS_HIDE_FILE + "=0 AND " +
            VideoStore.MediaColumns.DATA + " NOT LIKE ?"; // not in camera path
    private static final String CAMERA_PATH_ARG =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath()
            + "/Camera/%";
    private static final String[] SELECTION_ARGS = { CAMERA_PATH_ARG };

    private static final int MSG_START_HELP_OVERLAY = 1;

    private static final String NEW_VIDEOS_HELP_OVERLAY_KEY = "new_videos_help_overlay";

    private final Context mContext;
    private HelpOverlayHandler mHelpOverlayHandler;
    protected SharedPreferences mPreferences;

    private int mCount;
    private boolean mEnabled;
    private int mHelpOverlayHorizontalOffset;
    private int mHelpOverlayVerticalOffset;

    private View mItemView;
    private TextView mTextView;
    private DropDownWindow mPopup;

    public NewVideosActionProvider(Context context) {
        super(context);
        mContext = context;
        mEnabled = true;

        mHelpOverlayHorizontalOffset = mContext.getResources().getDimensionPixelSize(R.dimen.help_overlay_horizontal_offset);
        mHelpOverlayVerticalOffset = mContext.getResources().getDimensionPixelSize(R.dimen.help_overlay_vertical_offset);

        mHelpOverlayHandler = new HelpOverlayHandler();

        mPreferences = mContext.getSharedPreferences(MediaUtils.SHARED_PREFERENCES_NAME, Activity.MODE_PRIVATE);
    }

    private MenuItem mItem;

    public void manageVisibility(MenuItem item) {
        mItem = item;
        item.setVisible(false);
        item.setEnabled(false);
        mEnabled = false;
    }

    public void setEnabled(boolean enable) {
        if (enable != mEnabled) {
            mEnabled = enable;
            updateCount(mCount);
        }
    }

    @Override
    public View onCreateActionView() {
        if (DBG) Log.d(TAG, "onCreateActionView");

        View view = LayoutInflater.from(mContext).inflate(R.layout.new_videos_action_item, null);
        view.setOnClickListener(this);
        mTextView = (TextView) view.findViewById(R.id.new_videos_textview);
        mItemView = view.findViewById(R.id.new_videos_action_button);
        updateCount(mCount);
        return view;
    }

    public void cancelHelpOverlayRequest() {
        mHelpOverlayHandler.removeMessages(MSG_START_HELP_OVERLAY);
    }

    private void updateCount(int count) {
        mCount = count;
        if (mItem != null) {
            if (count > 0 && mEnabled&& !(AutoScrapeService.isEnable(getContext())&& ArchosUtils.isNetworkConnected(getContext()))) {
                mItem.setVisible(true);
                mItem.setEnabled(true);

                // There are videos for which we have not searched online infos yet
                // => display the help overlay if this is the first time the action bar item is shown
                if (!helpOverlayAlreadyActivated() && !mHelpOverlayHandler.hasMessages(MSG_START_HELP_OVERLAY)&&!PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true)) {
                    mHelpOverlayHandler.sendEmptyMessageDelayed(MSG_START_HELP_OVERLAY, 200);
                }
            } else {
                mItem.setVisible(false);
                mItem.setEnabled(false);
            }
        }
        if (mTextView != null) {
            mTextView.setText(mContext.getString(R.string.new_videos_count, Integer.valueOf(count)));
        }
    }

    private DropDownWindow getPopopWindow(View anchor) {
        Context context = anchor.getContext();
        LayoutInflater infl = LayoutInflater.from(context);
        View view = infl.inflate(R.layout.new_videos_action_dropdown, null);
        Button go = (Button) view.findViewById(R.id.new_videos_go_button);
        go.setOnClickListener(this);
        return new DropDownWindow(anchor, view);
    }

    @Override
    public boolean onPerformDefaultAction() {
        if (DBG) Log.d(TAG, "onPerformDefaultAction");
        // Search all the videos in the database
        Intent as = new Intent(mContext, AutoScraperActivity.class);
        as.setAction(Intent.ACTION_MAIN);
        mContext.startActivity(as);
        return true;
    }

    @Override
    public void onPrepareSubMenu(SubMenu subMenu) {
        if (DBG) Log.d(TAG, "onPrepareSubMenu");
    }

    @Override
    public boolean hasSubMenu() {
        if (DBG) Log.d(TAG, "hasSubMenu");
        return false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (DBG) Log.d(TAG, "onCreateLoader");
        return new CursorLoader(mContext, URI, PROJECTION, SELECTION, SELECTION_ARGS, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (DBG) Log.d(TAG, "onLoadFinished");
        if (cursor != null && cursor.moveToFirst()) {
            updateCount(cursor.getInt(0));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        if (DBG) Log.d(TAG, "onLoaderReset");
        // nothing
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.new_videos_action_button:
                mPopup = getPopopWindow(v);
                mPopup.show();
                break;
            case R.id.new_videos_go_button:
                onPerformDefaultAction();
                //$FALL-THROUGH$
            default:
                if (mPopup != null && mPopup.isShowing()) {
                    mPopup.dismiss();
                    mPopup = null;
                }
                break;
        }
    }

    private boolean helpOverlayAlreadyActivated() {
        return mPreferences.getBoolean(NEW_VIDEOS_HELP_OVERLAY_KEY, false);
    }

    private class HelpOverlayHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_START_HELP_OVERLAY && mItemView != null) {
                if (DBG) Log.d(TAG, "handleMessage MSG_START_HELP_OVERLAY");

                // Get the size of the action bar item
                int itemWidth = mItemView.getWidth();
                int itemHeight = mItemView.getHeight();
                if (DBG) Log.d(TAG, "item size=" + itemWidth + "x" + itemHeight);

                // Make sure the item is currently displayed in the action bar 
                // (the size is 0x0 if the item is in the options menu)
                if (itemWidth > 0 && itemHeight > 0) {
                    // Get the position of the action bar item
                    int[] location = new int[2];
                    mItemView.getLocationOnScreen(location);

                    // Get the size of the window which will provide the height of the statusbar
                    // if it is displayed at the top of the screen
                    Rect windowFrame = new Rect();
                    mItemView.getWindowVisibleDisplayFrame(windowFrame);
                    int windowWidth = windowFrame.right - windowFrame.left;
                    int windowHeight = windowFrame.bottom - windowFrame.top;
                    int statusbarHeight = windowFrame.top;
                    if (DBG) Log.d(TAG, "windowFrame=" + windowFrame);

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
                    if (DBG) Log.d(TAG, "Selected target area=" + left + " " + top + " " + right + " " + bottom);
                    
                    // Start the help overlay activity with the selected target area
                    Intent hov = new Intent(Intent.ACTION_MAIN);
                    hov.setComponent(new ComponentName(mContext, HelpOverlayActivity.class));
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_LEFT, left);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_TOP, top);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_RIGHT, right);
                    hov.putExtra(HelpOverlayActivity.EXTRA_TARGET_AREA_BOTTOM, bottom);
                    hov.putExtra(HelpOverlayActivity.EXTRA_POPUP_CONTENT_LAYOUT_ID, R.layout.help_overlay_scraper);
                    mContext.startActivity(hov);

                    // Remember that the help overlay has been activated so that it won't be shown again in the future
                    Editor ed = mPreferences.edit();
                    ed.putBoolean(NEW_VIDEOS_HELP_OVERLAY_KEY, true);
                    ed.commit();
                }
            }
        }
    }
    

    static class DropDownWindow extends PopupWindow implements OnAttachStateChangeListener {
        private final View mAnchor;

        public DropDownWindow(View anchor, View contentView) {
            super(anchor.getContext());
            mAnchor = anchor;
            final Resources r = anchor.getResources();
            setBackgroundDrawable(r.getDrawable(R.drawable.dialog_full_holo_dark));
            setWidth(r.getDimensionPixelSize(R.dimen.new_videos_action_dropdown_width)); // Takes too much space on tablets when using LayoutParams.WRAP_CONTENT
            setHeight(LayoutParams.WRAP_CONTENT);

            setContentView(contentView);

            setTouchable(true);
            setFocusable(true);

            // listen to anchor getting removed (e.g. activity destroyed)
            // -> dismiss() self so we don't keep this window open
            anchor.addOnAttachStateChangeListener(this);
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            // nothing
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            dismiss();
        }

        public void show() {
            // Fixes PopupWindow not visible on Kindle Fire HD (#2378)
            // While trying to fix the issue by setting carefully computed values, I found out that
            // any Y value larger than 2 fixes it... (0,1 or 2 doesn't)
            // I know this is nasty, but it's Kindle Fire HD that started this nastiness!!!
            // I tested that it works fine on Archos@Android4.1 and XperiaPhone@Android4.0.3
            showAsDropDown(mAnchor, 0, 3); // MAGICAL
        }
    }
}
