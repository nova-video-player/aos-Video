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
package com.archos.mediacenter.video.utils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.BuildConfig;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.leanback.MainFragment;
import com.archos.mediacenter.video.leanback.animes.AllAnimesGridFragment;
import com.archos.mediacenter.video.leanback.animes.AnimesSortOrderEntry;
import com.archos.mediacenter.video.leanback.movies.AllMoviesGridFragment;
import com.archos.mediacenter.video.leanback.movies.MoviesSortOrderEntry;
import com.archos.mediacenter.video.leanback.settings.VideoSettingsLicencesActivity;
import com.archos.mediacenter.video.leanback.settings.VideoSettingsMoreLeanbackActivity;
import com.archos.mediacenter.video.leanback.tvshow.AllTvshowsGridFragment;
import com.archos.mediacenter.video.leanback.tvshow.TvshowsSortOrderEntry;
import com.archos.mediacenter.video.tvshow.AnimeShowSortOrderEntries;
import com.archos.mediacenter.video.tvshow.TvshowSortOrderEntries;
import com.archos.mediacenter.video.utils.credentialsmanager.CredentialsManagerPreferenceActivity;
import com.archos.medialib.MediaFactory;
import com.archos.mediaprovider.video.VideoProvider;
import com.archos.mediascraper.AllCollectionScrapeService;
import com.archos.mediascraper.AutoScrapeService;
import com.archos.mediascraper.settings.ScraperPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.archos.filecorelibrary.FileUtils.backupDatabase;

public class VideoPreferencesCommon implements OnSharedPreferenceChangeListener {
    private static final String TAG = VideoPreferencesCommon.class.getSimpleName();
    private static final boolean DBG = false;

    // should we provide adaptive refresh rate for all (not only on TV)
    private static final boolean REFRESHRATE_FORALL = true;

    // default stream buffer size in MB before parser
    public static final int DEFAULT_STREAM_BUFFER_SIZE = 24;
    // default max iframe compressed frame size in MB
    public static final int DEFAULT_MAX_IFRAME_SIZE = 6;

    public static final String KEY_ADVANCED_VIDEO_ENABLED = "preferences_advanced_video_enabled";
    public static final String KEY_ADVANCED_VIDEO_CATEGORY = "preferences_category_advanced_video";
    public static final String KEY_ABOUT_CATEGORY = "about_category";
    public static final String KEY_ADVANCED_3D_TV_SWITCH_SUPPORTED = "preferences_tv_switch_supported";
    public static final String KEY_ADVANCED_VIDEO_QUIT = "preferences_video_advanced_quit";
    public static final String KEY_TORRENT_BLOCKLIST = "preferences_torrent_blocklist";
    public static final String KEY_TORRENT_PATH = "preferences_torrent_path";
    public static final String KEY_SHARED_FOLDERS= "share_folders";
    public static final String KEY_SUBTITILES_CREDENTIALS= "subtitles_credentials";
    public static final String KEY_FORCE_SW = "force_software_decoding";
    public static final String KEY_FORCE_AUDIO_PASSTHROUGH = "force_passthrough";
    public static final String KEY_PARSER_SYNC_MODE = "parser_sync_mode";
    public static final String KEY_DISABLE_DOLBY_VISION = "disable_dolby_vision";
    public static final String KEY_STREAM_BUFFER_SIZE = "stream_buffer_size";
    public static final String KEY_STREAM_MAX_IFRAME_SIZE = "stream_max_iframe_size";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed";
    public static final String KEY_ACTIVATE_REFRESHRATE_SWITCH = "enable_tv_refreshrate_switch";
    public static final String KEY_ACTIVATE_3D_SWITCH = "activate_tv_switch";
    public static final String KEY_ADULT_SCRAPE = "enable_adult_scrap_key";

    public static final String KEY_SEPARATE_ANIME_MOVIE_SHOW = "separate_anime_movie_show";
    public static final String KEY_SHOW_WATCHING_UP_NEXT_ROW = "show_watching_up_next_row";
    public static final String KEY_SHOW_LAST_ADDED_ROW = "show_last_added_row";
    public static final String KEY_SHOW_LAST_PLAYED_ROW = "show_last_played_row";
    public static final String KEY_SHOW_ALL_MOVIES_ROW = "show_all_movies_row";
    public static final String KEY_MOVIE_SORT_ORDER ="preferences_movie_sort_order";
    public static final String KEY_SHOW_ALL_TV_SHOWS_ROW = "show_all_tv_shows_row";
    public static final String KEY_TV_SHOW_SORT_ORDER ="preferences_tv_show_sort_order";
    public static final String KEY_SHOW_ALL_ANIMES_ROW = "show_all_animes_row";
    public static final String KEY_ANIMES_SORT_ORDER ="preferences_animes_sort_order";

    public static final String KEY_MAKE_TIME_NEGATIVE = "make_time_negative";
    public static final String KEY_HIDE_TRAILER_ROW = "hide_trailer_row";
    public static final String KEY_SHOW_BY_RATING = "show_by_rating";

    public static final String KEY_VIDEO_OS = "preferences_video_os";
    public static final String KEY_TMDB="preferences_video_tmdb";
    public static final String KEY_TRAKT="preferences_video_trakt";
    public static final String KEY_TRAKT_SYNC_PROGRESS ="trakt_sync_resume";
    public static final String KEY_LICENCES="preferences_video_licences";

