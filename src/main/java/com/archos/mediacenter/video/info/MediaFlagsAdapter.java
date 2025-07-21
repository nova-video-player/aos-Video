package com.archos.mediacenter.video.info;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MediaFlagsAdapter extends RecyclerView.Adapter<MediaFlagsAdapter.ViewHolder> {

    private final List<MediaFlag> flags;
    private final Context context;

    public MediaFlagsAdapter(Context context, List<MediaFlag> flags) {
        this.context = context;
        this.flags = flags;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media_flag, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MediaFlag flag = flags.get(position);
        Bitmap bitmap = getBitmapFromAsset(flag.assetPath);
        Log.d("Adapter", "Binding flag at position " + position + ": " + flag.assetPath);
        if (bitmap != null) {
            holder.icon.setImageBitmap(bitmap);
            holder.itemView.setVisibility(View.VISIBLE);
        } else {
            holder.itemView.setVisibility(View.GONE); // Hide if asset missing
        }

        holder.itemView.setOnClickListener(flag.clickListener);
    }

    @Override
    public int getItemCount() {
        return flags.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.media_flag_icon);
        }
    }

    private Bitmap getBitmapFromAsset(String assetPath) {
        AssetManager assetManager = context.getAssets();
        try (InputStream inputStream = assetManager.open(assetPath)) {
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}