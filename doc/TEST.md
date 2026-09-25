# Video Module Test Guide

This document describes how to run and create unit/functional tests for the `Video` module, as well as how to use the CLI extraction tool to capture real-world video track selection test cases.

---

## Running the Unit Test Suite

Run tests from the `Video` directory:

```bash
cd Video
./gradlew testNoamazonDebugUnitTest
```

Or run offline when dependencies are already cached:

```bash
./gradlew --offline testNoamazonDebugUnitTest
```

### Test Reports
- **HTML Report**: `Video/build/reports/tests/testNoamazonDebugUnitTest/index.html`
- **JUnit XML Results**: `Video/build/test-results/testNoamazonDebugUnitTest/`

---

## Running Specific Tests

Run a single test class:

```bash
./gradlew testNoamazonDebugUnitTest \
  --tests 'com.archos.mediacenter.video.player.TrackSelectionTest'
```

Run a specific test method:

```bash
./gradlew testNoamazonDebugUnitTest \
  --tests 'com.archos.mediacenter.video.player.TrackSelectionTest.testCsvTrackSelectionSuite'
```

---

## Existing Tests

### TV audio menu disabled controls (`TVMenuItemTest`)

```bash
./gradlew --offline testNoamazonDebugUnitTest \
  --tests 'com.archos.mediacenter.video.player.tvmenu.TVMenuItemTest'
```

Checks that a disabled item cannot invoke its listener through the remote OK
key or its child text view, whether disabled before or after listener attachment,
and that re-enabling restores both paths. This covers the spatialization control
used in the audio tile when passthrough is selected.

### 1. Audio Track Selection Policy (`AudioTrackSelectionPolicyTest`)

**Class**: `com.archos.mediacenter.video.player.AudioTrackSelectionPolicyTest`

Dedicated specification unit tests verifying all rules and tie-breakers in [`AUDIO_TRACK_SELECTION.md`](AUDIO_TRACK_SELECTION.md):
- **Preservation of User Choices**: Valid saved audio track index is retained and not overwritten.
- **Selection Triggers**: Unset (-1), out-of-bounds, or unsupported audio tracks trigger auto-selection.
- **Selection Precedence**: Prefer VO/Original Audio (`KEY_PREFER_ORIGINAL_AUDIO_TRACK`) -> Favorite Audio Language (`favAudioLang`) -> First Supported Track fallback.
- **VO Scraper Integration**: Matches scraped original language; skips undetermined (`und`) original language.
- **Tie-Breakers**: Respects container `default` disposition flags for both VO and favorite language; disambiguates Chinese audio title variants (Mandarin, Cantonese, Taiwan).
- **Unsupported Tracks**: Skips unsupported audio codecs even if the language matches.

### 2. Subtitle Track Selection Policy (`SubtitleTrackSelectionPolicyTest`)

**Class**: `com.archos.mediacenter.video.player.SubtitleTrackSelectionPolicyTest`

Dedicated specification unit tests verifying all rules and the complete decision table in [`SUBTITLES.md`](SUBTITLES.md):
- **Full Examples Table (Rows 1-10)**: Covers English native (full vs forced vs none), French native, foreign audio with full native subtitles, exact sidecar fallback (`dummy.srt` selection for native English audio over competing tagged sidecars), hide subtitles with forced tracks, and single-audio untagged forced tracks.
- **Audio Track Switching**: Changing audio track dynamically switches matching forced subtitles or disables them if none exists, without overriding manual or full subtitle selections.
- **Multi-Audio Untagged Forced Rejection**: Rejects untagged forced subtitles when multiple audio streams are present to prevent language mismatch.
- **Saved Subtitle Preservation**: Retains valid saved subtitle selection when the language at that index matches.
- **Full Subtitle Fallback**: Never falls back to a forced track when full subtitles are requested; falls back to English full subtitle when foreign audio has no native subtitle match.
- **Tie-Breakers**: Respects container `default` disposition and Chinese subtitle title variants (Simplified vs Traditional).

