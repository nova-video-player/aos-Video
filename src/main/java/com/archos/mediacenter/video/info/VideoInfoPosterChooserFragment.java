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
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.archos.mediacenter.utils.imageview.ImageProcessor;
import com.archos.mediacenter.utils.imageview.ImageViewSetter;
import com.archos.mediacenter.utils.imageview.LoadResult.Status;
import com.archos.mediacenter.utils.imageview.LoadTaskItem;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Base;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScraperImage;

import java.util.Collections;
import java.util.List;

public class VideoInfoPosterChooserFragment extends Fragment implements
        AdapterView.OnItemClickListener,
        View.OnClickListener {
    private static final String TAG = VideoInfoPosterChooserFragment.class.getSimpleName();
    private static final boolean DBG = false;
    // debug fragment lifecycle
    private static final boolean DBG_LC = false;

    public static final String MSG_SHOW_MODE = "showmode";

    private GridView mGrid;
    private PosterAdapter mAdapter;
    private BaseTags mTag;

    private boolean mShowMode;
    private View mView;
    private long mOnlineId = -1;

    public VideoInfoPosterChooserFragment() {
    }

    // ---------------------- FRAGMENT LIFECYCLE ---------------------------- //

    // onAttach

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DBG_LC) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // in case we get recreated restore the tag, may be null anyways.
        if (savedInstanceState != null)
            mTag = savedInstanceState.getParcelable(TAG);
        if(mTag==null)
            setVideo((Base)getActivity().getIntent().getSerializableExtra(VideoInfoPosterBackdropActivity.EXTRA_VIDEO));
        // we'd like to keep this instance when rotating
       // setRetainInstance(VideoInfoActivity2.KEEP_FRAGMENT_INSTANCE);

        // init the adapter here so it does not get recreated when rotating and it keeps the list
        // using application context here since we keep the adapter around for longer and keeping
        // a reference to the whole activity could prevent GC
        mAdapter = new PosterAdapter(getActivity().getApplicationContext(), null);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.video_info_poster_chooser, null);
        return mView;
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (DBG_LC) Log.d(TAG, "onViewCreated");

        // cancel button
        view.findViewById(R.id.cancel).setOnClickListener(this);

        // image grid
        mGrid = (GridView) view.findViewById(R.id.list);
        mGrid.setAdapter(mAdapter);
        mGrid.setOnItemClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        if (DBG_LC) Log.d(TAG, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        startLoadingIfReady();
    }

    @Override
    public void onStart() {
        if (DBG_LC) Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onResume() {
        if (DBG_LC) Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    public void onPause() {
        if (DBG_LC) Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        if (DBG_LC) Log.d(TAG, "onStop");
        super.onStop();
        // tell adapter that it does not need to set images any more
        // prevents long running downloads from affecting the ui which is not
        // visible after here.
        mAdapter.stopLoading();
    }

    @Override
    public void onDestroyView() {
        if (DBG_LC) Log.d(TAG, "onDestroyView");
        super.onDestroyView();
        // no more view = no more grid.
        mGrid = null;
    }

    @Override
    public void onDestroy() {
        if (DBG_LC) Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    // onDetach

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (DBG_LC) Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putParcelable(TAG, mTag); // abuse our logtag
    }



    public void setVideo(Base video) {
        if (DBG) Log.d(TAG, "setInfoItem");
        BaseTags tags = video.getFullScraperTags(getActivity());
        mOnlineId = -1;
        if(video instanceof Movie)
            mOnlineId = ((Movie) video).getOnlineId();
        if (mShowMode && tags instanceof EpisodeTags) {
            mTag = ((EpisodeTags) tags).getShowTags();
        } else {
            mTag = tags;
        }
        if (DBG) Log.d(TAG, "Info set");
        startLoadingIfReady();
    }




    // ---------------------- EVENT HANDLER IMPLEMENTATION ------------------ //
    public void onClick(View v) {
        if (DBG) Log.d(TAG, "onClick - cancel");
        stop();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (DBG) Log.d(TAG, "onItemClick - " + position);
        ScraperImage clickedImage = (ScraperImage) mAdapter.getItem(position);
        int season = -1;
        if (mTag instanceof EpisodeTags)
            season = ((EpisodeTags) mTag).getSeason();
        new PosterSaver(getActivity(), this, season, mOnlineId).execute(clickedImage);
    }

    // ---------------------- INTERNAL UTILITY METHODS ---------------------- //
    /* default */ void stop() {
        if(getActivity()!=null)
            getActivity().finish();
    }

    private void startLoadingIfReady() {
        if (mTag != null && getActivity() != null && mAdapter != null) {
            new PosterListLoader(getActivity(), mAdapter).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,mTag);
        }
    }

    // ---------------------- INTERNALLY USED CLASSES ----------------------- //

    /** Loads a List<ScraperImage> from the database and sets it to a PosterAdapter */
    private static class PosterListLoader extends AsyncTask<BaseTags, Void, List<ScraperImage>> {
        private final Context mContext;
        private final PosterAdapter mTargetAdapter;

        public PosterListLoader(Context context, PosterAdapter target) {
            mContext = context;
            mTargetAdapter = target;
        }

        @Override
        protected List<ScraperImage> doInBackground(BaseTags... params) {
            Log.d(TAG, "get posters");
            if (params != null && params.length > 0) {
                return params[0].getAllPostersInDb(mContext);
            }
            return null;
        }

        @Override
        protected void onPostExecute(List<ScraperImage> result) {
            if (mTargetAdapter != null)
                mTargetAdapter.setList(result);
        }
    }

    /** Saves a Poster as default poster for a video and stops the hosting fragment */
    private static class PosterSaver extends AsyncTask<ScraperImage, Void, Boolean> {
        private final Context mContext;
        private final VideoInfoPosterChooserFragment mHost;
        private final int mSeason;
        private final long mOnlineID;
        public PosterSaver(Context context, VideoInfoPosterChooserFragment host, int season, long onlineID) {
            mContext = context;
            mHost = host;
            mOnlineID = onlineID;
            mSeason = season;
        }

        @Override
        protected Boolean doInBackground(ScraperImage... params) {
            params[0].setOnlineId(mOnlineID);
            if (params != null && params.length > 0) {
                return Boolean.valueOf(params[0].setAsDefault(mContext, mSeason));
            }
            return Boolean.FALSE;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mHost != null)
                mHost.stop();
        }
    }

    /** Adapter showing & loading images */
    static class PosterAdapter extends BaseAdapter {
        private List<ScraperImage> mList;
        private final LayoutInflater mInflater;
        private final ImageViewSetter mSetter;
        private final ScraperImageProcessor mLoader;

        public PosterAdapter(Context context, List<ScraperImage> list) {
            mList = list != null ? list : Collections.<ScraperImage> emptyList();

            mInflater = LayoutInflater.from(context);
            mSetter = new ImageViewSetter(context, null);
            mLoader = new ScraperImageProcessor(context);
        }

        public void setList(List<ScraperImage> list) {
            Log.d(TAG, "setList");

            mList = list != null ? list : Collections.<ScraperImage> emptyList();
            notifyDataSetChanged();
        }

        /** stops setter from further setting images async */
        public void stopLoading() {
            mSetter.stopLoadingAll();
        }

        /** stops all tasks and clears the caches etc */
        public void cleanup() {
            mSetter.stopLoadingAll();
            mSetter.clearCache();
            mList = Collections.<ScraperImage> emptyList();
            notifyDataSetChanged();
        }

        public int getCount() {
            if (DBG) Log.d(TAG, "getCount = " + mList.size());
            return mList.size();
        }

        public Object getItem(int position) {
            if (DBG) Log.d(TAG, "getItem " + position);
            return mList.get(position);
        }

        public long getItemId(int position) {
            if (DBG) Log.d(TAG, "getItemId " + position);
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (DBG) Log.d(TAG, "getView " + position);

            View view;
            ViewHolder holder;

            view = convertView;
            if (view == null) {
                view = mInflater.inflate(R.layout.video_info_poster_chooser_list_item, parent,
                        false);
                holder = new ViewHolder();
                holder.image = (ImageView) view.findViewById(R.id.image);
                holder.image.setScaleType(ScaleType.CENTER_CROP);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }
            mSetter.set(holder.image, mLoader, mList.get(position));

            return view;
        }

        private static class ViewHolder {
            public ViewHolder() { /* empty */ }

            ImageView image;
        }

        private static class ScraperImageProcessor extends ImageProcessor {
            private final Context mContext;

            public ScraperImageProcessor(Context context) {
                mContext = context;
            }

            @Override
            public void loadBitmap(LoadTaskItem taskItem) {
                if (taskItem.loadObject instanceof ScraperImage) {
                    ScraperImage image = (ScraperImage) taskItem.loadObject;
                    String file = image.getLargeFile();
                    if (file != null) {
                        image.download(mContext);
                        taskItem.result.bitmap = BitmapFactory.decodeFile(file);
                    }
                    taskItem.result.status = taskItem.result.bitmap != null ?
                            Status.LOAD_OK : Status.LOAD_ERROR;
                } else {
                    taskItem.result.status = Status.LOAD_BAD_OBJECT;
                }
            }

            @Override
            public boolean canHandle(Object loadObject) {
                return loadObject instanceof ScraperImage;
            }

            @Override
            public String getKey(Object loadObject) {
                if (loadObject instanceof ScraperImage) {
                    ScraperImage image = (ScraperImage) loadObject;
                    // using the url here since images from same url may be used
                    // as
                    // different files. But for cache reasons urls are a better
                    // key
                    return image.getLargeUrl();
                }
                return null;
            }
        }
    }

}
