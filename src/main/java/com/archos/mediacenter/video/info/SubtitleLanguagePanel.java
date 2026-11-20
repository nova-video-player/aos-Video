// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.ThemeManager;

/** Shared phone/TV binding; feedback is outside the scrolling track list and never takes focus. */
public final class SubtitleLanguagePanel {
    private final View root, feedback, actions, fallback;
    private final TextView status;
    private final ProgressBar progress;
    private final Button analyze, cancel, save, restore;
    private final Runnable listener = this::render;
    private SubtitleLanguageController controller;
    private final Runnable onRender;

    public SubtitleLanguagePanel(View root, View fallback, Runnable onRender) {
        this.root = root;
        this.fallback = fallback;
        this.onRender = onRender;
        feedback = root.findViewById(R.id.subtitle_language_feedback);
        actions = root.findViewById(R.id.subtitle_language_actions);
        status = root.findViewById(R.id.subtitle_language_status);
        progress = root.findViewById(R.id.subtitle_language_progress);
        analyze = root.findViewById(R.id.subtitle_languages);
        cancel = root.findViewById(R.id.subtitle_language_cancel);
        save = root.findViewById(R.id.subtitle_language_save);
        restore = root.findViewById(R.id.subtitle_language_restore);
        progress.setIndeterminateTintList(ColorStateList.valueOf(
                ThemeManager.getInstance(root.getContext()).getAccentColor()));
        analyze.setOnClickListener(v -> { if (controller != null) controller.analyze(); });
        cancel.setOnClickListener(v -> { if (controller != null) controller.cancel(); });
        save.setOnClickListener(v -> { if (controller != null) controller.save(); });
        restore.setOnClickListener(v -> { if (controller != null) controller.restore(); });
    }

    public void bind(SubtitleLanguageController value) {
        if (controller == value) { render(); return; }
        detach();
        controller = value;
        if (controller != null) controller.addListener(listener);
        else render();
    }

    public void detach() {
        if (controller != null) controller.removeListener(listener);
        controller = null;
    }

    private View firstAction() {
        for (Button button : new Button[]{analyze, cancel, save, restore}) {
            if (button.getVisibility() == View.VISIBLE && button.isEnabled()) return button;
        }
        return null;
    }

    private void render() {
        View focused = root.findFocus();
        boolean ownedFocus = focused == analyze || focused == cancel || focused == save || focused == restore;
        boolean busy = controller != null && controller.isBusy();
        String message = controller == null ? null : controller.getStatus();
        feedback.setVisibility(message != null ? View.VISIBLE : View.GONE);
        status.setText(message);
        status.setVisibility(message == null ? View.GONE : View.VISIBLE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        analyze.setVisibility(controller != null && !busy && controller.canAnalyze() ? View.VISIBLE : View.GONE);
        cancel.setVisibility(controller != null && controller.canCancel() ? View.VISIBLE : View.GONE);
        cancel.setEnabled(controller != null && !controller.isCancelRequested());
        save.setVisibility(controller != null && !busy && controller.canSave() ? View.VISIBLE : View.GONE);
        restore.setVisibility(controller != null && !busy && controller.canRestore() ? View.VISIBLE : View.GONE);
        boolean hasAction = analyze.getVisibility() == View.VISIBLE || cancel.getVisibility() == View.VISIBLE
                || save.getVisibility() == View.VISIBLE || restore.getVisibility() == View.VISIBLE;
        actions.setVisibility(hasAction ? View.VISIBLE : View.GONE);
        if (ownedFocus && (!focused.isShown() || !focused.isEnabled())) {
            View next = firstAction();
            if (next != null) next.requestFocus();
            else if (fallback != null) fallback.requestFocus();
        }
        if (onRender != null) onRender.run();
    }
}
