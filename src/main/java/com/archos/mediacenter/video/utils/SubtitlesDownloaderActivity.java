// Copyright 2017 Archos SA
// Copyright 2020 Courville Software
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
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.BadTokenException;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import de.timroes.axmlrpc.XMLRPCClient;
import de.timroes.axmlrpc.XMLRPCException;

import static com.archos.filecorelibrary.FileUtils.removeFileSlashSlash;

public class SubtitlesDownloaderActivity extends AppCompatActivity {

    private static final Logger log = LoggerFactory.getLogger(SubtitlesDownloaderActivity.class);

    public static final String FILE_URLS = "fileUrls";
    public static final String FILE_URL = "fileUrl";
    public static final String FILE_NAMES = "fileNames"; //friendly name for Upnp
    public static final String FILE_SIZES = "fileSizes";
    
    //to distinguished program dismiss and users
    private boolean mDoNotFinish;
    private final String OpenSubtitlesAPIUrl = "https://api.opensubtitles.org/xml-rpc";
    private final String USER_AGENT = "novavideoplayer v1";
    private SharedPreferences sharedPreferences;
    private File subsDir;
    Handler mHandler;
    HashMap<String, Long> mFileSizes = null;
    HashMap<String, String> mFriendlyFileNames = null; // they need to have an extension
    HashMap<String, String> mIndexableUri = null;
    boolean stop = false;
    private NovaProgressDialog mDialog;
    private ProgressBar mProgressBar;

    private AlertDialog mSumUpDialog;
    private OpenSubtitlesTask mOpenSubtitlesTask = null;

    private Cursor mCursor;
    //database URI : VideoStore.Video.Media.EXTERNAL_CONTENT_URI
    private static final String[] imdbIdProjection = {
            VideoStore.Video.VideoColumns._ID,
            VideoStore.Video.VideoColumns.SCRAPER_IMDB_ID,
    };

    private static final String WHERE = VideoStore.Video.VideoColumns.DATA + "=?";
    private static final int FIRST_PASS = 0; // moviehash+moviebytesize based (provides only one result)
    private static final int SECOND_PASS = 1; // tag (full filename) based (provides only one result) but does not work if file is renamed...
    private static final int THIRD_PASS = 2; // query (friendly name from filename) based (provides multiple choices)
    private static final int FOURTH_PASS = 3; // scraped information based (imdbid for movie/show with season episode number for show) (provide multiple choices)
    private static final boolean firstPassEnabled = true;
    private static final boolean secondPassEnabled = true; // NOTE: keep it for non scraped content
    private static final boolean thirdPassEnabled = false; // NOTE: cannot select both 3rd and 4th since multiple choices
    private static final boolean fourthPassEnabled = true; // NOTE: cannot select both 3rd and 4th since multiple choices

    private static class NonConfigurationInstance {
        public NovaProgressDialog progressDialog;
        public AlertDialog sumUpDialog;
    };

