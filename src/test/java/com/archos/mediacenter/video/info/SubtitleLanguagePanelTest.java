// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.info;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import androidx.preference.PreferenceManager;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.ThemeManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class)
public class SubtitleLanguagePanelTest {
    @Test public void phoneAndTvShowThemedNonFocusableProgressAndRecoverActionFocus() {
        for (boolean tv : new boolean[]{false, true}) for (boolean black : new boolean[]{false, true}) {
            ReflectionHelpers.setStaticField(ThemeManager.class, "sInstance", null);
            PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication()).edit()
                    .putString(ThemeManager.KEY_APP_THEME, black ? ThemeManager.THEME_BLACK : ThemeManager.THEME_BLUE).commit();
            int theme = tv ? (black ? R.style.MyLeanbackTheme_Black : R.style.MyLeanbackTheme)
                    : (black ? R.style.ArchosThemeBlackNoActionBar : R.style.ArchosThemeBlueNoActionBar);
            Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme);
            FrameLayout parent = new FrameLayout(context);
            View root = LayoutInflater.from(context).inflate(tv ? R.layout.androidtv_subtitles_info_group
                    : R.layout.video_info2_subtitles, parent, false);
            parent.addView(root);
            View fallback = root.findViewById(tv ? R.id.action_get_online_subtitles : R.id.subtitles_online);
            SubtitleLanguagePanel panel = new SubtitleLanguagePanel(root, fallback, null);
            SubtitleLanguageController controller = mock(SubtitleLanguageController.class);
            when(controller.canAnalyze()).thenReturn(true);
            panel.bind(controller);
            ArgumentCaptor<Runnable> listener = ArgumentCaptor.forClass(Runnable.class);
            verify(controller).addListener(listener.capture());
            listener.getValue().run();
            View analyze = root.findViewById(R.id.subtitle_languages);
            assertEquals(View.VISIBLE, analyze.getVisibility());
            assertTrue(analyze.requestFocus());
            analyze.performClick();
            verify(controller).analyze();
            when(controller.isBusy()).thenReturn(true);
            when(controller.canCancel()).thenReturn(true);
            when(controller.getStatus()).thenReturn("Identifying subtitle languages…");
            listener.getValue().run();
            ProgressBar progress = root.findViewById(R.id.subtitle_language_progress);
            assertEquals(View.VISIBLE, progress.getVisibility());
            assertTrue(progress.isIndeterminate());
            assertFalse(progress.isFocusable());
            parent.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            parent.layout(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight());
            assertEquals(root.findViewById(R.id.subtitle_language_actions).getWidth(), progress.getWidth());
            assertEquals("The animation must span the full bar, without intrinsic aspect-ratio constraining it",
                    progress.getWidth() - progress.getPaddingLeft() - progress.getPaddingRight(),
                    progress.getIndeterminateDrawable().getBounds().width());
            Rect line = new Rect(), action = new Rect();
            progress.getDrawingRect(line);
            ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(progress, line);
            root.findViewById(R.id.subtitle_language_cancel).getDrawingRect(action);
            ((android.view.ViewGroup) root).offsetDescendantRectToMyCoords(root.findViewById(R.id.subtitle_language_cancel), action);
            assertTrue("The progress line belongs below the actions", line.top >= action.bottom);
            assertEquals(ThemeManager.getInstance(context).getAccentColor(), progress.getIndeterminateTintList().getDefaultColor());
            View cancel = root.findViewById(R.id.subtitle_language_cancel);
            assertTrue("Cancel must inherit Identify's focus", cancel.hasFocus());
            cancel.performClick();
            verify(controller).cancel();
            when(controller.isBusy()).thenReturn(false);
            when(controller.canCancel()).thenReturn(false);
            when(controller.canAnalyze()).thenReturn(false);
            when(controller.canSave()).thenReturn(true);
            listener.getValue().run();
            assertEquals(View.GONE, progress.getVisibility());
            assertTrue(root.findViewById(R.id.subtitle_language_save).hasFocus());
            panel.detach();
            verify(controller).removeListener(listener.getValue());
        }
    }
}
