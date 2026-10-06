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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class UpdateCheckerTest {

    private static ReleaseAsset arm64() {
        return new ReleaseAsset("org.courville.nova-2549909-6.5.4-arm64-v8a-release.apk", "https://x/arm64", 10, ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-arm64-v8a-release.apk"));
    }

    private static ReleaseAsset x86() {
        return new ReleaseAsset("org.courville.nova-2549909-6.5.4-x86-release.apk", "https://x/x86", 10, ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-x86-release.apk"));
    }

    private static ReleaseAsset x86_64() {
        return new ReleaseAsset("org.courville.nova-2549909-6.5.4-x86_64-release.apk", "https://x/x86_64", 10, ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-x86_64-release.apk"));
    }

    private static ReleaseAsset universal() {
        return new ReleaseAsset("org.courville.nova-2549909-6.5.4-universal-release.apk", "https://x/universal", 30, ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-universal-release.apk"));
    }

    @Test
    public void abiFromAssetNameRecognizesSplits() {
        assertEquals("arm64-v8a", ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-arm64-v8a-release.apk"));
        assertEquals("x86_64", ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-x86_64-release.apk"));
        assertEquals("x86", ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-x86-release.apk"));
        assertEquals("universal", ReleaseAsset.abiFromAssetName("org.courville.nova-2549909-6.5.4-universal-release.apk"));
        assertNull(ReleaseAsset.abiFromAssetName("nova.apk"));
    }

    @Test
    public void baseVersionStripsBuildDate() {
        assertEquals("6.5.4", UpdateChecker.baseVersion("6.5.4-20261003.0017"));
        assertEquals("6.5.4", UpdateChecker.baseVersion("6.5.4"));
        assertEquals("", UpdateChecker.baseVersion(null));
    }

    @Test
    public void versionComparison() {
        assertTrue(UpdateChecker.compareVersionStrings("6.5.4", "6.5.3") > 0);
        assertTrue(UpdateChecker.compareVersionStrings("6.5.4", "6.6.0") < 0);
        assertEquals(0, UpdateChecker.compareVersionStrings("6.5.4", "6.5.4"));
        assertTrue(UpdateChecker.compareVersionStrings("7.0.0", "6.9.9") > 0);
    }

    @Test
    public void versionCodeParsedFromAssets() {
        List<ReleaseAsset> assets = Arrays.asList(arm64(), universal());
        assertEquals(2549909L, UpdateChecker.parseVersionCodeFromAssets(assets));
    }

    @Test
    public void selectsPreferredAbiBeforeUniversal() {
        GitHubRelease release = new GitHubRelease("v6.5.4", "6.5.4", 2549909L,
                Arrays.asList(universal(), x86(), arm64()));
        assertEquals("arm64-v8a", UpdateChecker.selectAsset(release, new String[]{"arm64-v8a", "armeabi-v7a"}).abi);
    }

    @Test
    public void x86DoesNotMatchX8664() {
        GitHubRelease release = new GitHubRelease("v6.5.4", "6.5.4", 2549909L,
                Arrays.asList(x86_64()));
        assertNull(UpdateChecker.selectAsset(release, new String[]{"x86"}));
    }

    @Test
    public void fallsBackToUniversal() {
        GitHubRelease release = new GitHubRelease("v6.5.4", "6.5.4", 2549909L,
                Arrays.asList(x86_64(), universal()));
        assertEquals("universal", UpdateChecker.selectAsset(release, new String[]{"mips"}).abi);
    }

    @Test
    public void newerByVersionCodeIsAuthoritative() {
        // Higher version code -> update, regardless of the version name.
        GitHubRelease higherCode = new GitHubRelease("v6.5.4", "6.5.4", 2549910L, Arrays.asList(arm64()));
        assertTrue(UpdateChecker.isNewer("6.5.4-20261003.0017", 2549909L, higherCode));

        // Higher version name but lower/equal code -> no update (code wins).
        GitHubRelease higherNameLowerCode = new GitHubRelease("v6.9.0", "6.9.0", 2549900L, Arrays.asList(arm64()));
        assertFalse(UpdateChecker.isNewer("6.5.4-20261003.0017", 2549909L, higherNameLowerCode));

        GitHubRelease sameCodeHigherName = new GitHubRelease("v6.9.0", "6.9.0", 2549909L, Arrays.asList(arm64()));
        assertFalse(UpdateChecker.isNewer("6.5.4-20261003.0017", 2549909L, sameCodeHigherName));

        // No usable code -> fall back to the version name.
        GitHubRelease noCode = new GitHubRelease("v6.6.0", "6.6.0", -1L, Arrays.asList(arm64()));
        assertTrue(UpdateChecker.isNewer("6.5.4-20261003.0017", 2549909L, noCode));

        assertFalse(UpdateChecker.isNewer("6.5.4-20261003.0017", 2549909L, null));
    }
}
