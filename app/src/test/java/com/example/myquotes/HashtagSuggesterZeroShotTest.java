package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
 * Reasons for small tags (#64) with fake vectors: unit vectors in the plane at an angle, so a
 * cosine is the cosine of the angle between them. The name of #Courage sits at 0°.
 */
public class HashtagSuggesterZeroShotTest {
    private static final HashtagSuggester.ZeroShot STEM = new HashtagSuggester.ZeroShot(true, false, 0f, 0);

    private final List<Quote> quotes = new ArrayList<>();
    private final Map<Integer, float[]> vectors = new HashMap<>();
    private final Map<String, float[]> names = new HashMap<>();

    private static float[] at(double degrees) {
        double radians = Math.toRadians(degrees);
        return new float[]{(float) Math.cos(radians), (float) Math.sin(radians)};
    }

    private static HashtagSuggester.ZeroShot meaning(double floorDegrees, int topN) {
        return new HashtagSuggester.ZeroShot(false, true, (float) Math.cos(Math.toRadians(floorDegrees)), topN);
    }

    private Quote add(Double degrees, String text, String... tags) {
        Quote quote = new Quote(quotes.size() + 1, "Author " + quotes.size(), text, "");
        quote.setTags(Arrays.asList(tags));
        quotes.add(quote);
        if (degrees != null) vectors.put(quote.getId(), at(degrees));
        return quote;
    }

    private HashtagSuggester model(HashtagSuggester.ZeroShot zeroShot) {
        return HashtagSuggester.fit(quotes, vectors).withNames(names).withZeroShot(zeroShot);
    }

    private static List<String> suggest(HashtagSuggester model, String text, Double degrees) {
        return model.suggest(HashtagSuggester.Draft.of(text, "Someone New", "", Collections.emptyList()).rejecting(Collections.emptyList()).withVector(degrees == null ? null : at(degrees)));
    }

    private void smallCourage() {
        add(90.0, "", "Courage");
        add(90.0, "", "Courage");
        names.put("courage", at(0));
    }

    @Test
    public void offSuggestsNoSmallTag() {
        smallCourage();
        assertEquals(Collections.emptyList(),
                suggest(HashtagSuggester.fit(quotes, vectors), "Courage is grace under pressure", 0.0));
        assertEquals(Collections.emptyList(),
                suggest(model(HashtagSuggester.ZeroShot.OFF), "Courage is grace under pressure", 0.0));
    }

    @Test
    public void stemSuggestsASmallTagWhoseNameIsInTheQuote() {
        smallCourage();
        HashtagSuggester model = model(STEM);
        assertEquals(Collections.singletonList("Courage"), suggest(model, "All courageous acts", null));
        assertEquals(Collections.emptyList(), suggest(model, "Grace under pressure", 0.0));
    }

    @Test
    public void meaningSuggestsAQuoteCloseToTheName() {
        smallCourage();
        HashtagSuggester model = model(meaning(30, 20));
        assertEquals(Collections.singletonList("Courage"), suggest(model, "", 20.0));
        assertEquals(Collections.emptyList(), suggest(model, "", 40.0));
        assertEquals("no vector, no meaning reason", Collections.emptyList(), suggest(model, "", null));
    }

    @Test
    public void meaningNeedsTheQuoteAmongTheTopNClosestQuotesWithoutTheTag() {
        smallCourage();
        add(1.0, "");
        add(2.0, "");
        add(3.0, "");
        HashtagSuggester model = model(meaning(80, 2));
        assertEquals(Collections.singletonList("Courage"), suggest(model, "", 0.5));
        assertEquals("closer than the 2nd closest", Collections.singletonList("Courage"), suggest(model, "", 2.0));
        assertEquals(Collections.emptyList(), suggest(model, "", 2.5));
    }

    @Test
    public void theTagsOwnQuotesTakeNoPlaceInTheRanking() {
        add(0.0, "", "Courage");
        add(0.0, "", "Courage");
        names.put("courage", at(0));
        add(10.0, "");
        HashtagSuggester model = model(meaning(80, 1));
        assertEquals(Collections.singletonList("Courage"), suggest(model, "", 10.0));
        assertEquals(Collections.emptyList(), suggest(model, "", 11.0));
    }

    @Test
    public void anEligibleTagHasNoMeaningReason() {
        for (int i = 0; i < HashtagSuggester.MIN_QUOTES_PER_TAG; i++) add(90.0, "", "Courage");
        names.put("courage", at(0));
        HashtagSuggester model = model(meaning(80, 20));
        assertEquals(Collections.emptyList(), suggest(model, "", 0.0));
    }

    @Test
    public void smallTagsRankBelowEveryOtherReasonStemBeforeMeaning() {
        smallCourage();
        add(90.0, "", "Hope");
        add(90.0, "", "Hope");
        names.put("hope", at(5));
        for (int i = 0; i < 3; i++) add(null, "lighthouse beacon", "Sea");
        add(null, "lighthouse", "Sea");
        add(null, "beacon", "Sea");
        add(null, "lighthouse beacon");
        HashtagSuggester.ZeroShot both = new HashtagSuggester.ZeroShot(true, true, 0.5f, 20);
        HashtagSuggester model = model(both);
        assertEquals(Arrays.asList("Sea", "Hope", "Courage"), suggest(model, "lighthouse hope", 4.0));
        assertEquals("closer name first", Arrays.asList("Sea", "Courage", "Hope"), suggest(model, "lighthouse", 1.0));
    }

    @Test
    public void suggestionForRanksACollectionQuoteAndLeavesOutItsOwnAndRejectedTags() {
        smallCourage();
        Quote close = add(1.0, "");
        Quote far = add(2.0, "");
        Quote rejecting = add(0.5, "");
        rejecting.setRejectedTags(Collections.singletonList("courage"));
        HashtagSuggester model = model(meaning(80, 2));

        HashtagSuggester.Suggestion suggestion = model.suggestionFor(close, "Courage");
        assertNotNull(suggestion);
        assertTrue(suggestion.strongest.meaning);
        assertNull("3rd closest without the tag", model.suggestionFor(far, "Courage"));
        assertNull(model.suggestionFor(rejecting, "Courage"));
        assertNull("has the tag", model.suggestionFor(quotes.get(0), "Courage"));
        assertTrue(model.suggest(rejecting).isEmpty());
        assertEquals("Courage", model.suggest(close).get(0).tag);
    }

    @Test
    public void nameTextSplitsCamelCase() {
        assertEquals("life wisdom", HashtagSuggester.splitName("LifeWisdom"));
        assertEquals("ai ethics", HashtagSuggester.splitName("AIEthics"));
        assertEquals("courage", HashtagSuggester.splitName("Courage"));
        assertEquals("A quote about life wisdom.", HashtagSuggester.NameText.TEMPLATE.of("LifeWisdom"));
        assertEquals("life wisdom", HashtagSuggester.NameText.BARE.of("LifeWisdom"));
    }
}
