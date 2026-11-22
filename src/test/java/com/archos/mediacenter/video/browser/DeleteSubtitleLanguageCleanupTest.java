// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.browser;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import com.archos.medialib.SubtitleLanguageResult;
import com.archos.medialib.SubtitleLanguageStore;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class)
public class DeleteSubtitleLanguageCleanupTest {
    @Before public void resetStoreForFreshApplication() {
        // Robolectric creates a fresh application/database directory for each test.
        com.archos.mediaprovider.video.VideoOpenHelper helper =
                org.robolectric.util.ReflectionHelpers.getStaticField(SubtitleLanguageStore.class, "helper");
        if (helper != null) helper.close();
        org.robolectric.util.ReflectionHelpers.setStaticField(SubtitleLanguageStore.class, "helper", null);
    }

    private final Context context = RuntimeEnvironment.getApplication();

    private Uri remember(String value) throws Exception {
        Uri uri = Uri.parse(value);
        SubtitleLanguageStore.save(context, value, SubtitleLanguageResult.fromCsv("fr", "99"));
        SubtitleLanguageStore.journal(context, value, new JSONObject(), "APPLIED");
        return uri;
    }

    private void awaitCleanup() throws Exception {
        ExecutorService worker = ReflectionHelpers.getStaticField(Delete.class, "subtitleCleanup");
        worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    @Test public void failedAndCancelledDeletesPreserveResultsAndJournal() throws Exception {
        Uri uri = remember("sftp://server/keep.mkv");
        Delete delete = new Delete(null, context);
        delete.deleteNOK(uri);
        delete.completeSystemDelete(Collections.singletonList(uri), false, Delete.OP_SINGLE_FILE);
        awaitCleanup();
        assertNotNull(SubtitleLanguageStore.load(context, uri.toString()));
        assertNotNull(SubtitleLanguageStore.journal(context, uri.toString()));
    }

    @Test public void successfulSingleAndBatchDeletesClearEveryUri() throws Exception {
        Uri sftp = remember("sftp://server/deleted.mkv");
        Uri smb = remember("smb://server/share/deleted.mkv");
        Uri webdav = remember("webdav://server/deleted.mkv");
        Delete delete = new Delete(null, context);
        delete.deleteOK(sftp);
        delete.completeSystemDelete(Arrays.asList(smb, webdav), true, Delete.OP_MULTIPLE_FILES);
        awaitCleanup();
        for (Uri uri : Arrays.asList(sftp, smb, webdav)) {
            assertNull(SubtitleLanguageStore.load(context, uri.toString()));
            assertNull(SubtitleLanguageStore.journal(context, uri.toString()));
        }
    }

    @Test public void successfulFolderCallbackClearsDescendants() throws Exception {
        Uri child = remember("smb://server/share/show/season/episode.mkv");
        Delete delete = new Delete(null, context);
        delete.completeSystemDelete(Collections.singletonList(Uri.parse("smb://server/share/show")),
                true, Delete.OP_FOLDER);
        awaitCleanup();
        assertNull(SubtitleLanguageStore.load(context, child.toString()));
        assertNull(SubtitleLanguageStore.journal(context, child.toString()));
    }

    @Test public void permissionCallbackDoesNotClearAnExistingLocalFile() throws Exception {
        File file = File.createTempFile("subtitle-delete-", ".mkv", context.getCacheDir());
        Uri uri = remember(Uri.fromFile(file).toString());
        Delete delete = new Delete(null, context);
        delete.completeSystemDelete(Collections.singletonList(uri), true, Delete.OP_SINGLE_FILE);
        awaitCleanup();
        assertNotNull(SubtitleLanguageStore.load(context, uri.toString()));
        assertTrue(file.delete());
        delete.deleteOK(uri);
        awaitCleanup();
        assertNull(SubtitleLanguageStore.load(context, uri.toString()));
        assertNull(SubtitleLanguageStore.journal(context, uri.toString()));
    }
}
