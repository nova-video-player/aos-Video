#!/usr/bin/env bash
# Copyright 2026 Courville Software
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VIDEO_DIR="$(cd "$SCRIPT_DIR/../../.." && pwd)"
DEFAULT_CSV="$VIDEO_DIR/src/test/resources/track_selection_tests.csv"

fav_sub_lang="en"
fav_audio_lang="en"
ui_lang="en"
hide_subtitles=false
prefer_original_audio=false
original_audio_lang=""
test_name=""
append=false
json_out=""
explicit_subs=()

usage() {
    cat <<EOFU
Usage: $0 [OPTIONS] VIDEO [CSV]

Extract audio and subtitle track metadata with ffprobe, evaluate Nova's track
selection logic (including VO / prefer original audio, external sidecars, and forced-only subtitles),
and generate CSV test rows for TrackSelectionTest.

Options:
  --fav-sub-lang LANG         Preferred subtitle language (default: en)
  --fav-audio-lang LANG       Preferred audio language (default: en)
  --ui-lang LOCALE            UI / system locale language (default: en)
  --hide-subtitles,           Enable "Hide subtitles by default / No subtitle apart forced"
    --no-subtitles-apart-forced
  --prefer-vo,                Prefer original audio (VO) track over favorite language
    --prefer-original-audio
  --vo-lang,                  Original audio language from scraper/metadata (e.g. ja, fr, de, is)
    --original-audio-lang LANG
  --sub FILE                  Add external subtitle file (can be repeated)
  --name NAME                 Test case name (default: video filename)
  --append                    Append generated row to CSV file
  --json-out FILE             Save raw ffprobe stream JSON to FILE
  -h, --help                  Show this help message

Default CSV target:
  $DEFAULT_CSV
EOFU
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --fav-sub-lang)
            fav_sub_lang="${2:-}"
            shift 2
            ;;
        --fav-audio-lang)
            fav_audio_lang="${2:-}"
            shift 2
            ;;
        --ui-lang)
            ui_lang="${2:-}"
            shift 2
            ;;
        --hide-subtitles|--no-subtitles-apart-forced)
            hide_subtitles=true
            shift
            ;;
        --prefer-vo|--prefer-original-audio)
            prefer_original_audio=true
            shift
            ;;
        --vo-lang|--original-audio-lang)
            original_audio_lang="${2:-}"
            shift 2
            ;;
        --sub)
            explicit_subs+=("${2:-}")
            shift 2
            ;;
        --name)
            test_name="${2:-}"
            shift 2
            ;;
        --append)
            append=true
            shift
            ;;
        --json-out)
            json_out="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            break
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
        *)
            break
            ;;
    esac
done

if [[ $# -lt 1 || $# -gt 2 ]]; then
    usage >&2
    exit 2
fi

video="$1"
csv="${2:-$DEFAULT_CSV}"

if [[ ! -f "$video" ]]; then
    echo "Video not found: $video" >&2
    exit 1
fi

if ! command -v ffprobe >/dev/null 2>&1; then
    echo "ffprobe is required" >&2
    exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required" >&2
    exit 1
fi

if [[ -z "$test_name" ]]; then
    test_name="$(basename "$video")"
fi

json_file="$json_out"
tmp_json=""
if [[ -z "$json_file" ]]; then
    tmp_json="$(mktemp "${TMPDIR:-/tmp}/track-sel-ffprobe.XXXXXX.json")"
    json_file="$tmp_json"
fi

cleanup() {
    if [[ -n "$tmp_json" ]]; then
        rm -f "$tmp_json"
    fi
}
trap cleanup EXIT

ffprobe -v error -show_streams -of json "$video" > "$json_file"

lower() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

iso639_1() {
    local code
    code="$(lower "$1")"
    case "$code" in
        en|eng) echo "en" ;;
        fr|fre|fra) echo "fr" ;;
        es|spa) echo "es" ;;
        de|ger|deu) echo "de" ;;
        it|ita) echo "it" ;;
        ja|jpn) echo "ja" ;;
        ko|kor) echo "ko" ;;
        is|ice|isl) echo "is" ;;
        zh|chi|zho|cmn|yue|"zh-cn"|"zh-tw"|"zh-hk") echo "zh" ;;
        pt|por|"pt-br") echo "pt" ;;
        ru|rus) echo "ru" ;;
        ar|ara) echo "ar" ;;
        nl|dut|nld) echo "nl" ;;
        pl|pol) echo "pl" ;;
        sv|swe) echo "sv" ;;
        tr|tur) echo "tr" ;;
        und|unknown|"") echo "" ;;
        *) echo "$code" ;;
    esac
}

