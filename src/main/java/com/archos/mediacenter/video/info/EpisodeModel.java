package com.archos.mediacenter.video.info;

import android.net.Uri;

public class EpisodeModel {
    private int mNumber;
    private String mPath;
    private Uri mEpisodePicture;
    private String mEpisodeName;
    private String mEpisodeFilePath;
    private String mEpisodeContentRating;
    private String mEpisodePlot;
    private long mId; // Add an ID field
    private long mOnlineId;
    private int mSeasonNumber;
    private long mEpisodeDate;
    private float mEpisodeRating;

    public EpisodeModel() {}

    public int getEpisodeNumber() {
        return mNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.mNumber = episodeNumber;
    }

    public String getEpisodePath() {
        return mPath;
    }

    public void setEpisodePath(String episodePath) {
        this.mPath = episodePath;
    }

    public long getId() {  // Getter for ID
        return mId;
    }

    public void setId(long id) {  // Setter for ID
        this.mId = id;
    }

    public String getEpisodeFilePath() {  // Getter for ID
        return mEpisodeFilePath;
    }

    public void setEpisodeFilePath(String mepisodeeilepath) {  // Setter for ID
        this.mEpisodeFilePath = mepisodeeilepath;
    }

    public long getOnlineId() {  // Getter for ID
        return mOnlineId;
    }

    public void setOnlineId(long onlineid) {  // Setter for ID
        this.mOnlineId = onlineid;
    }

    public int getSeasonNumber() {
        return mSeasonNumber;
    }

    public void setSeasonNumber(int seasonNumber) {
        this.mSeasonNumber = seasonNumber;
    }


    public Uri getPictureUri() {
        return mEpisodePicture;
    }

    public void setPictureUri(Uri pictureUri) {
        this.mEpisodePicture = pictureUri;
    }

    public String getEpisodeName() {
        return mEpisodeName;
    }

    public void setEpisodeName(String episodeName) {
        this.mEpisodeName = episodeName;
    }

    public long getEpisodeDate() {  // Getter for ID
        return mEpisodeDate;
    }

    public void setEpisodeDate(long episodeDate) {  // Setter for ID
        this.mEpisodeDate = episodeDate;
    }

    public Float getEpisodeRating() {  // Getter for ID
        return mEpisodeRating;
    }

    public void setEpisodeRating(float episodeRating) {  // Setter for ID
        this.mEpisodeRating = episodeRating;
    }

    public String getEpisodeContentRating() {
        return mEpisodeContentRating;
    }

    public void setEpisodeContentRating(String episodeContentRating) {
        this.mEpisodeContentRating = episodeContentRating;
    }

    public String getEpisodePlot() {
        return mEpisodePlot;
    }

    public void setEpisodePlot(String episodePlot) {
        this.mEpisodePlot = episodePlot;
    }

}