    public static final String KEY_DEC_CHOICE = "dec_choice";
    public static final String KEY_AUDIO_INTERFACE_CHOICE = "audio_interface_choice";
    public static final String KEY_SUBTITLES_HIDE = "subtitles_hide_default";
    public static final String KEY_SUBTITLES_FAV_LANG = "favSubLang";
    public static final String KEY_TRAKT_CATEGORY = "trakt_category";
    public static final String KEY_TRAKT_GETFULL = "trakt_getfull";
    public static final String KEY_TRAKT_SIGNIN = "trakt_signin";
    public static final String KEY_TRAKT_WIPE = "trakt_wipe";
    public static final String KEY_TRAKT_LIVE_SCROBBLING = "trakt_live_scrobbling";
    public static final String KEY_TRAKT_SYNC_COLLECTION = "trakt_sync_collection";
    public static final String KEY_HIDE_WATCHED = "hide_watched";
    public static final String KEY_CREATE_REMOTE_THUMBS = VideoProvider.PREFERENCE_CREATE_REMOTE_THUMBS;
    public static final String KEY_ENABLE_SPONSOR = "enable_sponsor";
    public static final String KEY_ABOUT_PREFERENCES = "preferences_about";

    public static final String KEY_SMB2 = "pref_smbv2";
    public static final String KEY_SMB_RESOLV = "pref_smb_resolv";

    public static final boolean SEPARATE_ANIME_MOVIE_SHOW_DEFAULT = true;
    // TODO: disabled until issue #186 is fixed
    public static final boolean SHOW_WATCHING_UP_NEXT_ROW_DEFAULT = true;
    public static final boolean SHOW_LAST_ADDED_ROW_DEFAULT = true;
    public static final boolean SHOW_LAST_PLAYED_ROW_DEFAULT = true;
    public static final boolean SHOW_ALL_MOVIES_ROW_DEFAULT = false;
    public static final boolean SHOW_ALL_TV_SHOWS_ROW_DEFAULT = false;
    public static final boolean SHOW_ALL_ANIMES_ROW_DEFAULT = false;

    public static final boolean MAKE_TIME_NEGATIVE_DEFAULT = false;
    public static final boolean HIDE_TRAILER_ROW_DEFAULT = false;
    public static final boolean SHOW_BY_RATING_DEFAULT = false;

    public static final boolean TRAKT_SYNC_COLLECTION_DEFAULT = false;
    public static final boolean TRAKT_LIVE_SCROBBLING_DEFAULT = true;
    public static final boolean ENABLE_SPONSOR_DEFAULT = false;

    public static final String LOGIN_DIALOG = "login_dialog";

    private static final boolean ACTIVATE_EMAIL_MEDIA_DB = true;
    private static final String KEY_RESCAN_STORAGE = "rescan_storage" ;
    private static final String KEY_DISPLAY_ALL_FILE = "preference_display_all_files" ;
    private final static String KEY_SCRAPER_CATEGORY = "scraper_category";

    private SharedPreferences mSharedPreferences = null;
    private int mAdvancedPrefsClickCount = 0;
    private long mAdvancedPrefsClickLastTime = 0;
    private int mEmailMediaDBPrefsClickCount = 0;
    private long mEmailMediaDBPrefsClickLastTime = 0;
    private int mMoreLeanbackPrefsClickCount = 0;
    private long mMoreLeanbackPrefsClickLastTime = 0;
    private ListPreference mDecChoicePreferences = null;
    private ListPreference mParserSyncMode = null;
    private ListPreference mAudioInterfaceChoicePreferences = null;
    private CheckBoxPreference mForceSwDecPreferences = null;
    private CheckBoxPreference mForceAudioPassthrough = null;
    private CheckBoxPreference mPlaybackSpeed = null;
    private CheckBoxPreference mDisableDownmix = null;
    private CheckBoxPreference mEnableDownmixATV = null;
    private CheckBoxPreference mActivateRefreshrateTVSwitch = null;
    private CheckBoxPreference mEnableCutoutModeShortEdge = null;
    private CheckBoxPreference mActivate3DTVSwitch = null;
    private PreferenceCategory mAdvancedPreferences = null;
    private PreferenceCategory mScraperCategory = null;
    private ListPreference mSubtitlesFavLangPreferences = null;
    private MultiSelectListPreference mSubtitlesDownloadLanguagePreferences = null;
    private CheckBoxPreference mEnableSponsor = null;
    private CheckBoxPreference mWatchingUpNext = null;
    private PreferenceCategory mAboutPreferences = null;
    private CheckBoxPreference mAdultScrape = null;
    private EditTextPreference mStreamBufferSize = null;
    private EditTextPreference mStreamMaxIFrameSize = null;
    private String mLastTraktUser = null;
    private Trakt.Status mTraktStatus = Trakt.Status.SUCCESS;
    private TraktSigninDialogPreference mTraktSigninPreference = null;
    private Preference mTraktWipePreference = null;
    private CheckBoxPreference mTraktLiveScrobblingPreference = null;
    private CheckBoxPreference mTraktSyncCollectionPreference = null;
    private CheckBoxPreference mTraktSyncProgressPreference = null;
    private CheckBoxPreference mAutoScrapPreference = null;

    private CheckBoxPreference mSeparateAnimeMoviePreference = null;
    private CheckBoxPreference mShowAllAnimesRowPreference = null;
    private ListPreference mAnimesSortOrderPreference = null;

    private Handler mHanlder = null;

    private Preference mTraktFull;

    private CheckBoxPreference mSmb2 = null;
    private CheckBoxPreference mSmbResolver = null;

    final public static int ACTIVITY_RESULT_UI_MODE_CHANGED = 665;
    final public static int ACTIVITY_RESULT_UI_ZOOM_CHANGED = 667;
    private Preference mExportManualPreference;
    private Preference mDbExportManualPreference = null;

    private PreferenceFragmentCompat mPreferencesFragment;

    public VideoPreferencesCommon(PreferenceFragmentCompat preferencesFragment) {
        mPreferencesFragment = preferencesFragment;
    }

    private Activity getActivity() {
        return mPreferencesFragment.getActivity();
    }

