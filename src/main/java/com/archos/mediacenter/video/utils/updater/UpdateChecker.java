// Copyright 2026 Courville Software
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

package com.archos.mediacenter.video.utils.updater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Query the latest GitHub release of the Nova project and decide whether an update
 * is available. The pure helpers here have no Android dependency so they can be
 * unit tested on the JVM.
 */
public final class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    public static final String OWNER = "nova-video-player";
    public static final String REPO = "aos-AVP";

    private static final String API_URL = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    private static final String USER_AGENT = "NovaVideoPlayer-Updater";

    // org.courville.nova-<versionCode>-<versionName>-<abi>-release.apk
    private static final Pattern ASSET_CODE_PATTERN = Pattern.compile("^org\\.courville\\.nova-(\\d+)-");

    private UpdateChecker() {
    }

    public static String getApiUrl() {
        return API_URL;
    }

    /**
     * Fetch the latest published (non-prerelease) release. Returns null when the
     * repository has no release yet. Throws on network/HTTP failures.
     */
    public static GitHubRelease fetchLatestRelease(OkHttpClient client) throws IOException, JSONException {
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                if (log.isDebugEnabled()) log.debug("fetchLatestRelease: no release found (404)");
                return null;
            }
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API HTTP " + response.code());
            }
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            String tag = json.optString("tag_name", "");
            List<ReleaseAsset> assets = parseAssets(json.optJSONArray("assets"));
            long versionCode = parseVersionCodeFromAssets(assets);
            GitHubRelease release = new GitHubRelease(tag, normalizeTag(tag), versionCode, assets);
            if (log.isDebugEnabled()) log.debug("fetchLatestRelease: {}", release);
            return release;
        }
    }

    static List<ReleaseAsset> parseAssets(JSONArray array) throws JSONException {
        List<ReleaseAsset> assets = new ArrayList<>();
        if (array == null) return assets;
        for (int i = 0; i < array.length(); i++) {
            JSONObject o = array.getJSONObject(i);
            String name = o.optString("name", "");
            if (!name.endsWith(".apk")) continue;
            String url = o.optString("browser_download_url", "");
            long size = o.optLong("size", -1L);
            assets.add(new ReleaseAsset(name, url, size, ReleaseAsset.abiFromAssetName(name)));
        }
        return assets;
    }

    /** Strip a leading "v"/"V" from a release tag. */
    public static String normalizeTag(String tag) {
        if (tag == null) return "";
        if (tag.startsWith("v") || tag.startsWith("V")) return tag.substring(1);
        return tag;
    }

    /** The release version without the build-date suffix, e.g. "6.5.4-20261003.0017" -> "6.5.4". */
    public static String baseVersion(String versionName) {
        if (versionName == null) return "";
        int idx = versionName.indexOf('-');
        return idx < 0 ? versionName : versionName.substring(0, idx);
    }

    /** Extract the version code embedded in Nova APK asset names, or -1. */
    public static long parseVersionCodeFromAssets(List<ReleaseAsset> assets) {
        if (assets == null) return -1L;
        for (ReleaseAsset asset : assets) {
            Matcher m = ASSET_CODE_PATTERN.matcher(asset.name);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (NumberFormatException e) {
                    // ignore and try the next asset
                }
            }
        }
        return -1L;
    }

    /**
     * Whether the release is newer than the running build. The version code parsed
     * from the APK asset name is authoritative (it is what Android/Play uses to
     * decide an update); the version name is only a fallback when no code is known.
     */
    public static boolean isNewer(String currentVersionName, long currentVersionCode, GitHubRelease release) {
        if (release == null) return false;
        if (release.versionCode > 0 && currentVersionCode > 0) {
            return release.versionCode > currentVersionCode;
        }
        // currentVersionCode <= 0 is not expected in a release build (only DEBUG builds,
        // which never reach the GitHub path, could report an unknown code). Fall back to
        // the version name so we still return something sensible in that case.
        return compareVersionStrings(release.versionName, baseVersion(currentVersionName)) > 0;
    }

    /** Numeric dot-separated comparison; non-numeric components count as 0. */
    public static int compareVersionStrings(String a, String b) {
        String[] as = (a == null ? "" : a).split("\\.");
        String[] bs = (b == null ? "" : b).split("\\.");
        int count = Math.max(as.length, bs.length);
        for (int i = 0; i < count; i++) {
            int ai = i < as.length ? parseIntSafe(as[i]) : 0;
            int bi = i < bs.length ? parseIntSafe(bs[i]) : 0;
            if (ai != bi) return Integer.compare(ai, bi);
        }
        return 0;
    }

    private static int parseIntSafe(String value) {
        if (value == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
            else break;
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Pick the asset matching the most preferred supported ABI, avoiding a larger
     * universal download. Falls back to the universal asset only when no ABI asset
     * matches. Returns null when nothing usable is present.
     */
    public static ReleaseAsset selectAsset(GitHubRelease release, String[] supportedAbis) {
        if (release == null || release.assets.isEmpty()) return null;
        if (supportedAbis != null) {
            for (String abi : supportedAbis) {
                for (ReleaseAsset asset : release.assets) {
                    if (abi != null && abi.equals(asset.abi)) return asset;
                }
            }
        }
        for (ReleaseAsset asset : release.assets) {
            if (ReleaseAsset.UNIVERSAL_ABI.equals(asset.abi)) return asset;
        }
        return null;
    }
}
