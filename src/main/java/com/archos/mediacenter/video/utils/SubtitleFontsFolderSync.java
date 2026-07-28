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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Custom subtitle fonts folder (MX Player / mpv-android style third-party fonts dir),
 * implemented via the Storage Access Framework instead of a raw filesystem path.
 *
 * WHY SAF AND NOT A PLAIN FOLDER PATH: this app only requests/holds the granular
 * "Photos and videos" media permissions (READ_MEDIA_IMAGES/READ_MEDIA_VIDEO), not
 * MANAGE_EXTERNAL_STORAGE. Under Android scoped storage, a shared folder like
 * Download/Subtitles is only visible to File.listFiles() for (a) media files the OS
 * classifies as photo/video/audio, or (b) files the app itself created -- confirmed by
 * direct logcat testing: a .mkv placed in a folder shows up in the RAW unfiltered
 * File.listFiles() result, a .ttf placed in the SAME folder does not appear AT ALL, not
 * merely filtered by app logic. SAF's ACTION_OPEN_DOCUMENT_TREE grant is a completely
 * separate access path from that media permission model: once the user taps "USE THIS
 * FOLDER" for a directory, the app gets a persistable content:// URI grant to that
 * specific tree, independent of and unaffected by the media-only permission scope.
 *
 * WHY COPY INTO A CACHE DIR RATHER THAN READ SAF DIRECTLY FROM NATIVE CODE: the native
 * side (sub_format_ssa.c's load_fonts_dir()) does plain opendir()/fopen()/fread() on a
 * filesystem path -- there is no such thing as a filesystem path for a content:// URI,
 * so native code cannot open one directly, and there's no clean way to hand a
 * ParcelFileDescriptor across the JNI boundary into that existing code without a much
 * larger rewrite of the SSA backend. Copying matched font files into
 * getCacheDir()/subtitle_fonts/ instead means: (1) the existing native pipeline needs
 * ZERO changes -- it keeps reading a plain directory path exactly as before, just
 * pointed at this cache dir instead of a user-visible one; (2) the copy only needs to
 * happen when the SAF folder changes, not on every video (see syncIfNeeded() below).
 *
 * WHY RAW ContentResolver.query() INSTEAD OF DocumentFile: an earlier revision used
 * androidx.documentfile.provider.DocumentFile's fromTreeUri()/listFiles(), which is
 * correct but expensive at scale -- DocumentFile's isFile()/getName()/lastModified()/
 * length() are each their OWN independent ContentResolver round trip per child, so
 * listing a folder of N fonts cost up to 4N SAF queries. Every method below instead
 * does exactly ONE ContentResolver.query() over the tree's children, requesting every
 * column needed (document ID, name, mimetype, size, lastModified) in a single
 * projection -- this is the pattern Android's own SAF performance guidance recommends
 * over DocumentFile for anything beyond a handful of files.
 */
public class SubtitleFontsFolderSync {

    private static final Logger log = LoggerFactory.getLogger(SubtitleFontsFolderSync.class);

    private static final String CACHE_SUBDIR = "subtitle_fonts";

    private static final String[] QUERY_PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    };

    // Extensions libass can actually use (see has_font_ext() in sub_format_ssa.c --
    // keep in sync with that native-side check).
    private static final Set<String> FONT_EXTENSIONS = new HashSet<>();
    static {
        FONT_EXTENSIONS.add("ttf");
        FONT_EXTENSIONS.add("otf");
        FONT_EXTENSIONS.add("ttc");
    }

    private SubtitleFontsFolderSync() {} // static utility, not instantiated

    private static boolean hasFontExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        return FONT_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Returns the app-private cache directory native code should be pointed at (via
     * SubtitleEngine.setFontsFolder()) -- this is always a plain filesystem path,
     * regardless of what SAF tree URI it was last synced from. Safe to call even if
     * nothing has ever been synced yet (returns the directory whether or not it has
     * been created/populated). */
    public static File getCacheDir(Context context) {
        return new File(context.getCacheDir(), CACHE_SUBDIR);
    }

    /** One font document discovered by a single batched query -- documentId/name/size/
     * lastModified all come from the SAME ContentResolver.query() row, so every caller
     * below that needs any of these has already paid the one-query cost and needs zero
     * further round trips per file. */
    private static final class FontDoc {
        final String documentId;
        final String name;
        final long size;
        final long lastModified;
        FontDoc(String documentId, String name, long size, long lastModified) {
            this.documentId = documentId;
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    /** Result of one queryFontDocs() call. `ok=false` means the tree itself couldn't be
     * queried at all (permission revoked, tree deleted) -- deliberately distinct from
     * `ok=true` with an empty `docs` list, which just means a readable, font-free folder;
     * callers need to tell those two apart (see syncFromTree()'s -1-vs-0 contract). */
    private static final class QueryResult {
        final boolean ok;
        final List<FontDoc> docs;
        QueryResult(boolean ok, List<FontDoc> docs) { this.ok = ok; this.docs = docs; }
    }

    /**
     * The ONE SAF round trip every method in this class needs: a single
     * ContentResolver.query() over every direct child of `treeUri`, requesting id/name/
     * mimetype/size/lastModified together in one projection instead of querying each
     * child individually. Filters out subfolders (SAF trees can be nested; only direct
     * file children matter here, matching the flat-folder contract sub_format_ssa.c's
     * load_fonts_dir() already assumes) and non-font files inline while walking the
     * cursor, so callers get back exactly the font documents they care about.
     */
    private static QueryResult queryFontDocs(Context context, Uri treeUri) {
        List<FontDoc> docs = new ArrayList<>();

        String treeDocId;
        try {
            treeDocId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            log.warn("queryFontDocs: '{}' is not a valid SAF tree URI: {}", treeUri, e.getMessage());
            return new QueryResult(false, docs);
        }
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId);

        try (Cursor cursor = context.getContentResolver().query(childrenUri, QUERY_PROJECTION, null, null, null)) {
            if (cursor == null) {
                log.warn("queryFontDocs: null cursor for tree '{}' (permission revoked / folder deleted?)", treeUri);
                return new QueryResult(false, docs);
            }
            int idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            int modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);

            while (cursor.moveToNext()) {
                String mime = mimeIdx >= 0 ? cursor.getString(mimeIdx) : null;
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) continue; // subfolder, skip
                String name = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                String docId = idIdx >= 0 ? cursor.getString(idIdx) : null;
                if (name == null || docId == null || !hasFontExtension(name)) continue;
                long size = sizeIdx >= 0 ? cursor.getLong(sizeIdx) : 0L;
                long lastMod = modIdx >= 0 ? cursor.getLong(modIdx) : 0L;
                docs.add(new FontDoc(docId, name, size, lastMod));
            }
        } catch (SecurityException | IllegalArgumentException e) {
            log.warn("queryFontDocs: query failed for tree '{}': {}", treeUri, e.getMessage());
            return new QueryResult(false, new ArrayList<>());
        }
        return new QueryResult(true, docs);
    }

    private static long signature(List<FontDoc> docs) {
        long sig = 0;
        for (FontDoc d : docs) sig += d.lastModified + d.size;
        return sig;
    }

    private static long signature(File[] files) {
        long sig = 0;
        if (files != null) for (File f : files) sig += f.lastModified() + f.length();
        return sig;
    }

    /**
     * Copies every `docs` entry (already fetched by queryFontDocs() -- no further SAF
     * queries here) into `cacheDir`, wiping it first. Deliberately NOT incremental/diffed
     * against the previous contents -- SAF trees are typically small (a folder of fonts,
     * not a media library), and a full wipe-and-recopy avoids an entire class of bugs
     * around stale cached fonts outliving a file the user removed from the SAF folder.
     * Returns the number of fonts copied, or -1 on a hard directory-prep failure. Shared
     * by syncFromTree() and syncIfNeeded() below so there's exactly one place that talks
     * to ContentResolver.openInputStream().
     */
    private static int copyToCache(Context context, Uri treeUri, List<FontDoc> docs, File cacheDir) {
        if (cacheDir.exists()) {
            File[] stale = cacheDir.listFiles();
            if (stale != null) {
                for (File f : stale) {
                    if (!f.delete()) {
                        log.warn("copyToCache: failed to delete stale cached font '{}'", f.getAbsolutePath());
                    }
                }
            }
        } else if (!cacheDir.mkdirs()) {
            log.warn("copyToCache: failed to create cache dir '{}'", cacheDir.getAbsolutePath());
            return -1;
        }

        ContentResolver resolver = context.getContentResolver();
        int copied = 0;
        for (FontDoc doc : docs) {
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.documentId);
            File destFile = new File(cacheDir, doc.name);
            try (InputStream in = resolver.openInputStream(docUri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) {
                    log.warn("copyToCache: could not open '{}' for reading", doc.name);
                    continue;
                }
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                copied++;
            } catch (IOException e) {
                log.warn("copyToCache: failed to copy '{}': {}", doc.name, e.getMessage());
            }
        }

        log.debug("copyToCache: copied {} font file(s) from '{}' into '{}'", copied, treeUri, cacheDir.getAbsolutePath());
        return copied;
    }

    /**
     * Unconditional sync: copies every .ttf/.otf/.ttc directly under `treeUri` into the
     * app's private fonts cache directory, replacing whatever was there before. Use this
     * right after the user picks/changes the SAF folder, where a resync is definitely
     * needed and there's no point spending a query checking a signature already known to
     * be stale -- for the "don't yet know if anything changed" case (e.g. Settings screen
     * reopened), use {@link #syncIfNeeded(Context, Uri)} instead, which folds the check
     * and the sync into the same single SAF query.
     *
     * Returns the number of fonts copied, or -1 on a hard failure (SAF permission revoked,
     * tree no longer exists, etc.) -- distinct from a legitimate 0 (folder access fine,
     * just no font files in it).
     *
     * Runs synchronous file I/O -- callers on the UI thread (e.g. a preference's
     * onFontsFolderPickerResult) should dispatch this to a background thread rather
     * than call it directly from onActivityResult.
     */
    public static int syncFromTree(Context context, Uri treeUri) {
        if (treeUri == null) return -1;
        QueryResult qr = queryFontDocs(context, treeUri);
        if (!qr.ok) return -1;
        return copyToCache(context, treeUri, qr.docs, getCacheDir(context));
    }

    /**
     * Combined "check whether the SAF folder changed since the last sync, and sync only if
     * it did" entry point -- the efficient way to call this class when you DON'T already
     * know a resync is needed (e.g. the Settings screen being reopened, where the folder
     * may or may not have changed via a file manager since last time). Does exactly ONE
     * SAF query total (queryFontDocs()) no matter the outcome, replacing what used to be
     * a separate needsResync() query followed by a second, independent syncFromTree()
     * query when a resync WAS needed -- i.e. this removes the double round trip that
     * combination cost on the common "folder is stale" path, on top of each individual
     * query already being collapsed from O(N) to O(1) above.
     *
     * Compares (entry count, summed mtime+size) between the live SAF listing and what's
     * currently in the cache dir -- not a perfect signature, but SAF doesn't expose a
     * directory-level mtime the way a plain File does, and this is cheap and good enough
     * to catch the common cases (font added/removed/replaced).
     *
     * Returns the number of fonts now in the cache dir (whether freshly copied or already
     * up to date), or -1 on a hard failure. Runs synchronous I/O -- dispatch to a
     * background thread, same as syncFromTree().
     */
    public static int syncIfNeeded(Context context, Uri treeUri) {
        if (treeUri == null) return -1;

        QueryResult qr = queryFontDocs(context, treeUri);
        if (!qr.ok) return -1;

        File cacheDir = getCacheDir(context);
        File[] cached = cacheDir.listFiles();
        int cachedCount = cached == null ? 0 : cached.length;

        boolean upToDate = qr.docs.size() == cachedCount && signature(qr.docs) == signature(cached);
        if (upToDate) {
            log.debug("syncIfNeeded: '{}' unchanged since last sync ({} font(s)), skipping copy", treeUri, cachedCount);
            return cachedCount;
        }

        return copyToCache(context, treeUri, qr.docs, cacheDir);
    }
}
