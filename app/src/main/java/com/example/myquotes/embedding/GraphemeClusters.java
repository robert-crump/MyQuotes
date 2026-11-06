package com.example.myquotes.embedding;

/**
 * A small subset of the Unicode extended grapheme cluster rules (UAX #29), enough for
 * {@link PrecompiledCharsMap}: it only treats clusters shorter than 6 UTF-8 bytes as a unit, and
 * those can only be CR LF or a base followed by combining marks, variation selectors, ZWJ/ZWNJ
 * or similar extenders. Longer clusters (emoji sequences, flags, Hangul jamo) are handled code
 * point by code point either way, so they don't need their rules here. Written out instead of
 * {@link java.text.BreakIterator} so the result is the same on every JDK and on Android.
 */
final class GraphemeClusters {

    private GraphemeClusters() {
    }

    /** The end (exclusive, in chars) of the cluster that starts at {@code start}. */
    static int end(String text, int start) {
        int base = text.codePointAt(start);
        int end = start + Character.charCount(base);
        if (base == '\r') {
            return end < text.length() && text.charAt(end) == '\n' ? end + 1 : end;
        }
        if (isControl(base)) return end;
        while (end < text.length()) {
            int cp = text.codePointAt(end);
            if (!isExtend(cp)) break;
            end += Character.charCount(cp);
        }
        return end;
    }

    private static boolean isControl(int cp) {
        if (cp == 0x200C || cp == 0x200D) return false;
        int type = Character.getType(cp);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static boolean isExtend(int cp) {
        if (cp == 0x200C || cp == 0x200D) return true;                  // ZWNJ, ZWJ
        if (cp >= 0x1F3FB && cp <= 0x1F3FF) return true;                // emoji skin tones
        if (cp == 0xFF9E || cp == 0xFF9F) return true;                  // halfwidth (semi-)voiced marks
        if (cp >= 0xE0020 && cp <= 0xE007F) return true;                // tag characters
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }
}
