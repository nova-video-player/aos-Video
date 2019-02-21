// Copyright 2017 Archos SA
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

package com.archos.mediacenter.video.leanback.details;

import android.content.Context;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.util.Log;

import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;

/**
 * Created by vapillon on 27/04/15.
 */
public class VideoActionAdapter extends SparseArrayObjectAdapter {

    // These are the ids AND the key -> they define the order of the actions!
    public static final int ACTION_RESUME = 1;
    public static final int ACTION_LOCAL_RESUME = 2;
    public static final int ACTION_REMOTE_RESUME = 3;
    public static final int ACTION_PLAY_FROM_BEGIN = 4;

    public static final int ACTION_NEXT_EPISODE = 5;
    public static final int ACTION_LIST_EPISODES = 6;
    public static final int ACTION_MARK_AS_WATCHED = 10;
    public static final int ACTION_MARK_AS_NOT_WATCHED = 11;
    public static final int ACTION_INDEX = 20;
    public static final int ACTION_UNINDEX = 21;
    public static final int ACTION_SCRAP = 30;
    public static final int ACTION_UNSCRAP = 31;
    public static final int ACTION_HIDE = 32;
    public static final int ACTION_UNHIDE = 33;
    public static final int ACTION_DELETE = 40;
    public static final int ACTION_CONFIRM_DELETE = 41;
    public static final int ACTION_ADD_TO_LIST = 42;
    public static final int ACTION_REMOVE_FROM_LIST = 43;

    private static final String TAG = "VideoActionAdapter";

    final Context mContext;
    private Video mCurrentVideo;
    private boolean mHasNextEpisode;
    private int mCurrentRemoteResume; //remote resume is directly changed inside video object so we need to keep a copy

    /**
     * @param context
     * @param video
     * @param inPlayer true if the player is already running (in background)
     * @param nextEpisode the next episode if there is one
     * @param isTvEpisode true if it is a tv episode
     */
    public VideoActionAdapter(Context context, Video video, boolean inPlayer, boolean displayRemoveFromList, boolean displayConfirmDelete, Episode nextEpisode, boolean isTvEpisode) {
        Log.d(TAG, "new VideoActionAdapter");
        mContext = context;
        update(video, inPlayer, displayRemoveFromList, displayConfirmDelete, nextEpisode, isTvEpisode);

    }

