// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.FrameLayout;
import androidx.leanback.widget.Presenter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoMetadata;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class, qualifiers = "land-television-xhdpi")
public class SubtitleLanguageActionLayoutTest {
    @Test public void identifyIsBelowOtherActionsAndReachableByDpad() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.MyLeanbackTheme);
        FrameLayout parent = new FrameLayout(context);
        SubtitleInterface actions = mock(SubtitleInterface.class);
        when(actions.canIdentifySubtitleLanguages()).thenReturn(true);
        VideoMetadata metadata = mock(VideoMetadata.class);
        when(metadata.hasUnknownSubtitleLanguages()).thenReturn(true);
        Video video = mock(Video.class);
        when(video.getFileUri()).thenReturn(Uri.parse("file:///video.mkv"));
        when(video.getMetadata()).thenReturn(metadata);
        SubtitlesDetailsRowPresenter presenter = new SubtitlesDetailsRowPresenter(actions, 0xff222222);
        Presenter.ViewHolder holder = presenter.onCreateViewHolder(parent);
        parent.addView(holder.view);
        SubtitlesDetailsRow row = new SubtitlesDetailsRow(context, video, Collections.emptyList());
        presenter.onBindViewHolder(holder, row);
        parent.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST));
        parent.layout(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight());

        View identify = parent.findViewById(R.id.subtitle_languages);
        View download = parent.findViewById(R.id.action_get_online_subtitles);
        View choose = parent.findViewById(R.id.action_choose_subtitles);
        assertEquals(View.VISIBLE, identify.getVisibility());
        Rect bounds = bounds(parent, identify);
        assertTrue(identify.getWidth() > 0 && identify.getHeight() > 0);
        assertTrue("Identify must not overlap Download", bounds.top >= bounds(parent, download).bottom);
        assertTrue("Identify must not overlap Choose", bounds.top >= bounds(parent, choose).bottom);
        assertTrue("Identify must fit inside its row", bounds.bottom <= parent.getHeight());
        assertSame(identify, android.view.FocusFinder.getInstance().findNextFocus(parent, download, View.FOCUS_DOWN));
        assertSame(identify, android.view.FocusFinder.getInstance().findNextFocus(parent, choose, View.FOCUS_DOWN));
        assertTrue(identify.performClick());
        verify(actions).performSubtitleLanguageAnalysis();

        when(metadata.hasUnknownSubtitleLanguages()).thenReturn(false);
        presenter.onBindViewHolder(holder, row);
        assertEquals(View.GONE, identify.getVisibility());
        when(metadata.hasInferredSubtitleLanguages()).thenReturn(true);
        presenter.onBindViewHolder(holder, row);
        assertEquals(View.VISIBLE, identify.getVisibility());
        when(actions.canIdentifySubtitleLanguages()).thenReturn(false);
        presenter.onBindViewHolder(holder, row);
        assertEquals(View.GONE, identify.getVisibility());
    }

    private Rect bounds(FrameLayout parent, View view) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        parent.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }
}
