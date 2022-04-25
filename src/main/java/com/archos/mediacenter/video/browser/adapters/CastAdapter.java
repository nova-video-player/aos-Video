package com.archos.mediacenter.video.browser.adapters;

import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
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

        ViewOutlineProvider mViewOutlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(final View view, final Outline outline) {
                float cornerRadiusDP = 4f;
                float cornerRadius = TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP, cornerRadiusDP, vh.itemView.getContext().getResources().getDisplayMetrics());
                outline.setRoundRect(0, 0, view.getWidth(), (int)(view.getHeight() + cornerRadius), cornerRadius);
            }
        };

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(vh.itemView.getContext());
        boolean displayActorPhoto = prefs.getBoolean("display_actorPhoto_toast", false);

        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater inflater = LayoutInflater.from(vh.itemView.getContext());
                View layout = inflater.inflate(R.layout.actor_toast,
                        vh.itemView.findViewById(R.id.toast_layout_root));
                TextView name = layout.findViewById(R.id.name);
                TextView character = layout.findViewById(R.id.character);
                ImageView photo = layout.findViewById(R.id.photo);
                if(displayActorPhoto){
                    File file = new File(cast.get(Index).getPhotoPath());
                    if(file.exists()){
                        Picasso.get().load(new File(cast.get(Index).getPhotoPath())).into(photo);
                    } else {
                        photo.setVisibility(View.GONE);
                    }
                } else {
                    photo.setVisibility(View.GONE);
                }
                name.setText(cast.get(Index).getName());
                character.setText(cast.get(Index).getCharacter());
                name.measure(0, 0);
                int nameMeasuredWidth = name.getMeasuredWidth();
                character.measure(0, 0);
                int characterMeasuredWidth = character.getMeasuredWidth();
                int photoWidth = photo.getLayoutParams().width;
                if(nameMeasuredWidth > photoWidth || characterMeasuredWidth > photoWidth){
                    photo.setClipToOutline(false);
                }else{
                    photo.setOutlineProvider(mViewOutlineProvider);
                    photo.setClipToOutline(true);
                }
                Toast toast = new Toast(vh.itemView.getContext());
                toast.setGravity(Gravity.BOTTOM, 0, 100);
                toast.setDuration(Toast.LENGTH_SHORT);
                toast.setView(layout);
                toast.show();
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