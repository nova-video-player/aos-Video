// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import com.archos.mediacenter.video.utils.MiscUtils;
import com.archos.mediacenter.video.utils.ThemeManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.VideoMetadata;
import com.archos.medialib.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Phone and TV share the same explicit analysis, persistence and write actions. */
public final class SubtitleLanguageActivity extends AppCompatActivity {
    private static final Logger log = LoggerFactory.getLogger(SubtitleLanguageActivity.class);
    private long analysisStarted;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Uri uri;
    private TextView status;
    private LinearLayout tracks;
    private View saveHint;
    private Button analyze, save, restore, cancel;
    private ProgressBar progress;
    private VideoMetadata metadata;
    private JSONObject result;
    private boolean busy, bound, writing, destroyed, cancelRequested;
    private Messenger service;
    private final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (destroyed)
            return true;
        log.info("worker message={} stage={} elapsed_ms={}", message.what,
                message.what == SubtitleLanguageAnalysisService.ERROR ? "error"
                        : message.getData().getString("result", ""),
                SystemClock.elapsedRealtime() - analysisStarted);
        if (message.what == SubtitleLanguageAnalysisService.PROGRESS) {
            String stage = message.getData().getString("result", "");
            int label = "header".equals(stage) ? R.string.subtitle_languages_reading_header
                    : "model".equals(stage) ? R.string.subtitle_languages_preparing
                    : "sampling".equals(stage) ? R.string.subtitle_languages_analyzing
                    : R.string.subtitle_languages_persisting;
            status.setText(label);
        } else if (message.what == SubtitleLanguageAnalysisService.COMPLETE) {
            unbind();
            setBusy(false);
            load(getString(R.string.subtitle_languages_ready));
        } else if (message.what == SubtitleLanguageAnalysisService.ERROR) {
            unbind();
            setBusy(false);
            status.setText(failureMessage(SubtitleLanguageException.fromWire(message.getData().getString("result"))));
        }
        return true;
    }));
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            log.info("worker connected elapsed_ms={}", SystemClock.elapsedRealtime() - analysisStarted);
            service = new Messenger(binder);
            if (cancelRequested || destroyed) {
                unbind();
                if (!destroyed)
                    setBusy(false);
                return;
            }
            try {
                Message request = Message.obtain(null, SubtitleLanguageAnalysisService.ANALYZE);
                request.replyTo = receiver;
                Bundle args = new Bundle();
                args.putString("uri", uri.toString());
                args.putBundle("headers", getIntent().getBundleExtra("headers"));
                request.setData(args);
                service.send(request);
            } catch (RemoteException e) {
                failedProcess();
            }
        }
        public void onServiceDisconnected(ComponentName name) {
            failedProcess();
        }
        public void onBindingDied(ComponentName name) {
            failedProcess();
        }
        public void onNullBinding(ComponentName name) {
            failedProcess();
        }
    };
    @Override
    public void onCreate(Bundle saved) {
        ThemeManager theme = ThemeManager.getInstance(this);
        setTheme(theme.isBlackTheme() ? R.style.ArchosThemeBlackNoActionBar : R.style.ArchosThemeBlueNoActionBar);
        super.onCreate(saved);
        theme.applyWindowTheme(this);
        uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        setTitle(R.string.subtitle_languages_identify);
        setContentView(R.layout.activity_subtitle_languages);
        View root = findViewById(R.id.subtitle_language_root);
        MiscUtils.applySystemWindowInsets(root, true, (v, left, top, right, bottom) ->
                v.setPadding(left, top, right, bottom));
        ViewCompat.requestApplyInsets(root);
        Toolbar toolbar = findViewById(R.id.subtitle_language_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        status = findViewById(R.id.subtitle_language_status);
        progress = findViewById(R.id.subtitle_language_progress);
        tracks = findViewById(R.id.subtitle_language_tracks);
        analyze = findViewById(R.id.subtitle_language_analyze);
        cancel = findViewById(R.id.subtitle_language_cancel);
        save = findViewById(R.id.subtitle_language_save);
        restore = findViewById(R.id.subtitle_language_restore);
        saveHint = findViewById(R.id.subtitle_language_save_hint);
        analyze.setOnClickListener(v -> startAnalysis());
        cancel.setOnClickListener(v -> cancelAnalysis());
        save.setOnClickListener(v -> edit(false));
        restore.setOnClickListener(v -> edit(true));
        load(saved == null ? getString(R.string.subtitle_languages_ready)
                : saved.getString("status", getString(R.string.subtitle_languages_ready)));
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (status != null) out.putString("status", bound ? getString(R.string.subtitle_languages_interrupted)
                : status.getText().toString());
    }
    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        analyze.setEnabled(!value);
        save.setEnabled(!value);
        restore.setEnabled(!value);
        cancel.setVisibility(value && !writing && bound ? View.VISIBLE : View.GONE);
    }
    private void load(String message) {
        long loadStarted = SystemClock.elapsedRealtime();
        log.info("UI metadata refresh start");
        setBusy(true);
        status.setText(R.string.subtitle_languages_loading);
        worker.execute(() -> {
            VideoMetadata fresh = new VideoMetadata(uri.toString());
            fresh.fillFromRetriever(getApplicationContext());
            log.info("UI metadata refresh retrieved elapsed_ms={}", SystemClock.elapsedRealtime() - loadStarted);
            JSONObject saved = SubtitleLanguageStore.load(this, uri.toString(), fresh.getSubtitleFingerprint());
            JSONObject journal = null;
            try {
                journal = SubtitleLanguageStore.journal(this, uri.toString());
            } catch (Exception ignored) {
            }
            JSONObject edit = journal;
            main.post(() -> {
                if (destroyed)
                    return;
                metadata = fresh;
                result = saved;
                setBusy(false);
                analyze.setVisibility(fresh.hasUnknownSubtitleLanguages() ? View.VISIBLE : View.GONE);
                boolean writable = fresh.hasInferredSubtitleLanguages() && saved != null
                        && MatroskaLanguageWriter.canSave(saved);
                save.setVisibility(writable ? View.VISIBLE : View.GONE);
                saveHint.setVisibility(writable ? View.VISIBLE : View.GONE);
                restore.setVisibility(
                    edit != null && !"UNDONE".equals(edit.optString("state")) ? View.VISIBLE : View.GONE);
                status.setText(edit != null && "PENDING".equals(edit.optString("state"))
                        ? getString(R.string.subtitle_languages_pending)
                        : message);
                if (saved != null && message.equals(getString(R.string.subtitle_languages_ready))
                        && (edit == null || !"PENDING".equals(edit.optString("state")))) {
                    org.json.JSONArray results = saved.optJSONArray("tracks");
                    int identified = 0;
                    if (results != null) for (int i = 0; i < results.length(); i++) {
                        org.json.JSONObject item = results.optJSONObject(i);
                        if (item != null && "identified".equals(item.optString("status"))) identified++;
                    }
                    status.setText(getString(R.string.subtitle_languages_summary, identified,
                            fresh.getSubtitleTrackNb()));
                }
                tracks.removeAllViews();
                for (int i = 0; i < fresh.getSubtitleTrackNb(); i++) {
                    VideoMetadata.SubtitleTrack track = fresh.getSubtitleTrack(i);
                    String format =
                        com.archos.mediacenter.video.utils.VideoUtils.getSubtitleFormatLabel(this, track.format);
                    TextView row = (TextView) getLayoutInflater().inflate(
                            R.layout.subtitle_language_track, tracks, false);
                    row.setText(android.text.Html.fromHtml(
                            com.archos.mediacenter.video.browser.subtitlesmanager.ISO639codes.generateSubtitleTrackName(
                                    this, track, format)));
                    tracks.addView(row);
                }
                log.info("UI results displayed refresh_ms={} identify_to_display_ms={}",
                        SystemClock.elapsedRealtime() - loadStarted,
                        analysisStarted == 0 ? -1 : SystemClock.elapsedRealtime() - analysisStarted);
            });
        });
    }
    private void startAnalysis() {
        if (busy) return;
        if (MatroskaLanguageWriter.isPlaybackActive()) {
            status.setText(R.string.subtitle_languages_stop_playback);
            return;
        }
        cancelRequested = false;
        analysisStarted = SystemClock.elapsedRealtime();
        log.info("Identify pressed pid={} scheme={}", android.os.Process.myPid(), uri.getScheme());
        status.setText(R.string.subtitle_languages_analyzing);
        bound =
            bindService(new Intent(this, SubtitleLanguageAnalysisService.class), connection, Context.BIND_AUTO_CREATE);
        setBusy(bound);
        if (!bound)
            failedProcess();
    }
    private void cancelAnalysis() {
        log.info("Cancel pressed elapsed_ms={}", SystemClock.elapsedRealtime() - analysisStarted);
        cancelRequested = true;
        if (service != null)
            try {
                service.send(Message.obtain(null, SubtitleLanguageAnalysisService.CANCEL));
            } catch (RemoteException ignored) {
            }
        if (cancel != null)
            cancel.setEnabled(false); // Wait for the worker's partial results and its acknowledgement.
    }
    private void failedProcess() {
        log.warn("worker unavailable elapsed_ms={}", SystemClock.elapsedRealtime() - analysisStarted);
        unbind();
        setBusy(false);
        status.setText(R.string.subtitle_languages_interrupted);
    }
    private void unbind() {
        service = null;
        if (bound) {
            bound = false;
            unbindService(connection);
        }
        if (cancel != null)
            cancel.setEnabled(true);
    }
    private String failureMessage(SubtitleLanguageException.Code code) {
        String detail = getString(code.messageResource);
        return code == SubtitleLanguageException.Code.CANCELLED ? detail
                : getString(R.string.subtitle_languages_failed, detail);
    }
    private void edit(boolean undo) {
        if (busy || (!undo && result == null))
            return;
        writing = true;
        setBusy(true);
        status.setText(undo ? R.string.subtitle_languages_restoring : R.string.subtitle_languages_writing);
        JSONObject selected = result;
        worker.execute(() -> {
            String message;
            try {
                if (undo)
                    MatroskaLanguageWriter.restore(getApplicationContext(), uri);
                else
                    MatroskaLanguageWriter.save(getApplicationContext(), uri, selected);
                message = getString(undo ? R.string.subtitle_languages_restored : R.string.subtitle_languages_saved);
            } catch (Exception | LinkageError e) {
                log.error(undo ? "Subtitle metadata restore failed" : "Subtitle metadata save failed", e);
                message = failureMessage(SubtitleLanguageException.codeOf(e));
            }
            String completed = message;
            main.post(() -> {
                if (!destroyed) {
                    writing = false;
                    load(completed);
                }
            });
        });
    }
    @Override
    protected void onStop() {
        if (bound)
            cancelAnalysis();
        super.onStop();
    }
    @Override
    protected void onDestroy() {
        destroyed = true;
        cancelAnalysis();
        unbind();
        worker.shutdown();
        super.onDestroy();
    }
}
