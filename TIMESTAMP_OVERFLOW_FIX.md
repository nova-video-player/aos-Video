# Timestamp Overflow Fix - Int to Long Migration

## Issue
Nova Player crashes when playing videos longer than ~13.5 hours due to 32-bit integer overflow in timestamp/duration calculations.

**Root Cause:** Audio timestamps in milliseconds overflow int32_max (2,147,483,647) for videos longer than 24.8 days, but practically cause issues at 13.5 hours due to sample rate calculations.

**Example:** A 20-hour video at 44.1kHz sample rate has `duration_ts = 3,297,391,616` which exceeds int32_max and wraps to negative values.

## Fix Strategy
Change duration and position fields from `int` (32-bit) to `long` (64-bit) throughout the codebase.

## Files Fixed in This PR

### ✅ Completed
1. **VideoMetadata.java** - Core metadata class
   - Line 50: `private long mDuration` (was int)
   - Line 227: `data.getLong(METADATA_KEY_DURATION)` (was getInt)
   - Line 278: `getMetadataRetrieverLong(...)` (was getMetadataRetrieverInt)
   - Line 306-310: `setDuration(long)` and `getDuration()` return long

## Files That Need Updating

This is a comprehensive list of 30+ locations identified that need similar int→long changes:

### Critical Priority (Data Model)

**Video.java** - Video data model
- Line 56: `int mDurationMs` → `long mDurationMs`
- Line 57: `int mResumeMs` → `long mResumeMs`
- Line 58: `int mRemoteResume` → `long mRemoteResume`
- Line 148: `getDurationMs()` return type → long
- Line 150: `getResumeMs()` return type → long
- Line 154: `setResumeMs(long resumeMs)` param → long
- Line 314: `setDuration(long duration)` param → long

**VideoProperties.java** - Video properties
- Line 191: `public int duration` → `public long duration`
- Line 189: `public int resumePosition` → `public long resumePosition`
- Line 187: `public int bookmarkPosition` → `public long bookmarkPosition`
- Line 128: `setDuration(long duration)` param → long
- Line 46: `getDuration()` return type → long
- Line 34: `getBookmarkPosition()` return type → long

**Player.java** - Playback control
- Line 96: `private int mDuration` → `private long mDuration`
- Line 161: `private int mBufferPosition` → `private long mBufferPosition`
- Line 162: `private int mRelativePosition` → `private long mRelativePosition`
- Line 163: `private int mStopPosition` → `private long mStopPosition`
- Line 164: `private int mSaveStopPosition` → `private long mSaveStopPosition`
- Line 757-761: `getDuration()` return type → long
- Line 764-772: `getCurrentPosition()` return type → long
- Line 783: `seekTo(long msec)` param → long

### High Priority (Database Layer)

**VideoCursorMapper.java** - Database reads
- Line 186: `c.getLong(mDurationColumn)` (was getInt)
- Line 187: `c.getLong(mResumeColumn)` (was getInt)
- Line 215: `c.getLong(mDurationColumn)` (was getInt)
- Line 216: `c.getLong(mResumeColumn)` (was getInt)
- Line 240: `c.getLong(mDurationColumn)` (was getInt)
- Line 241: `c.getLong(mResumeColumn)` (was getInt)

**AnimesNShowsMapper.java** - Database reads
- Line 218-219: Use `getLong()` instead of `getInt()` for duration/resume

### Medium Priority (UI/Controllers)

**PlayerActivity.java**
- Line 359: `private int mLastPosition` → `private long mLastPosition`

**PlayerService.java**
- Line 183: `private int mResume` → `private long mResume`
- Line 239: `private int mTorrentFilePosition` → `private long mTorrentFilePosition`

## Database Migration Required

⚠️ **IMPORTANT**: Changing from `cursor.getInt()` to `cursor.getLong()` requires database schema verification:

1. Check `VideoStore.Video.VideoColumns.DURATION` column type
2. Check `VideoStore.Video.VideoColumns.BOOKMARK` column type
3. If columns are INTEGER (SQLite), they can store 64-bit values
4. If migration needed, create database upgrade script

## Testing Checklist

- [ ] Short videos (< 1 hour) still play correctly
- [ ] Medium videos (1-12 hours) play correctly
- [ ] Long videos (13+ hours) no longer crash
- [ ] Seeking works at all positions
- [ ] Resume positions save/restore correctly
- [ ] Database reads/writes work correctly
- [ ] No integer overflow in calculations
- [ ] Backward compatibility with existing database

## Verification

Test with videos at these durations:
- **13h 30m** (48,600s) - Just under old int32 limit
- **13h 32m** (48,720s) - Just over old int32 limit
- **20h 46m** (74,770s) - Real-world test case

Expected: All videos play and seek correctly without crashes.

## Implementation Notes

- Java `long` is 64-bit signed: range ±9.2 quintillion
- Maximum duration at ms precision: ~292 million years
- SQLite INTEGER type stores up to 64-bit values
- Android MediaPlayer API supports long for duration/position
- This is a **breaking API change** - requires major version bump

## Related Issue

Fixes: https://github.com/nova-video-player/aos-AVP/issues/1615

---

**Author:** jloutsch
**Date:** 2025-12-06
