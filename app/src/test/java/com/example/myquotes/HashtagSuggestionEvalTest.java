package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HashtagSuggestionEvalTest {
    private static final double EPS = 1e-9;
    private final List<Quote> quotes = new ArrayList<>();
    private final Map<Quote, List<String>> suggestions = new HashMap<>();

    private Quote add(String text, List<String> tags, String... suggested) {
        Quote q = new Quote(quotes.size() + 1, "Author " + quotes.size(), text, "");
        q.setTags(tags);
        quotes.add(q);
        suggestions.put(q, Arrays.asList(suggested));
        return q;
    }

    private HashtagSuggestionEval.Result evaluate() {
        return HashtagSuggestionEval.evaluate(quotes, suggestions::get);
    }

    @Test
    public void countsPrecisionRecallAndCoverage() {
        // #Love on 7 quotes (eligible when one is left out), #Rare on 2 (never eligible).
        add("q1", Arrays.asList("Love", "Rare"), "Love", "Hope", "Rare");
        add("q2", Arrays.asList("Love", "Rare"), "Hope");
        add("q3", Collections.singletonList("Love"));
        for (int i = 0; i < 4; i++) add("love " + i, Collections.singletonList("Love"), "Love");
        add("untagged a", Collections.emptyList(), "Love");
        add("untagged b", Collections.emptyList());

        HashtagSuggestionEval.Result result = evaluate();

        assertEquals(7, result.taggedQuotes);
        assertEquals(2, result.untaggedQuotes);
        // Suggestions on tagged quotes: q1 3, q2 1, four love quotes 1 each = 8; hits: q1 Love+Rare, 4 love.
        assertEquals(8, result.suggested);
        assertEquals(6, result.hits);
        assertEquals(6.0 / 8, result.precision(), EPS);
        // Tags: 7 Love + 2 Rare = 9; eligible only the 7 Love; eligible hits 5 (q1 + four).
        assertEquals(9, result.tags);
        assertEquals(7, result.eligibleTags);
        assertEquals(5, result.eligibleHits);
        assertEquals(5.0 / 7, result.recallEligible(), EPS);
        assertEquals(6.0 / 9, result.recallUnrestricted(), EPS);
        assertEquals(6.0 / 7, result.coverageTagged(), EPS);
        assertEquals(1.0 / 2, result.coverageUntagged(), EPS);

        HashtagSuggestionEval.TagResult love = result.perTag.get("love");
        assertEquals(7, love.quotes);
        assertEquals(7, love.eligible);
        assertEquals(5, love.suggested);
        assertEquals(5, love.hits);
        assertEquals(1.0, love.precision(), EPS);
        assertEquals(5.0 / 7, love.recall(), EPS);

        HashtagSuggestionEval.TagResult hope = result.perTag.get("hope");
        assertEquals(0, hope.quotes);
        assertEquals(2, hope.suggested);
        assertEquals(0.0, hope.precision(), EPS);
        assertTrue(Double.isNaN(hope.recall()));

        HashtagSuggestionEval.TagResult rare = result.perTag.get("rare");
        assertEquals(2, rare.quotes);
        assertEquals(0, rare.eligible);
        assertEquals(1, rare.hits);
        assertEquals(0, rare.eligibleHits);
    }

    @Test
    public void onlyTheTopThreeCountAndTagsMatchIgnoringCase() {
        add("q", Collections.singletonList("Love"), "Hope", "Joy", "Peace", "Love");
        add("r", Collections.singletonList("Love"), "love", "LOVE");

        HashtagSuggestionEval.Result result = evaluate();

        assertEquals(4, result.suggested);
        assertEquals(1, result.hits);
    }

    @Test
    public void emptyCollectionHasNoRatios() {
        HashtagSuggestionEval.Result result = evaluate();
        assertTrue(Double.isNaN(result.precision()));
        assertTrue(Double.isNaN(result.recallEligible()));
        assertTrue(Double.isNaN(result.coverageUntagged()));
        assertTrue(HashtagSuggestionEval.format(result).contains("precision@3:"));
    }

    @Test
    public void leaveOneOutMatchesTheEditor() {
        // Five quotes with "forest" carry #Nature; the sixth does too. Left out, it still sees five.
        for (int i = 0; i < 6; i++) add("forest river " + i, Collections.singletonList("Nature"));
        for (int i = 0; i < 5; i++) add("money market " + i, Collections.emptyList());
        Quote target = quotes.get(0);

        List<Quote> others = new ArrayList<>(quotes);
        others.remove(target);
        List<String> editor = HashtagSuggester.fit(others)
                .suggest(target.getQuoteText(), target.getAuthor(), target.getSource(), Collections.emptyList());

        assertEquals(editor, HashtagSuggestionEval.leaveOneOut(quotes).apply(target));
        assertEquals(Collections.singletonList("Nature"), editor);
    }

    @Test
    public void leaveOneOutDropsATagThatFallsBelowTheMinimum() {
        // #Nature on exactly 5 quotes: left out, a quote sees only 4, so it can't be suggested.
        for (int i = 0; i < 5; i++) add("forest river " + i, Collections.singletonList("Nature"));
        Quote target = quotes.get(0);
        assertEquals(Collections.emptyList(), HashtagSuggestionEval.leaveOneOut(quotes).apply(target));

        HashtagSuggestionEval.Result result =
                HashtagSuggestionEval.evaluate(quotes, HashtagSuggestionEval.leaveOneOut(quotes));
        assertEquals(0, result.eligibleTags);
        assertEquals(5, result.tags);
    }

    @Test
    public void leaveOneOutLeavesRejectedTagsOut() {
        for (int i = 0; i < 6; i++) add("forest river " + i, Collections.singletonList("Nature"));
        Quote target = add("forest river walk", Collections.emptyList());
        target.setRejectedTags(Collections.singletonList("Nature"));
        assertEquals(Collections.emptyList(), HashtagSuggestionEval.leaveOneOut(quotes).apply(target));
    }
}
