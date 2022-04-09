package com.archos.mediacenter.video.browser.adapters;

import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.archos.mediacenter.video.R;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.List;

public class CastAdapter extends RecyclerView.Adapter<CastAdapter.CastViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private List<CastData> cast;
    private OnItemClickListener listener;

    public CastAdapter(List<CastData> cast, OnItemClickListener listener) {
        this.cast = cast;
        this.listener = listener;
    }
    @Override
    public CastAdapter.CastViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.cast_layout, parent, false);
        CastViewHolder vh = new CastViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(CastAdapter.CastViewHolder vh, int position) {
        vh.actorName.setText(cast.get(position).getName());
        vh.actorCharacter.setText(cast.get(position).getCharacter());
        Picasso.get().load(new File(cast.get(position).getPhotoPath())).fit().centerInside().error(R.drawable.nocast).into(vh.actorPhoto);
        final int Index = position;
        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItemClick(Index);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cast.size();
    }

    public class CastViewHolder extends RecyclerView.ViewHolder {
        protected TextView actorName, actorCharacter;
        protected ImageView actorPhoto;

        public CastViewHolder(View itemView) {
            super(itemView);
            actorName = (TextView) itemView.findViewById(R.id.cast_name);
            actorCharacter = (TextView) itemView.findViewById(R.id.cast_character);
            actorPhoto = (ImageView) itemView.findViewById(R.id.cast_photo);
        }
    }

    public static class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private int space;
        public SpacesItemDecoration(int space) {
            this.space = space;
        }
        @Override
        public void getItemOffsets(Rect outRect, View view,
                                   RecyclerView parent, RecyclerView.State state) {
            final int itemPosition = parent.getChildAdapterPosition(view);
            final int itemCount = state.getItemCount();
            /** first position */
            if (itemPosition == 0) {
                outRect.left = 0;
                outRect.right = space;
            /** last position */
            } else if (itemCount > 0 && itemPosition == itemCount - 1) {
                outRect.right = 0;
                outRect.left = space;
            }
            /** positions between first and last */
            else {
                outRect.left = space;
                outRect.right = space;
            }
        }
    }
}