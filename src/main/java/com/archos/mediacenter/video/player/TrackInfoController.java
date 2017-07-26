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

package com.archos.mediacenter.video.player;


import android.content.Context;
import android.graphics.Rect;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import com.archos.mediacenter.video.R;

import java.util.ArrayList;

public class TrackInfoController implements OnMenuItemClickListener, OnItemClickListener {
    private MenuItem mMenuItem = null;
    private ListPopupWindow mPopup = null;
    private TrackInfoAdapter mTrackInfoAdapter = null;
    private int mTrackPosition = 0;
    private TrackInfoListener mTrackInfoListener = null;
    private TrackItemList mTrackItemList = new TrackItemList();
    private ActionBar mActionBar;
    private View mAnchorView;
    private Context mContext;
    private boolean mIsAlwaysDisplayed;


    public interface TrackInfoListener {
        public boolean onTrackSelected(TrackInfoController trackInfoController, int position, CharSequence name, CharSequence summary);
        public boolean onSettingsSelected(TrackInfoController trackInfoController, int key, CharSequence name);
    }

    private static final int TRACK_INFO = 0;
    private static final int TRACK_SETTINGS = 1;
    private static final int TRACK_SEP = 2;

    private class TrackItem {
        public final int type;
        public TrackItem(int type) {
            this.type = type;
        }
    }

    private class TrackInfo extends TrackItem {
        public final CharSequence name;
        public final CharSequence summary;
        public final int position;
        public TrackInfo(int position, CharSequence name) {
            super(TRACK_INFO);
            this.position = position;
            this.name = name;
            this.summary = null;
        }
        public TrackInfo(int position, CharSequence name, CharSequence summary) {
            super(TRACK_INFO);
            this.position = position;
            this.name = name;
            this.summary = summary;
        }
        public String toString() {
            return this.name.toString();
        }
    }

    private class TrackSeparator extends TrackItem {
        public TrackSeparator() {
            super(TRACK_SEP);
        }
    }

    private class TrackSettings extends TrackItem {
        public final CharSequence name;
        public final int iconResId;
        public final int key;
        public boolean enabled = true;

        public TrackSettings(CharSequence name, int iconResId, int key) {
            super(TRACK_SETTINGS);
            this.name = name;
            this.iconResId = iconResId;
            this.key = key;
        }
    }

    public class TrackItemList extends ArrayList<TrackItem> {
        private static final long serialVersionUID = 1L;
        private int mTrackCount = 0;

        public void addTrack(CharSequence name, CharSequence summary) {
            add(new TrackInfo(mTrackCount++, name, summary));
        }

        public void addSeparator() {
            add(new TrackSeparator());
        }

        public void addSettings(CharSequence name, int iconResId, int key) {
            add(new TrackSettings(name, iconResId, key));
        }

        public int getTrackCount() {
            return mTrackCount;
        }

        public void clear() {
            super.clear();
            mTrackCount = 0;
        }
    }

    public class TrackInfoAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private CheckedHolder mCheckedHolder = new CheckedHolder();

        private class CheckedHolder {
            private View mView = null;
            public void setView(View view, boolean update) {
                if (update)
                    setChecked(false);
                mView = view;
                if (update)
                    setChecked(true);
            }
            private void setChecked(boolean checked) {
                if (mView != null) {
                    RadioButton radio = (RadioButton)mView.findViewById(R.id.radio);
                    if (radio != null)
                        radio.setChecked(checked);
                }
            }
        }

        public TrackInfoAdapter(LayoutInflater inflater) {
            super();
            mInflater = inflater;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v = null;
            TrackItem item = mTrackItemList.get(position);

            if (convertView == null) {
                switch (item.type) {
                    case TRACK_INFO:
                        TrackInfo trackInfo = (TrackInfo) item;
                        v = mInflater.inflate(trackInfo.summary == null ?
                                R.layout.menu_track_info_view : R.layout.menu_track_info_summary_view,
                                parent, false);
                        break;
                    case TRACK_SETTINGS:
                        v = mInflater.inflate(R.layout.menu_track_settings_view, parent, false);
                        break;
                    case TRACK_SEP:
                        v = mInflater.inflate(R.layout.menu_track_separator_view, parent, false);
                        break;
                }
            } else {
                v = convertView;
            }
            switch (item.type) {
                case TRACK_INFO:
                    TrackInfo trackInfo = (TrackInfo) item;
                    RadioButton radio;

                    if (trackInfo.name != null)
                        ((TextView)v.findViewById(R.id.name)).setText(trackInfo.name);
                    if (trackInfo.summary != null) {
                        TextView textView = (TextView)v.findViewById(R.id.summary);
                        if (textView != null)
                            textView.setText(trackInfo.summary);
                    }
                    radio = (RadioButton)v.findViewById(R.id.radio);
                    if (radio != null)
                        radio.setChecked(trackInfo.position == mTrackPosition);
                    if (trackInfo.position == mTrackPosition)
                        mCheckedHolder.setView(v, false);
                    break;
                case TRACK_SETTINGS:
                    TrackSettings trackSettings = (TrackSettings) item;
                    if (trackSettings.name != null) {
                        TextView tv = (TextView) v.findViewById(R.id.name);
                        tv.setText(trackSettings.name);
                        tv.setEnabled(trackSettings.enabled);
                    }
                    if (trackSettings.iconResId != 0) {
                        ((ImageView)v.findViewById(R.id.icon)).setImageResource(trackSettings.iconResId);
                    }
                    break;
                case TRACK_SEP:
                    break;
            }
            return v;
        }

