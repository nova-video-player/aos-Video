package com.archos.mediacenter.video.player.cast;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.PlayerController;
import com.archos.mediacenter.video.player.SurfaceController;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.google.android.libraries.cast.companionlibrary.utils.FetchBitmapTask;
import com.google.android.libraries.cast.companionlibrary.utils.Utils;
import com.google.android.libraries.cast.companionlibrary.widgets.IMiniController;
import com.google.android.libraries.cast.companionlibrary.widgets.MiniController;

/**
 * A compound component that provides a superset of functionalities required for the global access
 * requirement. This component provides an image for the album art, a play/pause button, and a
 * progressbar to show the current position. When an auto-play queue is playing and pre-loading is
 * set, then this component can show an additional view to inform the user of the upcoming item and
 * to allow immediate playback of the next item or to stop the auto-play.
 *
 * <p>Clients can add this
 * compound component to their layout xml and preferably set the {@code auto_setup} attribute to
 * {@code true} to have the CCL manage the visibility and behavior of this component. Alternatively,
 * clients can register this component with the instance of
 * {@link VideoCastManager} by using the following pattern:<br/>
 *
 * <pre>
 * mMiniController = (MiniController) findViewById(R.id.miniController);
 * mArchosCastManager.addMiniController(mMiniController);
 * mMiniController.setOnMiniControllerChangedListener(mArchosCastManager);
 * </pre>
 *
 * In this case, clients should remember to unregister the component themselves.
 * Then the {@link VideoCastManager} will manage the behavior, including its state and metadata and
 * interactions. Note that using the {@code auto_setup} attribute hand;les all of these
 * automatically.
 */
/**
 * Created by alexandre on 07/06/16.
 */
public class ArchosMiniPlayer extends RelativeLayout implements IMiniController, ArchosVideoCastManager.ArchosCastManagerListener {
    /*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */




        public static final int UNDEFINED_STATUS_CODE = -1;
    private static final String DIALOG_TAG = "tracks_dialog";
    private boolean mAutoSetup;
        private ArchosVideoCastManager mArchosCastManager;
        private Handler mHandler;
        protected ImageView mIcon;
        protected TextView mTitle;
        protected TextView mSubTitle;
        protected ImageView mPlayPause;
        private Uri mIconUri;
        private Drawable mPauseDrawable;
        private Drawable mPlayDrawable;
        private Drawable mStopDrawable;
        private FetchBitmapTask mFetchBitmapTask;
        private ProgressBar mProgressBar;
        private ImageView mUpcomingIcon;
        private TextView mUpcomingTitle;
        private View mUpcomingContainer;
        private View mUpcomingPlay;
        private View mUpcomingStop;
        private Uri mUpcomingIconUri;
        private FetchBitmapTask mFetchUpcomingBitmapTask;
        private View mMainContainer;
        private MediaQueueItem mUpcomingItem;
        private String mTitleText;


    private ImageButton mSecondaryPlayPause;
    private TextView mLiveText;
    private TextView mStart;
    private TextView mEnd;
    private SeekBar mSeekbar;
    private ImageView mRatioButton;
    private ProgressBar mLoading;
    private double mVolumeIncrement;
    private MiniController.OnMiniControllerChangedListener mListener;
    private int mStreamType = MediaInfo.STREAM_TYPE_BUFFERED;
    private ImageButton mClosedCaptionIcon;
    private ImageButton mSkipNext;
    private ImageButton mSkipPrevious;
    private View mPlaybackControls;
    private Toolbar mToolbar;
    private Button mSwitchButton;
    private Button mTroubleshootButton;
    private VideoCastManager mCastManager;
    private static final int MSG_SEEK = 0;
    private static final int ARG_FORWARD = 1;
    private static final int ARG_BACKWARD= -1;
    private ProgressBar mSecondaryLoading;
    private View mFullController;
    private int mPlaybackState;
    private int mIdleReason;
    private boolean mIsSeeking;
    private View mBlackVeil;
    private View mRootContainer;
    private View mShadow;

