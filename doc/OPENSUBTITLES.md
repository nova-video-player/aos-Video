# OpenSubtitles Download Strategy in Nova Video Player

## Overview

Nova Video Player downloads external subtitles from the OpenSubtitles REST API on demand. Unlike Trakt, OpenSubtitles does not maintain a long-lived signed-in state in Nova settings. Credentials are stored if the user provides them, but login happens only when validating credentials or when a subtitle download flow is used.

The integration supports:
- **Manual subtitle search and download** for the currently selected video
- **Anonymous mode** when no OpenSubtitles credentials are stored
- **User account mode** when username/password are stored
- **Quota visibility** based on the latest real OpenSubtitles operation
- **Status display in settings** without performing network calls from settings

## Core Components

1. **OpenSubtitlesApiHelper.java** - REST API wrapper, runtime session state, quota parsing, and persisted status helper
2. **OpenSubtitlesCredentialsDialog.java** - Settings dialog used to enter and validate credentials
3. **OpenSubtitlesQueryParams.java** - Query model built from the selected video file and scraped metadata
4. **SubtitlesDownloaderActivity2.java** - End-to-end subtitle search, selection, download, and file write flow
5. **VideoPreferencesCommon.java** - Settings summary for OpenSubtitles credentials/status

## Authentication and Session Model

### Stored Credentials

Credentials are stored in the private `opensubtitles_credentials` SharedPreferences file:

```java
OPENSUBTITLES_USERNAME
OPENSUBTITLES_PASSWORD
```

The presence of stored credentials means "user account configured", not "currently signed in". OpenSubtitles tokens are runtime-only static state in `OpenSubtitlesApiHelper` and are cleared when the downloader logs out.

### Login Timing

Login occurs only during real OpenSubtitles work:

1. **Credential validation** from `OpenSubtitlesCredentialsDialog`
2. **Subtitle download flow** from `SubtitlesDownloaderActivity2`

Settings must not login or ping OpenSubtitles only to render a summary. The settings screen reads the last persisted status.

### Anonymous Mode

If no username is stored, `OpenSubtitlesApiHelper.login(...)` initializes the API key and returns false without treating it as a fatal error. `SubtitlesDownloaderActivity2.logIn()` allows the flow to continue in this case, so search/download requests run without an Authorization header.

If a username is stored and login fails, the download flow stops and records a bad-credentials status.

## Download Flow

### Activity Startup

`SubtitlesDownloaderActivity2` starts with:

1. A required `FILE_URL` intent extra
2. Optional `FILE_SIZE`
3. Optional friendly `FILE_NAME`
4. Preferred subtitle languages from `languages_list`

If there is no network connection, the activity shows the no-network dialog and exits.

### Login Step

The downloader reads credentials from `opensubtitles_credentials` and calls:

```java
OpenSubtitlesApiHelper.login(apiKey, username, password)
```

Behavior:
- Empty username/password: show the "credentials empty" toast, continue as anonymous
- Bad stored username/password: persist `OS_STATUS_BAD_CREDENTIALS`, show login failed, stop
- IOException or service exception: persist `OS_STATUS_NETWORK_ERROR`, show service/login failure, stop

The boolean result of `login()` must be honored. Do not continue after failed user-account login.

### Query Construction

`OpenSubtitlesQueryParams` is populated from both file information and local scraper metadata.

Sources:
- File name or friendly UPnP name
- File size
- OpenSubtitles movie hash when computable
- IMDb/TMDb IDs from `OnlineIdUtils`
- Show season/episode number from `VideoDbInfo`
- Show title from scraper metadata with trailing year removed

Query preference:
1. TMDb ID if available
2. IMDb ID if available
3. File name query as fallback
4. Movie hash is added when available
5. Season/episode parameters are added for shows

`OpenSubtitlesQueryParams.setOnlineId()` must independently guard IMDb and TMDb assignments:

```java
if (onlineId.getImdbId() != null) imdbId = onlineId.getImdbId();
if (onlineId.getTmdbId() != null) tmdbId = onlineId.getTmdbId();
```

## Search Behavior

`OpenSubtitlesApiHelper.searchSubtitle(...)` calls:

```text
GET /api/v1/subtitles
```

Request details:
- `languages` is a comma-separated list of ISO 639-1 language codes
- `order_by=from_trusted,ratings,download_count`
- Authorization header is added only when authenticated
- Only the first result page is queried

Result behavior:
- If exactly one subtitle is found, it is downloaded immediately
- If multiple subtitles are found, a chooser dialog is shown
- If one hash-matched subtitle is found for a single requested language, it is returned as the focused match
- If no subtitle is found after a successful API response, no OpenSubtitles status is overwritten
- If search fails with a non-OK result, status is persisted according to error mapping

## Download Link and Quota

`OpenSubtitlesApiHelper.getDownloadSubtitleLink(fileId)` calls:

```text
POST /api/v1/download
```

The response provides:
- Temporary subtitle download link
- Remaining downloads
- Number of requests/downloads
- Quota reset time

Before requesting a link, Nova avoids the call if `remainingDownloads <= 0` and the reset time is still in the future.

On success, Nova persists `OS_STATUS_OK` with the current quota values and shows the existing quota toast:

```text
OpenSubtitles download quota: X/Y remaining
```

On quota exhaustion, Nova persists `OS_STATUS_QUOTA_EXCEEDED` and shows:

```text
OpenSubtitles download quota exceeded
Quota resets in <time>
```

## Subtitle File Write Flow

Downloaded subtitles are written as `.srt`.