fav_sub_lang="$(iso639_1 "$fav_sub_lang")"
fav_audio_lang="$(iso639_1 "$fav_audio_lang")"
ui_lang="$(iso639_1 "$ui_lang")"
original_audio_lang="$(iso639_1 "$original_audio_lang")"

lang_matches() {
    local l1 l2
    l1="$(iso639_1 "$1")"
    l2="$(iso639_1 "$2")"
    [[ -n "$l1" && -n "$l2" && "$l1" == "$l2" ]]
}

is_forced_track() {
    local disp="$1"
    local title="$2"
    local path="${3:-}"
    if (( (disp & 64) != 0 )); then
        return 0
    fi
    local lower_title
    lower_title="$(lower "$title")"
    if [[ "$lower_title" == *"forced"* ]]; then
        return 0
    fi
    local lower_path
    lower_path="$(lower "$path")"
    if [[ -n "$lower_path" && "$lower_path" == *"forced"* ]]; then
        return 0
    fi
    return 1
}

format_label() {
    local codec="$1"
    local type="$2"
    case "$type:$codec" in
        audio:eac3) echo "EAC3" ;;
        audio:ac3) echo "AC3" ;;
        audio:dts) echo "DTS" ;;
        audio:aac) echo "AAC" ;;
        audio:mp3) echo "MP3" ;;
        audio:opus) echo "OPUS" ;;
        audio:vorbis) echo "VORBIS" ;;
        audio:flac) echo "FLAC" ;;
        subtitle:ass|subtitle:ssa) echo "SSA" ;;
        subtitle:subrip|subtitle:text|subtitle:mov_text) echo "TEXT" ;;
        subtitle:webvtt) echo "VTT" ;;
        subtitle:hdmv_pgs_subtitle) echo "PGS" ;;
        *) printf '%s\n' "$codec" | tr '[:lower:]' '[:upper:]' ;;
    esac
}

extract_sub_lang_from_path() {
    local sub_file="$1"
    local video_file="$2"
    local sub_base video_base
    sub_base="$(basename "$sub_file")"
    video_base="$(basename "$video_file")"
    local video_stem="${video_base%.*}"
    local sub_stem="${sub_base%.*}"

    # Exact sidecar
    if [[ "$sub_stem" == "$video_stem" ]]; then
        echo ""
        return
    fi

    # Subtitle with suffix (e.g. video.eng.srt -> eng)
    if [[ "$sub_stem" == "$video_stem".* ]]; then
        local suffix="${sub_stem#"$video_stem".}"
        local lang_part="${suffix%%.*}"
        if [[ "$lang_part" != "forced" && "$lang_part" != "default" ]]; then
            echo "$lang_part"
            return
        fi
    fi

    # Check for .eng, .ger, .fre, .fra, .fra, .spa, .ita, etc in sub_base
    local lower_name
    lower_name="$(lower "$sub_base")"
    for code in eng fra fre ger deu spa ita jpn kor chi zho is isl ice rus ara por pol dut swe tur; do
        if [[ "$lower_name" =~ [._-]${code}[._-] ]]; then
            echo "$code"
            return
        fi
    done
    echo ""
}

# Parse audio tracks into arrays
audio_titles=()
audio_langs=()
audio_formats=()
audio_disps=()
audio_supporteds=()
detected_original_lang=""

while IFS= read -r row; do
    [[ -z "$row" ]] && continue
    title="$(printf '%s' "$row" | jq -r '.[0]')"
    lang="$(printf '%s' "$row" | jq -r '.[1]')"
    codec="$(printf '%s' "$row" | jq -r '.[2]')"
    mask="$(printf '%s' "$row" | jq -r '.[3]')"
    fmt="$(format_label "$codec" "audio")"
    audio_titles+=("$title")
    audio_langs+=("$lang")
    audio_formats+=("$fmt")
    audio_disps+=("$mask")
    audio_supporteds+=(true)
    if (( (mask & 4) != 0 )) && [[ -z "$detected_original_lang" ]]; then
        detected_original_lang="$lang"
    fi
