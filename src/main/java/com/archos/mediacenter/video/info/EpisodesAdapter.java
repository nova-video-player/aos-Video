package com.archos.mediacenter.video.info;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.R;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.List;

public class EpisodesAdapter extends RecyclerView.Adapter<EpisodesAdapter.EpisodesViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private List<EpisodeModel> episodeslist;
    private EpisodesAdapter.OnItemClickListener listener;
    private int isEpisodeSelected;
    private int selectedIndex;

    void setSelectedIndex(int position){
        selectedIndex = position;
    }


    public EpisodesAdapter(List<EpisodeModel> episodeslist, EpisodesAdapter.OnItemClickListener listener) {
        this.episodeslist = episodeslist;
        this.listener = listener;
    }
    @Override
    public EpisodesAdapter.EpisodesViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.episode_itemview, parent, false);
        EpisodesAdapter.EpisodesViewHolder vh = new EpisodesAdapter.EpisodesViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(EpisodesAdapter.EpisodesViewHolder vh, int position) {
        TextView textView = vh.itemView.findViewById(R.id.episode_number);
        ImageView imageView = vh.itemView.findViewById(R.id.episode_picture);
        LinearLayout pictureContainer = vh.itemView.findViewById(R.id.episode_picture_container);
        EpisodeModel episodeModel = episodeslist.get(position);
        textView.setText(Integer.toString(episodeModel.getEpisodeNumber()));
        textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

        if (episodeModel.getEpisodePath()!=null) {
            Picasso.get().load(new File(episodeModel.getEpisodePath())).fit().centerCrop().into(imageView);
        } else {
            Picasso.get().load(R.drawable.default_image).fit().centerCrop().into(imageView);
        }

        Drawable background = ResourcesCompat.getDrawable(vh.itemView.getContext().getResources(),
                R.drawable.episode_selector, null);

        if (selectedIndex == position) {
            pictureContainer.setBackgroundColor(vh.itemView.getContext().getResources().getColor(R.color.selected_episode_picture));
            textView.setBackground(background);
            textView.setTextColor(Color.BLACK);

        } else {
            pictureContainer.setBackgroundColor(vh.itemView.getContext().getResources().getColor(R.color.unselected_episode_picture));
            textView.setBackgroundColor(Color.TRANSPARENT);
            textView.setTextColor(Color.WHITE);
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

    public class EpisodesViewHolder extends RecyclerView.ViewHolder {
        protected TextView episodeNumber;
        protected ImageView episodePicture;
        public EpisodesViewHolder(View itemView) {
            super(itemView);
        }
    }
}