// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.mediacenter.video.leanback.details;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.VerticalGridView;
import androidx.leanback.widget.Presenter;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.info.SubtitleLanguageController;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoMetadata;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {23, 33}, application = Application.class, qualifiers = "land-television-xhdpi")
public class SubtitleLanguageActionLayoutTest {
    @Test public void shortFileInfoRemainsFocusableWithoutActionButtons() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.MyLeanbackTheme);
        FrameLayout parent = new FrameLayout(context);
        DetailsScrollView scroll = (DetailsScrollView) LayoutInflater.from(context)
                .inflate(R.layout.androidtv_detailled_info_group, parent, false);
        // Keep the real file-info pane's configuration, with content that fits entirely.
        scroll.removeAllViews();
        TextView text = new TextView(context);
        text.setText("Short file information");
        scroll.addView(text);
        parent.addView(scroll);
        layout(parent);
        assertTrue(text.getHeight() <= scroll.getHeight());
        assertTrue("File info has no buttons and must always remain a focus target", scroll.isFocusable());
        assertTrue(scroll.requestFocus());
    }

    @Test public void sharedFileAndSubtitlePanesExitToAdjacentRows() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Context context = new ContextThemeWrapper(activity, R.style.MyLeanbackTheme);
        VerticalGridView grid = new VerticalGridView(context);
        Presenter panePresenter = new Presenter() {
            @Override public ViewHolder onCreateViewHolder(ViewGroup parent) {
                DetailsScrollView scroll = new DetailsScrollView(context, null);
                scroll.setFocusable(true);
                scroll.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
                scroll.setLayoutParams(new ViewGroup.LayoutParams(1200, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView text = new TextView(context);
                text.setText(String.join("\n", Collections.nCopies(80, "File or subtitle information")));
                scroll.addView(text);
                return new ViewHolder(scroll);
            }
            @Override public void onBindViewHolder(ViewHolder holder, Object item) { }
            @Override public void onUnbindViewHolder(ViewHolder holder) { }
        };
        ArrayObjectAdapter rows = new ArrayObjectAdapter(panePresenter);
        rows.add("previous");
        rows.add("current");
        rows.add("next");
        grid.setAdapter(new ItemBridgeAdapter(rows));
        activity.setContentView(grid);
        grid.setSelectedPosition(1);
        grid.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 1920, 1080);
        DetailsScrollView scroll = (DetailsScrollView) grid.findViewHolderForAdapterPosition(1).itemView;
        assertTrue(scroll.getHeight() <= context.getResources().getDisplayMetrics().heightPixels * 0.8f);
        scroll.requestFocus();
        assertTrue(scroll.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertEquals("Top boundary must select the previous Leanback row", 0, grid.getSelectedPosition());
        grid.setSelectedPosition(1);
        scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
        assertTrue(scroll.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        assertEquals("Bottom boundary must select the next Leanback row", 2, grid.getSelectedPosition());
        activity.finish();
    }

    @Test public void longTrackListScrollsWithoutHidingActionsAndShrinksOnRebind() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.MyLeanbackTheme);
        FrameLayout parent = new FrameLayout(context);
        SubtitleInterface actions = mock(SubtitleInterface.class);
        when(actions.canIdentifySubtitleLanguages()).thenReturn(true);
        SubtitleLanguageController controller = mock(SubtitleLanguageController.class);
        when(controller.canAnalyze()).thenReturn(true);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(controller).addListener(any());
        when(actions.getSubtitleLanguageController()).thenReturn(controller);
        VideoMetadata metadata = mock(VideoMetadata.class);
        when(metadata.hasUnknownSubtitleLanguages()).thenReturn(true);
        when(metadata.getSubtitleTrackNb()).thenReturn(40);
        when(metadata.getSubtitleTrack(anyInt())).thenReturn(mock(VideoMetadata.SubtitleTrack.class));
        Video video = mock(Video.class);
        when(video.getFileUri()).thenReturn(Uri.parse("file:///modern-family.mkv"));
        when(video.getMetadata()).thenReturn(metadata);
        SubtitlesDetailsRowPresenter presenter = new SubtitlesDetailsRowPresenter(actions, 0xff222222);
        Presenter.ViewHolder holder = presenter.onCreateViewHolder(parent);
        parent.addView(holder.view);
        SubtitlesDetailsRow row = new SubtitlesDetailsRow(context, video, Collections.emptyList());
        presenter.onBindViewHolder(holder, row);
        layout(parent);

        ScrollView scroll = parent.findViewById(R.id.subtitle_tracks_scroll);
        View download = parent.findViewById(R.id.action_get_online_subtitles);
        View choose = parent.findViewById(R.id.action_choose_subtitles);
        View identify = parent.findViewById(R.id.subtitle_languages);
        int range = scroll.getChildAt(0).getHeight() - scroll.getHeight();
        assertTrue("Many tracks must overflow the bounded list", range > 0);
        assertTrue("Overflowing lists must be focusable", scroll.isFocusable());
        assertTrue(scroll.getHeight() <= context.getResources().getDisplayMetrics().heightPixels * 0.4f);
        assertTrue(bounds(parent, download).top >= bounds(parent, scroll).bottom);
        assertTrue("Actions must fit on screen", bounds(parent, identify).bottom <= 1080);

        assertTrue(choose.requestFocus());
        assertTrue(choose.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertTrue(scroll.hasFocus());
        scroll.setSmoothScrollingEnabled(false);
        assertTrue(scroll.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        assertTrue("Scrolling must retain list focus", scroll.hasFocus());
        scroll.scrollTo(0, range);
        assertTrue(scroll.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        assertTrue("At bottom, Down must reach actions", download.hasFocus());
        assertTrue(download.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertTrue(scroll.hasFocus());
        assertEquals("Returning from actions preserves reading position", range, scroll.getScrollY());

        int longHeight = scroll.getHeight();
        when(metadata.getSubtitleTrackNb()).thenReturn(1);
        presenter.onBindViewHolder(holder, row);
        layout(parent);
        assertEquals(0, scroll.getScrollY());
        assertTrue("Short lists must not leave a large empty viewport", scroll.getHeight() < longHeight);
        assertFalse("Short lists must not add an unnecessary D-pad stop", scroll.isFocusable());
        assertTrue("Shrinking a focused list must move directly to Download", download.hasFocus());
        when(metadata.getSubtitleTrackNb()).thenReturn(0);
        presenter.onBindViewHolder(holder, row);
        assertEquals(View.GONE, scroll.getVisibility());
        assertTrue("Removing tracks must not lose focus", download.hasFocus());
    }

    private void layout(FrameLayout parent) {
        parent.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST));
        parent.layout(0, 0, parent.getMeasuredWidth(), parent.getMeasuredHeight());
    }

    @Test public void identifyIsBelowOtherActionsAndReachableByDpad() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.MyLeanbackTheme);
        FrameLayout parent = new FrameLayout(context);
        SubtitleInterface actions = mock(SubtitleInterface.class);
        when(actions.canIdentifySubtitleLanguages()).thenReturn(true);
        SubtitleLanguageController controller = mock(SubtitleLanguageController.class);
        when(controller.canAnalyze()).thenReturn(true);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(controller).addListener(any());
        when(actions.getSubtitleLanguageController()).thenReturn(controller);
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
        verify(controller).analyze();
        when(controller.canSave()).thenReturn(true);
        presenter.onBindViewHolder(holder, row);
        layout(parent);
        View save = parent.findViewById(R.id.subtitle_language_save);
        assertTrue(choose.requestFocus());
        assertTrue(choose.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        assertTrue("Down must retain the right column", save.hasFocus());
        assertTrue(save.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        assertTrue(choose.hasFocus());
        when(controller.canSave()).thenReturn(false);


        when(controller.canAnalyze()).thenReturn(false);
        presenter.onBindViewHolder(holder, row);
        assertEquals(View.GONE, identify.getVisibility());
        when(controller.canSave()).thenReturn(true);
        presenter.onBindViewHolder(holder, row);
        assertEquals(View.VISIBLE, parent.findViewById(R.id.subtitle_language_save).getVisibility());
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
