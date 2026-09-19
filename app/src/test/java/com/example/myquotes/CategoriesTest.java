package com.example.myquotes;

import static org.junit.Assert.assertEquals;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CategoriesTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static Quote quote(int id, String category) {
        Quote q = new Quote(id, "a", "text " + id, "src");
        q.setCategory(category);
        return q;
    }

    private InMemoryQuoteStore quoteStore;
    private InMemoryCategoryStore categoryStore;
    private QuoteCollection collection;
    private Categories categories;
    private int emissions;

    private void setUp(InMemoryCategoryStore cStore, Quote... quotes) {
        quoteStore = new InMemoryQuoteStore(Arrays.asList(quotes));
        categoryStore = cStore;
        collection = new QuoteCollection(quoteStore);
        collection.loadFromStore();
        categories = new Categories(categoryStore, collection);
        emissions = 0;
        collection.getQuoteList().observeForever(list -> emissions++);
        emissions = 0; // ignore the initial delivery
        quoteStore.saveCount = 0;
    }

    @Test
    public void unionIncludesSavedAndInUseNamesOnceEachSorted() {
        setUp(new InMemoryCategoryStore("Zen", "Work"),
                quote(1, "Work"), quote(2, "Art"), quote(3, "Art"));
        assertEquals(Arrays.asList("Art", "Work", "Zen"), categories.all());
    }

    @Test
    public void whitespaceOnlyAndNullCategoriesCountAsNone() {
        setUp(new InMemoryCategoryStore(" "), quote(1, "   "), quote(2, null));
        assertEquals(new ArrayList<String>(), categories.all());
    }

    @Test
    public void nullCategoryNormalizesToEmpty() {
        Quote q = new Quote(1, "a", "t", "s");
        q.setCategory("x");
        q.setCategory(null);
        assertEquals("", q.getCategory());
    }

    @Test
    public void addPersistsAndAppearsInSet() {
        setUp(new InMemoryCategoryStore());
        categories.add("Music");
        assertEquals(Arrays.asList("Music"), categories.all());
        assertEquals(Arrays.asList("Music"), categoryStore.saved);
    }

    @Test
    public void renameUpdatesSetAndEveryQuoteWithOneEmissionAndOneSave() {
        setUp(new InMemoryCategoryStore("Old"),
                quote(1, "Old"), quote(2, "Old"), quote(3, "Old"), quote(4, "Other"));

        categories.rename("Old", "New");

        assertEquals(Arrays.asList("New", "Other"), categories.all());
        assertEquals("New", collection.findById(1).getCategory());
        assertEquals("New", collection.findById(3).getCategory());
        assertEquals("Other", collection.findById(4).getCategory());
        assertEquals(1, emissions);
        assertEquals(1, quoteStore.saveCount);
        assertEquals(Arrays.asList("New"), categoryStore.saved);
    }

    @Test
    public void deleteRemovesNameAndBlanksAffectedQuotesInOneBatch() {
        setUp(new InMemoryCategoryStore("Gone", "Keep"),
                quote(1, "Gone"), quote(2, "Gone"), quote(3, "Keep"));

        categories.delete("Gone");

        assertEquals(Arrays.asList("Keep"), categories.all());
        assertEquals("", collection.findById(1).getCategory());
        assertEquals("", collection.findById(2).getCategory());
        assertEquals("Keep", collection.findById(3).getCategory());
        assertEquals(1, emissions);
        assertEquals(1, quoteStore.saveCount);
        assertEquals(Arrays.asList("Keep"), categoryStore.saved);
    }

    @Test
    public void liveSetReemitsWhenQuotesChange() {
        setUp(new InMemoryCategoryStore(), quote(1, "A"));
        List<List<String>> seen = new ArrayList<>();
        categories.getCategories().observeForever(seen::add);
        collection.add(quote(0, "B"));
        assertEquals(Arrays.asList("A", "B"), seen.get(seen.size() - 1));
    }
}
