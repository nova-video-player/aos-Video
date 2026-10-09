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

import android.database.Cursor;
import android.text.TextUtils;

import java.util.HashSet;
import java.util.Set;

/** Checks whether existing category row loaders can serve an updated category cursor. */
public final class CategoryRowsDiff {

    private CategoryRowsDiff() {
    }

    public static boolean canKeepRowsAfterRemovals(Cursor oldCursor, Cursor newCursor,
            String categoryIdColumn, String categoryNameColumn, String memberIdsColumn) {
        if (oldCursor.getCount() != newCursor.getCount()) return false;

        int oldCategoryId = oldCursor.getColumnIndexOrThrow(categoryIdColumn);
        int newCategoryId = newCursor.getColumnIndexOrThrow(categoryIdColumn);
        int oldCategoryName = oldCursor.getColumnIndexOrThrow(categoryNameColumn);
        int newCategoryName = newCursor.getColumnIndexOrThrow(categoryNameColumn);
        int oldMemberIds = oldCursor.getColumnIndexOrThrow(memberIdsColumn);
        int newMemberIds = newCursor.getColumnIndexOrThrow(memberIdsColumn);

        oldCursor.moveToFirst();
        newCursor.moveToFirst();
        while (!oldCursor.isAfterLast()) {
            if (oldCursor.getLong(oldCategoryId) != newCursor.getLong(newCategoryId)
                    || !TextUtils.equals(oldCursor.getString(oldCategoryName),
                            newCursor.getString(newCategoryName))
                    || !containsOnlyOldIds(oldCursor.getString(oldMemberIds),
                            newCursor.getString(newMemberIds))) {
                return false;
            }
            oldCursor.moveToNext();
            newCursor.moveToNext();
        }
        return true;
    }

    private static boolean containsOnlyOldIds(String oldIds, String newIds) {
        if (TextUtils.equals(oldIds, newIds)) return true;
        if (TextUtils.isEmpty(oldIds) || TextUtils.isEmpty(newIds)) return false;

        Set<String> oldIdSet = new HashSet<>();
        for (String id : oldIds.split(",")) {
            if (id.trim().isEmpty()) return false;
            oldIdSet.add(id.trim());
        }
        for (String id : newIds.split(",")) {
            if (id.trim().isEmpty() || !oldIdSet.contains(id.trim())) return false;
        }
        return true;
    }
}
