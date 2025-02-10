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

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.Player;

public class CodecDiscovery {

	private static final Logger log = LoggerFactory.getLogger(CodecDiscovery.class);

	// log4j/logback not possible since used from native it seems
	private static boolean isDoViDisabled = false;
	private static int hdrCapabilities = 0; // bitmask 0: none, 1: HDR10, 2: HLG, 4: HDR10+, 8: Dolby Vision

	public static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec) {
		return isCodecTypeSupported(codecType, allowSwCodec, MediaCodecList.REGULAR_CODECS);
	}

	public static void displaySupportsDoVi(boolean isSupported) {
		log.debug("displaySupportsDoVi={}", isSupported);
		// set bit 3 of hdrCapabilities to 1 if display supports Dolby Vision
		hdrCapabilities |= 8;
	}

	public static void displaySupportsHdr10(boolean isSupported) {
		log.debug("displaySupportsHdr10={}", isSupported);
		// set bit 1 of hdrCapabilities to 1 if display supports HDR10
		hdrCapabilities |= 1;
	}

	public static void displaySupportsHdrHLG(boolean isSupported) {
        log.debug("displaySupportsHdrHLG={}", isSupported);
		// set bit 2 of hdrCapabilities to 1 if display supports HLG
		hdrCapabilities |= 2;
	}

	public static void displaySupportsHdr10Plus(boolean isSupported) {
        log.debug("displaySupportsHdr10Plus={}", isSupported);
		// set bit 4 of hdrCapabilities to 1 if display supports HDR10+
		hdrCapabilities |= 4;
	}

	// [None, HDR10, HDR HLG, HDR10+, Dolby Vision] based on display capabilities

	public static String getHdrScreenCapabilities(Context context) {
		// concatenate all supported HDR formats supported by the screen
		// perform a loop on getResources().getStringArray(R.array.display_hdr) [None, HDR10, HDR HLG, HDR10+, Dolby Vision] and concatenate the strings
		// [None, HDR10, HDR HLG, HDR10+, Dolby Vision] based on display capabilities
		return getHdrScreenCapabilities(context, hdrCapabilities);
	}

	public static String getHdrScreenCapabilities(Context context, int hdrCapabilities) {
		// Concatenate all supported HDR formats supported by the screen
		String[] hdrFormats = context.getResources().getStringArray(R.array.display_hdr);
		StringBuilder sb = new StringBuilder();
		if (hdrCapabilities == 0) {
			sb.append(hdrFormats[0]); // None
		} else {
			for (int i = 1; i < hdrFormats.length; i++) { // Start from 1 to skip "None"
				int mask = 1 << (i - 1); // Calculate the bitmask for the current HDR format
				if ((hdrCapabilities & mask) != 0) {
					sb.append(hdrFormats[i]).append(", ");
				}
			}
			if (sb.length() > 0) {
				sb.setLength(sb.length() - 2); // Remove trailing ", "
			}
		}
		return sb.toString();
	}

	public static void disableDoVi(boolean isDisabled) {
		log.debug("disableDovi={}", isDisabled);
		isDoViDisabled = isDisabled;
	}

	private static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec, int kind) {
		MediaCodecList codecList = new MediaCodecList(kind);
		MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

		for (MediaCodecInfo codecInfo : codecInfos) {
			if (isCodecInfoSupported(codecInfo, codecType, allowSwCodec)) {
				log.trace("isCodecTypeSupported2: codecInfo.getName {} supported", codecInfo.getName());
				return true;
			} else {
				log.trace("isCodecTypeSupported2: codecInfo.getName {} not supported", codecInfo.getName());
			}
		}
		return false;
	}

	private static String getCodecForProfile(String mimeType, int profile) {
		log.debug("getCodecForProfile: mimeType={} profile={}", mimeType, profile);
		if (Build.VERSION.SDK_INT < 27) return null;
		MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
		MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
		for (MediaCodecInfo codecInfo : codecInfos) {
			// Ignore encoders
			if (codecInfo.isEncoder()) {
				continue;
			}

			if (isCodecInfoSupported(codecInfo, mimeType, false)) {
				MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
				if (capabilities != null) {
					if (capabilities.profileLevels != null) {
						for (MediaCodecInfo.CodecProfileLevel level : capabilities.profileLevels) {
							log.debug("getCodecForProfile: profile={}, level={}", level.profile, level.level);
							if (level.profile == profile) {
								return codecInfo.getName();
							}
						}
					}
				}
			}
		}
		return null;
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
        log.trace("isCodecInfoSupported: isDoViDisabled={} allowSwCodec={} isEncoder={}", isDoViDisabled, allowSwCodec, codecInfo.isEncoder());
		if (codecInfo.isEncoder() || (!allowSwCodec && isSwCodec(codecInfo))) {
            log.trace("isCodecTypeSupported: codecInfo.getName {} not supported (isEncoder,swCodecs)", codecInfo.getName());
			return false;
		}
		String[] types = codecInfo.getSupportedTypes();
        log.trace("isCodecTypeSupported: looking for codecType {}, codecInfo.getName {}, supported types {}", codecType, codecInfo.getName(), Arrays.toString(types));
		for (String type : types) {
			if (type.equalsIgnoreCase(codecType)) {
                log.trace("isCodecTypeSupported: codecInfo.getName {} matching {}", codecInfo.getName(), codecType);
				if (type.equalsIgnoreCase("video/dolby-vision") && isDoViDisabled) {
                    log.debug("isCodecTypeSupported: rejecting codecInfo.getName {} because dolby vision disabled", codecInfo.getName());
					return false;
				} else {
                    log.debug("isCodecTypeSupported: validating codecInfo.getName {}", codecInfo.getName());
					return true;
				}
			}
		}
		return false;
	}

	public static String getTechnicalInfo(Context context) {
		String technicalInfo = "";
		String hdrMode = Player.getHdr(context);
		technicalInfo += context.getResources().getString(R.string.supported_refresh_rates) + " " + CustomApplication.getSupportedRefreshRates() + " → " + Player.getRefreshRate() + " / " + Player.getFps();
		technicalInfo += "\n" + context.getResources().getString(R.string.hdr_capability) + " " + getHdrScreenCapabilities(context) + ( hdrMode.isEmpty() ? "" : " → " + hdrMode);
		String supportedAudioCodecs = CustomApplication.getSupportedAudioCodecs();
		if (!supportedAudioCodecs.isEmpty())
			technicalInfo += "\n" + context.getResources().getString(R.string.hdmi_audio_capabilities) + " " + supportedAudioCodecs;
		int maxAudioChannelCount = CustomApplication.getMaxAudioChannelCount();
		if (maxAudioChannelCount > 0)
			technicalInfo += "\n" + context.getResources().getString(R.string.max_audio_channels) + " " + maxAudioChannelCount;
		return technicalInfo;
	}

}
