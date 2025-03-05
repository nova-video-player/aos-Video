package com.archos.mediacenter.video.browser.adapters;

import static com.archos.filecorelibrary.ImagePaddingUtil.addPadding;
import static com.archos.filecorelibrary.ImagePaddingUtil.getPaddingForRatio;
import static com.archos.filecorelibrary.ImagePaddingUtil.shouldApplyPadding;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
        File file = new File(path);
        if (file.exists()){
            // Load bitmap from file
            Bitmap originalBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (originalBitmap != null) {
                // Calculate padding based on a fixed ratio (e.g., 8% of the image width)
                int padding = getPaddingForRatio(originalBitmap.getWidth(), 0.20f); // Use 20% of the image width for padding
                float transparencyThreshold = 0.65f;  // Define a threshold (e.g., 65% transparent pixels on edges)
                // Check if the edges of the image are transparent enough
                boolean shouldAddPadding = shouldApplyPadding(originalBitmap, padding, transparencyThreshold);
                // If padding should be added, apply it; otherwise, just use the original image
                if (shouldAddPadding) {
                    Bitmap paddedBitmap = addPadding(originalBitmap, padding);
                    vh.logoImage.setImageBitmap(paddedBitmap);
                } else {
                    vh.logoImage.setImageBitmap(originalBitmap);
                }
            }
        } else {
            ViewGroup.LayoutParams params = vh.itemView.getLayoutParams();
            params.height = 0;
            params.width = 0;
            vh.itemView.setLayoutParams(params);
            vh.itemView.setVisibility(View.GONE);
        }
        String baseStudioPath = MediaScraper.getStudioLogoDirectory(vh.itemView.getContext()).getPath() + "/";
        String extension = ".png";
        final String clicked_studioname = path.replace(baseStudioPath, "").replace(extension, "");
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
                //Toast.makeText(vh.itemView.getContext(), vh.itemView.getContext().getResources().getString(R.string.studiologo_changed) + " " + clicked_studioname, Toast.LENGTH_SHORT ).show();
                listener.onItemLongClick(Position);
                return true;
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