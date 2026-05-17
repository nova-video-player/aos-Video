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

import static com.archos.mediacenter.video.utils.VideoUtils.isColorDark;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class VideoInfoActivity extends AppCompatActivity {

    private static final String TAG = "VideoInfoActivity";
    private static final boolean DBG = false;

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


    @SuppressWarnings("deprecation") // getSerializableExtra: API 33+ branch uses typed form; else branch suppressed
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DBG) Log.d(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        ViewGroup globalLayout = (ViewGroup) getWindow().getDecorView();

        if(getIntent().hasExtra(EXTRA_VIDEO_PATHS))
            mPaths = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? getIntent().getSerializableExtra(EXTRA_VIDEO_PATHS, ArrayList.class)
                    : (ArrayList) getIntent().getSerializableExtra(EXTRA_VIDEO_PATHS);
        if(mPaths==null) {
            mPaths = new ArrayList<>();
            if(getIntent().getData()!=null)
                mPaths.add(getIntent().getData());
        }
        mId = getIntent().getLongExtra(EXTRA_VIDEO_ID, -1);
        mCurrentPosition = getIntent().getIntExtra(EXTRA_CURRENT_POSITION, 0);
        if(getIntent().hasExtra(EXTRA_VIDEO))
            mCurrentVideo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? getIntent().getSerializableExtra(EXTRA_VIDEO, Video.class)
                    : (Video) getIntent().getSerializableExtra(EXTRA_VIDEO);

        mForceCurrentPosition = getIntent().getBooleanExtra(EXTRA_FORCE_VIDEO_SELECTION, false);
        mGlobalBackdrop = getLayoutInflater().inflate(R.layout.browser_main_video_backdrop, null);
        setContentView(R.layout.activity_video_info);
        mViewPager = (ViewPager)findViewById(R.id.pager);
        mViewPager.setAdapter(new ScreenSlidePagerAdapter(
                getSupportFragmentManager(),
                this, // Pass the activity instance for the WeakReference
                mPaths,
                mCurrentVideo,
                mId,
                mCurrentPosition,
                mForceCurrentPosition
        ));
        if(mCurrentPosition>0)
            mViewPager.setCurrentItem(mCurrentPosition);
        globalLayout.addView(mGlobalBackdrop, 0);
    }

    protected void onStop(){
        if (DBG) Log.d(TAG,"onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        super.onDestroy();
        // This is the critical fix: remove the backdrop from the window's DecorView.
        if (mGlobalBackdrop != null) {
            ViewGroup globalLayout = (ViewGroup) getWindow().getDecorView();
            globalLayout.removeView(mGlobalBackdrop);
            mGlobalBackdrop = null; // Also good practice to nullify the reference.
        }
        // Nullify the fragment reference to be safe
        mCurrentFragment = null;
    }

    public View getGlobalBackdropView(){
        return mGlobalBackdrop;
    }

    @SuppressWarnings("deprecation")
    public void setBackgroundColor(int color) {
        getWindow().getDecorView().setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
            insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            insetsController.setAppearanceLightStatusBars(isColorDark(color));
            insetsController.setAppearanceLightNavigationBars(isColorDark(color));
        } else {
            // For older APIs (API < 29), setStatusBarColor/setNavigationBarColor are the only option
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
        if (DBG) Log.d(TAG,"onOptionsItemSelected");
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement


        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("deprecation") // legacy overload retained for callers without ActivityResultLauncher
    public static void startInstance(Context context,
                                     Fragment fragment,
                                     Video currentVideo,
                                     int currentPosition,
                                     ArrayList<Uri> paths,
                                     long id,
                                     boolean forceVideoSelection,
                                     long playlistId){

        if (DBG) Log.d(TAG, "startInstance: " + currentVideo.getFilePath());
        Intent intent = buildIntent(context, currentVideo, currentPosition, paths, id, forceVideoSelection, playlistId);
        if (fragment != null) {
            fragment.startActivityForResult(intent, 0);
        } else if (context instanceof AppCompatActivity) {
            ((AppCompatActivity) context).startActivityForResult(intent, 0);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }

    public static void startInstance(ActivityResultLauncher<Intent> launcher,
                                     Context context,
                                     Video currentVideo,
                                     int currentPosition,
                                     ArrayList<Uri> paths,
                                     long id,
                                     boolean forceVideoSelection,
                                     long playlistId){
        if (DBG) Log.d(TAG, "startInstance (launcher): " + currentVideo.getFilePath());
        launcher.launch(buildIntent(context, currentVideo, currentPosition, paths, id, forceVideoSelection, playlistId));
    }

    private static Intent buildIntent(Context context,
                                      Video currentVideo,
                                      int currentPosition,
                                      ArrayList<Uri> paths,
                                      long id,
                                      boolean forceVideoSelection,
                                      long playlistId) {
        Intent intent = new Intent(context, VideoInfoActivity.class);
        if (currentVideo != null)
            intent.putExtra(VideoInfoActivityFragment.EXTRA_VIDEO, currentVideo);
        if (paths != null)
            intent.putExtra(EXTRA_VIDEO_PATHS, paths);
        intent.putExtra(EXTRA_FORCE_VIDEO_SELECTION, forceVideoSelection);
        intent.putExtra(EXTRA_VIDEO_ID, id);
        intent.putExtra(EXTRA_PLAYLIST_ID, playlistId);
        intent.putExtra(EXTRA_CURRENT_POSITION, currentPosition);
        return intent;
    }
    public static void startInstance(Context context, Video video, Uri path, Long id){
        if (DBG) Log.d(TAG, "startInstance: " + path);
        ArrayList<Uri> paths = new ArrayList<>();
        paths.add(path);
        startInstance(context,null,video, 0,paths,id, false, -1);
    }

    private static class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {

        // Fields to hold the data previously accessed from the outer class
        private final ArrayList<Uri> mPaths;
        private final Video mCurrentVideo;
        private final long mId;
        private final int mCurrentPosition;
        private final boolean mForceCurrentPosition;

        // Use a WeakReference to hold the activity to prevent leak cycles
        private final WeakReference<VideoInfoActivity> mActivityRef;

        public ScreenSlidePagerAdapter(FragmentManager fm, VideoInfoActivity activity, ArrayList<Uri> paths,
                                       Video currentVideo, long id, int currentPosition, boolean forceCurrentPosition) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.mActivityRef = new WeakReference<>(activity);
            this.mPaths = paths;
            this.mCurrentVideo = currentVideo;
            this.mId = id;
            this.mCurrentPosition = currentPosition;
            this.mForceCurrentPosition = forceCurrentPosition;
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            VideoInfoActivity activity = mActivityRef.get();
            // Check if activity is still alive before using it
            if (activity != null) {
                activity.mCurrentFragment = (Fragment) object;
            }
        }

        @NonNull
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
            // Use the class's fields instead of the outer activity's fields
            return VideoInfoActivityFragment.getInstance(video, !mPaths.isEmpty() ? mPaths.get(position) : null, id, forceVideoSelection);
        }

        @Override
        public int getCount() {
            // Handle null mPaths gracefully
            if (mPaths == null || mPaths.isEmpty()) {
                return 1;
            }
            return mPaths.size();
        }
    }
}
