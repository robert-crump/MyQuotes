package com.example.myquotes;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Shared SAF write path for turning a {@link BackupDocument} into a JSON file at a Uri. */
public final class QuoteExporter {
    private QuoteExporter() {}

    public static void writeToUri(Context context, Uri uri, BackupDocument document) throws IOException {
        String json = document.encodePretty();
        // "wt" forces truncate on providers (e.g. SAF DocumentsProvider) that don't truncate on "w" alone.
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open output stream for " + uri);
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }
}
