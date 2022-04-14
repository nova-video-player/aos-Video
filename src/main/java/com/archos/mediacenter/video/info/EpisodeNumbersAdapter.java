package com.archos.mediacenter.video.info;


import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.R;

import java.util.List;

public class EpisodeNumbersAdapter extends RecyclerView.Adapter<EpisodeNumbersAdapter.EpisodeNumbersViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private List<EpisodeModel> episodeslist;
    private EpisodeNumbersAdapter.OnItemClickListener listener;
    private int isEpisodeSelected;
    private int selectedIndex;

    void setSelectedIndex(int position){
        selectedIndex = position;
    }

    public EpisodeNumbersAdapter(List<EpisodeModel> episodeslist, EpisodeNumbersAdapter.OnItemClickListener listener) {
        this.episodeslist = episodeslist;
        this.listener = listener;
    }
    @Override
    public EpisodeNumbersAdapter.EpisodeNumbersViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.episode_numbers_itemview, parent, false);
        EpisodeNumbersAdapter.EpisodeNumbersViewHolder vh = new EpisodeNumbersAdapter.EpisodeNumbersViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(EpisodeNumbersAdapter.EpisodeNumbersViewHolder vh, int position) {
        TextView tv = (TextView) vh.itemView;
        EpisodeModel episodeModel = episodeslist.get(position);
        String episodeNumber = Integer.toString(episodeModel.getEpisodeNumber());
        tv.setText(episodeNumber);
        tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        Drawable background = ResourcesCompat.getDrawable(vh.itemView.getContext().getResources(),
                R.drawable.episode_number_selector, null);
        if (selectedIndex == position) {
            vh.itemView.setBackground(background);
            tv.setTextColor(Color.BLACK);
        } else {
            vh.itemView.setBackgroundColor(Color.TRANSPARENT);
            tv.setTextColor(Color.WHITE);
        }
        final int newPosition = position;
        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int previousItem = isEpisodeSelected;
                isEpisodeSelected = newPosition;
                notifyItemChanged(previousItem);
                notifyItemChanged(newPosition);
                notifyDataSetChanged();
                listener.onItemClick(newPosition);
            }
        });
    }
    @Override
    public int getItemCount() {
        return episodeslist.size();
    }
    public class EpisodeNumbersViewHolder extends RecyclerView.ViewHolder {
        protected TextView episode;
        public EpisodeNumbersViewHolder(View itemView) {
            super(itemView);
            episode = itemView.findViewById(R.id.episode_number);
        }
    }
} 