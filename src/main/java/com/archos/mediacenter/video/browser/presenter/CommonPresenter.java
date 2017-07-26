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
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.mediacenter.utils.ImageLabel;
import com.archos.mediacenter.utils.ThumbnailEngine;
import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.AdapterDefaultValues;
import com.archos.mediacenter.video.browser.filebrowsing.ListingAdapter;

/**
 * Created by alexandre on 26/10/15.
 */
public class CommonPresenter implements Presenter {

    protected final AdapterDefaultValues mDefaultValues;
    protected final Context mContext;
    private final ExtendedClickListener mOnExtendedClick;

    public interface ExtendedClickListener{
        public void onExtendedClick(View view, Object v, int positionInAdapter);
    }
    public int getItemType() {
        return ListingAdapter.ITEM_VIEW_TYPE_FILE;
    }

    public static class ViewHolder {
        int type;
        ImageLabel bookmark;
        ImageButton expanded;
        ProgressBar resume;
        ImageLabel subtitle;
        ImageLabel network;
        public ImageView thumbnail;
        ImageView traktWatched;
        ImageView traktLibrary;
        ImageView video3D;
        TextView info;
        TextView name;
        TextView number;
        View secondLine;
        // This is the clickable zone.
        View expandedZone;
        View countcontainer;
        TextView count;
    }

    public CommonPresenter(Context context, AdapterDefaultValues defaultValues, ExtendedClickListener onExtendedClick){
        mDefaultValues = defaultValues;
        mContext = context;
        mOnExtendedClick = onExtendedClick;


    }


    @Override
    public View getView(ViewGroup parent, Object object, View view) {
        ViewHolder holder;
        int inflate = mDefaultValues.getLayoutId(getItemType());
        if(!(view!=null&& view.getTag()!=null&& view.getTag().getClass().isInstance(getNewViewHolder()) && ((ViewHolder)view.getTag()).type == inflate))
            view = LayoutInflater.from(mContext).inflate(inflate, parent, false);
        // -------------------------------------------------
        // File-based item => create a ViewHolder structure
        // -------------------------------------------------
        holder = getNewViewHolder();
        holder.type = inflate;

        holder.number = (TextView) view.findViewById(R.id.number);
        holder.thumbnail = (ImageView) view.findViewById(R.id.thumbnail);

        if (Trakt.isTraktV2Enabled(mContext, PreferenceManager.getDefaultSharedPreferences(mContext))) {
            holder.traktWatched = (ImageView) view.findViewById(R.id.trakt_watched);
            holder.traktLibrary = (ImageView) view.findViewById(R.id.trakt_library);
        }

        holder.video3D = (ImageView) view.findViewById(R.id.flag_3d);
        holder.expanded = (ImageButton) view.findViewById(R.id.expanded);
        holder.secondLine=view.findViewById(R.id.bottom_row);
        holder.name = (TextView) view.findViewById(R.id.name);
        holder.count = (TextView) view.findViewById(R.id.occurencies_text_view);
        holder.countcontainer = view.findViewById(R.id.occurencies_container);
        holder.info = (TextView) view.findViewById(R.id.info);
        holder.resume = (ProgressBar) view.findViewById(R.id.resume_notif);
        holder.bookmark = (ImageLabel) view.findViewById(R.id.bookmark_notif);
        holder.subtitle = (ImageLabel) view.findViewById(R.id.subtitle_notif);
        holder.network = (ImageLabel) view.findViewById(R.id.network_notif);

        view.setTag(holder);
        return view;
    }

    /**
     * Basic ViewHolder can be extended to handle more views.
     */
    public ViewHolder getNewViewHolder() {
        return new ViewHolder();
    }

    @Override
    public View bindView(View view, final Object object, ThumbnailEngine.Result r, final int positionInAdapter) {

            final ViewHolder holder = (ViewHolder) view.getTag();
        if(holder.expanded!=null){
            holder.expanded.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mOnExtendedClick.onExtendedClick(holder.thumbnail,object,positionInAdapter );
                }
            });
            holder.expanded.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {

                    return false;
                }
            });
        }
        return view;
    }
}
