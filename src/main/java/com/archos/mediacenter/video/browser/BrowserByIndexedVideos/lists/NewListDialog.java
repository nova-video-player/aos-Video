package com.archos.mediacenter.video.browser.BrowserByIndexedVideos.lists;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.trakt.TraktService;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.browser.adapters.object.Video;
import com.archos.mediaprovider.video.VideoStore;
import com.archos.mediascraper.BaseTags;
import com.archos.mediascraper.EpisodeTags;

/**
 * Created by alexandre on 22/05/17.
 */

public class NewListDialog extends DialogFragment {

    private View mView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme);
        mView = LayoutInflater.from(getActivity()).inflate(R.layout.list_creator_layout, null);
        builder.setView(mView);

        View customTitleView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom_title, null);
        java.util.function.IntFunction<Integer> dpToPx = dp ->
                Math.round(dp * customTitleView.getContext().getResources().getDisplayMetrics().density);
        customTitleView.setPadding(dpToPx.apply(12),dpToPx.apply(10),dpToPx.apply(16),dpToPx.apply(4));
        TextView titleText = customTitleView.findViewById(R.id.dialog_title);
        titleText.setText(R.string.list_title);
        Typeface customFont = ResourcesCompat.getFont(requireContext(), R.font.nhaasgroteskdspro_95blk);
        titleText.setTypeface(customFont);
        titleText.setTextSize(24);
        ImageView iconView = customTitleView.findViewById(R.id.dialog_icon);
        iconView.setVisibility(View.GONE);

        builder.setCustomTitle(customTitleView);
        builder.setPositiveButton(android.R.string.ok
                , new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                EditText text = (EditText)mView.findViewById(R.id.list_title);
                VideoStore.List.ListObj list = new VideoStore.List.ListObj(text.getText().toString(), -1, VideoStore.List.SyncStatus.STATUS_NOT_SYNC);
                Uri uri = getActivity().getContentResolver().insert(VideoStore.List.LIST_CONTENT_URI, list.toContentValues());
                Video video = (Video) getArguments().getSerializable(ListDialog.EXTRA_VIDEO);
                BaseTags metadata = video.getFullScraperTags(getActivity());
                boolean isEpisode = metadata instanceof EpisodeTags;
                VideoStore.VideoList.VideoItem videoItem  =
                        new VideoStore.VideoList.VideoItem(-1,!isEpisode?(int)metadata.getOnlineId():-1, isEpisode?(int)metadata.getOnlineId():-1, VideoStore.List.SyncStatus.STATUS_NOT_SYNC);

                getActivity().getContentResolver().insert(uri, videoItem.toContentValues());
                TraktService.sync(ArchosUtils.getGlobalContext(), TraktService.FLAG_SYNC_AUTO);

            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                AlertDialog ad = (AlertDialog)dialog;
                Typeface typeface = ResourcesCompat.getFont(getContext(), R.font.nhaasgroteskdspro_95blk);
                Button positiveButton = ad.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positiveButton != null) {
                    positiveButton.setTypeface(typeface);
                    Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
                    positiveButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                    positiveButton.setBackground(ripple);
                    positiveButton.setClipToOutline(true);
                }
                Button negativeButton = ad.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (negativeButton != null) {
                    negativeButton.setTypeface(typeface);
                    Drawable ripple = ContextCompat.getDrawable(getContext(), R.drawable.custom_ripple);
                    negativeButton.setTextColor(ContextCompat.getColor(getContext(), R.color.green_accent));
                    negativeButton.setBackground(ripple);
                    negativeButton.setClipToOutline(true);
                }
            }
        });

        return dialog;
    }
}
