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

package com.archos.mediacenter.video.browser.subtitlesmanager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;

import com.archos.filecorelibrary.CopyCutEngine;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.OperationEngineListener;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.RawListerFactoryWithUpnp;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.utils.VideoUtils;
import com.archos.mediaprovider.ArchosMediaIntent;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Created by alexandre on 12/05/15.
 */
public class SubtitleManager {

    private static final Logger log = LoggerFactory.getLogger(SubtitleManager.class);

    private static final int MAX_SUB_SIZE = 61644800; //not more than 50mo (subs can be really large)
    private CopyCutEngine engine;

    public void abort() {

        if(engine!=null)
            engine.stop();
        if(mListener!=null)
            mListener.onAbort();
    }
    public static class SubtitleFile implements Serializable{

        public final MetaFile2 mFile;
        /**
         * The "name" of the file. We put the langage here if we have it
         */
        public final String mName;

        public SubtitleFile(MetaFile2 file, String name) {
            mFile = file;
            mName = name;
        }

        /**
         * NOTE: we're checking filename and file size only (not Uri)
         * @param o
         * @return
         */
        @Override
        public boolean equals(Object o) {
            SubtitleFile other = (SubtitleFile)o;
            return ((mFile.getName().equals(other.mFile.getName())) &&
                    (mFile.length() == other.mFile.length()));
        }
    }
    public static void deleteAssociatedSubs(Uri fileUri, Context context) {
        try {
            List<MetaFile2> subs = getSubtitleList(fileUri);
            for(MetaFile2 sub : subs){
                sub.getFileEditorInstance(context).delete();
            }
        } catch (Exception e) {
            log.error("deleteAssociatedSubs: caught Exception", e);
        }

    }

    public interface Listener{
        void onAbort();
        void onError(Uri uri, Exception e);
        void onSuccess(Uri uri);
        void onNoSubtitlesFound(Uri uri);
    }

    private final Context mContext;
    private final Handler mHandler;
    private Listener mListener;

    public SubtitleManager(Context context, Listener listener){
        mContext = context;
        mListener = listener;
        mHandler = new Handler(Looper.getMainLooper());
    }

    private static List<String> prefetchedListOfSubs;

    public static List<String> getPreFetchedListOfSubs() {
        log.debug("getPreFetchedListOfSubs: " + Arrays.toString(prefetchedListOfSubs.toArray()));
        return prefetchedListOfSubs;
    }

