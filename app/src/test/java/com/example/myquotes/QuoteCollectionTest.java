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
    public void addStampsAddedAt() {
        QuoteCollection c = collectionOf(new InMemoryQuoteStore(Arrays.asList(quote(1, "a"))));
        long before = System.currentTimeMillis();
        Quote added = quote(0, "b");
        c.add(added);
        assertTrue(added.getAddedAt() >= before);
    }

    @Test
    public void editPreservesFavoriteViewStateAndAddedAt() {
        Quote q = quote(1, "a");
        q.setFavorite(true);
        q.setFavoritedAt(1234L);
        q.setTimesShown(5);
        q.setLastShown(99L);
        q.setAddedAt(42L);
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(q));
        QuoteCollection c = collectionOf(store);

        c.edit(1, "new author", "new text", "new src", Arrays.asList("cat", "#Dog"));

        Quote edited = c.findById(1);
        assertEquals("new author", edited.getAuthor());
        assertEquals("new text", edited.getQuoteText());
        assertEquals(Arrays.asList("cat", "Dog"), edited.getTags());
        assertTrue(edited.isFavorite());
        assertEquals(1234L, edited.getFavoritedAt());
        assertEquals(5, edited.getTimesShown());
        assertEquals(99L, edited.getLastShown());
        assertEquals(42L, edited.getAddedAt());
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

    private static Quote tagged(int id, String... tags) {
        Quote q = quote(id, "a");
        q.setTags(Arrays.asList(tags));
        return q;
    }

    @Test
    public void loadFromStoreSavesOnceWhenTheStoreHoldsTheOldFormat() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(tagged(1, "LifeWisdom")));
        store.oldFormat = true;
        collectionOf(store);
        assertEquals(1, store.saveCount);
        assertFalse(store.isOldFormat());
    }

    @Test
    public void loadFromStoreDoesNotSaveCurrentFormatUnchanged() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(tagged(1, "Zen")));
        collectionOf(store);
        assertEquals(0, store.saveCount);
    }

    @Test
    public void loadFromStoreUnifiesTagSpellingsFirstOneWins() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(
                tagged(1, "Love"), tagged(2, "love", "Zen"), tagged(3, "LOVE")));
        QuoteCollection c = collectionOf(store);
        assertEquals(Arrays.asList("Love", "Zen"), c.findById(2).getTags());
        assertEquals(Arrays.asList("Love"), c.findById(3).getTags());
        assertEquals(1, store.saveCount);
    }

    @Test
    public void addAndEditTakeTheExistingSpellingOfATag() {
        QuoteCollection c = collectionOf(new InMemoryQuoteStore(Arrays.asList(tagged(1, "Love"), tagged(2, "Zen"))));
        Quote added = tagged(0, "love", "new");
        c.add(added);
        assertEquals(Arrays.asList("Love", "new"), c.findById(added.getId()).getTags());

        c.edit(2, "a", "t", "s", Arrays.asList("zen", "LOVE"));
        assertEquals(Arrays.asList("Love", "zen"), c.findById(2).getTags());
    }

    @Test
    public void replaceTagRenamesIgnoringCaseInOneEmissionAndOneSave() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(
                tagged(1, "Old", "Keep"), tagged(2, "old"), tagged(3, "Keep")));
        QuoteCollection c = collectionOf(store);
        int[] emissions = {0};
        c.getQuoteList().observeForever(list -> emissions[0]++);
        emissions[0] = 0;
        store.saveCount = 0;

        assertEquals(2, c.replaceTag("OLD", "New"));

        assertEquals(Arrays.asList("Keep", "New"), c.findById(1).getTags());
        assertEquals(Arrays.asList("New"), c.findById(2).getTags());
        assertEquals(Arrays.asList("Keep"), c.findById(3).getTags());
        assertEquals(1, emissions[0]);
        assertEquals(1, store.saveCount);
    }

    @Test
    public void replaceTagMergesIntoAnExistingTagOncePerQuote() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(
                tagged(1, "Luv", "Love"), tagged(2, "Luv")));
        QuoteCollection c = collectionOf(store);
        store.saveCount = 0;

        assertEquals(2, c.replaceTag("Luv", "Love"));

        assertEquals(Arrays.asList("Love"), c.findById(1).getTags());
        assertEquals(Arrays.asList("Love"), c.findById(2).getTags());
        assertEquals(1, store.saveCount);
    }

    @Test
    public void replaceTagWithNullDeletesItKeepingTheQuotes() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(tagged(1, "Gone", "Keep"), tagged(2, "Gone")));
        QuoteCollection c = collectionOf(store);
        store.saveCount = 0;

        assertEquals(2, c.replaceTag("Gone", null));

        assertEquals(Arrays.asList("Keep"), c.findById(1).getTags());
        assertTrue(c.findById(2).getTags().isEmpty());
        assertEquals(2, store.load().size());
        assertEquals(1, store.saveCount);
    }

    @Test
    public void replaceTagWithoutMatchesNeitherEmitsNorSaves() {
        InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(tagged(1, "Keep")));
        QuoteCollection c = collectionOf(store);
        store.saveCount = 0;
        assertEquals(0, c.replaceTag("Missing", "X"));
        assertEquals(0, store.saveCount);
    }
}
