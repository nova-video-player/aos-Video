package com.archos.mediacenter.video.leanback;

public final class LoaderIds {

    private LoaderIds() {
    }

    // TODO MARC change id for LoaderManager getId rows.add mAdaptersMap.append
    // TODO MARC check loadRows --> ??? how add offset to loadRows

    // TODO MARC check in tvdb and novadb the genres id shift to verify compatibility with 500 offset

    // TODO MARC: git add src/main/java/com/archos/mediacenter/video/leanback/LoaderIds.java
    // TODO MARC search for 'id == -1' 'id != -1' in Video/src/main/java/com/archos/mediacenter/video/leanback
    // TODO MARC ag "id != -1"
    // in onCreateLoader id == -1
    // in onLoadFinished
    // cursorLoader.getId() == -1
    // rows.add(new ListRow(subsetId,
    // mAdaptersMap.append(subsetId,
    // .initLoader(-1,
    // if (id == 0) {
    // if (cursorLoader.getId()==0) {
    // if (cursorLoader.getId() == -1) {
    // .restartLoader(  TODO MARC TvshowFragment
    // .initLoader(
    // LoaderManager.getInstance(
    // if (id != -1)
    // if (id != 0)

    public final static int AllAnimeShowsGridLoaderId = 4000;
    public final static int TvshowsByLoaderId = 8000;
    public final static int AllTvshowsGridLoaderId = -77;
    public final static int EpisodesByDateLoaderId = 3000; // TODO MARC check the onLoadFinished else clause
    public final static int TvshowLoaderId = -42; // TODO MARC not finished
    public final static int SeasonLoaderId = -88;
    public final static int AnimesByLoaderId = 7000; // subsetId offset 500
    public final static int AllAnimesGridLoaderId= -66;
    public final static int VideoDetailsLoaderId = 2000;
    public final static int VideosByLoaderId = 6000; // subsetId offset 500
    public final static int ListingLoaderId = -46;
    public final static int AllMoviesGridLoaderId = -45;
    public final static int MoviesByLoaderId = 5000; // subsetId offset 500 // TODO MARC check
    public final static int NonScrapedVideosLoaderId = -666;
    public final static int CollectionLoaderId = -43;
    public final static int AllAnimeCollectionsGridLoaderId = -55;
    public final static int AllCollectionsGridLoaderId = -44;
    public final static int MainFragmentWatchingUpNextLoaderId = 47;
    public final static int MainFragmentLastAddedLoaderId = 42;
    public final static int MainFragmentLastPlayerLoaderId = 43;
    public final static int MainFragmentAllMoviesLoaderId = 46;
    public final static int MainFragmentAllTvShowsLoaderId = 44;
    public final static int MainFragmentNonScrapedVideosCountLoaderId = 45;
    public final static int MainFragmentAllAnimesLoaderId = 48;

}
