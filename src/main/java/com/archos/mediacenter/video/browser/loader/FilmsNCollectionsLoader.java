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

public class FilmsNCollectionsLoader extends VideoLoader {

    private static final Logger log = LoggerFactory.getLogger(FilmsNCollectionsLoader.class);

    static public String DEFAULT_SORT = "name COLLATE NOCASE ASC";
    private final boolean mGroupByOnlineId;

    // TODO: not finished, see AnimesNShowsLoader and associated mapper

    /* Design:
SELECT
  CASE WHEN m_coll_id IS NULL THEN m_id ELSE m_coll_id END id,
  CASE WHEN m_coll_id IS NULL THEN '0' ELSE '1' END isCollection,
  CASE WHEN m_coll_id IS NULL THEN m_name ELSE m_coll_name END name,
  CASE WHEN m_coll_id IS NULL THEN m_po_large_file ELSE m_coll_po_large_file END poster,
  CASE WHEN m_coll_id IS NULL THEN COUNT(DISTINCT  m_id) ELSE COUNT(DISTINCT  m_coll_id)  END count
FROM video
WHERE m_id IS NOT NULL AND m_genres NOT LIKE '%Animation%'
  AND ((m_coll_po_large_file IS NOT NULL) OR (m_coll_id IS NULL ))
GROUP BY (CASE WHEN m_coll_id IS NULL THEN m_name ELSE m_coll_name END)
ORDER BY name ASC
 */

    static private String[] FILMSnCOLLECTIONS = new String[] {
        "CASE WHEN m_coll_id IS NULL THEN m_id ELSE m_coll_id END uId",
                "CASE WHEN m_coll_id IS NULL THEN '0' ELSE '1' END isCollection",
                "CASE WHEN m_coll_id IS NULL THEN m_name ELSE m_coll_name END uName",
                "CASE WHEN m_coll_id IS NULL THEN m_po_large_file ELSE m_coll_po_large_file END uPoster",
                "CASE WHEN m_coll_id IS NULL THEN COUNT(DISTINCT  m_id) ELSE COUNT(DISTINCT  m_coll_id) END count"
    };

    private String mSortOrder;

    private boolean mShowWatched;

    private static Context mContext;

    public FilmsNCollectionsLoader(Context context, boolean groupbyOnlineId) {
        this(context, DEFAULT_SORT, true, groupbyOnlineId);
    }

    public FilmsNCollectionsLoader(Context context, String SortOrder, boolean showWatched, boolean groupByOnlineId) {
        super(context);
        mContext = context;
        mGroupByOnlineId = groupByOnlineId;
        mSortOrder = SortOrder;
        mShowWatched = showWatched;
        init();
    }
    @Override
    protected void init() {
        super.init();
        if(mGroupByOnlineId) {
            setUri(getUri().buildUpon().appendQueryParameter("group",
                    "CASE\n"+
                            "WHEN " + VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID + ">0 THEN " + VideoStore.Video.VideoColumns.SCRAPER_VIDEO_ONLINE_ID+"\n" +
                            " ELSE "+VideoStore.Video.VideoColumns._ID+"\n" +
                            "END"
            ).build());
        }
        log.debug("init: getProjection " + getProjection().toString() + ", getSelection " + getSelection() + ", getSelectionArgs " + getSelectionArgs());
    }
    @Override
    public String[] getProjection() {
        if(mGroupByOnlineId)
            return  concatTwoStringArrays(super.getProjection(),
                    concatTwoStringArrays(
                    new String[] { "max("+VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED+") as last", COUNT },
                            FILMSnCOLLECTIONS)
                    );
        else
            return concatTwoStringArrays(super.getProjection(),
                    FILMSnCOLLECTIONS
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
        sb.append("m_id IS NOT NULL AND ( m_genres NOT NULL AND m_genres NOT LIKE '%Animation%' )" +
                        " AND ((m_coll_po_large_file IS NOT NULL) OR (m_coll_id IS NULL))"
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
