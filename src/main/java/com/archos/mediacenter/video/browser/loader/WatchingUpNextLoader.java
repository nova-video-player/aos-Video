package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.util.Log;

import com.archos.mediaprovider.video.VideoStore;

public class WatchingUpNextLoader extends VideoLoader {

    // /!\ FIXME this query requires optimization and cannot be used for now
    // Problem: on large collection of videos viewed, loader takes forever to complete
    // this causes VideoLoader that has only a poolsize of one to not process any other loaders

    // TODO redesign so that LastPlayed = only completed videos and
    // UpNext = following episodes of completed videos + CurrentlyPlayed (ongoing videos not completed)

    public WatchingUpNextLoader(Context context) {
        super(context);
        init();
        // cf. https://github.com/nova-video-player/aos-AVP/issues/134 reduce strain
        // only updates the CursorLoader on data change every 10s since used only in MainFragment as nonScraped box presence
        if (VideoLoader.THROTTLE) setUpdateThrottle(VideoLoader.THROTTLE_DELAY);
    }

    @Override
    public String getSelection() {
        StringBuilder builder = new StringBuilder();
        builder.append(super.getSelection());

        if (builder.length() > 0)
            builder.append(" AND ");

        // former code which is not scalable because when there is a large number of video that are seen the join takes forever on the whole video table
        // solution: do it on the last 100 last played videos
        /*
        builder.append(
            "(bookmark > 0 OR e_id in (" + // display bookmarked videos and
                "SELECT n.e_id " +
                "FROM video as n " +
                "INNER JOIN (SELECT * FROM video WHERE Archos_lastTimePlayed!=0 AND s_id NOT NULL ORDER BY Archos_lastTimePlayed DESC LIMIT 100) AS w " +
                "ON n.s_id = w.s_id AND (" +
                    "CASE " +
                        "WHEN w.e_episode = (" + // if there is no more episodes in the season
                            "SELECT MAX(e_episode) FROM video WHERE e_id IS NOT NULL AND s_id = w.s_id AND e_season = w.e_season" +
                        ") " +
                        "THEN n.e_season = w.e_season + 1 AND n.e_episode = (" + // jump to next season as result
                            "SELECT MIN(e_episode) FROM video WHERE e_id IS NOT NULL AND s_id = n.s_id AND e_season = n.e_season" +
                        ") " + // and take the min of the episodes present
                        "ELSE n.e_season = w.e_season AND n.e_episode = w.e_episode + 1 " + // otherwise take simply the next episode
                    "END" +
                ") " +
                "WHERE n.e_id IS NOT NULL AND n.Archos_lastTimePlayed = 0 AND w.e_id IS NOT NULL AND w.Archos_lastTimePlayed != 0" +
            "))"
        );
         */

        builder.append(
                "(video_online_id IN\n" +
                        "  (SELECT video_online_id FROM\n" +
                        "    (WITH v AS \n" +
                        "      (SELECT video_online_id, scraper_name, m_coll_id, m_year, s_id, e_season, e_episode\n" +
                        "        FROM video WHERE (m_coll_id NOT NULL OR s_id NOT NULL) AND Archos_lastTimePlayed=0 AND archos_hiddenbyuser = 0 GROUP BY video_online_id),\n" +
                        "    l AS \n" +
                        "      (SELECT m_coll_id, s_id, MAX(e_season) AS e_season, max(e_episode) AS e_episode, archos_lasttimeplayed, MAX(m_year) AS m_year \n" +
                        "        FROM video WHERE Archos_lastTimePlayed > 1 AND (m_coll_id NOT NULL OR s_id NOT NULL) AND archos_hiddenbyuser = 0 GROUP BY m_coll_id, s_id LIMIT 100)\n" +
                        "    SELECT v.video_online_id, v.scraper_name, v.e_season, v.e_episode, l.archos_lasttimeplayed \n" +
                        "      FROM v INNER JOIN l ON v.s_id = l.s_id AND\n" +
                        "        (CASE WHEN l.e_episode = (SELECT MAX(e_episode) FROM v WHERE s_id = l.s_id AND e_season = l.e_season)\n" +
                        "          THEN v.e_season = l.e_season + 1 AND v.e_episode = (SELECT MIN(e_episode) FROM v WHERE s_id = v.s_id AND e_season = l.e_season + 1)\n" +
                        "          ELSE v.e_season = l.e_season AND v.e_episode  = (SELECT MIN(e_episode) FROM v WHERE s_id = l.s_id  AND e_season = l.e_season  AND e_episode > l.e_episode)\n" +
                        "        END)\n" +
                        "    UNION\n" +
                        "    SELECT v.video_online_id, v.scraper_name, v.e_season, v.e_episode, l.archos_lasttimeplayed \n" +
                        "      FROM v INNER JOIN l ON v.m_coll_id = l.m_coll_id\n" +
                        "        AND v.m_year = (SELECT Min(m_year) FROM v WHERE m_coll_id = l.m_coll_id AND m_year > l.m_year) \n" +
                        "    ORDER BY l.archos_lasttimeplayed DESC)))"
        );

        return builder.toString();
    }

    /*
    @Override
    public String getSortOrder() {
        return "CASE WHEN " + VideoStore.Video.VideoColumns.BOOKMARK + " > 0 THEN 0 ELSE 1 END, " + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " DESC, " + VideoLoader.DEFAULT_SORT + " LIMIT 100";
    }
     */
    @Override
    public String getSortOrder() {
        return "";
    }
}