Destination selection:
1. If the source URL is handled by FileCore and is not a slow remote, Nova attempts to write next to the video.
2. Nova performs a real write test because some storage providers report write capability incorrectly.
3. If the write test fails, or the source is slow remote/not FileCore, Nova writes to the local subtitles directory from `MediaUtils.getSubsDir(...)`.
4. If a remote sidecar write succeeds and the video is not local, Nova also copies the file to the local subtitles directory.

File naming:

```text
<video-name>.<language>.srt
```

After a successful write:
- `SUBTITLE_COUNT_EXTERNAL` is incremented in the video DB when possible
- `ACTION_VIDEO_SCANNER_METADATA_UPDATE` is broadcast for the video URI

## Runtime Session Cleanup

`SubtitlesDownloaderActivity2.onStop()`:

1. Cancels the current OpenSubtitles task
2. Calls `OpenSubtitlesApiHelper.logout()` on a background thread
3. Closes the progress dialog
4. Finishes the activity

Logout clears runtime authentication state:

```java
authTokenValid = false;
authenticated = false;
authToken = null;
```

Stored credentials are not removed by logout.

## Persisted Status Model

OpenSubtitles status is persisted in the same `opensubtitles_credentials` SharedPreferences file.

Keys:

```java
PREF_LAST_STATUS
PREF_LAST_STATUS_TIME
PREF_LAST_REMAINING_DOWNLOADS
PREF_LAST_ALLOWED_DOWNLOADS
PREF_LAST_RESET_TIME_UTC
```

Status values:

```java
OS_STATUS_NONE
OS_STATUS_OK
OS_STATUS_BAD_CREDENTIALS
OS_STATUS_QUOTA_EXCEEDED
OS_STATUS_NETWORK_ERROR
```

Persist status only after real OpenSubtitles operations:
- Credential validation
- Download-flow login
- Subtitle search failure with non-OK API result
- Subtitle search Throwable/service outage (OS_STATUS_NETWORK_ERROR)
- Download link success
- Download link quota/error failure

Do not update the status for "no subtitles found" when the API response itself was successful.

## Error Mapping

OpenSubtitles API/helper result codes map to persisted settings status as follows:

| Result | Persisted status |
| --- | --- |
| `RESULT_CODE_OK` | `OS_STATUS_OK` when a real operation succeeds |
| `RESULT_CODE_BAD_CREDENTIALS` | `OS_STATUS_BAD_CREDENTIALS` |
| `RESULT_CODE_TOKEN_EXPIRED` | `OS_STATUS_BAD_CREDENTIALS` |
| `RESULT_CODE_QUOTA_EXCEEDED` | `OS_STATUS_QUOTA_EXCEEDED` |
| `RESULT_CODE_TOO_MANY_REQUESTS` | `OS_STATUS_QUOTA_EXCEEDED` |
| `RESULT_CODE_SERVER_ISSUE` | `OS_STATUS_NETWORK_ERROR` |
| `RESULT_NOT_ENOUGH_PARAMETERS` | `OS_STATUS_NETWORK_ERROR` |
| `RESULT_CODE_BAD_API_KEY` | `OS_STATUS_NETWORK_ERROR` |
| `RESULT_CODE_LINK_GONE` | `OS_STATUS_NETWORK_ERROR` |
| `RESULT_CODE_INVALID_FILE_ID` | `OS_STATUS_NETWORK_ERROR` |

`RESULT_CODE_UNACCEPTABLE` (406) is always refined by `parseResult()` into one of `RESULT_CODE_TOKEN_EXPIRED`, `RESULT_CODE_QUOTA_EXCEEDED`, or `RESULT_CODE_INVALID_FILE_ID` before being returned to callers and does not appear raw in the mapping above.

If `searchSubtitle()` returns null while `RESULT_CODE_OK` is still set, leave the persisted status unchanged.

## Settings Summary Contract

The `subtitles_credentials` preference shows status from persisted prefs only.

Expected summaries:

| Condition | Summary |
| --- | --- |
| Anonymous, no history | Existing static credentials summary |
| Anonymous, quota known | `Anonymous · OpenSubtitles download quota: X/Y remaining` |
| User credentials saved, no check yet | `User account · Not checked yet` |
| User credentials verified, no quota yet | `User account · Credentials verified` |
| User success with quota | `User account · OpenSubtitles download quota: X/Y remaining` |
| Quota exceeded, reset known | `User account · Quota resets in <time>` |
| Quota exceeded, reset unknown/past | `User account · Quota exhausted` |
| Bad credentials with username stored | `User account · Login failed` |
| Bad credentials after dialog cleared username | `Login failed` |
| Network/service failure | `User account · Service unavailable` |

The summary is refreshed:
- When `opensubtitles_credentials` SharedPreferences changes
- When returning to settings via `VideoPreferencesFragment.onResume()`

## Invariants

- Settings must not perform network calls to compute OpenSubtitles status.
- Empty username is anonymous mode, not a fatal login failure.
- Non-empty username with failed login is fatal for the current subtitle flow.
- User-visible quota should reflect the latest real download/link response.
- Successful API search with no subtitle results should not overwrite account/quota status.
- OpenSubtitles auth token is runtime-only and must not be treated as persistent login state.

## Known Limitations

- Only the first OpenSubtitles search result page is queried.
- User credentials are stored as application private SharedPreferences, not an OAuth-style token flow.
- Settings status can become stale if quota changes outside Nova; it is updated only after Nova uses OpenSubtitles.
- Anonymous quota defaults come from in-memory helper defaults until a real API operation returns quota information.
