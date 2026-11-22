# Explicit subtitle language analysis and MKV editing

The `dev/subtitleIdentification` branches replace automatic fastText scanning
with a user action in phone/tablet Video Info and the TV subtitle details row.
The published `dev/fastText` branches remain the reference for the previous design.

## User flow

1. Ordinary metadata and existing subtitle labels appear immediately. Opening
   details or playback never starts language inference. Saved AI results are
   looked up by source URI and applied in embedded subtitle track order.
2. **Identify subtitle languages** starts analysis directly in the existing
   phone/tablet or Leanback subtitle section. A themed indeterminate horizontal
   line below the actions spans the full content width while processing. A short
   stage label appears above the tracks; neither takes focus. Feedback and actions
   stay outside the track list. On phone/tablet, all tracks are always shown and
   scroll with the whole details page, without an inner scrolling area or an
   expand/collapse button. TV keeps its bounded
   scrolling track list. TV actions share
   two fixed-width columns; labels wrap without ellipsis and row heights follow
   the longest translation. Labels share a start edge and equal padding within
   each column, so short labels do not appear offset. A lone action stays in the start column, including
   RTL layouts. The track viewport shrinks to leave room for the actions. Cancel, Save and
   Restore appear in the same section as applicable; no separate activity opens.
   The shared controller reads local saved results during normal binding, but
   never opens the media or starts inference until an explicit action. Leaving
   details cancels analysis. A journalled save/restore already in progress is
   allowed to finish safely, without updating a detached view or another video.
3. The read-only analysis service runs in a separate app process. It samples
   embedded subtitle text with FFmpeg and uses the
   compact fastText model. It releases its retriever and inference model afterward.
   This provides native-crash containment; it is not Android's permission-free
   `isolatedProcess` security sandbox. Network access uses the app's credentials.
4. Completed analysis, including accepted results and unresolved/unsupported
   tracks, is saved in MediaLib before the completion message. Cancellation
   during sampling can return partial accepted results; cancellation before a
   usable source is opened cannot. Process death before persistence loses the
   current job, not previously stored results. There is no background retry.
5. Playback and details merge accepted stored languages only into unknown tracks.
   File metadata and deterministic title resolution remain authoritative. AI
   labels retain their confidence suffix. Results do not expire on a timer.
6. **Save languages to MKV** is a separate user action. It patches only confident,
   compatible fields after reading the current MKV header, then verifies the result.
   **Restore previous MKV languages** restores the last before-image
   or recovers an interrupted write. Playback and writing are mutually excluded
   within Nova; opening a video while a write is active is refused.

The first version targets MKV text tracks. Analysis is bounded by 60 seconds,
256 MiB read, eight classification attempts per unresolved track, 4 KiB samples,
minimum eight distinct cues and 512 bytes per sample. It requires two accepted
samples agreeing, score >= 0.85 and margin >= 0.20. Permanent writing additionally
requires the reported agreement score >= 90. Scores are not calibrated probabilities.
A bounded scan can leave tracks unidentified; the user may explicitly retry.
There is no OCR, automatic whole-library analysis or full subtitle dumping.

### Network read-ahead for explicit analysis

Explicit analysis opens its retriever with a per-source `SUBTITLE_ANALYSIS`
read mode. Ordinary metadata and direct MKV header reads
keep their existing conservative policy. Reusing a retriever for ordinary
metadata resets the policy and closes the previous analysis proxy.

| Backend | Sequential analysis reads |
| --- | --- |
| SSHJ SFTP (`sshj`) | Existing read-ahead stream with 16 pending requests |
| JSch SFTP (`sftp`) | Existing stream with 16 bulk requests |
| SMBJ (`smbj`, or `smb` when selected) | Existing prefetched input stream |
| jcifs SMB (`smb`) | 1 MiB upstream refills, matching playback batching |
| FTP / FTPS | Existing streaming transfer and REST-offset handling |
| WebDAV / WebDAVS | Existing HTTP range stream and response validation |
| HTTP(S), UPnP, local files, document URIs | Existing source/descriptor handling |

Finite HTTP ranges retain conservative backend reads (SFTP depth one, SMBJ
demand reads). Analysis jcifs probes retain 64 KiB refills. Sidecar and poster
requests remain metadata reads. Read-ahead does not open writable handles or
alter authentication, range validation, ownership, cancellation or retry rules.
The existing 60-second/256-MiB native budget remains; backend prefetch can read a
small amount beyond bytes consumed by the demuxer before cancellation closes it.
No complete movie/subtitle dump is written to device storage.

