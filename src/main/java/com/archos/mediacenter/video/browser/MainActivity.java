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
import static com.archos.mediacenter.video.browser.BrowserCategoryVideo.MOVIE_CATEGORIES_NAMES_ID;

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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.FileUtils;
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
import com.archos.mediacenter.video.utils.CustomTypefaceSpan;
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

import java.lang.reflect.Field;
import java.util.Arrays;
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

    private SearchManager mSearchManager;

    private final static String SCRAPER_SELECTION = ScraperStore.AllVideos.SCRAPER_TYPE + "=? AND "
            + ScraperStore.AllVideos.SCRAPER_ID + "=?";
    private final static String TITLE_FORMAT = "%s  S%dE%d <i> %s </i>";

    private final static String[] CURSORS = {
            VideoStore.Video.VideoColumns._ID, VideoStore.Video.VideoColumns.TITLE,
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID,
            VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE
    };

    private final static String[] SCRAPER_PROJECTION = {
            ScraperStore.AllVideos.MOVIE_OR_SHOW_NAME, ScraperStore.AllVideos.MOVIE_OR_SHOW_BACKDROP,
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
        int backgroundResId = PrivateMode.isActive() ? R.drawable.background_2014_private : R.drawable.background_2014;
        getWindow().getDecorView().setBackgroundResource(backgroundResId);

        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * mDrawerLayout.getContext().getResources().getDisplayMetrics().density);
        int maxWidthPx = ItemDataWidthCalculator.getMaxItemDataWidth(this);
        if(mDrawerLayout != null){
            mDrawerLayout.findViewById(R.id.category_container).setBackgroundResource(backgroundResId);
            ViewGroup.LayoutParams layoutParams = mDrawerLayout.findViewById(R.id.category_container).getLayoutParams();
            layoutParams.width = maxWidthPx + dpToPx.apply(44);
            mDrawerLayout.findViewById(R.id.category_container).setLayoutParams(layoutParams);
        }
    }

    private static MainActivity mInstanceActivity;
    public static MainActivity getmInstanceActivity() {
        return mInstanceActivity;
    }
    private Toolbar mToolbar;

    public void setDarkMode() {
        getWindow().getDecorView().setBackgroundResource(R.color.deep_dark_blue);

        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * mDrawerLayout.getContext().getResources().getDisplayMetrics().density);
        int maxWidthPx = ItemDataWidthCalculator.getMaxItemDataWidth(this);
        if(mDrawerLayout != null){
            mDrawerLayout.findViewById(R.id.category_container).setBackgroundResource(R.color.deep_dark_blue);
            ViewGroup.LayoutParams layoutParams = mDrawerLayout.findViewById(R.id.category_container).getLayoutParams();
            layoutParams.width = maxWidthPx + dpToPx.apply(44);
            mDrawerLayout.findViewById(R.id.category_container).setLayoutParams(layoutParams);
        }
        mToolbar.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.deep_dark_blue_transparent));
    }

    public void setNormalMode() {
        int backgroundResId = R.drawable.background_2014;
        getWindow().getDecorView().setBackgroundResource(backgroundResId);

        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * mDrawerLayout.getContext().getResources().getDisplayMetrics().density);
        int maxWidthPx = ItemDataWidthCalculator.getMaxItemDataWidth(this);
        if(mDrawerLayout != null){
            mDrawerLayout.findViewById(R.id.category_container).setBackgroundResource(backgroundResId);
            ViewGroup.LayoutParams layoutParams = mDrawerLayout.findViewById(R.id.category_container).getLayoutParams();
            layoutParams.width = maxWidthPx + dpToPx.apply(44);
            mDrawerLayout.findViewById(R.id.category_container).setLayoutParams(layoutParams);
        }
        mToolbar.setBackgroundColor(getApplicationContext().getResources().getColor(R.color.leanback_background_transparent));
    }

    private void setHomeButton() {
        int iconResId = PrivateMode.isActive() ? R.mipmap.nova_private : R.mipmap.nova;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(false);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ((CustomApplication) getApplication()).loadLocale();
        //CustomApplication.loadLocale(getResources());
        requestWindowFeature(Window.FEATURE_OPTIONS_PANEL);
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        super.onCreate(savedInstanceState);

        try {
            mSearchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            if (mSearchManager == null) {
                log.error("onCreate: searchManager is null");
            } else {
                mSearchView = new SearchView(this);
                mSearchView.setSearchableInfo(mSearchManager.getSearchableInfo(getComponentName()));

                // Set iconified state
                mSearchView.setIconifiedByDefault(false);

                // Update the search icon immediately after initializing mSearchView
                ImageView searchIcon = mSearchView.findViewById(
                        mSearchView.getContext().getResources().getIdentifier(
                                "search_mag_icon", "id", mSearchView.getContext().getPackageName()
                        )
                );
                if (searchIcon != null) {
                    searchIcon.setImageDrawable(ContextCompat.getDrawable(getApplicationContext(), R.drawable.android29_ic_menu_search_mtrl_alpha));
                    searchIcon.setColorFilter(null);

                    // Remove default padding from the SearchView that pushes icon to the side
                    View searchPlate = mSearchView.findViewById(
                            mSearchView.getContext().getResources().getIdentifier(
                                    "search_plate", "id", mSearchView.getContext().getPackageName()
                            )
                    );
                    if (searchPlate != null) {
                        searchPlate.setPadding(0, 0, 0, 0);
                    }

                    // Add margin to center icon better (tweak these numbers as needed)
                    ViewGroup.MarginLayoutParams iconParams = (ViewGroup.MarginLayoutParams) searchIcon.getLayoutParams();
                    iconParams.setMargins(16, 0, 16, 0);  // Adjust to center it visually
                    searchIcon.setLayoutParams(iconParams);

                    // Optionally center the icon in its parent using layout gravity
                    ViewGroup parent = (ViewGroup) searchIcon.getParent();
                    if (parent instanceof LinearLayout) {
                        ((LinearLayout) parent).setGravity(Gravity.CENTER_VERTICAL);
                    }
                }
            }
        } catch (IllegalStateException e) {
            log.error("onCreate: searchManager is null");
        }

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

        mToolbar = (Toolbar)findViewById(R.id.main_toolbar);

        mToolbar.post(() -> {
            ViewGroup toolbarViewGroup = (ViewGroup) mToolbar;
            View hamburgerIcon = toolbarViewGroup.getChildAt(1); // AppCompatImageButton
            View spinner = toolbarViewGroup.getChildAt(0); // AppCompatSpinner
            View ActionMenuView  = toolbarViewGroup.getChildAt(2); // ActionMenuView

            /***
             2025-06-13 03:36:51.211 19310-19310 ToolbarView             org.courville.nova                   D  Child #0: androidx.appcompat.widget.AppCompatSpinner, width=597, layoutParams=androidx.appcompat.widget.Toolbar$LayoutParams
             2025-06-13 03:36:51.211 19310-19310 ToolbarView             org.courville.nova                   D  Child #1: androidx.appcompat.widget.AppCompatImageButton, width=147, layoutParams=androidx.appcompat.widget.Toolbar$LayoutParams
             2025-06-13 03:36:51.211 19310-19310 ToolbarView             org.courville.nova                   D  Child #2: androidx.appcompat.widget.ActionMenuView, width=0, layoutParams=androidx.appcompat.widget.Toolbar$LayoutParams
             */

            if (spinner instanceof AppCompatSpinner) {
                setupSpinner((AppCompatSpinner) spinner);
            }
        });

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

        mInstanceActivity = this;

        boolean darkModeActive = mPreferences.getBoolean("dark_mode", false);

        boolean drawerIsNull;
        drawerIsNull = mDrawerLayout == null;
        mPreferences.edit().putBoolean("drawerIsNull", drawerIsNull).apply();

        if(darkModeActive){
            setDarkMode();
        }else{
            setNormalMode();
        }

        BrowserCategory category = (BrowserCategory) getSupportFragmentManager().findFragmentById(R.id.category);
        if (category != null && mDrawerLayout== null){
            category.setDrawerLayuot(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mInstanceActivity = null;
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
                    int showId = Integer.parseInt(FileUtils.getName(data));
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
                    int videoId = Integer.parseInt(FileUtils.getName(data));
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
        ((CustomApplication) getApplication()).loadLocale();

        mPermissionChecker.checkAndRequestPermission(this);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(mTraktRelogBroadcastReceiver,new IntentFilter(Trakt.TRAKT_ISSUE_REFRESH_TOKEN), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mTraktRelogBroadcastReceiver,new IntentFilter(Trakt.TRAKT_ISSUE_REFRESH_TOKEN));
        }
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
        } else {
            // Get the fragment that is currently at the top of the back stack
            String fragmentTag = getSupportFragmentManager().getBackStackEntryAt(backStackCount - 1).getName();
            Fragment currentFragment = getSupportFragmentManager().findFragmentByTag(fragmentTag);
            // Check if the fragment is added before trying to remove it
            if (currentFragment != null && currentFragment.isAdded()) {
                getSupportFragmentManager().beginTransaction().remove(currentFragment).commit();
            }
            getSupportFragmentManager().popBackStackImmediate();
        }
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
            textView.setTypeface(ResourcesCompat.getFont(this, R.font.nhaasgroteskdspro_75bd));
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
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);
        /// /setHomeButtonsetHomeButton();

        if (mSearchManager == null) {
            log.error("onCreateOptionsMenu: searchManager is null");
        } else {
            MenuItem item = menu.add(MENU_SEARCH_GROUP, MENU_SEARCH_ITEM, Menu.NONE, applyCustomFont(R.string.search_title));
            item.setIcon(R.drawable.android29_ic_menu_search_mtrl_alpha);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
            item.setActionView(mSearchView);

            Toolbar toolbar = findViewById(R.id.main_toolbar);
            String expectedTitle = getString(R.string.search_title);
            toolbar.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                for (int i = 0; i < toolbar.getChildCount(); i++) {
                    View child = toolbar.getChildAt(i);
                    if (child instanceof ActionMenuView) {
                        ActionMenuView menuView = (ActionMenuView) child;
                        for (int j = 0; j < menuView.getChildCount(); j++) {
                            View itemView = menuView.getChildAt(j);
                            if (itemView instanceof ActionMenuItemView) {
                                CharSequence title = ((ActionMenuItemView) itemView).getItemData().getTitle();
                                if (title != null && title.toString().equalsIgnoreCase(expectedTitle)) {
                                    attachCustomTooltip(itemView, getString(R.string.search_title));
                                    return;
                                }
                            }
                        }
                    }
                }
            });

            // Set custom search text and hint text
            // 1. Get the SearchAutoComplete (internal EditText)
            SearchView.SearchAutoComplete searchEditText = mSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
            // 2. Set custom font (from res/font/)
            Typeface customFont = ResourcesCompat.getFont(this, R.font.nhaasgroteskdspro_45lt);
            if (customFont != null) {
                searchEditText.setTypeface(customFont);
            }
            // 3. Set hint and color
            searchEditText.setHint(" Search");
            searchEditText.setHintTextColor(Color.GRAY);
            searchEditText.setTextColor(Color.WHITE); // Optional: set typing text color
            // 4. Optional: clear hint when user types
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() > 0) {
                        searchEditText.setHint("");
                    } else {
                        searchEditText.setHint(" Search");
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.CENTER; // Center inside LinearLayout
            searchEditText.setLayoutParams(params);

            mSearchItem = item;
        }
        MenuItem menuItem = menu.add(MENU_SCRAPER_GROUP, MENU_START_AUTO_SCRAPER_ACTIVITY, Menu.NONE,
                applyCustomFont(R.string.start_auto_scraper_activity));
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        // below line removed to avoid warning W/ActionProvider(support): setVisibilityListener: Setting a new ActionProvider.VisibilityListener when one is already set. Are you reusing this NewVideosActionProvider instance while it is still in use somewhere else?
        //MenuItemCompat.setActionProvider(menuItem, mNewVideosActionProvider);
        mNewVideosActionProvider.manageVisibility(menuItem);

        menuItem = menu.add(MENU_PRIVATE_MODE_GROUP, MENU_PRIVATE_MODE_ITEM, Menu.CATEGORY_SECONDARY, applyCustomFont(R.string.activate_private_mode));
        menuItem.setIcon(R.drawable.ic_menu_private_mode);
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return ret;
    }

    private SpannableString applyCustomFont(@StringRes int resId) {
        String family ="";
        Typeface typeface = ResourcesCompat.getFont(this, R.font.nhaasgroteskdspro_75bd);
        int color = ContextCompat.getColor(this, android.R.color.white);
        float textSize = 18f; // in SP
        String text = this.getString(resId);
        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new CustomTypefaceSpan(family, typeface, textSize, color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
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
            item.setTitle(applyCustomFont(PrivateMode.isActive() ? R.string.deactivate_private_mode : R.string.activate_private_mode));
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
                // disable dark mode before toggling private mode
                mPreferences.edit().putBoolean("dark_mode", false).apply();
                BrowserCategory category = (BrowserCategory) getSupportFragmentManager().findFragmentById(R.id.category);
                if (category != null){
                    category.setCategoryItemSeparatorBackground();
                }

                // Ensure video list updates backgrounds live when dark mode is toggled
                Browser browser = (Browser) getSupportFragmentManager().findFragmentById(R.id.content); // or the correct fragment ID
                if (browser != null) {
                    browser.notifyDataSetChanged();
                }

                setNormalMode();
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
            // A change occurred in the medialib concerning one or several videos
            // => update the global resume view in case the video to resume has
            // been deleted
            updateGlobalResume();
        }
    }

    public GlobalResumeView getGlobalResumeView() {
        if (mGlobalResumeView == null) {
            mGlobalResumeView = (GlobalResumeView) mGlobalResumeViewStub.inflate();
        }
        mGlobalResumeView.setVisibility(View.VISIBLE);
        return mGlobalResumeView;
    }

    private class GlobalResumeTask extends AsyncTask<Void, Void, Map> {
        protected Map doInBackground(Void... anything) {
            Map<String, Object> result = new HashMap<>();
            ContentResolver contentResolver = getContentResolver();

            // Add the BOOKMARK column to the query so we can fetch resume time
            String[] projection = Arrays.copyOf(CURSORS, CURSORS.length + 1);
            projection[CURSORS.length] = VideoStore.Video.VideoColumns.BOOKMARK;

            Cursor c = contentResolver.query(VideoStore.Video.Media.EXTERNAL_CONTENT_URI, projection,
                    VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + "!=0" +
                            (LoaderUtils.mustHideUserHiddenObjects() ? " AND " + LoaderUtils.HIDE_USER_HIDDEN_FILTER : ""),
                    null,
                    VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " DESC LIMIT 1");

            if (c != null && c.getCount() != 0) {
                int index_id = c.getColumnIndex(VideoStore.Video.VideoColumns._ID);
                int index_scraper_id = c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_ID);
                int index_bookmark = c.getColumnIndex(VideoStore.Video.VideoColumns.BOOKMARK);  // Bookmark column

                c.moveToFirst();

                boolean firstGlobalResume = (mGlobalResumeId == -1);
                mGlobalResumeId = c.getInt(index_id);
                Bitmap thumbnail = null;
                CharSequence name = null;
                int scraperId = c.getInt(index_scraper_id);
                long resumeTime = c.getLong(index_bookmark);  // Get resume time from the cursor

                if (scraperId > 0) {
                    int scraperType = c.getInt(c.getColumnIndex(VideoStore.Video.VideoColumns.ARCHOS_MEDIA_SCRAPER_TYPE));
                    String[] selectionArgs = new String[] {
                            String.valueOf(scraperType), String.valueOf(scraperId)
                    };

                    Cursor scraperCursor = contentResolver.query(ScraperStore.AllVideos.URI.ALL,
                            SCRAPER_PROJECTION, SCRAPER_SELECTION, selectionArgs, null);
                    if (scraperCursor.moveToFirst()) {
                        int index_cover = scraperCursor.getColumnIndex(ScraperStore.AllVideos.MOVIE_OR_SHOW_BACKDROP);
                        int index_name = scraperCursor.getColumnIndex(ScraperStore.AllVideos.MOVIE_OR_SHOW_NAME);

                        thumbnail = BitmapFactory.decodeFile(scraperCursor.getString(index_cover));

                        if (scraperType == com.archos.mediascraper.BaseTags.MOVIE) {
                            name = scraperCursor.getString(index_name);
                        } else {
                            int index_number = scraperCursor.getColumnIndex(ScraperStore.AllVideos.EPISODE_NUMBER);
                            int index_season = scraperCursor.getColumnIndex(ScraperStore.AllVideos.EPISODE_SEASON_NUMBER);
                            int index_episode_name = scraperCursor.getColumnIndex(ScraperStore.AllVideos.EPISODE_NAME);
                            String episodeName = String.format(getString(R.string.quotation_format),
                                    scraperCursor.getString(index_episode_name));
                            name = HtmlCompat.fromHtml(String.format(Locale.ENGLISH, TITLE_FORMAT,
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

                result.put("name", name);
                result.put("thumbnail", thumbnail);
                result.put("setListener", firstGlobalResume);
                result.put("resumeTime", resumeTime);  // Add resume time to the result map
            } else {
                result = new HashMap<>(0);
            }

            if (c != null) {
                c.close();
            }

            return result;
        }

        protected void onPostExecute(Map result) {

            if (!result.isEmpty()) {
                GlobalResumeView grv = getGlobalResumeView();
                grv.resetOpenAnimation();
                TextView text = (TextView) grv.findViewById(R.id.global_resume_text);
                TextView timestamp = (TextView) grv.findViewById(R.id.timestamp_text);

                long resumeTime = (Long) result.get("resumeTime");
                // Convert milliseconds to minutes and seconds
                long minutes = (resumeTime / 1000) / 60;
                long seconds = (resumeTime / 1000) % 60;

                // Format the time as MM:SS
                String formattedTime = String.format("%02d:%02d", minutes, seconds);
                timestamp.setText(formattedTime);

                // Create the blinking animation
                AlphaAnimation blinkAnimation = new AlphaAnimation(0.0f, 1.0f);  // Fade out to fully visible
                blinkAnimation.setDuration(500);  // Duration for each fade cycle (in milliseconds)
                blinkAnimation.setRepeatMode(Animation.REVERSE);  // Reverse the animation (fade in/out)
                blinkAnimation.setRepeatCount(Animation.INFINITE);  // Repeat infinitely

                // Set the blinking effect on the timestamp TextView
                // timestamp.startAnimation(blinkAnimation);

                // Set the text color to red initially
                timestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.yellow_light));

                text.setText((CharSequence) result.get("name"));
                text.setSingleLine(true);
                text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                text.setMarqueeRepeatLimit(-1); // -1 for infinite
                text.setSelected(true); // Required to start marquee
                text.setFocusable(true);
                text.setFocusableInTouchMode(true);
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

                // resume icon option
                String mode = mPreferences.getString("global_resume_icon", null);
                int iconStyle;
                if(mode == null){
                    iconStyle = 0;
                }else{
                    iconStyle = Integer.parseInt(mode);
                }
                ImageView resumeButton = (ImageView) findViewById(R.id.global_resume_btn);
                LinearLayout resumeButtonDetailed =  (LinearLayout) findViewById(R.id.resume_button);
                if (iconStyle == 0){
                    resumeButton.setVisibility(View.VISIBLE);
                    resumeButtonDetailed.setVisibility(View.GONE);
                }
                if (iconStyle == 1){
                    resumeButton.setVisibility(View.GONE);
                    resumeButtonDetailed.setVisibility(View.VISIBLE);
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
                            ImageView resumeIcon = (ImageView) grv.findViewById(R.id.resume_icon);
                            ImageView resumeText = (ImageView) grv.findViewById(R.id.resume_text);
                            ImageView globalResumeButton = (ImageView) grv.findViewById(R.id.global_resume_btn);
                            int action = event.getAction();
                            if (action == MotionEvent.ACTION_DOWN) {
                                resumeGlobalFocusView.setVisibility(View.VISIBLE);
                                text.setTextColor(Color.RED);
                                resumeIcon.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
                                timestamp.setTextColor(Color.RED);
                                resumeText.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
                                globalResumeButton.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
                            } else if (action == MotionEvent.ACTION_UP) {
                                resumeGlobalFocusView.setVisibility(View.GONE);
                                text.setTextColor(Color.WHITE);
                                resumeIcon.clearColorFilter();
                                timestamp.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.yellow_light));
                                resumeText.clearColorFilter();
                                globalResumeButton.clearColorFilter();
                            }
                            return false;
                        }
                    });
                }
                // change browser resume button height to 240 (smaller) when in phone and landscape mode
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !getResources().getConfiguration().isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)) {
                    grv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 240));
                    boolean mDisplayClearLogoInPlayer = mPreferences.getBoolean("display_backdrop_global_resume", true);
                    if (!mDisplayClearLogoInPlayer){
                        text.setBackground(null);
                        grv.clearImage(); // optionally remove the image
                        grv.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.browser_resume_stroke));
                    }
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) text.getLayoutParams();
                    if (iconStyle == 0){
                        params.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                        params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                        text.setLayoutParams(params);
                    }
                    if (iconStyle == 1){
                        params.addRule(RelativeLayout.ALIGN_PARENT_START);
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                        // Convert 2dp to pixels
                        int marginTopInPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                2,
                                text.getResources().getDisplayMetrics()
                        );
                        // Set the top margin
                        params.topMargin = marginTopInPx;
                        text.setLayoutParams(params);
                    }
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

    private void setupSpinner(AppCompatSpinner spinner) {
        updateSpinnerWidth(spinner, spinner.getSelectedItemPosition());
    }

    public void updateSpinnerWidth(AppCompatSpinner spinner, int position) {
        mToolbar.post(() -> {
            spinner.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {

                    // Try multiple times to catch the popup early
                    for (int i = 0; i < 5; i++) {
                        final int attempt = i;
                        spinner.postDelayed(() -> {
                            try {
                                Field popupField = AppCompatSpinner.class.getDeclaredField("mPopup");
                                popupField.setAccessible(true);
                                Object popupWindow = popupField.get(spinner);

                                if (popupWindow != null &&
                                        popupWindow.getClass().getName().equals("androidx.appcompat.widget.AppCompatSpinner$DropdownPopup")) {

                                    Field dropDownListField = popupWindow.getClass().getSuperclass().getDeclaredField("mDropDownList");
                                    dropDownListField.setAccessible(true);
                                    ListView listView = (ListView) dropDownListField.get(popupWindow);

                                    if (listView != null) {
                                        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
                                        Log.d("SpinnerHack", "Ripple removed (attempt " + attempt + ")");
                                    }
                                }
                            } catch (Exception e) {
                                Log.e("SpinnerHack", "Reflection error: " + e.getMessage());
                            }
                        }, i * 50); // Try at 0ms, 50ms, 100ms, 150ms, 200ms
                    }
                    v.performClick(); // <-- this is the fix for accessibility
                }
                return false; // Let spinner handle click normally
            });
        });


        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * mToolbar.getContext().getResources().getDisplayMetrics().density);
        int mDropDownWidth = 0;
        int textResId = MOVIE_CATEGORIES_NAMES_ID[position];
        String text = getString(textResId);

        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setTextSize(getResources().getDimension(R.dimen.video_info_big_text));
        paint.setTypeface(ResourcesCompat.getFont(this, R.font.gotham_bold));

        float textWidth = paint.measureText(text);
        int widthToApply = (int) (textWidth + dpToPx.apply(24)); // 24dp for dropdown icon padding
        //mDropDownWidth = (int) textWidth  + dpToPx.apply(22);

        Toolbar.LayoutParams replicatedParams = new Toolbar.LayoutParams(widthToApply,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );

        replicatedParams.setMarginStart(0);
        replicatedParams.setMarginEnd(0);
        replicatedParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        boolean mIsPortraitMode = getApplicationContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (mIsPortraitMode){
            spinner.setTranslationX(-50); // shift right by 24px
        } else {
            spinner.setTranslationX(0);
        }

        spinner.setLayoutParams(replicatedParams);
        spinner.setDropDownHorizontalOffset(-26);
        spinner.setDropDownWidth(ViewGroup.LayoutParams.WRAP_CONTENT);

        // Strip padding and min width
        spinner.setPadding(0, 0, 0, 9);
        spinner.setMinimumWidth(0);

        // Fix padding inside selected item
        View selectedView = spinner.getSelectedView();
        if (selectedView != null) {
            selectedView.setPadding(0, 0, 0, 0);
            selectedView.setMinimumWidth(0);
        }
    }
}
