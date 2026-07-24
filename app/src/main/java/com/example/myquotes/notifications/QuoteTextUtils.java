package com.example.myquotes.notifications;

final class QuoteTextUtils {

    private static final String ELLIPSIS = " (...)";
    private static final char[] SENTENCE_END = {'.', '!', '?'};

    private QuoteTextUtils() {
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