In `make logs`, the long analysis request should now show
`purpose=SUBTITLE_ANALYSIS bounded=false pipeline=16`; ordinary metadata should
still show `purpose=METADATA pipeline=1`. The pipeline count describes the SFTP
policy; other protocols use their own streaming/prefetch mechanisms. Compare
`read_ms`, bytes, and `identify_to_display_ms` with the prior SFTP trace, which
spent 58.1 seconds in upstream reads and 63.4 seconds from Identify to display.

Read-ahead validation: 65 focused tests passed (61 protocol/proxy/read-policy
cases plus four analysis-routing/header cases), and APK assembly/lint passed.
Tests cover both SFTP implementations, FTP, WebDAV, SMBJ helpers, jcifs batching,
finite ranges, unknown lengths, source replacement and direct/document inputs.
The required broader MediaLib run stopped with fail-fast at the existing
TrackNamingTest locale mismatch: 36 cases, one failure, followed by a Gradle
test-process cleanup error. It is not a passing full suite. Real SFTP/SMB/FTP/
WebDAV server throughput remains to be measured on the installed app; host
fixtures establish routing/policy compatibility, not live-server performance.

Sampling reads forward once and collects text for all unresolved tracks together.
It does not timestamp-seek before sampling: that can trigger expensive remote
Cues/index reads before delivering any dialogue. Small HTTP payload skips reuse
the current connection. Audio/video are discarded without decoding, but their
interleaved bytes can still need transferring over a network share. A full dump
of every subtitle would require scanning much more of the file.

## Persistence

Database version 63 adds `subtitle_language_analysis` to the existing MediaLib
video database. Its unreleased schema now has exactly three columns:

```sql
CREATE TABLE subtitle_language_analysis (
    uri TEXT PRIMARY KEY,
    languages TEXT NOT NULL,
    confidences TEXT NOT NULL
);
```

One row per source URI stores parallel comma-separated lists, such as
`en,und,fra` and `97,0,99`. Entries follow embedded subtitle track order, excluding
external subtitle files. Only fastText guesses are stored; known metadata,
description-derived languages and unidentified tracks use `und,0` placeholders.
AI provenance is implicit. Two- and three-letter lowercase codes and integer
confidence scores from 0 to 100 are accepted; malformed or mismatched lists are
ignored. A reanalysis replaces the URI's row. Non-indexed videos are supported.
Thirty-eight three-letter codes and two-digit scores occupy 264 bytes, excluding
the URI and SQLite overhead. No JSON, header snapshot, model identifier, timestamp,
fingerprint or TrackUID is stored in the table.

Cached results apply only when the current embedded track count matches, and
never override metadata or deterministic language identification. File contents
are not fingerprinted for cache validation: replacing a file at the same URI with
the same track count can leave stale results until the user identifies again.
Changing the URI requires reanalysis.

Save reads and inspects the current MKV header, checks the current track count
and language fields, and compares the fresh header byte-for-byte immediately
before writing. It cannot verify that those tracks are the ones originally
analysed. The separate recovery journal holds only the before/after header bytes,
file URI, size, compact language/confidence lists and edit state. TrackUIDs read by the
inspector are not persisted. Save eligibility in the UI uses the confidence
threshold; actual field-size/write compatibility is checked after pressing Save.

Confirmed physical deletion through Nova removes the cached row and recovery
journal, including unindexed files and descendants of deleted folders. Failed or
cancelled deletion preserves them. Library removal/unindexing alone does not
clear them: database row deletion can precede physical deletion or simply mean a
share was unindexed, so a database delete trigger would be insufficient.

After a verified MKV save, only the guesses actually written are cleared to
`und`/`0`; lower-confidence or incompatible fields retain their guesses at the
same track positions. The row is removed when no identified guesses remain.
The recovery journal remains available, and Restore reinstates its original
language/confidence lists. Files deleted outside Nova are not automatically
observed by this cleanup.

There is deliberately no migration from the earlier experimental JSON table and
no additional database version bump. Existing development installations must
recreate only this table using the definition above and remove old-format edit
journals before testing; resolve any pending edit first. No reset of the media
library itself is required. Fresh installs and the normal upgrade from version
62 create the compact table directly.

The previous native `.lid` cache, seven-day expiry, ten-minute cooldown, automatic
Video Info/pre-playback scans, and playback packet-fed classification are removed.
The old model-initialization job at application startup is also removed; the model
is installed/verified only when the explicit worker needs it.

## In-place write contract

