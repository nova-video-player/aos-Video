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

package com.archos.mediacenter.video.utils;

/** Capability ranking for clear, non-tunneled hardware video candidates. */
final class VideoCodecSelection {
    private VideoCodecSelection() {}

    static int rank(boolean dovi, boolean force, boolean advertised,
            boolean profileMatches, int sizeSupport) {
        // Auto DV must retain profile-specific selection. Unknown base profiles
        // can be attempted, but never prefer a known incompatible bit depth.
        if (!profileMatches && !force && (dovi || advertised)) return -1;
        int profileRank = profileMatches ? 0 : 3;
        // Reported size/rate limits are imperfect on some TV firmware. Rank
        // those candidates last instead of removing previously working devices.
        int sizeRank = sizeSupport > 0 ? 0 : sizeSupport == 0 ? 1 : 2;
        return profileRank + sizeRank;
    }
}
