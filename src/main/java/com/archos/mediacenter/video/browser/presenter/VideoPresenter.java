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

package com.archos.mediacenter.video.browser.presenter;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.archos.filecorelibrary.FileUtils;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.player.PlayerActivity;

import httpimage.HttpImageManager;

/**
 * Created by alexandre on 26/10/15.
 */
public class VideoPresenter extends CommonPresenter{


    private final HttpImageManager mImageManager;

    public VideoPresenter(Context context, AdapterDefaultValues defaultValues, ExtendedClickListener onExtendedClick, HttpImageManager imageManager) {
        super(context, defaultValues,onExtendedClick);
        mImageManager = imageManager;

    }


    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        super.bindView(view, object, thumbnailResult, positionInAdapter);
        ViewHolder holder = (ViewHolder) view.getTag();
        final Video video = (Video) object;




        // ------------------------------------------------
        // File-based item => fill the ViewHolder fields depending
        // on the file type (file, folder or shortcut)
        // ------------------------------------------------

        if(holder.secondLine!=null)
            holder.secondLine.setVisibility(View.VISIBLE);
        // Set name.


        // Set duration.
        //if(holder.info!=null)
        //    holder.info.setText(video.getInfo());


        // Set thumbnail.
        if(holder.thumbnail!=null) {
            if (thumbnailResult == null || thumbnailResult.getThumbnail() == null) {
                holder.thumbnail.setImageResource(mDefaultValues.getDefaultVideoThumbnail());
                //holder.thumbnail.setColorFilter(mDefaultIconsColor);
                holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER); // thumbnail may be smaller, must not be over scaled
                if ("upnp".equals(video.getFileUri().getScheme())&&mImageManager!=null) {
                    Uri uri = video.getPosterUri();
                    if(uri != null){
                        Bitmap bitmap = mImageManager.loadImage(new HttpImageManager.LoadRequest(uri, holder.thumbnail));
                        if (bitmap != null) {
                            holder.thumbnail.setImageBitmap(bitmap);
                            holder.thumbnail.clearColorFilter();
                            holder.thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER); // poster must be scaled in detailled view
                        }
                    }
                }
            } else {
                holder.thumbnail.setImageBitmap(thumbnailResult.getThumbnail());
                holder.thumbnail.clearColorFilter();
                holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP); // poster must be scaled in detailled view
            }
        }

        // Set the expanded info.
        boolean bookmark = false; //06/2015: no more bookmark feature
        //boolean bookmark = mBrowserAdapterCommon.hasBookmark();
        boolean resume = video.getResumeMs()>0||video.getRemoteResumeMs()>0;
        //boolean subtitles = video.hasSubtitles();
        // Set duration.
        if(holder.info!=null) {
            if (video.getDurationMs() > 0) {

                holder.info.setVisibility(View.VISIBLE);
                holder.info.setText(MediaUtils.formatTime(video.getDurationMs()));
            }
            else
                holder.info.setVisibility(View.INVISIBLE);
        }

                /* //06/2015: no more bookmark feature
                if(holder.bookmark!=null){
                    holder.bookmark.setVisibility(View.VISIBLE);
                    holder.bookmark.setEnabled(bookmark);
                }*/
        if(holder.subtitle!=null){
            holder.subtitle.setVisibility(View.VISIBLE);
            holder.subtitle.setEnabled(video.hasSubs());
        }

        // Network notification
        if(holder.network!=null){

            holder.network.setEnabled(!FileUtils.isLocal(video.getFileUri()));
            holder.network.setVisibility(View.VISIBLE);
        }
        if(holder.expanded!=null)
            holder.expanded.setVisibility(View.GONE);

        if (holder.traktWatched != null)
            holder.traktWatched.setVisibility(video.isWatched() ? View.VISIBLE : View.GONE);
        if (holder.traktLibrary != null)
            holder.traktLibrary.setVisibility(video.isTraktLibrary() ? View.VISIBLE : View.GONE);

        if (holder.video3D != null)
            holder.video3D.setVisibility(video.is3D() ? View.VISIBLE : View.GONE);
        if(holder.count!=null){
            holder.countcontainer.setVisibility(video.getOccurencies()>1 ? View.VISIBLE : View.GONE);
            holder.count.setText(String.valueOf(video.getOccurencies()));
        }
        return view;
    }
    public void setResume(boolean display, int max, int resumePosition, ProgressBar resume){
        if (display) {
            resume.setMax(max);
            resume.setProgress(resumePosition == PlayerActivity.LAST_POSITION_END ? max : resumePosition);
            resume.setIndeterminate(false);
            resume.setVisibility(View.VISIBLE);
        } else { //no progress slider for resume, but simple label icon instead
            resume.setVisibility(View.GONE);
        }
    }
}
