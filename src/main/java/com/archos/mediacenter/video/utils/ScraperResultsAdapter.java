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

package com.archos.mediacenter.video.utils;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.utils.imageview.ImageViewSetter;
import com.archos.mediacenter.utils.imageview.ImageViewSetterConfiguration;
import com.archos.mediacenter.utils.imageview.SimpleFileProcessor;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.MovieTags;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.ShowTags;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;


public class ScraperResultsAdapter extends BaseAdapter {
    private final static String TAG = "ScraperResultsAdapter";

    private Context mContext;
    private LayoutInflater mInflater;

    private int mItemsUpdated;
    private List<ItemData> mItems;

    // to set bitmap images from files
    private ImageViewSetter mImgSetter;
    private SimpleFileProcessor mFileLoader;

    private int mPosterWidth;
    private int mPosterHeight;

    private final class ItemData {
        public String posterPath;
        public String name;
        public String date;
        public String directors;

        public ItemData(String title) {
            this.posterPath = null;
            this.name = title;
            this.date = "";
            this.directors = "";
        }
    }

    //********************************************************************
    // ListView interface implementation
    //********************************************************************

    static class ViewHolder {
        ImageView poster;
        ProgressBar spinbar;
        TextView name;        
        TextView date;
        TextView directors;
    }
 
    public ScraperResultsAdapter(Context context,BaseTags nfoTags,  List<SearchResult> results) {
        super();

        mContext = context;
        mInflater = LayoutInflater.from(context);

        mPosterWidth = mContext.getResources().getDimensionPixelSize(R.dimen.auto_scraper_poster_width);
        mPosterHeight = mPosterWidth * 3 / 2;

        // setup image loader
        Drawable defaultImage = ContextCompat.getDrawable(context, R.drawable.filetype_video_vertical);
        ImageViewSetterConfiguration config = ImageViewSetterConfiguration.Builder
                .createNew()
                .setDrawableWhileLoading(defaultImage)
                .build();
        mImgSetter = new ImageViewSetter(mContext, config);
        mFileLoader = new SimpleFileProcessor(true, mPosterWidth, mPosterHeight);

        setResultList(nfoTags, results);
    }

    public void setResultList (BaseTags nfoTags, List<SearchResult> results) {
        mItemsUpdated = 0;
        mItems = buildInitialItemsData(nfoTags, results);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            // Inflate the view layout
            convertView = mInflater.inflate(R.layout.video_info_scraper_search_result_item, parent, false);

            // Build a new ViewHolder and attach it to the view
            holder = new ViewHolder();
            holder.poster = (ImageView)convertView.findViewById(R.id.poster);
            holder.spinbar = (ProgressBar)convertView.findViewById(R.id.spinbar);
            holder.name = (TextView)convertView.findViewById(R.id.name);
            holder.date = (TextView)convertView.findViewById(R.id.date);
            holder.directors = (TextView)convertView.findViewById(R.id.directors);

            convertView.setTag(holder);

            // Set the size of the poster
            ImageView poster = (ImageView)convertView.findViewById(R.id.poster);
            ViewGroup.LayoutParams lp = poster.getLayoutParams();
            lp.width = mPosterWidth;
            lp.height = mPosterHeight;
        }
        else {
            // Use the provided ViewHolder
            holder = (ViewHolder)convertView.getTag();
        }

        ItemData itemData = mItems.get(position);

        // itemData can be null according to sentry
        if (itemData != null) {
            mImgSetter.set(holder.poster, mFileLoader, itemData.posterPath);
            holder.name.setText(itemData.name);
            holder.date.setText(itemData.date);
            holder.directors.setText(itemData.directors);
        }
        holder.spinbar.setVisibility((mItemsUpdated == position) ? View.VISIBLE : View.GONE);
        holder.date.setVisibility((itemData != null & itemData.date.length() > 0) ? View.VISIBLE : View.INVISIBLE);
        holder.directors.setVisibility((itemData != null & itemData.directors.length() > 0) ? View.VISIBLE : View.INVISIBLE);

        return convertView;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    public int getCount() {
        return mItems.size();
    }

