package com.example.myquotes;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Shared SAF write path for turning a quote list into a JSON document at a Uri. */
public final class QuoteExporter {
    private QuoteExporter() {}

    public static void writeToUri(Context context, Uri uri, List<Quote> quotes) throws IOException {
        String json = QuoteCodec.encodePretty(quotes);
        // "wt" forces truncate on providers (e.g. SAF DocumentsProvider) that don't truncate on "w" alone.
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open output stream for " + uri);
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
