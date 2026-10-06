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

import java.util.Collections;
import java.util.List;

/** Parsed representation of the latest GitHub release. */
public class GitHubRelease {

    /** Raw tag, e.g. "v6.5.4". */
    public final String tagName;
    /** Tag without a leading "v", e.g. "6.5.4". */
    public final String versionName;
    /** Version code parsed from the asset names, or -1 when unavailable. */
    public final long versionCode;
    public final List<ReleaseAsset> assets;

    public GitHubRelease(String tagName, String versionName, long versionCode, List<ReleaseAsset> assets) {
        this.tagName = tagName;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.assets = assets != null ? assets : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "GitHubRelease{" + tagName + ", versionCode=" + versionCode + ", assets=" + assets.size() + "}";
    }
}
