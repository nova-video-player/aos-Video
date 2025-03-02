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

package com.archos.mediacenter.video.info;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.VideoDb;
import com.archos.mediaprovider.video.VideoOpenHelper;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.EpisodeTags;

import java.util.ArrayList;
import java.util.List;

public class VideoInfoActivity extends AppCompatActivity  implements VideoInfoActivityFragment.OnEpisodeSwitchListener {

    private static final String TAG = "VideoInfoActivity";
    private static final boolean DBG = false;

    public static final String SHARED_ELEMENT_NAME = "poster";
    public static final int MAX_VIDEO = 1000;
    public static final String EXTRA_NO_ONLINE_UPDATE = "no_online_updates";
    private static String name;
    private View mGlobalBackdrop;
    private ViewPager mViewPager;
    public static final String EXTRA_VIDEO = "VIDEO";
    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_PLAYLIST_ID = "playlist_id";
    public static final String EXTRA_LAUNCHED_FROM_PLAYER = "launchedFromPlayer";
    public static final String EXTRA_VIDEO_PATHS = "video_paths";
    public static final String EXTRA_VIDEO_PATH = "video_path";
    public static final String EXTRA_CURRENT_POSITION = "current_position";
    public static final String EXTRA_USE_VIDEO_METADATA = "useVideoMetadata";
    public static final String EXTRA_PLAYER_TYPE = "playerType";
    public static final String EXTRA_FORCE_VIDEO_SELECTION = "force_video_selection";
    List<VideoInfoActivityFragment> fragments = new ArrayList<>();

    private ArrayList<Uri> mPaths;
    private Video mCurrentVideo;
    private int mCurrentPosition;
    private long mId;
    private boolean mForceCurrentPosition;
    private Fragment mCurrentFragment;

    private static final float MILLISECONDS_PER_INCH_PIC = 45f; //default is 25f (bigger = slower)
    private static final float MILLISECONDS_PER_INCH_NUM = 85f;

    private boolean BrowserListOfEpisodes;

