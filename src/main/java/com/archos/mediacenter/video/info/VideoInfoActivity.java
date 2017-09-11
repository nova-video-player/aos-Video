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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;

import java.util.ArrayList;

public class VideoInfoActivity extends AppCompatActivity {

    public static final String SHARED_ELEMENT_NAME = "poster";
    public static final int MAX_VIDEO = 200;
    public static final String EXTRA_NO_ONLINE_UPDATE = "no_online_updates";
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

    private ArrayList<Uri> mPaths;
    private Video mCurrentVideo;
    private int mCurrentPosition;
    private long mId;
    private boolean mForceCurrentPosition;
    private Fragment mCurrentFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        if(getIntent().hasExtra(EXTRA_VIDEO))
            mCurrentVideo = (Video) getIntent().getSerializableExtra(EXTRA_VIDEO);

        mForceCurrentPosition = getIntent().getBooleanExtra(EXTRA_FORCE_VIDEO_SELECTION, false);
        mGlobalBackdrop = getLayoutInflater().inflate(R.layout.browser_main_video_backdrop, null);
        setContentView(R.layout.activity_video_info);
        mViewPager = (ViewPager)findViewById(R.id.pager);
        mViewPager.setAdapter(new ScreenSlidePagerAdapter(getSupportFragmentManager()));
        if(mCurrentPosition>0)
            mViewPager.setCurrentItem(mCurrentPosition);
        globalLayout.addView(mGlobalBackdrop, 0);


    }

    protected void onStop(){
        super.onStop();
    }

    public View getGlobalBackdropView(){
        return mGlobalBackdrop;
    }

    public void setBackgroundColor(int color){
        getWindow().getDecorView().setBackgroundColor(color);
        if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(color);
            getWindow().setStatusBarColor(color);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
        }else if(context instanceof Activity)
            ((Activity) context).startActivityForResult(intent, 0);
        else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);

        }


    }
    public static void startInstance(Context context, Video video, Uri path, Long id){
        ArrayList<Uri> paths = new ArrayList<>();
        paths.add(path);
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
    }
}