done < <(jq -c '
  def bit($name; $value): if (.disposition[$name] // 0) == 1 then $value else 0 end;
  [ .streams[] | select(.codec_type == "audio") ] | .[]
  | [
      (.tags.title // ""),
      (.tags.language // "und"),
      .codec_name,
      (bit("default"; 1) + bit("dub"; 2) + bit("original"; 4) + bit("comment"; 8) + bit("forced"; 64) + bit("hearing_impaired"; 128))
    ]
' "$json_file")

# Parse embedded subtitle tracks into arrays
sub_titles=()
sub_langs=()
sub_paths=()
sub_disps=()
sub_is_gfxs=()
sub_formats=()

while IFS= read -r row; do
    [[ -z "$row" ]] && continue
    title="$(printf '%s' "$row" | jq -r '.[0]')"
    lang="$(printf '%s' "$row" | jq -r '.[1]')"
    codec="$(printf '%s' "$row" | jq -r '.[2]')"
    mask="$(printf '%s' "$row" | jq -r '.[3]')"
    fmt="$(format_label "$codec" "subtitle")"
    is_gfx=false
    if [[ "$codec" == "hdmv_pgs_subtitle" || "$codec" == "dvd_subtitle" ]]; then
        is_gfx=true
    fi
    sub_titles+=("$title")
    sub_langs+=("$lang")
    sub_paths+=("")
    sub_disps+=("$mask")
    sub_is_gfxs+=("$is_gfx")
    sub_formats+=(0)
done < <(jq -c '
  def bit($name; $value): if (.disposition[$name] // 0) == 1 then $value else 0 end;
  [ .streams[] | select(.codec_type == "subtitle") ] | .[]
  | [
      (.tags.title // ""),
      (.tags.language // "und"),
      .codec_name,
      (bit("default"; 1) + bit("forced"; 64) + bit("hearing_impaired"; 128))
    ]
' "$json_file")

# Discover external subtitle files if requested or present in directory
ext_files=()
if (( ${#explicit_subs[@]} > 0 )); then
    ext_files=("${explicit_subs[@]}")
else
    video_dir="$(dirname "$video")"
    video_base="$(basename "$video")"
    video_stem="${video_base%.*}"
    # Look for sidecars
    for ext in srt sub vtt ass ssa; do
        for f in "$video_dir/$video_stem"*."$ext"; do
            [[ -f "$f" ]] && ext_files+=("$f")
        done
    done
fi

for ext_file in "${ext_files[@]}"; do
    [[ ! -f "$ext_file" ]] && continue
    sub_name="$(basename "$ext_file")"
    detected_lang="$(extract_sub_lang_from_path "$ext_file" "$video")"
    [[ -z "$detected_lang" ]] && detected_lang="und"
    disp=0
    if is_forced_track 0 "$sub_name" "$ext_file"; then
        disp=64
    fi
    sub_titles+=("$sub_name")
    sub_langs+=("$detected_lang")
    sub_paths+=("$ext_file")
    sub_disps+=("$disp")
    sub_is_gfxs+=(false)
    sub_formats+=(0)
done

nb_audio="${#audio_langs[@]}"
nb_subs="${#sub_langs[@]}"

if [[ -z "$original_audio_lang" && -n "$detected_original_lang" ]]; then
    original_audio_lang="$(iso639_1 "$detected_original_lang")"
fi

# Determine Audio Selection
selected_audio=0
orig_lang_match=""
orig_default_match=""
lang_match=""
default_match=""
first_supported=""

for ((i = 0; i < nb_audio; i++)); do
    if [[ -z "$first_supported" ]]; then
        first_supported="$i"
    fi
    # Check original language match (if prefer VO is enabled)
    if [[ "$prefer_original_audio" == "true" && -n "$original_audio_lang" ]]; then
        if lang_matches "$original_audio_lang" "${audio_langs[$i]}"; then
            [[ -z "$orig_lang_match" ]] && orig_lang_match="$i"
            if (( (audio_disps[i] & 1) != 0 )) && [[ -z "$orig_default_match" ]]; then
                orig_default_match="$i"
            fi
        fi
    fi
    # Check favorite audio language match
    if lang_matches "$fav_audio_lang" "${audio_langs[$i]}"; then
        [[ -z "$lang_match" ]] && lang_match="$i"
        if (( (audio_disps[i] & 1) != 0 )) && [[ -z "$default_match" ]]; then
            default_match="$i"
        fi
    fi
done

if [[ -n "$orig_default_match" ]]; then
    selected_audio="$orig_default_match"
elif [[ -n "$orig_lang_match" ]]; then
    selected_audio="$orig_lang_match"
elif [[ -n "$default_match" ]]; then
    selected_audio="$default_match"
elif [[ -n "$lang_match" ]]; then
    selected_audio="$lang_match"
elif [[ -n "$first_supported" ]]; then
    selected_audio="$first_supported"
fi

# Determine Subtitle Selection
active_audio_lang="${audio_langs[$selected_audio]:-und}"
selected_audio_is_in_ui_locale=false
if lang_matches "$ui_lang" "$active_audio_lang"; then
    selected_audio_is_in_ui_locale=true
fi

find_forced_sub() {
    local match=""
    local def_match=""
    local unk_match=""
    local unk_def_match=""
    for ((i = 0; i < nb_subs; i++)); do
        if ! is_forced_track "${sub_disps[$i]}" "${sub_titles[$i]}" "${sub_paths[$i]}"; then
            continue
        fi
        local is_def=false
        if (( (sub_disps[i] & 1) != 0 )); then
            is_def=true
        fi
        if lang_matches "$active_audio_lang" "${sub_langs[$i]}"; then
            [[ -z "$match" ]] && match="$i"
            [[ -z "$def_match" && "$is_def" == "true" ]] && def_match="$i"
        elif (( nb_audio == 1 )) && [[ "${sub_langs[$i]}" == "und" || "${sub_langs[$i]}" == "unknown" || -z "${sub_langs[$i]}" ]]; then
            [[ -z "$unk_match" ]] && unk_match="$i"
            [[ -z "$unk_def_match" && "$is_def" == "true" ]] && unk_def_match="$i"
        fi
    done
    if [[ -n "$def_match" ]]; then echo "$def_match"; return; fi
    if [[ -n "$match" ]]; then echo "$match"; return; fi
    if [[ -n "$unk_def_match" ]]; then echo "$unk_def_match"; return; fi
    if [[ -n "$unk_match" ]]; then echo "$unk_match"; return; fi
    echo "-1"
}

find_exact_default_sidecar() {
    local video_base="$(basename "$video")"
    local video_stem="${video_base%.*}"
    for ((i = 0; i < nb_subs; i++)); do
        if [[ -n "${sub_paths[$i]}" ]] && ! is_forced_track "${sub_disps[$i]}" "${sub_titles[$i]}" "${sub_paths[$i]}"; then
            local sub_base="$(basename "${sub_paths[$i]}")"
            local sub_stem="${sub_base%.*}"
            if [[ "$sub_stem" == "$video_stem" ]]; then
                echo "$i"
                return
            fi
        fi
    done
    echo "-1"
}

selected_sub="-1"

if [[ "$hide_subtitles" == "true" ]]; then
    selected_sub="$(find_forced_sub)"
elif [[ "$selected_audio_is_in_ui_locale" == "true" ]] && lang_matches "$fav_sub_lang" "$ui_lang"; then
    # Native speaker case: suppress full subtitles, only keep matching forced subtitles or untagged exact sidecar
    forced_candidate="$(find_forced_sub)"
    if (( forced_candidate >= 0 )); then
        selected_sub="$forced_candidate"
    else
        sidecar_candidate="$(find_exact_default_sidecar)"
        if (( sidecar_candidate >= 0 )); then
            selected_sub="$sidecar_candidate"
        else
            selected_sub="-1"
        fi
    fi
else
    # Regular full subtitle search
    sub_lang_match=""
    sub_def_match=""
    english_match=""
    english_def_match=""
    fallback_text=""

    for ((i = 0; i < nb_subs; i++)); do
        if is_forced_track "${sub_disps[$i]}" "${sub_titles[$i]}" "${sub_paths[$i]}"; then
            continue
        fi
        is_def=false
        if (( (sub_disps[i] & 1) != 0 )); then
            is_def=true
        fi
        if lang_matches "$fav_sub_lang" "${sub_langs[$i]}"; then
            [[ -z "$sub_lang_match" ]] && sub_lang_match="$i"
            if [[ "$is_def" == "true" && -z "$sub_def_match" ]]; then
                sub_def_match="$i"
            fi
        fi
        if lang_matches "en" "${sub_langs[$i]}"; then
            [[ -z "$english_match" ]] && english_match="$i"
            if [[ "$is_def" == "true" && -z "$english_def_match" ]]; then
                english_def_match="$i"
            fi
        fi
        if [[ -n "${sub_paths[$i]}" && -z "$fallback_text" ]]; then
            fallback_text="$i"
        fi
    done

    if [[ -n "$sub_def_match" ]]; then
        selected_sub="$sub_def_match"
    elif [[ -n "$sub_lang_match" ]]; then
        selected_sub="$sub_lang_match"
    elif [[ "$selected_audio_is_in_ui_locale" == "false" && -n "$english_def_match" ]]; then
        selected_sub="$english_def_match"
    elif [[ "$selected_audio_is_in_ui_locale" == "false" && -n "$english_match" ]]; then
        selected_sub="$english_match"
    elif [[ -n "$fallback_text" ]]; then
        selected_sub="$fallback_text"
    else
        selected_sub="-1"
    fi
fi

# Build audioTracks serialized string
audio_str=""
for ((i = 0; i < nb_audio; i++)); do
    entry="${audio_titles[$i]};${audio_langs[$i]};${audio_formats[$i]};${audio_disps[$i]};${audio_supporteds[$i]}"
    if [[ -z "$audio_str" ]]; then
        audio_str="$entry"
    else
        audio_str="$audio_str|$entry"
    fi
done

# Build subtitleTracks serialized string
sub_str=""
for ((i = 0; i < nb_subs; i++)); do
    entry="${sub_titles[$i]};${sub_langs[$i]};${sub_paths[$i]};${sub_disps[$i]};${sub_is_gfxs[$i]};${sub_formats[$i]}"
    if [[ -z "$sub_str" ]]; then
        sub_str="$entry"
    else
        sub_str="$sub_str|$entry"
    fi
done

csv_quote() {
    local val="${1//\"/\"\"}"
    printf '"%s"' "$val"
}

csv_line="$(csv_quote "$test_name"),$(csv_quote "$audio_str"),$(csv_quote "$sub_str"),$(csv_quote "$ui_lang"),$(csv_quote "$fav_audio_lang"),$(csv_quote "$fav_sub_lang"),$hide_subtitles,$prefer_original_audio,$(csv_quote "$original_audio_lang"),$selected_audio,$selected_sub"

echo "=== Track Analysis for '$video' ==="
echo "Audio tracks ($nb_audio):"
for ((i = 0; i < nb_audio; i++)); do
    marker=" "
    (( i == selected_audio )) && marker="*"
    flags=""
    (( (audio_disps[i] & 1) != 0 )) && flags+="/default"
    (( (audio_disps[i] & 4) != 0 )) && flags+="/original(VO)"
    echo "  $marker [$i] Lang: ${audio_langs[$i]}, Format: ${audio_formats[$i]}, Disp: ${audio_disps[$i]} $flags, Title: '${audio_titles[$i]}'"
done

echo "Subtitle tracks ($nb_subs):"
for ((i = 0; i < nb_subs; i++)); do
    marker=" "
    (( i == selected_sub )) && marker="*"
    forced=""
    if is_forced_track "${sub_disps[$i]}" "${sub_titles[$i]}" "${sub_paths[$i]}"; then
        forced="[FORCED]"
    fi
    ext_label=""
    [[ -n "${sub_paths[$i]}" ]] && ext_label="[EXTERNAL: $(basename "${sub_paths[$i]}")]"
    echo "  $marker [$i] Lang: ${sub_langs[$i]}, Disp: ${sub_disps[$i]}, Title: '${sub_titles[$i]}' $forced $ext_label"
done

echo "Settings:"
echo "  UI Language:            $ui_lang"
echo "  Favorite Audio:         $fav_audio_lang"
echo "  Favorite Subtitle:      $fav_sub_lang"
echo "  Hide Subs / Forced Only:$hide_subtitles"
echo "  Prefer VO (Original):   $prefer_original_audio (VO Lang: '${original_audio_lang:-none}')"
echo "Result:"
echo "  Selected Audio:         $selected_audio [int, ${audio_langs[$selected_audio]:-none}]"
if (( selected_sub >= 0 )); then
    sub_type="int"
    [[ -n "${sub_paths[$selected_sub]}" ]] && sub_type="ext"
    echo "  Selected Subtitle:      $selected_sub [$sub_type, ${sub_langs[$selected_sub]:-unknown}]"
else
    echo "  Selected Subtitle:      none (-1)"
fi
echo ""

if [[ "$append" == "true" ]]; then
    echo "$csv_line" >> "$csv"
    echo "Appended row to $csv"
else
    echo "CSV Row:"
    echo "$csv_line"
fi