`native/libebml-android-builder` and `native/libmatroska-android-builder` build
libebml 1.4.5 and libmatroska 1.7.1 from pinned,
SHA-256-verified upstream archives. A bounded JNI inspector receives bytes, never
writable file handles, and uses those libraries to locate tracks and checksums.
The libraries and notices are packaged with the application. They are LGPL-2.1-or-later;
Nova's adapter source retains its own Apache-2.0 notice. Builds are reproducible
from the source archives and the included CMake/Python build instructions.

Only same-length replacements in existing undefined language fields are supported.
Legacy ISO-639-2/B codes are used (for example `fre`, not `fra`). An existing modern
language field must also fit its replacement and be undefined; otherwise that
track is skipped. Missing fields, metadata relocation/growth, header windows
larger than 1 MiB, and detected Segment-wide CRCs are unsupported. This bounded
inspector is not a full-file Matroska validator; it inspects the first Tracks
section. Unusual malformed layouts with later duplicate Tracks/Segment CRCs are
outside the supported input contract.

Track/Tracks CRC-32 values are checked before editing and recomputed from inside
out. Media payload, file length, track identities, names and dispositions remain
unchanged. Network analysis can transfer much more data than the write itself.

`RandomAccessMediaFile` opens existing files without create/truncate/append:

- SMB uses SMBJ offset writes and denies concurrent write/delete sharing.
- SFTP uses SSHJ READ|WRITE handles, also when JSch is selected for playback.
  Synchronous writes wait for acknowledgements; portable SFTP v3 does not promise
  server-side fsync or exclusive locking.
- Local files and seekable document-provider handles use positional reads/writes.
- Other protocols are not supported for editing.

Before the first write, an AtomicFile recovery journal stores the original and
proposed header, file length and compact AI results in app-private no-backup storage,
with an explicit file-descriptor sync. Only differing byte ranges are written.
The implementation rereads the header and reparses it before marking the journal
applied. On failure it leaves a pending journal. Restore accepts only original,
proposed or partially applied recorded bytes and refuses unrelated header changes.
It is recovery, not an atomic multi-range transaction; losing the app's journal
or external concurrent modifications can prevent automatic recovery. Keep one
undo generation. A full-file backup/remux is never silently substituted.

## Validation

Build through the normal native/Gradle targets. MediaLib requests
`native_build_native/libmatroska-android-builder` and consumes
`native/prebuilt/libmatroska/lib` plus its license assets directly. The JNI
inspector source and CMake project live in the Matroska builder; no native
project remains in MediaLib. Both builders have matching prebuilt repositories.
The four Android ABI libraries use API 23 and 16 KiB ELF alignment. Verified
prebuilts skip rebuilding; builder or dependency changes invalidate their stamps.
The host inspector build is `python3 native/libmatroska-android-builder/build.py --host`.
Focused tests cover fixed-size patches, language-code mapping, confidence gates,
track-count mismatch and interrupted writes. Device and real SMB/SFTP server
checks are separate from host tests; see the implementation report for actual
validation results. The user's source MKV must never be edited during testing:
use a disposable copy.

### Compact persistence and lifecycle validation (2026-11-22)

The schema and result handling now use URI-keyed language/confidence CSV lists.
The debug APK and lint tasks passed. Focused persistence, save/restore, read-mode,
controller, panel, deletion and playback-metadata checks passed: 49 tests, with one optional
real-MKV fixture test skipped. Database migration/helper/provider checks also
passed. The required full MediaLib suite ran 262 tests with 12 failures and three
skips; failures were in TrackNaming, import reconciliation, ShowTags persistence,
MovieScraperFile and TmdbTitleLanguage tests outside the modified paths. Complete
output is retained in `/private/tmp/nova-subtitle-lifecycle-suite.log` for this checkout.
No device was connected, so real-device analysis and network editing were not
retested. Fresh-header save, partial-write recovery and concurrent header-change
refusal were exercised with simulated file access on API 23 and 33. Lifecycle
checks cover failed/cancelled deletion, Android permission callbacks with an
existing file, batch deletion, literal folder boundaries, orphan recovery journals,
partial-save pruning and Restore repopulation. Actual network deletion and Android
permission dialogs still require device validation.

### Validation performed (2026-11-16)

- Standard debug APK and lint pass; all four Android ABIs and the standalone
  AVOS host build were exercised. APK inspection confirms the inspector library
  and both upstream LGPL license files are included.
- 76 focused Java tests pass: 22 MediaLib writer/persistence/migration tests,
  52 subtitle/UI/selection tests and two SMB/SFTP handle tests. The network tests
  mock the server handles; they are not real-server validation.
- Native subtitle suite: 23 pass with the optional media test skipped; rerunning
  the language suite with Ride or Die enabled passes all three tests.