    public ArchosMiniPlayer(Context context, AttributeSet attrs) {
            super(context, attrs);
            LayoutInflater inflater = LayoutInflater.from(context);
            inflater.inflate(R.layout.archos_cast_mini_controller, this);
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.MiniController);
            mAutoSetup = a.getBoolean(R.styleable.MiniController_auto_setup, false);
        a.recycle();
            mPauseDrawable = getResources().getDrawable(R.drawable.video_pause);
            mPlayDrawable = getResources().getDrawable(R.drawable.video_play);;
            mStopDrawable = getResources().getDrawable(R.drawable.ic_mini_controller_stop);
            mHandler = new Handler();
            mArchosCastManager = ArchosVideoCastManager.getInstance();
            loadViews();
            setUpCallbacks();
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            if(mShadow!=null)
                mShadow.setVisibility(visibility);
            if(visibility==GONE)
                setFullVisible(false); //reset controllers
            if (visibility == View.VISIBLE) {
                mProgressBar.setProgress(0);
            }
        }

        /**
         * Sets the listener that should be notified when a relevant event is fired from this
         * component.
         * Clients can register the {@link VideoCastManager} instance to be the default listener so it
         * can control the remote media playback.
         */
        @Override
        public void setOnMiniControllerChangedListener(MiniController.OnMiniControllerChangedListener listener) {
            if (listener != null) {
                this.mListener = listener;
            }
        }

        /**
         * Removes the listener that was registered by
         * {@link #setOnMiniControllerChangedListener(MiniController.OnMiniControllerChangedListener)}
         */
        public void removeOnMiniControllerChangedListener(MiniController.OnMiniControllerChangedListener listener) {
            if ((listener != null) && (mListener == listener)) {
                mListener = null;
            }
        }

        @Override
        public void setStreamType(int streamType) {
            mStreamType = streamType;
        }

        @Override
        public void setProgress(final int progress, final int duration) {
            // for live streams, we do not attempt to update the progress bar
            if (mStreamType == MediaInfo.STREAM_TYPE_LIVE || mProgressBar == null) {
                return;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.setMax(duration);
                    mProgressBar.setProgress(progress);
                    updateSeekbar(progress, duration);
                }
            });
        }

        @Override
        public void setProgressVisibility(boolean visible) {
            if (mProgressBar == null) {
                return;
            }
            mProgressBar.setVisibility(
                    visible && (mStreamType != MediaInfo.STREAM_TYPE_LIVE) ? View.VISIBLE
                            : View.INVISIBLE);
        }

        @Override
        public void setUpcomingVisibility(boolean visible) {
            mUpcomingContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
            setProgressVisibility(!visible);
        }

        @Override
        public void setUpcomingItem(MediaQueueItem item) {
            mUpcomingItem = item;
            if (item != null) {
                MediaInfo mediaInfo = item.getMedia();
                if (mediaInfo != null) {
                    MediaMetadata metadata = mediaInfo.getMetadata();
                    setUpcomingTitle(metadata.getString(MediaMetadata.KEY_TITLE));
                    setUpcomingIcon(Utils.getImageUri(mediaInfo, 0));
                }
            } else {
                setUpcomingTitle("");
                setUpcomingIcon((Uri) null);
            }
        }

        @Override
        public void setCurrentVisibility(boolean visible) {       }

        private void setUpCallbacks() {

            mPlayPause.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        setLoadingVisibility(true);
                        try {
                            mListener.onPlayPauseClicked(v);
                        } catch (CastException e) {
                            mListener.onFailed(R.string.ccl_failed_perform_action,
                                    UNDEFINED_STATUS_CODE);
                        } catch (TransientNetworkDisconnectionException e) {
                            mListener.onFailed(R.string.ccl_failed_no_connection_trans,
                                    UNDEFINED_STATUS_CODE);
                        } catch (NoConnectionException e) {
                            mListener
                                    .onFailed(R.string.ccl_failed_no_connection, UNDEFINED_STATUS_CODE);
                        }
                    }
                }
            });

            mMainContainer.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {

                    setFullVisible(!isFullVisible());

                }
            });

            mUpcomingPlay.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        mListener.onUpcomingPlayClicked(v, mUpcomingItem);
                    }
                }
            });

            mUpcomingStop.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mListener != null) {
                        mListener.onUpcomingStopClicked(v, mUpcomingItem);
                    }
                }
            });
        }

        public ArchosMiniPlayer(Context context) {
            super(context);
            loadViews();
        }

        @Override
        public final void setIcon(Bitmap bm) {
            if(bm == null){
                mIcon.setImageResource(R.drawable.filetype_new_video);
            }
            else
                mIcon.setImageBitmap(bm);
        }

        private void setUpcomingIcon(Bitmap bm) {
            mUpcomingIcon.setImageBitmap(bm);
        }

        @Override
        public void setIcon(Uri uri) {
            if (mIconUri != null && mIconUri.equals(uri)) {
                return;
            }

            mIconUri = uri;
            Bitmap bitmap = BitmapFactory.decodeFile(mIconUri.getPath());
            setIcon(bitmap);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (mAutoSetup&&mArchosCastManager!=null) {
                mArchosCastManager.addMiniController(this);
                mArchosCastManager.addArchosCastManagerListener(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (mFetchBitmapTask != null) {
                mFetchBitmapTask.cancel(true);
                mFetchBitmapTask = null;
            }
            if (mAutoSetup) {
                mArchosCastManager.removeMiniController(this);
            }
        }

        @Override
        public void setTitle(String title) {
            mTitleText = title;
            mTitle.setText(title);
        }

        @Override
        public void setSubtitle(String subtitle) {
            /*mSubTitle.setText(subtitle);
                Disabled : text should stay the same, "touch view to expand"
             */
        }

        @Override
        public void setPlaybackStatus(int state, int idleReason) {
            mPlaybackState = state;
            mIdleReason = idleReason;
            mIcon.setVisibility(View.VISIBLE);
            //restore title
            if(state != ArchosMediaStatus.PLAYER_STATE_PREPARING&&mTitleText!=null){
                mTitle.setText(mTitleText);
            }
            switch (state) {
                case MediaStatus.PLAYER_STATE_PLAYING:
                    if(!isFullVisible())
                        mPlayPause.setVisibility(View.VISIBLE);
                    mPlayPause.setImageDrawable(getPauseStopDrawable());
                    mSecondaryPlayPause.setImageDrawable(getPauseStopDrawable());
                    setLoadingVisibility(false);
                    break;
                case MediaStatus.PLAYER_STATE_PAUSED:
                    if(!isFullVisible())
                        mPlayPause.setVisibility(View.VISIBLE);
                    mPlayPause.setImageDrawable(mPlayDrawable);
                    mSecondaryPlayPause.setImageDrawable(mPlayDrawable);
                    setLoadingVisibility(false);
                    break;
                case MediaStatus.PLAYER_STATE_IDLE:
                    switch (mStreamType) {
                        case MediaInfo.STREAM_TYPE_BUFFERED:
                            mPlayPause.setVisibility(View.INVISIBLE);
                            setLoadingVisibility(false);
                            break;
                        case MediaInfo.STREAM_TYPE_LIVE:
                            if (idleReason == MediaStatus.IDLE_REASON_CANCELED) {
                                if(!isFullVisible())
                                    mPlayPause.setVisibility(View.VISIBLE);
                                mPlayPause.setImageDrawable(mPlayDrawable);
                                mSecondaryPlayPause.setImageDrawable(mPlayDrawable);
                                setLoadingVisibility(false);
                            } else {
                                mPlayPause.setVisibility(View.INVISIBLE);
                                setLoadingVisibility(false);
                            }
                            break;
                    }
                    break;
                case MediaStatus.PLAYER_STATE_BUFFERING:
                    mPlayPause.setVisibility(View.INVISIBLE);
                    setLoadingVisibility(true);
                    break;
                case ArchosMediaStatus.PLAYER_STATE_PREPARING:
                case ArchosMediaStatus.PLAYER_STATE_SWITCHING:
                    mIcon.setVisibility(View.INVISIBLE);
                    mTitle.setText(R.string.loading);
                    mPlayPause.setVisibility(View.INVISIBLE);
                    setLoadingVisibility(true);
                    break;
                default:
                    mPlayPause.setVisibility(View.INVISIBLE);
                    setLoadingVisibility(false);
                    break;
            }
            updateRatioButton();
        }

        @Override
        public boolean isVisible() {
            return isShown();
        }

        private void loadViews() {
            mIcon = (ImageView) findViewById(R.id.icon_view);
            mTitle = (TextView) findViewById(R.id.title_view);
            mSubTitle = (TextView) findViewById(R.id.subtitle_view);
            mPlayPause = (ImageView) findViewById(R.id.play_pause);
            mLoading = (ProgressBar) findViewById(R.id.loading_view);
            mMainContainer = findViewById(R.id.container_current);
            mRootContainer = findViewById(R.id.container_all);
            mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
            mUpcomingIcon = (ImageView) findViewById(R.id.icon_view_upcoming);
            mUpcomingTitle = (TextView) findViewById(R.id.title_view_upcoming);
            mUpcomingContainer = findViewById(R.id.container_upcoming);
            mUpcomingPlay = findViewById(R.id.play_upcoming);
            mUpcomingStop = findViewById(R.id.stop_upcoming);
            loadAndSetupViews();
        }

        private void setLoadingVisibility(boolean show) {
            if(!isFullVisible())
                mLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            mSecondaryLoading.setVisibility(show ? View.VISIBLE : View.GONE);
            mPlaybackControls.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
            mSeekbar.setEnabled(!show);

        }

        private Drawable getPauseStopDrawable() {
            switch (mStreamType) {
                case MediaInfo.STREAM_TYPE_BUFFERED:
                    return mPauseDrawable;
                case MediaInfo.STREAM_TYPE_LIVE:
                    return mStopDrawable;
                default:
                    return mPauseDrawable;
            }
        }

        private void setUpcomingIcon(Uri uri) {
            if (mUpcomingIconUri != null && mUpcomingIconUri.equals(uri)) {
                return;
            }

            mUpcomingIconUri = uri;
            if (mFetchUpcomingBitmapTask != null) {
                mFetchUpcomingBitmapTask.cancel(true);
            }
            mFetchUpcomingBitmapTask = new FetchBitmapTask() {
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    if (bitmap == null) {
                        bitmap = BitmapFactory.decodeResource(getResources(),
                                R.drawable.album_art_placeholder);
                    }
                    setUpcomingIcon(bitmap);
                    if (this == mFetchUpcomingBitmapTask) {
                        mFetchUpcomingBitmapTask = null;
                    }
                }
            };

            mFetchUpcomingBitmapTask.execute(uri);
        }

        private void setUpcomingTitle(String title) {
            mUpcomingTitle.setText(title);
        }

    private boolean isFullVisible(){
        return mFullController.getVisibility()==View.VISIBLE;
    }

    private void setFullVisible(boolean visible){
        if(visible){
            mFullController.setVisibility(View.VISIBLE);
            mPlayPause.setVisibility(GONE);
            mLoading.setVisibility(GONE);
            mSubTitle.setVisibility(GONE);
            mProgressBar.setVisibility(View.INVISIBLE);
            if(mBlackVeil!=null) {
                mBlackVeil.setVisibility(View.VISIBLE);
            }
        }
        else {
            mFullController.setVisibility(GONE);
            mSubTitle.setVisibility(VISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
            if(mBlackVeil!=null)
                mBlackVeil.setVisibility(View.GONE);
        }
        setPlaybackStatus(mPlaybackState, mIdleReason); //refresh
    }

    public void setPlaybackStatus(int state) {
       /* switch (state) {
            case MediaStatus.PLAYER_STATE_PLAYING:
                mLoading.setVisibility(View.INVISIBLE);
                mPlaybackControls.setVisibility(View.VISIBLE);
                if (mStreamType == MediaInfo.STREAM_TYPE_LIVE) {
                    mPlayPause.setImageDrawable(mStopDrawable);
                } else {
                    mPlayPause.setImageDrawable(mPauseDrawable);
                }

                break;
            case MediaStatus.PLAYER_STATE_PAUSED:
                mLoading.setVisibility(View.INVISIBLE);
                mPlaybackControls.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(mPlayDrawable);
                break;
            case MediaStatus.PLAYER_STATE_IDLE:
                if (mStreamType == MediaInfo.STREAM_TYPE_LIVE) {
                    mLoading.setVisibility(View.INVISIBLE);
                    mPlaybackControls.setVisibility(View.VISIBLE);
                    mPlayPause.setImageDrawable(mPlayDrawable);
                } else {
                    mPlaybackControls.setVisibility(View.INVISIBLE);
                    mLoading.setVisibility(View.VISIBLE);
                }
                break;
            case MediaStatus.PLAYER_STATE_BUFFERING:
                mPlaybackControls.setVisibility(View.INVISIBLE);
                mLoading.setVisibility(View.VISIBLE);
                break;
            default:
        }

        */
    }

    private void updateRatioButton() {
        if(ArchosVideoCastManager.getInstance().isRemoteDisplayConnected()&&CastPlayerService.sCastPlayerService.getSurfaceManager()!=null){
            mRatioButton.setVisibility(View.VISIBLE);
            switch (CastPlayerService.sCastPlayerService.getSurfaceManager().getNextVideoFormat()) {
                case SurfaceController.VideoFormat.ORIGINAL:
                    mRatioButton.setImageResource(R.drawable.video_format_original_selector);
                    break;
                case SurfaceController.VideoFormat.FULLSCREEN:
                    mRatioButton.setImageResource(R.drawable.video_format_fullscreen_selector);
                    break;
                case SurfaceController.VideoFormat.AUTO:
                    mRatioButton.setImageResource(R.drawable.video_format_auto_selector);
                    break;
            }
        }
        else
            mRatioButton.setVisibility(View.GONE);

    }

    public void updateSeekbar(int position, int duration) {
        if(mIsSeeking)
            return;
        mSeekbar.setProgress(position);
        mSeekbar.setMax(duration);
        mStart.setText(Utils.formatMillis(position));
        mEnd.setText(Utils.formatMillis(duration));
    }


    private void loadAndSetupViews() {
        mSecondaryPlayPause = (ImageButton) findViewById(R.id.play_pause_toggle);
        mSecondaryLoading = (ProgressBar) findViewById(R.id.secondary_progressbar);
        mFullController = findViewById(R.id.full_controller);
        mLiveText = (TextView) findViewById(R.id.live_text);
        mStart = (TextView) findViewById(R.id.start_text);
        mEnd = (TextView) findViewById(R.id.end_text);
        mSeekbar = (SeekBar) findViewById(R.id.seekbar);
        mRatioButton = (ImageView) findViewById(R.id.ratio_button);
        mRatioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ArchosVideoCastManager.getInstance().isRemoteDisplayConnected()){
                    CastPlayerService.sCastPlayerService.getSurfaceManager().switchVideoFormat();
                    updateRatioButton();
                }
            }
        });
        mClosedCaptionIcon = (ImageButton) findViewById(R.id.cc);
        mSkipNext = (ImageButton) findViewById(R.id.next);
        mSkipPrevious = (ImageButton) findViewById(R.id.previous);
        mPlaybackControls = findViewById(R.id.playback_controls);
        mSwitchButton = (Button) findViewById(R.id.switch_mode);
        mTroubleshootButton = (Button) findViewById(R.id.troubleshoot_button);
        ((ArchosMiniPlayer) findViewById(R.id.miniController1)).setCurrentVisibility(false);
       // setClosedCaptionState(CC_DISABLED);
