> Superseded on `dev/subtitleIdentification` by [explicit analysis and MKV editing](SUBTITLE_LANGUAGE_EDITOR.md). This document describes the previous automatic design.

# Subtitle language guessing

## Revision: identify tracks before playback

Pre-playback analysis now supplements the original playback-only implementation.
Video information retrieval and direct playback preparation explicitly request
a bounded, independent FFmpeg scan. Ordinary library metadata reads do not
automatically scan entire collections. The existing classifier and acceptance
rules remain unchanged. Metadata and deterministic descriptions still win.

The information screen first displays ordinary metadata, then refreshes it
after background analysis. Direct playback performs preparation on its source
worker before opening AVOS, so initial language-based selection sees accepted
results. A first uncached file can therefore spend additional time opening;
no UI-thread work or wait for playback to reach later dialogue is required.
Manual selections remain authoritative. Unknown tracks retain their existing fallback labels.

Native metadata and playback share a persistent, versioned, bounded cache in
app-private storage. Its key fingerprints the file prefix, size, duration and
subtitle stream descriptors; entries also validate track index/stream/format.
This works across temporary proxy URLs without retaining credentials. It is a
quick content fingerprint, not a whole-file checksum: remote replacements that
preserve all fingerprinted information cannot be detected immediately. Entries expire after seven days; at most 512 files are retained. Completed or
budget-exhausted scans have a ten-minute retry cooldown, including unresolved
tracks. Expiry limits stale-result risk. Corrupt entries are ignored and writes use atomic replacement.

Only one analysis runs at a time; concurrent requests reuse its cached results.
Native analysis has an eight-second deadline and a 128 MiB read budget, uses
seekable input only, and samples later dialogue when a suitable video seek is
available. Limits include native opening/probing; Java source/proxy setup precedes this
deadline and has its own network timeouts. The byte limit is
checked around demux operations, so one operation may overshoot it. Failed or
cancelled scans preserve ordinary metadata and already accepted results.
Stop/navigation cancellation interrupts native I/O and classification waits.
The model is freed when the analysis session closes. Playback fallback remains
available for unresolved tracks; completed cache entries prevent repeated scans
for a limited period. Background library-wide scheduling is deferred to avoid
silently scanning every network video.

## Purpose and scope

Identify otherwise unlabelled embedded text subtitles, such as the 38 tracks
in the Ride.or.Die sample described in [SUBTITLES.md](SUBTITLES.md). Detection
must keep the UI responsive and must not turn uncertain guesses into
authoritative metadata. First uncached playback may spend up to the native scan
budget preparing languages before opening the player. This specification was
written before implementation on `dev/fastText` and updated before adding the
pre-playback path.

The first implementation covers embedded SubRip/plain text, ASS/SSA, WebVTT,
and MOV_TEXT. Bitmap subtitles need OCR and are excluded. External subtitle
discovery and filename-based identification retain their existing behavior.

## Precedence and data contract

For each embedded subtitle track:

1. Preserve a usable container language code.
2. Otherwise resolve explicit language names or codes in its description using
   deterministic, conservative rules. Ambiguous descriptions remain unresolved.
3. Otherwise sample text and ask fastText. Never overwrite either earlier source.

Keep the ISO language code in `SUB_PROPERTIES.lang`. Add separate provenance
and confidence fields; do not put presentation markers into the code or the
original title. Provenance distinguishes unknown, container, description, and
fastText. Confidence is an integer percentage for accepted fastText results;
it is not a calibrated probability of correctness.

Publish the fields through native player metadata to Java. Add independent
metadata key ranges for the new fields, preserving the existing per-track
stride so old retriever/player metadata cannot be misinterpreted.

Java displays the existing localized track label with ` (AI 94%)` appended
only for fastText results. Container/description results receive no marker.
Inference updates refresh the list without changing a manually selected track
or treating the new language as evidence that a saved track index is stale.

## Ownership and threading

AVOS owns deterministic resolution, text sampling, inference, and accepted
results. Sampling happens in the FFmpeg parser before filtering for the active
subtitle track, so unselected tracks can be identified too. Rendering does not
participate and subtitle text does not cross JNI.

