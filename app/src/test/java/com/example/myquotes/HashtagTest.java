package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class HashtagTest {

    @Test
    public void normalizeDropsLeadingHashAndCharactersATagCantHold() {
        assertEquals("Love", Hashtag.normalize("#Love"));
        assertEquals("LifeWisdom", Hashtag.normalize(" Life Wisdom "));
        assertEquals("self_help2", Hashtag.normalize("self_help-2!"));
        assertEquals("Größe", Hashtag.normalize("#Größe"));
        assertEquals("", Hashtag.normalize("#!?"));
        assertEquals("", Hashtag.normalize(null));
    }

    @Test
    public void tagCharactersAreLettersDigitsAndUnderscore() {
        assertTrue(Hashtag.isTagChar('a'));
        assertTrue(Hashtag.isTagChar('É'));
        assertTrue(Hashtag.isTagChar('7'));
        assertTrue(Hashtag.isTagChar('_'));
        assertFalse(Hashtag.isTagChar('#'));
        assertFalse(Hashtag.isTagChar(' '));
        assertFalse(Hashtag.isTagChar('-'));
    }

    @Test
    public void categoryBecomesCamelCaseTag() {
        assertEquals("LifeWisdom", Hashtag.fromCategory("Life Wisdom"));
        assertEquals("LifeWisdom", Hashtag.fromCategory("life wisdom"));
        assertEquals("SelfHelp", Hashtag.fromCategory("self-help"));
        assertEquals("RockNRoll", Hashtag.fromCategory("Rock 'n' Roll"));
        assertEquals("Humor", Hashtag.fromCategory("Humor"));
        assertEquals("stoicism", Hashtag.fromCategory("stoicism"));
        assertEquals("Ärger", Hashtag.fromCategory("  Ärger! "));
        assertEquals("", Hashtag.fromCategory(""));
        assertEquals("", Hashtag.fromCategory("  ?! "));
        assertEquals("", Hashtag.fromCategory(null));
    }

    @Test
    public void normalizeAllDeduplicatesIgnoringCaseAndSortsAlphabetically() {
        assertEquals(Arrays.asList("art", "Love", "zen"),
                Hashtag.normalizeAll(Arrays.asList("zen", "Love", "#love", "LOVE", "art", "", "#")));
        assertTrue(Hashtag.normalizeAll(null).isEmpty());
    }

    @Test
    public void canonicalPrefersTheExistingSpelling() {
        assertEquals("Love", Hashtag.canonical("love", Arrays.asList("Art", "Love")));
        assertEquals("zen", Hashtag.canonical("zen", Arrays.asList("Art", "Love")));
    }

    @Test
    public void displayAndLine() {
        assertEquals("#Love", Hashtag.display("Love"));
        assertEquals("#A #B #C", Hashtag.line(Arrays.asList("A", "B", "C")));
        assertEquals("", Hashtag.line(Collections.emptyList()));
    }

    @Test
    public void stripHashRemovesOneLeadingHash() {
        assertEquals("Love", Hashtag.stripHash(" #Love "));
        assertEquals("Love", Hashtag.stripHash("Love"));
        assertEquals("", Hashtag.stripHash("#"));
    }
}
