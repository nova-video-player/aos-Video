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

import com.archos.mediacenter.utils.MusicAlphabetIndexer;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediaprovider.video.VideoStore;

import android.app.ListActivity;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.io.IOException;


/**
 * Activity allowing the user to select a video on the device, and
 * return it to its caller. 
 */
public class VideoPicker extends ListActivity implements AdapterView.OnItemClickListener, View.OnClickListener {
    private static final boolean DBG = false;
    private static final String TAG = "VideoPicker";

    private static final String LIST_STATE_KEY = "liststate";
    private static final String FOCUS_KEY = "focused";
    
    // Arbitrary number, doesn't matter since we only do one query type
    private static final int MY_QUERY_TOKEN = 142;

    // These are the columns in the music cursor that we are interested in
    private static final String[] CURSOR_COLS = new String[] {
            VideoStore.Video.Media._ID,
            VideoStore.Video.Media.TITLE,
            VideoStore.Video.VideoColumns.DURATION
    };

    private VideoListAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Parcelable mListState = null;
    private boolean mListHasFocus;
    private Cursor mCursor;

    private View mProgressContainer;
    private View mListContainer;
    private View mCancelButton;

    private Uri mBaseUri;
    private Uri mSelectedUri;
    private long mSelectedId = -1;

    private boolean mListShown;


    /******************************************************************
    ** Activity life cycle
    ******************************************************************/

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.d(TAG, "onCreate : intent=" + getIntent());
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        if (icicle != null) {
            // Restore former activity state
            mListState = icicle.getParcelable(LIST_STATE_KEY);
            mListHasFocus = icicle.getBoolean(FOCUS_KEY);
        }
        
