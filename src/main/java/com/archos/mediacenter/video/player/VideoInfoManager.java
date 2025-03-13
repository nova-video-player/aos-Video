package com.archos.mediacenter.video.player;

public class VideoInfoManager {
    private static VideoInfoManager instance;
    private String mTitle;
    private int mVideoDefinition;
    private String mContentRating;
    private String mMovieYear;
    private String FinalEpisodeAirDate;
    private String mRating;
    private String mPosterPath;

    private VideoInfoManager() { }

    public static VideoInfoManager getInstance() {
        if (instance == null) {
            instance = new VideoInfoManager();
        }
        return instance;
    }

    public String getVideoTitle() {
        return mTitle;
    }

    public void setVideoTitle(String title) {
        this.mTitle = title;
    }

    public int getVideoDefinition() {
        return mVideoDefinition;
    }

    public void setVideoDefinition(int definition) {
        this.mVideoDefinition = definition;
    }

    public String getContentRating() {
        return mContentRating;
    }

    public void setContentRating(String contentRating) {
        this.mContentRating = contentRating;
    }

    public String getMovieYear() {
        return mMovieYear;
    }

    public void setMovieYear(String movieYear) {
        this.mMovieYear = movieYear;
    }

    public String getFinalEpisodeAirDate() {
        return FinalEpisodeAirDate;
    }

    public void setFinalEpisodeAirDate(String finalEpisodeAirDate) {
        this.FinalEpisodeAirDate = finalEpisodeAirDate;
    }

    public String getRating() {
        return mRating;
    }

    public void setRating(String rating) {
        this.mRating = rating;
    }

    public String getPosterPath() {
        return mPosterPath;
    }

    public void setPosterPath(String posterPath) {
        this.mPosterPath = posterPath;
    }
}
