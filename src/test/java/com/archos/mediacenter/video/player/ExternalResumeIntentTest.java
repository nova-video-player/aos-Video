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

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalResumeIntentTest {

    @Test
    public void acceptsIntegerAndLongPositions() {
        assertEquals(12_000, ExternalResumeIntent.readLaunchPosition(
                intentWith(ExternalResumeIntent.POSITION, 12_000)));
        assertEquals(13_000, ExternalResumeIntent.readLaunchPosition(
                intentWith(ExternalResumeIntent.START_FROM, 13_000L)));
    }

    @Test
    public void preservesPositionPriority() {
        Map<String, Object> values = new HashMap<>();
        values.put(ExternalResumeIntent.RESUME_POSITION, 10_000);
        values.put(ExternalResumeIntent.POSITION, 20_000);
        values.put(ExternalResumeIntent.START_FROM, 30_000);
        Intent intent = intentWith(values);
        assertEquals(30_000, ExternalResumeIntent.readLaunchPosition(intent));
    }

    @Test
    public void zeroAndInvalidValuesDoNotSelectRemoteResume() {
        Intent zero = intentWith(ExternalResumeIntent.POSITION, 0);
        assertTrue(ExternalResumeIntent.hasPositionExtra(zero));
        assertFalse(ExternalResumeIntent.hasValidLaunchPosition(zero));
        assertFalse(ExternalResumeIntent.hasValidLaunchPosition(
                intentWith(ExternalResumeIntent.POSITION, "12000")));
        assertFalse(ExternalResumeIntent.hasValidLaunchPosition(
                intentWith(ExternalResumeIntent.POSITION, (long) Integer.MAX_VALUE + 1)));
    }

    @Test
    public void traceDescriptionRedactsNonResumeValues() {
        Bundle headers = mock(Bundle.class);
        when(headers.toString()).thenReturn("Authorization=secret");
        Map<String, Object> values = new HashMap<>();
        values.put("headers", headers);
        values.put(ExternalResumeIntent.POSITION, 12_000);
        String description = ExternalResumeIntent.describeForTrace(intentWith(values));
        assertTrue(description.contains("position(Integer)=12000"));
        assertTrue(description.contains("headers(Bundle)"));
        assertFalse(description.contains("secret"));
    }

    private static Intent intentWith(String key, Object value) {
        Map<String, Object> values = new HashMap<>();
        values.put(key, value);
        return intentWith(values);
    }

    @SuppressWarnings("deprecation")
    private static Intent intentWith(Map<String, Object> values) {
        Intent intent = mock(Intent.class);
        Bundle extras = mock(Bundle.class);
        when(intent.getExtras()).thenReturn(extras);
        when(intent.hasExtra(anyString())).thenAnswer(
                invocation -> values.containsKey(invocation.getArgument(0)));
        when(extras.get(anyString())).thenAnswer(
                invocation -> values.get(invocation.getArgument(0)));
        when(extras.keySet()).thenReturn(values.keySet());
        return intent;
    }
}