Each playback parser owns an inference session. A worker receives bounded
samples and produces results. It never dereferences the stream or changes
track properties. The parser polls completed results, updates its track
properties, and publishes a metadata-change notification through the existing
stream event mechanism. Closing a parser cancels queued work and joins its
worker before freeing the session; results cannot outlive their video.

MediaLib supplies a model path through JNI. Model extraction runs off the UI
thread, uses app-private storage and a model-version/hash-specific filename,
verifies SHA-256, and publishes the file atomically. A missing or corrupt model
disables inference without affecting playback. Native sessions can start
collecting samples while model extraction finishes.

## Sampling, acceptance, and resource limits

Strip format wrappers, HTML/ASS styling, and line-break escapes before inference.
Preserve UTF-8 text and word boundaries. Bound individual packets and collected
text; malformed/binary text must not reach the classifier. Ignore repeated cues
and previously sampled timestamps after backward seeks.

Initial conservative policy, subject to validation against actual subtitles:

- At most 128 tracks per session, matching AVOS's track limit.
- At most 4 KiB of sample text per track, plus bounded cue deduplication state
  and one worker sample. No unbounded packet or job queue.
- A sample requires at least eight distinct cues and 512 UTF-8 bytes.
- Require two non-overlapping samples to agree on the winning language.
- Both predictions must have score at least 0.85 and lead the runner-up by at
  least 0.20. Publish the lower of the two scores, clamped to 0-100 percent.
- At most eight inference attempts per track. Once accepted or exhausted,
  release its sample buffers. Short forced tracks may remain unknown.
- A failed or uncertain classification leaves the language unset. Do not infer
  regional variants or hearing-impaired/forced dispositions from model output.

Load the model only when a complete sample needs classification. Reuse one
instance for the session's pending jobs, not one per track. Release the model
after five seconds without inference work, and on session close. Sampling may
continue with the model unloaded. Accepted results are shared through the
bounded persistent cache described above. Playback fallback results are saved
at parser teardown, keeping cache writes out of the packet-reading loop. Record
actual Android peak memory separately from the model's 917 KiB on-disk size;
freeing allocations does not guarantee an immediate RSS decrease.

## Build and APK packaging

The canonical model remains in
`native/prebuilt/fastText/share/fastText/lid.176.ftz`. MediaLib stages it into
generated assets along with the model license and attribution; no second model
copy is committed in Video or MediaLib. The APK contains
`assets/fasttext/lid.176.ftz`, `LICENSE.model`, and `NOTICE` under that directory.
Retain the classifier's MIT license as well.

The native C++ bridge uses the existing fastText prebuilt and the NDK's static
C++ runtime. The shared build configuration must support Android and host
builds, with a no-inference fallback where fastText is unavailable. The builder
repository remains on `main`; application/engine changes use `dev/fastText`.

## Validation and completion criteria

- Test precedence, deterministic ambiguity, generic format/disposition titles,
  packet cleaning, limits, repeated/backward-seek cues, confidence margins,
  sample agreement, and clean cancellation.
- Run real-model host inference with the production session/bridge, including
  lazy loading and idle/close release. Replay subtitle packets from the supplied
  MKV and report accepted/unresolved results without calling scores accuracy.
- Verify native metadata compatibility and Java AI label formatting. Exercise
  inference-only updates without reselecting subtitles.
- Build Android native code and the APK, inspect packaged model/licensing assets,
  and run the relevant unit suites and scoped whitespace gates.
- Validate UI behavior, memory, close/reopen, and seeks on an attached device
  when available. Report absent device coverage explicitly.

## Implementation and validation (2026-11-15)

Implemented in AVOS `Source/sub_language.cpp` and the FFmpeg parser,
MediaLib `SubtitleLanguageModel`/`SubtitleLanguage`, and Video's metadata,
player notification, and track-label paths. Source values are unknown=0,
container=1, description=2, fastText=3. Native/Java metadata keys use
`40000 + trackIndex` for source and `41000 + trackIndex` for confidence;
the existing six-field subtitle stride is unchanged. A language-only update
uses `MEDIA_INFO_METADATA_UPDATE` with extra=1. Updates deferred during a seek
retain this distinction; a coalesced generic metadata update takes precedence.

