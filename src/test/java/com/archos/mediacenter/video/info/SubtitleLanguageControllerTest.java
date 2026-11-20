// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.SubtitleLanguageAnalysisService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class)
public class SubtitleLanguageControllerTest {
    private Context context;
    private ExecutorService worker;
    private SubtitleLanguageController controller;
    private final List<Message> requests = new ArrayList<>();

    @Before public void setup() {
        context = mock(Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getString(anyInt())).thenAnswer(call -> RuntimeEnvironment.getApplication().getString(call.getArgument(0)));
        when(context.getString(anyInt(), any())).thenAnswer(call ->
                RuntimeEnvironment.getApplication().getString(call.getArgument(0), new Object[]{call.getArgument(1)}));
        when(context.bindService(any(), any(), anyInt())).thenReturn(true);
        worker = mock(ExecutorService.class);
        controller = new SubtitleLanguageController(context, Uri.parse("sftp://server/video.mkv"), null,
                mock(SubtitleLanguageController.MetadataListener.class), worker);
        VideoMetadata metadata = mock(VideoMetadata.class);
        when(metadata.hasUnknownSubtitleLanguages()).thenReturn(true);
        controller.updateMetadata(metadata);
    }

    private void connect() {
        ArgumentCaptor<ServiceConnection> captor = ArgumentCaptor.forClass(ServiceConnection.class);
        verify(context).bindService(any(Intent.class), captor.capture(), eq(Context.BIND_AUTO_CREATE));
        Messenger remote = new Messenger(new Handler(Looper.getMainLooper(), message -> {
            requests.add(Message.obtain(message));
            return true;
        }));
        captor.getValue().onServiceConnected(new ComponentName("nova", "worker"), remote.getBinder());
        ShadowLooper.idleMainLooper();
    }

    private void reply(int what, String value) throws Exception {
        Message message = Message.obtain(null, what);
        Bundle data = new Bundle();
        data.putString("result", value);
        message.setData(data);
        ((Messenger) ReflectionHelpers.getField(controller, "receiver")).send(message);
        ShadowLooper.idleMainLooper();
    }

    @Test public void openingDetailsDoesNotStartAnalysis() {
        assertTrue(controller.canAnalyze());
        assertFalse(controller.isBusy());
        assertNull(controller.getStatus());
        verify(context, never()).bindService(any(), any(), anyInt());
        controller.close();
    }

    @Test public void explicitAnalysisPublishesStagesAndCancelsWithoutStartingAnotherJob() throws Exception {
        controller.analyze();
        connect();
        assertTrue(controller.isBusy());
        assertTrue(controller.canCancel());
        assertEquals(SubtitleLanguageAnalysisService.ANALYZE, requests.get(0).what);
        reply(SubtitleLanguageAnalysisService.PROGRESS, "sampling");
        assertEquals(context.getString(R.string.subtitle_languages_analyzing), controller.getStatus());
        controller.analyze();
        controller.cancel();
        ShadowLooper.idleMainLooper();
        assertTrue(controller.isCancelRequested());
        assertEquals(2, requests.size());
        assertEquals(SubtitleLanguageAnalysisService.CANCEL, requests.get(1).what);
        controller.close();
        verify(context).unbindService(any());
    }

    @Test public void completionRefreshesBeforeEndingProgress() throws Exception {
        controller.analyze();
        connect();
        clearInvocations(worker);
        reply(SubtitleLanguageAnalysisService.COMPLETE, "saved");
        assertTrue("Keep progress visible through metadata refresh", controller.isBusy());
        assertFalse(controller.canCancel());
        verify(worker).execute(any(Runnable.class));
        controller.close();
    }

    @Test public void errorsAreLocalizedAndLateRepliesAfterCloseAreIgnored() throws Exception {
        controller.analyze();
        connect();
        reply(SubtitleLanguageAnalysisService.ERROR, "private/path: internal exception");
        assertFalse(controller.isBusy());
        assertFalse(controller.getStatus().contains("private/path"));
        Runnable listener = mock(Runnable.class);
        controller.addListener(listener);
        clearInvocations(listener);
        controller.close();
        reply(SubtitleLanguageAnalysisService.PROGRESS, "model");
        verifyNoInteractions(listener);
    }

    @Test public void restoreKeepsItsOwnStatusAndContinuesSafelyAfterClosing() {
        ReflectionHelpers.setField(controller, "restorable", true);
        controller.restore();
        assertTrue(controller.isBusy());
        assertFalse(controller.canCancel());
        assertEquals(context.getString(R.string.subtitle_languages_restoring), controller.getStatus());
        controller.close();
        verify(worker).shutdown();
        verify(worker, never()).shutdownNow();
    }
}
