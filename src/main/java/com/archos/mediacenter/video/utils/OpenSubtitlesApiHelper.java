// Copyright 2023 Courville Software
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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.util.ArrayList;

public class OpenSubtitlesApiHelper {

    private static final Logger log = LoggerFactory.getLogger(OpenSubtitlesApiHelper.class);

    private static volatile OpenSubtitlesApiHelper sInstance;

    private static final String API_BASE_URL = "https://api.opensubtitles.com/api/v1";
    private static final String USER_AGENT = "User-Agent";
    private static final String USER_AGENT_VALUE = "nova video player";
    private static final String AUTHORIZATION = "Authorization";
    private static final String API_KEY = "Api-Key";
    public static final int RESULT_CODE_OK = 0;
    public static final int RESULT_CODE_BAD_CREDENTIALS = 1;
    public static final int RESULT_CODE_TOKEN_EXPIRED = 2;
    public static final int RESULT_CODE_QUOTA_EXCEEDED = 3;
    public static final int RESULT_CODE_BAD_API_KEY = 4;
    public static final int RESULT_CODE_INVALID_FILE_ID = 5;
    public static final int RESULT_CODE_LINK_GONE = 6;
    public static final int RESULT_CODE_TOO_MANY_REQUESTS = 7;

    private static int LAST_QUERY_RESULT = RESULT_CODE_OK;

    private static OkHttpClient httpClient;
    private static String baseUrl;
    private static String apiKey;

    private static String username = null;
    private static String password = null;
    private static String authToken = null;
    private static int allowedDownloads = 0;
    private static String level = "";
    private static int remaningDownloads = 10;
    private static boolean vip = false;

    private static boolean authTokenValid = false;
    private static boolean authenticated = false;

