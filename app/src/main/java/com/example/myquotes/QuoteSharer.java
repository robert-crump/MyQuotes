package com.example.myquotes;

import android.content.Context;
import android.content.Intent;

/** Starts the system share chooser for a Quote. */
final class QuoteSharer {

    private QuoteSharer() {
    }

    static void share(Context context, Quote quote) {
        if (quote == null) {
            return;
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, QuoteTextRenderer.shareText(quote));
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Quote from My Quotes");
        context.startActivity(Intent.createChooser(shareIntent, "Share quote via"));
    }
}
