package com.archos.mediacenter.video.info;

public class EpisodeModel {
    private int mNumber;
    private String mPath;

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

}