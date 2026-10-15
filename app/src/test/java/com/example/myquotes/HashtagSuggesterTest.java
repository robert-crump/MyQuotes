package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class HashtagSuggesterTest {
    private final List<Quote> quotes = new ArrayList<>();

    private Quote add(String author, String text, String... tags) {
        Quote q = new Quote(quotes.size() + 1, author, text, "");
        q.setTags(Arrays.asList(tags));
        quotes.add(q);
        return q;
    }

    // Each author once, so the author token supports nothing.
    private void addTagged(int count, String text, String tag) {
        for (int i = 0; i < count; i++) add("Author " + tag + i, text, tag);
    }

    // Unrelated untagged quotes, so the tags' words are rare in the collection.
    private void addFiller(int count) {
        for (int i = 0; i < count; i++) add("Filler " + i, "money business office market");
    }

    private List<String> suggest(String text) {
        return HashtagSuggester.fit(quotes).suggest(text, "Someone New", "", Collections.emptyList());
    }

    @Test
    public void aTagOnFewerThanFiveQuotesIsNeverSuggested() {
        addTagged(4, "forest river mountain", "Nature");
        addFiller(10);
        assertEquals(Collections.emptyList(), suggest("forest river mountain"));

        addTagged(1, "forest river mountain", "Nature");
        assertEquals(Collections.singletonList("Nature"), suggest("forest river mountain"));
    }

    @Test
    public void oneMatchingWordIsNotEnoughButTwoAre() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        assertEquals(Collections.emptyList(), suggest("A walk in the forest."));
        assertEquals(Collections.singletonList("Nature"), suggest("A forest by the river."));
        assertEquals(Arrays.asList("forest", "river"),
                HashtagSuggester.fit(quotes).supportingTokens("A forest by the river.", "", "Nature"));
    }

    @Test
    public void theAuthorAloneIsEnoughAtThreeQuotesAndSixtyPercent() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        add("Emerson", "trust thyself", "Nature");
        add("Emerson", "hitch your wagon", "Nature");
        add("Emerson", "every wall is a door");
        add("Emerson", "nothing great was achieved");
        add("Emerson", "to be great is to be misunderstood", "Nature");
        HashtagSuggester model = HashtagSuggester.fit(quotes);
        assertEquals(Collections.singletonList("Nature"),
                model.suggest("gold coin", "  emerson ", "", Collections.emptyList()));
    }

    @Test
    public void theAuthorAloneIsNotEnoughBelowSixtyPercent() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        add("Emerson", "trust thyself", "Nature");
        add("Emerson", "hitch your wagon", "Nature");
        add("Emerson", "every wall is a door");
        add("Emerson", "nothing great was achieved");
        add("Emerson", "to be great is to be misunderstood");
        HashtagSuggester model = HashtagSuggester.fit(quotes);
        assertEquals(Collections.emptyList(), model.suggest("gold coin", "Emerson", "", Collections.emptyList()));
    }

    @Test
    public void theAuthorAloneIsNotEnoughBelowThreeQuotes() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        add("Emerson", "trust thyself", "Nature");
        add("Emerson", "hitch your wagon", "Nature");
        HashtagSuggester model = HashtagSuggester.fit(quotes);
        assertEquals(Collections.emptyList(), model.suggest("gold coin", "Emerson", "", Collections.emptyList()));
    }

    @Test
    public void tagsTheQuoteHasAreExcludedIgnoringCase() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        HashtagSuggester model = HashtagSuggester.fit(quotes);
        assertEquals(Collections.emptyList(),
                model.suggest("forest river", "X", "", Collections.singletonList("nature")));
        assertEquals(Collections.emptyList(),
                model.suggest("forest river", "X", "", Collections.singletonList("#NATURE")));
    }

    @Test
    public void atMostThreeBestScoreFirst() {
        // Every word is on its own tag's quotes only, so each supporting word adds the same lift:
        // Zeta has 5 supporting words, Yota 4, Xeno 3, Wolf 2.
        addTagged(5, "apple banana cherry damson elder", "Zeta");
        addTagged(5, "grape guava lemon lime", "Yota");
        addTagged(5, "mango melon olive", "Xeno");
        addTagged(5, "peach pear", "Wolf");
        addFiller(10);
        String text = "peach pear mango melon olive grape guava lemon lime apple banana cherry damson elder";
        assertEquals(Arrays.asList("Zeta", "Yota", "Xeno"), suggest(text));
    }

    @Test
    public void equalScoresAreOrderedByTagName() {
        addTagged(5, "peach pear", "Beta");
        addTagged(5, "mango melon", "Alpha");
        addFiller(10);
        assertEquals(Arrays.asList("Alpha", "Beta"), suggest("peach pear mango melon"));
    }

    @Test
    public void stopwordsAndShortWordsDoNotCount() {
        assertEquals(new LinkedHashSet<>(Collections.singletonList("forest")),
                HashtagSuggester.tokens("The forest is where we go, and they are ours.", null));
        assertEquals(new LinkedHashSet<>(Arrays.asList("wald", "bäume")),
                HashtagSuggester.tokens("Der Wald und die Bäume, ich bin immer da.", ""));

        addTagged(5, "the and forest", "Nature");
        addFiller(10);
        assertEquals(Collections.emptyList(), suggest("the and forest"));
    }

    @Test
    public void theStemMergesWordForms() {
        assertEquals(new LinkedHashSet<>(Collections.singletonList("friend")),
                HashtagSuggester.tokens("Friendship, friendships and a friend.", null));
        assertEquals(new LinkedHashSet<>(Arrays.asList("freund", "author:goethe")),
                HashtagSuggester.tokens("Freundschaft und Freunde", " Goethe "));

        addTagged(5, "true friendship lasting companionship", "Friends");
        addFiller(10);
        assertEquals(Collections.singletonList("Friends"), suggest("Friendships are companions."));
    }

    @Test
    public void fittingWithoutTheEditedQuoteStopsItsOwnWordsFromVoting() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        Quote edited = add("Anon", "zebra giraffe savanna", "Nature");

        assertTrue(HashtagSuggester.fit(quotes)
                .suggest(edited.getQuoteText(), "Anon", "", Collections.emptyList()).contains("Nature"));

        List<Quote> others = new ArrayList<>(quotes);
        others.remove(edited);
        assertEquals(Collections.emptyList(), HashtagSuggester.fit(others)
                .suggest(edited.getQuoteText(), "Anon", "", Collections.emptyList()));
    }

    @Test
    public void anEmptyCollectionOrTextGivesNothing() {
        assertEquals(Collections.emptyList(), HashtagSuggester.fit(Collections.emptyList())
                .suggest("forest river", "X", "", Collections.emptyList()));

        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        HashtagSuggester model = HashtagSuggester.fit(quotes);
        assertEquals(Collections.emptyList(), model.suggest("", "", "", Collections.emptyList()));
        assertEquals(Collections.emptyList(), model.suggest(null, null, null, null));
    }

    @Test
    public void explainGivesSupportingWordsAsWrittenWithCounts() {
        addTagged(5, "forest river mountain", "Nature");
        add("Someone", "a river of money", "Nature");
        addFiller(10);
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes)
                .explain("Forests by the River.", "Nobody", "nature");
        assertEquals("Nature", why.tag);
        // "forests" stems to "forest" (5 of 5); "river" is on 6 of 6. Equal lift: by stem.
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("forests", 5, 5),
                new HashtagSuggester.Evidence("river", 6, 6)), why.words);
        assertEquals(null, why.author);
    }

    @Test
    public void explainNamesTheAuthorWhenTheAuthorExceptionHolds() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        add("Emerson", "trust thyself", "Nature");
        add("Emerson", "hitch your wagon", "Nature");
        add("Emerson", "every wall is a door");
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes).explain("gold coin", " Emerson ", "Nature");
        assertEquals(Collections.emptyList(), why.words);
        assertEquals(new HashtagSuggester.Evidence("Emerson", 2, 3), why.author);
    }

    @Test
    public void explainIsNullForATagThatCannotBeSuggested() {
        addTagged(4, "forest river mountain", "Nature");
        assertEquals(null, HashtagSuggester.fit(quotes).explain("forest river", "", "Nature"));
    }

    @Test
    public void profileShowsTheStrongestWordsInTheirMostFrequentForm() {
        addTagged(3, "friendships forever", "Friends");
        addTagged(2, "friendship forever loyal", "Friends");
        add("Someone", "loyal dog", "Friends");
        addFiller(10);
        HashtagSuggester.TagProfile profile = HashtagSuggester.fit(quotes).profile("#friends");
        assertTrue(profile.suggestable);
        assertEquals(6, profile.quotes);
        // "dog" is on only 1 of its quotes, so it is left out.
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("forever", 5, 5),
                new HashtagSuggester.Evidence("friendships", 5, 5),
                new HashtagSuggester.Evidence("loyal", 3, 3)), profile.words);
        assertEquals(Collections.emptyList(), profile.authors);
    }

    @Test
    public void profileListsAuthorsTheExceptionHoldsFor() {
        addTagged(5, "forest river mountain", "Nature");
        add("Emerson", "trust thyself", "Nature");
        add("Emerson", "hitch your wagon", "Nature");
        add("Emerson", "every wall is a door");
        assertEquals(Collections.singletonList(new HashtagSuggester.Evidence("Emerson", 2, 3)),
                HashtagSuggester.fit(quotes).profile("Nature").authors);
    }

    @Test
    public void profileOfASmallTagSaysItIsNotSuggestable() {
        addTagged(3, "forest river mountain", "Nature");
        HashtagSuggester.TagProfile profile = HashtagSuggester.fit(quotes).profile("Nature");
        assertEquals(false, profile.suggestable);
        assertEquals(3, profile.quotes);
        assertEquals(Collections.emptyList(), profile.words);
    }
}
