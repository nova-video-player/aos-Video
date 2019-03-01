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

package com.archos.mediacenter.video.leanback.search;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v17.leanback.app.SearchFragment;
import android.view.KeyEvent;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.leanback.details.VideoDetailsActivity;
import com.archos.mediacenter.video.leanback.details.VideoDetailsFragment;
import com.archos.mediacenter.video.info.SingleVideoLoader;


public class VideoSearchActivity extends Activity {

    public static final String EXTRA_SEARCH_MODE = "searchMode";
    public static final int SEARCH_MODE_ALL = 0;
    public static final int SEARCH_MODE_MOVIE = 1;
    public static final int SEARCH_MODE_EPISODE = 3;
    public static final int SEARCH_MODE_NON_SCRAPED = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent != null ? intent.getAction() : null;
        if (Intent.ACTION_VIEW.equals(action)) {
            // this is something we got from the global search bar
            Cursor cursor = null;
            try {
                int videoId = Integer.parseInt(intent.getData().getLastPathSegment());
                SingleVideoLoader loader = new SingleVideoLoader(this, videoId);
                cursor = getContentResolver().query(loader.getUri(), loader.getProjection(), loader.getSelection(), loader.getSelectionArgs(), loader.getSortOrder());
                if (cursor.getCount() > 0) {
                    VideoCursorMapper cursorMapper = new VideoCursorMapper();
                    cursorMapper.publicBindColumns(cursor);
                    cursor.moveToFirst();
                    Video video = (Video)cursorMapper.publicBind(cursor);

                    Intent activityIntent = new Intent(this, VideoDetailsActivity.class);
                    activityIntent.putExtra(VideoDetailsFragment.EXTRA_VIDEO, video);
                    startActivity(activityIntent);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            finish();
            return;
        }

        setContentView(R.layout.androidtv_search_activity);

        Bundle args = new Bundle();
        args.putInt(EXTRA_SEARCH_MODE, getIntent().getIntExtra(EXTRA_SEARCH_MODE, SEARCH_MODE_ALL));

        VideoSearchFragment frag = new VideoSearchFragment();
        frag.setArguments(args);
        getFragmentManager().beginTransaction().add(R.id.video_search_fragment, frag).commit();
    }

    /**
     * Catch the SEARCH key so that the general Android TV search is not launched.
     * Try to relaunch the AVP search instead
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SEARCH) {
            Fragment f = getFragmentManager().findFragmentById(R.id.video_search_fragment);
            if (f instanceof SearchFragment) {
                ((SearchFragment)f).startRecognition();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event); // default
    }
}
