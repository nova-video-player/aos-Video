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


package com.archos.mediacenter.video.browser.adapters;

import android.content.Context;
import android.database.Cursor;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SectionIndexer;

import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.utils.MediaUtils;
import com.archos.mediacenter.video.browser.ThumbnailAdapterVideo;
import com.archos.mediacenter.video.browser.ThumbnailEngineVideo;
import com.archos.mediacenter.video.browser.ThumbnailRequestVideo;
import com.archos.mediacenter.video.browser.presenter.Presenter;
import com.archos.mediacenter.video.utils.VideoUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class PresenterAdapterByCursor extends CursorAdapter implements  SectionIndexer,
        ThumbnailAdapterVideo,PresenterAdapterInterface {




    private final HashMap<Class, Presenter> mPresenters;
    private int mAvailableSubtitlesFilesCount = 0;
    private List<String> mAvailableSubtitlesFiles = new ArrayList<String>();
    private final SparseArray<String> mIndexer;
    private String[] mSections;
    protected final ThumbnailEngineVideo mThumbnailEngine;
    protected Context mContext;
    public static final String COVER_PATH = "cover";


    public PresenterAdapterByCursor(Context context, Cursor c) {
        super(context, c, 0);

        mContext = context;

        mThumbnailEngine = ThumbnailEngineVideo.getInstance(mContext);
        mIndexer = new SparseArray<String>();
        mPresenters = new HashMap<>();
    }

    public void setData(Cursor c) {

        setSections();
        buildAvailableSubtitlesFileList();
    }

    private void setSections() {

    }
    private Presenter getPresenter(Class classToGet){
        Presenter pr;
        if((pr = mPresenters.get(classToGet))!=null)
            return pr;
        else if(classToGet.getSuperclass()!=null)
            return getPresenter(classToGet.getSuperclass());
        return null;


    }
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
        return null;
    }


    @Override
    public View getView(int position, View convert, ViewGroup parent){
        Object video = getItem(position);
        Presenter pr;
        if((pr = getPresenter(video.getClass())) !=null){
            View view = pr.getView(parent, video, convert);
            bindView(view, mContext, getCursor());
            return view;
        }


        return null;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        Object video = getItem(getCursor().getPosition());
        Presenter pr;
        if((pr = getPresenter(video.getClass())) != null){
            ThumbnailEngine.Result result = null;

                ThumbnailRequestVideo trv = getThumbnailRequest(getCursor().getPosition());
                if (trv != null) {
                    result = mThumbnailEngine.getResultFromPool(trv.getKey());

            }
            pr.bindView(view, video, result, getCursor().getPosition());
        }
    }

    @Override
    public ThumbnailRequestVideo getThumbnailRequest(int position) {
        Cursor c = getCursor();
        if (c != null && c.moveToPosition(position)) {
            return new ThumbnailRequestVideo( position, getItemId(position), getCover());
        }
        return null;
    }

    public boolean doesItemNeedAThumbnail(int position) {
        return true;
    }

    

  


    public String getCover() {
        return getCursor().getString(getCursor().getColumnIndex(COVER_PATH));
    }
    // check for local stored subtitles
    private void buildAvailableSubtitlesFileList() {
        mAvailableSubtitlesFiles.clear();
        mAvailableSubtitlesFilesCount = 0;
        // Get first all the files/folders located in the current folder
        File[] files = null;
        try {
            files = MediaUtils.getSubsDir(mContext).listFiles();
        }
        catch (SecurityException e) {
        }

        // Add to the list the files whose extension corresponds to a subtitle file
        if (files != null && files.length > 0) {
            for (File f : files) {
                if (f.isFile()) {
                    if (!f.getName().startsWith(".")) {
                        // Check the file extension
                        String extension = getExtension(f.getPath());
                        if (extension != null && VideoUtils.getSubtitleExtensions().contains(extension)) {
                            // This is a subtitles file => add it to the list
                            // (we only care about the filename so we can drop the extension)
                            String nameWithoutExtension = f.getName().substring(0, f.getName().length() - extension.length() - 1);
                            mAvailableSubtitlesFiles.add(nameWithoutExtension);
                        }
                    }
                }
            }
        }
        files = null;
        mAvailableSubtitlesFilesCount = mAvailableSubtitlesFiles.size();
    }

    public String getExtension(String filename) {
        int dotPos = filename.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filename.length()) {
            return filename.substring(dotPos + 1).toLowerCase();
        }
        return null;
    }



    public boolean hasLocalSubtitles(String filePath) {
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        if (mAvailableSubtitlesFilesCount > 0) {
            // Extract the name of the video
            String extension = getExtension(fileName);
            if (extension == null)
                return false;
            String fileNameWithoutExtension = fileName.substring(0, fileName.length() - extension.length() - 1);

            // Check if there is at least one subtitle file which corresponds to this video
            int i;
            for (i = 0; i < mAvailableSubtitlesFilesCount; i++) {
                if (mAvailableSubtitlesFiles.get(i).startsWith(fileNameWithoutExtension)) {
                    return true;
                }
            }
        }
        return false;
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

    public String getPath(int position) {
        return null;
    }

    public boolean hasRemoteSubtitles(int position) {
        return false;
    }
    public void setPresenter(Class<?> objectClass, Presenter presenter) {
        mPresenters.put(objectClass, presenter);
    }
}
