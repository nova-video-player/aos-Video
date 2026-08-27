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
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.Spatializer;
import android.os.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;

import com.archos.mediacenter.video.CustomApplication;
import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.player.Player;

public class CodecDiscovery {

	private static final Logger log = LoggerFactory.getLogger(CodecDiscovery.class);
	public static final int DOVI_MODE_AUTO = 0;
	public static final int DOVI_MODE_OFF = 1;
	public static final int DOVI_MODE_FORCE = 2;
	public static final int SPATIALIZER_CAP_SUPPORTED = 1;
	public static final int SPATIALIZER_CAP_AVAILABLE = 1 << 1;
	public static final int SPATIALIZER_CAP_ENABLED = 1 << 2;
	public static final int SPATIALIZER_CAP_CAN_SPATIALIZE_5_1 = 1 << 3;
	public static final int SPATIALIZER_CAP_CAN_SPATIALIZE_7_1 = 1 << 4;

	// log4j/logback not possible since used from native it seems
	private static volatile boolean isDoViDisabled = false;
	private static volatile int doViMode = DOVI_MODE_AUTO;
	private static int hdrCapabilities = 0; // bitmask 0: none, 1: HDR10, 2: HLG, 4: HDR10+, 8: Dolby Vision

	public static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec) {
		return isCodecTypeSupported(codecType, allowSwCodec, MediaCodecList.REGULAR_CODECS);
	}

	public static long getMediaCodecAudioCapabilities(boolean allowSwCodec) {
		long flags = 0;
		final MediaCodecInfo[] codecInfos;
		try {
			MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
			codecInfos = codecList.getCodecInfos();
		} catch (RuntimeException e) {
			log.warn("getMediaCodecAudioCapabilities: MediaCodecList query failed", e);
			return 0;
		}

		for (MediaCodecInfo codecInfo : codecInfos) {
			if (codecInfo.isEncoder() || (!allowSwCodec && isSwCodec(codecInfo))) {
				continue;
			}

			String[] types = codecInfo.getSupportedTypes();
			for (String type : types) {
				String mime = type.toLowerCase(Locale.US);
				switch (mime) {
					case "audio/ac3":
						flags |= 1L << 5;
						break;
					case "audio/eac3":
						flags |= 1L << 6;
						break;
					case "audio/eac3-joc":
						flags |= 1L << 6;
						flags |= 1L << 18;
						break;
					case "audio/vnd.dts":
					case "audio/dts":
						flags |= 1L << 7;
						break;
					case "audio/vnd.dts.hd":
					case "audio/dts-hd":
						flags |= 1L << 8;
						flags |= 1L << 29;
						break;
					case "audio/mpeg":
						flags |= 1L << 9;
						break;
					case "audio/mp4a-latm":
						flags |= 1L << 10;
						break;
					case "audio/true-hd":
					case "audio/truehd":
						flags |= 1L << 14;
						break;
					case "audio/opus":
						flags |= 1L << 20;
						break;
					default:
						break;
				}
			}
		}

		if (log.isDebugEnabled()) log.debug("getMediaCodecAudioCapabilities: allowSwCodec={} flags={}", allowSwCodec, flags);

		return flags;
	}

	public static int getSpatializerCapabilities(Context context) {
		if (Build.VERSION.SDK_INT < 32 || context == null) {
			return 0;
		}
		try {
			AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			if (audioManager == null) {
				return 0;
			}
			return Api32.getSpatializerCapabilities(audioManager);
		} catch (RuntimeException e) {
			log.warn("getSpatializerCapabilities: spatializer query failed", e);
			return 0;
		}
	}

	public static String getSpatializerCapabilitiesDescription(Context context, int capabilities) {
		if (Build.VERSION.SDK_INT < 32) {
			return context.getString(R.string.status_unsupported_api, Build.VERSION.SDK_INT);
		}
		if ((capabilities & SPATIALIZER_CAP_SUPPORTED) == 0) {
			return context.getString(R.string.status_not_supported);
		}
		StringBuilder sb = new StringBuilder(context.getString(R.string.status_supported));
		sb.append(", ");
		sb.append((capabilities & SPATIALIZER_CAP_AVAILABLE) != 0
				? context.getString(R.string.status_available)
				: context.getString(R.string.status_unavailable));
		sb.append(", ");
		sb.append((capabilities & SPATIALIZER_CAP_ENABLED) != 0
				? context.getString(R.string.status_enabled)
				: context.getString(R.string.status_disabled));
		sb.append(", 5.1 ");
		sb.append((capabilities & SPATIALIZER_CAP_CAN_SPATIALIZE_5_1) != 0 ? "✓" : "✕");
		sb.append(", 7.1 ");
		sb.append((capabilities & SPATIALIZER_CAP_CAN_SPATIALIZE_7_1) != 0 ? "✓" : "✕");
		if ((capabilities & (SPATIALIZER_CAP_CAN_SPATIALIZE_5_1 | SPATIALIZER_CAP_CAN_SPATIALIZE_7_1)) == 0) {
			sb.append(", ");
			sb.append(context.getString(R.string.status_multichannel));
			sb.append(" ✕");
		}
		return sb.toString();
	}

	public static void displaySupportsDoVi(boolean isSupported) {
		if (log.isDebugEnabled()) log.debug("displaySupportsDoVi={}", isSupported);
		// Set bit 3 of hdrCapabilities if display supports Dolby Vision.
		if (isSupported) {
			hdrCapabilities |= 8;
		} else {
			hdrCapabilities &= ~8;
		}
	}

	public static void displaySupportsHdr10(boolean isSupported) {
		if (log.isDebugEnabled()) log.debug("displaySupportsHdr10={}", isSupported);
		// Set bit 1 of hdrCapabilities if display supports HDR10.
		if (isSupported) {
			hdrCapabilities |= 1;
		} else {
			hdrCapabilities &= ~1;
		}
	}

	public static void displaySupportsHdrHLG(boolean isSupported) {
		if (log.isDebugEnabled()) log.debug("displaySupportsHdrHLG={}", isSupported);
		// Set bit 2 of hdrCapabilities if display supports HLG.
		if (isSupported) {
			hdrCapabilities |= 2;
		} else {
			hdrCapabilities &= ~2;
		}
	}

	public static void displaySupportsHdr10Plus(boolean isSupported) {
		if (log.isDebugEnabled()) log.debug("displaySupportsHdr10Plus={}", isSupported);
		// Set bit 4 of hdrCapabilities if display supports HDR10+.
		if (isSupported) {
			hdrCapabilities |= 4;
		} else {
			hdrCapabilities &= ~4;
		}
	}

	public static void resetHdrCapabilities() {
		if (log.isDebugEnabled()) log.debug("resetHdrCapabilities");
		hdrCapabilities = 0;
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
		if (log.isDebugEnabled()) log.debug("disableDovi={}", isDisabled);
		setDoViMode(isDisabled ? DOVI_MODE_OFF : DOVI_MODE_AUTO);
	}

	public static void setDoViMode(int mode) {
		if (log.isDebugEnabled()) log.debug("setDoViMode={}", mode);
		doViMode = mode;
		isDoViDisabled = (mode == DOVI_MODE_OFF);
	}

	public static int getDoViMode() {
		return doViMode;
	}

	private static boolean isCodecTypeSupported(String codecType, boolean allowSwCodec, int kind) {
		MediaCodecList codecList = new MediaCodecList(kind);
		MediaCodecInfo[] codecInfos = codecList.getCodecInfos();

		for (MediaCodecInfo codecInfo : codecInfos) {
			if (isCodecInfoSupported(codecInfo, codecType, allowSwCodec)) {
				if (log.isTraceEnabled()) log.trace("isCodecTypeSupported2: codecInfo.getName {} supported", codecInfo.getName());
				return true;
			} else {
				if (log.isTraceEnabled()) log.trace("isCodecTypeSupported2: codecInfo.getName {} not supported", codecInfo.getName());
			}
		}
		return false;
	}

	private static String getCodecForProfile(String mimeType, int profile) {
		if (log.isDebugEnabled()) log.debug("getCodecForProfile: mimeType={} profile={}", mimeType, profile);
		if (Build.VERSION.SDK_INT < 27) return null;
		MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
		MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
		String firstSupportedCodec = null;
		for (MediaCodecInfo codecInfo : codecInfos) {
			// Ignore encoders
			if (codecInfo.isEncoder()) {
				continue;
			}

			if (isCodecInfoSupported(codecInfo, mimeType, false)) {
				if (firstSupportedCodec == null) {
					firstSupportedCodec = codecInfo.getName();
				}
				MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
				if (capabilities != null) {
					if (capabilities.profileLevels != null) {
						for (MediaCodecInfo.CodecProfileLevel level : capabilities.profileLevels) {
							if (log.isDebugEnabled()) log.debug("getCodecForProfile: profile={}, level={}", level.profile, level.level);
							if (level.profile == profile) {
								return codecInfo.getName();
							}
						}
					}
				}
			}
		}
		if (doViMode == DOVI_MODE_FORCE && "video/dolby-vision".equalsIgnoreCase(mimeType) && firstSupportedCodec != null) {
			if (log.isDebugEnabled()) log.debug("getCodecForProfile: forcing first Dolby Vision codec {}", firstSupportedCodec);
			return firstSupportedCodec;
		}
		return null;
	}

	public static boolean isSwCodec(MediaCodecInfo codecInfo) {
		if (Build.VERSION.SDK_INT >= 29) {
			return codecInfo.isSoftwareOnly();
		} else {
			return codecInfo.getName().startsWith("OMX.google") ||
					codecInfo.getName().toLowerCase(Locale.ROOT).contains("sw") ||
					codecInfo.getName().toLowerCase(Locale.ROOT).startsWith("c2.android");
		}
	}

	private static boolean isCodecInfoSupported(MediaCodecInfo codecInfo, String codecType, boolean allowSwCodec) {
		if (log.isTraceEnabled()) log.trace("isCodecInfoSupported: isDoViDisabled={} allowSwCodec={} isEncoder={}", isDoViDisabled, allowSwCodec, codecInfo.isEncoder());
		if (codecInfo.isEncoder() || (!allowSwCodec && isSwCodec(codecInfo))) {
			if (log.isTraceEnabled()) log.trace("isCodecTypeSupported: codecInfo.getName {} not supported (isEncoder,swCodecs)", codecInfo.getName());
			return false;
		}
		String[] types = codecInfo.getSupportedTypes();
		if (log.isTraceEnabled()) log.trace("isCodecTypeSupported: looking for codecType {}, codecInfo.getName {}, supported types {}", codecType, codecInfo.getName(), Arrays.toString(types));
		for (String type : types) {
			if (type.equalsIgnoreCase(codecType)) {
				if (log.isTraceEnabled()) log.trace("isCodecTypeSupported: codecInfo.getName {} matching {}", codecInfo.getName(), codecType);
				if (type.equalsIgnoreCase("video/dolby-vision") && isDoViDisabled) {
					if (log.isDebugEnabled()) log.debug("isCodecTypeSupported: rejecting codecInfo.getName {} because dolby vision disabled", codecInfo.getName());
					return false;
				} else {
					if (log.isDebugEnabled()) log.debug("isCodecTypeSupported: validating codecInfo.getName {}", codecInfo.getName());
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

		String hdmiAudioCodecs = CustomApplication.getSupportedAudioCodecs(CustomApplication.getHdmiOnlyAudioCodecsFlag());
		if (!hdmiAudioCodecs.isEmpty())
			technicalInfo += "\n" + context.getResources().getString(R.string.hdmi_audio_capabilities) + " " + hdmiAudioCodecs;

		if (CustomApplication.isSpdifConnected()) {
			String spdifAudioCodecs = CustomApplication.getSupportedAudioCodecs(CustomApplication.getSpdifOnlyAudioCodecsFlag());
			if (!spdifAudioCodecs.isEmpty())
				technicalInfo += "\n" + context.getResources().getString(R.string.spdif_audio_capabilities) + " " + spdifAudioCodecs;
		}

		String mediaCodecAudioCodecs = CustomApplication.getSupportedAudioCodecs(CustomApplication.getMediaCodecAudioCapabilitiesFlag());
		if (!mediaCodecAudioCodecs.isEmpty())
			technicalInfo += "\n" + context.getResources().getString(R.string.mediacodec_audio_capabilities) + ": " + mediaCodecAudioCodecs;

		technicalInfo += "\n" + context.getResources().getString(R.string.spatialization_capabilities) + ": "
				+ getSpatializerCapabilitiesDescription(context, CustomApplication.getSpatializerCapabilities());

		int maxAudioChannelCount = CustomApplication.getMaxAudioChannelCount();
		if (maxAudioChannelCount > 0)
			technicalInfo += "\n" + context.getResources().getString(R.string.max_audio_channels) + " " + maxAudioChannelCount;
		return technicalInfo;
	}

	@androidx.annotation.RequiresApi(32)
	private static final class Api32 {
		private static int getSpatializerCapabilities(AudioManager audioManager) {
			Spatializer spatializer = audioManager.getSpatializer();
			if (spatializer == null) {
				return 0;
			}
			int capabilities = 0;
			if (spatializer.getImmersiveAudioLevel() != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE) {
				capabilities |= SPATIALIZER_CAP_SUPPORTED;
			}
			if (spatializer.isAvailable()) {
				capabilities |= SPATIALIZER_CAP_AVAILABLE;
			}
			if (spatializer.isEnabled()) {
				capabilities |= SPATIALIZER_CAP_ENABLED;
			}
			AudioAttributes audioAttributes = new AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_MEDIA)
					.setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
					.build();
			AudioFormat format51 = new AudioFormat.Builder()
					.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
					.setSampleRate(48000)
					.setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1)
					.build();
			if (spatializer.canBeSpatialized(audioAttributes, format51)) {
				capabilities |= SPATIALIZER_CAP_CAN_SPATIALIZE_5_1;
			}
			AudioFormat format71 = new AudioFormat.Builder()
					.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
					.setSampleRate(48000)
					.setChannelMask(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND)
					.build();
			if (spatializer.canBeSpatialized(audioAttributes, format71)) {
				capabilities |= SPATIALIZER_CAP_CAN_SPATIALIZE_7_1;
			}
			return capabilities;
		}
	}

}
