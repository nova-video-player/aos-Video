// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.player;

import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compatibility boundary for resume positions supplied by other players/applications. */
final class ExternalResumeIntent {
    static final String FLOATING_POSITION = "floating_player_position";
    static final String START_FROM = "startfrom";
    static final String POSITION = "position";
    static final String RESUME_POSITION = "resume_position";
    static final String EXTERNAL_PLAYER_LAUNCH = "nova_external_player_launch";

    private ExternalResumeIntent() {}

    /**
     * Return a launch position in milliseconds. Integer and Long are both accepted because
     * external-player APIs disagree on the numeric type. The ordering preserves Nova's historic
     * behavior while giving its internal floating-player handoff highest priority.
     */
    static int readLaunchPosition(Intent intent) {
        int position = readPosition(intent, FLOATING_POSITION);
        if (position <= 0) position = readPosition(intent, START_FROM);
        if (position <= 0) position = readPosition(intent, POSITION);
        if (position <= 0) position = readPosition(intent, RESUME_POSITION);
        return position;
    }

    /** Presence is used to avoid treating a repeated external ACTION_VIEW as a history launch. */
    static boolean hasPositionExtra(Intent intent) {
        return intent != null && (intent.hasExtra(START_FROM)
                || intent.hasExtra(POSITION)
                || intent.hasExtra(RESUME_POSITION));
    }

    static boolean hasValidLaunchPosition(Intent intent) {
        return readLaunchPosition(intent) > 0;
    }

    @SuppressWarnings("deprecation") // Bundle.get is required to accept either Integer or Long.
    static int readPosition(Intent intent, String key) {
        if (intent == null || !intent.hasExtra(key)) return -1;
        Bundle extras = intent.getExtras();
        if (extras == null) return -1;
        Object value = extras.get(key);
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Long) {
            long longValue = (Long) value;
            return longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE
                    ? (int) longValue : -1;
        }
        return -1;
    }

    /**
     * Trace diagnostic that keeps all extra names/types but prints values only for resume keys.
     * Header bundles and authentication tokens must never be expanded into logs.
     */
    @SuppressWarnings("deprecation")
    static String describeForTrace(Intent intent) {
        if (intent == null) return "intent=null";
        StringBuilder result = new StringBuilder("action=")
                .append(intent.getAction())
                // The complete URI may itself contain credentials or access tokens.
                .append(" dataScheme=")
                .append(intent.getData() != null ? intent.getData().getScheme() : null)
                .append(" extras=[");
        Bundle extras = intent.getExtras();
        if (extras != null) {
            List<String> keys = new ArrayList<>(extras.keySet());
            Collections.sort(keys);
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                Object value = extras.get(key);
                if (i > 0) result.append(", ");
                result.append(key).append('(')
                        .append(value != null ? value.getClass().getSimpleName() : "null")
                        .append(')');
                if (isResumeKey(key)) result.append('=').append(value);
            }
        }
        return result.append(']').toString();
    }

    private static boolean isResumeKey(String key) {
        return FLOATING_POSITION.equals(key) || START_FROM.equals(key)
                || POSITION.equals(key) || RESUME_POSITION.equals(key)
                || "player_service_session_position".equals(key) || "resume".equals(key);
    }
}
