package com.archos.mediacenter.video.browser.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.R;
import com.bumptech.glide.Glide;

import java.util.List;

public class StudioAdapter extends RecyclerView.Adapter<StudioAdapter.StudioViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(String item);
        void onItemLongClick(int position);
    }
    private List<String> StudioLogoPaths;
    private OnItemClickListener listener;
    public StudioAdapter(List<String> StudioLogoPaths, OnItemClickListener listener) {
        this.StudioLogoPaths = (List<String>) StudioLogoPaths;
        this.listener = listener;
    }
    @Override
    public StudioAdapter.StudioViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.studio_logo, parent, false);
        StudioViewHolder vh = new StudioViewHolder(v);
        return vh;
    }
    @Override
    public void onBindViewHolder( StudioAdapter.StudioViewHolder vh, int position) {
        final String path = StudioLogoPaths.get(position);
        Glide.with(vh.itemView.getContext()).load(path).into(vh.logoImage);
        String basepath = "/data/user/0/org.courville.nova/app_scraper_studiologos/";
        String extension = ".png";
        final String clicked_studioname = path.replace(basepath, "").replace(extension, "");
        final int Position = position;
        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(vh.itemView.getContext(), clicked_studioname, Toast.LENGTH_SHORT).show();
            }
        });
        vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onItemLongClick(Position);
                return false;
            }
        });
    }
    @Override
    public int getItemCount() {
        return StudioLogoPaths.size();
    }
    public class StudioViewHolder extends RecyclerView.ViewHolder {
        protected ImageView logoImage;

        public StudioViewHolder(View itemView) {
            super(itemView);
            logoImage = itemView.findViewById(R.id.studio_logo);
        }
    }
}