Deterministic title resolution recognizes an explicit table of language names,
endonyms and ISO aliases, bracketed codes, `l_XXX`, and a leading code followed
by standard qualifiers. It rejects conflicting languages and generic labels
such as TEXT/SDH/HI. It is deliberately conservative and not a complete
multilingual natural-language title parser. Container language validation
accepts syntactically usable two/three-letter codes and hyphenated subtags,
excluding und/mul/zxx/unk. Character encoding alone never determines language.

Validation completed:

- Native subtitle suite: 23 tests passed. Real-model tests cover all supported
  text wrappers, metadata/title precedence, invalid/sparse/repeated cues,
  backward seeks, disagreement between confident samples, low-confidence
  rejection, retry exhaustion, lazy/idle/close release, and missing-model
  failure. The bridge also compiles and runs without fastText.
- Standalone AVOS builds passed both normally and with `FASTTEXT=ON` and a host
  fastText installation. All four Android ABIs compiled and linked, and
  `assembleNoamazonDebug` including lint passed. C++ compilation preserves the
  builder's file-offset ABI on 32-bit API 23; AVOS C retains large-file support.
- Both new MediaLib tests passed: verified atomic installation preserves a
  good model after a corrupt replacement, and only AI results get annotations.
  The 46 focused Video tests passed, including preservation of manual/None
  selection, independent metadata keys, and updates deferred during seeks.
- All four APKs contain the exact 938,013-byte model with the SHA-256 documented
  in the prebuilt, plus the model license, fastText license, and NOTICE.
- The full MediaLib suite ran: 236 tests, 12 failures, 2 skipped. Failures are
  in existing TMDB title-language/JDK-reflection, movie scraper, database
  reconciliation/show persistence, and track-naming tests. The full suite is
  not green; these failures are outside the new inference tests.
- No Android device was connected. Device UI, runtime inference latency,
  peak memory, and real playback close/reopen/seek behavior remain unverified.

### Replay of the supplied MKV

Host replay used all extracted subtitle cues with their media timestamps and
the production session implementation. It drained pending jobs between feeds
to test the acceptance policy without host demux-speed effects. It accepted
32 of 38 tracks; this is a coverage result, not a measured accuracy percentage.

The two French tracks (subtitle tracks 13 and 14, using one-based numbering)
were accepted as `fr` at 94% and 95%, after evidence through approximately
06:00 and 06:05 of media time. Accepted tracks ranged from about 03:41 to
13:21 of media time. These are evidence timestamps, not classification latency
or guaranteed on-device menu-update times. Parser read-ahead, pauses, seeks,
and worker availability affect when labels appear.

Six tracks remained unresolved: the three-cue forced English track, Basque,
Galician, Indonesian, Malay, and Norwegian. Keeping these unknown is intentional
under the initial conservative thresholds. Spanish/Portuguese regional variants
and Chinese script variants are not inferred by this model integration.

### Reproducing the focused checks

From `native/fastText-android-builder`, configure/build the host target as
described in its README, then install it with
`cmake --install build-host --prefix build-host-install`.
From `native/avos`, run `make`, `make FASTTEXT=ON -W Source/sub_language.cpp`,
and `python3 -m pytest -q test/integration/subtitles` in a pytest environment.
Set `AVOS_FASTTEXT_MODEL` to the model's absolute path for host playback.

From `Video`, run:

```sh
./gradlew --offline :MediaLib:testDebugUnitTest --tests '*SubtitleLanguageTest' \
  testNoamazonDebugUnitTest --tests '*SubtitleTrackSelectionPolicyTest' \
  --tests '*SubtitleManager*Test' --tests '*SubtitleLanguageMetadataTest' \
  assembleNoamazonDebug
```

### Android TV details parity

Leanback `VideoDetailsFragment.VideoInfoTask` now publishes ordinary metadata
before requesting the same bounded native analysis as phone/tablet information.
It then refreshes both subtitle and file-detail rows with the accepted results.
Internal tracks no longer wait for external-subtitle discovery; external results
merge when available. Existing TV numbering, two-column layout and controls are
retained. No extra track prefix or status row is added.

