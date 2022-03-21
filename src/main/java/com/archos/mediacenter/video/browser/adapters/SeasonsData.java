package com.archos.mediacenter.video.browser.adapters;

public class SeasonsData {
    String seasonNumber,seasonPlot, seasonName, airdate;

    public SeasonsData() {
    }

    public String getSeasonPlot() {
        return seasonPlot;
    }

    public void setSeasonPlot(String seasonPlot) {
        this.seasonPlot = seasonPlot;
    }

    public String getSeasonName() {
        return seasonName;
    }

    public void setSeasonName(String seasonName) {
        this.seasonName = seasonName;
    }

    public String getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(String seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getSeasonAirdate() {
        return airdate;
    }

    public void setSeasonAirdate(String airdate) {
        this.airdate = airdate;
    }
}