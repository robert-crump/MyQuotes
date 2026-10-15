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
    public void explanationListsWordsThenTheAuthorWithTheUnitOnTheFirst() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Thankful",
                Arrays.asList(evidence("gratitude", 6, 9), evidence("grateful", 4, 7)), evidence("Rumi", 4, 5));
        assertEquals("gratitude (6/9 quotes), grateful (4/7) · by Rumi (4/5)",
                HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTheAuthor() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Wisdom",
                Collections.emptyList(), evidence("Seneca", 5, 6));
        assertEquals("by Seneca (5/6 quotes)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void aSmallTagSaysWhatItNeeds() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("AI", 3, false,
                Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList("Not suggested yet: on 3 quotes, needs 5."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void aSuggestableTagListsItsWordsAndAuthors() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Thankful", 12, true,
                Arrays.asList(evidence("gratitude", 6, 9), evidence("grateful", 4, 7)),
                Arrays.asList(evidence("Rumi", 4, 5), evidence("Seneca", 3, 4)));
        assertEquals(Arrays.asList(
                        "Words: This hashtag is suggested for quotes containing gratitude (6/9 quotes), grateful (4/7).",
                        "Author: This hashtag is suggested for quotes by the author(s) "
                                + "Rumi (4/5 quotes have that hashtag), Seneca (3/4)."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void noWordAndNoAuthor() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Life", 8, true,
                Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList(
                        "Words: No word is strong enough yet as a hashtag predictor. Such a word needs to "
                                + "appear in at least 4 quotes tagged #Life, and at least 50% of all quotes "
                                + "with that word need to be tagged #Life."),
                HashtagSuggestionText.suggestions(profile));
    }
}
