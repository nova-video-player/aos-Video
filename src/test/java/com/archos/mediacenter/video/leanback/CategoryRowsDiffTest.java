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

package com.archos.mediacenter.video.leanback;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.database.MatrixCursor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class CategoryRowsDiffTest {

    private static MatrixCursor categories(Object[]... rows) {
        MatrixCursor cursor = new MatrixCursor(new String[] { "_id", "name", "list" });
        for (Object[] row : rows) cursor.addRow(row);
        return cursor;
    }

    private static boolean canKeep(MatrixCursor oldCursor, MatrixCursor newCursor) {
        return CategoryRowsDiff.canKeepRowsAfterRemovals(oldCursor, newCursor,
                "_id", "name", "list");
    }

    @Test
    public void deletionWithinExistingCategoryKeepsRowsDespiteIdOrderChange() {
        MatrixCursor oldCursor = categories(new Object[] { 2026, "2026", "10,11,12" },
                new Object[] { 2025, "2025", "20" });
        MatrixCursor newCursor = categories(new Object[] { 2026, "2026", "12,10" },
                new Object[] { 2025, "2025", "20" });

        assertTrue(canKeep(oldCursor, newCursor));
    }

    @Test
    public void additionRequiresNewLoaderArguments() {
        assertFalse(canKeep(categories(new Object[] { 2026, "2026", "10,11" }),
                categories(new Object[] { 2026, "2026", "10,11,12" })));
    }

    @Test
    public void categoryRemovalOrReplacementRequiresRebuild() {
        MatrixCursor oldCursor = categories(new Object[] { 2026, "2026", "10" },
                new Object[] { 2025, "2025", "20" });
        assertFalse(canKeep(oldCursor, categories(new Object[] { 2025, "2025", "20" })));
        assertFalse(canKeep(categories(new Object[] { 2026, "2026", "10" }),
                categories(new Object[] { 2024, "2024", "10" })));
    }
}
