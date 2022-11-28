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

import static com.archos.filecorelibrary.FileUtils.hasManageExternalStoragePermission;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.DisplayCutout;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.text.HtmlCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.utils.GlobalResumeView;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.DensityTweak;
import com.archos.mediacenter.video.EntryActivity;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;
import com.archos.mediacenter.video.autoscraper.AutoScraperActivity;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.BrowserListOfSeasons;
import com.archos.mediacenter.video.browser.BrowserByIndexedVideos.CursorBrowserByVideo;
import com.archos.mediacenter.video.browser.adapters.mappers.VideoCursorMapper;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.dialogs.Paste;
import com.archos.mediacenter.video.browser.filebrowsing.BrowserByVideoFolder;
import com.archos.mediacenter.video.info.SingleVideoLoader;
import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediacenter.video.player.PrivateMode;
import com.archos.mediacenter.video.utils.ExternalPlayerResultListener;
import com.archos.mediacenter.video.utils.ExternalPlayerWithResultStarter;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.mediacenter.video.utils.PlayUtils;
import com.archos.mediacenter.video.utils.TraktSigninDialogPreference;
import com.archos.mediacenter.video.utils.VideoPreferencesActivity;
import com.archos.mediacenter.video.utils.VideoPreferencesCommon;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.ScraperStore;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.AutoScrapeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/*
 * This is the launch class for the video browser.
 */
public class MainActivity extends BrowserActivity implements ExternalPlayerWithResultStarter {

    private static final Logger log = LoggerFactory.getLogger(MainActivity.class);

    public final static int DIALOG_DELETE = 1;
    public final static int DIALOG_DELETING = 2;
    public final static int MENU_SCRAPER_GROUP = 1;

    private final static int MENU_START_AUTO_SCRAPER_ACTIVITY = 0;
    private final static int MENU_SCRAPER_SETTINGS = 1;

    private final static int MENU_PREFERENCES_GROUP = 5;
    private final static int MENU_PREFERENCES_ITEM = 31;

    private static final int MENU_SEARCH_GROUP = 6;
    private static final int MENU_SEARCH_ITEM = 32;

    private static final int MENU_PRIVATE_MODE_GROUP = 7;
    private static final int MENU_PRIVATE_MODE_ITEM = 33;
    private static final int PERMISSION_REQUEST = 1;
    private static final int PLAY_ACTIVITY_REQUEST_CODE = 900;
    public static String LAUNCH_DIALOG = "LAUNCH_DIALOG";
    private PermissionChecker mPermissionChecker;

    private SearchView mSearchView;
    public static final int MENU_CHANGE_FOLDER = 6;

    public static final int ACTIVITY_REQUEST_CODE_PREFERENCES = 101;

    private int mGlobalResumeId = -1;
    private GlobalResumeContentObserver mGlobalResumeContentObserver = null;

    private final static String SCRAPER_SELECTION = ScraperStore.AllVideos.SCRAPER_TYPE + "=? AND "
            + ScraperStore.AllVideos.SCRAPER_ID + "=?";
    private final static String TITLE_FORMAT = "%s  S%dE%d <i> %s </i>";

    private final static String[] CURSORS = {
            VideoStore.Video.VideoColumns._ID, VideoStore.Video.VideoColumns.TITLE,
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE
    };

    private final static String[] SCRAPER_PROJECTION = {
            ScraperStore.AllVideos.MOVIE_OR_SHOW_NAME, ScraperStore.AllVideos.MOVIE_OR_SHOW_COVER,
            ScraperStore.AllVideos.EPISODE_NUMBER, ScraperStore.AllVideos.EPISODE_SEASON_NUMBER,
            ScraperStore.AllVideos.EPISODE_NAME
    };

    private final static String StereoActivity = "com.archos.mediacenter.video.browser.MainActivityStereo";

    private NewVideosActionProvider mNewVideosActionProvider = null;