        if (Intent.ACTION_GET_CONTENT.equals(getIntent().getAction())) {
            mBaseUri = VideoStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else {
            mBaseUri = getIntent().getData();
            if (mBaseUri == null) {
                Log.e(TAG, "No data URI given to GET_CONTENT action");
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        setContentView(R.layout.video_picker);

        final ListView listView = getListView();
        listView.setItemsCanFocus(false);
        listView.setTextFilterEnabled(true);
        listView.setOnItemClickListener(this);

        mAdapter = new VideoListAdapter(this, listView,
                R.layout.video_picker_item, new String[] {},
                new int[] {});

        setListAdapter(mAdapter);
        
        // We manually save/restore the listview state
        listView.setSaveEnabled(false);

        mQueryHandler = new QueryHandler(this);

        mProgressContainer = findViewById(R.id.progressContainer);
        mListContainer = findViewById(R.id.listContainer);
        mCancelButton = findViewById(R.id.cancelButton);
        mCancelButton.setOnClickListener(this);
       
        // If there is a currently selected Uri, then try to determine who it is.
        if (mSelectedUri != null) {
            Uri.Builder builder = mSelectedUri.buildUpon();
            String path = mSelectedUri.getEncodedPath();
            int idx = path.lastIndexOf('/');
            if (idx >= 0) {
                path = path.substring(0, idx);
            }
            builder.encodedPath(path);
            Uri baseSelectedUri = builder.build();
            if (DBG) Log.v(TAG, "Selected Uri: " + mSelectedUri);
            if (DBG) Log.v(TAG, "Selected base Uri: " + baseSelectedUri);
            if (DBG) Log.v(TAG, "Base Uri: " + mBaseUri);
            if (baseSelectedUri.equals(mBaseUri)) {
                // If the base Uri of the selected Uri is the same as our
                // content's base Uri, then use the selection!
                mSelectedId = ContentUris.parseId(mSelectedUri);
            }
        }

        doQuery(false, null);
    }

    @Override public void onRestart() {
        super.onRestart();

        doQuery(false, null);
    }

    @Override public void onStop() {
        // We don't want the list to display the empty state, since when we
        // resume it will still be there and show up while the new query is
        // happening. After the async query finishes in response to onResume()
        // setLoading(false) will be called.
        mAdapter.setLoading(true);
        mAdapter.changeCursor(null);

        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the
        // QueryHandler has run
        icicle.putParcelable(LIST_STATE_KEY, getListView().onSaveInstanceState());
        icicle.putBoolean(FOCUS_KEY, getListView().hasFocus());
    }

 
    /******************************************************************
    ** Activity events management
    ******************************************************************/

    /*
     * Called when the user clicks on the Cancel button
     */
    public void onClick(View v) {
        if (v.getId() == R.id.cancelButton) {
            // Tell the caller that the pick request was cancelled by the user
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    /*
     * Called when the user clicks on an item of the list
     */
    public void onItemClick(AdapterView parent, View v, int position, long id) {
        mCursor.moveToPosition(position);

        mSelectedId = mCursor.getLong(mCursor.getColumnIndex(VideoStore.Video.Media._ID));
        mSelectedUri = ContentUris.withAppendedId(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, mSelectedId);

        if (mSelectedId >= 0) {
            // Valid Id => return the URI of the selected item to the caller
            setResult(RESULT_OK, new Intent().setData(mSelectedUri));
            finish();
        }
    }


    /******************************************************************
    ** Local methods
    ******************************************************************/

    private final class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(Context context) {
            super(context.getContentResolver());
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (!isFinishing()) {
                // Update the adapter: we are no longer loading, and have
                // a new cursor for it.
                mAdapter.setLoading(false);
                mAdapter.changeCursor(cursor);
                setProgressBarIndeterminateVisibility(false);
    
                // Now that the cursor is populated again, it's possible to restore the list state
                if (mListState != null) {
                    getListView().onRestoreInstanceState(mListState);
                    if (mListHasFocus) {
                        getListView().requestFocus();
                    }
                    mListHasFocus = false;
                    mListState = null;
                }
            } else {
                cursor.close();
            }
        }
    }

    private void makeListShown() {
        if (!mListShown) {
            mListShown = true;
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                    this, android.R.anim.fade_out));
            mProgressContainer.setVisibility(View.GONE);
            mListContainer.startAnimation(AnimationUtils.loadAnimation(
                    this, android.R.anim.fade_in));
            mListContainer.setVisibility(View.VISIBLE);
        }
    }

    private Cursor doQuery(boolean sync, String filterstring) {
        // Cancel any pending queries
        mQueryHandler.cancelOperation(MY_QUERY_TOKEN);

        if (sync) {
            try {
                return getContentResolver().query(mBaseUri, CURSOR_COLS, null, null, VideoStore.Video.Media.DEFAULT_SORT_ORDER);
            }
            catch (UnsupportedOperationException ex) {
                Log.e(TAG, "doQuery : UnsupportedOperationException=" + ex);
            }
        }
        else {
            mAdapter.setLoading(true);
            setProgressBarIndeterminateVisibility(true);
            mQueryHandler.startQuery(MY_QUERY_TOKEN, null, mBaseUri, CURSOR_COLS, null, null, VideoStore.Video.Media.DEFAULT_SORT_ORDER);
        }
        return null;
    }


    /******************************************************************
    ** VideoListAdapter
    ******************************************************************/

    class VideoListAdapter extends SimpleCursorAdapter implements SectionIndexer {
        final ListView mListView;
        
        private final StringBuilder mBuilder = new StringBuilder();
        private String mUnknownVideo;

        private int mIdIdx;
        private int mVideoIdx;
        private int mDurationIdx;

        private boolean mLoading = true;
        private MusicAlphabetIndexer mIndexer;

        class ViewHolder {
            TextView line1;
            TextView line2;
            TextView duration;
            CharArrayBuffer buffer1;
            char [] buffer2;
        }

        VideoListAdapter(Context context, ListView listView, int layout, String[] from, int[] to) {
            super(context, layout, null, from, to);

            mListView = listView;
            mUnknownVideo = context.getString(R.string.unknown_video_name);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = super.newView(context, cursor, parent);
            ViewHolder vh = new ViewHolder();
            vh.line1 = (TextView) v.findViewById(R.id.line1);
            vh.line2 = (TextView) v.findViewById(R.id.line2);
            vh.duration = (TextView) v.findViewById(R.id.duration);
            vh.buffer1 = new CharArrayBuffer(100);
            vh.buffer2 = new char[200];
            v.setTag(vh);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();

            cursor.copyStringToBuffer(mVideoIdx, vh.buffer1);
            vh.line1.setText(vh.buffer1.data, 0, vh.buffer1.sizeCopied);

            vh.duration.setText(MediaUtils.makeDurationString(context, cursor.getInt(mDurationIdx), false));
            vh.duration.setVisibility(View.VISIBLE);

            final StringBuilder builder = mBuilder;
            builder.delete(0, builder.length());

            String name = cursor.getString(mVideoIdx);
            if (name == null || name.equals("<unknown>")) {
                builder.append(mUnknownVideo);
            } else {
                builder.append(name);
            }
            int len = builder.length();
            builder.append('\n');
            builder.getChars(0, len, vh.buffer2, 0);
            vh.line2.setText(vh.buffer2, 0, len);
        }

        /*
         * The mLoading flag is set while we are performing a background
         * query, to avoid displaying the "No music" empty view during
         * this time.
         */
        public void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            if (DBG) Log.v(TAG, "Setting cursor to: " + cursor
                    + " from: " + VideoPicker.this.mCursor);
            
            VideoPicker.this.mCursor = cursor;
            
            if (cursor != null) {
                // Retrieve indices of the various columns we are interested in.
                mIdIdx = cursor.getColumnIndex(VideoStore.Video.Media._ID);
                mVideoIdx = cursor.getColumnIndex(VideoStore.Video.Media.TITLE);
                mDurationIdx = cursor.getColumnIndex(VideoStore.Video.Media.DURATION);

                // If we haven't yet created an indexer, create a new one
                if (mIndexer == null) {
                    mIndexer = new MusicAlphabetIndexer(cursor, mVideoIdx,
                         getResources().getString(R.string.fast_scroll_alphabet));
                } else {
                    // If we have a valid indexer, but the cursor has changed since
                    // its last use, then point it to the current cursor.
                    mIndexer.setCursor(cursor);
                }
            }
            
            // Ensure that the list is shown (and initial progress indicator
            // hidden) in case this is the first cursor we have gotten.
            makeListShown();
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            if (DBG) Log.v(TAG, "Getting new cursor...");
            return doQuery(true, constraint.toString());
        }
        
        public int getPositionForSection(int section) {
            Cursor cursor = getCursor();
            if (cursor == null) {
                // No cursor, the section doesn't exist so just return 0
                return 0;
            }
            
            return mIndexer.getPositionForSection(section);
        }

        public int getSectionForPosition(int position) {
            return 0;
        }

        public Object[] getSections() {
            if (mIndexer != null) {
                return mIndexer.getSections();
            }
            return null;
        }
    }
}
