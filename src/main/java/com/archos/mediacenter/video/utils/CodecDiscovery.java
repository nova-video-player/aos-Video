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
import android.util.Log;

import java.util.Arrays;

public class CodecDiscovery {

	// log4j/logback not possible since used from native it seems
	private final static boolean DBG = true;
	private final static boolean DBG2 = false;
	private final static String TAG = "CodecDiscovery";
	private static boolean isDoViDisabled = false;
	private static boolean displaySupportsDovi = false; // could be used to auto disable DoVi codecs

	public static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec) {
		return isCodecTypeSupported(codecType, allowSwCodec, MediaCodecList.REGULAR_CODECS);
	}

	public static void displaySupportsDoVi(boolean isSupported) {
		if (DBG) Log.d(TAG,"displaySupportsDoVi=" + isSupported);
		displaySupportsDovi = isSupported;
	}

	public static void disableDoVi(boolean isDisabled) {
		if (DBG) Log.d(TAG,"disableDovi=" + isDisabled);
		isDoViDisabled = isDisabled;
	}

	private static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec, int kind) {
		MediaCodecList codecList = new MediaCodecList(kind);
		MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

		for (MediaCodecInfo codecInfo : codecInfos) {
			if (isCodecInfoSupported(codecInfo, codecType, allowSwCodec)) {
				if (DBG2) Log.d(TAG, "isCodecTypeSupported2: codecInfo.getName " + codecInfo.getName() + " supported");
				return true;
			} else {
				if (DBG2) Log.d(TAG,"isCodecTypeSupported2: codecInfo.getName " + codecInfo.getName() + " not supported");
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
		if (DBG2) Log.d(TAG,"isCodecInfoSupported: isDoViDisabled=" + isDoViDisabled + " allowSwCodec=" + allowSwCodec + " isEncoder=" + codecInfo.isEncoder());
		if (codecInfo.isEncoder() || (!allowSwCodec && isSwCodec(codecInfo))) {
			if (DBG2) Log.d(TAG,"isCodecTypeSupported: codecInfo.getName " + codecInfo.getName() + " not supported (isEncoder,swCodecs)");
			return false;
		}
		String[] types = codecInfo.getSupportedTypes();
		if (DBG2) Log.d(TAG,"isCodecTypeSupported: looking for codecType " + codecType + ", codecInfo.getName " + codecInfo.getName() + ", supported types " + Arrays.toString(types));
		for (String type : types) {
			if (type.equalsIgnoreCase(codecType)) {
				if (DBG2) Log.d(TAG,"isCodecTypeSupported: codecInfo.getName " + codecInfo.getName() + " matching " + codecType);
				if (type.equalsIgnoreCase("video/dolby-vision") && isDoViDisabled) {
					if (DBG) Log.d(TAG,"isCodecTypeSupported: rejecting codecInfo.getName " + codecInfo.getName() + " because dolby vision disabled");
					return false;
				} else {
					if (DBG) Log.d(TAG,"isCodecTypeSupported: validating codecInfo.getName " + codecInfo.getName());
					return true;
				}
			}
		}
		return false;
	}

}