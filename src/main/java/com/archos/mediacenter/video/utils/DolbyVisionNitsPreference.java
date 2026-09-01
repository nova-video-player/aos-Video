/*
 * Copyright (C) 2026 The Nova Video Player Project
 *
 * Dolby Vision tone-map target luminance preference: a slider (200-10000
 * nits, logarithmic feel via steps) plus a text box for exact values, plus
 * an "Auto" reset. The summary always shows what the player will actually
 * use: the manual value, or what "Auto" resolves to (display-reported
 * HDR desired max luminance, same query the PlayerActivity uses; 0 in the
 * renderer then falls back to the source HDR max).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.archos.mediacenter.video.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import android.widget.SeekBar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DolbyVisionNitsPreference extends Preference {

    private static final Logger log = LoggerFactory.getLogger(DolbyVisionNitsPreference.class);

    private static final int MIN_NITS = 100;
    private static final int MAX_NITS = 10000;

    public DolbyVisionNitsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateSummary();
    }

    public DolbyVisionNitsPreference(Context context) {
        super(context);
        updateSummary();
    }

    @Override
    protected void onClick() {
        showNitsDialog();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        updateSummary();
    }

    private int getStoredValue() {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(getContext())
                    .getString(VideoPreferencesCommon.KEY_DOLBY_VISION_TARGET_NITS, "0");
            return (int) Float.parseFloat(v);
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }

    private void storeValue(int nits) {
        SharedPreferences.Editor ed = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        ed.putString(VideoPreferencesCommon.KEY_DOLBY_VISION_TARGET_NITS, String.valueOf(nits));
        ed.apply();
        updateSummary();
        if (getOnPreferenceChangeListener() != null)
            getOnPreferenceChangeListener().onPreferenceChange(this, String.valueOf(nits));
    }

    /** Display-reported desired max luminance, same source as PlayerActivity auto mode. */
    private float getDisplayMaxNits() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.view.Display.HdrCapabilities caps =
                        ((android.app.Activity) getContext()).getWindowManager().getDefaultDisplay().getHdrCapabilities();
                if (caps != null && caps.getDesiredMaxLuminance() > 0f)
                    return caps.getDesiredMaxLuminance();
            } catch (Exception e) {
                log.debug("getDisplayMaxNits: {}", e.getMessage());
            }
        }
        return 0f;
    }

    private void updateSummary() {
        int v = getStoredValue();
        if (v > 0) {
            setSummary(getContext().getString(R.string.pref_dolby_vision_nits_manual, v));
        } else {
            float auto = getDisplayMaxNits();
            if (auto > 0f)
                setSummary(getContext().getString(R.string.pref_dolby_vision_nits_auto_resolved, (int) auto));
            else
                setSummary(getContext().getString(R.string.pref_dolby_vision_nits_auto_source));
        }
    }

    private void showNitsDialog() {
        Context ctx = getContext();
        View view = LayoutInflater.from(ctx).inflate(R.layout.dolby_vision_nits_dialog, null);
        SeekBar slider = view.findViewById(R.id.dv_nits_slider);
        EditText text = view.findViewById(R.id.dv_nits_text);
        CheckBox auto = view.findViewById(R.id.dv_nits_auto);
        TextView autoInfo = view.findViewById(R.id.dv_nits_auto_info);

        int stored = getStoredValue();
        // linear 100..10000 in steps of 10: progress = (nits - 100) / 10
        slider.setMax((MAX_NITS - MIN_NITS) / 10);
        int init = stored > 0 ? Math.max(MIN_NITS, Math.min(MAX_NITS, stored)) : 400;
        slider.setProgress((init - MIN_NITS) / 10);
        text.setText(stored > 0 ? String.valueOf(stored) : "");
        text.setInputType(InputType.TYPE_CLASS_NUMBER);
        float displayMax = getDisplayMaxNits();
        autoInfo.setText(displayMax > 0f
                ? ctx.getString(R.string.pref_dolby_vision_nits_auto_resolved, (int) displayMax)
                : ctx.getString(R.string.pref_dolby_vision_nits_auto_source));
        auto.setChecked(stored <= 0);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int nits = MIN_NITS + progress * 10;
                    text.setText(String.valueOf(nits));
                    auto.setChecked(false);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        text.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                try {
                    int n = Integer.parseInt(text.getText().toString().trim());
                    n = Math.max(MIN_NITS, Math.min(MAX_NITS, n));
                    slider.setProgress((n - MIN_NITS) / 10);
                    auto.setChecked(false);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        auto.setOnCheckedChangeListener((btn, checked) -> {
            if (checked)
                text.setText("");
            else if (text.getText().length() == 0)
                text.setText(String.valueOf(MIN_NITS + slider.getProgress() * 10));
        });

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.preference_dolby_vision_target_nits)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int nits;
                    if (auto.isChecked()) {
                        nits = 0;
                    } else {
                        try {
                            nits = Integer.parseInt(text.getText().toString().trim());
                            nits = Math.max(MIN_NITS, Math.min(MAX_NITS, nits));
                        } catch (NumberFormatException e) {
                            nits = 0; // invalid -> auto
                        }
                    }
                    storeValue(nits);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
