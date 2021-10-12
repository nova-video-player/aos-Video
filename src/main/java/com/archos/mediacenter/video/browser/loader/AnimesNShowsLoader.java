// Copyright 2021 Courville SA
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

package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.provider.BaseColumns;
import android.util.Log;

import com.archos.mediacenter.video.player.PlayerActivity;
import com.archos.mediaprovider.video.LoaderUtils;
import com.archos.mediaprovider.video.VideoStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnimesNShowsLoader extends VideoLoader {

    private static final Logger log = LoggerFactory.getLogger(AnimesNShowsLoader.class);

    static public String DEFAULT_SORT = "name COLLATE NOCASE ASC";

    /* Design: get all animation movies and shows and regroup by shows all episodes
SELECT
  CASE WHEN s_id IS NULL THEN m_id ELSE s_id END uId,
  CASE WHEN s_id IS NULL THEN '0' ELSE '1' END isShow,
  CASE WHEN s_id IS NULL THEN m_name ELSE s_name END uName,
  CASE WHEN s_id IS NULL THEN m_po_large_file ELSE s_po_large_file END uPoster,
  CASE WHEN s_id IS NULL THEN m_plot ELSE s_plot END uPlot,
  CASE WHEN s_id IS NULL THEN '0' ELSE COUNT(DISTINCT e_season) END season_count,
  CASE WHEN s_id IS NULL THEN '0' ELSE COUNT(DISTINCT e_episode) END episode_count,
  CASE WHEN s_id IS NULL THEN m_year ELSE s_premiered END uYear,
  CASE WHEN s_id IS NULL THEN m_actors ELSE e_actors END uActors,
  CASE WHEN s_id IS NULL THEN m_studios ELSE s_studios END uStudios,
  CASE WHEN s_id IS NULL THEN '0' ELSE  COUNT(CASE bookmark WHEN -2 THEN 1 ELSE NULL END) END episode_watched_count
FROM video
WHERE (m_id IS NOT NULL OR s_id IS NOT NULL)
  AND (m_genres  LIKE '%Animation%' OR s_genres LIKE '%Animation%' )
  AND (s_po_large_file IS NOT NULL OR m_po_large_file IS NOT NULL)
GROUP BY (CASE WHEN s_id IS NULL THEN m_name ELSE s_name END)
ORDER BY uName ASC
 */

    static private String[] ANIMESnSHOWS = new String[] {
        "CASE WHEN s_id IS NULL THEN m_id ELSE s_id END uId",
            "CASE WHEN s_id IS NULL THEN '0' ELSE '1' END isShow",
            "CASE WHEN s_id IS NULL THEN m_name ELSE s_name END uName",
            "CASE WHEN s_id IS NULL THEN m_plot ELSE s_plot END uPlot",
            "CASE WHEN s_id IS NULL THEN m_studios ELSE s_studios END uStudios",
            "CASE WHEN s_id IS NULL THEN m_actors ELSE e_actors END uActors",
            "CASE WHEN s_id IS NULL THEN m_year ELSE s_premiered END uYear",
            "CASE WHEN s_id IS NULL THEN m_po_large_file ELSE s_po_large_file END uPoster",
            "CASE WHEN s_id IS NULL THEN '0' ELSE COUNT(DISTINCT e_season) END season_count",
            "CASE WHEN s_id IS NULL THEN '0' ELSE COUNT(DISTINCT e_episode) END episode_count",
            "CASE WHEN s_id IS NULL THEN COUNT(DISTINCT  m_id) ELSE COUNT(DISTINCT e_id) END count"
    };

    private String mSortOrder;

    private boolean mShowWatched;

    private static Context mContext;

    public AnimesNShowsLoader(Context context) {
        this(context, DEFAULT_SORT, true);
    }

    public AnimesNShowsLoader(Context context, String SortOrder, boolean showWatched) {
        super(context);
        mContext = context;
        mSortOrder = SortOrder;
        mShowWatched = showWatched;
        init();
    }
    @Override
    protected void init() {
        super.init();
        setUri(getUri().buildUpon().appendQueryParameter("group",
                "CASE WHEN s_id IS NULL THEN m_name ELSE s_name END"
        ).build());
        log.debug("init: getProjection " + getProjection().toString() + ", getSelection " + getSelection() + ", getSelectionArgs " + getSelectionArgs());
    }
    @Override
    public String[] getProjection() {
            return concatTwoStringArrays(super.getProjection(),
                    ANIMESnSHOWS
                    );
    }

    @Override
    public String getSortOrder() {
        return mSortOrder;
    }

    @Override
    public String getSelection() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getSelection()); // get common selection from the parent

        // collections and movies not in collections that are not animes
        if (sb.length()>0) { sb.append(" AND "); }
        sb.append("(m_id IS NOT NULL OR s_id IS NOT NULL)" +
                " AND (m_genres  LIKE '%Animation%' OR s_genres LIKE '%Animation%')" +
                " AND (s_po_large_file IS NOT NULL OR m_po_large_file IS NOT NULL)"
        );
        if (!mShowWatched) {
            sb.append(" AND ");
            sb.append(LoaderUtils.HIDE_WATCHED_FILTER);
        }
        return sb.toString();
    }

    @Override
    public String[] getSelectionArgs() {
        return null;
    }
}
