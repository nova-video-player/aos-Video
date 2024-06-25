// Copyright 2023 Courville Software
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
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import com.archos.environment.ArchosUtils;
import com.archos.environment.NetworkState;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.MetaFileFactoryWithUpnp;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediacenter.video.ui.NovaProgressDialog;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;


import static com.archos.filecorelibrary.FileUtils.removeFileSlashSlash;

public class SubtitlesDownloaderActivity2 extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(SubtitlesDownloaderActivity2.class);

    public static final String FILE_URL = "fileUrl";
    public static final String FILE_NAME = "fileName"; //friendly name for Upnp
    public static final String FILE_SIZE = "fileSize";

    //to distinguished program dismiss and users
    private boolean mDoNotFinish;
    private SharedPreferences sharedPreferences;
    private File subsDir;
    Handler mHandler;
    Long mFileSize = null;
    String mFriendlyFileName = null; // they need to have an extension

    private NovaProgressDialog mDialog;

    private OpenSubtitlesTask mOpenSubtitlesTask = null;

    private static class NonConfigurationInstance {
        public NovaProgressDialog progressDialog;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public void onStart() {
        super.onStart();
        log.debug("onStart");
        mHandler = new Handler(getMainLooper());
        final NonConfigurationInstance nci = (NonConfigurationInstance) getLastNonConfigurationInstance();
        subsDir = MediaUtils.getSubsDir(this);
        if (nci != null) {
            // The activity is created again after a rotation => just restore the state of the dialogs
            // as the OpenSubtitlesTask is still running in the background
            mDialog = nci.progressDialog;
        }  else {
            // Normal start of the activity
            if(NetworkState.isNetworkConnected(this)){
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                final Intent intent = getIntent();
                String fileUrl = null;
                if (intent.hasExtra(FILE_URL)){
                    fileUrl = intent.getStringExtra(FILE_URL);
                } else {
                    finish();
                    return;
                }
                if (intent.hasExtra(FILE_SIZE))
                    mFileSize = intent.getLongExtra(FILE_SIZE, 0);
                else
                    mFileSize = null;
                if (intent.hasExtra(FILE_NAME))
                    mFriendlyFileName = intent.getStringExtra(FILE_NAME);
                else
                    mFriendlyFileName = null;
                mOpenSubtitlesTask = new OpenSubtitlesTask();
                ArrayList<String> fileUrls = new ArrayList<>();
                fileUrls.add(fileUrl);
                mOpenSubtitlesTask.execute(fileUrls, getSubLangValue());
            } else {
                log.debug("onStart: no network");
                Builder dialogNoNetwork;
                dialogNoNetwork = new AlertDialog.Builder(this);
                dialogNoNetwork.setCancelable(true);
                dialogNoNetwork.setOnCancelListener(dialog ->
                        finish()
                );
                dialogNoNetwork.setTitle(R.string.dialog_subloader_nonetwork_title);
                dialogNoNetwork.setMessage(getString(R.string.dialog_subloader_nonetwork_message));
                Dialog d = dialogNoNetwork.create();
                d.setOnDismissListener(dialog -> {
                    if (!mDoNotFinish)
                        finish();
                    mDoNotFinish = false;
                });
                d.show();
            }
        }
    }

    @Override
    public void onStop() {
        log.debug("onStop");
        if (mOpenSubtitlesTask != null) {
            log.debug("mOpenSubtitlesTask.cancel");
            mOpenSubtitlesTask.cancel(false);
            mOpenSubtitlesTask = null;
        }
        logOut();
        closeDialog();
        finish();
        super.onStop();
    }

    public void onResume(){
        super.onResume();
        // Check if there are some dialogs to restore after a device rotation
        // or after the backlight was turned off and on
        if (mDialog != null) {
            // The results dialog was not visible but the progress dialog was => show the progress dialog
            mDialog.show();
        }
        TorrentObserverService.resumed(this);
    }

    public void onPause(){
        super.onPause();
        closeDialog();
        TorrentObserverService.paused(this);
    }

    private ArrayList<String> getSubLangValue() {
        Set<String> existingLanguages = new HashSet<>(sharedPreferences.getStringSet("languages_list", new HashSet<>()));
        if (existingLanguages.isEmpty()) {
            // if no language is set, add default locale at least
            String defaultLanguage = Locale.getDefault().getLanguage();
            // if defaultLanguage is not equal ignoring case zh-cn or zh-tw replace by zh-cn since zh-cn and zh-tw are the only two known by opensubtitles
            if (defaultLanguage.toLowerCase().startsWith("zh") && !defaultLanguage.equalsIgnoreCase("zh-cn") && !defaultLanguage.equalsIgnoreCase("zh-tw")) {
                log.warn("getSubLangValue: curing defaultLanguage=" + defaultLanguage + " to zh-cn");
                defaultLanguage = "zh-cn";  // Simplified Chinese
            }
            existingLanguages.add(defaultLanguage);
            sharedPreferences.edit().putStringSet("languages_list", existingLanguages).apply();
        }
        // replace all strings from existingLanguages starting with zh and not equal to zh-cn or zh-tw by zh-cn since zh-cn and zh-tw are the only two known by opensubtitles
        Set<String> toRemove = new HashSet<>();
        Set<String> toAdd = new HashSet<>();
        boolean modifiedList = false;
        for (String lang : existingLanguages) {
            if (lang.toLowerCase().startsWith("zh") && !lang.equalsIgnoreCase("zh-cn") && !lang.equalsIgnoreCase("zh-tw")) {
                toRemove.add(lang);
                toAdd.add("zh-cn");
                modifiedList = true;
            }
        }
        if (modifiedList) {
            log.warn("getSubLangValue: curing subsFavLang modifiedList: toRemove=" + toRemove + ", toAdd=" + toAdd);
            existingLanguages.removeAll(toRemove);
            existingLanguages.addAll(toAdd);
            sharedPreferences.edit().putStringSet("languages_list", existingLanguages).apply();
        }
        ArrayList<String> languageList = new ArrayList<>(existingLanguages);
        log.debug("getSubLangValue: langDefault=" + languageList);
        return languageList;
    }

    private class OpenSubtitlesTask extends AsyncTask<ArrayList<String>, Integer, Void>{
        ArrayList<OpenSubtitlesSearchResult> searchResults = null;
        @Override
        protected void onPreExecute() {
            log.debug("OpenSubtitlesTask: onPreExecute");
            setInitDialog();
        }
        @Override
        protected Void doInBackground(ArrayList<String>... params) {
            String fileUrl =  params[0].get(0);
            ArrayList<String> languages = params[1];
            if (logIn()){
                getSubtitle(fileUrl, languages);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            // Close the progress dialog
            if (mDialog != null) {
                mDoNotFinish = mDoNotFinish &&
                        searchResults != null &&
                        !searchResults.isEmpty() &&
                        (OpenSubtitlesApiHelper.getLastQueryResult() == OpenSubtitlesApiHelper.RESULT_CODE_OK);
                log.debug("OpenSubtitlesTask: onPostExecute: mDoNotFinish=" + mDoNotFinish);
                if (searchResults != null) log.debug("OpenSubtitlesTask: onPostExecute: found " +  searchResults.size() + " subs");
                else log.debug("OpenSubtitlesTask: onPostExecute: searchResults=null");
                mDialog.dismiss();
            }
        }

        /**************************************************
         *        OpenSubtitles framework
         *************************************************/
        @SuppressWarnings("unchecked")
        public boolean logIn() {
            SharedPreferences mPreferences = getApplicationContext().getSharedPreferences("opensubtitles_credentials", Context.MODE_PRIVATE);
            String mUsername = mPreferences.getString(OpenSubtitlesCredentialsDialog.OPENSUBTITLES_USERNAME, "");
            String mPassword = mPreferences.getString(OpenSubtitlesCredentialsDialog.OPENSUBTITLES_PASSWORD, "");

            try {
                if (mUsername.isEmpty() || mPassword.isEmpty()) {
                    displayToast(getString(R.string.toast_subloader_credentials_empty));
                }
                OpenSubtitlesApiHelper.login(getApplicationContext().getString(R.string.opensubtitles_api_key), mUsername, mPassword);
            } catch (IOException e) {
                log.warn("logIn error message: result=" + OpenSubtitlesApiHelper.getLastQueryResult() + " message:" + e.getMessage() + "; localizedMessage:" + e.getLocalizedMessage() + ", cause: " + e.getCause());
                displayToast(getString(R.string.toast_subloader_login_failed) + " (ERR " + OpenSubtitlesApiHelper.getLastQueryResult() + ")");
                closeDialog();
                return false;
            } catch (Throwable e) { //for various service outages
                log.error("logIn: caught exception result=" + OpenSubtitlesApiHelper.getLastQueryResult(),e);
                displayToast(getString(R.string.toast_subloader_service_unreachable) + " (ERR " + OpenSubtitlesApiHelper.getLastQueryResult() + ")");
                closeDialog();
                return false;
            }
            return true;
        }

        /**
         * returns name WITHOUT extension
         * @param fileUrl
         * @return
         */
        public String getFriendlyFilename(String fileUrl){
            if (mFriendlyFileName != null) return mFriendlyFileName;
            else return FileUtils.getFileNameWithoutExtension(Uri.parse(fileUrl));
        }

        public void getSubtitle(final String fileUrl, final ArrayList<String> languages) {
            log.debug("getSubtitle: fileUrl " +  fileUrl + ", language=" + String.join(",", languages));
            if (fileUrl == null || fileUrl.isEmpty() || languages == null || languages.isEmpty()){
                return;
            }
            mDoNotFinish = true;
            // REST-API takes ISO639-1 2 letter code languages: no need to convert
            ArrayList<String> subLanguageId = new ArrayList<String>(languages);
            String languagesString = TextUtils.join(",", subLanguageId);
            OpenSubtitlesQueryParams fileInfo = getFileInfo(fileUrl);
            if (fileInfo != null) log.debug("getSubtitle: tmdbId=" + fileInfo.getTmdbId() + ", imdbId=" + fileInfo.getImdbId() + ", videoHash=" + fileInfo.getFileHash() + ", fileName=" + fileInfo.getFileName() + ", languages=" + languagesString);
            else log.debug("getSubtitle: fileInfo is null for " + fileUrl);
            try {
                searchResults = OpenSubtitlesApiHelper.searchSubtitle(fileInfo, languagesString);
            } catch (Throwable e) { //for various service outages
                log.error("getSubtitles: caught Throwable ", e);
                displayToast(getString(R.string.toast_subloader_service_unreachable));
                mDoNotFinish = false;
                return;
            }
            // when there is one sub only directly download it
            if (searchResults != null && searchResults.size() == 1) {
                log.debug("getSubtitles: one sub found for " + fileUrl);
                getSub(fileUrl, searchResults.get(0));
                mDoNotFinish = false; // one sub only, we are done
                return;
            }
            if (searchResults != null && searchResults.size() > 1) {
                mHandler.post(() -> askSubChoice(fileUrl, searchResults,languages.size()>1, !searchResults.isEmpty()));
            } else {
                log.warn("getSubtitles: no subs found on opensubtitles for " + fileUrl);
                displayToast(getString(R.string.dialog_subloader_fails) + " " + ((fileInfo != null) ? fileInfo.getFileName() : null));
                mDoNotFinish = false;
                return;
            }
            MediaUtils.removeLastSubs(SubtitlesDownloaderActivity2.this);
            if (!isCancelled() && !searchResults.isEmpty()) setResult(AppCompatActivity.RESULT_OK);
        }

        private void getSub(String fileUrl, OpenSubtitlesSearchResult searchResult) {
            String subUrl;
            try {
                subUrl = OpenSubtitlesApiHelper.getDownloadSubtitleLink(searchResult.getFileId());
                if (OpenSubtitlesApiHelper.getLastQueryResult() == OpenSubtitlesApiHelper.RESULT_CODE_QUOTA_EXCEEDED) {
                    log.warn("getSub: quota exceeded, quota resets in " + OpenSubtitlesApiHelper.getTimeRemaining());
                    displayToast(getString(R.string.toast_subloader_quota_exceeded));
                    displayToast(getString(R.string.opensubtitles_quota_reset_time_remaining, OpenSubtitlesApiHelper.getTimeRemaining()));
                    mDoNotFinish = false;
                    finish();
                    return;
                }
                if (subUrl == null) {
                    log.warn("getSub: subUrl is null for " + fileUrl);
                    displayToast(getString(R.string.dialog_subloader_fails) + " " + searchResult.getFileName());
                    mDoNotFinish = false;
                    finish();
                    return;
                }
                displayToast(getString(R.string.opensubtitles_quota_download_remaining, OpenSubtitlesApiHelper.getRemainingDownloads(), OpenSubtitlesApiHelper.getAllowedDownloads()));
            } catch (IOException e) {
                log.error("getSub: caught IOException", e);
                mDoNotFinish = false;
                finish();
                return;
            }
            downloadSubtitles(subUrl, fileUrl, getFriendlyFilename(fileUrl), searchResult.getLanguage());
            setResult(Activity.RESULT_OK);
            finish();
        }

        private OpenSubtitlesQueryParams getFileInfo(String fileUrl) {
            OpenSubtitlesQueryParams openSubtitlesQueryParams = new OpenSubtitlesQueryParams();
            MetaFile2 mf2 = null;
            log.debug("getFileInfo: " + fileUrl);
            if (!fileUrl.startsWith("http://")) { // if not http, we will need metafile2 even for upnp (file length + streaming uri)
                try {
                    mf2 = MetaFileFactoryWithUpnp.getMetaFileForUrl(Uri.parse(fileUrl));
                } catch (Exception e) {
                    log.error("getFileInfo: caught Exception", e);
                }
                if (mf2 == null) return null;
            }
            String newFileUrl = fileUrl;
            if (newFileUrl.startsWith("upnp://")) { //request streaming uri that will start with http
                Uri newUri = mf2.getStreamingUri();
                if (newUri != null) {
                    newFileUrl = newUri.toString();
                    log.debug("getFileInfo: shorten fileUrl to get fileName = " + Uri.parse(fileUrl).getLastPathSegment());
                    openSubtitlesQueryParams.setFileName(Uri.parse(fileUrl).getLastPathSegment());
                    Long fileLength = mf2.length();
                    // fileLength can be null (seen on google play console)
                    openSubtitlesQueryParams.setFileLength(fileLength != null ? fileLength : 0); // Add null check here
                    log.debug("getFileInfo: consider {} -> openSubtitlesQueryParams =(fileName={}, size={})", fileUrl, openSubtitlesQueryParams.getFileName(), openSubtitlesQueryParams.getFileLength());
                }
            }
            if (newFileUrl.startsWith("http://")) {
                URL url = null;
                HttpURLConnection urlConnection = null;
                try {
                    url = new URL(newFileUrl);
                    urlConnection = (HttpURLConnection) url.openConnection();
                    if (openSubtitlesQueryParams.getFileLength() != null && openSubtitlesQueryParams.getFileLength() > 0 && (urlConnection == null || !"bytes".equalsIgnoreCase(urlConnection.getHeaderField("Accept-Ranges"))))
                        openSubtitlesQueryParams.setFileHash(OpenSubtitlesHasher.computeHash(urlConnection, openSubtitlesQueryParams.getFileLength()));
                } catch (MalformedURLException e) {
                    log.error("getFileInfo: caught MalformedURLException for fileUrl " + newFileUrl, e);
                } catch (IOException e) {
                    log.error("getFileInfo: caught IOException for fileUrl " + newFileUrl, e);
                } finally {
                    if (urlConnection != null) urlConnection.disconnect();
                    openSubtitlesQueryParams.setFileHash(null);
                }
            } else {
                try {
                    Long fileLength = mf2.length();
                    openSubtitlesQueryParams.setFileLength(fileLength != null ? fileLength : 0);
                    if (openSubtitlesQueryParams.getFileLength() != null && openSubtitlesQueryParams.getFileLength() > 0) openSubtitlesQueryParams.setFileHash(OpenSubtitlesHasher.computeHash(Uri.parse(fileUrl), openSubtitlesQueryParams.getFileLength()));
                } catch (Exception e) { // failure for this file
                    openSubtitlesQueryParams.setFileHash(null);
                }
            }
            openSubtitlesQueryParams.setFileName(getFriendlyFilename(fileUrl));
            ContentResolver resolver = getContentResolver();
            log.debug("getFileInfo: trying to get VideoDbInfo for " + Uri.parse(fileUrl));
            VideoDbInfo videoDbInfo = VideoDbInfo.fromUri(resolver, Uri.parse(removeFileSlashSlash(fileUrl)));
            if (videoDbInfo != null) {
                // index is used to find back fileUrl, to allow search on query or imdbid do not put the moviebytesize otherwise it is the only search criteria
                log.debug("getFileInfo: (fileHash,url) <- (" + openSubtitlesQueryParams.getFileHash() + "," + fileUrl + ")");
                // try to use imdbId since the title can be translated...
                openSubtitlesQueryParams.setOnlineId(OnlineIdUtils.getOnlineId(fileUrl, getContentResolver()));
                log.debug("getFileInfo: imdbid=" + openSubtitlesQueryParams.getImdbId());
                if (openSubtitlesQueryParams.getImdbId() == null) { log.warn("getFileInfo: imdbId null for fileUrl " + fileUrl + "!!!");}
                openSubtitlesQueryParams.setIsShow(videoDbInfo.isShow);
                if (openSubtitlesQueryParams.isShow()) { // this is a show
                    // remove date from scraperTitle \([0-9]*\) because match does not work with e.g. The Flash (2015) or Doctor Who (2005)
                    openSubtitlesQueryParams.setShowTitle(videoDbInfo.scraperTitle.replaceAll(" *\\(\\d*?\\)", ""));
                    openSubtitlesQueryParams.setSeasonNumber(videoDbInfo.scraperSeasonNr);
                    openSubtitlesQueryParams.setEpisodeNumber(videoDbInfo.scraperEpisodeNr);
                    log.debug("getFileInfo: replacing " + videoDbInfo.scraperTitle + ", by " + openSubtitlesQueryParams.getShowTitle() + " season=" + openSubtitlesQueryParams.getSeasonNumber() +  ", episode=" + openSubtitlesQueryParams.getEpisodeNumber());
                }
            } else {
                log.warn("getFileInfo: cannot rely on scrape data for fileUrl " + fileUrl);
            }
            return openSubtitlesQueryParams;
        }

        private void askSubChoice(final String videoFilePath, final ArrayList<OpenSubtitlesSearchResult> searchResults, final boolean displayLang, final boolean hasSuccess) {
            View view = LayoutInflater.from(SubtitlesDownloaderActivity2.this).inflate(R.layout.subtitle_chooser_title_layout, null);
            ((TextView) view.findViewById(R.id.video_name)).setText(HtmlCompat.fromHtml(getString(R.string.select_sub_file, getFriendlyFilename(videoFilePath)), HtmlCompat.FROM_HTML_MODE_LEGACY));
            final AlertDialog subChoiceDialog = new AlertDialog.Builder(SubtitlesDownloaderActivity2.this)
                    .setCustomTitle(view)
                    .setAdapter(new BaseAdapter() {
                        @Override
                        public int getCount() {
                            return searchResults.size();
                        }

                        @Override
                        public Object getItem(int i) {
                            return null;
                        }

                        @Override
                        public long getItemId(int i) {
                            return 0;
                        }

                        @Override
                        public View getView(int i, View view, ViewGroup viewGroup) {
                            if (view == null) {
                                view = LayoutInflater.from(SubtitlesDownloaderActivity2.this).inflate(R.layout.subtitle_item_layout, null);
                            }
                            ((TextView) view.findViewById(R.id.video_name)).setText(searchResults.get(i).getFileName());
                            // use bold font for subs with hash match
                            if (searchResults.get(i).getMoviehashMatch())
                                ((TextView) view.findViewById(R.id.video_name)).setTypeface(null, Typeface.BOLD);
                            else
                                ((TextView) view.findViewById(R.id.video_name)).setTypeface(null, Typeface.NORMAL);
                            if (displayLang)
                                ((TextView) view.findViewById(R.id.lang)).setText(searchResults.get(i).getLanguage());
                            else view.findViewById(R.id.lang).setVisibility(View.GONE);
                            return view;
                        }
                    }, (dialogInterface, i) -> new Thread() {
                        public void run() {
                            log.debug("askSubChoice: entry " + i + " selected -> download sub " + searchResults.get(i).getFileName() + " for " + videoFilePath + " fileID=" + searchResults.get(i).getFileId() + " lang=" + searchResults.get(i).getLanguage());
                            getSub(videoFilePath, searchResults.get(i));
                        }
                    }.start())
                    .setCancelable(true)
                    .setOnCancelListener(dialog -> finish())
                    .create();

            if (!isFinishing() && !isDestroyed()) {
                subChoiceDialog.show();
                ListView listView = subChoiceDialog.getListView();
                if (listView != null) {
                    // Set the divider height
                    listView.setDividerHeight(10);
                }
            }
        }

        public void downloadSubtitles(String subUrl, String fileUrl, String name, String language){
            log.debug("downloadSubtitles: subUrl=" + subUrl + ", fileUrl=" + fileUrl + ", name=" + name + ", language=" + language);
            if (fileUrl == null) return;
            boolean canWrite = false;
            Uri parentUri = null;
            if(UriUtils.isImplementedByFileCore(Uri.parse(fileUrl))&&!FileUtils.isSlowRemote(Uri.parse(fileUrl))){ // do not write subs on slow remote when downloading
                parentUri = FileUtils.getParentUrl(Uri.parse(fileUrl));
                if(parentUri!=null){
                    try {
                        MetaFile2 metaFile2 = MetaFile2Factory.getMetaFileForUrl(parentUri);
                        canWrite = metaFile2.canWrite();
                    } catch (Exception e) {
                        log.error("downloadSubtitles: caught Exception", e);
                    }
                }
                log.debug("downloadSubtitles: we are not on slow remote try to write to " + fileUrl + " and canWrite=" + canWrite);
            } else {
                log.debug("downloadSubtitles: we are on slow remote or not implemented by filecore, do not try to write sub on remote");
            }
            StringBuilder localSb = null;
            StringBuilder sb = null;

            if (canWrite) {
                sb = new StringBuilder();
                sb.append(fileUrl.substring(0,fileUrl.lastIndexOf('.')+1)).append(language).append('.').append("srt");
                /* Check we can really create the file */
                try {
                    log.debug("downloadSubtitles: test we can write to " + sb);
                    FileEditor editor = FileEditorFactory.getFileEditorForUrl(Uri.parse(sb.toString()), SubtitlesDownloaderActivity2.this);
                    OutputStream tmp = editor.getOutputStream();
                    tmp.close();
                } catch (FileNotFoundException e) {
                    /* Fallback to subsDir */
                    canWrite = false;
                } catch (Exception e) {
                    canWrite = false;
                }
            }
            if (name == null || name.isEmpty())
                name = FileUtils.getFileNameWithoutExtension(Uri.parse(fileUrl));
            localSb = new StringBuilder();
            localSb.append(subsDir.getPath()).append('/').append(name).append('.').append(language).append('.').append("srt");
            if(!canWrite)
                sb = localSb;
            log.debug("downloadSubtitles: download to " + sb.toString() + " from " + subUrl + " because canwrite=" + canWrite);
            String srtURl = sb.toString();
            sb = null;
            OutputStream f =null;
            InputStream in = null;
            URL url;
            HttpURLConnection urlConnection = null;
            try {
                url  = new URL(subUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                // We get the first matching subtitle
                FileEditor editor = FileEditorFactory.getFileEditorForUrl(Uri.parse(srtURl), SubtitlesDownloaderActivity2.this);
                if(editor.exists()) {
                    log.debug("downloadSubtitles: delete first " + srtURl);
                    editor.delete();//delete first
                }
                f = editor.getOutputStream();

                in = urlConnection.getInputStream();
                int l = 0;
                byte[] buffer = new byte[1024];
                while ((l = in.read(buffer)) != -1) {
                    f.write(buffer, 0, l);
                }
                // f needs to be closed before the copy otherwise STATUS_SHARING_VIOLATION with smbj
                f.close();
                if(fileUrl != null) {
                    ContentResolver resolver = getContentResolver();
                    VideoDbInfo videoDbInfo = VideoDbInfo.fromUri(resolver, Uri.parse(fileUrl));
                    if (videoDbInfo != null) {
                        log.debug("downloadSubtitles: update subtitle count videoDbInfo for " + videoDbInfo.id);
                        final String where = VideoStore.Video.VideoColumns._ID + " = " + videoDbInfo.id;
                        videoDbInfo.nbSubtitles = videoDbInfo.nbSubtitles == -1 ? 1 : videoDbInfo.nbSubtitles + 1;
                        ContentValues values = new ContentValues(1);
                        values.put(VideoStore.Video.VideoColumns.SUBTITLE_COUNT_EXTERNAL, videoDbInfo.nbSubtitles);
                        resolver.update(VideoStore.Video.Media.EXTERNAL_CONTENT_URI,
                                values, where, null);
                    }
                }
                try {
                    //catching all exceptions for now for quick release
                    log.debug("downloadSubtitles: index " + fileUrl);
                    Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE, Uri.parse(fileUrl));
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    sendBroadcast(intent);
                } catch (Exception e){
                }
                if (canWrite) {
                    if(!FileUtils.isLocal(Uri.parse(fileUrl))){ // when not local, we need to copy our file
                        log.debug("downloadSubtitles: copy file {}->{}", fileUrl, localSb);
                        editor.copyFileTo(Uri.parse(localSb.toString()),SubtitlesDownloaderActivity2.this);
                    }
                }
            } catch (FileNotFoundException e) {
                log.error("downloadSubtitles: caught FileNotFoundException", e);
            } catch (IOException e) {
                log.error("downloadSubtitles: caught IOException", e);
            } catch (Throwable e){ //for various service outages
                log.error("downloadSubtitles: caught Throwable", e);
            }finally{
                MediaUtils.closeSilently(f);
                MediaUtils.closeSilently(in);
                f = null;
                in = null;
            }
        }

        private void setInitDialog() {
            log.debug("OpenSubtitlesTask: setInitDialog");
            mHandler.post(() -> {
                mDialog = NovaProgressDialog.show(SubtitlesDownloaderActivity2.this, "", getString(R.string.dialog_subloader_connecting), true, true, dialog -> {
                    dialog.cancel();
                    finish();
                });
                mDialog.setCanceledOnTouchOutside(false); // to not cancel when tapping the screen out of dialog zone
                mDialog.setOnDismissListener(dialog -> {
                    if(!mDoNotFinish) {
                        dialog.cancel();
                        finish();
                    }
                    mDoNotFinish = false;
                });
                log.debug("OpenSubtitlesTask: setInitDialog setMessage " + getString(R.string.dialog_subloader_connecting));
            });
        }

        private void displayToast(final String message){
            mHandler.post(() -> Toast.makeText(SubtitlesDownloaderActivity2.this, message, Toast.LENGTH_SHORT).show());
        }

    }

    private void closeDialog() {
        if (mDialog != null) {
            mDoNotFinish = false;
            mDialog.dismiss();
        }
    }

    @SuppressWarnings("unchecked")
    public void logOut() {
        new Thread(() -> {
            try {
                OpenSubtitlesApiHelper.logout();
            } catch (IOException e1) {
                log.error("logOut: caught IOException", e1);
            } catch (Throwable e) { //for various service outages
                log.error("logOut: caught Exception", e);
            }
        }).start();
    }

}