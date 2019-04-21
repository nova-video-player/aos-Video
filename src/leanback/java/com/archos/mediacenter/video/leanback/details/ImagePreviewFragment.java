package com.archos.mediacenter.video.leanback.details;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;

import com.archos.mediacenter.video.R;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.File;

public class ImagePreviewFragment extends Fragment {

    public static final String EXTRA_IMAGE_URLS = "image_urls";
    public static final String EXTRA_SELECTED_IMAGE = "selected_image";

    private String[] mImageUrls;
    private int mSelectedImage;

    private View mProgressBar;
    private ImageView mImageView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String imageUrls = getActivity().getIntent().getStringExtra(EXTRA_IMAGE_URLS);
        mImageUrls = imageUrls.split(",");
        mSelectedImage = getActivity().getIntent().getIntExtra(EXTRA_SELECTED_IMAGE, -1);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.leanback_image_preview_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mProgressBar = view.findViewById(R.id.progress);
        mImageView = (ImageView)view.findViewById(R.id.image);
        
        loadImage();
    }

    private void loadImage() {
        String imageUrl = mImageUrls[mSelectedImage].replace("/w342/", "/w780/");

        mProgressBar.setVisibility(View.VISIBLE);
        Picasso.get()
                .load(imageUrl)
                .resize(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels)
                .centerInside()
                .into(mImageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        mProgressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void onError(Exception e) {}
                });
    }

    public boolean goLeft() {
        if (mSelectedImage > 0) {
            mSelectedImage -= 1;

            loadImage();

            return true;
        }

        return false;
    }

    public boolean goRight() {
        if (mSelectedImage < mImageUrls.length - 1) {
            mSelectedImage += 1;

            loadImage();

            return true;
        }

        return false;
    } 
}