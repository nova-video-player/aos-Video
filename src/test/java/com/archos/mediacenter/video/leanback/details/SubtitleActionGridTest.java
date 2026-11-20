// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.archos.mediacenter.video.R;
import java.util.Collections;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class,
        qualifiers = "w960dp-h540dp-land-television-xhdpi")
public class SubtitleActionGridTest {
    @Test
    @Config(sdk = 33)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void translatedLabelsWrapAndColumnsStayAlignedIncludingRtl() {
        for (String language : new String[]{"en", "fr", "de", "fi", "ru", "ar", "ja"}) {
            Configuration configuration = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
            configuration.setLocale(new Locale(language));
            configuration.fontScale = 1.3f;
            Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication()
                    .createConfigurationContext(configuration), R.style.MyLeanbackTheme);
            FrameLayout root = new FrameLayout(context);
            root.addView(LayoutInflater.from(context).inflate(R.layout.androidtv_subtitles_info_group, root, false));
            // Exercise start/end positioning even on API 23's detached test hierarchy.
            root.setLayoutDirection(configuration.getLayoutDirection());
            ViewGroup actions = root.findViewById(R.id.subtitle_language_actions);
            actions.setLayoutDirection(configuration.getLayoutDirection());
            View download = root.findViewById(R.id.action_get_online_subtitles);
            ((View) download.getParent()).setLayoutDirection(configuration.getLayoutDirection());
            actions.setVisibility(View.VISIBLE);
            Button identify = root.findViewById(R.id.subtitle_languages);
            identify.setVisibility(View.VISIBLE);
            layout(root);
            assertEquals("A lone action must retain one column's width", download.getWidth(), identify.getWidth());
            assertEquals(bounds(root, download).left, bounds(root, identify).left);
            Button save = root.findViewById(R.id.subtitle_language_save);
            save.setVisibility(View.VISIBLE);
            layout(root);
            View choose = root.findViewById(R.id.action_choose_subtitles);
            assertEquals(bounds(root, choose).left, bounds(root, save).left);
            assertEquals(identify.getWidth(), save.getWidth());
            assertEquals(identify.getHeight(), save.getHeight());
            for (int id : new int[]{R.id.action_get_online_subtitles, R.id.action_choose_subtitles,
                    R.id.subtitle_languages, R.id.subtitle_language_save}) assertUncut(root.findViewById(id));

            // Future translations can be much longer than the currently supplied strings.
            identify.setText(String.join(" ", Collections.nCopies(12, "Identification des langues des sous-titres")));
            layout(root);
            assertTrue(identify.getLineCount() > 3);
            assertEquals(identify.getHeight(), save.getHeight());
            assertUncut(identify);
            identify.setVisibility(View.GONE);
            layout(root);
            assertEquals("A lone Save also belongs in the start column", bounds(root, download).left, bounds(root, save).left);
        }
    }

    @Test public void wrappedActionsReduceTrackViewportInsteadOfClippingButtons() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.MyLeanbackTheme);
        FrameLayout root = new FrameLayout(context);
        root.addView(LayoutInflater.from(context).inflate(R.layout.androidtv_subtitles_info_group, root, false));
        TextView tracks = root.findViewById(R.id.integrated_subtitles_values_col1);
        tracks.setText(String.join("\n", Collections.nCopies(60, "English (TEXT) (AI 99%)")));
        root.findViewById(R.id.subtitle_language_actions).setVisibility(View.VISIBLE);
        Button identify = root.findViewById(R.id.subtitle_languages);
        identify.setVisibility(View.VISIBLE);
        layout(root);
        View viewport = root.findViewById(R.id.subtitle_tracks_scroll);
        int initialHeight = viewport.getHeight();
        for (int id : new int[]{R.id.subtitle_language_save, R.id.subtitle_language_restore}) {
            Button button = root.findViewById(id);
            button.setVisibility(View.VISIBLE);
            button.setText("Restore the previously saved language metadata for every subtitle track in this video file");
        }
        root.findViewById(R.id.subtitle_language_feedback).setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.subtitle_language_status)).setText("Subtitle language results");
        layout(root);
        assertTrue("Extra rows must shrink the track viewport", viewport.getHeight() < initialHeight);
        assertTrue("Tracks must remain scrollable", viewport.getHeight() > 0);
        assertUncut(root.findViewById(R.id.subtitle_language_restore));
        assertTrue(bounds(root, root.findViewById(R.id.subtitle_language_restore)).bottom <= 1080);
    }

    private void assertUncut(Button button) {
        assertEquals(View.TEXT_ALIGNMENT_VIEW_START, button.getTextAlignment());
        assertEquals(Gravity.START, button.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);
        Layout text = button.getLayout();
        assertNotNull(text);
        assertEquals(text.getText().length(), text.getLineEnd(text.getLineCount() - 1));
        for (int i = 0; i < text.getLineCount(); i++) assertEquals(0, text.getEllipsisCount(i));
        assertTrue(button.getHeight() >= text.getHeight() + button.getCompoundPaddingTop() + button.getCompoundPaddingBottom());
    }
    private void layout(FrameLayout root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }
    private Rect bounds(FrameLayout root, View child) {
        Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
        root.offsetDescendantRectToMyCoords(child, bounds);
        return bounds;
    }
}
