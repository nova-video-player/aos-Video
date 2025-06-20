package com.archos.mediacenter.video.browser;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;

import androidx.core.content.res.ResourcesCompat;

import com.archos.mediacenter.video.R;

public class ItemDataWidthCalculator {
    public static int getMaxItemDataWidth(Context context) {
        // List all relevant string resource IDs
        int[] stringIds = {
            R.string.movies,
            //R.string.movies_by_year,
            //R.string.movies_by_genre,
            R.string.all_tv_shows,
            R.string.all_videos,
            R.string.recently_added_videos,
            R.string.recently_played_videos,
            R.string.video_lists,
            R.string.video_folder,
            R.string.sd_card_storage,
            R.string.usb_host_storage,
            R.string.other_storage,
            R.string.network_shared_folders,
            R.string.network_shortcuts,
            R.string.network_media_servers,
            R.string.provider_folders,
            R.string.preferences,
            R.string.help_faq,
            R.string.sponsor
            // Add any other relevant strings here
        };

        Paint paint = new Paint();
        Typeface typeface = ResourcesCompat.getFont(context, R.font.gotham_bold);
        paint.setTypeface(typeface);
        // 18sp to px
        float textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 18, context.getResources().getDisplayMetrics());
        paint.setTextSize(textSizePx);

        int maxWidth = 0;
        for (int id : stringIds) {
            String text = context.getString(id);
            int width = (int) paint.measureText(text);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        return maxWidth; // in pixels
    }
} 