Navigation cancels the request's native retriever and ignores queued results for
a superseded task/video. Shared SMB transports are not interrupted. Opening
details from playback uses the player's metadata and does not start another
scan. Failed analysis preserves ordinary metadata, and successful analysis
shares the existing persistent native cache with playback.

Validation: four Leanback regression tests cover metadata-before-inference,
accepted-result refresh/cache reuse, external-discovery independence,
cancellation and stale navigation results. Those and the five focused
phone/metadata tests passed, as did all four debug APK builds and lint. The
Android TV emulator crashed on launch (`Illegal instruction`) both inside and
outside the sandbox. The phone disconnected before the runtime fallback check;
TV hardware/emulator layout and remote-focus validation remain outstanding.

### Pre-playback integration and validation

`SubtitleLanguagePreparation` runs on the player's source worker before AVOS
opens the stream. `VideoInfoTask` publishes normal metadata immediately, then
refreshes it after its cancellable analysis. Retriever and player expose the
same source/confidence fields. Labels retain the existing language/title format,
e.g. `French (AI 94%)`, without an added track-number prefix. Duplicate-language
tracks remain separately selectable. Initial selection uses the existing preference policy;
subsequent language-only updates never override a manual selection. The
information screen retains its original `1:`, `2:` list formatting, with no
extra status row. It refreshes on each metadata result independently of external
subtitle discovery, so ordinary TEXT entries appear before language analysis.

`ffmpeg_guess_subtitle_languages()` owns an independent FFmpeg context and
uses the same cleaning, classifier, and confidence policy as playback. It
seeks using the video index to dialogue around five minutes (or a quarter of a
shorter video), then samples all unknown text tracks in one bounded pass.
Discarding audio/video avoids decoding, not necessarily network reads of their
interleaved packets. A sparse or uncertain track can consume the entire scan
budget. This is why changing the classifier alone does not fix the UX.

`Source/sub_language_cache.cpp` writes versioned, atomic records under
`getNoBackupFilesDir()/subtitle-languages`. The fingerprint includes the first
64 KiB, file size, duration and subtitle descriptors. Both ordinary metadata
retrieval and playback merge matching entries before publishing tracks. Only
one scan runs at a time; active playback interrupts competing analysis.
Inference sessions and their model are destroyed when a scan ends.

Production-native host tests with the supplied Ride.or.Die file:

| Path | Observed result |
| --- | --- |
| Local cold scan | 31/38 tracks accepted in 178 ms |
| Local cached scan | Same labels in 1 ms |
| Throttled localhost HTTP, 16 MiB/s, cold | Native scan stopped at 8.003 s; both French tracks accepted |
| Same HTTP file, cached | Native retrieval 3 ms, whole process 73 ms; server sent about 192 KiB |
| Cancellation during HTTP scan | Native request stopped at 101 ms; whole process 161 ms |

Both French tracks (13/14) were available before playback at 94%/93% scores.
Cold HTTP served about 107 MiB over five requests. These are host measurements,
not Android/SMB measurements or guaranteed latency. Remaining unknown tracks
keep their existing fallback labels and can still be identified during playback.
The initial playback-only replay above sampled a different region, so its
coverage and scores differ.

Added regression coverage exercises cache persistence across processes,
corruption/expiry rejection, source-setup cancellation and release, retriever
provenance, label formatting, and playback-exit cancellation. The opt-in native
integration test links production AVOS objects and checks cold/cached results
and cancellation with the actual MKV. After `make FASTTEXT=ON`, run:

```sh
AVOS_LANGUAGE_SAMPLE='/absolute/path/to/Ride.or.Die.2026.S01E01.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv' \
  python3 -m pytest -q test/integration/subtitles
```

Android native builds for all four ABIs, the debug APK build including lint,
and all 52 focused Java subtitle tests passed after the pre-playback changes.
The 24 regular native subtitle tests passed; the added opt-in production demux
test passed separately after correcting its build-command extraction. Both
standalone configurations (without fastText and with `FASTTEXT=ON`) passed. The
full MediaLib run still reported 236 tests, 12 failures, 2 skipped in the
previously listed areas; its complete output was retained. Device UI, real
network shares, playback startup/track switching, and peak memory still need
a connected phone/emulator.

Library-wide scheduling, external subtitle content detection, and bitmap OCR
remain outside this change.
