package com.archos.mediacenter.video.browser.loader;

import android.content.Context;
import android.util.Log;

import com.archos.mediaprovider.video.VideoStore;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by vapillon on 10/04/15.
 */
public class VideosSelectionInPlaylistLoader extends VideosSelectionLoader {


    public VideosSelectionInPlaylistLoader(Context context, String listOfMoviesIds) {
        super(context, listOfMoviesIds, DEFAULT_SORT);
    }

    //disable sort order for now
    @Override
    public String getSortOrder() {
        if(mListOfIds==null) //called too early by init()
            return "";
        String[] pairsOrderId = mListOfIds.split(",");
        if(pairsOrderId.length>1) {
            String finalOrder = "CASE " + VideoStore.Video.VideoColumns._ID + " \n";
            int i = 0;
            for (String pair : pairsOrderId) {
                finalOrder += "WHEN " + pair + " THEN " + i + "\n";
                i++;
            }
            finalOrder += "END \n";
            return finalOrder;

        }
        return null;
    }
}