    public void preFetchHTTPSubtitlesAndPrepareUpnpSubs(final Uri upnpNiceUri, final Uri fileUri){
        log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs on " + upnpNiceUri + ", " + fileUri);
        new Thread() {
            public void run() {
                prefetchedListOfSubs = new ArrayList<>();
                //preparing upnp
                if ("upnp".equalsIgnoreCase(upnpNiceUri.getScheme())) {
                    File subsDir = MediaUtils.getSubsDir(mContext);
                    Uri destinationDir = Uri.fromFile(subsDir);
                    String nameSource = FileUtils.getFileNameWithoutExtension(upnpNiceUri);
                    String nameDest = FileUtils.getFileNameWithoutExtension(fileUri);
                    if (subsDir != null) {
                        for (File file : subsDir.listFiles()) {
                            Uri fileUri = Uri.fromFile(file);
                            String nameWithoutExtension = FileUtils.getFileNameWithoutExtension(fileUri);
                            String extension = MimeUtils.getExtension(FileUtils.getName(fileUri));
                            String lang = MimeUtils.getExtension(nameWithoutExtension);
                            if (nameWithoutExtension.startsWith(nameSource)) {
                                try {
                                    Uri destFile = Uri.withAppendedPath(destinationDir,
                                            nameDest + (lang != null && !lang.isEmpty() ? ("." + lang) : "") + "." + extension
                                    );
                                    try {
                                        FileEditorFactory.getFileEditorForUrl(destFile, mContext).delete();
                                    } catch (Exception e) {
                                        log.error("preFetchHTTPSubtitlesAndPrepareUpnpSubs: caught exception", e);
                                    }
                                    FileEditorFactory.getFileEditorForUrl(Uri.fromFile(file), mContext).copyFileTo(destFile, mContext);
                                    prefetchedListOfSubs.add(destFile.getPath());
                                    log.trace("preFetchHTTPSubtitlesAndPrepareUpnpSubs: copy " + Uri.fromFile(file) + " -> " + destFile.getPath());
                                } catch (Exception e) {
                                    log.error("preFetchHTTPSubtitlesAndPrepareUpnpSubs: caught exception", e);
                                }
                            }
                        }
                    }
                }
                //force find subs in http
                if ("http".equalsIgnoreCase(fileUri.getScheme())) {
                    //check http
                    HttpURLConnection.setFollowRedirects(false);
                    InputStream in = null;
                    FileOutputStream fos = null;
                    int l;
                    byte[] buffer;
                    for (String ext : VideoUtils.getSubtitleExtensions()) {
                        String url = FileUtils.stripExtensionFromName(fileUri.toString()) + "." + ext;
                        String name = FileUtils.getFileNameWithoutExtension(fileUri) + "." + ext;
                        HttpURLConnection con = null;
                        try {
                            con = (HttpURLConnection) new URL(url).openConnection();
                            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                int total = 0;
                                /*     Do not download more than MAX_SUB_SIZE
                                       this is a way to prevent weird server side behaviour
                                       in case http always send a file even, for example the video file, even when not available
                                */
                                if(con.getContentLength()>= MAX_SUB_SIZE) {
                                    //we break because this server isn't trustful, so we won't try next subs
                                    break;
                                }
                                in = con.getInputStream();
                                File subFile = new File(MediaUtils.getSubsDir(mContext), name);
                                log.trace("preFetchHTTPSubtitlesAndPrepareUpnpSubs: copy " + name + " -> " + subFile.getPath());
                                prefetchedListOfSubs.add(subFile.getPath());
                                fos = new FileOutputStream(subFile);
                                l = 0;
                                buffer = new byte[1024];
                                while ((l = in.read(buffer)) != -1) {
                                    total+=l;
                                    if(total >= MAX_SUB_SIZE)
                                        break;
                                    fos.write(buffer, 0, l);
                                }
                                if(total >= MAX_SUB_SIZE) {//delete wrong sub
                                    MediaUtils.closeSilently(in);
                                    MediaUtils.closeSilently(fos);
                                    subFile.delete();
                                    //we break because this server isn't trustful, so we won't try next subs
                                    break;
                                }

                            }
                        } catch (IOException e) {
                            log.error("preFetchHTTPSubtitlesAndPrepareUpnpSubs: caught IOException", e);
                        } finally {
                            if (con != null)
                                con.disconnect();
                            MediaUtils.closeSilently(in);
                            MediaUtils.closeSilently(fos);
                        }

                    }
                }
                else if(!"upnp".equals(fileUri.getScheme())&&UriUtils.isImplementedByFileCore(fileUri)&&!FileUtils.isLocal(fileUri)){
                    privatePrefetchSub(fileUri);
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onSuccess(upnpNiceUri);
                    }
                });
            }
        }.start();
    }


    public void preFetchSubtitles(final Uri videoUri) {
        new Thread(){
            public void run(){
                privatePrefetchSub(videoUri);
            }
        }.start();
    }

    private void privatePrefetchSub(final Uri videoUri) {
        log.debug("privatePrefetchSub");
        try {
            MediaUtils.removeLastSubs(mContext);List<MetaFile2> subs = getSubtitleList(videoUri);
            if (!subs.isEmpty()){

                Uri target = Uri.fromFile(MediaUtils.getSubsDir(mContext));
                engine = new CopyCutEngine(mContext);
                engine.setListener(new OperationEngineListener() {
                    @Override
                    public void onStart() {}
                    @Override
                    public void onProgress(int currentFile, long currentFileProgress,int currentRootFile, long currentRootFileProgress, long totalProgress, double currentSpeed) {}
                    @Override
                    public void onSuccess(Uri target) {
                        log.trace("privatePrefetchSub: copy " + videoUri + " -> " + target);
                        prefetchedListOfSubs.add(target.getPath());
                        if(FileUtils.isLocal(target)){
                            try {
                                Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE, target);
                                mContext.sendBroadcast(intent);
                            }catch (Exception e){}//catching all exceptions for now for quick release
                        }
                    }
                    @Override
                    public void onFilesListUpdate(List<MetaFile2> copyingMetaFiles,List<MetaFile2> rootFiles) {  }
                    @Override
                    public void onEnd() {
                        mListener.onSuccess(videoUri);
                    }
                    @Override
                    public void onFatalError(Exception e) {
                        mListener.onError(videoUri, e);
                    }
                    @Override
                    public void onCanceled() {}
                });
                //force prefixing with video name before copy
                engine.setAllTargetFilesShouldStartWithString(stripExtension(videoUri));
                engine.copy(subs, target, true);
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onNoSubtitlesFound(videoUri);
                    }
                });
            }
        } catch (final Exception e) {
            if(e instanceof NetworkOnMainThreadException)
                throw new NetworkOnMainThreadException();
            else
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onError(videoUri, e);
                    }
                });
        }
    }

    private static String stripExtension(Uri video){
        final String videoFileName = FileUtils.getName(video);
        final String videoExtension = MimeUtils.getExtension(videoFileName);
        String filenameWithoutExtension ;
        if (videoExtension!=null) { // may happen in UPnP
            filenameWithoutExtension = videoFileName.substring(0, videoFileName.length() - (videoExtension.length() + 1));
        } else {
            filenameWithoutExtension = videoFileName;
        }
        return filenameWithoutExtension;
    }

    /**
     * returns a list of metafiles detected as subs in the same directory as the video
     * @param video
     * @return
     * @throws SftpException
     * @throws AuthenticationException
     * @throws JSchException
     * @throws IOException
     */
    public static List<MetaFile2> getSubtitleList(Uri video) throws SftpException, AuthenticationException, JSchException, IOException {
        log.debug("getSubtitleList");
        return getSubtitleList(video, false);
    }

    public static List<MetaFile2> getSubtitleList(Uri video, boolean addAllSubs) throws SftpException, AuthenticationException, JSchException, IOException {
        final Uri parentUri = FileUtils.getParentUrl(video);
        ArrayList<MetaFile2> subs = new ArrayList<>();
        subs.addAll(recursiveSubListing(parentUri,stripExtension(video), addAllSubs));
        return subs;
    }

    private static ArrayList<MetaFile2> recursiveSubListing(Uri parentUri, String filenameWithoutExtension, boolean addAllSubs)  {
        ArrayList<MetaFile2> subs = new ArrayList<>();
        List<MetaFile2> metaFile2List = null;
        try {
            log.debug("recursiveSubListing: " + parentUri.toString());
            metaFile2List = RawListerFactoryWithUpnp.getRawListerForUrl(parentUri).getFileList();
            List<String> subtitlesExtensions = VideoUtils.getSubtitleExtensions();
            String name;
            String nameNoCase;
            String extension;

            if(metaFile2List!=null)
                for (MetaFile2 item : metaFile2List){
                    name = item.getName();
                    nameNoCase = name.toLowerCase();
                    //list files in subs/ or sub/ etc
                    if(item.isDirectory()&&(
                            nameNoCase.equals("subs")||
                                    nameNoCase.equals("sub")||
                                    nameNoCase.equals("subtitles")||
                                    nameNoCase.equals("subtitle")
                    )){
                        subs.addAll(recursiveSubListing(item.getUri(), filenameWithoutExtension, true));
                        continue;
                    }

                    if (!name.startsWith(filenameWithoutExtension)&&!addAllSubs || name.lastIndexOf('.') == -1) {

                        continue;
                    }
                    extension = item.getExtension();
                    if (subtitlesExtensions.contains(extension)){
                        subs.add(item);
                    }
                }
        } catch (IOException e) {
            log.error("recursiveSubListing: caught IOException", e);
        } catch (AuthenticationException e) {
            log.error("recursiveSubListing: caught AuthenticationException", e);
        } catch (SftpException e) {
            log.error("recursiveSubListing: caught SftpException", e);
        } catch (JSchException e) {
            log.error("recursiveSubListing: caught JSchException", e);
        } catch (NullPointerException e) {
            log.error("recursiveSubListing: caught NullPointerException", e);
        }

        return subs;
    }

    /**
     * returns a list of subtitles of the video's directory and of AVP subs directory
     * Only used on leanback, needs to be ported to legacy UI
     * @param video
     * @return
     */
    public List<SubtitleFile> listLocalAndRemotesSubtitles(Uri video, boolean addCache) {
        return listLocalAndRemotesSubtitles(video, false, false, addCache);
    }

    public List<SubtitleFile> listLocalAndRemotesSubtitles(Uri video, boolean addAllSubs, boolean includeIdx, boolean addCache) {
        List<MetaFile2> allFiles = new ArrayList<MetaFile2>();
        List<SubtitleFile> subList = new LinkedList<SubtitleFile>();

        // List files next to the video files
        if(UriUtils.isImplementedByFileCore(video)) try {
            allFiles.addAll(getSubtitleList(video, addAllSubs));
        } catch (IOException e) {
            log.error("listLocalAndRemotesSubtitles: caught IOException", e);
        } catch (AuthenticationException e) {
            log.error("listLocalAndRemotesSubtitles: caught AuthenticationException", e);
        } catch (SftpException e) {
            log.error("listLocalAndRemotesSubtitles: caught SftpException", e);
        } catch (JSchException e) {
            log.error("listLocalAndRemotesSubtitles: caught JSchException", e);
        }

        // addCache controls whether subs in /sdcard/Android/data/org.courville.nova/cache/subtitles (cache online sub download dir) are taken into account
        // this is for not clogging SubtitlesWizard listing since in theory all these files should already be associated to a video automatically
        if (addCache) {
            // List files in the local temporary folder
            String filenameWithoutExtension = stripExtension(video);
            Uri localSubsDirUri = Uri.fromFile(MediaUtils.getSubsDir(mContext));
            if (localSubsDirUri != null) {
                try {
                    List<MetaFile2> files = RawListerFactoryWithUpnp.getRawListerForUrl(localSubsDirUri).getFileList();
                    for (MetaFile2 file : files) {
                        if (file.getName().startsWith(filenameWithoutExtension) || addAllSubs) {
                            allFiles.add(file);
                            log.trace("listLocalAndRemotesSubtitles: cache add " + file.getName());
                        }
                    }
                } catch (Exception e) {
                }
            }
        }

        final List<String> SubtitleExtensions = VideoUtils.getSubtitleExtensions();

        for (MetaFile2 file : allFiles) {
            // Check file starting with same name
            try {

                // Check if it is a subtitles file
                final String fileExtension = file.getExtension();
                if (fileExtension != null) {
                    String subtitleName = null;
                    if (SubtitleExtensions.contains(fileExtension.toLowerCase(Locale.US))&&(!fileExtension.toLowerCase(Locale.US).equals("idx") || includeIdx)) {
                        //log.debug("Found external subtitle file: " + file.getUri().toString());
                        // Check if there is    a language extension
                        String language = "";
                        final String subFilename = file.getName();
                        final String subFilenameWithoutExtension = subFilename.substring(0, subFilename.length() - (fileExtension.length() + 1));
                        final String languageExtension = getLanguage(subFilenameWithoutExtension);
                        if (languageExtension != null) {
                            // note: does not work for fra but ok for fr or fre
                            //final Locale locale = new Locale.Builder().setLanguage(languageExtension).build();
                            //subtitleName = locale.getDisplayLanguage();
                            subtitleName = ISO639codes.getLanguageNameForCode(mContext, languageExtension);
                        }
                        // In case we don't have the subtitle language we put the full file name
                        if (subtitleName==null || subtitleName.isEmpty()) {
                            subtitleName = subFilename;
                        }
                        subList.add(new SubtitleFile(file, subtitleName));
                        log.trace("listLocalAndRemotesSubtitles: add external " + file.getUri().toString() + "(" + subtitleName +")");
                    }
                }
            } catch (Exception e) {
            }
        }
        // Remove duplicates due to the fact that the remote subtitles may have already been downloaded to the tmp folder
        List<SubtitleFile> subListUnique = new LinkedList<SubtitleFile>();
        for (SubtitleFile f : subList) {
            if (!subListUnique.contains(f)) {
                subListUnique.add(f);
            }
        }

        return subListUnique;
    }


    static public String getLanguage(String filename) {
        if (filename == null)
            return null;
        int dotPos = filename.lastIndexOf('.');
        int dashPos = filename.lastIndexOf('_');
        int pos = -1;
        if(dotPos>dashPos) {
            pos = dotPos;
        }
        else {
            pos = dashPos;
        }
        if (pos >= 0 && pos < filename.length()) {
            return filename.substring(pos + 1).toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
