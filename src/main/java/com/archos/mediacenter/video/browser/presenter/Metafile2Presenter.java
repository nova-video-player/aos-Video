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
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.localstorage.JavaFile2;
import com.archos.mediacenter.utils.InfoDialog;
import com.archos.mediacenter.utils.ShortcutDbAdapter;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;

/**
 * Created by alexandre on 26/10/15.
 */
public class Metafile2Presenter extends CommonPresenter{
    public Metafile2Presenter(Context context, AdapterDefaultValues defaultValues) {
        super(context, defaultValues, null);
    }


    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result thumbnailResult, int positionInAdapter) {
        super.bindView(view, object, thumbnailResult, positionInAdapter);
        ViewHolder holder = (ViewHolder) view.getTag();
        MetaFile2 metaFile2 = (MetaFile2) object;
        // The file is a directory or a shortcut.

        if (metaFile2.isDirectory()) {
            holder.thumbnail.setImageResource(mDefaultValues.getDefaultDirectoryThumbnail());
        } else {
            holder.thumbnail.setImageResource(mDefaultValues.getDefaultVideoThumbnail());

        }
        holder.thumbnail.setScaleType(ImageView.ScaleType.CENTER);
        holder.thumbnail.clearColorFilter();
        String name = metaFile2.getName();
        if (name.endsWith("/"))
            name = name.substring(0, name.length()-1);
        if(holder.name!=null){
            holder.name.setText(name);
            holder.name.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (metaFile2.isDirectory() && metaFile2 instanceof JavaFile2) {
            JavaFile2 javafile = (JavaFile2) metaFile2;
            holder.info.setVisibility(View.VISIBLE);
            if (javafile.getNumberOfFilesInside() > 0 || javafile.getNumberOfDirectoriesInside() > 0 ) {
                holder.info.setText(InfoDialog.formatDirectoryInfo(mContext, javafile.getNumberOfDirectoriesInside(),
                        javafile.getNumberOfFilesInside()));
            } else if(javafile.getNumberOfDirectoriesInside() == 0 || javafile.getNumberOfFilesInside() == 0) {
                holder.info.setText(mContext.getString(R.string.directory_empty));
            }
            else
                holder.info.setVisibility(View.INVISIBLE);
        }
        else {
            holder.info.setVisibility(View.INVISIBLE);
            if(holder.secondLine!=null)
                holder.secondLine.setVisibility(View.GONE);
        }

        // Hide all the notification icons
        if(holder.resume!=null){
            holder.resume.setVisibility(View.GONE);
        }
        if(holder.bookmark!=null)
            holder.bookmark.setVisibility(View.GONE);
        if(holder.subtitle!=null)
            holder.subtitle.setVisibility(View.GONE);
        if(holder.network!=null)
            holder.network.setVisibility(View.GONE);

        // Hide the info icon/button
        if(holder.expanded!=null)
            holder.expanded.setVisibility(View.GONE);
        if (holder.traktWatched != null)
            holder.traktWatched.setVisibility(View.GONE);
        if (holder.traktLibrary != null)
            holder.traktLibrary.setVisibility(View.GONE);

        if (holder.video3D != null)
            holder.video3D.setVisibility(View.GONE);

        if(ShortcutDbAdapter.VIDEO.isShortcut(mContext, metaFile2.getUri().toString())>0)
            holder.thumbnail.setImageResource(mDefaultValues.getDefaultShortcutThumbnail());
        return view;
    }
}
