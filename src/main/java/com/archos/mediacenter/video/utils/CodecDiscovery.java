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

package com.archos.mediacenter.video.utils;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class CodecDiscovery {

	private static final Logger log = LoggerFactory.getLogger(CodecDiscovery.class);

	private static boolean isDoViDisabled = false;
	private static boolean displaySupportsDovi = false; // could be used to auto disable DoVi codecs

	public static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec) {
		return isCodecTypeSupported(codecType, allowSwCodec, MediaCodecList.REGULAR_CODECS);
	}

	public static void displaySupportsDoVi(boolean isSupported) {
		displaySupportsDovi = isSupported;
	}

	public static void disableDoVi(boolean isDisabled) {
		isDoViDisabled = isDisabled;
	}

	private static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec, int kind) {
		MediaCodecList codecList = new MediaCodecList(kind);
		MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

		for (MediaCodecInfo codecInfo : codecInfos) {
			if (isCodecInfoSupported(codecInfo, codecType, allowSwCodec)) {
				log.debug("isCodecTypeSupported: codecInfo.getName " + codecInfo.getName() + "supported");
				return true;
			} else {
				log.debug("isCodecTypeSupported: codecInfo.getName " + codecInfo.getName() + "not supported");
			}
		}
		return false;
	}

	public static boolean isSwCodec(MediaCodecInfo codecInfo) {
		if (Build.VERSION.SDK_INT >= 29) {
			return codecInfo.isSoftwareOnly();
		} else {
			return codecInfo.getName().startsWith("OMX.google") ||
					codecInfo.getName().toLowerCase().contains("sw") ||
					codecInfo.getName().toLowerCase().startsWith("c2.android");
		}
	}

	private static boolean isCodecInfoSupported(MediaCodecInfo codecInfo, String codecType, boolean allowSwCodec) {
		if (codecInfo.isEncoder() || (!allowSwCodec && isSwCodec(codecInfo))) {
			log.debug("isCodecTypeSupported: codecInfo.getName " + codecInfo.getName() + " not supported");
			return false;
		}
		String[] types = codecInfo.getSupportedTypes();
		log.trace("isCodecTypeSupported: looking for codecType " + codecType + ", codecInfo.getName " + codecInfo.getName() + ", supported types " + Arrays.toString(types));
		for (String type : types) {
			if (type.equalsIgnoreCase(codecType)) {
				log.debug("isCodecTypeSupported: codecInfo.getName " + codecInfo.getName() + " not supported");
				if (type.equalsIgnoreCase("video/dolby-vision") && isDoViDisabled) {
					log.debug("isCodecTypeSupported: rejecting codecInfo.getName " + codecInfo.getName() + " because dolby vision disabled");
					return false;
				} else {
					log.debug("isCodecTypeSupported: validating codecInfo.getName " + codecInfo.getName());
					return true;
				}
			}
		}
		return false;
	}

}