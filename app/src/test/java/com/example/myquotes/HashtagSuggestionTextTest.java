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
                Arrays.asList(evidence("gratitude", 6, 9), evidence("grateful", 4, 7)), evidence("Rumi", 4, 5),
                Arrays.asList(evidence("#Grief", 4, 4), evidence("#Death", 6, 9)));
        assertEquals("gratitude (6/9 quotes), grateful (4/7) · by Rumi (4/5) · with #Grief (4/4), #Death (6/9)",
                HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTheAuthor() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Wisdom",
                Collections.emptyList(), evidence("Seneca", 5, 6), Collections.emptyList());
        assertEquals("by Seneca (5/6 quotes)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTags() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Life",
                Collections.emptyList(), null, Collections.singletonList(evidence("#Death", 6, 9)));
        assertEquals("with #Death (6/9 quotes)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void aSmallTagSaysWhatItNeeds() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("AI", 3, false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList("Not suggested yet: on 3 quotes, needs 5."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void aSuggestableTagListsItsWordsAndAuthors() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Thankful", 12, true,
                Arrays.asList(evidence("gratitude", 6, 9), evidence("grateful", 4, 7)),
                Arrays.asList(evidence("Rumi", 4, 5), evidence("Seneca", 3, 4)),
                Arrays.asList(evidence("#Grief", 4, 4), evidence("#Death", 6, 9)));
        assertEquals(Arrays.asList(
                        "Words: gratitude (6/9), grateful (4/7).",
                        "Author: Rumi (4/5), Seneca (3/4).",
                        "With: #Grief (4/4), #Death (6/9)."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void noWordAndNoAuthor() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Life", 8, true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList(
                        "Words: No word is strong enough yet to serve as a predictor for this hashtag."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void suggestedLineShowsEachTagWithItsStrongestCounts() {
        assertEquals("Suggested: #Gratitude (10/11) #Love (6/8)", HashtagSuggestionText.suggestedLine(Arrays.asList(
                new HashtagSuggester.Suggestion("Gratitude", evidence("grateful", 10, 11)),
                new HashtagSuggester.Suggestion("Love", evidence("Rumi", 6, 8)))));
    }

    @Test
    public void suggestedLineIsNullWithoutSuggestions() {
        assertEquals(null, HashtagSuggestionText.suggestedLine(Collections.emptyList()));
    }
}