    protected SharedPreferences mPreferences;

    private View mGlobalBackdrop;

    public static Boolean mStereoForced = false;
    private BroadcastReceiver mTraktRelogBroadcastReceiver;
    private AlertDialog mTraktRelogAlertDialog;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private ViewStub mGlobalResumeViewStub;
    private GlobalResumeView mGlobalResumeView;
    private MenuItem mSearchItem;
    private int mNavigationMode;
    private void updateStereoMode(Intent intent) {
        mStereoForced = false;
        try {
            if (intent.getComponent().getClassName().equals(StereoActivity)) {
                ActivityInfo ai = getPackageManager().getActivityInfo(getComponentName(), PackageManager.GET_ACTIVITIES|PackageManager.GET_META_DATA);
                Bundle bundle = ai.metaData;
                mStereoForced = bundle.getBoolean("stereo_mode");
            }
        } catch (NameNotFoundException e) {
            log.error("Failed to load meta-data, NameNotFound: " + e.getMessage());
        } catch (NullPointerException e) {
            log.error("Failed to load meta-data, NullPointer: " + e.getMessage());
        }        
    }

    public void setBackground() {
        int backgroundResId = PrivateMode.isActive() ? R.drawable.background_2014_dark : R.drawable.background_2014;
        getWindow().getDecorView().setBackgroundResource(backgroundResId);
        if(mDrawerLayout != null)
            mDrawerLayout.findViewById(R.id.category_container).setBackgroundResource(backgroundResId);
    }