    public void update(Video video, boolean inPlayer, boolean displayRemoveFromList, boolean displayConfirmDelete, Episode nextEpisode, boolean isTvEpisode){
        Video oldVideo = mCurrentVideo;
        mCurrentVideo = video;
        int oldRemoteResume = mCurrentRemoteResume;
        mCurrentRemoteResume = video.getRemoteResumeMs();
        if(!foundDifferencesRequiringDetailsUpdate(oldVideo, video,oldRemoteResume)&&mHasNextEpisode==(nextEpisode!=null)  && (indexOf(ACTION_CONFIRM_DELETE) >= 0) == displayConfirmDelete)
            return;
        mHasNextEpisode = nextEpisode!=null;
        if (!inPlayer) {
            if(video.getRemoteResumeMs()>0 && video.getResumeMs()!=video.getRemoteResumeMs()){
                set(ACTION_REMOTE_RESUME, new Action(ACTION_REMOTE_RESUME, mContext.getString(R.string.remote_resume), MediaUtils.formatTime(video.getRemoteResumeMs())));
                if (video.getResumeMs() > 0) { // this will be used to FORCE local resume
                    set(ACTION_RESUME, new Action(ACTION_LOCAL_RESUME, mContext.getString(R.string.resume), MediaUtils.formatTime(video.getResumeMs())));
                }else{
                    clear(ACTION_RESUME);
                }
            }
            else {
                clear(ACTION_REMOTE_RESUME);
                if (video.getResumeMs() > 0) { // LAST_POSITION_UNKNOWN is -1 and LAST_POSITION_END is -2
                    set(ACTION_RESUME, new Action(ACTION_RESUME, mContext.getString(R.string.resume), MediaUtils.formatTime(video.getResumeMs())));
                }else{
                    clear(ACTION_RESUME);
                }
            }
            if(video.getResumeMs() > 0 || video.getRemoteResumeMs() > 0){
                set(ACTION_PLAY_FROM_BEGIN, new Action(ACTION_PLAY_FROM_BEGIN, mContext.getString(R.string.play_from_beginning)));
            } else{
                set(ACTION_PLAY_FROM_BEGIN, new Action(ACTION_PLAY_FROM_BEGIN, mContext.getString(R.string.play_selection)));
            }

            if (nextEpisode!=null) {
                set(ACTION_NEXT_EPISODE, new Action(ACTION_NEXT_EPISODE, mContext.getString(R.string.next_episode)));
            }else{
                clear(ACTION_NEXT_EPISODE);
            }

            if (isTvEpisode) {
                set(ACTION_LIST_EPISODES, new Action(ACTION_LIST_EPISODES, mContext.getString(R.string.list_episodes)));
            }else{
                clear(ACTION_LIST_EPISODES);
            }

            if (video.isIndexed()) {
                if (video.isWatched() || (video.getResumeMs() == PlayerActivity.LAST_POSITION_END)) {
                    clear(ACTION_MARK_AS_WATCHED);
                    set(ACTION_MARK_AS_NOT_WATCHED, new Action(ACTION_MARK_AS_NOT_WATCHED, mContext.getString(R.string.mark_as_not_watched)));
                } else {
                    clear(ACTION_MARK_AS_NOT_WATCHED);
                    set(ACTION_MARK_AS_WATCHED, new Action(ACTION_MARK_AS_WATCHED, mContext.getString(R.string.mark_as_watched)));
                }
            }
            if (video.locationSupportsDelete()) {
                if (!displayConfirmDelete) {
                    clear(ACTION_CONFIRM_DELETE);
                    set(ACTION_DELETE, new Action(ACTION_DELETE, mContext.getString(R.string.delete)));
                }
                else {
                    clear(ACTION_DELETE);
                    set(ACTION_CONFIRM_DELETE, new Action(ACTION_CONFIRM_DELETE, mContext.getString(R.string.confirm_delete_short)));
                }
            }else{
                clear(ACTION_DELETE);
                clear(ACTION_CONFIRM_DELETE);
            }
        }

        if (video.isIndexed()) {
            if (video.hasScraperData()) {
                clear(ACTION_SCRAP);
                if(displayRemoveFromList)
                    set(ACTION_REMOVE_FROM_LIST, new Action(ACTION_REMOVE_FROM_LIST, mContext.getString(R.string.remove_from_list)));
                else
                    clear(ACTION_REMOVE_FROM_LIST);
                set(ACTION_ADD_TO_LIST, new Action(ACTION_ADD_TO_LIST, mContext.getString(R.string.add_to_list)));
                set(ACTION_UNSCRAP, new Action(ACTION_UNSCRAP, mContext.getString(R.string.leanback_unscrap)));
            } else {
                clear(ACTION_UNSCRAP);
                clear(ACTION_ADD_TO_LIST);
                clear(ACTION_REMOVE_FROM_LIST);
                set(ACTION_SCRAP, new Action(ACTION_SCRAP, mContext.getString(R.string.leanback_scrap)));
            }
            /**** WIP
             if (video.isUserHidden()) {
             set(ACTION_UNHIDE, Action(ACTION_UNHIDE, "Unhide")); //TODO
             } else {
             set(ACTION_HIDE, Action(ACTION_HIDE, "Hide")); //TODO
             } ******/


            set(ACTION_UNINDEX, new Action(ACTION_UNINDEX, mContext.getString(R.string.video_browser_unindex_file)));
            clear(ACTION_INDEX);
        }
        else {
            set(ACTION_INDEX, new Action(ACTION_INDEX, mContext.getString(R.string.video_browser_index_file)));
            clear(ACTION_UNINDEX);
            clear(ACTION_SCRAP);
            clear(ACTION_UNSCRAP);
        }
        notifyChanged();
    }
    //differs from VideoDetailsFragment
    private boolean foundDifferencesRequiringDetailsUpdate(Video v1, Video v2, int oldRemoteResume) {

        if (v1==null || v2==null) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate null"); return true;}
        Log.d(TAG, "foundDifferencesRequiringDetailsUpdate remotev1"+oldRemoteResume);
        Log.d(TAG, "foundDifferencesRequiringDetailsUpdate remotev2"+v2.getRemoteResumeMs());
        Log.d(TAG, "foundDifferencesRequiringDetailsUpdate v2"+v2.getResumeMs());

        if (v1.hasScraperData() != v2.hasScraperData()) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate hasScraperData"); return true;}
        if (v1.getResumeMs() != v2.getResumeMs()) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate resumeMs"); return true;}

        if (oldRemoteResume != v2.getRemoteResumeMs()&&v2.getRemoteResumeMs()!=v2.getResumeMs()) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate resumeMs"); return true;}
        if (v1.isWatched() != v2.isWatched()) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate isWatched"); return true;}
        if (v1.isIndexed() != v2.isIndexed()) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate isUserHidden"); return true;}
        if (v1.locationSupportsDelete() != v2.locationSupportsDelete()) {Log.d(TAG, "foundDifferencesRequiringDetailsUpdate subtitleCount"); return true;}
        return false;
    }

