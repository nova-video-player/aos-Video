# Audio Track Selection Specification

This document defines Nova's automatic audio-track selection policy. The
canonical implementation is `PlayerService.onAudioMetadataUpdated()`.

## Scope and preservation of user choices

Automatic selection runs only on first playback when the stored audio-track
index is absent, invalid, or unsupported. A valid saved track is retained.
Manual track choices made from `PlayerActivity` are persisted and therefore
take precedence on later playback.

Only supported audio tracks are candidates. If no policy candidate matches,
Nova falls back to the first supported track, then to the player's reported
track when necessary.

## Automatic-selection order

For an eligible first selection, candidates are considered in this order:

1. **Original language**, when *Prefer original audio track* is enabled and
   scraped `original_language` is known (not `und`). A supported track whose
   language matches this ISO code is selected ahead of the configured favorite
   audio language.
2. **Favorite audio language** (`favAudioLang`). This defaults to the device
   locale unless the user changes it.
3. **First supported track**, when neither language has a supported match.

Therefore enabling the original-language preference affects only media with
usable scraper metadata and a matching track. It never removes the existing
favorite-language fallback.

## Language matching and tie breakers

Language comparison uses `ISO639codes.isFavoriteLanguageMatch()`. It accepts
the common ISO 639-1/2/3 representations and locale variants while comparing
their normalized language identity. `und` is not a usable original language.

When several tracks match the original language, a track marked with the
container's `default` disposition is preferred; otherwise the first matching
track is selected.

When several tracks match `favAudioLang`, the existing richer tie-breakers are
preserved:

1. a matching Chinese title variant (Mainland, Hong Kong, or Taiwan) when the
   favorite language expresses one;
2. a matching track marked `default` by the container;
3. the first matching track.

## Interaction with subtitles

Subtitle selection evaluates the audio track that this policy actually chose,
not the configured favorite language. In particular, the forced-subtitle
rules use the active audio language; see [SUBTITLES.md](SUBTITLES.md).

## Non-goals

This policy does not select unsupported tracks, override a valid saved/manual
selection, or infer an original language from stream titles. Original language
comes from the scraped movie or TV-show metadata exposed by the media database.