        @Override
        public int getItemViewType(int position) {
            return mTrackItemList.get(position).type;
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public boolean isEnabled(int position) {
            switch (mTrackItemList.get(position).type) {
                case TRACK_INFO:
                    return true;
                case TRACK_SETTINGS:
                    return ((TrackSettings)mTrackItemList.get(position)).enabled;
                case TRACK_SEP:
                    return false;
            }
            return super.isEnabled(position);
        }

        public int getCount() {
            return mTrackItemList.size();
        }

        public Object getItem(int position) {
            return mTrackItemList.get(position);
        }

        public long getItemId(int position) {
            return 0;
        }

        public void setChecked(View view) {
            mCheckedHolder.setView((ViewGroup)view, true);
        }
    }

    public TrackInfoController(Context context, LayoutInflater inflater, View anchor, ActionBar actionBar) {
        mContext = context;
        mTrackInfoAdapter = new TrackInfoAdapter(inflater);

        mAnchorView = anchor;
        mActionBar = actionBar;
    }

    public void attachMenu(Menu menu, int resId) {
        mMenuItem = menu.add(null);
        mMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mMenuItem.setIcon(resId);
        mMenuItem.setOnMenuItemClickListener(this);
        setVisible();
    }

    public void setListener(TrackInfoListener listener) {
        mTrackInfoListener = listener;
    }

    public boolean onMenuItemClick(MenuItem item) {
        if (mTrackInfoAdapter != null && mPopup != null) {
            // little hack: put the popup below the action bar
            View actionBarView = mActionBar.getCustomView();
            int actionBarHeight = actionBarView.getBottom();

            Rect windowFrame = new Rect();
            actionBarView.getWindowVisibleDisplayFrame(windowFrame);
            int statusbarHeight = windowFrame.top;

            mAnchorView.setY(actionBarHeight + statusbarHeight);
            mPopup.show();
            return true;
        }
        return false;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        boolean dismiss = false;
        TrackItem item = mTrackItemList.get(position);
        switch (item.type) {
            case TRACK_INFO:
                dismiss = false;
                if (mTrackInfoListener != null) {
                    TrackInfo trackInfo = (TrackInfo) item;
                    if (mTrackInfoListener.onTrackSelected(this, trackInfo.position, trackInfo.name, trackInfo.summary)) {
                        mTrackInfoAdapter.setChecked(view);
                        mTrackPosition = position;
                    }
                }
                break;
            case TRACK_SETTINGS:
                dismiss = true;
                if (mTrackInfoListener != null) {
                    TrackSettings trackSettings = (TrackSettings) item;
                    mTrackInfoListener.onSettingsSelected(this, trackSettings.key, trackSettings.name);
                }
                break;
            case TRACK_SEP:
                break;
        }
        if (dismiss)
            mPopup.dismiss();
    }

    public void setTrack(int position) {
        mTrackPosition = position;
    }

    public int getTrack() {
        return mTrackPosition;
    }

    public int getTrackCount() {
        return mTrackItemList.getTrackCount();
    }
    
    public CharSequence getTrackNameAt(int position) {
        TrackItem item = mTrackItemList.get(position);
        return ((TrackInfo)item).name;
    }

    public void addTrack(CharSequence name) {
        addTrack(name, null);
    }

    public void addTrack(CharSequence name, CharSequence summary) {
        mTrackItemList.addTrack(name, summary);
        setVisible();
    }

    public void addSeparator() {
        mTrackItemList.addSeparator();
    }

    public void addSettings(CharSequence name, int iconResId, int key) {
        mTrackItemList.addSettings(name, iconResId, key);
    }

    public void enableSettings(int key, boolean enabled, boolean invalidate) {
        for (int i=0 ; i < mTrackItemList.size() ; i++) {
            TrackItem item = mTrackItemList.get(i);
            if (item.type == TRACK_SETTINGS) {
                TrackSettings trackSettings = (TrackSettings)item;
                if (trackSettings.key == key) {
                    trackSettings.enabled = enabled;
                    if (invalidate&&mPopup.getListView()!=null)
                        mPopup.getListView().invalidateViews();
                    break;
                }
            }
        }
    }

    public void resetPopup() {
        // Make sure to close the popup first
        if (mPopup != null) {
            mPopup.dismiss();
        }

        // Build and initialize a new popup
        mPopup = new ListPopupWindow(mContext, null);
        mPopup.setAdapter(mTrackInfoAdapter);
        mPopup.setAnchorView(mAnchorView);
        mPopup.setModal(true);
        mPopup.setOnItemClickListener(this);
        int popupContentWidth = mContext.getResources().getDimensionPixelSize(R.dimen.player_subtitles_popup_width);
        mPopup.setContentWidth(popupContentWidth);
        setVisible();
    }

    public void clear() {
        mTrackItemList.clear();
        mTrackPosition = 0;
        setVisible();
    }

    public void setAlwayDisplay(boolean alwaysDisplay) {
        mIsAlwaysDisplayed = alwaysDisplay;
    }
    private void setVisible() {
        if (mMenuItem != null)
            mMenuItem.setVisible(getTrackCount()>1||mIsAlwaysDisplayed);
    }
}