package com.example.myquotes.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SentencePiece's precompiled normalization (the "Precompiled" normalizer of a Hugging Face
 * {@code tokenizer.json}, NFKC plus a few SentencePiece rules), read from its base64
 * {@code precompiled_charsmap}.
 *
 * <p>The map is a Darts double-array trie over UTF-8 bytes whose values point into a blob of
 * NUL-terminated replacement strings. Like Hugging Face's {@code spm_precompiled}, each grapheme
 * cluster shorter than 6 UTF-8 bytes is first looked up as a whole, using the shortest match in
 * the trie (so "ﬁ" plus a combining accent becomes plain "fi"); otherwise each code point is
 * looked up on its own and kept when it has no entry.
 */
final class PrecompiledCharsMap {

    private final int[] trie;
    private final byte[] normalized;

    PrecompiledCharsMap(String base64) {
        ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(base64)).order(ByteOrder.LITTLE_ENDIAN);
        int trieBytes = buffer.getInt();
        trie = new int[trieBytes / 4];
        for (int i = 0; i < trie.length; i++) trie[i] = buffer.getInt();
        normalized = new byte[buffer.remaining()];
        buffer.get(normalized);
    }

    String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int start = 0;
        while (start < text.length()) {
            int end = GraphemeClusters.end(text, start);
            String cluster = text.substring(start, end);
            String whole = utf8Length(cluster) < 6 ? transform(cluster) : null;
            if (whole != null) {
                out.append(whole);
            } else {
                for (int i = start; i < end; ) {
                    int cp = text.codePointAt(i);
                    int next = i + Character.charCount(cp);
                    String part = transform(text.substring(i, next));
                    out.append(part != null ? part : text.substring(i, next));
                    i = next;
                }
            }
            start = end;
        }
        return out.toString();
    }

    /** The replacement of the shortest prefix of {@code chunk} in the trie, or null. */
    private String transform(String chunk) {
        byte[] key = chunk.getBytes(StandardCharsets.UTF_8);
        int value = firstPrefixValue(key);
        if (value < 0) return null;
        int end = value;
        while (end < normalized.length && normalized[end] != 0) end++;
        return new String(normalized, value, end - value, StandardCharsets.UTF_8);
    }

    /** Darts common prefix search, stopping at the first (shortest) match. */
    private int firstPrefixValue(byte[] key) {
        int nodePos = 0;
        int unit = trie[nodePos];
        nodePos ^= offset(unit);
        for (byte b : key) {
            int c = b & 0xFF;
            if (c == 0) break;
            nodePos ^= c;
            if (nodePos < 0 || nodePos >= trie.length) return -1;
            unit = trie[nodePos];
            if (label(unit) != c) return -1;
            nodePos ^= offset(unit);
            if (hasLeaf(unit)) return value(trie[nodePos]);
        }
        return -1;
    }

    private static boolean hasLeaf(int unit) {
        return ((unit >>> 8) & 1) == 1;
    }

    private static int value(int unit) {
        return unit & 0x7FFFFFFF;
    }

    private static int label(int unit) {
        return unit & (0x80000000 | 0xFF);
    }

    private static int offset(int unit) {
        return (unit >>> 10) << ((unit & (1 << 9)) >>> 6);
    }

    static int utf8Length(String s) {
        int length = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            length += cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            i += Character.charCount(cp);
        }
        return length;
    }
}
