package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class ReadingSessionTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private QuoteCollection collection;
    private ReadingSession session;

    @Before
    public void setUp() {
        collection = new QuoteCollection(new InMemoryQuoteStore());
        session = new ReadingSession(collection);
    }

    private static Quote quote(int id) {
        return new Quote(id, "author " + id, "text " + id, "src");
    }

    private void seed(int count) {
        List<Quote> quotes = new ArrayList<>();
        for (int i = 1; i <= count; i++) quotes.add(quote(i));
        collection.setList(quotes);
    }

    private List<Integer> deckIds() {
        List<Integer> ids = new ArrayList<>();
        for (Quote q : session.getDeck().getValue()) ids.add(q.getId());
        return ids;
    }

    @Test
    public void firstNonEmptyCollectionProducesShuffledDeckOfSameIds() {
        seed(20);
        List<Integer> ids = deckIds();
        assertEquals(20, ids.size());
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)),
                new HashSet<>(ids));
        assertEquals(Integer.valueOf(ids.get(0)), session.getCurrentQuote().getValue().getId());
        assertEquals(0, session.getCurrentPosition());
    }

    @Test
    public void addAppendsToEndOfDeck() {
        seed(5);
        List<Integer> before = deckIds();
        collection.add(quote(0));
        List<Integer> after = deckIds();
        assertEquals(6, after.size());
        assertEquals(before, after.subList(0, 5));
        assertEquals(Integer.valueOf(6), after.get(5));
    }

    @Test
    public void deleteBeforeCurrentShiftsPositionBack() {
        seed(5);
        List<Integer> ids = deckIds();
        session.setPosition(3);
        int currentId = session.getCurrentQuote().getValue().getId();

        collection.deleteById(ids.get(1));

        assertEquals(2, session.getCurrentPosition());
        assertEquals(currentId, (int) session.getDeck().getValue().get(2).getId());
    }

    @Test
    public void deletingCurrentSelectsNext() {
        seed(5);
        List<Integer> ids = deckIds();
        session.setPosition(2);

        collection.deleteById(ids.get(2));

        assertEquals(2, session.getCurrentPosition());
        assertEquals(ids.get(3), session.getCurrentQuote().getValue().getId());
    }

    @Test
    public void deletingLastQuoteClearsCurrent() {
        seed(1);
        collection.deleteById(1);
        assertNull(session.getCurrentQuote().getValue());
        assertEquals(0, session.getDeck().getValue().size());
    }

    @Test
    public void updateReplacesInPlaceWithoutReordering() {
        seed(5);
        List<Integer> before = deckIds();

        collection.edit(before.get(2), "new author", "new text", "s", "c");

        assertEquals(before, deckIds());
        assertEquals("new author", session.getDeck().getValue().get(2).getAuthor());
    }
}
