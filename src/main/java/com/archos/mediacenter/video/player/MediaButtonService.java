package com.archos.mediacenter.video.player;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaButtonService extends Service {

    private static final Logger log = LoggerFactory.getLogger(MediaButtonService.class);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        log.debug("iBinder");
        return null;
    }
    private MediaSessionCompat.Callback mediaSessionCompatCallBack = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            super.onPlay();
            log.debug("onPlay");
        }

        @Override
        public void onPause() {
            super.onPause();
            log.debug("onPause");
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            log.debug("mediaSessionCompatCallBack: next button is pressed");
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            log.debug("mediaSessionCompatCallBack: previous button pressed");
        }

        @Override
        public void onStop() {
            super.onStop();
            log.debug("mediaSessionCompatCallBack: stop button pressed");
        }

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            String intentAction = mediaButtonIntent.getAction();
            log.debug("onMediaButtonEvent");
            if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null) {
                    int action = event.getAction();
                    if (action == KeyEvent.ACTION_DOWN) {
                        switch (event.getKeyCode()) {
                            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                                // code for fast forward
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_NEXT:
                                // code for next
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                                // code for play/pause
                                log.debug("onMediaButtonEvent: play/pause button pressed");
                                PlayerService.playPausePlayer();
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                                // code for previous
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_REWIND:
                                // code for rewind
                                return true;
                            case KeyEvent.KEYCODE_MEDIA_STOP:
                                // code for stop
                                log.debug("onMediaButtonEvent: stop button pressed");
                                return true;

                        }
                        return false;
                    }
                    if (action == KeyEvent.ACTION_UP) {
                    }
                }
            }
            return super.onMediaButtonEvent(mediaButtonIntent);
        }
    };

    private MediaSessionCompat mediaSessionCompat;


    @Override
    public void onCreate() {
        log.debug("onCreate");

        mediaSessionCompat = new MediaSessionCompat(this, "MEDIA");
        mediaSessionCompat.setCallback(mediaSessionCompatCallBack);
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        PlaybackStateCompat.Builder mStateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE);

        mediaSessionCompat.setPlaybackState(mStateBuilder.build());
        mediaSessionCompat.setActive(true);
    }

    @Override
    public void onDestroy() {
        log.debug("onDestroy");
        mediaSessionCompat.release();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.debug("onStartCommand");
        MediaButtonReceiver.handleIntent(mediaSessionCompat, intent);
        return super.onStartCommand(intent, flags, startId);
    }
}