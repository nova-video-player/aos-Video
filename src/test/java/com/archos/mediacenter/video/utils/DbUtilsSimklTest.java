// Copyright 2026 Nova Video Player Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.utils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.mediacenter.utils.simkl.Simkl;
import com.archos.mediacenter.utils.simkl.SimklSyncQueue;
import com.archos.mediacenter.video.browser.adapters.object.Collection;
import com.archos.mediacenter.video.browser.adapters.object.Movie;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, sdk = 33)
public class DbUtilsSimklTest {

    private Application app;
    private SharedPreferences prefs;
    private MockWebServer server;
    private ContentResolver mockResolver;
    private Context testContext;

    @Before
    public void setUp() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        prefs = PreferenceManager.getDefaultSharedPreferences(app);
        Simkl.wipe(prefs);
        SimklSyncQueue.getInstance(app).clear();

        server = new MockWebServer();
        server.start();
        Simkl.setApiBaseUrlForTests(server.url("/").toString());

        Simkl.AccessToken token = new Simkl.AccessToken();
        token.access_token = "access-1";
        token.refresh_token = "refresh-1";
        Simkl.completeSignIn(app, prefs, token);
        Simkl.setUser(prefs, "alice");

        mockResolver = mock(ContentResolver.class);
        when(mockResolver.update(any(Uri.class), any(ContentValues.class), any(), any())).thenReturn(1);
        testContext = new ContextWrapper(app) {
            @Override
            public ContentResolver getContentResolver() {
                return mockResolver;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        Simkl.awaitPendingTasksForTests();
        Simkl.setApiBaseUrlForTests(null);
        Simkl.wipe(prefs);
        SimklSyncQueue.getInstance(app).clear();
        server.shutdown();
    }

    @Test
    public void markAsNotReadUpdatesLocalDbWithoutCallingSimklRemoveEndpoint() throws Exception {
        Movie movie = mock(Movie.class);
        when(movie.isIndexed()).thenReturn(true);
        when(movie.getId()).thenReturn(42L);
        when(movie.getOnlineId()).thenReturn(808L);
        when(movie.getFilePath()).thenReturn("/movies/watchlist_movie.mkv");

        DbUtils.markAsNotRead(testContext, movie);
        Simkl.awaitPendingTasksForTests();

        verify(mockResolver).update(any(Uri.class), any(ContentValues.class), any(), any());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void syncSimklWithWatchedFalseDoesNotCallSimklRemoveEndpoint() throws Exception {
        Movie movie = mock(Movie.class);
        when(movie.getOnlineId()).thenReturn(808L);

        DbUtils.syncSimkl(testContext, movie, false);
        Simkl.awaitPendingTasksForTests();

        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void markAsReadCallsSimklSyncHistory() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("{\"added\":{\"movies\":1,\"shows\":0,\"episodes\":0}}"));

        Movie movie = mock(Movie.class);
        when(movie.isIndexed()).thenReturn(true);
        when(movie.getId()).thenReturn(42L);
        when(movie.getOnlineId()).thenReturn(808L);
        when(movie.getFilePath()).thenReturn("/movies/watchlist_movie.mkv");

        DbUtils.markAsRead(testContext, movie);
        Simkl.awaitPendingTasksForTests();

        assertEquals(1, server.getRequestCount());
        RecordedRequest req = server.takeRequest();
        assertEquals("/sync/history", req.getPath());
        JSONObject body = new JSONObject(req.getBody().readUtf8());
        assertEquals(808L, body.getJSONArray("movies").getJSONObject(0).getJSONObject("ids").getLong("tmdb"));
    }

    @Test
    public void markAsNotReadCollectionUpdatesLocalDbWithoutCallingSimklRemoveEndpoint() throws Exception {
        Collection collection = new Collection(7L, "Watchlist Collection", null, null, 2, 2, 2);

        DbUtils.markAsNotRead(testContext, collection);
        DbUtils.syncSimkl(testContext, collection, false);
        Simkl.awaitPendingTasksForTests();

        verify(mockResolver).update(any(Uri.class), any(ContentValues.class), any(), any());
        assertEquals(0, server.getRequestCount());
    }
}