    private Context getContext() {
        return mPreferencesFragment.getContext();
    }

    private FragmentManager getParentFragmentManager() {
        return mPreferencesFragment.getParentFragmentManager();
    }

    private Resources getResources() {
        return mPreferencesFragment.getResources();
    }

    private String getString(int resId) {
        return mPreferencesFragment.getString(resId);
    }

    private boolean isVisible() {
        return mPreferencesFragment.isVisible();
    }

    private void startActivity(Intent intent) {
        mPreferencesFragment.startActivity(intent);
    }

    private void addPreferencesFromResource(int preferencesResId) {
        mPreferencesFragment.addPreferencesFromResource(preferencesResId);
    }

    private Preference findPreference(CharSequence key) {
        return mPreferencesFragment.findPreference(key);
    }

    private PreferenceManager getPreferenceManager() {
        return mPreferencesFragment.getPreferenceManager();
    }

    private PreferenceScreen getPreferenceScreen() {
        return mPreferencesFragment.getPreferenceScreen();
    }

    private void switchAdvancedPreferences() {
        PreferenceCategory prefCategory = (PreferenceCategory) findPreference("preferences_category_video");
        PreferenceCategory aboutCategory = (PreferenceCategory) findPreference(KEY_ABOUT_CATEGORY);
        if (!ArchosFeatures.isAndroidTV(getActivity())) { // not a TV
            prefCategory.removePreference(mActivate3DTVSwitch);
            prefCategory.removePreference(mEnableDownmixATV); // on TV downmix is disabled: show the option to enable it for harmonyOS
            if (REFRESHRATE_FORALL) prefCategory.addPreference(mActivateRefreshrateTVSwitch);
            else prefCategory.removePreference(mActivateRefreshrateTVSwitch);
            prefCategory.addPreference(mActivateRefreshrateTVSwitch);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                prefCategory.removePreference(mDisableDownmix); // on old Android downmix is forced: do not show the option
            else
                prefCategory.addPreference(mDisableDownmix);
            if (MiscUtils.hasCutout) {
                prefCategory.addPreference(mEnableCutoutModeShortEdge);
            }
            else {
                prefCategory.removePreference(mEnableCutoutModeShortEdge);
            }
        } else {
            // note enable_downmix_androidtv and disable_downmix are the opposite same settings but only one applies to androidTV
            // this is done on purpose to respect logic of presentation and default value
            // on huawei harmonyOS seems that you need downmix otherwise you loose front channels
            prefCategory.removePreference(mDisableDownmix); // on TV downmix for phone/tablet is disabled: do not show the option
            prefCategory.addPreference(mEnableDownmixATV); // on TV downmix is disabled: show the option to enable it for harmonyOS
            prefCategory.addPreference(mActivate3DTVSwitch);
            prefCategory.addPreference(mActivateRefreshrateTVSwitch);
            prefCategory.removePreference(mEnableCutoutModeShortEdge);
        }
        PreferenceCategory prefScraperCategory = (PreferenceCategory) findPreference(KEY_SCRAPER_CATEGORY);
        if (mSharedPreferences.getBoolean(KEY_ADVANCED_VIDEO_ENABLED, false)) {
            // advanced preferences
            Editor editor = mSharedPreferences.edit();
            editor.remove(KEY_FORCE_SW);
            editor.apply();
            // no need of the enable sponsor link if not installed from ggplay
            if (! ArchosUtils.isInstalledfromPlayStore(getContext())) {
                aboutCategory.removePreference(mEnableSponsor);
            } else {
                if (BuildConfig.ENABLE_SPONSOR)
                    aboutCategory.addPreference(mEnableSponsor);
                else aboutCategory.removePreference(mEnableSponsor);
            }
            prefCategory.removePreference(mForceSwDecPreferences);
            prefCategory.addPreference(mStreamBufferSize);
            prefCategory.addPreference(mStreamMaxIFrameSize);
            prefCategory.addPreference(mDecChoicePreferences);
            prefCategory.addPreference(mAudioInterfaceChoicePreferences);
            prefCategory.addPreference(mParserSyncMode);
            prefScraperCategory.addPreference(mDbExportManualPreference);
            getPreferenceScreen().addPreference(mAdvancedPreferences);
            if (BuildConfig.ADULT_SCRAPE) prefScraperCategory.addPreference(mAdultScrape);
        } else {
            // normal preferences
            //Editor editor = mDecChoicePreferences.getEditor();
            Editor editor = mSharedPreferences.edit();
            editor.remove(KEY_DEC_CHOICE);
            editor.remove(KEY_AUDIO_INTERFACE_CHOICE);
            editor.apply();
            aboutCategory.removePreference(mEnableSponsor);
            prefCategory.removePreference(mDecChoicePreferences);
            prefCategory.removePreference(mAudioInterfaceChoicePreferences);
            prefCategory.removePreference(mParserSyncMode);
            prefCategory.addPreference(mForceSwDecPreferences);
            prefScraperCategory.removePreference(mDbExportManualPreference);
            getPreferenceScreen().removePreference(mAdvancedPreferences);
            prefScraperCategory.removePreference(mAdultScrape);
            prefCategory.removePreference(mStreamBufferSize);
            prefCategory.removePreference(mStreamMaxIFrameSize);
        }
    }