### 3. Data-Driven Track Selection Regression Suite (`TrackSelectionTest`)

**Class**: `com.archos.mediacenter.video.player.TrackSelectionTest`

Data-driven regression runner evaluating curated representative fixtures in `track_selection_tests.csv`. Additional fixtures from real-world media files can be generated and appended using `ffprobe_track_selection_to_csv.sh`. Failures are explicitly reported with row numbers if any malformed rows are encountered.

### 4. Playback Resume Policy (`PlaybackResumePolicyTest`)

**Class**: `com.archos.mediacenter.video.player.PlaybackResumePolicyTest`

Verifies resume bookmarking logic, lifecycle checkpoints vs database state, external resume intents, and "Start from beginning" overrides.

### 5. External Resume Intent Parsing (`ExternalResumeIntentTest`)

**Class**: `com.archos.mediacenter.video.player.ExternalResumeIntentTest`

Verifies intent extra extraction for external video playback launches (position priority, timestamp bounds, redaction in logs).

### 6. Image Transformations (`FidelityTransformationTest`)

**Class**: `com.archos.mediacenter.video.picasso.FidelityTransformationTest`

Verifies Picasso image cropping, aspect ratio preservation, and dimension clamping.

### 7. Sort Order Resolution (`SortUtilsTest`)

**Class**: `com.archos.mediacenter.video.utils.SortUtilsTest`

Verifies dynamic sort clause rewriting across all scopes (`MOVIE`, `SHOW`, `COLLECTION`, `VIDEO_VIEW`) based on the "Ignore initial articles when sorting" setting (`sort_ignore_articles`):
- `VIDEO_VIEW`: verifies null-safe `COALESCE(NULLIF(sort_name, ''), name)` expression for unscraped/mixed media queries when enabled, and raw `name` when disabled.
- Compound search and alpha clauses (e.g. season/episode order preservations).

---

## CLI Track Selection Extraction Tool

A standalone CLI script is provided to inspect video files using `ffprobe` and `jq`, evaluate Nova's selection rules, and optionally append the generated test case to `track_selection_tests.csv`.

**Location**: `Video/src/test/tools/ffprobe_track_selection_to_csv.sh`

### Usage

```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh [OPTIONS] VIDEO_FILE [CSV_FILE]
```

### Available Options

| Option | Description | Default |
|---|---|---|
| `--fav-sub-lang <LANG>` | Preferred subtitle language code (e.g. `en`, `fr`, `ja`, `zh`) | `en` |
| `--fav-audio-lang <LANG>` | Preferred audio language code (e.g. `en`, `fr`, `es`) | `en` |
| `--ui-lang <LOCALE>` | System / UI locale code (e.g. `en`, `fr`, `es`) | `en` |
| `--prefer-vo`, `--prefer-original-audio` | Enable prefer original audio (VO) track setting | `false` |
| `--vo-lang <LANG>`, `--original-audio-lang <LANG>` | Scraper original/VO audio language (e.g. `de`, `is`, `ja`, `fr`) | Auto from container or empty |
| `--sub <FILE>` | Add external sidecar subtitle file (can be repeated) | Auto-discovered from directory |
| `--hide-subtitles`, `--no-subtitles-apart-forced` | Enable "Hide subtitles by default / Forced only" | `false` |
| `--name <NAME>` | Name / description for the test case | Video basename |
| `--append` | Append generated test case directly to CSV file | `false` |
| `--json-out <FILE>` | Save raw ffprobe stream JSON to file | Temporary file |

### Examples

#### 1. German Series with External English and German Subtitles (`Deutschland 83`):
Evaluates original German audio (`de`) with French UI locale and subtitle preference (`fr`). Since no French subtitle exists, it falls back to the external English full subtitle:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --append \
  --prefer-vo \
  --vo-lang de \
  --fav-sub-lang fr \
  --ui-lang fr \
  --sub Deutschland-S01E01-Quantum_Jump-1080p-AVC-AC3-5.1.eng.srt \
  --sub Deutschland-S01E01-Quantum_Jump-1080p-AVC-AC3-5.1.ger.srt \
  Deutschland-S01E01-Quantum_Jump-1080p-AVC-AC3-5.1-short.mkv
