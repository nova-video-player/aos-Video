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
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager.BadTokenException;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.MetaFileFactoryWithUpnp;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.videodb.VideoDbInfo;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.TorrentObserverService;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.archos.mediaprovider.video.VideoStore;

import org.xmlrpc.android.XMLRPCClient;
import org.xmlrpc.android.XMLRPCException;

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
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class SubtitlesDownloaderActivity extends Activity{
    public static final String FILE_URLS = "fileUrls";
    public static final String FILE_URL = "fileUrl";
    public static final String FILE_NAMES = "fileNames"; //friendly name for Upnp
    public static final String FILE_SIZES = "fileSizes";

    private final String TAG = "SubtitlesDownloaderActivity";
    //to distinguished program dismiss and users
    private boolean mDoNotFinish;
    private final String OpenSubtitlesAPIUrl = "https://api.opensubtitles.org/xml-rpc";
    private final String USER_AGENT = "ArchosMediaCenter";
    private SharedPreferences sharedPreferences;
    private File subsDir;
    Handler mHandler;
    HashMap<String, Long> mFileSizes = null;
    HashMap<String, String> mFriendlyFileNames = null; // they need to have an extension
    HashMap<String, String> mIndexableUri = null;
    boolean stop = false;
    private ProgressDialog mDialog;
    private AlertDialog mSumUpDialog;
    private OpenSubtitlesTask mOpenSubtitlesTask = null;

    private Cursor mCursor;
    //database URI : VideoStore.Video.Media.EXTERNAL_CONTENT_URI
    private static final String[] mProjection = {
            VideoStore.Video.VideoColumns._ID,
            VideoStore.Video.VideoColumns.SCRAPER_M_IMDB_ID,
            VideoStore.Video.VideoColumns.SCRAPER_E_IMDB_ID
    };

    private static final String WHERE = VideoStore.Video.VideoColumns.DATA + "=?";
    private static final int FIRST_PASS = 0;
    private static final int SECOND_PASS = 1;
    private static final int THIRD_PASS = 2;

    private static class NonConfigurationInstance {
        public ProgressDialog progressDialog;
        public AlertDialog sumUpDialog;
    };

    @SuppressWarnings({"unchecked", "serial"})
    @Override
    public void onStart() {
        super.onStart();

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
            if(ArchosUtils.isNetworkConnected(this)){
                sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                final Intent intent = getIntent();
                ArrayList<String> fileUrls = null;
                if (intent.hasExtra(FILE_URL)){
                    fileUrls = new ArrayList<String>(){{add(intent.getStringExtra(FILE_URL));}};
                } else if (intent.hasExtra(FILE_URLS)){
                    fileUrls = intent.getStringArrayListExtra(FILE_URLS);
                } else {
                    finish();
                    return;
                }
                if (intent.hasExtra(FILE_SIZES))
                    mFileSizes = (HashMap<String, Long>)intent.getSerializableExtra(FILE_SIZES);
                else
                    mFileSizes = new HashMap<>();
                if (intent.hasExtra(FILE_NAMES)) {
                    mFriendlyFileNames = (HashMap<String, String>) intent.getSerializableExtra(FILE_NAMES);

                }
                else
                    mFriendlyFileNames = new HashMap<>();
                mOpenSubtitlesTask = new OpenSubtitlesTask();
                for(String uri : fileUrls)
                    mIndexableUri.put(uri, uri);
                mOpenSubtitlesTask.execute(fileUrls, getSubLangValue());
                subsDir = MediaUtils.getSubsDir(this);
            } else {
                Builder dialogNoNetwork;

                dialogNoNetwork = new AlertDialog.Builder(this);
                dialogNoNetwork.setCancelable(true);
                dialogNoNetwork.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                });

                dialogNoNetwork.setTitle(R.string.dialog_subloader_nonetwork_title);
                dialogNoNetwork.setMessage(getString(R.string.dialog_subloader_nonetwork_message));
                Dialog d = dialogNoNetwork.create();
                d.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        if (!mDoNotFinish)
                            finish();
                        mDoNotFinish = false;
                    }
                });
                d.show();
            }
        }
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        if (mOpenSubtitlesTask != null) {
            Log.d(TAG, "mOpenSubtitlesTask.cancel");
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

    public Object onRetainNonConfigurationInstance() {
        // The activity is going to be destroyed after a rotation => save the state of the dialogs
        NonConfigurationInstance nci = new NonConfigurationInstance();
        nci.progressDialog = mDialog;
        nci.sumUpDialog = mSumUpDialog;
        return nci;
    }

    private ArrayList<String> getSubLangValue() {
        Set<String> langDefault = new HashSet<String>();
        langDefault.add("system");
        Set<String> languages = sharedPreferences.getStringSet("languages_list", langDefault);
        final ArrayList<String> languageDefault = new ArrayList<String>(languages);
        langDefault = null;
        languages = null;
        return languageDefault;
    }

    private class OpenSubtitlesTask extends AsyncTask<ArrayList<String>, Integer, Void>{
        HashMap<String, Object> map = null;
        XMLRPCClient client = new XMLRPCClient(OpenSubtitlesAPIUrl);
        private String token = null;


        @Override
        protected void onPreExecute() {
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
            if (stop && (mSumUpDialog == null || !mSumUpDialog.isShowing())) {
                finish();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values == null || values.length != 2 || isCancelled()) {
                return;
            }

            int progress = values[0];
            int filesNumber = values[1];

            if (progress == 0) {
                if (mDialog != null && mDialog.isShowing()) {
                    mDoNotFinish = true;
                    mDialog.dismiss();
                }

                mDialog = new ProgressDialog(SubtitlesDownloaderActivity.this);
                mDialog.setCancelable(false);
                mDialog.setOnKeyListener(new OnKeyListener() {
                    // Called when pressing the BACK button
                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                        stop();
                        mDialog.dismiss();
                        finish();
                        return false;
                    }
                });


                mDialog.setMessage(getString(R.string.dialog_subloader_downloading));
                if (filesNumber > 1){
                    mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mDialog.setProgress(progress);
                    mDialog.setMax(filesNumber);
                }
                mDialog.show();
            } else {
                mDialog.setProgress(progress);
                mDialog.setMax(filesNumber);
            }
        }

        /**************************************************
         *        OpenSubtitles framework
         *************************************************/
        @SuppressWarnings("unchecked")
        public boolean logIn() {
            try {
                map = ((HashMap<String, Object>) client.call("LogIn","","","fre",USER_AGENT));
                token = (String) map.get("token");
            } catch (XMLRPCException e) {
                displayToast(getString(R.string.toast_subloader_service_unreachable));
                if (mDialog != null) {
                    mDoNotFinish = true;
                    mDialog.dismiss();
                }
                stop = true;
                return false;
            } catch (Throwable e){ //for various service outages
                e.printStackTrace();
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
                e1.printStackTrace();
                Log.w("subtitles", "XMLRPCException");
            } catch (Throwable e){ //for various service outages
                e.printStackTrace();
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
                map = ((HashMap<String, Object>)  client.call("NoOperation", token));
            } catch (XMLRPCException e1) {
                e1.printStackTrace();
                Log.w("subtitles", "XMLRPCException");
            } catch (Throwable e){ //for various service outages
                e.printStackTrace();
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
            List<HashMap<String, String>> videoSearchList;
            videoSearchList = prepareRequestList(fileUrls, languages, index, FIRST_PASS);
            Object[] subtitleMaps = null;
            try {
                map = (HashMap<String, Object>) client.call("SearchSubtitles", token, videoSearchList);
            } catch (Throwable e) { //for various service outages
                Log.e("subs", "errrr", e);
                stop = true;
                displayToast(getString(R.string.toast_subloader_service_unreachable));
                return;
            }
            if(!isCancelled() && map.get("data") instanceof Object[]){
                subtitleMaps = ((Object[]) map.get("data"));
                int progress = 0;
                publishProgress(new Integer[] {progress, fileUrls.size()});
                String lastProcessedFile = "";
                if (subtitleMaps.length > 0){
                    String srtFormat, movieHash, fileUrl, fileName, subLanguageID, subDownloadLink;
                    for (int i = 0 ; i < subtitleMaps.length ; i++){
                        if (isCancelled())
                            break;
                        srtFormat = ((HashMap<String, String>) subtitleMaps[i]).get("SubFormat");
                        movieHash = ((HashMap<String, String>) subtitleMaps[i]).get("MovieHash");
                        subLanguageID = ((HashMap<String, String>) subtitleMaps[i]).get("SubLanguageID");
                        subDownloadLink = ((HashMap<String, String>) subtitleMaps[i]).get("SubDownloadLink");
                        fileUrl = index.get(movieHash);
                        if(fileUrl==null) {
                            publishProgress(new Integer[] {++progress, fileUrls.size()});
                            continue;
                        }
                        fileName = getFriendlyFilename(fileUrl);
                        if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)){
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
                            publishProgress(new Integer[] {++progress, fileUrls.size()});
                        }
                    }
                }
            }
            if (videoSearchList != null)
                videoSearchList.clear();
            index.clear();
            subtitleMaps = null;

            //Second pass
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName)){
                    notFoundFiles.add(fileUrl);
                }
            }
            if (!isCancelled() && !notFoundFiles.isEmpty() && 
                    !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, SECOND_PASS)).isEmpty()) {
                try {
                    map = (HashMap<String, Object>) client.call("SearchSubtitles", token,
                            videoSearchList);
                } catch (Throwable e) { //for various service outages
                    Log.e("subs", "errrr", e);
                    stop = true;
                    displayToast(getString(R.string.toast_subloader_service_unreachable));
                    return;
                }
                if (map.get("data") instanceof Object[]) {
                    subtitleMaps = ((Object[]) map.get("data"));
                    int progress = 0;
                    publishProgress(new Integer[] {progress, fileUrls.size()});
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
                            if (fileUrl == null){ //we keep only result for exact matching name
                                continue;
                            }
                            fileName = getFriendlyFilename(fileUrl);
                            if (success.containsKey(fileName) && success.get(fileName).contains(subLanguageID)){
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
                                publishProgress(new Integer[] {++progress, fileUrls.size()});

                            }
                        }
                    }
                } else
                    Log.d("subs", "FAIL " + map.get("data"));
            }

            boolean doNotFinish = false;

            //Second pass
            notFoundFiles.clear();
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName)){
                    notFoundFiles.add(fileUrl);
                }
            }
            if (!isCancelled() &&single&& !notFoundFiles.isEmpty() &&
                    !(videoSearchList = prepareRequestList(notFoundFiles, languages, index, THIRD_PASS)).isEmpty()) {
                try {
                    map = (HashMap<String, Object>) client.call("SearchSubtitles", token,
                            videoSearchList);
                } catch (Throwable e) { //for various service outages
                    Log.e("subs", "errrr", e);
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

                    }
                } else
                    Log.d("subs", "FAIL " + map.get("data"));
            }


            if(doNotFinish)
                return;
            //fill fails list
            for (String fileUrl : fileUrls){
                String fileName = getFriendlyFilename(fileUrl);
                if (!success.containsKey(fileName)){
                    ArrayList<String> langs = new ArrayList<String>();
                    for (String language : languages) {
                        langs.add(getCompliantLanguageID(language));
                    }
                    fails.put(fileName, langs);
                } else {
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
                    setResult(Activity.RESULT_OK);
                }
            }
            logOut();
            return;
        }

        private List<HashMap<String, String>> prepareRequestList(final ArrayList<String> fileUrls,
                ArrayList<String> languages, HashMap<String, String> index, int pass) {
            List<HashMap<String, String>> videoSearchList;
            videoSearchList = new ArrayList<HashMap<String, String>>();
            for (String fileUrl : fileUrls){
                if (stop)
                    break;
                String hash = null, tag = null;
                long fileLength = 0;
                if (pass == FIRST_PASS) { //first pass, search by index
                    fileLength = 0;
                    MetaFile2 mf2=null;
                    if(!fileUrl.startsWith("http://")) { // if not http, we will need metafile2 even for upnp (file length + streaming uri)
                        try {
                            mf2 = MetaFileFactoryWithUpnp.getMetaFileForUrl(Uri.parse(fileUrl));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if(mf2==null)
                            continue;
                    }

                    if (fileUrl.startsWith("upnp://")) { //request streaming uri
                        // TODO need also length
                            Uri newUri = mf2.getStreamingUri();
                            if(newUri != null){

                                String oldFileUrl = fileUrl;
                                fileUrl = newUri.toString();
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
                        else
                            continue;
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
                            e.printStackTrace();
                            continue;
                        } catch (IOException e) {
                            e.printStackTrace();
                            continue;
                        } finally {
                            if (urlConnection != null)
                                urlConnection.disconnect();
                        }
                    }else{
                        try {
                            fileLength = mf2.length();
                            hash = OpenSubtitlesHasher.computeHash(Uri.parse(fileUrl), fileLength);
                        } catch (Exception e) { // failure for this file
                            e.printStackTrace();
                            break;
                        }
                    }
                    if (hash == null)
                        continue;
                } else { //Second pass, search by TAG (filename)

                    tag = getFriendlyFilename(fileUrl);
                }
                HashMap<String, String> video;
                for (String item : languages){
                    if (stop)
                        break;
                    String languageID = getCompliantLanguageID(item);

                    video = new HashMap<String, String>();
                    video.put("sublanguageid", languageID);
                    if (pass == FIRST_PASS){
                        index.put(hash, fileUrl);
                        video.put("moviehash", hash);
                        video.put("moviebytesize", String.valueOf(fileLength));
                    } else {
                        if(pass==SECOND_PASS)
                            video.put("tag", tag);
                        else
                            video.put("query", tag);
                        int dotPos = tag.lastIndexOf('.');

                        if (dotPos > -1){
                            index.put(tag.substring(0, dotPos), fileUrl);
                        }else {
                            index.put(tag, fileUrl);
                        }
                        index.put(getFriendlyFilename(fileUrl), fileUrl);
                    }
                    videoSearchList.add(video);
                }
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
            ((TextView) view.findViewById(R.id.video_name)).setText(Html.fromHtml(getString(R.string.select_sub_file, getFriendlyFilename(videoFilePath))));
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
                            String lang = "";
                            if (displayLang) {
                                for (HashMap<String, String> item : items.get(name)) {
                                    if (!lang.contains(item.get("SubLanguageID") + " "))
                                        lang += item.get("SubLanguageID") + " ";
                                }
                                ((TextView) view.findViewById(R.id.lang)).setText(lang);
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
                                    publishProgress(new Integer[]{0, 1});
                                    for (HashMap<String, String> item : items.get(keys.get(i))) {
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
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }
                    })
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
                        e.printStackTrace();
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
            if(name==null||name.isEmpty())
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
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Throwable e){ //for various service outages
                e.printStackTrace();
            }finally{
                MediaUtils.closeSilently(f);
                MediaUtils.closeSilently(in);
                MediaUtils.closeSilently(gzIS);
                f = null;
                gzIS = null;
                in = null;
                map = null;
            }
            return;
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
            mHandler.post(new Runnable(){

                public void run() {
                    mSumUpDialog = new AlertDialog.Builder(SubtitlesDownloaderActivity.this).setTitle(R.string.dialog_subloader_sumup)
                            .setMessage(displayText)
                            .setCancelable(true)
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                public void onCancel(DialogInterface dialog) {
                                    finish();
                                }
                            })

                            .setPositiveButton(android.R.string.yes, new OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    mDoNotFinish = true;
                                    dialog.dismiss();
                                    finish();
                                }
                            }).create();
                    mSumUpDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            if (!mDoNotFinish)
                                finish();
                            mDoNotFinish = false;
                        }
                    });
                    //We catch these exceptions because context might disappear while loading/showing the dialog, no matter if we wipe it in onPause()
                    try{
                        mSumUpDialog.show();
                    } catch (IllegalArgumentException e){
                        e.printStackTrace();
                    } catch (BadTokenException e){
                        e.printStackTrace();
                    }
                }
            });
        }

        private void setInitDialog(){
            mHandler.post(new Runnable(){

                public void run() {
                    mDialog = new ProgressDialog(SubtitlesDownloaderActivity.this);
                    mDialog.setCancelable(true);
                    mDialog.setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            stop();
                            finish();
                        }
                    });
                    mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            if(!mDoNotFinish)
                                finish();
                            mDoNotFinish = false;
                        }
                    });
                    mDialog.setMessage(getString(R.string.dialog_subloader_connecting));
                    mDialog.show();
                }
            });
        }

        private void displayToast(final String message){
            mHandler.post(new Runnable() {
                public void run() {
                    Toast.makeText(SubtitlesDownloaderActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        private String getIMDBID(String path){
            String[] selection = {path};
            mCursor = getContentResolver().query(
                    VideoStore.Video.Media.EXTERNAL_CONTENT_URI,   // The content URI of the words table
                    mProjection,                     // The columns to return for each row
                    WHERE,                          // Selection criteria
                    selection,                     // Selection
                    null);                        // The sort order for the returned rows
            if (mCursor == null || mCursor.getCount() < 1){
                if (mCursor != null) {
                    mCursor.close();
                }
                return null;
            } else {
                mCursor.moveToFirst();
                String ID = mCursor.getString(1);
                if (ID == null){
                    ID = mCursor.getString(2);
                }
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
        return language;
    }
}
