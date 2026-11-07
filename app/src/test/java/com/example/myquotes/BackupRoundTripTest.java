package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.myquotes.notifications.NotificationHistory;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BackupRoundTripTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final class InMemoryHistory implements BackupRoundTrip.HistoryStore {
        NotificationHistory current = new NotificationHistory();

        @Override
        public NotificationHistory load() {
            return current;
        }

        @Override
        public void replace(NotificationHistory history) {
            current = history;
        }
    }

    private static Quote quote(int id) {
        Quote q = new Quote(id, "author " + id, "text " + id, "src");
        q.setAddedAt(100L * id);
        return q;
    }

    /** A history with one entry for the quote with this id, notified after it was added. */
    private static NotificationHistory historyFor(int quoteId) throws Exception {
        return NotificationHistory.fromJson(new JSONObject(
                "{\"" + quoteId + "\": {\"notifiedCount\": 2, \"clickedCount\": 1,"
                        + " \"lastNotifiedAt\": 999999}}"));
    }

    private final InMemoryQuoteStore store = new InMemoryQuoteStore(Arrays.asList(quote(1), quote(2)));
    private final QuoteCollection collection = new QuoteCollection(store);
    private final InMemoryHistory history = new InMemoryHistory();
    private final BackupRoundTrip roundTrip = new BackupRoundTrip(store, collection, history);

    @Before
    public void loadCollection() {
        collection.loadFromStore();
    }

    @Test
    public void snapshotCarriesTheStoredQuotesAndTheHistory() throws Exception {
        history.current = historyFor(2);

        BackupDocument document = roundTrip.snapshot();

        assertEquals(2, document.quotes.size());
        assertEquals("text 1", document.quotes.get(0).getQuoteText());
        assertEquals("text 2", document.quotes.get(1).getQuoteText());
        assertSame(history.current, document.history);
        assertNotNull(document.history.entryFor(document.quotes.get(1)));
    }

    @Test
    public void snapshotOfAnEmptyStoreHasNoQuotes() {
        BackupRoundTrip empty = new BackupRoundTrip(new InMemoryQuoteStore(),
                new QuoteCollection(new InMemoryQuoteStore()), history);

        assertTrue(empty.snapshot().quotes.isEmpty());
    }

    @Test
    public void replaceSetsTheCollectionTheStoreAndTheHistory() throws Exception {
        NotificationHistory imported = historyFor(5);

        roundTrip.replace(new BackupDocument(new ArrayList<>(Collections.singletonList(quote(5))),
                imported));

        List<Quote> live = collection.getQuoteList().getValue();
        assertEquals(1, live.size());
        assertEquals(Integer.valueOf(5), live.get(0).getId());
        assertEquals(1, store.load().size());
        assertEquals("text 5", store.load().get(0).getQuoteText());
        assertSame(imported, history.current);
    }

    @Test
    public void replaceWithAnEmptyHistoryClearsTheExistingOne() throws Exception {
        history.current = historyFor(1);

        roundTrip.replace(new BackupDocument(new ArrayList<>(Collections.singletonList(quote(1))),
                new NotificationHistory()));

        assertTrue(history.current.isEmpty());
        assertNull(history.current.entryFor(quote(1)));
    }

    @Test
    public void replaceKeepsRejectedTagsAddedAtFavoriteAndViewState() {
        Quote q = quote(9);
        q.setTags(Arrays.asList("Life"));
        q.setRejectedTags(Arrays.asList("Love"));
        q.setAddedAt(4242L);
        q.setFavorite(true);
        q.setFavoritedAt(5000L);
        q.setTimesShown(7);
        q.setLastShown(6000L);

        roundTrip.replace(new BackupDocument(new ArrayList<>(Collections.singletonList(q)),
                new NotificationHistory()));

        Quote stored = store.load().get(0);
        assertEquals(Collections.singletonList("Life"), new ArrayList<>(stored.getTags()));
        assertEquals(Collections.singletonList("Love"), new ArrayList<>(stored.getRejectedTags()));
        assertEquals(4242L, stored.getAddedAt());
        assertTrue(stored.isFavorite());
        assertEquals(5000L, stored.getFavoritedAt());
        assertEquals(7, stored.getTimesShown());
        assertEquals(6000L, stored.getLastShown());
    }

    @Test
    public void snapshotAfterReplaceGivesTheReplacedState() throws Exception {
        NotificationHistory imported = historyFor(3);
        roundTrip.replace(new BackupDocument(new ArrayList<>(Arrays.asList(quote(3), quote(4))),
                imported));

        BackupDocument document = roundTrip.snapshot();

        assertEquals(2, document.quotes.size());
        assertEquals(imported.toJson().toString(), document.history.toJson().toString());
        assertFalse(document.history.isEmpty());
    }
}
