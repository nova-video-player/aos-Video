# Dolby Vision playback

How NOVA plays Dolby Vision (DV) through Android MediaCodec, what it rewrites in
the bitstream, and why. Native code lives in `native/avos`
(`Source/stream_parser_ffmpeg.c`, `Source/video_dovi_rpu.c`,
`Include/video_dovi.h`); the user setting and codec queries are in
`CodecDiscovery.java`.

## Constraints

- Hardware decoding only. No CPU color conversion, no CPU frame copies.
- Correct DV colors need either a DV decoder (the device does the Dolby
  processing) or a base layer the display understands (HDR10, HLG, SDR).
- NOVA only edits metadata in the compressed stream; it never processes pixels.

## Profiles

| Profile | Layers | Base layer | Without a DV decoder |
| --- | --- | --- | --- |
| 5 | single, IPTPQc2 | none usable | wrong colors (warning toast) |
| 7 MEL / FEL | base + enhancement + RPU | HDR10 (CCID 6) | HDR10 |
| 8.1 / 8.2 / 8.4 | single + RPU | HDR10 / SDR / HLG | base format |
| 9 (AVC), 10 (AV1) | single + RPU | per CCID | base format if compatible |

MEL carries no picture data; FEL adds a 12-bit residual. Forum comparisons
report a small visible difference (FEL > MEL = 8.1).

## Setting: one Dolby Vision mode

`Auto` (default), `Force`, `Off`. No per-feature switches: every rewrite below
is selected automatically from the stream, the decoder and the display.

| Mode | Behaviour |
| --- | --- |
| Auto | Use the DV decoder when it matches the profile and the display advertises DV, or when the stream has no usable base layer (P5). Otherwise play the base layer. |
| Force | Use a DV decoder when one exists, even if the profile or display is not advertised. |
| Off | Never use the DV decoder; play the base layer. |

Justification: users cannot know which profiles their decoder accepts; the
player can query it. Kodi and Vimu expose conversion switches, which leave users
to guess on Sony and Fire TV.

## Automatic bitstream rewrites (DV decoder path only)

Applied once per packet at demux time, in the original framing (hvcC length
prefix or Annex B). Any parse or write failure keeps the original packet.

### Profile 7 to 8.1

- **When:** HEVC Profile 7, a DV decoder matching Profile 8 (Auto, with a DV
  display) or any DV decoder (Force), and no decoder advertises
  `DolbyVisionProfileDvheDtb` (128, Profile 7). Otherwise Profile 7 plays its
  HDR10 base as before.
- **What:** libdovi `dovi_convert_rpu_with_mode(rpu, 2)` on NAL 62, drop NAL 63
  (enhancement layer). For FEL, libdovi also removes the luma/chroma mapping,
  which is only valid with the enhancement layer present.
- **Why:** DV TVs and boxes often accept only single-layer Profile 8
  (#1870, #1980, #1260); without conversion they stay in HDR10 or show a black
  screen. The same conversion is used by Dolby's tooling, Kodi, CoreELEC and
  Vimu. Decoders that advertise Profile 7 receive the stream unchanged, keeping
  FEL on the few devices that reconstruct it (some Amlogic devices). Most
  Profile 7 decoders (e.g. Shield) drop FEL anyway, so conversion loses nothing
  there.
- **Limit:** a decoder that advertises Profile 7 but renders it incorrectly is
  not detected; the no-first-frame watchdog covers only startup failures.

### HDR10+ removal

- **When:** the DV decoder is selected.
- **What:** remove ITU-T T.35 SEI messages carrying ST 2094-40 (country 0xB5,
  provider 0x003C, oriented code 0x0001, application 4). Other SEI messages in
  the same NAL are kept; a NAL left empty is dropped.
- **Why:** with DV output HDR10+ is redundant, and hybrid DV/HDR10+ files stop
  some displays from entering DV (#1980, #1503). Restored when playback falls
  back to the base codec so HDR10 output keeps HDR10+.

## Fallbacks

- DV decoder fails to open, errors, or never produces a first frame: retry the
  base codec when its layer is compatible, or play anyway with a color warning
  (P5).
- Rewrites stop when the base codec takes over.

## Not supported

- Profile 5 on devices without a DV decoder (Pixel): correct colors need per
  pixel Dolby reshaping on the GPU (`dovi_color_profile_plan.md`), not a
  bitstream rewrite.
- FEL reconstruction when converting Profile 7.

## Diagnostics

DEBUG logs from avos:

- `Dolby Vision config` / `selection`: container record and decoder choice.
- `Dolby Vision RPU: guessed_profile=… el=none|MEL|FEL`: first parsed RPU.
- `Dolby Vision rewrite selection` / `rewrite`: chosen rewrites, then counts on
  the first rewritten packet; RPU failures are logged once.

## Build

libdovi 3.4.0 (MIT) is built by `native/libdovi-android-builder` into
`native/prebuilt/libdovi`. When the prebuilt is missing, avos builds without it:
the Profile 7 conversion and the RPU probe are disabled, HDR10+ removal still
works.