    private GestureDetector gestureDetector;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private VideoInfoActivityFragment videoInfoActivityFragment;
    private boolean gestureEnabled = true; // Default to true

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DBG) Log.d(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        ViewGroup globalLayout = (ViewGroup) getWindow().getDecorView();

        if(getIntent().hasExtra(EXTRA_VIDEO_PATHS))
            mPaths = (ArrayList)getIntent().getSerializableExtra(EXTRA_VIDEO_PATHS);
        if(mPaths==null) {
            mPaths = new ArrayList<>();
            if(getIntent().getData()!=null)
                mPaths.add(getIntent().getData());
        }
        mId = getIntent().getLongExtra(EXTRA_VIDEO_ID, -1);
        mCurrentPosition = getIntent().getIntExtra(EXTRA_CURRENT_POSITION, 0);
        mForceCurrentPosition = getIntent().getBooleanExtra(EXTRA_FORCE_VIDEO_SELECTION, false);
        if(getIntent().hasExtra(EXTRA_VIDEO))
            mCurrentVideo = (Video) getIntent().getSerializableExtra(EXTRA_VIDEO);

        // Load the fragment dynamically
        if (savedInstanceState == null) {
            loadFragment(VideoInfoActivityFragment.getInstance(mCurrentVideo, null, mId, mForceCurrentPosition));
        }
        setContentView(R.layout.activity_video_info);
        mViewPager = (ViewPager)findViewById(R.id.pager);

        if (name!=null) {
            BrowserListOfEpisodes = name.equalsIgnoreCase("BrowserListOfEpisodes");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.edit().putBoolean("BrowserListOfEpisodes", BrowserListOfEpisodes).apply();

        if(mCurrentVideo instanceof Episode) {
            mViewPager.setVisibility(View.GONE);
            // Initialize GestureDetector and pass the listener
            gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    float deltaX = e1.getX() - e2.getX();
                    float deltaY = e1.getY() - e2.getY();

                    if (Math.abs(deltaX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                            && Math.abs(deltaX) > Math.abs(deltaY) * 2) { // Ensure horizontal movement is dominant

                        if (deltaX > 0) {
                            Log.d("GestureTest", "Swipe LEFT detected");
                            onEpisodeSwiped(1);  // Next episode
                        } else {
                            Log.d("GestureTest", "Swipe RIGHT detected");
                            onEpisodeSwiped(-1); // Previous episode
                        }
                        return true;
                    }
                    return false;
                }
            });
        }
        mGlobalBackdrop = getLayoutInflater().inflate(R.layout.browser_main_video_backdrop, null);
        mViewPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
        if(mCurrentPosition>0)
            mViewPager.setCurrentItem(mCurrentPosition);
        globalLayout.addView(mGlobalBackdrop, 0);
    }

    protected void onStop(){
        if (DBG) Log.d(TAG,"onStop");
        super.onStop();
    }

    public View getGlobalBackdropView(){
        return mGlobalBackdrop;
    }

    public void setBackgroundColor(int color){
        getWindow().getDecorView().setBackgroundColor(color);
        getWindow().setNavigationBarColor(color);
        getWindow().setStatusBarColor(color);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (DBG) Log.d(TAG,"onOptionsItemSelected");
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement


        return super.onOptionsItemSelected(item);
    }

    public static void startInstance(Context context,
                                     Fragment fragment,
                                     Video currentVideo,
                                     int currentPosition,
                                     ArrayList<Uri> paths,
                                     long id,
                                     boolean forceVideoSelection,
                                     long playlistId){

        if (DBG) Log.d(TAG, "startInstance: " + currentVideo.getFilePath());
        Intent intent = new Intent(context, VideoInfoActivity.class);
        if(currentVideo!=null)
            intent.putExtra(VideoInfoActivityFragment.EXTRA_VIDEO, currentVideo);
        if(paths!=null)
            intent.putExtra(EXTRA_VIDEO_PATHS, paths);
        intent.putExtra(EXTRA_FORCE_VIDEO_SELECTION,forceVideoSelection);
        intent.putExtra(EXTRA_VIDEO_ID, id);
        intent.putExtra(EXTRA_PLAYLIST_ID, playlistId);
        intent.putExtra(EXTRA_CURRENT_POSITION, currentPosition);
        if(fragment!=null) {
            fragment.startActivityForResult(intent, 0);
            name = fragment.getClass().getSimpleName();
        }else if(context instanceof AppCompatActivity)
            ((AppCompatActivity) context).startActivityForResult(intent, 0);
        else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);

        }


    }
    public static void startInstance(Context context, Video video, Uri path, Long id){
        if (DBG) Log.d(TAG, "startInstance: " + path);
        ArrayList<Uri> paths = new ArrayList<>();
        paths.add(path);

        Intent intent = new Intent(context, VideoInfoActivity.class);
        intent.putExtra(EXTRA_VIDEO, video);
        intent.putExtra(EXTRA_VIDEO_ID, id);


        startInstance(context,null,video, 0,paths,id, false, -1);
    }

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {



        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container,position, object);
            mCurrentFragment = (Fragment) object;

        }

        @Override
        public Fragment getItem(int position) {
            Video video = null;
            long id = -1;
            boolean forceVideoSelection = false;
            if(position == mCurrentPosition) {
                video = mCurrentVideo;
                id = mId;
                forceVideoSelection = mForceCurrentPosition;
            }
            return VideoInfoActivityFragment.getInstance(video,mPaths.size()>0? mPaths.get(position):null, id, forceVideoSelection);
        }

        @Override
        public int getCount() {
            return mPaths.size()>0?mPaths.size():1;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;  // Forces ViewPager to recreate all fragments
        }
    }

    // This method should not have the @Override annotation
    public void onEpisodeSwiped(int direction) {
        videoInfoActivityFragment = (VideoInfoActivityFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (videoInfoActivityFragment != null) {
            Log.d("GESTURE", "Swipe detected, direction: " + direction);
            videoInfoActivityFragment.SwitchEpisode(direction);
        } else {
            Log.e("GESTURE", "Fragment is null, cannot switch episode.");
        }
    }
    /** Loads a new fragment into the activity */
    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
        mCurrentFragment = fragment;
    }

    public void setGestureEnabled(boolean enabled) {
        gestureEnabled = enabled;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!gestureEnabled) {
            return super.dispatchTouchEvent(event); // Ignore gesture detection
        }
        if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mViewPager.getAdapter() != null) {
            mViewPager.getAdapter().notifyDataSetChanged();  // Force ViewPager to refresh its fragments
        }
    }
}