    public static void resetPassthroughPref(SharedPreferences preferences){
        if(Integer.parseInt(preferences.getString("force_audio_passthrough_multiple","-1"))==-1&&preferences.getBoolean("force_audio_passthrough",false)){ //has never been set
            //has never been set with new mode but was set with old mode
            preferences.edit().putString("force_audio_passthrough_multiple","1").apply(); //set pref
        }
        if(Integer.parseInt(preferences.getString("force_audio_passthrough_multiple","-1"))>0){ // passthrough is set, reset audio_speed
            // if passthrough is set audio_speed is reset to 1.0f
            if (DBG) Log.d(TAG, "MARC resetPassthroughPref: audio_speed to 1.0f since passthrough is " + Integer.parseInt(preferences.getString("force_audio_passthrough_multiple","-1")));
            preferences.edit().putFloat("save_audio_speed_setting_pref_key", 1.0f).apply();
        }
    }

    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        // Load the preferences from an XML resource
        resetPassthroughPref(mSharedPreferences);

        addPreferencesFromResource(R.xml.preferences_video);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
        final Preference pref = (Preference) findPreference(KEY_VIDEO_OS);
        pref.setEnabled(true);
        pref.setOnPreferenceClickListener(preference -> {
            videoPreferenceOsClick();
            return false;
        });

        findPreference(KEY_TMDB).setOnPreferenceClickListener(preference -> {
            videoPreferenceTmdbClick();
            return false;
        });
        findPreference(KEY_TRAKT).setOnPreferenceClickListener(preference -> {
            videoPreferenceTraktClick();
            return false;
        });
        findPreference(KEY_LICENCES).setOnPreferenceClickListener(preference -> {
            if (!UiChoiceDialog.applicationIsInLeanbackMode(getActivity()))
                startActivity(new Intent(getActivity(), VideoPreferencesLicencesActivity.class));
            else
                startActivity(new Intent(getActivity(), VideoSettingsLicencesActivity.class));
            return false;
        });

        mDecChoicePreferences = (ListPreference) findPreference(KEY_DEC_CHOICE);
        mAudioInterfaceChoicePreferences = (ListPreference) findPreference(KEY_AUDIO_INTERFACE_CHOICE);
        mParserSyncMode = (ListPreference) findPreference(KEY_PARSER_SYNC_MODE);
        mForceSwDecPreferences = (CheckBoxPreference) findPreference(KEY_FORCE_SW);
        mEnableSponsor = (CheckBoxPreference) findPreference(KEY_ENABLE_SPONSOR);
        mWatchingUpNext = (CheckBoxPreference) findPreference(KEY_SHOW_WATCHING_UP_NEXT_ROW);
        mForceAudioPassthrough = (CheckBoxPreference) findPreference(KEY_FORCE_AUDIO_PASSTHROUGH);
        mStreamBufferSize = (EditTextPreference) findPreference(KEY_STREAM_BUFFER_SIZE);
        mStreamMaxIFrameSize = (EditTextPreference) findPreference(KEY_STREAM_MAX_IFRAME_SIZE);
        mPlaybackSpeed = (CheckBoxPreference) findPreference(KEY_PLAYBACK_SPEED);
        mDisableDownmix = (CheckBoxPreference) findPreference("disable_downmix");
        mEnableDownmixATV = (CheckBoxPreference) findPreference("enable_downmix_androidtv");
        mActivate3DTVSwitch = (CheckBoxPreference) findPreference(KEY_ACTIVATE_3D_SWITCH);
        mEnableCutoutModeShortEdge = (CheckBoxPreference) findPreference("enable_cutout_mode_short_edges");
        mActivateRefreshrateTVSwitch = (CheckBoxPreference) findPreference(KEY_ACTIVATE_REFRESHRATE_SWITCH);
        mAdultScrape = (CheckBoxPreference) findPreference(KEY_ADULT_SCRAPE);
        mTraktSyncProgressPreference = (CheckBoxPreference) findPreference(KEY_TRAKT_SYNC_PROGRESS);
        mAdvancedPreferences = (PreferenceCategory) findPreference(KEY_ADVANCED_VIDEO_CATEGORY);
        mSeparateAnimeMoviePreference = (CheckBoxPreference) findPreference(KEY_SEPARATE_ANIME_MOVIE_SHOW);
        mShowAllAnimesRowPreference = (CheckBoxPreference) findPreference(KEY_SHOW_ALL_ANIMES_ROW);
        mAnimesSortOrderPreference = (ListPreference) findPreference(KEY_ANIMES_SORT_ORDER);
        mAboutPreferences = (PreferenceCategory) findPreference(KEY_ABOUT_PREFERENCES);
        Preference novaVersion = (Preference) findPreference("preferences_version");
        novaVersion.setTitle(mSharedPreferences.getString("nova_version", "@string/APP_INFO"));

        mSmb2 = (CheckBoxPreference) findPreference(KEY_SMB2);
        mSmbResolver = (CheckBoxPreference) findPreference(KEY_SMB_RESOLV);

