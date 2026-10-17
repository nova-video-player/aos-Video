package com.archos.mediacenter.video.utils;

/**
 * JNI bridge to font_name_parser.c/.h -- reads a font file's real family/style names via
 * FreeType (the same library libass uses internally), instead of guessing from the filename.
 *
 * Used by VideoPreferencesCommon to populate the "default subtitle font" list with every
 * selectable (family, style) combination a font file actually contains -- a single .ttf can
 * report multiple names (a .ttc collection bundles several distinct fonts; a variable font
 * like Bahnschrift reports many named instances such as "Bahnschrift Bold",
 * "Bahnschrift SemiCondensed", etc., all under one file).
 *
 * Deliberately NOT part of SubtitleEngine: this is a stateless, one-shot operation on a font
 * file's contents, unrelated to any live native engine handle -- Settings can call it without
 * a SubtitleEngine instance existing at all (which is exactly the situation in the Settings
 * screen, where no video is playing).
 */
public class FontNameParser {

    /** One (family, style) entry extracted from a font file. */
    public static class Entry {
        public final String family;
        public final String style;
        public final int faceIndex;      // which sub-font within a .ttc (0 for plain .ttf/.otf)
        public final int namedInstance;  // 0 = the face's default instance; >0 = a specific
                                          // named instance index within a variable font

        public Entry(String family, String style, int faceIndex, int namedInstance) {
            this.family = family;
            this.style = style;
            this.faceIndex = faceIndex;
            this.namedInstance = namedInstance;
        }

        /**
         * Encodes this entry's (faceIndex, namedInstance) selector into a suffix appended to
         * a stored preference value, so a SPECIFIC entry (not just "this file, whichever
         * default FreeType picks") round-trips back correctly. Format: "#faceIndex.namedInstance"
         * -- omitted entirely when both are 0 (the overwhelmingly common case: a plain
         * single-style .ttf/.otf), so existing stored values from before this feature existed
         * keep working unchanged.
         */
        public String encodeSelector() {
            if (faceIndex == 0 && namedInstance == 0) return "";
            return "#" + faceIndex + "." + namedInstance;
        }

        /** Display label for a Settings list entry: just the style name if it adds
         * information beyond the family (e.g. "Bahnschrift" + "Bold" -> "Bahnschrift Bold"),
         * or just the family if the style is redundant/generic (e.g. "Regular"). */
        public String displayLabel() {
            if (style == null || style.isEmpty())
                return family;
            return family + " " + style;
        }
    }

    /**
     * Parses the font file at `path` and returns every (family, style) entry it contains, in
     * the order FreeType/font_name_parse_file() reported them (each face of a .ttc, and each
     * named instance of a variable font, in turn). Returns an empty array (never null) if the
     * file couldn't be parsed as a font at all -- callers should treat that the same as "no
     * selectable fonts from this file" rather than distinguish it from "valid font, zero
     * entries", since font_name_parse_file() itself doesn't distinguish those either.
     */
    public static Entry[] parse(String path) {
        String[] raw = nativeParseFontFile(path);
        if (raw == null || raw.length == 0) return new Entry[0];

        Entry[] entries = new Entry[raw.length];
        for (int i = 0; i < raw.length; i++) {
            // Fields are \u0001-separated (a control character that will never legitimately
            // appear in a font family/style name) to avoid any ambiguity with delimiters like
            // '|' or ',' that COULD appear in a real family name (e.g. "Roboto,Roboto Medium").
            String[] fields = raw[i].split("\u0001", -1);
            if (fields.length != 4) continue; // malformed record from the native side -- skip rather than crash
            try {
                entries[i] = new Entry(fields[0], fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
            } catch (NumberFormatException e) {
                entries[i] = null;
            }
        }

        // Drop any nulls from skipped/malformed records rather than return a sparse array.
        int validCount = 0;
        for (Entry e : entries) if (e != null) validCount++;
        if (validCount == entries.length) return entries;
        Entry[] compact = new Entry[validCount];
        int j = 0;
        for (Entry e : entries) if (e != null) compact[j++] = e;
        return compact;
    }

    /**
     * Returns one \u0001-delimited "family\u0001style\u0001faceIndex\u0001namedInstance"
     * string per (family, style) entry found in the font file at `path`, or an empty array if
     * the file couldn't be parsed as a font. See font_name_parser.h for the underlying
     * extraction logic (FreeType-based: FT_New_Face + FT_Get_MM_Var for named instances).
     */
    private static native String[] nativeParseFontFile(String path);
}