    public Object getItem(int position) {
        return mItems.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    //********************************************************************
    // Additional public API
    //********************************************************************

    public void updateItemData(int position, MovieTags movieTags) {
        // Replace the data of the item at the provided position
        if (position >= mItems.size())
            return;
        ItemData itemData = mItems.get(position);

        File posterFile = movieTags.getCover();
        if (posterFile != null) {
            itemData.posterPath = posterFile.getPath();
        }

        if (movieTags.getYear() != 0) {
            itemData.date = String.valueOf(movieTags.getYear());
        }

        if (movieTags.getDirectors().size() > 0) {
            // Append all the directors as a single string
            itemData.directors = TextUtils.join(", ", movieTags.getDirectors());
        }

        mItems.set(position, itemData);
    }

    public void addItemData(MovieTags movieTags) {
        ItemData itemData = new ItemData(movieTags.getTitle());

        File posterFile = movieTags.getCover();
        if (posterFile != null) {
            itemData.posterPath = posterFile.getPath();
        }

        if (movieTags.getYear() != 0) {
            itemData.date = String.valueOf(movieTags.getYear());
        }

        if (movieTags.getDirectors().size() > 0) {
            // Append all the directors as a single string
            itemData.directors = TextUtils.join(", ", movieTags.getDirectors());
        }

        mItems.add(itemData);
    }

    public void updateItemData(int position, EpisodeTags episodeTags) {
        // Replace the data of the item at the provided position
        ShowTags showTags = episodeTags.getShowTags();
        ItemData itemData = mItems.get(position);

        // returns show cover if no ep cover exists
        File episodeCover = episodeTags.getCover();
        if (episodeCover != null) {
            itemData.posterPath = episodeCover.getPath();
        }

        String date = "";
        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
        if (episodeTags.getAired() != null && episodeTags.getAired().getTime() > 0) {
            // Display the aired date of the current episode
            date = df.format(episodeTags.getAired());
        }
        else if (showTags != null && showTags.getPremiered() != null && showTags.getPremiered().getTime() > 0) {
            // Aired date not available => try at least the premiered date
            date = df.format(showTags.getPremiered());
        }
        itemData.date = date;

        if (episodeTags.getDirectors().size() > 0) {
            // Append all the directors as a single string
            itemData.directors = TextUtils.join(", ", episodeTags.getDirectors());
        }

        mItems.set(position, itemData);
    }

    public void addItemData(EpisodeTags episodeTags) {
        // Replace the data of the item at the provided position
        ShowTags showTags = episodeTags.getShowTags();
        ItemData itemData = new ItemData(episodeTags.getTitle());

        // returns show cover if no ep cover exists
        File episodeCover = episodeTags.getCover();
        if (episodeCover != null) {
            itemData.posterPath = episodeCover.getPath();
        }

        String date = "";
        DateFormat df = DateFormat.getDateInstance(DateFormat.LONG);
        if (episodeTags.getAired() != null && episodeTags.getAired().getTime() > 0) {
            // Display the aired date of the current episode
            date = df.format(episodeTags.getAired());
        }
        else if (showTags != null && showTags.getPremiered() != null && showTags.getPremiered().getTime() > 0) {
            // Aired date not available => try at least the premiered date
            date = df.format(showTags.getPremiered());
        }
        itemData.date = date;

        if (episodeTags.getDirectors().size() > 0) {
            // Append all the directors as a single string
            itemData.directors = TextUtils.join(", ", episodeTags.getDirectors());
        }

        mItems.add(itemData);
    }

    public void updateAvailableItemsData(List<BaseTags> tagsList) {
        int position;
        int size = tagsList.size();
        for (position = 0; position < size; position++) {
            BaseTags tags = tagsList.get(position);

            if (tags instanceof MovieTags) {
                updateItemData(position, (MovieTags)tags);
            }
            else if (tags instanceof EpisodeTags) {
                updateItemData(position, (EpisodeTags)tags);
            }
        }

        mItemsUpdated = tagsList.size();
    }

    public void setItemsUpdated(int items) {
        mItemsUpdated = items;
    }


    //********************************************************************
    // Local functions
    //********************************************************************

    private List<ItemData> buildInitialItemsData(BaseTags nfoTags, List<SearchResult> results) {
        int position;
        int size = results != null ? results.size() : 0;
        List<ItemData> items = new ArrayList<ItemData>(size+(nfoTags==null?0:1));
        if(nfoTags!=null){
            items.add(new ItemData(nfoTags.getTitle()));
        }
        for (position = 0; position < size; position++) {
            SearchResult res = results.get(position);

            ItemData itemData = new ItemData(res.getTitle());
            items.add(itemData);
        }

        return items;
    }

}