        mScraperCategory = (PreferenceCategory) findPreference(KEY_SCRAPER_CATEGORY);
        mExportManualPreference = findPreference(getString(R.string.nfo_export_manual_prefkey));
        mExportManualPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AutoScrapeService.EXPORT_EVERYTHING, null, getActivity(), AutoScrapeService.class);
            ContextCompat.startForegroundService(getActivity(), intent);
            Toast.makeText(getActivity(), R.string.nfo_export_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(getString(R.string.rescrap_all_prefkey)).setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AutoScrapeService.RESCAN_EVERYTHING, null, getActivity(), AutoScrapeService.class);
            intent.putExtra(AutoScrapeService.RESCAN_EVERYTHING, true);
            intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, false);
            ContextCompat.startForegroundService(getActivity(), intent);
            Toast.makeText(getActivity(), R.string.rescrap_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(getString(R.string.rescrap_all_movies_prefkey)).setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AutoScrapeService.RESCAN_MOVIES, null, getActivity(), AutoScrapeService.class);
            intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, false);
            ContextCompat.startForegroundService(getActivity(), intent);
            Toast.makeText(getActivity(), R.string.rescrap_movies_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(getString(R.string.rescrap_all_collections_prefkey)).setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AllCollectionScrapeService.INTENT_RESCRAPE_ALL_COLLECTIONS, null, getActivity(), AllCollectionScrapeService.class);
            ContextCompat.startForegroundService(getActivity(), intent);
            Toast.makeText(getActivity(), R.string.rescrap_collections_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        // recretate contexts in case of smb pref change
        mSmb2.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getActivity(), preference.getKey() + "=" + newValue.toString(), Toast.LENGTH_SHORT).show();
            JcifsUtils.notifyPrefChange();
            return true;
        });

        mSmbResolver.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getActivity(), preference.getKey() + "=" + newValue.toString(), Toast.LENGTH_SHORT).show();
            JcifsUtils.notifyPrefChange();
            return true;
        });

        mDbExportManualPreference = findPreference(getString(R.string.db_export_manual_prefkey));
        mDbExportManualPreference.setOnPreferenceClickListener(preference -> {
            backupDatabase(getContext(),"media.db");
            Toast.makeText(getActivity(), R.string.db_export_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(KEY_RESCAN_STORAGE).setOnPreferenceClickListener(preference -> {
            rescanPath(Environment.getExternalStorageDirectory().getAbsolutePath());
            ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
            final boolean hasExternal = storageManager.hasExtStorage();
            if (hasExternal) {
                for(String s : storageManager.getExtSdcards()) {
                    rescanPath(s);
                }
                for(String s : storageManager.getExtUsbStorages()) {
                    rescanPath(s);
                }
                for(String s : storageManager.getExtOtherStorages()) {
                    rescanPath(s);
                }
            }
            Toast.makeText(getActivity(), R.string.rescanning,Toast.LENGTH_SHORT).show();
            return true;
        });
        mAutoScrapPreference = (CheckBoxPreference) findPreference(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP);

        //manage change manually to set pref before starting service
        mAutoScrapPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean oldValue = getPreferenceManager().getSharedPreferences().getBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, true);
            getPreferenceManager().getSharedPreferences().edit().putBoolean(AutoScrapeService.KEY_ENABLE_AUTO_SCRAP, !oldValue).apply();

            mAutoScrapPreference.setChecked(!oldValue);
            if (!oldValue)
                AutoScrapeService.startService(getActivity());
            return false;
        });

        mTraktFull = findPreference(KEY_TRAKT_GETFULL);
        findPreference(KEY_SHARED_FOLDERS).setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(getActivity(), CredentialsManagerPreferenceActivity.class));
            return false;
        });
        findPreference("tv_shows").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), ScraperPreferences.class);
            intent.putExtra("media",12);
            startActivity(intent);
            return false;
        });
        findPreference("movies").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getActivity(), ScraperPreferences.class);
            intent.putExtra("media",11);
            startActivity(intent);
            return false;
        });
        mForceSwDecPreferences.setOnPreferenceClickListener(preference -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - mAdvancedPrefsClickLastTime > 1000)
                mAdvancedPrefsClickCount = 1;

            mAdvancedPrefsClickCount++;

            mAdvancedPrefsClickLastTime = currentTime;

            if (mAdvancedPrefsClickCount > 8) {
                Editor editor = mSharedPreferences.edit();
                editor.putBoolean(KEY_ADVANCED_VIDEO_ENABLED, true);
                editor.apply();
                switchAdvancedPreferences();
                return true;
            }
            return false;
        });

        mSeparateAnimeMoviePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean oldValue = getPreferenceManager().getSharedPreferences().getBoolean(KEY_SEPARATE_ANIME_MOVIE_SHOW, SEPARATE_ANIME_MOVIE_SHOW_DEFAULT);
            // !oldValue is the new value...
            mSeparateAnimeMoviePreference.setChecked(!oldValue);
            PreferenceCategory prefCategory = (PreferenceCategory) findPreference("category_leanback_user_interface");
            if (!oldValue) {
                // set visible
                prefCategory.addPreference(mShowAllAnimesRowPreference);
                prefCategory.addPreference(mAnimesSortOrderPreference);
            } else {
                // set not visible
                prefCategory.removePreference(mShowAllAnimesRowPreference);
                prefCategory.removePreference(mAnimesSortOrderPreference);
            }
            return false;
        });

        Preference p = findPreference(KEY_ADVANCED_VIDEO_QUIT);
        p.setOnPreferenceClickListener(preference -> {
            Editor editor = mSharedPreferences.edit();
            editor.putBoolean(KEY_ADVANCED_VIDEO_ENABLED, false);
            editor.apply();
            switchAdvancedPreferences();
            return true;
        });

        switchAdvancedPreferences();

        Preference subtitlesCredentialsButton = findPreference(KEY_SUBTITILES_CREDENTIALS);
        subtitlesCredentialsButton.setOnPreferenceClickListener(preference -> {
            String tag = OpenSubtitlesCredentialsDialog.class.getCanonicalName();
            OpenSubtitlesCredentialsDialog dialog = (OpenSubtitlesCredentialsDialog)getParentFragmentManager().findFragmentByTag(tag);
            if (dialog == null) {
                dialog = new OpenSubtitlesCredentialsDialog();
                dialog.show(getParentFragmentManager(), tag);
            }
            return true;
        });

        CheckBoxPreference cbp = (CheckBoxPreference)findPreference(KEY_SUBTITLES_HIDE);
        cbp.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean doHide = ((Boolean) newValue);
            mSubtitlesFavLangPreferences.setEnabled(!doHide);
            return true;
        });
        boolean doHide = mSharedPreferences.getBoolean(KEY_SUBTITLES_HIDE, false);

        mSubtitlesFavLangPreferences = (ListPreference) findPreference(KEY_SUBTITLES_FAV_LANG);
        mSubtitlesFavLangPreferences.setEnabled(!doHide);
        Locale defaultLocale = Locale.getDefault();
        String locale = defaultLocale.getISO3Language();

        List<String> entries = new ArrayList<String>(Arrays.asList(getResources().getStringArray(R.array.entries_list_preference)));
        List<String> entryValues = new ArrayList<String>(Arrays.asList(getResources().getStringArray(R.array.entryvalues_list_preference)));

        entries.set(0, defaultLocale.getDisplayLanguage()) ;
        Locale currentLocale;
        for (int i = 1; i < entryValues.size(); ++i){
            currentLocale = new Locale(entryValues.get(i));
            //entries.set(i, currentLocale.getDisplayLanguage()); // better use our translations, Android ones are not very consistant
            if (entryValues.get(i).equalsIgnoreCase(locale)){
                entries.remove(0);
                entryValues.remove(0);
            }
        }

        final CharSequence[] newEntries = new CharSequence[entries.size()];
        final CharSequence[] newEntryValues = new CharSequence[entryValues.size()];

        entries.toArray(newEntries);
        entryValues.toArray(newEntryValues);
        int systemLanguageIndex = -1;
        final String currentFavoriteLang = getPreferenceManager().getSharedPreferences().getString(KEY_SUBTITLES_FAV_LANG, Locale.getDefault().getISO3Language());
        int i = 0;
        for(CharSequence value : newEntryValues){
            if(value.toString().equalsIgnoreCase(currentFavoriteLang)) {
                systemLanguageIndex = i;
            }
            i++;
        }


        mSubtitlesDownloadLanguagePreferences = (MultiSelectListPreference) findPreference("languages_list");
        mSubtitlesDownloadLanguagePreferences.setEntries(newEntries);
        mSubtitlesDownloadLanguagePreferences.setEntryValues(newEntryValues);
        mSubtitlesFavLangPreferences.setEntries(newEntries);
        mSubtitlesFavLangPreferences.setEntryValues(newEntryValues);
        if(systemLanguageIndex>=0) mSubtitlesFavLangPreferences.setValueIndex(systemLanguageIndex);

        ListPreference lp = (ListPreference) findPreference("codepage");
        int cp = MediaFactory.getCodepage();
        int cpStringID = getResources().getIdentifier("codepage_extra_" + cp, "string", getActivity().getPackageName());
        if (cpStringID == 0)
            cpStringID = R.string.codepage_extra_1252;
        CharSequence [] entryArray = lp.getEntries();
        entryArray[0] = getResources().getString(R.string.codepage_default,getResources().getString(cpStringID));
        lp.setEntries(entryArray);

        mHanlder = new Handler();
        mTraktSigninPreference = (TraktSigninDialogPreference) findPreference(KEY_TRAKT_SIGNIN);
        if(mTraktSigninPreference!= null && savedInstanceState!=null) {
            if (DBG) Log.d(TAG, "onCreatePreferences: closing mTraktSigninPreference dialog to prevent leaked window");
            // close dialog to prevent leaked window
            mTraktSigninPreference.showDialog(savedInstanceState.getBoolean(LOGIN_DIALOG, false));
        }

        mTraktWipePreference = findPreference(KEY_TRAKT_WIPE);
        mTraktLiveScrobblingPreference = (CheckBoxPreference) findPreference(KEY_TRAKT_LIVE_SCROBBLING);
        //trakt resume must be disabled when no scrobbling
        mTraktSyncProgressPreference.setEnabled(mTraktLiveScrobblingPreference.isChecked() && mTraktLiveScrobblingPreference.isEnabled());
        mTraktLiveScrobblingPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                mTraktSyncProgressPreference.setEnabled((Boolean) o && mTraktLiveScrobblingPreference.isEnabled());
                if (!(Boolean) o)
                    mTraktSyncProgressPreference.setChecked(false);
                return true;
            }
        });
        mTraktSyncCollectionPreference = (CheckBoxPreference) findPreference(KEY_TRAKT_SYNC_COLLECTION);

        mTraktStatus = Trakt.Status.SUCCESS;

        CheckBoxPreference nfoExportAutoPreferences = (CheckBoxPreference) findPreference("nfo_export_auto");
        if (nfoExportAutoPreferences != null) {
            nfoExportAutoPreferences.setOnPreferenceClickListener(preference -> {
                if (!ACTIVATE_EMAIL_MEDIA_DB) {
                    return false;
                }
                // Check click speed
                long currentTime = System.currentTimeMillis();
                if (currentTime - mEmailMediaDBPrefsClickLastTime < 1000) {
                    mEmailMediaDBPrefsClickCount++;
                } else {
                    mEmailMediaDBPrefsClickCount = 0;
                }
                mEmailMediaDBPrefsClickLastTime = currentTime;

                if (mEmailMediaDBPrefsClickCount > 4) {
                    new AlertDialog.Builder(getActivity())
                            .setMessage(R.string.ask_to_mail_media_DB)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    new DebugDbExportDialogFragment()
                                            .show(getParentFragmentManager(), "DebugDbExportDialogFragment");
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create().show();
                    mEmailMediaDBPrefsClickCount = 0; // reset to not have several dialogs displayed if user continue to click very quickly
                    return true;
                }
                return false;
            });
        }

        PreferenceCategory userInterfaceCategory = (PreferenceCategory)findPreference("category_user_interface");
        if (userInterfaceCategory!=null) {
            // Leanback device case: no UI preference at all (for now at least)
            if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                getPreferenceScreen().removePreference(userInterfaceCategory);
            }
            // Not a leanback device, but if the APK integrates leanback the user can choose to use it
            else {
                userInterfaceCategory.removePreference(findPreference("uimode")); // remove the old uimode settings
                findPreference("uimode_leanback").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    public boolean onPreferenceChange(Preference preference, Object o) {
                        getActivity().setResult(ACTIVITY_RESULT_UI_MODE_CHANGED); // way to tell the MainActivity that an important preference has been changed
                        getActivity().finish(); // close the preference activity right away
                        return true;
                    }
                });
                Preference uiZoomPref = findPreference("ui_zoom");
                uiZoomPref.setOnPreferenceClickListener(preference -> {
                    getActivity().setResult(ACTIVITY_RESULT_UI_ZOOM_CHANGED); // way to tell the MainActivity that an important preference has been changed
                    getActivity().finish(); // close the preference activity right away
                    return true;
                });
                // Do not show the zoom preference if user is not in TV more UI
                String currentUiMode = getPreferenceManager().getSharedPreferences().getString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, "-");
                if (!currentUiMode.equals(UiChoiceDialog.UI_CHOICE_LEANBACK_TV_VALUE)) {
                    userInterfaceCategory.removePreference(uiZoomPref);
                }
            }
        }
        
        PreferenceCategory leanbackUserInterfaceCategory = (PreferenceCategory)findPreference("category_leanback_user_interface");
        
        if (leanbackUserInterfaceCategory != null) {
            if (!UiChoiceDialog.applicationIsInLeanbackMode(getActivity())) {
                getPreferenceScreen().removePreference(leanbackUserInterfaceCategory);
            }
            else {
                findPreference("reset_last_played_row").setOnPreferenceClickListener(preference -> {
                    DbUtils.markAsNotRead(getActivity());
                    Toast.makeText(getActivity(), R.string.reset_last_played_row_in_progress, Toast.LENGTH_SHORT).show();

                    return true;
                });

                // FIXME: for now feature watch up next is disabled because makes the interface crash
                if (! MainFragment.FEATURE_WATCH_UP_NEXT)
                    leanbackUserInterfaceCategory.removePreference(mWatchingUpNext);

                ListPreference movieSortOrderPref = (ListPreference)findPreference(KEY_MOVIE_SORT_ORDER);
                
                movieSortOrderPref.setEntries(MoviesSortOrderEntry.getSortOrderEntries(getActivity(), AllMoviesGridFragment.sortOrderIndexer));
                movieSortOrderPref.setEntryValues(MoviesSortOrderEntry.getSortOrderEntryValues(getActivity(), AllMoviesGridFragment.sortOrderIndexer));
                
                if (movieSortOrderPref.getValue() == null)
                    movieSortOrderPref.setValue(MoviesLoader.DEFAULT_SORT);

                ListPreference tvshowSortOrderPref = (ListPreference)findPreference(KEY_TV_SHOW_SORT_ORDER);

                tvshowSortOrderPref.setEntries(TvshowsSortOrderEntry.getSortOrderEntries(getActivity(), AllTvshowsGridFragment.sortOrderIndexer));
                tvshowSortOrderPref.setEntryValues(TvshowsSortOrderEntry.getSortOrderEntryValues(getActivity(), AllTvshowsGridFragment.sortOrderIndexer));
                
                if (tvshowSortOrderPref.getValue() == null)
                    tvshowSortOrderPref.setValue(TvshowSortOrderEntries.DEFAULT_SORT);

                ListPreference animesSortOrderPref = (ListPreference)findPreference(KEY_ANIMES_SORT_ORDER);

                animesSortOrderPref.setEntries(AnimesSortOrderEntry.getSortOrderEntries(getActivity(), AllAnimesGridFragment.sortOrderIndexer));
                animesSortOrderPref.setEntryValues(AnimesSortOrderEntry.getSortOrderEntryValues(getActivity(), AllAnimesGridFragment.sortOrderIndexer));

                if (animesSortOrderPref.getValue() == null)
                    animesSortOrderPref.setValue(AnimeShowSortOrderEntries.DEFAULT_SORT);

                findPreference(KEY_SHOW_ALL_TV_SHOWS_ROW).setOnPreferenceClickListener(preference -> {
                    // Check click speed
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - mMoreLeanbackPrefsClickLastTime < 1000) {
                        mMoreLeanbackPrefsClickCount++;
                    } else {
                        mMoreLeanbackPrefsClickCount = 0;
                    }
                    mMoreLeanbackPrefsClickLastTime = currentTime;

                    if (mMoreLeanbackPrefsClickCount > 4) {
                        if (UiChoiceDialog.applicationIsInLeanbackMode(getActivity()))
                            startActivity(new Intent(getActivity(), VideoSettingsMoreLeanbackActivity.class));
                        mMoreLeanbackPrefsClickCount = 0; // reset
                    }
                    return false;
                });
            }
        }

        // Free / Paid below was before in setPaidStatus();
        PreferenceCategory prefCategorty = (PreferenceCategory) findPreference(KEY_TRAKT_CATEGORY);
        prefCategorty.removePreference(mTraktFull);
        mTraktSigninPreference.setEnabled(true);
        findPreference(KEY_TORRENT_BLOCKLIST).setOnPreferenceClickListener(null);
        findPreference(KEY_TORRENT_PATH).setOnPreferenceClickListener(null);

        mLastTraktUser = Trakt.getUserFromPreferences(mSharedPreferences);
        if (onTraktUserChange()) {
            Trakt trakt = new Trakt(getActivity());
            trakt.setListener(new Trakt.Listener() {
                @Override
                public void onResult(Trakt.Result result) {
                    mTraktStatus = result.status;
                    mHanlder.post(new Runnable() {
                        @Override
                        public void run() {
                            if (isVisible())
                                onTraktUserChange();
                        }
                    });
                }
            });
        }
    }

    private void rescanPath(String s) {
        isMediaScannerScanning(getActivity().getContentResolver());
        Uri toIndex = Uri.parse(s);
        if (toIndex.getScheme() == null)
            toIndex = Uri.parse("file://" + toIndex.toString());
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setData(toIndex);
        getActivity().sendBroadcast(scanIntent);
    }

    public static boolean isMediaScannerScanning(ContentResolver cr) {
        boolean result = false;
        Cursor cursor  = cr.query(MediaStore.getMediaScannerUri(),
                new String[]{MediaStore.MEDIA_SCANNER_VOLUME},
                null, null, null);
        if (cursor != null) {
            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                result = "external".equals(cursor.getString(0));
            }
            cursor.close();
        }
        return result;
    }

    public void videoPreferenceOsClick() {
        // Breaks AndroidTV acceptance: inappropriate content TV-AA rating on opensubtitles web site
        //WebUtils.openWebLink(getActivity(), "https://www.opensubtitles.org/support");
    }
    public void videoPreferenceTmdbClick() {
        // Breaks AndroidTV acceptance: text is cut on edges
        //WebUtils.openWebLink(getActivity(), "https://www.themoviedb.org/faq/general");
    }
    public void videoPreferenceTraktClick() {
        //WebUtils.openWebLink(getActivity(), "https://trakt.tv/about");
    }

    public void onDestroy() {
        if (mSharedPreferences != null)
            mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void setTraktPreferencesEnabled(boolean enabled) {
        mTraktWipePreference.setEnabled(enabled);
        mTraktLiveScrobblingPreference.setEnabled(enabled);
        mTraktSyncCollectionPreference.setEnabled(enabled);
        mTraktSyncProgressPreference.setEnabled(mTraktLiveScrobblingPreference.isChecked() && mTraktLiveScrobblingPreference.isEnabled());
        mTraktSigninPreference.setEnabled(!enabled);
    }

    public void onSaveInstanceState(Bundle outState) {
        if(mTraktSigninPreference!= null && mTraktSigninPreference.isDialogShowing()) {
            // close dialog to prevent leaked window
            mTraktSigninPreference.dismissDialog();
            outState.putBoolean(LOGIN_DIALOG, true);
        }
    }
    private boolean onTraktUserChange() {
        final String traktUser = Trakt.getAccessTokenFromPreferences(mSharedPreferences);

        if (traktUser != null) {
            if (mLastTraktUser != null && !traktUser.equals(mLastTraktUser)) {
                Trakt.wipePreferences(mSharedPreferences, true);
                new TraktService.Client(getActivity(), null, false).wipe();
            }

            setTraktPreferencesEnabled(true);
            if (mTraktStatus == Trakt.Status.ERROR_AUTH) {
                mTraktSigninPreference.setSummary(getResources().getString(R.string.trakt_signin_summary_logged_error));
            } else {
                mTraktSigninPreference.setSummary(getResources().getString(R.string.trakt_signin_summary_logged));
                new TraktService.Client(getActivity(), null, false).sync(0);
            }
        } else {
            if (mLastTraktUser != null ) {
                Trakt.wipePreferences(PreferenceManager.getDefaultSharedPreferences(getActivity()), false);
                new TraktService.Client(getActivity(), null, false).wipe();
            }
            mTraktSigninPreference.setSummary(R.string.trakt_signin_summary);
            mTraktSyncCollectionPreference.setChecked(TRAKT_SYNC_COLLECTION_DEFAULT);
            mTraktLiveScrobblingPreference.setChecked(TRAKT_LIVE_SCROBBLING_DEFAULT);
            setTraktPreferencesEnabled(false);
        }
        mLastTraktUser = traktUser;

        return traktUser != null;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode==VideoPreferencesActivity.FOLDER_PICKER_REQUEST_CODE){
            if (resultCode == AppCompatActivity.RESULT_OK) {
                String newPath = data.getStringExtra(FolderPicker.EXTRA_SELECTED_FOLDER);
                if (newPath!=null) {
                    File f = new File(newPath);
                    if ((f!=null) && f.isDirectory() && f.exists()) { //better safe than sorry x3
                        PreferenceManager.getDefaultSharedPreferences(getActivity()).edit().putString(VideoPreferencesCommon.KEY_TORRENT_PATH, f.getAbsolutePath()).apply();
                        ((TorrentPathDialogPreference)findPreference(KEY_TORRENT_PATH)).notifyChanged();
                    }
                }
            }
        }
    }
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equals(Trakt.KEY_TRAKT_USER) ||key.equals(Trakt.KEY_TRAKT_ACCESS_TOKEN)|| key.equals(Trakt.KEY_TRAKT_SHA1)) {
            // preference just changed, assume it's valid
            mTraktStatus = Trakt.Status.SUCCESS;
            onTraktUserChange();
        } else if (key.equals(KEY_TRAKT_SYNC_COLLECTION)) {
            if (Trakt.isTraktV2Enabled(getActivity(), mSharedPreferences)) {
                Boolean newBoolean = (Boolean) sharedPreferences.getBoolean(KEY_TRAKT_SYNC_COLLECTION, TRAKT_SYNC_COLLECTION_DEFAULT);
                if (newBoolean) {
                    TraktService.sync(getActivity(),
                            TraktService.FLAG_SYNC_MOVIES |
                                    TraktService.FLAG_SYNC_SHOWS |
                                    TraktService.FLAG_SYNC_TO_DB_COLLECTION |
                                    TraktService.FLAG_SYNC_TO_TRAKT_COLLECTION);
                } else {
                    new TraktService.Client(getActivity(), null, false).wipeCollection();
                }
            }
        }
    }

    /*

        preference helper
     */

    public static class PreferenceHelper{

        public static boolean shouldDisplayAllFiles(Context context){
            if (context != null)
                return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_DISPLAY_ALL_FILE, false);
            else return false;
        }
    }

}
