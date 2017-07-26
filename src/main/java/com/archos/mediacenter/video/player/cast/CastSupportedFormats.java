// Copyright 2017 Archos SA
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

package com.archos.mediacenter.video.player.cast;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alexandre on 09/03/17.
 */

public class CastSupportedFormats {
    public static List sMediaFormats = new ArrayList();
    public static List sAudioCodecs = new ArrayList();
    public static List sVideoCodecs = new ArrayList();

    static{
        sMediaFormats.add("mkv");
        sMediaFormats.add("mp4");
        sMediaFormats.add("webm");
        sVideoCodecs.add("h.264");
        sVideoCodecs.add("vp8");
        sVideoCodecs.add("vp9");
        sAudioCodecs.add("mp3");
        sAudioCodecs.add("vorbis");
        sAudioCodecs.add("wav");
        sAudioCodecs.add("aac");
        sAudioCodecs.add("opus");

    }

    public static boolean isVideoSupported(String format) {
        return sVideoCodecs.contains(format.toLowerCase());
    }

    public static boolean isAudioSupported(String format) {
        return sAudioCodecs.contains(format.toLowerCase());
    }
}
