// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.view.ViewGroup;
import android.view.View;
import android.graphics.Rect;
import android.view.WindowInsets;
import android.graphics.Insets;
import com.archos.mediacenter.video.R;
import java.util.concurrent.ExecutorService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class)
public class SubtitleLanguageActivityTest {
    @Test
    public void phoneLaunchUsesCompatibleManifestTheme() {
        launchWithManifestTheme();
    }

    @Test
    @Config(qualifiers = "television")
    public void televisionLaunchUsesCompatibleManifestTheme() {
        launchWithManifestTheme();
    }

    @Test
    @Config(sdk = 33)
    public void systemBarsAndToolbarDoNotCoverActions() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SubtitleLanguageActivity.class)
                .setData(Uri.parse("file:///subtitle-language-test.mkv"));
        try (ActivityController<SubtitleLanguageActivity> controller =
                     Robolectric.buildActivity(SubtitleLanguageActivity.class, intent)) {
            SubtitleLanguageActivity activity = controller.get();
            ReflectionHelpers.setField(activity, "worker", mock(ExecutorService.class));
            controller.create();
            ViewGroup root = activity.findViewById(R.id.subtitle_language_root);
            root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 161, 0, 59)).build());
            root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2404, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1080, 2404);
            View toolbar = activity.findViewById(R.id.subtitle_language_toolbar);
            View button = activity.findViewById(R.id.subtitle_language_analyze);
            Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
            root.offsetDescendantRectToMyCoords(button, bounds);
            assertTrue("Toolbar must clear the status bar", toolbar.getTop() >= 161);
            assertTrue("Identify must clear the toolbar", bounds.top >= toolbar.getBottom());
            assertTrue("Identify must be visible above navigation", bounds.bottom <= 2404 - 59);
            assertTrue(button.hasOnClickListeners());
        }
    }

    private void launchWithManifestTheme() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SubtitleLanguageActivity.class)
                .setData(Uri.parse("file:///subtitle-language-test.mkv"));
        try (ActivityController<SubtitleLanguageActivity> controller =
                     Robolectric.buildActivity(SubtitleLanguageActivity.class, intent)) {
            SubtitleLanguageActivity activity = controller.get();
            ExecutorService worker = mock(ExecutorService.class);
            // Exercise real activity inflation with the manifest theme, without native/network I/O.
            ReflectionHelpers.setField(activity, "worker", worker);
            controller.create();
            assertEquals(activity.getString(R.string.subtitle_languages_identify), activity.getTitle());
            ViewGroup content = activity.findViewById(android.R.id.content);
            assertTrue(content.getChildCount() > 0);
            verify(worker).execute(any(Runnable.class));
        }
    }
}
