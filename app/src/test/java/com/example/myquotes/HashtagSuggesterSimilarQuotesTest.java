package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The similar-quotes reason (#61) with fake vectors: unit vectors in the plane at an angle, so the
 * cosine of two quotes is the cosine of the angle between them. Quote texts have no shared words
 * unless a test adds them, so the only reason is similar quotes.
 */
public class HashtagSuggesterSimilarQuotesTest {
    private final List<Quote> quotes = new ArrayList<>();
    private final Map<Integer, float[]> vectors = new HashMap<>();

    private static float[] at(double degrees) {
        double radians = Math.toRadians(degrees);
        return new float[]{(float) Math.cos(radians), (float) Math.sin(radians)};
    }

    /** A quote at {@code degrees} (null: no vector) with its own author and no words. */
    private Quote quote(Double degrees, String text, String... tags) {
        Quote quote = new Quote(quotes.size() + 1, "Author " + quotes.size(), text, "");
        quote.setTags(Arrays.asList(tags));
        quotes.add(quote);
        if (degrees != null) vectors.put(quote.getId(), at(degrees));
        return quote;
    }

    private Quote add(double degrees, String... tags) {
        return quote(degrees, "", tags);
    }

    private void addMany(int count, double degrees, String... tags) {
        for (int i = 0; i < count; i++) add(degrees, tags);
    }

    private List<String> suggest(HashtagSuggester model, double degrees) {
        return model.suggest(HashtagSuggester.Draft.of("", "Someone New", "", Collections.emptyList()).rejecting(Collections.emptyList()).withVector(at(degrees)));
    }

    private HashtagSuggester fit(HashtagSuggester.Knn knn) {
        return HashtagSuggester.fit(quotes, vectors, knn);
    }

    @Test
    public void aTagOnThreeOfTheTenNearestTaggedQuotesIsSuggested() {
        addMany(3, 0, "Sea");
        addMany(7, 1, "Other");
        addMany(5, 90, "Sea");
        addMany(5, 90, "Rare");
        HashtagSuggester model = HashtagSuggester.fit(quotes, vectors);
        assertEquals(Arrays.asList("Other", "Sea"), suggest(model, 0));
        HashtagSuggester.Explanation why = model.explain(HashtagSuggester.Draft.of("", "Someone New", "", Collections.emptyList()).withVector(at(0)), "Sea");
        assertEquals(HashtagSuggester.Evidence.similar(3, 10), why.similar);
        assertTrue(why.similar.similar);
    }

    @Test
    public void twoOfTheTenNearestIsNotEnough() {
        addMany(2, 0, "Sea");
        addMany(8, 1, "Other");
        addMany(5, 90, "Sea");
        assertEquals(Collections.singletonList("Other"), suggest(HashtagSuggester.fit(quotes, vectors), 0));
    }

    @Test
    public void onlyTheTenNearestCount() {
        addMany(10, 0, "Other");
        addMany(5, 10, "Sea");
        assertEquals(Collections.singletonList("Other"), suggest(HashtagSuggester.fit(quotes, vectors), 0));
        assertEquals(Arrays.asList("Other", "Sea"), suggest(fit(new HashtagSuggester.Knn(15, 3, 0f, false)), 0));
    }

    @Test
    public void neighboursBelowTheFloorAreIgnored() {
        addMany(3, 0, "Sea");
        addMany(5, 60, "Sea");
        HashtagSuggester.Knn floor = new HashtagSuggester.Knn(10, 3, 0.9f, false);
        // cos 20° = 0.94 passes, cos 30° = 0.87 doesn't.
        assertEquals(Collections.singletonList("Sea"), suggest(fit(floor), 20));
        assertEquals(Collections.emptyList(), suggest(fit(floor), 30));
        HashtagSuggester.Explanation why = fit(floor).explain(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList()).withVector(at(20)), "Sea");
        assertEquals(HashtagSuggester.Evidence.similar(3, 3), why.similar);
    }

    @Test
    public void untaggedQuotesAreNeverNeighbours() {
        addMany(20, 0);
        addMany(3, 5, "Sea");
        addMany(2, 45, "Sea");
        HashtagSuggester model = HashtagSuggester.fit(quotes, vectors);
        assertEquals(Collections.singletonList("Sea"), suggest(model, 0));
        HashtagSuggester.Explanation why = model.explain(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList()).withVector(at(0)), "Sea");
        assertEquals(HashtagSuggester.Evidence.similar(5, 5), why.similar);
    }

    @Test
    public void aQuoteIsNeverItsOwnNeighbour() {
        Quote land = quote(0.0, "", "Land");
        addMany(3, 1, "Sea");
        addMany(2, 90, "Sea");
        addMany(4, 90, "Land");
        // k = 3: counted with itself, only 2 of the 3 nearest would have #Sea.
        HashtagSuggester model = fit(new HashtagSuggester.Knn(3, 3, 0f, false));
        assertEquals(Collections.singletonList(
                        new HashtagSuggester.Suggestion("Sea", HashtagSuggester.Evidence.similar(3, 3))),
                model.suggest(land));
        assertEquals(HashtagSuggester.Evidence.similar(3, 3), model.suggestionFor(land, "Sea").strongest);
        HashtagSuggester.Explanation why = model.explain(land, "Sea");
        assertEquals(HashtagSuggester.Evidence.similar(3, 3), why.similar);
        assertEquals(Arrays.asList(2, 3, 4), why.similarQuoteIds);
    }

    @Test
    public void theNeighboursWithTheTagAreListedMostSimilarFirst() {
        addMany(2, 60, "Sea");
        quote(30.0, "", "Sea");
        quote(10.0, "", "Sea");
        quote(20.0, "", "Sea");
        quote(5.0, "", "Other");
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes, vectors)
                .explain(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList()).withVector(at(0)), "Sea");
        assertEquals(Arrays.asList(4, 5, 3, 1, 2), why.similarQuoteIds);
    }

    @Test
    public void similarQuotesRankByShareLikeEveryOtherReason() {
        // #Words: "harbour" on 4 of its 5 quotes (80%), far away; #Near: 9 of the 10 nearest (90%).
        for (int i = 0; i < 4; i++) quote(180.0, "harbour", "Words");
        quote(180.0, "harbour");
        quote(180.0, "", "Words");
        addMany(9, 0, "Near");
        quote(1.0, "", "Filler");
        HashtagSuggester model = HashtagSuggester.fit(quotes, vectors);
        assertEquals(Arrays.asList("Near", "Words"), model.suggest(HashtagSuggester.Draft.of("harbour", "X", "", Collections.emptyList()).rejecting(Collections.emptyList()).withVector(at(0))));

        HashtagSuggester below = fit(new HashtagSuggester.Knn(10, 3, 0f, true));
        assertEquals(Arrays.asList("Words", "Near"), below.suggest(HashtagSuggester.Draft.of("harbour", "X", "", Collections.emptyList()).rejecting(Collections.emptyList()).withVector(at(0))));
    }

    @Test
    public void belowCountedSimilarQuotesStillRankAboveANameMatch() {
        addMany(5, 90, "Ocean");
        addMany(10, 0, "Near");
        HashtagSuggester below = fit(new HashtagSuggester.Knn(10, 3, 0f, true));
        assertEquals(Arrays.asList("Near", "Ocean"), below.suggest(HashtagSuggester.Draft.of("the ocean", "X", "", Collections.emptyList()).rejecting(Collections.emptyList()).withVector(at(0))));
        assertTrue(HashtagSuggester.byStrength(HashtagSuggester.Evidence.similar(10, 10, true),
                HashtagSuggester.Evidence.nameMatch("ocean")) < 0);
        assertTrue(HashtagSuggester.byStrength(HashtagSuggester.Evidence.similar(10, 10, true),
                new HashtagSuggester.Evidence("word", 3, 10)) > 0);
    }

    @Test
    public void aTagOnFewerThanFiveQuotesIsNotSuggestedBySimilarQuotes() {
        addMany(4, 0, "Sea");
        assertEquals(Collections.emptyList(), suggest(HashtagSuggester.fit(quotes, vectors), 0));
    }

    @Test
    public void theQuotesOwnAndRejectedTagsAreLeftOut() {
        addMany(5, 0, "Sea");
        addMany(5, 0, "Sky");
        HashtagSuggester model = HashtagSuggester.fit(quotes, vectors);
        assertEquals(Collections.singletonList("Sky"), model.suggest(HashtagSuggester.Draft.of("", "X", "", Collections.singletonList("Sea")).rejecting(Collections.emptyList()).withVector(at(0))));
        assertEquals(Collections.emptyList(), model.suggest(HashtagSuggester.Draft.of("", "X", "", Collections.singletonList("Sea")).rejecting(Collections.singletonList("sky")).withVector(at(0))));
    }

    @Test
    public void aNeighboursRejectedTagsDoNotCountAgainstTheTag() {
        addMany(5, 0, "Sea");
        for (int i = 0; i < 5; i++) quote(0.0, "", "Sky").setRejectedTags(Collections.singletonList("Sea"));
        HashtagSuggester.Explanation why = HashtagSuggester.fit(quotes, vectors)
                .explain(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList()).withVector(at(0)), "Sea");
        assertEquals(HashtagSuggester.Evidence.similar(5, 10), why.similar);
    }

    @Test
    public void withoutAVectorThereIsNoSimilarQuotesReason() {
        Quote noVector = quote(null, "", "Land");
        addMany(5, 0, "Sea");
        quote(null, "", "Sky");
        HashtagSuggester model = HashtagSuggester.fit(quotes, vectors);
        assertEquals(Collections.emptyList(), model.suggest(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList())));
        assertEquals(Collections.emptyList(), model.suggest(noVector));
        HashtagSuggester.Explanation why = model.explain(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList()), "Sea");
        assertNull(why.similar);
        assertEquals(Collections.emptyList(), why.similarQuoteIds);
        assertFalse(HashtagSuggester.fit(quotes).suggest(HashtagSuggester.Draft.of("", "X", "", Collections.emptyList()).rejecting(Collections.emptyList()).withVector(at(0))).contains("Sea"));
    }

    @Test
    public void aQuoteWithoutAVectorIsNeverANeighbour() {
        for (int i = 0; i < 5; i++) quote(null, "", "Sea");
        addMany(5, 45, "Sky");
        assertEquals(Collections.singletonList("Sky"), suggest(HashtagSuggester.fit(quotes, vectors), 0));
    }
}
