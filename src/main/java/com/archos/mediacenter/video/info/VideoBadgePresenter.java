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

package com.archos.mediacenter.video.info;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.browser.presenter.Presenter;
import com.archos.mediaprovider.video.VideoStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Display a video, a movie, an episode (Poster or Thumbnail) or a TvShow (Poster)
 * using a regular ImageCardView
 * Created by vapillon on 10/04/15.
 */
public class VideoBadgePresenter implements Presenter {


    private boolean mDisplay3dBadge;
    private Uri mSelectedUri;
    private int mBackgroundColor;

    public void setSelectedUri(Uri fileUri) {
        mSelectedUri = fileUri;
    }

    public void setSelectedBackgroundColor(int color) {
        mBackgroundColor = color;
    }

    @Override
    public View getView(ViewGroup parent, Object object, View view) {
        mContext = parent.getContext();
        BadgeViewHolder holder = new BadgeViewHolder(mContext, parent);
        return holder.getRootView();


    }

    @Override
    public View bindView(View view, Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        if(object instanceof Video){
            bindVideo((BadgeViewHolder) view.getTag(), (Video) object);
        }
        return null;
    }



    final Drawable mErrorDrawable;

    protected Context mContext;

    public class BadgeViewHolder  {

        private final ImageView mResImageView;
        private final TextView mSourceTextView;
        private final TextView mVideoFormat;
        private final ViewGroup mAudioFormatContainer;
        private final ImageView m3dImageView;
        private final View mContentView;
        private final View mRootView;
        private final TextView mSizeTv;

        public BadgeViewHolder(Context context, ViewGroup layout) {

            mRootView = LayoutInflater.from(mContext).inflate(R.layout.video_badge_presenter,layout, false);
            mRootView.setTag(this);
            mContentView = mRootView.findViewById(R.id.root);
            mResImageView = (ImageView) mContentView.findViewById(R.id.image_res);
            m3dImageView =  (ImageView) mContentView.findViewById(R.id.image_3d);
            mSourceTextView = (TextView) mContentView.findViewById(R.id.source);
            mVideoFormat = (TextView) mContentView.findViewById(R.id.video_format);
            mSizeTv = (TextView) mContentView.findViewById(R.id.size);
            mAudioFormatContainer = (ViewGroup) mContentView.findViewById(R.id.audio_format_container);
        }

        public View getRootView(){
            return mRootView;
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
                ((CardView)mRootView).setCardBackgroundColor(mBackgroundColor);
            }
            else ((CardView)mRootView).setCardBackgroundColor(ContextCompat.getColor(mContext, R.color.transparent_grey));

            if(FileUtils.isLocal(source)){
                mSourceTextView.setText("Local");
            }
            else {
                mSourceTextView.setText(source.getScheme());
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
                    View v = LayoutInflater.from(mContext).inflate(R.layout.audio_format_badge_presenter, null);
                    TextView tv = (TextView) v.findViewById(R.id.audio_format);
                    tv.setText(format);
                    String channel = array.getJSONObject(i).getString("channels");
                    if(channel!=null) {
                        ImageView iv = (ImageView) v.findViewById(R.id.audio_label);
                        if(channel.equals("unknown"))
                            iv.setVisibility(View.INVISIBLE);
                        else
                            iv.setVisibility(View.VISIBLE);
                        iv.setImageResource(channel.startsWith("5") ? R.drawable.badge_5_1 : R.drawable.badge_stereo_wide);
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





    private void bindVideo(BadgeViewHolder vh, Video video) {
        Log.d("formatdebug", "video " + video.getCalculatedVideoFormat());
        vh.setVideoFormat(video.getCalculatedVideoFormat() != null && !video.getCalculatedVideoFormat().isEmpty() ? video.getCalculatedVideoFormat() : video.getGuessedVideoFormat());
        vh.setAudioFormat(video.getCalculatedBestAudioFormat() != null && !video.getCalculatedBestAudioFormat().isEmpty() ? video.getCalculatedBestAudioFormat() :
                "{audiotracks:[{format:" + JSONObject.quote(video.getGuessedAudioFormat()) + ",channels: \"unknown\"}]}");
        vh.setAudioBadge(video.getCalculatedBestAudiotrack());
        vh.setResolution(video.getNormalizedDefinition(), video.is3D());
        vh.setSource(video.getFileUri());
        vh.setSize(video.getSize());
    }




}
