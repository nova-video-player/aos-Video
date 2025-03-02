package com.archos.mediacenter.video.info;

import androidx.lifecycle.ViewModel;
import java.util.List;

public class EpisodeViewModel extends ViewModel {
    private List<EpisodeModel> episodeModels;
    private int currentPosition = 0;

    public List<EpisodeModel> getEpisodeModels() {
        return episodeModels;
    }

    public void setEpisodeModels(List<EpisodeModel> episodes) {
        this.episodeModels = episodes;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int position) {
        this.currentPosition = position;
    }
}
