package com.example.myquotes;

import java.util.function.Function;

/** Renders a Quote as text: the share format and the daily-notification title/body. Pure. */
public final class QuoteTextRenderer {

    static final int NOTIFICATION_CONTENT_LIMIT = 150;
    static final int NOTIFICATION_BIG_TEXT_LIMIT = 300;


    private static final String ELLIPSIS = " (...)";
    private static final char[] SENTENCE_END = {'.', '!', '?'};

    private QuoteTextRenderer() {
    }

    public static String shareText(Quote quote) {
        String text = "\"" + quote.getQuoteText() + "\"\n\n" + "— " + quote.getAuthor();
        if (!quote.getSource().isEmpty()) {
            text += " (" + quote.getSource() + ")";
        }
        return text;
    }

    public static String notificationTitle(Quote quote, String fallbackTitle,
                                           Function<String, String> titleWithAuthor) {
        String author = quote.getAuthor();
        return author.isEmpty() ? fallbackTitle : titleWithAuthor.apply(author);
    }

    public static String notificationContent(Quote quote) {
        return truncate(quote.getQuoteText(), NOTIFICATION_CONTENT_LIMIT);
    }

    public static String notificationBigText(Quote quote) {
        return truncate(quote.getQuoteText(), NOTIFICATION_BIG_TEXT_LIMIT);
    }

    static String truncate(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }

        String window = text.substring(0, limit);

        int sentenceEnd = lastIndexOfAny(window, SENTENCE_END);
        if (sentenceEnd != -1) {
            return text.substring(0, sentenceEnd + 1) + ELLIPSIS;
        }

        int lastSpace = window.lastIndexOf(' ');
        if (lastSpace > 0) {
            return text.substring(0, lastSpace) + ELLIPSIS;
        }

        return window + ELLIPSIS;
    }

    private static int lastIndexOfAny(String s, char[] chars) {
        for (int i = s.length() - 1; i >= 0; i--) {
            char c = s.charAt(i);
            for (char target : chars) {
                if (c == target) {
                    return i;
                }
            }
        }
        return -1;
    }
}
