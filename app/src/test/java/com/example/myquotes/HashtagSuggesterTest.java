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

    // Each author once, so the author is never a reason.
    private void addTagged(int count, String text, String tag) {
        for (int i = 0; i < count; i++) add("Author " + quotes.size(), text, tag);
    }

    private void addUntagged(int count, String text) {
        for (int i = 0; i < count; i++) add("Untagged " + quotes.size(), text);
    }

    // Unrelated untagged quotes.
    private void addFiller(int count) {
        addUntagged(count, "money business office market");
    }

    private List<String> suggest(String text) {
        return HashtagSuggester.fit(quotes).suggest(text, "Someone New", "", Collections.emptyList());
    }

    private void addEmerson(int withTag, int without) {
        for (int i = 0; i < withTag; i++) add("Emerson", "trust thyself " + i, "Nature");
        for (int i = 0; i < without; i++) add("Emerson", "every wall is a door " + i);
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
    public void oneQualifyingWordIsEnough() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        assertEquals(Collections.singletonList("Nature"), suggest("A walk in the forest."));
    }

    @Test
    public void aWordNeedsFourQuotesWithTheTag() {
        addTagged(3, "forest", "Nature");
        addTagged(2, "river", "Nature");
        addFiller(10);
        assertEquals(Collections.emptyList(), suggest("forest"));

        addTagged(1, "forest", "Nature");
        assertEquals(Collections.singletonList("Nature"), suggest("forest"));
    }

    @Test
    public void aWordNeedsHalfOfItsQuotesWithTheTag() {
        addTagged(5, "forest", "Nature");
        addUntagged(5, "forest");
        assertEquals(Collections.singletonList("Nature"), suggest("forest"));

        addUntagged(1, "forest");
        assertEquals(Collections.emptyList(), suggest("forest"));
    }

    @Test
    public void theAuthorIsAReasonAtTwoQuotesAndHalf() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        addEmerson(2, 2);
        assertEquals(Collections.singletonList("Nature"), HashtagSuggester.fit(quotes)
                .suggest("gold coin", "  emerson ", "", Collections.emptyList()));

        addEmerson(0, 1);
        assertEquals(Collections.emptyList(), HashtagSuggester.fit(quotes)
                .suggest("gold coin", "Emerson", "", Collections.emptyList()));
    }

    @Test
    public void anAuthorWithOneTaggedQuoteIsNoReason() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        addEmerson(1, 0);
        assertEquals(Collections.emptyList(), HashtagSuggester.fit(quotes)
                .suggest("gold coin", "Emerson", "", Collections.emptyList()));
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
    public void atMostThreeRankedByTheStrongestReasonsShare() {
        addTagged(5, "grape", "Yota");                       // grape 5/5
        addTagged(4, "peach", "Wolf");
        addTagged(1, "plum", "Wolf");
        addUntagged(3, "peach");                             // peach 4/7
        addTagged(5, "apple banana cherry", "Zeta");
        addUntagged(4, "apple banana cherry");               // each 5/9, three of them
        addTagged(6, "mango", "Xeno");
        addUntagged(6, "mango");                             // mango 6/12
        assertEquals(Arrays.asList("Yota", "Wolf", "Zeta"),
                suggest("mango apple banana cherry peach grape"));
    }

    @Test
    public void anEqualShareGoesToTheHigherCount() {
        addTagged(5, "peach", "Beta");                       // 5/5
        addTagged(4, "mango", "Alpha");
        addTagged(1, "plum", "Alpha");                       // 4/4
        assertEquals(Arrays.asList("Beta", "Alpha"), suggest("mango peach"));
    }

    @Test
    public void equalStrengthIsOrderedByTagName() {
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
        assertEquals(Collections.emptyList(), suggest("the and"));
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
        addTagged(3, "zebra giraffe", "Nature");
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
    public void explainGivesReasonWordsAsWrittenStrongestFirst() {
        addTagged(5, "forest river mountain", "Nature");
        add("Someone", "a river of money", "Nature");
        addUntagged(1, "a forest of money");
        addFiller(10);
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes)
                .explain("Forests by the River, a lake.", "Nobody", null, "nature");
        assertEquals("Nature", why.tag);
        // "river" 6/6, then "forests" (stem "forest") 5/6; "lake" is on no quote.
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("river", 6, 6),
                new HashtagSuggester.Evidence("forests", 5, 6)), why.words);
        assertEquals(null, why.author);
    }

    @Test
    public void explainLeavesOutWeakWords() {
        addTagged(5, "forest river mountain", "Nature");
        addTagged(1, "time", "Nature");
        addUntagged(65, "time");
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes)
                .explain("forest time", "", null, "Nature");
        assertEquals(Collections.singletonList(new HashtagSuggester.Evidence("forest", 5, 5)), why.words);
    }

    @Test
    public void explainNamesTheAuthorWhenTheyAreAReason() {
        addTagged(5, "forest river mountain", "Nature");
        addFiller(10);
        addEmerson(2, 1);
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes).explain("gold coin", " Emerson ", null, "Nature");
        assertEquals(Collections.emptyList(), why.words);
        assertEquals(new HashtagSuggester.Evidence("Emerson", 2, 3), why.author);
    }

    @Test
    public void explainIsNullForATagThatCannotBeSuggested() {
        addTagged(4, "forest river mountain", "Nature");
        assertEquals(null, HashtagSuggester.fit(quotes).explain("forest river", "", null, "Nature"));
    }

    @Test
    public void profileShowsEveryReasonWordInItsMostFrequentForm() {
        addTagged(3, "friendships forever", "Friends");
        addTagged(2, "friendship forever loyal", "Friends");
        add("Someone", "loyal dog", "Friends");
        add("Other", "loyal cat", "Friends");
        addUntagged(1, "loyal");
        addFiller(10);
        HashtagSuggester.TagProfile profile = HashtagSuggester.fit(quotes).profile("#friends");
        assertTrue(profile.suggestable);
        assertEquals(7, profile.quotes);
        // "dog" and "cat" are on 1 quote each, so they are left out.
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("forever", 5, 5),
                new HashtagSuggester.Evidence("friendships", 5, 5),
                new HashtagSuggester.Evidence("loyal", 4, 5)), profile.words);
        assertEquals(Collections.emptyList(), profile.authors);
    }

    @Test
    public void profileIsNotCappedAtFiveWords() {
        addTagged(5, "apple banana cherry damson elder grape guava", "Fruit");
        assertEquals(7, HashtagSuggester.fit(quotes).profile("Fruit").words.size());
    }

    @Test
    public void profileListsEveryAuthorWhoIsAReasonStrongestFirst() {
        addTagged(5, "forest river mountain", "Nature");
        addEmerson(2, 1);
        for (int i = 0; i < 3; i++) add("Thoreau", "walden " + i, "Nature");
        add("Muir", "the mountains are calling", "Nature");
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("Thoreau", 3, 3),
                        new HashtagSuggester.Evidence("Emerson", 2, 3)),
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
    // No words, so only the tags can be a reason.
    private void addWithTags(int count, String... tags) {
        for (int i = 0; i < count; i++) add("Tagger " + quotes.size(), "", tags);
    }

    private List<String> suggestWithTags(String... tags) {
        return HashtagSuggester.fit(quotes).suggest("gold coin", "Someone New", "", Arrays.asList(tags));
    }

    @Test
    public void aTagOfTheQuoteIsAReasonAtFourQuotesAndHalf() {
        addWithTags(3, "Death", "Life");
        addWithTags(2, "Life");
        addWithTags(3, "Death");
        assertEquals(Collections.emptyList(), suggestWithTags("Death"));        // 3/6

        addWithTags(1, "Death", "Life");
        assertEquals(Collections.singletonList("Life"), suggestWithTags("death"));  // 4/7

        addWithTags(2, "Death");
        assertEquals(Collections.emptyList(), suggestWithTags("Death"));        // 4/9
    }

    @Test
    public void aCoTagMustStillBeOnFiveQuotes() {
        addWithTags(4, "Death", "Life");
        assertEquals(Collections.emptyList(), suggestWithTags("Death"));

        addWithTags(1, "Life");
        assertEquals(Collections.singletonList("Life"), suggestWithTags("Death"));
    }

    @Test
    public void coTagReasonsRankWithWordReasons() {
        addWithTags(4, "Stoicism", "Philosophy");             // #Stoicism 4/4 for Philosophy
        addWithTags(1, "Philosophy");
        addTagged(5, "gold", "Wealth");
        addUntagged(3, "gold");                               // gold 5/8 for Wealth
        assertEquals(Arrays.asList("Philosophy", "Wealth"),
                HashtagSuggester.fit(quotes).suggest("gold", "X", "", Collections.singletonList("Stoicism")));
    }

    @Test
    public void aSuggestedTagOnceAddedSuggestsTheNext() {
        addWithTags(4, "Death", "Life");
        addWithTags(4, "Life", "Meaning");
        addWithTags(1, "Meaning");
        assertEquals(Collections.singletonList("Life"), suggestWithTags("Death"));
        assertEquals(Collections.singletonList("Meaning"), suggestWithTags("Death", "Life"));
    }

    @Test
    public void aChipOnlyTheEditedQuoteHasIsNoReason() {
        addWithTags(5, "Life");
        assertEquals(Collections.emptyList(), suggestWithTags("BrandNew"));
    }

    @Test
    public void explainNamesTheQuotesTagsThatAreReasons() {
        addWithTags(4, "Death", "Life");
        addWithTags(1, "Death");
        addWithTags(4, "Grief", "Life");
        addWithTags(1, "Love", "Life");
        addWithTags(3, "Love");
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes)
                .explain("gold coin", "", Arrays.asList("death", "Grief", "Love"), "Life");
        // #Love 1/4 is too weak.
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("#Grief", 4, 4),
                new HashtagSuggester.Evidence("#Death", 4, 5)), why.tags);
    }

    @Test
    public void profileListsTheTagsThatAreReasonsButNotItself() {
        addWithTags(4, "Death", "Life");
        addWithTags(1, "Death");
        addWithTags(4, "Grief", "Life");
        addWithTags(2, "Life");
        HashtagSuggester.TagProfile profile = HashtagSuggester.fit(quotes).profile("Life");
        assertEquals(Arrays.asList(new HashtagSuggester.Evidence("#Grief", 4, 4),
                new HashtagSuggester.Evidence("#Death", 4, 5)), profile.tags);
        assertEquals(Collections.emptyList(), profile.words);
    }
}
