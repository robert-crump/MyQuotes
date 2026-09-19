package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class QuoteCollectionTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static Quote quote(int id, String author) {
        return new Quote(id, author, "text " + id, "src");
    }

    private static QuoteCollection collectionOf(InMemoryQuoteStore store) {
        QuoteCollection c = new QuoteCollection(store);
        c.loadFromStore();
        return c;
    }

    @Test
    public void addAssignsMaxIdPlusOne() {
        QuoteCollection c = collectionOf(new InMemoryQuoteStore(Arrays.asList(quote(3, "a"), quote(7, "b"))));
        Quote added = quote(0, "c");
        c.add(added);
        assertEquals(Integer.valueOf(8), added.getId());
    }

    @Test
    public void editPreservesFavoriteAndViewState() {
        Quote q = quote(1, "a");
        q.setFavorite(true);
        q.setFavoritedAt(1234L);
        q.setTimesShown(5);
        q.setLastShown(99L);
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(q));
        QuoteCollection c = collectionOf(store);

        c.edit(1, "new author", "new text", "new src", "cat");

        Quote edited = c.findById(1);
        assertEquals("new author", edited.getAuthor());
        assertEquals("new text", edited.getQuoteText());
        assertEquals("cat", edited.getCategory());
        assertTrue(edited.isFavorite());
        assertEquals(1234L, edited.getFavoritedAt());
        assertEquals(5, edited.getTimesShown());
        assertEquals(99L, edited.getLastShown());
        assertTrue(store.load().get(0).isFavorite());
    }

    @Test
    public void deletingLastQuoteSavesEmptyList() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(quote(1, "a")));
        QuoteCollection c = collectionOf(store);

        c.deleteById(1);

        assertTrue(store.hasStoredQuotes());
        assertTrue(store.load().isEmpty());
    }

    @Test
    public void settingEmptyListIsPersisted() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(quote(1, "a")));
        QuoteCollection c = collectionOf(store);

        c.setList(new java.util.ArrayList<>());

        assertTrue(store.load().isEmpty());
    }

    @Test
    public void toggleFavoriteSetsAndClearsFavoritedAt() {
        QuoteCollection c = collectionOf(new InMemoryQuoteStore(Arrays.asList(quote(1, "a"))));

        assertTrue(c.toggleFavorite(1));
        assertTrue(c.findById(1).isFavorite());
        assertTrue(c.findById(1).getFavoritedAt() > 0);

        assertFalse(c.toggleFavorite(1));
        assertFalse(c.findById(1).isFavorite());
        assertEquals(0L, c.findById(1).getFavoritedAt());
    }

    @Test
    public void getFavoritesIsRecencySorted() {
        Quote a = quote(1, "a");
        Quote b = quote(2, "b");
        Quote d = quote(3, "c");
        a.setFavorite(true);
        a.setFavoritedAt(100L);
        b.setFavorite(true);
        b.setFavoritedAt(300L);
        d.setFavorite(true);
        d.setFavoritedAt(200L);
        QuoteCollection c = collectionOf(new InMemoryQuoteStore(Arrays.asList(a, b, d)));

        List<Quote> favorites = c.getFavorites();

        assertEquals(Arrays.asList(2, 3, 1),
                Arrays.asList(favorites.get(0).getId(), favorites.get(1).getId(), favorites.get(2).getId()));
    }

    @Test
    public void loadFromStoreTrimsFields() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(quote(1, "padded  ")));
        QuoteCollection c = collectionOf(store);
        assertEquals("padded", c.findById(1).getAuthor());
        assertEquals("padded", store.load().get(0).getAuthor());
    }
}