    public void setNextEpisodeStatus(boolean visible) {
        if (visible) {
            set(ACTION_NEXT_EPISODE, new Action(ACTION_NEXT_EPISODE, mContext.getString(R.string.next_episode)));
        } else {
            clear(ACTION_NEXT_EPISODE);
        }
    }

    public void setListEpisodesStatus(boolean visible) {
        if (visible) {
            set(ACTION_LIST_EPISODES, new Action(ACTION_LIST_EPISODES, mContext.getString(R.string.list_episodes)));
        } else {
            clear(ACTION_LIST_EPISODES);
        }
    }

    public void updateRemoteResume(Context context, Video video) {
        Video oldVideo = mCurrentVideo;
        mCurrentVideo = video;
        int oldRemoteResume = mCurrentRemoteResume;
        mCurrentRemoteResume = video.getRemoteResumeMs();
        Log.d(TAG, "updateRemoteResume");
        if(!foundDifferencesRequiringDetailsUpdate(oldVideo, video, oldRemoteResume))
            return;
        if(video.getRemoteResumeMs()>0 && video.getResumeMs()!=video.getRemoteResumeMs()){
            set(ACTION_REMOTE_RESUME, new Action(ACTION_REMOTE_RESUME, context.getString(R.string.remote_resume), MediaUtils.formatTime(video.getRemoteResumeMs())));
            if (video.getResumeMs() > 0) { // this will be used to FORCE local resume
                set(ACTION_RESUME, new Action(ACTION_LOCAL_RESUME, context.getString(R.string.resume), MediaUtils.formatTime(video.getResumeMs())));
            } else {
                clear(ACTION_RESUME);
            }
        }
        else if (video.getResumeMs() > 0) { // LAST_POSITION_UNKNOWN is -1 and LAST_POSITION_END is -2
            clear(ACTION_REMOTE_RESUME);
            set(ACTION_RESUME, new Action(ACTION_RESUME, context.getString(R.string.resume), MediaUtils.formatTime(video.getResumeMs())));
        }
        else {
            clear(ACTION_REMOTE_RESUME);
            clear(ACTION_RESUME);
        }

        if(video.getResumeMs() > 0 || video.getRemoteResumeMs() > 0){
            set(ACTION_PLAY_FROM_BEGIN, new Action(ACTION_PLAY_FROM_BEGIN, context.getString(R.string.play_from_beginning)));
        } else{
            set(ACTION_PLAY_FROM_BEGIN, new Action(ACTION_PLAY_FROM_BEGIN, context.getString(R.string.play_selection)));
        }
        notifyChanged();
    }

    public void updateToNonIndexed(Context context) {
        clear(ACTION_RESUME);
        clear(ACTION_LOCAL_RESUME);
        set(ACTION_PLAY_FROM_BEGIN, new Action(ACTION_PLAY_FROM_BEGIN, context.getString(R.string.play_selection)));
        clear(ACTION_NEXT_EPISODE);
        clear(ACTION_LIST_EPISODES);
        clear(ACTION_MARK_AS_WATCHED);
        clear(ACTION_MARK_AS_NOT_WATCHED);
        clear(ACTION_UNINDEX);
        clear(ACTION_SCRAP);
        clear(ACTION_UNSCRAP);
        clear(ACTION_HIDE);
        clear(ACTION_UNHIDE);
        clear(ACTION_ADD_TO_LIST);

        set(ACTION_INDEX, new Action(ACTION_INDEX, context.getString(R.string.video_browser_index_file)));
    }

    public void updateToNonScraped(Context context) {
        clear(ACTION_NEXT_EPISODE);
        clear(ACTION_LIST_EPISODES);
        clear(ACTION_UNSCRAP);
        set(ACTION_SCRAP, new Action(ACTION_SCRAP, context.getString(R.string.leanback_scrap)));
    }
}
