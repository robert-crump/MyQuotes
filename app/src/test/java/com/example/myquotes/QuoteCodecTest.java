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
        q.setTags(Arrays.asList("Wisdom", "Life"));
        q.setFavorite(true);
        q.setFavoritedAt(1000L);
        q.setLastShown(2000L);
        q.setTimesShown(5);
        q.setAddedAt(3000L);
        return q;
    }

    @Test
    public void roundtrip_allTenFields() throws QuoteCodecException {
        List<Quote> in = Arrays.asList(makeQuote(42));

        List<Quote> out = QuoteCodec.decode(QuoteCodec.encode(in));

        assertEquals(1, out.size());
        Quote result = out.get(0);
        assertEquals(42, (int) result.getId());
        assertEquals("Author 42", result.getAuthor());
        assertEquals("Text 42", result.getQuoteText());
        assertEquals("Source 42", result.getSource());
        assertEquals(Arrays.asList("Life", "Wisdom"), result.getTags());
        assertTrue(result.isFavorite());
        assertEquals(1000L, result.getFavoritedAt());
        assertEquals(2000L, result.getLastShown());
        assertEquals(5, result.getTimesShown());
        assertEquals(3000L, result.getAddedAt());
    }

    @Test
    public void encode_emitsV2EnvelopeWithTagsAndNoCategory() throws JSONException {
        String json = QuoteCodec.encode(Arrays.asList(makeQuote(1)));

        JSONObject envelope = new JSONObject(json);
        assertEquals(2, envelope.getInt("version"));
        assertEquals(1, envelope.getJSONArray("quotes").length());
        JSONObject quote = envelope.getJSONArray("quotes").getJSONObject(0);
        assertFalse(quote.has("category"));
        JSONArray tags = quote.getJSONArray("tags");
        assertEquals(2, tags.length());
        assertEquals("Life", tags.getString(0));
        assertEquals("Wisdom", tags.getString(1));
        assertFalse(QuoteCodec.isOldFormat(json));
    }

    private static List<String> legacyTags(String category) throws QuoteCodecException {
        String json = "{\"version\":1,\"quotes\":[{\"id\":1,\"author\":\"A\",\"quoteText\":\"T\"," +
                "\"source\":\"S\",\"category\":" + JSONObject.quote(category) + "}]}";
        return QuoteCodec.decode(json).get(0).getTags();
    }

    @Test
    public void decode_legacyCategoryBecomesOneTag() throws QuoteCodecException {
        assertEquals(Arrays.asList("Wisdom"), legacyTags("Wisdom"));
        assertEquals(Arrays.asList("LifeWisdom"), legacyTags("Life Wisdom"));
        assertEquals(Arrays.asList("SelfHelp"), legacyTags(" self-help! "));
        assertTrue(legacyTags("").isEmpty());
        assertTrue(legacyTags(" ?! ").isEmpty());
    }

    @Test
    public void decode_withoutTagsOrCategoryHasNoTags() throws QuoteCodecException {
        String json = "{\"version\":2,\"quotes\":[{\"id\":1,\"author\":\"A\",\"quoteText\":\"T\"}]}";
        assertTrue(QuoteCodec.decode(json).get(0).getTags().isEmpty());
    }

    @Test
    public void decode_tagsWinOverACategory() throws QuoteCodecException {
        String json = "{\"version\":2,\"quotes\":[{\"id\":1,\"tags\":[\"Zen\",\"#art\"],\"category\":\"Old\"}]}";
        assertEquals(Arrays.asList("art", "Zen"), QuoteCodec.decode(json).get(0).getTags());
    }

    @Test
    public void isOldFormat_bareArraysAndV1AreOld() {
        assertTrue(QuoteCodec.isOldFormat("[]"));
        assertTrue(QuoteCodec.isOldFormat("{\"version\":1,\"quotes\":[]}"));
        assertFalse(QuoteCodec.isOldFormat("{\"version\":2,\"quotes\":[]}"));
        assertFalse(QuoteCodec.isOldFormat(null));
    }

    @Test
    public void decode_acceptsLegacyBareArray() throws QuoteCodecException {
        String legacy = "[{\"id\":7,\"author\":\"A\",\"quoteText\":\"T\",\"source\":\"S\"," +
                "\"category\":\"C\",\"isFavorite\":false," +
                "\"favoritedAt\":0,\"lastShown\":0,\"timesShown\":0}]";

        List<Quote> quotes = QuoteCodec.decode(legacy);
        assertEquals(1, quotes.size());
        assertEquals(7, (int) quotes.get(0).getId());
        assertEquals(0L, quotes.get(0).getAddedAt());
        assertEquals(Arrays.asList("C"), quotes.get(0).getTags());
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

}
