package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HashtagsTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static Quote quote(int id, String... tags) {
        Quote q = new Quote(id, "a", "text " + id, "src");
        q.setTags(Arrays.asList(tags));
        return q;
    }

    private InMemoryQuoteStore store;
    private QuoteCollection collection;
    private Hashtags hashtags;
    private int emissions;

    private void setUp(Quote... quotes) {
        store = new InMemoryQuoteStore(Arrays.asList(quotes));
        collection = new QuoteCollection(store);
        collection.loadFromStore();
        hashtags = new Hashtags(collection);
        collection.getQuoteList().observeForever(list -> emissions++);
        emissions = 0; // ignore the initial delivery
        store.saveCount = 0;
    }

    @Test
    public void setIsTheUnionOfTagsInUseSortedAlphabetically() {
        setUp(quote(1, "Work", "art"), quote(2, "Zen"), quote(3, "Art"), quote(4));
        assertEquals(Arrays.asList("art", "Work", "Zen"), hashtags.all());
    }

    @Test
    public void countsCountEachQuoteOncePerTag() {
        setUp(quote(1, "Art", "Zen"), quote(2, "Art"), quote(3, "Zen", "Work"), quote(4));
        Map<String, Integer> expected = new HashMap<>();
        expected.put("Art", 2);
        expected.put("Zen", 2);
        expected.put("Work", 1);
        assertEquals(expected, hashtags.quoteCounts());
        assertEquals(3, hashtags.quotesWithAny(Arrays.asList("art", "Zen")));
    }

    @Test
    public void canonicalAndMergeTargetUseTheExistingSpelling() {
        setUp(quote(1, "Love"), quote(2, "Luv"));
        assertEquals("Love", hashtags.canonical("love"));
        assertEquals("new", hashtags.canonical("new"));
        assertEquals("Love", hashtags.mergeTarget("Luv", "love"));
        assertNull(hashtags.mergeTarget("Luv", "Luvv"));
        assertNull(hashtags.mergeTarget("Love", "LOVE")); // only a respelling of itself
    }

    @Test
    public void renameUpdatesEveryQuoteWithOneEmissionAndOneSave() {
        setUp(quote(1, "Old"), quote(2, "Old", "Other"), quote(3, "Other"));

        hashtags.rename("Old", "#New");

        assertEquals(Arrays.asList("New", "Other"), hashtags.all());
        assertEquals(Arrays.asList("New"), collection.findById(1).getTags());
        assertEquals(Arrays.asList("New", "Other"), collection.findById(2).getTags());
        assertEquals(1, emissions);
        assertEquals(1, store.saveCount);
    }

    @Test
    public void renameToAnExistingTagMergesAndAQuoteWithBothKeepsItOnce() {
        setUp(quote(1, "Luv"), quote(2, "Luv", "Love"), quote(3, "Love"));

        hashtags.rename("Luv", "love");

        assertEquals(Arrays.asList("Love"), hashtags.all());
        assertEquals(Arrays.asList("Love"), collection.findById(1).getTags());
        assertEquals(Arrays.asList("Love"), collection.findById(2).getTags());
        assertEquals(1, emissions);
        assertEquals(1, store.saveCount);
    }

    @Test
    public void renameCanChangeTheCaseOfATag() {
        setUp(quote(1, "love"), quote(2, "love"));
        hashtags.rename("love", "Love");
        assertEquals(Arrays.asList("Love"), hashtags.all());
        assertEquals(Arrays.asList("Love"), collection.findById(2).getTags());
    }

    @Test
    public void deleteRemovesTheTagButKeepsTheQuotesInOneBatch() {
        setUp(quote(1, "Gone"), quote(2, "Gone", "Keep"), quote(3, "Keep"));

        hashtags.delete("gone");

        assertEquals(Arrays.asList("Keep"), hashtags.all());
        assertEquals(3, collection.getCurrentList().size());
        assertTrue(collection.findById(1).getTags().isEmpty());
        assertEquals(Arrays.asList("Keep"), collection.findById(2).getTags());
        assertEquals(1, emissions);
        assertEquals(1, store.saveCount);
    }

    @Test
    public void liveSetReemitsWhenQuotesChange() {
        setUp(quote(1, "A"));
        List<List<String>> seen = new ArrayList<>();
        hashtags.getHashtags().observeForever(seen::add);
        collection.add(quote(0, "B"));
        assertEquals(Arrays.asList("A", "B"), seen.get(seen.size() - 1));
    }
}
