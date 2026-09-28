package com.example.myquotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SuggestionProvider {

    /** A distinct field value offered while searching, with the number of quotes using it. */
    public static final class FieldSuggestion {
        public final String value;
        public final int count;

        FieldSuggestion(String value, int count) {
            this.value = value;
            this.count = count;
        }
    }

    /**
     * Distinct author, source or category values with a word starting with {@code input}
     * (case-insensitive; "fast" finds "Thinking, Fast and Slow"), most-used first, then by
     * name, at most {@code limit}. Quote text has no suggestions.
     */
    public List<FieldSuggestion> getFieldSuggestions(List<Quote> quotes, QuoteQuery.Field field,
                                                     String input, int limit) {
        String query = input.trim().toLowerCase();
        List<FieldSuggestion> result = new ArrayList<>();
        if (query.isEmpty() || field == QuoteQuery.Field.QUOTE_TEXT) return result;

        Map<String, String> displayName = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Quote q : quotes) {
            String value = valueOf(q, field).trim();
            if (value.isEmpty()) continue;
            String key = value.toLowerCase();
            displayName.putIfAbsent(key, value);
            count.merge(key, 1, Integer::sum);
        }

        for (Map.Entry<String, String> e : displayName.entrySet()) {
            if (hasWordStartingWith(e.getKey(), query)) {
                result.add(new FieldSuggestion(e.getValue(), count.get(e.getKey())));
            }
        }
        result.sort((a, b) -> {
            int c = Integer.compare(b.count, a.count);
            return c != 0 ? c : a.value.compareToIgnoreCase(b.value);
        });
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }

    private static String valueOf(Quote quote, QuoteQuery.Field field) {
        switch (field) {
            case AUTHOR: return quote.getAuthor();
            case SOURCE: return quote.getSource();
            case CATEGORY: return quote.getCategory();
            default: return quote.getQuoteText();
        }
    }

    private static boolean hasWordStartingWith(String value, String query) {
        for (int i = value.indexOf(query); i >= 0; i = value.indexOf(query, i + 1)) {
            if (i == 0 || !Character.isLetterOrDigit(value.charAt(i - 1))) return true;
        }
        return false;
    }

    /**
     * Returns authors matching {@code input} (case-insensitive substring match), ranked:
     * tier 0 = exact match, tier 1 = prefix match, tier 2 = contains match.
     * Within each tier, sorted by quote count desc then name asc.
     */
    public List<String> getAuthorSuggestions(List<Quote> quotes, String input) {
        String query = input.toLowerCase();

        Map<String, String> displayName = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Quote q : quotes) {
            String a = q.getAuthor();
            if (a.isEmpty()) continue;
            String key = a.toLowerCase();
            displayName.putIfAbsent(key, a);
            count.merge(key, 1, Integer::sum);
        }

        List<String[]> matches = new ArrayList<>();
        for (Map.Entry<String, String> e : displayName.entrySet()) {
            String key = e.getKey();
            if (!key.contains(query)) continue;
            int tier = key.equals(query) ? 0 : (key.startsWith(query) ? 1 : 2);
            matches.add(new String[]{e.getValue(), String.valueOf(tier), String.valueOf(count.get(key))});
        }

        matches.sort((a, b) -> {
            int t = Integer.compare(Integer.parseInt(a[1]), Integer.parseInt(b[1]));
            if (t != 0) return t;
            int c = Integer.compare(Integer.parseInt(b[2]), Integer.parseInt(a[2]));
            if (c != 0) return c;
            return a[0].compareToIgnoreCase(b[0]);
        });

        List<String> result = new ArrayList<>(matches.size());
        for (String[] m : matches) result.add(m[0]);
        return result;
    }

    /**
     * Returns source suggestions split into two groups:
     * - authorSources: sources used by {@code authorContext} that match {@code input}, sorted alphabetically.
     * - otherSources: all other matching sources (no duplicates from authorSources), sorted alphabetically.
     * If {@code authorContext} is empty, authorSources is empty and otherSources contains all matches.
     */
    public SourceSuggestions getSourceSuggestions(List<Quote> quotes, String input, String authorContext) {
        String query = input.toLowerCase();
        String authorKey = authorContext.toLowerCase();

        Map<String, String> allDisplayNames = new HashMap<>();
        for (Quote q : quotes) {
            String s = q.getSource();
            if (s.isEmpty()) continue;
            allDisplayNames.putIfAbsent(s.toLowerCase(), s);
        }

        List<String> authorSources = new ArrayList<>();
        if (!authorContext.isEmpty()) {
            Map<String, String> authorSourceDisplay = new HashMap<>();
            for (Quote q : quotes) {
                if (!q.getAuthor().toLowerCase().equals(authorKey)) continue;
                String s = q.getSource();
                if (s.isEmpty()) continue;
                String key = s.toLowerCase();
                authorSourceDisplay.putIfAbsent(key, allDisplayNames.getOrDefault(key, s));
            }
            for (Map.Entry<String, String> e : authorSourceDisplay.entrySet()) {
                if (e.getKey().contains(query)) authorSources.add(e.getValue());
            }
            Collections.sort(authorSources, String.CASE_INSENSITIVE_ORDER);
        }

        Set<String> authorKeys = new HashSet<>();
        for (String s : authorSources) authorKeys.add(s.toLowerCase());

        List<String> otherSources = new ArrayList<>();
        for (Map.Entry<String, String> e : allDisplayNames.entrySet()) {
            if (!e.getKey().contains(query)) continue;
            if (authorKeys.contains(e.getKey())) continue;
            otherSources.add(e.getValue());
        }
        Collections.sort(otherSources, String.CASE_INSENSITIVE_ORDER);

        return new SourceSuggestions(authorSources, otherSources);
    }
}
