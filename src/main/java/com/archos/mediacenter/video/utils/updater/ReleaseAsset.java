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

/**
 * A single APK asset attached to a GitHub release.
 *
 * Release APKs are named:
 *   org.courville.nova-&lt;versionCode&gt;-&lt;versionName&gt;-&lt;abi&gt;-release.apk
 * where &lt;abi&gt; is one of arm64-v8a, armeabi-v7a, x86, x86_64 or universal.
 */
public class ReleaseAsset {

    public static final String UNIVERSAL_ABI = "universal";

    public final String name;
    public final String downloadUrl;
    public final long size;
    /** CPU ABI this asset targets, or {@link #UNIVERSAL_ABI}, or null if unrecognized. */
    public final String abi;

    public ReleaseAsset(String name, String downloadUrl, long size, String abi) {
        this.name = name;
        this.downloadUrl = downloadUrl;
        this.size = size;
        this.abi = abi;
    }

    /** ABI tokens used in Nova's split APK names, longest first. */
    private static final String[] KNOWN_ABIS = {"arm64-v8a", "armeabi-v7a", "x86_64", "x86", UNIVERSAL_ABI};

    /**
     * Extract the ABI from a release asset file name, or null when the name does not
     * match the expected -&lt;abi&gt;-release.apk shape. Matching against known ABI
     * suffixes (rather than the token after the last dash) keeps hyphenated ABIs such
     * as arm64-v8a intact and stops x86 from matching an x86_64 asset.
     */
    public static String abiFromAssetName(String name) {
        if (name == null) return null;
        for (String abi : KNOWN_ABIS) {
            if (name.endsWith("-" + abi + "-release.apk")) return abi;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ReleaseAsset{" + name + ", abi=" + abi + ", size=" + size + "}";
    }
}