- The inspector finds 38 subtitle TrackUIDs in Ride or Die. The actual production
  patch planner/writer was exercised on a disposable copy, including restoration.
  Independent FFmpeg inspection reads all 38 edited language tags. Independent
  CRC verification passes, changes are confined to language/checksum bytes, and
  restoring the copy's header produces the original whole-file SHA-256.
  These deliberate French test patches validate editing, not classification of
  every track as French. The source movie was never modified.
- The full MediaLib suite was also run: 240 tests, 13 failures and two skips.
  Failures were in scraper/TrackNaming/provider reconciliation/persistence tests,
  including raw-FileDescriptor JRE errors and live scraper failures. These were
  outside the focused feature tests; no claim is made that the full suite is green.
- End-to-end device validation remains outstanding. Installation on the connected
  phone was rejected because its existing Nova uses a different signing key;
  side-by-side installation was then rejected due to fixed provider authorities.
  Neither existing app nor its data was removed. Both available emulator attempts
  exited with SIGILL before Android booted, including a software-graphics attempt.
  Real phone/TV interaction, interruption recovery on Android, and real SMB/SFTP
  server editing therefore remain unverified.

The implementation is committed on `dev/subtitleIdentification` in AVP, Video,
MediaLib, FileCoreLibrary and native/avos, with the fastText prebuilt on the same
branch. AVP's default and v6.6 manifests select these branches; the shared native
builders and other prebuilts keep their existing published revisions. The earlier
`dev/subtitle-language-editor` branches remain available. Benchmark work and
unrelated local changes are excluded.

### UI and network analysis follow-up

A phone capture and UI hierarchy confirmed that the original programmatic screen
placed its status at y=58 and its Identify button at y=184..301, underneath the
status bar and action bar (ending at y=298). The screen now uses an explicit,
non-overlay toolbar, system/cutout insets, Nova's theme preference, subtitle card
styles and borderless actions. Subtitle labels are separate scrollable rows.
Analysis stages, errors and result counts are visible below the toolbar; errors
survive recreation, and a pending recovery warning takes precedence over counts.

Activating the covered button through keyboard focus reproduced the hidden error
`Source has no usable file size`. The analysis incorrectly relied on optional
`FileEditor.length()`, whose default is -1. Header reads now obtain the size from
`MetaFile2Factory`, the metadata/stat path used by the existing network backends.
A regression test covers SSHJ, SFTP, SMB and SMBJ URLs with unsupported editor
length and metadata sizes greater than 4 GiB. Phone/TV launch and measured inset
layout tests cover the action placement. Worker failures are also logged.

The six focused regression cases and APK/lint checks passed using temporary build
output. The required broader MediaLib run stopped with fail-fast after 36 cases
on the previously observed TrackNamingTest locale mismatch. The phone disconnected
before installation of this follow-up, so its updated appearance and successful
network analysis still require an on-device check. The before capture and hierarchy
are `/private/tmp/nova-subtitle-ui.png` and `/private/tmp/nova-subtitle-ui.xml`.

Suggested commit: `Fix subtitle analysis layout and network file-size lookup`.

### Zero-result network analysis follow-up

`nova-47.log` showed the remote timestamp seek failing almost exactly 30 seconds
after opening. The ordinary retriever deadline was interrupting the explicit
scanner despite its 60-second budget. Explicit analysis now shares the 60-second
deadline throughout; ordinary metadata retains its 30-second limit. Sampling
continues forward from probing without the initial timestamp seek. Model errors,
I/O errors and interruptions with no accepted result fail analysis instead of
persisting a successful zero-result scan; accepted partial results remain usable.
A native summary records packet count, bytes read, elapsed time and outcome.

The five focused host cases passed, covering model failure, explicit cancellation,
deadline separation, repeatable local inference and real demuxing over a delayed
HTTP range server that rejects distant index reads. The network case identified
31 of 38 Ride or Die tracks, including both French tracks, in approximately six
seconds. This is a host measurement with simulated delays, not a phone/SFTP
latency guarantee. The Android APK and lint checks also passed.
The complete native subtitle suite then passed all 26 cases with the real sample
enabled. The normal debug APK installed successfully over the existing phone app
without clearing its data.

### Diagnosing analysis on a network share

From the workspace root, run `make logs` before opening the language
screen, reproduce Identify, wait until results appear, then press Ctrl-C. The
command prints its `nova-NNN.log` filename. It captures UI, worker and native
records across PIDs with timestamps, along with other device logs. Its previous
main-PID filter hid the entire `:subtitle_languages` worker.

