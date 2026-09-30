package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The hashtag rules (#49), pure. A tag is one token of Unicode letters, digits and {@code _},
 * stored without {@code #} and displayed with it. Tags compare case-insensitively; a quote's
 * tag list is deduplicated that way and kept alphabetical.
 */
public final class Hashtag {
    /** Alphabetical, ignoring case. */
    public static final Comparator<String> ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private Hashtag() {}

    public static boolean isTagChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_';
    }

    /** {@code raw} without a leading {@code #} and without any character a tag can't hold. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder tag = new StringBuilder();
        raw.trim().codePoints().filter(Hashtag::isTagChar).forEach(tag::appendCodePoint);
        return tag.toString();
    }

    /**
     * An old free-text category as a tag: words joined in CamelCase ("Life Wisdom" →
     * "LifeWisdom", "self-help" → "SelfHelp"), invalid characters dropped. A single word keeps
     * its spelling. Empty in, empty out.
     */
    public static String fromCategory(String category) {
        if (category == null) return "";
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        category.codePoints().forEach(cp -> {
            if (isTagChar(cp)) {
                word.appendCodePoint(cp);
            } else if (word.length() > 0) {
                words.add(word.toString());
                word.setLength(0);
            }
        });
        if (word.length() > 0) words.add(word.toString());
        if (words.size() == 1) return words.get(0);
        StringBuilder tag = new StringBuilder();
        for (String w : words) {
            int first = w.codePointAt(0);
            tag.appendCodePoint(Character.toUpperCase(first)).append(w.substring(Character.charCount(first)));
        }
        return tag.toString();
    }

    /** Normalized, empty ones dropped, deduplicated case-insensitively (first spelling wins), sorted. */
    public static List<String> normalizeAll(Collection<String> raw) {
        List<String> tags = new ArrayList<>();
        if (raw == null) return tags;
        for (String r : raw) {
            String tag = normalize(r);
            if (!tag.isEmpty() && indexOf(tags, tag) < 0) tags.add(tag);
        }
        tags.sort(ORDER);
        return tags;
    }

    /** Index of {@code tag} in {@code tags}, compared ignoring case; -1 if absent. */
    public static int indexOf(List<String> tags, String tag) {
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).equalsIgnoreCase(tag)) return i;
        }
        return -1;
    }

    public static boolean containsIgnoreCase(List<String> tags, String tag) {
        return indexOf(tags, tag) >= 0;
    }

    /** The spelling {@code tag} already has among {@code existing}, or {@code tag} itself. */
    public static String canonical(String tag, Collection<String> existing) {
        for (String e : existing) {
            if (e.equalsIgnoreCase(tag)) return e;
        }
        return tag;
    }

    /** Lowercased with {@link Locale#ROOT}, for keys and case-insensitive contains. */
    static String key(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }

    /** {@code #Tag}. */
    public static String display(String tag) {
        return "#" + tag;
    }

    /** The card's tag line: {@code #A #B #C}, in list order. */
    public static String line(List<String> tags) {
        StringBuilder line = new StringBuilder();
        for (String tag : tags) {
            if (line.length() > 0) line.append(' ');
            line.append(display(tag));
        }
        return line.toString();
    }

    /** {@code text} without one leading {@code #} (after trimming). */
    public static String stripHash(String text) {
        String t = text == null ? "" : text.trim();
        return t.startsWith("#") ? t.substring(1) : t;
    }
}
