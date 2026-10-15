package com.example.myquotes;

import java.util.ArrayList;
import java.util.List;

/**
 * Words for the why behind hashtag suggestions (#51), from {@link HashtagSuggester}'s evidence.
 * Pure. Evidence is shown as counts ("heart (6 of 8)": 6 of the 8 quotes containing the word
 * carry the tag), never as a percentage: the score only ranks.
 */
final class HashtagSuggestionText {
    private HashtagSuggestionText() {
    }

    /** "heart (6 of 8)". */
    static String evidence(HashtagSuggester.Evidence evidence) {
        return evidence.label + " (" + evidence.withTag + " of " + evidence.total + ")";
    }

    /** "heart (6 of 8), lieben (3 of 4) · by Rumi (4 of 5)"; the Add/Edit popup's line per tag. */
    static String explanation(HashtagSuggester.Explanation explanation) {
        List<String> parts = new ArrayList<>();
        for (HashtagSuggester.Evidence word : explanation.words) parts.add(evidence(word));
        String line = String.join(", ", parts);
        if (explanation.author != null) {
            String by = "by " + evidence(explanation.author);
            line = line.isEmpty() ? by : line + " · " + by;
        }
        return line;
    }

    /** The sheet's status line: whether and how the tag is suggested. */
    static String status(HashtagSuggester.TagProfile profile) {
        if (!profile.suggestable) {
            return "Not suggested yet: on " + quotes(profile.quotes) + ", needs "
                    + HashtagSuggester.MIN_QUOTES_PER_TAG + ".";
        }
        StringBuilder status = new StringBuilder("Suggested when a quote shares at least "
                + HashtagSuggester.MIN_SUPPORTING_TOKENS + " telling words with its quotes.");
        for (HashtagSuggester.Evidence author : profile.authors) {
            status.append("\nSuggested for every quote by ").append(author.label).append(" (")
                    .append(author.withTag).append(" of ").append(author.total).append(" have it).");
        }
        return status.toString();
    }

    /** "6 of 8 quotes with “heart” have #Love"; one line per word in the sheet. */
    static String word(HashtagSuggester.Evidence word, String tag) {
        return word.withTag + " of " + quotes(word.total) + " with “" + word.label + "” "
                + (word.withTag == 1 ? "has " : "have ") + Hashtag.display(tag);
    }

    static String quotes(int count) {
        return count == 1 ? "1 quote" : count + " quotes";
    }
}
