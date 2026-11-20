// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.MatroskaLanguageWriter;
import com.archos.medialib.SubtitleLanguageAnalysisService;
import com.archos.medialib.SubtitleLanguageException;
import com.archos.medialib.SubtitleLanguageStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One explicit operation for one URI. Owned by a details fragment, never by a recycled row. */
public final class SubtitleLanguageController {
    private static final Logger log = LoggerFactory.getLogger(SubtitleLanguageController.class);
    public interface MetadataListener { void onMetadataChanged(VideoMetadata metadata); }
    private final Context context;
    private final Uri uri;
    private final Bundle headers;
    private final ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Runnable> listeners = new LinkedHashSet<>();
    private MetadataListener metadataListener;
    private VideoMetadata metadata;
    private JSONObject result;
    private boolean busy, writing, bound, closed, cancelRequested, pending, restorable;
    private int generation;
    private long started;
    private String status;
    private Messenger service;
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), this::receive));

    private boolean receive(Message message) {
        if (closed || !bound) return true;
        log.info("Subtitle operation message={} elapsed_ms={}", message.what, SystemClock.elapsedRealtime() - started);
        if (message.what == SubtitleLanguageAnalysisService.PROGRESS) {
            String stage = message.getData().getString("result", "");
            int label = "header".equals(stage) ? R.string.subtitle_languages_reading_header
                    : "model".equals(stage) ? R.string.subtitle_languages_preparing
                    : "sampling".equals(stage) ? R.string.subtitle_languages_analyzing
                    : R.string.subtitle_languages_persisting;
            status = context.getString(label);
            publish();
        } else if (message.what == SubtitleLanguageAnalysisService.COMPLETE) {
            unbind();
            refresh(null);
        } else if (message.what == SubtitleLanguageAnalysisService.ERROR) {
            unbind();
            busy = false;
            status = failureMessage(SubtitleLanguageException.fromWire(message.getData().getString("result")));
            publish();
        }
        return true;
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed || !bound) return;
            service = new Messenger(binder);
            if (cancelRequested) {
                unbind();
                busy = false;
                status = context.getString(R.string.subtitle_languages_interrupted);
                publish();
                return;
            }
            try {
                Message request = Message.obtain(null, SubtitleLanguageAnalysisService.ANALYZE);
                request.replyTo = receiver;
                Bundle args = new Bundle();
                args.putString("uri", uri.toString());
                args.putBundle("headers", headers);
                request.setData(args);
                service.send(request);
            } catch (RemoteException e) {
                failedProcess();
            }
        }
        @Override public void onServiceDisconnected(ComponentName name) { failedProcess(); }
        @Override public void onBindingDied(ComponentName name) { failedProcess(); }
        @Override public void onNullBinding(ComponentName name) { failedProcess(); }
    };

    public SubtitleLanguageController(Context context, Uri uri, Bundle headers, MetadataListener listener) {
        this(context, uri, headers, listener, Executors.newSingleThreadExecutor());
    }

    SubtitleLanguageController(Context context, Uri uri, Bundle headers, MetadataListener listener,
            ExecutorService worker) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        this.headers = headers == null ? null : new Bundle(headers);
        this.metadataListener = listener;
        this.worker = worker;
    }

    public Uri getUri() { return uri; }
    public boolean isBusy() { return busy; }
    public String getStatus() { return status; }
    public boolean canAnalyze() { return metadata != null && metadata.hasUnknownSubtitleLanguages() && !pending; }
    public boolean canSave() { return !pending && metadata != null && metadata.hasInferredSubtitleLanguages()
            && result != null && MatroskaLanguageWriter.canSave(result); }
    public boolean canRestore() { return restorable; }
    public boolean canCancel() { return bound && !writing; }
    public boolean isCancelRequested() { return cancelRequested; }
    public void addListener(Runnable listener) { listeners.add(listener); listener.run(); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    /** Ordinary details binding only reads local persistence; it never opens or scans the media. */
    public void updateMetadata(VideoMetadata value) {
        if (closed || busy || value == metadata) return;
        metadata = value;
        final int ticket = ++generation;
        publish();
        if (value == null) return;
        worker.execute(() -> {
            JSONObject saved = SubtitleLanguageStore.load(context, uri.toString(), value.getSubtitleFingerprint());
            JSONObject journal = readJournal();
            main.post(() -> {
                if (closed || ticket != generation) return;
                applyStoredState(saved, journal);
                publish();
            });
        });
    }

    public void analyze() {
        if (closed || busy || !canAnalyze()) return;
        if (MatroskaLanguageWriter.isPlaybackActive()) {
            status = context.getString(R.string.subtitle_languages_stop_playback);
            publish();
            return;
        }
        ++generation;
        started = SystemClock.elapsedRealtime();
        cancelRequested = false;
        busy = true;
        status = context.getString(R.string.subtitle_languages_analyzing);
        log.info("Inline Identify pressed scheme={}", uri.getScheme());
        try {
            bound = context.bindService(new Intent(context, SubtitleLanguageAnalysisService.class),
                    connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) {
            log.error("Cannot bind subtitle analysis service", e);
            bound = false;
        }
        if (!bound) failedProcess();
        else publish();
    }

    public void cancel() {
        if (!bound || cancelRequested) return;
        cancelRequested = true;
        log.info("Cancel subtitle analysis elapsed_ms={}", SystemClock.elapsedRealtime() - started);
        if (service != null) {
            try { service.send(Message.obtain(null, SubtitleLanguageAnalysisService.CANCEL)); }
            catch (RemoteException e) { failedProcess(); return; }
        }
        publish();
    }

    public void save() { edit(false); }
    public void restore() { edit(true); }

    private void edit(boolean undo) {
        if (closed || busy || (undo ? !canRestore() : !canSave())) return;
        ++generation;
        busy = writing = true;
        started = SystemClock.elapsedRealtime();
        status = context.getString(undo ? R.string.subtitle_languages_restoring : R.string.subtitle_languages_writing);
        publish();
        JSONObject selected = result;
        worker.execute(() -> {
            String message;
            try {
                if (undo) MatroskaLanguageWriter.restore(context, uri);
                else MatroskaLanguageWriter.save(context, uri, selected);
                message = context.getString(undo ? R.string.subtitle_languages_restored : R.string.subtitle_languages_saved);
            } catch (Exception | LinkageError e) {
                log.error("Subtitle metadata edit failed", e);
                message = failureMessage(SubtitleLanguageException.codeOf(e));
            }
            String completed = message;
            main.post(() -> {
                if (closed) return;
                writing = false;
                refresh(completed);
            });
        });
    }

    private JSONObject readJournal() {
        try { return SubtitleLanguageStore.journal(context, uri.toString()); }
        catch (Exception e) { log.warn("Cannot read subtitle recovery journal", e); return null; }
    }

    private void applyStoredState(JSONObject saved, JSONObject journal) {
        result = saved;
        restorable = journal != null && !"UNDONE".equals(journal.optString("state"));
        pending = journal != null && "PENDING".equals(journal.optString("state"));
        if (pending) status = context.getString(R.string.subtitle_languages_pending);
    }

    private void refresh(String message) {
        busy = true;
        status = context.getString(R.string.subtitle_languages_loading);
        publish();
        worker.execute(() -> {
            try {
                VideoMetadata fresh = new VideoMetadata(uri.toString());
                HashMap<String, String> requestHeaders = new HashMap<>();
                if (headers != null) for (String key : headers.keySet()) requestHeaders.put(key, headers.getString(key));
                fresh.fillFromRetriever(context, requestHeaders);
                JSONObject saved = SubtitleLanguageStore.load(context, uri.toString(), fresh.getSubtitleFingerprint());
                JSONObject journal = readJournal();
                main.post(() -> {
                    if (closed) return;
                    metadata = fresh;
                    busy = false;
                    cancelRequested = false;
                    int identified = 0;
                    JSONArray items = saved == null ? null : saved.optJSONArray("tracks");
                    if (items != null) for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item != null && "identified".equals(item.optString("status"))) identified++;
                    }
                    status = message != null ? message : context.getString(R.string.subtitle_languages_summary,
                            identified, fresh.getSubtitleTrackNb());
                    applyStoredState(saved, journal);
                    if (metadataListener != null) metadataListener.onMetadataChanged(fresh);
                    publish();
                    log.info("Inline subtitle results displayed elapsed_ms={}", SystemClock.elapsedRealtime() - started);
                });
            } catch (Exception | LinkageError e) {
                log.error("Subtitle metadata refresh failed", e);
                main.post(() -> {
                    if (closed) return;
                    busy = false;
                    status = failureMessage(SubtitleLanguageException.codeOf(e));
                    publish();
                });
            }
        });
    }

    private String failureMessage(SubtitleLanguageException.Code code) {
        String detail = context.getString(code.messageResource);
        return code == SubtitleLanguageException.Code.CANCELLED ? detail
                : context.getString(R.string.subtitle_languages_failed, detail);
    }
    private void failedProcess() {
        if (closed) return;
        unbind();
        busy = false;
        status = context.getString(R.string.subtitle_languages_interrupted);
        publish();
    }
    private void unbind() {
        service = null;
        if (bound) {
            bound = false;
            context.unbindService(connection);
        }
    }
    private void publish() {
        if (!closed) for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
    /** Cancels sampling on exit; an in-flight journalled write must finish safely. */
    public void close() {
        if (closed) return;
        listeners.clear();
        metadataListener = null;
        cancel();
        closed = true;
        ++generation;
        unbind();
        worker.shutdown();
    }
}
