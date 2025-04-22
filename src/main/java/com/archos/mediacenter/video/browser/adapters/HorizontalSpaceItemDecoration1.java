package com.archos.mediacenter.video.browser.adapters;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class HorizontalSpaceItemDecoration1 extends RecyclerView.ItemDecoration {
    private final int horizontalSpace;

    public HorizontalSpaceItemDecoration1(int horizontalSpace) {
        this.horizontalSpace = horizontalSpace;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (view.getVisibility() != View.VISIBLE) {
            // Skip spacing if item is hidden
            outRect.set(0, 0, 0, 0);
            return;
        }

        int position = parent.getChildAdapterPosition(view);
        int itemCount = parent.getAdapter().getItemCount();
        int halfSpace = horizontalSpace / 2;

        if (position == 0) {
            outRect.left = horizontalSpace;
            outRect.right = halfSpace;
        } else if (position == itemCount - 1) {
            outRect.left = halfSpace;
            outRect.right = horizontalSpace;
        } else {
            outRect.left = halfSpace;
            outRect.right = halfSpace;
        }
    }
}



