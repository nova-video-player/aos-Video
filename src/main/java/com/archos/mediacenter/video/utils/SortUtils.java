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

package com.archos.mediacenter.video.utils;

import android.content.Context;
import androidx.preference.PreferenceManager;

import com.archos.mediaprovider.video.VideoStore;

/**
 * Helper to resolve SQL columns and sort expressions for alphabetical title sorting
 * according to the user's "Ignore initial articles" preference.
 */
public final class SortUtils {

    public enum SortScope {
        /** video view queries exposing sort_name / name */
        VIDEO_VIEW,
        /** movie queries / views exposing m_sort_name / m_name */
        MOVIE,
        /** show queries / views exposing s_sort_name / s_name */
        SHOW,
        /** movie collection queries exposing m_coll_sort_name / m_coll_name */
        COLLECTION
    }

    private SortUtils() { }

    /**
     * @return true if the user preference to ignore initial articles when sorting is enabled (default: false).
     */
    public static boolean isIgnoreArticlesEnabled(Context context) {
        if (context == null) return VideoPreferencesCommon.SORT_IGNORE_ARTICLES_DEFAULT;
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(VideoPreferencesCommon.KEY_SORT_IGNORE_ARTICLES, VideoPreferencesCommon.SORT_IGNORE_ARTICLES_DEFAULT);
    }

    /**
     * Resolves the proper SQL column for alphabetical sorting based on query scope and preference.
     *
     * @param context Application context
     * @param scope Target query scope
     * @return The column name to sort or group by
     */
    public static String getTitleSortColumn(Context context, SortScope scope) {
        boolean ignoreArticles = isIgnoreArticlesEnabled(context);
        switch (scope) {
            case MOVIE:
                return ignoreArticles ? VideoStore.Video.VideoColumns.SCRAPER_M_SORT_NAME : VideoStore.Video.VideoColumns.SCRAPER_M_NAME;
            case SHOW:
                return ignoreArticles ? VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME : VideoStore.Video.VideoColumns.SCRAPER_S_NAME;
            case COLLECTION:
                return ignoreArticles ? VideoStore.Video.VideoColumns.SCRAPER_C_SORT_NAME : VideoStore.Video.VideoColumns.SCRAPER_C_NAME;
            case VIDEO_VIEW:
            default:
                return ignoreArticles ? "COALESCE(NULLIF(" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + ", ''), name)" : "name";
        }
    }

    /**
     * Returns an ORDER BY clause for alphabetical name sorting in a given scope.
     *
     * @param context Application context
     * @param scope Target query scope
     * @param asc true for ASC, false for DESC
     * @return SQL ORDER BY fragment e.g. "m_sort_name COLLATE LOCALIZED ASC"
     */
    public static String getNameSortOrder(Context context, SortScope scope, boolean asc) {
        String column = getTitleSortColumn(context, scope);
        return column + " COLLATE LOCALIZED " + (asc ? "ASC" : "DESC");
    }

    /**
     * Returns the column to extract initial character bucket from (e.g. SUBSTR(col, 1, 1)) in By-Alpha loaders.
     */
    public static String getAlphaBucketColumn(Context context, SortScope scope) {
        return getTitleSortColumn(context, scope);
    }

    /**
     * Translates a sort order expression at query runtime to use the effective column
     * (e.g. replacing 'name', 's_title', 'm_coll_name', etc. with sort_name / s_sort_name / m_coll_sort_name when ignoreArticles is active,
     * and restoring raw title columns when disabled).
     *
     * @param context Application context
     * @param scope Target query scope
     * @param sortOrder Raw or persisted sort order expression
     * @return The rewritten sort order expression matching current preferences
     */
    public static String resolveSortOrder(Context context, SortScope scope, String sortOrder) {
        if (sortOrder == null) {
            return getNameSortOrder(context, scope, true);
        }
        boolean ignoreArticles = isIgnoreArticlesEnabled(context);
        switch (scope) {
            case MOVIE:
                if (ignoreArticles) {
                    return sortOrder.replace(VideoStore.Video.VideoColumns.SCRAPER_M_NAME, VideoStore.Video.VideoColumns.SCRAPER_M_SORT_NAME)
                            .replaceAll("\\bname\\b", VideoStore.Video.VideoColumns.SCRAPER_M_SORT_NAME);
                } else {
                    return sortOrder.replace(VideoStore.Video.VideoColumns.SCRAPER_M_SORT_NAME, VideoStore.Video.VideoColumns.SCRAPER_M_NAME)
                            .replaceAll("\\b" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + "\\b", "name");
                }
            case SHOW:
                if (ignoreArticles) {
                    return sortOrder.replace(VideoStore.Video.VideoColumns.SCRAPER_S_NAME, VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME)
                            .replace(VideoStore.Video.VideoColumns.SCRAPER_TITLE, VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME)
                            .replaceAll("\\bname\\b", VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME);
                } else {
                    return sortOrder.replace(VideoStore.Video.VideoColumns.SCRAPER_S_SORT_NAME, VideoStore.Video.VideoColumns.SCRAPER_TITLE)
                            .replaceAll("\\b" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + "\\b", "name");
                }
            case COLLECTION:
                if (ignoreArticles) {
                    return sortOrder.replace(VideoStore.Video.VideoColumns.SCRAPER_C_NAME, VideoStore.Video.VideoColumns.SCRAPER_C_SORT_NAME)
                            .replaceAll("\\bname\\b", VideoStore.Video.VideoColumns.SCRAPER_C_SORT_NAME);
                } else {
                    return sortOrder.replace(VideoStore.Video.VideoColumns.SCRAPER_C_SORT_NAME, VideoStore.Video.VideoColumns.SCRAPER_C_NAME)
                            .replaceAll("\\b" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + "\\b", "name");
                }
            case VIDEO_VIEW:
            default:
                String effectiveVideoSortName = "COALESCE(NULLIF(" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + ", ''), name)";
                if (ignoreArticles) {
                    return sortOrder.replaceAll("\\bname\\b", effectiveVideoSortName);
                } else {
                    return sortOrder.replace(effectiveVideoSortName, "name")
                            .replaceAll("\\b" + VideoStore.Video.VideoColumns.SCRAPER_SORT_NAME + "\\b", "name");
                }
        }
    }

    /**
     * Returns the default sort order for a given scope, resolved against current user preferences.
     */
    public static String getDefaultSortOrder(Context context, SortScope scope) {
        return getNameSortOrder(context, scope, true);
    }
}
