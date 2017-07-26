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

package com.archos.mediacenter.video.player.cast.subconverter.subtitleFile;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by alexandre on 24/06/16.
 */
public class Utils {
    public static InputStreamReader getInputStreamReader(InputStream is, InputStream is2) throws IOException {
        UniversalDetector detector = new UniversalDetector(null);

        int nread;
        byte[] buf = new byte[1024];
        while ((nread = is2.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, nread);
        }
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        if(encoding!=null)
            return new InputStreamReader(is, encoding);
        else
            return new InputStreamReader(is);
    }


}
