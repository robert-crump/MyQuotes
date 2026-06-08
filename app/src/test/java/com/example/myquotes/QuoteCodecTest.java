package com.example.myquotes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class QuoteCodecTest {

    private Quote makeQuote(int id) {
        Quote q = new Quote(id, "Author " + id, "Text " + id, "Source " + id);
        q.setCategory("Category");
        q.setFavorite(true);
        q.setFavoritedAt(1000L);
        q.setLastShown(2000L);
        q.setTimesShown(5);
        return q;
    }

    @Test
    public void roundtrip_allNineFields() throws QuoteCodecException {
        List<Quote> in = Arrays.asList(makeQuote(42));

        List<Quote> out = QuoteCodec.decode(QuoteCodec.encode(in));

        assertEquals(1, out.size());
        Quote result = out.get(0);
        assertEquals(42, (int) result.getId());
        assertEquals("Author 42", result.getAuthor());
        assertEquals("Text 42", result.getQuoteText());
        assertEquals("Source 42", result.getSource());
        assertEquals("Category", result.getCategory());
        assertTrue(result.isFavorite());
        assertEquals(1000L, result.getFavoritedAt());
        assertEquals(2000L, result.getLastShown());
        assertEquals(5, result.getTimesShown());
    }

    @Test
    public void encode_emitsV1EnvelopeShape() throws JSONException {
        String json = QuoteCodec.encode(Arrays.asList(makeQuote(1)));

        JSONObject envelope = new JSONObject(json);
        assertEquals(1, envelope.getInt("version"));
        assertTrue(envelope.has("quotes"));
        assertEquals(1, envelope.getJSONArray("quotes").length());
    }

    @Test
    public void decode_acceptsLegacyBareArray() throws QuoteCodecException {
        String legacy = "[{\"id\":7,\"author\":\"A\",\"quoteText\":\"T\",\"source\":\"S\"," +
                "\"category\":\"C\",\"isFavorite\":false," +
                "\"favoritedAt\":0,\"lastShown\":0,\"timesShown\":0}]";

        List<Quote> quotes = QuoteCodec.decode(legacy);
        assertEquals(1, quotes.size());
        assertEquals(7, (int) quotes.get(0).getId());
    }

    @Test
    public void decode_malformedTopLevelJson_throwsQuoteCodecException() {
        try {
            QuoteCodec.decode("{not valid json");
            fail("Expected QuoteCodecException");
        } catch (QuoteCodecException e) {
            // expected
        }
    }

    @Test
    public void decode_perQuoteMissingId_skippedNotPropagated() throws QuoteCodecException {
        String json = "{\"version\":1,\"quotes\":[" +
                "{\"id\":1,\"author\":\"A\",\"quoteText\":\"T\",\"source\":\"S\"}," +
                "{\"author\":\"B\",\"quoteText\":\"U\",\"source\":\"V\"}" +
                "]}";

        List<Quote> quotes = QuoteCodec.decode(json);
        assertEquals(1, quotes.size());
        assertEquals(1, (int) quotes.get(0).getId());
    }

    @Test
    public void roundtrip_emptyList() throws QuoteCodecException {
        List<Quote> result = QuoteCodec.decode(QuoteCodec.encode(new ArrayList<>()));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void encodePretty_produces2SpaceIndentedOutput() throws JSONException {
        String json = QuoteCodec.encodePretty(Arrays.asList(makeQuote(1)));

        assertTrue("Expected 2-space indent", json.contains("\n  "));
        JSONObject envelope = new JSONObject(json);
        assertEquals(1, envelope.getInt("version"));
        assertEquals(1, envelope.getJSONArray("quotes").length());
    }
}
