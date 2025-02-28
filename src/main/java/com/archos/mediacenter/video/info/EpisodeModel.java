package com.archos.mediacenter.video.info;

import android.net.Uri;

public class EpisodeModel {
    private long mId;
    private long mEpisodeId;
    private int mSeasonNumber;
    private int mNumber;
    private String mEpisodeName;
    private long mEpisodeDate;
    private float mEpisodeRating;
    private String mEpisodeContentRating;
    private String mEpisodePlot;
    private String mShowName;
    private String mEpisodeFilePath;
    private Uri mEpisodePicture;
    private Uri mPosterUri;
    private int mDuration;
    private int mResume;
    private int mVideo3dMode;
    private int mGuessedDefinition;
    private boolean mTraktSeen;
    private boolean mIsTraktLibrary;
    private boolean mHasSubs;
    private boolean mIsUserHidden;
    private long mOnlineId;
    private long mLastTimePlayed;
    private int mCalculatedWidth;
    private int mCalculatedHeight;
    private String mBestAudioFormat;
    private String mVideoFormat;
    private String mGuessedAudioFormat;
    private String mGuessedVideoFormat;
    private int mCalculatedBestAudioTrack;
    private int mOccurrences;
    private long mSize;
    private String mPath;

    public EpisodeModel() {}

    // Getters and Setters
    public long getId() { return mId; }
    public void setId(long id) { this.mId = id; }

    public long getEpisodeId() { return mEpisodeId; }
    public void setEpisodeId(long episodeId) { this.mEpisodeId = episodeId; }

    public int getSeasonNumber() { return mSeasonNumber; }
    public void setSeasonNumber(int seasonNumber) { this.mSeasonNumber = seasonNumber; }

    public int getEpisodeNumber() { return mNumber; }
    public void setEpisodeNumber(int number) { this.mNumber = number; }

    public String getEpisodeName() { return mEpisodeName; }
    public void setEpisodeName(String episodeName) { this.mEpisodeName = episodeName; }

    public long getEpisodeDate() { return mEpisodeDate; }
    public void setEpisodeDate(long episodeDate) { this.mEpisodeDate = episodeDate; }

    public float getEpisodeRating() { return mEpisodeRating; }
    public void setEpisodeRating(float episodeRating) { this.mEpisodeRating = episodeRating; }

    public String getEpisodeContentRating() { return mEpisodeContentRating; }
    public void setEpisodeContentRating(String episodeContentRating) { this.mEpisodeContentRating = episodeContentRating; }

    public String getEpisodePlot() { return mEpisodePlot; }
    public void setEpisodePlot(String episodePlot) { this.mEpisodePlot = episodePlot; }

    public String getShowName() { return mShowName; }
    public void setShowName(String showName) { this.mShowName = showName; }

    public String getEpisodeFilePath() { return mEpisodeFilePath; }
    public void setEpisodeFilePath(String episodeFilePath) { this.mEpisodeFilePath = episodeFilePath; }

    public Uri getPictureUri() { return mEpisodePicture; }
    public void setPictureUri(Uri pictureUri) { this.mEpisodePicture = pictureUri; }

    public Uri getPosterUri() { return mPosterUri; }
    public void setPosterUri(Uri posterUri) { this.mPosterUri = posterUri; }

    public int getDuration() { return mDuration; }
    public void setDuration(int duration) { this.mDuration = duration; }

    public int getResume() { return mResume; }
    public void setResume(int resume) { this.mResume = resume; }

    public int getVideo3dMode() { return mVideo3dMode; }
    public void setVideo3dMode(int video3dMode) { this.mVideo3dMode = video3dMode; }

    public int getGuessedDefinition() { return mGuessedDefinition; }
    public void setGuessedDefinition(int guessedDefinition) { this.mGuessedDefinition = guessedDefinition; }

    public boolean isTraktSeen() { return mTraktSeen; }
    public void setTraktSeen(boolean traktSeen) { this.mTraktSeen = traktSeen; }

    public boolean isTraktLibrary() { return mIsTraktLibrary; }
    public void setTraktLibrary(boolean isTraktLibrary) { this.mIsTraktLibrary = isTraktLibrary; }

    public boolean hasSubs() { return mHasSubs; }
    public void setHasSubs(boolean hasSubs) { this.mHasSubs = hasSubs; }

    public boolean isUserHidden() { return mIsUserHidden; }
    public void setUserHidden(boolean isUserHidden) { this.mIsUserHidden = isUserHidden; }

    public long getOnlineId() { return mOnlineId; }
    public void setOnlineId(long onlineId) { this.mOnlineId = onlineId; }

    public long getLastTimePlayed() { return mLastTimePlayed; }
    public void setLastTimePlayed(long lastTimePlayed) { this.mLastTimePlayed = lastTimePlayed; }

    public int getCalculatedWidth() { return mCalculatedWidth; }
    public void setCalculatedWidth(int calculatedWidth) { this.mCalculatedWidth = calculatedWidth; }

    public int getCalculatedHeight() { return mCalculatedHeight; }
    public void setCalculatedHeight(int calculatedHeight) { this.mCalculatedHeight = calculatedHeight; }

    public String getBestAudioFormat() { return mBestAudioFormat; }
    public void setBestAudioFormat(String bestAudioFormat) { this.mBestAudioFormat = bestAudioFormat; }

    public String getVideoFormat() { return mVideoFormat; }
    public void setVideoFormat(String videoFormat) { this.mVideoFormat = videoFormat; }

    public String getGuessedAudioFormat() { return mGuessedAudioFormat; }
    public void setGuessedAudioFormat(String guessedAudioFormat) { this.mGuessedAudioFormat = guessedAudioFormat; }

    public String getGuessedVideoFormat() { return mGuessedVideoFormat; }
    public void setGuessedVideoFormat(String guessedVideoFormat) { this.mGuessedVideoFormat = guessedVideoFormat; }

    public int getCalculatedBestAudioTrack() { return mCalculatedBestAudioTrack; }
    public void setCalculatedBestAudioTrack(int calculatedBestAudioTrack) { this.mCalculatedBestAudioTrack = calculatedBestAudioTrack; }

    public int getOccurrences() { return mOccurrences; }
    public void setOccurrences(int occurrences) { this.mOccurrences = occurrences; }

    public long getSize() { return mSize; }
    public void setSize(long size) { this.mSize = size; }

    public String getEpisodePath() { return mPath; }
    public void setEpisodePath(String episodePath) { this.mPath = episodePath; }
}