The UI records opening/refreshing metadata, pressing Identify, worker connection,
progress/completion messages and the time until results are displayed. This
separates analysis latency from the subsequent metadata refresh. With full
logging enabled, `StreamOverHttp.Transfer` records each proxy request's local
port/request ID, source scheme, range, read policy/pipeline depth and opening
time. Transfer progress appears at most once per five seconds of completed I/O;
completion/failure records include bytes received/delivered, cumulative time
waiting in upstream reads and local socket writes, longest read, cancellation,
failure class and cleanup timing. A blocked read delays the next progress record
until it completes or fails; the resulting wait is included in the final totals.
Upstream timings measure waiting for data, including any buffering, rather than
the duration of every SSH protocol request. No remote pathname or dialogue is
included in these transfer summaries. This instrumentation does not change
buffer sizes, read-ahead policy or timeout values.

Capture/transfer follow-up validation (2026-11-17): the then-existing focused
capture target recorded an Android log marker from a separate process on the
connected phone. That target was subsequently removed; use `make logs`.
All 24 focused checks passed (16 proxy buffer, three read-policy, five UI), with
transfer logging explicitly enabled during the proxy tests; byte counts, range
policies and close summaries appeared in their output. APK assembly and lint
passed using fresh temporary build output. An earlier broader proxy run had
eight SMB cancellation-fixture failures reporting unavailable application
context; that run is not green. New transfer/UI records still need a real SFTP
reproduction after installing a build signed by the developer's usual key.

`res/raw/logback_full.xml` enables INFO records from `SubtitleLanguageAnalysis`
and `SubtitleLanguageAnalysisService`. They report the worker PID, source scheme,
model checksum, network/header/model/source-open stages, elapsed times, per-track
results and persistence completion. Native records use the Logcat tag
`SubtitleLanguageNative` directly, including in release builds and the separate
worker process; changing Logback levels cannot enable/disable these native records.
The Logback file appender does not collect native messages. Capture Logcat across
both app processes while reproducing, rather than filtering to the UI PID:

```sh
adb logcat -b main -b crash -v threadtime 'SubtitleLanguageNative:I' \
  'com.archos.mediacenter.video.info.SubtitleLanguageController:I' \
  'com.archos.medialib.SubtitleLanguageAnalysis:I' \
  'com.archos.medialib.SubtitleLanguageAnalysisService:I' \
  'com.archos.medialib.SmbProxy:I' 'com.archos.filecorelibrary.StreamOverHttp:I' \
  'com.archos.filecorelibrary.StreamOverHttp.Transfer:D' \
  'AndroidRuntime:E' 'libc:F' '*:S' > subtitle-analysis.log
```

Start capture before pressing Identify and stop it after completion/error. Native
records distinguish demux open/probe/fingerprint/scan failures, deadline, byte
budget, playback interruption and retriever cancellation. Scan progress records
appear about every five seconds when packets arrive; a blocked read can delay
the next record until I/O returns. Final records show FFmpeg error codes, bytes,
packets per track, missing timestamps, distinct usable cues, classification
attempts, last sample size, top language, score, runner-up and acceptance. Track
indexes are zero-based. No subtitle dialogue, request headers or source credentials
are included in these new diagnostic records.

Diagnostics validation: the five focused native cases passed, and the Android
APK/lint checks passed with fresh temporary Gradle output. Installing that APK
was rejected: the installed app has the Courville signing certificate while this
environment's build uses the standard Android debug certificate. Build/install
from the developer's usual configured terminal to test these records on-device;
the existing app and its data were preserved.

### Native builder relocation (2026-11-19)

The native project moved out of MediaLib into separate `libebml-android-builder`
and `libmatroska-android-builder` repositories, with matching
`native/prebuilt/libebml` and `native/prebuilt/libmatroska` outputs. The Matroska
builder owns the unchanged JNI inspector source. MediaLib packages the shared
library and both LGPL assets directly from the prebuilt directory; its native
build task removes obsolete `libs/*/libnova_matroska.so` copies to prevent duplicate
packaging when upgrading an existing checkout.

All four Android ABI builds and APK/lint checks passed. ELF load segments retain
16 KiB alignment and the expected JNI symbol. The moved host inspector produced
exactly the original JSON for all 38 Ride or Die subtitle tracks and rejected
malformed input. Ten focused MediaLib tests passed, with one optional real-file
fixture test skipped. The required full suite stopped at the existing
`TrackNamingTest` locale assertion (36 tests, one failure). No phone save or
playback behavior was retested for this build-only relocation.

An unchanged native build verified the prebuilts in approximately 0.5 seconds
without recompiling or changing the shared libraries. The new repositories are
initialized locally on `main`; publish the builders and prebuilts before syncing
the updated AVP manifest in a fresh remote checkout.