    private void setHomeButton() {
        int iconResId = PrivateMode.isActive() ? R.mipmap.nova_private : R.mipmap.nova;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_OPTIONS_PANEL);
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null){
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    R.string.drawer_open, R.string.drawer_close);
            mDrawerToggle.setDrawerIndicatorEnabled(true);
            mDrawerToggle.syncState();

            if(savedInstanceState==null && !isShortcutIntent())
                mDrawerLayout.openDrawer(GravityCompat.START);

        }

        // determine if display has cutouts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener( new View.OnApplyWindowInsetsListener() {
                @SuppressLint("NewApi")
                @Override
                public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
                        if (cutout != null) {
                            log.debug("device with cutout");
                            MiscUtils.hasCutout = true;
                        } else
                            log.debug("device without cutout");
                    }
                    getWindow().getDecorView().setOnApplyWindowInsetsListener(null);
                    return view.onApplyWindowInsets(insets);
                }
            });
        }

        mGlobalResumeViewStub = (ViewStub) findViewById(R.id.global_resume_stub);
        AutoScrapeService.registerObserver(this);
        mPermissionChecker = new PermissionChecker(hasManageExternalStoragePermission(getApplicationContext()));
        setBackground();

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mNewVideosActionProvider = new NewVideosActionProvider(this);
        LoaderManager.getInstance(this).initLoader(0, null, mNewVideosActionProvider);

        // Register a content observer which will be used to update the global
        // resume view
        mGlobalResumeContentObserver = new GlobalResumeContentObserver();

        if (savedInstanceState==null) {
            handleIntent(getIntent());
        }

        ViewGroup globalLayout = (ViewGroup) getWindow().getDecorView();
        mGlobalBackdrop = getLayoutInflater().inflate(R.layout.browser_main_video_backdrop, null);
        globalLayout.addView(mGlobalBackdrop, 0);
        if(Trakt.isTraktV1Enabled(this,PreferenceManager.getDefaultSharedPreferences(this)))
        {
        	Trakt.wipePreferences(PreferenceManager.getDefaultSharedPreferences(MainActivity.this),false);
        	new AlertDialog.Builder(this)
        	.setTitle("Trakt")
        	.setMessage(R.string.trakt_change)
        	.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int which) { 
        			dialog.dismiss();
        		}
        	})

        	.setIcon(android.R.drawable.ic_dialog_alert)
        	.show();
        }

        //in case we need to re-log in trakt
        mTraktRelogBroadcastReceiver = new BroadcastReceiver(){

            @Override
            public void onReceive(Context context, Intent intent) {
                if( System.currentTimeMillis() - Trakt.sLastTraktRefreshToken > Trakt.ASK_RELOG_FREQUENCY&&(mTraktRelogAlertDialog==null||!mTraktRelogAlertDialog.isShowing())) {
                    Trakt.sLastTraktRefreshToken = System.currentTimeMillis();
                    AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                    alert.setTitle(R.string.trakt_signin_summary_logged_error)
                            .setMessage(R.string.trakt_relog_description)
                            .setPositiveButton(R.string.trakt_signin, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    TraktSigninDialogPreference dialog = new TraktSigninDialogPreference(MainActivity.this, null);
                                    dialog.onClick();
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null);
                    mTraktRelogAlertDialog = alert.create();
                    mTraktRelogAlertDialog.show();
                }
            }
        };
        CustomApplication.showChangelogDialog(CustomApplication.getChangelog(this.getApplicationContext()), this);
    }

    private boolean isShortcutIntent() {
        return getIntent()!=null && getIntent().getAction() != null
                && (getString(R.string.action_resume).equals(getIntent().getAction())
                || getString(R.string.action_recently_added).equals(getIntent().getAction())
                || getString(R.string.action_recently_played).equals(getIntent().getAction()));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }



    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        updateStereoMode(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            try {
                // Open a show
                if (data.getScheme().equals("show")) {
                    int showId = Integer.parseInt(data.getLastPathSegment());
                    Bundle args = new Bundle(2);
                    args.putLong(VideoColumns.SCRAPER_SHOW_ID, showId);
                    args.putString(CursorBrowserByVideo.SUBCATEGORY_NAME, ""); // should better have the show title, but...
                    Fragment f = new BrowserListOfSeasons();
                    f.setArguments(args);
                    BrowserCategory category = (BrowserCategory) getSupportFragmentManager().findFragmentById(R.id.category);
                    category.clearCheckedItem(); // a category may have be selected previously
                    category.startContent(f);
                }
                // Open a file
                else {
                    int videoId = Integer.parseInt(data.getLastPathSegment());
                    Uri uri = ContentUris.withAppendedId(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, videoId);
                    Intent intent2 = new Intent(Intent.ACTION_VIEW, uri);
                    if (!mPreferences.getBoolean(VideoPreferencesActivity.ALLOW_3RD_PARTY_PLAYER, VideoPreferencesActivity.ALLOW_3RD_PARTY_PLAYER_DEFAULT)) {
                        intent2.setClass(this, PlayerActivity.class);
                    }
                    try {
                        startActivity(intent2);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.cannot_open_video, Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (NullPointerException npe) {}
        }
        else if(LAUNCH_DIALOG.equals(intent.getAction())){
            if(FileManagerService.fileManagerService!=null&&FileManagerService.fileManagerService.isPastingInProgress()&&(mPasteDialog==null||!mPasteDialog.isShowing())) {
                mPasteDialog = new Paste(this);
                mPasteDialog.show();
            }
        }
        else if(getString(R.string.action_resume).equals(intent.getAction())){
            ContentResolver contentResolver = getContentResolver();
            Cursor c = contentResolver.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, CURSORS,
                    VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + "!=0" + (LoaderUtils.mustHideUserHiddenObjects() ? " AND " + LoaderUtils.HIDE_USER_HIDDEN_FILTER : ""), null,
                    VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " DESC LIMIT 1");

            if (c != null && c.getCount() != 0) {
                int index_id = c.getColumnIndex(VideoStore.Video.VideoColumns._ID);
                c.moveToFirst();
                long resumeId = c.getLong(index_id);
                Video video = getVideoFromId(resumeId);
                PlayUtils.startVideo(this, video, PlayerActivity.RESUME_FROM_LAST_POS, true,-1, this, -1);
            }else
                Toast.makeText(this, R.string.no_resume_available, Toast.LENGTH_LONG).show();
        }
        else if(getString(R.string.action_recently_added).equals(intent.getAction())){
            final BrowserCategoryVideo category = (BrowserCategoryVideo) getSupportFragmentManager().findFragmentById(R.id.category);
            category.getView().post(new Runnable() {
                @Override
                public void run() {
                    closeDrawer();
                    category.goToRecentlyAdded();
                }
            });
        }
        else if(getString(R.string.action_recently_played).equals(intent.getAction())){
            final BrowserCategoryVideo category = (BrowserCategoryVideo) getSupportFragmentManager().findFragmentById(R.id.category);
            category.getView().post(new Runnable() {
                @Override
                public void run() {
                    closeDrawer();
                    category.goToRecentlyPlayed();
                }
            });
        }
    }

    private Video getVideoFromId(long resumeId) {
        SingleVideoLoader singleVideoLoader = new SingleVideoLoader(this, resumeId);
        Cursor c = singleVideoLoader.loadInBackground();
        if(c.getCount()>0){
            VideoCursorMapper cursorMapper = new VideoCursorMapper();
            cursorMapper.publicBindColumns(c);
            c.moveToFirst();
            Video video = (Video) cursorMapper.publicBind(c);
            if (c != null) c.close();
            return video;
        }
        if (c != null) c.close();
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPermissionChecker.checkAndRequestPermission(this);

        registerReceiver(mTraktRelogBroadcastReceiver,new IntentFilter(Trakt.TRAKT_ISSUE_REFRESH_TOKEN));
        getContentResolver().registerContentObserver(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                false, mGlobalResumeContentObserver);
        LoaderManager.getInstance(this).restartLoader(0, null, mNewVideosActionProvider);
    }


    @Override
    public void onPause() {
        unregisterReceiver(mTraktRelogBroadcastReceiver);
        if (mGlobalResumeContentObserver != null) {
            getContentResolver().unregisterContentObserver(mGlobalResumeContentObserver);
        }

        // Make sure to cancel any request to display the help overlay for the "new videos" action
        // so that it won't be displayed later on top of the new active screen
        if (mNewVideosActionProvider != null) {
            mNewVideosActionProvider.cancelHelpOverlayRequest();
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        int backStackCount = getSupportFragmentManager().getBackStackEntryCount();
        if(backStackCount<=1) {
            if(mDrawerLayout==null||mDrawerLayout.isDrawerOpen(GravityCompat.START))
                supportFinishAfterTransition();
            else{
                mDrawerLayout.openDrawer(GravityCompat.START);
            }
        }
        else
            getSupportFragmentManager().popBackStackImmediate();
        updateHomeIcon(getSupportFragmentManager().getBackStackEntryCount() > 1);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                launchGlobalResume();
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public int getLayoutID() {
        /*if (Build.DEVICE.equals("shieldtablet") || Build.DEVICE.equals("flounder"))
            return R.layout.browser_main_video_no_coverroll;
        else*/
            return R.layout.browser_main_video;
    }

    @Override
    public int getTitleID() {
        return R.string.nova;
    }

    public View getGlobalBackdropView() {
        return mGlobalBackdrop;
    }

    protected void launchGlobalResume() {
        if (mGlobalResumeId != -1) {
            Video video = getVideoFromId(mGlobalResumeId);
            PlayUtils.startVideo(this, video, PlayerActivity.RESUME_FROM_LAST_POS, true,-1, this, -1);
        }
    }

    @Override
    public void startActivityWithResultListener(Intent intent) {
        startActivityForResult(intent, PLAY_ACTIVITY_REQUEST_CODE);
    }

    @Override
    protected void updateGlobalResume() {
        if (mPreferences.getBoolean("display_resume_box", true))
            new GlobalResumeTask().execute();
        else if (mGlobalResumeView != null)
            mGlobalResumeView.setVisibility(View.GONE);
    }

    public SearchView getSearchView() { //useful for sftp activity filter
        return mSearchView;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);
        /// /setHomeButtonsetHomeButton();
        MenuItem item = menu.add(MENU_SEARCH_GROUP, MENU_SEARCH_ITEM, Menu.NONE, R.string.search_title);
        item.setIcon(R.drawable.android29_ic_menu_search_mtrl_alpha);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        mSearchView = new SearchView(this);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        item.setActionView(mSearchView);
        mSearchItem = item;
        MenuItem menuItem = menu.add(MENU_SCRAPER_GROUP, MENU_START_AUTO_SCRAPER_ACTIVITY, Menu.NONE,
                R.string.start_auto_scraper_activity);
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        // below line removed to avoid warning W/ActionProvider(support): setVisibilityListener: Setting a new ActionProvider.VisibilityListener when one is already set. Are you reusing this NewVideosActionProvider instance while it is still in use somewhere else?
        //MenuItemCompat.setActionProvider(menuItem, mNewVideosActionProvider);
        mNewVideosActionProvider.manageVisibility(menuItem);

        menuItem = menu.add(MENU_PRIVATE_MODE_GROUP, MENU_PRIVATE_MODE_ITEM, Menu.CATEGORY_SECONDARY, R.string.activate_private_mode);
        menuItem.setIcon(R.drawable.ic_menu_private_mode);
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return ret;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean scraperVisible = true;
        // Allow to launch the auto scraper activity only if it is not currently running
        if (((CustomApplication) getApplication()).isAutoScraperActive()) {
            scraperVisible = false;
        }
        mNewVideosActionProvider.setEnabled(scraperVisible);

        MenuItem item = menu.findItem(MENU_PRIVATE_MODE_ITEM);
        if (item != null) {
            item.setTitle(PrivateMode.isActive() ? R.string.deactivate_private_mode : R.string.activate_private_mode);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = super.onOptionsItemSelected(item);

        switch (item.getItemId()) {
            case MENU_START_AUTO_SCRAPER_ACTIVITY:
                // Search all the videos in the database
                Intent as = new Intent(Intent.ACTION_MAIN);
                as.setComponent(new ComponentName(this, AutoScraperActivity.class));
                startActivity(as);
                break;

            case MENU_PREFERENCES_ITEM:
                startPreference();
                break;

            case MENU_PRIVATE_MODE_ITEM:
                if (!PrivateMode.isActive() && PrivateMode.canShowDialog(this)) {
                    PrivateMode.showDialog(this);
                }
                PrivateMode.toggle();
                setBackground();
                //setHomeButton();
                break;
            case android.R.id.home:
                if (mDrawerLayout == null || !mDrawerToggle.onOptionsItemSelected(item))
                    onBackPressed();
                break;

            }

        return ret;
    }

    private String mCurrentUiModeLeanback = null;

    /**
     * Handle the return from VideoPreferencesActivity, check if the UiMode has been changed or if
     * the zoom dialog must be displayed
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Preference activity sets RESULT_OK if something need to be checked when back
        if (requestCode == ACTIVITY_REQUEST_CODE_PREFERENCES) {
            if (resultCode == VideoPreferencesCommon.ACTIVITY_RESULT_UI_MODE_CHANGED) {
                // Check if the UI mode changed
                String newUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this).getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
                if (!newUiModeLeanback.equals(mCurrentUiModeLeanback)) {
                    // ui mode changed -> quit the current activity and restart
                    finish();
                    startActivity(new Intent(this, EntryActivity.class));
                }
                mCurrentUiModeLeanback = null; // reset
            }
            else if (resultCode == VideoPreferencesCommon.ACTIVITY_RESULT_UI_ZOOM_CHANGED) {
                new DensityTweak(this)
                        .forceDensityDialogAtNextStart();
                // restart the leanback activity for user to change the zoom
                finish();
                startActivity(new Intent(this, EntryActivity.class));
            }
        }
       else if(requestCode == PLAY_ACTIVITY_REQUEST_CODE){
            ExternalPlayerResultListener.getInstance().onActivityResult(requestCode,resultCode,data);
        }

    }

    public void startPreference(){
        Intent p = new Intent(Intent.ACTION_MAIN);
        p.setComponent(new ComponentName(this, VideoPreferencesActivity.class));
        startActivityForResult(p, ACTIVITY_REQUEST_CODE_PREFERENCES);
        // Save the uimode_leanback to check if it changed when back from preferences
        mCurrentUiModeLeanback = PreferenceManager.getDefaultSharedPreferences(this).getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
    }

    /**
     * For DEMO purpose only: Reset scraper info for all movies
     */
    private void resetScraperInfo() {
        ContentValues cv = new ContentValues(2);
        cv.put(VideoStore.Video.Media.ARCHOS_MEDIA_SCRAPER_ID, 0);
        cv.put(VideoStore.Video.Media.ARCHOS_MEDIA_SCRAPER_TYPE, 0);
        getContentResolver().update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, cv, null, null);
        Toast.makeText(this, "Movie info reset done", Toast.LENGTH_SHORT).show();
    }

    public void updateHomeIcon(boolean show) {
        if(mDrawerLayout!=null){
             mDrawerToggle.setDrawerIndicatorEnabled(!show);
            return;
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(show);
        getSupportActionBar().setHomeButtonEnabled(show);
    }

    public void closeDrawer() {
        if(mDrawerLayout!=null)
            mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    public void hideSeachView() {
        if(mSearchItem!=null)
            mSearchItem.collapseActionView();
    }

    //delegating to activity because getNavigationMode on support action bar doesn't work anymore
    public void setNavigationMode(int navigationMode) {
        getSupportActionBar().setNavigationMode(navigationMode);
        mNavigationMode = navigationMode;
    }

    public int getNavigationMode(){
        return mNavigationMode;
    }

    private class GlobalResumeContentObserver extends ContentObserver {
        public GlobalResumeContentObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            // A change occured in the medialib concerning one or several videos
            // => update the global resume view in case the video to resume has
            // been deleted
            updateGlobalResume();
        }
    }

    public GlobalResumeView getGlobalResumeView() {
        if (mGlobalResumeView == null) {
            mGlobalResumeView = (GlobalResumeView) mGlobalResumeViewStub.inflate();
        }
        SharedPreferences mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mGlobalResumeView.setVisibility(View.VISIBLE);
        return mGlobalResumeView;
    }

    private class GlobalResumeTask extends AsyncTask<Void, Void, Map> {
        protected Map doInBackground(Void... anything) {
            Map<String, Object> result;
            ContentResolver contentResolver = getContentResolver();
            Cursor c = contentResolver.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, CURSORS,
                    VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + "!=0" + (LoaderUtils.mustHideUserHiddenObjects() ? " AND " + LoaderUtils.HIDE_USER_HIDDEN_FILTER : ""), null,
                    VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " DESC LIMIT 1");

            if (c != null && c.getCount() != 0) {
                int index_id = c.getColumnIndex(VideoStore.Video.VideoColumns._ID);
                int index_scraper_id = c
                        .getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID);
                c.moveToFirst();

                boolean firstGlobalResume = (mGlobalResumeId == -1);
                mGlobalResumeId = c.getInt(index_id);
                Bitmap thumbnail = null;
                CharSequence name = null;
                int scraperId = c.getInt(index_scraper_id);

                if (scraperId > 0) {
                    int scraperType = c
                            .getInt(c
                                    .getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                    String[] selectionArgs = new String[] {
                            String.valueOf(scraperType), String.valueOf(scraperId)
                    };

                    Cursor scraperCursor = contentResolver.query(ScraperStore.AllVideos.URI.ALL,
                            SCRAPER_PROJECTION, SCRAPER_SELECTION, selectionArgs, null);
                    if (scraperCursor.moveToFirst()) {
                        int index_cover = scraperCursor
                                .getColumnIndex(ScraperStore.AllVideos.MOVIE_OR_SHOW_COVER);
                        int index_name = scraperCursor
                                .getColumnIndex(ScraperStore.AllVideos.MOVIE_OR_SHOW_NAME);

                        thumbnail = BitmapFactory.decodeFile(scraperCursor.getString(index_cover));

                        if (scraperType == com.archos.mediascraper.BaseTags.MOVIE) {
                            name = scraperCursor.getString(index_name);
                        } else {
                            int index_number = scraperCursor
                                    .getColumnIndex(ScraperStore.AllVideos.EPISODE_NUMBER);
                            int index_season = scraperCursor
                                    .getColumnIndex(ScraperStore.AllVideos.EPISODE_SEASON_NUMBER);
                            int index_episode_name = scraperCursor
                                    .getColumnIndex(ScraperStore.AllVideos.EPISODE_NAME);
                            String episodeName = String.format(getString(R.string.quotation_format),
                                    scraperCursor.getString(index_episode_name));
                            name = HtmlCompat.fromHtml(String.format(Locale.ENGLISH,TITLE_FORMAT,
                                    scraperCursor.getString(index_name),
                                    scraperCursor.getInt(index_season),
                                    scraperCursor.getInt(index_number), episodeName),
                                    HtmlCompat.FROM_HTML_MODE_LEGACY);
                        }
                    } // else: cursor is empty -> no thumbnail
                    scraperCursor.close();
                }

                if (name == null) {
                    int index_name = c.getColumnIndex(VideoStore.MediaColumns.TITLE);
                    name = c.getString(index_name);
                }

                if (thumbnail == null) {
                    thumbnail = VideoStore.Video.Thumbnails.getThumbnail(contentResolver, mGlobalResumeId,
                            VideoStore.Video.Thumbnails.MINI_KIND, null);
                }

                result = new HashMap<>(3);
                result.put("name", name);
                result.put("thumbnail", thumbnail);
                result.put("setListener", firstGlobalResume);
            } else {
                result = new HashMap<>(0);
            }

            if (c != null)
                c.close();

            return result;
        }

        protected void onPostExecute(Map result) {

            if (!result.isEmpty()) {
                GlobalResumeView grv = getGlobalResumeView();
                grv.resetOpenAnimation();
                TextView text = (TextView) grv.findViewById(R.id.global_resume_text);
                text.setText((CharSequence) result.get("name"));
                View tint = grv.findViewById(R.id.tint);

                // it seems to be possible that tint is null. Prevent crash and
                // and stop here. Probably because this was called before / after
                // the regular lifecycle.
                if (tint == null) return;

                Bitmap thumbnail = (Bitmap) result.get("thumbnail");
                grv.setImage(thumbnail);
                if (thumbnail != null) {
                    tint.setVisibility(View.VISIBLE);
                } else {
                    tint.setVisibility(View.GONE);
                }

                if ((Boolean) result.get("setListener")) {
                    final GlobalResumeView f_grv = grv;

                    // Handle clicks on the "resume global" area
                    grv.setOnClickListener(new View.OnClickListener() {
                        boolean launching = false;
                        public void onClick(View v) {
                            if (launching)
                                return;
                            f_grv.launchOpenAnimation(new Animator.AnimatorListener() {
                                public void onAnimationStart(Animator animation) {
                                    launching = true;
                                }

                                public void onAnimationRepeat(Animator animation) {
                                }

                                public void onAnimationEnd(Animator animation) {
                                    launching = false;
                                    launchGlobalResume();
                                }

                                public void onAnimationCancel(Animator animation) {
                                }
                            });
                        }
                    });

                    // TODO KISS the xml and this.
                    // Handle focus on the "resume global" area
                    grv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                        // Allow to draw a specific image on top of the
                        // "resume global"
                        // area when it has the focus
                        public void onFocusChange(View v, boolean hasFocus) {
                            ImageView resumeGlobalFocusView = (ImageView) findViewById(R.id.global_resume_focus);
                            resumeGlobalFocusView
                                    .setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                        }
                    });

                    // Handle pressed state on the "resume global" area
                    grv.setOnTouchListener(new View.OnTouchListener() {
                        // Allow to draw a specific image on top of the
                        // "resume global"
                        // area when it is pressed
                        public boolean onTouch(View v, MotionEvent event) {
                            ImageView resumeGlobalFocusView = (ImageView) findViewById(R.id.global_resume_focus);
                            int action = event.getAction();
                            if (action == MotionEvent.ACTION_DOWN) {
                                resumeGlobalFocusView.setVisibility(View.VISIBLE);
                            } else if (action == MotionEvent.ACTION_UP) {
                                resumeGlobalFocusView.setVisibility(View.GONE);
                            }
                            return false;
                        }
                    });
                }
            }
        }
    }

    public void reloadBrowserByVideoFolder() {
        BrowserCategory category = (BrowserCategory) getSupportFragmentManager().findFragmentById(R.id.category);
        Fragment f = new BrowserByVideoFolder();
        category.loadFragmentAfterStackReset(f);
    }

    // ====================== UiChoiceDialog ====================

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        boolean ignore = false;

        // Special case: BACK button on touchscreen generate a keyboard event... Let's ignore it...
        if (ev.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            ignore = true;
        }

        // Special case: Vol- and Vol+ buttons on phone generate a keyboard event... Let's ignore it...
        if (ev.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || ev.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            ignore = true;
        }

        // Special case: many phones also have an hardware MENU key...
        if (ev.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            ignore = true;
        }

        if (!ignore) {
            checkUiChoice(ev);
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        checkUiChoice(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        checkUiChoice(ev);
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        checkUiChoice(ev);
        return super.dispatchTrackballEvent(ev);
    }

    static boolean sUiChoiceCheckDone = false;

    /**
     * Check if the input event make us think that user is on TV.
     * If it is the case and if the Ui mode is not setup, then we propose to try the TV UI.
     * @param event
     */
    private void checkUiChoice(InputEvent event) {

        // Make sure we go through this method only once
        if (sUiChoiceCheckDone) {
            return;
        }
        sUiChoiceCheckDone = true;

        boolean probablyTv = false;

        switch (event.getSource()) {
            // All these case mean the user is probably on TV
            case InputDevice.SOURCE_KEYBOARD:
            case InputDevice.SOURCE_TOUCHPAD:
            case InputDevice.SOURCE_DPAD:
            case InputDevice.SOURCE_GAMEPAD:
            case InputDevice.SOURCE_JOYSTICK:
            case InputDevice.SOURCE_HDMI:
                log.debug("event source = "+event.getSource()+" -> probably TV");
                probablyTv = true;
                break;
            case InputDevice.SOURCE_STYLUS:
            case InputDevice.SOURCE_TOUCHSCREEN:
            case InputDevice.SOURCE_TRACKBALL:
            case InputDevice.SOURCE_MOUSE:
            default:
                log.debug("event source = "+event.getSource()+" -> probably not TV");
                probablyTv = false;
                break;
        }

        if (!probablyTv) {
            return;
        }

        final String uiMode = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "unset");

        // If the choice has not been done yet, ask user
        if (uiMode.equals("unset") &&
             !getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) { // no UI choice to do on actual AndroidTV devices
            new UiChoiceDialog().show(getSupportFragmentManager(), "UiChoiceDialog");
        }
    }
}
