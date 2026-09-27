package com.example.myquotes.notifications;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.myquotes.Quote;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class NotificationHistoryTest {

    private static Quote quote(int id) {
        return new Quote(id, "A", "T", "S");
    }

    @Test
    public void recordNotifiedCountsAndStampsTime() {
        NotificationHistory history = new NotificationHistory();
        history.recordNotified(quote(1), 100L);
        history.recordNotified(quote(1), 200L);

        NotificationHistory.Entry entry = history.entryFor(quote(1));
        assertEquals(2, entry.notifiedCount);
        assertEquals(0, entry.clickedCount);
        assertEquals(200L, entry.lastNotifiedAt);
    }

    @Test
    public void recordClickedCountsOnlyForNotifiedQuotes() {
        NotificationHistory history = new NotificationHistory();
        history.recordNotified(quote(1), 100L);
        history.recordClicked(1);
        history.recordClicked(2);

        assertEquals(1, history.entryFor(quote(1)).clickedCount);
        assertEquals(100L, history.entryFor(quote(1)).lastNotifiedAt);
        assertNull(history.entryFor(quote(2)));
    }

    @Test
    public void entryOlderThanTheQuoteIsIgnoredAndRestartedOnNextNotification() {
        NotificationHistory history = new NotificationHistory();
        history.recordNotified(quote(1), 100L);
        history.recordClicked(1);
        Quote reused = quote(1);
        reused.setAddedAt(150L);

        assertNull(history.entryFor(reused));

        history.recordNotified(reused, 300L);
        NotificationHistory.Entry entry = history.entryFor(reused);
        assertEquals(1, entry.notifiedCount);
        assertEquals(0, entry.clickedCount);
    }

    @Test
    public void retainOnlyPrunesDeletedQuotes() {
        NotificationHistory history = new NotificationHistory();
        history.recordNotified(quote(1), 100L);
        history.recordNotified(quote(2), 100L);

        history.retainOnly(Arrays.asList(2, 3));

        assertNull(history.entryFor(quote(1)));
        assertNotNull(history.entryFor(quote(2)));
    }

    @Test
    public void jsonRoundTrip() throws Exception {
        NotificationHistory history = new NotificationHistory();
        history.recordNotified(quote(7), 123L);
        history.recordClicked(7);

        NotificationHistory decoded = NotificationHistory.fromJson(new JSONObject(history.toJson().toString()));

        NotificationHistory.Entry entry = decoded.entryFor(quote(7));
        assertEquals(1, entry.notifiedCount);
        assertEquals(1, entry.clickedCount);
        assertEquals(123L, entry.lastNotifiedAt);
    }

    @Test
    public void fromJsonSkipsMalformedEntries() throws Exception {
        NotificationHistory decoded = NotificationHistory.fromJson(new JSONObject(
                "{\"abc\":{\"notifiedCount\":1},\"4\":5,\"5\":{\"notifiedCount\":2}}"));

        assertNull(decoded.entryFor(quote(4)));
        assertEquals(2, decoded.entryFor(quote(5)).notifiedCount);
    }

    @Test
    public void newHistoryIsEmpty() {
        assertTrue(new NotificationHistory().isEmpty());
    }
}