```

#### 2. Icelandic Drama with Embedded French & Icelandic Audio and French Subtitles (`Trapped`):
Prefers the original Icelandic audio (`is`) over the default French dub, triggering automatic selection of French full subtitles for the French UI user:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --append \
  --prefer-vo \
  --vo-lang is \
  --fav-sub-lang fr \
  --ui-lang fr \
  Trapped_\(2015\)-S01E01-Episode_1-1080p-AVC-EAC3-5.1-short.mkv
```

#### 3. Anime with Japanese VO Preference and English Subtitles:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --append \
  --name "Anime Japanese VO with English Subtitles" \
  --prefer-vo \
  --vo-lang ja \
  --fav-audio-lang en \
  --fav-sub-lang en \
  --ui-lang en \
  anime_episode.mkv
```

#### 4. Forced-Only Subtitle Selection:
```bash
./Video/src/test/tools/ffprobe_track_selection_to_csv.sh \
  --no-subtitles-apart-forced \
  --fav-audio-lang fr \
  --fav-sub-lang fr \
  --ui-lang fr \
  movie.mkv
```

---

## CSV Fixture Format (`track_selection_tests.csv`)

Rows in `Video/src/test/resources/track_selection_tests.csv` follow the format:

```text
name,audioTracks,subtitleTracks,uiLocale,favAudioLang,favSubLang,hideSubtitles,preferOriginalAudio,originalAudioLang,expectedAudioTrack,expectedSubtitleTrack
```

- **Audio Track Syntax**: `name;lang;format;disposition;supported|...`
  - `disposition`: Bitmask (`1` = Default, `4` = Original/VO, `64` = Forced, `128` = Hearing Impaired)
- **Subtitle Track Syntax**: `name;lang;path;disposition;isGfx;format|...`
  - `disposition`: Bitmask (`1` = Default, `64` = Forced, `128` = Hearing Impaired)
  - `path`: Non-empty if external subtitle file
- **`expectedAudioTrack`**: 0-based index of selected audio track.
- **`expectedSubtitleTrack`**: 0-based index of selected subtitle track, or `-1` for no subtitles.

---

## FileCoreLibrary: Remote Transfer Speed Test (`SpeedTestTransferTest`)

**Class**: `com.archos.filecorelibrary.SpeedTestTransferTest` (module `FileCoreLibrary`)

An opt-in, real-network diagnostic host test that benchmarks download throughput across all remote-file implementations Nova ships — jcifs-ng, smbj, sftp (jsch), sshj, webdav and webdavs — through Nova's actual local httpproxy (`StreamOverHttp`, the same class `SmbProxy` uses to feed the player). It is not a real unit test: it opens real connections to real servers, so it is skipped by default and must never run in CI.

### Why it's opt-in

The test is guarded by an `assumeTrue(...)` check in `@Before` keyed off a system property; if the property isn't set (or doesn't point to a real file), the test is reported as **SKIPPED**, not failed. This follows the same pattern already used by `RealDatabasePruningTest` in `MediaLib` (`-Dnova.test.mediaDbPath`).

### CSV input format

One `url,user,password` row per line (comma-separated). Blank lines and lines starting with `#` are ignored. The scheme of each URL selects the implementation under test:

```text
smb://host/share/path/file      -> jcifs-ng
smbj://host/share/path/file     -> smbj
sftp://host/path/file           -> sftp (jsch)
sshj://host/path/file           -> sshj
webdav://host/path/file         -> webdav (http)
webdavs://host/path/file        -> webdav (https)
```

List the same file twice, once under `smb://` and once under `smbj://` (or `sftp://` / `sshj://`), to compare the two implementations of a given protocol head to head against the same server.