    public OpenSubtitlesApiHelper() {
        if (log.isTraceEnabled()) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            httpClient = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();
        } else {
            httpClient = new OkHttpClient();
        }
        setBaseUrl(API_BASE_URL);
    }

    // get the instance
    public static OpenSubtitlesApiHelper getInstance() {
        if (sInstance == null) {
            synchronized(OpenSubtitlesApiHelper.class) {
                if (sInstance == null) sInstance = new OpenSubtitlesApiHelper();
            }
        }
        return sInstance;
    }

    public static void setAuthToken(String token) {
        authToken = token;
        authTokenValid = true;
    }

    public static void setBaseUrl(String url) {
        log.debug("setBaseUrl: " + url);
        baseUrl = url;
    }

    public static int getLastQueryResult() {
        return LAST_QUERY_RESULT;
    }

    public static int getAllowedDownloads() {
        return allowedDownloads;
    }

    public static boolean isVip() {
        return vip;
    }

    public static int getRemaningDownloads() {
        return remaningDownloads;
    }

    public static boolean login(String openSubtitlesApiKey, String u, String p) throws IOException {
        username = u;
        password = p;
        apiKey = openSubtitlesApiKey;
        if (u != null && u.isEmpty()) {
            log.warn("auth: no username provided, using anonymous mode");
            authenticated = false;
            return false;
        }
        try {
            JSONObject authRequest = new JSONObject();
            authRequest.put("username", username);
            authRequest.put("password", password);
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), authRequest.toString());
            Request request = new Request.Builder()
                    .url(baseUrl + "login")
                    .post(requestBody)
                    .addHeader(USER_AGENT, USER_AGENT_VALUE)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    parseResult(jsonResponse);
                    if (jsonResponse.has("error")) {
                        // Handle authentication error
                        return false;
                    } else if (jsonResponse.has("data")) {
                        // Authentication successful
                        JSONObject dataObject = jsonResponse.getJSONObject("data");
                        String authToken = dataObject.getString("token");
                        log.debug("auth: token = " + authToken);
                        // Check if "base_url" is present in the response
                        if (dataObject.has("base_url")) {
                            String baseUrl = dataObject.getString("base_url");
                            // Store the base URL for future requests
                            log.debug("auth: base_url = " + baseUrl);
                            setBaseUrl(baseUrl);
                        }
                        // Check if "user" object is present in the response
                        if (dataObject.has("user")) {
                            JSONObject userObject = dataObject.getJSONObject("user");
                            allowedDownloads = userObject.getInt("allowed_downloads");
                            level = userObject.getString("level");
                            vip = userObject.getBoolean("vip");
                            log.debug("auth: allowed_downloads={}, level={}, vip={}", allowedDownloads, level, vip);
                        }

                        if (authToken != null) {
                            setAuthToken(authToken);
                            authenticated = true;
                            return true;
                        }
                    }
                }
            }
        } catch (JSONException e) {
            log.error("auth: caught JSONException", e);
        }
        authTokenValid = false;
        return false;
    }

    public static void logout() throws IOException {
        if (authenticated) {
            try {
                JSONObject authRequest = new JSONObject();
                authRequest.put(AUTHORIZATION, "Bearer " + authToken);
                RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), authRequest.toString());
                Request request = new Request.Builder()
                        .url(baseUrl + "logout")
                        .post(requestBody)
                        .addHeader(USER_AGENT, USER_AGENT_VALUE)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        parseResult(jsonResponse);
                        if (jsonResponse.has("error")) {
                            log.warn("logout: error in response, code=" + LAST_QUERY_RESULT);
                        }
                    }
                }
            } catch (JSONException e) {
                log.error("auth: caught JSONException", e);
            }
        }
        authTokenValid = false;
        authenticated = false;
        authToken = null;
    }

    private static int parseResult(JSONObject jsonResponse) throws IOException {
        int code = 0;
        String message = "";

        if (jsonResponse.has("error")) {
            JSONObject errorObject = null;
            try {
                errorObject = jsonResponse.getJSONObject("error");
            } catch (JSONException e) {
                log.error("parseResult: caught JSONException extracting errorObject", e);
            }
            try {
                code = errorObject.getInt("code");
            } catch (JSONException e) {
                log.error("parseResult: caught JSONException trying to determine code", e);
            }
            try {
                message = errorObject.getString("message");
            } catch (JSONException e) {
                log.error("parseResult: caught JSONException trying to determine message", e);
            }
            log.warn("parseResult: error code={}, message={}", code, message);
            try {
                remaningDownloads = errorObject.getInt("remaining");
                log.debug("parseResult: remaining downloads={}", remaningDownloads);
            } catch (JSONException e) {
                log.error("parseResult: caught JSONException trying to determine message", e);
            }
            if (message.equals("invalid token")) {
                // Handle invalid token error
                authTokenValid = false;
                LAST_QUERY_RESULT = RESULT_CODE_TOKEN_EXPIRED;
                log.warn("parseResult: invalid token");
                return LAST_QUERY_RESULT;
            }
            switch (code) {
                case 401:
                    LAST_QUERY_RESULT = RESULT_CODE_BAD_CREDENTIALS;
                    log.warn("parseResult: bad credentials");
                    return LAST_QUERY_RESULT;
                case 403:
                    LAST_QUERY_RESULT = RESULT_CODE_BAD_API_KEY;
                    log.warn("parseResult: bad API key");
                    return LAST_QUERY_RESULT;
                case 406:
                    LAST_QUERY_RESULT = RESULT_CODE_INVALID_FILE_ID;
                    log.warn("parseResult: invalid file ID");
                    return LAST_QUERY_RESULT;
                case 410:
                    LAST_QUERY_RESULT = RESULT_CODE_LINK_GONE;
                    log.warn("parseResult: invalid or expired link");
                    return LAST_QUERY_RESULT;
                case 429:
                    LAST_QUERY_RESULT = RESULT_CODE_TOO_MANY_REQUESTS;
                    log.warn("parseResult: throttle limit reached, try later");
                    return LAST_QUERY_RESULT;
            }
            if (remaningDownloads == -1) {
                LAST_QUERY_RESULT = RESULT_CODE_QUOTA_EXCEEDED;
                log.warn("parseResult: quota exceeded");
                return LAST_QUERY_RESULT;
            }
        } else {
            LAST_QUERY_RESULT = RESULT_CODE_OK;
            return RESULT_CODE_OK;
        }
        return LAST_QUERY_RESULT;
    }

    public static ArrayList<OpenSubtitlesSearchResult> searchSubtitle(String tmdbId, String videoHash, String fileName, String languages) throws IOException {
        // Note: only the first result page is queried because it is assumed that it should be enough with order_by criteria
        // input: languages is a comma separated list of languages (e.g. "en,fr")
        // output: an arrayList of {id, release, language} for each subtitle found
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "search").newBuilder();
        if (videoHash != null) urlBuilder.addQueryParameter("moviehash", videoHash);
        if (tmdbId != null) urlBuilder.addQueryParameter("tmdb_id", tmdbId);
        if (fileName != null) urlBuilder.addQueryParameter("query", fileName);
        urlBuilder.addQueryParameter("order_by", "from_trusted,ratings,download_count");
        urlBuilder.addQueryParameter("sublanguageid", languages);

        String url = urlBuilder.build().toString();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get()
                .addHeader(USER_AGENT, USER_AGENT_VALUE)
                .addHeader(API_KEY, apiKey);

        if (authenticated) requestBuilder.addHeader(AUTHORIZATION, "Bearer " + authToken);
        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    int result = parseResult(jsonResponse);
                    if (jsonResponse.has("error")) {
                        log.warn("searchSubtitle: error in response, result={}", result);
                        return null;
                    } else if (jsonResponse.has("data")) {
                        switch (result) {
                            case RESULT_CODE_OK:
                                JSONArray dataArray = jsonResponse.getJSONArray("data");

                                int numSubtitles = dataArray.length();
                                ArrayList<OpenSubtitlesSearchResult> subtitleRefs = new ArrayList<>();

                                log.debug("searchSubtitle: found {} subtitles", numSubtitles);

                                for (int i = 0; i < numSubtitles; i++) {
                                    JSONObject subtitleInfo = dataArray.getJSONObject(0);
                                    String id = subtitleInfo.getString("id");
                                    String language = "";
                                    //String release = "";
                                    String file_id = "";
                                    String file_name = "";
                                    if (subtitleInfo.has("attributes")) {
                                        JSONObject subtitleAttribute = jsonResponse.getJSONObject("attributes");
                                        language = subtitleAttribute.getString("language");
                                        //release = subtitleAttribute.getString("release");
                                    }
                                    if (subtitleInfo.has("files")) {
                                        JSONObject subtitleFiles = jsonResponse.getJSONArray("files").getJSONObject(0);
                                        file_id = subtitleFiles.getString("file_id");
                                        file_name = subtitleFiles.getString("file_name");
                                    }
                                    OpenSubtitlesSearchResult subtitleResult = new OpenSubtitlesSearchResult(file_id, file_name, language);
                                    subtitleRefs.add(subtitleResult);
                                    log.debug("searchSubtitle: found " + subtitleResult);
                                    // Check if "moviehash_match" is present and true in the response
                                    if (jsonResponse.has("moviehash_match") && jsonResponse.getBoolean("moviehash_match")) {
                                        log.debug("searchSubtitle: hash match, focus on first match");
                                        break;
                                    }
                                }
                                return subtitleRefs;
                            case RESULT_CODE_TOKEN_EXPIRED:
                                // Handle invalid token error
                                if (! authTokenValid) { // one retry in case of invalid token
                                    log.warn("searchSubtitle: invalid token, retrying");
                                    login(apiKey, username, password);
                                    return searchSubtitle(tmdbId, videoHash, fileName, languages);
                                } else {
                                    return null;
                                }
                            case RESULT_CODE_BAD_CREDENTIALS, RESULT_CODE_QUOTA_EXCEEDED:
                                // Handle quota error
                                return null;
                        }
                    } else return null;
                } catch (JSONException e) {
                    log.error("searchSubtitle: caught JSONException", e);
                }
            }
        }
        return null;
    }

    public static String getDownloadSubtitleLink(String file_id) throws IOException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "download").newBuilder();
        urlBuilder.addQueryParameter("file_id", file_id);

        String url = urlBuilder.build().toString();

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get()
                .addHeader(USER_AGENT, USER_AGENT_VALUE)
                .addHeader(API_KEY, apiKey);

        if (authenticated) requestBuilder.addHeader(AUTHORIZATION, "Bearer " + authToken);
        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                try {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    int result = parseResult(jsonResponse);
                    if (jsonResponse.has("error")) {
                        log.warn("getDownloadSubtitleLink: error in response, result={}", result);
                        return null;
                    } else {
                        switch (result) {
                            case RESULT_CODE_OK:
                                if (jsonResponse.has("remaining")) {
                                    remaningDownloads = jsonResponse.getInt("remaining");
                                }
                                if (jsonResponse.has("link")) {
                                    String subtitleLink = jsonResponse.getString("link");
                                    log.debug("getDownloadSubtitleLink: found link {}", subtitleLink);
                                    return subtitleLink;
                                } else return null;
                            case RESULT_CODE_TOKEN_EXPIRED:
                                // Handle invalid token error
                                if (! authTokenValid) { // one retry in case of invalid token
                                    log.warn("getDownloadSubtitleLink: invalid token, retrying");
                                    login(apiKey, username, password);
                                    return getDownloadSubtitleLink(file_id);
                                } else {
                                    return null;
                                }
                            case RESULT_CODE_BAD_CREDENTIALS, RESULT_CODE_QUOTA_EXCEEDED:
                                // Handle quota error
                                return null;
                        }
                    }
                } catch (JSONException e) {
                    log.error("getDownloadSubtitleLink: caught JSONException", e);
                }
            }
        }
        return null;
    }
    
}