/*
        mSwitchButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if(b)
                    mArchosCastManager.switchToDisplayCast();
                else
                    mArchosCastManager.switchToVideoCast();
            }
        });*/
        mSwitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ModeSwitchDialog(getContext()).show();
            }
        });

        mTroubleshootButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(getContext()).setMessage(mArchosCastManager.isRemoteDisplayConnected()?R.string.troubleshoot_remotemode_msg:R.string.troubleshoot_streammode_msg).setPositiveButton(android.R.string.ok, null).show();
            }
        });

        mSecondaryPlayPause.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    mListener.onPlayPauseClicked(v);
                } catch (TransientNetworkDisconnectionException e) {
                    Utils.showToast(getContext(),
                            R.string.ccl_failed_no_connection_trans);
                } catch (NoConnectionException e) {
                    Utils.showToast(getContext(),
                            R.string.ccl_failed_no_connection);
                } catch (Exception e) {
                    Utils.showToast(getContext(),
                            R.string.ccl_failed_perform_action);
                }
            }
        });

        mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsSeeking = false;
                try {
                    if (mListener != null) {
                        if (mArchosCastManager.isRemoteMediaPlaying()) {
                            mArchosCastManager.play(seekBar.getProgress());
                        } else if (mArchosCastManager.isRemoteMediaPaused()) {
                            mArchosCastManager.seek(seekBar.getProgress());
                        }
                        //restartTrickplayTimer();
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeeking = true;
               /* try {
                    stopTrickplayTimer();
                } catch (Exception e) {

                }*/
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                mStart.setText(Utils.formatMillis(progress));
            }
        });

        mClosedCaptionIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    showTracksChooserDialog();
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                }
            }
        });

        mSkipNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeMessages(MSG_SEEK);
                try {
                    mArchosCastManager.seekNext();
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                }
            }
        });
        mSkipNext.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                mHandler.removeMessages(MSG_SEEK);
                try {
                    mArchosCastManager.seekNext();
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                }

                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SEEK, ARG_FORWARD, 0), PlayerController.SEEK_LONG_INIT_DELAY );
                return true;
            }


        });

        mSkipPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeMessages(MSG_SEEK);
                try {

                    mArchosCastManager.seekPrev();
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                }
            }
        });
        mSkipPrevious.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                mHandler.removeMessages(MSG_SEEK);
                try {
                    mArchosCastManager.seekPrev();
                } catch (TransientNetworkDisconnectionException | NoConnectionException e) {
                }
                mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SEEK, ARG_BACKWARD, 0),PlayerController.SEEK_LONG_INIT_DELAY );
                return true;
            }


        });
        //<archos change>
        mArchosCastManager = ArchosVideoCastManager.getInstance();
        //switchCastMode();
        //<!archos change>
        mCastManager = VideoCastManager.getInstance();
        //<archos change>
        //updateControllers();
        //<!archos change>

    }

    private void showTracksChooserDialog()
            throws TransientNetworkDisconnectionException, NoConnectionException {
        if(!(getContext() instanceof FragmentActivity))
            throw new IllegalStateException("Activity needs to be FragmentActivity");
        FragmentTransaction transaction = ((FragmentActivity)getContext()).getSupportFragmentManager().beginTransaction();
        Fragment prev = ((FragmentActivity)getContext()).getSupportFragmentManager().findFragmentByTag(DIALOG_TAG);
        if (prev != null) {
            transaction.remove(prev);
        }
        transaction.addToBackStack(null);

        // Create and show the dialog.

        /* <--archos changes> */
        ArchosTracksChooserDialog dialogFragment = ArchosTracksChooserDialog
                .newInstance(ArchosVideoCastManager.getInstance().getMediaInfo());
        /* <!--archos changes> */
        dialogFragment.show(transaction, DIALOG_TAG);
    }


    @Override
    public void updateUI() {
        setPlaybackStatus(mArchosCastManager.getPlaybackStatus());
    }

    @Override
    public void switchCastMode() {

    }

    public void setBackgroundColor(int color){
        mRootContainer.setBackgroundColor(color);
    }

    /**
     * set view that wil be shown when full controller appears,
     * will set onClickListener to hide full controller when pressed
     * @param veil
     */
    public void setBlackVeil(View veil){
        mBlackVeil = veil;
        if(mBlackVeil !=null) {
            mBlackVeil.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    setFullVisible(false);
                }
            });
            if(isFullVisible())
                mBlackVeil.setVisibility(VISIBLE);
            else
                mBlackVeil.setVisibility(GONE);
        }
    }

    public void setShadow(View shadow){
        mShadow = shadow;
        shadow.setVisibility(getVisibility());
    }
}
