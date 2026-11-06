package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HashtagSuggestionTextTest {
    private static HashtagSuggester.Evidence evidence(String label, int withTag, int total) {
        return new HashtagSuggester.Evidence(label, withTag, total);
    }

    @Test
    public void explanationListsWordsThenTheAuthorWithTheUnitOnTheFirst() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Thankful",
                Arrays.asList(evidence("gratitude", 6, 9), evidence("grateful", 4, 7)), evidence("Rumi", 4, 5),
                evidence("Masnavi", 3, 4), Arrays.asList(evidence("#Grief", 4, 4), evidence("#Death", 6, 9)),
                true);
        assertEquals("gratitude (6/9 quotes), grateful (4/7) · by Rumi (4/5) · from Masnavi (3/4)"
                        + " · with #Grief (4/4), #Death (6/9) · contains the tag's name",
                HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTheAuthor() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Wisdom",
                Collections.emptyList(), evidence("Seneca", 5, 6), null, Collections.emptyList(), false);
        assertEquals("by Seneca (5/6 quotes)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTheSource() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Stoicism",
                Collections.emptyList(), null, evidence("Meditations", 3, 4), Collections.emptyList(), false);
        assertEquals("from Meditations (3/4 quotes)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTheName() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Nature",
                Collections.emptyList(), null, null, Collections.emptyList(), true);
        assertEquals("contains the tag's name", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlyTags() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Life",
                Collections.emptyList(), null, null, Collections.singletonList(evidence("#Death", 6, 9)), false);
        assertEquals("with #Death (6/9 quotes)", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithSimilarQuotesBeforeTheName() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Grief",
                Collections.singletonList(evidence("loss", 4, 6)), null, null, Collections.emptyList(), true,
                HashtagSuggester.Evidence.similar(4, 10), Arrays.asList(7, 3, 9, 1));
        assertEquals("loss (4/6 quotes) · similar to 4 of the 10 closest tagged quotes · contains the tag's name",
                HashtagSuggestionText.explanation(why));
    }

    @Test
    public void explanationWithOnlySimilarQuotes() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Grief",
                Collections.emptyList(), null, null, Collections.emptyList(), false,
                HashtagSuggester.Evidence.similar(3, 7), Arrays.asList(2, 5, 8));
        assertEquals("similar to 3 of the 7 closest tagged quotes", HashtagSuggestionText.explanation(why));
    }

    @Test
    public void aSmallTagSaysWhatItNeeds() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("AI", 3, false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList("Not suggested yet: on 3 quotes, needs 5."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void aSuggestableTagListsItsWordsAndAuthors() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Thankful", 12, true,
                Arrays.asList(evidence("gratitude", 6, 9), evidence("grateful", 4, 7)),
                Arrays.asList(evidence("Rumi", 4, 5), evidence("Seneca", 3, 4)),
                Collections.singletonList(evidence("Masnavi", 3, 4)),
                Arrays.asList(evidence("#Grief", 4, 4), evidence("#Death", 6, 9)));
        assertEquals(Arrays.asList(
                        "Words: gratitude (6/9), grateful (4/7).",
                        "Author: Rumi (4/5), Seneca (3/4).",
                        "Source: Masnavi (3/4).",
                        "With: #Grief (4/4), #Death (6/9).",
                        "Also suggested by similar quotes."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void noWordAndNoAuthor() {
        HashtagSuggester.TagProfile profile = new HashtagSuggester.TagProfile("Life", 8, true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertEquals(Arrays.asList(
                        "Words: No word is strong enough yet to serve as a predictor for this hashtag.",
                        "Also suggested by similar quotes."),
                HashtagSuggestionText.suggestions(profile));
    }

    @Test
    public void suggestedLineShowsEachTagWithItsStrongestCounts() {
        assertEquals("Suggested: #Gratitude (10/11) #Love (6/8)", HashtagSuggestionText.suggestedLine(Arrays.asList(
                new HashtagSuggester.Suggestion("Gratitude", evidence("grateful", 10, 11)),
                new HashtagSuggester.Suggestion("Love", evidence("Rumi", 6, 8)))));
    }

    @Test
    public void suggestedLineMarksANameMatch() {
        assertEquals("Suggested: #Love (6/8) #Nature (name)", HashtagSuggestionText.suggestedLine(Arrays.asList(
                new HashtagSuggester.Suggestion("Love", evidence("Rumi", 6, 8)),
                new HashtagSuggester.Suggestion("Nature", HashtagSuggester.Evidence.nameMatch("natur")))));
    }

    @Test
    public void suggestedLineIsNullWithoutSuggestions() {
        assertEquals(null, HashtagSuggestionText.suggestedLine(Collections.emptyList()));
    }

    @Test
    public void notSuggestedLineListsTheRejectedTagsTheQuoteDoesNotHave() {
        assertEquals("Not suggested for this quote: #Hope, #Love", HashtagSuggestionText.notSuggestedLine(
                Arrays.asList("Hope", "Love", "Zen"), Arrays.asList("zen")));
        assertEquals(null, HashtagSuggestionText.notSuggestedLine(
                Collections.singletonList("Zen"), Arrays.asList("Zen")));
    }

    @Test
    public void suggestedForLineNamesTheTagThenItsExplanation() {
        HashtagSuggester.Explanation why = new HashtagSuggester.Explanation("Stoicism",
                Collections.singletonList(evidence("virtue", 4, 6)), evidence("Seneca", 3, 4), null,
                Collections.emptyList(), false);
        assertEquals("#Stoicism: virtue (4/6 quotes) \u00b7 by Seneca (3/4)",
                HashtagSuggestionText.suggestedForLine(why));
    }

    @Test
    public void suggestedLineExplainsSimilarQuotesCounts() {
        assertEquals("Suggested: #Grief (4/10, based on similar quotes) #Love (6/8)",
                HashtagSuggestionText.suggestedLine(Arrays.asList(
                        new HashtagSuggester.Suggestion("Grief", HashtagSuggester.Evidence.similar(4, 10)),
                        new HashtagSuggester.Suggestion("Love", evidence("Rumi", 6, 8)))));
    }

    @Test
    public void introMentionsSimilarQuotesAndTheTap() {
        assertTrue(HashtagSuggestionText.INTRO.contains("hashtags of similar quotes"));
        assertTrue(HashtagSuggestionText.INTRO.endsWith("Tap \"similar to \u2026\" to see those quotes."));
    }

    @Test
    public void neighbourLinesAreSnippetsMostSimilarFirstSkippingDeletedQuotes() {
        Map<Integer, String> texts = new HashMap<>();
        texts.put(3, "Grief is love\n  with nowhere to go.");
        texts.put(7, "What we have once enjoyed we can never lose; all that we love deeply becomes a part of us.");
        assertEquals(Arrays.asList(
                        "\u2022 What we have once enjoyed we can never lose; all that we\u2026",
                        "\u2022 Grief is love with nowhere to go."),
                HashtagSuggestionText.neighbourLines(Arrays.asList(7, 9, 3), texts));
    }

    @Test
    public void snippetKeepsShortTextsAndCutsLongOnesAtSixtyCharacters() {
        String sixty = "123456789 123456789 123456789 123456789 123456789 123456789 ";
        assertEquals(sixty.trim(), HashtagSuggestionText.snippet(sixty));
        assertEquals("123456789 123456789 123456789 123456789 123456789 123456789\u2026",
                HashtagSuggestionText.snippet(sixty + "more"));
        // A surrogate pair counts as one character and is never split.
        String face = new String(Character.toChars(0x1F600));
        assertEquals(face.repeat(60) + "\u2026", HashtagSuggestionText.snippet(face.repeat(61)));
    }
}
