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

import static com.archos.filecorelibrary.FileUtils.getName;
import static com.archos.filecorelibrary.FileUtils.stripExtensionFromName;
import static com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.getLanguageNameForLetterCode;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;

import com.archos.filecorelibrary.AuthenticationException;
import com.archos.filecorelibrary.CopyCutEngine;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.OperationEngineListener;
import com.archos.mediacenter.filecoreextension.UriUtils;
import com.archos.mediacenter.filecoreextension.upnp2.RawListerFactoryWithUpnp;
import com.archos.mediacenter.utils.ISO639codes;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.utils.VideoMetadata;
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if(mListener!=null) {
            if (log.isDebugEnabled()) log.debug("abort: calling onAbort");
            mListener.onAbort();
        }
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
            // test checks if the file is already in the list via fileSize and fleName
            SubtitleFile other = (SubtitleFile)o;
            //log.trace("equals: {} vs {} ({} vs {})", mFile.getStreamingUri(), other.mFile.getStreamingUri(), mFile.length(), other.mFile.length());
            // do not compare entire fileName but only trailing part (i.e. "en.srt" instead of "videoName.en.srt") to capture copy of Subs/en.srt to videoName.en.srt by privatePrefetchSub
            //return ((mFile.getName().equals(other.mFile.getName())) && (mFile.length() == other.mFile.length()));
            return ((mFile.getName().endsWith(other.mFile.getName())) && (mFile.length() == other.mFile.length()));
        }
    }
    public static void deleteAssociatedSubs(Uri fileUri, Context context) {
        if (log.isDebugEnabled()) log.debug("deleteAssociatedSubs: {}", fileUri.toString());
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

    /**
     * Cache entry for subtitle enumeration and AVOS processing results
     * Holds both raw file list and processed metadata
     */
    private static class SubtitleCacheEntry {
        List<SubtitleFile> subtitleFiles;
        VideoMetadata processedMetadata;

        SubtitleCacheEntry(List<SubtitleFile> files) {
            this.subtitleFiles = files;
            this.processedMetadata = null;
        }

        void updateMetadata(VideoMetadata metadata) {
            this.processedMetadata = metadata != null ? new VideoMetadata(metadata) : null;
        }
    }

    private final Context mContext;
    private final Handler mHandler;
    private Listener mListener;
    // Thread-safe cache for subtitle enumeration results keyed by video URI string
    // Using String key (Uri.toString()) instead of Uri object to handle Uri object inequality
    private static final ConcurrentHashMap<String, SubtitleCacheEntry> mSubtitleCache = new ConcurrentHashMap<>();

    public SubtitleManager(Context context, Listener listener){
        mContext = context;
        mListener = listener;
        mHandler = new Handler(Looper.getMainLooper());
    }

    private static List<String> prefetchedListOfSubs;

    private static List<String> listOfLocalSubs;

    public static List<String> getPreFetchedListOfSubs() {
        if (log.isDebugEnabled()) log.debug("getPreFetchedListOfSubs: {}", Arrays.toString(prefetchedListOfSubs.toArray()));
        return prefetchedListOfSubs;
    }

    public static List<String> getListOfLocalSubs() {
        if (listOfLocalSubs != null) if (log.isDebugEnabled()) log.debug("getListOfLocalSubs: {}", Arrays.toString(listOfLocalSubs.toArray()));
        else if (log.isDebugEnabled()) log.debug("getListOfLocalSubs: null");
        return listOfLocalSubs;
    }

    /**
     * Get cached subtitle files for a video if they exist
     * Cache is invalidated when exiting VideoDetailsFragment or VideoInfoActivityFragment
     * @param videoUri The video file URI
     * @return List of SubtitleFiles if cached, null otherwise
     */
    public static List<SubtitleFile> getCachedSubtitleFiles(Uri videoUri) {
        String uriKey = videoUri.toString();
        SubtitleCacheEntry entry = mSubtitleCache.get(uriKey);
        if (entry != null) {
            if (log.isDebugEnabled()) log.debug("getCachedSubtitleFiles: cache hit for {}", uriKey);
            return entry.subtitleFiles;
        }
        if (log.isDebugEnabled()) log.debug("getCachedSubtitleFiles: cache miss for {}", uriKey);
        return null;
    }

    /**
     * Get cached processed AVOS metadata if it exists
     * @param videoUri The video file URI
     * @return VideoMetadata if cached, null otherwise
     */
    public static VideoMetadata getCachedProcessedMetadata(Uri videoUri) {
        String uriKey = videoUri.toString();
        SubtitleCacheEntry entry = mSubtitleCache.get(uriKey);
        if (entry != null && entry.processedMetadata != null) {
            if (log.isDebugEnabled()) log.debug("getCachedProcessedMetadata: cache hit for {}", uriKey);
            return new VideoMetadata(entry.processedMetadata);
        }
        if (log.isDebugEnabled()) log.debug("getCachedProcessedMetadata: cache miss for {}", uriKey);
        return null;
    }

    /**
     * Cache enumerated subtitle files for a video
     * @param videoUri The video file URI
     * @param subtitleFiles List of found subtitles
     */
    public static void cacheSubtitleFiles(Uri videoUri, List<SubtitleFile> subtitleFiles) {
        String uriKey = videoUri.toString();
        mSubtitleCache.put(uriKey, new SubtitleCacheEntry(subtitleFiles));
        if (log.isDebugEnabled()) log.debug("cacheSubtitleFiles: cached {} subtitles for {}", subtitleFiles.size(), uriKey);
    }

    /**
     * Cache processed AVOS metadata for a video
     * @param videoUri The video file URI
     * @param metadata The processed VideoMetadata from AVOS
     */
    public static void cacheProcessedMetadata(Uri videoUri, VideoMetadata metadata) {
        String uriKey = videoUri.toString();
        SubtitleCacheEntry entry = mSubtitleCache.get(uriKey);
        if (entry != null) {
            entry.updateMetadata(metadata);
            if (log.isDebugEnabled()) log.debug("cacheProcessedMetadata: cached metadata for {}", uriKey);
        } else {
            // If cache entry doesn't exist, create one (shouldn't normally happen but be safe)
            SubtitleCacheEntry newEntry = new SubtitleCacheEntry(new ArrayList<>());
            newEntry.updateMetadata(metadata);
            mSubtitleCache.put(uriKey, newEntry);
            if (log.isDebugEnabled()) log.debug("cacheProcessedMetadata: created new cache entry with metadata for {}", uriKey);
        }
    }

    /**
     * Invalidate cached subtitle data for a specific video
     * Called when exiting VideoDetailsFragment or VideoInfoActivityFragment to ensure fresh enumeration on next browse
     * @param videoUri The video file URI to invalidate cache for
     */
    public static void invalidateCache(Uri videoUri) {
        String uriKey = videoUri.toString();
        mSubtitleCache.remove(uriKey);
        if (log.isDebugEnabled()) log.debug("invalidateCache: cleared cache for {}", uriKey);
    }

    private static String encodeFileName(String fileName) {
        if (fileName == null) return null;
        try {
            String encoded = URLEncoder.encode(fileName, "UTF-8");
            return encoded.isEmpty() ? fileName : encoded;
        } catch (Exception e) {
            log.warn("encodeFileName: failed for {}", fileName, e);
            return fileName;
        }
    }

    public void preFetchHTTPSubtitlesAndPrepareUpnpSubs(final Uri upnpNiceUri, final Uri fileUri){
        if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs on {}, {}", upnpNiceUri, fileUri);
        Thread mainThread = new Thread() {
            public void run() {
                prefetchedListOfSubs = new ArrayList<>();
                // Track pending enumeration operations to ensure completion before calling onSuccess
                AtomicInteger pendingOps = new AtomicInteger(0);
                // Guard to ensure onSuccess is only called once, even if completion and timeout race
                AtomicInteger successCalled = new AtomicInteger(0); // 0=not called, 1=called
                boolean hasPrivatePrefetch = false;

                //preparing upnp
                if ("upnp".equalsIgnoreCase(upnpNiceUri.getScheme())) {
                    File subsDir = MediaUtils.getSubsDir(mContext);
                    Uri destinationDir = Uri.fromFile(subsDir);
                    String nameSource = FileUtils.getFileNameWithoutExtension(upnpNiceUri);
                    String nameDest = FileUtils.getFileNameWithoutExtension(fileUri);
                    if (subsDir != null) {
                        try {
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
                                        if (log.isTraceEnabled()) log.trace("preFetchHTTPSubtitlesAndPrepareUpnpSubs: copy {} -> {}", nameWithoutExtension, destFile.getPath());
                                    } catch (Exception e) {
                                        log.error("preFetchHTTPSubtitlesAndPrepareUpnpSubs: caught exception", e);
                                    }
                                }
                            }
                        } catch (NullPointerException npe) {
                            log.error("preFetchHTTPSubtitlesAndPrepareUpnpSubs: caught NullPointerException for {}", subsDir.getPath(), npe);
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
                        String url = FileUtils.getParentUrl(fileUri).toString() + FileUtils.getFileNameWithoutExtension(fileUri) + "." + ext;
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
                                File subFile = new File(MediaUtils.getSubsDir(mContext), encodeFileName(name));
                                if (log.isTraceEnabled()) log.trace("preFetchHTTPSubtitlesAndPrepareUpnpSubs: copy {} -> {}", name, subFile.getPath());
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
                else {
                    if (!"upnp".equals(fileUri.getScheme()) && UriUtils.isImplementedByFileCore(fileUri)) {
                        if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: trying to fetch subtitles from {}", fileUri);
                        hasPrivatePrefetch = true;
                        pendingOps.incrementAndGet();
                        if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: pendingOps incremented to {}", pendingOps.get());
                        Thread fetchThread = new Thread(() -> {
                            try {
                                privatePrefetchSub(fileUri);
                                if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: privatePrefetchSub completed for {}", fileUri);
                            } finally {
                                int remaining = pendingOps.decrementAndGet();
                                if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: pendingOps decremented to {}", remaining);
                                if (remaining == 0 && successCalled.compareAndSet(0, 1)) {
                                    if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: all operations completed, calling onSuccess");
                                    callOnSuccess(upnpNiceUri);
                                }
                            }
                        });
                        fetchThread.setName("SubtitleFetch-" + (fileUri.getLastPathSegment() != null ? fileUri.getLastPathSegment() : "unknown"));
                        fetchThread.start();
                    }
                }

                // If no async operations were scheduled, call onSuccess immediately
                if (!hasPrivatePrefetch) {
                    if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: no async operations scheduled, calling onSuccess immediately");
                    if (successCalled.compareAndSet(0, 1)) {
                        callOnSuccess(upnpNiceUri);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: waiting for {} pending operations", pendingOps.get());
                }

                // Safety timeout: if onSuccess hasn't been called within 5 seconds, call it anyway.
                // This prevents indefinite hanging if async operations get stuck on network shares.
                // Not applied to local files: local I/O always completes, just slowly for large
                // directories, and firing early would launch the external player before subtitles
                // are collected or with an incomplete list.
                if (hasPrivatePrefetch && !FileUtils.isLocal(fileUri)) {
                    Thread timeoutThread = new Thread(() -> {
                        try {
                            Thread.sleep(5000);
                            // Guard: only call if not already called by normal completion
                            if (successCalled.compareAndSet(0, 1)) {
                                log.warn("preFetchHTTPSubtitlesAndPrepareUpnpSubs: 5 second timeout reached, forcing onSuccess completion");
                                callOnSuccess(upnpNiceUri);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    timeoutThread.setName("SubtitleFetchTimeout");
                    timeoutThread.start();
                }
            }
        };
        mainThread.setName("SubtitleFetchMain-" + (fileUri.getLastPathSegment() != null ? fileUri.getLastPathSegment() : "unknown"));
        mainThread.start();
    }

    /**
     * Posts onSuccess callback to main thread handler with logging
     * Uses a latch mechanism internally to prevent duplicate calls
     */
    private void callOnSuccess(Uri uri) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (log.isDebugEnabled()) log.debug("preFetchHTTPSubtitlesAndPrepareUpnpSubs: onSuccess handler executing");
                if (mListener != null) {
                    mListener.onSuccess(uri);
                } else {
                    log.error("preFetchHTTPSubtitlesAndPrepareUpnpSubs: mListener is null!");
                }
            }
        });
    }


    public void preFetchSubtitles(final Uri videoUri) {
        new Thread(){
            public void run(){
                privatePrefetchSub(videoUri);
            }
        }.start();
    }

    private void privatePrefetchSub(final Uri videoUri) {
        if (log.isDebugEnabled()) log.debug("privatePrefetchSub");
        try {
            MediaUtils.removeLastSubs(mContext);
            String baseName = getName(videoUri);
            // Compute prefix early so we can purge stale cached subs for this video before
            // copying fresh ones. Without this, renamed copies from a previous run (e.g.
            // video.Ep02.srt, video.Ep03.srt …) survive in the cache and pollute the listing.
            String prefix = stripExtension(videoUri) + ".";
            File subsDir = MediaUtils.getSubsDir(mContext);
            if (subsDir != null) {
                File[] cachedSubs = subsDir.listFiles();
                if (cachedSubs != null) {
                    for (File f : cachedSubs) {
                        String fname = f.getName();
                        if (!fname.startsWith(prefix)) continue;
                        // Extract the segment between the video prefix and the final subtitle extension.
                        // e.g. "videoName.en.srt"  → segment = "en"   (language code → keep)
                        //      "videoName.srt"      → segment = ""     (exact match → keep)
                        //      "videoName.Ep02 - Title.srt" → segment = "Ep02 - Title" (episode title → delete)
                        String afterPrefix = fname.substring(prefix.length()); // e.g. "en.srt" or "Ep02 - Title.srt"
                        int lastDot = afterPrefix.lastIndexOf('.');
                        // lastDot == -1 means afterPrefix is just a bare extension (e.g. "srt"),
                        // which is an exact-name match (videoName.srt) — always preserve.
                        // Otherwise segment is the part between prefix and final extension.
                        String segment = (lastDot > 0) ? afterPrefix.substring(0, lastDot) : "";
                        // Preserve exact-name cached sub (segment is empty) or a recognized language code
                        if (segment.isEmpty() || ISO639codes.isletterCode(segment)) {
                            if (log.isDebugEnabled()) log.debug("privatePrefetchSub: keeping cached sub {} (segment='{}')", fname, segment);
                            continue;
                        }
                        if (log.isDebugEnabled()) log.debug("privatePrefetchSub: removing stale cached sub {} (segment='{}')", fname, segment);
                        f.delete();
                    }
                }
            }
            List<MetaFile2> subs;
            // do not prefetch first level subs for local files to avoid duplicate on local videos since they are already captured afterwards
            if (FileUtils.isLocal(videoUri)) subs = getSubtitleListExcludingFirstLevelSubs(videoUri);
            else subs = getSubtitleList(videoUri);
            if (!subs.isEmpty() && subsDir != null){
                Uri target = Uri.fromFile(subsDir);
                final CountDownLatch latch = new CountDownLatch(subs.size()); // Initialize the CountDownLatch with a count of 1
                engine = new CopyCutEngine(mContext);
                engine.setListener(new OperationEngineListener() {
                    @Override
                    public void onStart() {}
                    @Override
                    public void onProgress(int currentFile, long currentFileProgress,int currentRootFile, long currentRootFileProgress, long totalProgress, double currentSpeed) {}
                    @Override
                    public void onSuccess(Uri target) {
                        if (log.isTraceEnabled()) log.trace("privatePrefetchSub: onSuccess copy {} -> {}", baseName, target);
                        prefetchedListOfSubs.add(target.getPath());
                        if(FileUtils.isLocal(target)){
                            try {
                                Intent intent = new Intent(ArchosMediaIntent.ACTION_VIDEO_SCANNER_METADATA_UPDATE, target);
                                mContext.sendBroadcast(intent);
                            } catch (Exception e) {}//catching all exceptions for now for quick release
                        }
                        latch.countDown(); // Decrement the count of the latch when the operation is successful
                    }
                    @Override
                    public void onFilesListUpdate(List<MetaFile2> copyingMetaFiles,List<MetaFile2> rootFiles) {  }
                    @Override
                    public void onEnd() {
                        if (log.isDebugEnabled()) log.debug("privatePrefetchSub: onEnd");
                        latch.countDown(); // Decrement the count of the latch when the operation ends
                    }
                    @Override
                    public void onFatalError(Exception e) {
                        if (log.isDebugEnabled()) log.debug("privatePrefetchSub: onFatalError calling onError");
                        mListener.onError(videoUri, e);
                        latch.countDown(); // Decrement the count of the latch when there is an error
                    }
                    @Override
                    public void onCanceled() {
                        latch.countDown(); // Decrement the count of the latch when the operation is canceled
                    }
                });
                //force prefixing with video name before copy if this is not the case i.e. Subs/en.srt -> videoName.en.srt,
                // /!\ it will cause subs duplicates because detection is based on fileName
                // Use the raw prefix so we don't double-prefix when files already carry encoded names (spaces -> '+')
                if (log.isDebugEnabled()) log.debug("privatePrefetchSub: setAllTargetFilesShouldStartWithString {}", prefix);
                engine.setAllTargetFilesShouldStartWithString(prefix);
                engine.copy(subs, target, true);

                latch.await(); // Wait for the latch to reach zero
            } else {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (log.isDebugEnabled()) log.debug("privatePrefetchSub: run calling onNoSubtitlesFound");
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
                        if (log.isDebugEnabled()) log.debug("privatePrefetchSub: run calling onError");
                        mListener.onError(videoUri, e);
                    }
                });
        }
    }

    private static String stripExtension(Uri video){
        final String videoFileName = getName(video);
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
        if (log.isDebugEnabled()) log.debug("getSubtitleList");
        return getSubtitleList(video, false);
    }

    public static List<MetaFile2> getSubtitleListExcludingFirstLevelSubs(Uri video) throws SftpException, AuthenticationException, JSchException, IOException {
        if (log.isDebugEnabled()) log.debug("getSubtitleListExcludingFirstLevelSubs");
        List<MetaFile2> subtitleList = getSubtitleList(video, false);
        // remove from subtitleList all first level subtitles if video is local
        List<MetaFile2> notFirstLevelSubs = new ArrayList<>();
        for (MetaFile2 sub : subtitleList) {
            if (! FileUtils.getParentUrl(sub.getUri()).equals(FileUtils.getParentUrl(video))) notFirstLevelSubs.add(sub);
        }
        return notFirstLevelSubs;
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
            if (log.isDebugEnabled()) log.debug("recursiveSubListing: {}", parentUri.toString());
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
                        if (log.isDebugEnabled()) log.debug("recursiveSubListing: recursing into {} for {}", item.getUri().toString(), filenameWithoutExtension);
                        // First try name-matched subs only (e.g. Ep01.srt when playing Ep01.mp4).
                        // Fall back to all subs only if nothing matches, to support language-code
                        // naming conventions (en.srt, fr.srt, etc.) without picking up unrelated
                        // episode subs from a shared Subtitles/ directory.
                        ArrayList<MetaFile2> matchingSubs = recursiveSubListing(item.getUri(), filenameWithoutExtension, false);
                        if (!matchingSubs.isEmpty()) {
                            subs.addAll(matchingSubs);
                        } else {
                            subs.addAll(recursiveSubListing(item.getUri(), filenameWithoutExtension, true));
                        }
                        continue;
                    }

                    // do not add subs that are not starting with video name
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
        if (log.isDebugEnabled()) log.debug("listLocalAndRemotesSubtitles: {} addAllSubs={} includeIdx={} addCache={}", video, addAllSubs, includeIdx, addCache);
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
                    String encodedFilenameWithoutExtension = encodeFileName(filenameWithoutExtension);
                    String cachePrefix = filenameWithoutExtension + ".";
                    String encodedCachePrefix = (encodedFilenameWithoutExtension != null) ? encodedFilenameWithoutExtension + "." : null;
                    for (MetaFile2 file : files) {
                        // ensures that we have a file with the same name as the video
                        String matchedPrefix = null;
                        if (file.getName().startsWith(cachePrefix)) matchedPrefix = cachePrefix;
                        else if (encodedCachePrefix != null && file.getName().startsWith(encodedCachePrefix)) matchedPrefix = encodedCachePrefix;
                        if (!addAllSubs && matchedPrefix == null) continue;
                        if (matchedPrefix != null) {
                            // Filter out stale renamed subs (e.g. "videoName.Episode Title.srt" from a previous
                            // run). Only accept exact-name match (no middle segment) or a recognised ISO 639
                            // language code (e.g. "en", "fre"), which are legitimate downloaded subs.
                            String afterPrefix = file.getName().substring(matchedPrefix.length());
                            int lastDot = afterPrefix.lastIndexOf('.');
                            String segment = (lastDot > 0) ? afterPrefix.substring(0, lastDot) : "";
                            if (!segment.isEmpty() && !ISO639codes.isletterCode(segment)) {
                                if (log.isDebugEnabled()) log.debug("listLocalAndRemotesSubtitles: skipping stale cached sub {} (segment='{}')", file.getName(), segment);
                                continue;
                            }
                        }
                        allFiles.add(file);
                        if (log.isTraceEnabled()) log.trace("listLocalAndRemotesSubtitles: cache add {}", file.getName());
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
                    String subtitleFileName = null;
                    if (SubtitleExtensions.contains(fileExtension.toLowerCase(Locale.US))&&(!fileExtension.toLowerCase(Locale.US).equals("idx") || includeIdx)) {
                        subtitleFileName = stripExtensionFromName(getName(file.getName()));
                        subtitleName = getSubLanguageFromSubPathAndVideoPath(mContext, file.getUri().getPath(), video.getPath());
                        if (subtitleFileName.equals(subtitleName)) {
                            String defaultFormatName = fileExtension != null ? fileExtension.toUpperCase(Locale.US) : "SRT";
                            subtitleName = defaultFormatName;
                        }
                        subList.add(new SubtitleFile(file, subtitleName));
                        if (log.isTraceEnabled()) log.trace("listLocalAndRemotesSubtitles: add external {} ({})", file.getUri().toString(), subtitleName);
                    }
                }
            } catch (Exception e) {
            }
        }
        // Remove duplicates due to the fact that the remote subtitles may have already been downloaded to the tmp folder
        List<SubtitleFile> subListUnique = new LinkedList<SubtitleFile>();
        listOfLocalSubs = new LinkedList<String>();
        for (SubtitleFile f : subList) {
            // this test checks if the file is already in the list via fileSize and fleName (it captures Subs/en.srt then it is renamed in privatePrefetchSub to videoName.en.srt)
            // refer to equal() method for this
            if (!subListUnique.contains(f)) {
                if (log.isDebugEnabled()) log.debug("listLocalAndRemotesSubtitles: adding only unique {} ({})", f.mFile.getUri().toString(), f.mName);
                subListUnique.add(f);
                if (FileUtils.isLocal(f.mFile.getUri())) listOfLocalSubs.add(f.mFile.getUri().getPath());
            } else {
                if (log.isDebugEnabled()) log.debug("listLocalAndRemotesSubtitles: skipping duplicate {} ({})", f.mFile.getUri().toString(), f.mName);
            }
        }

        return subListUnique;
    }

    public static String getLanguage3(String basename) {
        // extract the 2 or 3 letters language code in a string located at after the start of the string or character "_" or "." or "]" till the end of the string or till a closing ".HI"
        // for some reason, some yts subtitles have a .HI at the end of the filename, and apparently this is not for Hindi but Hearing Impaired, note that they are preceded by SDH for Deaf and hard of Hearing
        Pattern pattern = Pattern.compile("(?:^|" + SEP + ")(" + COUNTRYCODE + ")(?:" + SEP + HI + "|$)");
        Matcher matcher = pattern.matcher(basename);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    // exclude parenthesis and brackets not to match mx (HI) in Rebel.Moon.-.Part.Two.The.Scargiver.2024.1080p.WEBRip.x265.10bit.AAC5.1-[YTS.MX].SDH.eng.HI.srt
    private static final String SEP = "[\\p{Punct}&&[^\\[\\]()\\s]]++";
    private static final String COUNTRYCODE = "[a-zA-Z]{2,3}";
    private static final String HI = "(HI|SDH)";

    public static String convertYTSSubNamingExceptions(String name) {
        String lowercaseName = name.toLowerCase();
        if (lowercaseName.endsWith("simplified.chi") || lowercaseName.endsWith("zh-cn")) {
            return "s_chinese_simplified";
        } else if (lowercaseName.endsWith("traditional.chi") || lowercaseName.endsWith("zh-tw")) {
            return "s_traditional_chinese";
        } else if (lowercaseName.endsWith("brazilian.por") || lowercaseName.endsWith("pt-br")) {
            return "s_brazilian";
        } else if (lowercaseName.endsWith("pt-pt")) {
            return "pt";
        } else if (lowercaseName.endsWith("latin american.spa")) {
            return "s_spanish_la";
        } else if (lowercaseName.endsWith("english")) {
            return "eng";
        } else {
            return name;
        }
    }

    public static String getSubLanguageFromSubPathAndVideoPath(Context context, String subPath, String videoPath) {
        String subFilenameWithoutExtension = stripExtensionFromName(getName(subPath));
        String videoFilenameWithoutExtension = stripExtensionFromName(getName(videoPath));
        if (subFilenameWithoutExtension.equals(videoFilenameWithoutExtension)) {
            String extension = MimeUtils.getExtension(getName(subPath));
            String formatName = extension != null ? extension.toUpperCase(Locale.US) : "SRT";
            if (log.isDebugEnabled()) log.debug("getSubLanguageFromSubPathAndVideoPath: video and sub have same name {} -> {}", subFilenameWithoutExtension, formatName);
            return formatName;
        }
        // subtract video name from sub name if they start the same (they should) but there could be Subs/en.srt too
        String lastPart = null;
        if (subFilenameWithoutExtension.startsWith(videoFilenameWithoutExtension + ".")) {
            lastPart = subFilenameWithoutExtension.substring(videoFilenameWithoutExtension.length() + 1);
        } else lastPart = subFilenameWithoutExtension;
        if (log.isDebugEnabled()) log.debug("getSubLanguageFromSubPathAndVideoPath: ({} - {})={}", subPath, videoPath, lastPart);
        // treat yts Simplified.chi.srt Traditional.chi.srt Latin American.spa.srt English.srt Brazilian.por.srt and reuse s_ special strings for this
        String lang = convertYTSSubNamingExceptions(lastPart);
        String subLanguageName = null;
        if (lastPart.equals(lang))
            // get 2 or 3 letter code for language
            lang = getLanguage3(lastPart);
        if (lang != null) {
            subLanguageName = getLanguageNameForLetterCode(context, lang);
            if (lang.equals(subLanguageName)) lang = null; // match was not a valid 2 or 3 letter code
        }
        if (lang != null) {
            // note that subLanguageName is already set to proper value
            // treat yts SDH and HI as hearing impaired e.g. SDH.eng.HI.srt: add it to the language name
            if (isSubtitleHearingImpaired(subFilenameWithoutExtension))
                subLanguageName = subLanguageName + " (HI)";
        } else { // subLanguageName is likely subFilenameWithoutExtension but cannot compare here because video filename not available
            subLanguageName = subFilenameWithoutExtension;
        }
        if (log.isDebugEnabled()) log.debug("getSubLanguageFromSubPathAndVideoPath: {} -> {}", subPath, subLanguageName);
        return subLanguageName;
    }

    public static boolean isSubtitleHearingImpaired(String basename) {
        // extract the 2 or 3 letters language code in a string located at after the start of the string or character "_" or "." or "]" till the end of the string or till a closing ".HI"
        // for some reason, some yts subtitles have a .HI at the end of the filename, and apparently this is not for Hindi but Hearing Impaired, note that they are preceded by SDH for Deaf and hard of Hearing
        Pattern pattern = Pattern.compile("(?:^|" + SEP + ")(" + COUNTRYCODE + ")" + SEP + HI + "$");
        Matcher matcher = pattern.matcher(basename);
        //log.debug("isSubtitleHearingImpaired: {} -> {}", basename, matcher.group(1));
        return matcher.find();
    }

}
