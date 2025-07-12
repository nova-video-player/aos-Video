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
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.DisplayMetrics;
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
import com.archos.mediacenter.video.browser.adapters.object.Episode;
import com.archos.mediacenter.video.browser.adapters.object.Movie;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScraperImage;

import java.util.Collections;
import java.util.List;

public class VideoInfoPosterChooserFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = VideoInfoPosterChooserFragment.class.getSimpleName();
    private static final boolean DBG = false;
    // debug fragment lifecycle
    private static final boolean DBG_LC = false;

    public static final String MSG_SHOW_MODE = "showmode";

    private PosterAdapter mAdapter;
    private BaseTags mTag;

    private boolean mShowMode;
    private View mView;
    private long mOnlineId = -1;
    private int mSeason;

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

        mView.findViewById(R.id.cancel).setOnClickListener(this);

        RecyclerView recyclerView = mView.findViewById(R.id.list);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidthPx = dm.widthPixels;
        int itemWidthPx = getResources().getDimensionPixelSize(R.dimen.video_info_poster_chooser_width);

        // Step 1: compute max number of columns that can fit
        int spanCount = screenWidthPx / itemWidthPx;
        if (spanCount < 1) spanCount = 1;

        // Step 2: compute remaining space and spacing
        int totalItemWidth = spanCount * itemWidthPx;
        int spacingPx = (screenWidthPx - totalItemWidth) / (spanCount + 1); // <<< spacing between and around

        // Step 3: Setup RecyclerView
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        recyclerView.setAdapter(mAdapter);

        // Set padding instead of decorating left/right
        recyclerView.setPadding(spacingPx, spacingPx, spacingPx, spacingPx);
        recyclerView.setClipToPadding(false);
        recyclerView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);

        // Only add spacing between columns (not left/right)
        int finalSpanCount = spanCount;
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                int column = position % finalSpanCount;

                // spacing between columns
                outRect.left = column * spacingPx / finalSpanCount;
                outRect.right = spacingPx - (column + 1) * spacingPx / finalSpanCount;

                // Apply top spacing only if NOT in first row
                if (position >= finalSpanCount) {
                    outRect.top = spacingPx;
                } else {
                    outRect.top = 0; // no top spacing for first row
                }
            }
        });

        mAdapter = new PosterAdapter(getContext(), image -> {
            new VideoInfoPosterChooserFragment.PosterSaver(getActivity(), VideoInfoPosterChooserFragment.this, mSeason, mOnlineId).execute(image);
        });

        recyclerView.setAdapter(mAdapter);
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
        mSeason = -1;
        if(video instanceof Movie)
            mOnlineId = ((Movie) video).getOnlineId();

        if (video instanceof Episode) {
            Episode episode =
                    (Episode) video;
            mSeason = episode.getSeasonNumber(); // You may need to adapt this if it's fetched differently
            mOnlineId = episode.getOnlineId();
        }

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

    protected static class ScraperImageProcessor extends ImageProcessor {
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
