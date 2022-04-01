package com.archos.mediacenter.video.browser.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import com.archos.mediacenter.video.R;
import com.archos.mediascraper.MediaScraper;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.List;

public class ShowNetworkAdapter extends RecyclerView.Adapter<ShowNetworkAdapter.ShowNetworkViewHolder> {
    public interface OnItemClickListener {
        void onItemClick(String item);
        void onItemLongClick(int position);
    }
    private List<String> NetworkLogoPaths;
    private OnItemClickListener listener;
    public ShowNetworkAdapter(List<String> NetworkLogoPaths, OnItemClickListener listener) {
        this.NetworkLogoPaths = (List<String>) NetworkLogoPaths;
        this.listener = listener;
    }
    @Override
    public ShowNetworkAdapter.ShowNetworkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.logo, parent, false);
        ShowNetworkViewHolder vh = new ShowNetworkViewHolder(v);
        return vh;
    }
    @Override
    public void onBindViewHolder( ShowNetworkAdapter.ShowNetworkViewHolder vh, int position) {
        final int Position = position;
        final String path = NetworkLogoPaths.get(position);
        File file = new File(path);
        if (file.exists()){
            Picasso.get().load(file).into(vh.logoImage);
        } else {
            ViewGroup.LayoutParams params = vh.itemView.getLayoutParams();
            params.height = 0;
            params.width = 0;
            vh.itemView.setLayoutParams(params);
            vh.itemView.setVisibility(View.GONE);
        }
        String basepath = MediaScraper.getNetworkLogoDirectory(vh.itemView.getContext()).getPath() + "/";
        String extension = ".png";
        final String clicked_networkname = path.replace(basepath, "").replace(extension, "");
        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(vh.itemView.getContext(), clicked_networkname, Toast.LENGTH_SHORT).show();
            }
        });
        vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onItemLongClick(Position);
                return true;
            }
        });
    }
    @Override
    public int getItemCount() {
        return NetworkLogoPaths.size();
    }
    public class ShowNetworkViewHolder extends RecyclerView.ViewHolder {
        protected ImageView logoImage;

        public ShowNetworkViewHolder(View itemView) {
            super(itemView);
            logoImage = itemView.findViewById(R.id.network_logo);
        }
    }
}