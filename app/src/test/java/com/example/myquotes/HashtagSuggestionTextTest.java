package com.example.myquotes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HashtagSuggestionTextTest {
    private static HashtagSuggester.Evidence evidence(String label, int withTag, int total) {
        return new HashtagSuggester.Evidence(label, withTag, total);
    }

    @Test
    public void explanationListsWordsThenTheAuthor() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Love",
                Arrays.asList(evidence("heart", 6, 8), evidence("lieben", 3, 4)), evidence("Rumi", 4, 5));
        assertEquals("heart (6 of 8), lieben (3 of 4) · by Rumi (4 of 5)",
                HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTheAuthor() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Wisdom",
                Collections.emptyList(), evidence("Seneca", 5, 6));
        assertEquals("by Seneca (5 of 6)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void statusOfASmallTagSaysWhatItNeeds() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("AI", 3, false,
                Collections.emptyList(), Collections.emptyList());
        assertEquals("Not suggested yet: on 3 quotes, needs 5.", HashtagSuggestionText.status(profile));
    }

    @Test
    public void statusOfASuggestableTagNamesItsAuthors() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Love", 12, true,
                Collections.emptyList(), Collections.singletonList(evidence("Rumi", 4, 5)));
        assertEquals("Suggested when a quote shares at least 2 telling words with its quotes."
                        + "\nSuggested for every quote by Rumi (4 of 5 have it).",
                HashtagSuggestionText.status(profile));
    }

    @Test
    public void wordLine() {
        assertEquals("6 of 8 quotes with “heart” have #Love",
                HashtagSuggestionText.word(evidence("heart", 6, 8), "Love"));
        assertEquals("1 of 1 quote with “heart” has #Love",
                HashtagSuggestionText.word(evidence("heart", 1, 1), "Love"));
    }
}