**The CSV must never be committed** — it contains real server addresses and credentials. Keep it outside the repository (e.g. `/absolute/path/to/servers.csv`).

### Running the test

Run from the `Video` directory (`FileCoreLibrary` has no `gradlew` of its own; it's included as a subproject of `Video/settings.gradle`):

```bash
cd Video
./gradlew :FileCoreLibrary:testDebugUnitTest --tests "*SpeedTestTransferTest" \
    -Dnova.test.speedtestCsv=/absolute/path/to/servers.csv
```

Output includes the upstream and HTTP client buffer sizes, followed by a results table with bytes transferred, elapsed seconds, and MiB/s (bytes / 1024² / seconds) per row; failed rows show the exception instead.

### Comparing upstream buffer sizes (host only)

The optional `-Dnova.test.speedtestUpstreamBufferBytes` property sets the proxy's
`BufferedInputStream` size for every CSV row. It defaults to **81920 bytes (80 KiB)**,
matching the general-purpose proxy default. Production playback uses up to 1 MiB for
the primary jcifs stream; this benchmark uses its explicit size for every backend so
comparisons remain controlled. The HTTP client buffer stays at 256 KiB and the proxy's
socket-write buffer stays at 8 KiB. This override does not change the app's settings.
See [buffer.md](buffer.md) for the complete playback and buffering architecture.

From the `Video` directory, compare 80 KiB and 1 MiB with the same dependency and CSV:

```bash
for size in 81920 1048576; do
    ./gradlew :FileCoreLibrary:testDebugUnitTest --rerun-tasks --tests '*SpeedTestTransferTest' \
        -Dnova.test.speedtestCsv=/absolute/path/to/servers.csv \
        -Dnova.test.speedtestUpstreamBufferBytes="$size"
done
```

For the jcifs-ng comparison, select the dependency in `FileCoreLibrary/build.gradle`:

| Version | Multicredit | Read-ahead |
| --- | --- | --- |
| `v2.1.11-upstream` | No | No |
| `v2.1.11-nova7` | Yes | No |
| `v2.1.11-nova8` | Yes | Yes |

Use `smb://` for jcifs-ng and `smbj://` for the smbj reference. First compare buffer sizes
with nova7 to measure the benefit of larger caller reads. Then hold the upstream buffer
at 1 MiB while comparing nova7 and nova8 to assess the additional benefit of read-ahead.
The buffer size is a caller-side setting; negotiated server limits and library behavior
can change actual SMB request sizes.

Repeat runs with the same file and network conditions, reversing the comparison order
to reduce cache/order bias. Compare transferred byte counts as well as throughput, and
inspect warnings and failed rows: this diagnostic does not assert the expected file
length or verify a checksum, so a passing Gradle test alone does not establish transfer
integrity. Host measurements also need confirmation on the target Android device before
changing production settings.

### Test Reports
- **HTML Report**: `FileCoreLibrary/build/reports/tests/testDebugUnitTest/index.html`
- **JUnit XML Results**: `FileCoreLibrary/build/test-results/testDebugUnitTest/`

### Running on a real device

The host test above runs on Robolectric, i.e. the development machine's JVM and JCE providers —
useful for correctness, but meaningless for on-device performance questions (e.g. whether
Conscrypt/ARM crypto extensions actually speed up SFTP/SMB transfers on a given SoC), since the
host JVM never touches the device's ART runtime, Conscrypt provider or network stack.

**Class**: `com.archos.filecorelibrary.SpeedTestTransferTest` (module `FileCoreLibrary`,
`androidTest/java` source set) — an on-device instrumented counterpart of the host test, same
CSV format and scheme-to-implementation mapping. It only builds/installs `FileCoreLibrary` plus
the androidx.test runner as a small instrumentation APK (`com.archos.filecorelibrary.test`), not
the full Nova app (no `Video` assemble required).

Build the androidTest APK:

```bash
cd Video
./gradlew :FileCoreLibrary:assembleDebugAndroidTest
```

`./gradlew :FileCoreLibrary:connectedDebugAndroidTest` (with a Gradle-level `--tests` filter or
`-Pandroid.testInstrumentationRunnerArguments...`) does **not** work for this test: `--tests` is
not a valid flag for `connectedDebugAndroidTest` (that's a JVM `test`-task-only option), and
instrumented tests don't inherit `-D` JVM system properties either way. Instead, install the APK
and drive it directly with `adb shell am instrument`, passing the CSV path as an `-e` runner
argument:

```bash
# Install the self-instrumenting test APK
adb install -r -t FileCoreLibrary/build/outputs/apk/androidTest/debug/FileCoreLibrary-debug-androidTest.apk

# Seed the CSV into the test package's private data dir (NOT /sdcard/Android/data/<pkg>/ -
# scoped storage rejects reads there even after chmod on some API levels/devices). run-as writes
# it as the app's own UID so it's guaranteed readable by the test process.
adb push /absolute/path/to/servers.csv /sdcard/Download/servers.csv
adb shell "cat /sdcard/Download/servers.csv | run-as com.archos.filecorelibrary.test sh -c 'mkdir -p files && cat > files/servers.csv'"

# Run just SpeedTestTransferTest
adb shell am instrument -w -r \
  -e class com.archos.filecorelibrary.SpeedTestTransferTest \
  -e speedtestCsv /data/user/0/com.archos.filecorelibrary.test/files/servers.csv \
  com.archos.filecorelibrary.test/androidx.test.runner.AndroidJUnitRunner
```

This requires a connected/authorized device (`adb devices`). `compareTransferRates` output
(`System.out.println`) is not shown by `am instrument` — read it from logcat after the run:

```bash
adb logcat -d -s System.out
```

The device benchmark accepts `-e speedtestUpstreamBufferBytes SIZE`, with the same
81920-byte default as the host diagnostic. After installing and seeding the CSV,
compare candidate sizes without rebuilding the APK:

```bash
for size in 262144 524288 1048576; do
    adb shell am instrument -w -r \
        -e class com.archos.filecorelibrary.SpeedTestTransferTest \
        -e speedtestCsv /data/user/0/com.archos.filecorelibrary.test/files/servers.csv \
        -e speedtestUpstreamBufferBytes "$size" \
        com.archos.filecorelibrary.test/androidx.test.runner.AndroidJUnitRunner
done
adb logcat -d -s System.out
```

Each run prints its buffer sizes. Repeat in reverse order and compare complete byte
counts and warnings as well as MiB/s. This tests backend/proxy throughput on Android;
startup, seeking, stop/reopen, concurrent scraping, and AVOS memory/GC behavior still
require real playback checks.

Re-run after reinstalling the APK (e.g. after code changes): reinstalling wipes the app's private
data dir, so the `run-as ... cat > files/servers.csv` seeding step must be repeated.

#### Cleartext localhost networking

`StreamOverHttp` (the local httpproxy `compareTransferRates` drives requests through) serves
plaintext HTTP on `http://localhost:<port>`. A bare androidTest APK has no network security
config, so API 28+ blocks this by default with `Cleartext HTTP traffic to localhost not
permitted`. This is already handled by `FileCoreLibrary/androidTest/AndroidManifest.xml` +
`FileCoreLibrary/androidTest/res/xml/network_security_config_test.xml`, which permit cleartext
**only** to `localhost`/`127.0.0.1` (not a blanket allow) — scoped to the androidTest APK, the
main app manifest/config is untouched.

### Test Reports (on-device)
- **HTML Report**: `FileCoreLibrary/build/reports/androidTests/connected/debug/index.html`
- **JUnit XML Results**: `FileCoreLibrary/build/outputs/androidTest-results/connected/debug/`
  (only populated when run via `connectedDebugAndroidTest`; `am instrument` directly does not
  write these)
