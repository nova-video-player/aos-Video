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

package com.archos.mediacenter.video.leanback.presenter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v17.leanback.widget.Presenter;
import android.support.v4.content.ContextCompat;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.video.VideoStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Display a video, a movie, an episode (Poster or Thumbnail) or a TvShow (Poster)
 * using a regular ImageCardView
 * Created by vapillon on 10/04/15.
 */
public class VideoBadgePresenter extends Presenter {


    private boolean mDisplay3dBadge;
    private Uri mSelectedUri;
    private int mBackgroundColor;

    public void setSelectedUri(Uri fileUri) {
        mSelectedUri = fileUri;
    }

    public void setSelectedBackgroundColor(int color) {
        mBackgroundColor = color;
    }

    public enum EpisodeDisplayMode {
        FOR_GENERAL_LIST,
        FOR_SEASON_LIST
    }

    final Drawable mErrorDrawable;

    protected Context mContext;

    public class BadgeViewHolder extends ViewHolder {

        private final ImageView mResImageView;
        private final TextView mSourceTextView;
        private final TextView mVideoFormat;
        private final ViewGroup mAudioFormatContainer;
        private final ImageView m3dImageView;
        private final View  mRootView;
        private final TextView mSizeTv;

        public BadgeViewHolder(Context context) {
            super(new CustomBaseCardview(context));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            mRootView = LayoutInflater.from(mContext).inflate(R.layout.leanback_badge_presenter,(FrameLayout) view, false);
            ((FrameLayout)view).addView(mRootView);
            mSizeTv = (TextView) view.findViewById(R.id.size);
            mResImageView = (ImageView) view.findViewById(R.id.image_res);
            m3dImageView =  (ImageView) view.findViewById(R.id.image_3d);
            mSourceTextView = (TextView) view.findViewById(R.id.source);
            mVideoFormat = (TextView) view.findViewById(R.id.video_format);
            mAudioFormatContainer = (ViewGroup) view.findViewById(R.id.audio_format_container);
        }



        public void setResolution(int res,boolean is3d){
            m3dImageView.setVisibility(mDisplay3dBadge?View.VISIBLE:View.GONE);
            if(is3d){
                m3dImageView.setImageResource(R.drawable.badge_3d);
            }
            else
                m3dImageView.setImageResource(R.drawable.badge_2d);

            switch (res){
                case (VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_4K):
                    mResImageView.setImageResource(R.drawable.badge_4k);
                    break;
                case (VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_1080P):
                    mResImageView.setImageResource(R.drawable.badge_1080);
                    break;
                case (VideoStore.Video.VideoColumns.ARCHOS_DEFINITION_720P):
                    mResImageView.setImageResource(R.drawable.badge_720);
                    break;

                default:{
                    mResImageView.setImageResource(R.drawable.badge_sd);
                }
            }
        }

        public void setSource(Uri source){
            if(source.equals(mSelectedUri)){
                mRootView.setBackgroundColor(mBackgroundColor);
            }
            else mRootView.setBackground(ContextCompat.getDrawable(mContext,  R.color.lb_basic_card_info_bg_color));

            if(FileUtils.isLocal(source)){
                mSourceTextView.setText("Local");
            }
            else {
                mSourceTextView.setText(source.getScheme().toUpperCase().replace("UPNP", "UPnP"));
            }

        }


        public void setAudioBadge(int calculatedBestAudiotrack) {
            //mAudioImageView.setImageResource(calculatedBestAudiotrack == VideoStore.Video.VideoColumns.ARCHOS_AUDIO_FIVEDOTONE ? R.drawable.badge_5_1 : R.drawable.badge_5_1);
        }

        public void setVideoFormat(String videoFormat) {
            mVideoFormat.setText(videoFormat);
        }
        public void setAudioFormat(String audioFormat) {

            try {
                mAudioFormatContainer.removeAllViews();
                JSONObject obj = new JSONObject(audioFormat);
                JSONArray array = obj.getJSONArray("audiotracks");
                for(int i = 0; i<array.length(); i++){
                    String format = array.getJSONObject(i).getString("format");
                    View v = LayoutInflater.from(mContext).inflate(R.layout.leanback_audio_format_badge_presenter, null);
                    TextView tv = (TextView) v.findViewById(R.id.audio_format);
                    tv.setText(format);
                    String channel = array.getJSONObject(i).getString("channels");
                    if(channel!=null) {
                        ImageView iv = (ImageView) v.findViewById(R.id.audio_label);
                        if(channel.equals("unknown"))
                            iv.setVisibility(View.INVISIBLE);
                        else
                            iv.setVisibility(View.VISIBLE);
                        
                        if (channel.startsWith("7.1"))
                            iv.setImageResource(R.drawable.badge_7_1);
                        else if (channel.startsWith("5.1"))
                            iv.setImageResource(R.drawable.badge_5_1);
                        else if (channel.startsWith("Stereo"))
                            iv.setImageResource(R.drawable.badge_2_0);
                    }
                    mAudioFormatContainer.addView(v);

                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
        public void setSize(long size) {
            if(size>0)
                mSizeTv.setText(Formatter.formatFileSize(mContext, size));
            else mSizeTv.setVisibility(View.GONE);
        }

    }



    public VideoBadgePresenter(Context context) {
        super();
        mErrorDrawable = context.getResources().getDrawable(R.drawable.filetype_new_video);
    }


    public void setDisplay3dBadge(boolean b) {
        mDisplay3dBadge = b;
    }
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mContext = parent.getContext();
        BadgeViewHolder vh = getVideoViewHolder(parent.getContext());
        return vh;
    }

    private BadgeViewHolder getVideoViewHolder(Context context) {
        return new BadgeViewHolder(context);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        BadgeViewHolder vh = (BadgeViewHolder)viewHolder;

        if (item instanceof Video) {
            bindVideo(vh, (Video) item);
        }

        else {
            throw new IllegalArgumentException("PosterPresenter do not handle this object: "+item);
        }
    }

    private void bindVideo(BadgeViewHolder vh, Video video) {
        vh.setVideoFormat(video.getCalculatedVideoFormat() != null && !video.getCalculatedVideoFormat().isEmpty() ? video.getCalculatedVideoFormat() : video.getGuessedVideoFormat());
        vh.setAudioFormat(video.getCalculatedBestAudioFormat() != null && !video.getCalculatedBestAudioFormat().isEmpty() ? video.getCalculatedBestAudioFormat() :
                "{audiotracks:[{format:" + JSONObject.quote(video.getGuessedAudioFormat()) + ",channels: \"unknown\"}]}");
        vh.setAudioBadge(video.getCalculatedBestAudiotrack());
        vh.setResolution(video.getNormalizedDefinition(), video.is3D());
        vh.setSource(video.getFileUri());
        vh.setSize(video.getSize());
    }


    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }


}
