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

package com.archos.mediacenter.video.browser.filebrowsing;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SectionIndexer;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.video.browser.adapters.AdapterByVideoObjectsInterface;
import com.archos.mediacenter.video.browser.ThumbnailAdapterVideo;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.ThumbnailRequestVideo;
import com.archos.mediacenter.video.browser.adapters.PresenterAdapterInterface;
import com.archos.mediacenter.video.browser.presenter.Presenter;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.util.HashMap;
import java.util.List;

public class ListingAdapter extends BaseAdapter implements AdapterByVideoObjectsInterface, PresenterAdapterInterface, ThumbnailAdapterVideo, SectionIndexer {

    protected final List<MetaFile2> mFilesFullList;
    private final HashMap<Class, Presenter> mPresenters;
    protected Context mContext;
    protected int mPosition;
    protected List<Object> mItemList;
    protected final ThumbnailEngineVideo mThumbnailEngine;

    // Add here all the available item types (each item type can have its own layout)
    public static final int ITEM_VIEW_TYPE_FILE = 0;        // File or folder

    public ListingAdapter(Context context, List<Object> itemList, List<MetaFile2> fullList) {
        super();
        mFilesFullList = fullList;
        mContext = context;
        mPresenters = new HashMap<>();
        mThumbnailEngine = ThumbnailEngineVideo.getInstance(context);
        mItemList = itemList;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }


    @Override
    public int getCount() {
        return mItemList.size();
    }

    @Override
    public Object getItem(int i) {
        return mItemList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        View v = null;
        mPosition = position;
        Object obj = mItemList.get(position);
        Presenter pr;
        if((pr = getPresenter(obj.getClass()))!=null){
            v = pr.getView(parent,obj,convertView );
        }

        bindView(position, v);
        return v;
    }
    private Presenter getPresenter(Class classToGet){
        Presenter pr;
        if((pr = mPresenters.get(classToGet))!=null)
            return pr;
        else if(classToGet.getSuperclass()!=null)
            return getPresenter(classToGet.getSuperclass());
        return null;


    }
    public void bindView(int position, View view) {

        Object obj = mItemList.get(position);
        Presenter pr;

        if((pr = getPresenter(obj.getClass())) !=null){
            ThumbnailEngine.Result result = null;
            if(doesItemNeedAThumbnail(position)) {
                ThumbnailRequestVideo trv = getThumbnailRequest(position);
                if (trv != null) {
                    result = mThumbnailEngine.getResultFromPool(trv.getKey());
                }

            }
            pr.bindView(view, obj, result, position );
        }
    }



    @Override
    public ThumbnailRequestVideo getThumbnailRequest(int position) {
        ThumbnailRequestVideo trv = null;
        if (position > mItemList.size())
            return null;

        if(!(mItemList.get(position) instanceof Video))
            return null;

        Video video = (Video) mItemList.get(position);
        trv = new ThumbnailRequestVideo(position, video.getId(), video.getPosterUri()!=null?VideoUtils.getMediaLibCompatibleFilepathFromUri(video.getPosterUri()):null ,
                Uri.parse(VideoUtils.getMediaLibCompatibleFilepathFromUri(video.getFileUri())));
        return trv;
    }


    @Override
    public Object[] getSections() {
        return new Object[0];
    }

    @Override
    public int getPositionForSection(int i) {
        return 0;
    }

    @Override
    public int getSectionForPosition(int i) {
        return 0;
    }

    @Override
    public boolean doesItemNeedAThumbnail(int position) {
        return mItemList.get(position) instanceof Video&&((Video)mItemList.get(position)).getId()>0;
    }

    public String getPath(int position) {
        return null;
    }

    @Override
    public Video getVideoItem(int position) {
        if(mItemList.get(position) instanceof Video)
            return (Video) mItemList.get(position);
        return null;
    }

    public void setPresenter(Class<?> objectClass, Presenter presenter) {
        mPresenters.put(objectClass, presenter);
    }
}
