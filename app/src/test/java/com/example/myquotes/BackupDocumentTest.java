package com.example.myquotes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.myquotes.notifications.NotificationHistory;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class BackupDocumentTest {

    private static Quote quote(int id) {
        Quote q = new Quote(id, "A" + id, "T" + id, "S" + id);
        q.setAddedAt(500L);
        return q;
    }

    private static NotificationHistory historyFor(int id, long notifiedAt) throws Exception {
        // Package-private mutators live in notifications; build via JSON instead.
        return NotificationHistory.fromJson(new JSONObject(
                "{\"" + id + "\":{\"notifiedCount\":3,\"clickedCount\":1,\"lastNotifiedAt\":" + notifiedAt + "}}"));
    }

    @Test
    public void encodesV2EnvelopeWithHistory() throws Exception {
        String json = new BackupDocument(Arrays.asList(quote(1)), historyFor(1, 900L)).encodePretty();

        JSONObject envelope = new JSONObject(json);
        assertEquals(2, envelope.getInt("version"));
        assertEquals(1, envelope.getJSONArray("quotes").length());
        assertEquals(3, envelope.getJSONObject("notificationHistory").getJSONObject("1").getInt("notifiedCount"));
        assertTrue("Expected 2-space indent", json.contains("\n  "));
    }

    @Test
    public void roundTripsQuotesAndHistory() throws Exception {
        BackupDocument in = new BackupDocument(Arrays.asList(quote(1), quote(2)), historyFor(2, 900L));

        BackupDocument out = BackupDocument.decode(in.encodePretty());

        assertEquals(2, out.quotes.size());
        assertEquals(500L, out.quotes.get(0).getAddedAt());
        NotificationHistory.Entry entry = out.history.entryFor(out.quotes.get(1));
        assertEquals(3, entry.notifiedCount);
        assertEquals(1, entry.clickedCount);
        assertEquals(900L, entry.lastNotifiedAt);
    }

    @Test
    public void v1EnvelopeDecodesWithEmptyHistory() throws Exception {
        String v1 = "{\"version\":1,\"quotes\":[{\"id\":1,\"author\":\"A\",\"quoteText\":\"T\",\"source\":\"S\"}]}";

        BackupDocument out = BackupDocument.decode(v1);

        assertEquals(1, out.quotes.size());
        assertEquals(0L, out.quotes.get(0).getAddedAt());
        assertTrue(out.history.isEmpty());
    }

    @Test
    public void legacyBareArrayDecodesWithEmptyHistory() throws Exception {
        BackupDocument out = BackupDocument.decode("[{\"id\":1,\"author\":\"A\",\"quoteText\":\"T\",\"source\":\"S\"}]");

        assertEquals(1, out.quotes.size());
        assertTrue(out.history.isEmpty());
    }

    @Test
    public void encodingIsDeterministic() throws Exception {
        BackupDocument doc = new BackupDocument(Collections.singletonList(quote(1)), historyFor(1, 900L));
        assertEquals(doc.encodePretty(), doc.encodePretty());
    }

    @Test(expected = QuoteCodecException.class)
    public void rejectsMalformedJson() throws Exception {
        BackupDocument.decode("{ not json");
    }
}
