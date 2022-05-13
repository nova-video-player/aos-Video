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

        builder.append(
            "(bookmark > 0 OR e_id in (" +
                "SELECT n.e_id " +
                "FROM video n " +
                "INNER JOIN video w " +
                "ON n.s_id = w.s_id AND (" +
                    "CASE " +
                        "WHEN w.e_episode = (" +
                            "SELECT MAX(e_episode) FROM video WHERE e_id IS NOT NULL AND s_id = w.s_id AND e_season = w.e_season" +
                        ") " +
                        "THEN n.e_season = w.e_season + 1 AND n.e_episode = (" +
                            "SELECT MIN(e_episode) FROM video WHERE e_id IS NOT NULL AND s_id = n.s_id AND e_season = n.e_season" +
                        ") " +
                        "ELSE n.e_season = w.e_season AND n.e_episode = w.e_episode + 1 " +
                    "END" +
                ") " +
                "WHERE n.e_id IS NOT NULL AND n.Archos_lastTimePlayed = 0 AND w.e_id IS NOT NULL AND w.Archos_lastTimePlayed != 0" +
            "))"
        );

        return builder.toString();
    }

    @Override
    public String getSortOrder() {
        return "CASE WHEN " + VideoStore.Video.VideoColumns.BOOKMARK + " > 0 THEN 0 ELSE 1 END, " + VideoStore.Video.VideoColumns.ARCHOS_LAST_TIME_PLAYED + " DESC, " + VideoLoader.DEFAULT_SORT + " LIMIT 100";
    }
}