    @SuppressWarnings({"unchecked", "serial"})
    @Override
    public void onStart() {
        super.onStart();
        log.debug("onStart");
        mHandler = new Handler(getMainLooper());
        mIndexableUri = new HashMap<>();
        final NonConfigurationInstance nci = (NonConfigurationInstance) getLastNonConfigurationInstance();
        if (nci != null) {
            // The activity is created again after a rotation => just restore the state of the dialogs
            // as the OpenSubtitlesTask is still running in the background
            mDialog = nci.progressDialog;
            mSumUpDialog = nci.sumUpDialog;
        }
        else {
            // Normal start of the activity
            if(NetworkState.isNetworkConnected(this)){
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                final Intent intent = getIntent();
                ArrayList<String> fileUrls = null;
                if (intent.hasExtra(FILE_URL)){
                    fileUrls = new ArrayList<String>(){{add(intent.getStringExtra(FILE_URL));}};
                } else if (intent.hasExtra(FILE_URLS)) {
                    fileUrls = intent.getStringArrayListExtra(FILE_URLS);
                } else {
                    finish();
                    return;
                }
                if (intent.hasExtra(FILE_SIZES))
                    mFileSizes = (HashMap<String, Long>)intent.getSerializableExtra(FILE_SIZES);
                else
                    mFileSizes = new HashMap<>();
                if (intent.hasExtra(FILE_NAMES))
                    mFriendlyFileNames = (HashMap<String, String>) intent.getSerializableExtra(FILE_NAMES);
                else
                    mFriendlyFileNames = new HashMap<>();
                mOpenSubtitlesTask = new OpenSubtitlesTask();
                for(String uri : fileUrls)
                    mIndexableUri.put(uri, uri);
                mOpenSubtitlesTask.execute(fileUrls, getSubLangValue());
                subsDir = MediaUtils.getSubsDir(this);
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
        finish();
        super.onStop();
    }

    public void onResume(){
        super.onResume();
        // Check if there are some dialogs to restore after a device rotation
        // or after the backlight was turned off and on
        if (mSumUpDialog != null) {
            // The results dialog existed previously (i.e. was visible) => show it again
            mSumUpDialog.show();
        }
        else if (mDialog != null) {
            // The results dialog was not visible but the progress dialog was => show the progress dialog
            mDialog.show();
        }
        TorrentObserverService.resumed(this);
    }

    public void onPause(){
        super.onPause();
        if (mDialog != null) {
            mDoNotFinish = true;
            mDialog.dismiss();
        }
        if (mSumUpDialog != null) {
            mDoNotFinish = true;
            mSumUpDialog.dismiss();
        }
        TorrentObserverService.paused(this);
    }

    // Disabled part of AppCompat migration since onRetainNonConfigurationInstance is final
    /*
    public Object onRetainNonConfigurationInstance() {
        // The activity is going to be destroyed after a rotation => save the state of the dialogs
        NonConfigurationInstance nci = new NonConfigurationInstance();
        nci.progressDialog = mDialog;
        nci.sumUpDialog = mSumUpDialog;
        return nci;
    }
     */

    private ArrayList<String> getSubLangValue() {
        // always add default system language in the list of languages
        Set<String> langDefault = new HashSet<String>();
        langDefault.add("system");
        Set<String> languages = sharedPreferences.getStringSet("languages_list", langDefault);
        final ArrayList<String> languageDefault = new ArrayList<String>(languages);
        log.debug("getSubLangValue: langDefault=" + languageDefault.toString());
        langDefault = null;
        languages = null;
        return languageDefault;
    }

    private class OpenSubtitlesTask extends AsyncTask<ArrayList<String>, Integer, Void>{
        HashMap<String, Object> map = null;
        XMLRPCClient client = null;
        private String token = null;
        @Override
        protected void onPreExecute() {
            log.debug("OpenSubtitlesTask: onPreExecute");
            setInitDialog();
        }
        @Override
        protected Void doInBackground(ArrayList<String>... params) {
            ArrayList<String> filesList =  params[0];
            ArrayList<String> languages = params[1];
            if (logIn()){
                getSubtitles(filesList, languages);
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            // Close the progress dialog
            if (mDialog != null) {
                mDoNotFinish = true;
                mDialog.dismiss();
            }
            // Exit the activity if processing is done/aborted and the SumUp dialog is not visible
            if (stop && (mSumUpDialog == null || !mSumUpDialog.isShowing()))
                finish();
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            log.debug("onProgressUpdate");
            if (values == null || values.length != 2 || isCancelled()) return;
            int progress = values[0];
            int filesNumber = values[1];
            if (progress == 0) {
                log.debug("onProgressUpdate: progress=" + progress);
                if (mDialog != null && mDialog.isShowing()) {
                    log.debug("onProgressUpdate: progress=0 and mDialog.isShowing() dismissing dialog!!!");
                    mDoNotFinish = true;
                    mDialog.dismiss();
                }
                mDialog = new NovaProgressDialog(SubtitlesDownloaderActivity.this);
                mDialog.setMessage(getString(R.string.dialog_subloader_downloading));
                mDialog.setCancelable(true);
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.setOnCancelListener(dialog -> {
                    dialog.cancel();
                    stop();
                    finish();
                });
                mDialog.setOnDismissListener(dialog -> {
                    if(!mDoNotFinish) {
                        dialog.cancel();
                        stop();
                        finish();
                    }
                    mDoNotFinish = false;
                });
                log.debug("onProgressUpdate: setMessage " + getString(R.string.dialog_subloader_downloading));
                if (filesNumber > 1){
                    log.debug("onProgressUpdate: progressbar filesNumber=" + filesNumber);
                    mDialog.setProgressStyle(NovaProgressDialog.STYLE_HORIZONTAL);
                    mDialog.setProgress(progress);
                    mDialog.setMax(filesNumber);
                } else {
                    log.debug("onProgressUpdate: spinner filesNumber=" + filesNumber);
                    mDialog.setProgressStyle(NovaProgressDialog.STYLE_SPINNER);
                    mDialog.setIndeterminate(true);
                    //mDialog.setProgress(progress);
                    //mDialog.setMax(filesNumber);
                }
                mDialog.show();
            } else {
                if (mDialog != null) {
                    log.debug("onProgressUpdate: mDialog not null");
                    mDialog.setProgress(progress);
                    mDialog.setMax(filesNumber);
                } else {
                    log.warn("onProgressUpdate: mDialog is null!!!");
                }
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
            if (mUsername.isEmpty() || mPassword.isEmpty())
                displayToast(getString(R.string.toast_subloader_credentials_empty));
            try {
                URL url = new URL(OpenSubtitlesAPIUrl);
                if (log.isTraceEnabled()) client = new XMLRPCClient(url, XMLRPCClient.FLAGS_DEBUG);
                else client = new XMLRPCClient(url);
            } catch (MalformedURLException e) {
                log.error("logIn: caught MalformedURLException");
            }
            try {
                if (!mUsername.isEmpty() && !mPassword.isEmpty())
                    map = ((HashMap<String, Object>) client.call("LogIn",mUsername, mPassword, "en", USER_AGENT));
                else
                    map = ((HashMap<String, Object>) client.call("LogIn","","","en",USER_AGENT));
                token = (String) map.get("token");
            } catch (XMLRPCException e) {
                // TODO parse error message
                // 401 Unauthorized
                // 414 Unknown User Agent
                // 415 Disabled user agent
                log.warn("logIn error message: " + e.getMessage() + "; localizedMessage:" + e.getLocalizedMessage() + ", cause: " + e.getCause());
                displayToast(getString(R.string.toast_subloader_login_failed));
                if (mDialog != null) {
                    mDoNotFinish = true;
                    mDialog.dismiss();
                }
                stop = true;
                return false;
            } catch (Throwable e) { //for various service outages
                log.error("logIn: caught exception",e);
                displayToast(getString(R.string.toast_subloader_service_unreachable));
                if (mDialog != null) {
                    mDoNotFinish = true;
                    mDialog.dismiss();
                }
                stop = true;
                return false;
            }
            map = null;
            return true;
        }

        @SuppressWarnings("unchecked")
        public void logOut() {
            if (token == null)
                return;
            try {
                map = ((HashMap<String, Object>)  client.call("LogOut", token));
            } catch (XMLRPCException e1) {
                log.error("logOut: caught XMLRPCException", e1);
            } catch (Throwable e){ //for various service outages
                log.error("logOut: caught Exception", e);
            }
            token = null;
            map = null;
            return;
        }

        //WebService to use for checking session validity
        @SuppressWarnings("unchecked")
        public void keepAlive() {
            if (token == null)
                return;
            try {
                map = ((HashMap<String, Object>) client.call("NoOperation", token));
            } catch (XMLRPCException e1) {
                log.error("keepAlive: caught XMLRPCException", e1);
            } catch (Throwable e){ //for various service outages
                log.error("keepAlive: caught Throwable", e);
            }
            return;
        }

        /**
         * returns name WITHOUT extension
         * @param fileUrl
         * @return
         */
        public String getFriendlyFilename(String fileUrl){
            if (mFriendlyFileNames != null&&mFriendlyFileNames.containsKey(fileUrl)){
                return mFriendlyFileNames.get(fileUrl);
            } else {
                return FileUtils.getFileNameWithoutExtension(Uri.parse(fileUrl));
            }
        }
        @SuppressWarnings("unchecked")
        public void getSubtitles(final ArrayList<String> fileUrls, final ArrayList<String> languages) {
            if (token == null || fileUrls == null || fileUrls.isEmpty() || languages == null || languages.isEmpty()){
                stop = true;
                return;
            }
            stop = false;
            boolean single = fileUrls.size() == 1;
            ArrayList<String> notFoundFiles = new ArrayList<String>();
            HashMap<String, String> index = new HashMap<String, String>();
            final HashMap<String, ArrayList<String>> success = new HashMap<String, ArrayList<String>>();
            HashMap<String, ArrayList<String>> fails = new HashMap<String, ArrayList<String>>();
            List<HashMap<String, Object>> videoSearchList;
            // first pass: moviehash+moviebytesize based
            videoSearchList = prepareRequestList(fileUrls, languages, index, FIRST_PASS);
            Object[] subtitleMaps = null;
            int progress = 0;
            if (firstPassEnabled) {
                try { // launch the search for first pass
                    map = (HashMap<String, Object>) client.call("SearchSubtitles", token, videoSearchList);
                } catch (Throwable e) { //for various service outages
                    log.error("getSubtitles: 1st pass caught Throwable", e);
                    stop = true;
                    displayToast(getString(R.string.toast_subloader_service_unreachable));
                    return;
                }
                if (!isCancelled() && map.get("data") instanceof Object[]) { // process the search results if any
                    subtitleMaps = ((Object[]) map.get("data"));
                    //publishProgress(new Integer[]{progress, fileUrls.size()});
                    String lastProcessedFile = "";
                    if (subtitleMaps.length > 0) {
                        String srtFormat, movieHash, fileUrl, fileName, subLanguageID, subDownloadLink;
                        for (int i = 0; i < subtitleMaps.length; i++) {
                            if (isCancelled()) break;
                            srtFormat = ((HashMap<String, String>) subtitleMaps[i]).get("SubFormat");
                            movieHash = ((HashMap<String, String>) subtitleMaps[i]).get("MovieHash");
                            subLanguageID = ((HashMap<String, String>) subtitleMaps[i]).get("SubLanguageID");
                            subDownloadLink = ((HashMap<String, String>) subtitleMaps[i]).get("SubDownloadLink");
                            fileUrl = index.get(movieHash);
                            log.debug("getSubtitles: 1st pass, fileURL=" + fileUrl +
                                    ", subDownloadLink=" + subDownloadLink +
                                    ", subLanguageID=" + subLanguageID);
                            if (fileUrl == null) {
                                //publishProgress(new Integer[]{++progress, fileUrls.size()});
                                publishProgress(i, subtitleMaps.length);
                                continue;
                            }
                            fileName = getFriendlyFilename(fileUrl);
                            if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)) {
                                publishProgress(i, subtitleMaps.length);
                                continue;
                            } else {
                                if (success.containsKey(fileName))
                                    success.get(fileName).add(subLanguageID);
                                else {
                                    ArrayList<String> newLanguage = new ArrayList<String>();
                                    newLanguage.add(subLanguageID);
                                    success.put(fileName, newLanguage);
                                }
                            }
                            downloadSubtitles(subDownloadLink, fileUrl, fileName, srtFormat, subLanguageID);
                            if (!single && !lastProcessedFile.equals(fileName)) {
                                lastProcessedFile = fileName;
                                //publishProgress(new Integer[]{++progress, fileUrls.size()});
                                publishProgress(i, subtitleMaps.length);
                            }
                        }
                    } else {
                        log.debug("getSubtitles: no result for 1st pass");
                    }
                }
            }
            if (videoSearchList != null) videoSearchList.clear();
            index.clear();
            subtitleMaps = null;
            // Second pass tag (full filename) based
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName))
                    notFoundFiles.add(fileUrl);
                else {
                    // add it too if not all languages are present
                    if (success.get(fileName).size() != languages.size())
                        notFoundFiles.add(fileUrl);
                }
            }
            if (secondPassEnabled && !isCancelled() && !notFoundFiles.isEmpty() &&
                    !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, SECOND_PASS)).isEmpty()) {
                try { // perform second pass search
                    map = (HashMap<String, Object>) client.call("SearchSubtitles", token, videoSearchList);
                } catch (Throwable e) { //for various service outages
                    log.error("getSubtitles: 2nd pass caught Throwable", e);
                    stop = true;
                    displayToast(getString(R.string.toast_subloader_service_unreachable));
                    return;
                }
                if (map.get("data") instanceof Object[]) {
                    subtitleMaps = ((Object[]) map.get("data"));
                    //publishProgress(new Integer[] {progress, fileUrls.size()});
                    String lastProcessedFile = "";
                    if (subtitleMaps.length > 0) {
                        String srtFormat, tag, fileUrl, fileName, subLanguageID, subDownloadLink, movieReleaseName;
                        for (int i = 0; i < subtitleMaps.length; i++) {
                            if (isCancelled())
                                break;
                            srtFormat = ((HashMap<String, String>) subtitleMaps[i])
                                    .get("SubFormat");
                            tag = ((HashMap<String, String>) subtitleMaps[i]).get("tag");
                            subLanguageID = ((HashMap<String, String>) subtitleMaps[i])
                                    .get("SubLanguageID");
                            subDownloadLink = ((HashMap<String, String>) subtitleMaps[i])
                                    .get("SubDownloadLink");
                            movieReleaseName = ((HashMap<String, String>) subtitleMaps[i])
                                    .get("MovieReleaseName");
                            fileUrl = index.get(movieReleaseName);
                            log.debug("getSubtitles: 2nd pass, fileURL=" + fileUrl +
                                    ", subDownloadLink=" + subDownloadLink +
                                    ", subLanguageID=" + subLanguageID);
                            if (fileUrl == null){ //we keep only result for exact matching name
                                publishProgress(i, subtitleMaps.length);
                                continue;
                            }
                            fileName = getFriendlyFilename(fileUrl);
                            if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)){
                                publishProgress(i, subtitleMaps.length);
                                continue;
                            } else {
                                if (success.containsKey(fileName))
                                    success.get(fileName).add(subLanguageID);
                                else {
                                    ArrayList<String> newLanguage = new ArrayList<String>();
                                    newLanguage.add(subLanguageID);
                                    success.put(fileName, newLanguage);
                                }
                            }
                            downloadSubtitles(subDownloadLink, fileUrl, fileName, srtFormat, subLanguageID);
                            if (!single && !lastProcessedFile.equals(fileName)){
                                lastProcessedFile = fileName;
                                //publishProgress(new Integer[] {++progress, fileUrls.size()});
                                publishProgress(i, subtitleMaps.length);
                            }
                        }
                    } else {
                        log.debug("getSubtitles: no result for 2nd pass");
                    }
                } else
                    log.debug("FAIL " + map.get("data"));
            }
            boolean doNotFinish = false;
            // Third pass query (friendly name from filename) based
            notFoundFiles.clear();
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName))
                    notFoundFiles.add(fileUrl);
                else {
                    // add it too if not all languages are present (but it will make multiple download of same language
                    if (success.get(fileName).size() != languages.size())
                        notFoundFiles.add(fileUrl);
                }
            }
            if (thirdPassEnabled && !isCancelled() && single && !notFoundFiles.isEmpty() &&
                    !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, THIRD_PASS)).isEmpty()) {
                try { // perform third pass search
                    map = (HashMap<String, Object>) client.call("SearchSubtitles", token, videoSearchList);
                } catch (Throwable e) { //for various service outages
                    log.error("getSubtitles: 3rd pass caught Throwable ", e);
                    stop = true;
                    displayToast(getString(R.string.toast_subloader_service_unreachable));
                    return;
                }
                if (map.get("data") instanceof Object[]) {
                    subtitleMaps = ((Object[]) map.get("data"));
                    if (subtitleMaps.length > 0) {
                        doNotFinish = true;
                        final Object[] finalSubtitleMaps = subtitleMaps;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                askSubChoice(fileUrls.get(0),finalSubtitleMaps,languages.size()>1, !success.isEmpty());
                            }
                        });
                    } else {
                        log.debug("getSubtitles: no result for 3rd pass");
                    }
                } else
                    log.debug("FAIL " + map.get("data"));
            }
            // Fourth pass scraped information based (imdbid for movie or query=showtitle season episode number)
            notFoundFiles.clear();
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName))
                    notFoundFiles.add(fileUrl);
                else {
                    // add it too if not all languages are present (but it will make multiple download of same language
                    if (success.get(fileName).size() != languages.size())
                        notFoundFiles.add(fileUrl);
                }
            }
            if (fourthPassEnabled && !isCancelled() && single && !notFoundFiles.isEmpty() &&
                    !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, FOURTH_PASS)).isEmpty()) {
                try { // perform fourth pass search
                    map = (HashMap<String, Object>) client.call("SearchSubtitles", token, videoSearchList);
                } catch (Throwable e) { //for various service outages
                    log.error("getSubtitles: 4th pass caught Throwable", e);
                    stop = true;
                    displayToast(getString(R.string.toast_subloader_service_unreachable));
                    return;
                }
                if (map.get("data") instanceof Object[]) {
                    subtitleMaps = ((Object[]) map.get("data"));
                    if (subtitleMaps.length > 0) {
                        doNotFinish = true;
                        final Object[] finalSubtitleMaps = subtitleMaps;
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                askSubChoice(fileUrls.get(0),finalSubtitleMaps,languages.size()>1, !success.isEmpty());
                            }
                        });
                    } else {
                        log.debug("getSubtitles: no result for 4th pass");
                    }
                } else
                    log.debug("FAIL " + map.get("data"));
            }
            if(doNotFinish) return;
            // fill fails list
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName)){
                    ArrayList<String> langs = new ArrayList<String>();
                    for (String language : languages) {
                        langs.add(getCompliantLanguageID(language));
                    }
                    fails.put(fileName, langs);
                } else { // here we get the subs missing only
                    ArrayList<String> langs = new ArrayList<String>();
                    for (String language : languages) {
                        String langID = getCompliantLanguageID(language);
                        if (!success.get(fileName).contains(langID)){
                            langs.add(langID);
                        }
                    }
                    if (!langs.isEmpty())
                        fails.put(fileName, langs);
                }
            }
            MediaUtils.removeLastSubs(SubtitlesDownloaderActivity.this);
            if (!isCancelled()) {
                if (single){
                    stop = true;
                    displayToast(buildSumup(success, fails, true));
                } else {
                    showSumup(buildSumup(success, fails, false));
                }
                if (!success.isEmpty()) {
                    setResult(AppCompatActivity.RESULT_OK);
                }
            }
            logOut();
            return;
        }

        private List<HashMap<String, Object>> prepareRequestList(final ArrayList<String> fileUrls,
                ArrayList<String> languages, HashMap<String, String> index, int pass) {
            List<HashMap<String, Object>> videoSearchList;
            videoSearchList = new ArrayList<HashMap<String, Object>>();
            for (String fileUrl : fileUrls){
                fileUrl = removeFileSlashSlash(fileUrl);
                if (stop) break;
                String hash = null, tag = null;
                long fileLength = 0;
                if (pass == FIRST_PASS) { //first pass, search by index
                    fileLength = 0;
                    MetaFile2 mf2=null;
                    if (!fileUrl.startsWith("http://")) { // if not http, we will need metafile2 even for upnp (file length + streaming uri)
                        try {
                            mf2 = MetaFileFactoryWithUpnp.getMetaFileForUrl(Uri.parse(fileUrl));
                        } catch (Exception e) {
                            log.error("prepareRequestList: caught Exception", e);
                        }
                        if (mf2 == null) continue;
                    }
                    if (fileUrl.startsWith("upnp://")) { //request streaming uri
                        // TODO need also length
                        Uri newUri = mf2.getStreamingUri();
                        if (newUri != null) {
                            String oldFileUrl = fileUrl;
                            fileUrl = newUri.toString();
                            log.debug("prepareRequestList: add mFriendlyFileNames <- (" + fileUrl + "," + Uri.parse(oldFileUrl).getLastPathSegment() + "), size=" + mf2.length());
                            mFriendlyFileNames.put(fileUrl, Uri.parse(oldFileUrl).getLastPathSegment());
                            mFileSizes.put(fileUrl,  mf2.length());
                        }
                    }
                    if (fileUrl.startsWith("http://")) {
                        if (mFileSizes != null) {
                            final Long fileLengthLong = mFileSizes.get(fileUrl);
                            if (fileLengthLong != null) {
                                fileLength = fileLengthLong.longValue();
                            }
                        }
                        else continue;
                        URL url = null;
                        HttpURLConnection urlConnection = null;
                        try {
                            url = new URL(fileUrl);
                            urlConnection = (HttpURLConnection) url.openConnection();
                            if (urlConnection == null
                                    || !"bytes".equalsIgnoreCase(urlConnection
                                            .getHeaderField("Accept-Ranges")))
                                continue;
                            hash = OpenSubtitlesHasher.computeHash(urlConnection, fileLength);
                        } catch (MalformedURLException e) {
                            log.error("prepareRequestList: caught MalformedURLException", e);
                            continue;
                        } catch (IOException e) {
                            log.error("prepareRequestList: caught IOException", e);
                            continue;
                        } finally {
                            if (urlConnection != null)
                                urlConnection.disconnect();
                        }
                    } else {
                        try {
                            fileLength = mf2.length();
                            hash = OpenSubtitlesHasher.computeHash(Uri.parse(fileUrl), fileLength);
                        } catch (Exception e) { // failure for this file
                            log.error("prepareRequestList: caught Exception", e);
                            break;
                        }
                    }
                    if (hash == null) continue;
                } else { //Second pass, search by TAG (filename)
                    tag = getFriendlyFilename(fileUrl);
                }

                HashMap<String, Object> video = new HashMap<String, Object>();
                // sublanguageid contains concatenated comma separated list of languages
                ArrayList<String> subLanguageId = new ArrayList<String>();
                for (String item : languages) subLanguageId.add(getCompliantLanguageID(item));
                video.put("sublanguageid", TextUtils.join(",",  subLanguageId));
                log.debug("prepareRequestList: search for sublanguageid " + TextUtils.join(",",  subLanguageId));
                if (stop) break;

                if (pass == FIRST_PASS) {  // FIRST_PASS is moviehash+moviebytesize for search
                    index.put(hash, fileUrl);
                    log.debug("prepareRequestList: first pass add index (hash,url) <- (" + hash + "," + fileUrl + ")");
                    video.put("moviehash", hash);
                    video.put("moviebytesize", String.valueOf(fileLength));
                    log.debug("prepareRequestList: first pass add video (hash,length) <- (" + hash + "," + fileLength + ")");
                } else {
                    if (pass == SECOND_PASS) { // SECOND_PASS is tag (filename) for search
                        log.debug("prepareRequestList: second pass add tag " + tag);
                        video.put("tag", tag);
                    } else if (pass == THIRD_PASS) {
                        log.debug("prepareRequestList: third pass add query " + tag);
                        video.put("query", tag); // THIRD_PASS is query (filename as search name but not clean) for search
                    } else if (pass == FOURTH_PASS) { // FOURTH_PASS is scraped info based
                        // try to get the video info if scraped
                        ContentResolver resolver = getContentResolver();
                        log.debug("prepareRequestList: fourth pass trying to get VideoDbInfo for " + Uri.parse(fileUrl));
                        VideoDbInfo videoDbInfo = VideoDbInfo.fromUri(resolver, Uri.parse(removeFileSlashSlash(fileUrl)));
                        if (videoDbInfo != null) {
                            // index is used to find back fileUrl, to allow search on query or imdbid do not put the moviebytesize otherwise it is the only search criteria
                            log.debug("prepareRequestList: fourth pass index (hash,url) <- (" + hash + "," + fileUrl + ")");
                            // try to use imdbId since the title can be translated...
                            String imdbId = getIMDBID(fileUrl);
                            if (imdbId != null) {
                                // remove all non numeric characters from imdbID (often starts with tt)
                                // if (imdbID.startsWith("tt")) imdbID = imdbID.substring(2);
                                imdbId = imdbId.replaceAll("[^\\d]", "");
                                video.put("imdbid", imdbId);
                                log.debug("prepareRequestList: fourth pass imdbid=" + imdbId);
                            } else { // imdbId is null
                                log.warn("prepareRequestList: imdbId null!!!");
                                if (videoDbInfo.isShow) { // this is a show
                                    // remove date from scraperTitle \([0-9]*\) because match does not work with e.g. The Flash (2015) or Doctor Who (2005)
                                    video.put("query", videoDbInfo.scraperTitle.replaceAll(" *\\(\\d*?\\)", ""));
                                    log.debug("prepareRequestList: replacing " + videoDbInfo.scraperTitle + ", by " +
                                            videoDbInfo.scraperTitle.replaceAll(" *\\(\\d*?\\)", ""));
                                }
                            }
                            if (videoDbInfo.isShow) { // this is a show
                                video.put("season", videoDbInfo.scraperSeasonNr);
                                video.put("episode", videoDbInfo.scraperEpisodeNr);
                                log.debug("prepareRequestList: fourth pass show query=" + videoDbInfo.scraperTitle + ", season=" + videoDbInfo.scraperSeasonNr + ", episode=" + videoDbInfo.scraperEpisodeNr);
                            }
                        } else {
                            log.warn("prepareRequestList: fourth pass uh videoDbInfo = null!!");
                        }
                    }
                    // since SECOND or THIRD or FOURTH pass not based on hash, put in index the tag
                    int dotPos = tag.lastIndexOf('.');
                    if (dotPos > -1) {
                        log.debug("prepareRequestList: other pass add index (tag without ext,url) <- (" + tag.substring(0, dotPos) + "," + fileUrl + ")");
                        index.put(tag.substring(0, dotPos), fileUrl);
                    } else {
                        log.debug("prepareRequestList: other pass add index (tag,url) <- (" + tag + "," + fileUrl + ")");
                        index.put(tag, fileUrl);
                    }
                    log.debug("prepareRequestList: other pass add index (friendly,url) <- (" + getFriendlyFilename(fileUrl) + "," + fileUrl + ")");
                    index.put(getFriendlyFilename(fileUrl), fileUrl);
                }
                log.debug("prepareRequestList: add video to videoSearchList " + video.toString());
                videoSearchList.add(video);
            }
            return videoSearchList;
        }

        private void askSubChoice(final String videoFilePath, final Object[] subtitleMaps, final boolean displayLang, final boolean hasSuccess) {
            final HashMap<String,List<HashMap<String,String>> > items = new HashMap<>();
            final List<String> keys = new ArrayList<>();
            for (int i = 0; i < subtitleMaps.length; i++) {
                if (isDestroyed()||isFinishing())
                    break;
                String subLanguageID = ((HashMap<String, String>) subtitleMaps[i])
                        .get("SubLanguageID");
                String movieReleaseName = ((HashMap<String, String>) subtitleMaps[i])
                        .get("MovieReleaseName");
                if(!items.containsKey(movieReleaseName))
                    items.put(movieReleaseName, new ArrayList<HashMap<String, String>>());
                items.get(movieReleaseName).add((HashMap<String, String>) subtitleMaps[i]);
                if(!keys.contains(movieReleaseName))
                    keys.add(movieReleaseName);
            }
            View view = LayoutInflater.from(SubtitlesDownloaderActivity.this).inflate(R.layout.subtitle_chooser_title_layout, null);
            ((TextView) view.findViewById(R.id.video_name)).setText(HtmlCompat.fromHtml(getString(R.string.select_sub_file, getFriendlyFilename(videoFilePath)), HtmlCompat.FROM_HTML_MODE_LEGACY));
            new AlertDialog.Builder(SubtitlesDownloaderActivity.this)
                    .setCustomTitle(view)
                    .setAdapter(new BaseAdapter() {
                        @Override
                        public int getCount() {
                            return keys.size() ;
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
                            if (view == null ) {
                                view = LayoutInflater.from(SubtitlesDownloaderActivity.this).inflate(R.layout.subtitle_item_layout, null);
                            }
                            String name = keys.get(i);
                            StringBuilder lang = new StringBuilder();
                            if (displayLang) {
                                for (HashMap<String, String> item : Objects.requireNonNull(items.get(name))) {
                                    if (!lang.toString().contains(item.get("SubLanguageID") + " "))
                                        lang.append(item.get("SubLanguageID")).append(" ");
                                }
                                ((TextView) view.findViewById(R.id.lang)).setText(lang.toString());
                            }
                            else
                                view.findViewById(R.id.lang).setVisibility(View.GONE);
                            ((TextView) view.findViewById(R.id.video_name)).setText(name);
                            return view;
                        }
                    }, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, final int i) {
                            new Thread() {
                                public void run() {
                                    publishProgress(0, 1);
                                    for (HashMap<String, String> item : Objects.requireNonNull(items.get(keys.get(i)))) {
                                        downloadSubtitles(item
                                                .get("SubDownloadLink"), videoFilePath, getFriendlyFilename(videoFilePath), item
                                                .get("SubFormat"), item
                                                .get("SubLanguageID"));
                                    }
                                    setResult(Activity.RESULT_OK);
                                    finish();
                                }
                            }.start();
                        }
                    })
                    .setCancelable(true)
                    .setOnCancelListener(dialog -> finish())
                    .show().getListView().setDividerHeight(10);
        }

        public void downloadSubtitles(String subUrl, String path, String name, String subFormat, String language){
            if (token == null || path == null)
                return;
            String indexableUri = mIndexableUri.get(path);
            boolean canWrite = false;
            Uri parentUri = null;
            if(UriUtils.isImplementedByFileCore(Uri.parse(path))&&!FileUtils.isSlowRemote(Uri.parse(path))){
                parentUri = FileUtils.getParentUrl(Uri.parse(path));
                if(parentUri!=null){
                    try {
                        MetaFile2 metaFile2 = MetaFile2Factory.getMetaFileForUrl(parentUri);
                        canWrite = metaFile2.canWrite();
                    } catch (Exception e) {
                        log.error("downloadSubtitles: caught Exception", e);
                    }
                }
            }
            String fileUrl;
            StringBuilder localSb = null;
            StringBuilder sb = null;

            if (canWrite) {
                fileUrl = path;
                sb = new StringBuilder();
                sb.append(fileUrl.substring(0,fileUrl.lastIndexOf('.')+1)).append(language).append('.').append(subFormat);
                /* Check we can really create the file */
                try {
                    FileEditor editor = FileEditorFactory.getFileEditorForUrl(Uri.parse(sb.toString()), SubtitlesDownloaderActivity.this);
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
                name = FileUtils.getFileNameWithoutExtension(Uri.parse(path));
            localSb = new StringBuilder();
            localSb.append(subsDir.getPath()).append('/').append(name).append('.').append(language).append('.').append(subFormat);
            if(!canWrite)
                sb = localSb;
            String srtURl = sb.toString();
            sb = null;
            OutputStream f =null;
            InputStream in = null;
            GZIPInputStream gzIS = null;
            URL url;
            HttpURLConnection urlConnection = null;
            try {
                url  = new URL(subUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                // We get the first matching subtitle
                FileEditor editor = FileEditorFactory.getFileEditorForUrl(Uri.parse(srtURl), SubtitlesDownloaderActivity.this);
                if(editor.exists())
                        editor.delete();//delete first
                f = editor.getOutputStream();

                //Base64 then gunzip uncompression
                gzIS = new GZIPInputStream(urlConnection.getInputStream());
                int l = 0;
                byte[] buffer = new byte[1024];
                while ((l = gzIS.read(buffer)) != -1) {
                    f.write(buffer, 0, l);
                }
                // f needs to be closed before the copy otherwise STATUS_SHARING_VIOLATION with smbj
                f.close();
                if(indexableUri!=null) {
                    ContentResolver resolver = getContentResolver();
                    VideoDbInfo videoDbInfo = VideoDbInfo.fromUri(resolver, Uri.parse(indexableUri));
                    if (videoDbInfo != null) {
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
                    Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE, Uri.parse(path));
                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                    sendBroadcast(intent);
                }
                catch (Exception e){
                }
                // Update the media database
                if (canWrite) {
                    if(!FileUtils.isLocal(Uri.parse(path))){ // when not local, we need to copy our file
                        editor.copyFileTo(Uri.parse(localSb.toString()),SubtitlesDownloaderActivity.this);
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
                MediaUtils.closeSilently(gzIS);
                f = null;
                gzIS = null;
                in = null;
                map = null;
            }
        }

        private String buildSumup(HashMap<String, ArrayList<String>> success, HashMap<String, ArrayList<String>> fails, boolean single) {
            StringBuilder textToDisplay = new StringBuilder();
            if (single){ // Text for the toast
                for (Entry<String, ArrayList<String>> entry : success.entrySet()){
                    textToDisplay.append(getString(R.string.toast_subloader_sub_found)).append(" ").append(entry.getValue().toString()).append("\n");
                }
                for (Entry<String, ArrayList<String>> entry : fails.entrySet()){
                    textToDisplay.append(getString(R.string.toast_subloader_sub_not_found)).append(" ").append(entry.getValue().toString()).append("\n");
                }
            }else{ // Text for the dialog box
                if (success.size()>0){
                    textToDisplay.append(getString(R.string.dialog_subloader_success)).append("\n");
                    for (Entry<String, ArrayList<String>> entry : success.entrySet()){
                        ArrayList<String> langs = entry.getValue();
                        String filename = entry.getKey();
                        textToDisplay.append("\n- ").append(filename).append(" ").append(langs.toString());
                    }
                    if (fails.size()>0)
                        textToDisplay.append("\n\n");
                }
                if (fails.size()>0){
                    textToDisplay.append(getString(R.string.dialog_subloader_fails)).append("\n");
                    for (Entry<String, ArrayList<String>> entry : fails.entrySet()){
                        ArrayList<String> langs = entry.getValue();
                        String filename = entry.getKey();
                        textToDisplay.append("\n- ").append(filename).append(" ").append(langs.toString());
                    }
                }
            }
            return textToDisplay.toString();
        }

        private void showSumup(final String displayText) {
            mHandler.post(() -> {
                mSumUpDialog = new Builder(SubtitlesDownloaderActivity.this).setTitle(R.string.dialog_subloader_sumup)
                        .setMessage(displayText)
                        .setCancelable(true)
                        .setOnCancelListener(dialog -> finish())
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            mDoNotFinish = true;
                            dialog.dismiss();
                            finish();
                        }).create();
                mSumUpDialog.setOnDismissListener(dialog -> {
                    if (!mDoNotFinish) finish();
                    mDoNotFinish = false;
                });
                //We catch these exceptions because context might disappear while loading/showing the dialog, no matter if we wipe it in onPause()
                try{
                    mSumUpDialog.show();
                } catch (IllegalArgumentException e){
                    log.error("showSumup: caught IllegalArgumentException", e);
                } catch (BadTokenException e){
                    log.error("showSumup: caught BadTokenException", e);
                }
            });
        }

        private void setInitDialog(){
            log.debug("OpenSubtitlesTask: setInitDialog");
            mHandler.post(() -> {
                mDialog = NovaProgressDialog.show(SubtitlesDownloaderActivity.this, "", getString(R.string.dialog_subloader_connecting), true, true, dialog -> {
                    dialog.cancel();
                    stop();
                    finish();
                });
                mDialog.setCanceledOnTouchOutside(false); // to not cancel when tapping the screen out of dialog zone
                mDialog.setOnDismissListener(dialog -> {
                    if(!mDoNotFinish) {
                        dialog.cancel();
                        stop();
                        finish();
                    }
                    mDoNotFinish = false;
                });
                log.debug("OpenSubtitlesTask: setInitDialog setMessage " + getString(R.string.dialog_subloader_connecting));
            });
        }

        private void displayToast(final String message){
            mHandler.post(() -> Toast.makeText(SubtitlesDownloaderActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        private String getIMDBID(String path) {
            String[] selection = {path};
            mCursor = getContentResolver().query(
                    VideoStore.Video.Media.EXTERNAL_CONTENT_URI,   // The content URI of the words table
                    imdbIdProjection,                     // The columns to return for each row
                    WHERE,                          // Selection criteria
                    selection,                     // Selection
                    null);                        // The sort order for the returned rows
            if (mCursor == null || mCursor.getCount() < 1){
                if (mCursor != null) mCursor.close();
                return null;
            } else {
                mCursor.moveToFirst();
                String ID = mCursor.getString(1);
                mCursor.close();
                return ID;
            }
        }
    }

    private void stop(){
        stop = true;
    }

    // Locale ID Control, because of OpenSubtitle support of ISO639-2 codes
    // e.g. French ID can be 'fra' or 'fre', OpenSubtitles considers 'fre' but Android Java Locale provides 'fra'
    // languages supported are available here http://www.opensubtitles.org/addons/export_languages.php
    // check correspondance with donottranslate.xml
    public String getCompliantLanguageID(String language){
        if (language.equals("system"))
            return getCompliantLanguageID(Locale.getDefault().getISO3Language());
        if (language.equals("fra"))
            return "fre";
        if (language.equals("deu"))
            return "ger";
        if (language.equals("zho"))
            return "chi";
        if (language.equals("ces"))
            return "cze";
        if (language.equals("fas"))
            return "per";
        if (language.equals("nld"))
            return "dut";
        if (language.equals("ron"))
            return "rum";
        if (language.equals("slk"))
            return "slo";
        if (language.equals("srp"))
            return "scc";
        return language;
    }
}
