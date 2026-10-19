package com.example.myquotes;

import java.util.ArrayList;
import java.util.List;

/**
 * Words for the why behind hashtag suggestions (#51, #52), from {@link HashtagSuggester}'s
 * evidence. Pure. Evidence is shown as counts, "(6/9)": 6 of the 9 quotes containing the word (or
 * by the author) carry the tag. In the Add/Edit dialog the first count of a line names the unit,
 * "(6/9 quotes)"; the hashtag sheet's lists are bare.
 */
final class HashtagSuggestionText {
    /** The top of the "Why these hashtags" dialog. */
    static final String INTRO = "The app analyzes each of the quote's words. It searches for the word "
            + "in other quotes and looks at their hashtags, trying to find a pattern. The app also "
            + "suggests hashtags that are often used for the author, and hashtags that often appear "
            + "together with the quote's hashtags.";

    private HashtagSuggestionText() {
    }

    /** "heart (6/8)", or with {@code unit}: "heart (6/8 quotes)". */
    static String evidence(HashtagSuggester.Evidence evidence, String unit) {
        return evidence.label + " (" + evidence.withTag + "/" + evidence.total
                + (unit == null ? "" : " " + unit) + ")";
    }

    /**
     * "gratitude (6/9 quotes), grateful (4/7) · by Rumi (4/5) · with #Death (6/9)"; the dialog's
     * line per tag.
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
        if (!explanation.tags.isEmpty()) {
            List<String> tags = new ArrayList<>();
            for (HashtagSuggester.Evidence tag : explanation.tags) {
                tags.add(evidence(tag, line.isEmpty() && tags.isEmpty() ? "quotes" : null));
            }
            String with = "with " + String.join(", ", tags);
            line = line.isEmpty() ? with : line + " · " + with;
        }
        return line;
    }

    /**
     * The sheet's Suggestions section, one paragraph each: why the tag can't be suggested yet, or
     * its Words line and (when they qualify) its Author and With lines.
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
        if (!profile.tags.isEmpty()) {
            lines.add("With: " + list(profile.tags) + ".");
        }
        return lines;
    }

    /**
     * The "No hashtag" list's line (#53): "Suggested: #Gratitude (10/11) #Love (6/8)", each tag
     * with the counts of its strongest reason; null when there are no suggestions.
     */
    static String suggestedLine(List<HashtagSuggester.Suggestion> suggestions) {
        if (suggestions.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (HashtagSuggester.Suggestion s : suggestions) {
            parts.add(Hashtag.display(s.tag) + " (" + s.strongest.withTag + "/" + s.strongest.total + ")");
        }
        return "Suggested: " + String.join(" ", parts);
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
