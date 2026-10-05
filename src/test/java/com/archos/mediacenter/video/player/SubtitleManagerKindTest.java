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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.FrameLayout;

import com.archos.mediacenter.video.utils.VideoMetadata;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * SubtitleManager is the single Java-side interpreter of the native subtitle kind
 * (SUB_KIND in native sub_kind.h). These tests pin that interpretation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SubtitleManagerKindTest {

    private SubtitleManager mManager;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        mManager = new SubtitleManager(ctx, new FrameLayout(ctx), null, false);
    }

    @Test
    public void kindFromNative_knownValuesPassThrough() {
        assertEquals(SubtitleManager.KIND_SSA, SubtitleManager.kindFromNative(1));
        assertEquals(SubtitleManager.KIND_PLAIN_TEXT, SubtitleManager.kindFromNative(2));
        assertEquals(SubtitleManager.KIND_GRAPHIC, SubtitleManager.kindFromNative(3));
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, SubtitleManager.kindFromNative(4));
    }

    @Test
    public void kindFromNative_missingOrUnknownFailsSafe() {
        // 0 is what VideoMetadata reads when native sent no kind (older native build).
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, SubtitleManager.kindFromNative(0));
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, SubtitleManager.kindFromNative(-1));
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, SubtitleManager.kindFromNative(5));
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, SubtitleManager.kindFromNative(99));
    }

    @Test
    public void predicatesPerKind() {
        check(SubtitleManager.KIND_NONE,        false, false);
        check(SubtitleManager.KIND_SSA,      false, true);
        check(SubtitleManager.KIND_PLAIN_TEXT,  false, true);
        check(SubtitleManager.KIND_GRAPHIC,     true,  false);
        check(SubtitleManager.KIND_UNSUPPORTED, false, false);
    }

    private void check(int kind, boolean graphic, boolean styleOk) {
        // move to a different kind first so setSubtitleKind() really transitions
        mManager.setSubtitleKind(kind == SubtitleManager.KIND_SSA
                ? SubtitleManager.KIND_PLAIN_TEXT : SubtitleManager.KIND_SSA);
        mManager.setSubtitleKind(kind);
        assertEquals("kind " + kind, kind, mManager.getSubtitleKind());
        assertEquals("isGraphic, kind " + kind, graphic, mManager.isGraphic());
        assertEquals("supportsUserStyle, kind " + kind, styleOk, mManager.supportsUserStyle());
    }

    @Test
    public void layoutCategoryPerKind() {
        mManager.setSubtitleKind(SubtitleManager.KIND_SSA);
        assertEquals(SurfaceController.SUBTITLE_CATEGORY_ASS, mManager.getLayoutCategory());
        mManager.setSubtitleKind(SubtitleManager.KIND_GRAPHIC);
        assertEquals(SurfaceController.SUBTITLE_CATEGORY_GFX, mManager.getLayoutCategory());
        mManager.setSubtitleKind(SubtitleManager.KIND_PLAIN_TEXT);
        assertEquals(SurfaceController.SUBTITLE_CATEGORY_PLAIN_TEXT, mManager.getLayoutCategory());
        mManager.setSubtitleKind(SubtitleManager.KIND_UNSUPPORTED);
        assertEquals(SurfaceController.SUBTITLE_CATEGORY_PLAIN_TEXT, mManager.getLayoutCategory());
        mManager.setSubtitleKind(SubtitleManager.KIND_NONE);
        assertEquals(SurfaceController.SUBTITLE_CATEGORY_PLAIN_TEXT, mManager.getLayoutCategory());
    }

    @Test
    public void userVerticalPositionSurvivesGraphicTracks() {
        // The saved position is what the settings dialog persists; a bitmap track must not
        // overwrite it with 0, and switching back to text must keep it.
        mManager.setSubtitleKind(SubtitleManager.KIND_PLAIN_TEXT);
        mManager.setVerticalPosition(40);
        assertEquals(40, mManager.getVerticalPosition());

        mManager.setSubtitleKind(SubtitleManager.KIND_GRAPHIC);
        assertEquals(40, mManager.getVerticalPosition());

        mManager.setVerticalPosition(40); // PlayerActivity passes the user's value on every track
        assertEquals(40, mManager.getVerticalPosition());

        mManager.setSubtitleKind(SubtitleManager.KIND_SSA);
        assertEquals(40, mManager.getVerticalPosition());
    }

    @Test
    public void staticRulesMatchInstanceRules() {
        for (int kind = SubtitleManager.KIND_NONE; kind <= SubtitleManager.KIND_UNSUPPORTED; kind++) {
            mManager.setSubtitleKind(kind == SubtitleManager.KIND_SSA
                    ? SubtitleManager.KIND_PLAIN_TEXT : SubtitleManager.KIND_SSA);
            mManager.setSubtitleKind(kind);
            assertEquals(SubtitleManager.isGraphic(kind), mManager.isGraphic());
            assertEquals(SubtitleManager.isPlainText(kind), mManager.isPlainText());
            assertEquals(SubtitleManager.isStyled(kind), mManager.isStyled());
            assertEquals(SubtitleManager.supportsUserStyle(kind), mManager.supportsUserStyle());
            assertEquals(SubtitleManager.canChooseOverrideMode(kind), mManager.canChooseOverrideMode());
        }
    }

    @Test
    public void plainTextIsLockedToCustom_styledKeepsStoredMode() {
        int[] stored = {SubtitleManager.OVERRIDE_EMBEDDED, SubtitleManager.OVERRIDE_CUSTOM,
                SubtitleManager.OVERRIDE_SCALE_ONLY};
        for (int mode : stored) {
            assertEquals(SubtitleManager.OVERRIDE_CUSTOM,
                    SubtitleManager.effectiveOverrideMode(SubtitleManager.KIND_PLAIN_TEXT, mode));
            assertEquals(mode, SubtitleManager.effectiveOverrideMode(SubtitleManager.KIND_SSA, mode));
        }
        assertTrue(SubtitleManager.canChooseOverrideMode(SubtitleManager.KIND_SSA));
        assertFalse(SubtitleManager.canChooseOverrideMode(SubtitleManager.KIND_PLAIN_TEXT));
        assertFalse(SubtitleManager.canChooseOverrideMode(SubtitleManager.KIND_GRAPHIC));
    }

    @Test
    public void effectiveModeFiltersButNeverOverwritesTheStoredMode() {
        mManager.setOverrideMode(SubtitleManager.OVERRIDE_SCALE_ONLY);
        mManager.setSubtitleKind(SubtitleManager.KIND_PLAIN_TEXT);
        assertEquals(SubtitleManager.OVERRIDE_CUSTOM, mManager.getEffectiveOverrideMode());
        assertEquals(SubtitleManager.OVERRIDE_SCALE_ONLY, mManager.getOverrideMode());
        mManager.setSubtitleKind(SubtitleManager.KIND_SSA);
        assertEquals(SubtitleManager.OVERRIDE_SCALE_ONLY, mManager.getEffectiveOverrideMode());
    }

    @Test
    public void kindOfTrack_isPureAndFailsSafe() throws Exception {
        assertEquals(SubtitleManager.KIND_NONE, SubtitleManager.kindOf(null));
        assertEquals(SubtitleManager.KIND_GRAPHIC, SubtitleManager.kindOf(trackWithRawKind(3)));
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, SubtitleManager.kindOf(trackWithRawKind(0)));
        assertEquals(SubtitleManager.KIND_NONE, mManager.getSubtitleKind()); // reading changed nothing
    }

    @Test
    public void setActiveTrack_nullIsNone() {
        mManager.setSubtitleKind(SubtitleManager.KIND_GRAPHIC);
        mManager.setActiveTrack(null);
        assertEquals(SubtitleManager.KIND_NONE, mManager.getSubtitleKind());
        assertFalse(mManager.supportsUserStyle()); // the panel shows its "no subtitle" note
    }

    @Test
    public void setActiveTrack_interpretsRawNativeKind() throws Exception {
        mManager.setActiveTrack(trackWithRawKind(3));
        assertTrue(mManager.isGraphic());
        mManager.setActiveTrack(trackWithRawKind(1));
        assertEquals(SubtitleManager.KIND_SSA, mManager.getSubtitleKind());
        mManager.setActiveTrack(trackWithRawKind(0)); // native sent nothing
        assertEquals(SubtitleManager.KIND_UNSUPPORTED, mManager.getSubtitleKind());
    }

    private static VideoMetadata.SubtitleTrack trackWithRawKind(int rawKind) throws Exception {
        Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        Method allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
        VideoMetadata.SubtitleTrack track =
                (VideoMetadata.SubtitleTrack) allocate.invoke(unsafe, VideoMetadata.SubtitleTrack.class);
        Field f = VideoMetadata.SubtitleTrack.class.getDeclaredField("kind");
        f.setAccessible(true);
        f.setInt(track, rawKind);
        return track;
    }
}
