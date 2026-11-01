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

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoCodecSelectionTest {
    @Test public void autoDvRequiresTheAdvertisedProfile() {
        assertEquals(-1, VideoCodecSelection.rank(true, false, false, false, 1));
        assertEquals(-1, VideoCodecSelection.rank(true, false, true, false, 1));
        assertTrue(VideoCodecSelection.rank(true, false, true, true, 1) >= 0);
    }
    @Test public void forceKeepsMatchedProfilesAheadOfUnmatchedCodecs() {
        assertTrue(VideoCodecSelection.rank(true, true, true, true, -1)
                < VideoCodecSelection.rank(true, true, true, false, 1));
    }
    @Test public void preferSupportedSizesWithoutBlacklistingVendorQuirks() {
        int fits = VideoCodecSelection.rank(false, false, true, true, 1);
        int unknown = VideoCodecSelection.rank(false, false, true, true, 0);
        int exceeds = VideoCodecSelection.rank(false, false, true, true, -1);
        assertTrue(fits < unknown && unknown < exceeds && exceeds >= 0);
    }
    @Test public void baseCodecMustNotAdvertiseOnlyTheWrongBitDepth() {
        assertEquals(-1, VideoCodecSelection.rank(false, false, true, false, 1));
        assertTrue(VideoCodecSelection.rank(false, false, false, false, 1) >= 0);
    }
}
