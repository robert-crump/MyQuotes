package com.example.myquotes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class QuoteTokensTest {
    private static Set<String> set(String... tokens) {
        return new LinkedHashSet<>(Arrays.asList(tokens));
    }

    private static Set<String> words(String text) {
        return QuoteTokens.of(text, null, null, null);
    }

    @Test
    public void stopwordsAndShortWordsAreDropped() {
        assertEquals(set("forest"), words("The forest is where we go, and they are ours."));
        assertEquals(set("wald", "baum"), QuoteTokens.of("Der Wald und die Bäume, ich bin immer da.", "", null, null));
        assertEquals(set(), words("the and"));
    }

    @Test
    public void theStemMergesWordForms() {
        assertEquals(set("love"), words("Love, loved and loving."));
        assertEquals(set("happi"), words("Happiness is being happy."));
        assertEquals(set("lieb", "author:goethe", "source:faust"),
                QuoteTokens.of("Die Liebe und lieben", " Goethe ", " Faust ", null));
    }

    @Test
    public void friendshipIsNotFriend() {
        assertEquals(set("friendship"), words("friendship"));
        assertEquals(set("friend"), words("friend"));
    }

    @Test
    public void germanWinsWhenItHasMoreGermanStopwords() {
        // German: "leben" → "leb", "sterben" → "sterb".
        assertEquals(set("leb", "sterb"), words("Wir leben und wir sterben."));
        // English: "living" → "live", "dying" → "die".
        assertEquals(set("live", "die"), words("We are living and we are dying."));
        // No stopwords at all: English.
        assertEquals(set("live"), words("Living!"));
    }

    @Test
    public void oneAuthorAndOneSourceTokenLowercasedWithSpacesCollapsed() {
        assertEquals(set("author:ralph waldo emerson", "source:self reliance"),
                QuoteTokens.of(null, "  Ralph   Waldo EMERSON ", " Self\tReliance", null));
        assertEquals(set(), QuoteTokens.of(null, "   ", "", null));
        assertEquals(set(), QuoteTokens.of(null, null, null, null));
    }

    @Test
    public void oneTagTokenPerTag() {
        assertEquals(set("tag:nature", "tag:life"),
                QuoteTokens.of(null, null, null, Arrays.asList("#Nature", "life", "NATURE", "")));
        assertEquals(set(), QuoteTokens.of(null, null, null, Collections.emptyList()));
    }

    @Test
    public void nameStemsAreTheEnglishAndGermanStems() {
        assertEquals(set("lieb"), QuoteTokens.nameStems("liebe"));
        assertEquals(set("love", "loving"), QuoteTokens.nameStems("loving"));
        assertEquals(set(), QuoteTokens.nameStems("ai"));
    }
}
