package com.example.myquotes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Words for the why behind hashtag suggestions (#51, #52), from {@link HashtagSuggester}'s
 * evidence. Pure. Evidence is shown as counts, "(6/9)": 6 of the 9 quotes containing the word (or
 * by the author, or from the source) carry the tag. In the Add/Edit dialog the first count of a line names the unit,
 * "(6/9 quotes)"; the hashtag sheet's lists are bare.
 */
final class HashtagSuggestionText {
    /** The top of the "Why these hashtags" dialog. */
    static final String INTRO = "The app analyzes each of the quote's words. It searches for the word "
            + "in other quotes and looks at their hashtags, trying to find a pattern. The app also "
            + "suggests hashtags that are often used for the author or the source, hashtags that "
            + "often appear together with the quote's hashtags, hashtags of similar quotes (the "
            + "quotes closest in meaning, found by an on-device language model), and hashtags whose "
            + "name is in the quote. Tap \"similar to …\" to see those quotes.";

    /** The hashtag sheet's note that the similar-quotes reason exists (#63); it has no per-tag counts. */
    static final String ALSO_SIMILAR = "Also suggested by similar quotes.";

    /** Characters of a neighbour quote shown in the Why dialog (#63), before the ellipsis. */
    static final int NEIGHBOUR_CHARS = 60;

    private HashtagSuggestionText() {
    }

    /** "heart (6/8)", or with {@code unit}: "heart (6/8 quotes)". */
    static String evidence(HashtagSuggester.Evidence evidence, String unit) {
        return evidence.label + " (" + evidence.withTag + "/" + evidence.total
                + (unit == null ? "" : " " + unit) + ")";
    }

    /**
     * "gratitude (6/9 quotes), grateful (4/7) · by Rumi (4/5) · from Masnavi (3/4) · with #Death (6/9)
     * · similar to 4 of the 10 closest tagged quotes · contains the tag's name"; the dialog's line per
     * tag. Similar quotes are counts, never a similarity score (#62).
     */
    static String explanation(HashtagSuggester.Explanation explanation) {
        List<String> parts = new ArrayList<>();
        for (HashtagSuggester.Evidence word : explanation.words) {
            parts.add(evidence(word, parts.isEmpty() ? "quotes" : null));
        }
        String line = String.join(", ", parts);
        if (explanation.author != null) {
            String by = "by " + evidence(explanation.author, line.isEmpty() ? "quotes" : null);
            line = line.isEmpty() ? by : line + " · " + by;
        }
        if (explanation.source != null) {
            String from = "from " + evidence(explanation.source, line.isEmpty() ? "quotes" : null);
            line = line.isEmpty() ? from : line + " · " + from;
        }
        if (!explanation.tags.isEmpty()) {
            List<String> tags = new ArrayList<>();
            for (HashtagSuggester.Evidence tag : explanation.tags) {
                tags.add(evidence(tag, line.isEmpty() && tags.isEmpty() ? "quotes" : null));
            }
            String with = "with " + String.join(", ", tags);
            line = line.isEmpty() ? with : line + " · " + with;
        }
        if (explanation.similar != null) {
            String similar = similar(explanation.similar);
            line = line.isEmpty() ? similar : line + " · " + similar;
        }
        if (explanation.containsName) {
            String name = "contains the tag's name";
            line = line.isEmpty() ? name : line + " · " + name;
        }
        return line;
    }

    /**
     * The sheet's Suggestions section, one paragraph each: why the tag can't be suggested yet, or
     * its Words line, (when they qualify) its Author, Source and With lines, and {@link #ALSO_SIMILAR}.
     */
    static List<String> suggestions(HashtagSuggester.TagProfile profile) {
        List<String> lines = new ArrayList<>();
        if (!profile.suggestable) {
            lines.add("Not suggested yet: on " + quotes(profile.quotes) + ", needs "
                    + HashtagSuggester.MIN_QUOTES_PER_TAG + ".");
            return lines;
        }
        if (profile.words.isEmpty()) {
            lines.add("Words: No word is strong enough yet to serve as a predictor for this hashtag.");
        } else {
            lines.add("Words: " + list(profile.words) + ".");
        }
        if (!profile.authors.isEmpty()) {
            lines.add("Author: " + list(profile.authors) + ".");
        }
        if (!profile.sources.isEmpty()) {
            lines.add("Source: " + list(profile.sources) + ".");
        }
        if (!profile.tags.isEmpty()) {
            lines.add("With: " + list(profile.tags) + ".");
        }
        lines.add(ALSO_SIMILAR);
        return lines;
    }

    /**
     * The "No hashtag" list's line (#53): "Suggested: #Gratitude (10/11) #Love (6/8) #Nature (name)",
     * each tag with the counts of its strongest reason ("name" when that is the tag's name in the
     * quote, "#Grief (4/10, based on similar quotes)" when it is similar quotes, so it doesn't read as
     * a word count, #63); null when there are no suggestions.
     */
    static String suggestedLine(List<HashtagSuggester.Suggestion> suggestions) {
        if (suggestions.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (HashtagSuggester.Suggestion s : suggestions) {
            parts.add(Hashtag.display(s.tag) + (s.strongest.nameMatch ? " (name)"
                    : " (" + s.strongest.withTag + "/" + s.strongest.total
                            + (s.strongest.similar ? ", based on similar quotes" : "") + ")"));
        }
        return "Suggested: " + String.join(" ", parts);
    }

    /**
     * One tag's "Suggested for" list line (#56): "#Stoicism: virtue (4/6 quotes) · by Seneca (3/4)",
     * the tag's {@link #explanation}.
     */
    static String suggestedForLine(HashtagSuggester.Explanation explanation) {
        return Hashtag.display(explanation.tag) + ": " + explanation(explanation);
    }

    /**
     * The end of the "Why these hashtags" dialog (#55): "Not suggested for this quote: #Hope, #Love",
     * the rejected tags the quote doesn't have ({@code tags}); null when there are none.
     */
    static String notSuggestedLine(List<String> rejected, List<String> tags) {
        List<String> parts = new ArrayList<>();
        for (String tag : rejected) {
            if (!Hashtag.containsIgnoreCase(tags, tag)) parts.add(Hashtag.display(tag));
        }
        return parts.isEmpty() ? null : "Not suggested for this quote: " + String.join(", ", parts);
    }

    /** "similar to 4 of the 10 closest tagged quotes". */
    static String similar(HashtagSuggester.Evidence similar) {
        return "similar to " + similar.withTag + " of the " + similar.total + " closest tagged "
                + (similar.total == 1 ? "quote" : "quotes");
    }

    /**
     * The Why dialog's lines under an expanded "similar to ..." (#63): one per neighbour with the tag,
     * most similar first, its text on one line and cut to {@link #NEIGHBOUR_CHARS} characters with
     * "…". Ids without a text in {@code textsById} (deleted meanwhile) are skipped.
     */
    static List<String> neighbourLines(List<Integer> ids, Map<Integer, String> textsById) {
        List<String> lines = new ArrayList<>();
        for (Integer id : ids) {
            String text = textsById.get(id);
            if (text == null) continue;
            lines.add("• " + snippet(text));
        }
        return lines;
    }

    /**
     * The text with runs of whitespace as one space, cut to at most {@link #NEIGHBOUR_CHARS} with
     * "…": at the last word break when that keeps at least half, else mid-word.
     */
    static String snippet(String text) {
        String flat = text.trim().replaceAll("\\s+", " ");
        if (flat.codePointCount(0, flat.length()) <= NEIGHBOUR_CHARS) return flat;
        int end = flat.offsetByCodePoints(0, NEIGHBOUR_CHARS);
        if (flat.charAt(end) != ' ') {
            int space = flat.lastIndexOf(' ', end);
            if (space >= end / 2) end = space;
        }
        return flat.substring(0, end).trim() + "…";
    }

    /** "gratitude (6/9), grateful (4/7)". */
    private static String list(List<HashtagSuggester.Evidence> evidence) {
        List<String> parts = new ArrayList<>();
        for (HashtagSuggester.Evidence e : evidence) parts.add(evidence(e, null));
        return String.join(", ", parts);
    }

    static String quotes(int count) {
        return count == 1 ? "1 quote" : count + " quotes";
    }
}
