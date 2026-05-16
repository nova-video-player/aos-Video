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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import android.os.Looper;
import android.provider.MediaStore;
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
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.BuildConfig;
import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;
import com.archos.mediacenter.video.browser.loader.MoviesLoader;
import com.archos.mediacenter.video.leanback.LeanbackActivity;
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
import com.archos.mediaprovider.video.LoaderUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

import static com.archos.filecorelibrary.FileUtils.backupDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VideoPreferencesCommon implements OnSharedPreferenceChangeListener {

    private static final Logger log = LoggerFactory.getLogger(VideoPreferencesCommon.class);

    // update with: `curl --request GET --url https://api.opensubtitles.com/api/v1/infos/languages | jq -r '.data[].language_code' | sort -u | gpaste -sd "|"`
    // list exceptions via `| grep -E '.{3,}' | gpaste -sd "|"` "zh-cn|pt-pt|pt-br|zh-tw"
    private static final String OPENSUBTITLES_LANGUAGES = "ab|af|an|ar|as|at|az|be|bg|bn|br|bs|ca|cs|cy|da|de|ea|el|en|eo|es|et|eu|ex|fa|fi|fr|ga|gd|gl|he|hi|hr|hu|hy|ia|id|ig|is|it|ja|ka|kk|km|kn|ko|ku|lb|lt|lv|ma|me|mk|ml|mn|mr|ms|my|ne|nl|no|nv|oc|or|pl|pm|pr|ps|pt-br|pt-pt|ro|ru|sd|se|si|sk|sl|so|sp|sq|sr|sv|sw|sx|sy|ta|te|th|tk|tl|tp|tr|tt|uk|ur|uz|vi|ze|zh-cn|zh-tw|zh-ca";

    // see https://developer.themoviedb.org/docs/languages
    // curl --request GET --url 'https://api.themoviedb.org/3/configuration/languages?api_key=APIKEY' | jq '.[] | .iso_639_1' | sed 's/"\([^"]*\)"/\1/g' | grep -v mo | grep -v xx | sort -u | paste -sd "|" -
    // to do after: add manually pt-br and substitute cn=zh-tw and zh=zh-cn
    // zh = Mandarin -> Chinese Simplified (zh-cn) or Chinese
    // cn = Cantonese -> Chinese Traditional (zh-tw)
    public final static String TMDB_LANGUAGES = "aa|ab|ae|af|ak|am|an|ar|as|av|ay|az|ba|be|bg|bi|bm|bn|bo|br|bs|ca|ce|ch|zh-tw|co|cr|cs|cu|cv|cy|da|de|dv|dz|ee|el|en|eo|es|et|eu|fa|ff|fi|fj|fo|fr|fy|ga|gd|gl|gn|gu|gv|ha|he|hi|ho|hr|ht|hu|hy|hz|ia|id|ie|ig|ii|ik|io|is|it|iu|ja|jv|ka|kg|ki|kj|kk|kl|km|kn|ko|kr|ks|ku|kv|kw|ky|la|lb|lg|li|ln|lo|lt|lu|lv|mg|mh|mi|mk|ml|mn|mr|ms|mt|my|na|nb|nd|ne|ng|nl|nn|no|nr|nv|ny|oc|oj|om|or|os|pa|pi|pl|ps|pt|pt-br|qu|rm|rn|ro|ru|rw|sa|sc|sd|se|sg|sh|si|sk|sl|sm|sn|so|sq|sr|ss|st|su|sv|sw|ta|te|tg|th|ti|tk|tl|tn|to|tr|ts|tt|tw|ty|ug|uk|ur|uz|ve|vi|vo|wa|wo|xh|yi|yo|za|zh-cn|zh-hk|zu";

    // basic Chinese howto
    // zh-cn Chinese (Mainland China): Mandarin (mostly in Simplified charset)
    // zh-tw Chinese (Taiwan): Min (mostly in Traditional charset)
    // zh-hk Chinese (Hong Kong): Cantonese
    // Character set = Simplified or Traditional
    // opensubtitles
    // zh-ca = Chinese (Cantonese) -> zh-hk Chinese (Hong Kong),
    // zh-cn = Chinese (simplified) -> zh-cn Chinese (Mainland China),
    // zh-tw = Chinese (traditional) -> zh-tw Chinese (Taiwan)
    // ze = Chinese bilingual -> Chinese Cantonese + English
    // tmdb (proposed by Yu)
    // do not use zh Mandarin = zh-cn, Chinese (Mainland China)
    // do not use cn Cantonese = zh-hk, Chinese (Hong Kong)
    // add zh-tw, Chinese (Taiwan)

    // crowdin nova translation languages
    // cd Video/res; (ls -d values-* | grep -Ev '(-w|-sw).*dp' | grep -Ev '(notouch|land)' | sed -E 's/values-//; s/-r/-/' | sort && echo "en") | sort | gpaste -sd "|"
    public final static String UI_LANGUAGES = "ar|cs-CZ|de|el-GR|en|es|fa-IR|fr|hu-HU|it|iw|kaa|kmr-TR|ko|lt-LT|nl|or-IN|pl|pt-BR|pt-PT|ru|sk-SK|sv-SE|ta-IN|tr-TR|uk-UA|vi-VN|zh-CN|zh-TW";

    // should we provide adaptive refresh rate for all (not only on TV)
    private static final boolean REFRESHRATE_FORALL = true;

    // default stream buffer size in MB before parser
    public static final int DEFAULT_STREAM_BUFFER_SIZE = 24;
    // default max iframe compressed frame size in MB
    public static final int DEFAULT_MAX_IFRAME_SIZE = 6;

    public static final String KEY_ADVANCED_VIDEO_ENABLED = "preferences_advanced_video_enabled";
    public static final String KEY_ADVANCED_VIDEO_CATEGORY = "preferences_category_advanced_video";
    public static final String KEY_ABOUT_CATEGORY = "about_category";
    public static final String KEY_NETSHARE_CATEGORY = "netshare_category";
    public static final String KEY_ADVANCED_3D_TV_SWITCH_SUPPORTED = "preferences_tv_switch_supported";
    public static final String KEY_ADVANCED_VIDEO_QUIT = "preferences_video_advanced_quit";
    public static final String KEY_TORRENT_BLOCKLIST = "preferences_torrent_blocklist";
    public static final String KEY_TORRENT_PATH = "preferences_torrent_path";
    public static final String KEY_SHARED_FOLDERS= "share_folders";
    public static final String KEY_SUBTITILES_CREDENTIALS= "subtitles_credentials";
    public static final String KEY_FORCE_SW = "force_software_decoding";
    public static final String KEY_FORCE_AUDIO_PASSTHROUGH = "force_passthrough";
    public static final String KEY_PARSER_SYNC_MODE = "parser_sync_mode";
    public static final String KEY_DOLBY_VISION_MODE = "dolby_vision_mode";
    public static final String KEY_DISABLE_DOLBY_VISION = "disable_dolby_vision";
    public static final String KEY_STREAM_BUFFER_SIZE = "stream_buffer_size";
    public static final String KEY_STREAM_MAX_IFRAME_SIZE = "stream_max_iframe_size";
    public static final String KEY_PLAYBACK_SPEED = "playback_speed";
    public static final String KEY_AUDIO_SPEED_AUDIOTRACK = "audio_speed_audiotrack";
    public static final String KEY_ENABLE_DYNAMIC_AUDIO_DELAY = "enable_dynamic_audio_delay";
    public static final String KEY_ACTIVATE_REFRESHRATE_SWITCH = "enable_tv_refreshrate_switch_mode";
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
    public static final String KEY_TMDB = "preferences_video_tmdb";
    public static final String KEY_TRAKT = "preferences_video_trakt";
    public static final String KEY_TRAKT_SYNC_PROGRESS = "trakt_sync_resume";
    public static final String KEY_LICENCES = "preferences_video_licences";

    public static final String KEY_UI_LANG = "ui_lang";
    public static final String KEY_UI_LANG_SYSTEM = "syst";
    public static final String KEY_DEC_CHOICE = "dec_choice";
    public static final String KEY_AUDIO_INTERFACE_CHOICE = "audio_interface_choice";
    public static final String KEY_AUDIO_DECODER_CHOICE = "audio_decoder_choice";
    public static final String KEY_SUBTITLES_HIDE = "subtitles_hide_default";
    public static final String KEY_SUBTITLES_FAV_LANG = "favSubLang";
    public static final String KEY_AUDIO_TRACK_FAV_LANG = "favAudioLang";
    public static final String KEY_SCRAPER_FAV_LANG = "favScraperLang";
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
    public static final String KEY_SMB_DISABLE_TCP_DISCOVERY = "pref_smb_disable_tcp_discovery";
    public static final String KEY_SMB_DISABLE_UDP_DISCOVERY = "pref_smb_disable_udp_discovery";
    public static final String KEY_SMB_DISABLE_MDNS_DISCOVERY = "pref_smb_disable_mdns_discovery";
    public static final String KEY_SMBJ = "pref_smbj";
    public static final String KEY_APP_THEME = "app_theme";

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
    private ListPreference mAudioDecoderChoicePreferences = null;
    private CheckBoxPreference mForceSwDecPreferences = null;
    private CheckBoxPreference mForceAudioPassthrough = null;
    private CheckBoxPreference mPlaybackSpeed = null;
    private CheckBoxPreference mAudioSpeedAudiotrack = null;
    private CheckBoxPreference mEnableDynamicAudioDelay = null;
    private CheckBoxPreference mDisableDownmix = null;
    private CheckBoxPreference mEnableDownmixATV = null;
    private ListPreference mActivateRefreshrateTVSwitch = null;
    private CheckBoxPreference mEnableCutoutModeShortEdge = null;
    private CheckBoxPreference mEnableCutBothSidesX = null;
    private CheckBoxPreference mActivate3DTVSwitch = null;
    private PreferenceCategory mAdvancedPreferences = null;
    private PreferenceCategory mScraperCategory = null;
    private ListPreference mSubtitlesFavLangPreferences = null;
    private ListPreference mUiLang = null;
    private MultiSelectListPreference mSubtitlesDownloadLanguagePreferences = null;
    private ListPreference mTMDbScraperLanguagePreferences = null;
    private ListPreference mAudioTrackFavoriteLanguage = null;
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
    private Preference mTraktForcePushPreference = null;
    private Preference mTraktForcePullPreference = null;
    private CheckBoxPreference mAutoScrapPreference = null;

    private CheckBoxPreference mSeparateAnimeMoviePreference = null;
    private CheckBoxPreference mShowAllAnimesRowPreference = null;
    private ListPreference mAnimesSortOrderPreference = null;
    private ListPreference mDefaultVideoSortOrderPreference = null;

    private Handler mHanlder = null;

    private Preference mTraktFull;

    private CheckBoxPreference mSmb2 = null;
    private CheckBoxPreference mSmbResolver = null;
    private CheckBoxPreference mSmbDisableTcpDiscovery = null;
    private CheckBoxPreference mSmbDisableUdpDiscovery = null;
    private CheckBoxPreference mSmbDisableMdnsDiscovery = null;
    private CheckBoxPreference mSmbj = null;

    final public static int ACTIVITY_RESULT_UI_MODE_CHANGED = 665;
    final public static int ACTIVITY_RESULT_UI_ZOOM_CHANGED = 667;
    final public static int ACTIVITY_RESULT_THEME_CHANGED = 668;
    private Preference mExportManualPreference;
    private Preference mDbExportManualPreference = null;

    private PreferenceFragmentCompat mPreferencesFragment;

    List<String> OpensubtitlesLanguageListEntries = new ArrayList<>();
    List<String> OpensubtitlesLanguageListEntryValues = new ArrayList<>();
    int OpensubtitlesSystemLanguageIndex = -1;
    int systemAudioLanguageIndex = -1;
    List<String> TMDbLanguageListEntries = new ArrayList<>();
    List<String> TMDbLanguageListEntryValues = new ArrayList<>();
    int TMDbSystemLanguageIndex = -1;

    int UiSystemLanguageIndex =  -1;
    List<String> UiLanguageListEntries = new ArrayList<>();
    List<String> UiLanguageListEntryValues = new ArrayList<>();

    private final ActivityResultLauncher<Intent> mFolderPickerLauncher;
    private final ActivityResultLauncher<Intent> mTraktAuthLauncher;

    public VideoPreferencesCommon(PreferenceFragmentCompat preferencesFragment) {
        mPreferencesFragment = preferencesFragment;
        mFolderPickerLauncher = preferencesFragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onFolderPickerResult);
        mTraktAuthLauncher = preferencesFragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::onTraktAuthResult);
    }

    private void onFolderPickerResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            String newPath = result.getData().getStringExtra(FolderPicker.EXTRA_SELECTED_FOLDER);
            if (newPath != null) {
                java.io.File f = new java.io.File(newPath);
                if (f.isDirectory() && f.exists()) {
                    PreferenceManager.getDefaultSharedPreferences(getActivity())
                            .edit().putString(KEY_TORRENT_PATH, f.getAbsolutePath()).apply();
                    TorrentPathDialogPreference pref =
                            (TorrentPathDialogPreference) findPreference(KEY_TORRENT_PATH);
                    if (pref != null) pref.notifyChanged();
                }
            }
        }
    }

    private void onTraktAuthResult(ActivityResult result) {
        if (mTraktSigninPreference != null)
            mTraktSigninPreference.onAuthCompleted(result.getResultCode() == Activity.RESULT_OK);
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
        PreferenceCategory netShareCategory = (PreferenceCategory) findPreference(KEY_NETSHARE_CATEGORY);
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
                prefCategory.addPreference(mEnableCutBothSidesX);
            }
            else {
                prefCategory.removePreference(mEnableCutoutModeShortEdge);
                prefCategory.removePreference(mEnableCutBothSidesX);
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
            prefCategory.removePreference(mEnableCutBothSidesX);
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
            // more smb discovery disabling options in advanced mode
            netShareCategory.addPreference(mSmbDisableTcpDiscovery);
            netShareCategory.addPreference(mSmbDisableMdnsDiscovery);
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
            // not needed since for fire10hd only UDP discovery is upsetting wifi drivers
            netShareCategory.removePreference(mSmbDisableTcpDiscovery);
            netShareCategory.removePreference(mSmbDisableMdnsDiscovery);
        }
    }

    public static void resetPassthroughPref(SharedPreferences preferences){
        if(Integer.parseInt(preferences.getString("force_audio_passthrough_multiple","-1"))==-1&&preferences.getBoolean("force_audio_passthrough",false)){ //has never been set
            //has never been set with new mode but was set with old mode
            preferences.edit().putString("force_audio_passthrough_multiple","1").apply(); //set pref
        }
        if(Integer.parseInt(preferences.getString("force_audio_passthrough_multiple","-1"))>0){ // passthrough is set, reset audio_speed
            // if passthrough is set audio_speed is reset to 1.0f
            if (log.isDebugEnabled()) log.debug("resetPassthroughPref: audio_speed to 1.0f since passthrough is {}", Integer.parseInt(preferences.getString("force_audio_passthrough_multiple","-1")));
            preferences.edit().putFloat("save_audio_speed_setting_pref_key", 1.0f).apply();
        }
    }

    /**
     * Update the enabled and selectable state of dynamic audio delay preference
     * based on passthrough settings. When passthrough is enabled, dynamic audio delay
     * is automatically turned off as it's not compatible with passthrough mode.
     *
     * @param passthroughEnabled true if audio passthrough is enabled
     * @param frameTimingEnabled true if Android frame timing is enabled (unused but kept for compatibility)
     */
    private void updateDynamicAudioDelayState(boolean passthroughEnabled, boolean frameTimingEnabled) {
        if (passthroughEnabled) {
            // Passthrough takes precedence - disable dynamic audio delay in UI
            // but keep its state so it's restored when passthrough is disabled
            mEnableDynamicAudioDelay.setEnabled(false);
            mEnableDynamicAudioDelay.setSelectable(false);
        } else {
            // Normal state - user can control dynamic audio delay
            mEnableDynamicAudioDelay.setEnabled(true);
            mEnableDynamicAudioDelay.setSelectable(true);
        }
    }

    private void updateAudioDecoderChoiceState(boolean passthroughEnabled) {
        if (mAudioDecoderChoicePreferences == null) {
            return;
        }
        mAudioDecoderChoicePreferences.setEnabled(!passthroughEnabled);
        mAudioDecoderChoicePreferences.setSelectable(!passthroughEnabled);
    }

    /**
     * Remove Mode 1 (IEC61937) from the passthrough mode preference list
     * Used when device doesn't support ENCODING_IEC61937
     * Preserves Mode 2 as "2" and Mode 3 as "3"
     */
    private void removeMode1FromPassthroughOptions(ListPreference preference) {
        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        if (entries == null || values == null) {
            return;
        }

        // Find and remove Mode 1 entry
        int mode1Index = -1;
        for (int i = 0; i < values.length; i++) {
            if ("1".equals(values[i].toString())) {
                mode1Index = i;
                break;
            }
        }

        if (mode1Index < 0) {
            return;  // Mode 1 not found
        }

        // Create new arrays without Mode 1
        CharSequence[] newEntries = new CharSequence[entries.length - 1];
        CharSequence[] newValues = new CharSequence[values.length - 1];

        int newIndex = 0;
        for (int i = 0; i < entries.length; i++) {
            if (i != mode1Index) {
                newEntries[newIndex] = entries[i];
                newValues[newIndex] = values[i];
                newIndex++;
            }
        }

        // Update the preference with the modified entries (Mode 2 and 3 keep their values)
        preference.setEntries(newEntries);
        preference.setEntryValues(newValues);

        // If Mode 1 is currently selected, reset to Mode 0
        String currentValue = preference.getValue();
        if ("1".equals(currentValue)) {
            preference.setValue("0");
            preference.setSummary(preference.getEntry());  // Update summary to reflect new selection
            mSharedPreferences.edit().putString("force_audio_passthrough_multiple", "0").apply();
            log.info("removeMode1FromPassthroughOptions: Mode 1 was selected but not supported, resetting to mode 0");
        } else {
            if (log.isDebugEnabled()) log.debug("removeMode1FromPassthroughOptions: Mode 1 removed from options (mode 2 and 3 preserved with original values)");
        }
    }

    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        CustomApplication.loadLocale(getResources());
        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        // Load the preferences from an XML resource
        resetPassthroughPref(mSharedPreferences);

        addPreferencesFromResource(R.xml.preferences_video);

        TorrentPathDialogPreference torrentPref =
                (TorrentPathDialogPreference) findPreference(KEY_TORRENT_PATH);
        if (torrentPref != null) torrentPref.setFolderPickerLauncher(mFolderPickerLauncher);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        migrateDolbyVisionPreference(mSharedPreferences);
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
        mAudioDecoderChoicePreferences = (ListPreference) findPreference(KEY_AUDIO_DECODER_CHOICE);
        mParserSyncMode = (ListPreference) findPreference(KEY_PARSER_SYNC_MODE);
        mForceSwDecPreferences = (CheckBoxPreference) findPreference(KEY_FORCE_SW);
        mEnableSponsor = (CheckBoxPreference) findPreference(KEY_ENABLE_SPONSOR);
        mWatchingUpNext = (CheckBoxPreference) findPreference(KEY_SHOW_WATCHING_UP_NEXT_ROW);
        mForceAudioPassthrough = (CheckBoxPreference) findPreference(KEY_FORCE_AUDIO_PASSTHROUGH);
        mPlaybackSpeed = (CheckBoxPreference) findPreference(KEY_PLAYBACK_SPEED);
        mAudioSpeedAudiotrack = (CheckBoxPreference) findPreference(KEY_AUDIO_SPEED_AUDIOTRACK);
        mEnableDynamicAudioDelay = (CheckBoxPreference) findPreference(KEY_ENABLE_DYNAMIC_AUDIO_DELAY);
        mDisableDownmix = (CheckBoxPreference) findPreference("disable_downmix");
        mEnableDownmixATV = (CheckBoxPreference) findPreference("enable_downmix_androidtv");
        final ListPreference mForceAudioPassthroughMultiple = (ListPreference) findPreference("force_audio_passthrough_multiple");
        boolean isPassthroughSupported = CustomApplication.isPassthroughSupported();
        boolean isIecEncapsulationCapable = CustomApplication.isIecEncapsulationCapable();
        mForceAudioPassthrough.setEnabled(isPassthroughSupported);
        mForceAudioPassthroughMultiple.setEnabled(isPassthroughSupported);
        mForceAudioPassthrough.setSelectable(isPassthroughSupported);
        mForceAudioPassthroughMultiple.setSelectable(isPassthroughSupported);

        // Remove Mode 1 (IEC61937 manual wrapping) from options if not supported by device.
        // On API < 23, EXTRA_ENCODINGS is unreliable on some TVs, so don't hide mode 1
        // based solely on that signal.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !isIecEncapsulationCapable
                && mForceAudioPassthroughMultiple != null) {
            removeMode1FromPassthroughOptions(mForceAudioPassthroughMultiple);
        }

        // Get initial frame timing state
        boolean frameTimingEnabled = mSharedPreferences.getBoolean("enable_android_frame_timing", false);

        if (isPassthroughSupported) {
            String passthroughMode = mSharedPreferences.getString("force_audio_passthrough_multiple", "0");
            boolean passthroughEnabled = !"0".equals(passthroughMode);
            mForceAudioPassthrough.setEnabled(passthroughEnabled);
            mPlaybackSpeed.setEnabled(!passthroughEnabled);
            mPlaybackSpeed.setSelectable(!passthroughEnabled);
            updateAudioDecoderChoiceState(passthroughEnabled);
            // Audio speed audiotrack should be non-selectable when playback speed is non-selectable
            mAudioSpeedAudiotrack.setSelectable(!passthroughEnabled);
            // Disable downmix should be non-selectable when passthrough is enabled
            mDisableDownmix.setSelectable(!passthroughEnabled);
            // Dynamic audio delay depends on both passthrough and frame timing
            updateDynamicAudioDelayState(passthroughEnabled, frameTimingEnabled);
            mForceAudioPassthroughMultiple.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean newPassthroughEnabled = !"0".equals(newValue.toString());
                mForceAudioPassthrough.setEnabled(newPassthroughEnabled);
                mPlaybackSpeed.setEnabled(!newPassthroughEnabled);
                mPlaybackSpeed.setSelectable(!newPassthroughEnabled);
                updateAudioDecoderChoiceState(newPassthroughEnabled);
                // Audio speed audiotrack should be non-selectable when playback speed is non-selectable
                mAudioSpeedAudiotrack.setSelectable(!newPassthroughEnabled);
                // Disable downmix should be non-selectable when passthrough is enabled
                mDisableDownmix.setSelectable(!newPassthroughEnabled);
                return true;
            });
        } else {
            // Ensure stored value reflects the fact passthrough is unsupported so that
            // audio-speed UI downstream does not treat it as active.
            if (!"0".equals(mSharedPreferences.getString("force_audio_passthrough_multiple", "0"))) {
                mSharedPreferences.edit().putString("force_audio_passthrough_multiple", "0").apply();
            }
            mPlaybackSpeed.setEnabled(true);
            mPlaybackSpeed.setSelectable(true);
            updateAudioDecoderChoiceState(false);
            // Audio speed audiotrack is selectable when playback speed is selectable
            mAudioSpeedAudiotrack.setSelectable(true);
            // Disable downmix is selectable when passthrough is not supported
            mDisableDownmix.setSelectable(true);
            // Frame timing can still affect dynamic audio delay even without passthrough
            updateDynamicAudioDelayState(false, frameTimingEnabled);
        }

        mStreamBufferSize = (EditTextPreference) findPreference(KEY_STREAM_BUFFER_SIZE);
        mStreamMaxIFrameSize = (EditTextPreference) findPreference(KEY_STREAM_MAX_IFRAME_SIZE);
        mActivate3DTVSwitch = (CheckBoxPreference) findPreference(KEY_ACTIVATE_3D_SWITCH);
        mEnableCutoutModeShortEdge = (CheckBoxPreference) findPreference("enable_cutout_mode_short_edges");
        mEnableCutBothSidesX = (CheckBoxPreference) findPreference("enable_cutout_both_sidesx");

        mActivateRefreshrateTVSwitch = (ListPreference) findPreference(KEY_ACTIVATE_REFRESHRATE_SWITCH);
        // last option "match frame rate" is only available for android 12+, remove it on old android versions
        if (Build.VERSION.SDK_INT < 31) {
            CharSequence[] entries = mActivateRefreshrateTVSwitch.getEntries();
            CharSequence[] entryValues = mActivateRefreshrateTVSwitch.getEntryValues();
            CharSequence[] newEntries = new CharSequence[entries.length - 1];
            CharSequence[] newEntryValues = new CharSequence[entryValues.length - 1];
            for (int i = 0; i < entries.length - 1; i++) {
                newEntries[i] = entries[i];
                newEntryValues[i] = entryValues[i];
            }
            mActivateRefreshrateTVSwitch.setEntries(newEntries);
            mActivateRefreshrateTVSwitch.setEntryValues(newEntryValues);
        }

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
        mSmbDisableTcpDiscovery = (CheckBoxPreference) findPreference(KEY_SMB_DISABLE_TCP_DISCOVERY);
        mSmbDisableUdpDiscovery = (CheckBoxPreference) findPreference(KEY_SMB_DISABLE_UDP_DISCOVERY);
        mSmbDisableMdnsDiscovery = (CheckBoxPreference) findPreference(KEY_SMB_DISABLE_MDNS_DISCOVERY);
        mSmbj = (CheckBoxPreference) findPreference(KEY_SMBJ);

        mScraperCategory = (PreferenceCategory) findPreference(KEY_SCRAPER_CATEGORY);
        mExportManualPreference = findPreference(getString(R.string.nfo_export_manual_prefkey));
        mExportManualPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AutoScrapeService.EXPORT_EVERYTHING, null, getActivity(), AutoScrapeService.class);
            getContext().startService(intent);
            Toast.makeText(getActivity(), R.string.nfo_export_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference("hide_watched").setOnPreferenceChangeListener((preference, newValue) -> {
            LoaderUtils.mMustHideWatchedVideo =  (boolean) newValue;
            getActivity().setResult(ACTIVITY_RESULT_UI_MODE_CHANGED); // way to tell the MainActivity that an important preference has been changed
            getActivity().finish();
            return true;
        });

        findPreference(getString(R.string.rescrap_all_prefkey)).setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AutoScrapeService.RESCAN_EVERYTHING, null, getActivity(), AutoScrapeService.class);
            intent.putExtra(AutoScrapeService.RESCAN_EVERYTHING, true);
            intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, false);
            getContext().startService(intent);
            Toast.makeText(getActivity(), R.string.rescrap_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(getString(R.string.rescrap_all_movies_prefkey)).setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AutoScrapeService.RESCAN_MOVIES, null, getActivity(), AutoScrapeService.class);
            intent.putExtra(AutoScrapeService.RESCAN_ONLY_DESC_NOT_FOUND, false);
            getContext().startService(intent);
            Toast.makeText(getActivity(), R.string.rescrap_movies_in_progress, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(getString(R.string.rescrap_all_collections_prefkey)).setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(AllCollectionScrapeService.INTENT_RESCRAPE_ALL_COLLECTIONS, null, getActivity(), AllCollectionScrapeService.class);
            getContext().startService(intent);
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

        mSmbDisableTcpDiscovery.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getActivity(), preference.getKey() + "=" + newValue.toString(), Toast.LENGTH_SHORT).show();
            SambaDiscovery sambaDiscovery = CustomApplication.getSambaDiscovery();
            if (sambaDiscovery != null) sambaDiscovery.notifyPrefChange();
            return true;
        });

        mSmbDisableUdpDiscovery.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getActivity(), preference.getKey() + "=" + newValue.toString(), Toast.LENGTH_SHORT).show();
            SambaDiscovery sambaDiscovery = CustomApplication.getSambaDiscovery();
            if (sambaDiscovery != null) sambaDiscovery.notifyPrefChange();
            return true;
        });

        mSmbDisableMdnsDiscovery.setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getActivity(), preference.getKey() + "=" + newValue.toString(), Toast.LENGTH_SHORT).show();
            SambaDiscovery sambaDiscovery = CustomApplication.getSambaDiscovery();
            if (sambaDiscovery != null) sambaDiscovery.notifyPrefChange();
            return true;
        });

        // disable jcifs-ng options if smbj SMB implementation is selected
        boolean smbjChecked = mSmbj.isChecked();
        mSmb2.setEnabled(!smbjChecked);
        mSmb2.setSelectable(!smbjChecked);
        mSmbj.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean newSmbjChecked = (boolean)newValue;
            mSmb2.setEnabled(!newSmbjChecked);
            mSmb2.setSelectable(!newSmbjChecked);
            // do not disable SMB resolver since jcifs-ng is used for address resolution even with smbj
            //mSmbResolver.setEnabled(!(boolean)newValue);
            return true;
        });

        mDbExportManualPreference = findPreference(getString(R.string.db_export_manual_prefkey));
        mDbExportManualPreference.setOnPreferenceClickListener(preference -> {
            if (LoaderUtils.getScrapeInProgress()) {
                //Stop the scrape.
                LoaderUtils.setScrapeInProgress(false);
            }

            backupDatabase(getContext(),"media.db");
            Toast.makeText(getActivity(), R.string.db_export_in_progress, Toast.LENGTH_SHORT).show();
             return true;
        });

        Preference exportLibraryPreference = findPreference(getString(R.string.media_library_export_prefkey));
        exportLibraryPreference.setOnPreferenceClickListener(preference -> {
            if (LoaderUtils.getScrapeInProgress()) {
                //Stop the scrape.
                LoaderUtils.setScrapeInProgress(false);
            }
            Toast.makeText(getActivity(), R.string.media_library_export_in_progress, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MediaLibraryBackupService.ACTION_EXPORT, null, getActivity(), MediaLibraryBackupService.class);           
            getContext().startService(intent);
            return true;
        });

        Preference importLibraryPreference = findPreference(getString(R.string.media_library_import_prefkey));
        importLibraryPreference.setOnPreferenceClickListener(preference -> {
            showImportDialog();
            return true;
        });

        findPreference(KEY_RESCAN_STORAGE).setOnPreferenceClickListener(preference -> {
            if (LoaderUtils.getScrapeInProgress()) {
                //Stop the scrape.
                LoaderUtils.setScrapeInProgress(false);
            }

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

        buildLanguageList(OPENSUBTITLES_LANGUAGES, OpensubtitlesLanguageListEntries, OpensubtitlesLanguageListEntryValues);
        OpensubtitlesSystemLanguageIndex = findLanguageIndex(OpensubtitlesLanguageListEntryValues, getPreferenceManager().getSharedPreferences().getString(KEY_SUBTITLES_FAV_LANG, Locale.getDefault().getLanguage()));
        mSubtitlesDownloadLanguagePreferences = (MultiSelectListPreference) findPreference("languages_list");
        CharSequence[] listEntries = OpensubtitlesLanguageListEntries.toArray(new CharSequence[0]);
        CharSequence[] listEntriesValues = OpensubtitlesLanguageListEntryValues.toArray(new CharSequence[0]);
        mSubtitlesDownloadLanguagePreferences.setEntries(listEntries);
        mSubtitlesDownloadLanguagePreferences.setEntryValues(listEntriesValues);
        mSubtitlesDownloadLanguagePreferences.setDefaultValue(new HashSet<>(List.of(Locale.getDefault().getLanguage())));
        Set<String> currentValues = mSubtitlesDownloadLanguagePreferences.getValues();
        if (currentValues.isEmpty()) { // enforce at least locale on subtitles download
            Set<String> newValues = new HashSet<>(currentValues);
            newValues.add(Locale.getDefault().getLanguage());
            mSubtitlesDownloadLanguagePreferences.setValues(newValues);
            if (log.isDebugEnabled()) log.debug("onCreatePreferences: getValues {}", mSubtitlesDownloadLanguagePreferences.getValues());
        }
        mSubtitlesFavLangPreferences.setEntries(listEntries);
        mSubtitlesFavLangPreferences.setEntryValues(listEntriesValues);
        if (OpensubtitlesSystemLanguageIndex>=0) mSubtitlesFavLangPreferences.setValueIndex(OpensubtitlesSystemLanguageIndex);

        buildLanguageList(TMDB_LANGUAGES, TMDbLanguageListEntries, TMDbLanguageListEntryValues);
        mTMDbScraperLanguagePreferences = (ListPreference) findPreference(KEY_SCRAPER_FAV_LANG);
        listEntries = TMDbLanguageListEntries.toArray(new CharSequence[0]);
        listEntriesValues = TMDbLanguageListEntryValues.toArray(new CharSequence[0]);
        mTMDbScraperLanguagePreferences.setEntries(listEntries);
        mTMDbScraperLanguagePreferences.setEntryValues(listEntriesValues);
        TMDbSystemLanguageIndex = findLanguageIndex(TMDbLanguageListEntryValues, getPreferenceManager().getSharedPreferences().getString(KEY_SCRAPER_FAV_LANG, Locale.getDefault().getLanguage()));
        if (TMDbSystemLanguageIndex>=0) mTMDbScraperLanguagePreferences.setValueIndex(TMDbSystemLanguageIndex);

        // TOFIX: reuse the OpensubtitlesLanguageList for mAudioTrackFavoriteLanguage
        mAudioTrackFavoriteLanguage = (ListPreference) findPreference(KEY_AUDIO_TRACK_FAV_LANG);
        listEntries = OpensubtitlesLanguageListEntries.toArray(new CharSequence[0]);
        listEntriesValues = OpensubtitlesLanguageListEntryValues.toArray(new CharSequence[0]);
        mAudioTrackFavoriteLanguage.setEntries(listEntries);
        mAudioTrackFavoriteLanguage.setEntryValues(listEntriesValues);
        systemAudioLanguageIndex = findLanguageIndex(OpensubtitlesLanguageListEntryValues, getPreferenceManager().getSharedPreferences().getString(KEY_AUDIO_TRACK_FAV_LANG, Locale.getDefault().getLanguage()));
        if (systemAudioLanguageIndex>=0) mAudioTrackFavoriteLanguage.setValueIndex(systemAudioLanguageIndex);

        mUiLang = (ListPreference) findPreference(KEY_UI_LANG);
        buildUILanguageList(UI_LANGUAGES, UiLanguageListEntries, UiLanguageListEntryValues);
        // Set entries and entry values for the ListPreference
        mUiLang.setEntries(UiLanguageListEntries.toArray(new CharSequence[0]));
        mUiLang.setEntryValues(UiLanguageListEntryValues.toArray(new CharSequence[0]));

        // set default value of the ListPreference to the System first entry
        UiSystemLanguageIndex = findLanguageIndex(UiLanguageListEntryValues, getPreferenceManager().getSharedPreferences().getString(KEY_UI_LANG, KEY_UI_LANG_SYSTEM));
        if (UiSystemLanguageIndex>=0) mUiLang.setValueIndex(UiSystemLanguageIndex);
        if (log.isDebugEnabled()) log.debug("onCreatePreferences: mUiLang UiSystemLanguageIndex {}", UiSystemLanguageIndex);

        // Update summary to reflect the current setting
        String selectedLocale = getPreferenceManager().getSharedPreferences().getString(KEY_UI_LANG, KEY_UI_LANG_SYSTEM);
        if (log.isDebugEnabled()) log.debug("onCreatePreferences: mUiLang selectedLocale {}", selectedLocale);
        mUiLang.setSummary(getLocaleDisplayName(selectedLocale));
        mUiLang.setOnPreferenceChangeListener((preference, newValue) -> {
            String newLocale = (String) newValue;
            // modify mUiLang summary to concatenate getLocaleDisplayName(newLocale)
            mUiLang.setSummary(getLocaleDisplayName(newLocale));
            if (log.isDebugEnabled()) log.debug("onCreatePreferences: mUiLang newLocale {}", newLocale);
            CustomApplication.setLocale(newLocale, getResources());
            // commit all the settings changes before restarting the activity
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            editor.putString(KEY_UI_LANG, newLocale);
            editor.commit();
            // TODO MARC BUG: does not change the title string.preferences of preferences_video.xml but all the rest is ok
            restartActivity(); // not enough to clear all the cached fragments
            //restartApplication(); // not enough when returning to settings
            //getActivity().recreate();
            return true;
        });

        ListPreference lp = (ListPreference) findPreference("codepage");
        int cp = MediaFactory.getCodepage();
        int cpStringID = getResources().getIdentifier("codepage_extra_" + cp, "string", getActivity().getPackageName());
        if (cpStringID == 0)
            cpStringID = R.string.codepage_extra_1252;
        CharSequence [] entryArray = lp.getEntries();
        entryArray[0] = getResources().getString(R.string.codepage_default,getResources().getString(cpStringID));
        lp.setEntries(entryArray);

        mHanlder = new Handler(Looper.getMainLooper());
        mTraktSigninPreference = (TraktSigninDialogPreference) findPreference(KEY_TRAKT_SIGNIN);
        if (mTraktSigninPreference != null) mTraktSigninPreference.setLauncher(mTraktAuthLauncher);
        if(mTraktSigninPreference!= null && savedInstanceState!=null) {
            if (log.isDebugEnabled()) log.debug("onCreatePreferences: closing mTraktSigninPreference dialog to prevent leaked window");
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
        mTraktForcePushPreference = findPreference("trakt_force_push");
        mTraktForcePullPreference = findPreference("trakt_force_pull");
        if (mTraktForcePushPreference != null) {
            mTraktForcePushPreference.setOnPreferenceClickListener(preference -> {
                if (Trakt.getAccessTokenFromPreferences(mSharedPreferences) == null) return false;
                new TraktService.Client(getActivity(), null, false).forcePush();
                return true;
            });
        }
        if (mTraktForcePullPreference != null) {
            mTraktForcePullPreference.setOnPreferenceClickListener(preference -> {
                if (Trakt.getAccessTokenFromPreferences(mSharedPreferences) == null) return false;
                new TraktService.Client(getActivity(), null, false).forcePull();
                return true;
            });
        }

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
            // Leanback device case: keep category but remove phone-specific UI preferences
            if (getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                // Remove phone-specific preferences on leanback devices
                Preference uimodePref = findPreference("uimode");
                if (uimodePref != null) userInterfaceCategory.removePreference(uimodePref);

                // Show uimode_leanback selector ONLY when in Classic mode (so user can switch to TV mode)
                // Hide it when in Leanback mode (user is already in TV mode)
                Preference uimodeLeanbackPref = findPreference("uimode_leanback");
                if (uimodeLeanbackPref != null) {
                    if (UiChoiceDialog.applicationIsInLeanbackMode(getActivity())) {
                        // In TV mode: hide the selector, user can use "Always Start in TV Interface" instead
                        userInterfaceCategory.removePreference(uimodeLeanbackPref);
                    } else {
                        // In Classic mode: show selector so user can switch back to TV mode
                        uimodeLeanbackPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                            public boolean onPreferenceChange(Preference preference, Object newValue) {
                                // Sync with always_leanback_on_tv_key and uimode on Android TV devices
                                String uiMode = (String) newValue;
                                boolean isLeanbackMode = UiChoiceDialog.UI_CHOICE_LEANBACK_TV_VALUE.equals(uiMode);
                                String uimodeValue = isLeanbackMode ? "2" : "1";
                                getPreferenceManager().getSharedPreferences().edit()
                                    .putBoolean("always_leanback_on_tv_key", isLeanbackMode)
                                    .putString("uimode", uimodeValue)
                                    .apply();
                                getActivity().setResult(ACTIVITY_RESULT_UI_MODE_CHANGED);
                                getActivity().finish();
                                return true;
                            }
                        });
                    }
                }

                Preference uiZoomPref = findPreference("ui_zoom");
                if (uiZoomPref != null) userInterfaceCategory.removePreference(uiZoomPref);
                Preference displayResumeBoxPref = findPreference("display_resume_box");
                if (displayResumeBoxPref != null) userInterfaceCategory.removePreference(displayResumeBoxPref);
                Preference resetBrightnessPref = findPreference(getString(R.string.reset_brightness_on_start_key));
                if (resetBrightnessPref != null) userInterfaceCategory.removePreference(resetBrightnessPref);
                // Note: smart_recently_rows is kept visible as it applies to both phone and leanback
            }
            // Not a leanback device, but if the APK integrates leanback the user can choose to use it
            else {
                Preference uimodePref = findPreference("uimode");
                if (uimodePref != null) userInterfaceCategory.removePreference(uimodePref); // remove the old uimode settings
                Preference uimodeLeanbackPref = findPreference("uimode_leanback");
                if (uimodeLeanbackPref != null) {
                    uimodeLeanbackPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                            // Sync with uimode preference (phones/tablets don't use always_leanback_on_tv_key)
                            String uiMode = (String) newValue;
                            boolean isLeanbackMode = UiChoiceDialog.UI_CHOICE_LEANBACK_TV_VALUE.equals(uiMode);
                            String uimodeValue = isLeanbackMode ? "2" : "1";
                            getPreferenceManager().getSharedPreferences().edit()
                                .putString("uimode", uimodeValue)
                                .apply();
                            getActivity().setResult(ACTIVITY_RESULT_UI_MODE_CHANGED);
                            getActivity().finish();
                            return true;
                        }
                    });
                }
                Preference uiZoomPref = findPreference("ui_zoom");
                if (uiZoomPref != null) {
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

                 //Last Played Section on Phone UI
                Preference resetLastPlayedPref = findPreference("reset_last_played_section");
                if (resetLastPlayedPref != null) {
                    resetLastPlayedPref.setOnPreferenceClickListener(preference -> {
                        //Show the toast BEFORE starting the actions!
                        Toast.makeText(getActivity(), R.string.reset_last_played_row_in_progress, Toast.LENGTH_SHORT).show();
                        DbUtils.markAsNotRead(getActivity());
                        return true;
                    });
                }
            }
        }
        
        PreferenceCategory leanbackUserInterfaceCategory = (PreferenceCategory)findPreference("category_leanback_user_interface");

        if (leanbackUserInterfaceCategory != null) {
            if (!UiChoiceDialog.applicationIsInLeanbackMode(getActivity())) {
                getPreferenceScreen().removePreference(leanbackUserInterfaceCategory);
            }
            else {
                // Hide "Always Start in TV Interface" on non-TV devices (it's only relevant on Android TV)
                if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                    Preference alwaysLeanbackPref = findPreference("always_leanback_on_tv_key");
                    if (alwaysLeanbackPref != null) leanbackUserInterfaceCategory.removePreference(alwaysLeanbackPref);
                } else {
                    // On Android TV: sync "Always Start in TV Interface" with uimode/uimode_leanback
                    Preference alwaysLeanbackPref = findPreference("always_leanback_on_tv_key");
                    if (alwaysLeanbackPref != null) {
                        alwaysLeanbackPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                            public boolean onPreferenceChange(Preference preference, Object newValue) {
                                boolean enabled = (Boolean) newValue;
                                // Sync with uimode_leanback and uimode preferences
                                String modeValue = enabled ? UiChoiceDialog.UI_CHOICE_LEANBACK_TV_VALUE : UiChoiceDialog.UI_CHOICE_LEANBACK_TABLET_VALUE;
                                String uimodeValue = enabled ? "2" : "1";
                                getPreferenceManager().getSharedPreferences().edit()
                                    .putString(UiChoiceDialog.UI_CHOICE_LEANBACK_KEY, modeValue)
                                    .putString("uimode", uimodeValue)
                                    .apply();
                                // Don't restart - let change take effect on next app launch
                                return true;
                            }
                        });
                    }
                }

                //Last Played section on Leanback.
                findPreference("reset_last_played_row").setOnPreferenceClickListener(preference -> {
                    //Show the toast BEFORE starting the actions!
                    Toast.makeText(getActivity(), R.string.reset_last_played_row_in_progress, Toast.LENGTH_SHORT).show();
                    DbUtils.markAsNotRead(getActivity());
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

    private static String getDolbyVisionModeValue(boolean isDisabled) {
        return isDisabled ? "off" : "auto";
    }

    private void migrateDolbyVisionPreference(SharedPreferences sharedPreferences) {
        if (sharedPreferences.contains(KEY_DOLBY_VISION_MODE) || !sharedPreferences.contains(KEY_DISABLE_DOLBY_VISION)) {
            return;
        }

        String doViModeValue = getDolbyVisionModeValue(sharedPreferences.getBoolean(KEY_DISABLE_DOLBY_VISION, false));
        sharedPreferences.edit().putString(KEY_DOLBY_VISION_MODE, doViModeValue).apply();
    }

    private int findLanguageIndex(List<String> languageEntryValues, String language) {
        for (int i = 0; i < languageEntryValues.size(); i++) {
            if (languageEntryValues.get(i).equalsIgnoreCase(language)) {
                return i;
            }
        }
        return -1;
    }

    private void buildLanguageList(String languages, List<String> languageEntries, List<String> languageEntryValues) {
        String[] languageCodeArray = languages.split("\\|"); // contains 2 letters language code

        Locale defaultLocale = Locale.getDefault();
        String defaultLocaleLanguage = defaultLocale.getDisplayLanguage();
        Locale englishLocale = Locale.forLanguageTag("en");
        String englishLocaleLanguage = englishLocale.getDisplayLanguage();

        // Add default system language first
        languageEntries.add(defaultLocaleLanguage);
        languageEntryValues.add(defaultLocale.getLanguage());

        // If default system language is not English, add English second
        if (!defaultLocaleLanguage.equalsIgnoreCase(englishLocaleLanguage)) {
            languageEntries.add(englishLocaleLanguage);
            languageEntryValues.add(englishLocale.getLanguage());
        }

        // Use a TreeMap to keep the languages sorted as they are added
        TreeMap<String, String> sortedLanguages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (String s : languageCodeArray) {
            String currentLocaleLanguage = com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.getLanguageNameFor2LetterCode(getActivity(), s);
            if (currentLocaleLanguage.equalsIgnoreCase(defaultLocaleLanguage) || currentLocaleLanguage.equalsIgnoreCase(englishLocaleLanguage))
                continue;
            sortedLanguages.put(currentLocaleLanguage, s);
        }

        // Add the sorted languages to the listEntries and listEntryValues
        languageEntries.addAll(sortedLanguages.keySet());
        languageEntryValues.addAll(sortedLanguages.values());
    }

    private void buildUILanguageList(String languages, List<String> languageEntries, List<String> languageEntryValues) {
        String[] languageCodeArray = languages.split("\\|");

        // Add "System" entry first
        languageEntries.add(getResources().getString(R.string.system));
        languageEntryValues.add(KEY_UI_LANG_SYSTEM);

        // Use a TreeMap to keep the languages sorted as they are added
        TreeMap<String, String> sortedLanguages = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (String s : languageCodeArray) {
            Locale locale = getLocaleFromCode(s);
            String currentLocaleLanguage = locale.getDisplayName(locale);
            sortedLanguages.put(currentLocaleLanguage, s);
        }

        // Add the sorted languages to the listEntries and listEntryValues
        languageEntries.addAll(sortedLanguages.keySet());
        languageEntryValues.addAll(sortedLanguages.values());
    }

    public static Locale getLocaleFromCode(String code) {
        //log.trace("getLocaleFromCode: code {}", code);
        if (code == null || code.isEmpty()) return Locale.getDefault();
        // forLanguageTag requires BCP-47 format with '-' separator; stored values may use '_'
        Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
        // forLanguageTag returns an empty Locale (not null) for unrecognised tags
        if (locale.toLanguageTag().equals("und")) return Locale.getDefault();
        return locale;
    }

    private String getLocaleDisplayName(String localeCode) {
        if (localeCode.equalsIgnoreCase(KEY_UI_LANG_SYSTEM)) {
            return getResources().getString(R.string.system);
        }
        Locale locale = getLocaleFromCode(localeCode);
        return locale.getDisplayName(locale);
    }

    // TODO MARC remove unused

    private void restartActivity() {
        //Intent intent = getActivity().getIntent();
        //getActivity().finish();
        //startActivity(intent);
        getActivity().recreate(); // other way
    }

    private void restartApplication() {
        Intent intent = new Intent(getActivity(), getActivity().getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getActivity().startActivity(intent);
        getActivity().finishAffinity(); // Use finishAffinity to finish this and all activities below it in the stack.
        System.exit(0); // Optionally use this to ensure the app process is fully restarted.
    }

    private static boolean DBG = false;

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
        mTraktWipePreference.setSelectable(enabled);
        mTraktLiveScrobblingPreference.setEnabled(enabled);
        mTraktLiveScrobblingPreference.setSelectable(enabled);
        if (mTraktSyncCollectionPreference != null) {
            mTraktSyncCollectionPreference.setEnabled(false);
            mTraktSyncCollectionPreference.setSelectable(false);
            mTraktSyncCollectionPreference.setVisible(false);
        }
        // Keep bookmark/resume sync available even if live scrobbling is disabled
        mTraktSyncProgressPreference.setEnabled(enabled);
        mTraktSyncProgressPreference.setSelectable(enabled);
        if (mTraktForcePushPreference != null) {
            mTraktForcePushPreference.setEnabled(enabled);
            mTraktForcePushPreference.setSelectable(enabled);
        }
        if (mTraktForcePullPreference != null) {
            mTraktForcePullPreference.setEnabled(enabled);
            mTraktForcePullPreference.setSelectable(enabled);
        }
        mTraktSigninPreference.setEnabled(!enabled);
        mTraktSigninPreference.setSelectable(!enabled);
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
            if (mTraktSyncCollectionPreference != null) {
                mTraktSyncCollectionPreference.setChecked(TRAKT_SYNC_COLLECTION_DEFAULT);
            }
            if (mTraktLiveScrobblingPreference != null) {
                mTraktLiveScrobblingPreference.setChecked(TRAKT_LIVE_SCROBBLING_DEFAULT);
            }
            setTraktPreferencesEnabled(false);
        }
        mLastTraktUser = traktUser;

        return traktUser != null;
    }


    private void showImportDialog() {
        File exportDir = getContext().getExternalFilesDir(null);
        if (exportDir == null || !exportDir.exists()) {
            Toast.makeText(getActivity(), R.string.media_library_export_dir_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        File backupFile = new File(exportDir, "backup.zip");
        if (!backupFile.exists()) {
            Toast.makeText(getActivity(), R.string.media_library_no_backup_found, Toast.LENGTH_LONG).show();
            return;
        }

        // Show backup file info and proceed to confirmation
        long fileSizeMB = backupFile.length() / (1024 * 1024);
        String message = getContext().getString(R.string.media_library_backup_info, fileSizeMB);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.media_library_import_dialog_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    confirmImport(backupFile.getAbsolutePath());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmImport(String importFilePath) {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.media_library_import_confirm_title)
                .setMessage(R.string.media_library_import_warning)
                .setPositiveButton(R.string.media_library_import_button_confirm, (dialog, which) -> {
                    Intent intent = new Intent(MediaLibraryBackupService.ACTION_IMPORT, null, getActivity(), MediaLibraryBackupService.class);
                    intent.putExtra(MediaLibraryBackupService.EXTRA_IMPORT_FILE, importFilePath);
                    getContext().startService(intent);
                    Toast.makeText(getActivity(), R.string.media_library_import_in_progress, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(true)
                .show();
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
        } else if (key.equals(KEY_APP_THEME)) {
            // Theme changed - set result code and restart activity to apply new theme
            Activity activity = getActivity();
            if (activity != null) {
                activity.setResult(ACTIVITY_RESULT_THEME_CHANGED);

                // Check if this is a leanback settings activity
                boolean isLeanbackSettings = (activity instanceof com.archos.mediacenter.video.leanback.settings.VideoSettingsActivity) ||
                                              (activity instanceof com.archos.mediacenter.video.leanback.settings.VideoSettingsMoreLeanbackActivity) ||
                                              (activity instanceof com.archos.mediacenter.video.leanback.settings.VideoSettingsLicencesActivity);

                if (isLeanbackSettings) {
                    // Recreate settings activity so theme changes immediately
                    activity.recreate();
                } else if (!(activity instanceof LeanbackActivity)) {
                    // Phone mode: recreate to apply theme
                    activity.recreate();
                }
            }
        } else if (key.equals("force_audio_passthrough_multiple") || key.equals(KEY_FORCE_AUDIO_PASSTHROUGH)) {
            boolean passthroughEnabled = !"0".equals(sharedPreferences.getString("force_audio_passthrough_multiple", "0"));
            updateAudioDecoderChoiceState(passthroughEnabled);
            boolean frameTimingEnabled = sharedPreferences.getBoolean("enable_android_frame_timing", false);
            updateDynamicAudioDelayState(passthroughEnabled, frameTimingEnabled);
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
