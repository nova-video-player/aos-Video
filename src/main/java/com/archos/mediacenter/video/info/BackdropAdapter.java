package com.archos.mediacenter.video.info;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.utils.imageview.ImageViewSetter;
import com.archos.mediacenter.video.R;
import com.archos.mediascraper.ScraperImage;

import java.util.Collections;
import java.util.List;

class BackdropAdapter extends RecyclerView.Adapter<BackdropAdapter.ViewHolder> {
    private List<ScraperImage> mList = Collections.emptyList();
    private final LayoutInflater mInflater;
    private final ImageViewSetter mSetter;
    private final VideoInfoBackdropChooserFragment.ScraperImageThumbProcessor mLoader;
    private final OnItemClickListener mListener;

    interface OnItemClickListener {
        void onItemClick(ScraperImage image);
    }

    public BackdropAdapter(Context context, OnItemClickListener listener) {
        mInflater = LayoutInflater.from(context);
        mSetter = new ImageViewSetter(context, null);
        mLoader = new VideoInfoBackdropChooserFragment.ScraperImageThumbProcessor(context);
        mListener = listener;
    }

    public void setList(List<ScraperImage> list) {
        mList = list != null ? list : Collections.emptyList();
        notifyDataSetChanged();
    }

    public void stopLoading() {
        mSetter.stopLoadingAll();
    }

    public void cleanup() {
        stopLoading();
        mSetter.clearCache();
        mList = Collections.emptyList();
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.video_info_backdrop_chooser_list_item, parent, false);
        return new ViewHolder(view, mListener);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ScraperImage image = mList.get(position);
        mSetter.set(holder.image, mLoader, image);
        holder.bind(image);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        ScraperImage currentImage;

        ViewHolder(View itemView, OnItemClickListener listener) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            itemView.setOnClickListener(v -> {
                if (listener != null && currentImage != null)
                    listener.onItemClick(currentImage);
            });
        }

        void bind(ScraperImage image) {
            currentImage = image;
        }
    }
}
