package com.example.myquotes;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuoteImporterTest {

    private static ByteArrayInputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void read_roundTripsPrettyEncoding() throws Exception {
        Quote q = new Quote(7, "Åuthor", "Multi\nline “text”", "Source");
        q.setCategory("Cat");
        q.setFavorite(true);
        List<Quote> in = Arrays.asList(q, new Quote(8, "B", "T", "S"));

        List<Quote> out = QuoteImporter.read(stream(QuoteCodec.encodePretty(in)));

        assertEquals(2, out.size());
        assertEquals(7, (int) out.get(0).getId());
        assertEquals("Åuthor", out.get(0).getAuthor());
        assertEquals("Multi\nline “text”", out.get(0).getQuoteText());
        assertTrue(out.get(0).isFavorite());
        assertEquals("Cat", out.get(0).getCategory());
    }

    @Test(expected = QuoteCodecException.class)
    public void read_rejectsMalformedJson() throws Exception {
        QuoteImporter.read(stream("{ not json"));
    }

    @Test
    public void read_acceptsLegacyBareArray() throws Exception {
        List<Quote> out = QuoteImporter.read(stream(
                "[{\"id\":1,\"author\":\"A\",\"quoteText\":\"T\",\"source\":\"S\"}]"));
        assertEquals(1, out.size());
        assertEquals("A", out.get(0).getAuthor());
    }

    @Test(expected = IOException.class)
    public void read_propagatesStreamFailure() throws Exception {
        QuoteImporter.read(new java.io.InputStream() {
            @Override public int read() throws IOException { throw new IOException("boom"); }
        });
    }
}
